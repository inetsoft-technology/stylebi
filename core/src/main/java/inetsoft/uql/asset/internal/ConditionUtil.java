/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.uql.asset.internal;

import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.DataVSAssemblyInfo;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toSet;

/**
 * Helper functions for conditions.
 *
 * @version 13.5
 * @author InetSoft Technology Corp
 */
public class ConditionUtil {
   /**
    * Negate the condition, this is not same as ConditionList.negate, this
    * function handle null value correct.
    */
   public static ConditionList negate(ConditionListWrapper wrapper) {
      if(wrapper == null || wrapper.isEmpty()) {
         return wrapper == null ? null : wrapper.getConditionList();
      }

      ConditionList conds = wrapper.getConditionList();
      ConditionList nconds = new ConditionList();
      boolean allAnd = isAllAnd(conds);
      boolean ident = conds.getSize() > 1 && !allAnd;

      for(int i = 0; i < conds.getSize(); i++) {
         HierarchyItem hitem = conds.getItem(i);

         if(hitem instanceof ConditionItem) {
            ConditionItem item = (ConditionItem) hitem.clone();
            XCondition cond = item.getXCondition();
            cond.setNegated(!cond.isNegated());
            nconds.append(item);

            if(cond.getOperation() != XCondition.NULL) {
               // ident
               if(ident) {
                  item.setLevel(item.getLevel() + 1);
               }

               JunctionOperator op = new JunctionOperator(JunctionOperator.OR,
                                                          item.getLevel());
               nconds.append(op);

               if(ident) {
                  op.setLevel(op.getLevel() + 1);
               }

               item = (ConditionItem) hitem.clone();
               cond = item.getXCondition();
               cond.setOperation(XCondition.NULL);
               nconds.append(item);

               if(ident) {
                  item.setLevel(item.getLevel() + 1);
               }
            }
         }
         else {
            JunctionOperator op = (JunctionOperator) hitem.clone();
            op.setJunction(op.getJunction() == JunctionOperator.AND ?
                           JunctionOperator.OR : JunctionOperator.AND);
            nconds.append(op);
         }
      }

      nconds = optimizeConditions(nconds);
      return nconds;
   }

   private static boolean isAllAnd(ConditionList conds) {
      boolean allAnd = false;
      Set<Integer> itemLevels = conds.stream()
         .filter(o -> o instanceof ConditionItem)
         .map(o -> ((ConditionItem) o).getLevel())
         .collect(toSet());

      if(itemLevels.size() == 1) {
         int itemLevel = itemLevels.iterator().next();
         allAnd = conds.stream()
            .filter(o -> o instanceof JunctionOperator)
            .filter(o -> ((JunctionOperator) o).getLevel() == itemLevel)
            .allMatch(o -> ((JunctionOperator) o).getJunction() == JunctionOperator.AND);
      }

      return allAnd;
   }

