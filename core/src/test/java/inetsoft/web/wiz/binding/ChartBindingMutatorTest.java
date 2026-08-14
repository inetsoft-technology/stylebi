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
package inetsoft.web.wiz.binding;

import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.model.graph.ChartAggregateRefModel;
import inetsoft.web.binding.model.graph.ChartDimensionRefModel;
import inetsoft.web.wiz.binding.model.FieldRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class ChartBindingMutatorTest {
   @Test
   void setsTheXShelfFromFieldRefs() {
      ChartBindingModel model = new ChartBindingModel();

      ChartBindingMutator.setShelf(model, "x",
                                   List.of(new FieldRef("Region", "dimension", null, null, null)));

      assertEquals(1, model.getXFields().size());
      assertInstanceOf(ChartDimensionRefModel.class, model.getXFields().get(0));
   }

   @Test
   void setsAMeasureOnTheYShelfCarryingItsAggregate() {
      ChartBindingModel model = new ChartBindingModel();

      ChartBindingMutator.setShelf(model, "y",
                                   List.of(new FieldRef("Sales", "measure", "Sum", null, null)));

      assertEquals(1, model.getYFields().size());
      assertInstanceOf(ChartAggregateRefModel.class, model.getYFields().get(0));
   }

   @Test
   void leavesEveryAestheticFieldUntouched() {
      ChartBindingModel model = new ChartBindingModel();
      Map<String, Object> before = ChartBindingFields.snapshotAesthetics(model);

      ChartBindingMutator.setShelf(model, "x",
                                   List.of(new FieldRef("Region", "dimension", null, null, null)));

      assertEquals(before, ChartBindingFields.snapshotAesthetics(model),
                   "a shelf write must not disturb the aesthetic fields spec 2c owns");
   }

   @Test
   void rejectsAnUnknownShelfNamingTheValidOnes() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> ChartBindingMutator.setShelf(new ChartBindingModel(), "z", List.of()));
      assertTrue(thrown.getMessage().contains("z"));
      assertTrue(thrown.getMessage().contains("x"));
   }

   @Test
   void rejectsAFieldWithoutATypeNamingTheField() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> ChartBindingMutator.setShelf(
            new ChartBindingModel(), "x",
            List.of(new FieldRef("Region", null, null, null, null))));
      assertTrue(thrown.getMessage().contains("Region"));
   }

   @Test
   void clearsAShelfWhenGivenNoFields() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "x",
                                   List.of(new FieldRef("Region", "dimension", null, null, null)));

      ChartBindingMutator.setShelf(model, "x", List.of());

      assertTrue(model.getXFields().isEmpty());
   }

   @Test
   void theDeclaredAestheticSplitCoversThirteenFields() {
      assertEquals(13, ChartBindingFields.AESTHETIC.size(),
                   "the 2b/2c split is declared once; changing it changes both sides");
   }
}
