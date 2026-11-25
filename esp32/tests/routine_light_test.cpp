#include <cassert>
#include <ctime>
#include <vector>
unsigned long millis() { return 0; }
#ifdef _WIN32
static struct tm* localtime_r(const time_t* value, struct tm* result) {
    return localtime_s(result, value) == 0 ? result : nullptr;
}
#endif
#include "../clockinator/alarm.ino"
#include "../clockinator/RoutineLightScheduler.h"
time_t start_time = 0;

int main() {
    const time_t day = 20787 * 86400LL;
    Alarm alarm;
    alarm.id = "routine";
    alarm.recurrence.type = REC_ONCE;
    alarm.recurrence.anchorEpochDay = 20787;
    alarm.lockPhone.hour = 22;
    alarm.lockPhone.epochDay = 20786;
    alarm.lockPhone.ringOnDevice = false;
    alarm.lockPhone.lightAction = 1;
    alarm.bedtime.hour = 23;
    alarm.bedtime.epochDay = 20786;
    alarm.bedtime.lightAction = 0;
    std::map<std::string, Alarm> routines{{alarm.id, alarm}};
    RoutineLightScheduler scheduler;
    std::vector<bool> emitted;
    auto emit = [&](bool on) { emitted.push_back(on); };
    const time_t evening = day - 2 * 3600;
    scheduler.service(routines, evening - 1, emit);
    assert(emitted.empty());
    scheduler.service(routines, evening, emit);
    scheduler.service(routines, evening + 1, emit);
    assert(emitted == std::vector<bool>{true});
    scheduler.service(routines, evening + 3600, emit);
    assert((emitted == std::vector<bool>{true, false}));
    scheduler.service(routines, evening + 86400, emit);
    assert(emitted.size() == 2); // no repetition tomorrow
    RoutineLightScheduler skipped;
    routines[alarm.id].dismissedMask = 1;
    routines[alarm.id].dismissedUntil[0] = evening;
    skipped.service(routines, evening, emit);
    assert(emitted.size() == 2);
    routines[alarm.id].dismissedMask = 0;
    skipped.service(routines, evening + 1, emit);
    assert(emitted.size() == 2); // clearing a skip must not replay its light action
    routines[alarm.id].isEnabled = false;
    skipped.service(routines, evening, emit);
    assert(emitted.size() == 2);
}
