package com.example.android.nsdchat.connections.host;

/**
 * Created by chris on 12/04/17.
 */

public interface IMessageRouter {

    String routing(String sessionId);
    
    /** return port server to allow establish connection with server  */
    int addIncomingClient(String sessionId, int serverPort);
    int addOutcomingClient(String sessionId, int serverPort);
    String removeClient(String sessionId);
    void addService(int port, int newPort);
    void removeService(int port);
}
