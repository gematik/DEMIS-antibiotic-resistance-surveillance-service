package de.gematik.demis.ars.service.service.sentinel;

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

import static de.gematik.demis.ars.service.utils.TestUtils.ARS_NOTIFICATION_SENTINEL_JSON;
import static de.gematik.demis.ars.service.utils.TestUtils.VALID_ARS_NOTIFICATION_JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.benmanes.caffeine.cache.LoadingCache;
import de.gematik.demis.ars.service.service.sentinel.terminology.CodeKey;
import de.gematik.demis.ars.service.service.sentinel.terminology.FutsClient;
import de.gematik.demis.ars.service.service.sentinel.terminology.ValueSet;
import de.gematik.demis.ars.service.service.sentinel.terminology.ValueSetCacheConfiguration;
import de.gematik.demis.ars.service.utils.LogListener;
import de.gematik.demis.ars.service.utils.TestUtils;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    classes = {
      SentinelService.class,
      SentinelTypeDetection.class,
      SentinelCleanupService.class,
      ValueSetCacheConfiguration.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      "ars.sentinel.enabled=true",
      "ars.sentinel.types.MEASLES.code-value-set=masern-loinc",
      "ars.sentinel.types.MEASLES.value-value-set=masern-snomed",
      "ars.sentinel.types.CHLAMYDIA.code-value-set=chlamydien-loinc",
      "ars.sentinel.types.CHLAMYDIA.value-value-set=chlamydien-snomed"
    })
@EnableConfigurationProperties(SentinelConfigurationProperties.class)
class SentinelServiceIntegrationTest {

  private static final String ARS_NOTIFICATION_SENTINEL_EXPECTED_CLEANED =
      "testdata/sentinel/ars-notification-sentinel-expected-cleaned.json";
  private static final String ARS_NOTIFICATION_SENTINEL_EXPECTED_MEASLES =
      "testdata/sentinel/ars-notification-sentinel-expected-measles.json";

  private final IParser jsonParser = FhirContext.forR4Cached().newJsonParser();
  private final TestUtils testUtils = new TestUtils();

  @Autowired LoadingCache<String, ValueSet> sentinelValueSetCache;
  @Autowired private SentinelService underTest;
  @MockitoBean private FutsClient futsClient;

  private ListAppender<ILoggingEvent> logAppender;

  @BeforeEach()
  void clearCacheAndStartLogListener() {
    sentinelValueSetCache.invalidateAll();
    logAppender = LogListener.listenToLog(SentinelService.class.getName());
  }

  private void assertNoSentinelLog() {
    assertThat(logAppender.list)
        .noneMatch(e -> e.getFormattedMessage().contains("Sentinel information removed"));
  }

  private void assertSentinelLog(final String removed) {
    assertThat(logAppender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .contains("Sentinel information removed: " + removed);
  }

  @Test
  void bundleWithoutSentinelData_should_remainUnchanged() {
    final Bundle bundle = testUtils.readFileToBundle(VALID_ARS_NOTIFICATION_JSON);
    final Bundle expected = testUtils.readFileToBundle(VALID_ARS_NOTIFICATION_JSON);

    assertDoesNotThrow(() -> underTest.removeSentinelData(bundle));
    assertThat(jsonParser.encodeResourceToString(bundle))
        .isEqualToIgnoringWhitespace(jsonParser.encodeResourceToString(expected));

    assertNoSentinelLog();
  }

  @Test
  void bundleWithSentinelDataAndNoSpecialSentinelType_should_removeAllSentinelDataAndLog() {
    when(futsClient.getValueSet(anyString())).thenReturn(List.of());
    callServiceAndAssertResult(ARS_NOTIFICATION_SENTINEL_EXPECTED_CLEANED);
    assertSentinelLog("[Patient.Address, Coverage, ReasonCode]");
  }

  @Test
  void bundleWithSentinelDataAndTypeIsChlamydia_should_keepSentinelDataAndNotLog() {
    when(futsClient.getValueSet("chlamydien-loinc"))
        .thenReturn(List.of(new CodeKey("http://loinc.org", "41852-5")));

    callServiceAndAssertResult(ARS_NOTIFICATION_SENTINEL_JSON);

    assertNoSentinelLog();
  }

  @Test
  void
      bundleWithSentinelDataAndTypeIsMeasles_should_keepReasonCodeAndRemoveAddressAndCoverageAndLog() {
    when(futsClient.getValueSet("masern-loinc"))
        .thenReturn(List.of(new CodeKey("http://loinc.org", "41852-5")));

    callServiceAndAssertResult(ARS_NOTIFICATION_SENTINEL_EXPECTED_MEASLES);

    assertSentinelLog("[Patient.Address, Coverage]");
  }

  @Test
  void futsUnavailableAndNoValuesCached_should_removeAllSentinelData() {
    when(futsClient.getValueSet(anyString())).thenThrow(RuntimeException.class);

    callServiceAndAssertResult(ARS_NOTIFICATION_SENTINEL_EXPECTED_CLEANED);
  }

  @Test
  void failedFutsCall_should_notBeCached() {
    when(futsClient.getValueSet("masern-loinc"))
        .thenThrow(RuntimeException.class)
        .thenReturn(List.of(new CodeKey("http://loinc.org", "41852-5")));

    callServiceAndAssertResult(ARS_NOTIFICATION_SENTINEL_EXPECTED_CLEANED);
    callServiceAndAssertResult(ARS_NOTIFICATION_SENTINEL_EXPECTED_MEASLES);
  }

  private void callServiceAndAssertResult(final String expectedBundle) {
    final Bundle bundle = testUtils.readFileToBundle(ARS_NOTIFICATION_SENTINEL_JSON);
    final Bundle expected = testUtils.readFileToBundle(expectedBundle);

    underTest.removeSentinelData(bundle);

    assertThat(jsonParser.encodeResourceToString(bundle))
        .isEqualToIgnoringWhitespace(jsonParser.encodeResourceToString(expected));
  }
}
