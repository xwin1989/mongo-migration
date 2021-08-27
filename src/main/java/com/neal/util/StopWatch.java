package com.neal.util;

/**
 * @author Neal
 * @date 2021/8/27
 */
public class StopWatch {
    private long start;

    public StopWatch() {
        reset();
    }

    public void reset() {
        start = System.nanoTime();
    }

    public long elapsed() {
        long end = System.nanoTime();
        return end - start;
    }
}
