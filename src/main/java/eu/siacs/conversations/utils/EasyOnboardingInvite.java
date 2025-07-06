package eu.siacs.conversations.utils;

import android.os.Parcel;
import android.os.Parcelable;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.QuickConversationsService;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.manager.DiscoManager;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
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

    public EasyOnboardingInvite(final Jid domain, final String uri, final HttpUrl landingUrl) {
        this.domain = domain;
        this.uri = uri;
        this.landingUrl = landingUrl;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(domain.toString());
        dest.writeString(uri);
        dest.writeString(landingUrl.toString());
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

    public static boolean anyHasSupport(final XmppConnectionService service) {
        if (QuickConversationsService.isQuicksy()) {
            return false;
        }
        return !getSupportingAccounts(service).isEmpty();
    }

    public static List<Account> getSupportingAccounts(final XmppConnectionService service) {
        final ImmutableList.Builder<Account> supportingAccountsBuilder =
                new ImmutableList.Builder<>();
        final List<Account> accounts =
                service == null ? Collections.emptyList() : service.getAccounts();
        for (final var account : accounts) {
            final var connection = account.getXmppConnection();
            final var discoManager = connection.getManager(DiscoManager.class);
            if (Objects.nonNull(
                    discoManager.getAddressForCommand(Namespace.EASY_ONBOARDING_INVITE))) {
                supportingAccountsBuilder.add(account);
            }
        }
        return supportingAccountsBuilder.build();
    }

    public String getDomain() {
        return domain.toString();
    }

    public String getShareableLink() {
        return this.landingUrl.toString();
    }
}
