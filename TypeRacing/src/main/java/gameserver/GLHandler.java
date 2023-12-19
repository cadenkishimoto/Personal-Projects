package gameserver;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

/**
 * Handles requests from a Leader - associated with Game Server.
 *
 * @author Caden Kishimoto
 */
public class GLHandler extends Thread {

    // GLHandler variables
    private final GameServer gs; // GameServer that is handling req's from Leader
    private Socket conn; // connection between associated game server and leader
    private final int clientPort; // port for listening to incoming clients
    private ObjectOutputStream out; // output stream to leader
    private ObjectInputStream in; // input stream from leader
    private boolean stayConnected; // boolean to keep connection with leader

    /**
     * Constructor for GLHandler. Includes init. vars and establishing connection with Leader.
     *
     * @param leaderHost host of leader server
     * @param leaderPort port of leader server
     * @param clientPort port for listening to incoming clients
     */
    public GLHandler(GameServer gs, String leaderHost, int leaderPort, int clientPort) {
        // Init. vars
        this.gs = gs;
        this.clientPort = clientPort;

        // Establish connection with leader server
        try {
            conn = new Socket(leaderHost, leaderPort);
            out = new ObjectOutputStream(conn.getOutputStream());
            in = new ObjectInputStream(conn.getInputStream());
        } catch (IOException ioe) {
            System.out.println("IOException while connecting to leader/setting up streams! Exiting...");
            System.exit(3);
        }

        stayConnected = true;
        System.out.println("Game server connected to leader!");
    }

    /**
     * Closes connection resources between the game server and leader.
     */
    public void closeConn() {
        // Closing connection resources
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (conn != null) conn.close();
        } catch (IOException ioe) {
            System.out.println("Error closing connection.");
        } finally {
            System.out.println("Leader disconnected.");
        }
    }

    /**
     * Run method for GLHandler. Sends the initial join request to leader - followed by directing flow into
     * communication handling.
     */
    @Override
    public void run() {
        // Send join request to leader
        try {
            JSONObject req = new JSONObject();
            req.put("type", "join");
            req.put("clientPort", clientPort); // add clientPort so Leader knows correct port info to give to clients
            out.writeObject(req.toString());
        } catch (IOException ioe) {
            System.out.println("IOException while writing object! Closing connection with leader...");
            closeConn();
            gs.closeGS();
            return;
        }

        // Direct flow to handling leader communication
        handleLeaderCommunication();
    }

    /**
     * Handles communication from leader.
     */
    public void handleLeaderCommunication() {
        try {
            while (stayConnected) {
                JSONObject receiveObj;
                JSONObject sendObj;

                // Reading in obj. from leader
                receiveObj = new JSONObject((String) in.readObject());

                String type = receiveObj.getString("type");
                switch (type) {
                    case "connected":
                        System.out.println(receiveObj.getString("message"));
                        break;
                    case "checkGameState":
                        sendObj = new JSONObject();
                        sendObj.put("type", "gameState");
                        sendObj.put("finished", gs.getFinished()); // true if game has finished
                        sendObj.put("playerCount", gs.getPlayersSize());
                        out.writeObject(sendObj.toString());
                        break;
                    case "error":
                        System.out.println(receiveObj.getString("message"));
                        stayConnected = false;
                        break;
                    default:
                        System.out.println("Unknown type received! Exiting...");
                        stayConnected = false;
                        break;
                }
            }
        } catch (IOException ioe) {
            System.out.println("IOException while handling request! Closing connection with leader...");
        } catch (JSONException jsone) {
            System.out.println("JSONException while handling request! Closing connection with leader...");
        } catch (ClassNotFoundException cnfe) {
            System.out.println("ClassNotFoundException while reading in object! Closing connection with leader...");
        } finally {
            closeConn(); // closing connection with Leader
            gs.closeGS(); // stops GS before closing this handler
        }
    }

    /**
     * Sends the winner to the Leader to update the system (users, leaderboard, etc.)
     *
     * @param winner username of the game's winner
     * @throws IOException cannot write to Leader (connection lost)
     */
    public void sendGameWinner(String winner) throws IOException {
        JSONObject req = new JSONObject();
        req.put("type", "gameWinner");
        req.put("winner", winner);
        out.writeObject(req.toString());
    }
}