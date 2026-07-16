#include "SynthEngine.h"
#include "SampleBuffer.h"
#include "SamplerInstrument.h"
#include "TimeStretchWorker.h"
#include "WavWriter.h"
#include <android/log.h>
#include <jni.h>
#include <mutex>

#define LOG_TAG "JujiDaw"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
JavaVM* g_jvm = nullptr;
jobject g_timeStretchListener = nullptr; // global ref
jclass g_timeStretchListenerClass = nullptr; // global ref
jmethodID g_onTimeStretchCompleteMid = nullptr;
std::mutex g_timeStretchMutex; // guards all above globals except g_jvm (set once)
}

SynthEngine& SynthEngine::getInstance() {
    static SynthEngine instance;
    return instance;
}

SynthEngine::SynthEngine() = default;

StartResult SynthEngine::tryOpenStream(bool lowLatency, bool exclusive) {
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output);
    builder.setPerformanceMode(lowLatency ? oboe::PerformanceMode::LowLatency : oboe::PerformanceMode::None);
    builder.setSharingMode(exclusive ? oboe::SharingMode::Exclusive : oboe::SharingMode::Shared);
    builder.setFormat(oboe::AudioFormat::Float);
    builder.setChannelCount(oboe::ChannelCount::Mono);
    builder.setCallback(this);

    if (!exclusive) {
        // Shared mode: allow Oboe to convert sample rate/format if needed.
        builder.setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium);
    }

    std::shared_ptr<oboe::AudioStream> stream;
    oboe::Result result = builder.openStream(stream);
    if (result != oboe::Result::OK) {
        std::string mode = (lowLatency ? "low-latency" : "shared") + std::string(" / ") + (exclusive ? "exclusive" : "shared");
        LOGE("Failed to open %s audio stream: %s", mode.c_str(), oboe::convertToText(result));
        return {false, std::string("Failed to open ") + mode + " stream: " + oboe::convertToText(result), 0, 0};
    }

    double actualSampleRate = stream->getSampleRate();
    engine_.init(actualSampleRate);
    LOGI("Audio engine initialized at %f Hz (%s/%s)",
         actualSampleRate,
         lowLatency ? "low-latency" : "none",
         exclusive ? "exclusive" : "shared");

    result = stream->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("Failed to start audio stream: %s", oboe::convertToText(result));
        stream->close();
        return {false, std::string("Failed to start stream: ") + oboe::convertToText(result), 0, 0};
    }

    stream_ = stream;
    isRunning_ = true;
    sampleRate_ = static_cast<int>(actualSampleRate);
    framesPerBurst_ = stream->getFramesPerBurst();
    lastError_.clear();
    LOGI("Audio engine started successfully (mode=%s/%s, burst=%d)",
         lowLatency ? "low-latency" : "none",
         exclusive ? "exclusive" : "shared",
         framesPerBurst_);
    return {true, "", sampleRate_, framesPerBurst_};
}

StartResult SynthEngine::start() {
    if (isRunning_) {
        LOGI("Engine already running");
        return {true, "", sampleRate_, framesPerBurst_};
    }

    // Primary: low-latency exclusive
    StartResult result = tryOpenStream(true, true);
    if (result.ok) return result;

    std::string primaryError = result.message;
    LOGI("Primary stream config failed, trying fallback: %s", primaryError.c_str());

    // Fallback 1: low-latency shared
    result = tryOpenStream(true, false);
    if (result.ok) return result;

    // Fallback 2: shared/none with conversion
    result = tryOpenStream(false, false);
    if (result.ok) return result;

    lastError_ = primaryError + "; fallback also failed";
    LOGE("All audio stream configurations failed");
    return {false, lastError_, 0, 0};
}

