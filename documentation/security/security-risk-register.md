# Security Risk Register

| Risk ID | Vulnerability / Threat Scenario | Inherent Severity | Mitigated Controls | Residual Risk | Status |
| :--- | :--- | :---: | :--- | :---: | :---: |
| **SEC-01** | Unauthorized cross-passenger booking view (IDOR) | High | Forced server-side filter `user_id = principal.userId()` | Low | Mitigated |
| **SEC-02** | Maker approves own fare change or refund | High | Database `chk_no_self_approval` + backend validation | Low | Mitigated |
| **SEC-03** | Internal staff notes leaked to passenger | High | Hardcoded SQL filter `AND is_internal_note = false` | Low | Mitigated |
| **SEC-04** | Webhook signature replay on demo payments | Medium | Timestamp & Idempotency-Key validation | Low | Mitigated |
| **SEC-05** | Credential stuffing on login endpoints | Medium | IP and identifier throttling tables (`login_throttles`) | Low | Mitigated |
| **SEC-06** | Browser framing & clickjacking | Medium | `X-Frame-Options: DENY` + CSP `frame-ancestors 'none'` | Low | Mitigated |
| **SEC-07** | Free-tier host infrastructure outage | Medium | Automated zero-cost health checks & container restart | Medium | Accepted |

