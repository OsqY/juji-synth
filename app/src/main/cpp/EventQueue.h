#ifndef JUJIDAW_EVENT_QUEUE_H
#define JUJIDAW_EVENT_QUEUE_H

#include "ScheduledEvent.h"
#include <array>
#include <atomic>

namespace jujidaw {

/**
 * Lock-free single-producer single-consumer event queue.
 * The Kotlin scheduler thread produces; the audio callback consumes.
 */
class EventQueue {
public:
    static constexpr int kCapacity = 4096;

    bool push(const ScheduledEvent& event);
    bool pop(ScheduledEvent& event);
    void clear();
    bool isEmpty() const;
    int available() const;

private:
    std::atomic<int> writePos_{0};
    std::atomic<int> readPos_{0};
    std::array<ScheduledEvent, kCapacity> buffer_;
};

} // namespace jujidaw

#endif // JUJIDAW_EVENT_QUEUE_H
