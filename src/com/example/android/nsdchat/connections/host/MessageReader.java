package com.example.android.nsdchat.connections.host;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.Queue;


public class MessageReader implements IMessageReader {

    private Queue<Message> mReadQueue = new LinkedList<>();
    private Message mMessageInProgress = null;
    private int totalBytesRead = 0;
    private boolean endOfStreamReached = false;

    @Override
    public void read(SocketChannel socketChannel, ByteBuffer byteBuffer) throws IOException {
        int bytesRead = socketChannel.read(byteBuffer);
        while(bytesRead > 0){
            bytesRead = socketChannel.read(byteBuffer);
        }

        if(bytesRead == -1){
            endOfStreamReached = true;
        }

        byteBuffer.flip();

        if(byteBuffer.remaining() == 0){
            byteBuffer.clear();
            return;
        }

        while(byteBuffer.hasRemaining()) {

            if (this.mMessageInProgress == null) {
                int length = byteBuffer.getInt();
                this.mMessageInProgress = new Message(length);
            }

            if (byteBuffer.remaining() >= (this.mMessageInProgress.getLength() - this.totalBytesRead)) {
                byteBuffer.get(this.mMessageInProgress.getPayload(), this.totalBytesRead,
                        this.mMessageInProgress.getLength() - this.totalBytesRead);
                this.totalBytesRead = 0;
                this.mReadQueue.offer(this.mMessageInProgress);
                this.mMessageInProgress = null;
            } else {
                byteBuffer.get(this.mMessageInProgress.getPayload(), this.totalBytesRead, byteBuffer.remaining());
                this.totalBytesRead += byteBuffer.remaining();
            }

        }

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
