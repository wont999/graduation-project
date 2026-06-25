package com.example.blockly_executor_service.service.execution;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.List;
import java.util.Map;

public class ToJSArray {

    private final Context context;

    public ToJSArray(Context context) {
        this.context = context;
    }

    @HostAccess.Export
    public Value toArray(Object javaList) {
        if (!(javaList instanceof List)) {
            return context.eval("js", "null");
        }
        List<?> list = (List<?>) javaList;
        StringBuilder js = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) js.append(",");
            js.append(toJSValue(list.get(i)));
        }
        js.append("]");
        return context.eval("js", js.toString());
    }

    @HostAccess.Export
    public Value toObj(Object javaMap) {
        if (!(javaMap instanceof Map)) {
            return context.eval("js", "null");
        }
        return context.eval("js", toJSObject((Map<String, Object>) javaMap));
    }

    private String toJSValue(Object val) {
        if (val == null) return "null";
        if (val instanceof Map) return toJSObject((Map<String, Object>) val);
        if (val instanceof List) {
            List<?> nested = (List<?>) val;
            StringBuilder arr = new StringBuilder("[");
            for (int i = 0; i < nested.size(); i++) {
                if (i > 0) arr.append(",");
                arr.append(toJSValue(nested.get(i)));
            }
            arr.append("]");
            return arr.toString();
        }
        if (val instanceof String) {
            String escaped = val.toString().replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r");
            return "'" + escaped + "'";
        }
        if (val instanceof Number || val instanceof Boolean) {
            return val.toString();
        }
        return "'" + val.toString().replace("'", "\\'") + "'";
    }

    private String toJSObject(Map<String, Object> map) {
        StringBuilder obj = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) obj.append(",");
            first = false;
            String key = entry.getKey();
            if (!key.matches("^[a-zA-Z_$][a-zA-Z0-9_$]*$")) {
                obj.append("'").append(key.replace("'", "\\'")).append("'");
            } else {
                obj.append(key);
            }
            obj.append(":").append(toJSValue(entry.getValue()));
        }
        obj.append("}");
        return obj.toString();
    }
}
