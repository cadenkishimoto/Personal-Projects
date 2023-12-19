package client;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Scanner;

/**
 * This class acts as the client for user-interaction. While the implementation in this class includes communication
 * with a leader server, it also instantiates a GameClient (see GameClient.java) every time the client joins a new game,
 * which communicates with the game server on its own Socket connection/thread.
 *
 * @author Caden Kishimoto
 */
public class Client {

    // Client variables
    private Socket conn; // connection with leader server
    private ObjectOutputStream out; // output stream to leader
    private ObjectInputStream in; // input stream from leader
    private boolean stayConnected; // boolean to keep the connection
    private final Scanner scan; // scanner for user input
    private String username; // username of client

    /**
     * Constructor for Client. Includes init. vars, setting up input/output streams, and sending the init. join req.
     *
     * @param lHost host of leader client is connecting to
     * @param lPort host of leader client is connecting to
     */
    public Client(String lHost, int lPort) {
        // Establish connection with leader server
        try {
            conn = new Socket(lHost, lPort);
            out = new ObjectOutputStream(conn.getOutputStream());
            in = new ObjectInputStream(conn.getInputStream());
        } catch (IOException ioe) {
            System.out.println("IOException while connecting to leader/setting up streams! Exiting...");
            System.exit(3);
        }

        // Init. vars.
        scan = new Scanner(System.in);
        stayConnected = true;

        // Send initial join req. and direct flow to handling responses
        try {
            JSONObject obj = new JSONObject();
            obj.put("type", "join");
            out.writeObject(obj.toString());
            handleResponses();
        } catch (IOException ioe) {
            System.out.println("IOException while handling response! Closing connection with leader...");
        } catch (JSONException jsone) {
            System.out.println("JSONException while handling response! Closing connection with leader...");
        } catch (ClassNotFoundException cnfe) {
            System.out.println("ClassNotFoundException while reading in object! Closing connection with leader...");
        }

        closeConn(); // closing connection afterward
    }

    /**
     * Closes connection resources between leader and client.
     */
    public void closeConn() {
        // Closing connection resources
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (conn != null) conn.close();
        } catch (IOException ioe) {
            System.out.println("IOException while closing connection with leader.");
        }
    }

    /**
     * Handles responses from leader.
     *
     * @throws IOException error reading/writing with leader
     * @throws JSONException error parsing JSON
     * @throws ClassNotFoundException class not found from object being read in
     */
    private void handleResponses() throws IOException, JSONException, ClassNotFoundException {
        while (stayConnected) {
            JSONObject req;
            JSONObject res;

            // Reading in response from leader
            res = new JSONObject((String) in.readObject());

            String type = res.getString("type");
            switch (type) { // switch for response types
                case "askForLogin":
                    System.out.println(res.getString("message"));
                    req = createLogin(); // create login req.
                    break;
                case "showMenu":
                    System.out.println(res.getString("menu"));
                    req = createMenuChoice(); // create menuChoice req.
                    break;
                case "joinGame":
                    String host = res.getString("host");
                    int port = res.getInt("port");
                    System.out.println(res.getString("message"));
                    playGame(host, port);
                    req = createAskForMenu();  // create askForMenu req.
                    break;
                case "leaderboard":
                    System.out.println(res.getString("leaderboard")); // printing leaderboard
                    req = createMenuChoice();  // create menuChoice req.
                    break;
                case "exit": // exit is printed the same as default/error
                default: // default/error response type
                    System.out.println(res.getString("message")); // printing message
                    stayConnected = false;
                    return;
            }

            // Writing request back to client
            out.writeObject(req.toString());
        }
    }

    /**
     * Plays a game by creating a GameClient instance and directing flow there. Once done, the leader communication resumes
     * in handleResponses().
     *
     * @param host host of game server client is connecting to
     * @param port port of game server client is connecting to
     */
    private void playGame(String host, int port) {
        GameClient gc = new GameClient(host, port, username); // create a new instance/establish conn. to game server
        gc.start(); // start a thread for communication

        try {
            gc.join(); // wait for thread/game server connection to finish
        } catch (InterruptedException ie) {
            System.out.println("InterruptedException while waiting for thread to join.");
        }
    }

    /**
     * Creates a login request.
     *
     * @return created request in JSON
     */
    private JSONObject createLogin() {
        System.out.print("\nEnter Username: ");
        username = scan.nextLine(); // get username from user
        System.out.print("Enter Password: ");
        String password = scan.nextLine(); // get password from user

        // Build request
        JSONObject req = new JSONObject();
        req.put("type", "login");
        req.put("username", username);
        req.put("password", password);
        return req;
    }

    /**
     * Creates a menuChoice request.
     *
     * @return created request in JSON
     */
    private JSONObject createMenuChoice() {
        boolean valid = false;
        int choice = -1;
        while (!valid) {
            try {
                choice = Integer.parseInt(scan.nextLine()); // get choice from user
                valid = true;
            } catch (NumberFormatException nfe) { // caught if not an int
                System.out.println("Not an integer! Please enter an integer. (0-2)\n");
            }
        }

        // Build request
        JSONObject req = new JSONObject();
        req.put("type", "menuChoice");
        req.put("choice", choice);
        return req;
    }

    /**
     * Creates an askForMenu request.
     *
     * @return created request in JSON
     */
    private JSONObject createAskForMenu() {
        // Build request
        JSONObject req = new JSONObject();
        req.put("type", "askForMenu");
        return req;
    }

    /**
     * Start of execution for Client.
     *
     * @param args args from CLI
     */
    public static void main(String[] args) {

        if (args.length != 2) {
            System.out.println("Expected arguments: <host(String)> <port(int)>");
            System.exit(1);
        }

        String host = "localhost"; // default leader host
        int port = 8080; // default leader port

        // Parsing args for port numbers
        try {
            host = args[0];
            port = Integer.parseInt(args[1]);
        } catch (NumberFormatException nfe) {
            System.out.println("Port must be an integer.");
            System.exit(2);
        }

        // Creates a new Client instance, passing in the args
        Client c = new Client(host, port);
    }
}
