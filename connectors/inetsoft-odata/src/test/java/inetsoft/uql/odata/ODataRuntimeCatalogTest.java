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
import inetsoft.uql.tabular.TabularCatalog;
import inetsoft.uql.tabular.TabularDatasetSchema;
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
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Charter assertion B1, through the real {@link ODataRuntime} entry points (as
 * {@code TabularCatalogService} calls them), against a stubbed {@code $metadata} endpoint.
 *
 * The contrast with {@code ODataQuery.getOutputColumns()} matters: that method silently returns
 * an empty array on a failed/malformed metadata fetch (by design, so a query dialog doesn't
 * explode). {@link TabularCatalogProvider} explicitly forbids that — a failure here MUST throw,
 * per the charter's "no silently-empty annotation" reverse assertion.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  ODataRuntimeCatalogTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@WireMockTest
class ODataRuntimeCatalogTest {
   private ODataDataSource dataSource;
   private ODataRuntime runtime;

   @AfterAll
   static void resetContext() {
      ConfigurationContext.getContext().setApplicationContext(null);
   }

   @BeforeEach
   void setupDataSource(WireMockRuntimeInfo info, TestInfo testInfo) {
      dataSource = new ODataDataSource();
      dataSource.setURL(info.getHttpBaseUrl() + "/V4/OData/OData.svc/");
      // Unique per test method (not just per port, which WireMock may reuse across methods in
      // this class) so the static ODataCatalogCache can never serve one test's cached snapshot
      // to another.
      dataSource.setName("ODataRuntimeCatalogTest/" + testInfo.getDisplayName());
      runtime = new ODataRuntime();
   }

   @Test
   void listDatasetsReturnsEntitySetsFromMetadata() throws Exception {
      stubFor(get(urlPathEqualTo("/V4/OData/OData.svc/%24metadata"))
                 .willReturn(okXml(readXml("catalog.metadata.xml"))));

      TabularCatalog catalog = runtime.listDatasets(dataSource);

      assertEquals(2, catalog.datasets().size());
      assertEquals("Products", catalog.datasets().get(0).id());
      assertEquals("Categories", catalog.datasets().get(1).id());
   }

   @Test
   void describeDatasetReturnsRealColumns() throws Exception {
      stubFor(get(urlPathEqualTo("/V4/OData/OData.svc/%24metadata"))
                 .willReturn(okXml(readXml("catalog.metadata.xml"))));

      TabularDatasetSchema schema = runtime.describeDataset(dataSource, "Products");

      assertEquals("Products", schema.datasetId());
      assertEquals(List.of("ID", "Name", "Price"),
         schema.columns().stream().map(c -> c.name()).toList());
   }

   @Test
   void describeDatasetForUnknownEntitySetThrows() throws Exception {
      stubFor(get(urlPathEqualTo("/V4/OData/OData.svc/%24metadata"))
                 .willReturn(okXml(readXml("catalog.metadata.xml"))));

      Exception ex = assertThrows(Exception.class,
         () -> runtime.describeDataset(dataSource, "NoSuchEntitySet"));
      assertTrue(ex.getMessage().contains("NoSuchEntitySet"));
   }

   @Test
   void listDatasetsThrowsRatherThanReturningEmptyWhenMetadataRequestFails() {
      stubFor(get(urlPathEqualTo("/V4/OData/OData.svc/%24metadata"))
                 .willReturn(aResponse().withStatus(500)));

      assertThrows(Exception.class, () -> runtime.listDatasets(dataSource));
   }

   @Test
   void listDatasetsThrowsRatherThanReturningEmptyWhenMetadataIsMalformed() {
      stubFor(get(urlPathEqualTo("/V4/OData/OData.svc/%24metadata"))
                 .willReturn(okXml("this is not well-formed xml <edmx:Edmx>")));

      assertThrows(Exception.class, () -> runtime.listDatasets(dataSource));
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
