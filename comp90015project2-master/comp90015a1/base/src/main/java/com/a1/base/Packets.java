package com.a1.base;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;

// Better but not exactly idiomatic - move stuff out of the static classes and it's good.
public class Packets {
  private static ObjectMapper mapper = new ObjectMapper();

  // Serialize ToServer packets into a json string
  public static String serialize(ToServer packet) throws Exception {
    String msg = mapper
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(packet);
    return msg;
  }

  // Serialize ToClient packets into a json string
  public static String serialize(ToClient packet) throws Exception {
    String msg = mapper
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(packet);
    return msg;
  }

  // Deserialize server to client json string back to JSON object
  public static ToClient deserializeS2C(String msg) throws Exception {
    return mapper.readValue(msg, ToClient.class);
  }

  // Deserialize client to server json string back to JSON object
  public static ToServer deserializeC2S(String msg) throws Exception {
    return mapper.readValue(msg, ToServer.class);
  }

  // Handle packets sent from client to server and get the JSON class object name
  public static String handleJsonC2S(String msg) throws Exception {
    ToServer jsonPacket = mapper.readValue(msg, ToServer.class);
    return jsonPacket.getClass().getSimpleName();
  }

  // Handle packets sent from server to client and get the JSON class object name
  public static String handleJsonS2C(String msg) throws Exception {
    ToClient jsonPacket = mapper.readValue(msg, ToClient.class);
    return jsonPacket.getClass().getSimpleName();
  }

  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME,
      property = "type"
  )
  @JsonSubTypes({
      @JsonSubTypes.Type(value = IdentityChange.class, name = "identitychange"),
      @JsonSubTypes.Type(value = Join.class, name = "join"),
      @JsonSubTypes.Type(value = Who.class, name = "who"),
      @JsonSubTypes.Type(value = List.class, name = "list"),
      @JsonSubTypes.Type(value = CreateRoom.class, name = "createroom"),
      @JsonSubTypes.Type(value = Delete.class, name = "delete"),
      @JsonSubTypes.Type(value = Quit.class, name = "quit"),
      @JsonSubTypes.Type(value = Message.class, name = "message"),
      @JsonSubTypes.Type(value = NewIdentity.class, name = "newidentity"),
      @JsonSubTypes.Type(value = HostChange.class, name = "hostchange"),
      @JsonSubTypes.Type(value = ListNeighbors.class, name = "listneighbors"),
      @JsonSubTypes.Type(value = Shout.class, name = "shout"),
      @JsonSubTypes.Type(value = ShoutMessage.class, name = "shoutmessage"),
  })
  public static class ToServer {
  }

  @JsonTypeName("hostchange")
  public static class HostChange extends ToServer {
    public String host;

    @JsonCreator
    public HostChange(@JsonProperty("host") String host) {
      this.host = host;
    }
  }

  @JsonTypeName("identitychange")
  public static class IdentityChange extends ToServer {
    public String identity;

    @JsonCreator
    public IdentityChange(@JsonProperty("identity") String identity) {
      this.identity = identity;
    }
  }

  @JsonTypeName("join")
  public static class Join extends ToServer {
    public String roomid;

    @JsonCreator
    public Join(@JsonProperty("roomid") String roomid) {
      this.roomid = roomid;
    }
  }

  @JsonTypeName("who")
  public static class Who extends ToServer {
    public String roomid;

    @JsonCreator
    public Who(@JsonProperty("roomid") String roomid) {
      this.roomid = roomid;
    }
  }

  @JsonTypeName("list")
  public static class List extends ToServer {
  }

  @JsonTypeName("listneighbors")
  public static class ListNeighbors extends ToServer {

  }

  @JsonTypeName("createroom")
  public static class CreateRoom extends ToServer {
    public String roomid;

    @JsonCreator
    public CreateRoom(@JsonProperty("roomid") String roomid) {
      this.roomid = roomid;
    }
  }

  @JsonTypeName("delete")
  public static class Delete extends ToServer {
    public String roomid;

    @JsonCreator
    public Delete(@JsonProperty("roomid") String roomid) {
      this.roomid = roomid;
    }
  }

  @JsonTypeName("quit")
  public static class Quit extends ToServer {
  }

  @JsonTypeName("message")
  public static class Message extends ToServer {
    public String content;

    @JsonCreator
    public Message (@JsonProperty("content") String content) {
      this.content = content;
    }
  }

  @JsonTypeName("shoutmessage")
  public static class ShoutMessage extends ToServer {
    public String identity;
    public String content;
    public String timestamp;

    @JsonCreator
    public ShoutMessage(@JsonProperty("identity") String identity,
                        @JsonProperty("timestamp") String timestamp,
                        @JsonProperty("content") String content) {
      this.identity = identity;
      this.content = content;
      this.timestamp = timestamp;
    }
  }

  @JsonTypeName("shout")
  public static class Shout extends ToServer {
    public String content;
    public String timestamp;

    @JsonCreator
    public Shout (@JsonProperty("timestamp") String timestamp,
                  @JsonProperty("content") String content) {
      this.content = content;
      this.timestamp = timestamp;
    }
  }

  @JsonTypeInfo(
          use = JsonTypeInfo.Id.NAME,
          property = "type"
  )
  @JsonSubTypes({
          @JsonSubTypes.Type(value = NewIdentity.class, name = "newidentity"),
          @JsonSubTypes.Type(value = RoomChange.class, name = "roomchange"),
          @JsonSubTypes.Type(value = RoomContents.class, name = "roomcontents"),
          @JsonSubTypes.Type(value = RoomList.class, name = "roomlist"),
          @JsonSubTypes.Type(value = Message2C.class, name = "message"),
          @JsonSubTypes.Type(value = Neighbors.class, name = "neighbors"),
          @JsonSubTypes.Type(value = ShoutMessage2C.class, name = "shoutmessage"),
  })

  /** Server to Client Part **/
  public static class ToClient {

  }

  @JsonTypeName("newidentity")
  public static class NewIdentity extends ToClient {
    public String former;
    public String identity;

    @JsonCreator
    public NewIdentity(@JsonProperty("former") String former, @JsonProperty("identity") String identity) {
      this.identity = identity;
      this.former = former;
    }
  }

  @JsonTypeName("roomchange")
  public static class RoomChange extends ToClient {
    public String identity;
    public String former;
    public String roomid;

    @JsonCreator
    public RoomChange (@JsonProperty("identity") String identity,
                       @JsonProperty("former") String former,
                       @JsonProperty("roomid") String roomid) {
      this.former = former;
      this.identity = identity;
      this.roomid = roomid;
    }
  }

  @JsonTypeName("roomcontents")
  public static class RoomContents extends ToClient {
    public String roomid;
    public ArrayList<String> identities;

    @JsonCreator
    public RoomContents(@JsonProperty("roomid") String roomid,
                        @JsonProperty("identities") ArrayList<String> identities) {
      this.roomid = roomid;
      this.identities = identities;
    }
  }

  @JsonTypeName("neighbors")
  public static class Neighbors extends ToClient {
    public ArrayList<String> neighbors;

    @JsonCreator
    public Neighbors(@JsonProperty("neighbors") ArrayList<String> neighbors) {
      this.neighbors = neighbors;
    }
  }

  @JsonTypeName("roomlist")
  public static class RoomList extends ToClient {
      public ArrayList<RoomDetails> rooms;

      @JsonCreator
      public RoomList(@JsonProperty("rooms") ArrayList<RoomDetails> rooms) {
        this.rooms = rooms;
      }

    // Using it for object mapping
    public static class RoomDetails {
      public String roomid;
      public int count;

      public RoomDetails(@JsonProperty("roomid") String roomid, @JsonProperty("count") int count) {
        this.roomid = roomid;
        this.count = count;
      }
    }
  }

  @JsonTypeName("message")
  public static class Message2C extends ToClient {
    public String identity;
    public String content;

    @JsonCreator
    public Message2C(@JsonProperty("identity") String identity, @JsonProperty("content") String content) {
      this.identity = identity;
      this.content = content;
    }
  }

  @JsonTypeName("shoutmessage")
  public static class ShoutMessage2C extends ToClient {
    public String identity;
    public String content;
    public String timestamp;

    @JsonCreator
    public ShoutMessage2C(@JsonProperty("identity") String identity,
                        @JsonProperty("timestamp") String timestamp,
                        @JsonProperty("content") String content) {
      this.identity = identity;
      this.content = content;
      this.timestamp = timestamp;
    }
  }

}
