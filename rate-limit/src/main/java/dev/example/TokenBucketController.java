package dev.example;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class TokenBucketController {

    private final Bucket bucket;

    public TokenBucketController(Bucket demoBucket) {
        this.bucket = demoBucket;
    }

    // 버튼 클릭 시 토큰 1개 소비
    @PostMapping("/request")
    public ResponseEntity<?> request() {
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return ResponseEntity.ok(Map.of(
                    "allowed", true,
                    "remainingTokens", probe.getRemainingTokens()
            ));
        } else {
            long nanosToWait = probe.getNanosToWaitForRefill();
            double seconds = nanosToWait / 1_000_000_000.0;
            return ResponseEntity.ok(Map.of(
                    "allowed", false,
                    "retryAfterSec", seconds
            ));
        }
    }

    // 현재 상태 조회
    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
                "availableTokens", bucket.getAvailableTokens()
        );
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        return Map.of("message", "Use refill cycle or re-create bucket for a real reset.");
    }

}
