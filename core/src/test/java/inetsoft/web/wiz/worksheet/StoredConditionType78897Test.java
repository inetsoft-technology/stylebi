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
package inetsoft.web.wiz.worksheet;

import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static inetsoft.web.wiz.worksheet.WorksheetMutationSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78897: a numeric condition keeps the type it was built with when its column later
 * narrows (double to integer). Re-sending its fractional literal is accepted through
 * edit_condition and the set_* tools instead of being refused as a fraction on an integer column,
 * while a fresh fractional literal on an integer column is still refused.
 */
@Tag("core")
@WizAgentTestSupport
class StoredConditionType78897Test {
   private static BoundTableAssembly table(String type) {
      Worksheet ws = new Worksheet();
      BoundTableAssembly t = new BoundTableAssembly(ws, "T");
      ColumnSelection cs = new ColumnSelection();
      ColumnRef col = new ColumnRef(new AttributeRef(null, "K"));
      col.setDataType(type);
      cs.addAttribute(col);
      t.setColumnSelection(cs, false);
      ws.addAssembly(t);
      return t;
   }

   /** A table whose K is now integer, holding the pre-conditions built while K was double. */
   private static BoundTableAssembly narrowed(ConditionNodes build) {
      BoundTableAssembly was = table(XSchema.DOUBLE);
      build.apply(was);
      BoundTableAssembly t = table(XSchema.INTEGER);
      t.setPreConditionList(was.getPreConditionList());
      return t;
   }

   private interface ConditionNodes {
      void apply(BoundTableAssembly t);
   }

   private static BoundTableAssembly narrowedWith(String op, String... values) {
      return narrowed(t -> addFilter(t, "K", op, values));
   }

   private static List<ConditionNode> cond(String op, String... values) {
      return List.of(new ConditionNode(
         new ConditionSpec("K", op, Arrays.asList(values), false, null), null, 0));
   }

   private static List<ConditionNode> two() {
      return List.of(
         new ConditionNode(new ConditionSpec("K", "<", List.of("2.5"), false, null), null, 0),
         new ConditionNode(null, new JunctionSpec("AND", 0), 0),
         new ConditionNode(new ConditionSpec("K", ">", List.of("0.5"), false, null), null, 0));
   }

   private static Condition stored(ConditionListWrapper list, int index) {
      return (Condition) ((ConditionItem) list.getConditionList().getItem(index)).getXCondition();
   }

   private static void refused(Runnable call, String... fragments) {
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class, call::run);

