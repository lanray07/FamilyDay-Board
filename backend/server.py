"""Opt-in FamilyDay sync transport. Run behind a TLS reverse proxy; bind loopback only.

No third-party dependencies. Provision owner devices via CLI; child profiles have no
server credentials. Snapshots exclude PINs. Store the database on encrypted storage.
"""
from __future__ import annotations

import argparse
import hashlib
import hmac
import json
import secrets
import sqlite3
import time
import uuid
from contextlib import contextmanager
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from zoneinfo import ZoneInfo

MAX_BODY = 5_500_000


class ApiError(Exception):
    def __init__(self, status, code):
        self.status, self.code = status, code


def token_hash(token):
    return hashlib.sha256(token.encode()).hexdigest()


def validate_snapshot(value):
    """Validate public schema before persistence; no PIN/account data accepted."""
    try:
        if not (set(value) == {"format", "household", "members", "entries"}): raise ValueError("invalid_snapshot")
        if not (value["format"] == 1): raise ValueError("invalid_snapshot")
        home = value["household"]
        if not (set(home) == {"name", "zone", "weekStart", "priority"}): raise ValueError("invalid_snapshot")
        if not (isinstance(home["name"], str) and 0 < len(home["name"]) <= 80): raise ValueError("invalid_snapshot")
        if not (home["weekStart"] in range(1, 8)): raise ValueError("invalid_snapshot")
        if not (isinstance(home["priority"], str) and len(home["priority"]) <= 200): raise ValueError("invalid_snapshot")
        ZoneInfo(home["zone"])
        members = value["members"]
        if not (1 <= len(members) <= 6): raise ValueError("invalid_snapshot")
        if not (sum(m["role"] == "OWNER" for m in members) == 1): raise ValueError("invalid_snapshot")
        ids = {m["id"] for m in members}
        if not (len(ids) == len(members)): raise ValueError("invalid_snapshot")
        for member in members:
            if not (set(member) == {"id", "name", "role", "color"}): raise ValueError("invalid_snapshot")
            if not (member["role"] in {"OWNER", "PARENT", "CARER", "ADULT", "CHILD"}): raise ValueError("invalid_snapshot")
            if not (isinstance(member["name"], str) and 0 < len(member["name"]) <= 50): raise ValueError("invalid_snapshot")
            if not (isinstance(member["id"], str) and 0 < len(member["id"]) <= 100): raise ValueError("invalid_snapshot")
            if not (isinstance(member["color"], int)): raise ValueError("invalid_snapshot")
        entries = value["entries"]
        if not (isinstance(entries, list) and len(entries) <= 10000): raise ValueError("invalid_snapshot")
        if not (len({e["id"] for e in entries}) == len(entries)): raise ValueError("invalid_snapshot")
        allowed = {"id", "kind", "title", "notes", "category", "memberIds", "parentId", "start", "end", "zone", "allDay", "repeat", "excluded", "completedDates", "done", "pinned", "childVisible", "expires", "approvalRequired", "approvedDates", "stars", "position", "reminderMinutes", "author", "updated", "version", "deleted", "every", "rotate", "source"}
        for entry in entries:
            if not (set(entry) <= allowed): raise ValueError("invalid_snapshot")
            if not (isinstance(entry["title"], str) and 0 < len(entry["title"]) <= 200): raise ValueError("invalid_snapshot")
            if not (entry["kind"] in {"EVENT", "NOTE", "CHORE", "LIST", "ITEM"}): raise ValueError("invalid_snapshot")
            if not (len(entry.get("notes", "")) <= 4000): raise ValueError("invalid_snapshot")
            if not (entry["end"] > entry["start"]): raise ValueError("invalid_snapshot")
            if not (entry["repeat"] in {"NONE", "DAILY", "WEEKLY", "MONTHLY", "YEARLY"}): raise ValueError("invalid_snapshot")
            if not (1 <= entry.get("every", 1) <= 365): raise ValueError("invalid_snapshot")
            if not (0 <= entry.get("stars", 0) <= 10): raise ValueError("invalid_snapshot")
            ZoneInfo(entry["zone"])
            if not (not entry.get("memberIds") or set(entry["memberIds"].split(",")) <= ids): raise ValueError("invalid_snapshot")
            if entry["kind"] == "ITEM":
                if not (any(e["id"] == entry["parentId"] and e["kind"] == "LIST" for e in entries)): raise ValueError("invalid_snapshot")
    except (AssertionError, KeyError, TypeError, ValueError) as exc:
        raise ApiError(400, "invalid_snapshot") from exc


