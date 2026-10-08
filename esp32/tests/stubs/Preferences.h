#pragma once
// In-memory NVS stand-in for the volume mailbox test; no hardware API is exercised.
namespace FakePreferences {
inline int persistedLevel = 3;
inline int writes = 0;
}
class Preferences {
public:
    bool begin(const char*, bool) { return true; }
    int getInt(const char*, int) { return FakePreferences::persistedLevel; }
    void putInt(const char*, int level) {
        FakePreferences::persistedLevel = level;
        ++FakePreferences::writes;
    }
    void end() {}
};
