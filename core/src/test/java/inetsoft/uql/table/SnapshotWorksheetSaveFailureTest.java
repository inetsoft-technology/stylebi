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
import inetsoft.storage.KeyValueStorageManager;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.util.MessageException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77986, end to end through {@code WorksheetEngine.setWorksheet} and the real asset
 * repository: a save whose snapshot data can't be written fails, and the stored worksheet keeps
 * its previous data. The failure kinds are covered by {@link SnapshotSaveDataFailureTest}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  SnapshotWorksheetSaveFailureTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow") // the Spring context with the real asset repository
class SnapshotWorksheetSaveFailureTest {
   @Autowired
   private WorksheetService worksheetService;

   @Test
   void failedDataWriteFailsSaveAndKeepsStoredWorksheet() throws Exception {
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
                                        "ws77986", null);
      XSwappableTable table1 = createTable("old", 50);
      XSwappableTable table2 = createTable("new", 80);
      Worksheet ws = new Worksheet();
      SnapshotEmbeddedTableAssembly assembly = new SnapshotEmbeddedTableAssembly(ws, NAME);
      ws.addAssembly(assembly);
      assembly.setEmbeddedData(new XEmbeddedTable(table1));

      try {
         worksheetService.setWorksheet(ws, entry, null, true, false);
         String[] paths0 = assembly.getDataPaths();
         assertEquals("old4", loadStored(entry).getObject(5, 1));

         assembly.setEmbeddedData(new XEmbeddedTable(table2));
         XTableFragment fragment = table2.getTables()[0];
         fragment.testBeforeWrite = () -> {
            throw new UncheckedIOException(new IOException("simulated write failure"));
         };
         MessageException ex = assertThrows(
            MessageException.class, () -> worksheetService.setWorksheet(ws, entry, null, true, false));
         fragment.testBeforeWrite = null;
         assertTrue(ex.getMessage().contains(NAME), ex.getMessage());

         XSwappableTable stored = loadStored(entry);
         assertEquals(51, stored.getRowCount(), "stored worksheet changed by the failed save");
         assertEquals("old4", stored.getObject(5, 1), "stored worksheet lost its data");

         worksheetService.setWorksheet(ws, entry, null, true, false);
         stored = loadStored(entry);
         assertEquals(81, stored.getRowCount());
         assertEquals("new79", stored.getObject(80, 1));

         for(String path : paths0) {
            assertFalse(EmbeddedTableStorage.getInstance().tableExists(path + "_s.tdat"),
                        "replaced data file not deleted: " + path);
         }
      }
      finally {
         table1.dispose();
         table2.dispose();
      }
   }

   private static XSwappableTable loadStored(AssetEntry entry) throws Exception {
      Worksheet ws = (Worksheet) AssetUtil.getAssetRepository(false)
         .getSheet(entry, null, false, AssetContent.ALL);
      XSwappableTable table = ((SnapshotEmbeddedTableAssembly) ws.getAssembly(NAME)).getTable();
      table.moreRows(XTable.EOT);
      return table;
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

   @Configuration
   static class Beans {
      // the constructor is package private; saving a sheet updates the dependency storage
      @Bean
      public DependencyStorageService dependencyStorageService(KeyValueStorageManager storage)
         throws Exception
      {
         Constructor<DependencyStorageService> ctor =
            DependencyStorageService.class.getDeclaredConstructor(KeyValueStorageManager.class);
         ctor.setAccessible(true);
         return ctor.newInstance(storage);
      }
   }

   private static final String NAME = "T77986";
}
