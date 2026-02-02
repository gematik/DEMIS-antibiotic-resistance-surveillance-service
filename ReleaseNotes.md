<div style="text-align:right"><img src="https://raw.githubusercontent.com/gematik/gematik.github.io/master/Gematik_Logo_Flag_With_Background.png" width="250" height="47" alt="gematik GmbH Logo"/> <br/> </div> <br/> 

# Release notes Antibiotic-Resistance-Surveillance-Service
## Release 1.2.4
- Adjust requested resources 
- updated spring-parent to 2.14.20

## Release 1.2.3
- error id in operation-outcome moved from location to diagnostics (FEATURE_FLAG_MOVE_ERROR_ID_TO_DIAGNOSTICS)
- accepts also fhir specific content types like "application/fhir+json"
- Sentinel related data is removed from the bundle

## Release 1.2.2
- Errors thrown by surveillance pseudonym service will lead to a 422 status code response

## Release 1.2.1
- Add default feature flag FEATURE_FLAG_ARS_VALIDATION_ENABLED to values.yaml
- Update spring-parent to 2.12.12
- Add feature flag FEATURE_FLAG_NEW_API_ENDPOINTS for header propagation of x-fhir-api-version and fhirProfile to validation-service
- Surveillance-pseudonym-service is called to determine new pseudonym (FF surveillance_pseudonym_service_enabled)
- Add new versioned API endpoints

## Release 1.2.0
- Updated Readme license disclaimer
- Removed ncapi apikey

## Release 1.1.0
- Remove cpu limits from helm chart
- Setting new timeouts and retries in helm chart
- Update spring-parent to 2.11.2

## Release 1.0.0
- Initial Project setup
- Add OSPO-Guidelines 
- Pseudonymization is currently performed by using a fixed pseudonym (10101010-1010-1010-1010-101010101010) and system "https://demis.rki.de/fhir/sid/SurveillancePatientPseudonymPeriod"
- The service contacts the Context Enrichment Service to set the Provenance resource in the bundle