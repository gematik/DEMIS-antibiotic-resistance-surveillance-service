package de.gematik.demis.ars.service.batchprocessing;

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

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.status;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchRuntimeException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

import de.gematik.demis.ars.service.batchprocessing.repository.BatchRepository;
import de.gematik.demis.ars.service.batchprocessing.repository.BatchResultDAO;
import de.gematik.demis.ars.service.service.NotificationContext;
import de.gematik.demis.ars.service.service.NotificationProcessingResult;
import de.gematik.demis.ars.service.service.NotificationService;
import de.gematik.demis.ars.service.utils.TestUtils;
import de.gematik.demis.fhirparserlibrary.MessageType;
import de.gematik.demis.service.base.error.ServiceCallException;
import de.gematik.demis.service.base.error.ServiceException;
import feign.Request;
import feign.RetryableException;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.wiremock.spring.EnableWireMock;

@SpringBootTest(
    properties = {
      "ars.batch-processing.enabled=true",
      "ars.batch-processing.retry.max-retries="
          + RetryableNotificationProcessorIntegrationTest.MAX_RETRIES
    })
@ActiveProfiles({"without-database", "test-without-rabbitmq", "batch-test"})
@MockitoBean(
    types = {
      BatchRepository.class,
      BatchResultDAO.class,
    })
@Slf4j
class RetryableNotificationProcessorIntegrationTest {

  public static final int MAX_RETRIES = 3;

  private static final String ARS_NOTIFICATION = new TestUtils().VALID_ARS_NOTIFICATION_JSON_STRING;

  @Autowired RetryableNotificationProcessor underTest;
  @MockitoSpyBean NotificationService notificationService;

  private RuntimeException executeTest() {
    final RuntimeException caughtRuntimeException =
        catchRuntimeException(
            () ->
                underTest.process(
                    ARS_NOTIFICATION,
                    MessageType.JSON,
                    "token",
                    NotificationContext.fromMessageQueue(Map.of(), UUID.randomUUID())));
    log.info("caught exception: ", caughtRuntimeException);
    return caughtRuntimeException;
  }

  private void assertServiceInvocations(int expectedInvocations) {
    verify(notificationService, times(expectedInvocations)).process(any(), any(), any(), any());
  }

  @Nested
  class UnitTest {

    @Test
    void happyPath_unitTest_noRetry() {
      doReturn(mock(NotificationProcessingResult.class))
          .when(notificationService)
          .process(any(), any(), any(), any());

      final RuntimeException exception = executeTest();

      assertThat(exception).isNull();
      assertServiceInvocations(1);
    }

    @Test
    void serviceUnavailable_allRetriesFailed_should_throw_originalException() {
      doThrow(new ServiceCallException("test", "VS", 503, null))
          .when(notificationService)
          .process(any(), any(), any(), any());

      final RuntimeException exception = executeTest();

      assertThat(exception).isNotNull().isInstanceOf(ServiceCallException.class);
      assertServiceInvocations(1 + MAX_RETRIES);
    }

    @Test
    void clientError_should_notRetries_and_throw_originalException() {
      doThrow(new ServiceCallException("test", "VS", 400, null))
          .when(notificationService)
          .process(any(), any(), any(), any());

      final RuntimeException exception = executeTest();

      assertThat(exception).isNotNull().isInstanceOf(ServiceCallException.class);
      assertServiceInvocations(1);
    }

    @Test
    void feignRetryableException_should_Retried() {
      doThrow(new RetryableException(-1, "test", Request.HttpMethod.POST, 0L, mock(Request.class)))
          .when(notificationService)
          .process(any(), any(), any(), any());

      final RuntimeException exception = executeTest();

      assertThat(exception).isNotNull().isInstanceOf(RetryableException.class);
      assertServiceInvocations(1 + MAX_RETRIES);
    }

    @Test
    void normalException_should_notRetried_and_throw_originalException() {
      doThrow(new ServiceException(INTERNAL_SERVER_ERROR, "MISSING_RESOURCE", "just for test"))
          .when(notificationService)
          .process(any(), any(), any(), any());

      final RuntimeException exception = executeTest();

      assertThat(exception).isNotNull().isInstanceOf(ServiceException.class);
      assertServiceInvocations(1);
    }
  }

  @EnableWireMock
  @Nested
  class IntegrationTestWithServiceInvocation {
    private static final String PS_RESPONSE =
        """
                  {
                    "system": "http://my.test/Pseudo",
                    "value": "urn:uuid:10101010-1010-1010-1010-101010101010"
                  }
                """;
    private static final String VS_RESPONSE =
        """
            {
              "resourceType": "OperationOutcome"
            }
            """;

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
      registry.add("ars.validation.url", () -> "http://localhost:${wiremock.server.port}/VS");
      registry.add("ars.pseudo.url", () -> "http://localhost:${wiremock.server.port}/PS");
      registry.add("ars.fss.url", () -> "http://localhost:${wiremock.server.port}/FSS");
      registry.add("ars.fss.context-path", () -> "");
    }

    @Test
    void happyPath_realServiceCall_noRetry() {
      okayStubs();

      final RuntimeException exception = executeTest();
      assertThat(exception).isNull();
      assertServiceInvocations(1);
    }

    @ParameterizedTest
    @EnumSource(RemoteService.class)
    void service5xx_should_retried(final RemoteService remoteService) {
      okayStubs();
      stubFor(post(remoteService.getUrl()).atPriority(1).willReturn(status(500)));

      final RuntimeException exception = executeTest();
      assertThat(exception).isNotNull().isInstanceOf(ServiceCallException.class);
      assertServiceInvocations(1 + MAX_RETRIES);
    }

    @ParameterizedTest
    @EnumSource(RemoteService.class)
    void service4xx_should_not_retries(final RemoteService remoteService) {
      okayStubs();
      stubFor(post(remoteService.getUrl()).atPriority(1).willReturn(status(400)));

      final RuntimeException exception = executeTest();

      assertThat(exception).isNotNull().isInstanceOf(ServiceCallException.class);
      assertServiceInvocations(1);
    }

    private void okayStubs() {
      stubFor(post(RemoteService.VS.getUrl()).willReturn(okJson(VS_RESPONSE)));
      stubFor(post(RemoteService.PS.getUrl()).willReturn(okJson(PS_RESPONSE)));
      stubFor(post(RemoteService.FSS.getUrl()).willReturn(status(200)));
    }

    // Note: CES is also a remote service, but it is optional for processing. Thus, we ignore the
    // CES
    // here, i.e. CES has no stub and always throw Exception (but not relevant for test execution)
    @RequiredArgsConstructor
    @Getter
    private enum RemoteService {
      VS("/VS/$validate"),
      PS("/PS/pseudonym"),
      FSS("/FSS");
      private final String url;
    }
  }
}
