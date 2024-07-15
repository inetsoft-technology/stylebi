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
package inetsoft.uql.viewsheet.internal;

import inetsoft.uql.*;
import inetsoft.uql.erm.DataRef;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Creates condition lists from a RangeCondition.
 */
class RangeConditionListCreator {
   RangeConditionListCreator(RangeCondition range) {
      this.rangeCond = range;

      this.nullable = rangeCond.isNullable() &&
         Stream.concat(Arrays.stream(rangeCond.getMins()),
                       Arrays.stream(rangeCond.getMaxes()))
            .anyMatch(this::isNullValue);
   }

   ConditionList createConditionList() {
      if(mins().length == 0) {
         return new ConditionList();
      }

      ConditionTreeNode root = andJunction(createMinimumConditionTree(0),
                                           createMaximumConditionTree(0));
      root = simplifyTree(root);
      return traverseTreeToGenerateConditionList(root);
   }

   private ConditionTreeNode createMinimumConditionTree(int index) {
      final Object[] mins = mins();
      final DataRef[] refs = refs();
      final Object min = mins[index];
      final DataRef ref = refs[index];
      final ConditionTreeNode node;

      if(index == mins.length - 1) {
         if(isNullValue(min)) {
            if(lowerInclusive()) {
               node = BooleanNode.TRUE;
            }
            else {
               node = isNotNull(ref);
            }
         }
         else {
            final Condition condition = new Condition(ref.getDataType());
            condition.setOperation(XCondition.GREATER_THAN);
            condition.setEqual(lowerInclusive());
            condition.addValue(min);
            node = new ConditionNode(ref, condition);
         }
      }
      else {
         final ConditionTreeNode leftBranch;
         final ConditionTreeNode rightBranch;

         if(isNullValue(min)) {
            leftBranch = isNotNull(ref);
            rightBranch = andJunction(isNull(ref), createMinimumConditionTree(index + 1));
         }
         else {
            final Condition greaterThanCondition = new Condition(ref.getDataType());
            greaterThanCondition.setOperation(XCondition.GREATER_THAN);
            greaterThanCondition.setEqual(false);
            greaterThanCondition.addValue(min);
            leftBranch = new ConditionNode(ref, greaterThanCondition);

            final Condition equalCondition = new Condition(ref.getDataType());
            equalCondition.setEqual(true);
            equalCondition.addValue(min);
            final ConditionNode equalNode = new ConditionNode(ref, equalCondition);
            rightBranch = andJunction(equalNode, createMinimumConditionTree(index + 1));
         }

         node = orJunction(leftBranch, rightBranch);
      }

      return node;
   }

   private ConditionTreeNode createMaximumConditionTree(int index) {
      final Object[] maxes = maxes();
      final DataRef[] refs = refs();
      final Object max = maxes[index];
      final DataRef ref = refs[index];
      final ConditionTreeNode node;

      if(index == maxes.length - 1) {
         if(isNullValue(max)) {
            if(upperInclusive()) {
               node = isNull(ref);
            }
            else {
               node = BooleanNode.FALSE;
            }
         }
         else {
            final Condition condition = new Condition(ref.getDataType());
            condition.setOperation(XCondition.LESS_THAN);
            condition.setEqual(upperInclusive());
            condition.addValue(max);
            final ConditionNode lessThan = new ConditionNode(ref, condition);

            if(nullable()) {
               node = orJunction(isNull(ref), lessThan);
            }
            else {
               node = lessThan;
            }
         }
      }
      else {
         if(isNullValue(max)) {
            node = andJunction(isNull(ref), createMaximumConditionTree(index + 1));
         }
         else {
            final Condition lessThanCondition = new Condition(ref.getDataType());
            lessThanCondition.setOperation(XCondition.LESS_THAN);
            lessThanCondition.setEqual(false);
            lessThanCondition.addValue(max);
            final ConditionNode lessThan = new ConditionNode(ref, lessThanCondition);
            final ConditionTreeNode leftBranch;

            if(nullable()) {
               leftBranch = orJunction(isNull(ref), lessThan);
            }
            else {
               leftBranch = lessThan;
            }

            final Condition equalCondition = new Condition(ref.getDataType());
            equalCondition.setEqual(true);
            equalCondition.addValue(max);
            final ConditionNode equalNode = new ConditionNode(ref, equalCondition);
            final ConditionTreeNode rightBranch =
               andJunction(equalNode, createMaximumConditionTree(index + 1));

            node = orJunction(leftBranch, rightBranch);
         }
      }

      return node;
   }

