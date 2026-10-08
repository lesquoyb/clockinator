#include <cassert>
#include <iostream>
#include <string>
#include "../clockinator/volume.ino"

static std::string reply;
static int notifications = 0, redraws = 0;
void notifyPhone(const char* json) { reply = json; ++notifications; }
void drawClock() { ++redraws; }

int main() {
    initAlarmVolume();
    assert(getAlarmVolume() == 3);
    assert(!requestAlarmVolume(0));
    assert(!requestAlarmVolume(6));
    serviceAlarmVolume();
    assert(notifications == 0);
    assert(requestAlarmVolume(1));
    assert(requestAlarmVolume(5)); // rapid changes must leave the latest value
    assert(getAlarmVolume() == 3); // callback does not write NVS or touch the screen
    serviceAlarmVolume();
    assert(getAlarmVolume() == 5);
    assert(reply == "{\"action\":\"VOLUME_STATUS\",\"level\":5}");
    assert(FakePreferences::writes == 1);
    assert(redraws == 0);
    refreshVolumeDisplay();
    refreshVolumeDisplay();
    assert(redraws == 1);
    assert(requestAlarmVolume(5));
    serviceAlarmVolume();
    assert(notifications == 2); // retry is confirmed even if already at that level
    assert(FakePreferences::writes == 1); // avoid unnecessary flash wear
    assert(requestAlarmVolume(2));
    assert(!requestAlarmVolume(-1)); // malformed request must not erase a valid one
    serviceAlarmVolume();
    assert(getAlarmVolume() == 2 && FakePreferences::persistedLevel == 2);
    volumeLevel.store(3);
    initAlarmVolume();
    assert(getAlarmVolume() == 2); // persisted value is reloaded on boot
    FakePreferences::persistedLevel = 0;
    initAlarmVolume();
    assert(getAlarmVolume() == 1); // corrupted preferences cannot enable mute
    std::cout << "Volume requests, confirmations and persistence passed\n";
}
