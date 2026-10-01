#include "globals.h"
#include <GxEPD2_BW.h>
#include <Fonts/FreeMonoBold12pt7b.h>


// The screen pins
#define EPD_CS   5
#define EPD_DC   16
#define EPD_RST  17
#define EPD_BUSY 4

// Text properties
#define SHOW_TEXT_SIZE  1
#define CLOCK_SIZE      13
#define ALARM_TEXT_SIZE 4

// This bool is used to check if we alternated between drawing clock to raw messages by saving false in the show function and true in the drawClock
// This can be used to force a full screen redraw only when needed.
bool was_previously_drawing = false; 

// The variable to manipulate to interact with the screen
GxEPD2_BW<GxEPD2_370_GDEY037T03, GxEPD2_370_GDEY037T03::HEIGHT> display(
  GxEPD2_370_GDEY037T03(
      EPD_CS,
      EPD_DC,
      EPD_RST,
      EPD_BUSY
  )
);

void initDisplay(){
  
  display.init(115200);
  display.setRotation(1); // Landscape

  show("BOOT", "starting");

}


void show(const char* line1){
  show(line1, "");
}

void show(const char *line1, const char *line2) {
    display.setFullWindow();
    display.firstPage();
    display.setTextSize(SHOW_TEXT_SIZE);
    do {
        display.fillScreen(GxEPD_WHITE);
        display.setTextColor(GxEPD_BLACK);
        display.setFont(&FreeMonoBold12pt7b);

        display.setCursor(10, 50);
        display.print(line1);

        display.setCursor(10, 90);
        display.print(line2);

    } while (display.nextPage());

    was_previously_drawing = false;
}

void drawClock() {

  time_t now = start_time + (millis() / 1000);

  struct tm timeinfo;
  localtime_r(&now, &timeinfo);

  char buffer[6];
  strftime(buffer, sizeof(buffer), "%H:%M", &timeinfo);

  // If previous use of the display was to draw a raw message then we redraw everything
  // otherwise partial redraw
  if ( ! was_previously_drawing){
    display.setFullWindow();
  }
  else {
    display.setPartialWindow(0, 0, display.width(), display.height());
  }

  display.firstPage();

  do {

    // General screen settings
    display.fillScreen(GxEPD_WHITE);
    display.setTextColor(GxEPD_BLACK);
    display.setFont(NULL);

    // Displaying current time
    display.setTextSize(CLOCK_SIZE);
    display.setCursor(20, 50);
    display.print(buffer);

    // Now writing the next alarm at the bottom
    display.setTextSize(ALARM_TEXT_SIZE);
    display.setCursor(20, 180);

    if (next_alarm > 0) {
      char alarm_buff[13];
      struct tm alarm_timeinfo;
      localtime_r(&next_alarm, &alarm_timeinfo);
      strftime(alarm_buff, sizeof(alarm_buff), "Alarm: %H:%M", &alarm_timeinfo);
      display.print(alarm_buff);
    }
    else {
      display.print("No alarm set");
    }

  } while (display.nextPage());

  was_previously_drawing = true;
}


