package com.example.android.nsdchat.connections.host;


import java.nio.ByteBuffer;

class MessageBuffer {

    private static final int KB = 1024;
    private static final int MB = 1024 * KB;

    private static final int CAPACITY_SMALL = KB;
    private static final int CAPACITY_MEDIUM = 128 * KB;
    private static final int CAPACITY_LARGE = MB;

    //package scope (default) - so they can be accessed from unit tests.
    private byte[] smallMessageBuffer = new byte[1024 * CAPACITY_SMALL]; //1024 x 1KB messages = 1MB.
    private byte[] mediumMessageBuffer = new byte[32 * CAPACITY_MEDIUM]; //32 x 128KB messages = 4MB.
    private byte[] largeMessageBuffer = new byte[4 * CAPACITY_LARGE];    //4 *    1MB messages = 4MB.

    private QueueIntFlip smallMessageBufferFreeBlocks = new QueueIntFlip(1024); //1024 free sections
    private QueueIntFlip mediumMessageBufferFreeBlocks = new QueueIntFlip(32);   //32 free sections
    private QueueIntFlip largeMessageBufferFreeBlocks = new QueueIntFlip(4);    //4 free sections

    public MessageBuffer() {
        //add all free sections to all free section queues.
        for(int i = 0; i < smallMessageBuffer.length; i += CAPACITY_SMALL){
            this.smallMessageBufferFreeBlocks.put(i);
        }
        for(int i = 0; i < mediumMessageBuffer.length; i += CAPACITY_MEDIUM){
            this.mediumMessageBufferFreeBlocks.put(i);
        }
        for(int i = 0; i < largeMessageBuffer.length; i += CAPACITY_LARGE){
            this.largeMessageBufferFreeBlocks.put(i);
        }
    }

    public Message getMessage() {
        int nextFreeSmallBlock = this.smallMessageBufferFreeBlocks.take();

        if(nextFreeSmallBlock == -1) return null;

        return new Message(this, this.smallMessageBuffer, nextFreeSmallBlock, CAPACITY_SMALL);
    }

    public boolean expandMessage(Message message){
        if(message.capacity == CAPACITY_SMALL){
            return moveMessage(message, CAPACITY_MEDIUM, this.smallMessageBufferFreeBlocks,
                    this.mediumMessageBufferFreeBlocks, this.mediumMessageBuffer);
        } else if(message.capacity == CAPACITY_MEDIUM){
            return moveMessage(message, CAPACITY_LARGE, this.mediumMessageBufferFreeBlocks,
                    this.largeMessageBufferFreeBlocks, this.largeMessageBuffer);
        } else {
            return false;
        }
    }

    private boolean moveMessage(Message message, int newCapacity, QueueIntFlip srcQueue,
                                QueueIntFlip destQueue, byte[] destArray) {

        int nextFreeBlock = destQueue.take();
        if(nextFreeBlock == -1){
            return false;
        }

        System.arraycopy(message.sharedArray, message.offset,
                destArray, nextFreeBlock, message.length);

        srcQueue.put(message.offset); //free smaller block after copy

        message.sharedArray = destArray;
        message.offset = nextFreeBlock;
        message.capacity = newCapacity;

        return true;
    }

    public class Message {

        private MessageBuffer messageBuffer = null;

        private String socketId; // the id of source socket or destination socket, depending on whether is going in or out.

        private byte[] sharedArray = null;
        private int offset = 0; //offset into sharedArray where this message data starts.
        private int capacity = 0; //the size of the section in the sharedArray allocated to this message.
        private int length = 0; //the number of bytes used of the allocated section.

        private Message(MessageBuffer messageBuffer, byte[] sharedArray, int offset, int capacity){
            this.messageBuffer = messageBuffer;
            this.sharedArray = sharedArray;
            this.offset = offset;
            this.capacity = capacity;
        }
        
        public String getSocketId(){
        	return socketId;
        }

        public int writeToMessage(ByteBuffer byteBuffer){
            int remaining = byteBuffer.remaining();

            while(this.length + remaining > capacity){
                if(!this.messageBuffer.expandMessage(this)) {
                    return -1;
                }
            }

            int bytesToCopy = Math.min(remaining, this.capacity - this.length);
            byteBuffer.get(this.sharedArray, this.offset + this.length, bytesToCopy);
            this.length += bytesToCopy;

            return bytesToCopy;
        }

    }

}
