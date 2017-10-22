package com.example.android.nsdchat.connections.host;

/**
 * Created by chris on 12/04/17.
 */

public class MessageReaderFactory {
    public IMessageReader createMessageReader(){
        return new MessageReader2();
    }
}
