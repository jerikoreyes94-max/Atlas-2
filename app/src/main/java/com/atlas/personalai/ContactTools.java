package com.atlas.personalai;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.ContactsContract;

public final class ContactTools {
    private ContactTools() {}

    public static ContactHit find(Context c, String target) {
        if (c.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null;
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER},
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
                new String[]{"%" + target + "%"},
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            );
            ContactHit first = null;
            while (cur != null && cur.moveToNext()) {
                ContactHit h = new ContactHit(cur.getString(0), cur.getString(1));
                if (first == null) first = h;
                if (h.name.equalsIgnoreCase(target)) return h;
            }
            return first;
        } catch (Exception e) { return null; }
        finally { if (cur != null) cur.close(); }
    }

    public static String last4(String s) {
        String d = s == null ? "" : s.replaceAll("\\D", "");
        return d.length() <= 4 ? d : d.substring(d.length() - 4);
    }

    public static final class ContactHit {
        public final String name;
        public final String number;
        public ContactHit(String n, String p) { name = n; number = p; }
    }
}
