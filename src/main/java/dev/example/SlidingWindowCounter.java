package dev.example;


import javax.xml.transform.Result;
import java.util.concurrent.locks.ReentrantLock;


/**
 * 이동 윈도우 카운터(Sliding Window Counter)
 * - 기준 윈도우: windowSizeMillis (예: 10초)
 * - 제한: limit (예: 윈도우당 10회)
 * - 현재 고정 윈도우 카운트 + 직전 윈도우 카운트를
 * 겹치는 비율만큼 가중 합산하여 현재 시점의 요청량을 추정.
 */
public class SlidingWindowCounter {
    private final long windowSizeMillis; // 예: 10_000ms
    private final long limit; // 10
    private final ReentrantLock lock = new ReentrantLock();

    // 고정 윈도우 2개만 관리 (직전, 현재)
    private long currentWindowStart; // 현재 윈도우 시작시각(절사)
    private long currentCount; // 현재 윈도우 카운트
    private long prevCount; // 직전 윈도우 카운트

    public SlidingWindowCounter(long windowSizeMillis, long limit) {
        this.windowSizeMillis = windowSizeMillis;
        this.limit = limit;
        long now = System.currentTimeMillis();
        this.currentWindowStart = alignWindowStart(now);
        this.currentCount = 0;
        this.prevCount = 0;
    }

    private long alignWindowStart(long epochMillis) {
        return (epochMillis / windowSizeMillis) * windowSizeMillis;
    }

    // 요청 1건을 시도. 허용/거부 및 남은 추정 가능 횟수, 대기 시간(대략)을 반환
    public Result tryAcquire() {
        lock.lock();
        try {
            long now = System.currentTimeMillis();
            rollWindowsIfNeeded(now);
            double effective = currentEffectiveCount(now);

            if (effective < limit) {
                currentCount += 1; // 허용 → 현재 윈도우 카운트 증가
                long remaining = Math.max(0, (long)Math.floor(limit - currentEffectiveCount(now)));
                return Result.allowed(remaining);
            } else {
                double secs = estimateRetryAfterSeconds(now);
                return Result.denied(secs);
            }
        } finally {
            lock.unlock();
        }
    }

    // 현재 시점의 추정 카운트(가중 합산)
    public double currentEffectiveCount(long now) {
        long elapsed = now - currentWindowStart; // [0, windowSize)
        double weightPrev = 1.0 - (elapsed / (double) windowSizeMillis);
        if (weightPrev < 0) weightPrev = 0;
        return currentCount + prevCount * weightPrev;
    }

    // 상태 조회용
    public Status status() {
        lock.lock();
        try {
            long now = System.currentTimeMillis();
            rollWindowsIfNeeded(now);
            double eff = currentEffectiveCount(now);
            long remaining = Math.max(0, (long)Math.floor(limit - eff));
            return new Status(limit, eff, remaining, windowSizeMillis, now - currentWindowStart);
        } finally {
            lock.unlock();
        }
    }

    // 윈도우 경계가 바뀌었으면 직전/현재 윈도우 갱신
    private void rollWindowsIfNeeded(long now) {
        long aligned = alignWindowStart(now);
        if (aligned == currentWindowStart) return;
        long diff = aligned - currentWindowStart;
        long steps = diff / windowSizeMillis; // 몇 개 윈도우가 지났는지
        if (steps >= 1) {
            // 한 번 이상 윈도우가 넘어갔으면, 현재→직전으로 밀고 현재는 0으로 리셋
            prevCount = (steps == 1) ? currentCount : 0; // 여러 윈도우가 지나면 직전도 0 취급
            currentCount = 0;
            currentWindowStart = aligned;
        }
    }

    // 아주 러프한 재시도 대기 시간(초) 추정: 현재 윈도우 종료까지 남은 시간
    private double estimateRetryAfterSeconds(long now) {
        long elapsed = now - currentWindowStart;
        long remainMs = Math.max(0, windowSizeMillis - elapsed);
        return remainMs / 1000.0;
    }

    // ==== DTOs ====
    public static class Result {

        public final boolean allowed;
        public final Long remaining;
        public final Double retryAfterSec;

        private Result(boolean allowed, Long remaining, Double retryAfterSec) {
            this.allowed = allowed; this.remaining = remaining; this.retryAfterSec = retryAfterSec;
        }

        public static Result allowed(long remaining) {
            return new Result(true, remaining, null);
        }

        public static Result denied(double retryAfterSec) {
            return new Result(false, null, retryAfterSec);
        }

    }

    public static class Status {

        public final long limit;
        public final double effectiveCount;
        public final long remaining;
        public final long windowSizeMillis;
        public final long elapsedInWindowMillis;

        public Status(long limit, double effectiveCount, long remaining, long windowSizeMillis, long elapsedInWindowMillis) {
            this.limit = limit; this.effectiveCount = effectiveCount; this.remaining = remaining;
            this.windowSizeMillis = windowSizeMillis; this.elapsedInWindowMillis = elapsedInWindowMillis;
        }

    }

}