package eu.siacs.conversations.utils;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;
import com.google.common.base.Strings;
import eu.siacs.conversations.xmpp.Jid;
import okhttp3.HttpUrl;

public class EasyOnboardingInvite implements Parcelable {

    private final Jid domain;
    private final String uri;
    private final HttpUrl landingUrl;

    protected EasyOnboardingInvite(final Parcel in) {
        this.domain = Jid.ofDomain(in.readString());
        this.uri = in.readString();
        final var landingUrl = in.readString();
        this.landingUrl = Strings.isNullOrEmpty(landingUrl) ? null : HttpUrl.parse(landingUrl);
    }

    public EasyOnboardingInvite(@NonNull final Jid domain, @NonNull final String uri) {
        this.domain = domain;
        this.uri = uri;
        this.landingUrl = null;
    }

    public EasyOnboardingInvite(
            @NonNull final Jid domain,
            @NonNull final String uri,
            @NonNull final HttpUrl landingUrl) {
        this.domain = domain;
        this.uri = uri;
        this.landingUrl = landingUrl;
    }

    @Override
    public void writeToParcel(final Parcel dest, final int flags) {
        dest.writeString(domain.toString());
        dest.writeString(uri);
        dest.writeString(landingUrl == null ? null : landingUrl.toString());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<EasyOnboardingInvite> CREATOR =
            new Creator<>() {
                @Override
                public EasyOnboardingInvite createFromParcel(Parcel in) {
                    return new EasyOnboardingInvite(in);
                }

                @Override
                public EasyOnboardingInvite[] newArray(int size) {
                    return new EasyOnboardingInvite[size];
                }
            };

    public String getDomain() {
        return domain.toString();
    }

    public String getShareableLink() {
        return this.landingUrl != null ? this.landingUrl.toString() : this.uri;
    }
}
