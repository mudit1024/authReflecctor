package com.authreflector.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the React app (built into static/index.html) for its client-side routes, so a refresh or a
 * deep link such as /register works. React Router then renders the right page in the browser.
 *
 * Routes are listed explicitly instead of a catch-all: unknown paths (e.g. a mistyped API URL) must
 * stay real 404s rather than silently returning HTML. Keep this list in sync with frontend/src/App.tsx.
 */
@Controller
public class SpaController {

    @GetMapping({"/", "/login", "/register", "/account", "/admin/apps", "/admin/apps/new", "/admin/apps/{clientId}"})
    public String index() {
        return "forward:/index.html";
    }
}
