package org.bentocorp;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectMapper.DefaultTypeResolverBuilder;
import com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping;
import com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder;

public class JSON {

    private static final ObjectMapper mapper = new ObjectMapper();
/*
    static {
        TypeResolverBuilder<?> builder = new CustomTypeResolverBuilder();
        builder.init(JsonTypeInfo.Id.CLASS, null);
        builder.inclusion(JsonTypeInfo.As.PROPERTY);
        builder.typeProperty("@class");
        mapper.setDefaultTyping(builder);
    }

    static class CustomTypeResolverBuilder extends DefaultTypeResolverBuilder {
        public CustomTypeResolverBuilder() {
            super(DefaultTyping.NON_FINAL);
        }
    }
*/
    public static String serialize(Object o) throws JsonProcessingException {
        return mapper.writeValueAsString(o);
    }

    public static <T> T deserialize(String str, Class<T> clazz) throws Exception {
        return mapper.readValue(str, clazz);
    }

    public static <T> T deserialize(String str, TypeReference<?> type) throws Exception {
        return mapper.readValue(str, type);
    }

    public static void registerModule(Module module) {
        mapper.registerModule(module);
    }
}
