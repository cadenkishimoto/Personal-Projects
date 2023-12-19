package leader;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * This class acts as the leader server for clients to connect to.  Client and Game Server Listeners are created
 * to listen for incoming connections (see LCListener.java and LGListener.java), which create Handlers for each
 * individual connection (see LCHandler.java and LGHandler.java).  All Listeners and Handlers operate in their own
 * threads to perform concurrently.
 *
 * @author Caden Kishimoto
 */
public class LeaderServer {

    // LeaderServer variables
    private final static String usersFilename = "users.txt"; // name of users file
    private HashMap<String, UserInfo> users; // collection of registered users; key = username; value = UserInfo
    private final ArrayList<LGHandler> gameServerList; // list of currently connected game servers

    /**
     * Constructor for LeaderServer. Includes init. vars and starting client/game server listeners.
     *
     * @param clientPort port num for incoming clients
     * @param gameServerPort port num for incoming game servers
     */
    public LeaderServer(int clientPort, int gameServerPort) {
        // Init. gameServerList
        gameServerList = new ArrayList<>();

        // Creating and starting the client/game server listeners (as threads)
        try {
            LCListener lcl = new LCListener(this, clientPort);
            LGListener lgl = new LGListener(this, gameServerPort);
            lcl.start();
            lgl.start();
        } catch (Exception e) {
            System.out.println("Unable to set up client/game server handlers!");
            System.exit(-1);
        }

        System.out.println("Leader server started!");
    }

    /**
     * Checks if a user is in the system.
     *
     * @param username username to check
     * @return true if the user exists
     */
    public boolean hasUser(String username) {
        if (users == null) { // loads users from file if not yet loaded
            loadUsers();
        }

        return users.containsKey(username);
    }

    /**
     * Adds a new user to the system. (Synchronized for multi-threading)
     *
     * @param username username of new user
     * @param password password of new user
     */
    public synchronized void addUser(String username, String password) {
        if (users == null) { // loads users from file if not yet loaded
            loadUsers();
        }

        UserInfo newUser = new UserInfo(username, password, 0);
        users.put(username, newUser);
        saveUsers(); // a change was made, saves users' info to file
    }

    /**
     * Checks if the given password is correct for a given username.
     *
     * @param username username of user
     * @param password password to check
     * @return true if the passwords are equal
     */
    public boolean checkPassword(String username, String password) {
        if (users == null) { // loads users from file if not yet loaded
            loadUsers();
        }

        for (String uName : users.keySet()) {
            if (uName.equals(username)) { // once username is found
                return users.get(uName).password.equals(password); // check the passwords
            }
        }
        return false; // shouldn't execute since LCHandler calls hasUser() first (to verify user exists)
    }

    /**
     * Adds a game server to the leader's current list of "connected" game servers. (Synchronized for multi-threading)
     *
     * @param gs game server being added to the list
     */
    public synchronized void addGameServer(LGHandler gs) {
        gameServerList.add(gs);
    }

    /**
     * Finds an available game server for a client to join. Always guarantees that a client will join the most populated,
     * available game server. (Synchronized for multi-threading)
     *
     * @return String of host:port of available game server; Empty String if no servers available
     */
    public synchronized String findGame() {
        if (gameServerList.isEmpty()) { // empty string if no servers in list
            return "";
        }

        LGHandler firstServ = gameServerList.get(0); // marking the first game server to be checked (to avoid looping in a cycle)
        do { // for each GS being looked at
            if (gameServerList.get(0).hasClosed()) { // if conn has closed with the GS
                gameServerList.remove(0); // remove it from the list
                continue;
            }

            if (!gameServerList.get(0).isAvailable()) { // if not available for a player to join (game full or in-progress)
                LGHandler temp = gameServerList.get(0);
                gameServerList.remove(0); // temporarily remove it from the list
                gameServerList.add(temp); // re-add it to the end of the list
            } else { // GS is available for a player to join
                return gameServerList.get(0).getHost().substring(1) + ":" + gameServerList.get(0).getClientPort(); // return its host and clientPort
            }
        } while (!gameServerList.isEmpty() && !gameServerList.get(0).equals(firstServ)); // checks for empty list and avoids a cycle

        return ""; // if this line is reached (loop breaks), then there are no available servers
    }

