#pragma once
#include <stdint.h>

class DisplayRefreshPolicy {
    bool initialPending = false, initialComplete = false;
    uint32_t changedAt = 0, lastFull = 0;
    uint32_t interval = 0;
public:
    void synchronized(uint32_t now) {
        if (!initialComplete) { initialPending = true; changedAt = now; }
    }
    void linkChanged(uint32_t now) { if (initialPending) changedAt = now; }
    void setMinutes(unsigned minutes) { interval = minutes <= 1440 ? minutes * 60000UL : 0; }
    bool due(uint32_t now, bool connectionsReady) const {
        return (initialPending && connectionsReady && uint32_t(now - changedAt) >= 2000) ||
            (interval > 0 && uint32_t(now - lastFull) >= interval);
    }
    void refreshed(uint32_t now, bool connectionsReady) {
        lastFull = now;
        if (initialPending && connectionsReady && uint32_t(now - changedAt) >= 2000) {
            initialPending = false;
            initialComplete = true;
        }
    }
};
