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
package inetsoft.web.wiz.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.rest.datasource.airtable.AirtableDataSource;
import inetsoft.uql.rest.datasource.airtable.AirtableRuntime;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.credential.ApiTokenCredential;
import inetsoft.util.credential.CredentialService;
import inetsoft.util.credential.CredentialType;
import inetsoft.web.wiz.model.DatasourceTablesResponse;
import inetsoft.web.wiz.model.osi.OsiDataset;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #81472: the Airtable catalog through {@link TabularCatalogService}, the caller behind
 * {@code GET /datasource/tables} and {@code POST /datasource/table/meta} (and so behind the
 * plugin's {@code list_tabular_targets}). Its validators (non-blank unique ids, XSchema column
 * types, params shape) run over what the connector really emits.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  AirtableTabularCatalogServiceTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@WireMockTest
class AirtableTabularCatalogServiceTest {
   private static final String DS_NAME = "Airtable/Test";
   private TabularCatalogService service;

   @AfterAll
   static void resetContext() {
      ConfigurationContext.getContext().setApplicationContext(null);
   }

   @BeforeEach
   void setup(WireMockRuntimeInfo info) throws Exception {
      String url = info.getHttpBaseUrl() + "/";
      AirtableDataSource dataSource = new AirtableDataSource() {
         @Override
         public String getURL() {
            return url;
         }
      };
      dataSource.setName(DS_NAME);
      XRepository repository = mock(XRepository.class);
      when(repository.getDataSource(DS_NAME)).thenReturn(dataSource);
      service = new TabularCatalogService(
         repository, new ObjectMapper(), name -> new AirtableRuntime());

      stubFor(get(urlPathEqualTo("/v0/meta/bases")).willReturn(okJson(
         "{\"bases\":[{\"id\":\"appAAA111\",\"name\":\"Sales\",\"permissionLevel\":\"create\"}]}")));
      stubFor(get(urlPathEqualTo("/v0/meta/bases/appAAA111/tables")).willReturn(okJson(
         "{\"tables\":[{\"name\":\"Sales & Returns\",\"fields\":[" +
         "{\"id\":\"f1\",\"name\":\"Ref\",\"type\":\"singleLineText\"}," +
         "{\"id\":\"f2\",\"name\":\"Amount\",\"type\":\"currency\"}]}," +
         "{\"name\":\"Order / No.\",\"fields\":[" +
         "{\"id\":\"f3\",\"name\":\"When\",\"type\":\"date\"}]}]}")));
   }

   @Test
   void listTablesPassesTheServiceValidationAndReturnsOneRowPerTable() throws Exception {
      DatasourceTablesResponse response = service.listTables(DS_NAME);

      assertEquals(List.of("appAAA111/Sales & Returns", "appAAA111/Order / No."),
                   response.getTables().stream().map(t -> t.getTable()).toList());
      assertTrue(response.getRelationships().isEmpty());
   }

   @Test
   void describeTableOfAListedIdYieldsTheColumnsARecordsQueryReturns() throws Exception {
      OsiDataset dataset = service.describeTable(DS_NAME, "appAAA111/Sales & Returns");

      assertEquals("appAAA111/Sales & Returns", dataset.getName());
      assertEquals(List.of("records.id", "records.createdTime", "records.fields.Ref",
                           "records.fields.Amount"),
                   dataset.getFields().stream().map(f -> f.getName()).toList());
      JsonNode data = new ObjectMapper().readTree(dataset.getCustomExtensions().get(0).getData());
      assertEquals("tabular", data.get("datasourceType").asText());
      assertEquals(DS_NAME, data.get("dsName").asText());
   }

   @Configuration
   static class TestConfig {
      @Bean
      public CredentialService credentialService() {
         CredentialService service = mock(CredentialService.class);
         ApiTokenCredential credential = mock(ApiTokenCredential.class);
         when(credential.getApiToken()).thenReturn("tok-123");
         when(service.createCredential(any(CredentialType.class))).thenReturn(credential);
         when(service.createCredential(any(CredentialType.class), anyBoolean()))
            .thenReturn(credential);
         return service;
      }
   }
}
