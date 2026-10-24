#!/usr/bin/env python3
"""Check that the TV's network ADB port is closed, after turning debugging off (no nc needed).

    adb-closed.py <tv ip[:port]> [port]   port defaults to TV_PORT or 5555; the IP to TV_HOST

Exits 0 when the port is closed: it refuses the connection, or it stays silent while the TV answers on
another port. 1 when it is still open. 2 when the check cannot tell: a name that does not resolve, no
route to the TV, or no answer from the TV on any port, which is what a TV that is off or at another
address looks like.
"""
import concurrent.futures
import os
import socket
import sys

TIMEOUT_S = 3.0
# Ports an Android TV answers while it is on, with debugging off: the Cast receiver (8008, 8009) and the
# Android TV Remote service the Google TV app pairs with (6466, 6467). A connection or a refusal on any of
# them proves the TV is up at this address; only a TV that is off, asleep or elsewhere stays silent on all.
WITNESS_PORTS = (8008, 8009, 6466, 6467)


def answers(host, port):
    """True when host:port connects or refuses within the timeout: either way the host is up."""
    try:
        with socket.create_connection((host, port), timeout=TIMEOUT_S):
            return True
    except ConnectionRefusedError:
        return True
    except OSError:   # a timeout too
        return False


def witness(host, ports):
    """The first of these ports the host answers on, all probed at once; None when it answers on none."""
    with concurrent.futures.ThreadPoolExecutor(max_workers=len(ports)) as pool:
        results = list(pool.map(lambda port: answers(host, port), ports))
    return next((port for port, ok in zip(ports, results) if ok), None)


def split_host_port(endpoint):
    """Split host:port or [ipv6]:port into (host, port_or_none)."""
    if endpoint.startswith("["):
        end = endpoint.find("]")
        if end != -1:
            host = endpoint[1:end]
            rest = endpoint[end + 1:]
            if rest.startswith(":"):
                return host, rest[1:]
            return host, None
    elif endpoint.count(":") == 1:
        host, port = endpoint.split(":", 1)
        return host, port
    return endpoint, None


def main():
    args = sys.argv[1:]
    if len(args) > 2 or args[:1] in (["-h"], ["--help"]):
        sys.stderr.write(__doc__)
        return 2
    raw_host = args[0] if args else os.environ.get("TV_HOST", "")
    if not raw_host:
        sys.stderr.write("usage: adb-closed.py <tv ip> [port]\n")
        return 2
    host, endpoint_port = split_host_port(raw_host)
    if len(args) == 2:
        port_str = args[1]
    elif len(args) == 1 and endpoint_port is not None:
        port_str = endpoint_port
    else:
        port_str = endpoint_port or os.environ.get("TV_PORT") or "5555"

    try:
        port = int(port_str)
        if not (1 <= port <= 65535):
            raise ValueError
    except ValueError:
        sys.stderr.write(f"not a port number: {port_str!r}\n")
        return 2

    try:
        with socket.create_connection((host, port), timeout=TIMEOUT_S):
            pass
    except ConnectionRefusedError:
        print(f"{host}:{port} is closed: the TV refused the connection.")
        return 0
    except socket.timeout:
        # Some firmware drops instead of refusing, but so does a TV that is off or at another address. Only
        # the TV answering somewhere else tells the two apart; silence everywhere proves nothing.
        seen = witness(host, [p for p in WITNESS_PORTS if p != port])
        if seen is not None:
            print(f"{host}:{port} is closed: it did not answer in {TIMEOUT_S:.0f} s while the TV answers on port "
                  f"{seen} (this firmware drops the connection instead of refusing it).")
            return 0
        sys.stderr.write(f"{host}:{port} did not answer in {TIMEOUT_S:.0f} s, and the TV answered on none of the "
                         f"ports it answers while it is on ({', '.join(map(str, WITNESS_PORTS))}). That is what a TV "
                         "that is off, asleep or at another address looks like, so it does not show the port is "
                         "closed: wake the TV, check its address in Settings > Network, and run this again.\n")
        return 2
    except OSError as e:
        sys.stderr.write(f"could not check {host}:{port} ({e}). Is the TV on and on this network?\n")
        return 2
    sys.stderr.write(f"{host}:{port} is still open: network ADB is on. On the TV, turn USB debugging and\n"
                     "Network/ADB debugging off, reboot it, and check again.\n")
    return 1


if __name__ == "__main__":
    sys.exit(main())
