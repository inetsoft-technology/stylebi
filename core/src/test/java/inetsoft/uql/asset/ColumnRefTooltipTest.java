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
package inetsoft.uql.asset;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.ExpressionRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #81291: {@link ColumnRef#getTooltip(ColumnRef)} is the server-side counterpart of the
 * worksheet Expression dialog's "show original names" column tree tooltip. For an attribute
 * column {@code getAttributeView} resolves a non-null view and the tooltip is built from it, but
 * for an expression (formula) column it is always null, and the method used to return the empty
 * string without ever consulting the column's own description - dropping a formula column's
 * description from the tooltip in that mode even though {@code col.getDescription()} carries it.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ColumnRefTooltipTest {
   @Test
   void expressionColumnWithDescriptionFallsBackToDescription() {
      ExpressionRef expr = new ExpressionRef(null, "myCol");
      expr.setExpression("field['x'] + 1");
      ColumnRef col = new ColumnRef(expr);
      col.setDescription("Twice the amount");

      assertEquals("Twice the amount", ColumnRef.getTooltip(col));
   }

   @Test
   void expressionColumnWithNoDescriptionStaysEmpty() {
      ExpressionRef expr = new ExpressionRef(null, "myCol");
      expr.setExpression("field['x'] + 1");
      ColumnRef col = new ColumnRef(expr);

      assertEquals("", ColumnRef.getTooltip(col));
   }

   @Test
   void plainAttributeColumnTooltipIsUnchanged() {
      AttributeRef attr = new AttributeRef("col");
      ColumnRef col = new ColumnRef(attr);
      col.setDescription("A plain column");

      assertEquals("col\nA plain column", ColumnRef.getTooltip(col));
   }

   /**
    * {@code ref.isExpression()} is inherited, unconditionally, by every subclass of
    * {@link ExpressionRef} - including {@link DateRangeRef} and {@link NumericRangeRef}, neither
    * of which overrides it. A date-range-grouped column built over an expression (rather than a
    * plain attribute) column has no resolvable attribute view either, so the fix's fallback
    * reaches this shape too, not just a bare formula column. This is a deliberate, known widening
    * beyond the literally reported case (a plain formula column) - pinned down here rather than
    * left as an untested side effect.
    */
   @Test
   void dateRangeOverExpressionColumnAlsoFallsBackToDescription() {
      ExpressionRef inner = new ExpressionRef(null, "innerCol");
      inner.setExpression("field['dateExpr']");
      DateRangeRef dateRange = new DateRangeRef("year", inner, DateRangeRef.YEAR_INTERVAL);
      ColumnRef col = new ColumnRef(dateRange);
      col.setDescription("Grouped by year over an expression");

      assertEquals("Grouped by year over an expression", ColumnRef.getTooltip(col));
   }
}
