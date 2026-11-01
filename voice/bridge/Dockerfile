FROM python:3.13-alpine

# ffmpeg converts Piper's raw audio to mp3, and your recordings to what Whisper reads.
RUN apk add --no-cache ffmpeg

WORKDIR /app
COPY bridge.py .

ENV PYTHONUNBUFFERED=1
USER nobody
EXPOSE 8000
HEALTHCHECK --interval=60s --timeout=5s CMD wget -qO- http://127.0.0.1:8000/health || exit 1
CMD ["python", "/app/bridge.py"]
