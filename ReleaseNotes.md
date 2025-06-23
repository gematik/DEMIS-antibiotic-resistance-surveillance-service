<img align="right" width="250" height="47" src="media/Gematik_Logo_Flag.png"/> <br/> 
 
# Release notes Antibiotic-Resistance-Surveillance-Service

## Release 1.1.0
- remove cpu limits from helm chart
- setting new timeouts and retries in helm chart
- update spring-parent to 2.11.2

## Release 1.0.0
- Initial Project setup
- Add OSPO-Guidelines 
- Pseudonymization is currently performed by using a fixed pseudonym (10101010-1010-1010-1010-101010101010) and system "https://demis.rki.de/fhir/sid/SurveillancePatientPseudonymPeriod"
- The service contacts the Context Enrichment Service to set the Provenance resource in the bundle