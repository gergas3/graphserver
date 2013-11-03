package com.gergas;

public enum MsgStage {
	REQUEST("req"),
	RESPONSE("res"),
	NONE("");
	
	private String name;
    MsgStage(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
