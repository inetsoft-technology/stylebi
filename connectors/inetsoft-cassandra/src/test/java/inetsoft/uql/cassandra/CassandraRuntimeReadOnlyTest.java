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
package inetsoft.uql.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import inetsoft.uql.VariableTable;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.credential.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationContext;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #79589: the connector is read-only. A non-SELECT queryString must be refused before any
 * session is opened, so no write/DDL statement ever reaches Cassandra. No Cassandra is needed.
 */
class CassandraRuntimeReadOnlyTest {
   @BeforeAll
   static void mockService() {
      CredentialService credentialService = mock(CredentialService.class);
      when(credentialService.createCredential(CredentialType.PASSWORD))
         .thenReturn(mock(LocalPasswordCredential.class));
      when(credentialService.createCredential(CredentialType.PASSWORD, false))
         .thenReturn(mock(LocalPasswordCredential.class));
      ApplicationContext context = mock(ApplicationContext.class);
      when(context.getBean(CredentialService.class)).thenReturn(credentialService);
      ConfigurationContext.getContext().setApplicationContext(context);
   }

   @AfterAll
   static void resetContext() {
      ConfigurationContext.getContext().setApplicationContext(null);
   }

   /** Counts session opens; always fails so nothing can execute even if reached. */
   private static final class CountingRuntime extends CassandraRuntime {
      final AtomicInteger opened = new AtomicInteger();

      @Override
      CqlSession openSession(CassandraDataSource ds) throws Exception {
         opened.incrementAndGet();
         throw new Exception("no session in unit test");
      }
   }

   private static int runAndCountSessions(String cql) {
      CassandraDataSource ds = new CassandraDataSource();
      ds.setHost("198.51.100.1");
      ds.setKeyspace("ks");
      CassandraQuery query = new CassandraQuery();
      query.setDataSource(ds);
      query.setQueryString(cql);
      CountingRuntime runtime = new CountingRuntime();
      assertNull(runtime.runQuery(query, new VariableTable()));
      return runtime.opened.get();
   }

   @ParameterizedTest
   @ValueSource(strings = {
      "INSERT INTO t (id) VALUES (1)",
      "TRUNCATE t",
      "DROP TABLE t",
      "UPDATE t SET a = 1 WHERE id = 1",
      "DELETE FROM t WHERE id = 1",
      "CREATE TABLE x (id int PRIMARY KEY)",
      "ALTER TABLE t ADD c int",
      "USE other_ks",
      "BEGIN BATCH INSERT INTO t (id) VALUES (1) APPLY BATCH",
      "/* select */ DROP TABLE t",
      "-- select\nTRUNCATE t",
      "SELECT * FROM t; DROP TABLE t",
      "SELECT * FROM t; -- ok\nTRUNCATE t",
      "selectx * FROM t",
      "",
      "   "
   })
   void runQuery_nonSelect_neverOpensSession(String cql) {
      assertEquals(0, runAndCountSessions(cql));
   }

   @ParameterizedTest
   @ValueSource(strings = {
      "SELECT * FROM t",
      "  \n select * from t",
      "-- note\nSELECT * FROM t",
      "// note\nSELECT * FROM t",
      "/* a; b */ SeLeCt * FROM t;",
      "SELECT * FROM t WHERE name = 'a;b''; DROP TABLE t' ;  -- trailing",
      "SELECT \"weird;col\" FROM t",
      "SELECT * FROM t WHERE s = $$x; y$$"
   })
   void runQuery_select_reachesSessionOpen(String cql) {
      assertEquals(1, runAndCountSessions(cql));
   }

   @Test
   void requireReadOnlyQuery_rejectsNull() {
      assertThrows(IllegalArgumentException.class, () -> CassandraRuntime.requireReadOnlyQuery(null));
   }

   @Test
   void requireReadOnlyQuery_unterminatedLiteralOrComment_rejected() {
      assertThrows(IllegalArgumentException.class,
         () -> CassandraRuntime.requireReadOnlyQuery("SELECT 'abc FROM t"));
      assertThrows(IllegalArgumentException.class,
         () -> CassandraRuntime.requireReadOnlyQuery("/* SELECT * FROM t"));
   }

   @Test
   void requireReadOnlyQuery_messageNamesReadOnly() {
      Exception ex = assertThrows(IllegalArgumentException.class,
         () -> CassandraRuntime.requireReadOnlyQuery("DROP TABLE t"));
      assertTrue(ex.getMessage().contains("SELECT"));
   }
}
