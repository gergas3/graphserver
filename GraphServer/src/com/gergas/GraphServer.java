package com.gergas;

import java.net.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.io.*;

import org.apache.commons.math3.linear.DecompositionSolver;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.MatrixUtils;

public class GraphServer extends Thread
{
	private AtomicLong clientCounter;
	private ServerSocket serverSocket;
	private ConcurrentHashMap<Long, ServerHandler> clientMap;
	private ConcurrentHashMap<Long, Long> vertexToClientMap;
	private CommState commState;
	public AtomicBoolean isAnyClientActive;
	private AtomicLong superStepCount;
	private ArrayList<Vertex> vertexArray;
	
	private final Algorithm algorithm;
	private final int clientCount;
	private final int vertexCount;
	private final int outCount;
	
	private final double PAGERANK_PRECISION;
	private static double PAGERANK_DAMPING = 0.85d;

	public GraphServer(int port, Algorithm algorithm, int clientCount, int vertexCount, int outCount) throws IOException
	{
		clientCounter = new AtomicLong(0);
		serverSocket = new ServerSocket(port);
		serverSocket.setSoTimeout(1000000);
		clientMap = new ConcurrentHashMap<Long, ServerHandler>();
		vertexArray = new ArrayList<Vertex>();
		vertexToClientMap = new ConcurrentHashMap<Long, Long>();
		commState = CommState.GENERAL;
		isAnyClientActive = new AtomicBoolean(true);
		superStepCount = new AtomicLong(0L);
		
		this.algorithm = algorithm;
		this.clientCount = clientCount;
		this.vertexCount = vertexCount;
		this.outCount = outCount;
		this.PAGERANK_PRECISION = 1/((double)vertexCount)/1000.0d;  //lower by 3 orders of magnitude
	}

	public void run()
	{
		while(true)
		{
			try {
				System.out.println("Waiting for clients on port " + serverSocket.getLocalPort() + "...");
				long newId = getNextClientId();
				ServerHandler handler = new ServerHandler(serverSocket.accept(), this);
				handler.id = newId;
				Protocol p = new Protocol(handler);
				handler.protocol = p;
				clientMap.put(new Long(newId), handler);
				handler.start();
				System.out.println("New client connected with id " + newId);
				if (clientMap.size() == clientCount) {
					System.out.println("We have " + Integer.toString(clientCount) + " clients! Starting algorithm...");
					initGraphData();
					sendHandshake();
				}
			} catch(SocketTimeoutException s) {
				System.out.println("Socket timed out!");
				break;
			} catch(IOException e) {
				e.printStackTrace();
				break;
			}
		}
	}

	public void sendPublicMessage(Object obj) {
		Iterator<Map.Entry<Long, ServerHandler>> clientIterator = clientMap.entrySet().iterator();
		while (clientIterator.hasNext()) {
			ServerHandler client = clientIterator.next().getValue();
			client.protocol.sendMessage(obj);
		}
	}
	
	public void sendPrivateMessage(Long vertexId, Object obj) {
		Long clientId = vertexToClientMap.get(vertexId);
		ServerHandler client = clientMap.get(clientId);
		client.protocol.sendMessage(obj);
	}

	public void initGraphData() {
		HashSet<Long> globalOutgoing = new HashSet<Long>();
				
		StringBuffer strBuf = new StringBuffer("Generated vertices:");
		vertexArray.clear();
		for (int i=0; i<vertexCount; i++) {
			Vertex v = new Vertex();
			v.id = i+1;
			v.value = algorithm == Algorithm.PAGERANK ? 1/((double)vertexCount) : Math.random();
			strBuf.append("\n           id: " + v.id + ", value: " + v.value + ", edges:");
			for (int j=0; j<outCount; j++) {
				Long edge = new Long(((long)(Math.random() * vertexCount)) + 1);
				if (!v.outgoingEdges.contains(edge) && edge.longValue() != v.id) {
					v.outgoingEdges.add(edge);
					globalOutgoing.add(edge);
					strBuf.append(" " + edge.toString());
				} else {
					j--;
				}
			}
			vertexArray.add(v);
		}
		if (globalOutgoing.size() != vertexCount) {
			System.out.println("Ooops random graph is incomplete. Generating again...");
			initGraphData();
		} else {
			System.out.println(strBuf.toString());
		}
	}
	
