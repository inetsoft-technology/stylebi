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

import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class ConditionUtilTest {
   /**
    * ref1 one of [...]
    */
   @Test
   void splitSimpleOneOfCondition() {
      List<Object> values = new ArrayList<>(Arrays.asList("val1", "val2", "val3", "val4", "val5"));
      ColumnRef ref = new ColumnRef(new AttributeRef("ref1"));
      Condition cond = new Condition();
      cond.setValues(values);
      cond.setOperation(XCondition.ONE_OF);
      ConditionItem item = new ConditionItem(ref, cond, 0);
      ConditionList conditionList = new ConditionList();
      conditionList.append(item);

      // split ONE OF conditions, limit set to 2
      conditionList = ConditionUtil.splitOneOfCondition(conditionList, 2);

      // 3 conditions and 2 ORs
      Assertions.assertEquals(5, conditionList.getSize());

      // 1st condition
      item = conditionList.getConditionItem(0);
      List<Object> actualValues = ((Condition) item.getXCondition()).getValues();
      Assertions.assertEquals(2, actualValues.size());
      Assertions.assertIterableEquals(Arrays.asList("val1", "val2"), actualValues);

      // 1st OR
      Assertions.assertEquals(JunctionOperator.OR,
                              conditionList.getJunctionOperator(1).getJunction());

      // 2nd condition
      item = conditionList.getConditionItem(2);
      actualValues = ((Condition) item.getXCondition()).getValues();
      Assertions.assertEquals(2, actualValues.size());
      Assertions.assertIterableEquals(Arrays.asList("val3", "val4"), actualValues);

      // 2nd OR
      Assertions.assertEquals(JunctionOperator.OR,
                              conditionList.getJunctionOperator(3).getJunction());

      // 3rd condition
      item = conditionList.getConditionItem(4);
      actualValues = ((Condition) item.getXCondition()).getValues();
      Assertions.assertEquals(1, actualValues.size());
      Assertions.assertIterableEquals(Arrays.asList("val5"), actualValues);
   }

   /**
    * ref1 one of [...] AND ref2 one of [...]
    */
   @Test
   void splitTwoOneOfConditions() {
      // First "ONE OF" condition with 7 values
      List<Object> values1 = new ArrayList<>(Arrays.asList("val1", "val2", "val3", "val4", "val5", "val6", "val7"));
      ColumnRef ref1 = new ColumnRef(new AttributeRef("ref1"));
      Condition cond1 = new Condition();
      cond1.setValues(values1);
      cond1.setOperation(XCondition.ONE_OF);
      ConditionItem item1 = new ConditionItem(ref1, cond1, 0);

      // Second "ONE OF" condition with 7 values
      List<Object> values2 = new ArrayList<>(Arrays.asList("val8", "val9", "val10", "val11", "val12", "val13", "val14"));
      ColumnRef ref2 = new ColumnRef(new AttributeRef("ref2"));
      Condition cond2 = new Condition();
      cond2.setValues(values2);
      cond2.setOperation(XCondition.ONE_OF);
      ConditionItem item2 = new ConditionItem(ref2, cond2, 0);

      // Create a ConditionList and append both conditions with an AND junction in between
      ConditionList conditionList = new ConditionList();
      conditionList.append(item1);
      conditionList.append(new JunctionOperator(JunctionOperator.AND, 0));  // AND between two "ONE OF" conditions
      conditionList.append(item2);

      // Split ONE OF conditions, limit set to 3
      conditionList = ConditionUtil.splitOneOfCondition(conditionList, 3);

      // First condition should be split into 3 sub-conditions, with 2 ORs in between
      // Second condition should also be split into 3 sub-conditions, with 2 ORs in between
      // Plus, there should be an AND between the two sets of conditions.
      // Expecting 6 condition items and 4 OR junctions + 1 AND junction
      Assertions.assertEquals(11, conditionList.getSize());

      // Checking the first set of conditions for the first "ONE OF"

      // 1st condition (for first set of values)
      item1 = conditionList.getConditionItem(0);
      List<Object> actualValues1 = ((Condition) item1.getXCondition()).getValues();
      Assertions.assertEquals(3, actualValues1.size());
      Assertions.assertIterableEquals(Arrays.asList("val1", "val2", "val3"), actualValues1);

      // 1st OR
      Assertions.assertEquals(JunctionOperator.OR, conditionList.getJunctionOperator(1).getJunction());

      // 2nd condition (for first set of values)
      item1 = conditionList.getConditionItem(2);
      actualValues1 = ((Condition) item1.getXCondition()).getValues();
      Assertions.assertEquals(3, actualValues1.size());
      Assertions.assertIterableEquals(Arrays.asList("val4", "val5", "val6"), actualValues1);

      // 2nd OR
      Assertions.assertEquals(JunctionOperator.OR, conditionList.getJunctionOperator(3).getJunction());

      // 3rd condition (for first set of values)
      item1 = conditionList.getConditionItem(4);
      actualValues1 = ((Condition) item1.getXCondition()).getValues();
      Assertions.assertEquals(1, actualValues1.size());
      Assertions.assertIterableEquals(Arrays.asList("val7"), actualValues1);

      // Now checking the AND junction between the two "ONE OF" conditions
      Assertions.assertEquals(JunctionOperator.AND, conditionList.getJunctionOperator(5).getJunction());

      // Now checking the second set of conditions for the second "ONE OF"

      // 1st condition (for second set of values)
      item2 = conditionList.getConditionItem(6);
      List<Object> actualValues2 = ((Condition) item2.getXCondition()).getValues();
      Assertions.assertEquals(3, actualValues2.size());
      Assertions.assertIterableEquals(Arrays.asList("val8", "val9", "val10"), actualValues2);

      // 1st OR
      Assertions.assertEquals(JunctionOperator.OR, conditionList.getJunctionOperator(7).getJunction());

      // 2nd condition (for second set of values)
      item2 = conditionList.getConditionItem(8);
      actualValues2 = ((Condition) item2.getXCondition()).getValues();
      Assertions.assertEquals(3, actualValues2.size());
      Assertions.assertIterableEquals(Arrays.asList("val11", "val12", "val13"), actualValues2);

      // 2nd OR
      Assertions.assertEquals(JunctionOperator.OR, conditionList.getJunctionOperator(9).getJunction());

      // 3rd condition (for second set of values)
      item2 = conditionList.getConditionItem(10);
      actualValues2 = ((Condition) item2.getXCondition()).getValues();
      Assertions.assertEquals(1, actualValues2.size());
      Assertions.assertIterableEquals(Arrays.asList("val14"), actualValues2);
   }

   /**
    * ref1 one of [...] AND ref2 one of [...] AND (ref3 equals a1 OR ref4 equals b1)
    */
   @Test
   void splitComplexConditionList() {
      // First "ONE OF" condition with 7 values
      List<Object> values1 = new ArrayList<>(Arrays.asList("val1", "val2", "val3", "val4", "val5", "val6", "val7"));
      ColumnRef ref1 = new ColumnRef(new AttributeRef("ref1"));
      Condition cond1 = new Condition();
      cond1.setValues(values1);
      cond1.setOperation(XCondition.ONE_OF);
      ConditionItem item1 = new ConditionItem(ref1, cond1, 0);

      // Second "ONE OF" condition with 7 values
      List<Object> values2 = new ArrayList<>(Arrays.asList("val8", "val9", "val10", "val11", "val12", "val13", "val14"));
      ColumnRef ref2 = new ColumnRef(new AttributeRef("ref2"));
      Condition cond2 = new Condition();
      cond2.setValues(values2);
      cond2.setOperation(XCondition.ONE_OF);
      ConditionItem item2 = new ConditionItem(ref2, cond2, 0);

      // Third condition (level 1)
      ColumnRef ref3 = new ColumnRef(new AttributeRef("ref3"));
      Condition cond3 = new Condition();
      cond3.addValue("a1");
      cond3.setOperation(XCondition.EQUAL_TO);
      ConditionItem item3 = new ConditionItem(ref3, cond3, 1);

      // Fourth condition (level 1)
      ColumnRef ref4 = new ColumnRef(new AttributeRef("ref4"));
      Condition cond4 = new Condition();
      cond4.addValue("b1");
      cond4.setOperation(XCondition.EQUAL_TO);
      ConditionItem item4 = new ConditionItem(ref4, cond4, 1);

      // Create a ConditionList and append conditions with junctions
      ConditionList conditionList = new ConditionList();
      conditionList.append(item1);
      conditionList.append(new JunctionOperator(JunctionOperator.AND, 0));
      conditionList.append(item2);
      conditionList.append(new JunctionOperator(JunctionOperator.AND, 0));
      conditionList.append(item3);
      conditionList.append(new JunctionOperator(JunctionOperator.OR, 1));
      conditionList.append(item4);

      // Split ONE OF conditions, limit set to 3
      conditionList = ConditionUtil.splitOneOfCondition(conditionList, 3);

      // After splitting, the first and second "ONE OF" conditions will be divided into sub-conditions.
      // Each will produce 3 sub-conditions, with 2 OR junctions in between for each "ONE OF".
      Assertions.assertEquals(15, conditionList.getSize());

      // Validate the first "ONE OF" condition split

      // 1st condition (part of the first "ONE OF")
      item1 = conditionList.getConditionItem(0);
      List<Object> actualValues1 = ((Condition) item1.getXCondition()).getValues();
      Assertions.assertEquals(3, actualValues1.size());
      Assertions.assertIterableEquals(Arrays.asList("val1", "val2", "val3"), actualValues1);
      Assertions.assertEquals(1, item1.getLevel());

      // 1st OR
      Assertions.assertEquals(JunctionOperator.OR, conditionList.getJunctionOperator(1).getJunction());
      Assertions.assertEquals(1, conditionList.getJunctionOperator(1).getLevel());

      // 2nd condition (part of the first "ONE OF")
      item1 = conditionList.getConditionItem(2);
      actualValues1 = ((Condition) item1.getXCondition()).getValues();
      Assertions.assertEquals(3, actualValues1.size());
      Assertions.assertIterableEquals(Arrays.asList("val4", "val5", "val6"), actualValues1);
      Assertions.assertEquals(1, item1.getLevel());

      // 2nd OR
      Assertions.assertEquals(JunctionOperator.OR, conditionList.getJunctionOperator(3).getJunction());
      Assertions.assertEquals(1, conditionList.getJunctionOperator(3).getLevel());

      // 3rd condition (part of the first "ONE OF")
      item1 = conditionList.getConditionItem(4);
      actualValues1 = ((Condition) item1.getXCondition()).getValues();
      Assertions.assertEquals(1, actualValues1.size());
      Assertions.assertIterableEquals(Arrays.asList("val7"), actualValues1);
      Assertions.assertEquals(1, item1.getLevel());

      // Validate the AND junction between the first and second "ONE OF"
      Assertions.assertEquals(JunctionOperator.AND, conditionList.getJunctionOperator(5).getJunction());
      Assertions.assertEquals(0, conditionList.getJunctionOperator(5).getLevel());

      // Validate the second "ONE OF" condition split

      // 1st condition (part of the second "ONE OF")
      item2 = conditionList.getConditionItem(6);
      List<Object> actualValues2 = ((Condition) item2.getXCondition()).getValues();
      Assertions.assertEquals(3, actualValues2.size());
      Assertions.assertIterableEquals(Arrays.asList("val8", "val9", "val10"), actualValues2);
      Assertions.assertEquals(1, item2.getLevel());

      // 1st OR
      Assertions.assertEquals(JunctionOperator.OR, conditionList.getJunctionOperator(7).getJunction());
      Assertions.assertEquals(1, conditionList.getJunctionOperator(7).getLevel());

      // 2nd condition (part of the second "ONE OF")
      item2 = conditionList.getConditionItem(8);
      actualValues2 = ((Condition) item2.getXCondition()).getValues();
      Assertions.assertEquals(3, actualValues2.size());
      Assertions.assertIterableEquals(Arrays.asList("val11", "val12", "val13"), actualValues2);
      Assertions.assertEquals(1, item2.getLevel());

      // 2nd OR
      Assertions.assertEquals(JunctionOperator.OR, conditionList.getJunctionOperator(9).getJunction());
      Assertions.assertEquals(1, conditionList.getJunctionOperator(9).getLevel());

      // 3rd condition (part of the second "ONE OF")
      item2 = conditionList.getConditionItem(10);
      actualValues2 = ((Condition) item2.getXCondition()).getValues();
      Assertions.assertEquals(1, actualValues2.size());
      Assertions.assertIterableEquals(Arrays.asList("val14"), actualValues2);
      Assertions.assertEquals(1, item2.getLevel());

      // Validate the AND between the second "ONE OF" and the third condition
      Assertions.assertEquals(JunctionOperator.AND, conditionList.getJunctionOperator(11).getJunction());
      Assertions.assertEquals(0, conditionList.getJunctionOperator(11).getLevel());

      // Validate the third condition (EQUAL TO "a1")
      item3 = conditionList.getConditionItem(12);
      Assertions.assertEquals("a1", ((Condition) item3.getXCondition()).getValue(0));
      Assertions.assertEquals(1, item3.getLevel());

      // Validate the OR between the third and fourth conditions
      Assertions.assertEquals(JunctionOperator.OR, conditionList.getJunctionOperator(13).getJunction());
      Assertions.assertEquals(1, conditionList.getJunctionOperator(13).getLevel());

      // Validate the fourth condition (EQUAL TO "b1")
      item4 = conditionList.getConditionItem(14);
      Assertions.assertEquals("b1", ((Condition) item4.getXCondition()).getValue(0));
      Assertions.assertEquals(1, item4.getLevel());
   }

   /**
    * ref1 one of [...] AND ref2 one of [...] AND (ref3 equals a1 OR ref4 one of [...])
    */
   @Test
   void splitComplexConditionListWithOneOfRef4() {
      // First "ONE OF" condition with 7 values
      List<Object> values1 = new ArrayList<>(Arrays.asList("val1", "val2", "val3", "val4", "val5", "val6", "val7"));
      ColumnRef ref1 = new ColumnRef(new AttributeRef("ref1"));
      Condition cond1 = new Condition();
      cond1.setValues(values1);
      cond1.setOperation(XCondition.ONE_OF);
      ConditionItem item1 = new ConditionItem(ref1, cond1, 0);

      // Second "ONE OF" condition with 7 values
      List<Object> values2 = new ArrayList<>(Arrays.asList("val8", "val9", "val10", "val11", "val12", "val13", "val14"));
      ColumnRef ref2 = new ColumnRef(new AttributeRef("ref2"));
      Condition cond2 = new Condition();
      cond2.setValues(values2);
      cond2.setOperation(XCondition.ONE_OF);
      ConditionItem item2 = new ConditionItem(ref2, cond2, 0);

      // Third condition (EQUAL TO for ref3)
      ColumnRef ref3 = new ColumnRef(new AttributeRef("ref3"));
      Condition cond3 = new Condition();
      cond3.addValue("a1");
      cond3.setOperation(XCondition.EQUAL_TO);
      ConditionItem item3 = new ConditionItem(ref3, cond3, 1);

      // Fourth "ONE OF" condition for ref4 with 5 values
      List<Object> values4 = new ArrayList<>(Arrays.asList("val15", "val16", "val17", "val18", "val19"));
      ColumnRef ref4 = new ColumnRef(new AttributeRef("ref4"));
      Condition cond4 = new Condition();
      cond4.setValues(values4);
      cond4.setOperation(XCondition.ONE_OF);
      ConditionItem item4 = new ConditionItem(ref4, cond4, 1);

      // Create a ConditionList and append conditions with junctions
      ConditionList conditionList = new ConditionList();
      conditionList.append(item1);
      conditionList.append(new JunctionOperator(JunctionOperator.AND, 0));
      conditionList.append(item2);
      conditionList.append(new JunctionOperator(JunctionOperator.AND, 0));
      conditionList.append(item3);
      conditionList.append(new JunctionOperator(JunctionOperator.OR, 1));
      conditionList.append(item4);

      // Split ONE OF conditions, limit set to 3
      conditionList = ConditionUtil.splitOneOfCondition(conditionList, 3);

      // After splitting, the first, second, and fourth "ONE OF" conditions will be divided into sub-conditions.
      // Each "ONE OF" condition produces sub-conditions, with OR junctions in between for each.
      Assertions.assertEquals(17, conditionList.getSize());

      // Validate the first "ONE OF" condition split
      // 1st condition (part of the first "ONE OF")
      item1 = conditionList.getConditionItem(0);
      List<Object> actualValues1 = ((Condition) item1.getXCondition()).getValues();
      Assertions.assertEquals(3, actualValues1.size());
      Assertions.assertIterableEquals(Arrays.asList("val1", "val2", "val3"), actualValues1);
      Assertions.assertEquals(1, item1.getLevel());

      // 1st OR
      Assertions.assertEquals(JunctionOperator.OR, conditionList.getJunctionOperator(1).getJunction());
      Assertions.assertEquals(1, conditionList.getJunctionOperator(1).getLevel());

      // 2nd condition (part of the first "ONE OF")
      item1 = conditionList.getConditionItem(2);
      actualValues1 = ((Condition) item1.getXCondition()).getValues();
      Assertions.assertEquals(3, actualValues1.size());
      Assertions.assertIterableEquals(Arrays.asList("val4", "val5", "val6"), actualValues1);
      Assertions.assertEquals(1, item1.getLevel());

      // 2nd OR
      Assertions.assertEquals(JunctionOperator.OR, conditionList.getJunctionOperator(3).getJunction());
      Assertions.assertEquals(1, conditionList.getJunctionOperator(3).getLevel());

      // 3rd condition (part of the first "ONE OF")
      item1 = conditionList.getConditionItem(4);
      actualValues1 = ((Condition) item1.getXCondition()).getValues();
      Assertions.assertEquals(1, actualValues1.size());
      Assertions.assertIterableEquals(Arrays.asList("val7"), actualValues1);
      Assertions.assertEquals(1, item1.getLevel());

      // Validate the AND junction between the first and second "ONE OF"
      Assertions.assertEquals(JunctionOperator.AND, conditionList.getJunctionOperator(5).getJunction());
      Assertions.assertEquals(0, conditionList.getJunctionOperator(5).getLevel());

      // Validate the second "ONE OF" condition split
      // 1st condition (part of the second "ONE OF")
      item2 = conditionList.getConditionItem(6);
      List<Object> actualValues2 = ((Condition) item2.getXCondition()).getValues();
      Assertions.assertEquals(3, actualValues2.size());
      Assertions.assertIterableEquals(Arrays.asList("val8", "val9", "val10"), actualValues2);
      Assertions.assertEquals(1, item2.getLevel());

      // 1st OR
      Assertions.assertEquals(JunctionOperator.OR, conditionList.getJunctionOperator(7).getJunction());
      Assertions.assertEquals(1, conditionList.getJunctionOperator(7).getLevel());

      // 2nd condition (part of the second "ONE OF")
      item2 = conditionList.getConditionItem(8);
      actualValues2 = ((Condition) item2.getXCondition()).getValues();
      Assertions.assertEquals(3, actualValues2.size());
      Assertions.assertIterableEquals(Arrays.asList("val11", "val12", "val13"), actualValues2);
      Assertions.assertEquals(1, item2.getLevel());

      // 2nd OR
      Assertions.assertEquals(JunctionOperator.OR, conditionList.getJunctionOperator(9).getJunction());
      Assertions.assertEquals(1, conditionList.getJunctionOperator(9).getLevel());

      // 3rd condition (part of the second "ONE OF")
      item2 = conditionList.getConditionItem(10);
      actualValues2 = ((Condition) item2.getXCondition()).getValues();
      Assertions.assertEquals(1, actualValues2.size());
      Assertions.assertIterableEquals(Arrays.asList("val14"), actualValues2);
      Assertions.assertEquals(1, item2.getLevel());

      // Validate the AND junction between the second "ONE OF" and the third condition
      Assertions.assertEquals(JunctionOperator.AND, conditionList.getJunctionOperator(11).getJunction());
      Assertions.assertEquals(0, conditionList.getJunctionOperator(11).getLevel());

      // Validate the third condition (EQUAL TO "a1")
      item3 = conditionList.getConditionItem(12);
      Assertions.assertEquals("a1", ((Condition) item3.getXCondition()).getValue(0));
      Assertions.assertEquals(1, item3.getLevel());

      // Validate the OR between the third and fourth conditions
      Assertions.assertEquals(JunctionOperator.OR, conditionList.getJunctionOperator(13).getJunction());
      Assertions.assertEquals(1, conditionList.getJunctionOperator(13).getLevel());

      // Validate the fourth "ONE OF" condition split
      // 1st condition (part of the fourth "ONE OF")
      // should be level 2
      item4 = conditionList.getConditionItem(14);
      List<Object> actualValues4 = ((Condition) item4.getXCondition()).getValues();
      Assertions.assertEquals(3, actualValues4.size());
      Assertions.assertIterableEquals(Arrays.asList("val15", "val16", "val17"), actualValues4);
      Assertions.assertEquals(2, item4.getLevel());

      // 1st OR
      Assertions.assertEquals(JunctionOperator.OR, conditionList.getJunctionOperator(15).getJunction());
      Assertions.assertEquals(2, conditionList.getJunctionOperator(15).getLevel());

      // 2nd condition (part of the fourth "ONE OF")
      item4 = conditionList.getConditionItem(16);
      actualValues4 = ((Condition) item4.getXCondition()).getValues();
      Assertions.assertEquals(2, actualValues4.size());
      Assertions.assertIterableEquals(Arrays.asList("val18", "val19"), actualValues4);
      Assertions.assertEquals(2, item4.getLevel());
   }

   private static ConditionList oneOfList(int valueCount, boolean negated, boolean withSibling) {
      List<Object> values = new ArrayList<>();

      for(int i = 0; i < valueCount; i++) {
         values.add("v" + i);
      }

      Condition cond = new Condition();
      cond.setValues(values);
      cond.setOperation(XCondition.ONE_OF);
      cond.setNegated(negated);
      ConditionList list = new ConditionList();

      if(withSibling) {
         Condition eq = new Condition();
         eq.setOperation(XCondition.EQUAL_TO);
         eq.addValue("t");
         list.append(new ConditionItem(new ColumnRef(new AttributeRef("ref0")), eq, 0));
         list.append(new JunctionOperator(JunctionOperator.AND, 0));
      }

      list.append(new ConditionItem(new ColumnRef(new AttributeRef("ref1")), cond, 0));
      return list;
   }

   /**
    * Bug #79483: "is not one of" split into chunks must be AND-ed, because
    * NOT IN (a) OR NOT IN (b) is true for every row and excludes nothing.
    */
   @Test
   void splitNegatedOneOfJoinsChunksWithAnd() {
      // Oracle limit 1000, 2500 values -> 3 chunks
      ConditionList list = ConditionUtil.splitOneOfCondition(oneOfList(2500, true, false), 1000);

      assertEquals(5, list.getSize());
      int[] sizes = { 1000, 1000, 500 };

      for(int i = 0; i < 3; i++) {
         Condition c = (Condition) list.getConditionItem(i * 2).getXCondition();
         assertTrue(c.isNegated());
         assertEquals(XCondition.ONE_OF, c.getOperation());
         assertEquals(sizes[i], c.getValueCount());
      }

      assertEquals(JunctionOperator.AND, list.getJunctionOperator(1).getJunction());
      assertEquals(JunctionOperator.AND, list.getJunctionOperator(3).getJunction());
   }

   @Test
   void splitOneOfLimitBoundary() {
      assertEquals(1, ConditionUtil.splitOneOfCondition(oneOfList(1000, true, false), 1000).getSize());
      ConditionList list = ConditionUtil.splitOneOfCondition(oneOfList(1001, true, false), 1000);
      assertEquals(3, list.getSize());
      assertEquals(JunctionOperator.AND, list.getJunctionOperator(1).getJunction());
      assertEquals(1, ((Condition) list.getConditionItem(2).getXCondition()).getValueCount());
   }

   /**
    * Non-negated control: chunks stay OR-ed.
    */
   @Test
   void splitNonNegatedOneOfStillUsesOr() {
      ConditionList list = ConditionUtil.splitOneOfCondition(oneOfList(1001, false, false), 1000);
      assertEquals(3, list.getSize());
      assertFalse(((Condition) list.getConditionItem(0).getXCondition()).isNegated());
      assertEquals(JunctionOperator.OR, list.getJunctionOperator(1).getJunction());
   }

   /**
    * The chunks are grouped one level deeper than the sibling, so the surrounding AND is kept.
    */
   @Test
   void splitNegatedOneOfKeepsGroupingWithSibling() {
      ConditionList list = ConditionUtil.splitOneOfCondition(oneOfList(1001, true, true), 1000);

      // sibling, AND, chunk, AND, chunk
      assertEquals(5, list.getSize());
      assertEquals(0, list.getConditionItem(0).getLevel());
      assertEquals(JunctionOperator.AND, list.getJunctionOperator(1).getJunction());
      assertEquals(0, list.getJunctionOperator(1).getLevel());
      assertEquals(1, list.getConditionItem(2).getLevel());
      assertEquals(JunctionOperator.AND, list.getJunctionOperator(3).getJunction());
      assertEquals(1, list.getJunctionOperator(3).getLevel());
      assertEquals(1, list.getConditionItem(4).getLevel());
   }

   /**
    * The XFilterNode built from the split list is an AND of negated IN nodes.
    */
   @Test
   void splitNegatedOneOfBuildsAndOfNotIn() {
      ConditionList list = ConditionUtil.splitOneOfCondition(oneOfList(2001, true, false), 1000);
      inetsoft.uql.jdbc.UniformSQL sql = new inetsoft.uql.jdbc.UniformSQL();
      inetsoft.uql.jdbc.XFilterNode node = new inetsoft.uql.jdbc.util.ConditionListHandler()
         .createXFilterNode(list, sql, new inetsoft.uql.VariableTable());

      int[] leaves = { 0 };
      checkAndOfNotIn(node, leaves);
      assertEquals(3, leaves[0]);
   }

   private static void checkAndOfNotIn(inetsoft.uql.jdbc.XFilterNode node, int[] leaves) {
      if(node instanceof inetsoft.uql.jdbc.XSet) {
         inetsoft.uql.jdbc.XSet set = (inetsoft.uql.jdbc.XSet) node;
         assertEquals(inetsoft.uql.jdbc.XSet.AND, set.getRelation());
         assertFalse(set.isIsNot());

         for(int i = 0; i < set.getChildCount(); i++) {
            checkAndOfNotIn((inetsoft.uql.jdbc.XFilterNode) set.getChild(i), leaves);
         }
      }
      else {
         assertTrue(node.isIsNot());
         leaves[0]++;
      }
   }
}
