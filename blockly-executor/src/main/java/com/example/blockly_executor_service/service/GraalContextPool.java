package com.example.blockly_executor_service.service;

import com.example.common.exception.ExceptionMessages;
import com.example.common.exception.RequestCapacityExceededException;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class GraalContextPool {

    private final Engine engine = Engine.newBuilder("js")
            .option("engine.WarnInterpreterOnly", "false")
            .build();

    private final BlockingQueue<Context> pool;
    private final int size;
    private final boolean poolEnabled;

    public GraalContextPool(
            @Value("${blockly.graal.pool-size:6}") int size,
            @Value("${blockly.graal.pool-enabled:true}") boolean poolEnabled) {
        this.poolEnabled = poolEnabled;
        if (poolEnabled) {
            this.size = size;
            this.pool = new ArrayBlockingQueue<>(size);
            for (int i = 0; i < size; i++) {
                pool.offer(createContext());
            }
            log.info("GraalContextPool initialized with {} reusable contexts", size);
        } else {
            this.size = 0;
            this.pool = null;
            log.info("GraalContextPool DISABLED — new context per request");
        }
    }

    private Context createContext() {
        return Context.newBuilder("js")
                .engine(engine)
                .allowHostAccess(HostAccess.EXPLICIT)
                .allowHostClassLookup(className -> false)
                .build();
    }

    public Context acquire() throws InterruptedException {
        if (!poolEnabled) {
            return createContext();
        }
        Context ctx = pool.poll(30, TimeUnit.SECONDS);
        if (ctx == null) {
            throw new RequestCapacityExceededException(
                    String.format(ExceptionMessages.REQUEST_CAPACITY_EXCEEDED, size));
        }
        return ctx;
    }

    public void release(Context ctx) {
        if (ctx == null) return;
        if (!poolEnabled) {
            ctx.close(true);
            return;
        }
        try {
            ctx.getBindings("js").removeMember("DB");
        } catch (Exception ignored) { }
        if (!pool.offer(ctx)) {
            ctx.close(true);
        }
    }

    public int size() { return size; }

    @PreDestroy
    public void shutdown() {
        if (pool != null) {
            Context c;
            while ((c = pool.poll()) != null) {
                try { c.close(true); } catch (Exception ignored) { }
            }
        }
        engine.close();
    }
}