package com.gathin.tvhome.home;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Build;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** HDMI / AV / Component / Tuner sources from the Android TV input framework. */
final class TvSources {

    static final class Source {
        final String id;
        final String label;
        final int type;
        final boolean passthrough;
        final int state; // TvInputManager.INPUT_STATE_*, -1 unknown

        Source(String id, String label, int type, boolean passthrough, int state) {
            this.id = id;
            this.label = label;
            this.type = type;
            this.passthrough = passthrough;
            this.state = state;
        }

        boolean connected() {
            return state == TvInputManager.INPUT_STATE_CONNECTED;
        }

        boolean isTuner() {
            return type == TvInputInfo.TYPE_TUNER;
        }

        boolean isHdmi() {
            return type == TvInputInfo.TYPE_HDMI || type == TvInputInfo.TYPE_DVI;
        }

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
            if (info.isHidden(ctx)) {
                continue;
            }
            // Only physical sources and tuners. Skip IPTV / app inputs.
            if (!info.isPassthroughInput() && info.getType() != TvInputInfo.TYPE_TUNER) {
                continue;
            }
            // HDMI-CEC child devices appear under their HDMI port; list ports only.
            if (info.getParentId() != null) {
                continue;
            }
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

    /** Switch the TV to a source. Passthrough URIs are handled by the system TV app. */
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
