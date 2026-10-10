package com.floatauto.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;
import java.util.regex.*;
import org.json.*;

public class Store {

    public static class Prompt {
        public String text = "";
        public boolean used = false;
    }

    public static class Step {
        public String name = "";
        public int x = -1, y = -1, wait = 3;
    }

    public static class Btn {
        public int id;
        public String name = "";
        public int color;
        public String pkg = "";
        public int boxX = -1, boxY = -1, boxWait = 1;
        public boolean useClip = false;
        public int posX = 24, posY = 300;
        public List<Step> steps = new ArrayList<>();
    }

    public static final int[] COLORS = {0xFF2979FF, 0xFF00C853, 0xFFFF6D00, 0xFFAA00FF, 0xFFE91E63, 0xFF00B8D4};
    public static final List<Prompt> prompts = new ArrayList<>();
    public static final List<Btn> btns = new ArrayList<>();
    public static boolean showFloat = true;
    private static SharedPreferences sp;

    private static final Pattern MARK = Pattern.compile(
            "^[\\s*#>_]*(?:ভিডিও|প্রম্পট|video|prompt)\\s*(?:[0-9০-৯]+\\s*[:：.\\-–—)]*|[:：.\\-–—)]+)[\\s*]*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    public static synchronized void init(Context c) {
        if (sp != null) return;
        sp = c.getApplicationContext().getSharedPreferences("fa", Context.MODE_PRIVATE);
        showFloat = sp.getBoolean("show", true);
        try {
            JSONArray a = new JSONArray(sp.getString("prompts", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Prompt p = new Prompt();
                p.text = o.optString("t");
                p.used = o.optBoolean("u");
                prompts.add(p);
            }
        } catch (Exception e) { }
        try {
            JSONArray a = new JSONArray(sp.getString("btns", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Btn b = new Btn();
                b.id = o.optInt("id");
                b.name = o.optString("n");
                b.color = o.optInt("c", COLORS[0]);
                b.pkg = o.optString("p");
                b.boxX = o.optInt("bx", -1);
                b.boxY = o.optInt("by", -1);
                b.boxWait = o.optInt("bw", 1);
                b.useClip = o.optBoolean("clip");
                b.posX = o.optInt("px", 24);
                b.posY = o.optInt("py", 300);
                JSONArray s = o.optJSONArray("s");
                if (s != null) {
                    for (int j = 0; j < s.length(); j++) {
                        JSONObject so = s.getJSONObject(j);
                        Step st = new Step();
                        st.name = so.optString("n");
                        st.x = so.optInt("x", -1);
                        st.y = so.optInt("y", -1);
                        st.wait = so.optInt("w", 3);
                        b.steps.add(st);
                    }
                }
                btns.add(b);
            }
        } catch (Exception e) { }
        if (btns.isEmpty()) addBtn();
    }

    public static void savePrompts() {
        try {
            JSONArray a = new JSONArray();
            for (Prompt p : prompts) {
                JSONObject o = new JSONObject();
                o.put("t", p.text);
                o.put("u", p.used);
                a.put(o);
            }
            sp.edit().putString("prompts", a.toString()).apply();
        } catch (Exception e) { }
    }

    public static void saveBtns() {
        try {
            JSONArray a = new JSONArray();
            for (Btn b : btns) {
                JSONObject o = new JSONObject();
                o.put("id", b.id);
                o.put("n", b.name);
                o.put("c", b.color);
                o.put("p", b.pkg);
                o.put("bx", b.boxX);
                o.put("by", b.boxY);
                o.put("bw", b.boxWait);
                o.put("clip", b.useClip);
                o.put("px", b.posX);
                o.put("py", b.posY);
                JSONArray s = new JSONArray();
                for (Step st : b.steps) {
                    JSONObject so = new JSONObject();
                    so.put("n", st.name);
                    so.put("x", st.x);
                    so.put("y", st.y);
                    so.put("w", st.wait);
                    s.put(so);
                }
                o.put("s", s);
                a.put(o);
            }
            sp.edit().putString("btns", a.toString()).apply();
        } catch (Exception e) { }
    }

    public static void saveShow() {
        sp.edit().putBoolean("show", showFloat).apply();
    }

    public static Btn addBtn() {
        int max = 0;
        for (Btn b : btns) if (b.id > max) max = b.id;
        Btn b = new Btn();
        b.id = max + 1;
        b.name = String.valueOf(b.id);
        b.color = COLORS[(b.id - 1) % COLORS.length];
        b.posX = 24;
        b.posY = 300 + ((b.id - 1) % 6) * 150;
        btns.add(b);
        saveBtns();
        return b;
    }

    public static void removeBtn(Btn b) {
        btns.remove(b);
        if (btns.isEmpty()) addBtn();
        else saveBtns();
    }

    public static Btn getBtn(int id) {
        for (Btn b : btns) if (b.id == id) return b;
        return null;
    }

    public static String stripMark(String s) {
        if (s == null) return "";
        Matcher m = MARK.matcher(s);
        return m.lookingAt() ? s.substring(m.end()).trim() : s.trim();
    }

    public static List<String> parse(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        String[] lines = raw.replace("\r", "").split("\n");
        boolean hasMark = false;
        for (String l : lines) {
            if (MARK.matcher(l).lookingAt()) { hasMark = true; break; }
        }
        StringBuilder cur = null;
        for (String l : lines) {
            String t = l.trim();
            Matcher m = MARK.matcher(l);
            if (hasMark) {
                if (m.lookingAt()) {
                    if (cur != null && cur.toString().trim().length() > 0) out.add(cur.toString().trim());
                    cur = new StringBuilder(l.substring(m.end()).trim());
                } else if (t.length() > 0) {
                    if (cur == null) cur = new StringBuilder();
                    if (cur.length() > 0) cur.append(' ');
                    cur.append(t);
                }
            } else {
                if (t.length() == 0) {
                    if (cur != null && cur.length() > 0) out.add(cur.toString());
                    cur = null;
                } else {
                    if (cur == null) cur = new StringBuilder();
                    else cur.append(' ');
                    cur.append(t);
                }
            }
        }
        if (cur != null && cur.toString().trim().length() > 0) out.add(cur.toString().trim());
        return out;
    }

    public static int addPrompts(List<String> list) {
        int n = 0;
        for (String s : list) {
            s = s.trim();
            if (s.isEmpty()) continue;
            Prompt p = new Prompt();
            p.text = s;
            prompts.add(p);
            n++;
        }
        savePrompts();
        return n;
    }

    public static int usedCount() {
        int n = 0;
        for (Prompt p : prompts) if (p.used) n++;
        return n;
    }

    public static void resetUsed() {
        for (Prompt p : prompts) p.used = false;
        savePrompts();
    }

    public static void clearPrompts() {
        prompts.clear();
        savePrompts();
    }

    public static int nextFree(Set<Integer> reserved) {
        for (int i = 0; i < prompts.size(); i++) {
            if (!prompts.get(i).used && !reserved.contains(i)) return i;
        }
        return -1;
    }
      }
