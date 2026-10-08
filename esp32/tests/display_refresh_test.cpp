#include <cassert>
#include "../clockinator/DisplayRefreshPolicy.h"

int main() {
    DisplayRefreshPolicy policy;
    policy.refreshed(100, false); // hardware's initial clear isn't the post-sync clean
    assert(!policy.due(600100, true)); // periodic cleaning defaults to disabled
    policy.synchronized(1000);
    assert(!policy.due(2999, true));
    assert(!policy.due(5000, false)); // Wi-Fi still initializing
    policy.linkChanged(5000);
    assert(!policy.due(6999, true));
    assert(policy.due(7000, true));
    policy.refreshed(7000, true);
    policy.synchronized(10000); // later synchronization doesn't clean again
    policy.linkChanged(11000);
    assert(!policy.due(999999, true));
    policy.setMinutes(10);
    assert(!policy.due(607000 - 1, true));
    assert(policy.due(607000, true));
    policy.refreshed(607000, true);
    policy.setMinutes(0);
    assert(!policy.due(2000000, true));
    DisplayRefreshPolicy wrap;
    wrap.synchronized(0xfffffff0U);
    assert(!wrap.due(1983, true));
    assert(wrap.due(1984, true)); // millis() rollover preserves the quiet period
}
