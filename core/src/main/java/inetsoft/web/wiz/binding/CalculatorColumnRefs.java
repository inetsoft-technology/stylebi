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

import inetsoft.report.composition.graph.calc.PercentCalc;
import inetsoft.report.composition.graph.calc.RunningTotalCalc;
import inetsoft.report.composition.graph.calc.ValueOfCalc;
import inetsoft.uql.viewsheet.VSDataRef;
import inetsoft.uql.viewsheet.XDimensionRef;
import inetsoft.uql.viewsheet.graph.AbstractCalc;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.web.binding.model.graph.CalculateInfo;
import inetsoft.web.binding.model.graph.calc.PercentCalcInfo;
import inetsoft.web.binding.model.graph.calc.RunningTotalCalcInfo;
import inetsoft.web.binding.model.graph.calc.ValueOfCalcInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * The dimension a chart calculator designates: a CHANGE/VALUE {@code columnName}, a PERCENT
 * {@code columnName}, or a RUNNINGTOTAL/COMPOUNDGROWTH {@code breakBy} (MOVING has none).
 * {@code ChangeChartProcessor} resolves such a name against the chart's dimensions and clears one
 * it cannot match, which would otherwise be reported as success with a calculator that designates
 * nothing; the pre-check and read-back refusals share this accessor (bugs #78403, #78751).
 */
final class CalculatorColumnRefs {
   private CalculatorColumnRefs() {
   }

   /** A calculator's dimension reference: the field it is sent under and its value. */
   record Ref(String key, String name) {
      /** Blank and the inner-row/column sentinels designate no bound dimension. */
      boolean checkable() {
         return name != null && !name.isBlank() && !AbstractCalc.ROW_INNER.equals(name) &&
            !AbstractCalc.COLUMN_INNER.equals(name);
      }
   }

   /** The reference carried by a calculator sent by the caller, or null if the type has none. */
   static Ref sent(CalculateInfo info) {
      if(info instanceof ValueOfCalcInfo calc) {
         return new Ref("columnName", calc.getColumnName());
      }

      if(info instanceof PercentCalcInfo calc) {
         return new Ref("columnName", calc.getColumnName());
      }

      if(info instanceof RunningTotalCalcInfo calc) {
         return new Ref("breakBy", calc.getBreakBy());
      }

      return null;
   }

   /**
    * Whether the runtime cleared the reference of the stored calculator that corresponds to a
    * {@code sent} reference of the same type.
    */
   static boolean cleared(Ref sent, Object storedCalc) {
      if(sent == null) {
         return false;
      }

      if(storedCalc instanceof ValueOfCalc calc) {
         return calc.getColumnName() == null;
      }

      if(storedCalc instanceof PercentCalc calc) {
         return calc.getColumnName() == null;
      }

      if(storedCalc instanceof RunningTotalCalc calc) {
         return calc.getBreakBy() == null;
      }

      return false;
   }

   /**
    * Post-write read-back: a calculator reference the caller sent must still designate a
    * dimension. Refuses only a sent non-blank name whose stored counterpart is null; a null or
    * blank name is legitimate (no break, grand-total percent).
    */
   static void requireResolved(VSChartInfo info, CalculateInfo sentInfo, Object storedCalc) {
      Ref sent = sent(sentInfo);

      if(sent == null || !sent.checkable() || !cleared(sent, storedCalc)) {
         return;
      }

      List<String> names = new ArrayList<>();

      for(ChartRef[] refs : new ChartRef[][] {
         info.getXFields(), info.getYFields(), info.getGroupFields()})
      {
         for(ChartRef ref : refs) {
            if(ref instanceof XDimensionRef && ref instanceof VSDataRef dim &&
               !names.contains(dim.getFullName()))
            {
               names.add(dim.getFullName());
            }
         }
      }

      throw new IllegalArgumentException(
         "calculateInfo." + sent.key() + " '" + sent.name() + "' does not designate one bound " +
         "dimension (unknown, or the same column is bound at several levels), so the " +
         "calculator would store no dimension. " + (names.isEmpty()
            ? "No dimension is bound; bind the dimension first."
            : "Use one of the bound dimensions' full names: " + String.join(", ", names) + "."));
   }
}
