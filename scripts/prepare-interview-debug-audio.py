"""Synthetic test speech only; no real microphone or private recordings."""
import asyncio
from pathlib import Path

import edge_tts
import numpy as np
import resampy
import soundfile as sf

root = Path(__file__).resolve().parent.parent
out = root / "artifacts" / "interview-live-debug"
out.mkdir(parents=True, exist_ok=True)
text = "哈希表会先计算键的哈希值定位数组桶，如果有冲突就比较键。键相同则覆盖值，不同则加入链表；链表较长时可以转成红黑树，降低查询开销。"


async def main():
    await edge_tts.Communicate(text, "zh-CN-YunyangNeural", rate="+15%").save(str(out / "candidate.mp3"))
    samples, rate = sf.read(out / "candidate.mp3")
    if samples.ndim > 1:
        samples = samples.mean(axis=1)
    samples = resampy.resample(samples.astype(np.float32), sr_orig=rate, sr_new=16000)
    sf.write(out / "candidate.wav", samples, 16000, subtype="PCM_16")
    sf.write(out / "microphone.wav", np.concatenate((np.zeros(16000), samples, np.zeros(160000))), 16000, subtype="PCM_16")
    print(f"Synthetic speech: {len(samples) / 16000:.2f}s; peak={np.max(np.abs(samples)):.3f}")


asyncio.run(main())
