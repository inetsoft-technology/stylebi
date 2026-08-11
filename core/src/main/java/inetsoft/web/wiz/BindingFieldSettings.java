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
package inetsoft.web.wiz;

import inetsoft.uql.XCondition;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.AestheticRef;
import inetsoft.uql.viewsheet.graph.Calculator;
import inetsoft.uql.viewsheet.graph.VSChartInfo;

import java.util.*;

/**
 * Moves a visualization's binding-field settings — a dimension's top-/bottom-N ranking, value/manual
 * sort and date grouping, a measure's aggregate formula and calculator — between two assemblies,
 * pairing refs by the column they bind.
 *
 * <p>Exists to keep those settings alive across a rebuild-from-recommendation. The wizard temp chart
 * in an autoBinding RVS's {@code VSTemporaryInfo} is the only binding state that survives one:
 * {@code ChartCombinationUtil} generates every recommendation from {@code temp.getXFields()/getYFields()},
 * and {@code ChartTypeFilter.getAllRefs(true)} puts a CLONE of each temp ref into every candidate chart
 * info. A setting recorded on the temp chart is therefore inherited by every candidate of every chart
 * type; one applied only to the rendered assembly is discarded the next time a chart is rebuilt.
 *
 * <p>The two directions are deliberately NOT symmetric — see {@link #record} and {@link #restore}.
 */
public final class BindingFieldSettings {
   private BindingFieldSettings() {
   }

   /**
    * Every ref carrying binding-field settings on this assembly, flattened: a chart's x/y plus the
    * dimensions/measures on its aesthetic slots (a dimension moved onto colour or size for a pie or
    * treemap keeps its ranking), or a crosstab's design row/col headers plus aggregates.
    */
   public static DataRef[] refsOf(VSAssembly assembly) {
      List<DataRef> refs = new ArrayList<>();

      if(assembly instanceof ChartVSAssembly chartAsm && chartAsm.getVSChartInfo() != null) {
         VSChartInfo info = chartAsm.getVSChartInfo();
         addAll(refs, info.getXFields());
         addAll(refs, info.getYFields());

         for(AestheticRef aref : new AestheticRef[] {
            info.getColorField(), info.getShapeField(), info.getSizeField(), info.getTextField() })
         {
            if(aref != null && aref.getDataRef() != null) {
               refs.add(aref.getDataRef());
            }
         }
      }
      else if(assembly instanceof CrosstabVSAssembly crosstabAsm &&
              crosstabAsm.getVSCrosstabInfo() != null)
      {
         VSCrosstabInfo info = crosstabAsm.getVSCrosstabInfo();
         addAll(refs, info.getDesignRowHeaders());
         addAll(refs, info.getDesignColHeaders());
         addAll(refs, info.getDesignAggregates());
      }

      return refs.toArray(new DataRef[0]);
   }

   /**
    * {@link #refsOf} with each ref detached from the assembly, for a caller that must read the settings
    * as they are NOW but record them later.
    *
    * <p>{@code createViewsheetInternal} needs exactly this: it may push aggregation down to the
    * worksheet, which rewrites a pushed measure's formula to NONE and clears a bucketed dimension's date
    * level on the very refs recorded here — while the recording itself must wait until the whole request
    * has succeeded, so a rolled-back create leaves the temp chart untouched.
    */
   public static DataRef[] snapshot(VSAssembly assembly) {
      DataRef[] refs = refsOf(assembly);
      DataRef[] detached = new DataRef[refs.length];

      for(int i = 0; i < refs.length; i++) {
         // Only the two kinds copy() reads are worth cloning; anything else is carried as-is and
         // ignored downstream.
         if(refs[i] instanceof VSDimensionRef dim) {
            detached[i] = (DataRef) dim.clone();
         }
         else if(refs[i] instanceof VSAggregateRef agg) {
            detached[i] = (DataRef) agg.clone();
         }
         else {
            detached[i] = refs[i];
         }
      }

      return detached;
   }

