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
package inetsoft.report.composition.execution;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.PluginsTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static inetsoft.report.composition.execution.MergeableFixture.integer;
import static org.junit.jupiter.api.Assertions.*;

/**
 * SQL option calculated fields that refer to each other (a = field['b'] + 1, b = field['a'] + 1)
 * are not mergeable and have no SQL column; they must not overflow the stack.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class, PluginsTestConfiguration.class,
                                  MergeableFixture.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PreAssetQuerySqlExpressionCycleTest {
   /** A calculated field with the SQL option. */
   private static ColumnRef sqlColumn(String name, String expression) {
      ExpressionRef exp = new ExpressionRef();
      exp.setName(name);
      exp.setExpression(expression);
      ColumnRef column = new ColumnRef(exp);
      column.setDataType(XSchema.INTEGER);
      column.setSQL(true);
      return column;
   }

   @Test
   void twoCycleIsNotMergeable() throws Exception {
      ColumnRef a = sqlColumn("a", "field['b'] + 1");
      ColumnRef b = sqlColumn("b", "field['a'] + 1");
      MergeableFixture.Built built = MergeableFixture.build("derby", integer("k"), a, b);
      PreAssetQuery query = built.query();

      assertFalse(query.isAttributeMergeable(a));
      assertFalse(query.isAttributeMergeable(b));
      assertNull(query.getColumn(a));
   }

   @Test
   void threeCycleIsNotMergeable() throws Exception {
      ColumnRef a = sqlColumn("a", "field['b'] + 1");
      ColumnRef b = sqlColumn("b", "field['c'] + 1");
      ColumnRef c = sqlColumn("c", "field['a'] + 1");
      MergeableFixture.Built built = MergeableFixture.build("h2", integer("k"), a, b, c);
      PreAssetQuery query = built.query();

      assertFalse(query.isAttributeMergeable(a));
      assertFalse(query.isAttributeMergeable(c));
      assertNull(query.getColumn(b));
   }

   @Test
   void selfReferenceIsNotMergeable() throws Exception {
      ColumnRef a = sqlColumn("a", "field['a'] + 1");
      MergeableFixture.Built built = MergeableFixture.build("derby", integer("k"), a);
      PreAssetQuery query = built.query();

      assertFalse(query.isAttributeMergeable(a));
      assertNull(query.getColumn(a));
   }

   @Test
   void visibleExpressionOverCyclicPairIsNotMergeable() throws Exception {
      ColumnRef a = sqlColumn("a", "field['b'] + 1");
      ColumnRef b = sqlColumn("b", "field['a'] + 1");
      a.setVisible(false);
      b.setVisible(false);
      ColumnRef v = sqlColumn("v", "field['a'] + field['k']");
      MergeableFixture.Built built = MergeableFixture.build("derby", integer("k"), a, b, v);
      PreAssetQuery query = built.query();

      assertFalse(query.isAttributeMergeable(v));
      assertNull(query.getColumn(v));
   }

   @Test
   void acyclicChainStaysMergeable() throws Exception {
      ColumnRef a = sqlColumn("a", "field['k'] + 1");
      ColumnRef b = sqlColumn("b", "field['a'] + 1");
      // a diamond: c uses a and b, which both use k
      ColumnRef c = sqlColumn("c", "field['a'] + field['b']");
      MergeableFixture.Built built = MergeableFixture.build("derby", integer("k"), a, b, c);
      PreAssetQuery query = built.query();

      assertTrue(query.isAttributeMergeable(a));
      assertTrue(query.isAttributeMergeable(b));
      assertTrue(query.isAttributeMergeable(c));
      // the guard is released: asking again gives the same answer and column
      assertTrue(query.isAttributeMergeable(c));
      assertNotNull(query.getColumn(c));
      assertEquals(query.getColumn(c), query.getColumn(c));
   }
}
