package com.foodordering.gateway;

import org.apache.hc.client5.http.DnsResolver;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.InitialDirContext;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Set;
import java.util.Locale;

/** DNS fallback limited to the public map services; the machine's DNS stays unchanged. */
final class MapDnsResolver implements DnsResolver {
    private static final Set<String> MAP_HOSTS = Set.of(
            "photon.komoot.io", "nominatim.openstreetmap.org", "tile.openstreetmap.org");
    @FunctionalInterface
    interface HostLookup {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    @FunctionalInterface
    interface RecordLookup {
        String[] resolve(String host) throws NamingException;
    }

    private final HostLookup system;
    private final RecordLookup fallback;

    MapDnsResolver() {
        this(InetAddress::getAllByName, MapDnsResolver::lookupPublicDns);
    }

    MapDnsResolver(HostLookup system, RecordLookup fallback) {
        this.system = system;
        this.fallback = fallback;
    }

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        try {
            return system.resolve(host);
        } catch (UnknownHostException original) {
            if (!MAP_HOSTS.contains(host.toLowerCase(Locale.ROOT))) throw original;
            try {
                var addresses = new ArrayList<InetAddress>();
                for (String record : fallback.resolve(host)) {
                    byte[] bytes = ipv4(record);
                    if (bytes != null) addresses.add(InetAddress.getByAddress(host, bytes));
                }
                if (addresses.isEmpty()) throw new UnknownHostException("No valid IPv4 map address");
                return addresses.toArray(InetAddress[]::new);
            } catch (Exception failure) {
                if (failure != original) original.addSuppressed(failure);
                throw original;
            }
        }
    }

    @Override
    public String resolveCanonicalHostname(String host) {
        return host;
    }

    private static byte[] ipv4(String record) {
        if (record == null || !record.matches("[0-9]{1,3}(?:\\.[0-9]{1,3}){3}")) return null;
        String[] parts = record.split("\\.");
        byte[] bytes = new byte[4];
        for (int index = 0; index < bytes.length; index++) {
            int value = Integer.parseInt(parts[index]);
            if (value > 255) return null;
            bytes[index] = (byte) value;
        }
        return bytes;
    }

    private static String[] lookupPublicDns(String host) throws NamingException {
        var settings = new Hashtable<String, String>();
        settings.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
        settings.put(Context.PROVIDER_URL, "dns://1.1.1.1/");
        settings.put("com.sun.jndi.dns.timeout.initial", "1000");
        settings.put("com.sun.jndi.dns.timeout.retries", "1");
        var context = new InitialDirContext(settings);
        try {
            var records = context.getAttributes(host, new String[]{"A"}).get("A");
            if (records == null) return new String[0];
            String[] values = new String[records.size()];
            for (int index = 0; index < values.length; index++) values[index] = records.get(index).toString();
            return values;
        } finally {
            context.close();
        }
    }
}
