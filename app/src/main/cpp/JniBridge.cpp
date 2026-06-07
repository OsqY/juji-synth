#include "SynthEngine.h"
#include <android/log.h>
#include <jni.h>

#define LOG_TAG "JujiSynth"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

SynthEngine& SynthEngine::getInstance() {
    static SynthEngine instance;
    return instance;
}

SynthEngine::SynthEngine() = default;

bool SynthEngine::start() {
    if (isRunning_) {
        LOGI("Engine already running");
        return true;
    }

    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output);
    builder.setPerformanceMode(oboe::PerformanceMode::LowLatency);
    builder.setSharingMode(oboe::SharingMode::Exclusive);
    builder.setFormat(oboe::AudioFormat::Float);
    builder.setChannelCount(1); // Mono output
    builder.setSampleRate(44100);
    builder.setFramesPerDataCallback(256);
    builder.setCallback(this);

    oboe::Result result = builder.openStream(stream_);
    if (result != oboe::Result::OK) {
        LOGE("Failed to open audio stream: %s",
             oboe::convertToText(result));
        return false;
    }

    // Initialize engine with actual sample rate
    double actualSampleRate = stream_->getSampleRate();
    engine_.init(actualSampleRate);
    LOGI("Audio engine initialized at %f Hz", actualSampleRate);

    result = stream_->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("Failed to start audio stream: %s",
             oboe::convertToText(result));
        return false;
    }

    isRunning_ = true;
    LOGI("Audio engine started successfully");
    return true;
}

bool SynthEngine::stop() {
    if (!isRunning_) return true;

    oboe::Result result = stream_->requestStop();
    if (result != oboe::Result::OK) {
        LOGE("Failed to stop audio stream: %s",
             oboe::convertToText(result));
        return false;
    }

    result = stream_->close();
    if (result != oboe::Result::OK) {
        LOGE("Failed to close audio stream: %s",
             oboe::convertToText(result));
    }

    isRunning_ = false;
    LOGI("Audio engine stopped");
    return true;
}

oboe::DataCallbackResult SynthEngine::onAudioReady(
    oboe::AudioStream* stream,
    void* audioData,
    int32_t numFrames)
{
    auto* outputBuffer = static_cast<float*>(audioData);
    engine_.processAudio(outputBuffer, numFrames);
    return oboe::DataCallbackResult::Continue;
}

void SynthEngine::onErrorAfterClose(
    oboe::AudioStream* stream,
    oboe::Result error)
{
    LOGE("Audio stream error after close: %s", oboe::convertToText(error));
    isRunning_ = false;
}

// ========== JNI Bridge ==========

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_jujisynth_audio_SynthEngine_nativeStart(JNIEnv* env, jclass /*clazz*/) {
    return static_cast<jboolean>(SynthEngine::getInstance().start());
}

JNIEXPORT jboolean JNICALL
Java_com_jujisynth_audio_SynthEngine_nativeStop(JNIEnv* env, jclass /*clazz*/) {
    return static_cast<jboolean>(SynthEngine::getInstance().stop());
}

JNIEXPORT jboolean JNICALL
Java_com_jujisynth_audio_SynthEngine_nativeIsRunning(JNIEnv* env, jclass /*clazz*/) {
    return static_cast<jboolean>(SynthEngine::getInstance().isRunning());
}

JNIEXPORT void JNICALL
Java_com_jujisynth_audio_SynthEngine_nativeNoteOn(JNIEnv* env, jclass /*clazz*/,
                                                  jint note, jint velocity) {
    SynthEngine::getInstance().getAudioEngine().noteOn(note, velocity);
}

JNIEXPORT void JNICALL
Java_com_jujisynth_audio_SynthEngine_nativeNoteOff(JNIEnv* env, jclass /*clazz*/,
                                                   jint note) {
    SynthEngine::getInstance().getAudioEngine().noteOff(note);
}

