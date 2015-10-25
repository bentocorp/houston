package org.bentocorp;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectMapper.DefaultTypeResolverBuilder;
import com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping;
import com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder;

import java.io.IOException;

public class Json {

    protected static final ObjectMapper mapper = new ObjectMapper();

    static {
//        TypeResolverBuilder<?> builder = new DefaultTypeResolverBuilder(DefaultTyping.NON_FINAL);
//        builder.init(JsonTypeInfo.Id.CLASS, null);
//        builder.inclusion(JsonTypeInfo.As.PROPERTY);
//        builder.typeProperty("@class");
//        mapper.setDefaultTyping(builder);
    }

    public static String stringify(Object value) throws JsonProcessingException {
        return mapper.writeValueAsString(value);
    }

    public static <T> T parse(String str, Class<T> clazz) throws IOException {
        return mapper.readValue(str, clazz);
    }

    public static <T> T parse(String str, TypeReference<?> type) throws IOException {
        return mapper.readValue(str, type);
    }

    public static <T> T parse(String str, JavaType type) throws IOException {
        return mapper.readValue(str, type);
    }
}
