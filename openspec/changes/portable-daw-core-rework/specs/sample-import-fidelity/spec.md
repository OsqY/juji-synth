# Spec: Sample Import Fidelity

## Requirements

- **R1** `AudioConverter.writeWavFile` SHALL compute the final (downmixed mono)
  PCM data size BEFORE writing the RIFF/WAVE/fmt/data headers, so that every
  header field (`channels`, `sampleRate`, `byteRate`, `blockAlign`, `dataSize`)
  matches the bytes that follow.
- **R2** For stereo imports, the converter SHALL downmix to mono (average L/R)
  and write `channels = 1` in the header, unless the engine is extended to
  support stereo sample buffers (out of scope here).
- **R3** `SampleBuffer::loadFromWav` SHALL read a WAV whose header `channels`
  and `dataSize` are consistent with the on-disk PCM bytes; the loader MUST
  NOT interpret a mono buffer as stereo interleaved.
- **R4** Imported samples SHALL play back at the original pitch and duration,
  with resampling for keyboard pitch handled only at playback time in
  `SamplerVoice` (unchanged).
- **R5** A round-trip test SHALL exist: import a known stereo kick → load into
  `SampleBuffer` → assert the decoded sample count equals the source frame
  count and the first/last N samples match the source within a tolerance.

## Scenarios

### S1: Stereo kick import plays correctly

- **Given** a stereo 44.1 kHz WAV file `kick.wav` of duration 0.5 s
- **When** the user imports `kick.wav` to pad 0 via `PadsViewModel.importSample`
- **Then** `AudioConverter.convertToWav` produces a mono WAV with `channels=1`
  and `data size = frames * 2` bytes
- **And** `SampleBuffer::loadFromWav` reads `frames` samples (not `frames/2`)
- **And** triggering pad 0 plays the kick at original pitch for ~0.5 s

### S2: Mono import unchanged

- **Given** a mono 44.1 kHz WAV `hat.wav`
- **When** imported to pad 1
- **Then** the resulting WAV header has `channels=1` and the sample plays
  identically to before this change.

### S3: Header/data consistency invariant

- **Given** any imported file
- **When** `SampleBuffer::loadFromWav` parses the WAV
- **Then** `chunkSize` from the header equals the actual `file.gcount()` bytes
  for the data chunk, and `sampleCount == bytesRead / (sizeof(int16_t) *
  channels_)`.
