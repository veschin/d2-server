# Build Clojure application
FROM clojure:tools-deps-trixie AS clj-builder

WORKDIR /app

COPY deps.edn .
RUN clj -P

COPY . .
RUN clj -T:build uber

# Final image
FROM eclipse-temurin:21-jre

# Install d2, system chromium, and playwright-go runtime deps
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl make chromium nodejs \
    libglib2.0-0t64 libnss3 libnspr4 libatk1.0-0t64 libatk-bridge2.0-0t64 \
    libcups2t64 libdrm2 libxcb1 libxkbcommon0 libatspi2.0-0t64 libx11-6 \
    libxcomposite1 libxdamage1 libxext6 libxfixes3 libxrandr2 libgbm1 \
    libpango-1.0-0 libcairo2 libasound2t64 \
    fonts-liberation fonts-noto-color-emoji && \
    curl -fsSL https://d2lang.com/install.sh | sh && \
    apt-get remove -y curl make && \
    apt-get autoremove -y && \
    rm -rf /var/lib/apt/lists/* && \
    # Point playwright-go to system chromium and node
    mkdir -p /root/.cache/ms-playwright-go/1.47.2 && \
    ln -s /usr/bin/node /root/.cache/ms-playwright-go/1.47.2/node && \
    mkdir -p /root/.cache/ms-playwright/chromium_headless_shell-1208/chrome-linux && \
    ln -s /usr/bin/chromium /root/.cache/ms-playwright/chromium_headless_shell-1208/chrome-linux/headless_shell && \
    mkdir -p /root/.cache/ms-playwright/chromium-1208/chrome-linux && \
    ln -s /usr/bin/chromium /root/.cache/ms-playwright/chromium-1208/chrome-linux/chrome

WORKDIR /app

COPY --from=clj-builder /app/target/d2server.jar .

EXPOSE 3000

CMD ["java", "-jar", "d2server.jar"]
