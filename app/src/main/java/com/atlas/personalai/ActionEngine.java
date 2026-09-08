package com.atlas.personalai;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.provider.ContactsContract;
import org.json.JSONObject;
import java.util.List;
import java.util.Locale;

public final class ActionEngine {
    public enum Policy { ALLOW, ASK, DENY }
    private ActionEngine() {}

    public static Policy policy(String type) {
        if (type == null) return Policy.DENY;
        switch(type) {
            case "open_app": case "navigate": case "get_location":
            case "media_play": case "media_pause": case "media_toggle":
            case "media_next": case "media_previous": return Policy.ALLOW;
            case "call_contact": case "create_calendar_event": return Policy.ASK;
            default: return Policy.DENY;
        }
    }

    public static String confirmation(Context c, JSONObject a) {
        String type=a.optString("type","");
        if("call_contact".equals(type)) {
            if(c.checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED)
                return "ERROR: Contacts access isn't granted yet. Tap Grant Phone Access.";
            ContactHit hit=findContact(c,a.optString("target",""));
            if(hit==null) return "ERROR: I couldn't find that contact.";
            a = copyWith(a,"resolvedName",hit.name,"resolvedNumber",hit.number);
            return "Call " + hit.name + " at the number ending in " + last4(hit.number) + "? Say yes to confirm.";
        }
        if("create_calendar_event".equals(type))
            return "Add " + a.optString("title","this event") + " to your calendar? Say yes to confirm.";
        return "Run this action? Say yes to confirm.";
    }

    private static JSONObject copyWith(JSONObject a, String k1, String v1, String k2, String v2) {
        try { a.put(k1,v1); a.put(k2,v2); } catch(Exception ignored) {}
        return a;
    }

    public static String execute(Context c, JSONObject a) {
        String type=a.optString("type","");
        try {
            switch(type) {
                case "open_app": return openApp(c,a.optString("app",a.optString("target","")));
                case "navigate": return navigate(c,a.optString("query",a.optString("target","")));
                case "get_location": return DeviceContext.locationSummary(c);
                case "media_play": return media(c,"play");
                case "media_pause": return media(c,"pause");
                case "media_toggle": return media(c,"toggle");
                case "media_next": return media(c,"next");
                case "media_previous": return media(c,"previous");
                case "call_contact": return callContact(c,a);
                case "create_calendar_event": return CalendarTools.createEvent(c,a);
                default: return "That action isn't supported yet.";
            }
        } catch(Exception e) { return "Action error: " + e.getMessage(); }
    }

    private static String openApp(Context c,String name) {
        name=name==null?"":name.trim();
        if(name.isEmpty()) return "Tell me which app to open.";
        if(name.equalsIgnoreCase("settings")) {
            Intent s=new Intent(android.provider.Settings.ACTION_SETTINGS); s.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); c.startActivity(s); return "Opening Settings.";
        }
        PackageManager pm=c.getPackageManager();
        Intent query=new Intent(Intent.ACTION_MAIN); query.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list=pm.queryIntentActivities(query,0);
        ResolveInfo best=null;
        String low=name.toLowerCase(Locale.US);
        for(ResolveInfo r:list) {
            String label=r.loadLabel(pm).toString();
            String l=label.toLowerCase(Locale.US);
            if(l.equals(low)) { best=r; break; }
            if(best==null && l.contains(low)) best=r;
        }
        if(best==null) return "I couldn't find an installed app called " + name + ".";
        String pkg=best.activityInfo.packageName;
        Intent launch=pm.getLaunchIntentForPackage(pkg);
        if(launch==null) return "I found the app but couldn't launch it.";
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); c.startActivity(launch);
        return "Opening " + best.loadLabel(pm) + ".";
    }

    private static String navigate(Context c,String q) {
        if(q==null || q.trim().isEmpty()) return "Tell me where you want to go.";
        Uri u=Uri.parse("geo:0,0?q="+Uri.encode(q));
        Intent i=new Intent(Intent.ACTION_VIEW,u); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { c.startActivity(i); return "Opening navigation for " + q + "."; }
        catch(Exception e) { return "I couldn't open a maps app."; }
    }

    private static String callContact(Context c,JSONObject a) {
        if(c.checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED)
            return "Contacts access isn't granted yet. Tap Grant Phone Access.";
        if(c.checkSelfPermission(Manifest.permission.CALL_PHONE)!=PackageManager.PERMISSION_GRANTED)
            return "Phone call access isn't granted yet. Tap Grant Phone Access.";
        String num=a.optString("resolvedNumber","");
        String name=a.optString("resolvedName",a.optString("target","contact"));
        if(num.isEmpty()) {
            ContactHit hit=findContact(c,a.optString("target",""));
            if(hit==null) return "I couldn't find that contact.";
            num=hit.number; name=hit.name;
        }
        Intent i=new Intent(Intent.ACTION_CALL,Uri.parse("tel:"+Uri.encode(num))); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); c.startActivity(i);
        return "Calling " + name + ".";
    }

    private static String media(Context c,String cmd) {
        try {
            MediaSessionManager msm=(MediaSessionManager)c.getSystemService(Context.MEDIA_SESSION_SERVICE);
            ComponentName listener=new ComponentName(c,AtlasNotificationListener.class);
            List<MediaController> controllers=msm.getActiveSessions(listener);
            if(controllers==null || controllers.isEmpty()) return "I don't see an active media session. Make sure Atlas notification access is enabled.";
            MediaController mc=controllers.get(0);
            MediaController.TransportControls t=mc.getTransportControls();
            if("play".equals(cmd)) t.play();
            else if("pause".equals(cmd)) t.pause();
            else if("next".equals(cmd)) t.skipToNext();
            else if("previous".equals(cmd)) t.skipToPrevious();
            else {
                PlaybackState ps=mc.getPlaybackState();
                if(ps!=null && ps.getState()==PlaybackState.STATE_PLAYING) t.pause(); else t.play();
            }
            return "Media command sent.";
        } catch(SecurityException e) { return "Atlas needs Notification Access for media control."; }
        catch(Exception e) { return "I couldn't control the current media session."; }
    }

    private static ContactHit findContact(Context c,String target) {
        Cursor cur=null;
        try {
            cur=c.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER},
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
                new String[]{"%"+target+"%"}, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME+" ASC");
            ContactHit first=null;
            while(cur!=null && cur.moveToNext()) {
                ContactHit h=new ContactHit(cur.getString(0),cur.getString(1));
                if(first==null) first=h;
                if(h.name.equalsIgnoreCase(target)) return h;
            }
            return first;
        } catch(Exception e) { return null; }
        finally { if(cur!=null) cur.close(); }
    }

    private static String last4(String s) {
        String d=s==null?"":s.replaceAll("\\D","");
        return d.length()<=4?d:d.substring(d.length()-4);
    }

    private static final class ContactHit {
        final String name; final String number;
        ContactHit(String n,String p){name=n;number=p;}
    }
}
