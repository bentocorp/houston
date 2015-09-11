package org.bentocorp.api;

import com.fasterxml.jackson.databind.ObjectMapper;

public class APIResponse<T> {
    private static ObjectMapper _mapper = new ObjectMapper();
    public int code;
    public String msg;
    public T ret;
    public APIResponse(int code, String msg, T ret) {
        this.code = code;
        this.msg = msg;
        this.ret = ret;
    }
    public static String error(int code, String msg) throws Exception {
        return _mapper.writeValueAsString(new APIResponse<Object>(code, msg, null));
    }

    public static String success(Object ret) throws Exception {
        return _mapper.writeValueAsString(new APIResponse<Object>(0, "", ret));
    }
}