    /**
     * Gets the current leaderboard with the given user shown at the top, followed by other users by decreasing win
     * count. (Synchronized for multi-threading)
     *
     * @param username username of user to show at the top
     * @return String of current leaderboard info
     */
    public synchronized String getLeaderboard(String username) {
        if (users == null) { // loads users from file if not yet loaded
            loadUsers();
        }

        StringBuilder sb = new StringBuilder();

        sb.append("\nYour wins:\n");
        sb.append(users.get(username).wins).append(" - ").append(username).append("\n");

        ArrayList<UserInfo> userList = new ArrayList<>(); // creating a list for users' info
        for (UserInfo ui : users.values()) { // adds users' info to list
            userList.add(ui);
        }
        userList.sort((a, b) -> b.wins - a.wins); // sorting by wins in decreasing order

        sb.append("\nFull Leaderboard: (by # of wins)\n"); // adding data from the sorted list
        for (UserInfo ui : userList) {
            sb.append(ui.wins).append(" - ").append(ui.username).append("\n");
        }

        return sb.toString();
    }

    /**
     * Adds a win of the given username to the system. (Synchronized for multi-threading)
     *
     * @param username username of winning user
     */
    public synchronized void addWin(String username) {
        if (users == null) { // loads users from file if not yet loaded
            loadUsers();
        }
        users.get(username).wonGame();
        saveUsers(); // a change was made, save users' info to file
    }

    /**
     * Load users from file. (Synchronized for multi-threading)
     */
    public synchronized void loadUsers() {
        users = new HashMap<>(); // initializes users var.

        try {
            File lFile = new File(usersFilename);

            if (lFile.exists()) { // only load if the file exists
                BufferedReader br = new BufferedReader(new FileReader(lFile));

                // Loops through each line of the file, building a new UserInfo and adding it to users HashMap
                for (String line = br.readLine(); line != null; line = br.readLine()) {
                    String[] userArr = line.split("#\t#");
                    String username = userArr[0];
                    String password = userArr[1];
                    int wins = Integer.parseInt(userArr[2]);
                    UserInfo newUser = new UserInfo(username, password, wins);
                    users.put(username, newUser);
                }

                br.close();
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
            System.out.println("An IOException occurred while reading leaderboard from file! (see stack trace above)");
            System.out.println("Exiting...");
            System.exit(3);
        } catch (NumberFormatException nfe) {
            nfe.printStackTrace();
            System.out.println("A score in the file was not an integer! (see stack trace above)");
            System.out.println("Exiting...");
            System.exit(2);
        }
    }

    /**
     * Save users to file. (Synchronized for multi-threading)
     */
    public synchronized void saveUsers() {
        try {
            File uFile = new File(usersFilename);

            if (!uFile.exists()) { // if the file doesn't yet exist
                uFile.createNewFile(); // create the file
            }

            BufferedWriter bw = new BufferedWriter(new FileWriter(uFile));

            // Loop through each UserInfo in users HashMap and write its info as a separate line
            for (UserInfo ui : users.values()) {
                bw.write(ui.username + "#\t#" + ui.password + "#\t#" + ui.wins + "\n");
            }

            bw.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
            System.out.println("An IOException occurred while reading leaderboard from file! (see stack trace above)");
            System.out.println("Exiting...");
            System.exit(3);
        }
    }

    /**
     * Start of execution for LeaderServer.
     *
     * @param args args from CLI
     */
    public static void main(String[] args) {
        // Checking for num of args
        if (args.length != 2) {
            System.out.println("Expected arguments: <clientPort(int)> <gameServerPort(int)>");
            System.exit(1);
        }

        int clientPort = 8000; // default client port
        int gameServerPort = 8080; // default node port

        // Parsing args for port numbers
        try {
            clientPort = Integer.parseInt(args[0]);
            gameServerPort = Integer.parseInt(args[1]);
        } catch (NumberFormatException nfe) {
            System.out.println("Port must be an integer.");
            System.exit(2);
        }

        // Creating new leader instance
        LeaderServer ls = new LeaderServer(clientPort, gameServerPort);
    }

    /**
     * Class that stores a client's username, password, and number of wins.
     */
    private static class UserInfo {

        // UserInfo variables
        private final String username; // client's username
        private final String password; // client's password
        private int wins; // # of client's wins

        /**
         * Constructor for UserInfo to init. vars
         *
         * @param username username of user
         * @param password password of user
         * @param wins wins of user
         */
        public UserInfo(String username, String password, int wins) {
            this.username = username;
            this.password = password;
            this.wins = wins;
        }

        /**
         * Increments number of games won for user.
         */
        public void wonGame() {
            wins++;
        }
    }
}
