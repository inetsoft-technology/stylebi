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

import inetsoft.web.wiz.model.WorksheetConstructionModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
