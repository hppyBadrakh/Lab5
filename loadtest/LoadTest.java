import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.function.DoubleUnaryOperator;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Open-model HTTP load generator for the Week 11 measurements (docs/quality/measurement-evidence.md).
 * It sends requests at a fixed arrival rate, like k6's constant-arrival-rate executor: every request runs on
 * its own virtual thread, so a slow server never slows down the arrival of new requests.
 * <p>
 * Run with JDK 21 or newer (no build needed), while the application is running:
 * <pre>
 *   java loadtest/LoadTest.java nominal  --rate 100 --duration 300
 *   java loadtest/LoadTest.java payment  --rate 120 --duration 60 --probe-rate 20
 *   java loadtest/LoadTest.java surge    --from 20 --to 1200 --ramp 10 --hold 20
 *   java loadtest/LoadTest.java malformed --rate 125 --duration 40
 * </pre>
 * Options: --base (default http://localhost:8080), --out FILE (appends the Markdown result).
 */
public final class LoadTest {

    private static final String ORDER_JSON = """
            {"customerId":"CUST-LOAD","shippingAddress":{"street":"Peace Ave 1","city":"Ulaanbaatar",\
            "postalCode":"14200","countryCode":"MN"},"items":[{"sku":"ELEC-1001","unitPrice":25.50,\
            "currency":"USD","quantity":2}]}""";
    private static final Pattern ORDER_ID = Pattern.compile("\"orderId\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern METRIC_VALUE = Pattern.compile("\"value\"\\s*:\\s*([0-9.]+)");

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String, ConcurrentLinkedQueue<long[]>> samples = new ConcurrentHashMap<>();
    private final String base;
    private final AtomicLong maxBusyThreads = new AtomicLong(-1);
    private final AtomicBoolean running = new AtomicBoolean(true);

    private LoadTest(String base) {
        this.base = base;
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("usage: java loadtest/LoadTest.java nominal|payment|surge [options]");
            System.exit(2);
        }
        Map<String, String> opt = options(args);
        LoadTest test = new LoadTest(opt.getOrDefault("base", "http://localhost:8080"));
        String report = switch (args[0]) {
            case "nominal" -> test.nominal(dbl(opt, "rate", 100), dbl(opt, "duration", 300));
            case "payment" -> test.payment(dbl(opt, "rate", 120), dbl(opt, "duration", 60), dbl(opt, "probe-rate", 20));
            case "malformed" -> test.malformed(dbl(opt, "rate", 125), dbl(opt, "duration", 40));
            case "surge" -> test.surge(dbl(opt, "from", 20), dbl(opt, "to", 1200), dbl(opt, "ramp", 10), dbl(opt, "hold", 20));
            default -> throw new IllegalArgumentException("unknown scenario " + args[0]);
        };
        System.out.println(report);
        if (opt.containsKey("out")) {
            java.nio.file.Files.writeString(java.nio.file.Path.of(opt.get("out")), report + "\n",
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        }
        System.exit(0);
    }

    /** S1: complete checkouts (place + pay) at a constant rate against a healthy gateway. */
    private String nominal(double rate, double seconds) throws Exception {
        startThreadSampler();
        long start = System.nanoTime();
        runConstant(rate, seconds, i -> () -> {
            String id = placeOrder("place");
            if (id != null) {
                send("pay", put("/api/v1/orders/" + id + "/payment"));
            }
        });
        return report("nominal checkout", "%.0f checkouts/s for %.0f s (POST /orders, then PUT /orders/{id}/payment)"
                .formatted(rate, seconds), start);
    }

    /** S2: pay pre-placed orders at a constant rate while the gateway is degraded; probe an unrelated read endpoint. */
    private String payment(double rate, double seconds, double probeRate) throws Exception {
        int needed = (int) Math.ceil(rate * seconds) + 10;
        System.err.printf("setup: placing %d orders...%n", needed);
        List<String> ids = Collections.synchronizedList(new ArrayList<>());
        Semaphore parallel = new Semaphore(32);
        CountDownLatch done = new CountDownLatch(needed);
        for (int i = 0; i < needed; i++) {
            parallel.acquire();
            workers.submit(() -> {
                try {
                    HttpResponse<String> r = client.send(post("/api/v1/orders"), HttpResponse.BodyHandlers.ofString());
                    Matcher m = ORDER_ID.matcher(r.body());
                    if (r.statusCode() == 201 && m.find()) {
                        ids.add(m.group(1));
                    }
                } catch (Exception ignored) {
                    // counted below as a missing order
                } finally {
                    parallel.release();
                    done.countDown();
                }
            });
        }
        done.await();
        if (ids.size() < needed) {
            throw new IllegalStateException("setup placed only " + ids.size() + " of " + needed
                    + " orders; is the checkout rate limiter disabled for this run?");
        }
        String probeId = ids.removeLast();
        System.err.println("setup done, starting load");

        startThreadSampler();
        long start = System.nanoTime();
        AtomicInteger next = new AtomicInteger();
        Thread probe = Thread.ofVirtual().start(() -> runConstantQuietly(probeRate, seconds,
                i -> () -> send("probe GET /orders/{id}", get("/api/v1/orders/" + probeId))));
        runConstant(rate, seconds, i -> () -> send("pay", put("/api/v1/orders/" + ids.get(next.getAndIncrement()) + "/payment")));
        probe.join();
        return report("payment under gateway degradation",
                "%.0f payments/s for %.0f s, plus %.0f reads/s on an unrelated endpoint".formatted(rate, seconds, probeRate),
                start);
    }

    /** Hostile payloads for S4. Each one must be rejected at the web boundary with 400. */
    private static final List<String> MALFORMED = List.of(
            ORDER_JSON.replace("ELEC-1001", "ELEC-1001'; DROP TABLE orders; --"),
            ORDER_JSON.replace("\"USD\"", "\"USD' OR '1'='1\""),
            ORDER_JSON.replace("\"MN\"", "\"MN'--\""),
            ORDER_JSON.replace("25.50", "-25.50"),
            ORDER_JSON.replace("25.50", "0.001"),
            ORDER_JSON.replace("\"quantity\":2", "\"quantity\":100000"),
            ORDER_JSON.replace("\"items\":[", "\"items\":[ {\"sku\": }"),
            "{\"customerId\":\"CUST-1\"");

    /** S4: a stream of malformed and injection payloads at a constant rate. */
    private String malformed(double rate, double seconds) throws Exception {
        startThreadSampler();
        long start = System.nanoTime();
        runConstant(rate, seconds, i -> () -> send("malformed POST /orders",
                HttpRequest.newBuilder(URI.create(base + "/api/v1/orders")).timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(MALFORMED.get(i % MALFORMED.size()))).build()));
        return report("malformed and injection payloads", "%.0f req/s for %.0f s, %d payload kinds in rotation"
                .formatted(rate, seconds, MALFORMED.size()), start);
    }

    /** S3: the arrival rate of new orders ramps linearly from {@code from} to {@code to}, then holds. */
    private String surge(double from, double to, double ramp, double hold) throws Exception {
        startThreadSampler();
        long start = System.nanoTime();
        DoubleUnaryOperator rateAt = t -> t < ramp ? from + (to - from) * t / ramp : to;
        runSchedule(rateAt, ramp + hold, i -> () -> send("place", post("/api/v1/orders")), true);
        return report("flash surge on checkout", "POST /orders ramping %.0f -> %.0f req/s in %.0f s, then %.0f s at %.0f req/s"
                .formatted(from, to, ramp, hold, to), start);
    }

    // ---------------------------------------------------------------- scheduling

    private void runConstant(double rate, double seconds, IntFunction<Runnable> task) throws InterruptedException {
        runSchedule(t -> rate, seconds, task, true);
    }

    private void runConstantQuietly(double rate, double seconds, IntFunction<Runnable> task) {
        try {
            runSchedule(t -> rate, seconds, task, false);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /** Fires task(i) at the arrival times implied by rateAt(t); then waits for all in-flight requests. */
    private void runSchedule(DoubleUnaryOperator rateAt, double seconds, IntFunction<Runnable> task, boolean main)
            throws InterruptedException {
        long start = System.nanoTime();
        long end = start + (long) (seconds * 1e9);
        List<Thread> inFlight = Collections.synchronizedList(new ArrayList<>());
        double next = 0;
        int i = 0;
        while (true) {
            long fireAt = start + (long) (next * 1e9);
            if (fireAt >= end) {
                break;
            }
            long wait = fireAt - System.nanoTime();
            if (wait > 0) {
                LockSupport.parkNanos(wait);
            }
            inFlight.add(Thread.ofVirtual().start(task.apply(i++)));
            next += 1.0 / Math.max(rateAt.applyAsDouble(next), 0.001);
        }
        for (Thread t : List.copyOf(inFlight)) {
            t.join();
        }
        if (main) {
            running.set(false);
        }
    }

    // ---------------------------------------------------------------- HTTP

    private HttpRequest post(String path) {
        return HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(ORDER_JSON)).build();
    }

    private HttpRequest put(String path) {
        return HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(30))
                .PUT(HttpRequest.BodyPublishers.noBody()).build();
    }

    private HttpRequest get(String path) {
        return HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(30)).GET().build();
    }

    private String placeOrder(String tag) {
        HttpResponse<String> r = send(tag, post("/api/v1/orders"));
        if (r == null || r.statusCode() != 201) {
            return null;
        }
        Matcher m = ORDER_ID.matcher(r.body());
        return m.find() ? m.group(1) : null;
    }

    /** Sends one request and records {status, latency in ns}. Status 0 means a client-side error or timeout. */
    private HttpResponse<String> send(String tag, HttpRequest request) {
        long t0 = System.nanoTime();
        HttpResponse<String> response = null;
        int status;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
            status = response.statusCode();
        } catch (Exception ex) {
            status = 0;
        } catch (Throwable fatal) {
            status = 0;
        }
        samples.computeIfAbsent(tag, k -> new ConcurrentLinkedQueue<>()).add(new long[]{status, System.nanoTime() - t0});
        return response;
    }

    /** Polls Tomcat's busy worker threads every 500 ms (needs server.tomcat.mbeanregistry.enabled=true). */
    private void startThreadSampler() {
        running.set(true);
        Thread.ofVirtual().start(() -> {
            while (running.get()) {
                try {
                    HttpResponse<String> r = client.send(HttpRequest.newBuilder(
                                    URI.create(base + "/actuator/metrics/tomcat.threads.busy")).timeout(Duration.ofSeconds(10)).build(),
                            HttpResponse.BodyHandlers.ofString());
                    Matcher m = METRIC_VALUE.matcher(r.body());
                    if (m.find()) {
                        maxBusyThreads.accumulateAndGet((long) Double.parseDouble(m.group(1)), Math::max);
                    }
                    Thread.sleep(500);
                } catch (Exception ex) {
                    LockSupport.parkNanos(500_000_000L);
                }
            }
        });
    }

    // ---------------------------------------------------------------- report

    private String report(String title, String load, long startNanos) {
        double wall = (System.nanoTime() - startNanos) / 1e9;
        StringBuilder out = new StringBuilder();
        out.append("### ").append(title).append("\n\n");
        out.append("- Load: ").append(load).append("\n");
        out.append("- Wall time: %.1f s; max Tomcat busy threads observed: %s%n%n".formatted(wall,
                maxBusyThreads.get() < 0 ? "n/a" : maxBusyThreads.get() + " / 200"));
        out.append("| Request | Count | Throughput (req/s) | p50 ms | p95 ms | p99 ms | max ms | Status codes |\n");
        out.append("|---|---:|---:|---:|---:|---:|---:|---|\n");
        for (String tag : new TreeMap<>(samples).keySet()) {
            List<long[]> list = new ArrayList<>(samples.get(tag));
            long[] nanos = list.stream().mapToLong(s -> s[1]).sorted().toArray();
            Map<Long, Integer> codes = new TreeMap<>();
            list.forEach(s -> codes.merge(s[0], 1, Integer::sum));
            StringBuilder codeText = new StringBuilder();
            codes.forEach((code, n) -> codeText.append(codeText.isEmpty() ? "" : ", ")
                    .append(code == 0 ? "client timeout/error" : code).append(": ").append(n)
                    .append(" (").append(String.format(Locale.ROOT, "%.1f", 100.0 * n / list.size())).append("%)"));
            out.append(String.format(Locale.ROOT, "| %s | %d | %.1f | %.1f | %.1f | %.1f | %.1f | %s |%n",
                    tag, nanos.length, nanos.length / wall, pct(nanos, 50), pct(nanos, 95), pct(nanos, 99),
                    nanos.length == 0 ? 0 : nanos[nanos.length - 1] / 1e6, codeText));
        }
        return out.toString();
    }

    private static double pct(long[] sorted, double p) {
        if (sorted.length == 0) {
            return 0;
        }
        int index = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(index, sorted.length - 1))] / 1e6;
    }

    private static Map<String, String> options(String[] args) {
        Map<String, String> opt = new TreeMap<>();
        for (int i = 1; i + 1 < args.length; i += 2) {
            opt.put(args[i].replaceFirst("^--", ""), args[i + 1]);
        }
        return opt;
    }

    private static double dbl(Map<String, String> opt, String key, double fallback) {
        return opt.containsKey(key) ? Double.parseDouble(opt.get(key)) : fallback;
    }
}
