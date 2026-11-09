#!/usr/bin/env python3
"""
Lena voice bridge.

Gives Open WebUI the OpenAI-style speech API it understands, in front of the Wyoming Piper (and,
optionally, Whisper) containers Home Assistant already uses. Lena then speaks with the same voice
everywhere. Standard library only; ffmpeg converts audio formats.

Endpoints (with or without a /v1 prefix):
  POST /audio/speech          {"input": "...", "voice": "...", "response_format": "mp3"}  -> audio
  POST /audio/transcriptions  multipart "file" (+ "language"), or JSON input_audio          -> {"text": ...}
  GET  /audio/voices          your Piper voices, for Open WebUI's voice list
  GET  /audio/models, /models
  GET  /health
"""

import base64
import json
import os
import socket
import subprocess
import tempfile
import traceback
from email.parser import BytesParser
from email.policy import default as email_policy
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PIPER = (os.environ.get("PIPER_HOST", "wyoming-piper"), int(os.environ.get("PIPER_PORT", "10200")))
WHISPER = (os.environ.get("WHISPER_HOST", ""), int(os.environ.get("WHISPER_PORT", "10300")))
DEFAULT_VOICE = os.environ.get("PIPER_VOICE", "").strip()
DEFAULT_LANGUAGE = os.environ.get("WHISPER_LANGUAGE", "").strip()
API_KEY = os.environ.get("API_KEY", "").strip()
PORT = int(os.environ.get("PORT", "8000"))
TIMEOUT = float(os.environ.get("TIMEOUT", "120"))
MAX_UPLOAD = 50 * 1024 * 1024

# Open WebUI's default voice names; these mean "use the bridge's default", not a Piper voice.
OPENAI_VOICES = {"alloy", "ash", "ballad", "coral", "echo", "fable", "nova", "onyx", "sage", "shimmer", "verse"}

FORMATS = {  # response_format -> (ffmpeg output args, content type)
    "mp3": (["-f", "mp3", "-b:a", "128k"], "audio/mpeg"),
    "wav": (["-f", "wav"], "audio/wav"),
    "opus": (["-f", "ogg", "-c:a", "libopus"], "audio/ogg"),
    "aac": (["-f", "adts"], "audio/aac"),
    "flac": (["-f", "flac"], "audio/flac"),
}


def log(*args):
    """Prints at once: the bridge runs in Docker, where buffered output would show up in the container
    log late or not at all.
    """
    print(*args, flush=True)


# ---- Wyoming --------------------------------------------------------------------------------


class Wyoming:
    """One connection to a Wyoming server: JSON header line, then data and payload bytes."""

    def __init__(self, address):
        """Opens a connection to a Wyoming server (Piper or Whisper). One connection per request keeps
        the bridge stateless.
        """
        self.sock = socket.create_connection(address, timeout=TIMEOUT)
        self.reader = self.sock.makefile("rb")

    def __enter__(self):
        """Used with `with`, so the connection always closes."""
        return self

    def __exit__(self, *_):
        """Closes the reader and the socket, even if closing the reader fails."""
        try:
            self.reader.close()
        finally:
            self.sock.close()

    def send(self, event_type, data=None, payload=b""):
        """Sends one Wyoming event: a JSON header line, then its data and binary payload, whose lengths
        the header gives.
        """
        header = {"type": event_type, "version": "1.5.0"}
        data_bytes = json.dumps(data).encode() if data else b""
        if data_bytes:
            header["data_length"] = len(data_bytes)
        if payload:
            header["payload_length"] = len(payload)
        self.sock.sendall(json.dumps(header).encode() + b"\n" + data_bytes + payload)

    def _read(self, n):
        """Reads exactly n bytes, or fails: a short read means the server hung up mid-event."""
        buf = self.reader.read(n)
        if buf is None or len(buf) < n:
            raise ConnectionError("Wyoming server closed the connection")
        return buf

    def receive(self):
        """Reads one Wyoming event and returns (type, data, payload)."""
        line = self.reader.readline()
        if not line:
            raise ConnectionError("Wyoming server closed the connection")
        event = json.loads(line)
        data = dict(event.get("data") or {})
        if event.get("data_length"):
            data.update(json.loads(self._read(event["data_length"])))
        payload = self._read(event["payload_length"]) if event.get("payload_length") else b""
        return event.get("type"), data, payload


def piper_voices():
    """The voices installed in Piper, from its describe event, for Open WebUI's voice list."""
    with Wyoming(PIPER) as w:
        w.send("describe")
        while True:
            event_type, data, _ = w.receive()
            if event_type == "info":
                break
    voices = [v for program in data.get("tts", []) for v in program.get("voices", [])]
    installed = [v for v in voices if v.get("installed", True)]
    return installed or voices


