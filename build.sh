#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

echo "==== 智能静音 App 构建脚本 ===="

# 1. 检查 JDK 17+
if java -version 2>&1 | grep -qE 'version "1\.(7|8|9|1[0-6])'; then
  echo "✗ 需要 JDK 17+，当前: $(java -version 2>&1 | head -1)"
  echo "  安装: brew install --cask temurin@17"
  echo "  然后设置: export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home"
  exit 1
fi
echo "✓ JDK 可用: $(java -version 2>&1 | head -1)"

# 2. 确保 gradle wrapper
if [ ! -x ./gradlew ]; then
  echo "→ 未找到 gradlew，尝试用本机 gradle 生成 wrapper..."
  if ! command -v gradle >/dev/null 2>&1; then
    echo "→ 安装 gradle（brew install gradle）..."
    brew install gradle
  fi
  gradle wrapper --gradle-version 8.9
fi

# 3. 构建 debug 包
./gradlew :app:assembleDebug

APK="$(pwd)/app/build/outputs/apk/debug/app-debug.apk"
echo ""
echo "==== 构建完成 ===="
echo "APK: $APK"
echo "将 APK 传到手机点击安装即可（首次安装需允许“未知来源应用”）"
