#include "globals.h"
#include <LittleFS.h>
#include <math.h>

/*
 * sound.ino
 * I2S driver setup, WAV file playback from LittleFS, and fallback melody.
 *
 * I2S_PORT is defined in globals.h.
 * activeRingtoneFile is defined in clockinator.ino (extern in globals.h).
 */

#define I2S_BCLK    26
#define I2S_LRC     25
#define I2S_DOUT    27
#define SAMPLE_RATE 44100

/* -------------------------------------------------------------------------
 * I2S driver initialisation
 * -------------------------------------------------------------------------*/
void setupI2S() {
    i2s_config_t config = {
        .mode                 = (i2s_mode_t)(I2S_MODE_MASTER | I2S_MODE_TX),
        .sample_rate          = SAMPLE_RATE,
        .bits_per_sample      = I2S_BITS_PER_SAMPLE_16BIT,
        .channel_format       = I2S_CHANNEL_FMT_RIGHT_LEFT,
        .communication_format = I2S_COMM_FORMAT_I2S_MSB,
        .intr_alloc_flags     = 0,
        .dma_buf_count        = 8,
        .dma_buf_len          = 64,
        .use_apll             = false,
        .tx_desc_auto_clear   = true,
        .fixed_mclk           = 0
    };
    i2s_pin_config_t pins = {
        .bck_io_num   = I2S_BCLK,
        .ws_io_num    = I2S_LRC,
        .data_out_num = I2S_DOUT,
        .data_in_num  = I2S_PIN_NO_CHANGE
    };
    i2s_driver_install(I2S_PORT, &config, 0, nullptr);
    i2s_set_pin(I2S_PORT, &pins);
    i2s_zero_dma_buffer(I2S_PORT);
}

/* -------------------------------------------------------------------------
 * Tone generator used by the fallback melody
 * -------------------------------------------------------------------------*/
static void playTone(float frequency, int durationMs) {
    const int samples = SAMPLE_RATE * durationMs / 1000;
    int16_t   buffer[256];
    int       generated = 0;

    while (generated < samples) {
        if (!isRinging) {
            i2s_zero_dma_buffer(I2S_PORT);
            return;
        }
        int count = min(128, samples - generated);
        for (int i = 0; i < count; i++) {
            float   t      = (float)(generated + i) / SAMPLE_RATE;
            int16_t sample = (int16_t)(10000 * sinf(2.0f * PI * frequency * t));
            buffer[2 * i]     = sample;
            buffer[2 * i + 1] = sample;
        }
        size_t written;
        i2s_write(I2S_PORT, buffer, count * 2 * sizeof(int16_t), &written, portMAX_DELAY);
        generated += count;
    }
}

/* Built-in fallback: Do-Mi-Sol-Mi-Do */
static void playMelody() {
    if (!isRinging) return;
    playTone(523.25f, 300); if (!isRinging) return; delay(100);
    playTone(659.25f, 300); if (!isRinging) return; delay(100);
    playTone(783.99f, 500); if (!isRinging) return; delay(100);
    playTone(659.25f, 300); if (!isRinging) return; delay(100);
    playTone(523.25f, 300);
}

/* -------------------------------------------------------------------------
 * WAV playback from LittleFS
 *
 * Supports PCM WAV files:
 *   8-bit unsigned or 16-bit signed
 *   mono or stereo
 *   any sample rate (nearest-neighbour resampled to SAMPLE_RATE)
 *
 * Returns true on success, false on missing file or unsupported format.
 * -------------------------------------------------------------------------*/
static uint16_t readU16(File& f) {
    uint8_t b[2];
    f.read(b, 2);
    return (uint16_t)b[0] | ((uint16_t)b[1] << 8);
}

static uint32_t readU32(File& f) {
    uint8_t b[4];
    f.read(b, 4);
    return (uint32_t)b[0] | ((uint32_t)b[1] << 8) |
           ((uint32_t)b[2] << 16) | ((uint32_t)b[3] << 24);
}

