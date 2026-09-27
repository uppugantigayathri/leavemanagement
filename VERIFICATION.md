# Delivery verification

Verified on 27 September 2026 using Microsoft OpenJDK 17.0.20.1 and Spring Boot 4.1.1.

## Passed

- All application and test sources compiled for Java 17.
- 13 Spring Boot/JUnit regression tests: zero failures, errors, or skipped tests.
- Tests cover registration approval hierarchy, hashed passwords, overlapping leave rejection, HOD escalation, unauthorized approvals, final/audited decisions, attendance authorization and updates, approved-leave marking, missing attendance, date serialization, single-use reset codes, and five-attempt reset lockout.
- Executable Spring Boot JAR built and started successfully against the persistent H2 demo database.
- HTTP checks verified student/teacher/HOD authentication, pending-account login rejection, CSRF enforcement, self-approval rejection, reviewer routing, and exclusion of password hashes from dashboard responses.
- JavaScript syntax check passed.
- Browser checks verified all role dashboards, teacher attendance saving and persistence after restart, teacher leave approval with a required review note, HOD teacher-registration queue, and the final date display.
- Dashboard visually inspected at desktop width and a 390px mobile viewport, with no page-wide horizontal overflow. Tables scroll within their containers.
- H2 deployment update: all 13 regression tests rerun successfully. The packaged default profile was started without demo/MySQL configuration, created its HOD account and H2 file, registered and approved a teacher, then was forcibly stopped and restarted against the same file. Account IDs, approved status, HOD login, and record counts were preserved; no demo or duplicate bootstrap accounts appeared. Both H2 profiles use `WRITE_DELAY=0`.

## Build environment note

The sandbox's Windows file permissions caused the standard JDK compiler's `Path.toRealPath()` checks to fail on dependency files. Sources were compiled using the Eclipse Java compiler 3.42.0 on Java 17 with Java 17 output, then tested with Maven Surefire and packaged using the normal Spring Boot Maven plugin. The project remains a standard Maven project; `mvn clean verify` is the normal command outside this sandbox. A complete standard `mvn clean verify` run in this sandbox was not successful, so it is not claimed as verified here.

## Requires your environment

- H2 is now the default deployment database; MySQL is an optional future profile and has not been integration-tested against a live server.
- Real SMS delivery and end-to-end mobile OTP receipt: Twilio credentials/sender were not supplied. Recovery code validation was tested locally. Demo mode disables SMS entirely.
- Production deployment: configure HTTPS, secure cookies, database credentials, your real HOD account, backups, and your SMS sender.
- GitHub Actions and Docker configuration are supplied but have not been executed on GitHub or a Docker host in this environment.

Policy recommendations are deterministic rules, not a trained AI/ML model. Future features from the PPT (voice assistant, native mobile app, external attendance integration and advanced fraud detection) are not claimed as implemented.