class Store:
    def __init__(self, path):
        self.path = str(path)
        with self.db() as db:
            db.executescript("""
                CREATE TABLE IF NOT EXISTS households(id TEXT PRIMARY KEY, version INTEGER NOT NULL DEFAULT 0, snapshot TEXT);
                CREATE TABLE IF NOT EXISTS devices(id TEXT PRIMARY KEY, household TEXT NOT NULL REFERENCES households(id), token_hash TEXT UNIQUE NOT NULL, revoked INTEGER NOT NULL DEFAULT 0);
                CREATE TABLE IF NOT EXISTS operations(household TEXT NOT NULL, id TEXT NOT NULL, payload_hash TEXT NOT NULL, version INTEGER NOT NULL, PRIMARY KEY(household,id));
                CREATE TABLE IF NOT EXISTS invites(hash TEXT PRIMARY KEY, household TEXT NOT NULL, expires REAL NOT NULL, used INTEGER NOT NULL DEFAULT 0);
            """)

    @contextmanager
    def db(self):
        connection = sqlite3.connect(self.path, timeout=10)
        connection.row_factory = sqlite3.Row
        connection.execute("PRAGMA foreign_keys=ON")
        try:
            with connection:
                yield connection
        finally:
            connection.close()

    def provision(self):
        household, device, token = str(uuid.uuid4()), str(uuid.uuid4()), secrets.token_urlsafe(48)
        with self.db() as db:
            db.execute("INSERT INTO households(id) VALUES (?)", (household,))
            db.execute("INSERT INTO devices(id,household,token_hash) VALUES (?,?,?)", (device, household, token_hash(token)))
        return {"household": household, "device": device, "token": token}

    def authenticate(self, db, token):
        if not token or len(token) > 512:
            raise ApiError(401, "unauthorised")
        device = db.execute("SELECT * FROM devices WHERE token_hash=? AND revoked=0", (token_hash(token),)).fetchone()
        if not device:
            raise ApiError(401, "unauthorised")
        return device

    @staticmethod
    def snapshot(db, household):
        row = db.execute("SELECT version,snapshot FROM households WHERE id=?", (household,)).fetchone()
        return {"version": row["version"], "snapshot": json.loads(row["snapshot"]) if row["snapshot"] else None}

    def get(self, token):
        with self.db() as db:
            device = self.authenticate(db, token)
            return self.snapshot(db, device["household"])

    def put(self, token, packet):
        try:
            operation = str(uuid.UUID(packet["operation_id"]))
            base = packet["base_version"]
            if not (isinstance(base, int) and base >= 0): raise ValueError("invalid_snapshot")
            validate_snapshot(packet["snapshot"])
        except (KeyError, ValueError, TypeError, AssertionError) as exc:
            raise ApiError(400, "invalid_operation") from exc
        payload = json.dumps(packet["snapshot"], sort_keys=True, separators=(",", ":"), ensure_ascii=False)
        digest = hashlib.sha256(payload.encode()).hexdigest()
        with self.db() as db:
            db.execute("BEGIN IMMEDIATE")
            device = self.authenticate(db, token)
            household = device["household"]
            prior = db.execute("SELECT * FROM operations WHERE household=? AND id=?", (household, operation)).fetchone()
            if prior:
                if not hmac.compare_digest(prior["payload_hash"], digest):
                    raise ApiError(400, "operation_reused")
                return 200, {"version": prior["version"]}
            current = self.snapshot(db, household)
            if current["version"] != base:
                return 409, dict(current, conflict=True)
            version = base + 1
            db.execute("UPDATE households SET version=?,snapshot=? WHERE id=?", (version, payload, household))
            db.execute("INSERT INTO operations VALUES (?,?,?,?)", (household, operation, digest, version))
            return 200, {"version": version}

    def invite(self, token):
        raw = secrets.token_urlsafe(32)
        with self.db() as db:
            device = self.authenticate(db, token)
            db.execute("INSERT INTO invites VALUES (?,?,?,0)", (token_hash(raw), device["household"], time.time() + 600))
        return {"invite": raw, "expires_in": 600}

    def redeem(self, raw):
        token, device = secrets.token_urlsafe(48), str(uuid.uuid4())
        with self.db() as db:
            db.execute("BEGIN IMMEDIATE")
            row = db.execute("SELECT * FROM invites WHERE hash=? AND used=0 AND expires>?", (token_hash(raw), time.time())).fetchone()
            if not row:
                raise ApiError(401, "invite_invalid")
            db.execute("UPDATE invites SET used=1 WHERE hash=?", (token_hash(raw),))
            db.execute("INSERT INTO devices(id,household,token_hash) VALUES (?,?,?)", (device, row["household"], token_hash(token)))
        return {"device": device, "token": token}

    def devices(self, token):
        with self.db() as db:
            me = self.authenticate(db, token)
            return {"devices": [dict(r) for r in db.execute("SELECT id,revoked FROM devices WHERE household=?", (me["household"],))]}

    def revoke(self, token, device):
        with self.db() as db:
            me = self.authenticate(db, token)
            result = db.execute("UPDATE devices SET revoked=1 WHERE id=? AND household=?", (device, me["household"]))
            if not result.rowcount:
                raise ApiError(404, "device_not_found")
        return {"revoked": True}

    def erase(self, token):
        with self.db() as db:
            me = self.authenticate(db, token)
            household = me["household"]
            for table in ("operations", "invites", "devices"):
                db.execute(f"DELETE FROM {table} WHERE household=?", (household,))
            db.execute("DELETE FROM households WHERE id=?", (household,))
        return {"deleted": True}


