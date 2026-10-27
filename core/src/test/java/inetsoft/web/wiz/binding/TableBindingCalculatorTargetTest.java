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
import inetsoft.uql.schema.XSchema;
import inetsoft.web.binding.model.BAggregateRefModel;
import inetsoft.web.binding.model.BDimensionRefModel;
import inetsoft.web.binding.model.graph.calc.ChangeCalcInfo;
import inetsoft.web.binding.model.graph.calc.ValueOfCalcInfo;
import inetsoft.web.binding.model.table.CrosstabBindingModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79450: a CHANGE / VALUE-OF calculator whose target dimension the crosstab cannot compute
 * is refused by name instead of failing in the engine with "Error loading data".
 */
@Tag("core")
class TableBindingCalculatorTargetTest {
   private static final int YEAR = XConstants.YEAR_DATE_GROUP;
   private static final int MONTH = XConstants.MONTH_DATE_GROUP;
   private static final int MOY = XConstants.MONTH_OF_YEAR_DATE_GROUP;

   private static BDimensionRefModel date(String name, int level) {
      BDimensionRefModel dim = new BDimensionRefModel();
      dim.setColumnValue(name);
      dim.setFullName(name);
      dim.setDataType(XSchema.DATE);
      dim.setDateLevel(String.valueOf(level));
      return dim;
   }

   private static BDimensionRefModel text(String name) {
      BDimensionRefModel dim = new BDimensionRefModel();
      dim.setColumnValue(name);
      dim.setFullName(name);
      dim.setDataType(XSchema.STRING);
      return dim;
   }

   private static CrosstabBindingModel crosstab(List<BDimensionRefModel> rows,
                                                List<BDimensionRefModel> cols, int from,
                                                String columnName, boolean change)
   {
      ValueOfCalcInfo calc = change ? new ChangeCalcInfo() : new ValueOfCalcInfo();
      calc.setFrom(from);
      calc.setColumnName(columnName);
      BAggregateRefModel agg = new BAggregateRefModel();
      agg.setColumnValue("Total");
      agg.setFullName("Sum(Total)");
      agg.setCalculateInfo(calc);
      CrosstabBindingModel model = new CrosstabBindingModel();
      model.setRows(rows);
      model.setCols(cols);
      model.setAggregates(List.of(agg));
      return model;
   }

   private static void refused(CrosstabBindingModel model, String... fragments) {
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> TableBindingMutator.refuseUnsupportedCalculatorTarget(model));

      for(String fragment : fragments) {
         assertTrue(e.getMessage().contains(fragment), e.getMessage());
      }
   }

   private static void allowed(CrosstabBindingModel model) {
      assertDoesNotThrow(() -> TableBindingMutator.refuseUnsupportedCalculatorTarget(model));
   }

   @Test
   void partLevelInnerTargetOnRowsIsRefused() {
      List<BDimensionRefModel> rows = List.of(date("Year", YEAR), date("MOY", MOY));
      refused(crosstab(rows, List.of(), 7, null, true), "Sum(Total)", "from 7", "Previous Month",
              "MOY", "month_of_year");
      refused(crosstab(rows, List.of(), 7, "0", true), "month_of_year");
      refused(crosstab(rows, List.of(), 7, null, false), "month_of_year");
   }

   @Test
   void partLevelTargetOnColsIsRefused() {
      refused(crosstab(List.of(), List.of(date("MOY", MOY)), 5, null, true), "month_of_year");
      refused(crosstab(List.of(text("Region")), List.of(date("MOY", MOY)), 4, "1", true),
              "month_of_year");
   }

   @Test
   void nonDateTargetIsRefused() {
      refused(crosstab(List.of(text("Region")), List.of(), 6, null, true), "not a date");
   }

   @Test
   void yearThenMonthFrom4WithYearTargetOnRowsIsRefused() {
      refused(crosstab(List.of(date("Year", YEAR), date("Month", MONTH)), List.of(), 4, "Year",
                       true), "from 4", "Previous Year", "Year");
   }

   @Test
   void yearTargetFrom7OnRowsIsRefused() {
      refused(crosstab(List.of(date("Year", YEAR), date("MOY", MOY)), List.of(), 7, "Year", true),
              "from 7", "year");
   }

   @Test
   void refusalSteersToAFlatMonthLevelNotToFromOne() {
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> TableBindingMutator.refuseUnsupportedCalculatorTarget(crosstab(
            List.of(date("Year", YEAR), date("MOY", MOY)), List.of(), 7, null, true)));

      // names the fix the user can act on; from 1 gives a blank January, so it is not the advice
      assertTrue(e.getMessage().contains("month level"), e.getMessage());
      assertTrue(e.getMessage().contains("previous December"), e.getMessage());
      assertTrue(e.getMessage().contains("remove the calculateInfo"), e.getMessage());
      assertFalse(e.getMessage().contains("from 1"), e.getMessage());
   }

   @Test
   void shapesThatComputeAreAllowed() {
      // flat month level compares January with the previous December
      allowed(crosstab(List.of(date("Month", MONTH)), List.of(), 7, null, true));
      // year then month_of_year, from 4 on the year target computes
      allowed(crosstab(List.of(date("Year", YEAR), date("MOY", MOY)), List.of(), 4, "Year", true));
      // from 1 is not a date shift
      allowed(crosstab(List.of(date("Year", YEAR), date("MOY", MOY)), List.of(), 1, null, true));
      // year then month, inner month target from 7 computes (January blank, warning shape)
      allowed(crosstab(List.of(date("Year", YEAR), date("Month", MONTH)), List.of(), 7, null,
                       true));
   }

   @Test
   void rowsOnlyRulesAreNotClaimedOnColumns() {
      allowed(crosstab(List.of(), List.of(date("Year", YEAR), date("Month", MONTH)), 4, "Year",
                       true));
      allowed(crosstab(List.of(), List.of(date("Year", YEAR), date("MOY", MOY)), 7, "Year", true));
   }

   @Test
   void unclassifiableTargetsAreNeverRefused() {
      List<BDimensionRefModel> rows = List.of(date("Year", YEAR), date("MOY", MOY));
      allowed(crosstab(rows, List.of(), 7, "NoSuchField", true));

      BDimensionRefModel dynamic = date("MOY", MOY);
      dynamic.setDateLevel("$(level)");
      allowed(crosstab(List.of(date("Year", YEAR), dynamic), List.of(), 7, null, true));

      BDimensionRefModel untyped = new BDimensionRefModel();
      untyped.setColumnValue("X");
      allowed(crosstab(List.of(untyped), List.of(), 7, null, true));
   }

   @Test
   void noCalculatorOrNonCrosstabIsAllowed() {
      CrosstabBindingModel model = crosstab(List.of(date("MOY", MOY)), List.of(), 7, null, true);
      model.getAggregates().get(0).setCalculateInfo(null);
      allowed(model);
      assertDoesNotThrow(() -> TableBindingMutator.refuseUnsupportedCalculatorTarget(
         new inetsoft.web.binding.model.table.TableBindingModel()));
   }
}
