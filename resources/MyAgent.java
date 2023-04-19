package PACKAGENAME;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MyAgent {
    private static final Logger logger = LoggerFactory.getLogger(MyAgent.class);
    public static HashMap<String, Object> handleInstrumentedLocals(String body, long startAt, HashMap<String, Object> tInfo, ArrayList<Object> arrayList) {
        HashMap<String, Object> map = new HashMap<>(4);
        map.put("time", Instant.now());
        map.put("name", tInfo.get("this"));
        map.put("body", body);

        List<HashMap<String, Object>> attributes = new ArrayList<>();
        attributes.add(
                new HashMap<String, Object>(1) {{
                    put("startAt", startAt);
                }}
        );
        attributes.add(
                new HashMap<String, Object>(1) {{
                    put("thread", tInfo);
                }}
        );
        if (arrayList != null) {
            attributes.add(
                    new HashMap<String, Object>(1) {{
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
                    }}
            );
        }
        map.put("attributes", attributes);
        return map;
    }

    public static HashMap<String, Object> getMethodStackInfo(Thread currentThread) {
        StackTraceElement[] elem = currentThread.getStackTrace();
        HashMap<String, Object> map = new HashMap<String, Object>(4) {{
            put("thread_name", currentThread.getName());
            put("thread_id", String.valueOf(currentThread.getId()));
        }};

        try {
            map.put("this", String.format("%s.%s", elem[2].getClassName(), elem[2].getMethodName()));
        } catch (Exception ignored) {
        }
        try {
            map.put("parent", String.format("%s.%s", elem[3].getClassName(), elem[3].getMethodName()));
        } catch (Exception ignored) {
        }
        return map;
    }

    public static void report(HashMap<String, Object> map) {
        logger.info(new JSONObject(map).toString());
        map.clear();
    }
}