   /**
    * The set of columns an assembly binds, keyed exactly as {@link #record}/{@link #restore} pair refs.
    *
    * <p>Used to decide whether a wizard temp chart still describes the assembly a call is about. Column
    * identity is the right granularity for that question and the SETTINGS are not: a recommendation
    * candidate carries per-type values the temp chart never had (the `order` and date level the
    * recommender picked — the very reason {@code restore} copies only what the source carries), so
    * comparing settings would report a difference after every ordinary autoBinding and force a rebuild
    * every time. The candidate's refs, by contrast, are CLONES of the temp chart's, so equal column
    * sets is a reliable "same binding" test.
    *
    * <p>Slot-agnostic on purpose — {@link #refsOf} flattens x/y and the aesthetics together, and a
    * chart-type change is precisely a change of slot placement.
    */
   public static Set<String> columnKeys(VSAssembly assembly) {
      return columnKeys(refsOf(assembly));
   }

   /**
    * {@link #columnKeys(VSAssembly)} over refs already in hand — for a caller holding a
    * {@link #snapshot}, whose keys must be read BEFORE a mutation (the pre-aggregation push rewrites
    * the assembly's refs to the pushed columns) rather than off the live assembly.
    */
   public static Set<String> columnKeys(DataRef[] refs) {
      if(refs == null) {
         return Collections.emptySet();
      }

      Set<String> keys = new HashSet<>();

      for(DataRef ref : refs) {
         String key = fieldKey(ref);

         if(key != null && !key.isEmpty()) {
            keys.add(key);
         }
      }

      return keys;
   }

   /**
    * RESTORE onto a freshly-rebuilt assembly: copies only the settings the source actually carries.
    *
    * <p>A default-valued source must not overwrite the ordering or date level the recommender chose for
    * the target type, so "absent" means "leave the target alone" here. Used when reading back off the
    * temp chart, where the target is a recommendation candidate with its own per-type decisions.
    */
   public static void restore(DataRef[] fromRefs, DataRef[] toRefs) {
      copy(fromRefs, toRefs, false);
   }

   /**
    * RECORD onto the temp chart: makes the target match the source exactly, clearing what the source
    * does not carry.
    *
    * <p>The source here is the chart the user is actually looking at, so it is authoritative about what
    * is set AND about what is no longer set. Without the clear, a removal could never reach the temp
    * chart and the next rebuild would push the stale setting back onto the chart — silently reverting
    * the user's own edit one turn later, and self-reinforcing from then on.
    */
   public static void record(DataRef[] fromRefs, DataRef[] toRefs) {
      copy(fromRefs, toRefs, true);
   }

   private static void copy(DataRef[] fromRefs, DataRef[] toRefs, boolean authoritative) {
      Map<String, DataRef> from = unambiguousIndex(fromRefs);
      Map<String, DataRef> to = unambiguousIndex(toRefs);

      if(from.isEmpty() || to.isEmpty()) {
         return;
      }

      for(Map.Entry<String, DataRef> target : to.entrySet()) {
         DataRef source = from.get(target.getKey());
         DataRef ref = target.getValue();

         if(source == null || source == ref) {
            continue;
         }

         if(source instanceof VSDimensionRef fromDim && ref instanceof VSDimensionRef toDim) {
            copyDimensionSettings(fromDim, toDim, authoritative);
         }
         else if(source instanceof VSAggregateRef fromAgg && ref instanceof VSAggregateRef toAgg) {
            copyAggregateSettings(fromAgg, toAgg, authoritative);
         }
      }
   }

   /**
    * Indexes refs by bound column, DROPPING any column bound more than once.
    *
    * <p>The key cannot disambiguate those: it is the column value precisely because the formula and the
    * date level are what this class carries, so keying on the ref's full name would stop a formula or
    * level change from ever transferring. Pairing by slot is not an option either — the restore
    * direction exists because slots move between chart types. So a column bound twice (Sum(amount) and
    * Average(amount) on y, or one date column at two levels) keeps its own settings on both refs rather
    * than having the first one's silently applied to the second.
    */
   private static Map<String, DataRef> unambiguousIndex(DataRef[] refs) {
      if(refs == null) {
         return Collections.emptyMap();
      }

      Map<String, DataRef> index = new HashMap<>();
      Set<String> ambiguous = new HashSet<>();

      for(DataRef ref : refs) {
         String key = fieldKey(ref);

         if(key == null || key.isEmpty()) {
            continue;
         }

         if(index.putIfAbsent(key, ref) != null) {
            ambiguous.add(key);
         }
      }

      ambiguous.forEach(index::remove);
      return index;
   }

