package com.example.android.nsdchat.connections.host;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class SocketSession implements ISocketSession {

	private SocketChannel socketChannel = null;
    private IMessageReader messageReader = null;
    private IMessageWriter messageWriter = null;

    private String socketId = null;
	
	public SocketSession(SocketChannel channel, IMessageReader messageReader, IMessageWriter messageWriter) {
		this.socketChannel = channel;
        this.messageReader = messageReader;
        this.messageWriter = messageWriter;
        this.socketId = channel.socket().getPort() + "." + channel.socket().getLocalPort();
	}

	@Override
	public String getSocketId() {
		return this.socketId;
	}

	@Override
	public SocketChannel getChannel() {
		return this.socketChannel;
	}

	@Override
	public void enqueue(Message message) {
		this.messageWriter.enqueue(message);		
	}

	@Override
	public boolean isEmpty() {
		return this.messageWriter.isEmpty();
	}

	@Override
	public void write(ByteBuffer buffer) throws IOException {
		this.messageWriter.write(this.socketChannel, buffer);
	}

	@Override
	public void read(ByteBuffer buffer) throws IOException {
		this.messageReader.read(socketChannel, buffer);
	}

	@Override
	public Message dequeue() {
		Message result = this.messageReader.dequeue();
		if(result != null){
			result.socketID = socketId;
		}
		return result;
	}

	@Override
	public boolean isEndOfStreamReached() {
		return this.messageReader.isEndOfStreamReached();
	}

}
