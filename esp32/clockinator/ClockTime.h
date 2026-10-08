#pragma once
#include <stdint.h>

// A bad packet must not reset a clock that was already synchronized.
inline bool clockBaseFromSync(int64_t timestamp, long utcOffset, uint32_t uptimeSeconds,
                              int64_t& base) {
    if (timestamp < 946684800LL || timestamp >= 4102444800LL ||
        utcOffset < -50400 || utcOffset > 50400) return false;
    base = timestamp + utcOffset - uptimeSeconds;
    return true;
}
