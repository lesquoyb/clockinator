#pragma once
#include <stdint.h>

namespace AudioVolume {
constexpr int MIN_LEVEL = 1;
constexpr int MAX_LEVEL = 5;
constexpr int DEFAULT_LEVEL = 3;
inline int clamp(int level) {
    return level < MIN_LEVEL ? MIN_LEVEL : (level > MAX_LEVEL ? MAX_LEVEL : level);
}
// Amplitude, not perceived loudness; none of the five settings is mute.
inline int16_t scale(int16_t sample, int level) {
    const int gains[] = {100, 200, 350, 600, 1000};
    return static_cast<int16_t>(static_cast<int32_t>(sample) * gains[clamp(level) - 1] / 1000);
}
}
