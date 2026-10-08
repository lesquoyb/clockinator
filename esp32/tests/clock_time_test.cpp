#include <cassert>
#include "../clockinator/ClockTime.h"

int main() {
    int64_t base = 123;
    assert(!clockBaseFromSync(0, 0, 15, base) && base == 123);
    assert(!clockBaseFromSync(-1, 0, 15, base) && base == 123);
    assert(!clockBaseFromSync(4102444800LL, 0, 15, base) && base == 123);
    assert(!clockBaseFromSync(1700000000LL, 50401, 15, base) && base == 123);
    assert(clockBaseFromSync(1700000000LL, 25200, 150, base));
    assert(base + 150 == 1700025200LL); // displayed local time immediately
    assert(base + 210 == 1700025260LL); // clock advances independently afterwards
    assert(clockBaseFromSync(1700000000LL, -18000, 600, base));
    assert(base + 600 == 1699982000LL);
}
