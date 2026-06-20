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
package inetsoft.web.wiz.service;

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.AbstractTableAssembly;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.web.wiz.model.WorksheetConstructionModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("core")
class GenerateWsServiceFieldTest {
   @Test
   void expressionNameUsesFieldNameWhenPresent() {
      WorksheetConstructionModel.QueryField field = new WorksheetConstructionModel.QueryField();
      field.setExpression("concat(field['a'], field['b'])");
      field.setFieldName("full_name");
      field.setAlias("other_alias");

      assertEquals("full_name", GenerateWsService.resolveExpressionName(field));
   }

   @Test
   void expressionNameFallsBackToAlias() {
      WorksheetConstructionModel.QueryField field = new WorksheetConstructionModel.QueryField();
      field.setExpression("concat(field['a'], field['b'])");
      field.setFieldName("");
      field.setAlias("full_name");

      assertEquals("full_name", GenerateWsService.resolveExpressionName(field));
   }

   @Test
   void expressionWithoutNameOrAliasThrows() {
      WorksheetConstructionModel.QueryField field = new WorksheetConstructionModel.QueryField();
      field.setExpression("concat(field['a'], field['b'])");

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> GenerateWsService.resolveExpressionName(field));
      assertEquals(true, e.getMessage().contains("fieldName or alias"));
   }

   @Test
   void synthesizedJoinKeyIsInvisible() {
      WorksheetConstructionModel.SourceInfo source = new WorksheetConstructionModel.SourceInfo();
      source.setType("DATABASE");
      source.setPath("postgres");
      source.setSchema("public");
      WorksheetConstructionModel.TableInfo table = new WorksheetConstructionModel.TableInfo();
      table.setName("rental");
      table.setSource(source);

      List<WorksheetConstructionModel.QueryField> fields = new ArrayList<>();
      GenerateWsService.addJoinKeyField(fields, table, "rental_id");

      assertEquals(1, fields.size());
      assertEquals("rental_id", fields.get(0).getFieldName());
      assertEquals(Boolean.FALSE, fields.get(0).getVisible());
   }

   @Test
   void declaredJoinKeyKeepsItsVisibility() {
      WorksheetConstructionModel.SourceInfo source = new WorksheetConstructionModel.SourceInfo();
      source.setType("DATABASE");
      source.setPath("postgres");
      source.setSchema("public");
      WorksheetConstructionModel.TableInfo table = new WorksheetConstructionModel.TableInfo();
      table.setName("rental");
      table.setSource(source);

      WorksheetConstructionModel.QueryField declared =
         new WorksheetConstructionModel.QueryField(table, "rental_id");
      declared.setVisible(true);
      List<WorksheetConstructionModel.QueryField> fields =
         new ArrayList<>(List.of(declared));

      GenerateWsService.addJoinKeyField(fields, table, "rental_id");

      assertEquals(1, fields.size());            // not re-added
      assertEquals(Boolean.TRUE, fields.get(0).getVisible());  // untouched
   }

   /**
    * Regression for the join key-swap bug: a synthesized join key is invisible
    * (addJoinKeyField → setVisible(false)), and setColumnSelection excludes invisible
    * columns from the PUBLIC selection. So resolving a join key against the public
    * selection returns null — the operator then carries a null attribute and the join
    * engine mis-pairs the keys to the wrong tables. The fix resolves keys against the
    * PRIVATE selection, which retains the hidden key.
    */
   @Test
   void hiddenJoinKeyResolvesAgainstPrivateSelection() {
      // PRIVATE selection keeps the (invisible) join key; PUBLIC selection drops it.
      ColumnSelection privateSel = new ColumnSelection();
      privateSel.addAttribute(new ColumnRef(new AttributeRef(null, "total_price")));
      privateSel.addAttribute(new ColumnRef(new AttributeRef(null, "supplier_id")));

      ColumnSelection publicSel = new ColumnSelection();
      publicSel.addAttribute(new ColumnRef(new AttributeRef(null, "total_price")));

      AbstractTableAssembly table = mock(AbstractTableAssembly.class);
      when(table.getColumnSelection(false)).thenReturn(privateSel);
      when(table.getColumnSelection(true)).thenReturn(publicSel);

      // The trap the bug fell into: resolving against the PUBLIC selection returns null.
      assertNull(table.getColumnSelection(true).getAttribute("supplier_id"),
                 "hidden join key must be excluded from the public selection");
      // The fix: resolveJoinKeyAttribute reads the PRIVATE selection, which keeps it.
      assertNotNull(GenerateWsService.resolveJoinKeyAttribute(table, "supplier_id"),
                    "join key must resolve against the private selection");
      // A visible column resolves regardless.
      assertNotNull(GenerateWsService.resolveJoinKeyAttribute(table, "total_price"));
   }
}
