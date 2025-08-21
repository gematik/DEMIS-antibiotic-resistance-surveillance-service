<img align="right" width="250" height="47" src="media/Gematik_Logo_Flag.png"/> <br/> 

# Release notes Antibiotic-Resistance-Surveillance-Service

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