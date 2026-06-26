# 0626-extract-xianyun-0625-sdk

Extract reusable backend SDK capabilities from xianyun `0625-reuse-sdk-main` into `basebackend` with minimal, host-neutral changes.

Scope:

- Default public Web customer service entry bootstrap API.
- Shared OSS endpoint root-address validation rules for admin node APIs.
- Shared CDN config payload builder that emits top-level `oss_url` and `oss_failback_url`.

Out of scope: xianyun persistence policy, push delivery, Redis/index initializers, invite-code policy, admin UI, deployment workflows, and project-specific secrets/configuration.
