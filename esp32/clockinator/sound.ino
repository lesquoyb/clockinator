#include "driver/i2s.h"
#include <math.h>
#define I2S_PORT I2S_NUM_0

#define I2S_BCLK 26
#define I2S_LRC  25
#define I2S_DOUT 27

#define SAMPLE_RATE 44100


void setupI2S(){
    
  i2s_config_t config = {
      .mode = (i2s_mode_t)(
          I2S_MODE_MASTER |
          I2S_MODE_TX
      ),

      .sample_rate = SAMPLE_RATE,

      .bits_per_sample =
          I2S_BITS_PER_SAMPLE_16BIT,

      .channel_format =
          I2S_CHANNEL_FMT_RIGHT_LEFT,

      .communication_format =
          I2S_COMM_FORMAT_I2S_MSB,

      .intr_alloc_flags = 0,

      .dma_buf_count = 8,
      .dma_buf_len = 64,

      .use_apll = false,
      .tx_desc_auto_clear = true,
      .fixed_mclk = 0
  };

  i2s_pin_config_t pins = {
      .bck_io_num = I2S_BCLK,
      .ws_io_num = I2S_LRC,
      .data_out_num = I2S_DOUT,
      .data_in_num = I2S_PIN_NO_CHANGE
  };

  i2s_driver_install(
      I2S_PORT,
      &config,
      0,
      nullptr
  );

  i2s_set_pin(I2S_PORT, &pins);

  i2s_zero_dma_buffer(I2S_PORT);
}

void playTone(float frequency, int durationMs) {

  const int samples = SAMPLE_RATE * durationMs / 1000;

  int16_t buffer[256];

  int generated = 0;

  while (generated < samples) {

    int count = min(128, samples - generated);

    for (int i = 0; i < count; i++) {

        float t = (float)(generated + i) / SAMPLE_RATE;

        int16_t sample = 10000 * sinf(2.0 * PI * frequency * t);

        buffer[2 * i]     = sample;
        buffer[2 * i + 1] = sample;
    }

    size_t bytesWritten;

    i2s_write(
        I2S_PORT,
        buffer,
        count * 2 * sizeof(int16_t),
        &bytesWritten,
        portMAX_DELAY
    );

    generated += count;
  }
}

void playAlarm(){
  
  playTone(523.25, 300);  // Do
  delay(100);

  playTone(659.25, 300);  // Mi
  delay(100);

  playTone(783.99, 500);  // Sol
  delay(100);

  playTone(659.25, 300);  // Mi
  delay(100);

  playTone(523.25, 300);  // Do
  delay(100);
}

