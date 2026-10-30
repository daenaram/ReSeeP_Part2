import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Response-time test for ReSeeP (Assessment 3 non-functional requirement).
 *
 * Operation measured: the Cook changes a pantry quantity, i.e.
 * PUT /api/pantry/{id} — a full write through REST -> service -> MySQL that
 * also triggers a "pantry-updated" WebSocket broadcast to every open page.
 *
 *   T1  HTTP response time       p95 <= 1000 ms
 *   T2  Live update delivered    p95 <= 1000 ms (from sending the request to a
 *       second, separately connected client receiving the WebSocket event)
 *
 * 1 s is the usual limit for a response to still feel uninterrupted, and for
 * the shared shopping list it means another user sees the change while they
 * are still looking at the screen.
 *
 * The test alternates the first pantry ingredient's quantity between its
 * current value and +1, then puts the original value back, so it leaves no
 * net change in the database. Open web pages will refresh on each change
 * while it runs.
 *
 * Needs only a JDK (11+). With the server running:
 *     java perf/ResponseTimeTest.java [baseUrl] [requests]
 *     java perf/ResponseTimeTest.java http://localhost:8080/reseep 50
 * Exit code 0 = both targets met, 1 = a target missed, 2 = test couldn't run.
 * Raw timings are written to perf/results/response-times-<timestamp>.csv.
 */
public class ResponseTimeTest {

    static final double TARGET_HTTP_P95_MS = 1000;
    static final double TARGET_LIVE_P95_MS = 1000;
    static final int WARMUP_REQUESTS = 5;
    static final long LIVE_TIMEOUT_SECONDS = 5;

    public static void main(String[] args) throws Exception {
        String base = args.length > 0 ? args[0].replaceAll("/+$", "") : "http://localhost:8080/reseep";
        int requests = args.length > 1 ? Integer.parseInt(args[1]) : 50;

        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

        // Use the first pantry ingredient; remember it so we can put it back.
        HttpResponse<String> pantry = send(http, HttpRequest.newBuilder(URI.create(base + "/api/pantry")).GET().build());
        if (pantry.statusCode() != 200) fail("GET /api/pantry returned HTTP " + pantry.statusCode() + " — is the app deployed?");
        Matcher firstItem = Pattern.compile("\\{[^{}]*\\}").matcher(pantry.body());
        if (!firstItem.find()) fail("The pantry is empty — add an ingredient first.");
        String item = firstItem.group();
        long id = Long.parseLong(field(item, "id"));
        String name = field(item, "name");
        String unit = field(item, "unit");
        double original = Double.parseDouble(field(item, "qtyOnHand"));

        // A second client listening for live updates, like another user's open page.
        BlockingQueue<Long> liveArrivals = new LinkedBlockingQueue<>();
        Pattern ourEvent = Pattern.compile("\"type\":\"pantry-updated\".*\"id\":" + id + "\\D");
        URI wsUri = URI.create(base.replaceFirst("^http", "ws") + "/ws/updates");
        WebSocket socket = http.newWebSocketBuilder().buildAsync(wsUri, new WebSocket.Listener() {
            private final StringBuilder partial = new StringBuilder();

            @Override
            public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                long now = System.nanoTime();
                partial.append(data);
                if (last) {
                    if (ourEvent.matcher(partial).find()) liveArrivals.add(now);
                    partial.setLength(0);
                }
                ws.request(1);
                return null;
            }
        }).get(5, TimeUnit.SECONDS);

        List<Double> httpMs = new ArrayList<>();
        List<Double> liveMs = new ArrayList<>();
        List<String> csv = new ArrayList<>(List.of("request,http_ms,live_update_ms"));
        int liveMissed = 0;

