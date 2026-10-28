package com.fcaj.hackathon.aijob;

import java.util.concurrent.*;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/** One bounded long-poll thread, separate from business timers and the outbox scheduler. */
@Component
@ConditionalOnProperty(name="app.ai.jobs.enabled", havingValue="true")
public class ResultPoller implements SmartLifecycle {
    private final JobDelivery delivery;
    private final boolean enabled;
    private volatile boolean running;
    private ScheduledExecutorService executor;
    public ResultPoller(JobDelivery delivery,@Value("${app.ai.jobs.delivery.enabled:true}") boolean enabled) {
        this.delivery=delivery;this.enabled=enabled;
    }
    @Override public synchronized void start() {
        if(running || !enabled) return;
        running=true;executor=Executors.newScheduledThreadPool(2,r -> {var t=new Thread(r,"ai-delivery");t.setDaemon(true);return t;});
        executor.scheduleWithFixedDelay(() -> {
            try { delivery.dispatchTick(); }
            catch(RuntimeException failed) { LoggerFactory.getLogger(ResultPoller.class).warn("ai_outbox dispatch=retry reason=DEPENDENCY_UNAVAILABLE"); }
        },2,2,TimeUnit.SECONDS);
        executor.submit(() -> {
            while(running && !Thread.currentThread().isInterrupted()) {
                try { delivery.pollOnce(); }
                catch(RuntimeException failed) {
                    if(running) LoggerFactory.getLogger(ResultPoller.class).warn("ai_results poll=retry reason=DEPENDENCY_UNAVAILABLE");
                    try { Thread.sleep(5000); } catch(InterruptedException stop) { Thread.currentThread().interrupt(); }
                }
            }
        });
    }
    @Override public synchronized void stop() {
        running=false;
        if(executor!=null) executor.shutdownNow();
        if(executor!=null) try { executor.awaitTermination(5,TimeUnit.SECONDS); }
        catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }
    @Override public boolean isRunning() { return running; }
    @Override public int getPhase() { return Integer.MAX_VALUE-100; }
}
