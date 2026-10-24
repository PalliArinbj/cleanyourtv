"""tools/adb-closed.py tells an open port from a closed one, on 127.0.0.1.

A port that stays silent cannot be made on 127.0.0.1, so those cases run the script's main() against a
fake network at a TEST-NET address instead: what matters there is that silence alone is never reported
as closed.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import contextlib
import importlib.util
import io
import os
from pathlib import Path
import socket
import subprocess
import sys
import unittest
from unittest import mock


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools" / "adb-closed.py"
TV = "192.0.2.10"   # TEST-NET-1: nothing real answers there, whatever the fake below does


def check(*args, env=None):
    return subprocess.run([sys.executable, "-I", str(SCRIPT), *args],
                          capture_output=True, text=True, timeout=30, env=env)


class FakeNetwork:
    """Stands in for socket.create_connection to one TV: each port connects, refuses or stays silent."""

    def __init__(self, ports, default="silent"):
        self.ports = ports
        self.default = default
        self.tried = []

    def __call__(self, address, timeout=None):
        host, port = address
        self.tried.append(port)
        behaviour = self.ports.get(port, self.default)
        if behaviour == "refuse":
            raise ConnectionRefusedError(f"{host}:{port} refused")
        if behaviour == "silent":
            raise socket.timeout("timed out")
        return contextlib.nullcontext()


def run_main(network, *args):
    spec = importlib.util.spec_from_file_location("adb_closed", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    out, err = io.StringIO(), io.StringIO()
    with mock.patch.object(module.socket, "create_connection", network), \
            mock.patch.object(sys, "argv", ["adb-closed.py", *args]), \
            contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
        code = module.main()
    return code, out.getvalue(), err.getvalue()


class SilentPortTest(unittest.TestCase):
    def test_a_dropped_port_on_a_tv_that_answers_elsewhere_is_closed(self):
        # Firmware that drops instead of refusing: the TV is up (its Cast receiver answers), 5555 is not.
        code, out, err = run_main(FakeNetwork({5555: "silent", 8009: "open"}), TV, "5555")
        self.assertEqual(code, 0, err)
        self.assertIn("is closed", out)
        self.assertIn("port 8009", out)

    def test_a_refusal_elsewhere_also_shows_the_tv_is_up(self):
        code, out, err = run_main(FakeNetwork({5555: "silent", 6466: "refuse"}), TV, "5555")
        self.assertEqual(code, 0, err)
        self.assertIn("port 6466", out)

    def test_a_tv_that_answers_nowhere_is_not_reported_closed(self):
        # Off, asleep or at another address: every port is silent, which used to count as closed.
        network = FakeNetwork({})
        code, out, err = run_main(network, TV, "5555")
        self.assertEqual(code, 2)
        self.assertNotIn("is closed", out)
        self.assertIn("does not show the port is closed", err)
        self.assertEqual(sorted(network.tried), [5555, 6466, 6467, 8008, 8009])

    def test_a_refused_port_needs_no_other_port(self):
        network = FakeNetwork({5555: "refuse"})
        code, out, _ = run_main(network, TV, "5555")
        self.assertEqual(code, 0)
        self.assertIn("refused", out)
        self.assertEqual(network.tried, [5555])


class AdbClosedTest(unittest.TestCase):
    def test_open_port(self):
        with socket.socket() as server:
            server.bind(("127.0.0.1", 0))
            server.listen(1)
            result = check("127.0.0.1", str(server.getsockname()[1]))
        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertIn("still open", result.stderr)

    def test_closed_port(self):
        # Take a free port and let it go again: nothing listens there, so the connection is refused.
        with socket.socket() as probe:
            probe.bind(("127.0.0.1", 0))
            port = probe.getsockname()[1]
        result = check("127.0.0.1", str(port))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("is closed: the TV refused", result.stdout)

    def test_endpoint_with_port(self):
        with socket.socket() as server:
            server.bind(("127.0.0.1", 0))
            server.listen(1)
            port = server.getsockname()[1]
            result = check(f"127.0.0.1:{port}")
        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertIn(f"127.0.0.1:{port} is still open", result.stderr)

    def test_closed_endpoint_with_port(self):
        with socket.socket() as probe:
            probe.bind(("127.0.0.1", 0))
            port = probe.getsockname()[1]
        result = check(f"127.0.0.1:{port}")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn(f"127.0.0.1:{port} is closed: the TV refused", result.stdout)

    def test_endpoint_from_tv_host_env(self):
        with socket.socket() as probe:
            probe.bind(("127.0.0.1", 0))
            port = probe.getsockname()[1]
        env = dict(os.environ, TV_HOST=f"127.0.0.1:{port}")
        result = check(env=env)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn(f"127.0.0.1:{port} is closed", result.stdout)

    def test_cli_port_overrides_endpoint_port(self):
        with socket.socket() as server:
            server.bind(("127.0.0.1", 0))
            server.listen(1)
            port = server.getsockname()[1]
            result = check("127.0.0.1:1", str(port))
        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertIn(f"127.0.0.1:{port} is still open", result.stderr)

    def test_endpoint_port_in_tv_host_env_overrides_tv_port_env(self):
        with socket.socket() as server:
            server.bind(("127.0.0.1", 0))
            server.listen(1)
            open_port = server.getsockname()[1]
            with socket.socket() as probe:
                probe.bind(("127.0.0.1", 0))
                closed_port = probe.getsockname()[1]
            env = dict(os.environ, TV_HOST=f"127.0.0.1:{open_port}", TV_PORT=str(closed_port))
            result = check(env=env)
        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertIn(f"127.0.0.1:{open_port} is still open", result.stderr)

    def test_tv_port_env_used_when_tv_host_has_no_port(self):
        with socket.socket() as server:
            server.bind(("127.0.0.1", 0))
            server.listen(1)
            port = server.getsockname()[1]
            env = dict(os.environ, TV_HOST="127.0.0.1", TV_PORT=str(port))
            result = check(env=env)
        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertIn(f"127.0.0.1:{port} is still open", result.stderr)

    def test_bad_arguments(self):
        self.assertEqual(check("127.0.0.1", "not-a-port").returncode, 2)
        self.assertEqual(check("127.0.0.1:not-a-port").returncode, 2)
        self.assertIn("not a port number: 'not-a-port'", check("127.0.0.1:not-a-port").stderr)
        self.assertEqual(check("[::1]:not-a-port").returncode, 2)
        self.assertEqual(check("127.0.0.1:99999").returncode, 2)
        self.assertEqual(check("a", "b", "c").returncode, 2)


if __name__ == "__main__":
    unittest.main()
