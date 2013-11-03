package com.gergas;

import java.io.IOException;
import java.net.Socket;

public class Main {

	public Main() {
		
	}
	
	public static void main(String [] args)
	   {
		   String cmd = args[0];
		   if (cmd.equals("server")) {
			   startServer(args);
		   } else if (cmd.equals("client")) {
			   startClient(args);
		   }
		   
	   }
	   
	   private static void startServer(String[] args) {
		   int port = Integer.parseInt(args[1]);
		   try {
			   int vertexCount = Integer.parseInt(args[2]);
			   int outCount = Integer.parseInt(args[3]);
			   Algorithm alg = Algorithm.fromName(args[4]);
			   int clientCount = Integer.parseInt(args[5]);
			   GraphServer gs = new GraphServer(port, alg, clientCount, vertexCount, outCount);
			   gs.start();
		   } catch (IOException e) {
			   e.printStackTrace();
		   }
	   }
	   
	   private static void startClient(String[] args) {
		   String serverName = args[1];
		   int port = Integer.parseInt(args[2]);
		   try {
			   ClientHandler ch = new ClientHandler(new Socket(serverName, port));
			   Protocol p = new Protocol(ch);
			   ch.protocol = p;
			   ch.start();
			   System.out.println("Client connection successful!");
		   } catch(IOException e) {
			   e.printStackTrace();
		   }
	   }
}