	public synchronized void checkStates() {
		boolean resHandshake = true;
		boolean resSendVertexData = true;
		boolean resSuperStepComm = true;
		boolean resSuperStepCalc = true;
		
		Iterator<Map.Entry<Long, ServerHandler>> clientIterator = clientMap.entrySet().iterator();
		while (clientIterator.hasNext()) {
			ServerHandler client = clientIterator.next().getValue();
			if ((resHandshake&& 
					client.protocol.containsOutstanding(CommState.HANDSHAKE)) || commState != CommState.HANDSHAKE ) {
				resHandshake = false;
			}
			if ((resSendVertexData && 
					client.protocol.containsOutstanding(CommState.SEND_VERTEX_DATA)) || commState != CommState.SEND_VERTEX_DATA ) {
				resSendVertexData = false;
			}
			if ((resSuperStepComm && 
					client.protocol.containsOutstanding(CommState.SUPERSTEPCOMM)) || commState != CommState.SUPERSTEPCOMM ) {
				resSuperStepComm = false;
			}
			if ((resSuperStepCalc && 
					client.protocol.containsOutstanding(CommState.SUPERSTEPCALC)) || commState != CommState.SUPERSTEPCALC ) {
				resSuperStepCalc = false;
			}
		}
		
		if (resHandshake) {
			sendVertexData();
		} else if (resSendVertexData) {
			superStepCount.set(0L);
			sendSuperStepComm();
		} else if (resSuperStepComm) {
			sendSuperStepCalc();
		} else if (resSuperStepCalc) {
			superStepCount.incrementAndGet();
			System.out.println("super step " + superStepCount.get() + " completed!");
			if (isAnyClientActive.get() && superStepCount.get() < 500L) {
				sendSuperStepComm();
			} else {
				System.out.println("Algorithm completed in " + superStepCount.get() + " steps!");
				sendPrintResult();
				printRealResult();
			}
		}
	}
	
	public void sendHandshake() {
		commState = CommState.HANDSHAKE;
		Iterator<Map.Entry<Long, ServerHandler>> clientIterator = clientMap.entrySet().iterator();
		while (clientIterator.hasNext()) {
			Message msg = new Message();
			msg.msgStage = MsgStage.REQUEST;
			msg.commState = CommState.HANDSHAKE;
			Object[] obj = new Object [1];
			ServerHandler client = clientIterator.next().getValue();
			obj[0] = new Long(client.id);
			msg.parameters = obj;
			client.protocol.sendMessage(msg);
		}
	}
	
	public void sendSuperStepComm() {
		commState = CommState.SUPERSTEPCOMM;
		Message msg = new Message();
		msg.msgStage = MsgStage.REQUEST;
		msg.commState = CommState.SUPERSTEPCOMM;
		addAlgorithmParams(msg, algorithm);
		sendPublicMessage(msg);
	}
	
	public void addAlgorithmParams(Message msg, Algorithm alg) {
		if (alg == Algorithm.MAXIMUM_VALUE || alg == Algorithm.MINIMUM_VALUE) {
			msg.parameters = new Object[1];
			msg.parameters[0] = alg;
		} else if (alg == Algorithm.PAGERANK) {
			msg.parameters = new Object[4];
			msg.parameters[0] = alg;
			msg.parameters[1] = new Long(vertexCount); // N: the total number of vertices
			msg.parameters[2] = new Double(PAGERANK_DAMPING); // learning factor
			msg.parameters[3] = new Double(PAGERANK_PRECISION); // precision
		}
	}
	
	public void sendSuperStepCalc() {
		commState = CommState.SUPERSTEPCALC;
		Message msg = new Message();
		msg.msgStage = MsgStage.REQUEST;
		msg.commState = CommState.SUPERSTEPCALC;
		isAnyClientActive.set(false);
		sendPublicMessage(msg);
	}
	
