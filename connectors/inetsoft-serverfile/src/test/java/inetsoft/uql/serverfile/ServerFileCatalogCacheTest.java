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
package inetsoft.uql.serverfile;

import inetsoft.uql.tabular.TabularCatalog;
import inetsoft.uql.tabular.TabularDatasetRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers charter A14/A15: the enumeration cache is {@code static} (a per-request runtime instance
 * makes an instance field never hit -- see {@link ServerFileCatalogCache}'s javadoc), a failed
 * enumeration is not cached, and the order guarantee is NOT proven here -- see
 * {@code ServerFileCatalogOrderTest}, which binds the UNCACHED {@code ServerFileCatalog.listDatasets}
 * directly, because "two consecutive calls are identical" against the CACHED entry point would
 * pass trivially (same cached object) whether or not the sort exists (03-reconcile.md D-2).
 */
class ServerFileCatalogCacheTest {
   @TempDir
   File root;

   private ServerFileDataSource ds;

   @BeforeEach
   void setUp() {
      ds = new ServerFileDataSource();
      // Unique per test so no two tests in this class can ever share a cache entry.
      ds.setName("cache-test-ds-" + System.nanoTime());
      ds.setFile(root);
   }

   @Test
   void twoRuntimeInstancesShareOneEnumeration() throws Exception {
      Files.writeString(new File(root, "a.csv").toPath(), "id\n1\n");

      // TabularUtil.createRuntime builds a fresh ServerFileRuntime per request -- an
      // instance-field cache would never be hit. Mimicked here with two separate instances.
      TabularCatalog first = new ServerFileRuntime().listDatasets(ds);
      assertEquals(List.of("a.csv"), idsOf(first));

      // A file added AFTER the first call. If the cache were per-instance (or absent), the
      // second runtime's call would re-walk and see it; the static, TTL-backed cache must still
      // answer from the FIRST snapshot.
      Files.writeString(new File(root, "b.csv").toPath(), "id\n1\n");
      TabularCatalog second = new ServerFileRuntime().listDatasets(ds);
      assertEquals(List.of("a.csv"), idsOf(second),
         "a second ServerFileRuntime instance must still hit the SAME static cache entry");
   }

   @Test
   void aFailedEnumerationIsNotCached() throws Exception {
      ServerFileDataSource broken = new ServerFileDataSource();
      broken.setName("cache-test-broken-" + System.nanoTime());
      File missingRoot = new File(root, "does-not-exist-yet");
      broken.setFile(missingRoot);

      ServerFileRuntime runtime = new ServerFileRuntime();
      assertThrows(Exception.class, () -> runtime.listDatasets(broken));

      // Fix the root, then retry immediately through the SAME cache key (well inside the TTL). A
      // cached exception would make this second call fail too, freezing a transient failure for
      // the whole TTL window instead of retrying it.
      assertTrue(missingRoot.mkdirs());
      Files.writeString(new File(missingRoot, "a.csv").toPath(), "id\n1\n");

      TabularCatalog retried = runtime.listDatasets(broken);
      assertEquals(List.of("a.csv"), idsOf(retried));
   }

   private static List<String> idsOf(TabularCatalog catalog) {
      return catalog.datasets().stream().map(TabularDatasetRef::id).collect(Collectors.toList());
   }
}