      for(String fragment : fragments) {
         assertTrue(e.getMessage().contains(fragment), e.getMessage() + " / " + fragment);
      }
   }

   @Test
   void resendingAFractionalConditionAfterNarrowingIsAcceptedAndKeepsItsType() {
      BoundTableAssembly t = narrowedWith("<", "1.5");

      replaceFilter(t, "K", "<", "1.5");

      assertEquals(XSchema.DOUBLE, stored(t.getPreConditionList(), 0).getType());
      assertEquals("1.5", stored(t.getPreConditionList(), 0).getValue(0).toString());
   }

   @Test
   void betweenAndNegatedOneOfAreAccepted() {
      BoundTableAssembly t = narrowedWith("between", "0.5", "2.5");
      replaceFilter(t, "K", "between", "0.5", "2.5");
      assertEquals(XSchema.DOUBLE, stored(t.getPreConditionList(), 0).getType());

      BoundTableAssembly n = narrowedWith("not_one_of", "1.5", "2.5");
      replaceFilter(n, "K", "not_one_of", "1.5", "2.5");
      Condition c = stored(n.getPreConditionList(), 0);
      assertEquals(XSchema.DOUBLE, c.getType());
      assertTrue(c.isNegated());
      assertEquals(2, c.getValueCount());
   }

   @Test
   void severalConditionsOnTheFieldCanBeReplayedWithSetConditions() {
      BoundTableAssembly t = narrowed(w -> setConditions(w, two(), false));

      refused(() -> replaceFilter(t, "K", "<", "2.5"), "2 conditions", "set_conditions");

      setConditions(t, two(), false);
      assertEquals(XSchema.DOUBLE, stored(t.getPreConditionList(), 0).getType());
      assertEquals(XSchema.DOUBLE, stored(t.getPreConditionList(), 2).getType());
   }

   @Test
   void setMvConditionsReplaysAgainstTheMvListBeingReplaced() {
      BoundTableAssembly was = table(XSchema.DOUBLE);
      setMVConditions(was, cond("<", "1.5"), null, null, null, null);
      BoundTableAssembly t = table(XSchema.INTEGER);
      t.setMVUpdatePreConditionList(was.getMVUpdatePreConditionList());

      setMVConditions(t, cond("<", "1.5"), null, null, null, null);

      assertEquals(XSchema.DOUBLE, stored(t.getMVUpdatePreConditionList(), 0).getType());
      refused(() -> setMVConditions(t, null, null, cond("<", "1.5"), null, null), "\"1.5\"");
   }

   @Test
   void aFreshFractionalLiteralOnAnIntegerColumnIsStillRefused() {
      BoundTableAssembly t = table(XSchema.INTEGER);

      refused(() -> addFilter(t, "K", "<", "1.5"), "\"1.5\"", "integer");
      refused(() -> replaceFilter(t, "K", "<", "1.5"), "\"1.5\"", "integer");
      refused(() -> setConditions(t, cond("<", "1.5"), false), "\"1.5\"", "integer");
   }

   @Test
   void anUnparseableValueIsRefusedAndNamesBothTypes() {
      BoundTableAssembly t = narrowedWith("<", "1.5");

      refused(() -> replaceFilter(t, "K", "<", "abc"), "\"abc\"", "integer", "double");
      assertEquals("1.5", stored(t.getPreConditionList(), 0).getValue(0).toString());
   }

   @Test
   void anIntegerConditionStillRefusesAFraction() {
      BoundTableAssembly t = table(XSchema.INTEGER);
      addFilter(t, "K", "<", "1");

      refused(() -> replaceFilter(t, "K", "<", "1.5"), "\"1.5\"", "integer");
      assertEquals(XSchema.INTEGER, stored(t.getPreConditionList(), 0).getType());
   }

   @Test
   void aNewFractionalValueIsAcceptedOnReplaceButNotOnAdd() {
      BoundTableAssembly t = narrowedWith("<", "1.5");

      replaceFilter(t, "K", "<", "2.7");
      assertEquals(XSchema.DOUBLE, stored(t.getPreConditionList(), 0).getType());

      refused(() -> addFilter(t, "K", "<", "2.7"), "\"2.7\"", "integer");
   }

   @Test
   void aValueTheColumnTypeAcceptsStillRetypesTheCondition() {
      BoundTableAssembly t = narrowedWith("<", "1.5");

      replaceFilter(t, "K", "<", "2");

      assertEquals(XSchema.INTEGER, stored(t.getPreConditionList(), 0).getType());
   }

   @Test
   void setConditionsRefusesAnUnparseableValueOnTheNarrowedField() {
      BoundTableAssembly t = narrowedWith("<", "1.5");

      refused(() -> setConditions(t, cond("<", "abc"), false), "\"abc\"", "integer", "double");
   }

   @Test
   void twoDifferentStoredTypesOnTheFieldFallBackToTheColumnType() {
      BoundTableAssembly t = table(XSchema.DOUBLE);
      addFilter(t, "K", "=", "1.5");
      ((ColumnRef) t.getColumnSelection().getAttribute(0)).setDataType(XSchema.INTEGER);
      addFilter(t, "K", "=", "3");
      assertEquals(XSchema.DOUBLE, stored(t.getPreConditionList(), 0).getType());
      assertEquals(XSchema.INTEGER, stored(t.getPreConditionList(), 2).getType());

      refused(() -> setConditions(t, cond("=", "1.5"), false), "\"1.5\"", "integer");
   }

   @Test
   void aFieldComparisonReplayOnTheNarrowedFieldIsNotJudgedAsALiteral() {
      BoundTableAssembly was = table(XSchema.DOUBLE);
      ColumnRef b = new ColumnRef(new AttributeRef(null, "B"));
      b.setDataType(XSchema.INTEGER);
      was.getColumnSelection().addAttribute(b);
      addFilter(was, "K", "=", "1.5");
      BoundTableAssembly t = table(XSchema.INTEGER);
      t.getColumnSelection().addAttribute(b);
      t.setPreConditionList(was.getPreConditionList());

      setConditions(t, List.of(new ConditionNode(new ConditionSpec(
         "K", "=", List.of("T.B"), false, null,
         List.of(new ConditionValueSpec("field", "T.B", null, null, 0))), null, 0)), false);

      assertEquals(1, t.getPreConditionList().getConditionSize());
   }
}
