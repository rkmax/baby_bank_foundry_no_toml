#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
REPO_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
BUILD_DIR="$SCRIPT_DIR/build/classes"

resolve_javac() {
  if [ -n "${JAVAC_BIN:-}" ] && [ -x "${JAVAC_BIN:-}" ]; then
    printf '%s\n' "$JAVAC_BIN"
    return 0
  fi

  if [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME:-}/bin/javac" ]; then
    printf '%s\n' "$JAVA_HOME/bin/javac"
    return 0
  fi

  if command -v javac >/dev/null 2>&1; then
    command -v javac
    return 0
  fi

  for candidate in \
    /usr/lib/jvm/java-17-openjdk/bin/javac \
    /usr/lib/jvm/default-java/bin/javac
  do
    if [ -x "$candidate" ]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done

  echo "Could not find javac. Set JAVA_HOME or JAVAC_BIN." >&2
  exit 1
}

JAVAC_BIN=$(resolve_javac)
JAVA_BIN=$(CDPATH= cd -- "$(dirname "$JAVAC_BIN")" && pwd)/java

mkdir -p "$BUILD_DIR"

"$JAVAC_BIN" \
  --release 17 \
  --add-modules jdk.httpserver \
  -d "$BUILD_DIR" \
  "$SCRIPT_DIR/BabyBankBackendApplication.java"

exec env \
  BABY_BANK_REPO_ROOT="${BABY_BANK_REPO_ROOT:-$REPO_ROOT}" \
  "$JAVA_BIN" \
  --add-modules jdk.httpserver \
  -cp "$BUILD_DIR" \
  BabyBankBackendApplication
