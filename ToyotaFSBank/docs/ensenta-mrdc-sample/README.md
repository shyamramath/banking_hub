# Ensenta Mobile Deposit Gateway (Spring Boot starter)

A backend service between your mobile banking app and the **Jack Henry EPS Ensenta Mobile API (Multi-Check)**.
The app calls a simple JSON API. This service holds the Ensenta client certificate and PartnerId, makes the
SOAP/XML calls, polls for processing, and translates Ensenta response codes.

> **Status: starter code, not compiled against the real WSDL.** Jack Henry publishes the WSDL only on its
> developer portal's Downloads page. Generated class and getter names in `EnsentaClient` follow the
> operation and field names in Jack Henry's guides. Expect small renames after you generate from the WSDL.

## Jack Henry documentation

| What | Where |
| --- | --- |
| API overview | https://api.payments.jackhenry.com/docs/papi3-multi/overview |
| Authentication Guide (client cert, sessions) | https://api.payments.jackhenry.com/docs/papi3-multi/Guides/Authentication |
| Check Deposit Guide (full workflow + XML) | https://api.payments.jackhenry.com/docs/papi3-multi/Guides/CheckDeposit |
| Reject / Confirmable Risk Factor / History guides | https://api.payments.jackhenry.com/docs/papi3-multi/workflows |
| API Reference | https://api.payments.jackhenry.com/docs/papi3-multi/APIReference/overview |
| Integration Certification Process | https://api.payments.jackhenry.com/docs/papi3-multi/Guides/IntegrationCertification |
| Downloads (WSDL, certificate request kit, certification form) | https://api.payments.jackhenry.com/developer/downloads/list |
| Smart Alerts webhook, other EPS APIs | https://api.payments.jackhenry.com/developer/api-cards |

## Setup

1. **Get access.** Register on the Jack Henry Payments developer portal. Ask EPS.TechIntegrations@jackhenry.com
   for a UAT PartnerId.
2. **Download the WSDL** ("EPS Ensenta Mobile API WSDL") and save the two service WSDLs as
   `src/main/resources/wsdl/PartnerSSORequest3.wsdl` and `src/main/resources/wsdl/PartnerDepositRequest3.wsdl`.
   Rename in `pom.xml` if the files are named differently.
3. **Client certificate.** Download the *Mobile Certificate Request* kit, create UAT and Production requests,
   and send them to EPS Tech Integrations. Install each issued certificate on the machine that created the
   request, then export it with the private key as `.p12`. UAT and Production certificates are not interchangeable.
4. **Configure** the environment variables used in `application.yml`:
   `ENSENTA_PARTNER_ID`, `ENSENTA_SSO_URL`, `ENSENTA_DEPOSIT_URL` (from the WSDL `soap:address`),
   `ENSENTA_SOAP_ACTION_SSO`, `ENSENTA_SOAP_ACTION_DEPOSIT` (prefix of the WSDL `soapAction` values),
   `ENSENTA_KEYSTORE_PATH`, `ENSENTA_KEYSTORE_PASSWORD`.
5. **Implement `EligibilityService`.** Ensenta leaves enrollment to you: return only enrolled users and the
   accounts they may deposit into.
6. `mvn clean package`, then fix any generated-type names the compiler flags in `EnsentaClient`.

## Flow

```
App                      Gateway (this service)                     Ensenta
POST /api/mobile-deposits
  checks[] (Base64)  ->  StartSession (mTLS)                  ->  SessionToken, SessionStateId, AccountKeys
                         CreateDepositBatch                   ->  DepositBatchId
                         AddDepositItem x N                   ->  "00"
                         GetDepositBatchSummary (poll 2-5 s)  ->  ItemCount, NeedReviewItemCount
                         GetDepositItem x N                   ->  MICR, OCR amount, RiskFactors
  <- items + risk factors for review
POST /{batchId}/post
  decisions[]        ->  UpdateDepositItems (confirm/fix/delete)
                         PostDepositBatch                     ->  TransactionId, ReceiptNumber, Status
                         EndSession
  <- receipt
```

## Things to settle with Jack Henry

- Values for `TenderType`, `CheckImageCategory`, `ImagePreprocessState` and `AccountTypeCode` in your setup
  (the code uses the values from Jack Henry's examples).
- Endorsement requirements (not covered in the Check Deposit Guide).
- Maximum checks per batch and deposit limits for your institution.
- How posted deposits reach your core, and whether you need the Smart Alerts webhook for status updates.

## Production notes

- Move `DepositSessionStore` to Redis so any instance can continue a user's deposit.
- Never log SOAP payloads in production: they contain check images and personal data.
- Capture images with Mitek MiSnap in the app; Jack Henry recommends it because Ensenta processes with Mitek.
- Before go-live Jack Henry runs an integration certification (about a week) and then hands you to Implementations
  for production credentials.
