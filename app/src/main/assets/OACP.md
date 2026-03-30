# Clock — OACP Context

## What this app does

Clock is an open-source Android alarm clock, timer, and stopwatch app. It lets users set one-time or repeating alarms, run multiple simultaneous countdown timers, and operate a stopwatch with lap recording. It supports gesture controls (flip/shake to dismiss), physical button snoozing, and customizable ringtones and labels. This is a local, on-device app — no network access needed.

## Vocabulary

- "alarm" means a scheduled time at which the device will ring to wake the user
- "timer" means a countdown from a set duration to zero — there can be multiple running at once
- "stopwatch" means the count-up timer starting from zero — there is only one stopwatch
- "lap" means a split-time marker recorded while the stopwatch is running
- "enabled" / "on" means an alarm is scheduled and will fire; "disabled" / "off" means it will not fire
- "active timers" means timers that are currently running or paused (not reset/expired)
- "weekdays" means Monday through Friday
- "weekends" means Saturday and Sunday
- "everyday" means all seven days

## Capabilities

### get_active_alarms
Queries all enabled alarms and speaks them to the user. If no alarms are enabled, says "No alarms are currently on." The response includes each alarm's time and label if set.

Confirmation notes:
- confirmation policy is never
- when running, say: "Checking your alarms."

Possible phrases:
- "What alarms do I have set?"
- "Are any alarms on?"
- "List my alarms"
- "Show my scheduled alarms"

### set_alarm
Creates a new enabled alarm at the given hour and minute (24-hour format). If `days` is omitted, the alarm fires once and is automatically deleted after use. If `days` is provided, the alarm repeats on those days each week.

Confirmation notes:
- confirmation policy is never
- when running, say: "Setting the alarm."

Possible phrases:
- "Set an alarm for 7am"
- "Wake me up at 6:30"
- "Set a weekday alarm for 8am"
- "Create an alarm for 9pm labeled bedtime"

Parameter notes:
- `hour` must be 0–23 (24-hour format); convert "7am" → 7, "9pm" → 21
- `minute` must be 0–59; convert "half past" → 30, "quarter past" → 15
- `days` accepts abbreviations: MON, TUE, WED, THU, FRI, SAT, SUN — or "weekdays", "weekends", "everyday"
- Omit `days` for a one-time alarm

### cancel_alarm
Disables a specific alarm so it will not fire. The alarm stays in the app but is switched off. Resolve the target alarm from the entity provider before invoking.

Confirmation notes:
- confirmation policy is if_destructive
- ask: "Turn off this alarm?"
- when running, say: "Turning off the alarm."

Possible phrases:
- "Cancel my 7am alarm"
- "Turn off the gym alarm"
- "Disable the work alarm"

### cancel_all_alarms
Turns off every enabled alarm. Always ask for confirmation before running this — it is a broad action that cannot be easily undone.

Confirmation notes:
- confirmation policy is always
- ask: "Turn off all your alarms?"
- when running, say: "Turning off all alarms."

Possible phrases:
- "Cancel all my alarms"
- "Turn off all alarms"
- "Disable every alarm"

### start_timer
Creates and immediately starts a countdown timer. `duration_seconds` is required — convert user-spoken minutes to seconds before passing. Multiple timers can run simultaneously.

Confirmation notes:
- confirmation policy is never
- when running, say: "Starting the timer."

Possible phrases:
- "Set a timer for 5 minutes"
- "Start a 10 minute timer"
- "Timer for 2 minutes 30 seconds"
- "Start a pasta timer for 8 minutes"

Parameter notes:
- `duration_seconds` is always in seconds. Convert: 1 min = 60s, 5 min = 300s, 1 hr = 3600s
- "2 minutes 30 seconds" → 150 seconds
- `label` is optional but helpful: "pasta", "tea", "pomodoro"

### get_active_timers
Lists all timers that are currently running or paused, with remaining time and labels. If no timers are running, says "No timers are currently active."

Confirmation notes:
- confirmation policy is never
- when running, say: "Checking your timers."

Possible phrases:
- "How much time is left on my timer?"
- "What timers are running?"
- "List my active timers"

### cancel_timer
Cancels and removes a specific running or paused timer. Resolve the target timer from the entity provider before invoking.

Confirmation notes:
- confirmation policy is if_destructive
- ask: "Cancel this timer?"
- when running, say: "Cancelling the timer."

Possible phrases:
- "Cancel my pasta timer"
- "Stop the 5 minute timer"
- "Remove the tea timer"

### start_stopwatch
Starts or resumes the stopwatch. If the stopwatch is paused, it resumes from where it left off.

Confirmation notes:
- confirmation policy is never
- when running, say: "Starting the stopwatch."

Possible phrases:
- "Start the stopwatch"
- "Begin timing"
- "Resume the stopwatch"

### stop_stopwatch
Pauses the stopwatch without resetting it. The elapsed time is preserved.

Confirmation notes:
- confirmation policy is never
- when running, say: "Pausing the stopwatch."

Possible phrases:
- "Stop the stopwatch"
- "Pause the stopwatch"
- "Pause timing"

### reset_stopwatch
Resets the stopwatch to zero and clears all recorded laps.

Confirmation notes:
- confirmation policy is never
- when running, say: "Resetting the stopwatch."

Possible phrases:
- "Reset the stopwatch"
- "Clear the stopwatch"
- "Zero out the stopwatch"

### lap_stopwatch
Records a lap (split time) on the currently running stopwatch.

Confirmation notes:
- confirmation policy is never
- when running, say: "Lap recorded."

Possible phrases:
- "Lap"
- "Record a lap"
- "Mark a split"

## Constraints

- `cancel_alarm` requires a valid alarm_id from the entity provider — the alarm must be enabled
- `cancel_timer` requires a valid timer_id from the entity provider — the timer must be active
- `start_timer` requires `duration_seconds` ≥ 1; maximum is 86400 (24 hours)
- `lap_stopwatch` only works when the stopwatch is actively running
- `stop_stopwatch` only has effect when the stopwatch is running
- Multiple timers can run simultaneously; there is only one stopwatch
- Alarms persist across device reboots; timers and stopwatch state do not survive a force-kill

## Examples

- "Set an alarm for 7am"
- "What alarms do I have on?"
- "Cancel all my alarms"
- "Set a timer for 5 minutes"
- "How much time is left on my timer?"
- "Start the stopwatch"
- "Lap"
- "Reset the stopwatch"
- "Wake me up at 6:30 every weekday"
- "Cancel my work alarm"
