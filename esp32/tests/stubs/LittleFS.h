#pragma once
#include <string>
struct FakeLittleFS {
    bool exists(const char* path) { return std::string(path) == "/alarm.wav"; }
};
inline FakeLittleFS LittleFS;