	public void sendVertexData() {
		commState = CommState.SEND_VERTEX_DATA;
		int modulo = vertexCount % clientCount;
		int whole = (int) vertexCount / clientCount;
		int cumNumVertices = 0;
		for (int c=0; c<clientCount; c++) {
			Long clientId = new Long(c+1);
			int numVertices = c<modulo ? whole+1 : whole;
			Vertex[] sendVertices = new Vertex[numVertices];
			for (int i=cumNumVertices; i<cumNumVertices+numVertices; i++) {
				Vertex v = vertexArray.get(i);
				sendVertices[i-cumNumVertices] = v;
				vertexToClientMap.put(new Long(v.id), clientId);
			}
			ServerHandler sh = clientMap.get(clientId);
			Message msg = new Message();
			msg.msgStage = MsgStage.REQUEST;
			msg.commState = CommState.SEND_VERTEX_DATA;
			Object[] obj = new Object[1];
			obj[0] = sendVertices;
			msg.parameters = obj;
			sh.protocol.sendMessage(msg);
			cumNumVertices += numVertices;
		}
	}
	
	public void sendPrintResult() {
		commState = CommState.PRINT_RESULT;
		Message msg = new Message();
		msg.msgStage = MsgStage.REQUEST;
		msg.commState = CommState.PRINT_RESULT;
		sendPublicMessage(msg);
	}
	
	public void printRealResult() {
		switch (algorithm) {
			case PAGERANK:
				printRealResultPageRank();
			break;
			case MINIMUM_VALUE:
				printRealResultMinVal();
			break;
			case MAXIMUM_VALUE:
				printRealResultMaxVal();
			break;
			default:
			break;
		}
	}
	
	private void printRealResultMinVal() {
		double minVal = Double.MAX_VALUE;
		long minId = 0L;
		for (Vertex v: vertexArray) {
			if (v.value < minVal) {
				minVal = v.value;
				minId = v.id;
			}
		}
		System.out.println("Arithmetic result: ");
		System.out.println("           id: " + minId + " value: "+ minVal);
	}
	
	private void printRealResultMaxVal() {
		double maxVal = Double.MIN_VALUE;
		long maxId = 0L;
		for (Vertex v: vertexArray) {
			if (v.value > maxVal) {
				maxVal = v.value;
				maxId = v.id;
			}
		}
		System.out.println("Arithmetic result: ");
		System.out.println("           id: " + maxId + " value: "+ maxVal);
	}
	
	private void printRealResultPageRank() {
		RealMatrix identity = MatrixUtils.createRealIdentityMatrix(vertexCount);
		double[][] gdata = new double[vertexCount][vertexCount];
		for (int i=0; i<gdata.length; i++) {
			for (int j=0; j<gdata[i].length; j++) {
				Vertex v = vertexArray.get(j);
				if (v.outgoingEdges.contains(new Long(i+1))) {
					gdata[i][j] = 1.0d/v.outgoingEdges.size();
				}
			}
		}
		RealMatrix g = MatrixUtils.createRealMatrix(gdata);
		//System.out.println(g.toString());
		double pdata[][] = new double[vertexCount][1];
		for (int i=0; i<pdata.length; i++) {
			pdata[i][0] = 1.0d/vertexCount;
		}
		RealMatrix p = MatrixUtils.createRealMatrix(pdata);
		//System.out.println(p.toString());
		RealMatrix toInvert = (identity.add(g.scalarMultiply(-PAGERANK_DAMPING)));
		DecompositionSolver solver = new LUDecomposition(toInvert).getSolver();
		if (!solver.isNonSingular()) {
			System.out.println("Singular matrix??");
		}
		RealMatrix result = solver.getInverse().multiply(p).scalarMultiply(1-PAGERANK_DAMPING);
		System.out.println("Arithmetic result: ");
		for (int i=0; i<result.getRowDimension(); i++) {
			System.out.println("           id: " + (i+1) + " value: "+ result.getEntry(i, 0));
		}
	}
	
	public long getNextClientId() {
		return clientCounter.incrementAndGet();
	}
}