        System.out.printf("Measuring %d x PUT /api/pantry/%d (%s) after %d warm-up requests...%n",
                requests, id, name, WARMUP_REQUESTS);
        try {
            for (int i = 0; i < WARMUP_REQUESTS + requests; i++) {
                double qty = (i % 2 == 0) ? original + 1 : original;
                liveArrivals.clear();

                long start = System.nanoTime();
                HttpResponse<String> res = send(http, putPantry(base, id, name, unit, qty));
                long end = System.nanoTime();
                if (res.statusCode() != 200) fail("PUT returned HTTP " + res.statusCode() + ": " + res.body());

                Long arrived = liveArrivals.poll(LIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (i < WARMUP_REQUESTS) continue; // first requests warm up connections and caches

                double h = (end - start) / 1e6;
                httpMs.add(h);
                String live = "";
                if (arrived == null) {
                    liveMissed++;
                } else {
                    double l = (arrived - start) / 1e6;
                    liveMs.add(l);
                    live = fmt(l);
                }
                csv.add((i - WARMUP_REQUESTS + 1) + "," + fmt(h) + "," + live);
            }
        } finally {
            send(http, putPantry(base, id, name, unit, original)); // leave the pantry as we found it
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "done");
        }

        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path out = Path.of("perf", "results", "response-times-" + stamp + ".csv");
        Files.createDirectories(out.getParent());
        Files.write(out, csv);

        boolean httpOk = percentile(httpMs, 95) <= TARGET_HTTP_P95_MS;
        boolean liveOk = liveMissed == 0 && !liveMs.isEmpty() && percentile(liveMs, 95) <= TARGET_LIVE_P95_MS;

        System.out.println();
        System.out.println("ReSeeP response-time test, " + LocalDateTime.now().withNano(0));
        System.out.println("Server: " + base + "   Java " + System.getProperty("java.version")
                + "   " + System.getProperty("os.name"));
        System.out.printf("Operation: PUT /api/pantry/%d (Cook changes a pantry quantity), %d sequential requests%n%n",
                id, requests);
        System.out.println("                         min   median     mean      p95      max   target p95   result");
        row("T1 HTTP response (ms)", httpMs, TARGET_HTTP_P95_MS, httpOk);
        row("T2 Live update (ms)  ", liveMs, TARGET_LIVE_P95_MS, liveOk);
        if (liveMissed > 0) {
            System.out.printf("   %d of %d live updates did not arrive within %d s%n", liveMissed, requests, LIVE_TIMEOUT_SECONDS);
        }
        System.out.println();
        System.out.println("Raw timings: " + out);
        System.exit(httpOk && liveOk ? 0 : 1);
    }

    static HttpRequest putPantry(String base, long id, String name, String unit, double qty) {
        String body = String.format(Locale.ROOT, "{\"name\":\"%s\",\"unit\":\"%s\",\"qtyOnHand\":%s}",
                jsonEscape(name), jsonEscape(unit), qty);
        return HttpRequest.newBuilder(URI.create(base + "/api/pantry/" + id))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    static HttpResponse<String> send(HttpClient http, HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            fail("Couldn't reach " + request.uri() + " (" + e + ") — is Payara running?");
            return null; // unreachable
        }
    }

    static void row(String label, List<Double> ms, double target, boolean ok) {
        if (ms.isEmpty()) {
            System.out.printf("%s   (no samples)%33s  %8.0f   %s%n", label, "", target, "FAIL");
            return;
        }
        double mean = ms.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        System.out.printf("%s %7.0f  %7.0f  %7.0f  %7.0f  %7.0f   %10.0f   %s%n", label,
                percentile(ms, 0), percentile(ms, 50), mean, percentile(ms, 95), percentile(ms, 100),
                target, ok ? "PASS" : "FAIL");
    }

    /** Nearest-rank percentile (p = 0 gives the minimum, 100 the maximum). */
    static double percentile(List<Double> values, double p) {
        if (values.isEmpty()) return Double.NaN;
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int rank = (int) Math.ceil(p / 100.0 * sorted.size());
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, rank - 1)));
    }

    static String field(String json, String name) {
        Matcher m = Pattern.compile("\"" + name + "\":(\"((?:[^\"\\\\]|\\\\.)*)\"|[^,}]+)").matcher(json);
        if (!m.find()) fail("Unexpected pantry JSON (no \"" + name + "\"): " + json);
        return m.group(2) != null ? m.group(2) : m.group(1).trim();
    }

    static String jsonEscape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    static String fmt(double ms) {
        return String.format(Locale.ROOT, "%.1f", ms);
    }

    static void fail(String message) {
        System.err.println("Response-time test could not run: " + message);
        System.exit(2);
    }
}
