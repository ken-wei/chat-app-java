package com.a1;

import com.a1.base.Base;
import com.a1.base.Packets;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;

import com.beust.jcommander.*;

public class Client
{
  private ServerHandler server;
  private String clientID = "";
  private String roomName = "";
  private boolean createRoomReq = false;
  private boolean quitCommand = false;
  private String roomReq = "";
  private MessageReceiver receiver;
  private boolean receiverThreadAlive = false;
  private MessageSender sender;
  private boolean whoCommandSent = false;
  private final int DEFAULT_PORT = 4444;

  @Parameter(names={"-p"}, description = "Port Number of the server host")
  private int portNumber = 0;

  @Parameter(names={"-i"}, description = "Outgoing port number to make connections to other peers")
  private int outgoingPort = -1;

  // Argument parsing using JCommander for required field (hostname) and port number
  private void argumentParsing(final String[] args) throws IOException {
    JCommander.newBuilder().addObject(this).build().parse(args);
    if (portNumber < 0 || portNumber > 65535) { // use default port of 4444
      portNumber = DEFAULT_PORT;
    }
    if (outgoingPort < 0 || outgoingPort > 65535) { // Let os choose the port
      outgoingPort = -1;
    }
  }

  // Alternative custom method - if InetAddress.getLocalHost() does not work
  public String getLocalAddress() throws UnknownHostException {
    try {
      // interfaces
      Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

      InetAddress addr = null;

        while (interfaces.hasMoreElements()) {
          Enumeration<InetAddress> iplist = interfaces.nextElement().getInetAddresses();

          while (iplist.hasMoreElements()) {
            InetAddress ad = iplist.nextElement();
            byte bs[] = ad.getAddress();
            if (bs.length == 4 && bs[0] != 127) {
              addr = ad;
              break;
            }
          }
        }

        if (addr != null) {
          return addr.getHostAddress();
        }

    }catch (SocketException e)  {}
    return InetAddress.getLocalHost().getHostAddress();
  }

  public static void main( String[] args ) throws Exception
  {
    final Client instance = new Client();
    try {
      instance.argumentParsing(args);
    } catch (ParameterException e ) { // Invalid argument passed
      System.out.println("Run the program as: java -jar chatpeer.jar [-p port] [-i port]");
      System.exit(0);
    }

    // Creates the threads to send and receive message
    try {
      // Creating the server thread and the message input thread
      instance.createThreads(instance);
    } catch (Exception e) {
      System.out.println(e.getMessage());
    }
  }

  // Creating threads for each MessageReceiver and MessageSender
  public void createThreads(Client client) throws Exception {
    // Server of this peer
    client.server = new ServerHandler(portNumber);
    client.server.start();
    clientID = client.server.address;

    // If the address found was loopback address, search for the ipv4 address
    if (clientID.substring(0,3).equals("127")) {
      String searchedAddress = getLocalAddress();
      // No address found
      if (!searchedAddress.substring(0,3).equals("127")) {
        client.server.address = searchedAddress + ":" + client.server.port;
        client.server.localAddress = searchedAddress;
        clientID = client.server.address;
      }
    }

    // Message sender (for remote peer and accept standard input from user)
    client.sender = new MessageSender(client);
    client.sender.start();
  }

  // Used for receiving JSON Packets from remote peer
  class MessageReceiver extends Thread {
    private Socket socket;
    private DataInputStream inFromServer;
    private boolean connectionAlive;
    private Client instance;

    public MessageReceiver(Client instance) throws IOException {
      this.instance = instance;
      this.connectionAlive = false;
    }

    // Reset the state back to starting state where user connects as a peer
    public void disconnectFromPeer() {
      this.instance.sender.peerConnectionAlive = false;
      this.instance.server.peerConnectionAlive = false;
      this.instance.sender.closeSocket();
      roomName = "";
      clientID = this.instance.server.address;
    }

    // Update socket information
    public void updateSocket(Socket socket) throws IOException {
      this.socket = socket;
      this.inFromServer = new DataInputStream(socket.getInputStream());
      this.connectionAlive = true;
    }

    // Closing sockets and input streams
    public void close() {
      try {
        socket.close();
        inFromServer.close();
      } catch (IOException e) {
        System.out.println(e.getMessage());
      }
    }

