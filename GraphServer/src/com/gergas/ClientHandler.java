package com.gergas;

import java.net.Socket;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientHandler extends AbstractSocketHandler {
	
	ConcurrentHashMap<Long, Vertex> vertexMap;
	AtomicBoolean isSendDone;
	volatile Object[] algParams;
	volatile Algorithm algorithm;
	
	public ClientHandler(Socket socket) {
		super(socket, null);
		vertexMap = new ConcurrentHashMap<Long, Vertex>();
		isSendDone = new AtomicBoolean(false);
		algorithm = Algorithm.NONE;
	}

	public void reqHandshake(Message msg) {
		System.out.println("handshake request");
		//send response
		Long id = (Long)msg.parameters[0];
		this.id = id.longValue();
		Message respMsg = msg.getReverse();
		protocol.sendMessage(respMsg);
	}
	
	public void reqSendVertexData(Message msg) {
		System.out.println("send vertex data request");
		Vertex[] vertices = (Vertex[]) msg.parameters[0];
		for (int i=0; i<vertices.length; i++) {
			Vertex v = vertices[i];
			vertexMap.put(new Long(v.id), v);
			StringBuffer strBuf = new StringBuffer("           id: " + v.id + ", value: " + v.value + ", edges:");
			for (int j=0; j<v.outgoingEdges.size(); j++) {
				strBuf.append(" " + v.outgoingEdges.get(j));
			}
			System.out.println(strBuf.toString());
		}
		//send response
		Message respMsg = msg.getReverse();
		protocol.sendMessage(respMsg);
	}
	
	public void reqSuperStepComm(Message msg) {
		//System.out.println("super step comm request");
		Message submsg;
		isSendDone.set(false);
		algorithm = (Algorithm) msg.parameters[0];
		algParams = msg.parameters;
		Iterator<Map.Entry<Long, Vertex>> vertexIterator = vertexMap.entrySet().iterator();
		while (vertexIterator.hasNext()) {
			Vertex v = vertexIterator.next().getValue();
			if (v.active) {
				for (int j=0; j<v.outgoingEdges.size(); j++) {
					Long vId = v.outgoingEdges.get(j);
					submsg = new Message();
					submsg.msgStage = MsgStage.REQUEST;
					submsg.commState = CommState.SEND_VALUE;
					Edge edge = new Edge();
					edge.vIdFrom = v.id; //sender
					edge.vIdTo = vId.longValue(); //recipient
					submsg.edge = edge;
					addAlgorithmParams(submsg, v);
					Vertex v0 = vertexMap.get(vId);
					if (v0 == null) {
						protocol.sendMessage(submsg);
					} else {
						v0.incomingMessages.put(new Long(v.id), submsg);
						v0.gotMessage = true;
					}
				}
			}
		}
		isSendDone.set(true);
		checkSuperStepCommDone(null);
	}
	
	public void addAlgorithmParams(Message msg, Vertex fromVertex) {
		Object[] params;
		if (algorithm == Algorithm.MAXIMUM_VALUE || algorithm == Algorithm.MINIMUM_VALUE) {
			params = new Object[1];
			params[0] = new Double(fromVertex.value); //send value
		} else if (algorithm == Algorithm.PAGERANK ) {
			params = new Object[1];
			params[0] = new Double(fromVertex.value / ((double)fromVertex.outgoingEdges.size())); //send value
			//System.out.println(((double)fromVertex.outgoingEdges.size()));
		} else {
			params = new Object[0];
		}
		msg.parameters = params;
	}
	
	public void reqSendValue(Message msg) {
		//System.out.println("send value request, " + msg.edge.vIdFrom + ", " + msg.edge.vIdTo + ", " + msg.hashCode());
		Vertex v = vertexMap.get(new Long(msg.edge.vIdTo));
		if (v != null) {
			synchronized (v) { // simple ArrayList needs external sync
				v.incomingMessages.put(new Long(msg.edge.vIdFrom), msg);
				v.gotMessage = true;
			}
		} else {
			try {
				throw new IllegalArgumentException("Vertex not found");
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			}
		}
		Message respMsg = msg.getReverse();
		protocol.sendMessage(respMsg);
	}
	
	public void resSendValue(Message msg) {
		//System.out.println("send value response, " + msg.edge.vIdFrom + ", " + msg.edge.vIdTo + ", " + msg.hashCode());
		checkSuperStepCommDone(msg);
	}
	
	public void checkSuperStepCommDone(Message msg) {
		if (msg == null) {
			if (!protocol.containsOutstanding(CommState.SEND_VALUE)) {
				Message respMsg = new Message();
				respMsg.msgStage = MsgStage.RESPONSE;
				respMsg.commState = CommState.SUPERSTEPCOMM;
				protocol.sendMessage(respMsg);
			}
		} else {
			if (isSendDone.get() && !protocol.containsOtherOutstanding(msg)) {
				//System.out.println("super step comm done!");
				Message respMsg = new Message();
				respMsg.msgStage = MsgStage.RESPONSE;
				respMsg.commState = CommState.SUPERSTEPCOMM;
				protocol.sendMessage(respMsg);
			}
		}
	}
	
	public void reqSuperStepCalc(Message msg) {
		System.out.println("super step calc request");
		Iterator<Map.Entry<Long, Vertex>> vertexIterator = vertexMap.entrySet().iterator();
		while (vertexIterator.hasNext()) {
			Vertex v = vertexIterator.next().getValue();
			updateVertex(v);
		}
		Message respMsg = msg.getReverse();
		respMsg.parameters = new Object[1];
		respMsg.parameters[0] = new Boolean(hasActiveVertex());
		protocol.sendMessage(respMsg);
	}
	
	public void updateVertex(Vertex v) {
		if (!v.gotMessage) {
			v.active = false;
			System.out.println("           id: " + v.id + ", value: " + v.value + ", active: " + v.active);
			return;
		}
		
		Iterator<Entry<Long, Message>> messageIterator = v.incomingMessages.entrySet().iterator();
		boolean hasUpdated = false;
		double updatedValue = v.value;
		double newValue = 0.0d;
		while (messageIterator.hasNext()) {
			Message msg = messageIterator.next().getValue();
			if (algorithm == Algorithm.MAXIMUM_VALUE) {
				newValue = ((Double) msg.parameters[0]).doubleValue();
				if (newValue > updatedValue) 
					updatedValue = newValue;
			} else if (algorithm == Algorithm.MINIMUM_VALUE) {
				newValue = ((Double) msg.parameters[0]).doubleValue();
				if (newValue < updatedValue) 
					updatedValue = newValue;
			} else if (algorithm == Algorithm.PAGERANK) {
				//System.out.println(v.id + " " + msg.edge.vIdTo + " received value " +  ((Double) msg.parameters[0]).doubleValue() + " from " + msg.edge.vIdFrom);
				newValue += ((Double) msg.parameters[0]).doubleValue();
			}
			if (v.value != updatedValue && algorithm != Algorithm.PAGERANK) {
				v.value = updatedValue;
				hasUpdated = true;
			}
		}
		if (algorithm == Algorithm.PAGERANK) {
			double N = (double) ((Long) algParams[1]).longValue();
			double learningFactor = ((Double) algParams[2]).doubleValue();
			double precision = ((Double) algParams[3]).doubleValue();
			//System.out.println("Sum incoming = " + newValue);
			newValue = (1.0d - learningFactor)/N + learningFactor * newValue;
			if (Math.abs(newValue - updatedValue) > precision) {
				v.value = newValue;
				hasUpdated = true;
			}
		}
		
		// set active status
		v.active = hasUpdated;
		v.gotMessage = false;
		System.out.println("           id: " + v.id + ", value: " + v.value + ", active: " + v.active);
	}
	
	public boolean hasActiveVertex() {
		Iterator<Map.Entry<Long, Vertex>> vertexIterator = vertexMap.entrySet().iterator();
		while (vertexIterator.hasNext()) {
			Vertex v = vertexIterator.next().getValue();
			if (v.active)
				return true;
		}
		return false;
	}
	
	public void reqPrintResult(Message msg) {
		System.out.println("print result request");
		Iterator<Map.Entry<Long, Vertex>> vertexIterator = vertexMap.entrySet().iterator();
		while (vertexIterator.hasNext()) {
			Vertex v = vertexIterator.next().getValue();
			System.out.println("           id: " + v.id + ", value: " + v.value + ", active: " + v.active);
		}
	}
}
