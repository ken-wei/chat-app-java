package com.a1;

import com.a1.base.Packets;

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerHandler extends Thread {
    private boolean alive = false;
    public static final int PORT = 4444;
    private Integer guestID = 1;
    private List<Integer> guestIDs = new ArrayList<>();
    private List<ChatConnection> connectionList = new ArrayList<>();
    private List<ChatRoom> roomList = new ArrayList<>();
    public int port;
    public String currentRoom = "";
    public String address;
    // Store IP that has been kicked previously
    private List<String> blockedIP = new ArrayList<>();
    private Queue<Packets.ShoutMessage> recentShouts = new LinkedList<>();
    public String localAddress = "";
    // Peer remote connection
    public boolean peerConnectionAlive = false;
    public String remotePeerAddress = "";
    public String currentClientID = "";
    public String remoteRoom = "";
    private Socket socket;
    private DataOutputStream os;

    public ServerHandler(int port) throws UnknownHostException {
        // If a port was specified else use default port = 4444
        this.port = PORT;
        if (port != 0) {
            this.port = port;
        }
        this.address = InetAddress.getLocalHost().getHostAddress()+":"+this.port;
        this.localAddress = InetAddress.getLocalHost().getHostAddress();
    }

    // Remove quiting client
    private void leave(ChatConnection conn) {
        synchronized (connectionList) {
            connectionList.remove(conn);
            conn.close();
        }
    }

    // Add new joining client
    private void join(ChatConnection conn) {
        synchronized (connectionList) {
            connectionList.add(conn);
        }
    }

    // Remote peer connection
    public void updateRemoteConnection(DataOutputStream os, Socket socket){
        this.os = os;
        this.socket = socket;
    }

    // Broadcast JSON packet to all clients except ignored client
    private void broadcast(String message, ChatConnection ignored) {
        try {
            synchronized (connectionList) {
                for (ChatConnection c : connectionList) {
                    if (ignored == null || !ignored.equals(c))
                        c.sendMessage(message);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void run() {
        ServerSocket serverSocket;
        try {
            serverSocket = new ServerSocket(this.port);

            alive = true;
            while (alive) {
                Socket socket = serverSocket.accept();
                // do some stuff here with socket
                ChatConnection connection = new ChatConnection(socket);
                if (blockedIP.contains(socket.getInetAddress().getHostAddress())) {
                    socket.close();
                } else {
                    connection.start();
                    join(connection);
                }
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
            System.exit(0);
        }
    }

    // Broadcast room changes to clients for Join Room Protocol
    public void broadcastRoomChange(Boolean valid, ChatConnection con,
                                    String former, String roomid) throws UnknownHostException {
        Packets.ToClient packet = new Packets.RoomChange(InetAddress.getLocalHost().getHostAddress(), former, roomid);
        String clientName = "";
        if (con == null) {
            clientName = this.address;
        } else {
            clientName = con.socket.getInetAddress().getHostAddress() + ":" + con.socket.getPort();
        }
        try {
            // Creates valid room change message and send to all clients in the former and curr room
            if (valid && !former.equals(roomid)) {
                // Broadcast to all clients in curr room and former room.
                synchronized (connectionList) {
                    for (ChatConnection c : connectionList) {
                        if (c.equals(con)) {
                            packet = new Packets.RoomChange(clientName, former, roomid);
                            c.sendMessage(Packets.serialize(packet));
                        } else if (!c.currentRoom.equals("") &&
                                (c.currentRoom.equals(roomid) || c.currentRoom.equals(former))) {
                            packet = new Packets.RoomChange(clientName, former, roomid);
                            c.sendMessage(Packets.serialize(packet));
                        }
                    }
                }
                // Update client's current room to the room changed
                if (con != null) {
                    con.currentRoom = roomid;
                    if (!this.currentRoom.equals("") &&
                            (this.currentRoom.equals(roomid) || this.currentRoom.equals(former))) {
                        if (!former.equals("")) {
                            if (!roomid.equals("")) {
                                System.out.println("\n" + clientName + " moved from " + former + " to " + roomid);
                            } else { // leaves room
                                System.out.println("\n" + clientName + " leaves " + former);
                            }
                        } else {
                            System.out.println("\n" + clientName + " moved to " + roomid);
                        }
                        System.out.print("[" + this.currentRoom + "] " + this.address + "> " );
                    }
                } else {
                    this.currentRoom = roomid;
                    if (roomid.equals("")) {
                        System.out.println(this.address + " leaves " + former);
                    } else {
                        System.out.println(this.address + " moved to " + roomid);
                    }

                }

            } else { // Invalid requests
                // Join Room called by the server
                if (con != null) {
                    // Both former and current room are the same because of invalid room
                    packet = new Packets.RoomChange(clientName, former, former);
                    con.sendMessage(Packets.serialize(packet));
                } else {
                    System.out.println("The requested room is invalid or non existent.");
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Join Room Protocol
    public void joinRoomProtocol(ChatConnection con, String roomid) throws UnknownHostException {
        // If room is invalid or non existent
        Boolean found = false;
        String formerRoom = "";
        String clientName = "";
        if (con == null) { // User of the peer issued the join room command
            clientName = this.address;
            formerRoom = this.currentRoom;
        } else { // Remote peer
            clientName = con.socket.getInetAddress().getHostAddress() + ":" + con.socket.getPort();
            formerRoom = con.currentRoom;
        }
        synchronized(roomList) {
            for (ChatRoom cr : roomList) {
                if (cr.getRoomName().equals(roomid)) {
                    found = true;
                    // Add client to the request room change
                    cr.addClientToRoom(clientName);
                    break;
                }
            }
            // Remove client name from previous room
            if (found || roomid.equals("")) {
                for (ChatRoom cr : roomList) {
                    if (cr.getRoomName().equals(formerRoom)) {
                        found = true;
                        cr.removeClientFromRoom(clientName);
                        break;
                    }
                }
            }
        }
        // Broadcast room change if room is valid and found
        if (con == null) {
            broadcastRoomChange(found, null, formerRoom, roomid);
        } else {
            broadcastRoomChange(found, con, formerRoom, roomid);
        }
    }

    // Who Protocol - Room Contents Message
    public void roomContentProtocol(ChatConnection con, String roomid) {
        try {
            synchronized (roomList) {
                for (ChatRoom cr : roomList) {
                    // If room id found
                    if (cr.getRoomName().equals(roomid)) {
                        if (con != null) {
                            Packets.RoomContents rcPacket = new Packets.RoomContents(roomid,
                                    (ArrayList<String>) cr.getClientList());
                            con.sendMessage(Packets.serialize(rcPacket));
                        } else {
                            System.out.println(roomid + " contains "
                                    + cr.getClientList().toString().replaceAll("[\\[\\],]", ""));
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    // Room List Message
    public void roomListProtocol(ChatConnection con, String ignoredRoom) {
        ArrayList<Packets.RoomList.RoomDetails> roomDetails = new ArrayList<Packets.RoomList.RoomDetails>();
        synchronized(roomList) {
            for (ChatRoom r : roomList) {
                if (r.getRoomName().equals(ignoredRoom)) {
                    continue;
                }
                Packets.RoomList.RoomDetails room = new Packets.RoomList.RoomDetails(r.getRoomName(),
                        r.getClientCount());
                roomDetails.add(room);
            }
        }
        try {
            // Local peer (No Packet is created)
            if (con == null) {
                for (Packets.RoomList.RoomDetails r: roomDetails) {
                    System.out.println(r.roomid + ": " + r.count + " guests");

                }
            } else {
                con.sendMessage(Packets.serialize(new Packets.RoomList(roomDetails)));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    // List Neighbors Protocol - Server and peer who requested are not included
    public void listNeighbors(ChatConnection con) throws Exception {
        ArrayList<String> neighbors = new ArrayList<>();
        synchronized (connectionList) {
            for (ChatConnection c : connectionList) {
                // Not adding the server and the client that requested for neighbors list
                if (con != null) {
                    if (!c.clientID.equals(con.clientID)) {
                        neighbors.add(c.clientID);
                    }
                } else {
                    neighbors.add(c.clientID);
                }
            }
            if (peerConnectionAlive) {
                neighbors.add(this.remotePeerAddress);
            }
            if (con == null) {
                System.out.println("Neighbors List: " + neighbors.toString());
            } else {
                Packets.ToClient packet = new Packets.Neighbors(neighbors);
                con.sendMessage(Packets.serialize(packet));
            }
        }

    }

    // Create Room Protocol
    public void createRoomProtocol(String roomid) throws UnknownHostException {
        if (roomid.matches("^[a-zA-Z0-9]{3,32}$")) {
            boolean sameRoom = false;
            synchronized (roomList) {
                for (ChatRoom r : roomList) {
                    if (r.getRoomName().equals(roomid)) {
                        sameRoom = true;
                        break;
                    }
                }
            }
            // Room not found
            if (!sameRoom) {
                ChatRoom room = new ChatRoom(InetAddress.getLocalHost().getHostAddress() + ":" + port, roomid);
                roomList.add(room);
                System.out.println("Room " + roomid + " created.");
                return;
            }
        }
        System.out.println("Room " + roomid + " is invalid or already in use.");
    }

    // Delete room protocol
    public void deleteRoomProtocol(ChatConnection con, String roomid) throws Exception {
        // Temp variables to store room clients and the room
        List<String> roomClients = new ArrayList<String>();
        ChatRoom roomToDelete = new ChatRoom("","");
        boolean roomFound = false;
        synchronized (roomList) {
            // Find the room to be deleted
            for (ChatRoom r : roomList) {
                if (r.getRoomName().equals(roomid)) {
                    roomFound = true;
                    roomToDelete = r;
                    roomClients = r.getClientList();
                    break;
                }
            }
            roomList.remove(roomToDelete);
        }

        // If room found and there's clients in that chat room to be deleted
        if (roomClients.size() > 0) {
            System.out.println("Send message to delted room users");
            synchronized (connectionList) {
                for (ChatConnection c : connectionList) {
                    String clientName = c.socket.getInetAddress().getHostAddress() +
                            ":" + c.socket.getPort();
                    if (roomClients.contains(clientName)) {
                        // Treat all users of the room had sent a RoomChange message
                        Packets.ToClient packet = new Packets.RoomChange(clientName, roomid, "");
                        c.sendMessage(Packets.serialize(packet));
                    }
                }
            }
        }

        if (roomFound) {
            System.out.println(roomid + " has been successfully deleted.");
            if (roomid.equals(currentRoom)) {
                currentRoom = "";
            }
        } else {
            System.out.println(roomid + " does not exist.");
        }
    }

    // Message packet protocol - Broadcast message to client's room
    // Message packet handling
    public void handleMessageFromClient(ChatConnection con, String content) {
        String roomid = "";
        String clientName = "";
        if (con == null) {
            roomid = this.currentRoom;
            clientName = this.address;
            if (!roomid.equals("")) {
                System.out.println(this.address + ":" + content);
            }
        } else {
            roomid = con.currentRoom;
            clientName = con.socket.getInetAddress().getHostAddress() + ":" + con.socket.getPort();
            // If server's peer is in the its own room and same room with the message packet
            if (con.currentRoom.equals(this.currentRoom)) {
                System.out.println("\n" + clientName + ": " + content);
                System.out.print("[" + this.currentRoom + "] " + this.address + "> ");
            }
        }
        try {
            // Broadcast the message to all clients inside the room (From client's room)
            synchronized (connectionList) {
                for (ChatConnection c : connectionList) {
                    if (c.currentRoom.equals(roomid)) {
                        Packets.Message2C msgPacket = new Packets.Message2C(clientName, content);
                        c.sendMessage(Packets.serialize(msgPacket));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Shout Protocol with flooding method
    public boolean shoutProtocol(ChatConnection con, String identity, String timestamp, String content) throws Exception {
        String clientName = "";
        if (con != null) {
            clientName = con.socket.getInetAddress().getHostAddress() +
                    ":" + con.socket.getPort();
        } else {
            clientName = identity;
        }
        boolean duplicate = false;
        // Check if the shout packet has been arrived before (duplicated)
        synchronized (recentShouts) {
            for (Packets.ShoutMessage shout : recentShouts) {
                if (shout.timestamp.equals(timestamp) && shout.content.equals(content)
                        && shout.identity.equals(clientName)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                recentShouts.add(new Packets.ShoutMessage(clientName, timestamp, content));
            }
        }
        if (!duplicate) {
            synchronized (connectionList) {
                for (ChatConnection c : connectionList) {
                    Packets.ShoutMessage2C msgPacket = new Packets.ShoutMessage2C(clientName, timestamp, content);
                    c.sendMessage(Packets.serialize(msgPacket));
                }
            }

            // if the current user of the peer shouted
            if (!clientName.equals(this.address) && !clientName.equals(this.currentClientID)) {
                    System.out.println();
            }
            System.out.println(clientName + " shouted " + content);

            // Send the message the the remote connection peer (All peer sending the packet to the remote peer)
            if (peerConnectionAlive) {
                Packets.ShoutMessage msgPacket = new Packets.ShoutMessage(clientName, timestamp, content);
                this.os.writeUTF(Packets.serialize(msgPacket));
                System.out.print("[" + this.remoteRoom + "] " + this.socket.getInetAddress().getHostAddress() + ":"
                        + this.socket.getLocalPort() + "> " );
            } else {
                System.out.print("[" + this.currentRoom + "] " + this.address + "> " );
            }

            return true;
        }
        return true;
    }

    // Kick Protocol - Local Command
    public void kickCommand(String clientID) {
        // Extract the first part of the string
        String[] ip = clientID.split(":");
        // Add ip into blocked list
        synchronized (blockedIP) {
            blockedIP.add(ip[0]);
        }

        synchronized (connectionList) {
            for (ChatConnection c : connectionList) {
                String cID = c.socket.getInetAddress().getHostAddress() + ":" + c.socket.getPort();
                if (clientID.equals(cID)) {
                    c.close();
                    break;
                }
            }
        }
    }

    // Quit Protocol - @parameter quitCmdUsed to indicate user terminated or #quit command issued
    private void quitProtocol(ChatConnection con, boolean quitCmdUsed) {
        String clientRoom = con.currentRoom;
        List<String> clientList = new ArrayList<String>();
        String clientName = con.socket.getInetAddress().getHostAddress() + ":" + con.socket.getPort();
        synchronized (roomList) {
            for (ChatRoom r : roomList) {
                if (r.getRoomName().equals(clientRoom)) {
                    r.removeClientFromRoom(clientName);
                    clientList = r.getClientList();
                }
            }
        }
        Packets.ToClient packet = new Packets.RoomChange(clientName, clientRoom, "");
        try {
            if (quitCmdUsed) {
                con.sendMessage(Packets.serialize(packet));
            }
            leave(con);
            synchronized (connectionList) {
                for (ChatConnection c : connectionList) {
                    if (c.currentRoom.equals(clientRoom) && !c.currentRoom.equals("")) {
                        c.sendMessage(Packets.serialize(packet));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Search Network Protocol
    // Send a list neighbors to all nearby peers then using BFS
    public void searchNetwork() throws Exception {
        // Used for searching the network in BFS manner, FIFO
        Queue<String> searchList = new LinkedList<>();

        Set<String> searchedNeighbor = new HashSet<>();
        searchedNeighbor.add(this.address); // Does not search itself

        // Add all the peers of this server
        synchronized (connectionList) {
            for (ChatConnection c : connectionList) {
                searchList.add(c.clientID);
            }

            if (peerConnectionAlive) {
                searchList.add(this.remotePeerAddress);
            }
        }

        while (!searchList.isEmpty()) {
            String[] ip = searchList.remove().split(":");

            // Check if the neighbor has already being inside the searchlist
            if (!searchedNeighbor.contains(ip[0]+":"+ip[1])) {
                //"Neighbor search: "
                System.out.println(ip[0]+":"+ip[1]);
                searchedNeighbor.add(ip[0]+":"+ip[1]);
            } else {
                continue;
            }
            Socket socket = new Socket(ip[0], Integer.parseInt(ip[1]));

            // Create the outputstream to write packet
            DataOutputStream os = new DataOutputStream(socket.getOutputStream());
            boolean neighborsReceived = sendNeighborsPacket(socket, os, searchList);
            boolean roomListReceived = sendList(socket, os);

            Packets.ToServer packet = new Packets.Quit();
            os.writeUTF(Packets.serialize(packet));
            os.flush();
            os.close();
        }
    }

    // helper method - Send list packet to peers - to get list of rooms
    private boolean sendList(Socket socket, DataOutputStream os) {
        int noOfRetry = 0; // 2 times retry for sending a send packet for list of rooms
        while (true) {
            try {
                // Inputstream of the remote peer
                DataInputStream inFromServer = new DataInputStream(socket.getInputStream());

                // Create the list packet
                Packets.ToServer listPacket = new Packets.List();
                os.writeUTF(Packets.serialize(listPacket));
                os.flush();

                String[] msg = {null};
                new Thread(() -> {
                    try {
                        if ((msg[0] = inFromServer.readUTF()) != null) {
                            Packets.RoomList roomListPacket = (Packets.RoomList) Packets.deserializeS2C(msg[0]);
                            for (Packets.RoomList.RoomDetails r : roomListPacket.rooms) {
                                System.out.println(r.roomid + " " + r.count + " users");
                            }
                        }
                    } catch (IOException e) {
                    } catch (Exception e) {
                    }
                }).start();

                Thread.sleep(1000);

                if (msg[0] == null) {
                    // Return false if retries finished
                    if (noOfRetry == 2) {
                        inFromServer.close();
                        return false;
                    }
                    ++noOfRetry;

                } else {
                    return true;
                }

            } catch (IOException e) { //System.out.print(e.getMessage());
            } catch (Exception e) {}
        }
    }

    // helper method - send list neighbors packet to peers
    private boolean sendNeighborsPacket(Socket socket, DataOutputStream os, Queue<String> searchList) {
        int noOfRetry = 0; // 2 times retry for sending a send packet for neighbors
        while (true) {
            try {
                // Inputstream of the remote peer
                DataInputStream inFromServer = new DataInputStream(socket.getInputStream());
                // Create the listneighbors packet
                Packets.ToServer listNeighborsPacket = new Packets.ListNeighbors();
                os.writeUTF(Packets.serialize(listNeighborsPacket));
                os.flush();

                String[] msg = {null};

                new Thread(() -> {
                    try {
                        if ((msg[0] = inFromServer.readUTF()) != null) {
                            Packets.Neighbors neighborsPacket = (Packets.Neighbors) Packets.deserializeS2C(msg[0]);
                            for (String n : neighborsPacket.neighbors) {
                                searchList.add(n);
                            }

                        }
                    } catch (IOException e) {
                    } catch (Exception e) {
                    }
                }).start();

                Thread.sleep(500);

                if (msg[0] == null) {
                    if (noOfRetry == 2) {
                        inFromServer.close();
                        return false;
                    }
                    ++noOfRetry;

                } else {
                    return true;
                }
            } catch (IOException e) {
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // Handles packets received from client
    private void handlePacket(String msg, ChatConnection con) {
        try {
            String command = Packets.handleJsonC2S(msg);
            switch(command) {
                case "Join":
                    Packets.Join joinPacket = (Packets.Join) Packets.deserializeC2S(msg);
                    joinRoomProtocol(con, joinPacket.roomid);
                    break;
                case "Who":
                    Packets.Who whoPacket = (Packets.Who) Packets.deserializeC2S(msg);
                    roomContentProtocol(con, whoPacket.roomid);
                    break;
                case "List":
                    roomListProtocol(con, "");
                    break;
                case "ListNeighbors":
                    listNeighbors(con);
                    break;
                case "HostChange":
                    Packets.HostChange peerPacket = (Packets.HostChange) Packets.deserializeC2S(msg);
                    con.clientID = peerPacket.host;
                    break;
                case "Delete":
                    Packets.Delete delPacket = (Packets.Delete) Packets.deserializeC2S(msg);
                    deleteRoomProtocol(con, delPacket.roomid);
                    break;
                case "Shout":
                    Packets.Shout shoutPacket = (Packets.Shout) Packets.deserializeC2S(msg);
                    shoutProtocol(con, "", shoutPacket.timestamp, shoutPacket.content);
                    break;
                case "ShoutMessage":
                    Packets.ShoutMessage shoutMsgPacket = (Packets.ShoutMessage) Packets.deserializeC2S(msg);
                    shoutProtocol(null, shoutMsgPacket.identity, shoutMsgPacket.timestamp, shoutMsgPacket.content);
                    break;
                case "Message":
                    Packets.Message msgPacket = (Packets.Message) Packets.deserializeC2S(msg);
                    handleMessageFromClient(con, msgPacket.content);
                    break;
                case "Quit":
                    quitProtocol(con, true);
                    break;

                default:
                    break;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Client connection
    private class ChatConnection extends Thread {
        private Socket socket;
        private boolean connectionAlive = false;
        public String clientID = "";
        public String currentRoom = "";
        private DataInputStream isReader;
        private DataOutputStream outWriter;

        public ChatConnection(Socket socket) throws IOException {
            this.socket = socket;
            this.outWriter = new DataOutputStream(socket.getOutputStream());
            this.isReader = new DataInputStream(socket.getInputStream());
        }

        @Override
        public void run() {
            connectionAlive = true;
            while (connectionAlive) {
                try {
                    String in = isReader.readUTF();
                    if (in != null) {
                        handlePacket(in, this);
                    } else {
                        connectionAlive = false;
                        quitProtocol(this, false);
                        close();
                    }
                } catch (Exception e) {
                    connectionAlive = false;
                    quitProtocol(this, false);
                    close();
                }
            }
        }

        public void close() {
            try {
                this.socket.close();
                this.outWriter.close();
                this.isReader.close();
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
        }

        // Send JSON object packet message to the client
        public void sendMessage(String message) throws IOException {
//            System.out.println(message);
            outWriter.writeUTF(message);
            outWriter.flush();
        }
    }
}
