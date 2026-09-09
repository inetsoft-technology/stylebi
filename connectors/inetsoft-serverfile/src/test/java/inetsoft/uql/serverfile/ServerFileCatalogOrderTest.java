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
import inetsoft.uql.tabular.TabularCatalogProvider;
import inetsoft.uql.tabular.TabularDataSource;
import inetsoft.uql.tabular.TabularDatasetRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers charter A2/C7 (enumeration order imposed by the connector, never inherited from
 * {@code File#listFiles()}) and C3 ({@code listRelationships} never overridden).
 *
 * <p>Per 03-reconcile.md D-2/A15: these tests bind the UNCACHED {@code ServerFileCatalog.listDatasets}
 * directly, never {@code ServerFileRuntime.listDatasets} -- with the 60s enumeration cache in
 * front (see {@link ServerFileCatalogCache}), "two consecutive calls are identical" would return
 * the SAME cached object and pass whether or not the sort exists. The
 * {@code orderIsStableAcrossRepeatedCalls} test below is kept only as a cheap smoke check and is
 * marked non-discriminating for exactly that reason.
 */
class ServerFileCatalogOrderTest {
   @TempDir
   File root;

   private ServerFileDataSource ds;

   @BeforeEach
   void setUp() {
      ds = new ServerFileDataSource();
      ds.setName("order-test-ds");
      ds.setFile(root);
   }

   @Test
   void orderIsStableAcrossRepeatedCalls_nonDiscriminating() throws Exception {
      // NON-DISCRIMINATING (see class javadoc): this alone would pass even if ServerFileCatalog
      // trusted native File#listFiles() order, as long as that native order happened to be stable
      // call-to-call within one test process -- which is exactly the trap the charter and the
      // TabularCatalog javadoc both call out by name. Kept as a cheap smoke check only; the
      // shuffled-fixture-driven ordering below is what actually proves the sort.
      Files.writeString(new File(root, "c.csv").toPath(), "id\n1\n");
      Files.writeString(new File(root, "a.csv").toPath(), "id\n1\n");
      Files.writeString(new File(root, "b.csv").toPath(), "id\n1\n");

      List<String> first = idsOf(ServerFileCatalog.listDatasets(ds));
      List<String> second = idsOf(ServerFileCatalog.listDatasets(ds));
      assertEquals(first, second);
   }

   @Test
   void enumerationIsSortedById_notLeftToNativeDirectoryOrder() throws Exception {
      // Created in reverse-alphabetical order on disk; a connector trusting File#listFiles()'s
      // native order (whatever the OS/filesystem happens to hand back -- not alphabetical by
      // contract on any of them) would be exposed by comparing against this FIXED expected order,
      // rather than only comparing two runs against each other.
      Files.writeString(new File(root, "c.csv").toPath(), "id\n1\n");
      Files.writeString(new File(root, "a.csv").toPath(), "id\n1\n");
      Files.writeString(new File(root, "b.csv").toPath(), "id\n1\n");

      assertEquals(List.of("a.csv", "b.csv", "c.csv"), idsOf(ServerFileCatalog.listDatasets(ds)));
   }

   @Test
   void enumerationIsRecursive() throws Exception {
      File nested = new File(root, "a/b");
      assertTrue(nested.mkdirs());
      Files.writeString(new File(nested, "deep.csv").toPath(), "id\n1\n");

      assertEquals(List.of("a/b/deep.csv"), idsOf(ServerFileCatalog.listDatasets(ds)));
   }

   @Test
   void listRelationshipsIsNeverOverridden() throws Exception {
      // C3: files have no relationships; the default (empty) is correct. Mirrors the shape of
      // TabularCatalogProviderImplementerCanaryTest's own check, done locally here because that
      // canary cannot see connector classes from core (A11).
      Method m = ServerFileRuntime.class.getMethod(
         "listRelationships", TabularDataSource.class, Collection.class);
      assertEquals(TabularCatalogProvider.class, m.getDeclaringClass());
   }

   @Test
   void listDatasetsPagedIsNeverOverridden() throws Exception {
      Method m = ServerFileRuntime.class.getMethod(
         "listDatasets", TabularDataSource.class, inetsoft.uql.tabular.TabularCatalogRequest.class);
      assertEquals(TabularCatalogProvider.class, m.getDeclaringClass(),
         "A10: default paging is kept -- a keyset cursor buys nothing on a filesystem (see " +
         "01-design.md D.3), and overriding either paged method here would have to be reconciled " +
         "with TabularCatalogProviderImplementerCanaryTest's own strictness assumption.");
   }

   private static List<String> idsOf(TabularCatalog catalog) {
      return catalog.datasets().stream().map(TabularDatasetRef::id).collect(Collectors.toList());
   }
}
