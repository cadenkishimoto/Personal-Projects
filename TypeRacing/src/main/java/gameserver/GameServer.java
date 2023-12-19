package gameserver;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Random;
import java.util.Timer;

/**
 * This class acts as the game server that will host games for connected clients. It uses a CountdownBroadcast instance
 * (see CountdownBroadcast.java) to send messages to each of the connected clients, and it creates handlers (see
 * GCHandler.java and GLHandler.java) for each individual connection. Both handler types and the CountdownBroadcast
 * operate in their own threads to perform concurrently.
 *
 * @author Caden Kishimoto
 */
public class GameServer {

    // GameServer variables
    private final static String promptsFilename = "prompts.txt"; // name of prompts file
    private ServerSocket clientSocket; // ServerSocket that listens to incoming clients
    private final GLHandler glh; // handler for connection/communication with leader server
    private boolean finished; // flag for if the current game is finished
    private boolean timesUp; // flag for time/countdown reaching 0 for current game
    private int playerCount; // number of players in a game server's current game
    private final ArrayList<GCHandler> players; // list of players for current game
    private final ArrayList<GCHandler> finishers; // list of finishers for current game
    private ArrayList<String> prompts; // list of text prompts for games

    /**
     * Constructor for GameServer. Includes init./setting vars and opening Sockets.
     *
     * @param leaderHost host of leader server
     * @param leaderPort port of leader server
     * @param clientPort port for listening to incoming clients
     */
    public GameServer(String leaderHost, int leaderPort, int clientPort) {
        // Init./setting vars.
        players = new ArrayList<>();
        finishers = new ArrayList<>();

        // Instantiate and start a GLHandler to establish connection (and handle communication) with Leader
        glh = new GLHandler(this, leaderHost, leaderPort, clientPort);
        glh.start();

        // Open server socket to listen for incoming clients
        try {
            clientSocket = new ServerSocket(clientPort);
        } catch (IOException ioe) {
            System.out.println("Error opening server socket for incoming clients!");
            System.exit(1);
        }

        // Init. more vars.
        finished = true;
        timesUp = false;
        playerCount = 0;
        loadPrompts(); // loading prompts

        listenForClients(); // Directs flow to listen for clients/players
    }

    /**
     * Stops GameServer (also closes clientSocket).
     */
    public void closeGS() {
        // Closing connection resources
        try {
            if (clientSocket != null) clientSocket.close();
        } catch (IOException ioe) {
            System.out.println("Error closing ServerSocket.");
            System.exit(1);
        }
        System.exit(0); // exit successfully
    }

