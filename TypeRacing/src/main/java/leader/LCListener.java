package leader;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Listens for incoming client connection requests.
 *
 * @author Caden Kishimoto
 */
public class LCListener extends Thread {

    // LCListener variables
    private final LeaderServer leader; // LeaderServer that clients are connecting to
    private final int port; // port num for incoming clients

    /**
     * Constructor for LCListener to initialize vars.
     *
     * @param leader LeaderServer that clients can query info (like leaderboard or finding a game server) from
     * @param port port num for incoming clients
     */
    public LCListener(LeaderServer leader, int port) {
        this.leader = leader;
        this.port = port;
    }

    /**
     * Run method for LCListener. Includes listening for incoming clients, establishing connection, and creating handlers.
     */
    @Override
    public void run() {
        ServerSocket serv = null;
        try { // opening server socket for listening
            serv = new ServerSocket(port);
        } catch (IOException ioe) {
            System.out.println("Error opening server socket for incoming clients!");
            System.exit(1);
        }

        while (true) {
            try {
                Socket conn = serv.accept(); // accepting an incoming client request
                System.out.println("Client connected from client port " + conn.getPort());

                // Creating and starting a handler for the client (new thread)
                LCHandler lch = new LCHandler(leader, conn);
                lch.start();
            } catch (IOException ioe) {
                System.out.println("Error accepting incoming client connection request!");
            }
        }
    }
}
