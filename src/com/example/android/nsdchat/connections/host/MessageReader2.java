package com.example.android.nsdchat.connections.host;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.Queue;

public class MessageReader2 implements IMessageReader {
	
	private Queue<Message> mReadQueue = new LinkedList<>();
    private Message mMessageInProgress = null;
    private int totalBytesRead = 0;
    private boolean endOfStreamReached = false;

	@Override
	public void read(SocketChannel socketChannel, ByteBuffer byteBuffer) throws IOException {
		System.out.println("started read");
		int bytesRead = socketChannel.read(byteBuffer);
        while(bytesRead > 0){
        	System.out.println("loop read");
            bytesRead = socketChannel.read(byteBuffer);
        }
        System.out.println("finished read");
        if(bytesRead == -1){
            endOfStreamReached = true;
        }

        byteBuffer.flip();

        if(byteBuffer.remaining() == 0){
            byteBuffer.clear();
            return;
        }
        
        System.out.println("remaining: " + byteBuffer.remaining());
        System.out.println("limit: " + byteBuffer.limit());
        System.out.println("capacity: " + byteBuffer.capacity());
        System.out.println("current position: " + byteBuffer.position());

        while(byteBuffer.hasRemaining()) {

            if (this.mMessageInProgress == null) {
            	System.out.println("read new message");
                this.mMessageInProgress = new Message(byteBuffer.limit());
                this.totalBytesRead = 0;
            }

            if (byteBuffer.remaining() >= (this.mMessageInProgress.getLength() - this.totalBytesRead)) {
                byteBuffer.get(this.mMessageInProgress.getPayload(), this.totalBytesRead,
                        this.mMessageInProgress.getLength() - this.totalBytesRead);
                this.totalBytesRead += byteBuffer.limit() - byteBuffer.remaining(); 
            }
            System.out.println("loop");
        }
        
        this.mReadQueue.offer(this.mMessageInProgress);
        this.mMessageInProgress = null;

        byteBuffer.clear();
	}

	@Override
    public boolean isEndOfStreamReached(){
        return this.endOfStreamReached;
    }

    @Override
    public Message dequeue(){
        return this.mReadQueue.poll();
    }

}
