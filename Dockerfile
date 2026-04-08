# Build Clojure application
FROM clojure:tools-deps-trixie AS clj-builder

WORKDIR /app

COPY deps.edn .
RUN clj -P

COPY . .
RUN clj -T:build uber

# Final image
FROM eclipse-temurin:21-jre

RUN apt-get update && \
    apt-get install -y --no-install-recommends curl make librsvg2-bin \
    fonts-liberation fonts-noto-color-emoji && \
    curl -fsSL https://d2lang.com/install.sh | sh && \
    apt-get remove -y curl make && \
    apt-get autoremove -y && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=clj-builder /app/target/d2server.jar .
COPY --from=clj-builder /app/resources/Agave-Regular-slashed.ttf /usr/share/fonts/Agave-Regular-slashed.ttf
RUN fc-cache -f

EXPOSE 3000

CMD ["java", "-jar", "d2server.jar"]