   /**
    * The bound column, taken from the VALUE accessors on the BASE ref types.
    *
    * <p>Deliberately not {@code WizardRecommenderUtil.getChartRefFieldName}, which only special-cases the
    * VSChart* subtypes: a crosstab's design headers and aggregates are plain VSDimensionRef /
    * VSAggregateRef and would fall through to {@code getAttribute()}, which returns the underlying column
    * attribute rather than the group/column value the chart side keys on. The two sides would then derive
    * keys differently and, for a qualified or unresolved ref, never pair up at all — a silent no-match.
    * The chart subtypes inherit these accessors, so both sides agree.
    */
   private static String fieldKey(DataRef ref) {
      if(ref instanceof VSDimensionRef dim) {
         return dim.getGroupColumnValue();
      }

      if(ref instanceof VSAggregateRef agg) {
         return agg.getColumnValue();
      }

      return ref == null ? null : ref.getAttribute();
   }

   private static void copyDimensionSettings(VSDimensionRef from, VSDimensionRef to,
                                             boolean authoritative)
   {
      boolean ranked = isActiveRanking(from.getRankingOptionValue());

      if(ranked || authoritative) {
         to.setRankingOptionValue(from.getRankingOptionValue());
         to.setRankingNValue(from.getRankingNValue());
         to.setRankingColValue(from.getRankingColValue());
         to.setGroupOthersValue(from.getGroupOthersValue());
      }

      String sortByCol = from.getSortByColValue();
      boolean hasSortByCol = sortByCol != null && !sortByCol.isEmpty();

      if(hasSortByCol || authoritative) {
         to.setSortByColValue(sortByCol);
      }

      List manualOrder = from.getManualOrderList();
      boolean hasManualOrder = manualOrder != null && !manualOrder.isEmpty();

      if(hasManualOrder) {
         to.setManualOrderList(new ArrayList<>(manualOrder));
      }
      else if(authoritative) {
         to.setManualOrderList(null);
      }

      // `order` is what gives a ranking or a value-/manual-sort its meaning (17 value-asc, 18 value-desc,
      // MANUAL). On a restore, copy it only when one of those is present — a bare default order would
      // overwrite the sort the recommender chose for the target type.
      if(ranked || hasSortByCol || hasManualOrder || authoritative) {
         to.setOrder(from.getOrder());
      }

      String dateLevel = from.getDateLevelValue();

      if(dateLevel != null && !dateLevel.isEmpty() || authoritative) {
         to.setDateLevelValue(dateLevel);
         to.setTimeSeries(from.isTimeSeries());
      }
   }

   /** True when the ranking option actually selects a top-/bottom-N (not NONE, not unset). */
   private static boolean isActiveRanking(String optionValue) {
      if(optionValue == null || optionValue.isEmpty()) {
         return false;
      }

      try {
         int option = Integer.parseInt(optionValue);
         return option == XCondition.TOP_N || option == XCondition.BOTTOM_N;
      }
      catch(NumberFormatException e) {
         // A dynamic (expression-valued) ranking option — not resolvable here, so carry it over rather
         // than dropping a setting the user did express.
         return true;
      }
   }

   private static void copyAggregateSettings(VSAggregateRef from, VSAggregateRef to,
                                             boolean authoritative)
   {
      String formula = from.getFormulaValue();

      if(formula != null && !formula.isEmpty() || authoritative) {
         to.setFormulaValue(formula);
      }

      Calculator calculator = from.getCalculator();

      if(calculator != null) {
         // Clone: VSAggregateRef.clone() treats the calculator as owned state and clones it defensively,
         // and the temp chart is long-lived — sharing one mutable instance with a rendered assembly would
         // let a later mutation on either side silently change the other.
         to.setCalculator((Calculator) calculator.clone());
      }
      else if(authoritative) {
         to.setCalculator(null);
      }
   }

   private static void addAll(List<DataRef> refs, DataRef[] arr) {
      if(arr != null) {
         for(DataRef ref : arr) {
            if(ref != null) {
               refs.add(ref);
            }
         }
      }
   }
}
