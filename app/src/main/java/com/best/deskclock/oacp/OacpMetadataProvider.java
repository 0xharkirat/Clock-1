/*
 * SPDX-License-Identifier: GPL-3.0-only
 * OACP metadata provider for the Clock app.
 * Serves oacp.json, OACP.md, and live entity lists for alarms and timers.
 */

package com.best.deskclock.oacp;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;

import com.best.deskclock.data.DataModel;
import com.best.deskclock.data.Timer;
import com.best.deskclock.provider.Alarm;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class OacpMetadataProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        List<String> segments = uri.getPathSegments();
        if (segments.isEmpty()) return null;
        switch (segments.get(0)) {
            case "manifest": return "application/json";
            case "context":  return "text/markdown";
            case "entities": return "application/json";
            default:         return null;
        }
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) {
            throw new FileNotFoundException("OACP metadata is read-only.");
        }
        List<String> segments = uri.getPathSegments();
        if (segments.isEmpty()) throw new FileNotFoundException("Unsupported URI: " + uri);

        switch (segments.get(0)) {
            case "manifest":
                return openPipeHelper(uri, "application/json", null,
                        loadManifestBytes(), OacpMetadataProvider::writeBytes);
            case "context":
                return openPipeHelper(uri, "text/markdown", null,
                        loadAssetBytes("OACP.md"), OacpMetadataProvider::writeBytes);
            case "entities":
                if (segments.size() < 2) throw new FileNotFoundException("Missing entity type in URI: " + uri);
                return openPipeHelper(uri, "application/json", null,
                        buildEntityJson(segments.get(1)), OacpMetadataProvider::writeBytes);
            default:
                throw new FileNotFoundException("Unsupported URI: " + uri);
        }
    }

    @Override
    public AssetFileDescriptor openAssetFile(Uri uri, String mode) throws FileNotFoundException {
        return new AssetFileDescriptor(openFile(uri, mode), 0, AssetFileDescriptor.UNKNOWN_LENGTH);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private byte[] loadManifestBytes() throws FileNotFoundException {
        Context ctx = getRequiredContext();
        String raw = new String(loadAssetBytes("oacp.json"), StandardCharsets.UTF_8);
        return raw.replace("__APPLICATION_ID__", ctx.getPackageName())
                  .getBytes(StandardCharsets.UTF_8);
    }

    private byte[] loadAssetBytes(String assetPath) throws FileNotFoundException {
        Context ctx = getRequiredContext();
        try (InputStream in = ctx.getAssets().open(assetPath)) {
            byte[] buffer = new byte[in.available()];
            //noinspection ResultOfMethodCallIgnored
            in.read(buffer);
            return buffer;
        } catch (IOException e) {
            throw new FileNotFoundException("Cannot open asset: " + assetPath);
        }
    }

    private byte[] buildEntityJson(String entityType) throws FileNotFoundException {
        Context ctx = getRequiredContext();
        try {
            JSONObject result = new JSONObject();
            result.put("entityType", entityType);
            JSONArray items = new JSONArray();

            if ("alarm".equals(entityType)) {
                String selection = Alarm.ENABLED + "=1";
                List<Alarm> alarms = Alarm.getAlarms(ctx.getContentResolver(), selection);
                for (Alarm alarm : alarms) {
                    JSONObject item = new JSONObject();
                    item.put("id", String.valueOf(alarm.id));
                    item.put("displayName", buildAlarmLabel(alarm));
                    items.put(item);
                }

            } else if ("timer".equals(entityType)) {
                List<Timer> timers = DataModel.getDataModel().getTimers();
                for (Timer timer : timers) {
                    if (!timer.isRunning() && !timer.isPaused()) continue;
                    JSONObject item = new JSONObject();
                    item.put("id", String.valueOf(timer.getId()));
                    item.put("displayName", buildTimerLabel(timer));
                    items.put(item);
                }
            }

            result.put("items", items);
            return result.toString().getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new FileNotFoundException("Failed to build entity JSON for: " + entityType);
        }
    }

    private static String buildAlarmLabel(Alarm alarm) {
        int hour = alarm.hour;
        int minute = alarm.minutes;
        boolean pm = hour >= 12;
        int displayHour = hour % 12;
        if (displayHour == 0) displayHour = 12;
        String time = String.format("%d:%02d %s", displayHour, minute, pm ? "PM" : "AM");
        if (!TextUtils.isEmpty(alarm.label)) {
            return alarm.label + " — " + time;
        }
        return time;
    }

    private static String buildTimerLabel(Timer timer) {
        long remainingMs = timer.getRemainingTime();
        long totalSec = remainingMs / 1000;
        long hours = totalSec / 3600;
        long minutes = (totalSec % 3600) / 60;
        long seconds = totalSec % 60;
        String duration;
        if (hours > 0) {
            duration = String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            duration = String.format("%dm %ds", minutes, seconds);
        } else {
            duration = String.format("%ds", seconds);
        }
        String label = timer.getLabel();
        if (!TextUtils.isEmpty(label)) {
            return label + " — " + duration + " remaining";
        }
        return duration + " remaining";
    }

    private Context getRequiredContext() throws FileNotFoundException {
        Context ctx = getContext();
        if (ctx == null) throw new FileNotFoundException("Provider context is null.");
        return ctx;
    }

    private static void writeBytes(ParcelFileDescriptor output, Uri uri, String mimeType,
                                   Bundle opts, byte[] data) {
        try (ParcelFileDescriptor.AutoCloseOutputStream stream =
                     new ParcelFileDescriptor.AutoCloseOutputStream(output)) {
            if (data != null) stream.write(data);
        } catch (IOException ignored) {
        }
    }

    // ── Unused ContentProvider stubs ─────────────────────────────────────────

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }
}
