FROM ubuntu:24.04

ENV DEBIAN_FRONTEND=noninteractive
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV ANDROID_HOME=/opt/android-sdk
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk
ENV PATH=$PATH:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$JAVA_HOME/bin

RUN apt-get update && \
    apt-get install -y --no-install-recommends \
      ca-certificates \
      libc6-amd64-cross \
      libgcc-s1-amd64-cross \
      libstdc++6-amd64-cross \
      openjdk-17-jdk \
      unzip \
      wget \
      git && \
    mkdir -p /lib64 /lib && \
    ln -sfn /usr/x86_64-linux-gnu/lib64/ld-linux-x86-64.so.2 /lib64/ld-linux-x86-64.so.2 && \
    ln -sfn /usr/x86_64-linux-gnu/lib /lib/x86_64-linux-gnu && \
    ln -sfn /usr/lib/jvm/java-17-openjdk-$(dpkg --print-architecture) /usr/lib/jvm/java-17-openjdk && \
    rm -rf /var/lib/apt/lists/*

RUN mkdir -p ${ANDROID_SDK_ROOT}/cmdline-tools && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O /tmp/commandlinetools.zip && \
    unzip -q /tmp/commandlinetools.zip -d ${ANDROID_SDK_ROOT}/cmdline-tools && \
    mv ${ANDROID_SDK_ROOT}/cmdline-tools/cmdline-tools ${ANDROID_SDK_ROOT}/cmdline-tools/latest && \
    rm -f /tmp/commandlinetools.zip

RUN yes | sdkmanager --licenses && \
    sdkmanager \
      "platform-tools" \
      "platforms;android-36" \
      "build-tools;36.0.0"

WORKDIR /workspace
