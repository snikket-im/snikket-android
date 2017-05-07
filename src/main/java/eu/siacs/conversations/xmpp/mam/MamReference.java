package eu.siacs.conversations.xmpp.mam;

public class MamReference {

    private final long timestamp;
    private final String reference;

    public MamReference(long timestamp) {
        this.timestamp = timestamp;
        this.reference = null;
    }

    public MamReference(long timestamp, String reference) {
        this.timestamp = timestamp;
        this.reference = reference;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getReference() {
        return reference;
    }

    public boolean greaterThan(MamReference b) {
        return timestamp > b.getTimestamp();
    }

    public boolean greaterThan(long b) {
        return timestamp > b;
    }

    public static MamReference max(MamReference a, MamReference b) {
        if (a != null && b != null) {
            return a.timestamp > b.timestamp ? a : b;
        } else if (a != null) {
            return a;
        } else {
            return b;
        }
    }

    public static MamReference max(MamReference a, long b) {
        return max(a,new MamReference(b));
    }

    public static MamReference fromAttribute(String attr) {
        if (attr == null) {
            return new MamReference(0);
        } else {
            String[] attrs = attr.split(":");
            try {
                long timestamp = Long.parseLong(attrs[0]);
                if (attrs.length >= 2) {
                    return new MamReference(timestamp,attrs[1]);
                } else {
                    return new MamReference(timestamp);
                }
            } catch (Exception e) {
                return new MamReference(0);
            }
        }
    }

    public MamReference timeOnly() {
        return reference == null ? this : new MamReference(timestamp);
    }
}
