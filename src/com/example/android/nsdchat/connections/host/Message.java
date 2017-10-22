package com.example.android.nsdchat.connections.host;


public class Message {

    private int length = 0;
    private byte[] payload = null;
    
    public String socketID;
    
    public long timestamp;

    public Message(int length){
        this.length = length;
        this.payload = new byte[length];
        this.timestamp = System.currentTimeMillis();
    }

    public byte[] getPayload() {
        return this.payload;
    }

    public void setPayload(byte[] payload) {
        this.payload = payload;
        this.length = payload.length;
    }

    public int getLength() {
        return this.length;
    }

}
