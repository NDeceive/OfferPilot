"""Export the actual browser-received WebRTC audio, keeping the final speech segment for review."""
import json
from pathlib import Path

import av
import numpy as np
import soundfile as sf

out = Path(__file__).resolve().parent.parent / 'artifacts' / 'interview-live-debug'
resampler = av.AudioResampler(format='fltp', layout='mono', rate=16000)
pieces = []
with av.open(str(out / 'received-audio.webm')) as source:
    for frame in source.decode(audio=0):
        for converted in resampler.resample(frame):
            pieces.append(converted.to_ndarray().reshape(-1))
samples = np.concatenate(pieces)
sf.write(out / 'received-audio.wav', samples, 16000, subtype='PCM_16')
window = 800
voiced = [i for i in range(0, len(samples) - window, window) if np.sqrt(np.mean(samples[i:i + window] ** 2)) > 0.004]
groups = []
for i in voiced:
    if not groups or i - groups[-1][-1] > 16000:
        groups.append([i])
    else:
        groups[-1].append(i)
segments = []
for group in groups:
    start = max(0, group[0] - 3200)
    end = min(len(samples), group[-1] + window + 4800)
    if end - start > 8000:
        segments.append((start, end))
for label, index in [('closing-output', -1), ('followup-output', -2)]:
    start, end = segments[index]
    sf.write(out / (label + '.wav'), samples[start:end], 16000, subtype='PCM_16')
metrics = {'sampleRate': 16000, 'receivedSeconds': len(samples) / 16000,
           'speechSegmentsSeconds': [round((end - start) / 16000, 2) for start, end in segments],
           'peak': float(np.max(np.abs(samples))), 'closingSeconds': (segments[-1][1] - segments[-1][0]) / 16000}
(out / 'audio-metrics.json').write_text(json.dumps(metrics, indent=2), encoding='utf-8')
assert metrics['closingSeconds'] >= 8, 'closing audio must contain the whole final sentence'
print(json.dumps(metrics))
