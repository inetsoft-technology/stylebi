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

package inetsoft.uql.viewsheet.internal;

import inetsoft.report.composition.graph.calc.RunningTotalCalc;
import inetsoft.report.composition.graph.calc.RunningTotalColumn;
import inetsoft.test.*;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.AggregateRef;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.AbstractCalc;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Bug #76797
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class VSUtilTest {
   // breakBy defaulting from unset to its first-time ROW_INNER sentinel must not wipe an
   // explicitly-set resetLevel.
   @Test
   void resetLevelIsPreservedWhenBreakByDefaultsToRowInner() {
      RunningTotalCalc calc = new RunningTotalCalc();
      calc.setResetLevel(RunningTotalColumn.YEAR);

      VSUtil.updateCalculateInfo(true, false, false, calc, null);

      assertEquals(AbstractCalc.ROW_INNER, calc.getBreakBy());
      assertEquals(RunningTotalColumn.YEAR, calc.getResetLevel());
   }

   // Same defaulting behavior for the column-inner sentinel.
   @Test
   void resetLevelIsPreservedWhenBreakByDefaultsToColumnInner() {
      RunningTotalCalc calc = new RunningTotalCalc();
      calc.setResetLevel(RunningTotalColumn.YEAR);

      VSUtil.updateCalculateInfo(false, true, false, calc, null);

      assertEquals(AbstractCalc.COLUMN_INNER, calc.getBreakBy());
      assertEquals(RunningTotalColumn.YEAR, calc.getResetLevel());
   }

   // breakBy already explicitly set to the sentinel matching the current shelf shape must
   // continue to short-circuit before the reset-clearing check (pre-existing behavior).
   @Test
   void resetLevelIsPreservedWhenBreakByAlreadyMatchesRowInner() {
      RunningTotalCalc calc = new RunningTotalCalc();
      calc.setBreakBy(AbstractCalc.ROW_INNER);
      calc.setResetLevel(RunningTotalColumn.YEAR);

      VSUtil.updateCalculateInfo(true, false, false, calc, null);

      assertEquals(AbstractCalc.ROW_INNER, calc.getBreakBy());
      assertEquals(RunningTotalColumn.YEAR, calc.getResetLevel());
   }

   @Test
   void resetLevelIsPreservedWhenBreakByAlreadyMatchesColumnInner() {
      RunningTotalCalc calc = new RunningTotalCalc();
      calc.setBreakBy(AbstractCalc.COLUMN_INNER);
      calc.setResetLevel(RunningTotalColumn.YEAR);

      VSUtil.updateCalculateInfo(true, true, false, calc, null);

      assertEquals(AbstractCalc.COLUMN_INNER, calc.getBreakBy());
      assertEquals(RunningTotalColumn.YEAR, calc.getResetLevel());
   }

   private static AggregateRef agg(String column, AggregateFormula formula) {
      return new AggregateRef(new ColumnRef(new AttributeRef(null, column)), formula);
   }

   // Bug #81202: the rewrite must find the text the way addUsedAggregateRef matched it
   @Test
   void createAOAExpressionRewritesNameInOtherCase() {
      Viewsheet vs1 = new Viewsheet();
      List<AggregateRef> aggs = new ArrayList<>();
      aggs.add(agg("a", AggregateFormula.COUNT_ALL));
      List<String> names = new ArrayList<>();
      names.add("field['Count([a])']");

      String proper = VSUtil.createAOAExpression(aggs, "field['Count([a])'] * 1", names, vs1);
      String lower = VSUtil.createAOAExpression(aggs, "field['count([A])'] * 1", names, vs1);
      String upper = VSUtil.createAOAExpression(aggs, "field['COUNT([A])'] * 1", names, vs1);

      assertNotEquals("field['Count([a])'] * 1", proper);
      assertEquals(proper, lower);
      assertEquals(proper, upper);
   }

   @Test
   void createAOAExpressionMatchesSharpSWrittenAsSS() {
      Viewsheet vs = new Viewsheet();
      List<AggregateRef> aggs = new ArrayList<>();
      aggs.add(agg("straße", AggregateFormula.COUNT_ALL));
      List<String> names = new ArrayList<>();
      names.add("field['Count([straße])']");

      String proper = VSUtil.createAOAExpression(
         aggs, "field['Count([straße])'] + 1", names, vs);
      String ss = VSUtil.createAOAExpression(aggs, "field['Count([STRASSE])'] + 1", names, vs);

      assertNotEquals("field['Count([straße])'] + 1", proper);
      assertEquals(proper, ss);
   }

   // columns a and A, both counted: each spelling is rewritten with its own aggregate
   @Test
   void createAOAExpressionKeepsColumnsThatDifferOnlyByCaseApart() {
      Viewsheet vs = new Viewsheet();
      List<AggregateRef> aggs = new ArrayList<>();
      aggs.add(agg("a", AggregateFormula.COUNT_ALL));
      aggs.add(agg("A", AggregateFormula.COUNT_ALL));
      List<String> names = new ArrayList<>();
      names.add("field['Count([a])']");
      names.add("field['Count([A])']");

      String both = VSUtil.createAOAExpression(
         aggs, "field['Count([A])'] + field['Count([a])']", names, vs);
      String onlyA = VSUtil.createAOAExpression(
         aggs.subList(1, 2), "field['Count([A])']", names.subList(1, 2), vs);
      String onlyLowerA = VSUtil.createAOAExpression(
         aggs.subList(0, 1), "field['Count([a])']", names.subList(0, 1), vs);

      assertNotEquals(onlyA, onlyLowerA);
      assertEquals(onlyA + " + " + onlyLowerA, both);
   }

   // a spelling that is the exact name of no aggregate takes the case-insensitive fallback; the
   // exact occurrences keep their own aggregate
   @Test
   void createAOAExpressionExactSpellingWinsOverOtherCase() {
      Viewsheet vs = new Viewsheet();
      List<AggregateRef> aggs = new ArrayList<>();
      aggs.add(agg("a", AggregateFormula.COUNT_ALL));
      aggs.add(agg("A", AggregateFormula.COUNT_ALL));
      List<String> names = new ArrayList<>();
      names.add("field['Count([a])']");
      names.add("field['Count([A])']");

      String exact = VSUtil.createAOAExpression(
         aggs, "field['Count([a])'] + field['Count([A])'] + field['COUNT([a])']", names, vs);
      String onlyA = VSUtil.createAOAExpression(
         aggs.subList(1, 2), "field['Count([A])']", names.subList(1, 2), vs);
      String onlyLowerA = VSUtil.createAOAExpression(
         aggs.subList(0, 1), "field['Count([a])']", names.subList(0, 1), vs);

      // the stray COUNT([a]) matches both names in any case; the first listed name takes it, and
      // the exact occurrences are untouched by it
      assertEquals(onlyLowerA + " + " + onlyA + " + " + onlyLowerA, exact);
   }
}
