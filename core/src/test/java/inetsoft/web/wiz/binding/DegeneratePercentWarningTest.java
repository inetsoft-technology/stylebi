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

import inetsoft.web.binding.model.BAggregateRefModel;
import inetsoft.web.binding.model.BDimensionRefModel;
import inetsoft.web.binding.model.graph.calc.PercentCalcInfo;
import inetsoft.web.binding.model.table.CrosstabBindingModel;
import inetsoft.web.binding.model.table.CrosstabOptionInfo;
import inetsoft.web.binding.model.table.TableBindingModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Bug #78909: a PERCENT aggregate on a crosstab whose percentageBy shelf is empty shows 100%. */
@Tag("core")
class DegeneratePercentWarningTest {
   private static BAggregateRefModel percentByCalc(int level) {
      BAggregateRefModel agg = new BAggregateRefModel();
      agg.setColumnValue("Total");
      PercentCalcInfo info = new PercentCalcInfo();
      info.setLevel(level);
      agg.setCalculateInfo(info);
      return agg;
   }

   private static BAggregateRefModel percentByOption(String percentage) {
      BAggregateRefModel agg = new BAggregateRefModel();
      agg.setColumnValue("Total");
      agg.setPercentage(percentage);
      return agg;
   }

   private static CrosstabBindingModel crosstab(String percentageBy, boolean rows, boolean cols,
                                                BAggregateRefModel agg)
   {
      CrosstabBindingModel model = new CrosstabBindingModel();
      CrosstabOptionInfo option = new CrosstabOptionInfo();
      option.setPercentageByValue(percentageBy);
      model.setOption(option);
      model.setRows(rows ? List.of(new BDimensionRefModel()) : List.of());
      model.setCols(cols ? List.of(new BDimensionRefModel()) : List.of());
      model.setAggregates(List.of(agg));
      return model;
   }

   @Test
   void warnsForRowsOnlyWithUnsetOrColPercentageBy() {
      for(String pct : new String[] { null, "", "1" }) {
         String w = TableBindingMutator.degeneratePercentWarning(
            crosstab(pct, true, false, percentByCalc(1)));
         assertNotNull(w, "percentageBy=" + pct);
         assertTrue(w.startsWith(TableBindingMutator.WARNING_PREFIX), w);
         assertTrue(w.contains("percentageBy") && w.contains("cols") && w.contains("100%"), w);
      }
   }

   @Test
   void warnsForTheRowTwinAndForLevelTwo() {
      String row = TableBindingMutator.degeneratePercentWarning(
         crosstab("2", false, true, percentByCalc(1)));
      assertNotNull(row);
      assertTrue(row.contains("rows shelf is empty"), row);
      assertNotNull(TableBindingMutator.degeneratePercentWarning(
         crosstab(null, true, false, percentByCalc(2))));
   }

   @Test
   void detectsPercentFromEitherRepresentation() {
      assertNotNull(TableBindingMutator.degeneratePercentWarning(
         crosstab(null, true, false, percentByCalc(1))));
      assertNotNull(TableBindingMutator.degeneratePercentWarning(
         crosstab(null, true, false, percentByOption("1"))));
      assertNotNull(TableBindingMutator.degeneratePercentWarning(
         crosstab(null, true, false, percentByOption("2"))));
   }

   @Test
   void staysQuietWhenTheShapeIsFine() {
      assertNull(TableBindingMutator.degeneratePercentWarning(
         crosstab("0", true, false, percentByCalc(1))));
      assertNull(TableBindingMutator.degeneratePercentWarning(
         crosstab("$(dir)", true, false, percentByCalc(1))));
      assertNull(TableBindingMutator.degeneratePercentWarning(
         crosstab("=dir", true, false, percentByCalc(1))));
      assertNull(TableBindingMutator.degeneratePercentWarning(
         crosstab(null, true, true, percentByCalc(1))));
      assertNull(TableBindingMutator.degeneratePercentWarning(
         crosstab("2", true, false, percentByCalc(1))));
      assertNull(TableBindingMutator.degeneratePercentWarning(
         crosstab(null, true, false, percentByOption("0"))));
      assertNull(TableBindingMutator.degeneratePercentWarning(
         crosstab(null, true, false, new BAggregateRefModel())));
      assertNull(TableBindingMutator.degeneratePercentWarning(new TableBindingModel()));
   }

   @Test
   void refusalTextsNoLongerClaimZeros() {
      for(String pct : new String[] { "col", "row" }) {
         CrosstabBindingModel model = crosstab(null, false, false, percentByCalc(1));
         IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
            TableBindingMutator.setOptions(model, java.util.Map.of("percentageBy", pct)));
         assertFalse(e.getMessage().contains("zeros"), e.getMessage());
         assertTrue(e.getMessage().contains("100%"), e.getMessage());
      }
   }
}