def synthesize(text, voice):
    """Text -> (pcm, rate, width, channels) from Piper."""
    data = {"text": text}
    if voice:
        data["voice"] = {"name": voice}
    pcm = bytearray()
    rate, width, channels = 22050, 2, 1
    with Wyoming(PIPER) as w:
        w.send("synthesize", data)
        while True:
            event_type, d, payload = w.receive()
            if event_type in ("audio-start", "audio-chunk"):
                rate = d.get("rate", rate)
                width = d.get("width", width)
                channels = d.get("channels", channels)
                pcm += payload
            elif event_type == "audio-stop":
                break
            elif event_type == "error":
                raise RuntimeError(d.get("text") or "Piper reported an error")
    if not pcm:
        raise RuntimeError("Piper returned no audio")
    return bytes(pcm), rate, width, channels


def transcribe(pcm16k, language):
    """16 kHz mono 16-bit PCM -> text from Whisper."""
    fmt = {"rate": 16000, "width": 2, "channels": 1}
    with Wyoming(WHISPER) as w:
        w.send("transcribe", {"language": language} if language else None)
        w.send("audio-start", fmt)
        for i in range(0, len(pcm16k), 32000):  # 1 s per chunk
            w.send("audio-chunk", fmt, pcm16k[i:i + 32000])
        w.send("audio-stop")
        while True:
            event_type, d, _ = w.receive()
            if event_type == "transcript":
                return d.get("text", "")
            if event_type == "error":
                raise RuntimeError(d.get("text") or "Whisper reported an error")


# ---- Audio conversion -----------------------------------------------------------------------


def ffmpeg(args, stdin=None):
    """Runs ffmpeg on bytes in memory and returns its output, raising with ffmpeg's own error if it
    fails.
    """
    p = subprocess.run(
        ["ffmpeg", "-hide_banner", "-loglevel", "error", *args],
        input=stdin, capture_output=True, timeout=TIMEOUT,
    )
    if p.returncode != 0:
        raise RuntimeError("ffmpeg: " + p.stderr.decode(errors="replace").strip()[-300:])
    return p.stdout


def encode(pcm, rate, width, channels, response_format):
    """Piper's raw audio in the format the caller asked for (mp3 unless it said otherwise), and its
    MIME type.
    """
    if response_format == "pcm":
        return pcm, "audio/pcm"
    out_args, content_type = FORMATS.get(response_format, FORMATS["mp3"])
    sample = {1: "u8", 2: "s16le", 4: "s32le"}.get(width, "s16le")
    audio = ffmpeg(["-f", sample, "-ar", str(rate), "-ac", str(channels), "-i", "pipe:0", *out_args, "pipe:1"], pcm)
    return audio, content_type


def to_pcm16k(audio):
    # A temp file, not a pipe: some formats (m4a, mp4) need to seek.
    """Any recording (WebM or Ogg from Open WebUI's call mode, WAV from the app, and others) as the 16
    kHz mono 16-bit audio Whisper wants.
    """
    with tempfile.NamedTemporaryFile(suffix=".audio") as f:
        f.write(audio)
        f.flush()
        return ffmpeg(["-i", f.name, "-f", "s16le", "-ar", "16000", "-ac", "1", "pipe:1"])


# ---- HTTP -----------------------------------------------------------------------------------


