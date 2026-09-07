/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.uql.mongodb;

import inetsoft.uql.VariableTable;
import inetsoft.uql.XTableNode;
import inetsoft.uql.tabular.TabularCatalog;
import inetsoft.uql.tabular.TabularDatasetRef;
import inetsoft.uql.tabular.TabularDatasetSchema;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.credential.*;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for <tt>MongoRuntime</tt>.
 */
@Testcontainers
@Disabled
class MongoRuntimeTest {
   private static final Logger LOG = LoggerFactory.getLogger(MongoRuntimeTest.class);

   @Container
   static MongoDbContainer container = new MongoDbContainer()
      .withEnv("MONGO_INITDB_ROOT_USERNAME", "root")
      .withEnv("MONGO_INITDB_ROOT_PASSWORD", "password")
      .withClasspathResourceMapping("/inetsoft/uql/mongodb/bootstrap.js", "/docker-entrypoint-initdb.d/bootstrap.js", BindMode.READ_ONLY)
      .waitingFor(Wait.forLogMessage(".*waiting for connections on port 27017.*\\n", 2))
      .withStartupTimeout(Duration.of(5L, ChronoUnit.MINUTES));


   @BeforeAll
   static void attachLogConsumer() {
      container.followOutput(new Slf4jLogConsumer(LOG));
      // A bare Mockito mock() does not remember what setUser/setPassword are called with -- every
      // getUser()/getPassword() call would answer null regardless, so the driver would silently
      // connect with no credentials at all against an auth-enabled server. A real
      // LocalPasswordCredential (a plain field-backed POJO) is required for authentication to
      // actually happen in this test.
      CredentialService credentialService = mock(CredentialService.class);
      when(credentialService.createCredential(CredentialType.PASSWORD))
         .thenAnswer(invocation -> new LocalPasswordCredential());
      when(credentialService.createCredential(CredentialType.PASSWORD, false))
         .thenAnswer(invocation -> new LocalPasswordCredential());
      ApplicationContext context = mock(ApplicationContext.class);
      when(context.getBean(CredentialService.class)).thenReturn(credentialService);
      ConfigurationContext.getContext().setApplicationContext(context);
   }

   @AfterAll
   static void resetContext() {
      ConfigurationContext.getContext().setApplicationContext(null);
   }

   @Test
   void testDataSourceShouldSucceed() throws Exception {
      MongoDataSource dataSource = new MongoDataSource();
      dataSource.setHost(container.getHost());
      dataSource.setPort(container.getPort());
      dataSource.setDB("test");
      dataSource.setUser("test");
      dataSource.setPassword("password");

      MongoRuntime runtime = new MongoRuntime();
      runtime.testDataSource(dataSource, new VariableTable());
   }

   @Test
   void testRunQuery() {
      MongoDataSource dataSource = new MongoDataSource();
      dataSource.setHost(container.getHost());
      dataSource.setPort(container.getPort());
      dataSource.setDB("test");
      dataSource.setUser("test");
      dataSource.setPassword("password");

      MongoQuery query = new MongoQuery();
      query.setDataSource(dataSource);
      query.setQueryString("{aggregate: 'table1', pipeline: [{$sort: {company: 1}}]}");

      MongoRuntime runtime = new MongoRuntime();
      XTableNode data = runtime.runQuery(query, new VariableTable());
      assertNotNull(data);

      assertEquals(6, data.getColCount());
      assertEquals("_id", data.getName(0));
      assertEquals("company", data.getName(1));
      assertEquals("state", data.getName(2));
      assertEquals("web", data.getName(3));
      assertEquals("revenue", data.getName(4));
      assertEquals("phone", data.getName(5));

      List<Object[]> expectedData = Arrays.asList(
         new Object[] { "AT&T", "NJ", "www.att.com", 123000D, "732-123-8899" },
         new Object[] { "IBM", "NY", "www.ibm.com", 21099D, "212-388-8211" }
      );

      int rowCount = 0;
      Object[] row = new Object[data.getColCount() - 1];

      while(data.next()) {
         assertThat(expectedData.size(), greaterThan(rowCount));

         Object id = data.getObject(0);
         assertNotNull(id);
         assertThat(id, instanceOf(String.class));

         for(int i = 0; i < row.length; i++) {
            Object value = data.getObject(i + 1);
            assertNotNull(value);
            Class<?> expectedType = i == 3 ? Double.class : String.class;
            assertThat(value, instanceOf(expectedType));
            row[i] = value;
         }

         assertArrayEquals(expectedData.get(rowCount++), row);
      }
   }

   private MongoDataSource dataSource() {
      MongoDataSource dataSource = new MongoDataSource();
      dataSource.setHost(container.getHost());
      dataSource.setPort(container.getPort());
      dataSource.setDB("test");
      dataSource.setUser("test");
      dataSource.setPassword("password");
      return dataSource;
   }

   @Test
   void listDatasets_excludesDottedCollection_keepsEveryOtherCollection() throws Exception {
      MongoRuntime runtime = new MongoRuntime();
      TabularCatalog catalog = runtime.listDatasets(dataSource());
      Set<String> ids = catalog.datasets().stream().map(TabularDatasetRef::id)
         .collect(Collectors.toSet());

      assertTrue(ids.contains("table1"));
      assertTrue(ids.contains("catalog_shapes"));
      assertTrue(ids.contains("catalog_empty"));
      // GridFS-shaped name from bootstrap.js -- must not appear, and must not have taken every
      // other collection down with it (TabularDatasetRef.id forbids '.').
      assertTrue(ids.stream().noneMatch(id -> id.contains(".")));
      assertTrue(catalog.relationships().isEmpty());
   }

   @Test
   void describeDataset_unionsKeysAndResolvesTypesAcrossRealDocuments() throws Exception {
      MongoRuntime runtime = new MongoRuntime();
      TabularDatasetSchema schema = runtime.describeDataset(dataSource(), "catalog_shapes");

      Map<String, String> types = schema.columns().stream()
         .collect(Collectors.toMap(c -> c.name(), c -> c.type()));

      // "_id" is not set by the fixture -- MongoDB auto-assigns an ObjectId to every document at
      // insert time regardless, so it is still expected on every sampled document.
      assertEquals(Set.of("_id", "name", "qty", "active", "when", "onlyInSecond"), types.keySet());
      assertEquals(XSchema.STRING, types.get("_id"));
      assertEquals(XSchema.STRING, types.get("name"));
      // First document's "qty" is a 32-bit int -- first-non-null-in-scan-order wins even though
      // the second document's "qty" is a 64-bit long.
      assertEquals(XSchema.INTEGER, types.get("qty"));
      assertEquals(XSchema.BOOLEAN, types.get("active"));
      assertEquals(XSchema.TIME_INSTANT, types.get("when"));
      // Only present in the second sampled document -- proves the key union, not just the first
      // document's shape, drives the reported column list.
      assertEquals(XSchema.STRING, types.get("onlyInSecond"));
      assertTrue(schema.columnsMayBeIncomplete());
      assertEquals(List.of("_id"), schema.keyColumns());
      assertEquals("catalog_shapes", schema.datasetId());
   }

   @Test
   void describeDataset_emptyExistingCollection_throws() {
      MongoRuntime runtime = new MongoRuntime();

      Exception ex = assertThrows(Exception.class,
         () -> runtime.describeDataset(dataSource(), "catalog_empty"));

      assertTrue(ex.getMessage().contains("catalog_empty"));
   }
}
