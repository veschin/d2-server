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
FROM eclipse-temurin:21-jre-alpine

    # Install D2
    RUN apk add --no-cache curl make && \
        curl -fsSL https://d2lang.com/install.sh | sh && \
        apk del curl make

# Set the working directory
WORKDIR /app

# Copy the uberjar from the clj-builder stage
COPY --from=clj-builder /app/target/d2server.jar .

EXPOSE 3000

# Command to run the application
CMD ["java", "-jar", "d2server.jar"]
