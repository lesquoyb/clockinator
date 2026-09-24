#include <GxEPD2_BW.h>
#include <Fonts/FreeMonoBold12pt7b.h>


// The screen
#define EPD_CS   5
#define EPD_DC   16
#define EPD_RST  17
#define EPD_BUSY 4

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

    do {
        display.fillScreen(GxEPD_WHITE);
        display.setTextColor(GxEPD_BLACK);
        display.setFont(&FreeMonoBold12pt7b);

        display.setCursor(10, 50);
        display.print(line1);

        display.setCursor(10, 90);
        display.print(line2);

    } while (display.nextPage());
}

void drawClock() {

  time_t now = start_time + (millis() / 1000);

  struct tm timeinfo;
  localtime_r(&now, &timeinfo);

  char buffer[6];
  strftime(buffer, sizeof(buffer), "%H:%M", &timeinfo);

  // Partial window
  display.setPartialWindow(0, 0, display.width(), display.height());

  display.firstPage();

  do {
    display.fillScreen(GxEPD_WHITE);

    display.setTextColor(GxEPD_BLACK);
    display.setFont(NULL);
    display.setTextSize(13);

    display.setCursor(20, 50);
    display.print(buffer);

    display.setTextSize(4);
    // display.setCursor(20, 160);
    // display.print(next_alarm);
    display.setCursor(20, 180);
    char alarm_buff[13];
    struct tm alarm_timeinfo;
    localtime_r(&next_alarm, &alarm_timeinfo);
    strftime(alarm_buff, sizeof(alarm_buff), "Alarm: %H:%M", &alarm_timeinfo);
    display.print(alarm_buff);

  } while (display.nextPage());
}


