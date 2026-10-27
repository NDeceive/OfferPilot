package com.zhimian.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.config.JudgeProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class Judge0ClientTest {
    @Test void capabilityChecksActualRuntimeAndRequestsDisableNetwork() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var json=new ObjectMapper();var calls=new ArrayList<Map<String,Object>>();
        server.createContext("/languages/100",exchange -> {
            byte[] body="{\"name\":\"Java (OpenJDK 17.0.20.1)\"}".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
        });
        server.createContext("/submissions",exchange -> {
            var input=json.readValue(exchange.getRequestBody(),Map.class);calls.add(input);
            String body="{\"token\":\"fixture\",\"status\":{\"id\":3,\"description\":\"Accepted\"},\"stdout\":\""+Base64.getEncoder().encodeToString("17.0.20.1".getBytes())+"\",\"stderr\":null,\"compile_output\":null}";
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });
        server.start();
        try {
            var config=new JudgeProperties();config.setEnabled(true);config.setBaseUrl("http://127.0.0.1:"+server.getAddress().getPort());config.setLanguageIds(Map.of("java",100));
            var client=new Judge0Client(config,json);assertEquals(true,client.capability().get("ready"));assertEquals("17.0.20.1",client.capability().get("runtimeVersion"));assertEquals(1,calls.size());
            assertEquals(false,calls.get(0).get("enable_network"));assertEquals(64,calls.get(0).get("max_file_size"));assertEquals(6,calls.get(0).get("wall_time_limit"));
            config.setJavaVersion("21");var mismatch=new Judge0Client(config,json);assertEquals(false,mismatch.capability().get("ready"));
        } finally {server.stop(0);}
    }
}
