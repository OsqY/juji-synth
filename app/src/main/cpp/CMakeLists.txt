cmake_minimum_required(VERSION 3.22)
project("jujisynth")

# Oboe audio library (local copy)
add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/oboe oboe)

# SoundTouch pitch/time library (local copy)
set(SOUNDTOUCH_SOURCES
    soundtouch/source/AAFilter.cpp
    soundtouch/source/BPMDetect.cpp
    soundtouch/source/FIFOSampleBuffer.cpp
    soundtouch/source/FIRFilter.cpp
    soundtouch/source/InterpolateCubic.cpp
    soundtouch/source/InterpolateLinear.cpp
    soundtouch/source/InterpolateShannon.cpp
    soundtouch/source/PeakFinder.cpp
    soundtouch/source/RateTransposer.cpp
    soundtouch/source/SoundTouch.cpp
    soundtouch/source/TDStretch.cpp
    soundtouch/source/cpu_detect_x86.cpp
    soundtouch/source/mmx_optimized.cpp
    soundtouch/source/sse_optimized.cpp
)

add_library(soundtouch STATIC ${SOUNDTOUCH_SOURCES})

target_include_directories(soundtouch PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/soundtouch/include
    ${CMAKE_CURRENT_SOURCE_DIR}/soundtouch/source
)

target_compile_definitions(soundtouch PRIVATE SOUNDTOUCH_FLOAT_SAMPLES)
target_compile_options(soundtouch PRIVATE -O3 -ffast-math -Wno-unused-parameter)
target_compile_features(soundtouch PRIVATE cxx_std_20)

# Enable ARM Neon when building for ARMv7/ARM64
if(CMAKE_ANDROID_ARCH_ABI STREQUAL "armeabi-v7a" OR CMAKE_ANDROID_ARCH_ABI STREQUAL "arm64-v8a")
    target_compile_definitions(soundtouch PRIVATE SOUNDTOUCH_USE_NEON)
    if(CMAKE_ANDROID_ARCH_ABI STREQUAL "armeabi-v7a")
        target_compile_options(soundtouch PRIVATE -mfpu=neon)
    endif()
endif()

set(SOURCES
    AudioEngine.cpp
    Oscillator.cpp
    Filter.cpp
    Envelope.cpp
    LFO.cpp
    Reverb.cpp
    Delay.cpp
    Distortion.cpp
    Chorus.cpp
    ModulationMatrix.cpp
    Sequencer.cpp
    SynthVoice.cpp
    MixerChannel.cpp
    Bus.cpp
    MasterBus.cpp
    SampleBuffer.cpp
    SamplerVoice.cpp
    SamplerInstrument.cpp
    SynthInstrument.cpp
    AudioRecorder.cpp
    TimeStretchWorker.cpp
    WavWriter.cpp
    EventQueue.cpp
    Transport.cpp
    AudioClipPlayer.cpp
    JniBridge.cpp
)

set(HEADERS
    AudioEngine.h
    Instrument.h
    MixerCommand.h
    MixerChannel.h
    Bus.h
    MasterBus.h
    SampleBuffer.h
    SamplerVoice.h
    SamplerInstrument.h
    SynthInstrument.h
    AudioRecorder.h
    TimeStretchWorker.h
    WavWriter.h
    ScheduledEvent.h
    EventQueue.h
    Transport.h
    AudioClipPlayer.h
    Oscillator.h
    Filter.h
    Envelope.h
    LFO.h
    Reverb.h
    Delay.h
    Distortion.h
    Chorus.h
    ModulationMatrix.h
    Sequencer.h
    SynthVoice.h
    SynthEngine.h
    SynthParams.h
)

add_library(jujisynth SHARED ${SOURCES} ${HEADERS})

target_include_directories(jujisynth PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/oboe/include
    ${CMAKE_CURRENT_SOURCE_DIR}/soundtouch/include
)

target_link_libraries(jujisynth oboe soundtouch)

target_compile_options(jujisynth PRIVATE -Wall -Wno-unused-parameter)
target_compile_features(jujisynth PRIVATE cxx_std_20)

target_compile_options(soundtouch PRIVATE -Wno-unused-parameter)
target_compile_features(soundtouch PRIVATE cxx_std_20)
