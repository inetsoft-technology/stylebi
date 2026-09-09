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

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.tabular.TabularCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers charter C4: {@code listDatasets} never returns an empty catalog to SIGNAL failure -- it
 * throws. The other side of C4 is that a genuinely empty, readable root returns an empty catalog
 * rather than throwing; "failed" and "genuinely empty" are different claims and both are tested
 * here, deliberately not collapsed into one case.
 *
 * <p>The "unreadable directory mid-walk" failure mode is exercised structurally by {@link
 * ServerFileCatalog#collect}'s own null-check -- not given a dedicated fixture test because
 * reliably revoking read permission on a directory is not portable on this repo's Windows test
 * runner (see the SKILL's own caution on this). The "unopenable workbook" failure mode IS given a
 * dedicated fixture test below (R1-3, P6 review) -- a corrupt {@code .xlsx} needs no ACLs and is
 * fully portable, so the earlier justification for skipping it did not actually apply to this one.
 * Needs the full Spring test context (see {@code ServerFileSpringTestSupport}'s class javadoc):
 * reading a real, or fake, workbook's sheet list reaches {@code FileSystemService.getInstance()}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  ServerFileSpringTestSupport.ConfigBeanConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
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

   // R1-3 (P6 review): readSheetNames' catch branch (wrapping ExcelFileSupport.getSheetNames'
   // exception into a named IOException) had zero coverage. A corrupt/unopenable workbook must
   // make the WHOLE listDatasets call throw, not silently skip that one file and return a short
   // (partial) catalog -- the same C4 requirement the directory-level tests above pin, at the
   // per-file level this time.
   @Test
   void anUnopenableWorkbookThrows_ratherThanBeingSkippedFromTheCatalog() throws Exception {
      // Garbage bytes with a .xlsx extension: not a zip at all, so ExcelFileSupport.getSheetNames
      // throws, no ACLs/permissions involved -- fully portable, unlike the directory-unreadable
      // case above.
      Files.write(new File(root, "corrupt.xlsx").toPath(), "this is not a zip file".getBytes());

      ServerFileDataSource ds = new ServerFileDataSource();
      ds.setName("failure-test-ds-corrupt-workbook");
      ds.setFile(root);

      assertThrows(Exception.class, () -> ServerFileCatalog.listDatasets(ds));
   }
}
