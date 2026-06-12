cmake_minimum_required(VERSION 3.22)
project("jujisynth")

# Oboe audio library (local copy)
add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/oboe oboe)

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
    JniBridge.cpp
)

set(HEADERS
    AudioEngine.h
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
)

target_link_libraries(jujisynth oboe)

target_compile_options(jujisynth PRIVATE -Wall -Wno-unused-parameter)
target_compile_features(jujisynth PRIVATE cxx_std_20)