   // retructure conditions for better performance.
   private static ConditionList optimizeConditions(ConditionList conds) {
      // condition (junction) level -> junction ops
      Map<Integer, Set<Integer>> levelOps = new HashMap<>();
      conds.stream().filter(o -> o instanceof JunctionOperator)
         .forEach(o -> {
               JunctionOperator op = (JunctionOperator) o;
               Set<Integer> ops = levelOps.computeIfAbsent(op.getLevel(), k -> new HashSet<>());
               ops.add(op.getJunction());
            });
      // fields used in condition.
      Set<DataRef> fields = new HashSet<>();
      // condition ops (not junction) used in condition.
      Set<Integer> ops = new HashSet<>();
      // condition negated flags.
      Set<Boolean> negates = new HashSet<>();

      // collect all field/op/negate from conditions.
      conds.stream()
         .filter(o -> o instanceof ConditionItem)
         .forEach(o -> {
               fields.add(((ConditionItem) o).getAttribute());
               ops.add(((ConditionItem) o).getXCondition().getOperation());
               negates.add(((ConditionItem) o).getXCondition().isNegated());
            });

      // condition with two levels, e.g. (54486)
      //   A is not equal to 'abc'
      //   or
      //   A is null
      // and
      //   A is not equal to 'def'
      //   or
      //   A is null
      // ...
      if(levelOps.size() == 2 && levelOps.containsKey(0) && levelOps.containsKey(1)) {
         if(levelOps.get(0).size() == 1 && levelOps.get(1).size() == 1) {
            int op0 = levelOps.get(0).iterator().next();
            int op1 = levelOps.get(1).iterator().next();
            // condition like:
            //   (A or B) and (A or C)
            boolean andOr = op0 == JunctionOperator.AND && op1 == JunctionOperator.OR;
            //   (A and B) or (A and C)
            boolean orAnd = op0 == JunctionOperator.OR && op1 == JunctionOperator.AND;

            // transform the preview example to:
            // A is null
            // or
            //   A is not one of ('abc', 'def')
            //
            // or the following:
            //   A is equals to 'abc'
            //   and
            //   B is equals to 'def'
            // or
            //   A is equals to 'abc'
            //   and
            //   B is equals to 'ghi'
            //
            // to the following:
            //   A is equals to 'abc'
            // and
            //   B is one of ('def', 'ghi')
            if(andOr || orAnd) {
               // each set in the condGroups is a collection of conditions between the AND/OR
               Set<Set<ConditionItem>> condGroups = new HashSet<>();
               // the current group of conditions to be added to condGroups
               Set<ConditionItem> currGroup = new HashSet<>();

               // break into sets of condition connected by OR/AND between AND/OR
               for(int i = 0; i < conds.getSize(); i++) {
                  Object item = conds.getItem(i);

                  // and, start new set
                  if(item instanceof JunctionOperator) {
                     if(((JunctionOperator) item).getJunction() == op0) {
                        condGroups.add(currGroup);
                        currGroup = new HashSet<>();
                     }
                  }
                  else if(item instanceof ConditionItem) {
                     ((ConditionItem) item).setLevel(0);
                     currGroup.add((ConditionItem) item);
                  }
               }

               condGroups.add(currGroup);

               // find common conditions among all condition groups
               Set<ConditionItem> commonConds = condGroups.stream()
                  .filter(a -> !a.isEmpty())
                  .reduce((a, b) -> {
                        Set<ConditionItem> s1 = new HashSet<>(a);
                        s1.retainAll(b);
                        return s1;
                     }).orElse(new HashSet<>());

               // if there are common conditions, extract the common conditions to be processed
               // by themselves, and remove them from individual condition groups.
               if(!commonConds.isEmpty()) {
                  ConditionList nconds1 = new ConditionList();
                  ConditionList nconds2 = new ConditionList();

                  // add the common conditions.
                  for(ConditionItem commonCond : commonConds) {
                     if(!nconds1.isEmpty()) {
                        nconds1.append(new JunctionOperator(op1, 0));
                     }

                     nconds1.append(commonCond);
                     commonCond.setLevel(0);
                  }

                  // add unique conditions without common conditions.
                  for(Set<ConditionItem> group : condGroups) {
                     group.removeAll(commonConds);

                     if(group.isEmpty()) {
                        continue;
                     }

                     if(!nconds2.isEmpty()) {
                        nconds2.append(new JunctionOperator(op0, 0));
                     }

                     boolean first = true;

                     for(ConditionItem cond : group) {
                        if(!first) {
                           nconds2.append(new JunctionOperator(op1, 1));
                        }

                        first = false;
                        cond.setLevel(1);
                        nconds2.append(cond);
                     }
                  }

                  // recursive optimization
                  ConditionList nconds3 = optimizeConditions(nconds2);
                  nconds3.indent(1);
                  nconds1.append(nconds3, new JunctionOperator(op1, 0));
                  conds = nconds1;
               }
            }
         }
      }
      // reduce a group of equal or not-equal with the same field to one-of condition
      else if(fields.size() == 1 && ops.size() == 1 && negates.size() == 1 &&
              levelOps.size() == 1)
      {
         Set<Integer> levelOpSet = levelOps.values().iterator().next();
         int op = ops.iterator().next();

         if(op == XCondition.EQUAL_TO && levelOpSet.size() == 1) {
            int levelOp = levelOpSet.iterator().next();
            boolean negate = negates.iterator().next();

            // change to IN operator
            if(levelOp == JunctionOperator.AND && negate ||
               levelOp == JunctionOperator.OR && !negate)
            {
               Set values = conds.stream()
                  .filter(o -> o instanceof ConditionItem)
                  .map(o -> ((Condition) ((ConditionItem) o).getXCondition()).getValue(0))
                  .collect(toSet());

               DataRef ref = fields.iterator().next();
               ConditionList nconds = new ConditionList();
               Condition cond = new Condition();
               cond.setOperation(XCondition.ONE_OF);
               cond.setValues(new ArrayList<>(values));
               cond.setNegated(negate);
               ConditionItem item = new ConditionItem(ref, cond, 0);
               nconds.append(item);
               conds = nconds;
            }
         }
      }

      return conds;
   }

   /**
    * Expand condition between from
    * "xx equal to t"
    * "and"
    * "xx between {a, b} and {c, d}" TO
    * <p/>
    * "xx equal to t"
    * "and"
    * "   xx between a and c"
    * "   or"
    * "   xx between b and d"
    */
   public static ConditionList expandBetween(ConditionList cond, VariableTable vars) {
      if(cond == null || cond.isEmpty()) {
         return cond;
      }

      // all hierarchy items
      List<HierarchyItem> items = new ArrayList<>();
      // hierarchy level -> hierarchy item
      Map<Integer, List<HierarchyItem>> levels = new HashMap();
      splitCondition(cond, items, levels);

      List<Integer> betweens = new ArrayList<>();

      // find all between condition match the pattern
      for(int i = 0; i < items.size(); i++) {
         HierarchyItem item = items.get(i);

         if(!(item instanceof ConditionItem)) {
            continue;
         }

         ConditionItem citem = (ConditionItem) item;
         XCondition xcond = citem.getXCondition();

         if(xcond instanceof Condition &&
            xcond.getOperation() == XCondition.BETWEEN) {
            Condition condition = (Condition) xcond;

            if(vars != null) {
               condition.replaceVariable(vars);
            }

            Object var1 = condition.getValue(0);
            Object var2 = condition.getValue(1);

            if(var1 instanceof Object[] && var2 instanceof Object[]) {
               Object[] arr1 = (Object[]) var1;
               Object[] arr2 = (Object[]) var2;

               if(arr1.length == arr2.length) {
                  betweens.add(i);
               }
            }
         }
      }

      if(betweens.size() <= 0) {
         return cond;
      }

      for(int i = betweens.size() - 1; i >= 0; i--) {
         expandBetween(items, betweens.get(i));
      }

      ConditionList ncond = new ConditionList();

      for(HierarchyItem item : items) {
         ncond.append(item);
      }

      return shrinkCondition(ncond);
   }

