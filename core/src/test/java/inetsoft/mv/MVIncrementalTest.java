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

import inetsoft.mv.data.MV;
import inetsoft.mv.data.MVConditionListHandler;
import inetsoft.mv.data.MVStorage;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The append condition of an incremental MV update: {@code MVIncremental.mergeAppendCondition}
 * ANDs the MV update conditions of a table with its own conditions, also for the tables under a
 * composed table, and replaces an {@code MV.} expression by its value. The MV's last update
 * time is fixed, so the result does not depend on the current date.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  PluginsTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class MVIncrementalTest {
   // 2021-03-04 05:06:07 UTC
   private static final long LAST_UPDATE = 1614834367000L;

   @Test
   void updateConditionsAreAndedWithTheTableConditions() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly table = table(ws, "T");
      table.setPreConditionList(conditions("state", XCondition.EQUAL_TO, "CA"));
      table.setPostConditionList(conditions("total", XCondition.GREATER_THAN, "10"));
      table.setMVUpdatePreConditionList(conditions("id", XCondition.GREATER_THAN, "100"));
      table.setMVUpdatePostConditionList(conditions("total", XCondition.LESS_THAN, "500"));

      merge(mvdef(LAST_UPDATE), table);

      assertEquals(List.of("id > 100", "AND", "state = CA"),
                   describe(table.getPreConditionList()));
      assertEquals(List.of("total < 500", "AND", "total > 10"),
                   describe(table.getPostConditionList()));
   }

   @Test
   void updateConditionsAloneBecomeTheTableConditions() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly table = table(ws, "T");
      table.setMVUpdatePreConditionList(conditions("id", XCondition.GREATER_THAN, "100"));

      merge(mvdef(LAST_UPDATE), table);

      assertEquals(List.of("id > 100"), describe(table.getPreConditionList()));
      assertEquals(List.of(), describe(table.getPostConditionList()));
   }

   @Test
   void updateConditionsOfTheTablesUnderAComposedTableAreMerged() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly child = table(ws, "C");
      child.setPreConditionList(conditions("state", XCondition.EQUAL_TO, "CA"));
      child.setMVUpdatePreConditionList(conditions("id", XCondition.GREATER_THAN, "100"));
      MirrorTableAssembly mirror = new MirrorTableAssembly(ws, "M", child);
      ws.addAssembly(mirror);
      mirror.setMVUpdatePreConditionList(conditions("total", XCondition.LESS_THAN, "500"));

      merge(mvdef(LAST_UPDATE), mirror);

      assertEquals(List.of("total < 500"), describe(mirror.getPreConditionList()));
      // the child is the table the mirror reads, so the condition is pushed to the database
      assertEquals(List.of("id > 100", "AND", "state = CA"),
                   describe(((TableAssembly) ws.getAssembly("C")).getPreConditionList()));
   }

   @Test
   void lastUpdateTimeExpressionIsReplacedByTheFixedTime() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly table = table(ws, "T");
      table.setMVUpdatePreConditionList(
         conditions("orderdate", XSchema.TIME_INSTANT, XCondition.GREATER_THAN,
                    expression("MV.LastUpdateTime")));

      merge(mvdef(LAST_UPDATE), table);

      ConditionList pre = table.getPreConditionList().getConditionList();
      assertEquals(1, pre.getSize());
      Object value = pre.getConditionItem(0).getXCondition() instanceof AssetCondition cond ?
         cond.getValue(0) : null;
      assertInstanceOf(Date.class, value);
      assertEquals(LAST_UPDATE, ((Date) value).getTime());
   }

   @Test
   void expressionWithoutMVIsLeftForTheQuery() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly table = table(ws, "T");
      ExpressionValue exp = expression("new Date(2020, 0, 1)");
      table.setMVUpdatePreConditionList(
         conditions("orderdate", XSchema.TIME_INSTANT, XCondition.GREATER_THAN, exp));

      merge(mvdef(LAST_UPDATE), table);

      ConditionList pre = table.getPreConditionList().getConditionList();
      AssetCondition cond = (AssetCondition) pre.getConditionItem(0).getXCondition();
      assertInstanceOf(ExpressionValue.class, cond.getValue(0));
      assertEquals("new Date(2020, 0, 1)", ((ExpressionValue) cond.getValue(0)).getExpression());
   }

   private static void merge(MVDef def, TableAssembly table) throws Exception {
      MV mv = Mockito.mock(MV.class);
      Mockito.when(mv.getDef()).thenReturn(Mockito.mock(MVDef.class));
      MVStorage storage = Mockito.mock(MVStorage.class);
      Mockito.when(storage.get(Mockito.anyString())).thenReturn(mv);

      try(MockedStatic<MVStorage> statics = Mockito.mockStatic(MVStorage.class)) {
         statics.when(() -> MVStorage.getFile(Mockito.any())).thenReturn("f");
         statics.when(MVStorage::getInstance).thenReturn(storage);
         TestIncremental incremental = new TestIncremental(def);
         MVConditionListHandler handler =
            new MVConditionListHandler(table, mv, new VariableTable(), def);
         incremental.mergeAppendCondition(table, handler);
      }
   }

   private static MVDef mvdef(long lastUpdate) {
      MVDef def = new MVDef();
      // a definition without columns; otherwise the columns are loaded from the MV manager
      def.container = new MVDef.MVContainer(new ArrayList<>(), null);
      Object old = ThreadContext.getSessionInfo("data.cache.ts");

      try {
         ThreadContext.setSessionInfo("data.cache.ts", lastUpdate);
         def.updateLastUpdateTime();
      }
      finally {
         ThreadContext.setSessionInfo("data.cache.ts", old);
      }

      assertEquals(lastUpdate, def.getLastUpdateTime());
      return def;
   }

   private static TableAssembly table(Worksheet ws, String name) {
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, name);
      ws.addAssembly(table);
      return table;
   }

   private static ConditionList conditions(String column, int op, Object value) {
      return conditions(column, XSchema.STRING, op, value);
   }

   private static ConditionList conditions(String column, String type, int op, Object value) {
      AssetCondition cond = new AssetCondition(type);
      cond.setOperation(op);
      cond.addValue(value);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(new AttributeRef(null, column), cond, 0));
      return list;
   }

   private static ExpressionValue expression(String script) {
      ExpressionValue exp = new ExpressionValue();
      exp.setType(ExpressionValue.JAVASCRIPT);
      exp.setExpression(script);
      return exp;
   }

   /**
    * The items of a condition list, a condition as "column op value", a junction as its name.
    */
   private static List<String> describe(ConditionListWrapper wrapper) {
      List<String> items = new ArrayList<>();
      ConditionList list = wrapper == null ? null : wrapper.getConditionList();

      for(int i = 0; list != null && i < list.getSize(); i++) {
         if(list.isConditionItem(i)) {
            ConditionItem item = list.getConditionItem(i);
            Condition cond = (Condition) item.getXCondition();
            items.add(item.getAttribute().getAttribute() + " " + operator(cond) + " " +
                         cond.getValue(0));
         }
         else {
            items.add(list.getJunctionOperator(i).getJunction() == JunctionOperator.AND ?
                         "AND" : "OR");
         }
      }

      return items;
   }

   private static String operator(Condition cond) {
      return switch(cond.getOperation()) {
         case XCondition.EQUAL_TO -> "=";
         case XCondition.GREATER_THAN -> ">";
         case XCondition.LESS_THAN -> "<";
         default -> String.valueOf(cond.getOperation());
      };
   }

   private static final class TestIncremental extends MVIncremental {
      TestIncremental(MVDef def) {
         super(def);
      }

      @Override
      public void update() {
      }

      @Override
      public void cancel() {
      }
   }
}
