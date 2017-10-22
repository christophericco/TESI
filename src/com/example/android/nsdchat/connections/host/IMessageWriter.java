package com.example.android.nsdchat.connections.host;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;


public interface IMessageWriter {

    void write(SocketChannel socketChannel, ByteBuffer byteBuffer) throws IOException;
    void enqueue(Message message);
    boolean isEmpty();
}
