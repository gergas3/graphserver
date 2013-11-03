package com.gergas;

public enum Algorithm {
	NONE(""),
	MAXIMUM_VALUE("maxval"), 
	MINIMUM_VALUE("minval"),
	PAGERANK("pr");
	
	private String name;
	Algorithm (String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
    
    public static Algorithm fromName(String text) {
        if (text != null) {
          for (Algorithm alg: Algorithm.values()) {
            if (text.equalsIgnoreCase(alg.getName())) {
              return alg;
            }
          }
        }
        return null;
      }
}
