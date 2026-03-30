/*
 * SPDX-License-Identifier: GPL-3.0-only
 * OACP broadcast receiver for the Clock app.
 * Handles all OACP capability invocations for alarms, timers, and stopwatch.
 */

package com.best.deskclock.oacp;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import com.best.deskclock.data.DataModel;
import com.best.deskclock.data.Timer;
import com.best.deskclock.data.Weekdays;
import com.best.deskclock.alarms.AlarmStateManager;
import com.best.deskclock.provider.Alarm;
import com.best.deskclock.provider.AlarmInstance;

import android.os.Handler;
import android.os.Looper;

import java.util.Calendar;
import java.util.List;

public class OacpReceiver extends BroadcastReceiver {

    // ── Intent action suffixes (prefix is ${applicationId} at runtime) ────────

    private static final String SUFFIX_GET_ACTIVE_ALARMS  = ".ACTION_OACP_GET_ACTIVE_ALARMS";
    private static final String SUFFIX_SET_ALARM          = ".ACTION_OACP_SET_ALARM";
    private static final String SUFFIX_CANCEL_ALARM       = ".ACTION_OACP_CANCEL_ALARM";
    private static final String SUFFIX_CANCEL_ALL_ALARMS  = ".ACTION_OACP_CANCEL_ALL_ALARMS";
    private static final String SUFFIX_START_TIMER        = ".ACTION_OACP_START_TIMER";
    private static final String SUFFIX_GET_ACTIVE_TIMERS  = ".ACTION_OACP_GET_ACTIVE_TIMERS";
    private static final String SUFFIX_CANCEL_TIMER       = ".ACTION_OACP_CANCEL_TIMER";
    private static final String SUFFIX_START_STOPWATCH    = ".ACTION_OACP_START_STOPWATCH";
    private static final String SUFFIX_STOP_STOPWATCH     = ".ACTION_OACP_STOP_STOPWATCH";
    private static final String SUFFIX_RESET_STOPWATCH    = ".ACTION_OACP_RESET_STOPWATCH";
    private static final String SUFFIX_LAP_STOPWATCH      = ".ACTION_OACP_LAP_STOPWATCH";

    // ── Intent extra suffixes (prefix is ${applicationId} at runtime) ───────

    private static final String SUFFIX_EXTRA_HOUR             = ".EXTRA_HOUR";
    private static final String SUFFIX_EXTRA_MINUTE           = ".EXTRA_MINUTE";
    private static final String SUFFIX_EXTRA_LABEL            = ".EXTRA_LABEL";
    private static final String SUFFIX_EXTRA_DAYS             = ".EXTRA_DAYS";
    private static final String SUFFIX_EXTRA_ALARM_ID         = ".EXTRA_ALARM_ID";
    private static final String SUFFIX_EXTRA_DURATION_SECONDS = ".EXTRA_DURATION_SECONDS";
    private static final String SUFFIX_EXTRA_TIMER_ID         = ".EXTRA_TIMER_ID";

    // ── OACP result extras ───────────────────────────────────────────────────

    private static final String OACP_RESULT_ACTION       = "org.oacp.ACTION_RESULT";
    private static final String OACP_EXTRA_REQUEST_ID    = "org.oacp.extra.REQUEST_ID";
    private static final String OACP_EXTRA_STATUS        = "org.oacp.extra.STATUS";
    private static final String OACP_EXTRA_CAPABILITY_ID = "org.oacp.extra.CAPABILITY_ID";
    private static final String OACP_EXTRA_SOURCE_PKG    = "org.oacp.extra.SOURCE_PACKAGE";
    private static final String OACP_EXTRA_MESSAGE       = "org.oacp.extra.MESSAGE";
    private static final String OACP_EXTRA_ERROR         = "org.oacp.extra.ERROR";

    // ── BroadcastReceiver ────────────────────────────────────────────────────

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (action == null) return;

