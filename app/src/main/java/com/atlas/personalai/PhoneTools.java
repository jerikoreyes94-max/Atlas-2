package com.atlas.personalai;

import android.Manifest;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.telecom.TelecomManager;

public final class PhoneTools {
    public static final int ROLE_REQUEST = 71;
    private PhoneTools() {}

    public static boolean callScreeningReady(Context c) {
        if (Build.VERSION.SDK_INT < 29) return false;
        try {
            RoleManager rm = (RoleManager)c.getSystemService(Context.ROLE_SERVICE);
            return rm != null && rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) && rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING);
        } catch (Exception e) { return false; }
    }

    public static void requestCallScreening(Activity a) {
        if (Build.VERSION.SDK_INT < 29) return;
        try {
            RoleManager rm = (RoleManager)a.getSystemService(Context.ROLE_SERVICE);
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) && !rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING))
                a.startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), ROLE_REQUEST);
        } catch (Exception ignored) {}
    }

    public static String dial(Context c, String number) {
        try {
            Intent i = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number)));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            return "Opening the dialer.";
        } catch (Exception e) { return "I couldn't open the dialer."; }
    }

    @SuppressWarnings("deprecation")
    public static String answer(Context c) {
        if (c.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED)
            return "Answer-call access isn't granted yet.";
        try {
            TelecomManager tm = (TelecomManager)c.getSystemService(Context.TELECOM_SERVICE);
            tm.acceptRingingCall();
            return "Answering the call.";
        } catch (Exception e) { return "I couldn't answer that call."; }
    }

    @SuppressWarnings("deprecation")
    public static String end(Context c) {
        if (c.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED)
            return "Phone-control access isn't granted yet.";
        try {
            TelecomManager tm = (TelecomManager)c.getSystemService(Context.TELECOM_SERVICE);
            return tm.endCall() ? "Call ended." : "I couldn't end the call.";
        } catch (Exception e) { return "I couldn't end that call."; }
    }
}
