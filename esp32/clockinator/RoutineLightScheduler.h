#pragma once
#include "globals.h"

// Independent of sound playback: phone-only slots can still control the lamp.
// No replay on snooze; honor one-cycle skips and suppress duplicate loop ticks.
class RoutineLightScheduler {
    std::map<std::string, time_t> applied;
public:
    template<class Emit>
    void service(const std::map<std::string, Alarm>& routines, time_t now, Emit emit) {
        for (auto it = applied.begin(); it != applied.end();) {
            const auto split = it->first.rfind(':');
            if (routines.count(it->first.substr(0, split)) == 0) it = applied.erase(it);
            else ++it;
        }
        for (const auto& entry : routines) {
            const Alarm& alarm = entry.second;
            if (!alarm.isEnabled) continue;
            for (int i = 0; i < 3; ++i) {
                const auto type = static_cast<RingtoneType>(i);
                const auto& slot = alarm.slot(type);
                if (!slot.enabled || slot.lightAction < 0) continue;
                Recurrence rule = alarm.recurrence;
                if (rule.type == REC_ONCE && slot.epochDay >= 0) rule.anchorEpochDay = slot.epochDay;
                const time_t occurrence = rule.nextOccurrence(now - 60, slot.hour, slot.minute);
                if (occurrence <= 0 || occurrence > now) continue;
                const std::string key = alarm.id + ":" + std::to_string(i);
                if (applied[key] >= occurrence) continue;
                applied[key] = occurrence;
                if (alarm.isDismissed(type) && alarm.dismissedUntil[i] >= occurrence) continue;
                emit(slot.lightAction == 1);
            }
        }
    }
};
