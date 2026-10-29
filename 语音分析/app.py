# -*- coding: utf-8 -*-
"""语音分析边车：stdlib HTTP，无第三方 web 框架。

POST /analyze  {"path": "绝对路径"} -> 指标 JSON
GET  /health                          -> ok
"""
import json
import os
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from analyzer import analyze  # noqa: E402

PORT = int(os.environ.get("VOICE_PORT", "8020"))


class Handler(BaseHTTPRequestHandler):
    def _send(self, code, payload):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path.startswith("/health"):
            self._send(200, {"ok": True})
        else:
            self._send(404, {"error": "not found"})

    def do_POST(self):
        if not self.path.startswith("/analyze"):
            return self._send(404, {"error": "not found"})
        try:
            length = int(self.headers.get("Content-Length", "0"))
            raw = self.rfile.read(length) or b"{}"
            # 客户端可能用 GBK 编码发中文路径，先按 utf-8 解，失败再退 gbk
            try:
                text = raw.decode("utf-8")
            except UnicodeDecodeError:
                text = raw.decode("gbk", "replace")
            payload = json.loads(text)
            path = payload.get("path")
            if not path or not os.path.isfile(path):
                return self._send(400, {"error": "file not found", "path": str(path), "cwd": os.getcwd()})
            self._send(200, analyze(path))
        except Exception as exc:  # noqa: BLE001
            self._send(500, {"error": str(exc)})

    def log_message(self, fmt, *args):
        pass


if __name__ == "__main__":
    print("voice analyzer listening on %d" % PORT, flush=True)
    ThreadingHTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
