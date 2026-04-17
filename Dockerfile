FROM node:20-alpine AS builder
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY tsconfig.json ./
COPY src/ ./src/
RUN npm run build

FROM node:20-slim
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends tini ffmpeg python3 python3-pip \
    && pip3 install --no-cache-dir --break-system-packages deepfilternet \
    && apt-get clean && rm -rf /var/lib/apt/lists/*
COPY package.json package-lock.json ./
RUN npm ci --omit=dev
COPY --from=builder /app/dist/ ./dist/
COPY data/ ./data/

# Cache directory for TTS/Unsplash/Ekilex disk cache
RUN mkdir -p /app/cache && chown 1000:1000 /app/cache
ENV CACHE_DIR=/app/cache

# Run as non-root (node user exists as uid 1000 in node images)
USER node

EXPOSE 8080
ENTRYPOINT ["/usr/bin/tini", "--"]
CMD ["node", "--max-old-space-size=512", "dist/index.js"]
