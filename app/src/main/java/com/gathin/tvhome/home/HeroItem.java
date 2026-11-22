package com.gathin.tvhome.home;

/** One featured slide in the hero banner. */
final class HeroItem {
    static final int LOGO_SERIF = 0;
    static final int LOGO_BOLD = 1;
    static final int LOGO_WIDE = 2;
    static final int LOGO_APP = 3;

    final String id;
    final String title;
    final String meta;
    final String pkg;
    final String deepLink;   // null -> just launch the app
    final int artRes;
    final int logoStyle;
    final boolean watchlist; // false hides the Watchlist pill (Live Channels app slide)

    HeroItem(String id, String title, String meta, String pkg, String deepLink,
             int artRes, int logoStyle, boolean watchlist) {
        this.id = id;
        this.title = title;
        this.meta = meta;
        this.pkg = pkg;
        this.deepLink = deepLink;
        this.artRes = artRes;
        this.logoStyle = logoStyle;
        this.watchlist = watchlist;
    }
}
