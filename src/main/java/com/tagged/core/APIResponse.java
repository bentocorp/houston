package com.tagged.core;

public class APIResponse<T> {
    public int code;
    public String msg;
    public T ret;

    public static class Track {
        public boolean connected;
    }
}
