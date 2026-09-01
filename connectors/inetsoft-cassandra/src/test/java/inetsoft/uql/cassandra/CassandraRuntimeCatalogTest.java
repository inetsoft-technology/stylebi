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

import inetsoft.util.ConfigurationContext;
import inetsoft.util.credential.*;
import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationContext;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers charter assertion A4's "blank/null keyspace" path through {@link CassandraRuntime}'s new
 * SPI methods — the one part of A4 that is provably testable without a real cluster, since
 * {@code requireKeyspace} runs before {@link CassandraRuntime#getSession} is ever called. Both
 * data sources here point at a reserved, documentation-only address
 * (RFC 5737 TEST-NET-2, 198.51.100.1) that is guaranteed never to be dialed: if the guard did not
 * run first, {@code getSession} would attempt a real connection and fail with a driver
 * connection exception instead, not this method's own message — {@link Assertions#assertTimeout}
 * additionally bounds the wait, since a real (doomed) connection attempt would take substantially
 * longer than an in-process check.
 */
class CassandraRuntimeCatalogTest {
   private static final String UNROUTABLE_HOST = "198.51.100.1";

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

   @Test
   void listDatasets_blankKeyspace_throwsBeforeConnecting() {
      CassandraDataSource ds = new CassandraDataSource();
      ds.setHost(UNROUTABLE_HOST);
      ds.setKeyspace("");
      CassandraRuntime runtime = new CassandraRuntime();

      Exception ex = assertTimeout(Duration.ofSeconds(2),
         () -> assertThrows(Exception.class, () -> runtime.listDatasets(ds)));

      assertTrue(ex.getMessage().contains("no keyspace configured"));
   }

   @Test
   void describeDataset_nullKeyspace_throwsBeforeConnecting() {
      CassandraDataSource ds = new CassandraDataSource();
      ds.setHost(UNROUTABLE_HOST);
      // keyspace left null — the field's default, never set.
      CassandraRuntime runtime = new CassandraRuntime();

      Exception ex = assertTimeout(Duration.ofSeconds(2),
         () -> assertThrows(Exception.class, () -> runtime.describeDataset(ds, "any_table")));

      assertTrue(ex.getMessage().contains("no keyspace configured"));
   }
}
