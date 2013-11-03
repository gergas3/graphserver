package com.gergas;

import java.io.Serializable;

public class Message implements Serializable {

	private static final long serialVersionUID = 1L;
	public CommState commState;
	public MsgStage msgStage;
	public Edge edge;
	public Object[] parameters; 
	
	public Message() {
		commState = CommState.GENERAL;
		msgStage = MsgStage.NONE;
		edge = new Edge();
	}
		
	public Message getReverse() {
		Message msg = new Message();
		msg.edge = this.edge;
		if (this.msgStage == MsgStage.REQUEST) {
			msg.msgStage = MsgStage.RESPONSE;
		} else if (this.msgStage == MsgStage.RESPONSE) {
			msg.msgStage = MsgStage.REQUEST;
		} else {
			msg.msgStage = this.msgStage;
		}
		msg.commState = this.commState;
		return msg;
	}

	@Override
	public int hashCode() {
		return (commState.getName() + msgStage.getName() + Integer.toString(edge.hashCode())).hashCode();
    }
	
	@Override
    public boolean equals(final Object obj) {
		if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;

        final Message other = (Message) obj;
        if (commState == other.commState && msgStage == other.msgStage && edge.equals(other.edge)) {
        	return true;
        }
        return false;
    }
	
}
