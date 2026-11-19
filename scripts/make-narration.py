#!/usr/bin/env python3
"""
Generates natural narration for the video ad with ElevenLabs or OpenAI text-to-speech.

The API key is read from ~/.openingcoach-tts.env (never from this repo), one of:
    ELEVENLABS_API_KEY=...        (optional: ELEVENLABS_VOICE_ID=...)
    OPENAI_API_KEY=...            (optional: OPENAI_VOICE=ash)

Writes frontend/public/ad/audio/n1.wav ... n10.wav and narration.json (start time and length of
each line). Each line must fit before the next scene starts; if one runs long, it is regenerated
a little faster.

Usage: python3 scripts/make-narration.py
"""
import json
import os
import re
import subprocess
import sys
import tempfile
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "frontend" / "public" / "ad" / "audio"
KEYFILE = Path.home() / ".openingcoach-tts.env"

# id, start (s), latest end (s), text
LINES = [
    ("n1", 0.6, 4.4, "Every game starts the same way."),
    ("n2", 4.9, 7.35, "So why do you keep losing by move ten?"),
    ("n3", 7.45, 9.5, "Thirty-eight percent in the Sicilian."),
    ("n4", 9.9, 13.7, "Meet OpeningCoach. Don't let your opening checkmate you."),
    ("n5", 14.1, 19.5, "It maps every opening you play, and compares it with nearly twenty-two thousand strong games."),
    ("n6", 19.8, 25.0, "It finds the lines where you keep getting forked, and shows exactly how many points they cost you."),
    ("n7", 25.3, 30.5, "Then it builds a study plan that fits your time. One hour fixes more than half."),
    ("n8", 30.8, 35.8, "Got a big game coming up? Scout your rival, and steer into the lines where they struggle."),
    ("n9", 36.1, 40.6, "And learn from the legends. Carlsen. Kasparov. Fischer. Morphy."),
    ("n10", 41.0, 44.7, "OpeningCoach. Make your move."),
]


def load_keys():
    if not KEYFILE.exists():
        sys.exit(f"Create {KEYFILE} with ELEVENLABS_API_KEY=... or OPENAI_API_KEY=... first.")
    env = {}
    for line in KEYFILE.read_text().splitlines():
        if "=" in line and not line.strip().startswith("#"):
            k, v = line.split("=", 1)
            env[k.strip()] = v.strip().strip('"').strip("'")
    return env


def post(url, headers, body):
    # curl uses the system's certificate store; headers (with the key) go through a private temp
    # file so the key never shows up in the process list
    with tempfile.TemporaryDirectory() as tmp:
        hfile, out = Path(tmp) / "h", Path(tmp) / "out"
        hfile.write_text("".join(f"{k}: {v}\n" for k, v in headers.items()))
        os.chmod(hfile, 0o600)
        r = subprocess.run(["curl", "-sS", "-X", "POST", url, "-H", f"@{hfile}", "--data-binary", "@-",
                            "-o", str(out), "-w", "%{http_code}", "--max-time", "90"],
                           input=json.dumps(body).encode(), capture_output=True)
        code = r.stdout.decode().strip()
        data = out.read_bytes() if out.exists() else b""
        if code != "200":
            sys.exit(f"TTS request failed ({code or r.stderr.decode()[:200]}): {data.decode(errors='replace')[:300]}")
        return data


def elevenlabs(env, text, speed):
    voice = env.get("ELEVENLABS_VOICE_ID", "JBFqnCBsd6RMkjVDRZzb")  # "George": warm, mature narrator
    return post(
        f"https://api.elevenlabs.io/v1/text-to-speech/{voice}?output_format=mp3_44100_128",
        {"xi-api-key": env["ELEVENLABS_API_KEY"], "Content-Type": "application/json", "Accept": "audio/mpeg"},
        {"text": text, "model_id": "eleven_multilingual_v2",
         "voice_settings": {"stability": 0.45, "similarity_boost": 0.8, "style": 0.35, "use_speaker_boost": True,
                            "speed": speed}},
    ), "mp3"


def openai(env, text, speed):
    voice = env.get("OPENAI_VOICE", "ash")
    body = {"model": "gpt-4o-mini-tts", "voice": voice, "input": text, "response_format": "mp3",
            "instructions": "Warm, confident, upbeat narrator for a short product ad about a chess app. "
                            "Natural pacing, friendly energy, clear diction, no exaggeration."
                            + (" Speak a little faster than usual." if speed > 1.0 else "")}
    return post(
        "https://api.openai.com/v1/audio/speech",
        {"Authorization": f"Bearer {env['OPENAI_API_KEY']}", "Content-Type": "application/json"},
        body,
    ), "mp3"


def duration(path):
    info = subprocess.run(["afinfo", str(path)], capture_output=True, text=True).stdout
    return float(re.search(r"estimated duration: ([0-9.]+)", info).group(1))


def main():
    env = load_keys()
    if env.get("ELEVENLABS_API_KEY"):
        engine, name = elevenlabs, "ElevenLabs"
    elif env.get("OPENAI_API_KEY"):
        engine, name = openai, "OpenAI"
    else:
        sys.exit(f"{KEYFILE} has no ELEVENLABS_API_KEY or OPENAI_API_KEY.")
    print(f"Using {name}")
    OUT.mkdir(parents=True, exist_ok=True)
    meta = []
    with tempfile.TemporaryDirectory() as tmp:
        for lid, start, end, text in LINES:
            slot, speed = end - start, 1.0
            for _ in range(2):  # at most one retry per line, to save free-plan credits
                audio, ext = engine(env, text, speed)
                src = Path(tmp) / f"{lid}.{ext}"
                src.write_bytes(audio)
                dur = duration(src)
                if dur <= slot - 0.1 or speed >= 1.19:
                    break
                speed = min(1.2, round(speed * dur / (slot - 0.15) + 0.02, 2))
            subprocess.run(["afconvert", "-f", "WAVE", "-d", "LEI16@48000", str(src), str(OUT / f"{lid}.wav")], check=True)
            fits = "ok" if dur <= slot else f"TOO LONG by {dur - slot:.2f}s"
            print(f"{lid}: {dur:.2f}s of {slot:.2f}s at speed {speed} ({fits})")
            meta.append({"id": lid, "start": start, "dur": round(dur, 2), "speed": speed, "text": text})
    (OUT / "narration.json").write_text(json.dumps(meta, indent=1))
    print("Done. Re-render the ad to use the new voice.")


if __name__ == "__main__":
    main()
