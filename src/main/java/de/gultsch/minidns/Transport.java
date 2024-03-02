package de.gultsch.minidns;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

public enum Transport {
    UDP,
    TCP,
    TLS,
    HTTPS;

    public static final Map<Transport, Integer> DEFAULT_PORTS;

    static {
        final ImmutableMap.Builder<Transport, Integer> builder = new ImmutableMap.Builder<>();
        builder.put(Transport.UDP, 53);
        builder.put(Transport.TCP, 53);
        builder.put(Transport.TLS, 853);
        builder.put(Transport.HTTPS, 443);
        DEFAULT_PORTS = builder.build();
    }
}