        final String requestId = intent.getStringExtra(OACP_EXTRA_REQUEST_ID);
        final PendingResult pendingResult = goAsync();

        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                dispatch(context, action, intent, requestId);
            } finally {
                pendingResult.finish();
            }
        });
    }

    // ── Dispatch ─────────────────────────────────────────────────────────────

    private void dispatch(Context ctx, String action, Intent intent, String requestId) {
        if (action.endsWith(SUFFIX_GET_ACTIVE_ALARMS)) {
            handleGetActiveAlarms(ctx, requestId);
        } else if (action.endsWith(SUFFIX_SET_ALARM)) {
            handleSetAlarm(ctx, intent);
        } else if (action.endsWith(SUFFIX_CANCEL_ALARM)) {
            handleCancelAlarm(ctx, intent);
        } else if (action.endsWith(SUFFIX_CANCEL_ALL_ALARMS)) {
            handleCancelAllAlarms(ctx);
        } else if (action.endsWith(SUFFIX_START_TIMER)) {
            handleStartTimer(ctx, intent);
        } else if (action.endsWith(SUFFIX_GET_ACTIVE_TIMERS)) {
            handleGetActiveTimers(ctx, requestId);
        } else if (action.endsWith(SUFFIX_CANCEL_TIMER)) {
            handleCancelTimer(ctx, intent);
        } else if (action.endsWith(SUFFIX_START_STOPWATCH)) {
            DataModel.getDataModel().startStopwatch();
        } else if (action.endsWith(SUFFIX_STOP_STOPWATCH)) {
            DataModel.getDataModel().pauseStopwatch();
        } else if (action.endsWith(SUFFIX_RESET_STOPWATCH)) {
            DataModel.getDataModel().resetStopwatch();
        } else if (action.endsWith(SUFFIX_LAP_STOPWATCH)) {
            if (DataModel.getDataModel().canAddMoreLaps()) {
                DataModel.getDataModel().addLap();
            }
        }
    }

    // ── Alarm handlers ───────────────────────────────────────────────────────

    private void handleGetActiveAlarms(Context ctx, String requestId) {
        if (requestId == null) return;

        ContentResolver cr = ctx.getContentResolver();
        List<Alarm> alarms = Alarm.getAlarms(cr, Alarm.ENABLED + "=1");

        String message;
        if (alarms.isEmpty()) {
            message = "No alarms are currently on.";
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(alarms.size() == 1 ? "You have 1 alarm on: " : "You have " + alarms.size() + " alarms on: ");
            for (int i = 0; i < alarms.size(); i++) {
                if (i > 0) sb.append(i == alarms.size() - 1 ? ", and " : ", ");
                sb.append(buildAlarmDescription(alarms.get(i)));
            }
            sb.append(".");
            message = sb.toString();
        }

        sendResult(ctx, requestId, "get_active_alarms", "completed", message, null);
    }

    private void handleSetAlarm(Context ctx, Intent intent) {
        int hour   = getIntExtraBySuffix(intent, SUFFIX_EXTRA_HOUR, -1);
        int minute = getIntExtraBySuffix(intent, SUFFIX_EXTRA_MINUTE, -1);
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return;

        String label = getStringExtraBySuffix(intent, SUFFIX_EXTRA_LABEL);
        String daysStr = getStringExtraBySuffix(intent, SUFFIX_EXTRA_DAYS);

        Alarm alarm = new Alarm();
        alarm.hour    = hour;
        alarm.minutes = minute;
        alarm.enabled = true;
        alarm.label   = TextUtils.isEmpty(label) ? "" : label;
        alarm.daysOfWeek = parseDays(daysStr);
        alarm.deleteAfterUse = (alarm.daysOfWeek == Weekdays.NONE);

        ContentResolver cr = ctx.getContentResolver();
        Alarm newAlarm = alarm.addAlarm(cr);

        Calendar now = DataModel.getDataModel().getCalendar();
        AlarmInstance instance = newAlarm.createInstanceAfter(now);
        instance.addInstance(cr);
        AlarmStateManager.registerInstance(ctx, instance, true);
    }

    private void handleCancelAlarm(Context ctx, Intent intent) {
        String alarmIdStr = getStringExtraBySuffix(intent, SUFFIX_EXTRA_ALARM_ID);
        if (TextUtils.isEmpty(alarmIdStr)) return;

        long alarmId;
        try {
            alarmId = Long.parseLong(alarmIdStr);
        } catch (NumberFormatException e) {
            return;
        }

        ContentResolver cr = ctx.getContentResolver();
        Alarm alarm = Alarm.getAlarm(cr, alarmId);
        if (alarm == null) return;

        alarm.enabled = false;
        alarm.updateAlarm(cr);
        AlarmStateManager.deleteAllInstances(ctx, alarm.id);
    }

    private void handleCancelAllAlarms(Context ctx) {
        ContentResolver cr = ctx.getContentResolver();
        List<Alarm> alarms = Alarm.getAlarms(cr, Alarm.ENABLED + "=1");
        for (Alarm alarm : alarms) {
            alarm.enabled = false;
            alarm.updateAlarm(cr);
            AlarmStateManager.deleteAllInstances(ctx, alarm.id);
        }
    }

    // ── Timer handlers ───────────────────────────────────────────────────────

    private void handleStartTimer(Context ctx, Intent intent) {
        int durationSeconds = getIntExtraBySuffix(intent, SUFFIX_EXTRA_DURATION_SECONDS, -1);
        if (durationSeconds < 1) return;

        String label = getStringExtraBySuffix(intent, SUFFIX_EXTRA_LABEL);
        if (label == null) label = "";

        long durationMs = durationSeconds * 1000L;
        Timer timer = DataModel.getDataModel().addTimer(durationMs, label, "", false);
        DataModel.getDataModel().startTimer(timer);
    }

    private void handleGetActiveTimers(Context ctx, String requestId) {
        if (requestId == null) return;

        List<Timer> allTimers = DataModel.getDataModel().getTimers();
        long now = System.currentTimeMillis();

        StringBuilder activeList = new StringBuilder();
        int count = 0;
        for (Timer timer : allTimers) {
            if (!timer.isRunning() && !timer.isPaused()) continue;
            if (count > 0) activeList.append(count == 1 ? "; " : ", ");
            activeList.append(buildTimerDescription(timer));
            count++;
        }

        String message;
        if (count == 0) {
            message = "No timers are currently active.";
        } else {
            message = (count == 1 ? "You have 1 active timer: " : "You have " + count + " active timers: ")
                    + activeList + ".";
        }

        sendResult(ctx, requestId, "get_active_timers", "completed", message, null);
    }

    private void handleCancelTimer(Context ctx, Intent intent) {
        String timerIdStr = getStringExtraBySuffix(intent, SUFFIX_EXTRA_TIMER_ID);
        if (TextUtils.isEmpty(timerIdStr)) return;

        int timerId;
        try {
            timerId = Integer.parseInt(timerIdStr);
        } catch (NumberFormatException e) {
            return;
        }

        Timer timer = DataModel.getDataModel().getTimer(timerId);
        if (timer == null) return;

        DataModel.getDataModel().removeTimer(timer);
    }

    // ── Result broadcast ─────────────────────────────────────────────────────

    private static void sendResult(Context ctx, String requestId, String capabilityId,
                                   String status, String message, String error) {
        Intent result = new Intent(OACP_RESULT_ACTION);
        result.putExtra(OACP_EXTRA_REQUEST_ID, requestId);
        result.putExtra(OACP_EXTRA_STATUS, status);
        result.putExtra(OACP_EXTRA_CAPABILITY_ID, capabilityId);
        result.putExtra(OACP_EXTRA_SOURCE_PKG, ctx.getPackageName());
        if (message != null) result.putExtra(OACP_EXTRA_MESSAGE, message);
        if (error   != null) result.putExtra(OACP_EXTRA_ERROR,   error);
        ctx.sendBroadcast(result);
    }

    // ── Formatting helpers ───────────────────────────────────────────────────

    private static String buildAlarmDescription(Alarm alarm) {
        int h = alarm.hour;
        int m = alarm.minutes;
        boolean pm = h >= 12;
        int dh = h % 12;
        if (dh == 0) dh = 12;
        String time = m == 0
                ? String.format("%d %s", dh, pm ? "PM" : "AM")
                : String.format("%d:%02d %s", dh, m, pm ? "PM" : "AM");
        if (!TextUtils.isEmpty(alarm.label)) {
            return alarm.label + " at " + time;
        }
        return time;
    }

    private static String buildTimerDescription(Timer timer) {
        long remainingMs = timer.getRemainingTime();
        long totalSec = Math.max(0, remainingMs / 1000);
        long hours   = totalSec / 3600;
        long minutes = (totalSec % 3600) / 60;
        long seconds = totalSec % 60;

        String remaining;
        if (hours > 0) {
            remaining = hours + " hour" + (hours != 1 ? "s" : "")
                    + " " + minutes + " minute" + (minutes != 1 ? "s" : "") + " remaining";
        } else if (minutes > 0) {
            remaining = minutes + " minute" + (minutes != 1 ? "s" : "")
                    + " " + seconds + " second" + (seconds != 1 ? "s" : "") + " remaining";
        } else {
            remaining = seconds + " second" + (seconds != 1 ? "s" : "") + " remaining";
        }

        String label = timer.getLabel();
        if (!TextUtils.isEmpty(label)) {
            return label + " — " + remaining;
        }
        return remaining;
    }

    // ── Day parsing ──────────────────────────────────────────────────────────

    /**
     * Parses a comma-separated day string into a Weekdays bitmask.
     * Accepts abbreviations (MON, TUE, WED, THU, FRI, SAT, SUN)
     * and shorthand ("weekdays", "weekends", "everyday").
     * Returns Weekdays.NONE for null/empty input (one-time alarm).
     */
    private static Weekdays parseDays(String daysStr) {
        if (TextUtils.isEmpty(daysStr)) return Weekdays.NONE;

        String normalized = daysStr.trim().toUpperCase();
        switch (normalized) {
            case "WEEKDAYS":
                return Weekdays.fromCalendarDays(
                        Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                        Calendar.THURSDAY, Calendar.FRIDAY);
            case "WEEKENDS":
                return Weekdays.fromCalendarDays(Calendar.SATURDAY, Calendar.SUNDAY);
            case "EVERYDAY":
            case "EVERY DAY":
            case "DAILY":
                return Weekdays.fromCalendarDays(
                        Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                        Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY);
        }

        String[] parts = normalized.split(",");
        int[] calendarDays = new int[parts.length];
        int count = 0;
        for (String part : parts) {
            int day = dayAbbrevToCalendar(part.trim());
            if (day != -1) calendarDays[count++] = day;
        }

        if (count == 0) return Weekdays.NONE;

        int[] used = new int[count];
        System.arraycopy(calendarDays, 0, used, 0, count);
        return Weekdays.fromCalendarDays(used);
    }

    private static int dayAbbrevToCalendar(String abbrev) {
        switch (abbrev) {
            case "MON": case "MONDAY":    return Calendar.MONDAY;
            case "TUE": case "TUESDAY":   return Calendar.TUESDAY;
            case "WED": case "WEDNESDAY": return Calendar.WEDNESDAY;
            case "THU": case "THURSDAY":  return Calendar.THURSDAY;
            case "FRI": case "FRIDAY":    return Calendar.FRIDAY;
            case "SAT": case "SATURDAY":  return Calendar.SATURDAY;
            case "SUN": case "SUNDAY":    return Calendar.SUNDAY;
            default: return -1;
        }
    }

    // ── Extra lookup by suffix ──────────────────────────────────────────────
    // The oacp.json uses __APPLICATION_ID__ placeholders, so extra keys arrive
    // with a build-variant-specific prefix (e.g. com.best.deskclock.debug.EXTRA_HOUR).
    // These helpers match by suffix so the receiver works across all build variants.

    private static String findExtraKey(Intent intent, String suffix) {
        if (intent.getExtras() == null) return null;
        for (String key : intent.getExtras().keySet()) {
            if (key.endsWith(suffix)) return key;
        }
        return null;
    }

    private static int getIntExtraBySuffix(Intent intent, String suffix, int defaultValue) {
        String key = findExtraKey(intent, suffix);
        if (key == null) return defaultValue;
        return intent.getIntExtra(key, defaultValue);
    }

    private static String getStringExtraBySuffix(Intent intent, String suffix) {
        String key = findExtraKey(intent, suffix);
        if (key == null) return null;
        return intent.getStringExtra(key);
    }
}
