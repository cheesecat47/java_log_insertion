package com.finedigital;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class MyAgent {
    private static final Logger logger = LoggerFactory.getLogger(MyAgent.class);

    public static HashMap<String, Object> handleInstrumentedLocals(String body, long startAt, HashMap<String, Object> tInfo, ArrayList<Object> arrayList) {
        HashMap<String, Object> map = new HashMap<>(4);
        map.put("time", Instant.now());
        map.put("name", tInfo.get("this"));
        map.put("body", body);

        List<HashMap<String, Object>> attributes = new ArrayList<>();
        attributes.add(new HashMap<String, Object>(1) {{
            put("startAt", startAt);
        }});
        attributes.add(new HashMap<String, Object>(1) {{
            put("thread", tInfo);
        }});
        if (arrayList != null) {
            attributes.add(new HashMap<String, Object>(1) {{
                switch (body) {
                    case "Method Start":
                        put("parameters", arrayList);
                        break;
                    case "Method End":
                        put("return", arrayList);
                        break;
                    case "From PrintStream":
                        put("print", arrayList);
                        break;
                }
            }});
        }
        map.put("attributes", attributes);
        attributes = null;

        return map;
    }

    public static HashMap<String, Object> getMethodStackInfo(Thread currentThread) {
        StackTraceElement[] stackTraceElements = currentThread.getStackTrace();
        HashMap<String, Object> map = new HashMap<>(4);
        map.put("thread_name", currentThread.getName());
        map.put("thread_id", String.valueOf(currentThread.getId()));

        try {
            map.put("this", String.format("%s.%s",
                    stackTraceElements[2].getClassName(), stackTraceElements[2].getMethodName()));
        } catch (Exception ignored) { /*e.printStackTrace();*/ }
        try {
            map.put("parent", String.format("%s.%s",
                    stackTraceElements[3].getClassName(), stackTraceElements[3].getMethodName()));
        } catch (Exception ignored) { /*e.printStackTrace();*/ }
        stackTraceElements = null;
        return map;
    }

    public static void report(HashMap<String, Object> map) {
        logger.info(new JSONObject(map).toString());
        map.clear();
        map = null;
    }
}
