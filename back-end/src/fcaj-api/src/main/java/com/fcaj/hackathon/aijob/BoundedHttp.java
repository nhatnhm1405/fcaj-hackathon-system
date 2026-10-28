package com.fcaj.hackathon.aijob;

import java.io.ByteArrayOutputStream;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import org.springframework.http.HttpStatus;

/** No blocking body stream: completion covers all body bytes, cancellation reaches the subscription. */
final class BoundedHttp {
    private BoundedHttp() {}
    static byte[] fetch(HttpClient client, HttpRequest request, int max, Duration timeout) {
        if (timeout.isNegative() || timeout.isZero()) throw timedOut();
        var subscriber = new LimitedSubscriber(max);
        var response = client.sendAsync(request, info -> subscriber);
        try {
            var completed = response.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
            if (completed.statusCode() != 200) throw JobException.invalid("REPOSITORY_FETCH_UNAVAILABLE");
            return completed.body();
        } catch (TimeoutException e) {
            throw timedOut();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw JobException.invalid("REPOSITORY_FETCH_UNAVAILABLE");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof JobException job) throw job;
            if (e.getCause() instanceof HttpTimeoutException) throw timedOut();
            throw JobException.invalid("REPOSITORY_FETCH_UNAVAILABLE");
        } finally {
            subscriber.cancel();
            response.cancel(true);
        }
    }
    static JobException timedOut() { return new JobException(HttpStatus.GATEWAY_TIMEOUT,"REPOSITORY_FETCH_TIMEOUT"); }
    static final class LimitedSubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final int max;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private Flow.Subscription subscription;
        private boolean cancelled;
        LimitedSubscriber(int max) { this.max=max; }
        public CompletionStage<byte[]> getBody() { return body; }
        public synchronized void onSubscribe(Flow.Subscription s) {
            if (cancelled || subscription != null) { s.cancel(); return; }
            subscription=s; s.request(1);
        }
        public synchronized void onNext(List<ByteBuffer> buffers) {
            if (cancelled) return;
            for (var buffer:buffers) {
                if (buffer.remaining() > max-bytes.size()) {
                    body.completeExceptionally(JobException.invalid("REPOSITORY_LIMIT_EXCEEDED")); cancel(); return;
                }
                byte[] chunk=new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        public synchronized void onError(Throwable error) { body.completeExceptionally(error); }
        public synchronized void onComplete() { body.complete(bytes.toByteArray()); }
        synchronized void cancel() {
            cancelled=true;
            if (subscription!=null) subscription.cancel();
            body.cancel(true);
        }
    }
}
