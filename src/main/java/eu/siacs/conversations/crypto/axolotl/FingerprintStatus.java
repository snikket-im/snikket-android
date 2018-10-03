package eu.siacs.conversations.crypto.axolotl;

import android.content.ContentValues;
import android.database.Cursor;

public class FingerprintStatus implements Comparable<FingerprintStatus> {

    private static final long DO_NOT_OVERWRITE = -1;

    private Trust trust = Trust.UNTRUSTED;
    private boolean active = false;
    private long lastActivation = DO_NOT_OVERWRITE;

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
        if (lastActivation != DO_NOT_OVERWRITE) {
            contentValues.put(SQLiteAxolotlStore.LAST_ACTIVATION,lastActivation);
        }
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
        status.lastActivation = cursor.getLong(cursor.getColumnIndex(SQLiteAxolotlStore.LAST_ACTIVATION));
        return status;
    }

    public static FingerprintStatus createActiveUndecided() {
        final FingerprintStatus status = new FingerprintStatus();
        status.trust = Trust.UNDECIDED;
        status.active = true;
        status.lastActivation = System.currentTimeMillis();
        return status;
    }

    public static FingerprintStatus createActiveTrusted() {
        final FingerprintStatus status = new FingerprintStatus();
        status.trust = Trust.TRUSTED;
        status.active = true;
        status.lastActivation = System.currentTimeMillis();
        return status;
    }

    public static FingerprintStatus createActiveVerified(boolean x509) {
        final FingerprintStatus status = new FingerprintStatus();
        status.trust = x509 ? Trust.VERIFIED_X509 : Trust.VERIFIED;
        status.active = true;
        return status;
    }

    public static FingerprintStatus createActive(Boolean trusted) {
        return createActive(trusted != null && trusted);
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
        return trust == Trust.TRUSTED || isVerified();
    }

    public boolean isVerified() {
        return trust == Trust.VERIFIED || trust == Trust.VERIFIED_X509;
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
        if (!status.active) {
            status.lastActivation = System.currentTimeMillis();
        }
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

    public FingerprintStatus toVerified() {
        FingerprintStatus status = new FingerprintStatus();
        status.active = active;
        status.trust = Trust.VERIFIED;
        return status;
    }

    public FingerprintStatus toUntrusted() {
        FingerprintStatus status = new FingerprintStatus();
        status.active = active;
        status.trust = Trust.UNTRUSTED;
        return status;
    }

    public static FingerprintStatus createInactiveVerified() {
        final FingerprintStatus status = new FingerprintStatus();
        status.trust = Trust.VERIFIED;
        status.active = false;
        return status;
    }

    @Override
    public int compareTo(FingerprintStatus o) {
        if (active == o.active) {
            if (lastActivation > o.lastActivation) {
                return -1;
            } else if (lastActivation < o.lastActivation) {
                return 1;
            } else {
                return 0;
            }
        } else if (active){
            return -1;
        } else {
            return 1;
        }
    }

    public long getLastActivation() {
        return lastActivation;
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
