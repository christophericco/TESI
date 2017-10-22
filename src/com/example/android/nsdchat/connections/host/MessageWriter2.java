package com.example.android.nsdchat.connections.host;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.Queue;

public class MessageWriter2 implements IMessageWriter {
	
	private Queue<Message> mWriteQueue = new LinkedList<>();
    private Message mMessageInProgress = null;
    private int bytesWritten = 0;

	@Override
	public void write(SocketChannel socketChannel, ByteBuffer byteBuffer) throws IOException {
		System.out.println("write message: " + mMessageInProgress.getPayload().toString());
        int bytesRemaining = this.mMessageInProgress.getLength() - this.bytesWritten;
        if (bytesRemaining <= (byteBuffer.capacity() - byteBuffer.position())) {
            byteBuffer.put(this.mMessageInProgress.getPayload(), this.bytesWritten,
                    this.mMessageInProgress.getLength() - this.bytesWritten);

            this.bytesWritten = 0;
            if (this.mWriteQueue.isEmpty()) {
                this.mMessageInProgress = null; 
            } else {
                this.mMessageInProgress = this.mWriteQueue.poll();
            }

        } else {
            int length = byteBuffer.capacity() - byteBuffer.position();
            byteBuffer.put(this.mMessageInProgress.getPayload(), this.bytesWritten, length);
            this.bytesWritten += length;
        }

        

        byteBuffer.flip();

        System.out.println("write started");
        int bytes = socketChannel.write(byteBuffer);

        while(bytes > 0 && byteBuffer.hasRemaining()){
            bytes = socketChannel.write(byteBuffer);
            System.out.println("write loop");
        }
        
        System.out.println("write finished");
        byteBuffer.clear();
	}

	@Override
	public void enqueue(Message message) {
		if(this.mMessageInProgress == null){
            this.mMessageInProgress = message;
        } else {
            this.mWriteQueue.offer(message);
        }
	}

	@Override
	public boolean isEmpty() {
		return this.mWriteQueue.isEmpty() && this.mMessageInProgress == null;
	}

}
