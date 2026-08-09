# Local secret rotation

The repository contains only empty Secret templates and placeholder names. Create or sync runtime
values out-of-band. During rotation, provision the replacement value, restart dependent workloads,
verify readiness and smoke checks, then revoke the old value. Never log, paste, or commit secret
contents. Local Compose placeholders must never be reused outside the isolated development stack.
