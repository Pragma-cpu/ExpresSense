package com.example.smiledetection;

import android.util.Log;

import java.util.concurrent.Semaphore;

public class CircularBuffer {
    private int size;
    private int front;
    private int rear;
    private int countOfElements;
    private short[][] soundBits;
    private Semaphore mutex = new Semaphore(1);
    private final String TAG = "CircularBuffer";

    CircularBuffer(int size) {
        this.size = size;
        this.front = front;
        this.soundBits = new short[size][0x800];
        front = rear = countOfElements = 0;
    }

    public int insertBuffer(short data[]) {
        if(countOfElements == size) {
            return -1;
        }
        try {
            mutex.acquire();
            countOfElements++;
        } catch (InterruptedException e) {
            Log.d(TAG, e.getMessage());
        }
        finally {
            mutex.release();
        }
        soundBits[front] = data;
        front = (front + 1) % size;
        return 0;
    }

    public short[] consumeBuffer() {
        short [] res = new short[0];
        if(countOfElements == 0) {
            return res;
        }
        try {
            mutex.acquire();
            countOfElements--;
        }
        catch (InterruptedException e) {

        }
        finally {
            mutex.release();
        }
        res = soundBits[rear];
        rear = (rear + 1) % size;
        return res;
    }
}
