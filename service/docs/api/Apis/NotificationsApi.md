# NotificationsApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**fhirProcessNotificationPost**](NotificationsApi.md#fhirProcessNotificationPost) | **POST** /$process-notification | Processes an ARS notification |


<a name="fhirProcessNotificationPost"></a>
# **fhirProcessNotificationPost**
> Object fhirProcessNotificationPost(Authorization, Content-Type, body)

Processes an ARS notification

    Accepts a FHIR R4 Bundle representing an antibiotic resistance notification and processes it.  The response content type mirrors the request serialisation format unless the Accept header explicitly requests a different FHIR format. 

### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **Authorization** | **String**| Bearer token issued by the identity provider. Must be present in every request. | [default to null] |
| **Content-Type** | **String**| FHIR serialisation format of the request body. Supported values: application/fhir+json, application/json+fhir, application/json, application/fhir+xml, application/xml+fhir, application/xml. | [default to null] [enum: application/fhir+json, application/json+fhir, application/json, application/fhir+xml, application/xml+fhir, application/xml] |
| **body** | **String**|  | |

### Return type

**Object**

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: application/fhir+json, application/fhir+xml, application/json, application/json+fhir, application/xml, application/xml+fhir
- **Accept**: application/fhir+json, application/fhir+xml

