import unittest
from types import SimpleNamespace
from queue import Queue
from threading import Event
from unittest.mock import patch

import cv2
import numpy as np

from avatars.wav2lip_avatar import LipReal
from avatars.base_avatar import AudioFrameData


class CandidateAvatarTest(unittest.TestCase):
    def avatar(self, mode='mouth', loop='forward'):
        avatar = LipReal.__new__(LipReal)
        avatar.opt = SimpleNamespace(face_composite=mode, avatar_loop=loop)
        rng = np.random.default_rng(20)
        avatar.frame_list_cycle = [rng.integers(50, 200, (240, 320, 3), dtype=np.uint8)]
        avatar.coord_list_cycle = [(30, 210, 90, 250)]
        return avatar

    def test_mouth_mode_keeps_original_eyes_background_and_source_frame(self):
        avatar = self.avatar()
        original = avatar.frame_list_cycle[0].copy()
        prediction = np.full((256, 256, 3), 235, dtype=np.float32)
        rendered = avatar.paste_back_frame(prediction, 0)
        np.testing.assert_array_equal(original[:138], rendered[:138])
        np.testing.assert_array_equal(original[:, :90], rendered[:, :90])
        np.testing.assert_array_equal(original[:, 250:], rendered[:, 250:])
        np.testing.assert_array_equal(original[210:], rendered[210:])
        np.testing.assert_array_equal(original, avatar.frame_list_cycle[0])
        self.assertGreater(np.abs(rendered.astype(float)-original).sum(), 0)

    def test_existing_full_face_mode_keeps_legacy_output(self):
        avatar = self.avatar(mode='full')
        prediction = np.arange(256*256*3, dtype=np.uint32).reshape(256,256,3).astype(np.uint8)
        expected = avatar.frame_list_cycle[0].copy()
        expected[30:210,90:250] = cv2.resize(prediction, (160,180))
        np.testing.assert_array_equal(expected, avatar.paste_back_frame(prediction,0))

    def test_forward_cycle_never_reverses_and_inference_uses_same_frame_index(self):
        avatar = self.avatar()
        avatar.frame_list_cycle *= 3
        avatar.face_list_cycle = [np.full((8,8,3), i*50, np.uint8) for i in range(3)]
        avatar.batch_size = 4
        captured = []
        def model(mel, images):
            captured.extend(np.rint(images[:,3,0,0].cpu().numpy()*255).astype(int).tolist())
            return images[:,:3]
        avatar.model = model
        avatar.inference_batch(2, [np.zeros((80,16),np.float32)]*4)
        self.assertEqual(captured, [100,0,50,100])
        self.assertEqual([avatar.get_frame_index(i) for i in range(8)], [0,1,2,0,1,2,0,1])

    def test_default_cycle_remains_mirrored(self):
        avatar = self.avatar(loop='mirror')
        avatar.frame_list_cycle *= 3
        self.assertEqual([avatar.get_frame_index(i) for i in range(8)], [0,1,2,2,1,0,0,1])

    def test_quiet_type_zero_audio_preserves_source_and_has_no_added_watermark(self):
        avatar = self.avatar()
        avatar.opt.audio_silence_threshold = .002
        avatar.recording = False
        avatar.width = avatar.height = 0
        avatar.custom_index = {}
        avatar.res_frame_queue = Queue()
        event = Event()
        output = []
        def push(frame):
            output.append(frame.copy())
            event.set()
        avatar.output = SimpleNamespace(start=lambda: None, stop=lambda: None,
            push_video_frame=push, push_audio_frame=lambda *args: None)
        quiet = [AudioFrameData(np.full(320,.0001,np.float32),type=0) for _ in range(2)]
        avatar.res_frame_queue.put((np.full((256,256,3),230,np.float32),quiet,0))
        avatar.process_frames(event)
        self.assertFalse(avatar.speaking)
        np.testing.assert_array_equal(output[0],avatar.frame_list_cycle[0])

    def test_candidate_bitrate_is_optional_and_clamped_max_allows_requested_rate(self):
        from aiortc.codecs import h264, vpx
        from server.rtc_manager import RTCManager
        with patch.object(h264,'DEFAULT_BITRATE',1000000), patch.object(h264,'MAX_BITRATE',3000000), \
             patch.object(vpx,'DEFAULT_BITRATE',500000), patch.object(vpx,'MAX_BITRATE',1500000):
            RTCManager(SimpleNamespace(video_bitrate=0))
            self.assertEqual(h264.DEFAULT_BITRATE,1000000)
            self.assertEqual(vpx.DEFAULT_BITRATE,500000)
            RTCManager(SimpleNamespace(video_bitrate=4000000))
            self.assertEqual(h264.DEFAULT_BITRATE,4000000)
            self.assertEqual(vpx.DEFAULT_BITRATE,4000000)
            self.assertGreaterEqual(h264.MAX_BITRATE,4000000)


if __name__ == '__main__':
    unittest.main()
