#include "globals.h"
#include <LittleFS.h>
#include <freertos/queue.h>
#include <ctype.h>

struct AudioTestRequest { bool stop; char filename[96]; };
static QueueHandle_t audioTestQueue;
static bool audioTestActive = false;
static uint32_t audioTestStarted = 0;
static std::string audioTestFile;

void initAudioTest() { audioTestQueue = xQueueCreate(1, sizeof(AudioTestRequest)); }

void requestAudioTest(const char* filename, bool stop) {
    if (!audioTestQueue) return;
    AudioTestRequest request{};
    request.stop = stop;
    if (filename && strlen(filename) < sizeof(request.filename))
        strncpy(request.filename, filename, sizeof(request.filename) - 1);
    else if (filename && strlen(filename) > 0) {
        notifyPhone("{\"action\":\"AUDIO_TEST_STATUS\",\"status\":\"error\"}");
        return;
    }
    xQueueOverwrite(audioTestQueue, &request);
}

void cancelAudioTest() {
    if (!audioTestActive) return;
    audioTestActive = false;
    isRinging = false;
    activeRingtoneFile = "";
    i2s_zero_dma_buffer(I2S_PORT);
    notifyPhone("{\"action\":\"AUDIO_TEST_STATUS\",\"status\":\"idle\"}");
}

void serviceAudioTest() {
    // A real ringtone started via the existing PUSH_RINGTONE command also takes
    // ownership of playback. Its lifetime must not be capped by the preview timer.
    if (audioTestActive && firingRingtone != RT_UNKNOWN) {
        audioTestActive = false;
        notifyPhone("{\"action\":\"AUDIO_TEST_STATUS\",\"status\":\"idle\"}");
    }
    AudioTestRequest request{};
    if (audioTestQueue && xQueueReceive(audioTestQueue, &request, 0) == pdTRUE) {
        if (request.stop) cancelAudioTest();
        else if (isRinging) {
            notifyPhone(audioTestActive
                ? "{\"action\":\"AUDIO_TEST_STATUS\",\"status\":\"playing\"}"
                : "{\"action\":\"AUDIO_TEST_STATUS\",\"status\":\"busy\"}");
        } else {
            bool valid = true;
            for (const char* p = request.filename; *p; ++p)
                if (!isalnum((unsigned char)*p) && *p != '_' && *p != '-' && *p != '.') valid = false;
            audioTestFile = strlen(request.filename) ? std::string("/") + request.filename : "";
            if (!valid || (!audioTestFile.empty() && !LittleFS.exists(audioTestFile.c_str()))) {
                notifyPhone("{\"action\":\"AUDIO_TEST_STATUS\",\"status\":\"error\"}");
                return;
            }
            // Own the test path independently of the alarm map, which SYNC replaces.
            activeRingtoneFile = audioTestFile.c_str();
            firingAlarmId.clear();
            firingRingtone = RT_UNKNOWN;
            audioTestActive = true;
            audioTestStarted = millis();
            isRinging = true;
            notifyPhone("{\"action\":\"AUDIO_TEST_STATUS\",\"status\":\"playing\"}");
        }
    }
    const time_t now = start_time + time_t(millis() / 1000);
    if (audioTestActive && (!isRinging || uint32_t(millis() - audioTestStarted) >= 5000 ||
                           (next_alarm > 0 && now >= next_alarm)))
        cancelAudioTest();
}