   /**
    * Expand item.
    */
   private static void expandBetween(List<HierarchyItem> items, int index) {
      ConditionItem citem = (ConditionItem) items.get(index);
      Condition cond = citem.getCondition();
      Object[] arr1 = (Object[]) cond.getValue(0);
      Object[] arr2 = (Object[]) cond.getValue(1);
      int level = citem.getLevel();
      int nlevel = arr1.length > 1 ? level + 1 : level;

      if(nlevel != level) {
         indent(level, items);
      }

      boolean add = index >= items.size() - 1;

      for(int i = 0; i < arr1.length; i++) {
         ConditionItem ncitem = (ConditionItem) citem.clone();
         ncitem.setLevel(nlevel);
         Condition ncond = ncitem.getCondition();
         ncond.removeAllValues();
         ncond.addValue(arr1[i]);
         ncond.addValue(arr2[i]);

         if(i == 0) {
            items.set(index, ncitem);
         }
         else if(add) {
            items.add(ncitem);
         }
         else {
            items.add(index + i + 1, ncitem);
         }

         if(i < arr1.length - 1) {
            JunctionOperator op =
               new JunctionOperator(JunctionOperator.OR, nlevel);

            if(add) {
               items.add(op);
            }
            else {
               items.add(index + i + 1, op);
            }
         }
      }
   }

   /**
    * Split condition by item.
    */
   private static void splitCondition(ConditionList cond,
                                      List<HierarchyItem> items,
                                      Map<Integer, List<HierarchyItem>> levels) {
      if(cond == null || cond.isEmpty()) {
         return;
      }

      for(int i = 0; i < cond.getSize(); i++) {
         HierarchyItem item = cond.getItem(i);
         item = (HierarchyItem) item.clone();
         items.add(item);

         List<HierarchyItem> list = levels.get(item.getLevel());

         if(list == null) {
            list = new ArrayList<>();
            levels.put(item.getLevel(), list);
         }

         list.add(item);
      }
   }

   /**
    * Not a condition list, convert the condition to opposite meaning.
    */
   public static ConditionListWrapper not(ConditionListWrapper wrapper) {
      if(wrapper == null || wrapper.isEmpty()) {
         return wrapper;
      }

      ConditionList cond = wrapper.getConditionList();
      // all hierarchy items
      List<HierarchyItem> items = new ArrayList<>();
      // hierarchy level -> hierarchy item
      Map<Integer, List<HierarchyItem>> levels = new HashMap();
      splitCondition(cond, items, levels);

      Collection<List<HierarchyItem>> values = levels.values();

      for(List<HierarchyItem> litems : values) {
         List[] subs = separate(litems);
         not(subs, items);
      }

      ConditionList ncond = new ConditionList();

      for(HierarchyItem item : items) {
         ncond.append(item);
      }

      return shrinkCondition(ncond);
   }

   /**
    * Split a date in range assembly condition into several date in range
    * condition.
    * "xx equal to t"
    * "and"
    * "date in range assembly (Last Year/This Year)" TO
    * <p/>
    * "xx equal to t"
    * "and"
    * "   date equal to Last Year"
    * "   or"
    * "   date equal to This Year"
    */
   public static ConditionList splitDateRangeCondition(ConditionList conds) {
      if(conds == null || conds.isEmpty()) {
         return conds;
      }

      // all hierarchy items
      List<HierarchyItem> items = new ArrayList<>();
      // hierarchy level -> hierarchy item
      Map<Integer, List<HierarchyItem>> levels = new HashMap();
      splitCondition(conds, items, levels);

      List<Integer> dateRanges = new ArrayList<>();

      // find all date in range condition match the pattern
      for(int i = 0; i < items.size(); i++) {
         HierarchyItem item = items.get(i);

         if(!(item instanceof ConditionItem)) {
            continue;
         }

         ConditionItem citem = (ConditionItem) item;
         XCondition xcond = citem.getXCondition();

         if(xcond instanceof DateRangeAssembly) {
            dateRanges.add(i);
         }
      }

      if(dateRanges.size() <= 0) {
         return conds;
      }

      for(int i = dateRanges.size() - 1; i >= 0; i--) {
         expandDateRange(items, dateRanges.get(i));
      }

      ConditionList ncond = new ConditionList();

      for(HierarchyItem item : items) {
         ncond.append(item);
      }

      return shrinkCondition(ncond);
   }