    /**
     * Gets a String of the game's results.
     *
     * @return String of game results
     */
    public String getResults() {
        StringBuilder sb = new StringBuilder(); // create StringBuilder
        sb.append("Results:\n");

        int rank = 1;
        for (GCHandler gch : finishers) { // add the finishers in rank order
            sb.append("#").append(rank++).append(" - ").append(gch.getUsername()).append("\n");
        }

        for (GCHandler gch : players) { // add the remaining players that did not finish
            if (!finishers.contains(gch)) {
                sb.append("DNF - ").append(gch.getUsername()).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * Controls game flow of a game (aka plays out game control)
     */
    public void playGame() {
        System.out.println("Game started!");

        // Select a random prompt from the prompts set
        Random rnd = new Random();
        String prompt = prompts.get(rnd.nextInt(prompts.size()));

        // create a TimerTask for game in-progress (type = "finish", non-empty prompt)
        CountdownBroadcast cb = new CountdownBroadcast(this, players, finishers, "finish", prompt); // counting down end of game/finish
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(cb, 0, 15000);

        // Busy waiting while waiting for game to complete (either time runs out or all players finish)
        while (!getTimesUp() && players.size() != getFinishersSize());
        cb.cancel(); // cancel the countdown regardless of whether it's still active

        System.out.println("Game finished!");

        // Create an array of threads (one thread per player)
        Thread[] threads = new Thread[players.size()];
        String res = getResults(); // get results

        // Broadcast/Thread the message to players
        for (int i = 0; i < threads.length; i++) {
            players.get(i).getBroadcaster().setBroadcastMsg("\nGame over!\n" + res);
            threads[i] = new Thread(players.get(i).getBroadcaster());
            threads[i].start();
        }

        // Joining threads
        try {
            for (Thread t : threads) {
                t.join();
            }
        } catch (InterruptedException ie) { // will continue regardless
            ie.printStackTrace();
            System.out.println("InterruptedException while waiting for threads to join.");
        }

        // Sending game winner to update winner's "account"
        try {
            if (getFinishersSize() > 0) { // checking to make sure at least 1 player finished
                glh.sendGameWinner(finishers.get(0).getUsername());
            }
        } catch (IOException ioe) {
            System.out.println("IOException while sending game winner to leader server! Exiting...");
            System.exit(1);
        }

        // Closing connections/freeing resources
        players.clear();
        finishers.clear();
        playerCount = 0;
        timesUp = false;

        setFinished(true); // set flag to true for more clients to join a new game
    }

    /**
     * Listens for incoming client connection requests, which also triggers the countdown/start of game.
     */
    public void listenForClients() {
        while (true) {
            try {
                Socket clientConn = clientSocket.accept(); // accept an incoming client connection request
                System.out.println("Client connected from client port " + clientConn.getPort());

                playerCount++; // increment player count

                // Creating and starting a handler for the client (new thread)
                GCHandler gch = new GCHandler(this, clientConn);
                gch.start();

                players.add(gch); // add handler to list of players

                if (playerCount == 1) { // if it's the first player, start the countdown
                    CountdownBroadcast cb = new CountdownBroadcast(this, players, finishers, "start", ""); // counting down start of game
                    Timer timer = new Timer();
                    timer.scheduleAtFixedRate(cb, 0, 5000);
                }
            } catch (IOException ioe) {
                System.out.println("Error accepting incoming client connection request!");
            }
        }
    }

    /**
     * Load prompts from file. (Synchronized for multi-threading)
     */
    public synchronized void loadPrompts() {
        prompts = new ArrayList<>(); // init. prompts var.

        try {
            File lFile = new File(promptsFilename);

            if (lFile.exists()) { // only load if the file exists
                BufferedReader br = new BufferedReader(new FileReader(lFile));

                // Loops through each line of the file and adds it to prompts var.
                for (String line = br.readLine(); line != null; line = br.readLine()) {
                    prompts.add(line);
                }

                br.close();
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
            System.out.println("An IOException occurred while reading prompts from file! (see stack trace above)");
            System.out.println("Exiting...");
            System.exit(1);
        }
    }

    /**
     * Gets the current size of the players list. (Synchronized for multi-threading)
     *
     * @return size of players list
     */
    public synchronized int getPlayersSize() { return players.size(); }

    /**
     * Add a finished player (that entered their answer correctly) to the finishers list. (Synchronized for multi-threading)
     *
     * @param gch handler of the player/finisher to add
     */
    public synchronized void addToFinishers(GCHandler gch) {
        finishers.add(gch);
    }

    /**
     * Gets the current size of the finishers list. (Synchronized for multi-threading)
     *
     * @return size of finishers list
     */
    public synchronized int getFinishersSize() { return finishers.size(); }

    /**
     * Gets the rank of the given finisher (via its GCHandler)
     *
     * @return rank of finisher
     */
    public synchronized int getRank(GCHandler gch) {
        return finishers.indexOf(gch);
    }

    /**
     * Gets a boolean on whether the current game is finished. (Synchronized for multi-threading)
     *
     * @return true if current game is finished
     */
    public synchronized boolean getFinished() {
        return finished;
    }

    /**
     * Sets the boolean/game state on whether the current game has finished. (Synchronized for multi-threading)
     *
     * @param finished boolean value to set this.finished to
     */
    public synchronized void setFinished(boolean finished) {
        this.finished = finished;
    }

    /**
     * Gets a boolean on whether the current game's time is up. (Synchronized for multi-threading)
     *
     * @return true if current game's time is up
     */
    public synchronized boolean getTimesUp() {
        return timesUp;
    }

    /**
     * Sets the boolean/game state on whether time is up for the current game. (Synchronized for multi-threading)
     *
     * @param timesUp boolean value to set this.timesUp to
     */
    public synchronized void setTimesUp(boolean timesUp) {
        this.timesUp = timesUp;
    }

    /**
     * Start of execution for GameServer.
     *
     * @param args args from CLI
     */
    public static void main(String[] args) {

        if (args.length != 3) {
            System.out.println("Expected arguments: <leaderHost(String)> <leaderPort(int)> <clientPort(int)>");
            System.exit(1);
        }

        String leaderHost = "localhost"; // default leader host
        int leaderPort = 8080; // default leader port
        int clientPort = 8040; // default client port

        // Parsing args for port numbers
        try {
            leaderHost = args[0];
            leaderPort = Integer.parseInt(args[1]);
            clientPort = Integer.parseInt(args[2]);
        } catch (NumberFormatException nfe) {
            System.out.println("Port must be an integer.");
            System.exit(2);
        }

        // Creates a new GameServer instance, passing in the args
        GameServer gs = new GameServer(leaderHost, leaderPort, clientPort);
    }
}
