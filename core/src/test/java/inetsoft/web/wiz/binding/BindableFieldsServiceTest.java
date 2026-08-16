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
package inetsoft.web.wiz.binding;

import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.service.VSBindingTreeService;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.wiz.binding.model.BindableTable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class BindableFieldsServiceTest {
   @Test
   void flattensTheTreeIntoTablesAndColumnsAndDropsTheUiChrome() throws Exception {
      DataRefModel ref = mock(DataRefModel.class);
      when(ref.getDataType()).thenReturn("double");

      TreeNodeModel column = TreeNodeModel.builder()
         .label("Sales").leaf(true).data(ref)
         .icon("column-icon").tooltip("a tooltip").build();
      TreeNodeModel table = TreeNodeModel.builder()
         .label("Orders").addChildren(column).build();
      TreeNodeModel root = TreeNodeModel.builder().label("root").addChildren(table).build();

      List<BindableTable> tables = serviceReturning(root).list("rt1", null, principal());

      assertEquals(1, tables.size());
      assertEquals("Orders", tables.get(0).name());
      assertEquals(1, tables.get(0).fields().size());
      assertEquals("Sales", tables.get(0).fields().get(0).column());
      assertEquals("double", tables.get(0).fields().get(0).dataType());
   }

   @Test
   void descendsThroughFolderLevelsSoNestingDepthDoesNotMatter() throws Exception {
      TreeNodeModel column = TreeNodeModel.builder().label("Qty").leaf(true).build();
      TreeNodeModel table = TreeNodeModel.builder().label("Items").addChildren(column).build();
      TreeNodeModel folder = TreeNodeModel.builder().label("Folder").addChildren(table).build();
      TreeNodeModel root = TreeNodeModel.builder().label("root").addChildren(folder).build();

      List<BindableTable> tables = serviceReturning(root).list("rt1", null, principal());

      assertEquals(1, tables.size(), "the folder level must not become a table");
      assertEquals("Items", tables.get(0).name());
   }

   @Test
   void returnsNoTablesRatherThanFailingWhenTheTreeIsEmpty() throws Exception {
      assertTrue(serviceReturning(null).list("rt1", null, principal()).isEmpty());
   }

   private static BindableFieldsService serviceReturning(TreeNodeModel root) throws Exception {
      VSBindingTreeService tree = mock(VSBindingTreeService.class);
      when(tree.getBinding(eq("rt1"), any(), anyBoolean(), any(Principal.class)))
         .thenReturn(root);
      return new BindableFieldsService(tree);
   }

   private static Principal principal() {
      return () -> "admin";
   }
}
