#pragma once
#include <stdint.h>

class DebouncedButton {
    bool candidate = false, pressed = false;
    uint32_t changedAt = 0;
public:
    // 1 = press edge, -1 = release edge, 0 = no stable change.
    int update(bool rawPressed, uint32_t now) {
        if (rawPressed != candidate) { candidate = rawPressed; changedAt = now; }
        if (candidate == pressed || uint32_t(now - changedAt) < 25) return 0;
        pressed = candidate;
        return pressed ? 1 : -1;
    }
};
