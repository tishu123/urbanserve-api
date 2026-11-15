# UrbanServe API (Spring Boot)

Requires JDK 17+ and Maven 3.9+.

## Run locally (H2 in-memory, OTP printed to the log)
    mvn clean package
    java -jar target/urbanserve.jar

## Auth flow (phone OTP -> JWT)
    curl -X POST localhost:8080/api/auth/otp/request -H "Content-Type: application/json" -d '{"phone":"9876543210"}'
    # dev: the code appears in the server log as [DEV OTP] ... code=123456
    curl -X POST localhost:8080/api/auth/otp/verify  -H "Content-Type: application/json" -d '{"phone":"9876543210","code":"123456"}'
    # -> {"token":"eyJ...","expiresAt":"...","phone":"9876543210","roles":["CUSTOMER"]}
    TOKEN=eyJ...

## Endpoints
| Method | Path | Auth |
|---|---|---|
| GET | /api/categories, /api/providers?categoryId=&city= | public |
| POST | /api/auth/otp/request, /api/auth/otp/verify | public |
| GET | /api/auth/me  (server confirmation: phone, loggedIn, otpVerified, verifiedVia, loggedInAt, expiresAt) | token |
| POST | /api/bookings  (phone taken from token; starts PENDING until dispatch assigns a pro) | token |
| GET | /api/bookings  (own bookings; admin may add ?phone=) | token |
| GET | /api/bookings/{id}  (own only, admin any) | token |
| PATCH | /api/bookings/{id}/status?status=CANCELLED  (customers: cancel own pending/confirmed; admin: any status) | token |

    curl -X POST localhost:8080/api/bookings -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{
      "customerName":"Asha","address":"12 MG Road","city":"Bengaluru","scheduledAt":"2027-01-15T10:00:00","categoryId":1}'
    curl localhost:8080/api/bookings -H "Authorization: Bearer $TOKEN"

## Configuration (env vars)
| Variable | Purpose |
|---|---|
| APP_JWT_SECRET | HMAC key for JWTs, >= 32 chars. Unset = random per start (dev only). |
| APP_ADMIN_PHONES | Comma-separated phones that get the ADMIN role. |
| APP_OTP_SENDER | `log` (dev, default), `sns` (SMS via Amazon SNS; default in prod profile) or `messagecentral` (Message Central VerifyNow). |
| APP_OTP_SNS_SENDER_ID / _ENTITY_ID / _TEMPLATE_ID | SNS sender ID and India DLT IDs. |
| APP_OTP_MC_CUSTOMER_ID | Message Central customer ID; defaults to `C-DC8D1C144CA446C`. |
| APP_OTP_MC_PASSWORD | Your Message Central password (plain). Put it in `.env`, never in a committed file. |
| APP_OTP_MC_KEY | Alternative to the password: Base64 of it (`printf '%s' 'yourPassword' \| base64`). |
| APP_OTP_MC_AUTH_TOKEN | Optional: an authToken from the console, used instead of APP_OTP_MC_KEY (not auto-refreshed). |
| APP_OTP_MC_FLOW_TYPE | `SMS` (default) or `WHATSAPP`. |
| SPRING_PROFILES_ACTIVE=prod | Use PostgreSQL: DB_URL, DB_USERNAME, DB_PASSWORD. |

## OTP via Message Central VerifyNow (no DLT registration needed)
Easiest: copy `.env.example` to `.env` in this folder, put your Message Central password in it, and
start the app from this folder (`java -jar target/urbanserve.jar`). No `export` commands needed.
`.env` is git-ignored. The customer ID defaults to C-DC8D1C144CA446C.

VerifyNow sends the SMS from Message Central's own DLT-registered sender and template, and checks the
code itself; the app stores only VerifyNow's verification id (never the code, never sent to the browser).
Rate limits, the 5-attempt limit and JWT issuing still run in this app.

    APP_OTP_SENDER=messagecentral \
    APP_OTP_MC_CUSTOMER_ID=C-XXXXXXXXXXXXXXXX \
    APP_OTP_MC_KEY=$(printf '%s' 'yourMessageCentralPassword' | base64) \
    java -jar target/urbanserve.jar

The resend timer shown to the user is the larger of `app.otp.resend-cooldown` and VerifyNow's own
timeout (usually 60 s). Provider outages return 502/503 without using up one of the user's attempts.

Every JWT carries `otp_verified: true` (and `verified_via`), set only by a successful OTP check.
The API rejects any token without it (401), and logs `OTP verified via ...: phone ******1234 is now logged in`.

OTP rules: 6 digits, valid 5 min, 5 wrong attempts max, 30 s resend cooldown, 5 codes/hour per phone.
Codes are stored only as an HMAC.

## Dispatch: assigning a professional
Bookings are matched to a free professional by **urbanserve-dispatch** (it owns the pros' shifts
and schedules). The two services talk only through **Amazon SQS** queues (ElasticMQ on your Mac):

```
POST /api/bookings ──▶ booking PENDING + outbox row (1 transaction)
OutboxRelay ─────────▶ booking-requested ──▶ dispatch: find free pro, reserve the slot
AssignmentHandler ◀── booking-assignment ◀── ASSIGNED {pro, arrival window} | NO_PROVIDER {reason}
   PENDING -> CONFIRMED (pro + arrival window)  or  CANCELLED (statusReason = why)
cancel ──────────────▶ booking-cancelled ──▶ dispatch frees the slot
```

- **No lost messages:** the message is saved with the booking and sent afterwards, retried until SQS accepts it.
- **Duplicates are harmless:** replies only change PENDING bookings; dispatch answers repeats the same way.
- **Cancel during matching:** if dispatch assigns after the customer cancelled, the slot is released again.
- Booking JSON adds `city`, `assignedProvider {id,name,phone,rating}`, `arrivalWindowStart/End`,
  `statusReason`, `preferredProviderId`.

**Run locally with dispatch:** start `urbanserve-infra/compose` (`docker compose up -d --build`: ElasticMQ,
PostgreSQL and dispatch), then uncomment the dispatch lines in `.env` and restart this app.
Without them (`APP_DISPATCH_TRANSPORT=none`, the default) bookings stay PENDING.

| Variable | Purpose |
|---|---|
| APP_DISPATCH_TRANSPORT | `none` (default) or `sqs` |
| APP_SQS_BOOKING_REQUESTED_URL / _CANCELLED_URL / _ASSIGNMENT_URL | Queue URLs (Terraform outputs on AWS) |
| APP_SQS_ENDPOINT | Local only: ElasticMQ, `http://localhost:9324` |

## Database
Schema is managed by Flyway (`src/main/resources/db/migration`). Add changes as new files
(`V2__add_x.sql`); never edit an applied migration. Locally H2 runs in PostgreSQL mode.

Run against a local Postgres:
    docker run -d -p 5432:5432 -e POSTGRES_PASSWORD=dev -e POSTGRES_DB=urbanserve postgres:16
    SPRING_PROFILES_ACTIVE=prod DB_URL=jdbc:postgresql://localhost:5432/urbanserve DB_USERNAME=postgres \
      DB_PASSWORD=dev APP_OTP_SENDER=log java -jar target/urbanserve.jar

H2 console (dev only): http://localhost:8080/h2-console  (JDBC URL jdbc:h2:mem:urbanserve)