   /**
    * Expand date in range item.
    */
   private static void expandDateRange(List<HierarchyItem> items, int index) {
      ConditionItem citem = (ConditionItem) items.get(index);
      XCondition xcond = citem.getXCondition();

      if(!(xcond instanceof DateRangeAssembly)) {
         return;
      }

      DateRangeAssembly assembly = (DateRangeAssembly) xcond;
      boolean isNegated = assembly.isNegated();

      if(assembly.getDateRange() instanceof PeriodCondition) {
         DateCondition dcond = (DateCondition) assembly.getDateRange();
         boolean isTimestamp =
            XSchema.TIME_INSTANT.equals(citem.getAttribute().getDataType());
         Condition cond = dcond.toSqlCondition(isTimestamp);
         cond.setNegated(isNegated);
         citem.setCondition(cond);

         return;
      }

      DateRange range = (DateRange) assembly.getDateRange();
      XCondition[] conditions = range.getDateConditions();
      int level = citem.getLevel();
      int nlevel = conditions.length > 1 ? level + 1 : level;

      if(nlevel != level) {
         indent(level, items);
      }

      boolean add = index >= items.size() - 1;

      for(int i = 0; i < conditions.length; i++) {
         DateCondition dcond = (DateCondition) conditions[i];
         citem = (ConditionItem) citem.clone();
         citem.setLevel(nlevel);
         boolean isTimestamp =
            XSchema.TIME_INSTANT.equals(citem.getAttribute().getDataType());
         Condition cond = dcond.toSqlCondition(isTimestamp);
         cond.setNegated(isNegated);
         citem.setCondition(cond);

         if(i == 0) {
            items.set(index, citem);
         }
         else if(add) {
            items.add(citem);
         }
         else {
            items.add(index + i + 1, citem);
         }

         if(i < conditions.length - 1) {
            JunctionOperator op =
               new JunctionOperator(isNegated ? JunctionOperator.AND :
                                    JunctionOperator.OR, nlevel);

            if(add) {
               items.add(op);
            }
            else {
               items.add(index + i + 1, op);
            }
         }
      }
   }

   /**
    * Remove condition item from conditions.
    */
   public static ConditionList filter(ConditionList conds, AssetUtil.Filter filter) {
      if(conds == null || conds.isEmpty() || filter == null) {
         return conds;
      }

      conds = conds.clone();

      for(int i = 0; i < conds.getSize(); i++) {
         HierarchyItem item = conds.getItem(i);

         if(item instanceof JunctionOperator) {
            continue;
         }

         ConditionItem citem = (ConditionItem) item;
         DataRef attr = citem.getAttribute();

         if(filter.keep(attr)) {
            continue;
         }

         JunctionOperator lOp = i > 0 ? conds.getJunctionOperator(i - 1) : null;
         JunctionOperator nOp = i < conds.getSize() - 1 ?
            conds.getJunctionOperator(i + 1) : null;
         int level = citem.getLevel();
         int lOpLevel = lOp == null ? -1 : lOp.getLevel();
         int nOpLevel = nOp == null ? -1 : nOp.getLevel();
         int r1 = -1;
         int r2 = -1;

         if(lOpLevel == level && nOpLevel != level) {
            r1 = i;
            r2 = i - 1;
         }
         else if(nOpLevel == level && lOpLevel != level) {
            r1 = i + 1;
            r2 = i;
         }
         else if(nOpLevel == level && lOpLevel == level) {
            boolean nand = nOp.getJunction() == JunctionOperator.AND;
            r1 = nand ? i + 1 : i;
            r2 = nand ? i : i - 1;
         }
         else {
            r1 = nOp != null ? i + 1 : i;
            r2 = nOp != null ? i : i - 1;
         }

         conds.remove(r1);
         conds.remove(r2);
         i--;
      }

      conds = shrinkCondition(conds);
      return conds;
   }

   /**
    * Shrink condition levels.
    */
   public static ConditionList shrinkCondition(ConditionList conds) {
      if(conds == null || conds.isEmpty()) {
         return conds;
      }

      // all hierarchy items
      List<HierarchyItem> items = new ArrayList<>();
      // hierarchy level -> hierarchy item
      Map<Integer, List<HierarchyItem>> levels = new HashMap();
      splitCondition(conds, items, levels);
      shrinkHierarchy(items, 0);
      int maxLevel = maxLevel(items);
      validateHierarchy(items, maxLevel);
      shrinkHierarchy(items, 0);

      ConditionList ncond = new ConditionList();

      for(HierarchyItem item : items) {
         ncond.append(item);
      }

      return ncond;
   }

   private static void validateHierarchy(List<HierarchyItem> items, int level) {
      if(level <= 0) {
         return;
      }

      List<HierarchyItem> temp = new ArrayList();
      boolean hasOp = false;

      for(int i = 0; i < items.size(); i++) {
         if(items.get(i).getLevel() < level) {
            if(!hasOp && temp.size() < 3) {
               for(HierarchyItem item : temp) {
                  item.setLevel(level - 1);
               }
            }

            temp.clear();
         }
         else if(items.get(i).getLevel() == level) {
            temp.add(items.get(i));

            if(items.get(i) instanceof JunctionOperator) {
               hasOp = true;
            }
         }
      }

      if(!hasOp && temp.size() < 3) {
         for(HierarchyItem item : temp) {
            item.setLevel(level - 1);
         }
      }

      validateHierarchy(items, level - 1);
   }

