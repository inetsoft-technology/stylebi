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

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.IntegrationTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.AssetCondition;
import inetsoft.uql.asset.ExpressionValue;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.jdbc.XBinaryCondition;
import inetsoft.uql.jdbc.XExpression;
import inetsoft.uql.jdbc.XFilterNode;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.script.ScriptException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Redmine 78271: a JavaScript condition value that throws, on a table whose condition is merged
 * into SQL, was swallowed by {@code PreConditionListHandler.createCondNode} and replaced with the
 * always-true {@code field = field}, silently returning unfiltered rows.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, IntegrationTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PreConditionCreateCondNodeTest {
   private static XFilterNode createCondNode(String script, XExpression field) throws Throwable {
      AssetQuerySandbox box = new AssetQuerySandbox(new Worksheet(), null, new VariableTable());
      box.setActive(true);

      PreAssetQuery query = Mockito.mock(PreAssetQuery.class, Mockito.CALLS_REAL_METHODS);
      setField(query, "box", box);
      setField(query, "mode", AssetQuerySandbox.LIVE_MODE);

      AssetCondition cond = new AssetCondition();
      cond.setOperation(XCondition.GREATER_THAN);
      cond.setType(XSchema.INTEGER);
      ExpressionValue value = new ExpressionValue();
      value.setExpression(script);
      value.setType(ExpressionValue.JAVASCRIPT);
      cond.addValue(value);

      Class<?> type = Class.forName(PreAssetQuery.class.getName() + "$PreConditionListHandler");
      Constructor<?> ctor = type.getDeclaredConstructor(PreAssetQuery.class);
      ctor.setAccessible(true);
      Object handler = ctor.newInstance(query);
      Field sql = inetsoft.uql.jdbc.util.ConditionListHandler.class.getDeclaredField("sql");
      sql.setAccessible(true);
      sql.set(handler, new inetsoft.uql.jdbc.UniformSQL());
      Method create = type.getDeclaredMethod(
         "createCondNode", inetsoft.uql.Condition.class, XExpression.class, VariableTable.class);
      create.setAccessible(true);

      try {
         return (XFilterNode) create.invoke(handler, cond, field, new VariableTable());
      }
      catch(InvocationTargetException ex) {
         throw ex.getCause();
      }
   }

   private static void setField(Object obj, String name, Object value) throws Exception {
      Field field = PreAssetQuery.class.getDeclaredField(name);
      field.setAccessible(true);
      field.set(obj, value);
   }

   @Test
   void throwingScriptConditionIsNotSwallowedIntoAlwaysTrue() {
      XExpression field = new XExpression("QUANTITY", XExpression.FIELD);

      assertThrows(ScriptException.class, () -> createCondNode("undefinedFn(1)", field));
   }

   @Test
   void workingScriptConditionStillBuildsARealCondition() throws Throwable {
      XExpression field = new XExpression("QUANTITY", XExpression.FIELD);

      XFilterNode node = createCondNode("5+5", field);

      assertNotNull(node);
      XBinaryCondition binary = assertInstanceOf(XBinaryCondition.class, node);
      assertNotSame(field, binary.getExpression2(), "must not be the field = field tautology");
   }
}
