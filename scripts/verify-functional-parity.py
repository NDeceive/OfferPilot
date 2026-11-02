"""Isolated checks: avatar failure cleanup and portable package dependencies."""
import ast
import asyncio
import logging
from pathlib import Path
import shutil
import tempfile
import typing
import unittest

ROOT = Path(__file__).resolve().parents[1]

class ParityChecks(unittest.IsolatedAsyncioTestCase):
    async def test_failed_avatar_load_releases_placeholder_and_retry_can_succeed(self):
        tree = ast.parse((ROOT / '数字人/server/session_manager.py').read_text(encoding='utf-8-sig'))
        node = next(node for node in tree.body if isinstance(node, ast.ClassDef) and node.name == 'SessionManager')
        scope = dict(asyncio=asyncio, Dict=dict, Optional=typing.Optional, BaseAvatar=object,
                     logger=logging.getLogger('parity-check'), _rand_session_id=lambda: 123456)
        exec(compile(ast.Module(body=[node], type_ignores=[]), 'session_manager.py', 'exec'), scope)
        manager = scope['SessionManager']()
        def fail(*args): raise RuntimeError('intentional load failure')
        manager.init_builder(fail)
        with self.assertRaises(RuntimeError): await manager.create_session({}, 123456)
        self.assertEqual({}, manager.sessions)
        ready = object()
        manager.init_builder(lambda *args: ready)
        self.assertEqual(123456, await manager.create_session({}, 123456))
        self.assertIs(ready, manager.get_session(123456))

    def test_package_includes_launcher_and_voice_service_but_not_installed_python(self):
        tree = ast.parse((ROOT / 'scripts/package-local-deploy.py').read_text(encoding='utf-8-sig'))
        exports = next(ast.literal_eval(node.iter) for node in tree.body if isinstance(node, ast.For)
                       and isinstance(node.target, ast.Name) and node.target.id == 'name' and isinstance(node.iter, ast.List))
        self.assertIn('scripts/dev-start.ps1', exports)
        self.assertIn('语音分析', exports)
        excluded = next(ast.literal_eval(node.value) for node in tree.body if isinstance(node, ast.Assign)
                        and any(isinstance(target, ast.Name) and target.id == 'skip' for target in node.targets))
        copy = next(node for node in tree.body if isinstance(node, ast.FunctionDef) and node.name == 'copy')
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / 'source'; dest = Path(directory) / 'package'
            for name in ['scripts/dev-start.ps1', '语音分析/app.py', '数字人/server/routes.py', '数字人/envs/speech/installed.py']:
                path = root / name; path.parent.mkdir(parents=True, exist_ok=True); path.write_text('fixture', encoding='utf-8')
            scope = dict(ROOT=root, DEST=dest, skip=excluded, shutil=shutil)
            exec(compile(ast.Module(body=[copy], type_ignores=[]), 'package-local-deploy.py', 'exec'), scope)
            for name in ['scripts/dev-start.ps1', '语音分析', '数字人']: scope['copy'](name)
            self.assertTrue((dest / 'scripts/dev-start.ps1').is_file())
            self.assertTrue((dest / '语音分析/app.py').is_file())
            self.assertTrue((dest / '数字人/server/routes.py').is_file())
            self.assertFalse((dest / '数字人/envs').exists())

if __name__ == '__main__': unittest.main()
