package com.gergas;

import java.io.Serializable;

public class Edge implements Serializable {

	private static final long serialVersionUID = 1L;
	
	public long vIdFrom;
	public long vIdTo;

	public Edge() {
		vIdFrom = 0;
		vIdTo = 0;
	}

	@Override
	public int hashCode() {
		return (int) (vIdFrom *1000 + vIdTo);
    }
	
	@Override
    public boolean equals(final Object obj) {
		if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;

        final Edge other = (Edge) obj;
        if (vIdFrom == other.vIdFrom && vIdTo == other.vIdTo) {
        	return true;
        }
        return false;
    }
	
}
