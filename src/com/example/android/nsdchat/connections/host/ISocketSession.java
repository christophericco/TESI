package com.example.android.nsdchat.connections.host;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public interface ISocketSession {
	
	SocketChannel getChannel();
	String getSocketId();
	void write(ByteBuffer buffer) throws IOException;
	void read(ByteBuffer buffer) throws IOException;
	void enqueue(Message message);
	Message dequeue();
	boolean isEmpty();
	boolean isEndOfStreamReached();

}
