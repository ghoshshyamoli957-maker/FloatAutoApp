package com.floatauto.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AutoService extends AccessibilityService {

    public static AutoService inst;

    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    static final int NF = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
    static final int NL = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
    static final int LIS = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
    static final int NT = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;

    interface PickCb { void got(int x, int y); }
    interface Adj { void by(int d); }

    static class Job {
        Store.Btn b;
        Store.Prompt p;
        List<Store.Step> steps;
        boolean nagged;
    }

    WindowManager wm;
    final Handler h = new Handler(Looper.getMainLooper());
    final Map<Integer, TextView> floats = new HashMap<>();
    final Map<View, WindowManager.LayoutParams> lps = new HashMap<>();
    final LinkedList<Job> queue = new LinkedList<>();
    Job running;

    LinearLayout panel;
    ScrollView panelScroll;
    Store.Btn panelBtn;
    int tab = 0;
    int savedY = 0;
    Store.Prompt armed;

    // ---------- জীবনচক্র ----------

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        inst = this;
        Store.init(this);
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        try {
            AccessibilityServiceInfo info = getServiceInfo();
            if (info != null) {
                info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
                setServiceInfo(info);
            }
        } catch (Exception e) { }
        rebuild();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override public void onInterrupt() { }

    @Override public boolean onUnbind(Intent intent) {
        cleanup();
        return super.onUnbind(intent);
    }

    @Override public void onDestroy() {
        cleanup();
        super.onDestroy();
    }

    void cleanup() {
        inst = null;
        queue.clear();
        running = null;
        try {
            for (TextView t : new ArrayList<>(floats.values())) rm(t);
            floats.clear();
            hidePanel();
        } catch (Exception e) { }
    }

    public void rebuild() {
        h.post(() -> {
            if (wm == null) return;
            for (TextView t : new ArrayList<>(floats.values())) rm(t);
            floats.clear();
            hidePanel();
            if (Store.showFloat) {
                for (Store.Btn b : new ArrayList<>(Store.btns)) addFloat(b);
            }
        });
    }

    // ---------- ছোট সাহায্যকারী ----------

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    GradientDrawable shape(int color, int r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(r));
        return g;
    }

    WindowManager.LayoutParams mkLp(int w, int hh, int flags) {
        return new WindowManager.LayoutParams(w, hh,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT);
    }

    void rm(View v) {
        try { wm.removeView(v); } catch (Exception e) { }
        lps.remove(v);
    }

    LinearLayout.LayoutParams ll(int w, int hh, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, hh);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    TextView tv(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }

    TextView chip(String s, int color, View.OnClickListener l) {
        TextView t = tv(s, 13, Color.WHITE, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(shape(color, 10));
        t.setPadding(dp(12), dp(8), dp(12), dp(8));
        t.setOnClickListener(l);
        return t;
    }

    LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    TextView sec(String s) {
        TextView t = tv(s, 13, 0xFF8FA3FF, true);
        t.setPadding(0, dp(10), 0, dp(4));
        return t;
    }

    void setTouchable(boolean t) {
        for (Map.Entry<View, WindowManager.LayoutParams> e : new ArrayList<>(lps.entrySet())) {
            WindowManager.LayoutParams lp = e.getValue();
            if (t) lp.flags &= ~NT; else lp.flags |= NT;
            try { wm.updateViewLayout(e.getKey(), lp); } catch (Exception x) { }
        }
    }

    void setLabel(Store.Btn b, String s) {
        TextView t = floats.get(b.id);
        if (t != null) t.setText(s);
    }

    // ---------- ফ্লোটিং বোতাম ----------

    void addFloat(final Store.Btn b) {
        final TextView v = new TextView(this);
        v.setText(b.name);
        v.setTextColor(Color.WHITE);
        v.setTextSize(15);
        v.setTypeface(null, android.graphics.Typeface.BOLD);
        v.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(b.color);
        g.setStroke(dp(2), 0xCCFFFFFF);
        v.setBackground(g);

        final WindowManager.LayoutParams lp = mkLp(dp(52), dp(52), NF | NL);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = b.posX;
        lp.y = b.posY;

        v.setOnTouchListener(new View.OnTouchListener() {
            float dx, dy;
            int sx, sy;
            boolean moved;

            @Override public boolean onTouch(View view, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        dx = e.getRawX();
                        dy = e.getRawY();
                        sx = lp.x;
                        sy = lp.y;
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float mx = e.getRawX() - dx, my = e.getRawY() - dy;
                        if (!moved && (Math.abs(mx) > dp(6) || Math.abs(my) > dp(6))) moved = true;
                        if (moved) {
                            lp.x = sx + (int) mx;
                            lp.y = sy + (int) my;
                            try { wm.updateViewLayout(view, lp); } catch (Exception x) { }
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (moved) {
                            b.posX = lp.x;
                            b.posY = lp.y;
                            Store.saveBtns();
                        } else {
                            togglePanel(b);
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });
        try {
            wm.addView(v, lp);
            lps.put(v, lp);
            floats.put(b.id, v);
        } catch (Exception e) { }
    }

    // ---------- প্যানেল ----------

    void togglePanel(Store.Btn b) {
        if (panel != null && panelBtn == b) {
            hidePanel();
        } else {
            hidePanel();
            panelBtn = b;
            tab = 0;
            buildPanel(false);
        }
    }

    void removePanelView() {
        if (panelScroll != null) savedY = panelScroll.getScrollY();
        if (panel != null) rm(panel);
        panel = null;
        panelScroll = null;
    }

    void hidePanel() {
        removePanelView();
        panelBtn = null;
        armed = null;
        savedY = 0;
    }

    void reopenSettings() {
        if (panelBtn != null) {
            tab = 1;
            buildPanel(true);
        }
    }

    void buildPanel(boolean keep) {
        final Store.Btn b = panelBtn;
        if (b == null || wm == null) return;
        removePanelView();
        final int sy = keep ? savedY : 0;
        DisplayMetrics dm = getResources().getDisplayMetrics();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(shape(0xF2151A2E, 18));
        root.setPadding(dp(10), dp(10), dp(10), dp(10));

        LinearLayout head = row();
        head.addView(tv("● বোতাম " + b.name, 15, b.color, true), new LinearLayout.LayoutParams(0, WRAP, 1f));
        head.addView(chip("প্রম্পট", tab == 0 ? 0xFF2979FF : 0xFF3D4570, v -> { tab = 0; buildPanel(false); }),
                ll(WRAP, WRAP, 4, 0, 0, 0));
        head.addView(chip("সেটিংস", tab == 1 ? 0xFF2979FF : 0xFF3D4570, v -> { tab = 1; buildPanel(false); }),
                ll(WRAP, WRAP, 4, 0, 0, 0));
        head.addView(chip("✕", 0xFF7A2A2A, v -> hidePanel()), ll(WRAP, WRAP, 4, 0, 0, 0));
        root.addView(head);

        final ScrollView sc = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        sc.addView(body);
        if (tab == 0) fillPrompts(body, b); else fillSettings(body, b);
        root.addView(sc, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        panel = root;
        panelScroll = sc;
        WindowManager.LayoutParams lp = mkLp(Math.min(dm.widthPixels - dp(16), dp(440)),
                (int) (dm.heightPixels * 0.62f), NF);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.y = dp(8);
        try {
            wm.addView(root, lp);
            lps.put(root, lp);
        } catch (Exception e) { }
        if (sy > 0) sc.post(() -> sc.scrollTo(0, sy));
    }

    void fillPrompts(LinearLayout body, final Store.Btn b) {
        int busy = queue.size() + (running != null ? 1 : 0);
        body.addView(tv("ব্যবহৃত " + Store.usedCount() + " / " + Store.prompts.size()
                + "   •   সারিতে " + busy, 12, 0xFF9AA3C7, false));
        body.addView(tv(b.pkg.isEmpty() ? "ব্রাউজার বাঁধা নেই (সেটিংসে বাঁধুন)" : b.pkg, 11, 0xFF6B7499, false));

        LinearLayout r = row();
        r.addView(chip("▶ পরেরটি চালাও", 0xFF00897B, v -> runNext(b)));
        r.addView(chip("⏹ থামাও", 0xFFD93025, v -> { stopAll(); buildPanel(true); }), ll(WRAP, WRAP, 6, 0, 0, 0));
        body.addView(r, ll(MATCH, WRAP, 0, 6, 0, 6));

        if (Store.prompts.isEmpty()) {
            body.addView(tv("কোনো প্রম্পট নেই। অ্যাপ খুলে প্রম্পট যোগ করুন।", 13, 0xFF9AA3C7, false));
            return;
        }
        for (int i = 0; i < Store.prompts.size(); i++) {
            final Store.Prompt p = Store.prompts.get(i);
            String pv = p.text.length() > 100 ? p.text.substring(0, 100) + "…" : p.text;
            String mark = inflight(p) ? "⏳ " : (p.used ? "✔ " : "");
            TextView t = tv(mark + (i + 1) + ".  " + pv, 13, p.used ? 0xFFFF8A80 : Color.WHITE, false);
            t.setBackground(shape(p.used ? 0xFF5A1E1E : 0xFF262D4A, 10));
            t.setPadding(dp(10), dp(8), dp(10), dp(8));
            if (p.used) t.setPaintFlags(t.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            t.setOnClickListener(v -> onPromptTap(b, p));
            t.setOnLongClickListener(v -> {
                p.used = !p.used;
                Store.savePrompts();
                armed = null;
                buildPanel(true);
                return true;
            });
            body.addView(t, ll(MATCH, WRAP, 0, 0, 0, 5));
        }
    }

    void stepper(LinearLayout r, final Adj a) {
        TextView m = chip("−", 0xFF3D4570, v -> a.by(-1));
        m.setOnLongClickListener(v -> { a.by(-10); return true; });
        TextView p = chip("+", 0xFF3D4570, v -> a.by(1));
        p.setOnLongClickListener(v -> { a.by(10); return true; });
        r.addView(m, ll(WRAP, WRAP, 4, 0, 0, 0));
        r.addView(p, ll(WRAP, WRAP, 4, 0, 0, 0));
    }

    void fillSettings(LinearLayout body, final Store.Btn b) {
        body.addView(sec("ব্রাউজার"));
        body.addView(tv(b.pkg.isEmpty() ? "বাঁধা নেই" : b.pkg, 12, Color.WHITE, false));
        LinearLayout r1 = row();
        r1.addView(chip("🔗 সামনের ব্রাউজার বাঁধো", 0xFF2979FF, v -> bindFront(b)));
        r1.addView(chip("মুছুন", 0xFF3D4570, v -> {
            b.pkg = "";
            Store.saveBtns();
            buildPanel(true);
        }), ll(WRAP, WRAP, 4, 0, 0, 0));
        body.addView(r1, ll(MATCH, WRAP, 0, 4, 0, 4));

        body.addView(sec("প্রম্পট বক্স"));
        body.addView(tv(b.boxX < 0 ? "জায়গা সেট নেই" : "জায়গা: (" + b.boxX + ", " + b.boxY + ")",
                12, Color.WHITE, false));
        body.addView(chip("📍 বক্স সেট করুন", 0xFF00897B, v ->
                pick("প্রম্পট লেখার বক্সের উপর ট্যাপ করুন", (px, py) -> {
                    b.boxX = px;
                    b.boxY = py;
                    Store.saveBtns();
                    toast("✔ বক্স সেট হয়েছে");
                    reopenSettings();
                })), ll(WRAP, WRAP, 0, 4, 0, 4));
        LinearLayout r3 = row();
        r3.addView(tv("বক্সে ট্যাপের পর অপেক্ষা: " + b.boxWait + " সে", 12, Color.WHITE, false),
                new LinearLayout.LayoutParams(0, WRAP, 1f));
        stepper(r3, d -> {
            b.boxWait = clamp(b.boxWait + d, 0, 999);
            Store.saveBtns();
            buildPanel(true);
        });
        body.addView(r3, ll(MATCH, WRAP, 0, 2, 0, 4));
        body.addView(chip(b.useClip ? "পেস্ট পদ্ধতি: ক্লিপবোর্ড (বদলাতে ট্যাপ)" : "পেস্ট পদ্ধতি: সরাসরি লেখা (বদলাতে ট্যাপ)",
                0xFF3D4570, v -> {
                    b.useClip = !b.useClip;
                    Store.saveBtns();
                    buildPanel(true);
                }), ll(WRAP, WRAP, 0, 4, 0, 4));

        body.addView(sec("অটো-ক্লিক ধাপ (ক্রম অনুযায়ী)"));
        body.addView(tv("ধাপের লেখায় ট্যাপ = জায়গা বদল।  − / + = ১ সেকেন্ড, চেপে ধরলে ১০ সেকেন্ড।",
                11, 0xFF9AA3C7, false));
        if (b.steps.isEmpty()) body.addView(tv("কোনো ধাপ নেই", 12, 0xFF9AA3C7, false));
        for (int i = 0; i < b.steps.size(); i++) {
            final Store.Step s = b.steps.get(i);
            final int num = i + 1;
            LinearLayout r = row();
            TextView lab = tv("ধাপ " + num + "  (" + s.x + ", " + s.y + ")\n⏱ ক্লিকের আগে " + s.wait + " সে",
                    12, Color.WHITE, false);
            lab.setOnClickListener(v -> pick("ধাপ " + num + "-এর নতুন জায়গায় ট্যাপ করুন", (px, py) -> {
                s.x = px;
                s.y = py;
                Store.saveBtns();
                reopenSettings();
            }));
            r.addView(lab, new LinearLayout.LayoutParams(0, WRAP, 1f));
            stepper(r, d -> {
                s.wait = clamp(s.wait + d, 0, 999);
                Store.saveBtns();
                buildPanel(true);
            });
            r.addView(chip("✕", 0xFF7A2A2A, v -> {
                b.steps.remove(s);
                Store.saveBtns();
                buildPanel(true);
            }), ll(WRAP, WRAP, 4, 0, 0, 0));
            body.addView(r, ll(MATCH, WRAP, 0, 4, 0, 4));
        }
        body.addView(chip("＋ নতুন ধাপ (স্ক্রিনে ট্যাপ করে দেখান)", 0xFFAA00FF, v ->
                pick("যে বোতামে ক্লিক হবে তার উপর ট্যাপ করুন", (px, py) -> {
                    Store.Step ns = new Store.Step();
                    ns.x = px;
                    ns.y = py;
                    ns.wait = 3;
                    ns.name = "ধাপ " + (b.steps.size() + 1);
                    b.steps.add(ns);
                    Store.saveBtns();
                    toast("✔ ধাপ " + b.steps.size() + " যোগ হয়েছে");
                    reopenSettings();
                })), ll(WRAP, WRAP, 0, 6, 0, 4));
        LinearLayout r4 = row();
        r4.addView(chip("📍 পয়েন্ট দেখাও", 0xFF3D4570, v -> showMarkers(b)));
        r4.addView(chip("▶ শুধু ধাপ টেস্ট", 0xFF00897B, v -> {
            hidePanel();
            enqueue(b, null);
        }), ll(WRAP, WRAP, 6, 0, 0, 0));
        body.addView(r4, ll(MATCH, WRAP, 0, 4, 0, 10));
    }

    void bindFront(Store.Btn b) {
        AccessibilityNodeInfo r = getRootInActiveWindow();
        CharSequence pk = r == null ? null : r.getPackageName();
        if (pk == null) { toast("ব্রাউজার পাওয়া যায়নি, আবার চেষ্টা করুন"); return; }
        String s = pk.toString();
        if (s.equals(getPackageName()) || s.equals("com.android.systemui")) {
            toast("আগে ব্রাউজারটি সামনে আনুন, তারপর বাঁধুন");
            return;
        }
        b.pkg = s;
        Store.saveBtns();
        toast("✔ বাঁধা হয়েছে: " + s);
        buildPanel(true);
    }

    // ---------- পয়েন্ট বেছে নেওয়া ----------

    void pick(String hint, final PickCb cb) {
        removePanelView();
        final FrameLayout f = new FrameLayout(this);
        f.setBackgroundColor(0x33000000);

        TextView t = tv(hint, 15, Color.WHITE, true);
        t.setBackground(shape(0xDD151A2E, 14));
        t.setPadding(dp(14), dp(10), dp(14), dp(10));
        t.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        tp.topMargin = dp(70);
        f.addView(t, tp);

        TextView c = chip("বাতিল", 0xFFD93025, v -> {
            rm(f);
            reopenSettings();
        });
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        cp.bottomMargin = dp(60);
        f.addView(c, cp);

        f.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                int px = (int) e.getRawX(), py = (int) e.getRawY();
                rm(f);
                cb.got(px, py);
            }
            return true;
        });
        WindowManager.LayoutParams lp = mkLp(MATCH, MATCH, NF | LIS | NL);
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        try { wm.addView(f, lp); } catch (Exception e) { reopenSettings(); }
    }

    class Dots extends View {
        final List<int[]> pts;
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);

        Dots(List<int[]> p) {
            super(AutoService.this);
            pts = p;
            fill.setColor(0xCCFF1744);
            txt.setColor(Color.WHITE);
            txt.setTextSize(dp(15));
            txt.setTextAlign(Paint.Align.CENTER);
            txt.setFakeBoldText(true);
        }

        @Override protected void onDraw(Canvas c) {
            int[] loc = new int[2];
            getLocationOnScreen(loc);
            for (int[] p : pts) {
                float x = p[0] - loc[0], y = p[1] - loc[1];
                c.drawCircle(x, y, dp(18), fill);
                c.drawText(p[2] == 0 ? "P" : String.valueOf(p[2]), x, y + dp(5), txt);
            }
        }
    }

    void showMarkers(Store.Btn b) {
        List<int[]> pts = new ArrayList<>();
        if (b.boxX >= 0) pts.add(new int[]{b.boxX, b.boxY, 0});
        for (int i = 0; i < b.steps.size(); i++) {
            Store.Step s = b.steps.get(i);
            if (s.x >= 0) pts.add(new int[]{s.x, s.y, i + 1});
        }
        if (pts.isEmpty()) { toast("কোনো পয়েন্ট সেট করা নেই"); return; }
        removePanelView();
        final Dots d = new Dots(pts);
        try { wm.addView(d, mkLp(MATCH, MATCH, NF | NT | LIS | NL)); } catch (Exception e) { reopenSettings(); return; }
        toast("P = প্রম্পট বক্স, সংখ্যা = ধাপ");
        h.postDelayed(() -> {
            rm(d);
            reopenSettings();
        }, 3000);
    }

    // ---------- সারি ও চালানো ----------

    boolean inflight(Store.Prompt p) {
        if (running != null && running.p == p) return true;
        for (Job j : queue) if (j.p == p) return true;
        return false;
    }

    Set<Integer> reservedIdx() {
        Set<Integer> s = new HashSet<>();
        if (running != null && running.p != null) {
            int i = Store.prompts.indexOf(running.p);
            if (i >= 0) s.add(i);
        }
        for (Job j : queue) {
            if (j.p != null) {
                int i = Store.prompts.indexOf(j.p);
                if (i >= 0) s.add(i);
            }
        }
        return s;
    }

    void onPromptTap(Store.Btn b, Store.Prompt p) {
        if (inflight(p)) { toast("এই প্রম্পট এখন চলছে বা সারিতে আছে"); return; }
        if (p.used && armed != p) {
            armed = p;
            toast("এটি আগেই ব্যবহার হয়েছে। আবার চালাতে আরেকবার ট্যাপ করুন");
            return;
        }
        armed = null;
        hidePanel();
        enqueue(b, p);
    }

    void runNext(Store.Btn b) {
        int i = Store.nextFree(reservedIdx());
        if (i < 0) { toast("সব প্রম্পট ব্যবহৃত বা চলছে"); return; }
        hidePanel();
        enqueue(b, Store.prompts.get(i));
    }

    void enqueue(Store.Btn b, Store.Prompt p) {
        Job j = new Job();
        j.b = b;
        j.p = p;
        j.steps = new ArrayList<>(b.steps);
        queue.add(j);
        if (running != null) toast("সারিতে যোগ হয়েছে");
        pump();
    }

    void pump() {
        if (running != null || queue.isEmpty()) return;
        startJob(queue.poll());
    }

    void stopAll() {
        queue.clear();
        if (running != null) {
            Job j = running;
            running = null;
            setLabel(j.b, j.b.name);
            setTouchable(true);
        }
        toast("থামানো হয়েছে");
    }

    void startJob(final Job j) {
        running = j;
        setLabel(j.b, "⏳");
        toast("▶ বোতাম " + j.b.name + " শুরু");
        whenFront(j, () -> {
            if (j.p == null) { runStep(j, 0); return; }
            if (j.b.boxX < 0) {
                toast("আগে সেটিংসে প্রম্পট বক্সের জায়গা সেট করুন");
                finish(j, false);
                return;
            }
            tap(j.b.boxX, j.b.boxY, () -> h.postDelayed(() -> {
                if (j != running) return;
                whenFront(j, () -> {
                    if (!insertText(j)) { finish(j, false); return; }
                    runStep(j, 0);
                });
            }, j.b.boxWait * 1000L));
        });
    }

    void runStep(final Job j, final int i) {
        if (j != running) return;
        if (i >= j.steps.size()) { finish(j, true); return; }
        final Store.Step s = j.steps.get(i);
        h.postDelayed(() -> {
            if (j != running) return;
            whenFront(j, () -> {
                if (s.x < 0) { runStep(j, i + 1); return; }
                tap(s.x, s.y, () -> runStep(j, i + 1));
            });
        }, Math.max(300L, s.wait * 1000L));
    }

    void finish(Job j, boolean ok) {
        if (j != running) return;
        running = null;
        setLabel(j.b, j.b.name);
        if (ok) {
            if (j.p != null) {
                j.p.used = true;
                Store.savePrompts();
                toast("✔ প্রম্পট " + (Store.prompts.indexOf(j.p) + 1) + " সম্পন্ন (বোতাম " + j.b.name + ")");
            } else {
                toast("✔ ধাপ শেষ (বোতাম " + j.b.name + ")");
            }
        }
        if (panel != null) buildPanel(true);
        h.post(this::pump);
    }

    void whenFront(final Job j, final Runnable r) {
        if (j != running) return;
        if (j.b.pkg.isEmpty() || rootOf(j.b.pkg) != null) {
            j.nagged = false;
            r.run();
            return;
        }
        if (!j.nagged) {
            j.nagged = true;
            toast("⏸ বোতাম " + j.b.name + "-এর ব্রাউজার সামনে আনুন");
        }
        h.postDelayed(() -> whenFront(j, r), 700);
    }

    // ---------- ট্যাপ ও লেখা বসানো ----------

    void tap(int x, int y, final Runnable done) {
        setTouchable(false);
        Path p = new Path();
        p.moveTo(x, y);
        boolean ok = false;
        try {
            GestureDescription g = new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(p, 0, 60)).build();
            ok = dispatchGesture(g, new GestureResultCallback() {
                @Override public void onCompleted(GestureDescription d) {
                    setTouchable(true);
                    done.run();
                }

                @Override public void onCancelled(GestureDescription d) {
                    setTouchable(true);
                    done.run();
                }
            }, null);
        } catch (Exception e) { }
        if (!ok) {
            setTouchable(true);
            done.run();
        }
    }

    AccessibilityNodeInfo rootOf(String pkg) {
        try {
            List<AccessibilityWindowInfo> ws = getWindows();
            if (ws != null) {
                for (AccessibilityWindowInfo w : ws) {
                    if (w.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
                    AccessibilityNodeInfo r = w.getRoot();
                    if (r != null && r.getPackageName() != null && pkg.equals(r.getPackageName().toString())) return r;
                }
            }
        } catch (Exception e) { }
        AccessibilityNodeInfo a = getRootInActiveWindow();
        if (a != null && a.getPackageName() != null && pkg.equals(a.getPackageName().toString())) return a;
        return null;
    }

    AccessibilityNodeInfo rootFor(Store.Btn b) {
        return b.pkg.isEmpty() ? getRootInActiveWindow() : rootOf(b.pkg);
    }

    AccessibilityNodeInfo findFocusedEditable(AccessibilityNodeInfo n, int d) {
        if (n == null || d > 40) return null;
        if (n.isEditable() && n.isFocused()) return n;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo r = findFocusedEditable(n.getChild(i), d + 1);
            if (r != null) return r;
        }
        return null;
    }

    boolean insertText(Job j) {
        AccessibilityNodeInfo root = rootFor(j.b);
        if (root == null) { toast("ব্রাউজারের স্ক্রিন পাওয়া যায়নি"); return false; }
        AccessibilityNodeInfo n = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (n == null || !n.isEditable()) n = findFocusedEditable(root, 0);
        if (n == null) {
            toast("লেখার বক্স ফোকাস হয়নি। বক্সের জায়গা আবার সেট করুন");
            return false;
        }
        String text = j.p.text;
        boolean ok = false;
        if (!j.b.useClip) {
            Bundle a = new Bundle();
            a.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
            ok = n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, a);
        }
        if (!ok) {
            try {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("prompt", text));
                CharSequence cur = n.getText();
                if (cur != null && cur.length() > 0) {
                    Bundle s = new Bundle();
                    s.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0);
                    s.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cur.length());
                    n.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, s);
                }
                ok = n.performAction(AccessibilityNodeInfo.ACTION_PASTE);
            } catch (Exception e) { }
        }
        if (!ok) toast("লেখা বসানো যায়নি। সেটিংসে পেস্ট পদ্ধতি বদলে দেখুন");
        return ok;
    }
  }
