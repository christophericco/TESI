package it.cricco.mqtt;


import java.io.IOException;
import java.util.logging.Logger;

public class Server {

    private static final String CLASS_NAME = Server.class.getSimpleName();
    private static final Logger log = Logger.getLogger(CLASS_NAME);

    private int mPort;


    private IServer mNettyServer = null;

    public int getPort(){
        return mPort;
    }


    public void startServer(int port) throws IOException {
        if(port <= 0){
            throw new IllegalArgumentException("port is not valid.");
        }

        mPort = port;
        log.info("Using port: " + port);
        log.info("Server starting...");

        mNettyServer = new NettyServer();
        mNettyServer.initialize(port);
        log.info("Server started.");
    }

    public void stopServer() {
    	log.info("Server stopping...");
        mNettyServer.stop();
        log.info("Server stopped");
    }
}
