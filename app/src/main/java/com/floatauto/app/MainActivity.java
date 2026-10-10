package com.floatauto.app;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.io.InputStream;
import java.util.*;

public class MainActivity extends AppCompatActivity {

    static final int BG = 0xFF0F1220, CARD = 0xFF1B2036, BLUE = 0xFF2979FF, RED = 0xFF5A1E1E;
    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT, WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    LinearLayout page, btnBox, listBox;
    TextView tvStat, tvCount;
    EditText paste;
    ActivityResultLauncher<String[]> picker;

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    GradientDrawable shape(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    TextView text(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }

    Button button(String s, int color, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setBackground(shape(color, 12));
        b.setPadding(dp(14), dp(8), dp(14), dp(8));
        b.setOnClickListener(l);
        return b;
    }

    LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(shape(CARD, 16));
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        page.addView(c, lp(MATCH, WRAP, 0, 0, 0, 12));
        return c;
    }

    void rebuildSvc() {
        if (AutoService.inst != null) AutoService.inst.rebuild();
    }

    boolean svcOn() {
        String s = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.contains(getPackageName() + "/");
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Store.init(this);
        picker = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) importFile(uri);
        });
        buildUi();
        refresh();
    }

    @Override protected void onResume() {
        super.onResume();
        if (page != null) refresh();
    }

    void buildUi() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(28), dp(16), dp(40));
        sv.addView(page);
        setContentView(sv);

        page.addView(text("⚡ Float Auto", 26, Color.WHITE, true));
        page.addView(text("প্রম্পট বসান, ক্লিক চালান, সব ব্রাউজারে", 13, 0xFF9AA3C7, false), lp(WRAP, WRAP, 0, 2, 0, 16));

        LinearLayout c1 = card();
        tvStat = text("", 14, Color.WHITE, false);
        c1.addView(tvStat);
        c1.addView(button("⚙ অনুমতি চালু করুন", BLUE, v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))), lp(WRAP, WRAP, 0, 8, 0, 0));
        Switch sw = new Switch(this);
        sw.setText("ফ্লোটিং বোতাম দেখাও");
        sw.setTextColor(Color.WHITE);
        sw.setChecked(Store.showFloat);
        sw.setOnCheckedChangeListener((v, on) -> {
            Store.showFloat = on;
            Store.saveShow();
            rebuildSvc();
        });
        c1.addView(sw, lp(MATCH, WRAP, 0, 10, 0, 0));

        LinearLayout c2 = card();
        c2.addView(text("প্রম্পট যোগ করুন", 16, Color.WHITE, true));
        c2.addView(text("পেস্ট করুন। 'ভিডিও ১:' বা 'Prompt 2' দেখলেই নতুন প্রম্পট শুরু ধরা হবে।", 12, 0xFF9AA3C7, false),
                lp(WRAP, WRAP, 0, 2, 0, 8));
        paste = new EditText(this);
        paste.setMinLines(5);
        paste.setGravity(Gravity.TOP);
        paste.setTextColor(Color.WHITE);
        paste.setHintTextColor(0xFF6B7499);
        paste.setHint("এখানে প্রম্পট পেস্ট করুন");
        paste.setTextSize(14);
        paste.setBackground(shape(0xFF262D4A, 12));
        paste.setPadding(dp(12), dp(10), dp(12), dp(10));
        c2.addView(paste, lp(MATCH, WRAP, 0, 0, 0, 8));
        LinearLayout r2 = new LinearLayout(this);
        r2.addView(button("＋ যোগ করুন", BLUE, v -> addPasted()));
        r2.addView(button("📂 এক্সেল", 0xFF00897B, v -> picker.launch(new String[]{"*/*"})), lp(WRAP, WRAP, 8, 0, 0, 0));
        c2.addView(r2);

        LinearLayout c3 = card();
        c3.addView(text("ফ্লোটিং বোতাম", 16, Color.WHITE, true));
        btnBox = new LinearLayout(this);
        btnBox.setOrientation(LinearLayout.VERTICAL);
        c3.addView(btnBox, lp(MATCH, WRAP, 0, 8, 0, 4));
        c3.addView(button("＋ নতুন বোতাম", 0xFFAA00FF, v -> {
            Store.addBtn();
            refresh();
            rebuildSvc();
        }));

        LinearLayout c4 = card();
        c4.addView(text("প্রম্পট তালিকা", 16, Color.WHITE, true));
        tvCount = text("", 13, 0xFF9AA3C7, false);
        c4.addView(tvCount, lp(WRAP, WRAP, 0, 2, 0, 8));
        LinearLayout r4 = new LinearLayout(this);
        r4.addView(button("↺ সব রিসেট", 0xFFE37400, v -> {
            Store.resetUsed();
            refresh();
        }));
        r4.addView(button("🗑 সব মুছুন", 0xFFD93025, v ->
                new AlertDialog.Builder(this).setMessage("সব প্রম্পট মুছে যাবে। নিশ্চিত?")
                        .setPositiveButton("মুছুন", (d, w) -> {
                            Store.clearPrompts();
                            refresh();
                        }).setNegativeButton("বাতিল", null).show()), lp(WRAP, WRAP, 8, 0, 0, 0));
        c4.addView(r4, lp(WRAP, WRAP, 0, 0, 0, 8));
        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        c4.addView(listBox);
    }

    void refresh() {
        tvStat.setText(svcOn() ? "✅ সার্ভিস চালু আছে"
                : "⚠ সার্ভিস বন্ধ। নিচের বোতামে গিয়ে 'Float Auto' খুঁজে চালু করুন");

        btnBox.removeAllViews();
        for (final Store.Btn b : new ArrayList<>(Store.btns)) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(4), 0, dp(4));
            View dot = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(b.color);
            dot.setBackground(g);
            row.addView(dot, lp(dp(14), dp(14), 0, 0, 10, 0));
            String info = "বোতাম " + b.name + "  •  ধাপ " + b.steps.size()
                    + (b.pkg.isEmpty() ? "  •  ব্রাউজার বাঁধা নেই" : "  •  " + b.pkg);
            TextView t = text(info, 13, Color.WHITE, false);
            row.addView(t, new LinearLayout.LayoutParams(0, WRAP, 1f));
            row.addView(button("নাম", 0xFF3D4570, v -> rename(b)), lp(WRAP, WRAP, 4, 0, 0, 0));
            row.addView(button("✕", 0xFF7A2A2A, v -> {
                Store.removeBtn(b);
                refresh();
                rebuildSvc();
            }), lp(WRAP, WRAP, 4, 0, 0, 0));
            btnBox.addView(row);
        }

        listBox.removeAllViews();
        tvCount.setText("ব্যবহৃত " + Store.usedCount() + " / মোট " + Store.prompts.size());
        for (int i = 0; i < Store.prompts.size(); i++) {
            final int idx = i;
            Store.Prompt p = Store.prompts.get(i);
            String pv = p.text.length() > 120 ? p.text.substring(0, 120) + "…" : p.text;
            TextView t = new TextView(this);
            t.setText((p.used ? "✔ " : "") + (i + 1) + ".  " + pv);
            t.setTextSize(14);
            t.setTextColor(p.used ? 0xFFFF8A80 : Color.WHITE);
            t.setPadding(dp(12), dp(10), dp(12), dp(10));
            t.setBackground(shape(p.used ? RED : 0xFF262D4A, 12));
            if (p.used) t.setPaintFlags(t.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            t.setOnClickListener(v -> promptMenu(idx));
            listBox.addView(t, lp(MATCH, WRAP, 0, 0, 0, 6));
        }
    }

    void promptMenu(final int idx) {
        final Store.Prompt p = Store.prompts.get(idx);
        new AlertDialog.Builder(this)
                .setTitle("প্রম্পট " + (idx + 1))
                .setMessage(p.text)
                .setPositiveButton("মুছুন", (d, w) -> {
                    Store.prompts.remove(idx);
                    Store.savePrompts();
                    refresh();
                })
                .setNeutralButton(p.used ? "আবার ব্যবহারযোগ্য করুন" : "ব্যবহৃত দাগ দিন", (d, w) -> {
                    p.used = !p.used;
                    Store.savePrompts();
                    refresh();
                })
                .setNegativeButton("বন্ধ", null).show();
    }

    void rename(final Store.Btn b) {
        final EditText et = new EditText(this);
        et.setText(b.name);
        new AlertDialog.Builder(this).setTitle("বোতামের নাম").setView(et)
                .setPositiveButton("ঠিক আছে", (d, w) -> {
                    String s = et.getText().toString().trim();
                    if (!s.isEmpty()) b.name = s;
                    Store.saveBtns();
                    refresh();
                    rebuildSvc();
                }).setNegativeButton("বাতিল", null).show();
    }

    void addPasted() {
        List<String> l = Store.parse(paste.getText().toString());
        if (l.isEmpty()) { toast("আগে প্রম্পট পেস্ট করুন"); return; }
        int n = Store.addPrompts(l);
        paste.setText("");
        refresh();
        toast(n + "টি প্রম্পট যোগ হয়েছে (সিরিয়াল " + (Store.prompts.size() - n + 1) + " থেকে)");
    }

    String colName(int i) {
        return i < 26 ? String.valueOf((char) ('A' + i)) : "কলাম" + (i + 1);
    }

    void importFile(Uri uri) {
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            List<String[]> rows = XlsxReader.read(in);
            in.close();
            if (rows.isEmpty()) { toast("ফাইলে কোনো ডাটা নেই"); return; }
            askColumn(rows);
        } catch (Exception e) {
            toast("ফাইল পড়া যায়নি: " + e.getMessage());
        }
    }

    void askColumn(final List<String[]> rows) {
        int n = 0;
        for (String[] r : rows) n = Math.max(n, r.length);
        final int cols = n;
        String[] items = new String[cols + 1];
        for (int i = 0; i < cols; i++) {
            String s = i < rows.get(0).length && rows.get(0)[i] != null ? rows.get(0)[i] : "";
            if (s.length() > 25) s = s.substring(0, 25) + "…";
            items[i] = colName(i) + "  —  " + s;
        }
        items[cols] = "সব কলাম জুড়ে এক প্রম্পট";
        new AlertDialog.Builder(this).setTitle("প্রম্পট কোন কলামে?")
                .setItems(items, (d, w) -> askHeader(rows, w, cols)).show();
    }

    void askHeader(final List<String[]> rows, final int col, final int cols) {
        new AlertDialog.Builder(this).setTitle("প্রথম রো কি শিরোনাম?")
                .setMessage("প্রথম রো-তে কলামের নাম লেখা থাকলে বাদ দিন।")
                .setPositiveButton("হ্যাঁ, বাদ দিন", (d, w) -> doImport(rows, col, 1, cols))
                .setNegativeButton("না, এটাও প্রম্পট", (d, w) -> doImport(rows, col, 0, cols)).show();
    }

    void doImport(List<String[]> rows, int col, int skip, int cols) {
        List<String> list = new ArrayList<>();
        for (int i = skip; i < rows.size(); i++) {
            String[] r = rows.get(i);
            String s = "";
            if (col == cols) {
                StringBuilder sb = new StringBuilder();
                for (String c : r) {
                    if (c != null && c.trim().length() > 0) {
                        if (sb.length() > 0) sb.append(' ');
                        sb.append(c.trim());
                    }
                }
                s = sb.toString();
            } else if (col < r.length && r[col] != null) {
                s = r[col];
            }
            s = Store.stripMark(s);
            if (!s.isEmpty()) list.add(s);
        }
        int n = Store.addPrompts(list);
        refresh();
        toast(n + "টি প্রম্পট যোগ হয়েছে");
    }
  }
