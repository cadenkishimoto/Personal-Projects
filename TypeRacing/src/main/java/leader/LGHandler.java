package leader;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

/**
 * Handles requests from an individual game server - associated with Leader.
 *
 * @author Caden Kishimoto
 */
public class LGHandler extends Thread {

    // LGHandler variables
    private final int MAX_PLAYERS = 5; // const. of max number of players for a game server
    private final LeaderServer leader; // LeaderServer that is handling req's from GameServer
    private final Socket conn; // Socket connection for game server
    private ObjectOutputStream out; // output stream to game server
    private ObjectInputStream in; // input stream from game server
    private int clientPort; // port num for game server's incoming clients
    private boolean stayConnected; // boolean to keep the connection
    private boolean finished; // flag for whether the game server's current game has finished
    private boolean gameStateUpdated; // flag checking if game state was updated after sending a gameState request
    private int playerCount; // number of players in a game server's current game

    /**
     * Constructor for LCHandler. Includes setting up vars and input/output streams.
     *
     * @param leader LeaderServer that the game server can send info to
     * @param conn Socket connection for game server
     */
    public LGHandler(LeaderServer leader, Socket conn) {
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
        finished = true;
        playerCount = 0;
    }

    /**
     * Run method for LGHandler. Start of flow for game server/leader interaction.
     */
    @Override
    public void run() {
        // Direct flow to handling requests
        try {
            handleRequests();
        } catch (IOException ioe) {
            System.out.println("IOException while handling request! Closing connection with game server...");
        } catch (JSONException jsone) {
            System.out.println("JSONException while handling request! Closing connection with game server...");
        } catch (ClassNotFoundException cnfe) {
            System.out.println("ClassNotFoundException while reading in object! Closing connection with game server...");
        }

        closeConn();
    }

    /**
     * Closes connection resources between the leader and game server.
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
            System.out.println("Game server disconnected from game server port " + conn.getPort());
        }
    }

    /**
     * Handles all requests from game server (acts as main request handling).
     *
     * @throws IOException error reading/writing with game server
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
                    res = handleJoin(req);
                    break;
                case "gameState":
                    handleGameState(req);
                    continue;
                case "gameWinner":
                    handleGameWinner(req);
                    continue;
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
     * Handles join request from game server.
     *
     * @param req request in JSON
     * @return response in JSON
     * @throws JSONException exception while reading JSONObject
     */
    private JSONObject handleJoin(JSONObject req) throws JSONException {
        clientPort = req.getInt("clientPort"); // getting clientPort of the game server
        leader.addGameServer(this); // adding this game server to leader's list

        JSONObject res = new JSONObject();
        res.put("type", "connected");
        res.put("message", "Connection established!\n");
        return res;
    }

    /**
     * Handles game state request from game server.
     *
     * @param req request in JSON
     * @throws JSONException exception while reading JSONObject
     */
    private void handleGameState(JSONObject req) throws JSONException {
        finished = req.getBoolean("finished"); // updates the finished flag
        playerCount = req.getInt("playerCount");
        setGameStateUpdated(true); // notifies the thread that's waiting for a response in isAvailable()
    }

    /**
     * Handles game winner request from game server.
     *
     * @param req request in JSON
     * @throws JSONException exception while reading JSONObject
     */
    private void handleGameWinner(JSONObject req)  throws JSONException {
        String winner = req.getString("winner");
        leader.addWin(winner); // adds user's win to the leaderboard
        finished = true; // update finished flag
        playerCount = 0; // update playerCount
    }

    /**
     * Creates an error response to send back to game server.
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
     * Determines if the game server is available for another player to join.
     *
     * @return true if game server is available
     */
    public boolean isAvailable() {
        // Sends JSON request to game server to check if a game has finished
        try {
            JSONObject req = new JSONObject();
            req.put("type", "checkGameState");
            out.writeObject(req.toString());
            while (!getGameStateUpdated()); // waits for "gameStateUpdated" to be updated before proceeding
            setGameStateUpdated(false); // resetting flag
        } catch (IOException ioe) {
            System.out.println("IOException while writing object! Closing connection with game server...");
            closeConn(); // close the connection
            return false; // GS will later be removed from leader's list now that the connection is closed
        }

        // Once finished var. is updated, ensure GS isn't full and that the game has finished
        return playerCount < MAX_PLAYERS && finished;
    }

    /**
     * Gets a boolean on whether the Socket connection has closed.
     *
     * @return true if Socket connection has closed
     */
    public boolean hasClosed() {
       return conn.isClosed();
    }

    /**
     * Gets host of the game server.
     *
     * @return host of connected game server
     */
    public String getHost() {
        return conn.getInetAddress().toString();
    }

    /**
     * Gets the port that GS is listening to incoming clients on.
     *
     * @return client port of GS
     */
    public int getClientPort() {
        return clientPort;
    }

    /**
     * Gets the current boolean value of finishedUpdated. (Synchronized for multi-threading)
     *
     * @return current boolean value of finishedUpdated.
     */
    public synchronized boolean getGameStateUpdated() {
        return gameStateUpdated;
    }

    /**
     * Sets the boolean value of finishedUpdated. (Synchronized for multi-threading)
     *
     * @param b boolean value to set finishedUpdated to
     */
    public synchronized void setGameStateUpdated(boolean b) {
        gameStateUpdated = b;
    }
}
