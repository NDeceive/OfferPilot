# -*- coding: utf-8 -*-
"""逐题语音特征提取：Praat(parselmouth) + numpy。

只输出原始量，分数换算交给后端，保证口径在一处。
"""
import math
import wave

import numpy as np

try:
    import parselmouth
except Exception:  # pragma: no cover - 环境没装也不该崩
    parselmouth = None

FRAME_MS = 50
PAUSE_S = 0.8


def _read_wav(path):
    with wave.open(path, "rb") as w:
        sr = w.getframerate()
        ch = w.getnchannels()
        width = w.getsampwidth()
        raw = w.readframes(w.getnframes())
    if width != 2:
        raise ValueError("only 16-bit wav supported")
    data = np.frombuffer(raw, dtype="<i2").astype(np.float32) / 32768.0
    if ch > 1:
        data = data.reshape(-1, ch).mean(axis=1)
    return data, sr


def _frames(x, sr, ms=FRAME_MS):
    n = max(1, int(sr * ms / 1000))
    usable = len(x) - (len(x) % n)
    if usable <= 0:
        return np.zeros((0, n), dtype=np.float32)
    return x[:usable].reshape(-1, n)


def analyze(path):
    x, sr = _read_wav(path)
    duration = len(x) / float(sr) if sr else 0.0
    if duration <= 0.2:
        return {"duration": round(duration, 3), "empty": True}

    frames = _frames(x, sr)
    rms = np.sqrt(np.mean(frames ** 2, axis=1) + 1e-12)
    db = 20 * np.log10(rms + 1e-9)

    # 噪声底取 20 分位，语音帧按中位数往上 8dB 判
    floor = float(np.percentile(db, 20))
    median = float(np.median(db))
    # 取“噪声底 +8dB”和“中位数 -6dB”里更低的那个：纯音/安静录音也能判出有声
    threshold = max(-45.0, min(floor + 8.0, median - 6.0))
    voiced = db > threshold

    frame_s = FRAME_MS / 1000.0
    speech_time = float(voiced.sum()) * frame_s
    silence_time = max(0.0, duration - speech_time)

    pauses, longest, run = 0, 0.0, 0.0
    for v in voiced:
        if not v:
            run += frame_s
        else:
            if run >= PAUSE_S:
                pauses += 1
                longest = max(longest, run)
            run = 0.0
    if run >= PAUSE_S:
        pauses += 1
        longest = max(longest, run)

    voiced_db = db[voiced] if voiced.any() else db
    out = {
        "duration": round(duration, 3),
        "speech_ratio": round(speech_time / duration, 3),
        "pause_count": int(pauses),
        "longest_pause": round(longest, 2),
        "rms_mean_db": round(float(np.mean(voiced_db)), 2),
        "rms_std_db": round(float(np.std(voiced_db)), 2),
        "frame_count": int(len(db)),
        "empty": False,
    }

    if parselmouth is not None:
        try:
            snd = parselmouth.Sound(path)
            pitch = snd.to_pitch(time_step=0.01, pitch_floor=75, pitch_ceiling=500)
            f0 = pitch.selected_array["frequency"]
            f0 = f0[f0 > 0]
            if f0.size:
                out["f0_mean"] = round(float(np.mean(f0)), 1)
                out["f0_std"] = round(float(np.std(f0)), 1)
            point = snd.to_point_process("pitch")
            out["jitter"] = round(float(point.get_jitter_local()), 4)
            out["shimmer"] = round(float(point.get_shimmer_local()), 4)
        except Exception:
            pass

    return out


if __name__ == "__main__":
    import sys
    print(analyze(sys.argv[1]))
