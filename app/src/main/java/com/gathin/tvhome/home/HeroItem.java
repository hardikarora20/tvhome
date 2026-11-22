package com.gathin.tvhome.home;

/** One featured slide in the hero banner. */
final class HeroItem {
    final String id;
    final String title;
    final String meta;
    final String pkg;
    final String deepLink;      // show / app deep link; null -> just launch the app
    final String channelNumber; // non-null -> tune this channel number in Live Channels
    final int artRes;
    final boolean watchlist;    // false hides the Watchlist pill (app / channel slides)

    HeroItem(String id, String title, String meta, String pkg, String deepLink,
             String channelNumber, int artRes, boolean watchlist) {
        this.id = id;
        this.title = title;
        this.meta = meta;
        this.pkg = pkg;
        this.deepLink = deepLink;
        this.channelNumber = channelNumber;
        this.artRes = artRes;
        this.watchlist = watchlist;
    }
}
