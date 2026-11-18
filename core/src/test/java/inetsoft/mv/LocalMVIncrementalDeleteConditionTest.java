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
package inetsoft.mv;

import inetsoft.mv.data.*;
import inetsoft.mv.local.LocalMVIncremental;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.jdbc.util.ConditionListHandler;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The delete condition of an incremental MV update: {@code LocalMVIncremental.fixCondition}
 * converts the MV delete conditions of a table to the filter the MV rows are deleted with. Date
 * conditions become ranges of whole days, and {@code MV.} expressions are replaced by the MV's
 * fixed last update time or column range, so the result does not depend on the current date.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  PluginsTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class LocalMVIncrementalDeleteConditionTest {
   // 2021-03-04 05:06:07 UTC
   private static final long LAST_UPDATE = 1614834367000L;

   @Test
   void noDeleteConditionGivesNoFilter() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly table = table(ws, "T");
      MV mv = mv();

      assertNull(fix(mvdef(), mv, table, null));
      assertNull(fix(mvdef(), mv, table, new ConditionList()));
      Mockito.verify(mv, Mockito.never()).fixFilter(Mockito.any());
   }

   @Test
   void deleteConditionsBecomeTheFilterOfTheColumns() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly table = table(ws, "T");
      ConditionList conds = conditions("customer_id", XSchema.INTEGER, XCondition.GREATER_THAN, 4);
      conds.append(new JunctionOperator(JunctionOperator.OR, 0));
      append(conds, "state", XSchema.STRING, XCondition.EQUAL_TO, "CA");

      List<XNode> filter = fix(mvdef(), mv(), table, conds);

      assertEquals(1, filter.size());
      assertEquals("(state = CA or customer_id > 4)", describe(filter.get(0)));
   }

   @Test
   void periodOnADateColumnCoversWholeDays() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly table = table(ws, "T");
      ConditionList conds = period("orderdate", XSchema.DATE, date(2002, 1, 7, 10, 30),
                                   date(2002, 12, 4, 10, 30));

      List<XNode> filter = fix(mvdef(), mv(), table, conds);

      assertEquals("orderdate BETWEEN [" + date(2002, 1, 7, 0, 0).getTime() + ", " +
                      (date(2002, 12, 5, 0, 0).getTime() - 1) + "]",
                   describe(filter.get(0)));
   }

   @Test
   void periodOnATimestampColumnEndsAtTheStartOfTheLastDay() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly table = table(ws, "T");
      ConditionList conds = period("orderdate", XSchema.TIME_INSTANT, date(2002, 1, 7, 10, 30),
                                   date(2002, 12, 4, 10, 30));

      List<XNode> filter = fix(mvdef(), mv(), table, conds);

      // a period holds days only, and the timestamps are not extended to the end of the last
      // day. This is intended: the rows deleted from the MV must be the rows that
      // PeriodCondition.evaluate accepts for the data that is not in an MV, and evaluate also
      // ends at the start of the last day. A change to either end must change both. The start
      // of a day is computed from the raw offset of the time zone, so it is not 00:00 during
      // daylight saving time; compare with the period.
      PeriodCondition period = (PeriodCondition) conds.getConditionItem(0).getXCondition();
      long from = period.getFrom().getTime();
      long to = period.getTo().getTime();
      assertEquals("orderdate BETWEEN [" + from + ", " + to + "]", describe(filter.get(0)));
      assertTrue(to - from > 300 * DateCondition.ONE_DAY);
      assertTrue(period.evaluate(new Timestamp(to)));
      assertFalse(period.evaluate(new Timestamp(to + 1000)));
   }

   @Test
   void lastUpdateTimeIsTheFixedLastUpdateTimeOfTheMV() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly table = table(ws, "T");
      ConditionList conds = conditions("orderdate", XSchema.TIME_INSTANT, XCondition.LESS_THAN,
                                       expression("MV.LastUpdateTime"));

      List<XNode> filter = fix(mvdef(), mv(), table, conds);

      assertEquals("orderdate < " + LAST_UPDATE, describe(filter.get(0)));
   }

   @Test
   void maxValueIsReadAgainOnEachUpdate() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly table = table(ws, "T");
      MVColumn column = new MVColumn(ref("customer_id", XSchema.INTEGER), false);
      MVDef def = mvdef(column);
      ConditionList conds = conditions("customer_id", XSchema.INTEGER, XCondition.EQUAL_TO,
                                       expression("MV.MaxValue"));

      column.setRange(1, 42);
      assertEquals("customer_id = 42", describe(fix(def, mv(), table, conds).get(0)));

      // the MV grew after the first update: the table's delete condition still has the
      // expression, the value of the first update is not kept
      column.setRange(1, 43);
      assertEquals("customer_id = 43", describe(fix(def, mv(), table, conds).get(0)));
      AssetCondition cond = (AssetCondition)
         table.getMVDeletePreConditionList().getConditionList().getConditionItem(0).getXCondition();
      assertInstanceOf(ExpressionValue.class, cond.getValue(0));
   }

   @Test
   void columnRangeVariableIsTheCurrentRangeOfTheColumn() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly table = table(ws, "T");
      MVColumn column = new MVColumn(ref("customer_id", XSchema.INTEGER), false);
      MVDef def = mvdef(column);
      ConditionList conds = conditions("customer_id", XSchema.INTEGER, XCondition.LESS_THAN,
                                       new UserVariable("MV.customer_id.Max"));
      conds.append(new JunctionOperator(JunctionOperator.AND, 0));
      append(conds, "customer_id", XSchema.INTEGER, XCondition.GREATER_THAN,
             new UserVariable("MV.customer_id.Min"));

      column.setRange(3, 42);

      assertEquals("(customer_id < 42 and customer_id > 3)",
                   describe(fix(def, mv(), table, conds).get(0)));
   }

   /**
    * Call {@code LocalMVIncremental.fixCondition} with the delete conditions set on the table and
    * the variables and handler that {@code update0} prepares. The MV returns the filter it is
    * given, so the result is the filter created from the conditions.
    */
   @SuppressWarnings("unchecked")
   private static List<XNode> fix(MVDef def, MV mv, TableAssembly table, ConditionList conds)
      throws Exception
   {
      table.setMVDeletePreConditionList(conds);
      MVStorage storage = Mockito.mock(MVStorage.class);
      Mockito.when(storage.get(Mockito.anyString())).thenReturn(mv);
      LocalMVIncremental incremental;

      try(MockedStatic<MVStorage> statics = Mockito.mockStatic(MVStorage.class)) {
         statics.when(() -> MVStorage.getFile(Mockito.any())).thenReturn("f");
         statics.when(MVStorage::getInstance).thenReturn(storage);
         incremental = new LocalMVIncremental(def);
      }

      VariableTable vars = new VariableTable();
      vars.put("MV.LastUpdateTime", new Timestamp(def.getLastUpdateTime()));

      for(MVColumn column : def.getColumns()) {
         MVScriptable script = new MVScriptable(def, column, mv);
         String prefix = "MV." + column.getName().replaceAll("[^\\p{Alnum}]", "_");
         vars.put(prefix + ".Min", script.getMember("MinValue"));
         vars.put(prefix + ".Max", script.getMember("MaxValue"));
      }

      MVConditionListHandler handler = new MVConditionListHandler(table, mv, vars, def);
      handler.setLikeCaseSensitive(MVConditionListHandler.getLikeCaseSensitivity(
         table, AssetUtil.getSQLHelper(table)));
      Method method = LocalMVIncremental.class.getDeclaredMethod(
         "fixCondition", ConditionListWrapper.class, ConditionListHandler.class,
         VariableTable.class);
      method.setAccessible(true);

      return (List<XNode>) method.invoke(incremental, table.getMVDeleteConditionList(), handler,
                                         vars);
   }

   private static MV mv() {
      MV mv = Mockito.mock(MV.class);
      Mockito.when(mv.getDef()).thenReturn(Mockito.mock(MVDef.class));
      // one block whose dictionaries are not loaded: MV.MaxValue is the column's range
      Mockito.when(mv.getBlockSize()).thenReturn(1);
      Mockito.when(mv.indexOfHeader(Mockito.anyString(), Mockito.anyInt())).thenReturn(-1);
      Mockito.when(mv.fixFilter(Mockito.any()))
         .thenAnswer(invocation -> List.of((XNode) invocation.getArgument(0)));
      return mv;
   }

   private static MVDef mvdef(MVColumn... columns) {
      MVDef def = new MVDef();
      // otherwise the columns are loaded from the MV manager
      def.container = new MVDef.MVContainer(new ArrayList<>(Arrays.asList(columns)), null);
      Object old = ThreadContext.getSessionInfo("data.cache.ts");

      try {
         ThreadContext.setSessionInfo("data.cache.ts", LAST_UPDATE);
         def.updateLastUpdateTime();
      }
      finally {
         ThreadContext.setSessionInfo("data.cache.ts", old);
      }

      assertEquals(LAST_UPDATE, def.getLastUpdateTime());
      return def;
   }

   private static TableAssembly table(Worksheet ws, String name) {
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, name);
      ws.addAssembly(table);
      return table;
   }

   private static ColumnRef ref(String column, String type) {
      ColumnRef ref = new ColumnRef(new AttributeRef(null, column));
      ref.setDataType(type);
      return ref;
   }

   private static ConditionList conditions(String column, String type, int op, Object value) {
      ConditionList list = new ConditionList();
      append(list, column, type, op, value);
      return list;
   }

   private static void append(ConditionList list, String column, String type, int op,
                              Object value)
   {
      AssetCondition cond = new AssetCondition(type);
      cond.setOperation(op);
      cond.addValue(value);
      list.append(new ConditionItem(ref(column, type), cond, 0));
   }

   private static ConditionList period(String column, String type, Date from, Date to) {
      PeriodCondition cond = new PeriodCondition();
      cond.setFrom(from);
      cond.setTo(to);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(ref(column, type), cond, 0));
      return list;
   }

   // a local time, the date conditions are converted in the default time zone
   private static Date date(int year, int month, int day, int hour, int minute) {
      Calendar cal = Calendar.getInstance();
      cal.clear();
      cal.set(year, month - 1, day, hour, minute, 0);
      return cal.getTime();
   }

   private static ExpressionValue expression(String script) {
      ExpressionValue exp = new ExpressionValue();
      exp.setType(ExpressionValue.JAVASCRIPT);
      exp.setExpression(script);
      return exp;
   }

   /**
    * A filter as "column op value", a date as its time in ms, a set as its items joined by its
    * relation in parentheses.
    */
   private static String describe(XNode node) {
      if(node instanceof XSet set) {
         List<String> items = new ArrayList<>();

         for(int i = 0; i < set.getChildCount(); i++) {
            items.add(describe(set.getChild(i)));
         }

         return "(" + String.join(" " + set.getRelation() + " ", items) + ")";
      }

      XBinaryCondition cond = (XBinaryCondition) node;
      return (cond.isIsNot() ? "not " : "") + cond.getExpression1().getValue() + " " +
         cond.getOp() + " " + value(cond.getExpression2().getValue());
   }

   private static String value(Object value) {
      if(value instanceof Object[] values) {
         List<String> items = new ArrayList<>();

         for(Object v : values) {
            items.add(value(v));
         }

         return items.toString();
      }

      return value instanceof Date date ? String.valueOf(date.getTime()) : String.valueOf(value);
   }
}
