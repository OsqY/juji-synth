## 1. Fix Velocity Retrigger

- [x] 1.1 In MainSynthScreen.kt, remove `notePressTimes` state variable
- [x] 1.2 Simplify `onNoteOff` callback to just `SynthEngine.noteOff(note)` + `activeNotes = activeNotes - note`
- [x] 1.3 Clean up `onNoteOn` to remove `notePressTimes` tracking (keep activeNotes + SynthEngine.noteOn)
- [x] 1.4 Build and verify notes no longer get stuck

## 2. Improve Voice Stealing

- [x] 2.1 In SynthVoice.h, add `uint64_t age_` field and `uint64_t getAge() const` method
- [x] 2.2 In SynthVoice.cpp noteOn(), increment `age_++`
- [x] 2.3 In AudioEngine.cpp allocateVoice(), replace voice-0 stealing with oldest-voice stealing
- [x] 2.4 Build and verify

## 3. Set Up Git

- [x] 3.1 Create `.gitignore` at project root for Android/Kotlin/C++ project
- [x] 3.2 Run `git init`
- [x] 3.3 Run `git add . && git commit -m "Initial commit: Juji Synth v8"`