class Handler(BaseHTTPRequestHandler):
    """The bridge's HTTP API, shaped like OpenAI's audio endpoints so Open WebUI can use it as an
    "OpenAI" speech engine.
    """
    server_version = "LenaVoiceBridge/0.6.15"

    def log_message(self, fmt, *args):
        """Request lines go to the same log, with the caller's address."""
        log("%s %s" % (self.address_string(), fmt % args))

    def _path(self):
        """The request path without its query, with or without the /v1 prefix OpenAI's clients add."""
        path = self.path.split("?", 1)[0].rstrip("/")
        return path[3:] if path.startswith("/v1/") else path

    def _authorized(self):
        """True without an API_KEY, otherwise only with the matching bearer token."""
        if not API_KEY:
            return True
        return self.headers.get("Authorization", "") == f"Bearer {API_KEY}"

    def _send(self, status, body, content_type="application/json"):
        """Sends a response: dicts and lists as JSON, text as UTF-8, bytes as they are."""
        if isinstance(body, (dict, list)):
            body = json.dumps(body).encode()
        elif isinstance(body, str):
            body = body.encode()
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _error(self, status, message):
        """An error in OpenAI's shape, so Open WebUI shows its message."""
        self._send(status, {"error": {"message": message, "type": "bridge_error"}})

    def _body(self):
        """Request body, including chunked uploads (Open WebUI streams audio files that way)."""
        if "chunked" in self.headers.get("Transfer-Encoding", "").lower():
            out = bytearray()
            while True:
                size = int(self.rfile.readline().split(b";")[0].strip() or b"0", 16)
                if size == 0:
                    while self.rfile.readline() not in (b"\r\n", b"\n", b""):
                        pass  # trailers
                    return bytes(out)
                out += self.rfile.read(size)
                self.rfile.readline()  # CRLF after each chunk
                if len(out) > MAX_UPLOAD:
                    raise ValueError("Upload too large")
        length = int(self.headers.get("Content-Length") or 0)
        if length > MAX_UPLOAD:
            raise ValueError("Upload too large")
        return self.rfile.read(length) if length else b""

    def do_GET(self):
        """GET: the voice list (and models) Open WebUI asks for when you choose a voice."""
        if not self._authorized():
            return self._error(401, "Invalid API key")
        path = self._path()
        try:
            if path in ("/health", ""):
                return self._send(200, {"status": "ok"})
            if path == "/audio/voices":
                voices = [{"id": v["name"], "name": v.get("description") or v["name"]} for v in piper_voices()]
                return self._send(200, {"voices": voices})
            if path in ("/audio/models", "/models"):
                models = [{"id": "piper", "name": "Piper"}]
                if WHISPER[0]:
                    models.append({"id": "whisper", "name": "Whisper"})
                return self._send(200, {"models": models, "data": [dict(m, object="model") for m in models]})
            return self._error(404, "Not found")
        except Exception as e:
            log("GET", path, "failed:", e)
            return self._error(502, str(e))

    def do_POST(self):
        """POST: speech (text to audio) and transcriptions (audio to text)."""
        if not self._authorized():
            return self._error(401, "Invalid API key")
        path = self._path()
        try:
            body = self._body()
            if path == "/audio/speech":
                return self._speech(body)
            if path == "/audio/transcriptions":
                return self._transcription(body)
            return self._error(404, "Not found")
        except ValueError as e:
            return self._error(400, str(e))
        except Exception as e:
            log("POST", path, "failed:", e)
            traceback.print_exc()
            return self._error(502, str(e))

    def _speech(self, body):
        """Text to speech: Open WebUI's request to Piper over Wyoming, and Piper's audio back in the
        asked-for format.
        """
        req = json.loads(body or b"{}")
        text = (req.get("input") or "").strip()
        if not text:
            raise ValueError("input is required")
        voice = (req.get("voice") or "").strip()
        if voice.lower() in OPENAI_VOICES:
            voice = ""
        voice = voice or DEFAULT_VOICE
        try:
            pcm, rate, width, channels = synthesize(text, voice)
        except RuntimeError:
            if not voice:
                raise
            log(f"Voice {voice!r} failed; using Piper's default voice")
            pcm, rate, width, channels = synthesize(text, "")
        audio, content_type = encode(pcm, rate, width, channels, (req.get("response_format") or "mp3").lower())
        log(f"speech: {len(text)} chars, voice={voice or 'default'}, {len(audio)} bytes")
        return self._send(200, audio, content_type)

    def _transcription(self, body):
        """Speech to text: the uploaded recording, converted with ffmpeg, to Whisper over Wyoming, and
        its text back as OpenAI's transcription response.
        """
        if not WHISPER[0]:
            return self._error(501, "Speech-to-text isn't set up on this bridge (set WHISPER_HOST)")
        content_type = self.headers.get("Content-Type", "")
        audio, language, response_format = b"", "", "json"
        if content_type.startswith("multipart/"):
            msg = BytesParser(policy=email_policy).parsebytes(
                b"Content-Type: " + content_type.encode() + b"\r\nMIME-Version: 1.0\r\n\r\n" + body
            )
            for part in msg.iter_parts():
                name = part.get_param("name", header="content-disposition")
                value = part.get_payload(decode=True) or b""
                if name == "file":
                    audio = value
                elif name == "language":
                    language = value.decode().strip()
                elif name == "response_format":
                    response_format = value.decode().strip()
        else:
            req = json.loads(body or b"{}")
            audio = base64.b64decode((req.get("input_audio") or {}).get("data") or "")
            language = req.get("language") or ""
            response_format = req.get("response_format") or "json"
        if not audio:
            raise ValueError("file is required")
        text = transcribe(to_pcm16k(audio), language or DEFAULT_LANGUAGE).strip()
        log(f"transcription: {len(audio)} bytes -> {len(text)} chars")
        if response_format == "text":
            return self._send(200, text, "text/plain; charset=utf-8")
        return self._send(200, {"text": text})


def main():
    """Serves until stopped; each request on its own thread, so a long transcription doesn't hold up
    speech.
    """
    log(f"Lena voice bridge on :{PORT}  piper={PIPER[0]}:{PIPER[1]}"
        f"  whisper={'%s:%s' % WHISPER if WHISPER[0] else 'off'}  voice={DEFAULT_VOICE or 'Piper default'}")
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()


if __name__ == "__main__":
    main()
