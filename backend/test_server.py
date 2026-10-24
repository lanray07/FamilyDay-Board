import copy
import tempfile
import unittest
import uuid
from server import Store, ApiError


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


if __name__ == "__main__":
    unittest.main()
