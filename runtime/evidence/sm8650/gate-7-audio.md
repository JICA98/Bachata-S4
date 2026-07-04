# Gate 7 Audio Evidence

- Device profile: OnePlus Pad 2, Snapdragon 8 Gen 3 (SM8650)
- Runtime SHA-256: `23fe7b4f514da0882de8057f635f49a21a8b7081f51851aa659cccc0a3c3493a`
- Game: legally supplied Sonic Mania (`CUSA07023`)
- Guest format observed: 48 kHz, stereo float, 1024-frame buffers
- Runtime result: `Opened Bachata audio transport (48000 Hz, 2 input channels)`
- Android result: FAST `AudioTrack` created with 4096 frames
- AudioFlinger raw underruns during validation: partial `0`, empty `0`
- Lifecycle: stop and relaunch created a fresh transport and `AudioTrack`
- Controller and rendered frame remained functional during playback
- Serial numbers and user paths excluded
