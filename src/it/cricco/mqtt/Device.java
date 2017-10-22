package it.cricco.mqtt;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.logging.Logger;

import io.netty.handler.codec.mqtt.MqttMessage;

public class Device {

    private static final String CLASS_NAME = Device.class.getSimpleName();
    private static final Logger log = Logger.getLogger(CLASS_NAME);

    private InetSocketAddress mHost = null;
    private IClient mClient = null;

    private volatile boolean isConnected = false;

    public InetAddress getHost() {
        return mHost != null ? mHost.getAddress() : null;
    }

    public boolean isConnected(){
        return isConnected;
    }

    public void connect(InetSocketAddress address) {
        if(address == null){
            throw new RuntimeException("address is null");
        }

        log.info("Connect to " + address.getAddress().getHostAddress() + ":" + address.getPort());

        mHost = address;
        mClient = new MqttClient();

        try {
            mClient.connect(address.getAddress(), address.getPort());
            isConnected = true;
        } catch (InterruptedException e) {
            log.severe("Connect failed: " + e.getLocalizedMessage());
        }
    }

    public void sendMessage(MqttMessage msg){
        if(mClient != null){
            try {
                mClient.sendMessage(msg);
            } catch (InterruptedException e) {
            	log.severe("Send message failed: " + e.getLocalizedMessage());
            }
        }
    }


    public void disconnect(){
        if(mClient != null){
            try {
                mClient.disconnect();
            } catch (InterruptedException e) {
            	log.severe("Close connection failed: " + e.getLocalizedMessage());
            }

            isConnected = false;
        }
    }
}
