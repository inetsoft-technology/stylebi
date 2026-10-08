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
package inetsoft.uql.table;

import inetsoft.report.composition.WorksheetService;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.util.FileSystemService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78022: other ways of removing a worksheet W2 that embeds a snapshot table of W1 (Save As
 * over W2, deleting W2's folder) must not delete W1's data files. See
 * {@link SnapshotOuterCopyOwnershipTest} for the plain removal.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  SnapshotWorksheetSaveFailureTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow") // the Spring context with the real asset repository
class SnapshotOuterCopyRemovalTest {
   @Autowired
   private WorksheetService worksheetService;

   // W2 reopened from storage (outer copy made again by updateMirrors) and saved again, then
   // overwritten by Save As of another worksheet, the way SaveWorksheetDialogService does it
   @Test
   void saveAsOverReopenedEmbeddingWorksheetKeepsOwnerFiles() throws Exception {
      AssetEntry e1 = entry("own_rm_sa_w1");
      AssetEntry e2 = entry("own_rm_sa_w2");
      AssetEntry e3 = entry("own_rm_sa_w3");
      String[] paths1 = saveOwner(e1);
      save(embed(e1), e2);

      repo().clearCache(e2);
      Worksheet ws2 = open(e2);
      save(ws2, e2);
      assertArrayEquals(paths1, storedCopy(e2).getDataPaths(), "W2 should name W1's files");

      // Save As of W3 over W2
      Worksheet ws3 = new Worksheet();
      SnapshotEmbeddedTableAssembly own = new SnapshotEmbeddedTableAssembly(ws3, "Own");
      ws3.addAssembly(own);
      ws3.setPrimaryAssembly("Own");
      own.setEmbeddedData(new XEmbeddedTable(createTable("w3", 5)));
      save(ws3, e3);

      // SaveWorksheetDialogService: ensureWorksheetDistinct, write the data, remove the target
      for(Assembly obj : ws3.getAssemblies()) {
         ((AbstractWSAssembly) obj).pasted();
      }

      SnapshotEmbeddedTableAssembly.writeDataFilesForSave(ws3);
      repo().removeSheet(e2, null, true);
      save(ws3, e2);

      assertTrue(exist(paths1), "W1's files deleted by Save As over W2");
      assertOwnerData(e1, paths1);
   }

   // a folder holding W2 is deleted
   @Test
   void removingFolderOfEmbeddingWorksheetKeepsOwnerFiles() throws Exception {
      AssetEntry e1 = entry("own_rm_fd_w1");
      AssetEntry folder =
         new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.FOLDER, "own_rm_fd", null);
      repo().addFolder(folder, null);
      AssetEntry e2 = entry("own_rm_fd/w2");
      String[] paths1 = saveOwner(e1);
      save(embed(e1), e2);
      assertArrayEquals(paths1, storedCopy(e2).getDataPaths(), "W2 should name W1's files");

      repo().removeFolder(folder, null, true);

      assertFalse(repo().containsEntry(e2));
      assertTrue(exist(paths1), "W1's files deleted by removing W2's folder");
      assertOwnerData(e1, paths1);
   }

   private void assertOwnerData(AssetEntry e1, String[] paths1) throws Exception {
      repo().clearCache(e1);
      SnapshotEmbeddedTableDataCache.getInstance().clear();

      for(String path : paths1) {
         FileSystemService.getInstance().getCacheFile(path + "_s.tdat").delete();
      }

      Worksheet ws1 = open(e1);
      XSwappableTable table = ((SnapshotEmbeddedTableAssembly) ws1.getAssembly(NAME)).getTable();
      table.moreRows(XTable.EOT);
      assertEquals(51, table.getRowCount());
      assertEquals("v4", table.getObject(5, 1));
      assertEquals("v49", table.getObject(50, 1));
   }

   private String[] saveOwner(AssetEntry entry) throws Exception {
      Worksheet ws = new Worksheet();
      SnapshotEmbeddedTableAssembly table = new SnapshotEmbeddedTableAssembly(ws, NAME);
      ws.addAssembly(table);
      ws.setPrimaryAssembly(NAME);
      table.setEmbeddedData(new XEmbeddedTable(createTable("v", 50)));
      save(ws, entry);
      return table.getDataPaths();
   }

   private static Worksheet embed(AssetEntry entry) throws Exception {
      Worksheet ws = new Worksheet();
      WSAssembly[] created = AssetUtil.copyOuterAssemblies(repo(), entry, null, ws, null);
      MirrorTableAssembly mirror =
         new MirrorTableAssembly(ws, "M", entry, true, created[created.length - 1]);
      ws.addAssembly(mirror);
      ws.setPrimaryAssembly("M");
      return ws;
   }

   private void save(Worksheet ws, AssetEntry entry) throws Exception {
      worksheetService.setWorksheet(ws, entry, null, true, false);
   }

   private static Worksheet open(AssetEntry entry) throws Exception {
      return (Worksheet) repo().getSheet(entry, null, false, AssetContent.ALL);
   }

   private static SnapshotEmbeddedTableAssembly storedCopy(AssetEntry entry) throws Exception {
      Worksheet ws = (Worksheet) repo().getStorage(entry)
         .getXMLSerializable(entry.toIdentifier(), null);

      for(Assembly a : ws.getAssemblies()) {
         if(a instanceof SnapshotEmbeddedTableAssembly && ((WSAssembly) a).isOuter()) {
            return (SnapshotEmbeddedTableAssembly) a;
         }
      }

      fail("no outer snapshot table");
      return null;
   }

   private static boolean exist(String[] paths) {
      for(String path : paths) {
         if(!EmbeddedTableStorage.getInstance().tableExists(path + "_s.tdat")) {
            return false;
         }
      }

      return true;
   }

   private static AbstractAssetEngine repo() {
      return (AbstractAssetEngine) AssetUtil.getAssetRepository(false);
   }

   private static AssetEntry entry(String name) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, name, null);
   }

   private static XSwappableTable createTable(String tag, int rows) {
      XSwappableTable table = new XSwappableTable(2, false);
      table.addRow(new Object[] { "a", "b" });

      for(int i = 0; i < rows; i++) {
         table.addRow(new Object[] { i, tag + i });
      }

      table.complete();
      return table;
   }

   private static final String NAME = "T";
}