bool SynthEngine::stop() {
    if (!isRunning_) return true;

    oboe::Result result = stream_->requestStop();
    if (result != oboe::Result::OK) {
        LOGE("Failed to stop audio stream: %s", oboe::convertToText(result));
    }

    result = stream_->close();
    if (result != oboe::Result::OK) {
        LOGE("Failed to close audio stream: %s", oboe::convertToText(result));
    }

    isRunning_ = false;
    stream_.reset();
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
Java_com_jujidaw_audio_SynthEngine_nativeStart(JNIEnv* env, jclass /*clazz*/) {
    return static_cast<jboolean>(SynthEngine::getInstance().start().ok);
}

JNIEXPORT jstring JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeGetLastError(JNIEnv* env, jclass /*clazz*/) {
    const std::string& err = SynthEngine::getInstance().getLastError();
    if (err.empty()) return nullptr;
    return env->NewStringUTF(err.c_str());
}

JNIEXPORT jint JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeGetSampleRate(JNIEnv* env, jclass /*clazz*/) {
    return static_cast<jint>(SynthEngine::getInstance().getSampleRate());
}

JNIEXPORT jint JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeGetFramesPerBurst(JNIEnv* env, jclass /*clazz*/) {
    return static_cast<jint>(SynthEngine::getInstance().getFramesPerBurst());
}

JNIEXPORT jboolean JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeStop(JNIEnv* env, jclass /*clazz*/) {
    return static_cast<jboolean>(SynthEngine::getInstance().stop());
}

JNIEXPORT jboolean JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeIsRunning(JNIEnv* env, jclass /*clazz*/) {
    return static_cast<jboolean>(SynthEngine::getInstance().isRunning());
}

JNIEXPORT void JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeNoteOn(JNIEnv* env, jclass /*clazz*/,
                                                  jint note, jint velocity) {
    SynthEngine::getInstance().getAudioEngine().noteOn(note, velocity);
}

JNIEXPORT void JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeNoteOff(JNIEnv* env, jclass /*clazz*/,
                                                   jint note) {
    SynthEngine::getInstance().getAudioEngine().noteOff(note);
}

JNIEXPORT void JNICALL
Java_com_jujidaw_audio_SynthEngine_nativePanic(JNIEnv* env, jclass /*clazz*/) {
    SynthEngine::getInstance().getAudioEngine().panic();
}

JNIEXPORT void JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeGetWaveform(JNIEnv* env, jclass /*clazz*/,
                                                       jfloatArray buffer) {
    jsize size = env->GetArrayLength(buffer);
    if (size <= 0) return;
    jfloat* elements = env->GetFloatArrayElements(buffer, nullptr);
    SynthEngine::getInstance().getAudioEngine().getWaveform(elements, size);
    env->ReleaseFloatArrayElements(buffer, elements, 0);
}

JNIEXPORT void JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeResetEffects(JNIEnv* env, jclass /*clazz*/) {
    SynthEngine::getInstance().getAudioEngine().setPendingEffectsReset();
}

JNIEXPORT void JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeSetParam(JNIEnv* env, jclass /*clazz*/,
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

        // Chorus (55-57)
        case 55: engine.setChorusRate(value); break;
        case 56: engine.setChorusDepth(value); break;
        case 57: engine.setChorusMix(value); break;

        // Master (50-52)
        case 50: engine.setMasterVolume(value); break;
        case 51: engine.setPitchBend(value); break;
        case 52: engine.setModWheel(value); break;

        // Sequencer (60-62)
        case 60: engine.setSequencerTempo(value); break;
        case 61: engine.setSequencerPlaying(value > 0.5f); break;
        case 62: engine.setSequencerLooping(value > 0.5f); break;
    }
}

JNIEXPORT jint JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeGetSequencerStep(JNIEnv* env, jclass /*clazz*/) {
    return static_cast<jint>(SynthEngine::getInstance().getAudioEngine().getSequencerStep());
}

JNIEXPORT jboolean JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeGetSequencerLooping(JNIEnv* env, jclass /*clazz*/) {
    return static_cast<jboolean>(SynthEngine::getInstance().getAudioEngine().getSequencerLooping());
}

JNIEXPORT void JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeApplySynthState(JNIEnv* env, jclass /*clazz*/,
                                                           jfloatArray values) {
    jsize count = env->GetArrayLength(values);
    if (count != AudioEngine::SYNTH_PARAM_COUNT) {
        LOGE("nativeApplySynthState: expected %d params, got %d", AudioEngine::SYNTH_PARAM_COUNT, static_cast<int>(count));
        return;
    }
    jfloat* elements = env->GetFloatArrayElements(values, nullptr);
    SynthEngine::getInstance().getAudioEngine().setAllParamsFromArray(elements, count);
    env->ReleaseFloatArrayElements(values, elements, JNI_ABORT);
}

JNIEXPORT void JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeSetModulationRoute(JNIEnv* env, jclass /*clazz*/,
                                                              jint index, jint source,
                                                              jint destination, jfloat amount,
                                                              jboolean active) {
    ModulationRoute route;
    route.source = source;
    route.destination = destination;
    route.amount = amount;
    route.active = static_cast<bool>(active);
    SynthEngine::getInstance().getAudioEngine().setModulationRoute(index, route);
}

JNIEXPORT void JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeSetSequencerSteps(JNIEnv* env, jclass /*clazz*/,
                                                              jintArray notes, jintArray velocities,
                                                              jfloatArray gates, jfloatArray automation) {
    jsize count = env->GetArrayLength(notes);
    if (count != SEQUENCER_STEPS) return;
    if (env->GetArrayLength(velocities) != SEQUENCER_STEPS) return;
    if (env->GetArrayLength(gates) != SEQUENCER_STEPS) return;
    if (env->GetArrayLength(automation) != SEQUENCER_STEPS) return;

    jint* notesArr = env->GetIntArrayElements(notes, nullptr);
    jint* velArr = env->GetIntArrayElements(velocities, nullptr);
    jfloat* gateArr = env->GetFloatArrayElements(gates, nullptr);
    jfloat* autoArr = env->GetFloatArrayElements(automation, nullptr);

    std::array<SequencerStep, SEQUENCER_STEPS> steps;
    for (int i = 0; i < SEQUENCER_STEPS; i++) {
        steps[i].note = static_cast<int>(notesArr[i]);
        steps[i].velocity = static_cast<int>(velArr[i]);
        steps[i].gate = gateArr[i];
        steps[i].automation = autoArr[i];
    }

    env->ReleaseIntArrayElements(notes, notesArr, JNI_ABORT);
    env->ReleaseIntArrayElements(velocities, velArr, JNI_ABORT);
    env->ReleaseFloatArrayElements(gates, gateArr, JNI_ABORT);
    env->ReleaseFloatArrayElements(automation, autoArr, JNI_ABORT);

    SynthEngine::getInstance().getAudioEngine().setSequencerSteps(steps);
}

JNIEXPORT jfloat JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeGetParam(JNIEnv* env, jclass /*clazz*/,
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
        case 55: return params.effects.chorus.rate;
        case 56: return params.effects.chorus.depth;
        case 57: return params.effects.chorus.mix;
        case 50: return params.master.volume;
        default: return 0.0f;
    }
}

