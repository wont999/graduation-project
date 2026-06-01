package com.example.blockly_executor_service.dao;

import org.graalvm.polyglot.HostAccess;

import java.util.HashMap;
import java.util.Map;

public class JSResult {
    private final Map<String, Object> data;

    public JSResult(Map<String, Object> source) {
        this.data = new HashMap<>(source);
    }

    @HostAccess.Export
    public Object get(String key) {
        return data.get(key);
    }

    @HostAccess.Export
    public int size() {
        return data.size();
    }

    public Map<String, Object> toMap() {
        return new HashMap<>(data);
    }
}