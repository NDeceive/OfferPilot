"""Validate a candidate pack and render real-model, real-audio comparison clips."""
import argparse
import hashlib
import json
import os
import pickle
from pathlib import Path
import shutil
import subprocess
import sys
import time
from types import SimpleNamespace

import av
import cv2
import numpy as np
import soundfile as sf
import torch

ENGINE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ENGINE))
from avatars.wav2lip_avatar import LipReal, load_model, warm_up
from avatars.wav2lip.audio import melspectrogram


def read(path):
    return cv2.imdecode(np.frombuffer(path.read_bytes(),np.uint8),cv2.IMREAD_COLOR)


def save(path, image):
    path.write_bytes(cv2.imencode('.png',image)[1].tobytes())


def video_writer(path):
    return subprocess.Popen([shutil.which('ffmpeg'),'-hide_banner','-loglevel','error','-y',
        '-f','rawvideo','-pixel_format','bgr24','-video_size','1920x768','-framerate','25','-i','pipe:0',
        '-an','-c:v','libx264','-crf','16','-preset','medium','-pix_fmt','yuv420p','-movflags','+faststart',str(path)],
        stdin=subprocess.PIPE,stderr=subprocess.PIPE)


def run(avatar_id, output):
    os.chdir(ENGINE)
    output=Path(output).resolve();output.mkdir(parents=True,exist_ok=True)
    pack=ENGINE/'data/avatars'/avatar_id
    manifest=json.loads((pack/'manifest.json').read_text(encoding='utf-8'))
    coordinates=pickle.loads((pack/'coords.pkl').read_bytes())
    frames=[read(path) for path in sorted((pack/'full_imgs').glob('*.png'))]
    faces=[read(path) for path in sorted((pack/'face_imgs').glob('*.png'))]
    assert len(frames)==len(faces)==len(coordinates)==manifest['frames']
    for frame,face,box in zip(frames,faces,coordinates):
        assert frame.shape==(768,1920,3) and face.shape==(256,256,3)
        y1,y2,x1,x2=box;assert 0<=y1<y2<=768 and 0<=x1<x2<=1920
    retained=0
    with av.open(manifest['source']) as video:
        for i,frame in enumerate(video.decode(video=0)):
            if manifest['sourceStartFrame']<=i<manifest['sourceEndFrameExclusive']:
                np.testing.assert_array_equal(frame.to_ndarray(format='bgr24'),frames[retained]);retained+=1
    assert retained==len(frames)
    # Inspect source loop boundary without concealing the difference with crossfades.
    first,last=frames[0],frames[-1]
    save(output/'loop-boundary.png',np.hstack([first[100:470,730:1130],last[100:470,730:1130]]))
    loop=float(np.mean(np.abs(first[100:470,730:1130].astype(float)-last[100:470,730:1130])))
    adjacent=[float(np.mean(np.abs(a[100:470,730:1130].astype(float)-b[100:470,730:1130]))) for a,b in zip(frames[:-1],frames[1:])]
    audio_path=ENGINE.parent/'brand-design/digital-human/quality-fix/speech-check.wav'
    voice,sr=sf.read(str(audio_path),dtype='float32');assert sr==16000 and voice.ndim==1
    samples=np.concatenate([np.zeros(16000,np.float32),voice,np.zeros(16000,np.float32)])
    count=int(np.ceil(len(samples)/640));samples=np.pad(samples,(0,count*640-len(samples)))
    sf.write(str(output/'speech-input.wav'),samples,16000,subtype='PCM_16')
    mel=melspectrogram(samples)
    chunks=[mel[:,min(round(i*80/25),mel.shape[1]-16):min(round(i*80/25),mel.shape[1]-16)+16] for i in range(count)]
    avatar=LipReal.__new__(LipReal)
    avatar.frame_list_cycle=frames;avatar.face_list_cycle=faces;avatar.coord_list_cycle=coordinates
    avatar.opt=SimpleNamespace(face_composite='mouth',avatar_loop='forward')
    avatar.model=load_model(str(ENGINE/'models/wav2lip.pth'))
    torch.set_num_threads(4)
    warm_up(4,avatar.model,256)
    if torch.cuda.is_available():torch.cuda.synchronize()
    mouth_writer=video_writer(output/'mouth-video-only.mp4')
    full_writer=video_writer(output/'full-face-video-only.mp4')
    idle_writer=video_writer(output/'idle-loop.mp4')
    evidence=[];elapsed=0;inferred=0;upper_preserved=0;quiet=0
    try:
        for start in range(0,count,4):
            used=min(4,count-start);avatar.batch_size=4
            batch=chunks[start:start+used];batch+=batch[-1:]*(4-used)
            timer=time.perf_counter();predictions=avatar.inference_batch(start,batch)[:used];elapsed+=time.perf_counter()-timer
            assert np.isfinite(predictions).all();inferred+=len(predictions)
            for offset,prediction in enumerate(predictions):
                i=start+offset;idx=avatar.get_frame_index(i);original=frames[idx]
                y1,y2,x1,x2=coordinates[idx]
                rms=max(float(np.sqrt(np.mean(samples[i*640+j:i*640+j+320]**2))) for j in [0,320])
                if rms<=.002:
                    blended=original.copy();full=original.copy();quiet+=1
                else:
                    avatar.opt.face_composite='mouth';blended=avatar.paste_back_frame(prediction,idx)
                    avatar.opt.face_composite='full';full=avatar.paste_back_frame(prediction,idx)
                preserve=np.ones(original.shape[:2],bool);preserve[y1+int((y2-y1)*.60):y2,x1:x2]=False
                np.testing.assert_array_equal(original[preserve],blended[preserve]);upper_preserved+=1
                mouth_writer.stdin.write(blended.tobytes());full_writer.stdin.write(full.tobytes());idle_writer.stdin.write(original.tobytes())
                if i in [0,30,40,50,65,80,100,120,125,135,150,170,190,210]:
                    a=cv2.resize(original[y1:y2,x1:x2],(256,256),interpolation=cv2.INTER_CUBIC)
                    b=cv2.resize(full[y1:y2,x1:x2],(256,256),interpolation=cv2.INTER_CUBIC)
                    c=cv2.resize(blended[y1:y2,x1:x2],(256,256),interpolation=cv2.INTER_CUBIC)
                    row=np.hstack([a,b,c]);cv2.putText(row,f'f{i}: source | full face | mouth blend',(5,22),cv2.FONT_HERSHEY_SIMPLEX,.52,(255,255,255),1);evidence.append(row)
            if start%40==0:print(f'offline inference {start}/{count}',flush=True)
    finally:
        for process in [mouth_writer,full_writer,idle_writer]:
            process.stdin.close();error=process.stderr.read().decode(errors='replace');assert process.wait()==0,error
    for stem,target in [('mouth','speaking-check.mp4'),('full-face','full-face-comparison.mp4')]:
        subprocess.run([shutil.which('ffmpeg'),'-hide_banner','-loglevel','error','-y',
            '-i',str(output/f'{stem}-video-only.mp4'),'-i',str(output/'speech-input.wav'),
            '-c:v','copy','-c:a','aac','-b:a','160k','-shortest','-movflags','+faststart',str(output/target)],check=True)
    for start in range(0,len(evidence),5):save(output/f'face-comparison-{start//5+1}.png',np.vstack(evidence[start:start+5]))
    report={'avatarId':avatar_id,'frames':len(frames),'fullFrameShape':[768,1920,3],'faceShape':[256,256,3],
        'sourceFramePixelEqualityVerified':retained,'nativeFaceWidthPixels':manifest['faceWidthPixels'],
        'realCheckpoint':str(ENGINE/'models/wav2lip.pth'),
        'checkpointSha256':hashlib.sha256((ENGINE/'models/wav2lip.pth').read_bytes()).hexdigest(),
        'audio':str(audio_path),'inputAudioDuration':len(voice)/sr,'testDuration':count/25,
        'inferredFrames':inferred,'inferenceOnlyFps':inferred/elapsed,
        'warmupExcludedFromInferenceTiming':True,
        'quietFramesPreserved':quiet,'framesWithOriginalUpperFaceAndBackgroundVerified':upper_preserved,
        'loopBoundaryHumanMeanAbsoluteDifference':loop,'adjacentHumanDifferenceMedian':float(np.median(adjacent)),
        'adjacentHumanDifferenceMaximum':max(adjacent),'playback':'forward, no source-frame reversal',
        'composite':'mouth-only feathered blend, no mouth geometry squash',
        'status':'offline complete; live playback and visual review required'}
    (output/'offline-validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(report,ensure_ascii=True,indent=2),flush=True)


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--avatar_id',required=True);parser.add_argument('--output',required=True)
    args=parser.parse_args();run(args.avatar_id,args.output)
