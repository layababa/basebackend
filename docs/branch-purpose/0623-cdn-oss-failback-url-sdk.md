# 0623-cdn-oss-failback-url-sdk

Add CDN OSS failback endpoint support to the reusable backend node-management contract.

The branch extends `ServerNode` and admin node create/update requests with `ossFailbackEndpoint`, preserving existing node-management behavior while allowing host backends to generate top-level `oss_url` and `oss_failback_url` values in `cdn.json`.
