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
package inetsoft.uql.jdbc.util;

import inetsoft.uql.*;
import inetsoft.uql.jdbc.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ConditionListHandler.
 */
public class ConditionListHandlerTest {

   private ConditionListHandler handler;

   @BeforeEach
   void setUp() {
      handler = new ConditionListHandler();
   }

   // -----------------------------------------------------------------------
   // validateJunction (private) — tested via reflection
   // -----------------------------------------------------------------------

   private String invokeValidateJunction(String input) throws Exception {
      Method m = ConditionListHandler.class.getDeclaredMethod("validateJunction", String.class);
      m.setAccessible(true);
      return (String) m.invoke(handler, input);
   }

   @Test
   void validateJunction_pureAnd_returnsAnd() throws Exception {
      assertEquals("and", invokeValidateJunction("and"));
   }

   @Test
   void validateJunction_pureOr_returnsOr() throws Exception {
      assertEquals("or", invokeValidateJunction("or"));
   }

   @Test
   void validateJunction_andWithLeadingDots_stripsDotsPreservesAnd() throws Exception {
      // dots are non-letter non-space chars and get stripped
      assertEquals("and", invokeValidateJunction(".......and"));
   }

   @Test
   void validateJunction_notAndWithDots_returnsNotAnd() throws Exception {
      // ".....not  and" — dots stripped, internal spaces preserved
      String result = invokeValidateJunction(".....not  and");
      assertEquals("not  and", result);
   }

   @Test
   void validateJunction_notOrWithDots_returnsNotOr() throws Exception {
      String result = invokeValidateJunction("...not  or");
      assertEquals("not  or", result);
   }

   @Test
   void validateJunction_trailingDotsOnly_stripsAndTrims() throws Exception {
      // "and..." — str.trim()="and...", str.length()=6; loop reads junction.charAt(0..5)
      // = 'a','n','d','.','.','.'; letters kept, dots stripped → "and", trim → "and"
      assertEquals("and", invokeValidateJunction("and..."));
   }

   @Test
   void validateJunction_mixedDigitsAndLetters_stripsDigits() throws Exception {
      // digits are not letters — "123and" → "and"
      assertEquals("and", invokeValidateJunction("123and"));
   }

   @ParameterizedTest
   @CsvSource({
      "and, and",
      "or,  or",
      "AND, AND",
      "OR,  OR"
   })
   void validateJunction_variousCases(String input, String expected) throws Exception {
      assertEquals(expected.trim(), invokeValidateJunction(input).trim());
   }

   // -----------------------------------------------------------------------
   // createXFilterNode(HierarchyList) — tests AND/OR tree building
   // -----------------------------------------------------------------------

   /**
    * Build a ConditionList with XFilterNodeItem/XSetItem objects appended directly
    * (HierarchyList.append accepts any HierarchyItem).
    */
   private ConditionList buildHierarchyList(HierarchyItem... items) {
      ConditionList list = new ConditionList();
      for(HierarchyItem item : items) {
         list.append(item);
      }
      return list;
   }

   @Test
   void createXFilterNode_singleConditionItem_returnsEquivalentNode() {
      XExpression field = new XExpression("col1", XExpression.FIELD);
      XExpression value = new XExpression("'hello'", XExpression.EXPRESSION);
      XBinaryCondition cond = new XBinaryCondition(field, value, "=");

      ConditionList list = buildHierarchyList(new XFilterNodeItem(cond, 0));
      XFilterNode result = handler.createXFilterNode(list);

      assertNotNull(result);
      assertInstanceOf(XBinaryCondition.class, result);
      assertEquals(cond.toString(), result.toString());
   }

   @Test
   void createXFilterNode_twoConditionsWithAnd_returnsAndSet() {
      XBinaryCondition cond1 = new XBinaryCondition(
         new XExpression("a", XExpression.FIELD),
         new XExpression("'x'", XExpression.EXPRESSION), "=");
      XBinaryCondition cond2 = new XBinaryCondition(
         new XExpression("b", XExpression.FIELD),
         new XExpression("'y'", XExpression.EXPRESSION), "=");

      ConditionList list = buildHierarchyList(
         new XFilterNodeItem(cond1, 0),
         new XSetItem(new XSet(XSet.AND), 0),
         new XFilterNodeItem(cond2, 0)
      );

      XFilterNode result = handler.createXFilterNode(list);

      assertNotNull(result);
      assertInstanceOf(XSet.class, result);
      assertEquals(XSet.AND, ((XSet) result).getRelation());
      assertEquals(2, result.getChildCount());
   }

   @Test
   void createXFilterNode_twoConditionsWithOr_returnsOrSet() {
      XBinaryCondition cond1 = new XBinaryCondition(
         new XExpression("a", XExpression.FIELD),
         new XExpression("'x'", XExpression.EXPRESSION), "=");
      XBinaryCondition cond2 = new XBinaryCondition(
         new XExpression("b", XExpression.FIELD),
         new XExpression("'y'", XExpression.EXPRESSION), "=");

      ConditionList list = buildHierarchyList(
         new XFilterNodeItem(cond1, 0),
         new XSetItem(new XSet(XSet.OR), 0),
         new XFilterNodeItem(cond2, 0)
      );

      XFilterNode result = handler.createXFilterNode(list);

      assertNotNull(result);
      assertInstanceOf(XSet.class, result);
      assertEquals(XSet.OR, ((XSet) result).getRelation());
      assertEquals(2, result.getChildCount());
   }

   @Test
   void createXFilterNode_emptyList_returnsNull() {
      ConditionList list = new ConditionList();
      XFilterNode result = handler.createXFilterNode(list);
      assertNull(result);
   }

   /**
    * Verify that A AND B OR C AND D produces an OR set at root with two AND children,
    * confirming AND-before-OR precedence.
    */
   @Test
   void createXFilterNode_andOrPrecedence_andBeforeOr() {
      XBinaryCondition condA = makeCond("a", "'1'");
      XBinaryCondition condB = makeCond("b", "'2'");
      XBinaryCondition condC = makeCond("c", "'3'");
      XBinaryCondition condD = makeCond("d", "'4'");

      // A AND B OR C AND D — all at level 0
      ConditionList list = buildHierarchyList(
         new XFilterNodeItem(condA, 0),
         new XSetItem(new XSet(XSet.AND), 0),
         new XFilterNodeItem(condB, 0),
         new XSetItem(new XSet(XSet.OR), 0),
         new XFilterNodeItem(condC, 0),
         new XSetItem(new XSet(XSet.AND), 0),
         new XFilterNodeItem(condD, 0)
      );

      XFilterNode result = handler.createXFilterNode(list);

      assertNotNull(result);
      assertInstanceOf(XSet.class, result);
      XSet root = (XSet) result;
      assertEquals(XSet.OR, root.getRelation());
      assertEquals(2, root.getChildCount());
      // each child should be an AND set
      assertInstanceOf(XSet.class, root.getChild(0));
      assertEquals(XSet.AND, ((XSet) root.getChild(0)).getRelation());
      assertInstanceOf(XSet.class, root.getChild(1));
      assertEquals(XSet.AND, ((XSet) root.getChild(1)).getRelation());
   }

   private XBinaryCondition makeCond(String field, String value) {
      return new XBinaryCondition(
         new XExpression(field, XExpression.FIELD),
         new XExpression(value, XExpression.EXPRESSION), "=");
   }
}
