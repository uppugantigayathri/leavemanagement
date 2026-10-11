# CampusFlow — College Leave & Attendance

For a GitHub-hosted POC URL, follow [the Codespaces setup](CODESPACES.md). Java 17, Maven, port forwarding, and automatic H2 demo startup are configured in `.devcontainer/`.

Java 17 · Spring Boot 4.1.1 · Spring Security · Persistent H2 · Responsive HTML/CSS/JavaScript

## Start the demo

Install a Java **17 JDK** and Maven **3.9+**. Open a terminal in this folder:

```powershell
mvn clean verify
java -jar target/campusflow-1.0.0.jar --spring.profiles.active=demo
```

Visit **http://localhost:8080**. Windows users can also run `./start.ps1 -Demo`.

| Role | PIN | Demo password |
|---|---|---|
| Student | 24093-CM-228 | Campus@2026 |
| Student | 24093-CM-252 | Campus@2026 |
| Teacher | FAC-CM-101 | Campus@2026 |
| HOD | HOD-CM-001 | Campus@2026 |
| Admin | ADMIN-CM-001 | Campus@2026 |

Demo mode uses a separate persistent H2 file database in `data/demo/`. It seeds 4 departments, sections A–D, 4 students per section, and 30 attendance entries per student. All demo accounts use `Campus@2026`; demo credentials are never seeded in the default deployment profile. SMS is disabled in demo mode. Do not use the demo profile for real college records.

## Deploy with persistent H2 (default)

No MySQL installation or connection is needed. The default profile stores accounts, attendance, leave, notifications, and recovery records in an H2 file database. Passwords are stored as BCrypt hashes. Tables and the class-section migration run automatically. Configure the initial Admin account using the `ADMIN_*` variables below; Admin reviews HOD registrations and HOD leave.

1. Copy `.env.example` to `.env` and set the database password and initial Admin PIN, password, 10-digit mobile, email, and name.
2. Run `./start.ps1` on Windows. This script loads `.env`. Alternatively, export the environment variables and run `java -jar target/campusflow-1.0.0.jar`. Spring Boot does not automatically read `.env`.
3. Sign in as Admin and approve HOD registrations. Teachers select an approved HOD as their mentor; students select an approved teacher and a class section.

`H2_DATA_PATH` defaults to `./data/campusflow` and is a **file prefix**: H2 writes `campusflow.mv.db`. Use an absolute prefix on a mounted **persistent disk**, such as `/data/campusflow`, on your host. Relative paths resolve against the application's working directory.

**A persistent disk is required to retain data across redeploys.** An ephemeral container filesystem can disappear when the host replaces the container, even though H2 persists ordinary process restarts. Run one application instance against one database file, with no overlapping deployment instances. Do not place the database in GitHub, the application JAR, or a build directory. The runtime user must be able to write the mounted disk (the Docker image uses UID 10001).

Keep the same database username and password after initialization. Changing environment credentials does not change credentials already stored by H2. These connection credentials do not themselves encrypt the database file; protect disk access and backups.

The H2 connection sets `WRITE_DELAY=0`, so committed updates are written immediately instead of waiting for H2's usual buffered-write interval. Persistent storage and backups are still required.

For local Docker, fill in `.env`, then run `docker compose up --build -d`. The supplied configuration runs the application with an H2 named volume and no MySQL service. A normal `docker compose down` preserves the volume; do not use `down -v` unless you intend to delete the data. Docker binds localhost by default; configure your hosting platform's routing/HTTPS when deploying publicly. Set `COOKIE_SECURE=true` behind HTTPS.

Demo and deployment databases are separate. The expanded demo uses a new `campusflow-v3` H2 file, leaving earlier demo data intact. The Codespaces starter likewise uses `.poc/data/campusflow-v3.mv.db`; existing sample files are preserved. Demo mode seeds 4 students per section (A–D) in each department, with 30 attendance days per student. Do not reuse a sample database for real registrations.

For a consistent backup, stop the application, copy the complete database file to protected backup storage, and restart it. Restore with the application stopped, the same connection credentials, and the same compatible H2 version. Test backups before relying on them.

## Upload to GitHub and run on a host

Upload the **contents of this campusflow folder** to your repository root, including `pom.xml`, `src/`, `Dockerfile`, and `.github/workflows/build.yml`. Hidden files matter. `.gitignore` excludes local credentials, database files, build output, and backups. Do not upload `.env` or real student records.

The included GitHub Actions workflow runs `mvn clean verify` on Java 17 and saves the runnable JAR as a build artifact. It verifies the project; it does not deploy a server. This workflow has not been run on your GitHub account yet.

GitHub Pages only hosts static websites and cannot run the Spring Boot backend or H2. Connect the repository to a Java/Docker-capable host with a persistent disk, or run the JAR on a server you control. The Dockerfile builds and tests the application. Set H2 and `ADMIN_*` environment variables in the host's secret/environment settings; leave `SPRING_PROFILES_ACTIVE` unset for normal H2 deployment. `PORT` is supported for host-assigned ports.

Official GitHub Pages scope: https://docs.github.com/en/pages/getting-started-with-github-pages/what-is-github-pages

## Future MySQL migration (optional)

MySQL support is retained in `application-mysql.properties`. When ready, provision a MySQL database/user, set `SPRING_PROFILES_ACTIVE=mysql`, and supply `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`.

Example JDBC URL: `jdbc:mysql://localhost:3306/campusflow?serverTimezone=Asia/Kolkata`.

**Changing the profile does not migrate H2 records.** Plan an offline backup/export/import, preserve IDs and relationships, validate row counts and login/workflow behavior, and switch only after checking the migrated database. Until then, H2 is used for both local work and deployment.

