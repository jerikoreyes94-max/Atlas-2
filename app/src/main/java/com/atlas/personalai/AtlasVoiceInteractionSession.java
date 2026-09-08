package com.atlas.personalai;

import android.content.Context;
import android.content.Intent;
import android.service.voice.VoiceInteractionSession;

public class AtlasVoiceInteractionSession extends VoiceInteractionSession {
    public AtlasVoiceInteractionSession(Context c) { super(c); }
    @Override public void onShow(android.os.Bundle args, int flags) {
        super.onShow(args, flags);
        try {
            Intent i = new Intent(getContext(), MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            i.putExtra("atlas_auto_listen", true);
            getContext().startActivity(i);
        } catch (Exception ignored) {}
    }
}
