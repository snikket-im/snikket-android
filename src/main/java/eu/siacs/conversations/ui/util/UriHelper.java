package eu.siacs.conversations.ui.util;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

/**
 * Helper methods for parsing URI's.
 */
public final class UriHelper {
	/**
	 * Parses a query string into a hashmap.
	 *
	 * @param q The query string to split.
	 * @return A hashmap containing the key-value pairs from the query string.
	 */
	public static Map<String, String> parseQueryString(final String q) {
		if (q == null || q.isEmpty()) {
            return ImmutableMap.of();
		}
		final ImmutableMap.Builder<String,String> queryMapBuilder = new ImmutableMap.Builder<>();

		final String[] query = q.split("&");
		for (final String param : query) {
			final String[] pair = param.split("=");
			queryMapBuilder.put(pair[0], pair.length == 2 && !pair[1].isEmpty() ? pair[1] : null);
		}

		return queryMapBuilder.build();
	}
}