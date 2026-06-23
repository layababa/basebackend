# CDN OSS Failback URL SDK Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `ossFailbackEndpoint` to the reusable backend SDK node-management contract.

**Architecture:** Extend the shared `ServerNode` model and admin node create/update DTO handling. Keep CDN publishing delegated to host applications through `AdminNodePort`, so the SDK contract remains reusable and does not assume a concrete OSS publisher.

**Tech Stack:** Kotlin, Spring MVC controller contract, JUnit 5.

---

### Task 1: Contract Tests

**Files:**
- Create: `src/test/kotlin/com/layababateam/xinxiwang_backend/controller/AdminNodeControllerOssEndpointTest.kt`

- [x] Write controller tests verifying create sends normalized `ossPublicEndpoint` and `ossFailbackEndpoint` to `AdminNodePort`.
- [x] Write update tests verifying null preserves existing values while blank strings clear endpoints.
- [x] Run the targeted test and verify it fails because the failback contract is missing.

### Task 2: Backend Contract Implementation

**Files:**
- Modify: `src/main/kotlin/com/layababateam/xinxiwang_backend/model/ServerNode.kt`
- Modify: `src/main/kotlin/com/layababateam/xinxiwang_backend/dto/NodeRequests.kt`
- Modify: `src/main/kotlin/com/layababateam/xinxiwang_backend/controller/AdminNodeController.kt`

- [x] Add `ossFailbackEndpoint` to `ServerNode`.
- [x] Add `ossPublicEndpoint` and `ossFailbackEndpoint` to create/update DTOs where needed.
- [x] Normalize create values, preserve null update values, and clear blank update values.

### Task 3: Verification

**Files:**
- Modified Kotlin files above.

- [x] Run `gradle --offline test --tests *AdminNodeControllerOssEndpointTest`.
- [x] Run `compileKotlin` as part of the targeted test build.
