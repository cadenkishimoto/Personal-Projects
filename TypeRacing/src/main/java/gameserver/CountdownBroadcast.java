package gameserver;

import java.util.ArrayList;
import java.util.TimerTask;

/**
 * Acts as countdown control for both game start/end.
 *
 * @author Caden Kishimoto
 */
public class CountdownBroadcast extends TimerTask {

    // CountdownBroadcast variables
    private final GameServer gs; // game server that the countdown is associated with
    private final ArrayList<GCHandler> players; // list of players from gs
    private final ArrayList<GCHandler> finishers; // list of finishers from gs
    private final String countdownType; // "start" = start countdown, "finish" = end/finish countdown
    private final String prompt; // text prompt that players will type for the game
    private int secondsRemaining; // depends on countdownType

    /**
     * Constructor for CountdownBroadcast. Includes setting up vars.
     *
     * @param gs associated GameServer for the countdown
     * @param players players in game
     * @param finishers finishers in game
     * @param countdownType "start" = start, "finish" = end/finish
     * @param prompt text that players will type
     */
    public CountdownBroadcast(GameServer gs, ArrayList<GCHandler> players, ArrayList<GCHandler> finishers,
                              String countdownType, String prompt) {
        this.gs = gs;
        this.players = players;
        this.finishers = finishers;
        this.countdownType = countdownType;
        this.prompt = prompt;

        if (countdownType.equals("start")) { // countdown for game to "start"
            secondsRemaining = 35; // countdown is 30 seconds but set to 35 for initial broadcast
        } else { // countdown for game to end/"finish"
            secondsRemaining = 75; // game time is 60 seconds but set to 75 for initial broadcast w/ prompt
        }
    }

    /**
     * Run method for CountdownBroadcast. A message is loaded and broadcast simultaneously to players, depending on the
     * state of the instance. (Messages are specifically sent through GCHandlers, however.)
     */
    @Override
    public void run() {
        // Create an array of threads (one thread per player)
        Thread[] threads = new Thread[players.size()];

        if (countdownType.equals("start")) { // for start countdown type
            secondsRemaining -= 5; // decrement by 5

            if (secondsRemaining == 0) { // if time's up
                gs.setFinished(false); // set flag
                gs.playGame(); // start game
                this.cancel(); // cancel the TimerTask
                return;
            } else { // timer's still ticking
                // Broadcast/Thread the message to players
                for (int i = 0; i < threads.length; i++) {
                    players.get(i).getBroadcaster().setBroadcastMsg("Game starts in " + secondsRemaining + " seconds!");
                    threads[i] = new Thread(players.get(i).getBroadcaster());
                    threads[i].start();
                }
            }
        } else if (countdownType.equals("finish")) { // for finish countdown type (end of game)
            secondsRemaining -= 15; // decrement by 15

            if (secondsRemaining == 0) { // if time's up
                gs.setTimesUp(true); // set flag
                this.cancel(); // cancel the TimerTask
                return;
            } else if (secondsRemaining == 60) { // if starting countdown (first run() call)
                // Broadcast/Thread the message to players
                for (int i = 0; i < threads.length; i++) {
                    players.get(i).setPrompt(prompt);
                    players.get(i).getBroadcaster().setBroadcastMsg("\nGame start! You have 60 seconds to type: \n" + prompt);
                    threads[i] = new Thread(players.get(i).getBroadcaster());
                    threads[i].start();
                }
            } else { // timer's still ticking
                // Broadcast/Thread the message to players
                for (int i = 0; i < threads.length; i++) {
                    synchronized (finishers) { // syncing finishers
                        if (!finishers.contains(players.get(i))) { // if the player hasn't already finished, broadcast
                            players.get(i).getBroadcaster().setBroadcastMsg("Less than " + secondsRemaining + " seconds remain!");
                            threads[i] = new Thread(players.get(i).getBroadcaster());
                            threads[i].start();
                        }
                    }
                }
            }
        }

        // Joining threads (same for both countdown types)
        try {
            for (Thread t : threads) {
                if (t != null) { // making sure it's non-null (finishers do not get "Less than X seconds remain!" broadcasts)
                    t.join();
                }
            }
        } catch (InterruptedException ie) { // will continue regardless
            ie.printStackTrace();
            System.out.println("InterruptedException while waiting for threads to join.");
        }
    }
}
