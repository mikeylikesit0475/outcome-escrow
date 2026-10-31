# ADR-0001 — Stack: Java 25, Spring Boot 4.1, Gradle 9.8, Postgres 16

## Status
Accepted.

## Context
The portfolio lacks a Java/Spring repo; the product framing is enterprise software. Java 25 LTS and Docker are installed, and Gradle 9.8 supports Java 25.

## Decision
Generated from start.spring.io with Spring Boot 4.1.x so starter names and Jackson 3 packages are correct for Boot 4. Gradle wrapper pinned; Spotless with google-java-format; `-Xlint:all -Werror`; Testcontainers for integration tests; springdoc for OpenAPI.

## Consequences
Builder must not hand-write Boot 3-era starter names. CI uses `actions/setup-java` Temurin 25 with Gradle caching.