   /**
    * Simplify the tree by resolving the boolean nodes of the tree.
    *
    * @param node the tree to simplify
    * @return the simplified tree.
    */
   private ConditionTreeNode simplifyTree(ConditionTreeNode node) {
      ConditionTreeNode simplifiedNode;

      if(node instanceof JunctionNode) {
         final JunctionNode junctionNode = (JunctionNode) node;
         final ConditionTreeNode leftNode = simplifyTree(junctionNode.leftBranch);
         final ConditionTreeNode rightNode = simplifyTree(junctionNode.rightBranch);
         final int op = junctionNode.junction.getJunction();

         final boolean allFalse = leftNode == BooleanNode.FALSE && rightNode == BooleanNode.FALSE;
         final boolean oneFalse = leftNode == BooleanNode.FALSE || rightNode == BooleanNode.FALSE;
         final boolean allTrue = leftNode == BooleanNode.TRUE && rightNode == BooleanNode.TRUE;
         final boolean oneTrue = leftNode == BooleanNode.TRUE || rightNode == BooleanNode.TRUE;

         if(op == JunctionOperator.OR) {
            if(allFalse) {
               simplifiedNode = BooleanNode.FALSE;
            }
            else if(allTrue || oneTrue) {
               simplifiedNode = BooleanNode.TRUE;
            }
            else if(oneFalse) {
               simplifiedNode = leftNode == BooleanNode.FALSE ? rightNode : leftNode;
            }
            else {
               simplifiedNode = orJunction(leftNode, rightNode);
            }
         }
         else if(op == JunctionOperator.AND) {
            if(allFalse || oneFalse) {
               simplifiedNode = BooleanNode.FALSE;
            }
            else if(allTrue) {
               simplifiedNode = BooleanNode.TRUE;
            }
            else if(oneTrue) {
               simplifiedNode = leftNode == BooleanNode.TRUE ? rightNode : leftNode;
            }
            else {
               simplifiedNode = andJunction(leftNode, rightNode);
            }
         }
         else {
            throw new IllegalStateException("Unexpected junction operator: " + op);
         }
      }
      else {
         simplifiedNode = node;
      }

      return simplifiedNode;
   }

   private ConditionList traverseTreeToGenerateConditionList(ConditionTreeNode node) {
      final ConditionList conditionList;

      if(node instanceof JunctionNode) {
         final JunctionNode junctionNode = (JunctionNode) node;
         final ArrayList<ConditionList> conds = new ArrayList<>(2);
         conds.add(traverseTreeToGenerateConditionList(junctionNode.leftBranch));
         conds.add(traverseTreeToGenerateConditionList(junctionNode.rightBranch));

         conditionList = VSUtil.mergeConditionList(conds, junctionNode.junction.getJunction());
      }
      else if(node instanceof ConditionNode) {
         conditionList = new ConditionList();
         conditionList.append(((ConditionNode) node).condition);
      }
      else {
         throw new IllegalStateException("ConditionTree is not well-formed");
      }

      return conditionList;
   }

   private JunctionNode andJunction(ConditionTreeNode leftBranch, ConditionTreeNode rightBranch) {
      final JunctionOperator andJunction = new JunctionOperator(JunctionOperator.AND, 0);
      return new JunctionNode(andJunction, leftBranch, rightBranch);
   }

   private JunctionNode orJunction(ConditionTreeNode leftBranch, ConditionTreeNode rightBranch) {
      final JunctionOperator orJunction = new JunctionOperator(JunctionOperator.OR, 0);
      return new JunctionNode(orJunction, leftBranch, rightBranch);
   }

   private ConditionNode isNull(DataRef ref) {
      final Condition condition = new Condition(ref.getDataType());
      condition.setOperation(XCondition.NULL);
      return new ConditionNode(ref, condition);
   }

   private ConditionNode isNotNull(DataRef ref) {
      final Condition condition = new Condition(ref.getDataType());
      condition.setOperation(XCondition.NULL);
      condition.setNegated(true);
      return new ConditionNode(ref, condition);
   }

   private boolean isNullValue(Object value) {
      return value == null || "".equals(value) || "__null__".equals(value);
   }

   private Object[] mins() {
      return rangeCond.getMins();
   }

   private Object[] maxes() {
      return rangeCond.getMaxes();
   }

   private DataRef[] refs() {
      return rangeCond.getRefs();
   }

   private boolean lowerInclusive() {
      return rangeCond.isLowerInclusive();
   }

   private boolean upperInclusive() {
      return rangeCond.isUpperInclusive();
   }

   private boolean nullable() {
      return nullable;
   }

   private interface ConditionTreeNode {
   }

   private static class JunctionNode implements ConditionTreeNode {
      JunctionNode(JunctionOperator junction,
                   ConditionTreeNode leftBranch,
                   ConditionTreeNode rightBranch)
      {
         this.junction = junction;
         this.leftBranch = leftBranch;
         this.rightBranch = rightBranch;
      }

      final JunctionOperator junction;
      final ConditionTreeNode leftBranch;
      final ConditionTreeNode rightBranch;
   }

   private static class ConditionNode implements ConditionTreeNode {
      ConditionNode(DataRef ref, Condition condition) {
         this.condition = new ConditionItem(ref, condition, 0);
      }

      final ConditionItem condition;
   }

   private enum BooleanNode implements ConditionTreeNode {
      TRUE, FALSE
   }

   private final RangeCondition rangeCond;
   private final boolean nullable;
}
