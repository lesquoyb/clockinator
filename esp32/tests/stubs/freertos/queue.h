#pragma once
#include <cstring>
#include <vector>
struct FakeQueue {
    std::vector<unsigned char> bytes;
    bool pending = false;
};
#define pdTRUE 1
inline void* xQueueCreate(int, unsigned size) {
    static FakeQueue queue;
    queue.bytes.resize(size);
    queue.pending = false;
    return &queue;
}
inline int xQueueOverwrite(void* handle, const void* item) {
    auto* queue = static_cast<FakeQueue*>(handle);
    std::memcpy(queue->bytes.data(), item, queue->bytes.size());
    queue->pending = true;
    return pdTRUE;
}
inline int xQueueReceive(void* handle, void* item, int) {
    auto* queue = static_cast<FakeQueue*>(handle);
    if (!queue->pending) return 0;
    std::memcpy(item, queue->bytes.data(), queue->bytes.size());
    queue->pending = false;
    return pdTRUE;
}
