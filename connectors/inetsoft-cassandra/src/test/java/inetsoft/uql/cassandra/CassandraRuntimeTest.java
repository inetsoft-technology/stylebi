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
package inetsoft.uql.cassandra;

import inetsoft.uql.VariableTable;
import inetsoft.uql.XTableNode;
import inetsoft.util.credential.*;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.testcontainers.containers.CassandraContainer;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Integration tests for <tt>CassandraRuntime</tt>
 */
class CassandraRuntimeTest {
   private CassandraContainer cassandraContainer;
   private String cassandraHost;
   private int cassandraPort;

   @BeforeAll
   static void mockService() {
      MockedStatic<CredentialService> mockedCredentialService = Mockito.mockStatic(CredentialService.class);
      mockedCredentialService.when(() -> CredentialService.newCredential(CredentialType.PASSWORD))
         .thenReturn(mock(LocalPasswordCredential.class));
      mockedCredentialService.when(() -> CredentialService.newCredential(CredentialType.PASSWORD, false))
         .thenReturn(mock(LocalPasswordCredential.class));
   }

   /**
    * Starts Cassandra.
    */
   @BeforeEach
   void startCassandra() {
      cassandraContainer =
         new CassandraContainer().withInitScript("inetsoft/uql/cassandra/init.sql");
      cassandraContainer.start();
      cassandraHost = cassandraContainer.getContainerIpAddress();
      cassandraPort = cassandraContainer.getMappedPort(CassandraContainer.CQL_PORT);
   }

   @AfterEach
   void stopCassandra() {
      cassandraContainer.stop();
   }

   /**
    * Test the <tt>runQuery</tt> for proper operation.
    *
    * @throws AssertionError if the test fails.
    * @throws Exception if an unexpected error occurs.
    */
   @Test
   @Disabled
   void testRunQuery() throws Exception {
      CassandraDataSource dataSource = new CassandraDataSource();
      dataSource.setHost(cassandraHost);
      dataSource.setPort(cassandraPort);
      dataSource.setUser("cassandra");
      dataSource.setPassword("cassandra");
      dataSource.setKeyspace("mykeyspace");

      CassandraQuery query = new CassandraQuery();
      query.setDataSource(dataSource);
      query.setQueryString("select * from users");

      CassandraRuntime runtime = new CassandraRuntime();
      XTableNode data = runtime.runQuery(query, new VariableTable());
      assertNotNull(data);

      assertEquals(3, data.getColCount());
      assertEquals("user_id", data.getName(0));
      assertEquals("fname", data.getName(1));
      assertEquals("lname", data.getName(2));

      Map<Integer, String[]> expectedData = new HashMap<>();
      expectedData.put(1744, new String[] { "john", "doe" });
      expectedData.put(1745, new String[] { "john", "smith" });
      expectedData.put(1746, new String[] { "john", "smith" });

      Map<Integer, String[]> actualData = new HashMap<>();

      try {
         while(data.next()) {
            Object id = data.getObject(0);
            assertNotNull(id);
            assertThat(id, instanceOf(Integer.class));

            Object fname = data.getObject(1);
            assertNotNull(fname);
            assertThat(fname, instanceOf(String.class));

            Object lname = data.getObject(2);
            assertNotNull(lname);
            assertThat(lname, instanceOf(String.class));

            actualData.put((Integer) id, new String[] { (String) fname, (String) lname });
         }
      }
      finally {
         data.close();
      }

      assertEquals(expectedData.size(), actualData.size());

      for(Map.Entry<Integer, String[]> actualEntry : actualData.entrySet()) {
         String[] expectedValue = expectedData.get(actualEntry.getKey());
         assertNotNull(expectedValue);
         assertArrayEquals(expectedValue, actualEntry.getValue());
      }
   }
}
