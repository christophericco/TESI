package com.example.android.nsdchat.connections.host;

/**
 * Created by chris on 12/04/17.
 */

public class MessageWriterFactory {

    public IMessageWriter createMessageWriter(){
        return new MessageWriter2();
    }
}