JNIEXPORT void JNICALL
Java_com_jujisynth_audio_SynthEngine_nativeSetParam(JNIEnv* env, jclass /*clazz*/,
                                                    jint paramId, jfloat value) {
    auto& engine = SynthEngine::getInstance().getAudioEngine();
    switch (paramId) {
        // Oscillators (0-9)
        case 0:  engine.setOsc1Level(value); break;
        case 1:  engine.setOsc2Level(value); break;
        case 2:  engine.setOsc1Waveform(static_cast<int>(value)); break;
        case 3:  engine.setOsc2Waveform(static_cast<int>(value)); break;
        case 4:  engine.setOscDetune(value); break;
        case 5:  engine.setSubOscLevel(value); break;
        case 6:  engine.setNoiseLevel(value); break;
        case 7:  engine.setOscMix(value); break;
        case 8:  engine.setOscSync(value > 0.5f); break;

        // Filter (10-19)
        case 10: engine.setFilterCutoff(value); break;
        case 11: engine.setFilterResonance(value); break;
        case 12: engine.setFilterMode(static_cast<int>(value)); break;
        case 13: engine.setFilterEnvAmount(value); break;

        // Amp Envelope (20-24)
        case 20: engine.setAmpAttack(value); break;
        case 21: engine.setAmpDecay(value); break;
        case 22: engine.setAmpSustain(value); break;
        case 23: engine.setAmpRelease(value); break;

        // Filter Envelope (25-29)
        case 25: engine.setFilterAttack(value); break;
        case 26: engine.setFilterDecay(value); break;
        case 27: engine.setFilterSustain(value); break;
        case 28: engine.setFilterRelease(value); break;

        // LFO1 (30-34)
        case 30: engine.setLfo1Rate(value); break;
        case 31: engine.setLfo1Depth(value); break;
        case 32: engine.setLfo1Waveform(static_cast<int>(value)); break;

        // LFO2 (35-39)
        case 35: engine.setLfo2Rate(value); break;
        case 36: engine.setLfo2Depth(value); break;
        case 37: engine.setLfo2Waveform(static_cast<int>(value)); break;

        // Effects (40-49)
        case 40: engine.setReverbMix(value); break;
        case 41: engine.setReverbDecay(value); break;
        case 42: engine.setDelayMix(value); break;
        case 43: engine.setDelayTime(value); break;
        case 44: engine.setDelayFeedback(value); break;
        case 45: engine.setDistortionDrive(value); break;
        case 46: engine.setDistortionMix(value); break;
        case 47: engine.setEffectsBypass(value > 0.5f); break;

        // Master (50-52)
        case 50: engine.setMasterVolume(value); break;
        case 51: engine.setPitchBend(value); break;
        case 52: engine.setModWheel(value); break;

        // Sequencer (60-62)
        case 60: engine.setSequencerTempo(value); break;
        case 61: engine.setSequencerPlaying(value > 0.5f); break;
    }
}

JNIEXPORT jfloat JNICALL
Java_com_jujisynth_audio_SynthEngine_nativeGetParam(JNIEnv* env, jclass /*clazz*/,
                                                    jint paramId) {
    auto params = SynthEngine::getInstance().getAudioEngine().getCurrentParams();
    switch (paramId) {
        case 0:  return params.oscillators.osc1.level;
        case 1:  return params.oscillators.osc2.level;
        case 2:  return static_cast<jfloat>(params.oscillators.osc1.waveform);
        case 3:  return static_cast<jfloat>(params.oscillators.osc2.waveform);
        case 4:  return params.oscillators.osc1.detune;
        case 5:  return params.oscillators.subOscLevel;
        case 6:  return params.oscillators.noiseLevel;
        case 7:  return params.oscillators.oscMix;
        case 10: return params.filter.cutoff;
        case 11: return params.filter.resonance;
        case 12: return static_cast<jfloat>(params.filter.mode);
        case 13: return params.filter.envelopeAmount;
        case 20: return params.envelopes.attack;
        case 21: return params.envelopes.decay;
        case 22: return params.envelopes.sustain;
        case 23: return params.envelopes.release;
        case 25: return params.envelopes.filterAttack;
        case 26: return params.envelopes.filterDecay;
        case 27: return params.envelopes.filterSustain;
        case 28: return params.envelopes.filterRelease;
        case 30: return params.lfos.lfo1.rate;
        case 31: return params.lfos.lfo1.depth;
        case 32: return static_cast<jfloat>(params.lfos.lfo1.waveform);
        case 35: return params.lfos.lfo2.rate;
        case 36: return params.lfos.lfo2.depth;
        case 37: return static_cast<jfloat>(params.lfos.lfo2.waveform);
        case 40: return params.effects.reverb.mix;
        case 41: return params.effects.reverb.decay;
        case 42: return params.effects.delay.mix;
        case 43: return params.effects.delay.time;
        case 44: return params.effects.delay.feedback;
        case 45: return params.effects.distortion.drive;
        case 46: return params.effects.distortion.mix;
        case 50: return params.master.volume;
        default: return 0.0f;
    }
}

} // extern "C"
