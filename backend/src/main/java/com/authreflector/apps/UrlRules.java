package com.authreflector.apps;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

/**
 * Which URLs an admin may register.
 *
 * Redirect URIs receive sign-in codes, so they are strict: https anywhere, plain http only on this
 * machine as an IP literal (OAuth 2.1; Spring Authorization Server refuses "localhost"), no fragments,
 * no credentials. Home URLs are only opened by the launcher, so any http(s) address is fine.
 */
public final class UrlRules {

    private static final Set<String> LOOPBACK_IPS = Set.of("127.0.0.1", "[::1]");

    private UrlRules() {
    }

    public static boolean isAllowedRedirectUri(String value) {
        URI uri = parse(value);
        if (uri == null || !uri.isAbsolute() || uri.getHost() == null
                || uri.getFragment() != null || uri.getUserInfo() != null) {
            return false;
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        return scheme.equals("https") || (scheme.equals("http") && LOOPBACK_IPS.contains(uri.getHost()));
    }

    public static boolean isAllowedHomeUrl(String value) {
        URI uri = parse(value);
        if (uri == null || !uri.isAbsolute() || uri.getHost() == null) {
            return false;
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        return scheme.equals("https") || scheme.equals("http");
    }

    private static URI parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new URI(value.trim());
        } catch (URISyntaxException exception) {
            return null;
        }
    }
}
