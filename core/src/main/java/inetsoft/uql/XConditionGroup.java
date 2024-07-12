/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql;

import inetsoft.report.filter.DCMergeCell;
import inetsoft.report.filter.DCMergeDatePartFilter.MergePartCell;
import inetsoft.uql.viewsheet.XDimensionRef;
import inetsoft.util.Queue;
import inetsoft.util.Tool;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.*;

/**
 * A ConditionGroup stores a list of conditions to be applied to
 * the resulting data.
 *
 * @author InetSoft Technology
 * @since 10.3
 */
public class XConditionGroup implements Cloneable, Serializable {
   /**
    * Flag indicating that a given condition should be or'ed to the associated
    * column.
    */
   public static final int OR = JunctionOperator.OR;
   /**
    * Flag indicating that a given condition should be and'ed to the associated
    * column.
    */
   public static final int AND = JunctionOperator.AND;

   /**
    * Set the option to return <tt>true</tt> or <tt>false</tt> when the
    * column of a condition isn't found or value isn't available.
    */
   public void setNotFoundResult(boolean result) {
      notFoundResult = result;
   }

   /**
    * Get the option to return <tt>true</tt> or <tt>false</tt> when the
    * column of a condition isn't found or value isn't available.
    */
   public boolean getNotFoundResult() {
      return notFoundResult;
   }

   /**
    * Associate a filter condition with the specified column.
    * @param col this column number.
    * @param subCol date comparison merge cell sub col name.
    * @param condition the condition to add
    * @param level the level of the condition will be added
    */
   public void addCondition(int col, String subCol, XCondition condition, int level) {
      if(list.size() == 0 || (list.get(list.size() - 1) instanceof Operator)) {
         list.add(new CondItem(col, condition, level, subCol));
      }
   }

   /**
    * Associate a filter condition with the specified column.
    * @param col this column number.
    * @param condition the condition to add
    * @param level the level of the condition will be added
    */
   public void addCondition(int col, XCondition condition, int level) {
      addCondition(col, null, condition, level);
   }

   /**
    * Associate a junction with the condition around this junction.
    * @param junction the junction to use with the condition.
    * @param level the level of the junction will be added
    */
   public void addOperator(int junction, int level) {
      if(list.size() > 0 && (list.get(size() - 1) instanceof CondItem)) {
         Operator cp = new Operator(junction, level);

         list.add(cp);
      }
   }

   /**
    * Clear all conditions from this selection.
    */
   public void clear() {
      list.clear();
   }

   /**
    * Get the size of the condition group.
    */
   public int size() {
      return list.size();
   }

   /**
    * Return the item at the specified index.
    */
   public Object getItem(int idx) {
      return list.get(idx);
   }

   /**
    * Evaluate the condition group with a give object array.
    * @param values the object array used for evaluation.
    */
   public boolean evaluate(Object values) {
      // scala Array[Any] calls evaluate(Object) instead of
      // ConditionGroup.evalute(Object[]), so we need to map it to array
      if(values instanceof Object[]) {
         return evaluate0((Object[]) values);
      }
      else {
         return evaluate0(new Object[] {values});
      }
   }

   /**
    * Evaluate the condition group with a given object array.
    * @param values the object array used for evaluation.
    */
   protected boolean evaluate0(Object[] values) {
      // when no condition defined, we should return true
      if(list.size() == 0) {
         return true;
      }

      return calculate(new inetsoft.util.Queue<>(list), values);
   }

   /**
    * Calculate the condition group.
    */
   protected boolean calculate(inetsoft.util.Queue<HierarchyItem> queue, Object[] values) {
      Stack<HierarchyItem> stack = new Stack<>();
      HierarchyItem iqueue;

      while((iqueue = queue.peek()) != null) {
         if(stack.isEmpty()) {
            stack.push(queue.dequeue());
            continue;
         }

         HierarchyItem istack = stack.peek();
         int stacklevel = istack.getLevel();
         int queuelevel = iqueue.getLevel();

         if(queuelevel > stacklevel) {
            stack.push(queue.dequeue());
         }
         else if(queuelevel == stacklevel) {
            if(iqueue instanceof Operator) {
               stack.push(queue.dequeue());
            }
            else {
               Operator op = (Operator) istack;

               if(op.operator == AND) {
                  calcQueueStack(queue, stack, values);
               }
               else {
                  stack.push(queue.dequeue());
               }
            }
         }
         else {
            calcStack(queue, stack, values);
         }
      }

      while(stack.size() > 2) {
         calcStackStack(queue, stack, values);
      }

      return stack.isEmpty() || ((BooleanValue) stack.pop()).getBooleanValue(values, notFoundResult);
   }

   /**
    * Calculate conditions from queue and stack and put to queue.
    */
   protected void calcQueueStack(inetsoft.util.Queue<HierarchyItem> queue, Stack<HierarchyItem> stack, Object[] values) {
      BooleanValue i2 = (BooleanValue) queue.dequeue();
      Operator op = (Operator) stack.pop();
      BooleanValue i1 = (BooleanValue) stack.pop();
      BooleanItem result = calc(i1, op, i2, queue, stack, values);

      queue.add(0, result);
   }

   /**
    * Check the result item level.
    */
   protected int checkLevel(int original, inetsoft.util.Queue<HierarchyItem> queue, Stack<HierarchyItem> stack) {
      int lvl1 = queue.peek() == null ? -1 : queue.peek().getLevel();
      int lvl2 = stack.isEmpty() ? -1 : stack.peek().getLevel();
      int lvl = Math.max(lvl1, lvl2);

      return (lvl == -1) ? original : lvl;
   }

