package eu.siacs.conversations.crypto.axolotl;

import android.content.ContentValues;
import android.database.Cursor;

public class FingerprintStatus {

    private Trust trust = Trust.UNTRUSTED;
    private boolean active = false;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FingerprintStatus that = (FingerprintStatus) o;

        return active == that.active && trust == that.trust;
    }

    @Override
    public int hashCode() {
        int result = trust.hashCode();
        result = 31 * result + (active ? 1 : 0);
        return result;
    }

    private FingerprintStatus() {


    }

    public ContentValues toContentValues() {
        final ContentValues contentValues = new ContentValues();
        contentValues.put(SQLiteAxolotlStore.TRUST,trust.toString());
        contentValues.put(SQLiteAxolotlStore.ACTIVE,active ? 1 : 0);
        return contentValues;
    }

    public static FingerprintStatus fromCursor(Cursor cursor) {
        final FingerprintStatus status = new FingerprintStatus();
        try {
            status.trust = Trust.valueOf(cursor.getString(cursor.getColumnIndex(SQLiteAxolotlStore.TRUST)));
        } catch(IllegalArgumentException e) {
            status.trust = Trust.UNTRUSTED;
        }
        status.active = cursor.getInt(cursor.getColumnIndex(SQLiteAxolotlStore.ACTIVE)) > 0;
        return status;
    }

    public static FingerprintStatus createActiveUndecided() {
        final FingerprintStatus status = new FingerprintStatus();
        status.trust = Trust.UNDECIDED;
        status.active = true;
        return status;
    }

    public static FingerprintStatus createActiveVerified(boolean x509) {
        final FingerprintStatus status = new FingerprintStatus();
        status.trust = x509 ? Trust.VERIFIED_X509 : Trust.VERIFIED;
        status.active = true;
        return status;
    }

    public static FingerprintStatus createActive(boolean trusted) {
        final FingerprintStatus status = new FingerprintStatus();
        status.trust = trusted ? Trust.TRUSTED : Trust.UNTRUSTED;
        status.active = true;
        return status;
    }

    public boolean isTrustedAndActive() {
        return active && isTrusted();
    }

    public boolean isTrusted() {
        return trust == Trust.TRUSTED || trust == Trust.VERIFIED || trust == Trust.VERIFIED_X509;
    }

    public boolean isCompromised() {
        return trust == Trust.COMPROMISED;
    }

    public boolean isActive() {
        return active;
    }

    public FingerprintStatus toActive() {
        FingerprintStatus status = new FingerprintStatus();
        status.trust = trust;
        status.active = true;
        return status;
    }

    public FingerprintStatus toInactive() {
        FingerprintStatus status = new FingerprintStatus();
        status.trust = trust;
        status.active = false;
        return status;
    }

    public Trust getTrust() {
        return trust;
    }

    public static FingerprintStatus createCompromised() {
        FingerprintStatus status = new FingerprintStatus();
        status.active = false;
        status.trust = Trust.COMPROMISED;
        return status;
    }

    public enum Trust {
        COMPROMISED,
        UNDECIDED,
        UNTRUSTED,
        TRUSTED,
        VERIFIED,
        VERIFIED_X509
    }

}
