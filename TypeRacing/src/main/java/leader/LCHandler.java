package leader;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;
import java.net.Socket;

/**
 * Handles requests from an individual client.
 *
 * @author Caden Kishimoto
 */
public class LCHandler extends Thread {

    // LCHandler variables
    private final LeaderServer leader; // LeaderServer that the client is connecting to
    private final Socket conn; // Socket connection for client
    private ObjectOutputStream out; // output stream to client
    private ObjectInputStream in; // input stream from client
    private boolean stayConnected; // boolean to keep the connection
    private String username; // username of client being serviced

    /**
     * Constructor for LCHandler. Includes setting up vars and input/output streams.
     *
     * @param leader LeaderServer that the client can query info (like leaderboard or finding a game server) from
     * @param conn Socket connection for client
     */
    public LCHandler(LeaderServer leader, Socket conn) {
        this.leader = leader;
        this.conn = conn;

        // Set up input/output streams
        try {
            out = new ObjectOutputStream(conn.getOutputStream());
            in = new ObjectInputStream(conn.getInputStream());
        } catch (IOException ioe) {
            System.out.println("Error while setting up out/in streams! Exiting...");
            System.exit(1);
        }

        stayConnected = true;
    }

    /**
     * Run method for LCHandler. Start of flow for client/leader interaction.
     */
    public void run() {
        // Direct flow to handling requests
        try {
            handleRequests();
        } catch (IOException ioe) {
            System.out.println("IOException while handling request! Closing connection with client...");
        } catch (JSONException jsone) {
            System.out.println("JSONException while handling request! Closing connection with client...");
        } catch (ClassNotFoundException cnfe) {
            System.out.println("ClassNotFoundException while reading in object! Closing connection with client...");
        }

        closeConn();
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
            System.out.println("Error closing connection.");
            System.exit(1);
        } finally {
            System.out.println("Client disconnected from client port " + conn.getPort());
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
        while (stayConnected) {
            JSONObject req;
            JSONObject res;

            // Reading in request from client
            req = new JSONObject((String) in.readObject());

            String type = req.getString("type");
            switch (type) {
                case "join":
                    res = handleJoin();
                    break;
                case "login":
                    res = handleLogin(req);
                    break;
                case "menuChoice":
                    res = handleMenuChoice(req);
                    break;
                case "askForMenu":
                    res = handleAskForMenu();
                    break;
                default:
                    res = handleError("An invalid request was received! Goodbye!");
                    stayConnected = false;
                    break;
            }

            // Writing response back to client
            out.writeObject(res.toString());
        }
    }

    /**
     * Handles join request from client.
     *
     * @return response in JSON
     */
    private JSONObject handleJoin() {
        JSONObject res = new JSONObject();
        res.put("type", "askForLogin");
        res.put("message", "Connection established!\n\n" + getLoginText());
        return res;
    }

    /**
     * Handles login request from client.
     *
     * @param req request in JSON
     * @return response in JSON
     * @throws JSONException exception while reading JSONObject
     */
    private JSONObject handleLogin(JSONObject req) throws JSONException {
        JSONObject res = new JSONObject();

        String username = req.getString("username");
        String password = req.getString("password");

        if (username.isEmpty() || password.isEmpty()) {
            return handleError("Username/Password is empty!");
        }

        if (!leader.hasUser(username)) {
            leader.addUser(username, password);
            this.username = username;
            res.put("type", "showMenu");
            res.put("menu", "New account created!\n\n" + getMenu());
        } else if (!leader.checkPassword(username, password)) {
            res.put("type", "askForLogin");
            res.put("message", "Incorrect password for the existing username! Please try again.");
        } else {
            this.username = username;
            res.put("type", "showMenu");
            res.put("menu", "Login successful!\n\n" + getMenu());
        }

        return res;
    }

    /**
     * Handles menu choice request from client.
     *
     * @param req request in JSON
     * @return response in JSON
     * @throws JSONException exception while reading JSONObject
     */
    private JSONObject handleMenuChoice(JSONObject req) throws JSONException {
        JSONObject res = new JSONObject();

        int choice;
        try { // getting user choice as int
            choice = req.getInt("choice");
        } catch (NumberFormatException nfe) {
            res.put("type", "showMenu");
            res.put("menu", "Not an integer! Please enter a valid option. (0-2)\n" + getMenu());
            return res;
        }

        switch (choice) {
            case 0: // exit
                res.put("type", "exit");
                res.put("message", "\nGoodbye!");
                stayConnected = false;
                break;
            case 1: // play new game
                String s = leader.findGame(); // getting conn info for game server
                String[] strs = s.split(":"); // splitting String for ip and port

                if (s.isEmpty()) { // if no available game server
                    res.put("type", "showMenu");
                    res.put("menu", "No available game servers! Please try again later when there is capacity.\n\n" +
                            getMenu());
                } else { // else send joinGame response for available game server
                    res.put("type", "joinGame");
                    res.put("host", strs[0]);
                    res.put("port", strs[1]);
                    res.put("message", "Game found! Joining game...");
                }
                break;
            case 2: // see current leaderboard
                res.put("type", "leaderboard");
                res.put("leaderboard", leader.getLeaderboard(username) + "\n" + getMenu());
                break;
            default: // integer outside 0-2 range
                res.put("type", "showMenu");
                res.put("menu", "Not a valid integer! Please enter a valid option. (0-2)\n" + getMenu());
                break;
        }

        return res;
    }

    /**
     * Handles ask for menu request from client.
     *
     * @return response in JSON
     */
    private JSONObject handleAskForMenu() {
        JSONObject res = new JSONObject();
        res.put("type", "showMenu");
        res.put("menu", getMenu());
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
     * Gets login text for client.
     *
     * @return String of login text
     */
    private String getLoginText() {
        return "Welcome!\nIf you are a new user: Please sign-up by entering a username followed by a password.\n" +
                "If you are a returning user: Please enter your existing username and password to login!";
    }

    /**
     * Gets menu text for client.
     *
     * @return String of menu text
     */
    private String getMenu() {
        return "Please select an option (0 to exit):\n 1. Play a game\n 2. See current leaderboard";
    }
}
