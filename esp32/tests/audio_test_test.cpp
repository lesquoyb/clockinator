#include <cassert>
#include <iostream>
#include <stdint.h>
#include <cstring>
#include <cctype>
static uint32_t fakeMillis = 0;
uint32_t millis() { return fakeMillis; }
static int silences = 0;
void i2s_zero_dma_buffer(int) { ++silences; }
#include "../clockinator/audio_test.ino"

time_t start_time = 0, next_alarm = 0;
volatile bool isRinging = false;
volatile RingtoneType firingRingtone = RT_UNKNOWN;
std::string firingAlarmId;
const char* activeRingtoneFile = "";
static std::string reply;
void notifyPhone(const char* json) { reply = json; }

int main() {
    initAudioTest();
    requestAudioTest("", false);
    serviceAudioTest();
    assert(isRinging && reply.find("playing") != std::string::npos);
    fakeMillis = 4999;
    serviceAudioTest();
    assert(isRinging);
    fakeMillis = 5000;
    serviceAudioTest();
    assert(!isRinging && reply.find("idle") != std::string::npos);
    assert(silences == 1);
    requestAudioTest("alarm.wav", false);
    serviceAudioTest();
    assert(std::string(activeRingtoneFile) == "/alarm.wav");
    requestAudioTest("", true);
    serviceAudioTest();
    assert(!isRinging);
    requestAudioTest("../alarm.wav", false);
    serviceAudioTest();
    assert(!isRinging && reply.find("error") != std::string::npos);
    requestAudioTest("missing.wav", false);
    serviceAudioTest();
    assert(!isRinging && reply.find("error") != std::string::npos);
    isRinging = true; // a genuine alarm must not be replaced or stopped by a preview
    requestAudioTest("", false);
    serviceAudioTest();
    assert(isRinging && reply.find("busy") != std::string::npos);
    requestAudioTest("", true);
    serviceAudioTest();
    assert(isRinging);
    isRinging = false;
    requestAudioTest("", false);
    serviceAudioTest();
    next_alarm = 6;
    fakeMillis = 6000;
    serviceAudioTest();
    assert(!isRinging && next_alarm == 6); // leave scheduling intact; loop will fire it
    next_alarm = 0;
    requestAudioTest("", false);
    serviceAudioTest();
    firingRingtone = RT_WAKEUP; // existing manual trigger took over playback
    fakeMillis = 12000;
    serviceAudioTest();
    assert(isRinging); // preview timeout must not silence the real ringtone
    requestAudioTest("", true);
    serviceAudioTest();
    assert(isRinging);
    std::cout << "Audio preview timeout, stop, files and alarm priority passed\n";
}
