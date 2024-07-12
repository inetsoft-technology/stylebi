/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.composer.vs.dialog;

import inetsoft.uql.asset.ColumnRef;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.composer.model.vs.OutputColumnRefModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SelectionDialogServiceTest {
   @BeforeEach
   void setup() {
      service = new SelectionDialogService();
   }

   @Test
   void testColumnIsFound() {
      final String table = "Supplier";
      final String attribute = "State";
      final ColumnRef column = mock(ColumnRef.class);
      when(column.getAttribute()).thenReturn(attribute);

      final OutputColumnRefModel refModel = new OutputColumnRefModel();
      refModel.setTable(table);
      refModel.setAttribute(attribute);

      final TreeNodeModel root = createTree(table, refModel);

      final Optional<OutputColumnRefModel> refModelResult =
         service.findSelectedOutputColumnRefModel(root, table, column);

      assertEquals(refModelResult.orElse(null), refModel);
   }

   @Test
   void testColumnIsNotFoundWhenAttributesDontMatch() {
      final String table = "Supplier";
      final String attributeA = "State";
      final String attributeB = "City";
      final ColumnRef column = mock(ColumnRef.class);
      when(column.getAttribute()).thenReturn(attributeB);

      final OutputColumnRefModel refModel = new OutputColumnRefModel();
      refModel.setTable(table);
      refModel.setAttribute(attributeA);

      final TreeNodeModel root = createTree(table, refModel);

      final Optional<OutputColumnRefModel> refModelResult =
         service.findSelectedOutputColumnRefModel(root, table, column);

      assertFalse(refModelResult.isPresent());
   }

   private TreeNodeModel createTree(String table, OutputColumnRefModel refModel) {
      final TreeNodeModel columnNode = TreeNodeModel.builder().data(refModel).build();
      final TreeNodeModel tableNode = TreeNodeModel.builder()
         .data(table).addChildren(columnNode).build();
      return TreeNodeModel.builder().addChildren(tableNode).build();
   }

   private SelectionDialogService service;
}
