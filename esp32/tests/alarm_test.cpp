#include <cassert>
#include <cstdlib>
#include <iostream>
#include <time.h>

unsigned long millis() { return 0; }
#ifdef _WIN32
static struct tm* localtime_r(const time_t* value, struct tm* result) {
    return localtime_s(result, value) == 0 ? result : nullptr;
}
#endif

// Exercise the firmware implementation itself, rather than a copy of its math.
#include "../clockinator/alarm.ino"
time_t start_time = 0;

static time_t at(int day, int hour, int minute = 0) {
    struct tm value{};
    value.tm_year = 2026 - 1900;
    value.tm_mon = 9;
    value.tm_mday = day;
    value.tm_hour = hour;
    value.tm_min = minute;
    value.tm_isdst = -1;
    return mktime(&value);
}

int main() {
#ifdef _WIN32
    _putenv_s("TZ", "UTC");
    _tzset();
#else
    setenv("TZ", "UTC", 1);
    tzset();
#endif
    Recurrence daily;
    assert(daily.nextOccurrence(at(7, 6), 7, 0) == at(7, 7));
    assert(daily.nextOccurrence(at(7, 7), 7, 0) == at(8, 7));
    Recurrence weekly;
    weekly.type = REC_WEEKLY;
    weekly.days = {1};
    assert(weekly.nextOccurrence(at(7, 6), 7, 0) == at(12, 7));
    Recurrence interval;
    interval.type = REC_INTERVAL;
    interval.interval = 3;
    interval.anchorEpochDay = (long)(at(7, 0) / 86400);
    assert(interval.nextOccurrence(at(7, 7), 7, 0) == at(10, 7));
    assert(interval.nextOccurrence(at(8, 6), 7, 0) == at(10, 7));
    assert(interval.nextOccurrence(at(9, 8), 7, 0) == at(10, 7));
    assert(interval.nextOccurrence(at(7, 7), 22, 0) == at(7, 22));

    Alarm alarm;
    alarm.wakeup.hour = 7;
    start_time = at(7, 6);
    alarm.setDismissed(RT_WAKEUP, true);
    assert(alarm.nextTrigger(start_time, RT_WAKEUP) == at(8, 7));
    // Once the skipped date passes, subsequent occurrences must fire normally.
    assert(alarm.nextTrigger(at(7, 8), RT_WAKEUP) == at(8, 7));
    alarm.setDismissed(RT_WAKEUP, false);
    assert(alarm.nextTrigger(start_time, RT_WAKEUP) == at(7, 7));
    alarm.wakeup.enabled = false;
    assert(alarm.nextTrigger(start_time, RT_WAKEUP) == 0);
    alarm.wakeup.enabled = true;
    alarm.wakeup.ringOnDevice = false;
    assert(alarm.nextTrigger(start_time, RT_WAKEUP) == 0);
    std::cout << "12 firmware scheduling assertions passed\n";
}