   /**
    * Shrink the hierarchy items.
    */
   private static void shrinkHierarchy(List<HierarchyItem> items, int level) {
      if(items.size() <= 0) {
         return;
      }

      int minLevel = minLevel(items);
      shrinkLevel(items, minLevel - level);

      List<HierarchyItem> sub = new ArrayList();

      for(HierarchyItem item : items) {
         if(item.getLevel() <= level) {
            List<HierarchyItem> temp = new ArrayList(sub);
            sub.clear();
            shrinkHierarchy(temp, level + 1);
         }
         else {
            sub.add(item);
         }
      }

      shrinkHierarchy(sub, level + 1);
   }

   /**
    * Shrink the hierarchy list level.
    */
   private static void shrinkLevel(List<HierarchyItem> items, int step) {
      if(step < 1) {
         return;
      }

      for(HierarchyItem item : items) {
         item.setLevel(item.getLevel() - step);
      }
   }

   /**
    * Get the min level of all hierarchy items.
    */
   private static int minLevel(List<HierarchyItem> items) {
      int level = Integer.MAX_VALUE;

      for(HierarchyItem item : items) {
         level = Math.min(item.getLevel(), level);
      }

      return level;
   }

   /**
    * Get the max level of all hierarchy items.
    */
   private static int maxLevel(List<HierarchyItem> items) {
      int level = Integer.MIN_VALUE;

      for(HierarchyItem item : items) {
         level = Math.max(item.getLevel(), level);
      }

      return level;
   }

   /**
    * Separate hierarchy item by or.
    */
   private static List[] separate(List<HierarchyItem> items) {
      boolean andOp = false;
      boolean orOp = false;

      // separate hierarchy item by OR
      for(HierarchyItem item : items) {
         if(item instanceof JunctionOperator) {
            JunctionOperator op = (JunctionOperator) item;

            if(op.getJunction() == JunctionOperator.OR) {
               orOp = true;
               continue;
            }

            if(op.getJunction() == JunctionOperator.AND) {
               andOp = true;
               continue;
            }
         }
      }

      if(!andOp || !orOp) {
         List[] arr = {items};
         return arr;
      }

      List<List<HierarchyItem>> subs = new ArrayList<>();
      List<HierarchyItem> sub = new ArrayList<>();

      for(HierarchyItem item : items) {
         if(item instanceof JunctionOperator) {
            JunctionOperator op = (JunctionOperator) item;

            if(op.getJunction() == JunctionOperator.OR) {
               // add previous items
               if(sub.size() > 0) {
                  subs.add(sub);
               }

               // add or items
               sub = new ArrayList();
               sub.add(item);
               subs.add(sub);

               // prepare to add next items
               sub = new ArrayList();
               continue;
            }
         }

         sub.add(item);
      }

      if(sub.size() > 0) {
         subs.add(sub);
      }

      return subs.toArray(new List[0]);
   }

   /**
    * Not hierarchy items to opposite meaning.
    */
   private static void not(List[] subs, List<HierarchyItem> items) {
      int level = 0;

      for(List sub : subs) {
         for(Object obj : sub) {
            HierarchyItem item = (HierarchyItem) obj;
            level = item.getLevel();
            break;
         }
      }

      // AND and OR junction both exist?
      if(subs.length > 1) {
         indent(level, items);
      }

      for(List sub : subs) {
         if(subs.length > 1 && sub.size() > 1) {
            indent(sub);
         }

         for(Object obj : sub) {
            HierarchyItem item = (HierarchyItem) obj;
            not(item);
         }
      }
   }

   /**
    * Not the condition item, if junction, "and" to "or", "or" to "and",
    * if condition, "is" to "is not", "is not" to "is".
    */
   private static void not(HierarchyItem item) {
      if(item instanceof JunctionOperator) {
         JunctionOperator op = (JunctionOperator) item;

         if(op.getJunction() == JunctionOperator.AND) {
            op.setJunction(JunctionOperator.OR);
         }
         else {
            op.setJunction(JunctionOperator.AND);
         }
      }
      else if(item instanceof ConditionItem) {
         ConditionItem citem = (ConditionItem) item;
         XCondition cond = citem.getXCondition();
         cond.setNegated(!cond.isNegated());
      }
   }

   /**
    * Indent one level for those hierarchy items whose level is large than
    * the specified level.
    */
   private static void indent(int level, List<HierarchyItem> items) {
      for(HierarchyItem item : items) {
         if(item.getLevel() > level) {
            item.setLevel(item.getLevel() + 1);
         }
      }
   }

   /**
    * Indent one level for the specified hierarchy items.
    */
   private static void indent(List<HierarchyItem> items) {
      for(HierarchyItem item : items) {
         item.setLevel(item.getLevel() + 1);
      }
   }