JNIEXPORT jboolean JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeLoadSampleToPad(JNIEnv* env, jclass /*clazz*/,
                                                          jstring path, jint padIndex) {
    if (path == nullptr) return JNI_FALSE;
    const char* cpath = env->GetStringUTFChars(path, nullptr);
    auto buffer = std::make_shared<SampleBuffer>();
    bool ok = buffer->loadFromWav(cpath);
    env->ReleaseStringUTFChars(path, cpath);
    if (ok) {
        SynthEngine::getInstance().getAudioEngine().getSampler().setPadBuffer(padIndex, std::move(buffer));
    }
    return static_cast<jboolean>(ok);
}

JNIEXPORT void JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeReleasePad(JNIEnv* env, jclass /*clazz*/, jint padIndex) {
    SynthEngine::getInstance().getAudioEngine().getSampler().releasePad(padIndex);
}

JNIEXPORT void JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeSetPadParam(JNIEnv* env, jclass /*clazz*/,
                                                      jint padIndex, jint paramId, jfloat value) {
    auto& sampler = SynthEngine::getInstance().getAudioEngine().getSampler();
    if (padIndex < 0 || padIndex >= NUM_PADS) return;
    auto& pad = sampler.getPad(padIndex);
    switch (paramId) {
        case 0: pad.pitch = value; break;
        case 1: pad.pan = value; break;
        case 2: pad.volume = value; break;
        case 3: pad.attack = value; break;
        case 4: pad.release = value; break;
        case 5: pad.filterCutoff = value; break;
        case 6: pad.filterResonance = value; break;
        case 7: pad.reverse = value > 0.5f; break;
        case 8: pad.loop = value > 0.5f; break;
        case 9: pad.oneShot = value > 0.5f; break;
        case 10: pad.useFilter = value > 0.5f; break;
        case 11: pad.synthMode = value > 0.5f; break;
        case 12: pad.synthRootNote = static_cast<int>(value); break;
    }
}

JNIEXPORT void JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeSetSamplerBank(JNIEnv* env, jclass /*clazz*/, jint bank) {
    SynthEngine::getInstance().getAudioEngine().getSampler().setActiveBank(bank);
}

JNIEXPORT void JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeTriggerPad(JNIEnv* env, jclass /*clazz*/,
                                                     jint padIndex, jint velocity) {
    auto& sampler = SynthEngine::getInstance().getAudioEngine().getSampler();
    int base = sampler.getActiveBank() * 16;
    int note = base + (padIndex % 16);
    sampler.noteOn(note, velocity);
}

JNIEXPORT void JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeChopSample(JNIEnv* env, jclass /*clazz*/,
                                                     jint sourcePad, jint startPad, jint numSlices) {
    SynthEngine::getInstance().getAudioEngine().getSampler().chopSample(sourcePad, startPad, numSlices);
}

