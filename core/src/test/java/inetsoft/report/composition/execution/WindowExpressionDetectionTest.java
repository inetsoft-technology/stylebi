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

import inetsoft.test.*;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.SortRef;
import inetsoft.uql.asset.WindowExpressionRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.ExpressionRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WindowExpressionDetectionTest {

   private ColumnRef expressionColumn(String expr) {
      ExpressionRef ref = new ExpressionRef(null, "name");
      ref.setExpression(expr);
      return new ColumnRef(ref);
   }

   @Test
   void rowNumberOverIsWindowExpression() {
      ColumnRef column = expressionColumn("ROW_NUMBER() OVER (ORDER BY field['x'] DESC)");
      assertTrue(PreAssetQuery.isWindowExpression(column));
   }

   @Test
   void windowExpressionRefColumnIsWindowExpressionByType() {
      AttributeRef orderAttr = new AttributeRef("x");
      SortRef orderBy = new SortRef(orderAttr);
      orderBy.setOrder(XConstants.SORT_DESC);

      WindowExpressionRef winRef = new WindowExpressionRef(
         "ROW_NUMBER", null, 0, Collections.emptyList(), Collections.singletonList(orderBy));
      winRef.setName("rn");
      ColumnRef column = new ColumnRef(winRef);
      column.setSQL(true);

      assertTrue(PreAssetQuery.isWindowExpression(column));
   }

   @Test
   void plainAggregateExpressionIsNotWindowExpression() {
      ColumnRef column = expressionColumn("SUM(field['x'])");
      assertFalse(PreAssetQuery.isWindowExpression(column));
   }

   @Test
   void nonExpressionColumnIsNotWindowExpression() {
      ColumnRef column = new ColumnRef(new AttributeRef("x"));
      assertFalse(PreAssetQuery.isWindowExpression(column));
   }

   @Test
   void identifierContainingOverWithoutParenIsNotWindowExpression() {
      ColumnRef column = expressionColumn("field['takeover']");
      assertFalse(PreAssetQuery.isWindowExpression(column));
   }

   @Test
   void nullColumnIsNotWindowExpression() {
      assertFalse(PreAssetQuery.isWindowExpression(null));
   }
}
