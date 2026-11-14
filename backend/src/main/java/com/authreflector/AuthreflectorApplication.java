package com.authreflector;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AuthreflectorApplication {

    public static void main(String[] args) {
        useUtc();
        SpringApplication.run(AuthreflectorApplication.class, args);
    }

    // The server always runs in UTC: token expiries, sessions and audit timestamps must not depend
    // on the host machine's time zone. Only the UI converts to the user's local time.
    // (Also avoids old zone aliases such as "Asia/Calcutta" that PostgreSQL 17 rejects.)
    static void useUtc() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }
}
