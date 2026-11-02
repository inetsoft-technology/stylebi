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

import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.SNamedGroupInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.graph.DefaultVSChartInfo;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.uql.asset.NamedRangeRef;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.model.graph.AestheticInfo;
import inetsoft.web.binding.model.graph.ChartDimensionRefModel;
import inetsoft.web.binding.model.graph.ChartRefModel;
import inetsoft.web.wiz.binding.model.FieldRef;
import inetsoft.web.wiz.binding.model.FieldRef.NamedGroupValues;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80062 item 2: an aesthetic named group that differs from the group of a shelf dimension on
 * the same column is refused (the shelf's group would silently replace it). The shelf and colour
 * models are built from real chart dimension refs through the model constructor the live read
 * path uses, because that is where the live shape (full name, empty group model) comes from.
 */
@WizAgentTestSupport
@Tag("core")
class AestheticNamedGroupShelfConflictTest {
   private static VSChartDimensionRef ref(String column, SNamedGroupInfo group, int groupType) {
      AttributeRef attribute = new AttributeRef(null, column);
      attribute.setDataType(XSchema.STRING);
      VSChartDimensionRef dim = new VSChartDimensionRef();
      dim.setDataRef(new ColumnRef(attribute));
      dim.setNamedGroupInfo(group);
      dim.setGroupType("" + groupType);
      return dim;
   }

   private static SNamedGroupInfo group(String name, String... members) {
      SNamedGroupInfo info = new SNamedGroupInfo();
      info.setGroupValue(name, new ArrayList<>(List.of((Object[]) members)));
      return info;
   }

   private static VSChartInfo barInfo() {
      VSChartInfo info = new DefaultVSChartInfo();
      info.setChartType(GraphTypes.CHART_BAR);
      return info;
   }

   private static ChartRefModel shelf(String column, SNamedGroupInfo group) {
      return new ChartDimensionRefModel(
         ref(column, group, NamedRangeRef.DATA_GROUP), barInfo());
   }

   private static ChartBindingModel chart(SNamedGroupInfo color, ChartRefModel... shelves) {
      ChartBindingModel model = new ChartBindingModel();
      model.setXFields(new ArrayList<>(List.of(shelves)));
      AestheticInfo aesthetic = new AestheticInfo();
      aesthetic.setFullName("Category");
      aesthetic.setDataInfo(new ChartDimensionRefModel(
         ref("Category", color, NamedRangeRef.COLOR_GROUP), barInfo()));
      model.setColorField(aesthetic);
      return model;
   }

   private static FieldRef sent() {
      NamedGroupValues values = new NamedGroupValues(
         List.of(new NamedGroupValues.Clause("Creative", List.of("Games", "Graphics"))), null);
      return new FieldRef("Category", "dimension", null, null, null, null, null, values, null,
                          null, null, null, null, null);
   }

   @Test
   void liveShelfModelIsNotKeyedByTheColumnName() {
      // The shape that masked the first version of the check: a grouped shelf dimension does not
      // report the bare column as its full name, and an ungrouped one carries an empty group
      // model rather than null.
      ChartDimensionRefModel grouped =
         (ChartDimensionRefModel) shelf("Category", group("Fun", "a"));
      assertNotEquals("Category", grouped.getFullName());
      assertNotNull(((ChartDimensionRefModel) shelf("Category", null)).getNamedGroupInfo());
   }

   @Test
   void refusesADifferentShelfGroupNamingBoth() {
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> ChartBindingService.requireAestheticNamedGroupAgreesWithShelf(
            chart(group("Creative", "Games", "Graphics"),
                  shelf("Category", group("Fun", "Games", "Educational"))),
            "color", sent()));
      assertTrue(e.getMessage().contains("Creative") && e.getMessage().contains("Fun") &&
                 e.getMessage().contains("set_chart_shelf"), e.getMessage());
   }

   @Test
   void refusesTheSameMembersUnderADifferentGroupName() {
      assertThrows(IllegalArgumentException.class,
         () -> ChartBindingService.requireAestheticNamedGroupAgreesWithShelf(
            chart(group("Creative", "Games", "Graphics"),
                  shelf("Category", group("Fun", "Games", "Graphics"))),
            "color", sent()));
   }

   @Test
   void acceptsTheIdenticalGroupRegardlessOfMemberOrder() {
      assertDoesNotThrow(() -> ChartBindingService.requireAestheticNamedGroupAgreesWithShelf(
         chart(group("Creative", "Games", "Graphics"),
               shelf("Category", group("Creative", "Graphics", "Games"))),
         "color", sent()));
   }

   @Test
   void anUngroupedShelfDimensionIsNotAConflict() {
      assertDoesNotThrow(() -> ChartBindingService.requireAestheticNamedGroupAgreesWithShelf(
         chart(group("Creative", "Games", "Graphics"), shelf("Category", null)),
         "color", sent()));
   }

   @Test
   void onlyTheShelfDimensionOfTheSameColumnCounts() {
      assertDoesNotThrow(() -> ChartBindingService.requireAestheticNamedGroupAgreesWithShelf(
         chart(group("Creative", "Games", "Graphics"),
               shelf("Region", group("Fun", "x")), shelf("Category", null)),
         "color", sent()));

      assertThrows(IllegalArgumentException.class,
         () -> ChartBindingService.requireAestheticNamedGroupAgreesWithShelf(
            chart(group("Creative", "Games", "Graphics"),
                  shelf("Region", group("Creative", "Games", "Graphics")),
                  shelf("Category", group("Fun", "Games"))),
            "color", sent()));
   }

   @Test
   void ignoresAFieldThatSentNoGroup() {
      assertDoesNotThrow(() -> ChartBindingService.requireAestheticNamedGroupAgreesWithShelf(
         chart(group("Fun", "Games"), shelf("Category", group("Fun", "Games"))), "color",
         new FieldRef("Category", "dimension", null, null, null)));
   }
}
