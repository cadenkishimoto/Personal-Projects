package client;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Scanner;

/**
 * Aids Client.java in handling client communication with a game server. (This class is instantiated every time a
 * client joins a new game.)
 *
 * @author Caden Kishimoto
 */
public class GameClient extends Thread {

    // GameClient variables
    private Socket conn; // connection with game server
    private ObjectOutputStream out; // output stream to GS
    private ObjectInputStream in; // input stream from GS
    private Sender sender; // Sender object for the connection
    private boolean stayConnected; // boolean to keep the connection
    private final String host; // GS host
    private final int port; // GS port
    private final String username; // username of client
    private boolean answerChecked; // flag that determines whether the current answer sent has been checked
    private final Object lock = new Object(); // lock object for answerChecked

    /**
     * Constructor for GameClient. Includes init./setting vars from an associated Client instance.
     *
     * @param host host name of GS to connect to
     * @param port port num of GS to connect to
     * @param username username of client/user
     */
    public GameClient(String host, int port, String username) {
        this.host = host;
        this.port = port;
        this.username = username;
    }

    /**
     * Run method for GameClient. Includes establishing connection/streams with GS, sending the initial join req.,
     * and directing flow to handleResponses() - ending with closing the connection resources in closeConn().
     */
    @Override
    public void run() {
        // Establish connection with game server
        try {
            conn = new Socket(host, port);
            out = new ObjectOutputStream(conn.getOutputStream());
            in = new ObjectInputStream(conn.getInputStream());
        } catch (IOException ioe) {
            System.out.println("IOException while connecting to leader/setting up streams! Exiting...");
            System.exit(3);
        }

        // Init. vars.
        stayConnected = true;
        answerChecked = false;

        // Send initial join req. and direct flow to handling responses
        try {
            JSONObject obj = new JSONObject();
            obj.put("type", "join");
            obj.put("username", username);
            out.writeObject(obj.toString());
            handleResponses();
        } catch (IOException ioe) {
            System.out.println("IOException while handling response! Closing connection with game server...");
        } catch (JSONException jsone) {
            System.out.println("JSONException while handling response! Closing connection with game server...");
        } catch (ClassNotFoundException cnfe) {
            System.out.println("ClassNotFoundException while reading in object! Closing connection with game server...");
        }

        closeConn(); // closing connection afterward
    }

    /**
     * Closes connection resources between game server and client.
     */
    public void closeConn() {
        // Closing connection resources
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (conn != null) conn.close();
        } catch (IOException ioe) {
            System.out.println("IOException while closing connection with game server.");
            System.exit(1);
        } finally {
            System.out.println("Leaving game... Returning to main menu...\n");
        }
    }

    /**
     * Handles all responses from game server.
     *
     * @throws IOException error reading/writing with game server
     * @throws JSONException error parsing JSON
     * @throws ClassNotFoundException class not found from object being read in
     */
    private void handleResponses() throws IOException, JSONException, ClassNotFoundException {
        while (stayConnected) {
            JSONObject res;

            // Reading in response from game server
            res = new JSONObject((String) in.readObject());

            String type = res.getString("type");
            switch (type) { // switch for response types
                case "connected":
                    System.out.println(res.getString("message"));
                    break;
                case "answerCheck":
                    if (res.getBoolean("correct")) { // if answer was correct
                        sender.stopGivingAnswers(); // stop the user from entering input
                    }
                    System.out.println(res.getString("message"));
                    setAnswerChecked(true); // set flag to true (current answer has been checked)
                    break;
                case "broadcast":
                    handleBroadcast(res.getString("message")); // handles info from the broadcast
                    break;
                default: // default/error response type
                    System.out.println(res.getString("message")); // printing error message
                    stayConnected = false;
                    break;
            }
        }
    }

    /**
     * Handles info in the broadcast that can alter GameClient/Sender state.
     *
     * @param broadcast message that was broadcast to Client
     * @throws IOException writing to GS (from sendDisconnect())
     */
    private void handleBroadcast(String broadcast) throws IOException {
        if (broadcast.contains("Game start!")) { // if the game has started
            System.out.println(broadcast);
            sender = new Sender(this, out); // creates the sender and starts it
            sender.start();
        } else if (broadcast.contains("Game over!")) { // if the game is over
            System.out.println(broadcast);
            sender.stopGivingAnswers(); // stop the user from entering input
            if (broadcast.contains("DNF - " + username)) { // if the user did not finish in-time
                System.out.println("Please press \"Enter\"."); // prompt user to press enter to flush Scanner/break loop in Sender
                try {
                    sender.join(); // wait for Sender's run() to finish
                } catch (InterruptedException ie) {
                    System.out.println("InterruptedException while waiting for thread to join.");
                }
            }
            sendDisconnect();
            stayConnected = false; // and stop looping in handleResponses() to close connection
        } else {
            synchronized (System.in) { // synchronized for mid-game broadcasts so user input isn't interrupted in console
                System.out.println(broadcast); // print the message
            }
        }
    }

    /**
     * Send a disconnect request to the game server.
     *
     * @throws IOException writing to GS
     */
    private void sendDisconnect() throws IOException {
        JSONObject req = new JSONObject();
        req.put("type", "disconnect");
        out.writeObject(req.toString());
    }

    /**
     * Gets the boolean value of answerChecked.
     *
     * @return boolean value of answerChecked
     */
    public boolean getAnswerChecked() {
        synchronized (lock) {
            return answerChecked;
        }
    }

    /**
     * Sets the boolean value of answerChecked.
     *
     * @param b boolean value to set answerChecked to
     */
    public void setAnswerChecked(boolean b) {
        synchronized (lock) {
            answerChecked = b;
        }
    }

    /**
     * Helper class for GameClient that allows client/player to send answers (in its own thread) while receiving broadcasts/messages.
     */
    private static class Sender extends Thread {

        // Sender variables
        private final GameClient gc; // associated GameClient
        private final ObjectOutputStream out; // output stream of associated GameClient
        private final Scanner scan; // Scanner for user input
        private boolean giveAnswers; // flag that allows sending answers (while game is in-progress)

        /**
         * Constructor for Sender. Includes setting up vars. from associated GameClient.
         *
         * @param gc associated GameClient
         * @param out output stream of associated GameClient
         */
        public Sender(GameClient gc, ObjectOutputStream out) {
            this.gc = gc;
            this.out = out;
            scan = new Scanner(System.in); // init. Scanner obj.
            giveAnswers = true; // Sender instance is created when the game begins
        }

        /**
         * Sets the flag to stop the client from giving answers. (Synchronized for multi-threading)
         */
        public synchronized void stopGivingAnswers() {
            giveAnswers = false;
        }

        /**
         * Run method for Sender. Loops to send receive user input/send answers until giveAnswers is flagged true.
         */
        @Override
        public void run() {
            while (giveAnswers) {
                String answer;
                synchronized (System.in) { // synchronized to ensure nothing is outputted to console while user is typing
                    answer = scan.nextLine();
                }
                if (!giveAnswers) { // if client stopped being able to give answers while typing, return
                    return;
                }

                JSONObject obj = new JSONObject();
                obj.put("type", "answer");
                obj.put("answer", answer);

                try { // Writes to GS
                    out.writeObject(obj.toString());
                    while (!gc.getAnswerChecked()); // waits for answer to be checked before proceeding
                    gc.setAnswerChecked(false); // resetting flag
                } catch (IOException ioe) {
                    System.out.println("Error writing answer to GS! Safely exiting... Goodbye!");
                    stopGivingAnswers(); // break the loop
                }
            }
        }
    }
}
