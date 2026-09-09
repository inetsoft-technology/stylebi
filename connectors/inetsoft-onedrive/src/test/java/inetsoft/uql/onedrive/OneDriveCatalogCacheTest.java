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
package inetsoft.uql.onedrive;

import inetsoft.uql.tabular.TabularCatalog;
import inetsoft.uql.tabular.TabularDatasetRef;
import inetsoft.util.ConfigurationContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B11: {@code OneDriveCatalogCache} -- two runtime instances share one enumeration, a failed
 * enumeration is not cached, and the cache key changes with the access token (the cross-account
 * variant of the predecessor round's F9). Driven entirely through the {@code catalog(ds, loader)}
 * test seam, since Graph itself cannot be reached in this session (charter's own verification
 * ceiling).
 */
@Tag("core")
class OneDriveCatalogCacheTest {
   @BeforeAll
   static void installContext() {
      previous = OneDriveTestSupport.installMockContext();
   }

   @AfterAll
   static void clearContext() {
      OneDriveTestSupport.clearContext(previous);
   }

   private static ConfigurationContext previous;

   private static OneDriveDataSource dataSource(String token) {
      OneDriveDataSource ds = OneDriveTestSupport.fakeDataSource("OneDrive Test");
      ds.setAccessToken(token);
      return ds;
   }

   /**
    * "Two runtime instances" is the SKILL's own required shape for a connector-held cache test
    * (`TabularUtil.createRuntime`/`createQuery` build a fresh instance per request, so an
    * instance-field cache would never be hit) -- simulated here by calling the cache's own static
    * entry point from two independent call sites, since the cache field itself is `static` and a
    * `new OneDriveRuntime()` per request is exactly what production does.
    */
   @Test
   void twoRuntimeInstancesShareOneEnumeration() throws Exception {
      OneDriveDataSource ds = dataSource("token-A");
      AtomicInteger loads = new AtomicInteger();
      TabularCatalog catalog = new TabularCatalog(List.of(new TabularDatasetRef("a.csv")), List.of());

      var loader = (java.util.concurrent.Callable<TabularCatalog>) () -> {
         loads.incrementAndGet();
         return catalog;
      };

      TabularCatalog first = OneDriveCatalogCache.catalog(ds, loader);
      TabularCatalog second = OneDriveCatalogCache.catalog(ds, loader);

      assertEquals(1, loads.get(), "the second call must be served from cache, not re-enumerate");
      assertSame(first, second);
   }

   @Test
   void aFailedEnumerationIsNotCached_theNextCallRetries() throws Exception {
      OneDriveDataSource ds = dataSource("token-B");
      AtomicInteger loads = new AtomicInteger();

      var failingThenSucceeding = (java.util.concurrent.Callable<TabularCatalog>) () -> {
         if(loads.incrementAndGet() == 1) {
            throw new java.io.IOException("transient Graph outage");
         }

         return new TabularCatalog(List.of(new TabularDatasetRef("a.csv")), List.of());
      };

      assertThrows(Exception.class,
         () -> OneDriveCatalogCache.catalog(ds, failingThenSucceeding));

      TabularCatalog second = OneDriveCatalogCache.catalog(ds, failingThenSucceeding);

      assertEquals(2, loads.get(), "a failed load must not freeze the outage for the whole TTL");
      assertNotNull(second);
   }

   @Test
   void aDifferentAccessTokenMissesRatherThanServingThePreviousAccountsListing() throws Exception {
      // The cross-account variant of the predecessor round's F9: a re-authentication to a
      // DIFFERENT account must not be served the previous account's cached listing.
      OneDriveDataSource dsAccountA = dataSource("token-account-A");
      OneDriveDataSource dsAccountB = dataSource("token-account-B");

      TabularCatalog catalogA =
         new TabularCatalog(List.of(new TabularDatasetRef("a-only.csv")), List.of());
      TabularCatalog catalogB =
         new TabularCatalog(List.of(new TabularDatasetRef("b-only.csv")), List.of());

      TabularCatalog resultA = OneDriveCatalogCache.catalog(dsAccountA, () -> catalogA);
      TabularCatalog resultB = OneDriveCatalogCache.catalog(dsAccountB, () -> catalogB);

      assertEquals("a-only.csv", resultA.datasets().get(0).id());
      assertEquals("b-only.csv", resultB.datasets().get(0).id());
   }
}
