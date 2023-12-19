package gameserver;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

/**
 * Handles requests from a Client - associated with Game Server.
 *
 * @author Caden Kishimoto
 */
public class GCHandler extends Thread {

    // GCHandler variables
    private final GameServer gs; // GameServer the handler is associated with
    private final Socket conn; // Socket between GS and client
    private ObjectOutputStream out; // output stream to client
    private ObjectInputStream in; // input stream from client
    private final Broadcaster broadcaster; // Broadcaster object for the connection
    private String username; // username of connected player/client
    private String prompt; // prompt that the player/client is typing for the game
    private boolean finished; // flag for when conn is no longer needed (either game finished or invalid client req.)

    /**
     * Constructor for LCHandler. Includes setting up vars and input/output streams.
     *
     * @param gs associated GS for the handler
     * @param conn Socket connection for client
     */
    public GCHandler(GameServer gs, Socket conn) {
        this.gs = gs;
        this.conn = conn;
        prompt = ""; // initially empty
        finished = false;

        // Set up input/output streams
        try {
            out = new ObjectOutputStream(conn.getOutputStream());
            in = new ObjectInputStream(conn.getInputStream());
        } catch (IOException ioe) {
            System.out.println("Error while setting up out/in streams! Exiting...");
            System.exit(1);
        }

        // Init. broadcaster
        broadcaster = new Broadcaster(out);
    }

    /**
     * Closes connection resources between game server and client.
     */
    public void closeConn() {
        // Closing connection resources
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (conn != null) {
                System.out.println("Client disconnected from client port " + conn.getPort());
                conn.close();
            }
        } catch (IOException ioe) {
            System.out.println("Error closing connection with client.");
            System.exit(1);
        }
    }

    /**
     * Run method for GCHandler. Start of flow for game server/client interaction.
     */
    public void run() {
        // Direct flow to handling requests
        try {
            handleRequests();
        } catch (IOException ioe) {
            System.out.println("IOException while handling request!");
        } catch (JSONException jsone) {
            System.out.println("JSONException while handling request!");
        } catch (ClassNotFoundException cnfe) {
            System.out.println("ClassNotFoundException while reading in object!");
        } finally {
            closeConn();
        }
    }

    /**
     * Handles all requests from client (acts as main request handling).
     *
     * @throws IOException error reading/writing with client
     * @throws JSONException error parsing JSON
     * @throws ClassNotFoundException class not found from object being read in
     */
    private void handleRequests() throws IOException, JSONException, ClassNotFoundException {
        while (!finished) {
            JSONObject req;
            JSONObject res;

            // Reading in request from client
            req = new JSONObject((String) in.readObject());

            String type = req.getString("type");
            switch (type) {
                case "join":
                    res = handleJoin(req);
                    break;
                case "answer":
                    res = handleAnswer(req);
                    break;
                case "disconnect":
                    finished = true; // setting flag to disconnect/break loop
                    continue;
                default:
                    res = handleError("An invalid request was received! Goodbye!");
                    finished = true; // breaking connection
                    break;
            }

            // Writing response back to client
            out.writeObject(res.toString());
        }
    }

    /**
     * Handles join request from client.
     *
     * @param req request in JSON
     * @return response in JSON
     * @throws JSONException exception while reading JSONObject
     */
    private JSONObject handleJoin(JSONObject req) throws JSONException {
        username = req.getString("username"); // getting username of the client

        JSONObject res = new JSONObject();
        res.put("type", "connected");
        res.put("message", "Connection established to game server!\n");
        return res;
    }

    /**
     * Handles join request from game server.
     *
     * @param req request in JSON
     * @return response in JSON
     * @throws JSONException exception while reading JSONObject
     */
    private JSONObject handleAnswer(JSONObject req) throws JSONException {
        String answer = req.getString("answer"); // getting client's answer

        JSONObject res = new JSONObject();
        res.put("type", "answerCheck");
        if (answer.equals(prompt)) {
            gs.addToFinishers(this);
            res.put("correct", true);
            res.put("message", "\nCorrect! You placed #" + (gs.getRank(this) + 1) + "!\nWaiting for other players to finish...");
        } else {
            res.put("correct", false);
            res.put("message", "\nIncorrect! Please try again.\nPrompt:\n" + prompt);
        }
        return res;
    }

    /**
     * Creates an error response to send back to client.
     *
     * @param msg error message to send
     * @return response in JSON
     */
    private JSONObject handleError(String msg) {
        JSONObject res = new JSONObject();
        res.put("type", "error");
        res.put("message", msg);
        return res;
    }

    /**
     * Gets the username of the client being handled.
     *
     * @return username of client
     */
    public String getUsername() {
        return username;
    }

    /**
     * Gets the Broadcaster object for this handler.
     *
     * @return handler's Broadcaster obj.
     */
    public Broadcaster getBroadcaster() {
        return broadcaster;
    }

    /**
     * Sets the prompt text for the current game.
     *
     * @param prompt text for the current game
     */
    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    /**
     * Helper class for GCHandler that allows broadcasts to be sent in its own thread.
     */
    public static class Broadcaster implements Runnable {

        // Broadcaster variables
        private final ObjectOutputStream out; // output stream of associated GCHandler
        private String broadcastMsg; // message to send to client

        /**
         * Constructor for Broadcaster. Includes setting up var. from associated GCHandler.
         *
         * @param out output stream to client
         */
        public Broadcaster(ObjectOutputStream out) {
            this.out = out;
            broadcastMsg = ""; // init. msg as well
        }

        /**
         * Sets the broadcast message that's being sent to the client (via run())
         *
         * @param broadcastMsg message to be broadcast to client
         */
        public void setBroadcastMsg(String broadcastMsg) {
            this.broadcastMsg = broadcastMsg;
        }

        /**
         * Run method of Broadcaster. Sends the current broadcastMsg to the client.
         */
        public void run() {
            JSONObject obj = new JSONObject();
            obj.put("type", "broadcast");
            obj.put("message", broadcastMsg);

            try { // Writes to the client
                out.writeObject(obj.toString());
            } catch (IOException ioe) { // no action is needed to verify client received it
                System.out.println("Error broadcasting to client!");
            }
        }
    }
}