    /** Client side Join Room protocol */
    private void joinRoomProtocol(String former, String identity, String roomid) {
      // Represent this client
      if (identity.equals(clientID)) {

        // Client quits the room
        if (roomid.equals("")) {

          if (quitCommand) { // Quit command issued by the user
            disconnectFromPeer();
            close();
            this.instance.server.remoteRoom = "";
            return;
          }

          if (former.equals(roomid)) { // Invalid room
            System.out.println("The requested room is invalid or non existent.");
          } else {
            System.out.println(identity + " leaves " + roomName);
          }

          // Update room name
          roomName = roomid;
          this.instance.server.remoteRoom = roomid;
          return;
        }

        // Invalid requested room
        if (former.equals(roomid)) {
          System.out.println("The requested room is invalid or non existent.");
        } else { // Update new room name
          roomName = roomid;
          this.instance.server.remoteRoom = roomid;
          if (former.equals("")) {
            System.out.println(identity + " moved to " + roomid);
          } else {
            System.out.println(identity + " moved from " + former + " to " + roomid);
          }
        }
      } else { // stdout from receiving packets of other clients
        System.out.println();
        if (roomid.equals("")) { // Other client leaves the room
          System.out.println(identity + " leaves " + roomName);
        } else if (!former.equals(roomid)) { // Other client move to a new room
          if (former.equals("")) {
            System.out.println(identity + " moved to " + roomid);
          } else {
            System.out.println(identity + " moved from " + former + " to " + roomid);
          }
        }
      }
    }

    /** Client side Room Contents message */
    private void roomContents(String roomid, ArrayList<String> identities) {
      String clientList = identities.toString().replaceAll("[\\[\\],]", "");
      System.out.println(roomid + " contains " + clientList);
    }

    /** Client side Room List message */
    private void roomList(ArrayList<Packets.RoomList.RoomDetails> roomList) {
      for (Packets.RoomList.RoomDetails r: roomList) {
        System.out.println(r.roomid + ": " + r.count + " guests");
      }
    }

    /** Client side list neighbors */
    private void listNeighbors(ArrayList<String> neighbors) {
      System.out.print("Neighbors list: ");
      for (String n : neighbors) {
        System.out.print(n + " ");
      }
      System.out.println();
    }

