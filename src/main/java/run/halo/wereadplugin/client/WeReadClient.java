package run.halo.wereadplugin.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.ReactiveExtensionClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 微信读书客户端 - 增强版 (支持动态 Cookie 维护)
 * @author haike
 * @date 2026-04-25
 */
@Component
public class WeReadClient {
    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(WeReadClient.class.getName());
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ReactiveExtensionClient extensionClient;

    public WeReadClient(ReactiveExtensionClient extensionClient) {
        this.extensionClient = extensionClient;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public static class WeReadResponse {
        private final JsonNode body;
        private final String updatedCookie;
        public WeReadResponse(JsonNode body, String updatedCookie) {
            this.body = body;
            this.updatedCookie = updatedCookie;
        }
        public JsonNode getBody() { return body; }
        public String getUpdatedCookie() { return updatedCookie; }
    }

    private String mergeCookies(String oldCookie, List<String> setCookies) {
        if (setCookies == null || setCookies.isEmpty()) return oldCookie;
        Map<String, String> cookieMap = new LinkedHashMap<>();
        if (oldCookie != null) {
            for (String part : oldCookie.split(";")) {
                part = part.trim();
                int idx = part.indexOf('=');
                if (idx > 0) cookieMap.put(part.substring(0, idx), part.substring(idx + 1));
            }
        }
        for (String sc : setCookies) {
            String kv = sc.split(";")[0].trim();
            int idx = kv.indexOf('=');
            if (idx > 0) cookieMap.put(kv.substring(0, idx), kv.substring(idx + 1));
        }
        return cookieMap.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("; "));
    }

    /**
     * 自动同步最新的 Cookie 到系统配置中
     */
    private Mono<Void> persistCookie(String newCookie) {
        return extensionClient.fetch(ConfigMap.class, "halo-weread-plugin-config")
                .flatMap(config -> {
                    Map<String, String> data = config.getData();
                    if (data == null) data = new HashMap<>();
                    String current = data.get("cookie");
                    if (newCookie.equals(current)) return Mono.empty();
                    
                    data.put("cookie", newCookie);
                    config.setData(data);
                    log.info("WeRead: 检测到凭证变化，已自动更新并持久化 Cookie。");
                    return extensionClient.update(config);
                })
                .then();
    }

    private Mono<WeReadResponse> executeGet(String url, String cookie) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Referer", "https://weread.qq.com/")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Accept", "application/json, text/plain, */*")
                .header("Cookie", cookie)
                .GET()
                .build();

        return Mono.fromFuture(httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()))
                .flatMap(response -> {
                    String body = response.body();
                    List<String> setCookies = response.headers().allValues("set-cookie");
                    String newMergedCookie = mergeCookies(cookie, setCookies);

                    // 异步更新 Cookie，不阻塞当前响应
                    Mono<Void> updateTask = newMergedCookie.equals(cookie) ? Mono.empty() : persistCookie(newMergedCookie);

                    if (response.statusCode() == 200) {
                        try {
                            if (body != null && (body.trim().startsWith("<!DOCTYPE") || body.trim().startsWith("<html"))) {
                                return Mono.error(new RuntimeException("WeRead API returned HTML (Login Required). URL: " + url));
                            }
                            JsonNode jsonBody = objectMapper.readTree(body);
                            return updateTask.thenReturn(new WeReadResponse(jsonBody, newMergedCookie));
                        } catch (Exception e) {
                            return Mono.error(new RuntimeException("Parse error: " + e.getMessage()));
                        }
                    } else if (response.statusCode() == 401) {
                        return Mono.error(new RuntimeException("401 Unauthorized"));
                    } else {
                        return Mono.error(new RuntimeException("API Error: " + response.statusCode()));
                    }
                });
    }

    public Mono<WeReadResponse> getNotebooks(String cookie) {
        return executeGet("https://weread.qq.com/api/user/notebook", cookie);
    }

    public Mono<WeReadResponse> getShelfSync(String cookie) {
        return executeGet("https://weread.qq.com/web/shelf", cookie);
    }
    
    public Mono<WeReadResponse> getBookInfo(String cookie, String bookId) {
        return executeGet("https://weread.qq.com/web/book/info?bookId=" + bookId, cookie);
    }

    public Mono<WeReadResponse> getBookProgress(String cookie, String bookId) {
        return executeGet("https://weread.qq.com/web/book/getProgress?bookId=" + bookId, cookie);
    }
}
