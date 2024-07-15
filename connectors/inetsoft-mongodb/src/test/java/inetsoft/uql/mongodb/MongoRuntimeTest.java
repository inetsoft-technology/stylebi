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
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.*;

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
}
