FROM node:20-alpine AS builder
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY tsconfig.json ./
COPY src/ ./src/
RUN npm run build

FROM node:20-alpine
WORKDIR /app
RUN apk add --no-cache tini ffmpeg
COPY package.json package-lock.json ./
RUN npm ci --omit=dev
COPY --from=builder /app/dist/ ./dist/
COPY data/ ./data/

# Run as non-root (node user already exists in node:20-alpine as uid 1000)
USER node

EXPOSE 8080
ENTRYPOINT ["/sbin/tini", "--"]
CMD ["node", "--max-old-space-size=200", "dist/index.js"]
