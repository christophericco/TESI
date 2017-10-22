package com.example.android.nsdchat.connections.host;


public class QueueIntFlip {

    private int[] elements = null;

    private int capacity = 0;
    private int writePos = 0;
    private int readPos  = 0;
    private boolean flipped = false;

    public QueueIntFlip(int capacity) {
        this.capacity = capacity;
        this.elements = new int[capacity];
    }

    public void reset() {
        this.writePos = 0;
        this.readPos  = 0;
        this.flipped  = false;
    }

    public int available() {
        if(!flipped){
            return writePos - readPos;
        }
        return capacity - readPos + writePos;
    }

    public int remainingCapacity() {
        if(!flipped){
            return capacity - writePos;
        }
        return readPos - writePos;
    }

    public boolean put(int element){
        if(!flipped){
            if(writePos == capacity){
                writePos = 0;
                flipped = true;

                if(writePos < readPos){
                    elements[writePos++] = element;
                    return true;
                } else {
                    return false;
                }
            } else {
                elements[writePos++] = element;
                return true;
            }
        } else {
            if(writePos < readPos ){
                elements[writePos++] = element;
                return true;
            } else {
                return false;
            }
        }
    }

    public int take() {
        if(!flipped){
            if(readPos < writePos){
                return elements[readPos++];
            } else {
                return -1;
            }
        } else {
            if(readPos == capacity){
                readPos = 0;
                flipped = false;

                if(readPos < writePos){
                    return elements[readPos++];
                } else {
                    return -1;
                }
            } else {
                return elements[readPos++];
            }
        }
    }
}
