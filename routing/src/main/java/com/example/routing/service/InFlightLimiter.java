package com.example.routing.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class InFlightLimiter {

    private final Semaphore semaphore;
    private final AtomicInteger inFlight = new AtomicInteger(0);
    private final AtomicInteger rejected = new AtomicInteger(0);
    private final int maxInFlight;

    public InFlightLimiter(
            @Value("${routing.max-in-flight:150}") int maxInFlight,
            MeterRegistry registry) {
        this.maxInFlight = maxInFlight;
        this.semaphore = new Semaphore(maxInFlight);

        Gauge.builder("routing_inflight_requests", inFlight, AtomicInteger::get)
                .description("Currently in-flight procedure requests")
                .register(registry);
        Gauge.builder("routing_inflight_max", () -> maxInFlight)
                .description("Max allowed in-flight requests")
                .register(registry);
        Gauge.builder("routing_rejected_total", rejected, AtomicInteger::get)
                .description("Requests rejected with 429")
                .register(registry);
    }

    public boolean tryAcquire() {
        boolean ok = semaphore.tryAcquire();
        if (ok) {
            inFlight.incrementAndGet();
        } else {
            rejected.incrementAndGet();
        }
        return ok;
    }

    public void release() {
        semaphore.release();
        inFlight.decrementAndGet();
    }

    public int getMaxInFlight() {
        return maxInFlight;
    }
}