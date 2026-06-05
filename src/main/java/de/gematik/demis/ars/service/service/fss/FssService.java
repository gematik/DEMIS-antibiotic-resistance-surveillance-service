package de.gematik.demis.ars.service.service.fss;

/*-
 * #%L
 * Antibiotic-Resistance-Surveillance-Service
 * %%
 * Copyright (C) 2025 - 2026 gematik GmbH
 * %%
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the
 * European Commission – subsequent versions of the EUPL (the "Licence").
 * You may not use this work except in compliance with the Licence.
 *
 * You find a copy of the Licence in the "Licence" file or at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either expressed or implied.
 * In case of changes by gematik find details in the "Readme" file.
 *
 * See the Licence for the specific language governing permissions and limitations under the Licence.
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik,
 * find details in the "Readme" file.
 * #L%
 */

import de.gematik.demis.ars.service.service.fhir.FhirParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

/** Service to send bundle to <FSS> */
@Slf4j
@Service
@RequiredArgsConstructor
public class FssService {
  private final FhirStorageServiceClient fssClient;
  private final FhirParser fhirParser;

  /**
   * Takes the <Bundle> and wraps it into a transactionBundle. Therefor encryption for RKI will be
   * done
   *
   * @param bundle the information in <Bundle> representation to send to <FSS>
   */
  public void sendNotificationToFss(Bundle bundle) {
    Bundle transactionBundle = createTransactionBundle(bundle);
    String jsonBundle = fhirParser.serializeResource(transactionBundle, MediaType.APPLICATION_JSON);
    fssClient.sendNotification(jsonBundle);
  }

  private Bundle createTransactionBundle(Bundle bundle) {
    final Bundle transactionBundle = new Bundle();
    transactionBundle.setType(Bundle.BundleType.TRANSACTION);
    addEntry(transactionBundle, bundle, "Bundle");
    return transactionBundle;
  }

  private void addEntry(final Bundle transactionBundle, final Resource resource, final String url) {
    if (resource != null) {
      transactionBundle
          .addEntry()
          .setFullUrl(IdType.newRandomUuid().getValue())
          .setResource(resource)
          .getRequest()
          .setUrl(url)
          .setMethod(Bundle.HTTPVerb.POST);
    }
  }
}
