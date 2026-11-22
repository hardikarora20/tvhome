package com.gathin.tvhome.home;

import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Build;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** HDMI / AV / Tuner sources and channel lookup via the Android TV input framework. */
final class TvSources {

    static final class Source {
        final String id;
        final String label;
        final int type;
        final boolean passthrough;
        final int state;

        Source(String id, String label, int type, boolean passthrough, int state) {
            this.id = id;
            this.label = label;
            this.type = type;
            this.passthrough = passthrough;
            this.state = state;
        }

        boolean connected() { return state == TvInputManager.INPUT_STATE_CONNECTED; }
        boolean isTuner() { return type == TvInputInfo.TYPE_TUNER; }
        boolean isHdmi() { return type == TvInputInfo.TYPE_HDMI || type == TvInputInfo.TYPE_DVI; }

        String status() {
            if (isTuner()) return "Antenna / Cable";
            if (state == TvInputManager.INPUT_STATE_CONNECTED) return "Connected";
            if (state == TvInputManager.INPUT_STATE_CONNECTED_STANDBY) return "Standby";
            if (state == TvInputManager.INPUT_STATE_DISCONNECTED) return "No signal";
            return typeName(type);
        }
    }

    private TvSources() {}

    static List<Source> list(Context ctx) {
        List<Source> out = new ArrayList<>();
        TvInputManager tim = (TvInputManager) ctx.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) {
            return out;
        }
        List<TvInputInfo> infos;
        try {
            infos = tim.getTvInputList();
        } catch (Exception e) {
            return out;
        }
        for (TvInputInfo info : infos) {
            if (info.isHidden(ctx)) continue;
            if (!info.isPassthroughInput() && info.getType() != TvInputInfo.TYPE_TUNER) continue;
            if (info.getParentId() != null) continue;
            CharSequence custom = info.loadCustomLabel(ctx);
            CharSequence label = custom != null && custom.length() > 0 ? custom : info.loadLabel(ctx);
            int state = -1;
            if (Build.VERSION.SDK_INT >= 24) {
                try {
                    state = tim.getInputState(info.getId());
                } catch (Exception ignored) {
                    // unknown
                }
            }
            out.add(new Source(info.getId(), String.valueOf(label), info.getType(),
                    info.isPassthroughInput(), state));
        }
        Collections.sort(out, (a, b) -> {
            if (a.isTuner() != b.isTuner()) return a.isTuner() ? -1 : 1;
            if (a.type != b.type) return a.isHdmi() ? -1 : (b.isHdmi() ? 1 : a.type - b.type);
            return a.label.compareToIgnoreCase(b.label);
        });
        return out;
    }

    static boolean open(Context ctx, Source s) {
        Intent i = new Intent(Intent.ACTION_VIEW, s.passthrough
                ? TvContract.buildChannelUriForPassthroughInput(s.id)
                : TvContract.buildChannelsUriForInput(s.id));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(i);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }

    /** content://android.media.tv/channel/<id> for a display number like "734", or null. */
    static Uri findChannel(Context ctx, String number) {
        ContentResolver cr = ctx.getContentResolver();
        String[] proj = {TvContract.Channels._ID, TvContract.Channels.COLUMN_DISPLAY_NUMBER,
                TvContract.Channels.COLUMN_BROWSABLE};
        Uri best = null;
        boolean bestBrowsable = false;
        try (Cursor c = cr.query(TvContract.Channels.CONTENT_URI, proj, null, null, null)) {
            if (c == null) {
                return null;
            }
            while (c.moveToNext()) {
                String num = c.getString(1);
                if (num == null) continue;
                num = num.trim();
                boolean exact = num.equals(number);
                boolean major = num.startsWith(number + "-") || num.startsWith(number + ".");
                if (!exact && !major) continue;
                boolean browsable = c.isNull(2) || c.getInt(2) == 1;
                Uri u = TvContract.buildChannelUri(c.getLong(0));
                if (exact && browsable) {
                    return u;
                }
                if (best == null || (browsable && !bestBrowsable)) {
                    best = u;
                    bestBrowsable = browsable;
                }
            }
        } catch (Exception e) {
            return null;
        }
        return best;
    }

    static String typeName(int type) {
        switch (type) {
            case TvInputInfo.TYPE_HDMI: return "HDMI";
            case TvInputInfo.TYPE_COMPOSITE: return "AV";
            case TvInputInfo.TYPE_COMPONENT: return "Component";
            case TvInputInfo.TYPE_SVIDEO: return "S-Video";
            case TvInputInfo.TYPE_SCART: return "SCART";
            case TvInputInfo.TYPE_VGA: return "VGA / PC";
            case TvInputInfo.TYPE_DVI: return "DVI";
            case TvInputInfo.TYPE_DISPLAY_PORT: return "DisplayPort";
            case TvInputInfo.TYPE_TUNER: return "TV";
            default: return "Input";
        }
    }
}
