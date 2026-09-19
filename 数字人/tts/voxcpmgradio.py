import os
from typing import Any

import requests

from registry import register
from utils.logger import logger
from .base_tts import BaseTTS, State


@register("tts", "voxcpmgradio")
class VoxCPMGradioTTS(BaseTTS):
    def __init__(self, opt, parent):
        super().__init__(opt, parent)
        self.server_url = (opt.TTS_SERVER or "http://127.0.0.1:7860").rstrip("/")
        self.api_root = f"{self.server_url}/gradio_api/run"
        self.speed = float(getattr(opt, "TTS_SPEED", 1.0) or 1.0)
        self.cfg = float(getattr(opt, "TTS_CFG", 2.0) or 2.0)
        self.steps = float(getattr(opt, "TTS_STEPS", 10) or 10)
        self.denoise = bool(getattr(opt, "TTS_DENOISE", False))
        self.timeout = int(getattr(opt, "TTS_TIMEOUT", 180) or 180)
        self.ref_payload, self.ref_text = self._resolve_reference()

    def _post(self, api_name: str, data: list[Any], timeout: int | None = None) -> list[Any]:
        response = requests.post(
            f"{self.api_root}/{api_name}",
            json={"data": data},
            timeout=timeout or self.timeout,
        )
        response.raise_for_status()
        payload = response.json()
        return payload.get("data") or []

    def _resolve_reference(self):
        ref_file = getattr(self.opt, "REF_FILE", "") or ""
        ref_text = getattr(self.opt, "REF_TEXT", None)

        if ref_file and os.path.exists(ref_file):
            if not ref_text:
                raise ValueError("voxcpmgradio requires REF_TEXT when REF_FILE points to a local audio file.")
            return self._file_payload(ref_file), ref_text

        voice_name = ref_file
        if not voice_name or voice_name == "zh-CN-YunxiaNeural":
            voice_name = self._first_voice_name()

        data = self._post("load_voice", [voice_name], timeout=60)
        if len(data) < 2 or not isinstance(data[0], dict) or not data[0].get("path"):
            raise ValueError(f"Failed to load voice preset: {voice_name}")

        logger.info("voxcpmgradio loaded voice preset: %s", voice_name)
        return data[0], data[1]

    def _first_voice_name(self) -> str:
        data = self._post("lambda", [], timeout=30)
        if not data:
            raise ValueError("No voice presets returned by VoxCPM service.")

        update = data[0] or {}
        choices = update.get("choices") or []
        if not choices:
            raise ValueError("No voice choices available from VoxCPM service.")

        first = choices[0]
        if isinstance(first, list) and first:
            return first[0]
        return str(first)

    def _file_payload(self, filepath: str) -> dict[str, Any]:
        return {
            "path": filepath,
            "url": None,
            "size": None,
            "orig_name": os.path.basename(filepath),
            "mime_type": None,
            "is_stream": False,
            "meta": {"_type": "gradio.FileData"},
        }

    def txt_to_audio(self, msg: tuple[str, dict]):
        text, textevent = msg
        if not text:
            return

        try:
            data = self._post(
                "tts_and_update",
                [text, self.ref_payload, self.ref_text, self.speed, self.cfg, self.steps, self.denoise],
            )
        except Exception:
            logger.exception("voxcpmgradio tts request failed")
            return

        if self.state != State.RUNNING:
            return

        if len(data) < 2:
            logger.error("voxcpmgradio returned an unexpected payload: %s", data)
            return

        audio_file = data[0]
        status_text = data[1]
        if not isinstance(audio_file, dict) or not audio_file.get("path"):
            logger.error("voxcpmgradio synthesis failed: %s", status_text)
            return

        datainfo = {"text": text}
        datainfo.update(**textevent)
        self.parent.put_audio_filepath(audio_file["path"], datainfo)

    def stop_tts(self):
        return
