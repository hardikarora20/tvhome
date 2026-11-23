package com.gathin.tvhome.home;

import java.util.Collections;
import java.util.Map;

/**
 * What a banner button does. Types:
 *  link    - open "uri" (ACTION_VIEW), optionally in "package"; "anyApp": true retries in any app
 *  app     - launch "package"
 *  channel - tune Live Channels to channel "number"
 *  intent  - custom: "action", "uri", "package", "class", "extras"
 * If it fails, "fallback" (another action) is tried.
 */
final class BannerAction {
    static final String LINK = "link";
    static final String APP = "app";
    static final String CHANNEL = "channel";
    static final String INTENT = "intent";

    final String type;
    final String uri;
    final String pkg;
    final String cls;
    final String action;
    final String number;
    final String label;   // text on the second pill
    final String icon;    // "open" | "live" | "play"
    final boolean anyApp;
    final Map<String, String> extras;
    final BannerAction fallback;

    BannerAction(String type, String uri, String pkg, String cls, String action, String number,
                 String label, String icon, boolean anyApp, Map<String, String> extras,
                 BannerAction fallback) {
        this.type = type;
        this.uri = uri;
        this.pkg = pkg;
        this.cls = cls;
        this.action = action;
        this.number = number;
        this.label = label;
        this.icon = icon;
        this.anyApp = anyApp;
        this.extras = extras != null ? extras : Collections.<String, String>emptyMap();
        this.fallback = fallback;
    }
}
