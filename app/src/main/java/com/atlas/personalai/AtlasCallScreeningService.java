package com.atlas.personalai;

import android.telecom.Call;
import android.telecom.CallScreeningService;

public class AtlasCallScreeningService extends CallScreeningService {
    @Override public void onScreenCall(Call.Details details) {
        try {
            String number = details.getHandle() == null ? "unknown" : details.getHandle().getSchemeSpecificPart();
            AtlasStore.addNotification(this, System.currentTimeMillis() + " | call | incoming | " + number);
        } catch (Exception ignored) {}
        respondToCall(details, new CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSilenceCall(false)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build());
    }
}
