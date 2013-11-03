graphserver
===========

Simple implementation of Google's Pregel distributed graph processing system for demonstration purposes.


This implementation can compute pagerank, minimum and maximum values of nodes. The underlying graph is randomly generated.

Usage (on Windows):

1. Start the server using the command 
   java -jar "your_path\GraphServer.jar" server 9321 10 4 pr 2
   Parameters explained:
   server   : tells to start a server
   9321     : port number
   10       : number of vertices in the generated random graph
   4        : number of outgoing edges per vertex
   pr       : name of algorithm - eligible values are "pr", "minval", "maxval"
   2        : number of clients to wait for before starting the algorithm

2. Start the specified number of clients (in this case start 2 client instances) using the command
   java -jar "your_path\GraphServer.jar" client localhost 9321
   Parameters explained:
   client   : tells to start a client
   localhost: IP address of server
   9321     : port number on server


The algorithm stops after convergence criteria are met or after 500 supersteps.
