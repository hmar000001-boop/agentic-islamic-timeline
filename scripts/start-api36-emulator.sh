#!/usr/bin/env bash
# Start the CI API 36 AVD using the SDK's explicit adb path.
set -Eeuo pipefail

: "${ANDROID_HOME:?ANDROID_HOME must point to the installed Android SDK}"
: "${AVDMANAGER:?AVDMANAGER must point to the Android SDK avdmanager}"

SDK_ROOT="${ANDROID_HOME}"
ADB="${ADB:-${SDK_ROOT}/platform-tools/adb}"
EMULATOR="${SDK_ROOT}/emulator/emulator"
# -no-window selects the emulator's headless QEMU backend. The GUI binary has
# additional Qt dependencies and is not the executable this job will launch.
QEMU="${SDK_ROOT}/emulator/qemu/linux-x86_64/qemu-system-x86_64-headless"
EMULATOR_LIB_DIR="${SDK_ROOT}/emulator/lib64"
QEMU_GLES_LIB_DIR="${EMULATOR_LIB_DIR}/gles_swiftshader"
QEMU_VULKAN_LIB_DIR="${EMULATOR_LIB_DIR}/vulkan"
QEMU_STDLIB_LIB_DIR="${EMULATOR_LIB_DIR}/libstdc++"
AVD_NAME="timeline-ci-api36"
LOG_FILE="${TIMELINE_EMULATOR_LOG:-/tmp/timeline-emulator.log}"

# Pin every Android tool to one canonical per-user configuration root.
# Modern SDK tools use ANDROID_USER_HOME/ANDROID_AVD_HOME; older avdmanager
# versions derive ~/.android from ANDROID_SDK_HOME. These values deliberately
# resolve to the same directories so AVD creation and emulator lookup cannot
# diverge because of runner-inherited environment variables.
ANDROID_USER_HOME="${HOME}/.android"
ANDROID_EMULATOR_HOME="$ANDROID_USER_HOME"
ANDROID_AVD_HOME="$ANDROID_USER_HOME/avd"
ANDROID_SDK_HOME="$HOME"
unset ANDROID_PREFS_ROOT
export ANDROID_USER_HOME ANDROID_EMULATOR_HOME ANDROID_AVD_HOME ANDROID_SDK_HOME
AVD_INI_PATH="$ANDROID_AVD_HOME/${AVD_NAME}.ini"
AVD_DATA_DIR="$ANDROID_AVD_HOME/${AVD_NAME}.avd"
mkdir -p "$ANDROID_USER_HOME" "$ANDROID_AVD_HOME"

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
if [[ ! -x "$QEMU" ]]; then
  echo "Android emulator headless QEMU executable is missing or not executable: $QEMU" >&2
  exit 1
fi
if [[ ! -d "$EMULATOR_LIB_DIR" ]]; then
  echo "Android emulator SDK bundled library directory is missing: $EMULATOR_LIB_DIR" >&2
  echo "Reinstall the Android SDK 'emulator' package with sdkmanager." >&2
  exit 1
fi
if ! command -v ldd >/dev/null 2>&1; then
  echo "Cannot validate Android emulator host libraries: ldd is unavailable on PATH." >&2
  exit 1
fi
# Mirror the Linux Android Emulator launcher for this exact invocation:
# -no-window selects headless QEMU, which loads lib64, the SwiftShader GLES
# backend, Vulkan ICD libraries, and the bundled libstdc++ directory. It does
# not load the GUI-only lib64/qt tree. AOSP adds these paths before re-execing
# QEMU; ldd must receive the same search path or it falsely reports SDK-owned
# libraries (libc++, libandroid-emu-*, libabseil, etc.) as missing.
QEMU_LIBRARY_PATH="${EMULATOR_LIB_DIR}:${QEMU_GLES_LIB_DIR}:${QEMU_VULKAN_LIB_DIR}:${QEMU_STDLIB_LIB_DIR}"
if [[ -n "${LD_LIBRARY_PATH:-}" ]]; then
  QEMU_LIBRARY_PATH="${QEMU_LIBRARY_PATH}:${LD_LIBRARY_PATH}"
fi
if ! QEMU_DEPENDENCIES="$(LD_LIBRARY_PATH="$QEMU_LIBRARY_PATH" ldd "$QEMU" 2>&1)"; then
  echo "Unable to inspect Android emulator headless QEMU shared-library dependencies: $QEMU" >&2
  printf '%s\n' "$QEMU_DEPENDENCIES" >&2
  exit 1
fi
MISSING_QEMU_LIBRARIES="$(printf '%s\n' "$QEMU_DEPENDENCIES" | awk '/not found/ { print $1 }' | sort -u)"
if [[ -n "$MISSING_QEMU_LIBRARIES" ]]; then
  echo "Android emulator headless QEMU has unresolved shared-library dependencies:" >&2
  printf '  %s\n' "$MISSING_QEMU_LIBRARIES" >&2
  echo "QEMU dependency search path: $QEMU_LIBRARY_PATH" >&2
  echo "If an unresolved library belongs to the Android SDK, reinstall the SDK 'emulator' package." >&2
  echo "If it is a host library, install the Ubuntu package that provides that exact shared object." >&2
  exit 1
fi

echo "Verified Android emulator headless QEMU shared-library dependencies."
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
  --device pixel_6 \
  --path "$AVD_DATA_DIR"

# avdmanager can exit successfully while creating files in a location the
# emulator does not search (for example, when old/new Android home variables
# disagree). Verify both the root registration and the AVD's actual config
# before launching QEMU.
if [[ ! -s "$AVD_INI_PATH" || ! -s "$AVD_DATA_DIR/config.ini" ]]; then
  echo "Android AVD creation did not produce a discoverable $AVD_NAME device." >&2
  echo "Expected AVD registration: $AVD_INI_PATH" >&2
  echo "Expected AVD config: $AVD_DATA_DIR/config.ini" >&2
  echo "Resolved Android user home: $ANDROID_USER_HOME" >&2
  echo "Resolved Android AVD home: $ANDROID_AVD_HOME" >&2
  echo "Resolved legacy Android SDK home: $ANDROID_SDK_HOME" >&2
  echo "--- AVD home contents ---" >&2
  ls -la "$ANDROID_AVD_HOME" >&2 || true
  echo "--- avdmanager list avd ---" >&2
  "$AVDMANAGER" list avd >&2 || true
  exit 1
fi

echo "Verified API 36 AVD registration and configuration under $ANDROID_AVD_HOME."
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
