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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.rest.datasource.graphql.GraphQLDataSource;
import inetsoft.uql.rest.datasource.graphql.GraphQLRuntime;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.credential.CredentialService;
import inetsoft.util.credential.CredentialType;
import inetsoft.util.credential.LocalPasswordCredential;
import inetsoft.web.wiz.model.DatabaseTableInfo;
import inetsoft.web.wiz.model.DatasourceTablesResponse;
import inetsoft.web.wiz.model.osi.OsiRelationship;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Charter G17, through the REAL {@link TabularCatalogService#listTables(String)} -- the headline
 * check the charter requires because a narrower test "cannot tell 'the connector is right' from
 * 'the caller caught it'". Declared in {@code TabularCatalogService}'s own package
 * ({@code inetsoft.web.wiz.service}) so it can reach the package-private test-seam constructor,
 * but lives in THIS module ({@code inetsoft-rest}, not {@code core}) because {@code core} cannot
 * depend on connector classes ({@link GraphQLRuntime}/{@link GraphQLDataSource}) -- the same
 * cross-module, same-package-name technique {@code RestSourceTypesAnnotationClassTest} uses for
 * {@code WizDatabaseController}'s package-private {@code classifyQueryClass}.
 *
 * <p>The negative control that proves this harness can actually detect a dangling endpoint
 * (charter's GC-17c) is NOT re-implemented here: {@code TabularCatalogServiceContractValidationTest}
 * in {@code core} (methods {@code listTables_relationshipFromDatasetMissing_throwsNamingTheViolation}
 * / {@code listTables_relationshipToDatasetMissing_throwsNamingTheViolation}) already drives
 * {@link TabularCatalogService#listTables(String)} with a fake provider that returns a dangling
 * relationship and confirms it throws naming the violation -- that is generic infrastructure
 * behavior, not GraphQL-specific, and duplicating it here would test the same code twice for no
 * new signal.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  GraphQLCatalogServiceListTablesTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@WireMockTest
class GraphQLCatalogServiceListTablesTest {
   private GraphQLDataSource dataSource;
   private GraphQLRuntime runtime;

   @AfterAll
   static void resetContext() {
      ConfigurationContext.getContext().setApplicationContext(null);
   }

   @BeforeEach
   void setupDataSource(WireMockRuntimeInfo info, TestInfo testInfo) {
      dataSource = new GraphQLDataSource();
      dataSource.setURL(info.getHttpBaseUrl() + "/graphql");
      dataSource.setName("GraphQLCatalogServiceListTablesTest/" + testInfo.getDisplayName());
      runtime = new GraphQLRuntime();
   }

   @Test
   void listTablesThroughTheRealServiceExcludesTheDanglingEdgeAndItsTarget() throws Exception {
      stubFor(post(urlPathEqualTo("/graphql")).willReturn(okJson(okIntrospectionBody())));

      XRepository xrepository = mock(XRepository.class);
      when(xrepository.getDataSource(dataSource.getFullName())).thenReturn(dataSource);

      TabularCatalogService service =
         new TabularCatalogService(xrepository, new ObjectMapper(), dsName -> runtime);

      DatasourceTablesResponse response = service.listTables(dataSource.getFullName());

      List<String> tableNames =
         response.getTables().stream().map(DatabaseTableInfo::getTable).toList();
      assertTrue(tableNames.contains("orders"));
      assertTrue(tableNames.contains("customers"));
      assertTrue(tableNames.contains("products"));
      assertFalse(tableNames.contains("LineItem"),
         "LineItem has no root Query field and must never surface as a table");

      for(OsiRelationship rel : response.getRelationships()) {
         assertNotEquals("LineItem", rel.getFrom());
         assertNotEquals("LineItem", rel.getTo());
      }
   }

   private static String okIntrospectionBody() throws Exception {
      // The same fixture GraphQLCatalogTest/GraphQLRuntimeCatalogTest use directly -- reached via
      // GraphQLRuntime's own classpath (both modules share the same resource root).
      try(InputStream in = GraphQLRuntime.class.getResourceAsStream(
         "/inetsoft/uql/rest/datasource/graphql/bookstore-schema.json"))
      {
         assertNotNull(in, "missing test resource bookstore-schema.json");
         return IOUtils.toString(in, StandardCharsets.UTF_8);
      }
   }

   @Configuration
   static class TestConfig {
      @Bean
      public CredentialService credentialService() {
         CredentialService credentialService = mock(CredentialService.class);
         when(credentialService.createCredential(CredentialType.PASSWORD_OAUTH2_WITH_FLAGS, false))
            .thenAnswer(invocation -> mock(LocalPasswordCredential.class));
         return credentialService;
      }
   }
}
