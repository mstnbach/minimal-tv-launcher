package com.example.tvlauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Settings;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public class MainActivity extends Activity {
    private static final int COLUMNS = 6;
    private final Handler handler = new Handler();
    private final List<App> apps = new ArrayList<>();
    private SharedPreferences prefs;
    private LinearLayout content;
    private ScrollView scroll;
    private TextView clock;
    private TextView date;
    private boolean expanded;
    private String focusTag;
    private int lastHomeColumn;
    private String homeFocusTag;
    private int movingSlot = -1;
    private String[] moveDraft;
    private final Runnable updateClock = new Runnable() {
        @Override public void run() {
            if (clock != null) updateClockText();
            handler.postDelayed(this, 1000);
        }
    };

    private static class App {
        final ComponentName component;
        final String label;
        App(ComponentName component, String label) { this.component = component; this.label = label; }
        String key() { return component.flattenToString(); }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        prefs = getSharedPreferences("launcher", MODE_PRIVATE);
        expanded = state != null && state.getBoolean("expanded");
        loadApps();
        initializeSlots();
        render();
        handler.post(updateClock);
    }
    @Override protected void onResume() { super.onResume(); if (prefs != null) { loadApps(); render(); } }
    @Override protected void onDestroy() { handler.removeCallbacks(updateClock); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle state) { state.putBoolean("expanded", expanded); super.onSaveInstanceState(state); }

    private int dp(float n) { return (int) (n * getResources().getDisplayMetrics().density + .5f); }
    private GradientDrawable background(int color, int stroke) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(12));
        if (stroke != 0) shape.setStroke(dp(2), stroke);
        return shape;
    }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); return view;
    }
    private void loadApps() {
        PackageManager pm = getPackageManager();
        Map<String, App> found = new LinkedHashMap<>();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo info : pm.queryIntentActivities(intent, 0)) {
                if (info.activityInfo == null || !info.activityInfo.exported || getPackageName().equals(info.activityInfo.packageName)) continue;
                ComponentName component = new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
                if (!found.containsKey(component.getPackageName())) found.put(component.getPackageName(), new App(component, info.loadLabel(pm).toString()));
            }
        }
        apps.clear(); apps.addAll(found.values());
        Collections.sort(apps, new Comparator<App>() { @Override public int compare(App a, App b) { return String.CASE_INSENSITIVE_ORDER.compare(a.label, b.label); } });
    }
    private void initializeSlots() {
        if (prefs.getBoolean("slots_initialized", false)) return;
        boolean hasExistingSlots = slotCount() > 0;
        SharedPreferences.Editor editor = prefs.edit();
        if (!hasExistingSlots) for (int i = 0; i < Math.min(COLUMNS, apps.size()); i++) editor.putString("slot_" + i, apps.get(i).key());
        editor.putBoolean("slots_initialized", true).apply();
    }
    // Slot keys from older versions may contain gaps or entries beyond the old visible rows.
    private int slotCount() {
        int count = 0;
        for (String key : prefs.getAll().keySet()) {
            if (!key.startsWith("slot_")) continue;
            try { count = Math.max(count, Integer.parseInt(key.substring(5)) + 1); }
            catch (NumberFormatException ignored) { }
        }
        return count;
    }
    private App selected(int slot) {
        String key = moveDraft == null ? prefs.getString("slot_" + slot, null) : moveDraft[slot];
        if (key == null) return null;
        for (App app : apps) if (app.key().equals(key) && !hidden().contains(app.component.getPackageName())) return app;
        return null;
    }
    private void render() {
        String desiredFocus = movingSlot >= 0 ? "home:" + movingSlot : focusTag;
        scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        GradientDrawable backdrop = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xff20243d, 0xff111827, 0xff0a101d}); scroll.setBackground(backdrop);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setClipChildren(false); content.setClipToPadding(false); content.setPadding(dp(56), dp(36), dp(56), dp(48));
        scroll.addView(content); setContentView(scroll);
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL); header.setMinimumHeight(dp(118)); content.addView(header, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout timeBlock = new LinearLayout(this); timeBlock.setOrientation(LinearLayout.VERTICAL); timeBlock.setGravity(Gravity.RIGHT);
        header.addView(timeBlock);
        clock = text("", 32, Color.WHITE); clock.setGravity(Gravity.RIGHT); timeBlock.addView(clock);
        date = text("", 15, 0xffc3cde0); date.setGravity(Gravity.RIGHT); timeBlock.addView(date); updateClockText();
        ImageView settings = new ImageView(this); settings.setImageResource(R.drawable.settings); settings.setPadding(dp(14), dp(14), dp(14), dp(14)); settings.setScaleType(ImageView.ScaleType.FIT_CENTER); settings.setContentDescription(getString(R.string.settings_title)); settings.setFocusable(true); settings.setClickable(true); settings.setTag("settings");
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(52), dp(52)); sp.leftMargin = dp(20); header.addView(settings, sp);
        settings.setBackground(background(Color.rgb(37, 50, 66), 0));
        settings.setOnFocusChangeListener((v, focused) -> { if (focused) focusTag = "settings"; v.setBackground(background(focused ? 0xff516879 : 0xff253242, focused ? Color.WHITE : 0)); v.animate().scaleX(focused ? 1.08f : 1f).scaleY(focused ? 1.08f : 1f).setDuration(120).start(); });
        settings.setOnClickListener(v -> settings());
        GridLayout favoritesGrid = grid(); LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-1, -2); gp.topMargin = dp(8); content.addView(favoritesGrid, gp);
        int count = moveDraft != null ? moveDraft.length : slotCount();
        List<Integer> visibleSlots = new ArrayList<>();
        for (int i = 0; i < count; i++) if (selected(i) != null) visibleSlots.add(i);
        for (int index = 0; index < visibleSlots.size(); index++) {
            final int slot = visibleSlots.get(index); App app = selected(slot);
            View tile = tile(app, movingSlot == slot); tile.setTag("home:" + slot);
            final int column = index % COLUMNS;
            tile.setOnClickListener(v -> { if (movingSlot >= 0) finishMove(true); else launch(app); });
            tile.setOnLongClickListener(v -> { if (movingSlot < 0) appMenu(app, slot); return true; });
            final int position = index;
            tile.setOnKeyListener((v, key, event) -> {
                if (key == KeyEvent.KEYCODE_MENU && event.getAction() == KeyEvent.ACTION_UP && movingSlot < 0) { appMenu(app, slot); return true; }
                if (key == KeyEvent.KEYCODE_MENU) return true;
                if (movingSlot >= 0 && (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT || key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN)) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) moveTile(key, position, visibleSlots);
                    return true;
                }
                if (position / COLUMNS == (visibleSlots.size() - 1) / COLUMNS && key == KeyEvent.KEYCODE_DPAD_DOWN && event.getAction() == KeyEvent.ACTION_DOWN && !expanded) { lastHomeColumn = column; expand(); return true; }
                return false;
            });
            addTile(favoritesGrid, tile);
        }
        if (visibleSlots.isEmpty()) settings.setOnKeyListener((v, key, event) -> {
            if (key == KeyEvent.KEYCODE_DPAD_DOWN && event.getAction() == KeyEvent.ACTION_DOWN && !expanded) { expand(); return true; }
            return false;
        });
        if (visibleSlots.isEmpty()) {
            TextView empty = text(getString(R.string.empty_home), 16, 0xffbdcad8);
            LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, -2); ep.setMargins(dp(5), dp(12), 0, dp(12)); content.addView(empty, ep);
        }
        TextView allButton = text(expanded ? getString(R.string.collapse) : getString(R.string.all_apps), 16, Color.WHITE);
        allButton.setGravity(Gravity.CENTER); allButton.setTypeface(null, Typeface.BOLD); allButton.setFocusable(true); allButton.setClickable(true); allButton.setTag("all_button");
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(200), dp(48)); bp.setMargins(dp(5), dp(16), 0, dp(8)); content.addView(allButton, bp);
        allButton.setBackground(background(0xff343b58, 0));
        allButton.setOnFocusChangeListener((v, focused) -> { if (focused) focusTag = "all_button"; v.setBackground(background(focused ? 0xff586786 : 0xff343b58, focused ? Color.WHITE : 0)); });
        allButton.setOnClickListener(v -> { if (expanded) collapse(); else expand(); });
        if (expanded) addAllApps(false);
        View restore = desiredFocus == null ? null : content.findViewWithTag(desiredFocus);
        if (restore == null && favoritesGrid.getChildCount() > 0) restore = favoritesGrid.getChildAt(0);
        if (restore == null) restore = settings;
        restore.requestFocus();
    }
    private void updateClockText() { Date now = new Date(); clock.setText(DateFormat.getTimeInstance(DateFormat.SHORT).format(now)); date.setText(DateFormat.getDateInstance(DateFormat.LONG).format(now)); }
    private GridLayout grid() { GridLayout grid = new GridLayout(this); grid.setColumnCount(COLUMNS); grid.setClipChildren(false); grid.setClipToPadding(false); grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS); return grid; }
    private void addTile(GridLayout grid, View tile) {
        GridLayout.LayoutParams p = new GridLayout.LayoutParams(); p.width = Math.max(dp(40), (getResources().getDisplayMetrics().widthPixels - dp(112)) / COLUMNS - dp(10)); p.height = Math.max(dp(80), p.width * 9 / 16 + (labelMode() == 0 ? dp(30) : 0)); p.setMargins(dp(5), dp(7), dp(5), dp(7)); grid.addView(tile, p);
    }
    private int labelMode() { return prefs.getInt("label_mode", 0); }
    private View tile(App app, boolean moving) {
        final int labelMode = labelMode();
        FrameLayout tile = new FrameLayout(this); tile.setFocusable(true); tile.setClickable(true);
        tile.setBackground(background(0xff252c40, 0)); tile.setClipToOutline(true);
        Drawable artwork = null; boolean banner = false; PackageManager pm = getPackageManager();
        try {
            artwork = pm.getActivityBanner(app.component);
            if (artwork == null) artwork = pm.getApplicationBanner(app.component.getPackageName());
            banner = artwork != null;
            if (artwork == null) artwork = pm.getActivityIcon(app.component);
        } catch (PackageManager.NameNotFoundException ignored) { }
        if (artwork == null) artwork = pm.getDefaultActivityIcon();
        ImageView icon = new ImageView(this); icon.setImageDrawable(artwork);
        icon.setScaleType(banner ? ImageView.ScaleType.FIT_XY : ImageView.ScaleType.FIT_CENTER);
        if (!banner) icon.setPadding(dp(12), dp(10), dp(12), dp(10));
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(-1, -1); iconParams.bottomMargin = labelMode == 0 ? dp(30) : 0; tile.addView(icon, iconParams);
        TextView label = text(app.label, 13, Color.WHITE); label.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT); label.setPadding(dp(12), 0, dp(8), 0); label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END); label.setBackgroundColor(labelMode == 0 ? 0xff252c40 : 0xdd151b2b);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, dp(30), Gravity.BOTTOM); tile.addView(label, lp); label.setVisibility(labelMode == 0 ? View.VISIBLE : View.INVISIBLE);
        View focus = new View(this); focus.setBackground(background(Color.TRANSPARENT, moving ? 0xff7cf3bd : Color.WHITE)); focus.setVisibility(View.INVISIBLE);
        tile.addView(focus, new FrameLayout.LayoutParams(-1, -1));
        tile.setOnFocusChangeListener((v, focused) -> {
            focus.setVisibility(focused ? View.VISIBLE : View.INVISIBLE);
            if (labelMode == 1) label.setVisibility(focused ? View.VISIBLE : View.INVISIBLE);
            v.animate().scaleX(focused ? 1.045f : 1f).scaleY(focused ? 1.045f : 1f).setDuration(120).start();
            if (focused) focusTag = (String) v.getTag();
        });
        tile.setContentDescription(app.label); return tile;
    }
    private void expand() { homeFocusTag = focusTag; expanded = true; TextView button = content.findViewWithTag("all_button"); if (button != null) button.setText(getString(R.string.collapse)); addAllApps(true); }
    private void collapse() { expanded = false; focusTag = homeFocusTag != null ? homeFocusTag : "all_button"; render(); }
    private void addAllApps(boolean focus) {
        GridLayout grid = grid(); content.addView(grid);
        Set<String> homeApps = new HashSet<>();
        int count = moveDraft != null ? moveDraft.length : slotCount();
        for (int i = 0; i < count; i++) { App app = selected(i); if (app != null) homeApps.add(app.component.getPackageName()); }
        Set<String> hiddenApps = hidden();
        for (App app : apps) {
            if (hiddenApps.contains(app.component.getPackageName()) || homeApps.contains(app.component.getPackageName())) continue;
            View tile = tile(app, false); tile.setTag("app:" + app.key()); tile.setOnClickListener(v -> launch(app));
            tile.setOnLongClickListener(v -> { appMenu(app, -1); return true; });
            tile.setOnKeyListener((v, key, event) -> { if (key != KeyEvent.KEYCODE_MENU) return false; if (event.getAction() == KeyEvent.ACTION_UP) appMenu(app, -1); return true; }); addTile(grid, tile);
        }
        if (grid.getChildCount() == 0) {
            TextView empty = text(getString(R.string.empty_apps), 16, 0xffbdcad8);
            LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, -2); ep.setMargins(dp(5), dp(8), 0, dp(16)); content.addView(empty, ep);
            if (focus) { View button = content.findViewWithTag("all_button"); if (button != null) button.requestFocus(); }
        }
        if (focus && grid.getChildCount() > 0) {
            grid.setAlpha(0f); grid.animate().alpha(1f).setDuration(160).start();
            grid.getChildAt(Math.min(lastHomeColumn, grid.getChildCount() - 1)).requestFocus();
        }
    }
    private Set<String> hidden() { return new HashSet<>(prefs.getStringSet("hidden", Collections.<String>emptySet())); }
    private void appMenu(App app, int slot) {
        String[] actions = slot >= 0
                ? new String[]{getString(R.string.move), getString(R.string.remove_home), getString(R.string.hide_app), getString(R.string.app_info), getString(R.string.uninstall)}
                : new String[]{getString(R.string.add_home), getString(R.string.hide_app), getString(R.string.app_info), getString(R.string.uninstall)};
        new AlertDialog.Builder(this).setTitle(app.label).setItems(actions, (dialog, which) -> {
            String action = actions[which];
            if (action.equals(getString(R.string.move))) startMove(slot);
            else if (action.equals(getString(R.string.add_home))) addToHome(app);
            else if (action.equals(getString(R.string.remove_home))) { prefs.edit().remove("slot_" + slot).apply(); render(); }
            else if (action.equals(getString(R.string.hide_app))) { Set<String> values = hidden(); values.add(app.component.getPackageName()); prefs.edit().putStringSet("hidden", values).apply(); render(); }
            else if (action.equals(getString(R.string.app_info))) openAppDetails(app.component.getPackageName());
            else uninstall(app);
        }).setNegativeButton(getString(R.string.cancel), null).show();
    }
    private void startMove(int slot) {
        moveDraft = new String[slotCount()];
        for (int i = 0; i < moveDraft.length; i++) moveDraft[i] = prefs.getString("slot_" + i, null);
        movingSlot = slot;
        Toast.makeText(this, getString(R.string.move_hint), Toast.LENGTH_LONG).show();
        render();
    }
    private void moveTile(int key, int position, List<Integer> slots) {
        int target = position;
        if (key == KeyEvent.KEYCODE_DPAD_LEFT && position % COLUMNS > 0) target--;
        else if (key == KeyEvent.KEYCODE_DPAD_RIGHT && position % COLUMNS < COLUMNS - 1) target++;
        else if (key == KeyEvent.KEYCODE_DPAD_UP) target -= COLUMNS;
        else if (key == KeyEvent.KEYCODE_DPAD_DOWN) target += COLUMNS;
        if (target < 0 || target >= slots.size() || target == position) return;
        int destination = slots.get(target);
        String other = moveDraft[destination]; moveDraft[destination] = moveDraft[movingSlot]; moveDraft[movingSlot] = other;
        movingSlot = destination; render();
    }
    private void finishMove(boolean save) {
        int slot = movingSlot;
        if (save) {
            SharedPreferences.Editor editor = prefs.edit();
            for (int i = 0; i < moveDraft.length; i++) {
                if (moveDraft[i] == null) editor.remove("slot_" + i); else editor.putString("slot_" + i, moveDraft[i]);
            }
            editor.apply();
        } else {
            String appKey = moveDraft[movingSlot];
            for (int i = 0; i < moveDraft.length; i++) if (appKey != null && appKey.equals(prefs.getString("slot_" + i, null))) { slot = i; break; }
        }
        movingSlot = -1; moveDraft = null; focusTag = "home:" + slot;
        render();
    }
    private void addToHome(App app) {
        int count = slotCount();
        List<String> existingApps = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String existing = prefs.getString("slot_" + i, null);
            if (app.key().equals(existing)) {
                focusTag = "home:" + i; render(); return;
            }
            if (existing != null) existingApps.add(existing);
        }
        int target = existingApps.size(); existingApps.add(app.key());
        SharedPreferences.Editor editor = prefs.edit();
        for (int i = 0; i < Math.max(count, existingApps.size()); i++) {
            if (i < existingApps.size()) editor.putString("slot_" + i, existingApps.get(i));
            else editor.remove("slot_" + i);
        }
        editor.apply();
        focusTag = "home:" + target; render();
    }
    private void uninstall(App app) {
        Intent intent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:" + app.component.getPackageName()));
        intent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
        try { startActivityForResult(intent, 1); }
        catch (android.content.ActivityNotFoundException | SecurityException e) {
            new AlertDialog.Builder(this).setMessage(getString(R.string.uninstall_error))
                    .setPositiveButton(getString(R.string.app_info), (dialog, which) -> openAppDetails(app.component.getPackageName()))
                    .setNegativeButton(getString(R.string.close), null).show();
        }
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode != RESULT_OK) Toast.makeText(this, getString(R.string.not_uninstalled), Toast.LENGTH_LONG).show();
    }
    private void openAppDetails(String packageName) {
        Intent details = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + packageName));
        try { startActivity(details); }
        catch (android.content.ActivityNotFoundException | SecurityException e) {
            new AlertDialog.Builder(this).setMessage(getString(R.string.details_fallback))
                    .setPositiveButton(getString(R.string.open), (dialog, which) -> {
                        try { startActivity(new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)); }
                        catch (android.content.ActivityNotFoundException | SecurityException ignored) {
                            Toast.makeText(this, getString(R.string.settings_unavailable), Toast.LENGTH_LONG).show();
                        }
                    }).setNegativeButton(getString(R.string.cancel), null).show();
        }
    }
    private void showInstalledApps() {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> installed = new ArrayList<>(pm.getInstalledApplications(0));
        for (int i = installed.size() - 1; i >= 0; i--) if (getPackageName().equals(installed.get(i).packageName)) installed.remove(i);
        Collections.sort(installed, (a, b) -> String.CASE_INSENSITIVE_ORDER.compare(pm.getApplicationLabel(a).toString(), pm.getApplicationLabel(b).toString()));
        String[] labels = new String[installed.size()];
        for (int i = 0; i < installed.size(); i++) labels[i] = pm.getApplicationLabel(installed.get(i)).toString();
        new AlertDialog.Builder(this).setTitle(getString(R.string.installed_apps))
                .setItems(labels, (dialog, which) -> installedAppMenu(installed.get(which)))
                .setNegativeButton(getString(R.string.close), null).show();
    }
    private void installedAppMenu(ApplicationInfo info) {
        App launchable = null;
        for (App candidate : apps) if (candidate.component.getPackageName().equals(info.packageName)) { launchable = candidate; break; }
        final App app = launchable;
        final boolean isHidden = hidden().contains(info.packageName);
        int homeSlot = -1;
        if (app != null) for (int i = 0; i < slotCount(); i++) if (app.key().equals(prefs.getString("slot_" + i, null))) { homeSlot = i; break; }
        final int slot = homeSlot;
        List<String> actions = new ArrayList<>();
        if (app != null) {
            actions.add(getString(R.string.open));
            actions.add(slot >= 0 ? getString(R.string.remove_home) : getString(R.string.add_home));
            actions.add(isHidden ? getString(R.string.show_app) : getString(R.string.hide_app));
        }
        actions.add(getString(R.string.app_info)); actions.add(getString(R.string.uninstall));
        new AlertDialog.Builder(this).setTitle(getPackageManager().getApplicationLabel(info))
                .setItems(actions.toArray(new String[0]), (dialog, which) -> {
                    String action = actions.get(which);
                    if (action.equals(getString(R.string.open))) launch(app);
                    else if (action.equals(getString(R.string.add_home))) {
                        if (isHidden) { Set<String> values = hidden(); values.remove(info.packageName); prefs.edit().putStringSet("hidden", values).apply(); }
                        addToHome(app);
                    } else if (action.equals(getString(R.string.remove_home))) { prefs.edit().remove("slot_" + slot).apply(); render(); }
                    else if (action.equals(getString(R.string.show_app)) || action.equals(getString(R.string.hide_app))) {
                        Set<String> values = hidden(); if (isHidden) values.remove(info.packageName); else values.add(info.packageName);
                        prefs.edit().putStringSet("hidden", values).apply(); render();
                    } else if (action.equals(getString(R.string.app_info))) openAppDetails(info.packageName);
                    else uninstall(new App(new ComponentName(info.packageName, ""), getPackageManager().getApplicationLabel(info).toString()));
                }).setNegativeButton(getString(R.string.back), (dialog, which) -> showInstalledApps())
                .setOnCancelListener(dialog -> showInstalledApps()).show();
    }
    private void settings() {
        new AlertDialog.Builder(this).setTitle(getString(R.string.settings_title))
                .setItems(new String[]{getString(R.string.installed_apps), getString(R.string.app_names), getString(R.string.hidden_apps), getString(R.string.reset_settings)}, (dialog, which) -> {
                    if (which == 0) showInstalledApps(); else if (which == 1) chooseLabelMode(); else if (which == 2) showHidden(); else confirmReset();
                }).setNegativeButton(getString(R.string.close), null).show();
    }
    private void chooseLabelMode() {
        String[] choices = {getString(R.string.labels_always), getString(R.string.labels_focus), getString(R.string.labels_never)};
        new AlertDialog.Builder(this).setTitle(getString(R.string.app_names))
                .setSingleChoiceItems(choices, labelMode(), (dialog, which) -> {
                    prefs.edit().putInt("label_mode", which).apply(); dialog.dismiss(); render();
                }).setNegativeButton(getString(R.string.cancel), null).show();
    }
    private void confirmReset() {
        new AlertDialog.Builder(this).setTitle(getString(R.string.reset_title))
                .setMessage(getString(R.string.reset_message))
                .setPositiveButton(getString(R.string.reset), (dialog, which) -> {
                    prefs.edit().clear().apply(); initializeSlots(); expanded = false; focusTag = null; render();
                }).setNegativeButton(getString(R.string.cancel), null).show();
    }
    private void showHidden() {
        Set<String> values = hidden(); List<App> hiddenApps = new ArrayList<>(); List<String> labels = new ArrayList<>();
        for (App app : apps) if (values.contains(app.component.getPackageName())) { hiddenApps.add(app); labels.add(app.label); }
        if (hiddenApps.isEmpty()) { new AlertDialog.Builder(this).setTitle(getString(R.string.hidden_apps)).setMessage(getString(R.string.no_hidden)).setPositiveButton(getString(R.string.done), null).show(); return; }
        new AlertDialog.Builder(this).setTitle(getString(R.string.show_app)).setItems(labels.toArray(new String[0]), (dialog, which) -> {
            Set<String> updated = hidden(); updated.remove(hiddenApps.get(which).component.getPackageName()); prefs.edit().putStringSet("hidden", updated).apply(); render(); showHidden();
        }).setNegativeButton(getString(R.string.close), null).show();
    }
    private void launch(App app) {
        PackageManager pm = getPackageManager();
        Intent[] intents = {
                new Intent(Intent.ACTION_MAIN).setComponent(app.component),
                pm.getLeanbackLaunchIntentForPackage(app.component.getPackageName()),
                pm.getLaunchIntentForPackage(app.component.getPackageName())
        };
        for (Intent intent : intents) {
            if (intent == null) continue;
            try { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return; }
            catch (android.content.ActivityNotFoundException | SecurityException ignored) { }
        }
        Toast.makeText(this, getString(R.string.launch_error), Toast.LENGTH_SHORT).show();
    }
    @Override public void onBackPressed() { if (movingSlot >= 0) { finishMove(false); } else if (expanded) { collapse(); } }
}
