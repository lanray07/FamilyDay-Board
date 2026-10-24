import copy
import tempfile
import unittest
import uuid
import json
import threading
import urllib.request
import urllib.error
from http.server import ThreadingHTTPServer
from server import Store, ApiError, Handler


def snapshot(name="A home"):
    return {"format": 1, "household": {"name": name, "zone": "Europe/London", "weekStart": 1, "priority": ""},
            "members": [{"id": "owner", "name": "Alex", "role": "OWNER", "color": 0}], "entries": []}


def packet(base=0, data=None):
    return {"operation_id": str(uuid.uuid4()), "base_version": base, "snapshot": data or snapshot()}


class SyncTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.store = Store(self.temp.name + "/test.sqlite3")
        self.a, self.b = self.store.provision(), self.store.provision()

    def tearDown(self):
        self.temp.cleanup()

    def test_households_are_isolated(self):
        self.store.put(self.a["token"], packet())
        self.assertIsNone(self.store.get(self.b["token"])["snapshot"])
        with self.assertRaises(ApiError) as error:
            self.store.revoke(self.b["token"], self.a["device"])
        self.assertEqual(404, error.exception.status)

    def test_retry_is_idempotent_even_after_newer_updates(self):
        first = packet()
        self.assertEqual((200, {"version": 1}), self.store.put(self.a["token"], first))
        self.store.put(self.a["token"], packet(1, snapshot("Updated")))
        self.assertEqual((200, {"version": 1}), self.store.put(self.a["token"], first))
        self.assertEqual("Updated", self.store.get(self.a["token"])["snapshot"]["household"]["name"])

    def test_conflict_preserves_server_and_returns_both_version_and_snapshot(self):
        self.store.put(self.a["token"], packet())
        status, result = self.store.put(self.a["token"], packet(0, snapshot("Stale")))
        self.assertEqual(409, status)
        self.assertTrue(result["conflict"])
        self.assertEqual("A home", result["snapshot"]["household"]["name"])

    def test_operation_ids_cannot_be_reused_with_different_data(self):
        first = packet(); self.store.put(self.a["token"], first)
        changed = copy.deepcopy(first); changed["snapshot"] = snapshot("Tampered")
        with self.assertRaises(ApiError):
            self.store.put(self.a["token"], changed)

    def test_invite_can_only_be_redeemed_once_and_revocation_is_immediate(self):
        invite = self.store.invite(self.a["token"])["invite"]
        device = self.store.redeem(invite)
        self.store.put(self.a["token"], packet())
        self.assertIsNotNone(self.store.get(device["token"])["snapshot"])
        with self.assertRaises(ApiError):
            self.store.redeem(invite)
        self.store.revoke(self.a["token"], device["device"])
        with self.assertRaises(ApiError):
            self.store.get(device["token"])

    def test_private_pin_or_unknown_snapshot_fields_are_rejected(self):
        data = snapshot(); data["pin"] = "123456"
        with self.assertRaises(ApiError):
            self.store.put(self.a["token"], packet(data=data))

    def test_erase_invalidates_every_device_and_removes_snapshot(self):
        token = self.a["token"]
        self.store.put(token, packet())
        self.store.erase(token)
        with self.assertRaises(ApiError):
            self.store.get(token)

    def test_http_transport_authenticates_before_returning_household_data(self):
        server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        server.store, server.rates, server.rate_lock = self.store, {}, threading.Lock()
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        url = f"http://127.0.0.1:{server.server_port}/v1/snapshot"
        try:
            with self.assertRaises(urllib.error.HTTPError) as error:
                urllib.request.urlopen(url, timeout=5)
            self.assertEqual(401, error.exception.code)
            request = urllib.request.Request(url, data=json.dumps(packet()).encode(), method="PUT", headers={"Authorization": "Bearer " + self.a["token"], "Content-Type": "application/json"})
            with urllib.request.urlopen(request, timeout=5) as response:
                self.assertEqual(1, json.load(response)["version"])
                self.assertEqual("no-store", response.headers["Cache-Control"])
            with urllib.request.urlopen(urllib.request.Request(url, headers={"Authorization": "Bearer " + self.b["token"]}), timeout=5) as response:
                self.assertIsNone(json.load(response)["snapshot"])
        finally:
            server.shutdown(); server.server_close(); thread.join(timeout=5)

    def test_http_returns_conflict_without_overwriting_data(self):
        self.store.put(self.a["token"], packet())
        server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        server.store, server.rates, server.rate_lock = self.store, {}, threading.Lock()
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            request = urllib.request.Request(f"http://127.0.0.1:{server.server_port}/v1/snapshot", data=json.dumps(packet(0, snapshot("Stale"))).encode(), method="PUT", headers={"Authorization": "Bearer " + self.a["token"]})
            with self.assertRaises(urllib.error.HTTPError) as error:
                urllib.request.urlopen(request, timeout=5)
            self.assertEqual(409, error.exception.code)
            self.assertEqual("A home", json.load(error.exception)["snapshot"]["household"]["name"])
        finally:
            server.shutdown(); server.server_close(); thread.join(timeout=5)


if __name__ == "__main__":
    unittest.main()
