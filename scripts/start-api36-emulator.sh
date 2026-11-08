#!/usr/bin/env bash
# Start the CI API 36 AVD using the SDK's explicit adb path.
set -Eeuo pipefail

: "${ANDROID_HOME:?ANDROID_HOME must point to the installed Android SDK}"
: "${AVDMANAGER:?AVDMANAGER must point to the Android SDK avdmanager}"

SDK_ROOT="${ANDROID_HOME}"
ADB="${ADB:-${SDK_ROOT}/platform-tools/adb}"
EMULATOR="${SDK_ROOT}/emulator/emulator"
AVD_NAME="timeline-ci-api36"
LOG_FILE="${TIMELINE_EMULATOR_LOG:-/tmp/timeline-emulator.log}"

print_diagnostics() {
  echo "--- adb devices ---" >&2
  if [[ -x "$ADB" ]]; then
    "$ADB" devices -l >&2 || true
  fi
  echo "--- emulator process ---" >&2
  pgrep -af '[e]mulator.*timeline-ci-api36' >&2 || true
  echo "--- emulator log: ${LOG_FILE} (last 200 lines) ---" >&2
  if [[ -f "$LOG_FILE" ]]; then
    tail -n 200 "$LOG_FILE" >&2 || true
  else
    echo "Emulator log was not created." >&2
  fi
}

if [[ ! -x "$ADB" ]]; then
  echo "Android platform-tools adb executable is missing or not executable: $ADB" >&2
  echo "The workflow must install the SDK package 'platform-tools' under $SDK_ROOT." >&2
  if [[ -d "${SDK_ROOT}/platform-tools" ]]; then
    ls -la "${SDK_ROOT}/platform-tools" >&2 || true
  fi
  exit 1
fi
if [[ ! -x "$EMULATOR" ]]; then
  echo "Android emulator executable is missing or not executable: $EMULATOR" >&2
  exit 1
fi
if [[ ! -x "$AVDMANAGER" ]]; then
  echo "Android AVD manager is missing or not executable: $AVDMANAGER" >&2
  exit 1
fi

"$ADB" version
"$EMULATOR" -version

# The runner needs KVM access for practical emulator performance.
echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS="static_node=kvm"' \
  | sudo tee /etc/udev/rules.d/99-kvm4all.rules
sudo udevadm control --reload-rules
sudo udevadm trigger --name-match=kvm

# Recreate the AVD so this job never reuses stale local state.
printf 'no\n' | "$AVDMANAGER" create avd --force \
  --name "$AVD_NAME" \
  --package "system-images;android-36;google_apis;x86_64" \
  --device pixel_6

"$ADB" start-server
nohup "$EMULATOR" -avd "$AVD_NAME" \
  -no-window -no-audio -no-boot-anim \
  -gpu swiftshader_indirect -no-snapshot -camera-back none \
  >"$LOG_FILE" 2>&1 &
EMULATOR_PID=$!

# Wait for adb to enumerate the emulator, while detecting an early emulator exit.
DEVICE_ONLINE=false
for _ in $(seq 1 90); do
  if "$ADB" get-state >/dev/null 2>&1; then
    DEVICE_ONLINE=true
    break
  fi
  if ! kill -0 "$EMULATOR_PID" 2>/dev/null; then
    echo "API 36 emulator process exited before registering with adb (pid $EMULATOR_PID)." >&2
    print_diagnostics
    exit 1
  fi
  sleep 2
done
if [[ "$DEVICE_ONLINE" != true ]]; then
  echo "API 36 emulator did not register with adb within 180 seconds." >&2
  print_diagnostics
  exit 1
fi

# Device enumeration is not boot completion. Allow up to ten minutes for first boot.
BOOTED=false
for _ in $(seq 1 120); do
  BOOT_PROPERTY="$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  if [[ "$BOOT_PROPERTY" == "1" ]]; then
    BOOTED=true
    break
  fi
  if ! kill -0 "$EMULATOR_PID" 2>/dev/null; then
    echo "API 36 emulator process exited during Android boot (pid $EMULATOR_PID)." >&2
    print_diagnostics
    exit 1
  fi
  sleep 5
done
if [[ "$BOOTED" != true ]]; then
  echo "API 36 emulator was visible to adb but sys.boot_completed did not become 1 within 600 seconds." >&2
  print_diagnostics
  exit 1
fi

"$ADB" shell settings put global window_animation_scale 0
"$ADB" shell settings put global transition_animation_scale 0
"$ADB" shell settings put global animator_duration_scale 0
"$ADB" devices -l

echo "API 36 emulator $AVD_NAME booted successfully (pid $EMULATOR_PID)."
