package com.fcaj.hackathon.aijob;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class BoundedHttpTest {
    @Test void stalledAndDrippingBodiesAreCancelledAndNextRequestStillWorks() throws Exception {
        var executor=Executors.newFixedThreadPool(3);var release=new CountDownLatch(1);var started=new CountDownLatch(1);
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.setExecutor(executor);
        server.createContext("/stall",e->{try {e.sendResponseHeaders(200,0);e.getResponseBody().write('x');e.getResponseBody().flush();started.countDown();release.await(5,TimeUnit.SECONDS);}catch(Exception ignored){}finally{e.close();}});
        server.createContext("/drip",e->{try {e.sendResponseHeaders(200,0);for(int i=0;i<50;i++){e.getResponseBody().write('x');e.getResponseBody().flush();Thread.sleep(20);}}catch(Exception ignored){}finally{e.close();}});
        server.createContext("/ok",e->{try{e.sendResponseHeaders(200,2);e.getResponseBody().write("ok".getBytes());}finally{e.close();}});
        server.start();
        try(var client=HttpClient.newHttpClient()) {
            for(String path:new String[]{"/stall","/drip"}) {
                long before=System.nanoTime();
                assertThatThrownBy(()->BoundedHttp.fetch(client,request(server,path),100,Duration.ofMillis(300))).hasMessage("REPOSITORY_FETCH_TIMEOUT");
                assertThat(Duration.ofNanos(System.nanoTime()-before)).isLessThan(Duration.ofSeconds(3));
            }
            assertThat(started.await(1,TimeUnit.SECONDS)).isTrue();
            assertThat(new String(BoundedHttp.fetch(client,request(server,"/ok"),2,Duration.ofSeconds(2)))).isEqualTo("ok");
            assertThatThrownBy(()->BoundedHttp.fetch(client,request(server,"/ok"),1,Duration.ofSeconds(2))).hasMessage("REPOSITORY_LIMIT_EXCEEDED");
        } finally {release.countDown();server.stop(0);executor.shutdownNow();assertThat(executor.awaitTermination(3,TimeUnit.SECONDS)).isTrue();}
    }
    @Test void interruptCancelsSubscriptionAndPreservesFlag() throws Exception {
        var subscribed=new CountDownLatch(1);var cancelled=new CountDownLatch(1);
        var client=org.mockito.Mockito.mock(HttpClient.class);
        org.mockito.Mockito.when(client.sendAsync(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any())).thenAnswer(c->{
            HttpResponse.BodyHandler<byte[]> h=c.getArgument(1);var s=h.apply(null);
            s.onSubscribe(new Flow.Subscription(){public void request(long n){subscribed.countDown();}public void cancel(){cancelled.countDown();}});
            return new CompletableFuture<HttpResponse<byte[]>>();
        });
        var flag=new java.util.concurrent.atomic.AtomicBoolean();
        Thread t=new Thread(()->{try {BoundedHttp.fetch(client,HttpRequest.newBuilder(URI.create("https://api.github.com")).build(),10,Duration.ofSeconds(2));}catch(JobException expected){flag.set(Thread.currentThread().isInterrupted());}});
        t.start();assertThat(subscribed.await(2,TimeUnit.SECONDS)).isTrue();t.interrupt();t.join(2000);
        assertThat(t.isAlive()).isFalse();assertThat(flag).isTrue();assertThat(cancelled.await(1,TimeUnit.SECONDS)).isTrue();
    }
    private HttpRequest request(HttpServer s,String path){return HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+s.getAddress().getPort()+path)).build();}
}
