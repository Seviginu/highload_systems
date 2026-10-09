package itmo.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/** Real HTTP endpoint for the Feign client; independent of the tracker database. */
public class UserApiStub {
    private final Map<Long, TestUser> users = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(1000);
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpServer server;
    private volatile boolean unavailable;
    private volatile long delayMillis;
    private volatile CountDownLatch responseEntered;
    private volatile CountDownLatch responseRelease;

    public UserApiStub() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
        server.setExecutor(Executors.newCachedThreadPool(task -> {
            Thread thread = new Thread(task, "user-api-stub");
            thread.setDaemon(true);
            return thread;
        }));
        server.createContext("/internal/users/resolve", exchange -> {
            try {
                if (!"POST".equals(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }
                var request = mapper.readTree(exchange.getRequestBody());
                CountDownLatch entered = responseEntered;
                CountDownLatch release = responseRelease;
                if (entered != null && release != null) {
                    entered.countDown();
                    try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                    }
                }
                long delay = delayMillis;
                if (delay > 0) {
                    try { Thread.sleep(delay); } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                    }
                }
                int status = unavailable ? 503 : 200;
                Object response;
                if (unavailable) {
                    response = Map.of("message", "Unavailable");
                } else {
                    Map<Long, Map<String, Object>> found = new LinkedHashMap<>();
                    for (var value : request.withArray("ids")) {
                        TestUser user = users.get(value.asLong());
                        if (user != null) {
                            found.put(user.getId(), Map.of("id", user.getId(), "role", user.getRole()));
                        }
                    }
                    response = found.values();
                }
                byte[] body = mapper.writeValueAsBytes(response);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, body.length);
                exchange.getResponseBody().write(body);
            } finally {
                exchange.close();
            }
        });
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
    }

    public String url() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
    public TestUser create(TestUser user) {
        user.assignId(sequence.incrementAndGet());
        users.put(user.getId(), user);
        return user;
    }
    public void delete(Long id) { users.remove(id); }
    public void unavailable(boolean value) { unavailable = value; }
    public void delay(long value) { delayMillis = value; }
    public void blockResponses(CountDownLatch entered, CountDownLatch release) {
        responseEntered = entered;
        responseRelease = release;
    }
    public void clear() {
        users.clear(); unavailable = false; delayMillis = 0;
        responseEntered = null; responseRelease = null;
    }
}
