package com.example.android.nsdchat.connections.host;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;


public interface IMessageReader {

    void read(SocketChannel socketChannel, ByteBuffer byteBuffer) throws IOException;
    boolean isEndOfStreamReached();
    Message dequeue();
}
