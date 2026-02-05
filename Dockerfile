# Build Clojure application
FROM clojure:tools-deps-trixie AS clj-builder

WORKDIR /app

# Copy Clojure dependencies
COPY deps.edn .
RUN clj -P

# Copy source code
COPY . .

# Build the uberjar
RUN clj -T:build uber

# Create the final, minimal image
FROM eclipse-temurin:21-jre

    # Install D2 and nodejs for Playwright
    RUN apt-get update && apt-get install -y curl make nodejs npm && \
        curl -fsSL https://d2lang.com/install.sh | sh && \
        apt-get remove -y curl make && \
        apt-get autoremove -y && \
        apt-get clean && \
        mkdir -p /root/.cache/ms-playwright-go/1.47.2 && \
        ln -s /usr/bin/node /root/.cache/ms-playwright-go/1.47.2/node && \
        npx playwright install-deps chromium

# Set the working directory
WORKDIR /app

# Copy the uberjar from the clj-builder stage
COPY --from=clj-builder /app/target/d2server.jar .

EXPOSE 3000

# Command to run the application
CMD npx playwright install chromium && java -jar d2server.jar
