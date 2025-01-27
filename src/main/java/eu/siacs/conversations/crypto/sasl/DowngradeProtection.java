package eu.siacs.conversations.crypto.sasl;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import java.util.Collection;

public class DowngradeProtection {

    private static final char SEPARATOR = 0x1E;
    private static final char SEPARATOR_MECHANISM_AND_BINDING = 0x1F;

    public final ImmutableList<String> mechanisms;
    public final ImmutableList<String> channelBindings;

    public DowngradeProtection(
            final Collection<String> mechanisms, final Collection<String> channelBindings) {
        this.mechanisms = Ordering.natural().immutableSortedCopy(mechanisms);
        this.channelBindings = Ordering.natural().immutableSortedCopy(channelBindings);
    }

    public DowngradeProtection(final Collection<String> mechanisms) {
        this.mechanisms = Ordering.natural().immutableSortedCopy(mechanisms);
        this.channelBindings = null;
    }

    public String asHString() {
        ensureSaslMechanismFormat(this.mechanisms);
        ensureNoSeparators(this.mechanisms);
        if (this.channelBindings != null) {
            ensureNoSeparators(this.channelBindings);
            ensureBindingFormat(this.channelBindings);
            final var builder = new StringBuilder();
            Joiner.on(SEPARATOR).appendTo(builder, mechanisms);
            builder.append(SEPARATOR_MECHANISM_AND_BINDING);
            Joiner.on(SEPARATOR).appendTo(builder, channelBindings);
            return builder.toString();
        } else {
            return Joiner.on(SEPARATOR).join(mechanisms);
        }
    }

    private static void ensureNoSeparators(final Iterable<String> list) {
        for (final String item : list) {
            if (item.indexOf(SEPARATOR) >= 0
                    || item.indexOf(SEPARATOR_MECHANISM_AND_BINDING) >= 0) {
                throw new SecurityException("illegal chars found in list");
            }
        }
    }

    private static void ensureSaslMechanismFormat(final Iterable<String> names) {
        for (final String name : names) {
            ensureSaslMechanismFormat(name);
        }
    }

    private static void ensureSaslMechanismFormat(final String name) {
        if (Strings.isNullOrEmpty(name)) {
            throw new SecurityException("Empty sasl mechanism names are not permitted");
        }
        // https://www.rfc-editor.org/rfc/rfc4422.html#section-3.1
        if (name.length() <= 20
                && CharMatcher.inRange('A', 'Z')
                        .or(CharMatcher.inRange('0', '9'))
                        .or(CharMatcher.is('-'))
                        .or(CharMatcher.is('_'))
                        .matchesAllOf(name)
                && !Character.isDigit(name.charAt(0))) {
            return;
        }
        throw new SecurityException("Encountered illegal sasl name");
    }

    private static void ensureBindingFormat(final Iterable<String> names) {
        for (final String name : names) {
            ensureBindingFormat(name);
        }
    }

    private static void ensureBindingFormat(final String name) {
        if (Strings.isNullOrEmpty(name)) {
            throw new SecurityException("Empty binding names are not permitted");
        }
        // https://www.rfc-editor.org/rfc/rfc5056.html#section-7d
        if (CharMatcher.inRange('A', 'Z')
                .or(CharMatcher.inRange('a', 'z'))
                .or(CharMatcher.inRange('0', '9'))
                .or(CharMatcher.is('.'))
                .or(CharMatcher.is('-'))
                .matchesAllOf(name)) {
            return;
        }
        throw new SecurityException("Encountered illegal binding name");
    }
}
