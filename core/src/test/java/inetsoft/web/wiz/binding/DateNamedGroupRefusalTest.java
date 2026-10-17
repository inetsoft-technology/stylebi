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

import inetsoft.uql.util.XNamedGroupInfo;
import inetsoft.web.binding.model.BDimensionRefModel;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.model.NamedGroupInfoModel;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.model.table.CrosstabBindingModel;
import inetsoft.web.wiz.binding.model.FieldRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78751 (p1): the render path always groups a date-typed column by its date range and never
 * applies a named group to it, so a {@code namedGroup}/{@code namedGroupValues} written on one was
 * stored, read back and silently ignored. The wiz write paths refuse it instead.
 */
@Tag("core")
class DateNamedGroupRefusalTest {
   private static final FieldRef.NamedGroupValues EARLY = new FieldRef.NamedGroupValues(
      List.of(new FieldRef.NamedGroupValues.Clause("Early", List.of("2023", "2024"))), null);

   private static FieldRef inline(String column, String level) {
      return new FieldRef(column, "dimension", null, level, null, null, null, EARLY);
   }

   private static FieldRef byName(String column, String level) {
      return new FieldRef(column, "dimension", null, level, "Early");
   }

   private static FieldRef plain(String column, String level) {
      return new FieldRef(column, "dimension", null, level, null);
   }

   private static List<BindingModel.SourceTable> tables(String column, String dataType) {
      BindingModel.SourceTable table = new BindingModel.SourceTable();
      table.setColumns(List.of(new BindingModel.SourceTableColumn(column, dataType)));
      return List.of(table);
   }

   private static ChartBindingModel chart(String dataType) {
      ChartBindingModel model = new ChartBindingModel();
      model.setTables(tables("Order Date", dataType));
      return model;
   }

   private static CrosstabBindingModel crosstab(String dataType) {
      CrosstabBindingModel model = new CrosstabBindingModel();
      model.setTables(tables("Order Date", dataType));
      return model;
   }

   private static void assertRefusal(IllegalArgumentException e, String key) {
      assertTrue(e.getMessage().contains(key), e.getMessage());
      assertTrue(e.getMessage().contains("Order Date"), e.getMessage());
      assertTrue(e.getMessage().contains("dateLevel"), "the remedy must be named: " +
         e.getMessage());
   }

   @Test
   void chartShelvesRefuseAnInlineGroupOnADateColumnAtEveryLevelAndWriteNothing() {
      for(String type : List.of("date", "timeInstant", "time")) {
         for(String shelf : List.of("x", "y", "group")) {
            for(String level : new String[]{ "year", "month", "none", null }) {
               ChartBindingModel model = chart(type);
               IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                  () -> ChartBindingMutator.setShelf(model, shelf, List.of(inline("Order Date", level))),
                  type + " " + shelf + " " + level);
               assertRefusal(e, "namedGroupValues");
               assertTrue(model.getXFields().isEmpty() && model.getYFields().isEmpty() &&
                          model.getGroupFields().isEmpty(), "nothing may be written");
            }
         }
      }
   }

   @Test
   void chartShelfRefusesAByNameGroupOnADateColumn() {
      ChartBindingModel model = chart("date");
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> ChartBindingMutator.setShelf(model, "x", List.of(byName("Order Date", "year"))));
      assertRefusal(e, "namedGroup");
      assertTrue(model.getXFields().isEmpty());
   }

   @Test
   void chartSingleShelfAndAestheticChannelsRefuseTheGroupToo() {
      ChartBindingModel single = chart("date");
      assertRefusal(assertThrows(IllegalArgumentException.class,
         () -> ChartBindingMutator.setSingleShelf(single, "path", inline("Order Date", "year"))),
         "namedGroupValues");

      for(String channel : List.of("color", "shape", "size", "text")) {
         ChartBindingModel model = chart("date");
         assertRefusal(assertThrows(IllegalArgumentException.class,
            () -> ChartAestheticMutator.setField(model, channel, inline("Order Date", "month")),
            channel), "namedGroupValues");
      }
   }

   @Test
   void crosstabRowsAndColsRefuseBothGroupFormsOnADateColumn() {
      for(String shelf : List.of("rows", "cols")) {
         for(String level : new String[]{ "year", "month", "none" }) {
            CrosstabBindingModel model = crosstab("date");
            assertRefusal(assertThrows(IllegalArgumentException.class,
               () -> TableBindingMutator.setShelf(model, shelf, List.of(inline("Order Date", level))),
               shelf + level), "namedGroupValues");
            assertRefusal(assertThrows(IllegalArgumentException.class,
               () -> TableBindingMutator.setShelf(model, shelf, List.of(byName("Order Date", level))),
               shelf + level), "namedGroup");
            assertTrue((shelf.equals("rows") ? model.getRows() : model.getCols()).isEmpty());
         }
      }
   }

   @Test
   void aStoredLegacyGroupEchoedBackByPlaceholderNameIsNotRefusedOnACrosstab() {
      CrosstabBindingModel model = crosstab("date");
      BDimensionRefModel stored = new BDimensionRefModel();
      stored.setColumnValue("Order Date");
      stored.setName("Order Date");
      NamedGroupInfoModel group = new NamedGroupInfoModel();
      group.setType(XNamedGroupInfo.EXPERT_NAMEDGROUP_INFO);
      group.setName("Custom");
      stored.setNamedGroupInfo(group);
      model.setRows(new ArrayList<>(List.of(stored)));

      assertDoesNotThrow(() -> TableBindingMutator.setShelf(model, "rows",
         List.of(new FieldRef("Order Date", "dimension", null, null, "Custom"))));
   }

   @Test
   void legitimateWritesAreUnchanged() {
      // a named group on a string / number column
      for(String type : List.of("string", "integer", "double")) {
         ChartBindingModel model = chart(type);
         ChartBindingMutator.setShelf(model, "x", List.of(inline("Order Date", null)));
         assertEquals(1, model.getXFields().size(), type);

         CrosstabBindingModel xtab = crosstab(type);
         TableBindingMutator.setShelf(xtab, "rows", List.of(inline("Order Date", null)));
         assertEquals(1, xtab.getRows().size(), type);
      }

      // a date column with no named group, at any level
      for(String level : new String[]{ "year", "month", "none", null }) {
         ChartBindingModel model = chart("date");
         ChartBindingMutator.setShelf(model, "x", List.of(plain("Order Date", level)));
         assertEquals(1, model.getXFields().size());

         CrosstabBindingModel xtab = crosstab("date");
         TableBindingMutator.setShelf(xtab, "cols", List.of(plain("Order Date", level)));
         assertEquals(1, xtab.getCols().size());
      }
   }

   @Test
   void aColumnOfUnknownTypeIsNotRefused() {
      assertDoesNotThrow(() ->
         ChartBindingMutator.setShelf(new ChartBindingModel(), "x", List.of(inline("Order Date", "year"))));
      assertDoesNotThrow(() ->
         TableBindingMutator.setShelf(new CrosstabBindingModel(), "rows", List.of(inline("Order Date", "year"))));
   }
}
