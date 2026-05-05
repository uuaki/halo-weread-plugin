package run.halo.wereadplugin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.wereadplugin.client.WeReadClient;
import run.halo.wereadplugin.extension.WereadBook;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@Service
public class WeReadSyncService {

    private static final Logger log = Logger.getLogger(WeReadSyncService.class.getName());

    private final WeReadClient weReadClient;
    private final ReactiveExtensionClient extensionClient;

    public WeReadSyncService(WeReadClient weReadClient,
                             ReactiveExtensionClient extensionClient) {
        this.weReadClient = weReadClient;
        this.extensionClient = extensionClient;
    }

    public Mono<Void> syncWeReadData() {
        return extensionClient.fetch(ConfigMap.class, "halo-weread-plugin-config")
                .onErrorResume(e -> Mono.empty())
                .map(configMap -> configMap.getData() != null ? configMap.getData().getOrDefault("cookie", "") : "")
                .defaultIfEmpty("")
                .filter(cookie -> !cookie.isBlank())
                .switchIfEmpty(Mono.error(new RuntimeException("未配置 WeRead Cookie")))
                .flatMap(cookie -> {
                    log.info("WeRead: 开始同步前验证 Cookie...");
                    // 1. 先验证一次 Cookie，顺便吸收服务器可能返回的最新 wr_skey
                    return weReadClient.getNotebooks(cookie)
                            .flatMap(resp -> {
                                String latestCookie = resp.getUpdatedCookie();
                                log.info("WeRead: Cookie 验证通过，准备加载书架...");
                                // 2. 使用可能已更新的 Cookie 预加载全量书架进度信息
                                return weReadClient.getShelfSync(latestCookie)
                                        .map(shelfResp -> parseShelfProgress(shelfResp.getBody()))
                                        .onErrorResume(e -> {
                                            log.warning("预加载书架进度失败: " + e.getMessage());
                                            return Mono.just(new HashMap<String, ProgressInfo>());
                                        })
                                        .flatMap(progressMap -> processNotebooks(latestCookie, progressMap));
                            })
                            .onErrorResume(e -> {
                                log.severe("WeRead: Cookie 验证失败，同步终止: " + e.getMessage());
                                return Mono.error(e);
                            });
                });
    }

    private Map<String, ProgressInfo> parseShelfProgress(JsonNode shelfBody) {
        Map<String, ProgressInfo> map = new HashMap<>();
        JsonNode books = shelfBody.path("books");
        if (books.isArray()) {
            for (JsonNode book : books) {
                String bid = book.path("bookId").asText();
                if (!bid.isEmpty()) {
                    map.put(bid, new ProgressInfo(
                        book.path("progress").asDouble(0),
                        book.path("readingTime").asInt(0),
                        book.path("updateTime").asLong(0)
                    ));
                }
            }
        }
        return map;
    }

    private static class ProgressInfo {
        double progress;
        int readingTime;
        long updateTime;
        ProgressInfo(double p, int rt, long ut) {
            this.progress = p;
            this.readingTime = rt;
            this.updateTime = ut;
        }
    }

    private Mono<Void> processNotebooks(String cookie, Map<String, ProgressInfo> shelfProgressMap) {
        log.info("开始同步微信读书数据 (全量书架模式)...");
        return weReadClient.getNotebooks(cookie)
                .flatMapMany(response -> {
                    JsonNode booksNode = response.getBody().path("books");
                    if (booksNode.isArray()) {
                        return Flux.fromIterable(booksNode);
                    }
                    return Flux.empty();
                })
                .flatMap(bookEntry -> {
                    JsonNode baseBookInfo = bookEntry.path("book");
                    String bookId = baseBookInfo.path("bookId").asText();
                    int noteCount = bookEntry.path("noteCount").asInt(0);
                    int reviewCount = bookEntry.path("reviewCount").asInt(0);
                    long sortTime = bookEntry.path("sort").asLong(0) * 1000L;

                    // 优先从预加载的书架数据中获取进度
                    ProgressInfo shelfInfo = shelfProgressMap.get(bookId);

                    Mono<JsonNode> infoMono = weReadClient.getBookInfo(cookie, bookId)
                            .map(r -> r.getBody())
                            .onErrorResume(e -> Mono.just(JsonNodeFactory.instance.objectNode()));

                    Mono<JsonNode> progressMono;
                    if (shelfInfo != null) {
                        // 如果书架里有，直接构造结果，省去一次 API 调用
                        ObjectNode mockProgress = JsonNodeFactory.instance.objectNode();
                        mockProgress.putObject("book")
                                .put("progress", shelfInfo.progress)
                                .put("readingTime", shelfInfo.readingTime)
                                .put("updateTime", shelfInfo.updateTime);
                        progressMono = Mono.just(mockProgress);
                    } else {
                        progressMono = weReadClient.getBookProgress(cookie, bookId)
                                .map(r -> r.getBody())
                                .onErrorResume(e -> Mono.just(JsonNodeFactory.instance.objectNode()));
                    }

                    return Mono.zip(infoMono, progressMono).flatMap(tuple -> {
                        JsonNode info = tuple.getT1();
                        JsonNode progress = tuple.getT2();
                        return saveBook(bookId, baseBookInfo, info, progress, noteCount, reviewCount, sortTime);
                    });
                }, 5)
                .then();
    }

