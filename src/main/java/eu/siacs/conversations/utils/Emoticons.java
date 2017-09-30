package eu.siacs.conversations.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Emoticons {

	private static final UnicodeRange MISC_SYMBOLS_AND_PICTOGRAPHS = new UnicodeRange(0x1F300,0x1F5FF);
	private static final UnicodeRange SUPPLEMENTAL_SYMBOLS = new UnicodeRange(0x1F900,0x1F9FF);
	private static final UnicodeRange EMOTICONS = new UnicodeRange(0x1F600,0x1F64F);
	private static final UnicodeRange TRANSPORT_SYMBOLS = new UnicodeRange(0x1F680,0x1F6FF);
	private static final UnicodeRange MISC_SYMBOLS = new UnicodeRange(0x2600,0x26FF);
	private static final UnicodeRange DINGBATS = new UnicodeRange(0x2700,0x27BF);
	private static final UnicodeRange ENCLOSED_ALPHANUMERIC_SUPPLEMENT = new UnicodeRange(0x1F100,0x1F1FF);
	private static final UnicodeRange ENCLOSED_IDEOGRAPHIC_SUPPLEMENT = new UnicodeRange(0x1F200,0x1F2FF);
	private static final UnicodeRange REGIONAL_INDICATORS = new UnicodeRange(0x1F1E6,0x1F1FF);
	private static final UnicodeRange GEOMETRIC_SHAPES = new UnicodeRange(0x25A0,0x25FF);
	private static final UnicodeRange LATIN_SUPPLEMENT = new UnicodeRange(0x80,0xFF);
	private static final UnicodeRange MISC_TECHNICAL = new UnicodeRange(0x2300,0x23FF);
	private static final UnicodeRange TAGS = new UnicodeRange(0xE0020,0xE007F);
	private static final UnicodeList CYK_SYMBOLS_AND_PUNCTUATION = new UnicodeList(0x3030,0x303D);
	private static final UnicodeList LETTERLIKE_SYMBOLS = new UnicodeList(0x2122,0x2139);

	private static final UnicodeBlocks KEYCAP_COMBINEABLE = new UnicodeBlocks(new UnicodeList(0x23),new UnicodeList(0x2A),new UnicodeRange(0x30,0x39));

	private static final UnicodeBlocks SYMBOLIZE = new UnicodeBlocks(
			GEOMETRIC_SHAPES,
			LATIN_SUPPLEMENT,
			CYK_SYMBOLS_AND_PUNCTUATION,
			LETTERLIKE_SYMBOLS,
			KEYCAP_COMBINEABLE);
	private static final UnicodeBlocks EMOJIS = new UnicodeBlocks(
			MISC_SYMBOLS_AND_PICTOGRAPHS,
			SUPPLEMENTAL_SYMBOLS,
			EMOTICONS,
			TRANSPORT_SYMBOLS,
			MISC_SYMBOLS,
			DINGBATS,
			ENCLOSED_ALPHANUMERIC_SUPPLEMENT,
			ENCLOSED_IDEOGRAPHIC_SUPPLEMENT,
			MISC_TECHNICAL);

	private static final int ZWJ = 0x200D;
	private static final int VARIATION_16 = 0xFE0F;
	private static final int COMBINING_ENCLOSING_KEYCAP = 0x20E3;
	private static final int BLACK_FLAG = 0x1F3F4;
	private static final UnicodeRange FITZPATRICK = new UnicodeRange(0x1F3FB,0x1F3FF);

	private static List<Symbol> parse(String input) {
		List<Symbol> symbols = new ArrayList<>();
		Builder builder = new Builder();
		boolean needsFinalBuild = false;
		for (int cp, i = 0; i < input.length(); i += Character.charCount(cp)) {
			cp = input.codePointAt(i);
			if (builder.offer(cp)) {
				needsFinalBuild = true;
			} else {
				symbols.add(builder.build());
				builder = new Builder();
				if (builder.offer(cp)) {
					needsFinalBuild = true;
				}
			}
		}
		if (needsFinalBuild) {
			symbols.add(builder.build());
		}
		return symbols;
	}

	public static boolean isEmoji(String input) {
		List<Symbol> symbols = parse(input);
		return symbols.size() == 1 && symbols.get(0) == Symbol.EMOJI;
	}

	public static boolean isOnlyEmoji(String input) {
		List<Symbol> symbols = parse(input);
		for(Symbol symbol : symbols) {
			if (symbol == Symbol.NON_EMOJI) {
				return false;
			}
		}
		return symbols.size() > 0;
	}

	private enum Symbol {
		EMOJI, NON_EMOJI
	}


	private static class Builder {
		private final List<Integer> codepoints = new ArrayList<>();


		public boolean offer(int codepoint) {
			boolean add = false;
			if (this.codepoints.size() == 0) {
				if (SYMBOLIZE.contains(codepoint)) {
					add = true;
				} else if (REGIONAL_INDICATORS.contains(codepoint)) {
					add = true;
				} else if (EMOJIS.contains(codepoint) && !FITZPATRICK.contains(codepoint) && codepoint != ZWJ) {
					add = true;
				}
			} else {
				int previous = codepoints.get(codepoints.size() -1);
				if (codepoints.get(0) == BLACK_FLAG) {
					add = TAGS.contains(codepoint);
				} else if (COMBINING_ENCLOSING_KEYCAP == codepoint) {
					add = KEYCAP_COMBINEABLE.contains(previous) || previous == VARIATION_16;
				} else if (SYMBOLIZE.contains(previous)) {
					add = codepoint == VARIATION_16;
				} else if (REGIONAL_INDICATORS.contains(previous) && REGIONAL_INDICATORS.contains(codepoint)) {
					add = codepoints.size() == 1;
				} else if (previous == VARIATION_16) {
					add = isMerger(codepoint);
				} else if (FITZPATRICK.contains(previous)) {
					add = codepoint == ZWJ;
				} else if (ZWJ == previous) {
					add = EMOJIS.contains(codepoint);
				} else if (isMerger(codepoint)) {
					add = true;
				} else if (codepoint == VARIATION_16 && EMOJIS.contains(previous)) {
					add = true;
				}
			}
			if (add) {
				codepoints.add(codepoint);
				return true;
			} else {
				return false;
			}
		}

		private static boolean isMerger(int codepoint) {
			return codepoint == ZWJ || FITZPATRICK.contains(codepoint);
		}

		public Symbol build() {
			if (codepoints.size() > 0 && SYMBOLIZE.contains(codepoints.get(codepoints.size() - 1))) {
				return Symbol.NON_EMOJI;
			} else if (codepoints.size() > 1 && KEYCAP_COMBINEABLE.contains(codepoints.get(0)) && codepoints.get(codepoints.size() - 1) != COMBINING_ENCLOSING_KEYCAP) {
				return Symbol.NON_EMOJI;
			}
			return codepoints.size() == 0 ? Symbol.NON_EMOJI : Symbol.EMOJI;
		}
	}

	public static class UnicodeBlocks implements UnicodeSet {
		final UnicodeSet[] unicodeSets;

		public UnicodeBlocks(UnicodeSet... sets) {
			this.unicodeSets = sets;
		}

		@Override
		public boolean contains(int codepoint) {
			for(UnicodeSet unicodeSet : unicodeSets) {
				if (unicodeSet.contains(codepoint)) {
					return true;
				}
			}
			return false;
		}
	}

	public interface UnicodeSet {
		boolean contains(int codepoint);
	}

	public static class UnicodeList implements UnicodeSet {

		private final List<Integer> list;

		public UnicodeList(Integer... codes) {
			this.list = Arrays.asList(codes);
		}

		@Override
		public boolean contains(int codepoint) {
			return this.list.contains(codepoint);
		}
	}


	public static class UnicodeRange implements UnicodeSet {

		private final int lower;
		private final int upper;

		UnicodeRange(int lower, int upper) {
			this.lower = lower;
			this.upper = upper;
		}

		public boolean contains(int codePoint) {
			return codePoint >= lower && codePoint <= upper;
		}
	}
}
