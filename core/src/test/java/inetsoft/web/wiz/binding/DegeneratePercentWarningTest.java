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
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.web.binding.model.graph.calc.PercentCalcInfo;
import inetsoft.web.binding.model.graph.calc.RunningTotalCalcInfo;
import inetsoft.web.binding.model.table.CrosstabBindingModel;
import inetsoft.web.binding.model.table.CrosstabOptionInfo;
import inetsoft.web.binding.model.table.TableBindingModel;
import inetsoft.web.wiz.binding.model.FieldRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
      agg.setFormula("Sum");
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

   private static FieldRef clearingCalc(String column, String aggregate) {
      try {
         return new ObjectMapper().readValue(
            "{\"column\":\"" + column + "\",\"type\":\"measure\",\"aggregate\":\"" +
            aggregate + "\",\"calculateInfo\":null}", FieldRef.class);
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }
   }

   /** A rows-only crosstab whose single aggregate is a stored PERCENT with the legacy value. */
   private static CrosstabBindingModel storedPercent(String percentage) {
      BAggregateRefModel agg = percentByCalc(percentage.equals("1") ? 2 : 1);
      agg.setPercentage(percentage);
      agg.setFormula("Sum");
      return crosstab(null, true, false, agg);
   }

   /** Bug #79453: clearing the calculator must not leave the stale percent warning behind. */
   @ParameterizedTest
   @ValueSource(strings = { "1", "2" })
   void clearingAPercentCalculatorLeavesNoPercentWarning(String percentage) {
      CrosstabBindingModel model = storedPercent(percentage);

      TableBindingMutator.setShelf(model, "aggregates", List.of(clearingCalc("Total", "Sum")));

      assertNull(model.getAggregates().get(0).getCalculateInfo());
      assertEquals("0", model.getAggregates().get(0).getPercentage());
      assertNull(TableBindingMutator.degeneratePercentWarning(model));
   }

   @ParameterizedTest
   @ValueSource(strings = { "1", "2" })
   void replacingPercentWithARunningTotalLeavesNoPercentWarning(String percentage) {
      CrosstabBindingModel model = storedPercent(percentage);

      TableBindingMutator.setShelf(model, "aggregates", List.of(
         new FieldRef("Total", "measure", "Sum", null, null, null, null, null,
                      new RunningTotalCalcInfo())));

      assertTrue(model.getAggregates().get(0).getCalculateInfo() instanceof RunningTotalCalcInfo);
      assertEquals("0", model.getAggregates().get(0).getPercentage());
      assertNull(TableBindingMutator.degeneratePercentWarning(model));
   }

   @Test
   void keepingAnOmittedPercentCalculatorStillWarnsAndKeepsPercentage() {
      CrosstabBindingModel model = storedPercent("2");

      TableBindingMutator.setShelf(model, "aggregates",
         List.of(new FieldRef("Total", "measure", "Sum", null, null)));

      assertTrue(model.getAggregates().get(0).getCalculateInfo() instanceof PercentCalcInfo);
      assertEquals("2", model.getAggregates().get(0).getPercentage());
      assertNotNull(TableBindingMutator.degeneratePercentWarning(model));
      TableBindingMutator.drainNotes();
   }

   @Test
   void aNewPercentFieldStillWarns() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      model.setRows(List.of(new BDimensionRefModel()));

      TableBindingMutator.setShelf(model, "aggregates", List.of(
         new FieldRef("Total", "measure", "Sum", null, null, null, null, null,
                      new PercentCalcInfo())));

      assertNotNull(TableBindingMutator.degeneratePercentWarning(model));
   }
}