   /**
    * Calculate conditions from stack and put to queue.
    */
   protected void calcStack(inetsoft.util.Queue<HierarchyItem> queue, Stack<HierarchyItem> stack, Object[] values) {
      BooleanValue i2 = (BooleanValue) stack.pop();
      Operator op = (Operator) stack.pop();
      BooleanValue i1 = (BooleanValue) stack.pop();
      BooleanItem result = calc(i1, op, i2, queue, stack, values);

      queue.add(0, result);
   }

   /**
    * Calculate conditions from stack and put back to stack.
    */
   protected void calcStackStack(Queue<HierarchyItem> queue, Stack<HierarchyItem> stack, Object[] values) {
      BooleanValue i1 = (BooleanValue) stack.pop();
      Operator op = (Operator) stack.pop();
      BooleanValue i2 = (BooleanValue) stack.pop();
      BooleanItem result = calc(i1, op, i2, queue, stack, values);
      stack.push(result);
   }

   /**
    * Calculate two boolean values.
    */
   protected BooleanItem calc(BooleanValue i1, Operator op, BooleanValue i2,
                              Queue<HierarchyItem> queue, Stack<HierarchyItem> stack, Object[] values)
   {
      final int lvl = checkLevel(op.getLevel(), queue, stack);
      boolean v;

      // optimization, avoid evaluating both if not necessary
      if(op.operator == AND) {
         v = i1.getBooleanValue(values, notFoundResult) &&
            i2.getBooleanValue(values, notFoundResult);
      }
      else {
         v = i1.getBooleanValue(values, notFoundResult) ||
            i2.getBooleanValue(values, notFoundResult);
      }

      return new BooleanItem(v, lvl);
   }

   public String toString() {
      StringBuilder str = new StringBuilder("ConditionGroup[");

      for(Object cond : list) {
         str.append("\n  ").append(cond);
      }

      str.append("]");
      return str.toString();
   }

   @Override
   public int hashCode() {
      return Objects.hash(list, notFoundResult);
   }

   /**
    * Boolean value.
    */
   protected interface BooleanValue {
      boolean getBooleanValue(Object[] values, boolean notFoundResult);
   }

   /**
    * Internal class represents a boolean item.
    */
   protected static class BooleanItem implements HierarchyItem, BooleanValue {
      public BooleanItem(boolean bool, int level) {
         this.bool = bool;
         this.level = level;
      }

      @Override
      public int getLevel() {
         return level;
      }

      @Override
      public void setLevel(int level) {
         this.level = level;
      }

      @Override
      public boolean getBooleanValue(Object[] values, boolean notFoundResult) {
         return bool;
      }

      @Override
      public BooleanItem clone() {
         return this;
      }

      public String toString() {
         return StringUtils.repeat("  ", level) + bool;
      }

      @Override
      public int hashCode() {
         return Objects.hash(level, bool);
      }

      public int level;
      public boolean bool;
   }

   /**
    * Internal class represents a condition item.
    * @hidden
    */
   public static class CondItem implements HierarchyItem, BooleanValue {
      public CondItem(int col, XCondition condition, int level) {
         this(col, condition, level, null);
      }

      public CondItem(int col, XCondition condition, int level, String subCol) {
         this.col = col;
         this.condition = condition;
         this.level = level;
         this.subCol = subCol;
      }

      public int getCol() {
         return col;
      }

      @Override
      public int getLevel() {
         return level;
      }

      @Override
      public void setLevel(int level) {
         this.level = level;
      }

      @Override
      public boolean getBooleanValue(Object[] values, boolean notFoundResult) {
         if(values == null || values.length == 0 ||
            ((col < 0 || col >= values.length) && values.length > 1))
         {
            return notFoundResult;
         }

         Object obj = values.length == 1 ? values[0] : values[col];

         if(subCol != null && obj instanceof MergePartCell) {
            MergePartCell cell = (MergePartCell) obj;

            for(int i = 0; i < cell.getMergedRefs().size(); i++) {
               XDimensionRef dimensionRef = cell.getMergedRefs().get(i);

               if(dimensionRef == null) {
                  continue;
               }

               if(Tool.equals(dimensionRef.getFullName(), subCol)) {
                  obj = cell.getValue(i);
                  break;
               }
            }
         }

         if(obj instanceof DCMergeCell) {
            return condition.evaluate(((DCMergeCell) obj).getOriginalData());
         }

         return condition.evaluate(obj);
      }

      @Override
      public CondItem clone() {
         return this;
      }

      public String toString() {
         return StringUtils.repeat("  ", level) + col + " " + condition.toString();
      }

      @Override
      public int hashCode() {
         return Objects.hash(level, condition, col);
      }

      public int level;
      public XCondition condition;
      public int col;
      public String subCol;
   }

   /**
    * Internal class represents an operator.
    * @hidden
    */
   public static class Operator implements HierarchyItem {
      public Operator(int operator, int level) {
         this.operator = operator;
         this.level = level;
      }

      @Override
      public int getLevel() {
         return level;
      }

      @Override
      public void setLevel(int level) {
         this.level = level;
      }

      @Override
      public Operator clone() {
         return this;
      }

      public String toString() {
         return StringUtils.repeat("  ", level) + " " + (operator == AND ? "&&" : "||");
      }

      @Override
      public int hashCode() {
         return Objects.hash(level, operator);
      }

      public int level;
      public int operator;
   }

   @Override
   public XConditionGroup clone() {
      try {
         XConditionGroup group = (XConditionGroup) super.clone();
         group.list = new ArrayList<>(list);
         return group;
      }
      catch(Exception ex) {
         // impossible
         return this;
      }
   }

   private List<HierarchyItem> list = new ArrayList<>();
   private boolean notFoundResult = true;
}
