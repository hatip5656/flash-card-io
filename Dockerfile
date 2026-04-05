FROM node:20-alpine AS builder
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY tsconfig.json ./
COPY src/ ./src/
RUN npm run build

FROM node:20-alpine
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --production
COPY --from=builder /app/dist/ ./dist/
COPY data/ ./data/
VOLUME /app/data/db
ENV DB_PATH=/app/data/db/progress.db
CMD ["node", "dist/index.js"]
