# Optional connected household server

This Python 3.10+ service has no third-party dependencies. It provides a platform-neutral snapshot API for the Android sync engine. The default app never contacts it until an adult opts in. Deploy it behind an HTTPS reverse proxy on a server you control; the Python listener deliberately binds only `127.0.0.1`.

```sh
python server.py provision --database /private/familyday.sqlite3
python server.py serve --database /private/familyday.sqlite3 --port 8080
```

Provisioning creates a new isolated household and returns one device token exactly once. Treat that token as a password. Do not commit the database or tokens. Configure a TLS reverse proxy to forward the root of your chosen hostname to `127.0.0.1:8080`, preserving the Authorization header. Set a 5.5 MB request limit, sensible connection/time limits and per-IP rate limits. Keep the database on encrypted storage with restricted permissions and maintain operator-managed backups. Do not expose the Python listener directly to the internet. No production deployment has been created by this repository.

In the app, sign in as an adult, open Settings → Connected household and enter the HTTPS origin and device token. The first upload initialises the provisioned server household. To add another device, create a one-use invitation in Settings and use Join with invitation code on that device. Each device has its own local parent PIN. When joining an existing server household, choose Use server version in the conflict prompt to replace the new tablet's initial household; export anything you wish to preserve first.

Connected mode synchronises all plans, imported events, notes, member names, lists and chores with the chosen server. It never sends the parent PIN. Every authorised adult device can read and administer its household; child profiles never hold server credentials. Revoking a device immediately prevents subsequent server requests, but cannot erase the device's offline copy. Use local deletion on that tablet as well. The server-deletion control removes the remote snapshot, all tokens, invitations and operation records. Other devices' existing local copies remain until deleted there.

## API

All authenticated operations use `Authorization: Bearer <device-token>`. The household is derived from the token, never a client-supplied household ID.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/v1/snapshot` | Read version and household snapshot |
| PUT | `/v1/snapshot` | Compare-and-swap using `operation_id`, `base_version`, `snapshot` |
| POST | `/v1/invites` | Create a 10-minute invitation; send `{}` |
| POST | `/v1/redeem` | Exchange `{"invite":"..."}` for a device token; no existing token required |
| GET | `/v1/devices` | List device IDs and revocation state |
| POST | `/v1/revoke` | Revoke `{"device":"..."}` within the same household |
| DELETE | `/v1/household` | Delete all remote household data and credentials |

PUT returns 409 with the current snapshot on a version conflict. Repeating an accepted operation UUID with identical data returns its original accepted version; reusing it with different data is rejected. The server validates public schema and household member references. Responses are marked `no-store`. Request bodies and access tokens are not logged. The built-in limiter is intentionally conservative; configure the edge proxy for your deployment.

Tests: `python -m unittest discover -s backend -v` from the repository root. Production TLS transport and Android-to-server end-to-end sync still need deployment testing. Managed account recovery, email login, remote push and Amazon receipt verification are not part of this service.
