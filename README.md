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
| GET | /api/auth/me | token |
| POST | /api/bookings  (phone taken from token) | token |
| GET | /api/bookings  (own bookings; admin may add ?phone=) | token |
| GET | /api/bookings/{id}  (own only, admin any) | token |
| PATCH | /api/bookings/{id}/status?status=CANCELLED  (customers: cancel own pending/confirmed; admin: any status) | token |

    curl -X POST localhost:8080/api/bookings -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{
      "customerName":"Asha","address":"12 MG Road","scheduledAt":"2027-01-15T10:00:00","categoryId":1}'
    curl localhost:8080/api/bookings -H "Authorization: Bearer $TOKEN"

## Configuration (env vars)
| Variable | Purpose |
|---|---|
| APP_JWT_SECRET | HMAC key for JWTs, >= 32 chars. Unset = random per start (dev only). |
| APP_ADMIN_PHONES | Comma-separated phones that get the ADMIN role. |
| APP_OTP_SENDER | `log` (dev, default) or `sns` (SMS via Amazon SNS; default in prod profile). |
| APP_OTP_SNS_SENDER_ID / _ENTITY_ID / _TEMPLATE_ID | SNS sender ID and India DLT IDs. |
| SPRING_PROFILES_ACTIVE=prod | Use PostgreSQL: DB_URL, DB_USERNAME, DB_PASSWORD. |

OTP rules: 6 digits, valid 5 min, 5 wrong attempts max, 30 s resend cooldown, 5 codes/hour per phone.
Codes are stored only as an HMAC.

## Database
Schema is managed by Flyway (`src/main/resources/db/migration`). Add changes as new files
(`V2__add_x.sql`); never edit an applied migration. Locally H2 runs in PostgreSQL mode.

Run against a local Postgres:
    docker run -d -p 5432:5432 -e POSTGRES_PASSWORD=dev -e POSTGRES_DB=urbanserve postgres:16
    SPRING_PROFILES_ACTIVE=prod DB_URL=jdbc:postgresql://localhost:5432/urbanserve DB_USERNAME=postgres \
      DB_PASSWORD=dev APP_OTP_SENDER=log java -jar target/urbanserve.jar

H2 console (dev only): http://localhost:8080/h2-console  (JDBC URL jdbc:h2:mem:urbanserve)
