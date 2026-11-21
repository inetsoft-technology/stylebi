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

import inetsoft.uql.asset.sync.RenameDependencyInfo;
import inetsoft.uql.erm.*;
import inetsoft.uql.erm.vpm.VirtualPrivateModel;
import inetsoft.uql.erm.vpm.VpmCondition;
import inetsoft.web.wiz.controller.WizModelKindOps.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

/**
 * The stateless physical view operations (rename, delete) on views that the session created:
 * dependents migrate with a rename, and a view that a logical model or VPM uses is never deleted
 * without force.
 */
class WizPhysicalViewOpsTest extends WizPhysicalViewTestBase {
   private WizPhysicalViewOps ops() {
      return new WizPhysicalViewOps(dataSourceService, manager);
   }

   private Ctx ctx() {
      return new Ctx(ds, fresh(), bob);
   }

   private static ModelRef view(String name) {
      return new ModelRef(Kind.PHYSICAL_VIEW, name, null);
   }

   private void logicalModelOn(String name, String viewName) {
      XLogicalModel model = new XLogicalModel(name);
      model.setPartition(viewName);
      XEntity entity = new XEntity("E");
      entity.addAttribute(new XAttribute("a", "T1", "ID"));
      model.addEntity(entity);
      dataModel.addLogicalModel(model);
   }

   @Test
   void renameMigratesDependents() throws Exception {
      ok(createViewRaw(definition("rv1", tables(plain("T1"))), null));
      logicalModelOn("lm", "rv1");
      createViewDescription("rv1", "kept description");

      int puts = runtimePuts.get();
      OpResult result = ops().rename(ctx(), view("rv1"), "rv2", "kept description");

      assertTrue(result.ok(), String.valueOf(result));
      assertNull(fresh().getPartition("rv1"));
      assertNotNull(fresh().getPartition("rv2"));
      assertEquals("rv2", fresh().getLogicalModel("lm").getPartition(),
                   "the logical model follows the view");
      assertEquals("kept description", fresh().getPartition("rv2").getDescription());
      verify(transformHandler).addTransformTask(any(RenameDependencyInfo.class));
      assertTrue(runtimes.isEmpty(), "a stateless operation opens no runtime");
      assertEquals(puts, runtimePuts.get());
   }

   @Test
   void deleteRefusedWhileUsedByLogicalModelOrVpm() throws Exception {
      ok(createViewRaw(definition("dv", tables(plain("T1"))), null));
      logicalModelOn("lm", "dv");

      OpResult byModel = ops().delete(ctx(), view("dv"), false);

      assertFalse(byModel.ok());
      assertEquals(WizDataModelWire.VIEW_IN_USE, byModel.reason());
      assertNotNull(fresh().getPartition("dv"), "the view is still there");

      dataModel.removeLogicalModel("lm");
      VirtualPrivateModel vpm = new VirtualPrivateModel("vp");
      VpmCondition condition = new VpmCondition("c");
      condition.setType(VpmCondition.PHYSICMODEL);
      condition.setTable("dv");
      vpm.addCondition(condition);
      dataModel.addVirtualPrivateModel(vpm, true);

      OpResult byVpm = ops().delete(ctx(), view("dv"), false);

      assertFalse(byVpm.ok());
      assertEquals(WizDataModelWire.VIEW_IN_USE, byVpm.reason());
      assertNotNull(fresh().getPartition("dv"));

      dataModel.removeVirtualPrivateModel("vp");
      OpResult free = ops().delete(ctx(), view("dv"), false);

      assertTrue(free.ok(), String.valueOf(free));
      assertNull(fresh().getPartition("dv"));
   }

   @Test
   void dependentsOfAViewNameTheLogicalModelsOnIt() throws Exception {
      ok(createViewRaw(definition("dv", tables(plain("T1"))), null));
      logicalModelOn("lm", "dv");

      List<DependentRef> dependents = ops().dependents(ctx(), view("dv"));

      assertTrue(dependents.stream().anyMatch(d -> "lm".equals(d.name())),
                 dependents.toString());
   }

   private void createViewDescription(String name, String description) throws Exception {
      Object result = updateView(name, new java.util.LinkedHashMap<>(
         java.util.Map.of("description", description)), null);
      ok(result);
      assertEquals(description, fresh().getPartition(name).getDescription());
   }
}