   /**
    * Rename the conditon list wrapper.
    *
    * @param conds the specified condition list wrapper.
    * @param oname the specified old name.
    * @param nname the specified new name.
    * @param ws    the associated worksheet.
    */
   public static void renameConditionListWrapper(ConditionListWrapper conds,
                                                 String oname, String nname,
                                                 Worksheet ws) {
      for(int i = 0; i < conds.getConditionSize(); i += 2) {
         ConditionItem item = conds.getConditionItem(i);
         XCondition condition = item.getXCondition();

         if(condition instanceof DateRangeAssembly) {
            DateRangeAssembly assembly = (DateRangeAssembly) condition;
            String name = assembly.getName();

            if(!name.equals(oname)) {
               continue;
            }

            DateRangeAssembly assembly2 = ws == null ?
               null :
               (DateRangeAssembly) ws.getAssembly(nname);

            if(assembly2 == null) {
               LOG.error("Date range assembly not found: " + nname);
               continue;
            }

            assembly2.update();
            assembly2 = (DateRangeAssembly) assembly2.clone();
            assembly2.setNegated(condition.isNegated());
            assembly2.setType(condition.getType());
            assembly2.setEqual(condition.isEqual());
            assembly2.setOperation(condition.getOperation());
            item.setXCondition(assembly2);
         }
         else if(condition instanceof AssetCondition) {
            ((AssetCondition) condition).renameDepended(oname, nname, ws);
         }
      }
   }

   /**
    * Update the conditon list wrapper.
    *
    * @param conds the specified condition list wrapper.
    * @param ws    the associated worksheet.
    * @return the updated condition list wrapper, <tt>null</tt> if exception
    *         occurs.
    */
   public static ConditionListWrapper updateConditionListWrapper(
      ConditionListWrapper conds, Worksheet ws)
   {
      if(conds == null) {
         return conds;
      }

      if(conds instanceof ConditionAssembly) {
         ConditionAssembly assembly = (ConditionAssembly) conds;
         String name = assembly.getName();
         assembly = ws == null ?
            null :
            (ConditionAssembly) ws.getAssembly(name);

         if(assembly == null) {
            LOG.error("Condition assembly not found: " + name);
            return null;
         }

         assembly.update();
         conds = assembly;
      }

      int size = conds.getConditionSize();

      for(int i = 0; i < size; i += 2) {
         ConditionItem item = conds.getConditionItem(i);
         XCondition condition = item.getXCondition();

         if(condition instanceof AssetCondition) {
            AssetCondition acondition = (AssetCondition) condition;

            if(!acondition.update(ws)) {
               return null;
            }
         }
         else if(condition instanceof DateRangeAssembly) {
            DateRangeAssembly assembly = (DateRangeAssembly) condition;
            String name = assembly.getName();
            assembly = ws == null ?
               null :
               (DateRangeAssembly) ws.getAssembly(name);

            if(assembly == null) {
               LOG.error("Date range assembly not found: " + name);
               return null;
            }

            assembly.update();
            assembly = (DateRangeAssembly) assembly.clone();
            assembly.setNegated(condition.isNegated());
            assembly.setType(condition.getType());
            assembly.setEqual(condition.isEqual());
            assembly.setOperation(condition.getOperation());
            item.setXCondition(assembly);
         }
      }

      return conds;
   }

   /**
    * Print the key to identify this content object. If the keys of two content
    * objects are equal, the content objects are equal too.
    */
   public static void printConditionsKey(ConditionListWrapper wrapper,
                                         PrintWriter writer)
         throws Exception {
      if(wrapper == null) {
         return;
      }

      ConditionList conds = wrapper.getConditionList();

      if(conds == null) {
         return;
      }

      writer.print("CONDS[");
      int cnt = conds.getSize();
      writer.print(cnt);

      for(int i = 0; i < cnt; i++) {
         HierarchyItem item = conds.getItem(i);
         writer.print(",");

         if(item instanceof ConditionItem) {
            printConditionKey((ConditionItem) item, writer);
         }
         else {
            printJunctionKey((JunctionOperator) item, writer);
         }
      }

      writer.print("]");
   }

   /**
    * Check if two condition list wrappers are equal in content.
    *
    * @param wrapper1 the specified condition list wrapper 1.
    * @param wrapper2 the specified condition list wrapper 2.
    * @return <tt>true</tt> if they are equal in content, <tt>false</tt>
    *         otherwise.
    */
   public static boolean equalsConditionListWrapper(
      ConditionListWrapper wrapper1, ConditionListWrapper wrapper2) {
      if(wrapper1 == null || wrapper2 == null) {
         if(wrapper1 != null && wrapper1.getConditionSize() == 0) {
            return true;
         }
         else if(wrapper2 != null && wrapper2.getConditionSize() == 0) {
            return true;
         }

         return wrapper1 == wrapper2;
      }

      ConditionList conds1 = wrapper1.getConditionList();
      ConditionList conds2 = wrapper2.getConditionList();

      if(conds1.getSize() != conds2.getSize()) {
         return false;
      }

      for(int i = 0; i < conds1.getSize(); i++) {
         HierarchyItem item1 = conds1.getItem(i);
         HierarchyItem item2 = conds2.getItem(i);

         if(item1 instanceof ConditionItem) {
            if(!equalsConditionItem((ConditionItem) item1,
                                    (ConditionItem) item2)) {
               return false;
            }
         }
         else {
            if(!item1.equals(item2)) {
               return false;
            }
         }
      }

      return true;
   }

