/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
package inetsoft.uql.asset;

import inetsoft.storage.KeyValueStorageManager;
import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.asset.sync.DependencyStorageServiceTestAccess;
import inetsoft.uql.erm.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82450, the outer dependencies of a shared logical model are written once per import, not
 * once per imported asset, and merged into the model as stored at that time.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class LocalDependencyHandlerModelBatchTest {
   @Autowired
   private KeyValueStorageManager keyValueStorageManager;

   private DependencyStorageService service;
   private XRepository xrep;
   private XDataModel xdm;
   private LocalDependencyHandler handler;
   /** The model as stored; every read returns a copy, like the registry does. */
   private XLogicalModel stored;

   @BeforeEach
   void setUp() throws Exception {
      service = DependencyStorageServiceTestAccess.create(keyValueStorageManager);
      stored = new XLogicalModel("m");
      XEntity entity = new XEntity("e");
      entity.addAttribute(new XAttribute("a", "t", "c"));
      stored.addEntity(entity);

      xdm = Mockito.mock(XDataModel.class);
      xrep = Mockito.mock(XRepository.class);
      Mockito.when(xrep.getDataModel("ds")).thenReturn(xdm);
      Mockito.when(xdm.getLogicalModel("m"))
         .thenAnswer(inv -> (XLogicalModel) stored.clone());
      Mockito.when(xdm.clone()).thenReturn(xdm);
      Mockito.doAnswer(inv -> {
         stored = (XLogicalModel) inv.getArgument(0);
         return null;
      }).when(xdm).addLogicalModel(Mockito.any(XLogicalModel.class), Mockito.anyBoolean());
      handler = new LocalDependencyHandler(xrep);
   }

   private static AssetEntry dashboard(int i) {
      return new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.VIEWSHEET,
                            "/dash" + i, null);
   }

   /** What the per-asset path does to the model for one dependent. */
   private void addDependent(DependencyStorageService.Batch batch, int i) throws Exception {
      LocalDependencyHandler.ModelWork work = handler.getModelWork(batch, xrep, "ds", "m", "m");
      work.working.addOuterDependency(dashboard(i));
      work.working.getEntity("e").getAttribute("a").addOuterDependency(dashboard(i));
      work.saved = true;
   }

   private Set<Object> modelOuters() {
      return new HashSet<>(Arrays.asList(stored.getOuterDependencies()));
   }

   private Set<Object> attributeOuters() {
      return new HashSet<>(Arrays.asList(
         stored.getEntity("e").getAttribute("a").getOuterDependencies()));
   }

   @Test
   void modelIsWrittenOncePerImportAndResultMatchesUnbatched() throws Exception {
      Set<Object> expected = new HashSet<>();

      try(DependencyStorageService.Batch batch = service.beginBatch()) {
         for(int i = 0; i < 60; i++) {
            addDependent(batch, i);
            expected.add(dashboard(i));
         }

         assertTrue(modelOuters().isEmpty(), "nothing is stored before the flush");
      }

      Mockito.verify(xdm, Mockito.times(1))
         .addLogicalModel(Mockito.any(XLogicalModel.class), Mockito.eq(true));
      Mockito.verify(xrep, Mockito.times(1)).updateDataModel(Mockito.any());
      // read once when first touched and once by the flush, not once per asset
      Mockito.verify(xdm, Mockito.atMost(3)).getLogicalModel("m");
      assertEquals(expected, modelOuters());
      assertEquals(expected, attributeOuters());
   }

   @Test
   void flushKeepsDependentsStoredMeanwhile() throws Exception {
      try(DependencyStorageService.Batch batch = service.beginBatch()) {
         addDependent(batch, 1);
         addDependent(batch, 2);

         // another writer adds its dependent to the stored model while the import runs
         stored.addOuterDependency(dashboard(99));
         stored.getEntity("e").getAttribute("a").addOuterDependency(dashboard(99));
      }

      Set<Object> expected = Set.of(dashboard(1), dashboard(2), dashboard(99));
      assertEquals(expected, modelOuters());
      assertEquals(expected, attributeOuters());
   }

   @Test
   void failureInTheMiddleStillFlushes() throws Exception {
      assertThrows(IllegalStateException.class, () -> {
         try(DependencyStorageService.Batch batch = service.beginBatch()) {
            for(int i = 0; i < 5; i++) {
               addDependent(batch, i);
            }

            throw new IllegalStateException("package failed");
         }
      });

      assertEquals(5, modelOuters().size());
      assertEquals(5, attributeOuters().size());
   }

   @Test
   void unchangedModelIsNotWritten() throws Exception {
      try(DependencyStorageService.Batch batch = service.beginBatch()) {
         LocalDependencyHandler.ModelWork work =
            handler.getModelWork(batch, xrep, "ds", "m", "m");
         work.saved = true;
      }

      Mockito.verify(xrep, Mockito.never()).updateDataModel(Mockito.any());
      ArgumentCaptor<XLogicalModel> captor = ArgumentCaptor.forClass(XLogicalModel.class);
      Mockito.verify(xdm, Mockito.never()).addLogicalModel(captor.capture(), Mockito.anyBoolean());
   }
}
