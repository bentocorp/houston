package com.bento.core;

public class APIResponse<T> {
    public int code;
    public String msg;
    public T ret;

    public static class Track {
        public boolean connected;
    }
}
