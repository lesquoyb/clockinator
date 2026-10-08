#include "globals.h"
#include "AudioVolume.h"
#include <atomic>
#include <stdio.h>
#include <Preferences.h>

static std::atomic<int> volumeLevel{AudioVolume::DEFAULT_LEVEL};
// A latest-value mailbox cannot overflow when the phone changes levels rapidly.
static std::atomic<int> requestedVolume{0};
static bool volumeDisplayDirty = false;

int getAlarmVolume() { return volumeLevel.load(); }

void initAlarmVolume() {
    Preferences prefs;
    prefs.begin("audio", true);
    volumeLevel.store(AudioVolume::clamp(prefs.getInt("volume", AudioVolume::DEFAULT_LEVEL)));
    prefs.end();
}

bool requestAlarmVolume(int level) {
    if (level < AudioVolume::MIN_LEVEL || level > AudioVolume::MAX_LEVEL) return false;
    requestedVolume.store(level);
    return true;
}

void notifyVolumeStatus() {
    char json[64];
    snprintf(json, sizeof(json), "{\"action\":\"VOLUME_STATUS\",\"level\":%d}", getAlarmVolume());
    notifyPhone(json);
}

// Main core only, including between audio buffers: no display work in BLE callbacks.
void serviceAlarmVolume() {
    const int requested = requestedVolume.exchange(0);
    if (!requested) return;
    if (requested != getAlarmVolume()) {
        volumeLevel.store(requested);
        Preferences prefs;
        prefs.begin("audio", false);
        prefs.putInt("volume", requested);
        prefs.end();
        volumeDisplayDirty = true;
    }
    notifyVolumeStatus();
}

void refreshVolumeDisplay() {
    if (!volumeDisplayDirty) return;
    volumeDisplayDirty = false;
    drawClock();
}
