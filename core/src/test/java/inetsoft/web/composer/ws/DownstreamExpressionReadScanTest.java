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
package inetsoft.web.composer.ws;

import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.ExpressionRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78679 / WBS-131: the remove_column guard for a downstream expression column. The runtime
 * column lookup ignores case, so {@code field.category_id} reads CATEGORY_ID and removing it must
 * be refused like the exact-case read; an exact column of the same spelling still wins.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DownstreamExpressionReadScanTest {
   private static TableAssembly table(ColumnRef... columns) {
      ColumnSelection cs = new ColumnSelection();

      for(ColumnRef column : columns) {
         cs.addAttribute(column);
      }

      TableAssembly table = mock(TableAssembly.class);
      when(table.getColumnSelection()).thenReturn(cs);
      return table;
   }

   private static ColumnRef expression(String name, String expr) {
      ExpressionRef ref = new ExpressionRef(null, name);
      ref.setExpression(expr);
      return new ColumnRef(ref);
   }

   @ParameterizedTest
   @ValueSource(strings = {
      "field.category_id * 2",
      "field['Category_Id'] * 2",
      "field[\"category_id\"] * 2",
      "field['categories.category_id'] * 2",
      "field['CATEGORIES.CATEGORY_ID'] * 2",
   })
   void seesACaseDifferentRead(String expr) {
      ColumnRef id = new ColumnRef(new AttributeRef("categories", "CATEGORY_ID"));
      ColumnRef dbl = expression("Dbl", expr);

      assertEquals(List.of("expression column 'Dbl'"),
         WorksheetControllerService.findOwnDerivedColumnReferences(table(id, dbl), id), expr);
   }

   @Test
   void seesACaseDifferentReadOfAnAlias() {
      ColumnRef id = new ColumnRef(new AttributeRef("categories", "CATEGORY_ID"));
      id.setAlias("CID");
      ColumnRef dbl = expression("Dbl", "field.cid * 2");

      assertEquals(List.of("expression column 'Dbl'"),
         WorksheetControllerService.findOwnDerivedColumnReferences(table(id, dbl), id));
   }

   @Test
   void keepsTheExactCaseControlAndIgnoresUnrelatedNames() {
      ColumnRef id = new ColumnRef(new AttributeRef("categories", "CATEGORY_ID"));

      assertEquals(1, WorksheetControllerService.findOwnDerivedColumnReferences(
         table(id, expression("Dbl", "field.CATEGORY_ID * 2")), id).size());
      assertTrue(WorksheetControllerService.findOwnDerivedColumnReferences(
         table(id, expression("Dbl", "field.category_id2 * 2")), id).isEmpty());
   }

   /** With a real column category_id too, field.category_id reads THAT column, not CATEGORY_ID. */
   @Test
   void letsAnExactColumnWin() {
      ColumnRef upper = new ColumnRef(new AttributeRef("categories", "CATEGORY_ID"));
      ColumnRef lower = new ColumnRef(new AttributeRef("categories", "category_id"));
      ColumnRef dbl = expression("Dbl", "field.category_id * 2");
      TableAssembly table = table(upper, lower, dbl);

      assertTrue(WorksheetControllerService.findOwnDerivedColumnReferences(table, upper).isEmpty());
      assertEquals(1, WorksheetControllerService.findOwnDerivedColumnReferences(table, lower).size());
   }
}
