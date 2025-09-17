package dev.example;


import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/swc")
public class SlidingWindowController {

    // 예시: 10초 윈도우에 10회 제한
    private final SlidingWindowCounter counter = new SlidingWindowCounter(10_000L, 10L);

    @PostMapping("/request")
    public ResponseEntity<?> request() {
        var res = counter.tryAcquire();
        if (res.allowed) {
            return ResponseEntity.ok(Map.of(
                    "allowed", true,
                    "remaining", res.remaining
            ));
        } else {
            return ResponseEntity.ok(Map.of(
                    "allowed", false,
                    "retryAfterSec", res.retryAfterSec
            ));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<?> status() {
        var st = counter.status();
        return ResponseEntity.ok(Map.of(
                "limit", st.limit,
                "effectiveCount", st.effectiveCount,
                "remaining", st.remaining,
                "windowSizeMillis", st.windowSizeMillis,
                "elapsedInWindowMillis", st.elapsedInWindowMillis
        ));
    }
}