class Handler(BaseHTTPRequestHandler):
    server_version = "FamilyDaySync/1"

    def log_message(self, *_):
        pass  # Do not log tokens, bodies, names, or private family data.

    def handle_request(self):
        try:
            # Reverse proxy should additionally enforce a per-IP rate/body limit.
            address = self.client_address[0]
            now = time.monotonic()
            with self.server.rate_lock:
                window, count = self.server.rates.get(address, (now, 0))
                if now - window >= 60:
                    window, count = now, 0
                self.server.rates[address] = (window, count + 1)
                if count >= 120:
                    raise ApiError(429, "rate_limited")
            raw = self.headers.get("Authorization", "")
            token = raw[7:] if raw.startswith("Bearer ") else ""
            packet = {}
            if self.command in {"PUT", "POST"}:
                length = int(self.headers.get("Content-Length", "0"))
                if not 0 < length <= MAX_BODY:
                    raise ApiError(413, "body_limit")
                packet = json.loads(self.rfile.read(length))
            store = self.server.store
            route = self.command, self.path
            if route == ("GET", "/v1/snapshot"):
                status, response = 200, store.get(token)
            elif route == ("PUT", "/v1/snapshot"):
                status, response = store.put(token, packet)
            elif route == ("POST", "/v1/invites"):
                status, response = 200, store.invite(token)
            elif route == ("POST", "/v1/redeem"):
                status, response = 200, store.redeem(packet.get("invite", ""))
            elif route == ("GET", "/v1/devices"):
                status, response = 200, store.devices(token)
            elif route == ("POST", "/v1/revoke"):
                status, response = 200, store.revoke(token, packet.get("device", ""))
            elif route == ("DELETE", "/v1/household"):
                status, response = 200, store.erase(token)
            else:
                raise ApiError(404, "not_found")
        except ApiError as exc:
            status, response = exc.status, {"error": exc.code}
        except (ValueError, TypeError, KeyError):
            status, response = 400, {"error": "invalid_request"}
        except Exception:
            status, response = 500, {"error": "server_error"}
        data = json.dumps(response, separators=(",", ":"), ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    do_GET = do_PUT = do_POST = do_DELETE = handle_request


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["provision", "serve"])
    parser.add_argument("--database", default="familyday.sqlite3")
    parser.add_argument("--port", type=int, default=8080)
    args = parser.parse_args()
    store = Store(Path(args.database))
    if args.command == "provision":
        print(json.dumps(store.provision(), indent=2))  # One-time secret, not an application log.
    else:
        import threading
        server = ThreadingHTTPServer(("127.0.0.1", args.port), Handler)
        server.store, server.rates, server.rate_lock = store, {}, threading.Lock()
        server.serve_forever()


if __name__ == "__main__":
    main()
