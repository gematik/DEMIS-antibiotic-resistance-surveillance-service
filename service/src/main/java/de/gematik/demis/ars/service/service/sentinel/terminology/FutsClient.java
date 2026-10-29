package de.gematik.demis.ars.service.service.sentinel.terminology;

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

import static de.gematik.demis.ars.service.exception.ServiceCallErrorCode.FUTS;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import de.gematik.demis.service.base.feign.annotations.ErrorCode;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Fetches FUTS ValueSet entries by system key. */
@FeignClient(name = "futs-service", url = "${ars.futs.url}")
public interface FutsClient {

  /** Returns all entries for the given FUTS ValueSet system. */
  @GetMapping(
      value = "/ValueSet",
      produces = APPLICATION_JSON_VALUE,
      headers = "x-fhir-package=${ars.futs.fhir-package}")
  @ErrorCode(FUTS)
  List<CodeKey> getValueSet(@RequestParam("system") String system);
}
