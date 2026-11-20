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

import inetsoft.uql.erm.ExpressionAttribute;
import inetsoft.uql.erm.XAttribute;
import inetsoft.web.portal.model.database.LogicalModelDefinition;
import inetsoft.web.portal.model.database.XAttributeModel;
import inetsoft.web.wiz.controller.WizLogicalModelWire.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins premises of {@code LogicalModelService} that decided the bridge's design (finding g of
 * design section 6), against the unchanged service.
 */
class WizLogicalModelServicePremiseTest extends WizLogicalModelTestBase {
   @Test
   void missingTypeBecomesExpressionAttributeWithNullExpression() throws Exception {
      Definition wire = def("typeless", entity("E", col("A", "T1", "ID")));
      LogicalModelDefinition dto = new WizLogicalModelMapper().toDto(wire, null, "pv");
      XAttributeModel attribute = dto.getEntities().get(0).getAttributes().get(0);
      assertEquals("column", attribute.getType(), "control: the mapper sets the type");

      // what the Portal path would send for a DTO whose type was lost
      attribute.setType(null);
      modelService.createModel(ds, null, "pv", dto, null, bob);

      XAttribute stored = fresh().getLogicalModel("typeless").getEntity("E").getAttribute("A");
      assertInstanceOf(ExpressionAttribute.class, stored,
                       "premise: any type but exactly \"column\" builds an expression attribute");
      String expression = ((ExpressionAttribute) stored).getExpression();
      assertTrue(expression == null || expression.isEmpty(),
                 "premise: ... with no expression, silently");
   }

   @Test
   void theMappedModelStoresAColumnAttribute() throws Exception {
      create(def("typed", entity("E", col("A", "T1", "ID"))));

      XAttribute stored = fresh().getLogicalModel("typed").getEntity("E").getAttribute("A");

      assertFalse(stored instanceof ExpressionAttribute);
      assertEquals("T1", stored.getTable());
      assertEquals("ID", stored.getColumn());
   }

   @Test
   void serviceDropsASecondEntityOfTheSameNameSoTheReadBackCatchesAnyThatSlipsThrough()
      throws Exception
   {
      // the validator refuses duplicates; this shows the read-back is the second line of defense
      Definition dup = def("dups", entity("E", col("A", "T1", "ID")),
                           entity("E", col("B", "T1", "NAME")));
      LogicalModelDefinition dto = new WizLogicalModelMapper().toDto(dup, null, "pv");
      modelService.createModel(ds, null, "pv", dto, null, bob);

      Definition stored = new WizLogicalModelMapper().toWire(
         modelService.getModel(ds, "pv", "dups", null, bob), false);

      assertEquals(1, stored.entities().size(), "premise: first wins, the second is dropped");
      assertNotNull(WizLogicalModelReadBack.diff(dup, stored));
   }
}
