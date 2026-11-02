import asyncio
import json
import unittest
from unittest.mock import patch

from avatars.base_avatar import BaseAvatar
from server import routes


class SpeechCompletionTest(unittest.TestCase):
    def test_response_supports_boolean_status_and_existing_flat_fields(self):
        self.assertFalse(json.loads(routes.json_ok(False).body)['data'])
        response = json.loads(routes.json_ok({'closed': True}).body)
        self.assertTrue(response['closed'])
        self.assertTrue(response['data']['closed'])

    def test_completion_tracks_the_requested_audio_end_not_inference_idle(self):
        avatar = BaseAvatar.__new__(BaseAvatar)
        avatar.speaking = False  # Inference can be idle while queued audio is still playing.

        async def status(speech_id=None):
            class Request:
                async def json(self):
                    return {'sessionid': 1, **({'speech_id': speech_id} if speech_id else {})}
            with patch.object(routes, 'get_session', return_value=avatar):
                response = await routes.is_speaking(Request())
            return json.loads(response.body)['data']

        self.assertFalse(asyncio.run(status('closing'))['completed'])
        avatar.notify({'speech_id': 'previous', 'status': 'end'})
        self.assertFalse(asyncio.run(status('closing'))['completed'])
        avatar.notify({'speech_id': 'closing', 'status': 'start'})
        self.assertFalse(asyncio.run(status('closing'))['completed'])
        avatar.notify({'speech_id': 'closing', 'status': 'end'})
        self.assertTrue(asyncio.run(status('closing'))['completed'])
        self.assertFalse(asyncio.run(status()))  # Existing boolean endpoint remains compatible.


if __name__ == '__main__':
    unittest.main()
