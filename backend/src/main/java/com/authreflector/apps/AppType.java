package com.authreflector.apps;

public enum AppType {
    /** Has a backend: confidential client with a secret, integrates with the Spring client starter (BFF). */
    WEB,
    /** Frontend only: public client without a secret, PKCE in the browser, integrates with the JS SDK. */
    SPA
}
