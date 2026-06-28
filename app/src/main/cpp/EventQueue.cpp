#include "EventQueue.h"

namespace jujidaw {

bool EventQueue::push(const ScheduledEvent& event) {
    int write = writePos_.load(std::memory_order_relaxed);
    int nextWrite = (write + 1) % kCapacity;
    int read = readPos_.load(std::memory_order_acquire);
    if (nextWrite == read) {
        return false; // full
    }
    buffer_[write] = event;
    writePos_.store(nextWrite, std::memory_order_release);
    return true;
}

bool EventQueue::pop(ScheduledEvent& event) {
    int read = readPos_.load(std::memory_order_relaxed);
    int write = writePos_.load(std::memory_order_acquire);
    if (read == write) {
        return false; // empty
    }
    event = buffer_[read];
    readPos_.store((read + 1) % kCapacity, std::memory_order_release);
    return true;
}

void EventQueue::clear() {
    readPos_.store(writePos_.load(std::memory_order_acquire), std::memory_order_release);
}

bool EventQueue::isEmpty() const {
    return readPos_.load(std::memory_order_acquire) ==
           writePos_.load(std::memory_order_acquire);
}

int EventQueue::available() const {
    int write = writePos_.load(std::memory_order_acquire);
    int read = readPos_.load(std::memory_order_acquire);
    int used = (write - read + kCapacity) % kCapacity;
    return kCapacity - 1 - used;
}

} // namespace jujidaw