Tables are created with the idempotent `schema.sql` initializer. Subsequent schema changes should use versioned migrations.

## Screens and workflow

- **Public:** login with PIN/password, registration with department and class section, email code recovery, new password. Mobile numbers are validated as 10 digits.
- **Student:** dashboard, new leave, request history and decision timeline, attendance, reports/CSV, notifications.
- **Teacher:** dashboard, assigned-student registration reviews, daily attendance, student leave approvals, personal leave submitted to HOD, department/section analytics and notifications.
- **HOD:** department dashboard, teacher registration reviews, long/low-attendance student leave approvals, teacher leave review, section analytics and notifications.
- **Admin:** HOD registration review and HOD leave approvals across departments.

Student accounts await their chosen teacher's approval. Teacher accounts await their selected HOD's approval. HOD accounts await Admin approval. Admin itself is provisioned from environment settings and cannot be publicly registered. Pending/rejected accounts cannot log in. Four departments are available: Computer Engineering, Information Technology, Electronics & Communication, and Mechanical Engineering.

Teachers may only review their assigned students. HOD access is restricted to the configured department. All decisions, including escalation and cancellation, keep the reviewer's name, note, and timestamp. Approval/rejection is final. Pending leave can be cancelled by its applicant. Rejected registration currently requires administrator assistance; self-service reapplication is not included.

## Policy recommendations

The PPT is used as a requirements source; its suggested Python/Streamlit implementation is replaced with your requested Java/Spring Boot stack.

The assistant is an **explainable rule engine**, not a trained machine-learning model. No training data or external AI service is required. It recommends mentor approval when rules are met, or HOD review when:

- student attendance is below **75%**, or attendance has not been recorded;
- leave lasts **more than 3 calendar days**;
- the applicant is a teacher (teacher leave goes to the HOD), or an HOD (HOD leave goes to Admin).

Student leave of 4 or more calendar days goes to the HOD. Requests of 3 days or fewer go to the teacher when attendance is at least 75%. Low or unrecorded attendance sends even short student requests to the HOD. The application form shows the attendance percentage and routing reason, and the HOD request card repeats the percentage and 75% threshold. Reviewers can approve or reject; teachers can escalate. Invalid dates and overlaps are rejected at submission. No automatic approval or rejection occurs.

Attendance = PRESENT ÷ all marked days, including ABSENT and LEAVE. Unmarked days are excluded. Teachers can enter/correct today and the previous seven days. LEAVE requires an approved request covering that date. Leave requests count weekends, must start today or later, and have a maximum duration of 30 days. Students see their percentage on the dashboard and leave form. Teacher and HOD reports can be filtered by department and section.

## SMS and password recovery

Email uses Gmail SMTP. Set `MAIL_ENABLED=true`, `MAIL_USERNAME` to the sending Gmail address, `MAIL_PASSWORD` to a Google App Password, and `MAIL_FROM` to that Gmail address in `.env` or the hosting provider's secret settings. Do not use your regular Gmail password or commit secrets. Gmail requires 2-Step Verification before you can create an App Password. The app sends registration receipts, account/review notifications, and password reset codes. If email is not configured, account registration still works, but email recovery is unavailable. Existing accounts created before email support have a blank email; an administrator must add each user's correct email before that account can use recovery.

Twilio SMS is an optional separate feature. Provide a Twilio account SID, auth token, and approved sending number in `.env` to enable SMS notifications. The form stores 10 digits; the current sender prefixes `+91` for Twilio delivery.

In-app notifications work without SMS. When configured, registration/leave notifications enter `sms_outbox`, are attempted every minute, and retry up to three times. `SENT` means the provider accepted the message, not handset delivery confirmation. `FAILED` messages remain visible in the database for administrator follow-up. Delivery receipts and a delivery-monitoring UI are not included. The dispatcher is intended for a single app instance; use distributed locking before horizontal scaling.

Password recovery checks PIN + registered Gmail address for an approved account, sends a random six-digit code, hashes it in the database, expires it after five minutes, and allows at most five attempts. Recovery responses do not disclose whether an account exists. Requests are throttled per PIN and source IP. Codes are never shown in the UI or application logs. Email recovery stays unavailable until Gmail SMTP is configured; there is no insecure demo bypass.

## Security and deployment

Passwords use BCrypt. Spring Security manages session authentication, CSRF protection, session fixation protection, role checks, and security headers. Queries are parameterized. All sensitive mutations are validated in the backend, not only hidden by the UI. Login requests are limited per source IP in memory; use a gateway/shared limiter for a multi-instance deployment. Serve via HTTPS and set `COOKIE_SECURE=true` for production. Protect the application host and H2 data directory, back up the database, and manage secrets outside source control.

The default session timeout is 30 minutes, with up to five simultaneous sessions per account. Password reset expires active sessions on this application instance. Use a shared session store before running multiple instances. No file attachments, automatic external attendance import, mobile native app, voice assistant, or trained fraud-detection model are included; the PPT identifies these as future scope.

## Development

```text
src/main/java/edu/campus/flow/   API, security, policy/workflows, SMS and bootstrap
src/main/resources/static/      Responsive frontend (no Node build required)
src/main/resources/schema.sql   MySQL/H2 schema
src/test/java/edu/campus/flow/   Workflow and authorization regression tests
```

Build: `mvn clean verify`. Run tests: `mvn test`. JavaScript syntax check: `node --check src/main/resources/static/app.js`.

Spring Boot's official Java compatibility documentation: https://docs.spring.io/spring-boot/system-requirements.html

See `VERIFICATION.md` for the exact checks completed in the delivery environment and remaining integration checks.

