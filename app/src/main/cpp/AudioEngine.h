#ifndef JUJIDAW_AUDIOENGINE_H
#define JUJIDAW_AUDIOENGINE_H

#include "Instrument.h"
#include "SynthParams.h"
#include "MixerChannel.h"
#include "Bus.h"
#include "MasterBus.h"
#include "MixerCommand.h"
#include "SamplerInstrument.h"
#include "SynthInstrument.h"
#include "AudioRecorder.h"
#include "TimeStretchWorker.h"
#include "ScheduledEvent.h"
#include "EventQueue.h"
#include "Transport.h"
#include "AudioClipPlayer.h"
#include "Filter.h"
#include <array>
#include <memory>
#include <vector>
#include <atomic>
#include <cmath>
#include <unordered_map>
#include <string>

/**
 * Main audio engine / mixer for the DAW.
 *
 * - Owns the mixer graph: 16 channels, 2 send buses, 1 master bus.
 * - Channel 0 hosts a SynthInstrument (subtractive synth).
 * - Channel 1 hosts the SamplerInstrument.
 * - Receives scheduled note events and mixer commands from the UI thread.
 * - Runs on the audio thread via Oboe callback.
 */
class AudioEngine {
public:
    AudioEngine();
    ~AudioEngine() = default;

    void init(double sampleRate);

    /** Process audio callback: fill buffer with samples */
    int processAudio(float* outputBuffer, int numFrames);

    // Synth note/panic delegations (used by the JNI bridge and the legacy
    // sequencer callback path). These forward to the SynthInstrument on
    // channel 0.
    void noteOn(int note, int velocity) { synthInstrument_->noteOn(note, velocity); }
    void noteOff(int note) { synthInstrument_->noteOff(note); }
    void panic() { synthInstrument_->panic(); }

    // ---- Synth parameter updates (delegated to SynthInstrument on channel 0) ----

    void setParams(const SynthParams& params) { synthInstrument_->setParams(params); }

    // ---- Per-pad synth pool (multi-timbral) ----
    // The sampler has two 16-pad banks. Each of its 32 pads needs an
    // independent synth state; sharing index 0 with index 16 makes Bank B
    // overwrite Bank A's sound.
    static constexpr int PAD_SYNTH_COUNT = NUM_PADS;
    /** Lazily create / retrieve the SynthInstrument for [padIndex] (0..31). */
    SynthInstrument* getPadSynth(int padIndex);
    /** Return an already-created pad synth without allocating on the audio thread. */
    SynthInstrument* getExistingPadSynth(int padIndex) const;
    /** Apply a full SynthParams snapshot to a per-pad synth. */
    void applyPadSynthState(int padIndex, const SynthParams& params);
    void setAllParamsFromArray(const float* values, int count) { synthInstrument_->setAllParamsFromArray(values, count); }
    static constexpr int SYNTH_PARAM_COUNT = SynthInstrument::SYNTH_PARAM_COUNT;

    void setOsc1Level(float v) { synthInstrument_->setOsc1Level(v); }
    void setOsc2Level(float v) { synthInstrument_->setOsc2Level(v); }
    void setOsc1Waveform(int w) { synthInstrument_->setOsc1Waveform(w); }
    void setOsc2Waveform(int w) { synthInstrument_->setOsc2Waveform(w); }
    void setOscDetune(float v) { synthInstrument_->setOscDetune(v); }
    void setSubOscLevel(float v) { synthInstrument_->setSubOscLevel(v); }
    void setNoiseLevel(float v) { synthInstrument_->setNoiseLevel(v); }
    void setOscMix(float v) { synthInstrument_->setOscMix(v); }
    void setOscSync(bool s) { synthInstrument_->setOscSync(s); }

    void setFilterCutoff(float v) { synthInstrument_->setFilterCutoff(v); }
    void setFilterResonance(float v) { synthInstrument_->setFilterResonance(v); }
    void setFilterMode(int m) { synthInstrument_->setFilterMode(m); }
    void setFilterEnvAmount(float v) { synthInstrument_->setFilterEnvAmount(v); }

