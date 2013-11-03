package com.gergas;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

public class Vertex implements Serializable {

	public static final long serialVersionUID = 1L;
	
	public long id;
	public double value;
	public boolean active;
	public boolean gotMessage;
	public ArrayList<Long> outgoingEdges;
	public HashMap<Long, Message> incomingMessages;
	
	public Vertex() {
		outgoingEdges = new ArrayList<Long>();
		incomingMessages = new HashMap<Long, Message>();
		active = true;
		gotMessage = false;
	}

	
}
