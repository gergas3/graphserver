package com.gergas;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;

public abstract class AbstractSocketHandler extends Thread {

		public long id;
		public Socket socket;
		public GraphServer server;
	    protected ObjectInputStream in;
	    protected ObjectOutputStream out;
	    protected Protocol protocol;
		
		public AbstractSocketHandler(Socket socket, GraphServer server) {
			this.socket = socket;
			this.server = server;
			try {
				this.out = new ObjectOutputStream(socket.getOutputStream());
				this.in = new ObjectInputStream(socket.getInputStream());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		   
		public void run() {
			while (true) {
				try {
					Object obj = in.readObject();
					if (obj == null) {
						return;
					} else {
						protocol.receiveMessage(obj);
						if (server != null)
							server.checkStates();
					}
				} catch (SocketException e) { // socket not closed gracefully
					e.printStackTrace();
					return;
				} catch (EOFException e) { // socket closed gracefully on client side
					e.printStackTrace();
					return;
				} catch (IOException e) { // other IOException
					e.printStackTrace();
				} catch (ClassNotFoundException e) { // message received could not be deserialized
					e.printStackTrace();
				} catch (Exception e) { // invalid state, no such request sent
					e.printStackTrace();
				}
			}

		}
		 
		protected synchronized void writeMessage(Object obj) {
			try {
				out.writeObject(obj);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		protected void close() {
			try {
				socket.close(); // this automatically closes in and out streams
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
}
