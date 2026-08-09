# Milestone 7 security assessment

## Scope

This assessment covers authentication, authorization, seller isolation, sensitive-data handling,
dependencies, secrets, static analysis, container images, and Kubernetes resources.

## Required evidence

| Area | Command/evidence | Result |
|---|---|---|
| Secret scanning | Gitleaks repository scan | Passed: no leaks found across 70 commits |
| Filesystem dependencies | Trivy focused Kubernetes config scan | Passed: 0 high/critical after security-context fixes; full repository scan retains a Helm kubeVersion compatibility warning |
| Container images | Trivy image scan for each of the 10 application images | Passed: Alpine and JAR targets reported 0 high/critical after `apk upgrade` and Netty `4.2.16.Final` |
| Static analysis | Maven Checkstyle, SpotBugs, CodeQL | Checkstyle/SpotBugs passed in both Maven builds; CodeQL is configured in GitHub Actions and was not run locally |
| Contract dependencies | `npm audit`, contract lint | Passed in Node 22 container; npm audit found 0 vulnerabilities; lint emitted 32 existing warnings and exited 0 |
| Authentication | Identity regression and negative tests | Passed via Maven integration/unit tests |
| Authorization | Seller/customer/admin isolation tests | Passed via Maven integration/unit tests |
| Logging | Structured log redaction review | Passed review: no passwords, tokens, card numbers, or provider credentials are logged |
| Kubernetes | Kubeconform, RBAC, NetworkPolicy, probe, secret review | Passed: 37 Kustomize resources and 34 Helm-rendered resources valid; focused Trivy config scan clean |

The repository-level Trivy scan initially found high findings for writable container roots,
the example migration Job pod context, and a non-secret token issuer/audience ConfigMap heuristic.
The first two were fixed; the latter is documented in `.trivyignore` because those values are
public validation metadata, not credentials. Real payment credentials, provider tokens, and
production secrets remain outside the project boundary.