    private Mono<Void> saveBook(String bookId, JsonNode baseInfo, JsonNode detailInfo, JsonNode progress, 
                               int noteCount, int reviewCount, long sortTime) {
        WereadBook.Spec spec = new WereadBook.Spec();
        spec.setBookId(bookId);
        
        String cover = detailInfo.has("cover") ? detailInfo.path("cover").asText() : baseInfo.path("cover").asText();
        if (cover != null) cover = cover.replace("/s_", "/t7_");
        spec.setCover(cover);

        String author = detailInfo.has("author") ? detailInfo.path("author").asText() : baseInfo.path("author").asText();
        if (author != null) author = author.replaceAll("\\[(.*?)\\]", "【$1】");
        spec.setAuthor(author);

        spec.setTitle(detailInfo.has("title") ? detailInfo.path("title").asText() : baseInfo.path("title").asText());
        spec.setPcUrl(calculatePcUrl(bookId));
        
        spec.setIntro(detailInfo.path("intro").asText(""));
        spec.setPublisher(detailInfo.path("publisher").asText(""));
        spec.setPublishTime(detailInfo.path("publishTime").asText(""));
        spec.setIsbn(detailInfo.path("isbn").asText(""));
        spec.setCategory(detailInfo.path("category").asText(baseInfo.path("category").asText("")));
        spec.setTotalWords(detailInfo.path("totalWords").asInt(0));

        double finalProgress = 0;
        if (progress.has("book")) {
            JsonNode bookProgress = progress.path("book");
            double rawProgress = bookProgress.path("progress").asDouble(0);
            finalProgress = rawProgress;
            spec.setReadingTime(bookProgress.path("readingTime").asInt(0) / 60);
            
            long finishTime = bookProgress.path("finishTime").asLong(0) * 1000L;
            if (finishTime > 0) spec.setFinishTime(finishTime);
        }
        spec.setProgress(finalProgress);
        spec.setLastReadTime(sortTime > 0 ? sortTime : System.currentTimeMillis());

        int finished = detailInfo.path("finished").asInt(0);
        if (finished == 1 || finalProgress >= 100) {
            spec.setReadInfo(3);
        } else if (finalProgress > 0) {
            spec.setReadInfo(2);
        } else {
            spec.setReadInfo(1);
        }

        spec.setNoteCount(noteCount);
        spec.setReviewCount(reviewCount);

        String resourceName = "wereadbook-" + bookId;
        return extensionClient.fetch(WereadBook.class, resourceName)
                .onErrorResume(e -> Mono.empty())
                .flatMap(existing -> {
                    existing.setSpec(spec);
                    return extensionClient.update(existing);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    WereadBook newBook = new WereadBook();
                    run.halo.app.extension.Metadata metadata = new run.halo.app.extension.Metadata();
                    metadata.setName(resourceName);
                    newBook.setMetadata(metadata);
                    newBook.setSpec(spec);
                    return extensionClient.create(newBook);
                }))
                .then();
    }

    private String calculatePcUrl(String bookId) {
        try {
            String md5 = md5(bookId);
            Object[] fa = getFa(bookId);
            StringBuilder sb = new StringBuilder(md5.substring(0, 3));
            sb.append(fa[0]);
            sb.append("2").append(md5.substring(md5.length() - 2));

            for (String part : ((String) fa[1]).split(",")) {
                String hexLen = Integer.toHexString(part.length());
                if (hexLen.length() == 1) sb.append("0");
                sb.append(hexLen).append(part);
            }

            if (sb.length() < 20) {
                sb.append(md5.substring(0, 20 - sb.length()));
            }

            sb.append(md5(sb.toString()).substring(0, 3));
            return "https://weread.qq.com/web/reader/" + sb.toString();
        } catch (Exception e) {
            return "https://weread.qq.com/web/reader/" + bookId;
        }
    }

    private Object[] getFa(String id) {
        if (id.matches("^\\d*$")) {
            List<String> parts = new ArrayList<>();
            for (int i = 0; i < id.length(); i += 9) {
                String sub = id.substring(i, Math.min(i + 9, id.length()));
                parts.add(Long.toHexString(Long.parseLong(sub)));
            }
            return new Object[]{"3", String.join(",", parts)};
        } else {
            StringBuilder sb = new StringBuilder();
            for (char c : id.toCharArray()) {
                sb.append(Integer.toHexString((int) c));
            }
            return new Object[]{"4", sb.toString()};
        }
    }

    private String md5(String input) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] messageDigest = md.digest(input.getBytes());
        StringBuilder hexString = new StringBuilder();
        for (byte b : messageDigest) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