    void setAmpAttack(float v) { synthInstrument_->setAmpAttack(v); }
    void setAmpDecay(float v) { synthInstrument_->setAmpDecay(v); }
    void setAmpSustain(float v) { synthInstrument_->setAmpSustain(v); }
    void setAmpRelease(float v) { synthInstrument_->setAmpRelease(v); }
    void setFilterAttack(float v) { synthInstrument_->setFilterAttack(v); }
    void setFilterDecay(float v) { synthInstrument_->setFilterDecay(v); }
    void setFilterSustain(float v) { synthInstrument_->setFilterSustain(v); }
    void setFilterRelease(float v) { synthInstrument_->setFilterRelease(v); }

    void setLfo1Rate(float v) { synthInstrument_->setLfo1Rate(v); }
    void setLfo1Depth(float v) { synthInstrument_->setLfo1Depth(v); }
    void setLfo1Waveform(int w) { synthInstrument_->setLfo1Waveform(w); }
    void setLfo2Rate(float v) { synthInstrument_->setLfo2Rate(v); }
    void setLfo2Depth(float v) { synthInstrument_->setLfo2Depth(v); }
    void setLfo2Waveform(int w) { synthInstrument_->setLfo2Waveform(w); }

    void setReverbMix(float v) { synthInstrument_->setReverbMix(v); }
    void setReverbDecay(float v) { synthInstrument_->setReverbDecay(v); }
    void setDelayMix(float v) { synthInstrument_->setDelayMix(v); }
    void setDelayTime(float v) { synthInstrument_->setDelayTime(v); }
    void setDelayFeedback(float v) { synthInstrument_->setDelayFeedback(v); }
    void setDistortionDrive(float v) { synthInstrument_->setDistortionDrive(v); }
    void setDistortionMix(float v) { synthInstrument_->setDistortionMix(v); }
    void setChorusRate(float v) { synthInstrument_->setChorusRate(v); }
    void setChorusDepth(float v) { synthInstrument_->setChorusDepth(v); }
    void setChorusMix(float v) { synthInstrument_->setChorusMix(v); }
    void setEffectsBypass(bool b) { synthInstrument_->setEffectsBypass(b); }

    void setModulationRoute(int index, const ModulationRoute& route) { synthInstrument_->setModulationRoute(index, route); }

    void setSequencerSteps(const std::array<SequencerStep, SEQUENCER_STEPS>& steps) { synthInstrument_->setSequencerSteps(steps); }
    void setSequencerTempo(float bpm) { synthInstrument_->setSequencerTempo(bpm); }
    void setSequencerPlaying(bool play) { synthInstrument_->setSequencerPlaying(play); }
    int getSequencerStep() const { return synthInstrument_->getSequencerStep(); }
    void setSequencerLooping(bool loop) { synthInstrument_->setSequencerLooping(loop); }
    bool getSequencerLooping() const { return synthInstrument_->getSequencerLooping(); }

    void setMasterVolume(float v) { synthInstrument_->setMasterVolume(v); }
    void setPitchBend(float v) { synthInstrument_->setPitchBend(v); }
    void setModWheel(float v) { synthInstrument_->setModWheel(v); }
    void setSequencerEnabled(bool enabled) { synthInstrument_->setSequencerEnabled(enabled); }

    SynthParams getCurrentParams() const { return synthInstrument_->getCurrentParams(); }

    /** Copy latest N output samples into buffer for oscilloscope display. */
    static constexpr int SCOPE_SIZE = 512;
    void getWaveform(float* out, int maxSize) const;

    /** Reset delay and reverb buffers (called from JNI on preset load). */
    void setPendingEffectsReset() { synthInstrument_->setPendingEffectsReset(); }

    // ---- Mixer graph control ----
    static constexpr int MAX_TRACKS = 16;
    static constexpr int NUM_BUSES = 2;

    void pushMixerCommand(const MixerCommand& cmd);
    MixerChannel& getChannel(int index) { return channels_[index]; }
    Bus& getBus(int index) { return buses_[index]; }
    MasterBus& getMasterBus() { return masterBus_; }
    SamplerInstrument& getSampler() { return *sampler_; }
    SynthInstrument& getSynth() { return *synthInstrument_; }
    float getMasterPeak() const { return masterPeak_.load(std::memory_order_relaxed); }
    AudioRecorder& getRecorder() { return recorder_; }
    TimeStretchWorker& getTimeStretchWorker() { return timeStretchWorker_; }
    jujidaw::Transport& getTransport() { return transport_; }
    jujidaw::EventQueue& getEventQueue() { return eventQueue_; }

