"""Narrow compatibility shims for the external VoxCPM runtime.

Loaded automatically by Python when this directory is present on PYTHONPATH.
"""

from __future__ import annotations

import importlib
import sys
import tarfile as _stdlib_tarfile
import types


def _install_backports_tarfile() -> None:
    """Provide backports.tarfile for runtimes missing that optional wheel."""
    try:
        backports_pkg = importlib.import_module("backports")
    except Exception:
        backports_pkg = types.ModuleType("backports")
        backports_pkg.__path__ = []  # type: ignore[attr-defined]
        sys.modules.setdefault("backports", backports_pkg)

    sys.modules.setdefault("backports.tarfile", _stdlib_tarfile)
    setattr(backports_pkg, "tarfile", _stdlib_tarfile)


_install_backports_tarfile()
