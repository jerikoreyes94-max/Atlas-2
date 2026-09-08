package com.atlas.personalai;
import android.service.voice.VoiceInteractionSession;
import android.service.voice.VoiceInteractionSessionService;
public class AtlasVoiceInteractionSessionService extends VoiceInteractionSessionService {
    @Override public VoiceInteractionSession onNewSession(android.os.Bundle args) {
        return new AtlasVoiceInteractionSession(this);
    }
}
