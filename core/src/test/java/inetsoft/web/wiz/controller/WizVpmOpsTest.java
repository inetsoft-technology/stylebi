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
package inetsoft.web.wiz.controller;

import inetsoft.test.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.erm.vpm.VirtualPrivateModel;
import inetsoft.web.admin.content.database.model.DataModelFolderManagerService;
import inetsoft.web.portal.controller.database.LogicalModelService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static inetsoft.web.wiz.controller.WizModelKindOps.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The VPM implementation of the kind SPI ({@code WizVpmOps}) behind the kind-agnostic item
 * controller: a rename keeps the stored description unless one is given (finding k, C2), a delete is
 * read back, a caller without WRITE on the source neither sees nor changes a VPM, and a new name is
 * checked against the one name space.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  WizVpmTestSupport.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
@Tag("integration")
class WizVpmOpsTest {
   private WizVpmTestSupport support;
   private WizVpmOps ops;
   private WizDataModelItemController items;

   @BeforeEach
   void setUp() throws Exception {
      support = new WizVpmTestSupport();
      ops = new WizVpmOps(support.dataSourceService, support.service);
      items = new WizDataModelItemController(
         List.of(ops), mock(DataModelFolderManagerService.class), support.dataSourceService,
         mock(LogicalModelService.class), support.repository);
      VirtualPrivateModel vpm = new VirtualPrivateModel("A");
      vpm.setDescription("stored description");
      support.fresh().addVirtualPrivateModel(vpm, true);
   }

   @Test
   void renameKeepsTheStoredDescriptionUnlessOneIsGiven() throws Exception {
      Object result = items.rename(new WizDataModelWire.RenameRequest(
         support.ds, "vpm", "A", "B", null), support.bob);

      WizDataModelWire.RenameResponse response = assertInstanceOf(
         WizDataModelWire.RenameResponse.class, result);
      assertTrue(response.ok());
      assertEquals("B", response.name());
      XDataModel after = support.fresh();
      assertNull(after.getVirtualPrivateModel("A"));
      assertEquals("stored description", after.getVirtualPrivateModel("B").getDescription());

      items.rename(new WizDataModelWire.RenameRequest(support.ds, "vpm", "B", "C", "new text"),
                   support.bob);
      assertEquals("new text", support.fresh().getVirtualPrivateModel("C").getDescription());
   }

   @Test
   void renameOntoAnotherKindOrWithoutWriteIsRefused() throws Exception {
      WizDataModelWire.Failure taken = assertInstanceOf(WizDataModelWire.Failure.class,
         items.rename(new WizDataModelWire.RenameRequest(support.ds, "vpm", "A", "pv", null),
                      support.bob));
      assertEquals("DUPLICATE_NAME", taken.reason());
      assertEquals("physicalView", taken.existingKind());
      assertNotNull(support.fresh().getVirtualPrivateModel("A"));

      // a caller without WRITE on the source is refused with a denial naming the path
      support.writeAllowed = false;
      SecurityException hidden = assertThrows(SecurityException.class, () -> items.rename(
         new WizDataModelWire.RenameRequest(support.ds, "vpm", "A", "Z", null), support.bob));
      assertTrue(hidden.getMessage().contains(support.ds), hidden.getMessage());
      support.writeAllowed = true;
      assertNull(support.fresh().getVirtualPrivateModel("Z"));
      assertNotNull(support.fresh().getVirtualPrivateModel("A"));
   }

   @Test
   void deleteRemovesTheVpmAndIsReadBack() throws Exception {
      WizDataModelWire.DeleteResponse response = assertInstanceOf(
         WizDataModelWire.DeleteResponse.class,
         items.delete(new WizDataModelWire.DeleteRequest(support.ds, List.of(
            new WizDataModelWire.DeleteItem("vpm", "A", null, false, null))), support.bob));

      assertEquals(1, response.results().size());
      assertTrue(response.results().get(0).ok(), response.results().get(0).message());
      assertNull(support.fresh().getVirtualPrivateModel("A"));
   }

   @Test
   void deleteWithoutWriteIsReportedPerItemAndRemovesNothing() throws Exception {
      support.writeAllowed = false;
      WizDataModelWire.DeleteResponse response = assertInstanceOf(
         WizDataModelWire.DeleteResponse.class,
         items.delete(new WizDataModelWire.DeleteRequest(support.ds, List.of(
            new WizDataModelWire.DeleteItem("vpm", "A", null, false, null))), support.bob));

      assertFalse(response.results().get(0).ok());
      assertNotNull(support.fresh().getVirtualPrivateModel("A"));
   }

   @Test
   void dependenciesOfAVpmWithoutWriteAreNotListedAndNotDeletable() throws Exception {
      support.writeAllowed = false;
      // READ on the source still holds (the route's own gate); only WRITE is missing
      when(support.dataSourceService.checkPermission(
         eq(support.ds), eq(inetsoft.sree.security.ResourceAction.READ),
         any(java.security.Principal.class))).thenReturn(true);
      WizDataModelWire.DependenciesResponse response = assertInstanceOf(
         WizDataModelWire.DependenciesResponse.class,
         items.dependencies(new WizDataModelWire.DependenciesRequest(support.ds, List.of(
            new WizDataModelWire.Ref("vpm", "A", null))), support.bob));
      WizDataModelWire.DepItem item = response.items().get(0);
      assertTrue(item.exists());
      assertFalse(item.deletable());
      assertTrue(item.dependents().isEmpty());
   }

   @Test
   void aCallerWithoutWriteStillFindsTheVpmButMayNotChangeIt() throws Exception {
      Ctx ctx = new Ctx(support.ds, support.fresh(), support.bob);
      ModelRef ref = new ModelRef(Kind.VPM, "A", null);

      Optional<ModelInfo> info = ops.find(ctx, ref);
      assertTrue(info.isPresent());
      assertEquals("stored description", info.get().description());
      assertNull(info.get().folder());
      assertFalse(info.get().extended());
      assertTrue(ops.mayDelete(ctx, ref));
      assertTrue(ops.mayRename(ctx, ref));
      assertEquals(Optional.empty(), ops.find(ctx, new ModelRef(Kind.VPM, "none", null)));

      support.writeAllowed = false;
      assertTrue(ops.find(ctx, ref).isPresent());
      assertFalse(ops.mayDelete(ctx, ref));
      assertFalse(ops.mayRename(ctx, ref));
   }

   @Test
   void dependentsAreListedWithoutFailing() throws Exception {
      Ctx ctx = new Ctx(support.ds, support.fresh(), support.bob);
      assertNotNull(ops.dependents(ctx, new ModelRef(Kind.VPM, "A", null)));
      assertEquals(Kind.VPM, ops.kind());
   }
}
