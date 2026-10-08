#include <cassert>
#include <iostream>
#include "../clockinator/AudioVolume.h"
#include "../button_test/DebouncedButton.h"

int main() {
    // Every level attenuates PCM safely, preserves polarity, and increases amplitude.
    int previous = 0;
    for (int level = 1; level <= 5; ++level) {
        const int positive = AudioVolume::scale(32767, level);
        const int negative = AudioVolume::scale(-32768, level);
        assert(positive > previous && positive <= 32767);
        assert(negative < 0 && negative >= -32768);
        assert(AudioVolume::scale(0, level) == 0);
        previous = positive;
    }
    assert(AudioVolume::scale(32767, 5) == 32767);
    assert(AudioVolume::scale(-32768, 5) == -32768);
    assert(AudioVolume::scale(10000, 0) == AudioVolume::scale(10000, 1));
    assert(AudioVolume::scale(10000, 99) == AudioVolume::scale(10000, 5));

    DebouncedButton button;
    assert(button.update(true, 10) == 0);
    assert(button.update(false, 15) == 0); // mechanical bounce
    assert(button.update(true, 20) == 0);
    assert(button.update(true, 44) == 0);
    assert(button.update(true, 45) == 1);
    assert(button.update(true, 500) == 0); // held key must not fire repeatedly
    assert(button.update(false, 510) == 0);
    assert(button.update(false, 535) == -1);
    assert(button.update(false, 900) == 0);
    DebouncedButton wrapped;
    assert(wrapped.update(true, UINT32_MAX - 10) == 0);
    assert(wrapped.update(true, 13) == 0);
    assert(wrapped.update(true, 14) == 1); // millis() wraps after ~49 days
    std::cout << "Audio levels and button debounce passed\n";
}
