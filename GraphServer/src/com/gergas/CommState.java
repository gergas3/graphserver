package com.gergas;

public enum CommState {
	GENERAL(""), 
	HANDSHAKE("Handshake"),
	SEND_VERTEX_DATA("SendVertexData"), 
	SUPERSTEPCOMM("SuperStepComm"),
	SUPERSTEPCALC("SuperStepCalc"),
	SEND_VALUE("SendValue"),
	PRINT_RESULT("PrintResult");

    private String name;
    CommState (String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