   /**
    * Print the key to identify this content object. If the keys of two content
    * objects are equal, the content objects are equal too.
    */
   public static void printConditionKey(ConditionItem citem, PrintWriter writer)
         throws Exception {
      writer.print("CITEM[");
      DataRef ref = citem.getAttribute();
      printDataRefKey(ref, writer);
      XCondition cond = citem.getXCondition();

      if(cond != null) {
         cond.printKey(writer);
      }

      int level = citem.getLevel();
      writer.print(",");
      writer.print(level);
      writer.print("]");
   }

   /**
    * Print the key to identify this content object. If the keys of two content
    * objects are equal, the content objects are equal too.
    */
   public static void printJunctionKey(JunctionOperator op, PrintWriter writer) {
      writer.print("JUN[");
      int jun = op.getJunction();
      writer.print(jun);
      int level = op.getLevel();
      writer.print(",");
      writer.print(level);
      writer.print("]");
   }

   /**
    * Check if two condition item.
    *
    * @param citem1 the specified condition item 1.
    * @param citem2 the specified condition item 2.
    * @return <tt>true</tt> if they are equal in content, <tt>false</tt>
    *         otherwise.
    */
   public static boolean equalsConditionItem(ConditionItem citem1,
                                             ConditionItem citem2) {
      DataRef ref1 = citem1.getAttribute();
      DataRef ref2 = citem2.getAttribute();

      if(!equalsDataRef(ref1, ref2)) {
         return false;
      }

      XCondition cond1 = citem1.getXCondition();
      XCondition cond2 = citem2.getXCondition();

      if(!cond1.equals(cond2)) {
         return false;
      }

      int level1 = citem1.getLevel();
      int level2 = citem2.getLevel();

      if(level1 != level2) {
         return false;
      }

      return true;
   }

   /**
    * Print the key to identify this content object. If the keys of two content
    * objects are equal, the content objects are equal too.
    */
   public static void printDataRefKey(DataRef ref, PrintWriter writer) {
      printDataRefKey(ref, writer, false);
   }

   public static void printDataRefKey(DataRef ref, PrintWriter writer, boolean fullyQualify) {
      if(ref == null) {
         return;
      }

      boolean col = ref instanceof ColumnRef;

      if(!col) {
         String cname = ref.getClass().getName();
         int idx = cname.lastIndexOf(".");
         cname = idx >= 0 ? cname.substring(idx + 1) : cname;
         writer.print(cname);
      }

      String name;

      if(fullyQualify && ref.getEntity() != null && !ref.getEntity().isEmpty()) {
         name = ref.getEntity() + "." + ref.getAttribute();
      }
      else {
         name = ref.getAttribute();
      }

      if(col) {
         ColumnRef cref = (ColumnRef) ref;

         if(!cref.isVisible()) {
            writer.print("F");
         }

         if(!cref.isSQL()) {
            writer.print("F");
         }

         if(cref.isHiddenParameter()) {
            writer.print("T");
         }

         if(cref.isApplyingAlias() && cref.getAlias() != null) {
            name += "[" + cref.getAlias() + "]";
         }
      }

      writer.print(name);
      String dtype = ref.getDataType();

      if(ref instanceof DataRefWrapper) {
         ref = ((DataRefWrapper) ref).getDataRef();

         if(!(ref instanceof ColumnRef)) {
            String cname = ref.getClass().getName();
            int idx = cname.lastIndexOf(".");
            cname = idx >= 0 ? cname.substring(idx + 1) : cname;
            writer.print(cname);
         }
      }

      if(ref instanceof ExpressionRef) {
         if(!((ExpressionRef) ref).isSQL()) {
            writer.print("F");
         }

         String exp = ((ExpressionRef) ref).getScriptExpression();
         writer.print(exp.hashCode());

         if(dtype != null) {
            writer.print(dtype);
         }
      }

      if(ref instanceof AliasDataRef) {
         printDataRefKey(((AliasDataRef) ref).getDataRef(), writer, fullyQualify);
      }
   }

   /**
    * Check if two data refs are equal in content.
    *
    * @param ref1 the specified data ref 1.
    * @param ref2 the specified data ref 2.
    * @return <tt>true</tt> if they are equal in content, <tt>false</tt>
    *         otherwise.
    */
   public static boolean equalsDataRef(DataRef ref1, DataRef ref2) {
      if(ref1 == null || ref2 == null) {
         return ref1 == ref2;
      }

      if(!ref1.getClass().equals(ref2.getClass())) {
         return false;
      }

      String name1 = ref1.getAttribute();
      String name2 = ref2.getAttribute();

      if(ref1 instanceof ColumnRef) {
         if(((ColumnRef) ref1).isVisible() != ((ColumnRef) ref2).isVisible()) {
            return false;
         }

         name1 = ((ColumnRef) ref1).getName();
         name2 = ((ColumnRef) ref2).getName();
      }

      if(!name1.equals(name2)) {
         return false;
      }

      if(ref1 instanceof DataRefWrapper) {
         ref1 = ((DataRefWrapper) ref1).getDataRef();
      }

      if(ref2 instanceof DataRefWrapper) {
         ref2 = ((DataRefWrapper) ref2).getDataRef();
      }

      if(!ref1.getClass().equals(ref2.getClass())) {
         return false;
      }

      if(ref1 instanceof ExpressionRef) {
         if(((ExpressionRef) ref1).isSQL() != ((ExpressionRef) ref2).isSQL()) {
            return false;
         }

         String exp1 = ((ExpressionRef) ref1).getScriptExpression();
         String exp2 = ((ExpressionRef) ref2).getScriptExpression();

         if(!Tool.equals(exp1, exp2)) {
            return false;
         }
      }

      return true;
   }

