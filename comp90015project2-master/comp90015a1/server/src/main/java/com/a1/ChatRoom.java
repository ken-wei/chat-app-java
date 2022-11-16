package com.a1;

import java.util.ArrayList;
import java.util.List;

public class ChatRoom {

    private String owner;
    private List<String> clientList = new ArrayList<>();
    private String roomName;

    public ChatRoom(String owner, String roomName) {
        this.owner = owner;
        this.roomName = roomName;
    }

    public void updateOwner(String newOwner) {
        this.owner = newOwner;
    }

    public String getOwner() {
        return owner;
    }

    public String getRoomName() {
        return roomName;
    }

    public List<String> getClientList() {
        return clientList;
    }

    public void addClientToRoom(String clientID) {
        clientList.add(clientID);
    }

    public void removeClientFromRoom(String clientID) {
        clientList.remove(clientID);
    }

    public int getClientCount() {
        return clientList.size();
    }

    public String roomInfo() {
        String info = "";
        for (String c : clientList) {
            info += c + " ";
        }
        return (roomName + " contains " + info + "\n");
    }
}
