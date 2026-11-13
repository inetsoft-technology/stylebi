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
package inetsoft.report;

import inetsoft.report.internal.binding.OrderInfo;
import inetsoft.report.internal.binding.TopNInfo;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.script.formula.FormulaFunctions;
import inetsoft.report.script.formula.NamedCellRange;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.CalcGroup;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression guard for #75747 / #75801: converting a crosstab to a freehand table
 * generates a {@code toList(data['Col'], ...)} expression per row/column GROUP cell,
 * where "Col" is the physical column name backing the regenerated crosstab's data.
 * {@code LayoutTool.getGroupColumnName()} decides that name from a cell binding's
 * value, which can hold either the physical column name or the dimension's display
 * full name (e.g. "None(Month(ndate))") depending on how the dimension was bound.
 * Guessing wrong collapses the group cell's expansion to zero rows (#75747), and
 * "fixing" it by always stripping the wrapper breaks dimensions whose physical
 * column genuinely is named with its display form, such as "Year(Date)" (#75801).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class LayoutToolTest {
   @Test
   void usesRawValueWhenItIsAlreadyTheOuterDateDimensionsPhysicalColumn() throws Exception {
      // #75801 (CroTable7): "Year(Date)" is a sole/outer date GROUP dimension whose
      // crosstab column is genuinely named with its display form -- must be used as-is.
      DefaultTableLens base = new DefaultTableLens(new Object[][] {
         { "Year(Date)", "Sum(Total)" },
         { "2022", 100.0 }
      });

      TableCellBinding cell = new TableCellBinding(CellBinding.BIND_COLUMN, "Year(Date)");
      assertEquals("Year(Date)", getGroupColumnName(base, cell));
   }

   @Test
   void unwrapsDisplayNameForNestedDateDimension() throws Exception {
      // #75747 (GroupAndTab): "None(Month(ndate))" is the display full name for a
      // date dimension nested under another group; the crosstab's actual column is
      // the unwrapped "Month(ndate))".
      DefaultTableLens base = new DefaultTableLens(new Object[][] {
         { "state", "Month(ndate)", "Sum(eid)" },
         { "Others", "2022-01", 5.0 }
      });

      TableCellBinding cell = new TableCellBinding(CellBinding.BIND_COLUMN, "None(Month(ndate))");
      assertEquals("Month(ndate)", getGroupColumnName(base, cell));
   }

   @Test
   void unwrapsDisplayNameForMultipleNestedPassthroughDateDimensions() throws Exception {
      // #75801 (Special2): several nested date/passthrough dimensions, each whose
      // display full name must be unwrapped to its own physical column.
      DefaultTableLens base = new DefaultTableLens(new Object[][] {
         { "NDateTime", "NDate", "NTime", "Sum(x)" },
         { "2022-01-01", "2022-01-01", "10:00", 3.0 }
      });

      assertEquals("NDateTime", getGroupColumnName(base,
         new TableCellBinding(CellBinding.BIND_COLUMN, "None(NDateTime)")));
      assertEquals("NDate", getGroupColumnName(base,
         new TableCellBinding(CellBinding.BIND_COLUMN, "None(NDate)")));
      assertEquals("NTime", getGroupColumnName(base,
         new TableCellBinding(CellBinding.BIND_COLUMN, "None(NTime)")));
   }

   @Test
   void returnsRawValueUncheckedWhenBaseIsNull() throws Exception {
      TableCellBinding cell = new TableCellBinding(CellBinding.BIND_COLUMN, "None(Month(ndate))");
      assertEquals("None(Month(ndate))", getGroupColumnName(null, cell));
   }

   @Test
   void returnsRawValueWhenNeitherFormMatchesAnyColumn() throws Exception {
      DefaultTableLens base = new DefaultTableLens(new Object[][] {
         { "unrelated_column" },
         { "x" }
      });

      TableCellBinding cell = new TableCellBinding(CellBinding.BIND_COLUMN, "None(Month(ndate))");
      assertEquals("None(Month(ndate))", getGroupColumnName(base, cell));
   }

   // escapeColName (#75665): ? @ { } , = ; & and ' get a leading backslash, nothing else does.
   private static final String BS = String.valueOf((char) 92);

   @ParameterizedTest
   @ValueSource(strings = { "?", "@", "{", "}", ",", "=", ";", "&", "'" })
   void escapeColNameEscapesEachSpecialCharacter(String special) throws Exception {
      assertEquals("a" + BS + special + "b", escapeColName("a" + special + "b"));
   }

   @Test
   void escapeColNameLeavesOtherNamesUntouched() throws Exception {
      assertEquals("YYYY" + BS + ", MMM D", escapeColName("YYYY, MMM D"));
      assertEquals("a b", escapeColName("a b"));
      assertEquals("", escapeColName(""));
      assertEquals("年度" + BS + ",金额", escapeColName("年度,金额"));
      assertEquals("say \"hi\"", escapeColName("say \"hi\""));
      // surrogate pair (U+20000) and CJK without specials are unchanged
      assertEquals("𠀀年度", escapeColName("𠀀年度"));
   }

   // #81085: getFieldNameByScript() must return the whole, real column name (the caller matches it
   // against the table's columns) for every character escapeColName escapes, not only ' and ].
   @ParameterizedTest
   @ValueSource(strings = { "plain", "a]b", "a'b", "a']b", "Sales ['USD']", "a@b", "a,b", "a?b",
                            "a{b}", "a=b;c&d", "a'@']b", "a:b", "Qty (units)", "say \"hi\"",
                            "a b", "年度,金额" })
   void getFieldNameByScriptKeepsAWholeColumnName(String name) throws Exception {
      String script = "data['" + escapeColName(name) + "@g:$g']";
      assertEquals(name, LayoutTool.getFieldNameByScript(script));
   }

   @Test
   void getFieldNameByScriptWithoutGroupAndNonDataScript() throws Exception {
      assertEquals("a']b", LayoutTool.getFieldNameByScript("data['" + escapeColName("a']b") + "']"));
      assertEquals(null, LayoutTool.getFieldNameByScript("foo"));
      assertEquals(null, LayoutTool.getFieldNameByScript(null));
   }

   private static GroupRef group(String name) {
      ColumnRef col = new ColumnRef(new AttributeRef(name));
      col.setDataType(XSchema.STRING);
      GroupRef group = new GroupRef(col);
      group.setOrderInfo(new OrderInfo());
      return group;
   }

   private static TopNInfo topN(boolean others) {
      TopNInfo topn = new TopNInfo();
      topn.setTopN(3);
      topn.setTopNSummaryCol(0);
      topn.setOthers(others);
      return topn;
   }

   private static String groupCond(GroupRef group, boolean subtopn) {
      return LayoutTool.createGroupCond(new CalcGroup[] { group }, new String[] { "g" }, 0, 1,
                                        subtopn);
   }

   private static Set<?> groupKeys(String cond) throws Exception {
      NamedCellRange range = new NamedCellRange("col" + cond);
      Field field = NamedCellRange.class.getDeclaredField("groupspecs");
      field.setAccessible(true);
      return ((Map<?, ?>) field.get(range)).keySet();
   }

   @Test
   void createGroupCondForOneGroupIsTheQualifierOfThatGroup() {
      assertEquals("@region:$region", LayoutTool.createGroupCond(
         new CalcGroup[] { group("region") }, new String[] { "region" }, 0, 1, false));
   }

   @Test
   void createGroupCondListsTheGroupsOfTheRangeInOrder() {
      CalcGroup[] groups = { group("a"), group("b") };
      String[] cells = { "a", "b" };
      assertEquals("@a:$a;b:$b", LayoutTool.createGroupCond(groups, cells, 0, 2, false));
      // control: only the groups of the range are listed
      assertEquals("@b:$b", LayoutTool.createGroupCond(groups, cells, 1, 2, false));
   }

   @Test
   void createGroupCondIsEmptyForAnEmptyRangeOrNoGroups() {
      CalcGroup[] groups = { group("a"), group("b") };
      String[] cells = { "a", "b" };
      assertEquals("", LayoutTool.createGroupCond(groups, cells, 1, 1, false));
      assertEquals("", LayoutTool.createGroupCond(null, cells, 1, 1, false));
      assertEquals("", LayoutTool.createGroupCond(null, cells, 0, 2, false));
   }

   @ParameterizedTest
   @ValueSource(strings = { "a?b", "a@b", "a{b", "a}b", "a,b", "a=b", "a;b", "a&b", "a'b", "a:b" })
   void createGroupCondQualifierParsesBackToTheGroupName(String name) throws Exception {
      assertEquals(Set.of(name), groupKeys(groupCond(group(name), false)));
   }

   // pinned current behavior: the sub-top-N expression lists the group by its row value
   @Test
   void createGroupCondPinsTheSubTopNInGroupsExpression() {
      GroupRef group = group("g");
      group.setTopN(topN(false));
      String cond = groupCond(group, true);
      assertTrue(cond.endsWith("inGroups([\"g\",rowValue[\"g\"]], \"Others \")"), cond);
   }

   // pinned current behavior of a top-N group with Others
   @Test
   void createGroupCondPinsTheTopNOthersExpression() {
      GroupRef group = group("g");
      group.setTopN(topN(true));
      String cond = groupCond(group, false);
      assertTrue(cond.startsWith("?("), cond);
      assertTrue(cond.contains("inArray($g[\"*\"], rowValue[\"g\"])"), cond);
      // the Others branch selects the rows that are NOT in the top-N list
      assertTrue(cond.contains("\"Others \" ? !inArray($g[\"*\"], rowValue[\"g\"]) : "), cond);
      assertTrue(cond.endsWith(" : rowValue[\"g\"] == $g[\".\"])"), cond);
   }

   // control of the test above: a top-N group without Others adds neither the Others branch nor
   // the sub-top-N expression
   @Test
   void createGroupCondOfATopNGroupWithoutOthersHasNoOthersBranch() {
      GroupRef group = group("g");
      group.setTopN(topN(false));
      assertEquals("@g:$g", groupCond(group, false));
   }

   // the Others group of a top-N is handled by its own branch; the sub-top-N expression is only
   // added for a top-N group without Others
   @Test
   void createGroupCondDoesNotAddTheSubTopNExpressionForATopNGroupWithOthers() {
      GroupRef group = group("g");
      group.setTopN(topN(true));
      String cond = groupCond(group, true);
      assertFalse(cond.contains("inGroups("), cond);
      assertTrue(cond.contains("\"Others \" ? !inArray($g[\"*\"], rowValue[\"g\"]) : "), cond);
   }

   @Test
   void createGroupCondAddsTheSubTopNExpressionForATopNGroupWithoutOthers() {
      GroupRef group = group("g");
      group.setTopN(topN(false));
      String cond = groupCond(group, true);
      assertTrue(cond.startsWith("@g:$g"), cond);
      assertTrue(cond.contains("inGroups("), cond);
      assertFalse(cond.contains("inArray("), cond);
   }

   @Disabled("Bug #81256: addSubTopNCond does not escape a double quote in the cell name")
   @Test
   void createGroupCondEscapesADoubleQuoteInTheCellNameOfTheInGroupsPart() {
      GroupRef group = group("g");
      group.setTopN(topN(false));
      String cond = LayoutTool.createGroupCond(new CalcGroup[] { group },
                                               new String[] { "a\"b" }, 0, 1, true);
      assertTrue(cond.contains("inGroups([\"a\\\"b\",rowValue[\"g\"]]"), cond);
   }

   // pinned current behavior of a date group on a part level
   @Test
   void createGroupCondPinsTheToListExpressionOfADateGroup() throws Exception {
      ColumnRef col = new ColumnRef(new AttributeRef("d"));
      col.setDataType(XSchema.DATE);
      GroupRef group = new GroupRef(col);
      OrderInfo order = new OrderInfo();
      order.setInterval(1, DateRangeRef.MONTH_OF_YEAR_PART);
      group.setOrderInfo(order);
      String cond = groupCond(group, false);
      assertEquals("@=toList(rowValue[\"d\"], \"date=month\"):$g", cond);
      assertEquals(1, groupKeys(cond).size());
   }

   // pinned current behavior of a date group at year level
   @Test
   void createGroupCondPinsTheRoundDateExpressionOfAYearLevelDateGroup() throws Exception {
      ColumnRef col = new ColumnRef(new AttributeRef("d"));
      col.setDataType(XSchema.DATE);
      GroupRef group = new GroupRef(col);
      OrderInfo order = new OrderInfo();
      order.setInterval(1, DateRangeRef.YEAR_INTERVAL);
      group.setOrderInfo(order);
      String cond = groupCond(group, false);
      assertEquals("@=toList(rowValue[\"d\"], \"rounddate=year,interval=1.0\"):$g", cond);
      assertEquals(1, groupKeys(cond).size());
   }

   @Test
   void everyGeneratedDateOptionIsAcceptedByToList() {
      java.util.Date date = new java.util.Date(1615790710000L);

      for(String option : LayoutTool.DATEOPT.values()) {
         assertDoesNotThrow(() -> FormulaFunctions.toList(new Object[] { date }, option), option);
      }
   }

   @Test
   void createGroupCondKeepsTheSecondGroupWhenOnlyTheFirstIsTopNOthers() {
      GroupRef first = group("g1");
      first.setTopN(topN(true));
      CalcGroup[] groups = { first, group("g2") };
      String cond = LayoutTool.createGroupCond(groups, new String[] { "g1", "g2" }, 0, 2, false);
      assertTrue(cond.startsWith("@g2:$g2?("), cond);
      assertTrue(cond.contains("$g1[\"*\"]"), cond);
      assertFalse(cond.contains("$g2[\"*\"]"), cond);
   }

   private static String escapeColName(String name) throws Exception {
      Method method = LayoutTool.class.getDeclaredMethod("escapeColName", String.class);
      method.setAccessible(true);
      return (String) method.invoke(null, name);
   }

   private static String getGroupColumnName(TableLens base, TableCellBinding cell) throws Exception {
      Method method = LayoutTool.class.getDeclaredMethod(
         "getGroupColumnName", TableLens.class, TableCellBinding.class);
      method.setAccessible(true);
      return (String) method.invoke(null, base, cell);
   }
}
