# 36 — Remote Desktop Testing and Performance

## 1. Functional matrix

### Displays
- one monitor;
- multi-monitor;
- DPI 100/125/150/200%;
- 1080p;
- 1440p;
- 4K.

### Input
- click;
- double click;
- right click;
- drag;
- wheel;
- text;
- shortcuts;
- held modifiers.

### Apps
- Explorer;
- browser;
- editor/IDE;
- terminal;
- video playback stress.

---

# 2. Connection

- good Wi-Fi;
- weak Wi-Fi;
- mobile hotspot;
- packet loss;
- high latency;
- temporary disconnect;
- desktop sleep/wake;
- Android app background/foreground.

---

# 3. Latency measurements

Measure:
- capture;
- encode;
- network;
- decode;
- render;
- input RTT.

Prefer instrumented timestamps.

---

# 4. Performance targets

Targets are established by benchmark, not guessed.

Track:
- PC CPU/GPU;
- Android CPU/GPU;
- Android battery;
- bandwidth;
- FPS;
- frame age;
- memory.

---

# 5. Battery test

30-minute Remote Desktop:
- Balanced;
- Data Saver;
- static code editing;
- high motion.

Compare releases.

---

# 6. Failure safety

Test:
- kill Bridge;
- kill Android app;
- remove Wi-Fi;
- revoke device mid-session;
- Windows display changes;
- encoder fails;
- decoder fails.

Expected:
- no stuck held keys;
- control stops;
- resources release.

---

# 7. Security tests

- unpaired connection;
- revoked device;
- replay old input packet;
- malformed coordinate;
- huge key-event flood;
- pairing brute force;
- path/action injection through semantic launcher.

---

# 8. Protected desktop

Verify actual behavior for:
- Windows lock;
- UAC;
- secure prompts.

Document unsupported instead of bypassing.
