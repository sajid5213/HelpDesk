package com.helpdesk.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;

import com.helpdesk.common.ApiException;
import com.helpdesk.common.HelpdeskMetrics;
import com.helpdesk.config.HelpdeskProperties;
import com.helpdesk.user.User;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * In-memory login throttling: one token bucket per client IP and one per account email.
 * Both maps are size-bounded, so a flood of random emails cannot exhaust memory.
 */
@Component
public class LoginRateLimiter {

	static final String RATE_LIMITED = "rate_limited";

	private static final int MAX_TRACKED_KEYS = 10_000;

	private static final Duration WINDOW = Duration.ofMinutes(1);

	private final Map<String, Bucket> byIp = boundedMap();

	private final Map<String, Bucket> byAccount = boundedMap();

	private final int perIpPerMinute;

	private final int perAccountPerMinute;

	private final TimeMeter time;

	private final HelpdeskMetrics metrics;

	LoginRateLimiter(HelpdeskProperties properties, Clock clock, HelpdeskMetrics metrics) {
		this.metrics = metrics;
		this.perIpPerMinute = properties.auth().rateLimit().perIpPerMinute();
		this.perAccountPerMinute = properties.auth().rateLimit().perAccountPerMinute();
		this.time = new ClockTimeMeter(clock);
	}

	/** Throws 429 with Retry-After when either the IP or the account is over its limit. */
	public void check(String ip, String email) {
		consume(byIp, "ip", ip, perIpPerMinute);
		consume(byAccount, "account", User.normalizeEmail(email), perAccountPerMinute);
	}

	/** Forgets all buckets. Used between tests, which all come from the same IP. */
	public void reset() {
		byIp.clear();
		byAccount.clear();
	}

	private void consume(Map<String, Bucket> buckets, String scope, String key, int perMinute) {
		Bucket bucket = buckets.computeIfAbsent(key, (k) -> newBucket(perMinute));
		ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
		if (!probe.isConsumed()) {
			metrics.rateLimited(scope);
			long retryAfterSeconds = Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds());
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, RATE_LIMITED,
					"Too many login attempts. Try again in " + retryAfterSeconds + " seconds.",
					Map.of("Retry-After", String.valueOf(retryAfterSeconds)));
		}
	}

	private Bucket newBucket(int perMinute) {
		return Bucket.builder()
			.addLimit((limit) -> limit.capacity(perMinute).refillGreedy(perMinute, WINDOW))
			.withCustomTimePrecision(time)
			.build();
	}

	private static Map<String, Bucket> boundedMap() {
		return Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<String, Bucket> eldest) {
				return size() > MAX_TRACKED_KEYS;
			}
		});
	}

	/** Bucket4j reads time through this, so it follows the app clock (and the test clock). */
	private record ClockTimeMeter(Clock clock) implements TimeMeter {

		@Override
		public long currentTimeNanos() {
			Instant now = clock.instant();
			return now.getEpochSecond() * 1_000_000_000L + now.getNano();
		}

		@Override
		public boolean isWallClockBased() {
			return true;
		}

	}

}
