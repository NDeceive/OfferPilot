"""Build Wav2Lip avatar frames from a contiguous video range, without overlays."""
import argparse
import hashlib
import json
import pickle
from pathlib import Path

import av
import cv2
import numpy as np
import torch

from avatars.wav2lip import face_detection


def write_png(path, image):
    path.write_bytes(cv2.imencode('.png', image, [cv2.IMWRITE_PNG_COMPRESSION, 2])[1].tobytes())


def build(args):
    if not args.avatar_id or any(not (c.isascii() and (c.isalnum() or c in '_-')) for c in args.avatar_id):
        raise ValueError('avatar_id must contain ASCII letters, digits, underscores or hyphens')
    if args.start_frame < 0 or not 0 < args.detect_scale <= 1 or args.face_det_batch_size < 1:
        raise ValueError('Invalid frame range, detection scale or batch size')
    source = Path(args.video_path).resolve(strict=True)
    target = Path('data/avatars') / args.avatar_id
    if target.exists():
        raise FileExistsError(f'Refusing to overwrite an existing avatar: {target}')
    frames = []
    with av.open(str(source)) as container:
        stream = container.streams.video[0]
        if stream.average_rate != 25:
            raise ValueError(f'Expected 25fps source, got {stream.average_rate}')
        for index, frame in enumerate(container.decode(stream)):
            if args.end_frame is not None and index >= args.end_frame:
                break
            if index >= args.start_frame:
                frames.append(frame.to_ndarray(format='bgr24'))
    if len(frames) < 25:
        raise ValueError('At least one second of contiguous source frames is required')
    if args.end_frame is not None and len(frames) != args.end_frame - args.start_frame:
        raise ValueError('Requested range extends beyond source video')
    height, width = frames[0].shape[:2]
    device = 'cuda' if torch.cuda.is_available() else 'cpu'
    detector = face_detection.FaceAlignment(face_detection.LandmarksType._2D, device=device)
    boxes = []
    detect_width = round(width * args.detect_scale)
    detect_height = round(height * args.detect_scale)
    for start in range(0, len(frames), args.face_det_batch_size):
        batch = frames[start:start + args.face_det_batch_size]
        small = np.asarray([cv2.resize(frame, (detect_width, detect_height)) for frame in batch])
        detected = detector.get_detections_for_batch(small)
        for offset, box in enumerate(detected):
            if box is None:
                raise ValueError(f'Face not detected at source frame {args.start_frame + start + offset}')
            x1, y1, x2, y2 = box
            pady1, pady2, padx1, padx2 = args.pads
            x1 = max(0, round(x1 * width / detect_width) - padx1)
            x2 = min(width, round(x2 * width / detect_width) + padx2)
            y1 = max(0, round(y1 * height / detect_height) - pady1)
            y2 = min(height, round(y2 * height / detect_height) + pady2)
            boxes.append((y1, y2, x1, x2))
        if start % 20 == 0:
            print(f'detected {min(start+len(batch),len(frames))}/{len(frames)}', flush=True)
    del detector
    coordinates = []
    for i, box in enumerate(boxes):
        if not args.nosmooth:
            box = np.median(boxes[max(0, i-2):min(len(boxes), i+3)], axis=0).astype(int)
        y1, y2, x1, x2 = map(int, box)
        if not (0 <= y1 < y2 <= height and 0 <= x1 < x2 <= width):
            raise ValueError(f'Invalid face box at frame {i}: {box}')
        coordinates.append((y1, y2, x1, x2))
    full = target / 'full_imgs'
    faces = target / 'face_imgs'
    full.mkdir(parents=True)
    faces.mkdir()
    for i, (frame, box) in enumerate(zip(frames, coordinates)):
        y1, y2, x1, x2 = box
        face = cv2.resize(frame[y1:y2, x1:x2], (args.img_size, args.img_size), interpolation=cv2.INTER_CUBIC)
        write_png(full / f'{i:08d}.png', frame)
        write_png(faces / f'{i:08d}.png', face)
    (target / 'coords.pkl').write_bytes(pickle.dumps(coordinates))
    manifest = {'avatarId': args.avatar_id, 'status': 'candidate; not production enabled',
        'source': str(source), 'sourceSha256': hashlib.sha256(source.read_bytes()).hexdigest(),
        'sourceStartFrame': args.start_frame, 'sourceEndFrameExclusive': args.start_frame+len(frames),
        'frames': len(frames), 'fps': 25, 'width': width, 'height': height,
        'faceSize': args.img_size, 'detectionScale': args.detect_scale,
        'coordinateSmoothing': 'none' if args.nosmooth else 'centered five-frame median',
        'faceWidthPixels': [min(b[3]-b[2] for b in coordinates), max(b[3]-b[2] for b in coordinates)],
        'fullFrameEdits': False, 'frameOrder': 'contiguous forward source frames'}
    (target / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(manifest, ensure_ascii=True, indent=2), flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--img_size', default=256, type=int, choices=[96, 256])
    parser.add_argument('--avatar_id', required=True)
    parser.add_argument('--video_path', required=True)
    parser.add_argument('--start_frame', default=0, type=int)
    parser.add_argument('--end_frame', default=None, type=int, help='Exclusive source-frame boundary')
    parser.add_argument('--nosmooth', action='store_true')
    parser.add_argument('--pads', nargs=4, type=int, default=[0, 10, 0, 0])
    parser.add_argument('--face_det_batch_size', type=int, default=4)
    parser.add_argument('--detect_scale', type=float, default=.5, help='Detection only; full frames stay untouched')
    build(parser.parse_args())
