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
package inetsoft.report.composition.execution;

import inetsoft.report.filter.ConditionGroup;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.IntegrationTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.AbstractCondition;
import inetsoft.uql.Condition;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.AssetCondition;
import inetsoft.uql.asset.ExpressionValue;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.jdbc.UniformSQL;
import inetsoft.uql.jdbc.XBinaryCondition;
import inetsoft.uql.jdbc.XExpression;
import inetsoft.uql.jdbc.util.ConditionListHandler;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Redmine 79204: a fractional comparison value (e.g. 4.5) on an integer column was truncated to
 * the column type, either by a cast in StyleBI (script values, {@code =}) or by the database
 * driver narrowing a bound parameter (Derby, HSQLDB), so {@code QUANTITY >= 4.5} acted as
 * {@code QUANTITY >= 4}. The fractional value is now kept and compared as a numeric literal.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, IntegrationTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class FractionalIntegralConditionTest {
   private static AssetCondition condition(String type, int op, Object value) {
      AssetCondition cond = new AssetCondition();
      cond.setOperation(op);
      cond.setType(type);
      cond.addValue(value);
      return cond;
   }

   private static ExpressionValue script(String js) {
      ExpressionValue value = new ExpressionValue();
      value.setExpression(js);
      value.setType(ExpressionValue.JAVASCRIPT);
      return value;
   }

   private static XExpression field() {
      XExpression field = new XExpression("Q", XExpression.FIELD);
      field.setSqlType(Types.INTEGER);
      return field;
   }

   private static Object baseHandler() throws Exception {
      return handler(ConditionListHandler.class, null);
   }

   /**
    * The handler used for a worksheet/viewsheet condition merged into SQL; it overrides
    * getExpression and ends in the base class's untyped overload.
    */
   private static Object preHandler() throws Exception {
      AssetQuerySandbox box = new AssetQuerySandbox(new Worksheet(), null, new VariableTable());
      box.setActive(true);

      PreAssetQuery query = Mockito.mock(PreAssetQuery.class, Mockito.CALLS_REAL_METHODS);
      setQueryField(query, "box", box);
      setQueryField(query, "mode", AssetQuerySandbox.LIVE_MODE);

      Class<?> type = Class.forName(PreAssetQuery.class.getName() + "$PreConditionListHandler");
      return handler(type, query);
   }

   private static Object handler(Class<?> type, PreAssetQuery query) throws Exception {
      Object handler;

      if(query == null) {
         handler = type.getDeclaredConstructor().newInstance();
      }
      else {
         Constructor<?> ctor = type.getDeclaredConstructor(PreAssetQuery.class);
         ctor.setAccessible(true);
         handler = ctor.newInstance(query);
      }

      Field sql = ConditionListHandler.class.getDeclaredField("sql");
      sql.setAccessible(true);
      sql.set(handler, new UniformSQL());
      return handler;
   }

   private static XBinaryCondition createCondNode(Object handler, Condition cond,
                                                  VariableTable vars)
      throws Throwable
   {
      Method create = ConditionListHandler.class.getDeclaredMethod(
         "createCondNode", Condition.class, XExpression.class, VariableTable.class);
      create.setAccessible(true);

      try {
         return (XBinaryCondition) create.invoke(handler, cond, field(), vars);
      }
      catch(InvocationTargetException ex) {
         throw ex.getCause();
      }
   }

   private static void setQueryField(Object obj, String name, Object value) throws Exception {
      Field field = PreAssetQuery.class.getDeclaredField(name);
      field.setAccessible(true);
      field.set(obj, value);
   }

   private static Object conditionGroupScriptValue(Object val, String type) throws Exception {
      Method method = ConditionGroup.class.getDeclaredMethod(
         "getScriptValue", Object.class, String.class);
      method.setAccessible(true);
      return method.invoke(new ConditionGroup(), val, type);
   }

   private static Object preAssetQueryScriptValue(Object val, String type) {
      return PreAssetQuery.getScriptValue(val, condition(type, XCondition.GREATER_THAN, 0));
   }

   private static boolean bound(XBinaryCondition node, VariableTable vars) {
      boolean variable = node.getExpression2().getValue().toString().startsWith("$(");
      assertEquals(variable, vars.keys().hasMoreElements());
      return variable;
   }

   @Test
   void predicateOnlyMatchesFiniteFractionalNumberOnIntegralType() {
      for(String type : new String[] { XSchema.BYTE, XSchema.SHORT, XSchema.INTEGER, XSchema.LONG }) {
         assertTrue(AbstractCondition.isFractionalIntegralValue(type, 4.5), type);
         assertTrue(AbstractCondition.isFractionalIntegralValue(type, -4.5f), type);
         assertTrue(AbstractCondition.isFractionalIntegralValue(type, new BigDecimal("4.5")), type);
         assertFalse(AbstractCondition.isFractionalIntegralValue(type, 5.0), type);
         assertFalse(AbstractCondition.isFractionalIntegralValue(type, 5), type);
         assertFalse(AbstractCondition.isFractionalIntegralValue(type, 5L), type);
         assertFalse(AbstractCondition.isFractionalIntegralValue(type, new BigDecimal("5.00")), type);
         assertFalse(AbstractCondition.isFractionalIntegralValue(type, Double.NaN), type);
         assertFalse(AbstractCondition.isFractionalIntegralValue(type, Double.POSITIVE_INFINITY), type);
         assertFalse(AbstractCondition.isFractionalIntegralValue(type, "4.5"), type);
         assertFalse(AbstractCondition.isFractionalIntegralValue(type, null), type);
      }

      assertFalse(AbstractCondition.isFractionalIntegralValue(XSchema.DOUBLE, 4.5));
      assertFalse(AbstractCondition.isFractionalIntegralValue(XSchema.FLOAT, 4.5));
      assertFalse(AbstractCondition.isFractionalIntegralValue(XSchema.STRING, 4.5));
      assertFalse(AbstractCondition.isFractionalIntegralValue(XSchema.DATE, 4.5));
      assertFalse(AbstractCondition.isFractionalIntegralValue(null, 4.5));
   }

   @Test
   void baseHandlerInlinesFractionalValueForRangeOperators() throws Throwable {
      for(int op : new int[] { XCondition.GREATER_THAN, XCondition.LESS_THAN }) {
         VariableTable vars = new VariableTable();
         XBinaryCondition node = createCondNode(
            baseHandler(), condition(XSchema.INTEGER, op, 4.5), vars);

         assertEquals("4.5", node.getExpression2().getValue());
         assertFalse(bound(node, vars), "a literal must not be bound");
      }
   }

   @Test
   void baseHandlerKeepsBindingForWholeAndNonIntegralTypedValues() throws Throwable {
      Object[][] cases = {
         { XSchema.INTEGER, 5 }, { XSchema.INTEGER, 5.0 }, { XSchema.INTEGER, Double.NaN },
         { XSchema.INTEGER, Double.POSITIVE_INFINITY }, { XSchema.DOUBLE, 4.5 },
         { XSchema.STRING, 4.5 }
      };

      for(Object[] c : cases) {
         VariableTable vars = new VariableTable();
         XBinaryCondition node = createCondNode(
            baseHandler(), condition((String) c[0], XCondition.GREATER_THAN, c[1]), vars);

         assertTrue(bound(node, vars), c[0] + " " + c[1]);
      }
   }

   @Test
   void baseHandlerEqualityKeepsFractionalValueOnIntegerSqlColumn() throws Throwable {
      VariableTable vars = new VariableTable();
      XBinaryCondition node = createCondNode(
         baseHandler(), condition(XSchema.INTEGER, XCondition.EQUAL_TO, 4.5), vars);

      assertEquals("4.5", node.getExpression2().getValue());
      assertFalse(bound(node, vars));
   }

   @Test
   void baseHandlerEqualityStillCastsStringToInteger() throws Throwable {
      VariableTable vars = new VariableTable();
      XBinaryCondition node = createCondNode(
         baseHandler(), condition(XSchema.INTEGER, XCondition.EQUAL_TO, "4"), vars);

      assertTrue(bound(node, vars));
      assertEquals(4, vars.get(vars.keys().nextElement()));
   }

   @Test
   void preHandlerInlinesFractionalLiteral() throws Throwable {
      VariableTable vars = new VariableTable();
      XBinaryCondition node = createCondNode(
         preHandler(), condition(XSchema.INTEGER, XCondition.GREATER_THAN, 4.5), vars);

      assertEquals("4.5", node.getExpression2().getValue());
      assertFalse(bound(node, vars));
   }

   @Test
   void preHandlerInlinesFractionalScriptResult() throws Throwable {
      VariableTable vars = new VariableTable();
      XBinaryCondition node = createCondNode(
         preHandler(), condition(XSchema.INTEGER, XCondition.GREATER_THAN, script("4.5")), vars);

      assertEquals("4.5", node.getExpression2().getValue());
      assertFalse(bound(node, vars));
   }

   @Test
   void preHandlerStillBindsWholeScriptResult() throws Throwable {
      VariableTable vars = new VariableTable();
      XBinaryCondition node = createCondNode(
         preHandler(), condition(XSchema.INTEGER, XCondition.GREATER_THAN, script("2+3")), vars);

      assertTrue(bound(node, vars));
      assertEquals(5, vars.get(vars.keys().nextElement()));
   }

   @Test
   void conditionGroupScriptValueKeepsFractionalNumber() throws Exception {
      assertEquals(4.5, conditionGroupScriptValue(4.5, XSchema.INTEGER));
      assertEquals(4.5, conditionGroupScriptValue(4.5, XSchema.LONG));
   }

   @Test
   void conditionGroupScriptValueStillConvertsOtherValues() throws Exception {
      assertEquals(4, conditionGroupScriptValue("4", XSchema.INTEGER));
      assertEquals(5, conditionGroupScriptValue(5.0, XSchema.INTEGER));
      assertEquals(4.5, conditionGroupScriptValue(4.5, XSchema.DOUBLE));
      assertEquals("4.5", conditionGroupScriptValue(4.5, XSchema.STRING));
   }

   @Test
   void preAssetQueryScriptValueKeepsFractionalNumber() {
      assertEquals(4.5, preAssetQueryScriptValue(4.5, XSchema.INTEGER));
      assertEquals(4.5, preAssetQueryScriptValue(4.5, XSchema.LONG));
   }

   @Test
   void preAssetQueryScriptValueStillConvertsOtherValues() {
      assertEquals(4, preAssetQueryScriptValue("4", XSchema.INTEGER));
      assertEquals(5, preAssetQueryScriptValue(5.0, XSchema.INTEGER));
      assertEquals(4.5, preAssetQueryScriptValue(4.5, XSchema.DOUBLE));
      assertEquals("4.5", preAssetQueryScriptValue(4.5, XSchema.STRING));
   }

   @Test
   void preAssetQueryScriptValueKeepsFractionalArrayElements() {
      Object[] values = { 4.5, "6" };
      Condition cond = condition(XSchema.INTEGER, XCondition.ONE_OF, 0);
      PreAssetQuery.getScriptValue(values, cond);

      assertEquals(4.5, values[0]);
      assertEquals(6, values[1]);
   }

   private static Condition saveAndReload(Condition cond) throws Exception {
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      cond.writeXML(writer);
      writer.flush();

      Condition loaded = new AssetCondition();
      loaded.parseXML(Tool.parseXML(
         new ByteArrayInputStream(buf.toString().getBytes(StandardCharsets.UTF_8)))
                         .getDocumentElement());
      return loaded;
   }

   @Test
   void savedConditionKeepsFractionalValue() throws Exception {
      for(String type : new String[] { XSchema.SHORT, XSchema.INTEGER, XSchema.LONG }) {
         assertEquals(4.5, saveAndReload(condition(type, XCondition.GREATER_THAN, 4.5)).getValue(0),
                      type);
         assertEquals(-4.5, saveAndReload(condition(type, XCondition.LESS_THAN, -4.5)).getValue(0),
                      type);
      }
   }

   @Test
   void savedConditionStillReadsWholeValueAsIntegerType() throws Exception {
      assertEquals(5, saveAndReload(condition(XSchema.INTEGER, XCondition.GREATER_THAN, 5)).getValue(0));
      assertEquals(5, saveAndReload(condition(XSchema.INTEGER, XCondition.GREATER_THAN, 5.0)).getValue(0));
      assertEquals(4.5, saveAndReload(condition(XSchema.DOUBLE, XCondition.GREATER_THAN, 4.5)).getValue(0));
   }

   @Test
   void storedStringValueKeepsFractionForIntegralType() {
      assertEquals(4.5, condition(XSchema.INTEGER, XCondition.GREATER_THAN, "4.5").getValue(0));
      assertEquals(4, condition(XSchema.INTEGER, XCondition.GREATER_THAN, "4").getValue(0));
   }

   /**
    * Runs the SQL produced by the PreAssetQuery condition handler against Derby (the engine
    * behind the Examples data source), which narrows a bound 4.5 to 4 for an integer column.
    */
   @Test
   void generatedSqlFiltersOnTheFractionalValueInDerby() throws Throwable {
      try(Connection conn = DriverManager.getConnection("jdbc:derby:memory:b79204;create=true")) {
         try(Statement stmt = conn.createStatement()) {
            stmt.execute("create table t (q integer)");
         }

         try(PreparedStatement ps = conn.prepareStatement("insert into t values (?)")) {
            for(int i = 1; i <= 20; i++) {
               ps.setInt(1, i);
               ps.executeUpdate();
            }
         }

         assertEquals("16/5/20", query(conn, XCondition.GREATER_THAN, true, 4.5));
         assertEquals("4/1/4", query(conn, XCondition.LESS_THAN, false, 4.5));
         assertEquals("0/0/0", query(conn, XCondition.EQUAL_TO, false, 4.5));
         assertEquals("16/5/20", query(conn, XCondition.GREATER_THAN, true, script("4.5")));
         assertEquals("4/1/4", query(conn, XCondition.LESS_THAN, false, script("4.5")));
         assertEquals("0/0/0", query(conn, XCondition.EQUAL_TO, false, script("4.5")));
         assertEquals("16/5/20", query(conn, XCondition.GREATER_THAN, true, 5));
      }
   }

   private static String query(Connection conn, int op, boolean equal, Object value)
      throws Throwable
   {
      AssetCondition cond = condition(XSchema.INTEGER, op, value);
      cond.setEqual(equal);
      VariableTable vars = new VariableTable();
      XBinaryCondition node = createCondNode(preHandler(), cond, vars);
      String where = node.toString();

      // the whole-number control is bound; a bound fractional value would fail the assert below
      if(vars.keys().hasMoreElements()) {
         String name = (String) vars.keys().nextElement();
         assertTrue(vars.get(name) instanceof Integer, where);
         where = where.replace("$(" + name + ")", String.valueOf(vars.get(name)));
      }

      assertFalse(where.contains("$("), where);

      try(Statement stmt = conn.createStatement();
          ResultSet rs = stmt.executeQuery(
             "select count(*), coalesce(min(q), 0), coalesce(max(q), 0) from t where " + where))
      {
         rs.next();
         return rs.getInt(1) + "/" + rs.getInt(2) + "/" + rs.getInt(3);
      }
   }
}
