#!/usr/bin/env bash
set -euo pipefail
repo_root=$(cd "$(dirname "$0")/../../../.." && pwd)
jdk_dir=${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")}
test_binary=$(mktemp /tmp/xtmapper-mouse-reader-test.XXXXXX)
trap 'rm -f "$test_binary"' EXIT
cc -std=c11 -Wall -Wextra -pthread \
  -I"$jdk_dir/include" -I"$jdk_dir/include/linux" \
  "$repo_root/app/src/test/native/mouse_reader_test.c" -o "$test_binary"
"$test_binary"