    // ---- Mixer state getters (thread-safe for UI polling) ----
    float getChannelFaderDb(int track) const;
    float getChannelPan(int track) const;
    bool isChannelMute(int track) const;
    bool isChannelSolo(int track) const;
    bool isChannelArm(int track) const;
    float getSendLevel(int track, int bus) const;
    float getChannelLevel(int track) const;
    float getBusFaderDb(int bus) const;
    float getMasterFaderDb() const;

    // ---- Insert reorder ----
    void reorderChannelInserts(int track, int fromSlot, int toSlot);

    // ---- Offline render (blocks calling thread, use from background thread) ----
    bool startOfflineRender(const std::string& outputPath, int64_t totalSamples);
    bool startOfflineRenderForTrack(const std::string& outputPath, int trackIndex, int64_t totalSamples);
    void stopOfflineRender();

    // ---- Perform FX ----
    void triggerPerformFx(int type);

    // ---- Audio clip registry (timeline audio clips) ----
    bool loadAudioClip(const std::string& clipId, const std::string& path);
    void unloadAudioClip(const std::string& clipId);
    bool startAudioClip(const std::string& clipId, int trackIndex, int startOffsetInBuffer);
    void stopAudioClip(int trackIndex);

    double getSampleRate() const { return sampleRate_; }

private:
    double sampleRate_ = 44100.0;

    // Oscilloscope circular buffer (master output monitor, lives at engine level)
    float scopeBuffer_[SCOPE_SIZE]{};
    int scopeWriteIndex_ = 0;

    // Mixer graph
    std::array<MixerChannel, MAX_TRACKS> channels_;
    std::array<Bus, NUM_BUSES> buses_;
    MasterBus masterBus_;
    std::unique_ptr<SynthInstrument> synthInstrument_;
    std::unique_ptr<SamplerInstrument> sampler_;
    // Per-pad synth pool: lazily created SynthInstrument instances,
    // one per sampler pad (0..31). Pads in SYNTH mode route noteOn to these
    // instead of the shared channel-0 synth.
    std::array<std::unique_ptr<SynthInstrument>, PAD_SYNTH_COUNT> synthForPad_{};
    AudioRecorder recorder_;
    TimeStretchWorker timeStretchWorker_;
    jujidaw::Transport transport_;
    jujidaw::EventQueue eventQueue_;
    std::unordered_map<std::string, std::shared_ptr<SampleBuffer>> audioClips_;
    std::array<std::unique_ptr<AudioClipPlayer>, MAX_TRACKS> audioClipPlayers_;
    std::atomic<int> activeTrackCount_{2}; // Channel 0 synth, channel 1 sampler

    // Lock-free mixer command queue
    static constexpr int MIXER_QUEUE_SIZE = 256;
    std::array<MixerCommand, MIXER_QUEUE_SIZE> mixerQueue_;
    std::atomic<int> mixerQueueHead_{0};
    std::atomic<int> mixerQueueTail_{0};

    void processMixerQueue();

    // Master peak level (for metering, read from UI thread)
    std::atomic<float> masterPeak_{0.0f};
    float masterPeakDecayFactor_ = 0.99977f;

    // ---- Offline render state ----
    std::atomic<bool> offlineRenderActive_{false};
    std::atomic<bool> offlineRenderCancel_{false};
    int offlineRenderTrack_ = -1; // -1 = master mix, >=0 = solo this track
    std::vector<float> offlineRenderBuffer_;
    std::string offlineRenderPath_;
    std::array<bool, MAX_TRACKS> savedSoloStates_{}; // for stem render save/restore

    // ---- Perform FX state ----
    std::atomic<int> performFxTrigger_{0};
    float tapeStopRamp_ = 1.0f;
    bool tapeStopActive_ = false;
    int bitcrushPhase_ = 0;
    int bitcrushSampleHold_ = 0;
    bool bitcrushEnabled_ = false;
    float filterSweepCutoff_ = 1.0f;
    float filterSweepTarget_ = 1.0f;
    int filterSweepCounter_ = 0;
    bool filterSweepActive_ = false;
    Filter performFilter_; // temporary filter for sweep
};

#endif // JUJIDAW_AUDIOENGINE_H
