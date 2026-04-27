FROM mcr.microsoft.com/devcontainers/java:1-17-bookworm

RUN rm -f /etc/apt/sources.list.d/yarn.list /etc/apt/sources.list.d/yarn.list.save \
    && apt-get update \
    && apt-get install -y --no-install-recommends maven aria2 qbittorrent-nox \
    && rm -rf /var/lib/apt/lists/*
