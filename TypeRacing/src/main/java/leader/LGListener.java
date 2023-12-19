package leader;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Listens for incoming game server connection requests.
 *
 * @author Caden Kishimoto
 */
public class LGListener extends Thread {

    // LGListener variables
    private final LeaderServer leader; // LeaderServer that game servers are connecting to
    private final int port; // port num for incoming game servers

    /**
     * Constructor for LGListener to initialize vars.
     *
     * @param leader LeaderServer that the game server can send info to
     * @param port port num for incoming game servers
     */
    public LGListener(LeaderServer leader, int port) {
        this.leader = leader;
        this.port = port;
    }

    /**
     * Run method for LGListener. Includes listening for incoming game servers, establishing connection, and creating handlers.
     */
    @Override
    public void run() {
        ServerSocket serv = null;
        try { // opening server socket for listening
            serv = new ServerSocket(port);
        } catch (IOException ioe) {
            System.out.println("Error opening server socket for incoming game servers!");
            System.exit(1);
        }

        while (true) {
            try {
                Socket conn = serv.accept(); // accepting an incoming game server requests
                System.out.println("Game server connected from game server port " + conn.getPort());

                // Creating and starting a handler for the game server (new thread)
                LGHandler lgh = new LGHandler(leader, conn);
                lgh.start();
            } catch (IOException ioe) {
                System.out.println("Error accepting incoming game server connection request!");
            }
        }
    }
}
