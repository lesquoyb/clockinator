// clock
time_t start_time = 0;
time_t next_alarm = 0;


void setup() {

    // Speaker 
    setupI2S();

    // Display
    initDisplay();

    // Server
    initBluetoothServer();
}


// global variable ensuring the screen and alarms are only refreshed when we reach a new minute
time_t last_update; 
void loop() {

    time_t now = start_time + millis()/1000;
    // Only update clock and trigger alarms every minute
    if (now/60 - last_update/60 >= 1){
        // Refresh clock
        drawClock();

        if (next_alarm > 0 && now >= next_alarm){
            playAlarm();
            next_alarm = 0; // reset the alarm
        }
        last_update = now;
    }

    delay(1000); // 1s between loops is enough for what we do

}







