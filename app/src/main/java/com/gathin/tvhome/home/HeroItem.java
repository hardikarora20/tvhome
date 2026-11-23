package com.gathin.tvhome.home;

/** One banner slide, built from an entry in banner.json. */
final class HeroItem {
    final String id;
    final String title;
    final String meta;
    final String image;              // res:name | asset:path | https://... | file name in files/hero/ | /abs/path
    final int color;                 // launch-animation / placeholder colour, 0 = auto
    final BannerAction play;         // Play button
    final BannerAction secondary;    // second pill; null hides it

    HeroItem(String id, String title, String meta, String image, int color,
             BannerAction play, BannerAction secondary) {
        this.id = id;
        this.title = title;
        this.meta = meta;
        this.image = image;
        this.color = color;
        this.play = play;
        this.secondary = secondary;
    }
}