bool playWavFile(const char* path) {
    File f = LittleFS.open(path, "r");
    if (!f) return false;

    /* RIFF header */
    char riff[4];
    f.read((uint8_t*)riff, 4);
    if (strncmp(riff, "RIFF", 4) != 0) { f.close(); return false; }
    readU32(f); /* chunk size, ignored */
    char wave[4];
    f.read((uint8_t*)wave, 4);
    if (strncmp(wave, "WAVE", 4) != 0) { f.close(); return false; }

    /* Scan for "fmt " sub-chunk */
    uint16_t audioFmt     = 0;
    uint16_t numChannels  = 0;
    uint32_t sampleRate   = 0;
    uint16_t bitsPerSample = 0;

    while (f.available()) {
        char     id[4];
        f.read((uint8_t*)id, 4);
        uint32_t chunkSize = readU32(f);
        if (strncmp(id, "fmt ", 4) == 0) {
            audioFmt      = readU16(f);
            numChannels   = readU16(f);
            sampleRate    = readU32(f);
            readU32(f);  /* byte rate */
            readU16(f);  /* block align */
            bitsPerSample = readU16(f);
            if (chunkSize > 16) f.seek(f.position() + chunkSize - 16);
            break;
        }
        f.seek(f.position() + chunkSize);
    }

    /* Only PCM (audioFmt == 1) supported */
    if (audioFmt != 1 || numChannels == 0 || bitsPerSample == 0) {
        f.close(); return false;
    }

    /* Find "data" sub-chunk */
    uint32_t dataSize = 0;
    while (f.available()) {
        char     id[4];
        f.read((uint8_t*)id, 4);
        uint32_t chunkSize = readU32(f);
        if (strncmp(id, "data", 4) == 0) { dataSize = chunkSize; break; }
        f.seek(f.position() + chunkSize);
    }
    if (dataSize == 0) { f.close(); return false; }

    /* Stream audio to I2S with nearest-neighbour resampling */
    const int      bytesPerSample = bitsPerSample / 8;
    const int      frameSize      = bytesPerSample * numChannels;
    const float    resampleRatio  = (float)sampleRate / SAMPLE_RATE;
    const uint32_t totalFrames    = dataSize / (uint32_t)frameSize;

    uint8_t  rawFrame[8];
    int16_t  outBuf[256];
    int      outIdx       = 0;
    uint32_t lastSrcFrame = 0xFFFFFFFFu;
    int16_t  lastL = 0, lastR = 0;

    for (uint32_t dstFrame = 0;
         dstFrame < (uint32_t)((float)totalFrames / resampleRatio);
         dstFrame++) {
        if (!isRinging) {
            f.close();
            i2s_zero_dma_buffer(I2S_PORT);
            return false;
        }

        uint32_t srcFrame = (uint32_t)((float)dstFrame * resampleRatio);
        if (srcFrame >= totalFrames) break;

        if (srcFrame != lastSrcFrame) {
            int32_t advance = (int32_t)srcFrame - (int32_t)lastSrcFrame - 1;
            if (advance != 0) f.seek(f.position() + advance * frameSize);
            f.read(rawFrame, (size_t)frameSize);
            lastSrcFrame = srcFrame;

            if (bitsPerSample == 8) {
                lastL = ((int16_t)rawFrame[0] - 128) * 256;
                lastR = (numChannels > 1) ? (((int16_t)rawFrame[1] - 128) * 256) : lastL;
            } else {
                lastL = (int16_t)((rawFrame[1] << 8) | rawFrame[0]);
                lastR = (numChannels > 1)
                    ? (int16_t)((rawFrame[3] << 8) | rawFrame[2])
                    : lastL;
            }
        }

        outBuf[outIdx++] = lastL;
        outBuf[outIdx++] = lastR;

        if (outIdx >= 256) {
            if (!isRinging) {
                f.close();
                i2s_zero_dma_buffer(I2S_PORT);
                return false;
            }
            size_t written;
            i2s_write(I2S_PORT, outBuf, sizeof(outBuf), &written, portMAX_DELAY);
            outIdx = 0;
        }
    }

    if (outIdx > 0) {
        if (!isRinging) {
            f.close();
            i2s_zero_dma_buffer(I2S_PORT);
            return false;
        }
        size_t written;
        i2s_write(I2S_PORT, outBuf, (size_t)outIdx * sizeof(int16_t), &written, portMAX_DELAY);
    }

    f.close();
    return true;
}

/* -------------------------------------------------------------------------
 * playAlarm
 * Entry point called by the main loop while isRinging is true.
 * Uses the WAV file pointed to by activeRingtoneFile if available,
 * otherwise falls back to the built-in melody.
 * -------------------------------------------------------------------------*/
void playAlarm() {
    if (!isRinging) return;
    if (strlen(activeRingtoneFile) > 0 && playWavFile(activeRingtoneFile)) {
        return;
    }
    if (isRinging) playMelody();
}
