#ifndef JUJIDAW_INSTRUMENT_H
#define JUJIDAW_INSTRUMENT_H

// Abstract interface for instruments that can be assigned to a mixer channel.
// Implementations must be real-time safe in process().
class Instrument {
public:
    virtual ~Instrument() = default;

    // Initialize with sample rate. Called on audio thread or setup thread.
    virtual void init(double sampleRate) = 0;

    // Render one sample. Must be real-time safe.
    virtual float process() = 0;

    // Trigger a note. May be called from UI thread or audio thread.
    virtual void noteOn(int note, int velocity) = 0;
    virtual void noteOff(int note) = 0;

    // Stop all sound immediately.
    virtual void panic() = 0;

    // Return true if instrument is currently producing sound.
    virtual bool isActive() const = 0;
};

#endif // JUJIDAW_INSTRUMENT_H
