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
package inetsoft.web.composer.ws.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.table.XSwappableTable;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.util.DataSpace;
import inetsoft.util.MessageException;
import inetsoft.web.composer.model.ws.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.io.InputStream;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77986, save-as onto an existing worksheet removes the target before it stores the new
 * one. The snapshot data must be written before the target is removed, so that a failed data
 * write fails the save-as without losing the worksheet being overwritten.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  SaveWorksheetDialogServiceSnapshotTest.StorageConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SaveWorksheetDialogServiceSnapshotTest {
   @BeforeEach
   void setUp() throws Exception {
      engine = mock(ViewsheetService.class);
      repository = mock(AssetRepository.class);
      when(engine.getAssetRepository()).thenReturn(repository);
      when(repository.containsEntry(any())).thenReturn(true);
      DataSpace dataSpace = mock(DataSpace.class);
      when(dataSpace.beginTransaction()).thenReturn(mock(DataSpace.Transaction.class));
      service = new SaveWorksheetDialogService(engine, mock(DataSourceRegistry.class), dataSpace);

      table = new XSwappableTable(2, false);
      table.addRow(new Object[] { "a", "b" });

      for(int i = 0; i < 50; i++) {
         table.addRow(new Object[] { i, "s" + i });
      }

      table.complete();
      ws = new Worksheet();
      assembly = new SnapshotEmbeddedTableAssembly(ws, "T77986");
      ws.addAssembly(assembly);
      assembly.setEmbeddedData(new XEmbeddedTable(table));

      rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getAssetQuerySandbox()).thenReturn(mock(AssetQuerySandbox.class));
      when(rws.getEntry()).thenReturn(new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "source77986", null));
   }

   @AfterEach
   void tearDown() {
      FailingStorage.fail = false;
      table.dispose();
   }

   // the data write fails: the save-as fails and the worksheet being overwritten is not removed
   @Test
   void failedDataWriteDoesNotRemoveOverwrittenWorksheet() throws Exception {
      FailingStorage.fail = true;

      MessageException ex = assertThrows(
         MessageException.class, () -> service.process(rws, createModel(), PRINCIPAL, true));

      assertTrue(ex.getMessage().contains("T77986"), ex.getMessage());
      verify(repository, never()).removeSheet(any(), any(), anyBoolean());
      verify(engine, never()).setWorksheet(any(), any(), any(), anyBoolean(), anyBoolean());
   }

   // the data is written before the worksheet being overwritten is removed
   @Test
   void dataIsWrittenBeforeOverwrittenWorksheetIsRemoved() throws Exception {
      doAnswer(inv -> {
         String[] paths = assembly.getDataPaths();
         assertNotNull(paths, "data not written before the target is removed");

         for(String path : paths) {
            assertTrue(EmbeddedTableStorage.getInstance().tableExists(path + "_s.tdat"), path);
         }

         return null;
      }).when(repository).removeSheet(any(), any(), anyBoolean());

      service.process(rws, createModel(), PRINCIPAL, true);

      InOrder order = inOrder(repository, engine);
      order.verify(repository).removeSheet(isTarget(), any(), eq(true));
      order.verify(engine).setWorksheet(eq(ws), isTarget(), any(), eq(true), anyBoolean());
   }

   private static SaveWorksheetDialogModel createModel() {
      AssetRepositoryPaneModel pane = new AssetRepositoryPaneModel();
      pane.setName("target77986");
      pane.setParentEntry(
         new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.FOLDER, "/", null));
      return SaveWorksheetDialogModel.builder()
         .worksheetOptionPaneModel(new WorksheetOptionPaneModel())
         .assetRepositoryPaneModel(pane)
         .build();
   }

   private static AssetEntry isTarget() {
      return argThat(e -> e != null && "target77986".equals(e.getPath()));
   }

   @Configuration
   static class StorageConfiguration {
      @Bean
      public EmbeddedTableStorage embeddedTableStorage(BlobStorageManager manager) {
         return new FailingStorage(manager);
      }
   }

   /**
    * Storage whose put fails while {@link #fail} is set.
    */
   static class FailingStorage extends EmbeddedTableStorage {
      FailingStorage(BlobStorageManager manager) {
         super(manager);
      }

      @Override
      public void writeTable(String path, InputStream input, boolean temp) throws IOException {
         if(fail) {
            throw new IOException("simulated storage put failure");
         }

         super.writeTable(path, input, temp);
      }

      static volatile boolean fail;
   }

   private static final Principal PRINCIPAL = null;

   private ViewsheetService engine;
   private AssetRepository repository;
   private SaveWorksheetDialogService service;
   private XSwappableTable table;
   private Worksheet ws;
   private SnapshotEmbeddedTableAssembly assembly;
   private RuntimeWorksheet rws;
}
