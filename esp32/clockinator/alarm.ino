#include "globals.h"

/*
 * alarm.ino
 * Method implementations for Recurrence and Alarm (declared in globals.h).
 */

/* -------------------------------------------------------------------------
 * Recurrence::nextOccurrence
 * Returns the first timestamp >= (from_time + 1 second) at which the clock
 * shows <hour:minute>, respecting the recurrence rule.
 * -------------------------------------------------------------------------*/
time_t Recurrence::nextOccurrence(time_t from_time, int hour, int minute) const {
    struct tm t;
    localtime_r(&from_time, &t);
    t.tm_hour = hour;
    t.tm_min  = minute;
    t.tm_sec  = 0;
    time_t candidate = mktime(&t);

    /* If the target time is already past today, start looking from tomorrow. */
    if (candidate <= from_time) {
        candidate += 86400;
        localtime_r(&candidate, &t);
        t.tm_hour = hour;
        t.tm_min  = minute;
        t.tm_sec  = 0;
        candidate = mktime(&t);
    }

    switch (type) {
        case REC_DAILY:
            return candidate;

        case REC_WEEKLY: {
            /* Scan up to 8 days to find the nearest matching weekday.
               tm_wday: 0=Sunday..6=Saturday; our convention: 1=Mon..7=Sun. */
            for (int offset = 0; offset < 8; offset++) {
                time_t probe = candidate + (time_t)offset * 86400;
                struct tm pt;
                localtime_r(&probe, &pt);
                int dow = (pt.tm_wday == 0) ? 7 : pt.tm_wday;
                if (days.count(dow)) {
                    pt.tm_hour = hour;
                    pt.tm_min  = minute;
                    pt.tm_sec  = 0;
                    return mktime(&pt);
                }
            }
            return candidate; /* fallback: should not reach here */
        }

        case REC_INTERVAL:
            /* The phone re-syncs after every cycle, so multi-day intervals
               are managed on the Android side. Return the next-day slot. */
            return candidate;
    }
    return candidate;
}

/* -------------------------------------------------------------------------
 * Alarm::nextTrigger
 * Returns the next firing time for slot [t], or 0 when:
 *   - alarm is disabled
 *   - slot.enabled is false (only relevant for BEDTIME)
 *   - slot.ringOnDevice is false
 *   - slot is currently dismissed
 * -------------------------------------------------------------------------*/
time_t Alarm::nextTrigger(time_t now, RingtoneType t) const {
    if (!isEnabled) return 0;

    const RingtoneSlot& s = slot(t);

    if (!s.enabled)      return 0;   /* bedtime disabled */
    if (!s.ringOnDevice) return 0;   /* this device should not ring for this slot */
    if (isDismissed(t))  return 0;   /* skipped for next occurrence */

    return recurrence.nextOccurrence(now, s.hour, s.minute);
}

/* -------------------------------------------------------------------------
 * Alarm::nextAnyTrigger
 * Scans all three slots and returns the earliest upcoming trigger.
 * [outType] is set to the corresponding RingtoneType.
 * Returns 0 if no slot will fire.
 * -------------------------------------------------------------------------*/
time_t Alarm::nextAnyTrigger(time_t now, RingtoneType& outType) const {
    time_t earliest = 0;
    outType = RT_UNKNOWN;

    const RingtoneType types[3] = { RT_LOCK_PHONE, RT_BEDTIME, RT_WAKEUP };
    for (RingtoneType t : types) {
        time_t ts = nextTrigger(now, t);
        if (ts > 0 && (earliest == 0 || ts < earliest)) {
            earliest = ts;
            outType  = t;
        }
    }
    return earliest;
}
