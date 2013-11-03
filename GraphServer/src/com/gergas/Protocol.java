package com.gergas;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArraySet;

public class Protocol {
	
	AbstractSocketHandler handler;
	CopyOnWriteArraySet<Message> outstandingMessages;
	
	public Protocol(AbstractSocketHandler handler) {
		outstandingMessages = new CopyOnWriteArraySet<Message>();
		this.handler = handler;
	}
	
	public void receiveMessage(Object obj) {
		try {
			Message msg = (Message) obj;
			Method m = handler.getClass().getMethod(msg.msgStage.getName() + msg.commState.getName(), Message.class);
			m.invoke(handler, msg);
			if (msg.msgStage == MsgStage.RESPONSE) {
				//System.out.println("Response found, removing...");
				boolean success = outstandingMessages.remove(msg.getReverse());
				if (!success) {
					throw new Exception();
				}
			}
		} catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void sendMessage(Object obj) {
		Message msg = (Message) obj;
		if (msg.msgStage == MsgStage.REQUEST) {
			boolean success = outstandingMessages.add(msg);
			if (!success) {
				System.out.println("This message has already been sent");
			}
		}
		handler.writeMessage(msg);
	}
	
	public boolean containsOutstanding(CommState cs) {
		Iterator<Message> messageIterator = outstandingMessages.iterator();
		while (messageIterator.hasNext()) {
			Message msg = messageIterator.next();
			if (msg.msgStage == MsgStage.REQUEST && msg.commState == cs) {
				return true;
			}
		}
		return false;
	}
	
	public boolean containsOtherOutstanding(Message incoming) {
		Message reversedIncoming = incoming.getReverse();
		CommState cs = reversedIncoming.commState;
		Iterator<Message> messageIterator = outstandingMessages.iterator();
		while (messageIterator.hasNext()) {
			Message msg = messageIterator.next();
			if (msg.msgStage == reversedIncoming.msgStage && msg.commState == cs) {
				if (!msg.equals(reversedIncoming))
					return true;
			}
		}
		return false;
	}
	
	public Class<?>[] getParametersType(Object[] parameters) {		
		ArrayList<Class<?>> parametersType = new ArrayList<Class<?>>();
		parametersType.add(Long.class);
		if (parameters != null) {
			for (int i=0; i<parameters.length; i++) {
				parametersType.add(parameters[i].getClass());
			}				
		}
		Class<?>[] returnArray = new Class<?>[parametersType.size()];
		return parametersType.toArray(returnArray);
	}
	
	/*public Long getNextId() {
		return new Long(idCounter.incrementAndGet());
	}*/
	
	/*public <T> T[] concatenate (T[] A, T[] B) {
		
		if (A == null) {
			return B;
		}
		
		if (B == null) {
			return A;
		}
		
	    int aLen = A.length;
	    int bLen = B.length;

	    @SuppressWarnings("unchecked")
		T[] C = (T[]) Array.newInstance(A.getClass().getComponentType(), aLen+bLen);
	    System.arraycopy(A, 0, C, 0, aLen);
	    System.arraycopy(B, 0, C, aLen, bLen);

	    return C;
	}*/
	
}
