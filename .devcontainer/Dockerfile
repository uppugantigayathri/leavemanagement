FROM maven:3.9.11-eclipse-temurin-17
RUN apt-get update && apt-get install -y --no-install-recommends git curl procps sudo \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --create-home --shell /bin/bash vscode \
    && mkdir -p /home/vscode/.m2 \
    && chown -R vscode:vscode /home/vscode \
    && echo 'vscode ALL=(ALL) NOPASSWD:ALL' > /etc/sudoers.d/vscode \
    && chmod 0440 /etc/sudoers.d/vscode
ENV MAVEN_CONFIG=/home/vscode/.m2
USER vscode
