package im.conversations.android.xmpp.model.mam;

import android.util.Log;
import com.google.common.base.CaseFormat;
import com.google.common.base.Strings;
import eu.siacs.conversations.Config;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(name = "prefs")
public class Preferences extends Extension {

    public Preferences() {
        super(Preferences.class);
    }

    public Default getDefault() {
        final var value = this.getAttribute("default");
        if (Strings.isNullOrEmpty(value)) {
            return null;
        }
        try {
            return Default.valueOf(CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_UNDERSCORE, value));
        } catch (final IllegalArgumentException e) {
            Log.d(Config.LOGTAG, "could not parse " + value, e);
            return null;
        }
    }

    public void setDefault(final Default preference) {
        this.setAttribute("default", preference);
    }

    public enum Default {
        NEVER,
        ROSTER,
        ALWAYS
    }
}
