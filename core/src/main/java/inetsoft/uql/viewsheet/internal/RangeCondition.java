/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.viewsheet.internal;

import inetsoft.report.filter.ConditionGroup;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;

import java.util.*;

/**
 * This class stores the range condition values.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public class RangeCondition {
   /**
    * Create range conditions from normal conditions in the form of greater
    * than and less than.
    */
   public static List<Object> createRangeConditions(ConditionList conds, String id) {
      Vector<Object> list = new Vector<>();

      for(int i = 0; i < conds.getSize() + 1; i += 4) {
         ConditionItem greater = conds.getConditionItem(i);
         ConditionItem less = conds.getConditionItem(i + 2);
         final boolean lowerInclusive = greater.getCondition().isEqual();
         final boolean upperInclusive = less.getCondition().isEqual();

         RangeCondition cond = new RangeCondition(
            new Object[] {greater.getCondition().getValue(0)},
            new Object[] {less.getCondition().getValue(0)},
            new DataRef[] {greater.getAttribute()},
            lowerInclusive, upperInclusive, false, id);
         list.add(cond);
      }

      return list;
   }

   public static RangeCondition from(TimeSliderVSAssembly assembly) {
      return from(assembly, assembly.getDataRefs());
   }

   public static RangeCondition from(TimeSliderVSAssembly assembly, DataRef[] refs) {
      final Object[] mins = assembly.getSplitSelectedMinValues();
      final Object[] maxes = assembly.getSplitSelectedMaxValues();
      final TimeInfo tinfo = assembly.getTimeInfo();
      // splitSelectedMax already accounts for upper inclusivity for composite time sliders
      final boolean upperInclusive =
         assembly.isUpperInclusive() || tinfo instanceof CompositeTimeInfo;
      final boolean lowerInclusive = true;
      final boolean nullable = tinfo instanceof CompositeTimeInfo;

      return new RangeCondition(mins, maxes, refs, lowerInclusive, upperInclusive, nullable,
                                assembly.getName());
   }

   private RangeCondition(Object[] mins, Object[] maxes, DataRef[] refs, boolean lowerInclusive,
                         boolean upperInclusive, boolean nullable, String id)
   {
      if(mins.length != maxes.length || mins.length != refs.length) {
         throw new IllegalArgumentException(
            "Range Condition constructor args must have same length");
      }

      this.mins = mins;
      this.maxes = maxes;
      this.refs = refs;
      this.lowerInclusive = lowerInclusive;
      this.upperInclusive = upperInclusive;
      this.nullable = nullable;
      this.id = id;
   }

   public boolean evaluate(Object[] vals) {
      return getEvaluator().evaluate(vals);
   }

   private ConditionGroup getEvaluator() {
      if(evaluator == null) {
         final Map<String, Integer> columnIndices = new HashMap<>();

         for(int i = 0; i < refs.length; i++) {
            columnIndices.put(refs[i].toView(), i);
         }

         final ConditionList conditionList = createConditionList();
         evaluator = new ConditionGroup(conditionList, columnIndices);
      }

      return evaluator;
   }

   /**
    * Create a condition list for the range.
    */
   public ConditionList createConditionList() {
      if(conditionList == null) {
         conditionList = new RangeConditionListCreator(this).createConditionList();
      }

      return conditionList;
   }

   Object[] getMins() {
      return mins;
   }

   Object[] getMaxes() {
      return maxes;
   }

   DataRef[] getRefs() {
      return refs;
   }

   boolean isUpperInclusive() {
      return upperInclusive;
   }

   boolean isLowerInclusive() {
      return lowerInclusive;
   }

   boolean isNullable() {
      return nullable;
   }

   /**
    * Conditions below to the same group (e.g. one calendar) share the same id.
    */
   public String getId() {
      return id;
   }

   public String toString() {
      final String lowerBracket = lowerInclusive ? "[" : "(";
      final String upperBracket = upperInclusive ? "]" : ")";

      final StringJoiner joiner = new StringJoiner(" - ", lowerBracket, upperBracket);
      joiner.add(Arrays.toString(mins));
      joiner.add(Arrays.toString(maxes));

      return "RangeCondition" + joiner;
   }

   private ConditionList conditionList;
   private ConditionGroup evaluator;

   private final Object[] mins;
   private final Object[] maxes;
   private final DataRef[] refs;
   private final boolean lowerInclusive;
   private final boolean upperInclusive;
   private final boolean nullable;
   private final String id;
}
