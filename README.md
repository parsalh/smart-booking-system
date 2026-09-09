# SmartBooking

A smart meeting room booking system built on Google Calendar. Pick a
meeting's details, get AI-assisted time and room suggestions, invite
participants (registered or not), and let SmartBooking keep your real
Google Calendar in sync automatically.

**Live app:** [book-a-meeting.site](https://book-a-meeting.site)
**Repository:** [github.com/parsalh/smart-booking-system](https://github.com/parsalh/smart-booking-system)

Built as a final year project at Harokopio University of Athens (HUA).

---

## What it does

SmartBooking sits on top of your Google Calendar rather than replacing it.
When you book a meeting room through the app, a real event is created on
your Google Calendar, participants receive real Google invites, and RSVP
responses stay in sync in both directions. Accept or decline from Gmail or
from SmartBooking, either way the other one reflects it.

### Core features

- **Smart booking wizard**: describe the meeting, and SmartBooking scores
  candidate time slots (favoring late morning and mid-afternoon, avoiding the
  lunch hour, weighing how soon the slot is available) and recommends rooms
  ranked by capacity fit and requested amenities.
- **Google Calendar as the source of truth**: every SmartBooking meeting is
  a real Google Calendar event. Declining, accepting, or checking a meeting
  from Gmail works exactly as it would for any other invite.
- **Guest participants**: invite people who aren't registered on
  SmartBooking by email. Invite sending is rate-limited (a cooldown per
  target email, a daily cap per organizer) to prevent abuse.
- **Personal calendar view**: a FullCalendar-based dashboard color-codes
  SmartBooking meetings alongside your other Google Calendar events, which
  get auto-classified (lecture, lab, meeting, office hours, or other) using
  a lightweight TF-IDF text classifier that understands both Greek and
  English titles.
- **Out of Office**: mark yourself unavailable for a date range; the time
  slot optimizer treats you as busy during that window when suggesting
  meetings.
- **Room directory**: browse rooms with photos, amenities, capacity, and
  live availability, with each room's campus location shown on an
  interactive map.
- **Admin panel**: manage rooms, review every
  booking ever made, and
  manage user roles (student, professor, admin).
- **Interactive API docs**: a full Swagger UI for the REST endpoints,
  generated from the controllers themselves.

---

## Tech stack

| Layer | Technology |
|---|---|
| Language / runtime | Java 21 |
| Backend framework | Spring Boot 4.0.2, Spring Security 7, Spring Data JPA (Hibernate 7.2.1) |
| Database | PostgreSQL 15 |
| Auth | Google OAuth2 (login) and Google Calendar API (event sync) |
| Frontend | Thymeleaf (server-rendered), Tailwind CSS, vanilla JS, FullCalendar 6, Leaflet.js |
| Email | Resend API |
| API docs | springdoc-openapi / Swagger UI |
| Testing | JUnit 5, Mockito, AssertJ |
| Build | Maven |
| Deployment | Docker Compose, Cloudflare Tunnel |

---

## Architecture notes

- The app is a fairly traditional server-rendered Spring MVC application
  (Thymeleaf templates), with a handful of REST endpoints (under `/api/**`)
  backing the more dynamic pieces: the booking wizard, RSVP actions, and
  invite handling, called via `fetch()` from the page's JavaScript.
- Every booking made through SmartBooking is mirrored as a real Google
  Calendar event (`Booking.googleEventId` links the two). The calendar view
  merges two sources on every load: a bulk fetch of the user's own upcoming
  Google Calendar events, plus a reconciliation pass against SmartBooking's
  own `bookings` table (to catch invites that wouldn't otherwise show up in
  that fetch, and to detect events that were deleted directly on Google).
- CSRF protection is enabled throughout, including for the handful of plain
  HTML forms (e.g. logout) that need it alongside the `fetch()`-based calls
  that carry the CSRF token as a header.
- Sensitive text fields (e.g. a meeting's title) are encrypted at rest with
  AES-GCM before being persisted.

---

## Getting started

### Prerequisites

- Java 21 (JDK)
- Maven 3.9+
- Docker and Docker Compose
- A Google Cloud project with OAuth 2.0 credentials and the Calendar API
  enabled
- A [Resend](https://resend.com) API key (for sending invite emails)

### Environment variables

Create a `.env` file in the project root (never commit this file):

```env
DB_USERNAME=your_db_username
DB_PASSWORD=your_db_password
GOOGLE_CLIENT_ID=your_google_oauth_client_id
GOOGLE_CLIENT_SECRET=your_google_oauth_client_secret
ENCRYPTION_KEY=your_32_byte_aes_key
RESEND_API_KEY=your_resend_api_key
```

| Variable | Purpose |
|---|---|
| `DB_USERNAME` / `DB_PASSWORD` | PostgreSQL credentials |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | OAuth2 login and Calendar API access |
| `ENCRYPTION_KEY` | AES key used to encrypt sensitive fields at rest |
| `RESEND_API_KEY` | Sends invite emails to unregistered guests |

### Running with Docker

```bash
docker compose up -d --build
```

This starts four containers: the Spring Boot app, PostgreSQL, an
[Adminer](https://www.adminer.org/) instance for inspecting the database,
and a Cloudflare Tunnel for exposing the app publicly. The app will be
available at `http://localhost:8085`.

### Running tests locally

Tests need a real database connection (there's no in-memory/H2 profile), so
Postgres has to be up and the environment variables loaded:

```bash
bash run-tests.sh
```

This script starts the database container if it isn't already running,
loads `.env`, and runs the full Maven test suite (`mvn test`).
 
---

## Testing

The project has **61 automated tests** across six areas:

| Test class | What it covers |
|---|---|
| `SmartbookingApplicationTests` | Full Spring context loads correctly |
| `ValidationConstraintsTest` | Bean Validation rules on request DTOs |
| `EventTypeClassifierTest` | The TF-IDF event classifier (English, Greek, tie-breaks, edge cases) |
| `MeetingOptimizerServiceTest` | Time slot scoring (time-of-day preference, lunch penalty, soonest-first) |
| `InviteRateLimiterServiceTest` | Per-email cooldown and per-organizer daily cap for invites |
| `BookingServiceTest` | RSVP status updates, participant matching, Google Calendar sync behavior |
| `StringCryptoConverterTest` | AES-GCM encrypt/decrypt round-trip, non-deterministic ciphertext, malformed input handling |

Run the whole suite with `bash run-tests.sh`, or `mvn test` if the database
and environment variables are already set up in your shell.

---

## API documentation

With the app running, interactive API docs are available at:

```
http://localhost:8085/swagger-ui/index.html
```

---

## Project structure (high level)

```
src/main/java/com/hua/smartbooking/
├── config/          # Security, encryption, and app-level configuration
├── controller/       # MVC + REST controllers
├── dto/               # Request/response DTOs with validation
├── enums/             # BookingStatus, RsvpStatus, Role, etc.
├── mapper/            # Google Calendar event to internal entity mapping
├── model/             # JPA entities (Booking, Room, User, ...)
├── nlp/               # Lightweight TF-IDF event classifier
├── repository/        # Spring Data JPA repositories
├── security/          # OAuth2 login success handling
├── service/           # Business logic (booking, RSVP, invites, calendar sync)
└── util/              # Encryption converter and other utilities

src/main/resources/
├── templates/         # Thymeleaf views
├── static/            # CSS, JS, images
└── application.yml

src/test/java/         # Unit tests (see Testing section above)
```

---

## Known limitations

Documented honestly, since this is a course project and not a production
system:

- The invite rate limiter is in-memory and resets on app restart, an
  acceptable tradeoff for a lightweight abuse guard, not meant to survive
  restarts.
- There's no dedicated test database profile (e.g. H2); the full test suite
  requires a real Postgres connection, which is why `run-tests.sh` exists.
- Booking title is encrypted at rest, but participant emails are stored in
  plain text. An earlier version encrypted them too, but non-deterministic
  encryption on a `Map` key made lookups unreliable, a real bug found and
  fixed during development.

---

## Author

Stavroula Parsali, Harokopio University of Athens, Department of
Informatics and Telematics.