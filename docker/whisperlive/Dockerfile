FROM python:3.10-slim

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
        ffmpeg libsndfile1 build-essential portaudio19-dev python3-dev \
    && rm -rf /var/lib/apt/lists/*

RUN pip install --no-cache-dir "whisper-live==0.9.0"

EXPOSE 9090

CMD ["python", "-c", \
     "from whisper_live.server import TranscriptionServer; \
      TranscriptionServer().run('0.0.0.0', port=9090, backend='faster_whisper')"]