   /**
    * Separate the condition list.
    *
    * @param conds the merged condition list.
    * @param op    a junction operation (constants in JunctionOperator).
    * @return the list stores condition lists.
    */
   public static List<ConditionList> separateConditionList(ConditionList conds,
                                                           int op,
                                                           boolean force) {
      List<ConditionList> list = new ArrayList();

      if(conds == null || conds.getSize() == 0) {
         return list;
      }

      ConditionList conds0 = (ConditionList) conds.clone();
      ConditionList clist = new ConditionList();
      boolean composite = false;

      for(int i = 0; i < conds0.getSize(); i++) {
         HierarchyItem item = conds0.getItem(i);

         if(conds0.isJunctionOperator(i) &&
            conds0.getJunction(i) == op && item.getLevel() == 0) {
            if(clist.getSize() > 0) {
               if(clist.getSize() > 1) {
                  composite = true;

                  for(int j = 0; j < clist.getSize(); j++) {
                     HierarchyItem item0 = clist.getItem(j);

                     if(item0.getLevel() > 0) {
                        item0.setLevel(item0.getLevel() - 1);
                     }
                  }
               }

               if(!list.contains(clist)) {
                  list.add(clist);
               }

               clist = new ConditionList();
               continue;
            }
         }

         clist.append(item);
      }

      // add in last condition list
      if(clist.getSize() > 0) {
         if(clist.getSize() > 1) {
            composite = true;

            for(int j = 0; j < clist.getSize(); j++) {
               HierarchyItem item0 = clist.getItem(j);

               if(item0.getLevel() > 0) {
                  item0.setLevel(item0.getLevel() - 1);
               }
            }
         }

         if(!list.contains(clist)) {
            list.add(clist);
         }
      }

      if(!force && !composite) {
         list.clear();
         list.add(conds);
      }

      return list;
   }

   /**
    * Merge the condition list.
    *
    * @param conds the list stores condition lists.
    * @param op   a junction operation (constants in JunctionOperator).
    * @return the merged condition list.
    */
   public static ConditionList mergeConditionList(List conds, int op) {
      ConditionList list = new ConditionList();
      boolean composite = false;

      for(int i = 0; i < conds.size(); i++) {
         ConditionList alist = (ConditionList) conds.get(i);

         if(alist == null || alist.getSize() == 0) {
            continue;
         }

         if(list.getSize() != 0) {
            list.append(new JunctionOperator(op, 0));
            composite = true;
         }

         for(int j = 0; j < alist.getSize(); j++) {
            HierarchyItem item = alist.getItem(j);

            if(alist.getSize() > 1) {
               item.setLevel(item.getLevel() + 1);
            }

            list.append(item);
         }
      }

      if(list.getSize() == 0) {
         list = null;
      }
      else if(!composite) {
         for(int i = 0; i < list.getSize(); i++) {
            HierarchyItem item = list.getItem(i);
            int level = Math.max(0, item.getLevel() - 1);
            item.setLevel(level);
         }
      }

      return list;
   }

   private static final Logger LOG = LoggerFactory.getLogger(ConditionUtil.class);

    /**
     * Merge all drill action condition of {@link inetsoft.uql.viewsheet.DrillFilterVSAssembly}.
     * @param vs viewsheet
     * @return Merged Condition. <tt>null</tt> if size is 0.
     */
    public static ConditionList getMergedDrillFilterCondition(Viewsheet vs, String source) {
       assert vs != null;
       Assembly[] assemblies = vs.getAssemblies();

       List<ConditionList> conditionLists = Arrays.stream(assemblies)
          .filter(assembly -> assembly instanceof DrillFilterVSAssembly
             && ((DrillFilterVSAssembly) assembly).hasDrillFilter()
             && Objects.equals(getAssemblySource(assembly), source))
          .map(assembly -> ((DrillFilterVSAssembly) assembly).getAllDrillFilterConditions())
          .collect(Collectors.toList());

       return mergeConditionList(conditionLists, JunctionOperator.AND);
    }

   public static String getAssemblySource(Assembly assembly) {
      String source = null;

      if(assembly instanceof DataVSAssembly) {
         DataVSAssemblyInfo info =  (DataVSAssemblyInfo) ((DataVSAssembly) assembly).getVSAssemblyInfo();
         SourceInfo sinfo = info.getSourceInfo();
         source = sinfo == null ? null : sinfo.getSource();
      }
      else if(assembly instanceof OutputVSAssembly) {
         ScalarBindingInfo binfo = ((OutputVSAssembly) assembly).getScalarBindingInfo();
         source = binfo == null ? null : binfo.getTableName();
      }

      return source;
   }

   /**
    * Check condition is empty.
    * @param condition ConditionList
    * @return <tt>true</tt> condition is null or empty.
    */
   public static boolean isEmpty(ConditionList condition) {
      return condition == null || condition.isEmpty();
   }
}