    /** Handle packets sent from remote peer to client */
    private void handlePacket(String msg) {
      try {
        String command = Packets.handleJsonS2C(msg);
        boolean printedIdentity = false;
        switch(command) {
          case "RoomChange":
            Packets.RoomChange rcPacket = (Packets.RoomChange) Packets.deserializeS2C(msg);
            joinRoomProtocol(rcPacket.former, rcPacket.identity, rcPacket.roomid);
            break;

          case "RoomContents":
            Packets.RoomContents contPacket = (Packets.RoomContents) Packets.deserializeS2C(msg);
            roomContents(contPacket.roomid, contPacket.identities);
            whoCommandSent = false;
            break;

          case "RoomList":
            Packets.RoomList roomListPacket = (Packets.RoomList) Packets.deserializeS2C(msg);
            roomList(roomListPacket.rooms);
            break;

          case "Message2C":
            Packets.Message2C msgPacket = (Packets.Message2C) Packets.deserializeS2C(msg);
            if (!msgPacket.identity.equals(clientID)) {
              System.out.println();
            }
            System.out.println(msgPacket.identity + ": " + msgPacket.content);
            break;

          case "ShoutMessage2C":
            Packets.ShoutMessage2C shoutPacket = (Packets.ShoutMessage2C) Packets.deserializeS2C(msg);
            // Peer server process the shout message
            printedIdentity = this.instance.server.shoutProtocol(null, shoutPacket.identity,
                    shoutPacket.timestamp, shoutPacket.content);
            break;

          case "Neighbors":
            Packets.Neighbors neighborsPacket = (Packets.Neighbors) Packets.deserializeS2C(msg);
            this.listNeighbors(neighborsPacket.neighbors);
            break;

          default:
            break;
        }
        if (!printedIdentity) {
          System.out.print("[" + roomName + "] " + clientID + "> ");
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    // Thread running method
    public void run() {
      try {
        String newChatMessage;
        while (connectionAlive) {
          if ((newChatMessage = inFromServer.readUTF()) != null) {
            handlePacket(newChatMessage);
          } else {
            connectionAlive = false;
          }
        }
      } catch (Exception e) {
        receiverThreadAlive = false;

        // Connection lost to the peer socket or being kicked
        if (!quitCommand) {
          this.instance.sender.closeSocket();
          this.instance.sender.peerConnectionAlive = false;
          this.instance.server.peerConnectionAlive = false;
          System.out.println("\nYou have been kicked or connection lost to peer.");
          clientID = this.instance.server.address;
          roomName = "";
          System.out.print("[" + roomName + "] " + clientID + "> ");
        }
        quitCommand = false;
      }
      if (!connectionAlive) { // Close streams and sockets
        this.close();
      }
    }
  }

  /**
   *  MessageSender Used for sending JSON packets to the remote peer
   */
  class MessageSender extends Thread {
    private boolean connectionAlive;
    private boolean peerConnectionAlive;
    private Socket socket;
    private BufferedReader inFromUser;
    private DataInputStream is;
    private DataOutputStream os;
    private Client instance;

    public MessageSender(Client instance) {
      this.inFromUser = new BufferedReader(new InputStreamReader(System.in));
      this.is = new DataInputStream(System.in);
      this.peerConnectionAlive = false;
      this.connectionAlive = true;
      this.instance = instance;
    }

    // Closing connections, sockets, streams
    public void close() {
      try {
        socket.close();
        inFromUser.close();
        os.close();
      } catch (IOException e) {
        System.out.println(e.getMessage());
      }
    }

    // Closing sockets and output stream
    public void closeSocket() {
      try {
        socket.close();
        os.close();
//        System.out.println("closed!");
      } catch (IOException e) {
        System.out.println(e.getMessage());
      }
    }

    /** Connect Command to create a TCP connection to a remote peer
     *    Create socket between peers, setup connection and create output stream
     */
    private boolean connect(String peerAddress, int peerPort, String local, int localPort ) {
      try {
        int remotePeerPort = peerPort > 0 ? peerPort : this.instance.server.PORT;

        // Create socket, bind, connect to remote peer
        this.socket = new Socket();
        this.socket.setReuseAddress(true);
        if (localPort > 0) {
          this.socket.bind(new InetSocketAddress(local, localPort));
        } else {
          this.socket.bind(new InetSocketAddress(local, 0));
        }
        this.socket.connect(new InetSocketAddress(peerAddress, remotePeerPort));

        // Remove user of the peer from room (if joined any room)
        if (!roomName.equals("")) {
          this.instance.server.joinRoomProtocol(null, "");
        }

        // Update the connection status and create OutputStream using the socket
        this.peerConnectionAlive = true;
        this.os = new DataOutputStream(socket.getOutputStream());
        roomName = "";
        clientID =  local + ":" + this.socket.getLocalPort();

        if (!receiverThreadAlive) { // Create the thread of MessageReceiver
          this.instance.receiver = new MessageReceiver(this.instance);
        }

        // Starts the receiver thread (Receives messages from peer server)
        this.instance.receiver.updateSocket(this.socket);
        this.instance.receiver.start();

        // Update server's remote connection settings
        this.instance.server.currentRoom = "";
        this.instance.server.updateRemoteConnection(this.os, this.socket);
        this.instance.server.peerConnectionAlive = true;
        this.instance.server.remotePeerAddress = peerAddress + ":" + remotePeerPort;
        this.instance.server.currentClientID = clientID;

        System.out.print("[" + roomName + "] " + clientID + "> ");
        return true;

      } catch (IOException e) {
        System.out.println("Connection failed! Please retry with another address.");
        System.out.print("[" + roomName + "] " + clientID + "> ");
        return false;
      }
    }

    public void run() {
      String msg;
      System.out.print("[" + roomName + "] " + clientID + "> ");
      while (connectionAlive) {
        // Used for printing roomName and clientId if #who is used
        if (whoCommandSent) {
          System.out.print("[" + roomName + "] " + clientID + "> ");
          whoCommandSent = false;
        }

        try {
          // Reading input from user's stdin
          if (((msg = inFromUser.readLine()) != null)) {
            String[] temp = msg.split(" ");
            if (temp[0].length() > 0 && temp[0].charAt(0) == '#') {
              String userMsg = "";
              if (temp.length > 1) {
                userMsg = msg.substring(temp[0].length() + 1);
              }
              String packet = "";

              // Process local command or send a remote peer command
              if (temp[0].equals("#help") || temp[0].equals("#searchnetwork") ||
                      (!peerConnectionAlive && !temp[0].equals("#connect"))) {
                packet = processLocalCommand(temp[0].substring(1), userMsg);
              } else  {
                packet = processPacketToSend(temp[0].substring(1), userMsg);
              }

              // Invalid command
              if (packet.equals("")) {
                if (peerConnectionAlive && !temp[0].equals("#connect") && !temp[0].equals("#help") &&
                        !temp[0].equals("#searchnetwork")) {
                  System.out.println("Please enter a valid command.");
                  System.out.print("[" + roomName + "] " + clientID + "> ");
                }
              } else { // Valid command send JSON packet
                os.writeUTF(packet);
                os.flush();
              }

              // thread sleeps if #who and #quit is sent to wait a response
              if (temp[0].equals("#quit") || temp[0].equals("#who")) {
                // Timeout period
                Thread.sleep(1000);
              }
            } else { // Text Messages sent when not in a room are ignored
              if (!roomName.equals("")) {
                if (!peerConnectionAlive) { // Process local message command
                  processLocalCommand("message", msg);
                } else { // Message JSON packet from user to remote peer
                  Packets.Message newMsg = new Packets.Message(msg);
                  String sMsg = Packets.serialize(newMsg);
                  os.writeUTF(sMsg);
                  os.flush();
                }
              } else {
                System.out.print("[" + roomName + "] " + clientID + "> ");
              }
            }
          } else {
            connectionAlive = false;
          }
        } catch (Exception e) {
          peerConnectionAlive = false;
          this.instance.server.peerConnectionAlive = false;
          clientID = this.instance.server.address;
          roomName = "";
        }
      }
      this.close();
    }

    /** Process local command for the local peer */
    private String processLocalCommand(String command, String commandDetails) {
      try {
        switch (command) {
          case "createroom":
            this.instance.server.createRoomProtocol(commandDetails);
            break;
          case "join":
            this.instance.server.joinRoomProtocol(null, commandDetails);
            break;
          case "who":
            this.instance.server.roomContentProtocol(null, commandDetails);
            break;
          case "kick":
            this.instance.server.kickCommand(commandDetails);
            break;
          case "message":
            this.instance.server.handleMessageFromClient(null, commandDetails);
            break;
          case "shout":
            String timeStamp = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
            this.instance.server.shoutProtocol(null, this.instance.server.address, timeStamp, commandDetails);
            break;
          case "list":
            this.instance.server.roomListProtocol(null, "");
            break;
          case "delete":
            this.instance.server.deleteRoomProtocol(null, commandDetails);
            break;
          case "listneighbors":
            this.instance.server.listNeighbors(null);
            break;
          case "searchnetwork":
            this.instance.server.searchNetwork();
            break;
          case "help":
            printHelp();
            break;
          case "quit": // Quit when not connecting to a remote peer will back to start state
            if (!this.instance.server.currentRoom.equals("")) {
              this.instance.server.joinRoomProtocol(null, "");
            }
            break;
          default:
            System.out.println("Please enter a valid command.");
            break;
        }
        roomName = this.instance.server.currentRoom;
        if (!command.equals("shout")) {
          System.out.print("[" + roomName + "] " + clientID + "> " );
        }
      } catch (Exception e) {
      }
      return "";
    }

    // Process command and messages into JSON Object string to send
    private String processPacketToSend(String command, String commandDetails) {
      Packets.ToServer packet = null;
      String jsonPacket = "";
//      System.out.println(commandDetails);
      Boolean validPacket = true;

      try {
        switch (command) {
          case "join":
            packet = new Packets.Join(commandDetails);
            break;
          case "who":
            packet = new Packets.Who(commandDetails);
            whoCommandSent = true;
            break;
          case "list":
            packet = new Packets.List();
            break;
          case "listneighbors":
            packet = new Packets.ListNeighbors();
            break;
          case "message":
            packet = new Packets.Message(commandDetails);
            break;
          case "shout":
            String timeStamp = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
            packet = new Packets.Shout(timeStamp, commandDetails);
            break;
          case "quit":
            packet = new Packets.Quit();
            quitCommand = true;
            break;
          case "connect":
            // User has to quit before starting a connection
            if (peerConnectionAlive) {
              System.out.println("Please quit before starting a new connection.");
              System.out.print("[" + roomName + "] " + clientID + "> ");
              validPacket = false;
              break;
            }

            validPacket = validateConnectCommand(commandDetails);

            // Create a host change packet to send to the remote server
            packet = new Packets.HostChange(this.instance.server.address);
            break;
          default:
            return "";
        }
        if (validPacket) {
          jsonPacket = Packets.serialize(packet);
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
      return jsonPacket;
    }

    private boolean validateConnectCommand(String commandDetails) {
      String[] details = commandDetails.split(" ");
      String peerAddress = details[0];
      boolean validPacket = true;
      // Validation of the input of #connect command
      boolean correctPeerAddress = peerAddress.split(":").length == 2
              || peerAddress.split(":").length == 1; // 0 to 65.535 ports
      boolean correctPortNo = true;
      boolean correctOutgoingPort = false;
      if (correctPeerAddress) { // Validate remote peer address and outgoing port
        // Validate remote peer
        if (peerAddress.split(":").length == 2 ) {
          String portNo = peerAddress.split(":")[1];
          // Cannot connect to itself
          correctPeerAddress = (peerAddress.split(":")[0].equals("localhost") &&
                  Integer.parseInt(portNo) == this.instance.server.port) ? false : true;
          correctPortNo = portNo.matches("[0-9]+") &&
                  Integer.parseInt(portNo) >= 0 && Integer.parseInt(portNo) <= 65535;
        } else { // Cannot connect to itself
          correctPortNo = !((peerAddress.split(":")[0].equals("localhost") ||
                  peerAddress.split(":")[0].equals(this.instance.server.localAddress)) &&
                  this.instance.server.port == 4444);
        }

        // Validate outgoing port
        if (details.length > 1){
          correctOutgoingPort = details[1].matches("[0-9]+") &&
                  Integer.parseInt(details[1]) >= 0 && Integer.parseInt(details[1]) <= 65535;
        } else {
          correctOutgoingPort = true;
        }
      }

      // Valid peeraddress, not connecting to itself (by default connected), correct port numbers
      boolean validFields = correctPeerAddress && correctPortNo && correctOutgoingPort;
      if (validFields && !peerAddress.equals(this.instance.server.address)) {

        // Provided specific outgoing port
        String peerAdd = details[0].split(":")[0];
        int remotePeerPort = -1;

        // Get the local address from the server of this peer
        if (details[0].split(":")[0].equals("localhost")) {
          peerAdd = this.instance.server.localAddress;
        }

        // Remote peer port has been provided
        if (peerAddress.split(":").length != 1) {
          remotePeerPort = Integer.parseInt(details[0].split(":")[1]);
        }

        // Connect to the remote peer using all provided fields
        if (details.length > 1) {
          validPacket = this.connect(peerAdd, remotePeerPort,
                  this.instance.server.localAddress, Integer.parseInt(details[1]));
        } else {
          validPacket = this.connect(peerAdd, remotePeerPort, this.instance.server.localAddress, outgoingPort);
        }
      } else { // Invalid address found
        System.out.println("Please enter a valid address for connection.");
        System.out.print("[" + roomName + "] " + clientID + "> ");
        validPacket = false;
      }
      return validPacket;
    }

    private void printHelp() {
      System.out.println("#help                - list this information");
      System.out.println("#connect IP[:port] [local port] - connect to another peer");
      System.out.println("#quit                - goes back to start state (when not connected to a remote peer)");
      System.out.println("                     - disconnect from a peer (connected to a remote peer) ");
      System.out.println("#who roomName        - listing who in specified room");
      System.out.println("#join roomName       - join specified room");
      System.out.println("#list                - listing all the rooms");
      System.out.println("#kick IP:port        - kick someone from the server (local command)");
      System.out.println("#listneighbors       - listing all the neighbors of the current peer");
      System.out.println("#searchnetwork       - get all peers and available rooms in the peer network");
      System.out.println("#shout message       - shouts a message to all peers in the network");
    }
  }

}
