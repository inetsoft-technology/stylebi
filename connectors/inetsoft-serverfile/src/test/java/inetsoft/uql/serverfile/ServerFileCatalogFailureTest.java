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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers charter C4: {@code listDatasets} never returns an empty catalog to SIGNAL failure -- it
 * throws. The other side of C4 is that a genuinely empty, readable root returns an empty catalog
 * rather than throwing; "failed" and "genuinely empty" are different claims and both are tested
 * here, deliberately not collapsed into one case.
 *
 * <p>The "unreadable directory mid-walk" and "unopenable workbook" failure modes D.9 also names
 * are exercised structurally by {@link ServerFileCatalog#collect}'s own null-check and by {@link
 * ServerFileCatalog}'s {@code readSheetNames} (both throw named exceptions rather than skipping) --
 * not given dedicated fixture tests here because reliably revoking read permission on a directory
 * is not portable on this repo's Windows test runner (see the SKILL's own caution on this), and a
 * corrupted-workbook fixture would add a Spring test context for a failure mode structurally
 * identical to the ones this class already exercises (throw, don't swallow).
 */
class ServerFileCatalogFailureTest {
   @TempDir
   File root;

   @Test
   void aNonexistentRootThrows_ratherThanReturningAnEmptyCatalog() {
      ServerFileDataSource ds = new ServerFileDataSource();
      ds.setName("failure-test-ds");
      ds.setFile(new File(root, "does-not-exist"));

      assertThrows(Exception.class, () -> ServerFileCatalog.listDatasets(ds));
   }

   @Test
   void aGenuinelyEmptyReadableRootReturnsAnEmptyCatalog_doesNotThrow() throws Exception {
      ServerFileDataSource ds = new ServerFileDataSource();
      ds.setName("failure-test-ds-empty");
      ds.setFile(root);   // @TempDir -- exists, readable, genuinely empty

      TabularCatalog catalog = ServerFileCatalog.listDatasets(ds);
      assertNotNull(catalog.datasets());
      assertTrue(catalog.datasets().isEmpty());
   }

   @Test
   void aFileGivenAsTheRootThrows_ratherThanBeingTreatedAsAnEmptyDirectory() throws Exception {
      File notADirectory = new File(root, "plain.csv");
      Files.writeString(notADirectory.toPath(), "a\n1\n");

      ServerFileDataSource ds = new ServerFileDataSource();
      ds.setName("failure-test-ds-file-root");
      ds.setFile(notADirectory);

      assertThrows(Exception.class, () -> ServerFileCatalog.listDatasets(ds));
   }
}
