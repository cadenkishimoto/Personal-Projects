# README.md
## TypeRacing Personal Project

#### Created by Caden Kishimoto

### Video Link: 

## Table of Contents
- Project Description
- Class Structure
  - Leader Server (L)
  - Game Server (G)
  - Client (C)
- Additional Files
- General Communication
- Handlers and Listeners Format: XX[Handler/Listener]
- How to Run the Program

## Project Description
Hi there! Welcome to my personal project: TypeRacing! I thought it would be a cool project to work on/start from scratch, 
and I got the inspiration for this project shortly after I finished my Distributed Systems course at Arizona State 
University - where I gained further experience on threading, concurrency, serialization, and networking. My idea is 
based off https://play.typeracer.com/, an online game where players race to type a prompt. As of creating this README 
(12/19/2023), I will say that this has been my most complex (and fun!) project to date - and I'm extremely proud to 
show this system in action!

To better understand how the system works, I decided to break it down into three main components: Leader Server (L), 
Game Server (G/GS), and Client (C). Each component has its own package that contains files implementing its functionality. 
As a general control flow, a client will first connect to a leader server - which provides services like signing-up, 
logging in, viewing personal stats/leaderboard, and matchmaking to play a game. When matchmaking, the leader will send 
the connection info of an available game server to the client, which will allow it to connect. Each game server runs in 
its own large loop, hosting one game at a time - with up to 5 players/clients. 

To see game rules and a full list of design requirements, please view the "TypeRacing Requirements Doc" that I've created!

I also used a custom JSON protocol for this project, which is detailed in the "TypeRacing JSON Protocol" document.

Lastly (and for reference), this project was built using JDK 17 and Gradle 8.2. Thanks for reading, and I hope you enjoy! :D

## Class Structure
In total, there are 3 packages, 11 Java files, and 14 classes (3 of the classes are inner classes of another class).
Here are the classes for each package:

### Leader Server (L):
- LeaderServer (with inner class UserInfo)
- LGListener
- LCListener
- LGHandler
- LCHandler

For context, LeaderServer creates one LCListener and one LGListener (each ran in its own thread). These listeners listen 
for incoming C/G connection requests, which then create LCHandlers and LGHandlers to handle each respective connection 
(also ran in its own thread). UserInfo acts as a helper class for LeaderServer, in which it stores login/win info for 
each user/player/client.


### Game Server (G):
- GameServer
- CountdownBroadcast
- GLHandler
- GCHandler (with inner class Broadcaster)

For context, GameServer connects with L and creates a GLHandler (ran in its own thread). After the GLHandler thread is 
started, it will loop to listen for incoming C connection requests (each getting its own GCHandler/thread). Once one 
C connection is established, it will create/start a CountdownBroadcast (a TimerTask) counting down the start of the game.
(Other clients (C) can join during the starting countdown.) Once the countdown has finished, it will start the game in 
GameServer - which will create another CountdownBroadcast that does a countdown for game time. (Note: CountdownBroadcasts 
extend TimerTasks, which are also ran in their own threads.) Broadcaster acts as a helper class for GCHandler, in which 
it will broadcast messages to its respective C.

### Client (C):
- Client
- GameClient (with inner class Sender)

For context, control is started in Client, but a new GameClient is created for every new game that the player/client 
joins. Client mostly deals with L communication, while GameClient deals with G communication. Sender acts as a helper 
class for GameClient, in which it sends inputted answers to G.

## Additional Files
In addition to the files/classes above, there are also 2 additional files: prompts.txt and users.txt. Both files are 
used to keep data persistent between server restarts.

prompts.txt holds all of the prompts for the game (which are sentences describing Lorem Ipsum, retrieved from 
https://www.lipsum.com/) - with each line containing a prompt

users.txt holds user data on each line - with each line containing a user's username, password, and number of wins

## General Communication
This is also detailed in the "TypeRacer Project Structure Visual", but here is the general communication for individual 
Socket connections:

LCHandler <--> Client

GCHandler <--> GameClient

LGHandler <--> GLHandler

## Handlers and Listeners Format: XX[Handler/Listener]
This is also detailed in the "TypeRacer Project Structure Visual", but here is the format for handler/listener naming:

- 1st Letter is the Package Group it belongs to
- 2nd Letter is the Package Group that it's servicing (handling/listening for)

#### Example:
LGHandler is a file of L that handles a connection with G

GLHandler is a file of G that handles a connection with L

## How to Run the Program
I chose Gradle as my build environment for this project, which makes things simple and easy to run via tasks. Please 
start L first before attempting to run C or G, as they will throw a ConnectException otherwise. (C and G can be started 
at any time after.)

For running L, CLI arguments of a clientPort and gameServerPort can be entered (clientPort is the port number listening 
for incoming C connection requests, gameServerPort is the port number listening for incoming G connection requests).

For running G, CLI arguments of a leaderHost, a leaderPort, and a clientPort can be entered (leaderHost is the host that 
G is connecting to, leaderPort is the port number that G is connecting to, clientPort is the port number listening for 
incoming C connection requests).

For running C, CLI arguments of a leaderHost and leaderPort can be entered (leaderHost is the host that C is connecting 
to, leaderPort is the port number that C is connecting to).

Below are some default Gradle commands that can be used to run the system locally: (Default values are shown as comments in build.gradle)

- L: gradle runLeaderServer --console=plain -q
- G: gradle runGameServer --console=plain -q
- C: gradle runClient --console=plain -q

Please note: If you are running everything with the default commands (which are all ran locally), please ensure that 
a new clientPort is manually entered as an argument for every additional G instance being run.
