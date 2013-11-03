package com.gergas;

import java.net.Socket;

public class ServerHandler extends AbstractSocketHandler {
	
	public ServerHandler(Socket socket, GraphServer server) {
		super(socket, server);
	}
	
	public void resHandshake(Message msg) {
		System.out.println("handshake response");
	}
	
	public void resSendVertexData(Message msg) {
		System.out.println("send vertex data response");
	}
		
	public void reqSendValue(Message msg) { //forwarding
		//System.out.println("request, " + msg.edge.vIdFrom + ", " + msg.edge.vIdTo + ", " + msg.hashCode());
		server.sendPrivateMessage(msg.edge.vIdTo, msg);
	}
	
	public void resSendValue(Message msg) { //forwarding
		//System.out.println("response, " + msg.edge.vIdFrom + ", " + msg.edge.vIdTo + ", " + msg.hashCode());
		server.sendPrivateMessage(msg.edge.vIdFrom, msg);
	}
	
	public void resSuperStepComm(Message msg) {
		//System.out.println("super step comm response");
	}
	
	public void resSuperStepCalc(Message msg) {
		//System.out.println("super step calc response");
		boolean hasActive = ((Boolean) msg.parameters[0]).booleanValue();
		//System.out.println("super step: " + hasActive);
		if (hasActive) {
			this.server.isAnyClientActive.set(true);
		}
	}
}
