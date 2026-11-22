package com.gathin.tvhome.home;

/** One featured slide in the hero banner. */
final class HeroItem {
    final String id;
    final String title;
    final String meta;
    final String pkg;           // app that plays it
    final String deepLink;      // Play target; null -> just launch the app
    final String channelNumber; // Live Channels fallback / secondary target (e.g. "734")
    final String openLabel;     // secondary pill text ("Open Netflix"); null hides it
    final int artRes;

    HeroItem(String id, String title, String meta, String pkg, String deepLink,
             String channelNumber, String openLabel, int artRes) {
        this.id = id;
        this.title = title;
        this.meta = meta;
        this.pkg = pkg;
        this.deepLink = deepLink;
        this.channelNumber = channelNumber;
        this.openLabel = openLabel;
        this.artRes = artRes;
    }
}