JNIEXPORT jboolean JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeTimeStretchPad(JNIEnv* /*env*/, jclass /*clazz*/,
                                                        jint padIndex, jdouble tempoChangePercent,
                                                        jdouble pitchSemiTones, jdouble rateChangePercent) {
    // Backward-compatible: enqueue the job and return success. The result is
    // delivered to the listener installed via nativeSetTimeStretchListener.
    TimeStretchWorker::Job job;
    job.padIndex = padIndex;
    job.tempoChangePercent = tempoChangePercent;
    job.pitchSemiTones = pitchSemiTones;
    job.rateChangePercent = rateChangePercent;
    SynthEngine::getInstance().getAudioEngine().getTimeStretchWorker().enqueue(job);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeStartRecording(JNIEnv* /*env*/, jclass /*clazz*/,
                                                        jint sampleRate) {
    return SynthEngine::getInstance().getAudioEngine().getRecorder().start(sampleRate) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeStopRecording(JNIEnv* /*env*/, jclass /*clazz*/) {
    SynthEngine::getInstance().getAudioEngine().getRecorder().stop();
}

JNIEXPORT jboolean JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeIsRecording(JNIEnv* /*env*/, jclass /*clazz*/) {
    return SynthEngine::getInstance().getAudioEngine().getRecorder().isRecording() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeAssignRecordingToPad(JNIEnv* /*env*/, jclass /*clazz*/,
                                                               jint padIndex) {
    auto buffer = SynthEngine::getInstance().getAudioEngine().getRecorder().takeBuffer();
    if (!buffer || !buffer->isLoaded()) return JNI_FALSE;
    SynthEngine::getInstance().getAudioEngine().getSampler().setPadBuffer(padIndex, std::move(buffer));
    return JNI_TRUE;
}

namespace {

// Trampoline invoked from the worker thread. Attaches to JVM and calls the
// stored listener's onTimeStretchComplete method.
void TimeStretchCompletionTrampoline(void* /*env*/, void* /*userData*/,
                                      int padIndex, int success) {
    if (!g_jvm) return;

    JNIEnv* env = nullptr;
    jint status = g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    bool needsDetach = false;
    if (status == JNI_EDETACHED) {
        if (g_jvm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
            LOGE("Failed to attach worker thread to JVM");
            return;
        }
        needsDetach = true;
    }

    // Lock the mutex to safely read the listener globals while holding the
    // JNI env. g_jvm is safe to read without a lock because it is set once
    // in JNI_OnLoad and never changes.
    {
        std::lock_guard<std::mutex> lock(g_timeStretchMutex);
        if (env && g_timeStretchListener && g_onTimeStretchCompleteMid) {
            env->CallVoidMethod(g_timeStretchListener, g_onTimeStretchCompleteMid,
                                static_cast<jint>(padIndex), success ? JNI_TRUE : JNI_FALSE);
        }
    }

    if (needsDetach) {
        g_jvm->DetachCurrentThread();
    }
}

} // namespace

JNIEXPORT jboolean JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeTimeStretchPadAsync(JNIEnv* /*env*/, jclass /*clazz*/,
                                                              jint padIndex, jdouble tempoChangePercent,
                                                              jdouble pitchSemiTones, jdouble rateChangePercent) {
    TimeStretchWorker::Job job;
    job.padIndex = padIndex;
    job.tempoChangePercent = tempoChangePercent;
    job.pitchSemiTones = pitchSemiTones;
    job.rateChangePercent = rateChangePercent;
    SynthEngine::getInstance().getAudioEngine().getTimeStretchWorker().enqueue(job);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeIsTimeStretching(JNIEnv* /*env*/, jclass /*clazz*/) {
    return SynthEngine::getInstance().getAudioEngine().getTimeStretchWorker().isBusy() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeSetTimeStretchListener(JNIEnv* env, jclass /*clazz*/,
                                                                  jobject listener) {
    // Lock to safely modify globals that are read by the worker thread in
    // TimeStretchCompletionTrampoline.
    std::lock_guard<std::mutex> lock(g_timeStretchMutex);

    // Clear any previous listener
    if (g_timeStretchListener) {
        env->DeleteGlobalRef(g_timeStretchListener);
        g_timeStretchListener = nullptr;
    }
    if (g_timeStretchListenerClass) {
        env->DeleteGlobalRef(g_timeStretchListenerClass);
        g_timeStretchListenerClass = nullptr;
    }
    g_onTimeStretchCompleteMid = nullptr;

    if (listener == nullptr) {
        SynthEngine::getInstance().getAudioEngine().getTimeStretchWorker()
            .setCompletionCallback(nullptr, nullptr);
        return;
    }

    g_timeStretchListener = env->NewGlobalRef(listener);
    g_timeStretchListenerClass = static_cast<jclass>(env->NewGlobalRef(
        env->GetObjectClass(listener)));
    g_onTimeStretchCompleteMid = env->GetMethodID(
        g_timeStretchListenerClass, "onTimeStretchComplete", "(IZ)V");
    if (!g_onTimeStretchCompleteMid) {
        LOGE("onTimeStretchComplete not found on listener");
        env->DeleteGlobalRef(g_timeStretchListener);
        env->DeleteGlobalRef(g_timeStretchListenerClass);
        g_timeStretchListener = nullptr;
        g_timeStretchListenerClass = nullptr;
        return;
    }

    SynthEngine::getInstance().getAudioEngine().getTimeStretchWorker()
        .setCompletionCallback(TimeStretchCompletionTrampoline, nullptr);
}

JNIEXPORT jboolean JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeWritePadToWav(JNIEnv* env, jclass /*clazz*/,
                                                        jint padIndex, jstring path) {
    if (path == nullptr) return JNI_FALSE;
    const char* cpath = env->GetStringUTFChars(path, nullptr);
    bool ok = false;
    auto& sampler = SynthEngine::getInstance().getAudioEngine().getSampler();
    if (padIndex >= 0 && padIndex < NUM_PADS) {
        const auto& buf = sampler.getPad(padIndex).buffer;
        if (buf && buf->isLoaded()) {
            ok = WavWriter::write(*buf, cpath);
        }
    }
    env->ReleaseStringUTFChars(path, cpath);
    return ok ? JNI_TRUE : JNI_FALSE;
}

// ---- Transport and scheduled events ----

JNIEXPORT jboolean JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeScheduleNoteOn(JNIEnv* /*env*/, jclass /*clazz*/,
                                                          jint trackIndex, jint note, jfloat velocity,
                                                          jlong targetSample) {
    if (trackIndex < 0 || trackIndex >= AudioEngine::MAX_TRACKS) return JNI_FALSE;
    auto event = jujidaw::ScheduledEvent::makeNoteOn(trackIndex, note, velocity,
                                                      static_cast<int64_t>(targetSample));
    return SynthEngine::getInstance().getAudioEngine().getEventQueue().push(event) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeScheduleNoteOff(JNIEnv* /*env*/, jclass /*clazz*/,
                                                           jint trackIndex, jint note,
                                                           jlong targetSample) {
    if (trackIndex < 0 || trackIndex >= AudioEngine::MAX_TRACKS) return JNI_FALSE;
    auto event = jujidaw::ScheduledEvent::makeNoteOff(trackIndex, note,
                                                       static_cast<int64_t>(targetSample));
    return SynthEngine::getInstance().getAudioEngine().getEventQueue().push(event) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeSchedulePadTrigger(JNIEnv* /*env*/, jclass /*clazz*/,
                                                              jint trackIndex, jint padIndex,
                                                              jfloat velocity, jlong targetSample) {
    if (trackIndex < 0 || trackIndex >= AudioEngine::MAX_TRACKS) return JNI_FALSE;
    if (padIndex < 0 || padIndex >= NUM_PADS) return JNI_FALSE;
    auto event = jujidaw::ScheduledEvent::makePadTrigger(trackIndex, padIndex, velocity,
                                                          static_cast<int64_t>(targetSample));
    return SynthEngine::getInstance().getAudioEngine().getEventQueue().push(event) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeScheduleAutomation(JNIEnv* /*env*/, jclass /*clazz*/,
                                                              jint trackIndex, jint paramIndex,
                                                              jfloat value, jlong targetSample) {
    if (trackIndex < 0 || trackIndex >= AudioEngine::MAX_TRACKS) return JNI_FALSE;
    auto event = jujidaw::ScheduledEvent::makeAutomation(trackIndex, paramIndex, value,
                                                          static_cast<int64_t>(targetSample));
    return SynthEngine::getInstance().getAudioEngine().getEventQueue().push(event) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeClearScheduledEvents(JNIEnv* /*env*/, jclass /*clazz*/) {
    SynthEngine::getInstance().getAudioEngine().getEventQueue().clear();
}

JNIEXPORT void JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeSetTransport(JNIEnv* /*env*/, jclass /*clazz*/,
                                                       jboolean playing, jboolean recording,
                                                       jfloat tempoBpm) {
    auto event = jujidaw::ScheduledEvent::makeTransport(playing != JNI_FALSE,
                                                        recording != JNI_FALSE,
                                                        tempoBpm);
    SynthEngine::getInstance().getAudioEngine().getEventQueue().push(event);
}

JNIEXPORT jlong JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeGetPlayheadSample(JNIEnv* /*env*/, jclass /*clazz*/) {
    return static_cast<jlong>(SynthEngine::getInstance().getAudioEngine().getTransport().getCurrentSample());
}

JNIEXPORT void JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeSetPlayheadSample(JNIEnv* /*env*/, jclass /*clazz*/,
                                                            jlong sample) {
    SynthEngine::getInstance().getAudioEngine().getEventQueue().clear();
    SynthEngine::getInstance().getAudioEngine().getTransport().setCurrentSample(static_cast<int64_t>(sample));
}

JNIEXPORT void JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeSetLoop(JNIEnv* /*env*/, jclass /*clazz*/,
                                                  jboolean enabled, jlong startSample, jlong endSample) {
    SynthEngine::getInstance().getAudioEngine().getTransport().setLoop(
        enabled != JNI_FALSE,
        static_cast<int64_t>(startSample),
        static_cast<int64_t>(endSample));
}

JNIEXPORT void JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeSetSequencerEnabled(JNIEnv* /*env*/, jclass /*clazz*/,
                                                              jboolean enabled) {
    SynthEngine::getInstance().getAudioEngine().setSequencerEnabled(enabled != JNI_FALSE);
}

JNIEXPORT jboolean JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeLoadAudioClip(JNIEnv* env, jclass /*clazz*/,
                                                        jstring clipId, jstring path) {
    if (clipId == nullptr || path == nullptr) return JNI_FALSE;
    const char* cid = env->GetStringUTFChars(clipId, nullptr);
    const char* cpath = env->GetStringUTFChars(path, nullptr);
    bool ok = SynthEngine::getInstance().getAudioEngine().loadAudioClip(cid, cpath);
    env->ReleaseStringUTFChars(clipId, cid);
    env->ReleaseStringUTFChars(path, cpath);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeUnloadAudioClip(JNIEnv* env, jclass /*clazz*/,
                                                          jstring clipId) {
    if (clipId == nullptr) return;
    const char* cid = env->GetStringUTFChars(clipId, nullptr);
    SynthEngine::getInstance().getAudioEngine().unloadAudioClip(cid);
    env->ReleaseStringUTFChars(clipId, cid);
}

JNIEXPORT jboolean JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeStartAudioClip(JNIEnv* env, jclass /*clazz*/,
                                                         jstring clipId, jint trackIndex,
                                                         jint startOffsetInBuffer) {
    if (clipId == nullptr || trackIndex < 0) return JNI_FALSE;
    const char* cid = env->GetStringUTFChars(clipId, nullptr);
    bool ok = SynthEngine::getInstance().getAudioEngine().startAudioClip(
        cid, trackIndex, startOffsetInBuffer);
    env->ReleaseStringUTFChars(clipId, cid);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeStopAudioClip(JNIEnv* /*env*/, jclass /*clazz*/,
                                                        jint trackIndex) {
    SynthEngine::getInstance().getAudioEngine().stopAudioClip(trackIndex);
}

JNIEXPORT void JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeSetPunchRange(JNIEnv* /*env*/, jclass /*clazz*/,
                                                        jboolean enabled, jlong inSample,
                                                        jlong outSample) {
    auto& recorder = SynthEngine::getInstance().getAudioEngine().getRecorder();
    recorder.setPunchEnabled(enabled != JNI_FALSE);
    recorder.setPunchRange(static_cast<int64_t>(inSample), static_cast<int64_t>(outSample));
}

// ========== Mixer control ==========

JNIEXPORT void JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeSetChannelFader(JNIEnv* /*env*/, jclass /*clazz*/,
                                                          jint trackIndex, jfloat db) {
    MixerCommand cmd;
    cmd.type = MixerCommandType::SetFader;
    cmd.track = static_cast<uint8_t>(trackIndex);
    cmd.value = db;
    SynthEngine::getInstance().getAudioEngine().pushMixerCommand(cmd);
}

JNIEXPORT void JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeSetChannelPan(JNIEnv* /*env*/, jclass /*clazz*/,
                                                        jint trackIndex, jfloat pan) {
    MixerCommand cmd;
    cmd.type = MixerCommandType::SetPan;
    cmd.track = static_cast<uint8_t>(trackIndex);
    cmd.value = pan;
    SynthEngine::getInstance().getAudioEngine().pushMixerCommand(cmd);
}

JNIEXPORT void JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeSetChannelMute(JNIEnv* /*env*/, jclass /*clazz*/,
                                                         jint trackIndex, jboolean mute) {
    MixerCommand cmd;
    cmd.type = MixerCommandType::SetMute;
    cmd.track = static_cast<uint8_t>(trackIndex);
    cmd.booleanValue = static_cast<bool>(mute);
    SynthEngine::getInstance().getAudioEngine().pushMixerCommand(cmd);
}

JNIEXPORT void JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeSetChannelSolo(JNIEnv* /*env*/, jclass /*clazz*/,
                                                         jint trackIndex, jboolean solo) {
    MixerCommand cmd;
    cmd.type = MixerCommandType::SetSolo;
    cmd.track = static_cast<uint8_t>(trackIndex);
    cmd.booleanValue = static_cast<bool>(solo);
    SynthEngine::getInstance().getAudioEngine().pushMixerCommand(cmd);
}

JNIEXPORT void JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeSetChannelArm(JNIEnv* /*env*/, jclass /*clazz*/,
                                                        jint trackIndex, jboolean arm) {
    MixerCommand cmd;
    cmd.type = MixerCommandType::SetArm;
    cmd.track = static_cast<uint8_t>(trackIndex);
    cmd.booleanValue = static_cast<bool>(arm);
    SynthEngine::getInstance().getAudioEngine().pushMixerCommand(cmd);
}

JNIEXPORT void JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeSetSendLevel(JNIEnv* /*env*/, jclass /*clazz*/,
                                                       jint trackIndex, jint bus, jfloat level) {
    MixerCommand cmd;
    cmd.type = MixerCommandType::SetSendLevel;
    cmd.track = static_cast<uint8_t>(trackIndex);
    cmd.slot = static_cast<uint8_t>(bus);
    cmd.value = level;
    SynthEngine::getInstance().getAudioEngine().pushMixerCommand(cmd);
}

JNIEXPORT void JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeSetBusFader(JNIEnv* /*env*/, jclass /*clazz*/,
                                                      jint bus, jfloat db) {
    MixerCommand cmd;
    cmd.type = MixerCommandType::SetBusFader;
    cmd.track = static_cast<uint8_t>(bus);
    cmd.value = db;
    SynthEngine::getInstance().getAudioEngine().pushMixerCommand(cmd);
}

JNIEXPORT void JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeSetMasterFader(JNIEnv* /*env*/, jclass /*clazz*/,
                                                         jfloat db) {
    MixerCommand cmd;
    cmd.type = MixerCommandType::SetMasterFader;
    cmd.value = db;
    SynthEngine::getInstance().getAudioEngine().pushMixerCommand(cmd);
}

JNIEXPORT void JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeAddInsertEffect(JNIEnv* /*env*/, jclass /*clazz*/,
                                                          jint trackIndex, jint slot, jint type) {
    MixerCommand cmd;
    cmd.type = MixerCommandType::AddInsertEffect;
    cmd.track = static_cast<uint8_t>(trackIndex);
    cmd.slot = static_cast<uint8_t>(slot);
    cmd.effectType = static_cast<EffectType>(type);
    SynthEngine::getInstance().getAudioEngine().pushMixerCommand(cmd);
}

JNIEXPORT void JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeRemoveInsertEffect(JNIEnv* /*env*/, jclass /*clazz*/,
                                                             jint trackIndex, jint slot) {
    MixerCommand cmd;
    cmd.type = MixerCommandType::RemoveInsertEffect;
    cmd.track = static_cast<uint8_t>(trackIndex);
    cmd.slot = static_cast<uint8_t>(slot);
    SynthEngine::getInstance().getAudioEngine().pushMixerCommand(cmd);
}

JNIEXPORT void JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeSetInsertBypass(JNIEnv* /*env*/, jclass /*clazz*/,
                                                          jint trackIndex, jint slot, jboolean bypass) {
    MixerCommand cmd;
    cmd.type = MixerCommandType::SetInsertBypass;
    cmd.track = static_cast<uint8_t>(trackIndex);
    cmd.slot = static_cast<uint8_t>(slot);
    cmd.booleanValue = static_cast<bool>(bypass);
    SynthEngine::getInstance().getAudioEngine().pushMixerCommand(cmd);
}

JNIEXPORT void JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeSetInsertParam(JNIEnv* /*env*/, jclass /*clazz*/,
                                                         jint trackIndex, jint slot, jint paramId, jfloat value) {
    MixerCommand cmd;
    cmd.type = MixerCommandType::SetInsertParam;
    cmd.track = static_cast<uint8_t>(trackIndex);
    cmd.slot = static_cast<uint8_t>(slot);
    cmd.paramId = static_cast<uint8_t>(paramId);
    cmd.value = value;
    SynthEngine::getInstance().getAudioEngine().pushMixerCommand(cmd);
}

JNIEXPORT jfloat JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeGetChannelLevel(JNIEnv* /*env*/, jclass /*clazz*/,
                                                          jint trackIndex) {
    auto& engine = SynthEngine::getInstance().getAudioEngine();
    if (trackIndex < 0 || trackIndex >= AudioEngine::MAX_TRACKS) return 0.0f;
    return engine.getChannel(trackIndex).getLevel();
}

// ========== Mixer state getters ==========

JNIEXPORT jfloat JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeGetChannelFaderDb(JNIEnv* /*env*/, jclass /*clazz*/,
                                                            jint trackIndex) {
    return SynthEngine::getInstance().getAudioEngine().getChannelFaderDb(trackIndex);
}

JNIEXPORT jfloat JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeGetChannelPan(JNIEnv* /*env*/, jclass /*clazz*/,
                                                        jint trackIndex) {
    return SynthEngine::getInstance().getAudioEngine().getChannelPan(trackIndex);
}

JNIEXPORT jboolean JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeIsChannelMute(JNIEnv* /*env*/, jclass /*clazz*/,
                                                        jint trackIndex) {
    return SynthEngine::getInstance().getAudioEngine().isChannelMute(trackIndex) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeIsChannelSolo(JNIEnv* /*env*/, jclass /*clazz*/,
                                                        jint trackIndex) {
    return SynthEngine::getInstance().getAudioEngine().isChannelSolo(trackIndex) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeIsChannelArm(JNIEnv* /*env*/, jclass /*clazz*/,
                                                       jint trackIndex) {
    return SynthEngine::getInstance().getAudioEngine().isChannelArm(trackIndex) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jfloat JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeGetSendLevel(JNIEnv* /*env*/, jclass /*clazz*/,
                                                       jint trackIndex, jint bus) {
    return SynthEngine::getInstance().getAudioEngine().getSendLevel(trackIndex, bus);
}

JNIEXPORT jfloat JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeGetBusFaderDb(JNIEnv* /*env*/, jclass /*clazz*/,
                                                        jint bus) {
    return SynthEngine::getInstance().getAudioEngine().getBusFaderDb(bus);
}

JNIEXPORT jfloat JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeGetMasterFaderDb(JNIEnv* /*env*/, jclass /*clazz*/) {
    return SynthEngine::getInstance().getAudioEngine().getMasterFaderDb();
}

JNIEXPORT jfloat JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeGetMasterLevel(JNIEnv* /*env*/, jclass /*clazz*/) {
    return SynthEngine::getInstance().getAudioEngine().getMasterPeak();
}

// ========== Offline render ==========

JNIEXPORT jboolean JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeStartOfflineRender(JNIEnv* env, jclass /*clazz*/,
                                                             jstring path, jlong durationSamples) {
    if (path == nullptr) return JNI_FALSE;
    const char* cpath = env->GetStringUTFChars(path, nullptr);
    std::string cppPath(cpath);
    env->ReleaseStringUTFChars(path, cpath);
    bool ok = SynthEngine::getInstance().getAudioEngine().startOfflineRender(cppPath, durationSamples);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeStartOfflineRenderForTrack(JNIEnv* env, jclass /*clazz*/,
                                                                     jstring path, jint trackIndex,
                                                                     jlong durationSamples) {
    if (path == nullptr) return JNI_FALSE;
    const char* cpath = env->GetStringUTFChars(path, nullptr);
    std::string cppPath(cpath);
    env->ReleaseStringUTFChars(path, cpath);
    bool ok = SynthEngine::getInstance().getAudioEngine().startOfflineRenderForTrack(
        cppPath, trackIndex, durationSamples);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeStopOfflineRender(JNIEnv* /*env*/, jclass /*clazz*/) {
    SynthEngine::getInstance().getAudioEngine().stopOfflineRender();
}

// ========== Recording to WAV ==========

JNIEXPORT jboolean JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeWriteRecordingToWav(JNIEnv* env, jclass /*clazz*/,
                                                              jstring path) {
    if (path == nullptr) return JNI_FALSE;
    const char* cpath = env->GetStringUTFChars(path, nullptr);
    std::string cppPath(cpath);
    env->ReleaseStringUTFChars(path, cpath);
    bool ok = SynthEngine::getInstance().getAudioEngine().getRecorder().writeToWav(cppPath);
    return ok ? JNI_TRUE : JNI_FALSE;
}

// ========== Perform FX ==========

JNIEXPORT void JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeTriggerPerformFx(JNIEnv* /*env*/, jclass /*clazz*/,
                                                           jint type) {
    SynthEngine::getInstance().getAudioEngine().triggerPerformFx(static_cast<int>(type));
}

// ========== Insert reorder ==========

JNIEXPORT void JNICALL
Java_com_jujidaw_audio_SynthEngine_nativeReorderChannelInserts(JNIEnv* /*env*/, jclass /*clazz*/,
                                                                jint trackIndex, jint fromSlot,
                                                                jint toSlot) {
    SynthEngine::getInstance().getAudioEngine().reorderChannelInserts(trackIndex, fromSlot, toSlot);
}

    // ========== Per-pad synth (multi-timbral) ==========
    
    JNIEXPORT jboolean JNICALL
    Java_com_jujidaw_audio_SynthEngine_nativeSynthNoteOn(JNIEnv* /*env*/, jclass /*clazz*/,
                                                           jint padIndex, jint note, jfloat velocity) {
        auto* synth = SynthEngine::getInstance().getAudioEngine().getPadSynth(padIndex);
        if (!synth) return JNI_FALSE;
        synth->noteOn(static_cast<int>(note), static_cast<int>(velocity * 127.0f + 0.5f));
        return JNI_TRUE;
    }

    JNIEXPORT jboolean JNICALL
    Java_com_jujidaw_audio_SynthEngine_nativeSynthNoteOff(JNIEnv* /*env*/, jclass /*clazz*/,
                                                           jint padIndex, jint note) {
        auto* synth = SynthEngine::getInstance().getAudioEngine().getPadSynth(padIndex);
        if (!synth) return JNI_FALSE;
        synth->noteOff(static_cast<int>(note));
        return JNI_TRUE;
    }
    
    JNIEXPORT jboolean JNICALL
    Java_com_jujidaw_audio_SynthEngine_nativeSetPadSynthParam(JNIEnv* /*env*/, jclass /*clazz*/,
                                                                jint padIndex, jint paramIndex,
                                                                jfloat value) {
        auto* synth = SynthEngine::getInstance().getAudioEngine().getPadSynth(padIndex);
        if (!synth) return JNI_FALSE;
        switch (paramIndex) {
            case 0:  synth->setOsc1Level(value); break;
            case 1:  synth->setOsc2Level(value); break;
            case 2:  synth->setOsc1Waveform(static_cast<int>(value)); break;
            case 3:  synth->setOsc2Waveform(static_cast<int>(value)); break;
            case 4:  synth->setOscDetune(value); break;
            case 5:  synth->setSubOscLevel(value); break;
            case 6:  synth->setNoiseLevel(value); break;
            case 7:  synth->setOscMix(value); break;
            case 8:  synth->setOscSync(value > 0.5f); break;
            case 10: synth->setFilterCutoff(value); break;
            case 11: synth->setFilterResonance(value); break;
            case 12: synth->setFilterMode(static_cast<int>(value)); break;
            case 13: synth->setFilterEnvAmount(value); break;
            case 20: synth->setAmpAttack(value); break;
            case 21: synth->setAmpDecay(value); break;
            case 22: synth->setAmpSustain(value); break;
            case 23: synth->setAmpRelease(value); break;
            case 25: synth->setFilterAttack(value); break;
            case 26: synth->setFilterDecay(value); break;
            case 27: synth->setFilterSustain(value); break;
            case 28: synth->setFilterRelease(value); break;
            case 30: synth->setLfo1Rate(value); break;
            case 31: synth->setLfo1Depth(value); break;
            case 32: synth->setLfo1Waveform(static_cast<int>(value)); break;
            case 35: synth->setLfo2Rate(value); break;
            case 36: synth->setLfo2Depth(value); break;
            case 37: synth->setLfo2Waveform(static_cast<int>(value)); break;
            case 40: synth->setReverbMix(value); break;
            case 41: synth->setReverbDecay(value); break;
            case 42: synth->setDelayMix(value); break;
            case 43: synth->setDelayTime(value); break;
            case 44: synth->setDelayFeedback(value); break;
            case 45: synth->setDistortionDrive(value); break;
            case 46: synth->setDistortionMix(value); break;
            case 47: synth->setEffectsBypass(value > 0.5f); break;
            case 50: synth->setMasterVolume(value); break;
            case 51: synth->setPitchBend(value); break;
            case 52: synth->setModWheel(value); break;
            case 55: synth->setChorusRate(value); break;
            case 56: synth->setChorusDepth(value); break;
            case 57: synth->setChorusMix(value); break;
        }
        return JNI_TRUE;
    }
    
    JNIEXPORT jfloat JNICALL
    Java_com_jujidaw_audio_SynthEngine_nativeGetPadSynthParam(JNIEnv* /*env*/, jclass /*clazz*/,
                                                                jint padIndex, jint paramIndex) {
        auto* synth = SynthEngine::getInstance().getAudioEngine().getPadSynth(padIndex);
        if (!synth) return 0.0f;
        return synth->getParamByIndex(static_cast<int>(paramIndex));
    }
    
    JNIEXPORT void JNICALL
    Java_com_jujidaw_audio_SynthEngine_nativeApplyPadSynthState(JNIEnv* env, jclass /*clazz*/,
                                                                  jint padIndex, jfloatArray values) {
        if (values == nullptr) return;
        int count = env->GetArrayLength(values);
        if (count != SynthInstrument::SYNTH_PARAM_COUNT) return;
        jfloat* elems = env->GetFloatArrayElements(values, nullptr);
        auto* synth = SynthEngine::getInstance().getAudioEngine().getPadSynth(padIndex);
        if (synth) {
            synth->setAllParamsFromArray(elems, count);
        }
        env->ReleaseFloatArrayElements(values, elems, JNI_ABORT);
    }
    
    JNIEXPORT void JNICALL
    Java_com_jujidaw_audio_SynthEngine_nativeSetPadSynthEnabled(JNIEnv* /*env*/, jclass /*clazz*/,
                                                                  jint padIndex, jboolean enabled) {
        // Eagerly create the synth instance so it's ready when triggered.
        if (enabled) {
            SynthEngine::getInstance().getAudioEngine().getPadSynth(padIndex);
        } else if (auto* synth = SynthEngine::getInstance().getAudioEngine().getExistingPadSynth(padIndex)) {
            // A pad changes between mutually-exclusive sample and synth modes.
            // Stop its voices immediately without discarding the stored state,
            // so re-enabling the pad restores its own previous sound.
            synth->panic();
        }
    }
    
    } // extern "C"

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {
    g_jvm = vm;
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    return JNI_VERSION_1_6;
}
