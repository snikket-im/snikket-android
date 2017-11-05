/*
 * Copyright (c) 2017, Daniel Gultsch All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package eu.siacs.conversations.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ImStyleParser {

	final static List<Character> KEYWORDS = Arrays.asList('*', '_', '~', '`');
	final static List<Character> NO_SUB_PARSING_KEYWORDS = Arrays.asList('`');
	final static boolean ALLOW_EMPTY = false;

	public static List<Style> parse(CharSequence text) {
		return parse(text, 0, text.length() - 1);
	}

	public static List<Style> parse(CharSequence text, int start, int end) {
		List<Style> styles = new ArrayList<>();
		for (int i = start; i <= end; ++i) {
			char c = text.charAt(i);
			if (KEYWORDS.contains(c) && precededByWhiteSpace(text, i, start) && !followedByWhitespace(text, i, end)) {
				int to = seekEnd(text, c, i + 1, end);
				if (to != -1 && (to != i + 1 || ALLOW_EMPTY)) {
					styles.add(new Style(c, i, to));
					if (!NO_SUB_PARSING_KEYWORDS.contains(c)) {
						styles.addAll(parse(text, i + 1, to - 1));
					}
					i = to;
				}
			}
		}
		return styles;
	}

	private static boolean precededByWhiteSpace(CharSequence text, int index, int start) {
		return index == start || Character.isWhitespace(text.charAt(index - 1));
	}

	private static boolean followedByWhitespace(CharSequence text, int index, int end) {
		return index >= end || Character.isWhitespace(text.charAt(index + 1));
	}

	private static int seekEnd(CharSequence text, char needle, int start, int end) {
		for (int i = start; i <= end; ++i) {
			char c = text.charAt(i);
			if (c == needle) {
				return i;
			} else if (c == '\n') {
				return -1;
			}
		}
		return -1;
	}

	public static class Style {

		private final char c;
		private final int start;
		private final int end;

		public Style(char c, int start, int end) {
			this.c = c;
			this.start = start;
			this.end = end;
		}

		public char getCharacter() {
			return c;
		}

		public int getStart() {
			return start;
		}

		public int getEnd() {
			return end;
		}
	}
}
