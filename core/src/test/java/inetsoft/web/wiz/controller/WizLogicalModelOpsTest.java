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
package inetsoft.web.wiz.controller;

import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.erm.XLogicalModel;
import inetsoft.web.wiz.controller.WizLogicalModelWire.Definition;
import inetsoft.web.wiz.controller.WizModelKindOps.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link WizLogicalModelOps}, the logical model side of the item controller's SPI, against the
 * real registry and service: the permission each question asks, the dependents, the rename's
 * description, and the delete that takes extended children with the base.
 */
class WizLogicalModelOpsTest extends WizLogicalModelTestBase {
   private WizLogicalModelOps ops;
   private Ctx ctx;

   @BeforeEach
   void setUpOps() throws Exception {
      ops = new WizLogicalModelOps(modelService);
      ctx = new Ctx(ds, dataModel, bob);
      Definition base = withDescription("the base");
      create(base);
   }

   private Definition withDescription(String description) {
      Definition d = def("base", entity("Sales", col("Amount", "T1", "AMOUNT")));
      return new Definition(d.name(), description, d.view(), "F", d.connection(), d.parent(),
                            d.extended(), d.entities());
   }

   private static ModelRef ref(String name) {
      return new ModelRef(WizModelKindOps.Kind.LOGICAL_MODEL, name, null);
   }

   @Test
   void kindIsLogicalModel() {
      assertEquals(WizModelKindOps.Kind.LOGICAL_MODEL, ops.kind());
   }

   @Test
   void findAnswersWithTheStoredFolderAndDescription() throws Exception {
      Optional<ModelInfo> info = ops.find(ctx, ref("base"));

      assertTrue(info.isPresent());
      assertEquals("F", info.get().folder());
      assertEquals("the base", info.get().description());
      assertFalse(info.get().extended());
      assertTrue(ops.find(ctx, ref("missing")).isEmpty());
   }

   @Test
   void aModelTheCallerCannotReadIsNotFound() throws Exception {
      doThrow(new inetsoft.sree.security.SecurityException("denied")).when(assetRepository)
         .checkAssetPermission(any(), any(AssetEntry.class), eq(ResourceAction.READ));

      assertTrue(ops.find(ctx, ref("base")).isEmpty());
   }

   @Test
   void deleteAndRenameNeedDeleteOnTheModelNotWrite() throws Exception {
      doThrow(new inetsoft.sree.security.SecurityException("denied")).when(assetRepository)
         .checkAssetPermission(any(), any(AssetEntry.class), eq(ResourceAction.DELETE));

      assertFalse(ops.mayDelete(ctx, ref("base")));
      assertFalse(ops.mayRename(ctx, ref("base")));

      reset(assetRepository);
      assertTrue(ops.mayDelete(ctx, ref("base")));
      assertTrue(ops.mayRename(ctx, ref("base")));
      assertFalse(ops.mayDelete(ctx, ref("missing")));
   }

   @Test
   void dependentsListsTheRecordedSheets() throws Exception {
      seedOuter("base", null, null,
                new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.LOGIC_MODEL,
                               ds + "/someOtherModel", null));

      List<DependentRef> dependents = ops.dependents(ctx, ref("base"));

      assertEquals(1, dependents.size());
      assertEquals("other", dependents.get(0).type());
   }

   @Test
   void sheetsAreNamedByTheirKindAndPath() {
      WizDataModelWire.Dependent ws = WizLogicalModelOps.dependent(new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "folder/ws1", null));
      WizDataModelWire.Dependent vs = WizLogicalModelOps.dependent(new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "vs1", null));

      assertEquals("worksheet", ws.type());
      assertEquals("folder/ws1", ws.path());
      assertEquals("viewsheet", vs.type());
      assertEquals("logicalModel", WizLogicalModelOps.dependent(
         new inetsoft.uql.erm.XLogicalModel("m")).type());
   }

   @Test
   void renameKeepsTheStoredDescriptionWhenTheCallerPassesIt() throws Exception {
      OpResult result = ops.rename(ctx, ref("base"), "renamed", "the base");

      assertTrue(result.ok(), result.message());
      assertEquals("renamed", result.affected().get(0).name());
      assertNull(fresh().getLogicalModel("base"));
      assertEquals("the base", fresh().getLogicalModel("renamed").getDescription());
   }

   @Test
   void renameWithNullDescriptionKeepsTheStoredOneAndANewOneReplacesIt() throws Exception {
      // finding (k): for a logical model the service already keeps the description when given
      // null (XDataModel.renameLogicalModel); the bridge still passes the resolved stored one, so
      // the guarantee does not rest on that detail
      ops.rename(ctx, ref("base"), "renamed", null);
      assertEquals("the base", fresh().getLogicalModel("renamed").getDescription());

      ops.rename(ctx, ref("renamed"), "renamed2", "a new description");
      assertEquals("a new description", fresh().getLogicalModel("renamed2").getDescription());
   }

   @Test
   void deleteListsTheExtendedChildrenThatGoWithTheBase() throws Exception {
      fresh().getLogicalModel("base").addLogicalModel(new XLogicalModel("child"), true);

      OpResult result = ops.delete(ctx, ref("base"), false);

      assertTrue(result.ok(), result.message());
      assertEquals(List.of("base", "child"),
                   result.affected().stream().map(ModelRef::name).toList());
      assertEquals("base", result.affected().get(1).parent());
      assertNull(fresh().getLogicalModel("base"));
   }

   @Test
   void anExtendedModelIsNeitherRenamedNorDeleted() throws Exception {
      fresh().getLogicalModel("base").addLogicalModel(new XLogicalModel("child"), true);
      ModelRef child = new ModelRef(WizModelKindOps.Kind.LOGICAL_MODEL, "child", "base");

      assertEquals(WizDataModelWire.EXTENDED_UNSUPPORTED,
                   ops.rename(ctx, child, "x", null).reason());
      assertEquals(WizDataModelWire.EXTENDED_UNSUPPORTED, ops.delete(ctx, child, false).reason());
      assertNotNull(fresh().getLogicalModel("base").getLogicalModel("child"));
      assertTrue(ops.find(ctx, child).orElseThrow().extended());
   }

   @Test
   void aMissingModelIsNotFoundNotAnException() throws Exception {
      assertEquals(WizDataModelWire.NOT_FOUND, ops.delete(ctx, ref("none"), false).reason());
      assertEquals(WizDataModelWire.NOT_FOUND,
                   ops.rename(ctx, ref("none"), "x", null).reason());
   }
}
