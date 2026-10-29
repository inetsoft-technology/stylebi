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

import inetsoft.uql.XConstants;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.SNamedGroupInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.graph.DefaultVSChartInfo;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.uql.viewsheet.graph.VSAestheticRef;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.web.wiz.binding.model.FieldRef;
import inetsoft.web.wiz.binding.model.FieldRef.NamedGroupValues;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Bug #79666: an aesthetic named group overwritten by a plain same-column shelf is refused. */
@WizAgentTestSupport
@Tag("core")
class AestheticNamedGroupReadBackTest {
   private static VSChartDimensionRef category(boolean grouped) {
      AttributeRef attribute = new AttributeRef(null, "Category");
      attribute.setDataType(XSchema.STRING);
      VSChartDimensionRef dim = new VSChartDimensionRef();
      dim.setDataRef(new ColumnRef(attribute));

      if(grouped) {
         dim.setNamedGroupInfo(new SNamedGroupInfo());
      }

      return dim;
   }

   private static VSChartInfo colorOn(VSChartDimensionRef dim) {
      VSChartInfo info = new DefaultVSChartInfo();
      info.setChartType(GraphTypes.CHART_BAR);
      VSAestheticRef color = new VSAestheticRef();
      color.setDataRef(dim);
      info.setColorField(color);
      return info;
   }

   private static FieldRef grouped() {
      NamedGroupValues values = new NamedGroupValues(
         List.of(new NamedGroupValues.Clause("Creative", List.of("Games"))), null);
      return new FieldRef("Category", "dimension", null, null, null, null, null, values, null,
                          null, null, null, null, null);
   }

   @Test
   void refusesWhenTheGroupWasDropped() {
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> ChartBindingService.requireAestheticNamedGroupKept(
            colorOn(category(false)), "color", grouped()));
      assertTrue(e.getMessage().contains("Category"), e.getMessage());
   }

   @Test
   void acceptsWhenTheGroupSurvived() {
      assertDoesNotThrow(() -> ChartBindingService.requireAestheticNamedGroupKept(
         colorOn(category(true)), "color", grouped()));
   }

   @Test
   void ignoresAFieldThatSentNoGroup() {
      assertDoesNotThrow(() -> ChartBindingService.requireAestheticNamedGroupKept(
         colorOn(category(false)), "color",
         new FieldRef("Category", "dimension", null, null, null)));
   }
}
