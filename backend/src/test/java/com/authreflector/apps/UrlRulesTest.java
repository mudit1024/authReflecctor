package com.authreflector.apps;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UrlRulesTest {

    // Where sign-in codes may be sent: https anywhere, plain http only on this machine (127.0.0.1)
    @ParameterizedTest
    @CsvSource({
            "https://splitmate.example.com/login/oauth2/code/authreflector, true",
            "http://127.0.0.1:5173/login/oauth2/code/authreflector, true",
            "http://[::1]:5173/cb, true",
            "http://127.0.0.1:5175/, true",
            // plain http over a network: codes could be read on the way
            "http://splitmate.example.com/cb, false",
            // OAuth 2.1 / Spring Authorization Server: loopback must be an IP literal, not 'localhost'
            "http://localhost:5173/cb, false",
            "javascript:alert(1), false",
            "https://example.com/cb#fragment, false",
            "/relative/path, false",
            "https://user:pass@example.com/cb, false",
            "not a url, false",
    })
    void shouldAcceptOnlySafeRedirectUris(String uri, boolean allowed) {
        assertThat(UrlRules.isAllowedRedirectUri(uri)).isEqualTo(allowed);
    }

    // The launcher only opens the app's home page: any http(s) address is fine
    @ParameterizedTest
    @CsvSource({
            "https://codequror.example.com/, true",
            "http://localhost:5173/, true",
            "http://127.0.0.1:5175/, true",
            "ftp://example.com/, false",
            "javascript:alert(1), false",
            "example.com, false",
    })
    void shouldAcceptOnlyWebHomeUrls(String url, boolean allowed) {
        assertThat(UrlRules.isAllowedHomeUrl(url)).isEqualTo(allowed);
    }
}
