package dev.bbbreaddd.breadmod;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public final class MixinDiagnostics {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

	private MixinDiagnostics() {
	}

	public static void warnOnce(String key, String message, Throwable throwable) {
		if (WARNED.add(key)) {
			LOGGER.warn("Breadmod compatibility hook '{}' failed; continuing without it. {}", key, message,
				throwable);
		}
	}

	public static void warnOnce(String key, String message) {
		if (WARNED.add(key)) {
			LOGGER.warn("Breadmod compatibility hook '{}' failed; continuing without it. {}", key, message);
		}
	}
}
