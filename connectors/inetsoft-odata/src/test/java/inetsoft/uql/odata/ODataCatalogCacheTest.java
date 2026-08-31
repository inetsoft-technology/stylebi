/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.uql.odata;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import inetsoft.test.*;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.credential.*;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The charter's cache reverse assertion, in its strongest testable form (see
 * {@code 12-spi-design.md} §6.3/§8.3): the number of {@code $metadata} downloads does not grow
 * with the number of targets described, because both SPI phases are served from one cached,
 * parsed snapshot.
 *
 * The second test below is the one that would fail if {@link ODataCatalogCache}'s cache field
 * were ever made an instance field instead of {@code static}: core builds a brand new
 * {@link ODataRuntime} per HTTP request ({@code TabularUtil.createRuntime}), so this test
 * constructs two separate instances itself to mimic that and asserts they still share one
 * download.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  ODataCatalogCacheTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@WireMockTest
class ODataCatalogCacheTest {
   private ODataDataSource dataSource;

   @AfterAll
   static void resetContext() {
      ConfigurationContext.getContext().setApplicationContext(null);
   }

   @BeforeEach
   void setupDataSource(WireMockRuntimeInfo info, TestInfo testInfo) {
      dataSource = new ODataDataSource();
      dataSource.setURL(info.getHttpBaseUrl() + "/V4/OData/OData.svc/");
      // Unique per test method so no two tests in this class can ever share a cache entry.
      dataSource.setName("ODataCatalogCacheTest/" + testInfo.getDisplayName());
   }

   @Test
   void oneDownloadServesListDatasetsAndEveryDescribeDatasetCall() throws Exception {
      stubFor(get(urlPathEqualTo("/V4/OData/OData.svc/%24metadata"))
                 .willReturn(okXml(readXml("catalog.metadata.xml"))));

      ODataRuntime runtime = new ODataRuntime();
      var catalog = runtime.listDatasets(dataSource);

      for(var ref : catalog.datasets()) {
         runtime.describeDataset(dataSource, ref.id());
      }

      verify(exactly(1), getRequestedFor(urlPathEqualTo("/V4/OData/OData.svc/%24metadata")));
   }

   @Test
   void twoRuntimeInstancesStillShareOneDownload() throws Exception {
      stubFor(get(urlPathEqualTo("/V4/OData/OData.svc/%24metadata"))
                 .willReturn(okXml(readXml("catalog.metadata.xml"))));

      // Mimics TabularUtil.createRuntime building a fresh instance per HTTP request. If
      // ODataCatalogCache's cache field were ever an instance field instead of static, this would
      // download twice.
      new ODataRuntime().listDatasets(dataSource);
      new ODataRuntime().describeDataset(dataSource, "Products");

      verify(exactly(1), getRequestedFor(urlPathEqualTo("/V4/OData/OData.svc/%24metadata")));
   }

   @Test
   void aFailedFetchIsNotCachedAndIsRetriedOnTheNextCall() throws Exception {
      stubFor(get(urlPathEqualTo("/V4/OData/OData.svc/%24metadata"))
                 .willReturn(aResponse().withStatus(500)));

      ODataRuntime runtime = new ODataRuntime();
      assertThrows(Exception.class, () -> runtime.listDatasets(dataSource));

      stubFor(get(urlPathEqualTo("/V4/OData/OData.svc/%24metadata"))
                 .willReturn(okXml(readXml("catalog.metadata.xml"))));

      var catalog = runtime.listDatasets(dataSource);
      assertEquals(2, catalog.datasets().size());

      verify(exactly(2), getRequestedFor(urlPathEqualTo("/V4/OData/OData.svc/%24metadata")));
   }

   private String readXml(String file) throws IOException {
      try(InputStream input = getClass().getResourceAsStream(file)) {
         assert input != null;
         return IOUtils.toString(input, StandardCharsets.UTF_8);
      }
   }

   @Configuration
   static class TestConfig {
      @Bean
      public CredentialService credentialService() {
         CredentialService credentialService = mock(CredentialService.class);
         when(credentialService.createCredential(CredentialType.PASSWORD))
            .thenReturn(mock(LocalPasswordCredential.class));
         when(credentialService.createCredential(CredentialType.PASSWORD, false))
            .thenReturn(mock(LocalPasswordCredential.class));
         return credentialService;
      }
   }
}
