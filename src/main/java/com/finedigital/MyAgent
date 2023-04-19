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
    private static final AtomicInteger systemAbnormality = new AtomicInteger(0);

    private static Timer scheduler = null;
    private static final HashMap<String, HashMap<String, Boolean>> edgeMap = new HashMap<>();
    private static final String API_URL = "http://155.230.118.234:24400/api/";
    private static String tsvFilePath = System.getProperty("user.dir") + "/output.edges.tsv";

    private static void initTask() {
        try (BufferedReader br = new BufferedReader(new FileReader(tsvFilePath))) {
            String line;

            while ((line = br.readLine()) != null) {
                List<String> values = Arrays.asList(line.split("\t"));
                String u = (String) values.get(0);
                String v = (String) values.get(1);
                edgeMap.computeIfAbsent(u, k -> new HashMap<>());
                edgeMap.get(u).put(v, true);
            }
            line = null;
        } catch (Exception ignored) { /*e.printStackTrace();*/ }

        scheduler = new Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                // https://bibi6666667.tistory.com/132
                // https://huisam.tistory.com/entry/completableFuture#Exceptionally
                Executor executor = Executors.newFixedThreadPool(1);
                CompletableFuture.runAsync(() -> {
                    HttpURLConnection conn = null;
                    try {
                        conn = (HttpURLConnection) new URL(API_URL).openConnection();
                        conn.setRequestMethod("GET");
                        conn.setRequestProperty("Content-type", "application/json");
                        conn.setDoOutput(true);

                        // Response JSON format
                        // {
                        //     "System Abnormality": sa[1],
                        //         "Latest Update": sa[0]
                        // }
                        StringBuilder sb = new StringBuilder();
                        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                        while (br.ready()) {
                            sb.append(br.readLine());
                        }
                        br.close();
                        br = null;

                        JSONObject response = new JSONObject(sb.toString());
                        systemAbnormality.set((int) response.get("System Abnormality"));
                        sb = null;
                        response = null;
                    } catch (Exception ignored) {
                    } finally {
                        if (conn != null) { conn.disconnect(); }
                        conn = null;
                    }
                }, executor);
            }
        };
        scheduler.scheduleAtFixedRate(task, 1000, 1000 * 10);
    }

    public static HashMap<String, Object> handleInstrumentedLocals(String body, int level, long startAt, HashMap<String, Object> tInfo, ArrayList<Object> arrayList) {
        HashMap<String, Object> map = new HashMap<>(5);
        map.put("time", Instant.now());
        map.put("severityNumber", level);
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

    public static boolean isEdgeInCG(HashMap<String, Object> map) {
        String u = (String) map.get("parent");
        if (u == null) { return false; }

        HashMap<String, Boolean> nu = edgeMap.get(u);
        if (nu == null) { return false; }
        u = null;

        String v = (String) map.get("this");
        if (v == null) { return false; }

        return nu.get(v) != null;
    }

    public static boolean checkSystemAbnormality(int methodLevel) {
        return methodLevel <= systemAbnormality.get();
    }

    public static void report(HashMap<String, Object> map) {
        if (scheduler == null) {
            initTask();
        }
        logger.info(new JSONObject(map).toString());
        map.clear();
        map = null;
    }
}
