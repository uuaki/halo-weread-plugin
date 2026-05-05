package run.halo.wereadplugin.controller;

import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import run.halo.wereadplugin.service.WeReadSyncService;
import org.springframework.http.ResponseEntity;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/halo-weread-plugin")
public class WeReadSyncController {

    private final WeReadSyncService syncService;

    public WeReadSyncController(WeReadSyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/sync")
    public Mono<ResponseEntity<Map<String, String>>> triggerSync() {
        return syncService.syncWeReadData()
                .thenReturn(ResponseEntity.ok(Map.of("message", "Sync task executed")))
                .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().body(Map.of("message", e.getMessage() != null ? e.getMessage() : e.toString()))));
    }
}
