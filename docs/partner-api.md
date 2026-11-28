# Afiati partner API (medical services)

For an ambulance service, hospital or other medical organisation that wants to receive Afiati help requests and answer them.

## How it works

1. The Afiati owner adds you on the control page and sends you two secrets. They are shown only once.
   - **API key** (`afp_…`): authenticates your calls to the partner API.
   - **Webhook secret** (`whsec_…`): lets you verify that the requests we send you really come from Afiati.
2. An elder presses the help button. Their family is alerted as usual. You receive the request too, when **both** of these hold:
   - the elder turned on «Share my help requests with medical services» in the app;
   - your organisation is active and serves the elder's country.
3. You answer with a status: `acknowledged` (on the way), `arrived`, or `closed`. The elder and the family are notified.

Base URL: `https://afiati-api.alshayeksa.workers.dev`. The current address is also given as `api_url` in `https://afiati.alshaye.ai/v1/app-config`; when `api_url` is null, use the base URL above.

## Webhook (Afiati → you)

If you gave a webhook URL, we send `POST <your URL>` with a JSON body:

```json
{
  "event": "help.created",
  "sent_at": "2026-11-28T09:15:02.120Z",
  "help": {
    "id": "6f1c…",
    "status": "open",
    "created_at": "2026-11-28T09:15:01.900Z",
    "resolved_at": null,
    "message": "I fell",
    "patient": { "name": "…", "phone": "05…", "gender": "male" },
    "location": { "latitude": 24.71, "longitude": 46.67, "accuracy_m": 12, "at": "…", "map_url": "https://www.google.com/maps?q=24.71,46.67" },
    "family_responder": null
  }
}
```

### Fields

- `location` is `null` until the phone has a position.
- `family_responder` is set once a relative tapped «I'm coming».
- `help.status` is one of `open`, `acknowledged` (a relative answered), `resolved` (the elder is fine), or `cancelled` (pressed by mistake).

### Events

| Event | When |
|---|---|
| `help.created` | The elder pressed the button. Pressing again while the request is still open sends it again with the same `id`. |
| `help.location` | The location arrived a few seconds after the request. |
| `help.resolved` | The elder confirmed they are fine, or cancelled the request. |

### Headers

| Header | Value |
|---|---|
| `X-Afiati-Event` | The event name |
| `X-Afiati-Timestamp` | Unix seconds |
| `X-Afiati-Signature` | `sha256=` followed by the hex HMAC-SHA256 of `<timestamp>.<raw body>`, keyed with your webhook secret |

To check a request:
- Compute the same HMAC and compare it in constant time.
- Reject timestamps more than 5 minutes old.

### Delivery

- Answer with any 2xx within 8 seconds.
- A failed `help.created` is retried every minute, 6 times at most, for up to an hour while the request is still active.
- Treat events as idempotent by `help.id`.

## Partner API (you → Afiati)

Every call sends `Authorization: Bearer <API key>`. Each key may make up to 600 requests per minute.

| Call | Purpose |
|---|---|
| `GET /v1/partner/ping` | Check the key. Returns your name and countries. |
| `GET /v1/partner/help/active` | Active requests sent to you in the last 12 hours, newest first. Use it if you have no webhook. |
| `GET /v1/partner/help/{id}` | The latest state of one request, with the same `help` object as the webhook. |
| `POST /v1/partner/help/{id}/status` | Your answer (see below). |

The body of a status call:

```json
{ "status": "acknowledged", "eta_minutes": 12, "note": "Unit 4 dispatched" }
```

- `status`: `acknowledged`, `arrived` or `closed`.
- `eta_minutes`: optional, from 0 to 600.
- `note`: optional, up to 300 characters.

What each status does:
- `acknowledged`: the elder gets «<your name> is on the way», and the family is told.
- `arrived`: the family is told.
- `closed`: ends your part. You receive no more events for this request.

Errors are JSON: `{ "error": "<code>", "message": "…" }`.

| Status | Code | Meaning |
|---|---|---|
| 401 | `unauthorized` | Bad, rotated or deactivated key |
| 404 | `help_not_found` | The request does not exist, or was not sent to you |
| 400 | `invalid_field` | A field is missing or invalid |
| 429 | `rate_limited` | Too many requests |

## Privacy

You receive the elder's name, phone, message and location only for requests they chose to share, and only while your organisation is active. Use them only to respond to the emergency.
