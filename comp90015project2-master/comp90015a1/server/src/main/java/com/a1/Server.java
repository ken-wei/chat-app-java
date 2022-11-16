package com.a1;

import com.a1.base.Base;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import java.io.IOException;

public class Server {

  @Parameter(names={"-p"}, description = "Port Number of server")
  private int portNumber = 0;

  private void argumentParsing(final String[] args) throws IOException {
    JCommander.newBuilder().addObject(this).build().parse(args);
//    System.out.println(portNumber);
  }

  public static void main(String[] args) throws IOException {
    final Server server = new Server();
    server.argumentParsing(args);
    ServerHandler handler = new ServerHandler(0);
//    handler.handle(server.portNumber);
  }
}
