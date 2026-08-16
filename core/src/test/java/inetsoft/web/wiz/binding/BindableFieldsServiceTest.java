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

import inetsoft.uql.asset.AssetEntry;
import inetsoft.web.binding.service.VSBindingTreeService;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.wiz.binding.model.BindableField;
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
   /**
    * Every node in the real tree carries an {@link AssetEntry} in {@code data()} --
    * {@code VSTreeHandler.createNodeFromEntry} builds them all with {@code .data(entry)}. These
    * tests used to mock a {@code DataRefModel} there, which the tree never produces, so they
    * passed while every column in production reported a null data type.
    */
   private static AssetEntry entry(AssetEntry.Type type, String name, String dtype) {
      return entry(type, name, dtype, null);
   }

   private static AssetEntry entry(AssetEntry.Type type, String name, String dtype, Integer cube) {
      AssetEntry entry = mock(AssetEntry.class);
      when(entry.getType()).thenReturn(type);
      when(entry.getProperty("dtype")).thenReturn(dtype);
      when(entry.getProperty(AssetEntry.CUBE_COL_TYPE))
         .thenReturn(cube == null ? null : String.valueOf(cube));
      return entry;
   }

   @Test
   void flattensTheTreeIntoTablesAndColumnsAndDropsTheUiChrome() throws Exception {
      TreeNodeModel column = TreeNodeModel.builder()
         .label("Sales").leaf(true).data(entry(AssetEntry.Type.COLUMN, "Sales", "double"))
         .icon("column-icon").tooltip("a tooltip").build();
      TreeNodeModel table = TreeNodeModel.builder()
         .label("Orders").data(entry(AssetEntry.Type.TABLE, "Orders", null))
         .addChildren(column).build();
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
      TreeNodeModel table = TreeNodeModel.builder()
         .label("Items").data(entry(AssetEntry.Type.TABLE, "Items", null))
         .addChildren(column).build();
      TreeNodeModel folder = TreeNodeModel.builder().label("Folder").addChildren(table).build();
      TreeNodeModel root = TreeNodeModel.builder().label("root").addChildren(folder).build();

      List<BindableTable> tables = serviceReturning(root).list("rt1", null, principal());

      assertEquals(1, tables.size(), "the folder level must not become a table");
      assertEquals("Items", tables.get(0).name());
   }

   /**
    * The live shape: a table whose columns sit under "Dimensions"/"Measures" folders.
    *
    * <p>Two things have to hold together. Naming each group after the node that directly holds the
    * columns produced nine groups called only "Dimensions"/"Measures", with no way to tell which
    * table a column came from. But naming them after the table while still emitting one group per
    * folder produces two groups with the <em>same</em> name -- and a caller keying by table name
    * keeps one and silently drops half the columns. So a table yields exactly one group.
    */
   @Test
   void yieldsOneGroupPerTableNamedAfterIt_notOnePerDimensionsFolder() throws Exception {
      TreeNodeModel qty = TreeNodeModel.builder().label("Qty").leaf(true)
         .data(entry(AssetEntry.Type.COLUMN, "Qty", "integer", AssetEntry.MEASURES)).build();
      TreeNodeModel city = TreeNodeModel.builder().label("City").leaf(true)
         .data(entry(AssetEntry.Type.COLUMN, "City", "string", AssetEntry.DIMENSIONS)).build();
      TreeNodeModel measures = TreeNodeModel.builder().label("Measures").addChildren(qty).build();
      TreeNodeModel dimensions = TreeNodeModel.builder()
         .label("Dimensions").addChildren(city).build();
      TreeNodeModel table = TreeNodeModel.builder()
         .label("ORDERS1").data(entry(AssetEntry.Type.TABLE, "ORDERS1", null))
         .addChildren(dimensions).addChildren(measures).build();
      TreeNodeModel root = TreeNodeModel.builder().label("root").addChildren(table).build();

      List<BindableTable> tables = serviceReturning(root).list("rt1", null, principal());

      assertEquals(1, tables.size(), "one table must not become two same-named groups");
      assertEquals("ORDERS1", tables.get(0).name());
      assertEquals(2, tables.get(0).fields().size(), "both folders' columns belong to the table");
   }

   /**
    * The dimension/measure distinction has to survive, because every binding tool downstream
    * requires it per field and this is the tool a caller runs first. Merging the folders removed
    * the only place it used to live implicitly (the group label), so it moves onto the field.
    */
   @Test
   void reportsDimensionOrMeasurePerColumn() throws Exception {
      TreeNodeModel qty = TreeNodeModel.builder().label("Qty").leaf(true)
         .data(entry(AssetEntry.Type.COLUMN, "Qty", "integer", AssetEntry.MEASURES)).build();
      TreeNodeModel city = TreeNodeModel.builder().label("City").leaf(true)
         .data(entry(AssetEntry.Type.COLUMN, "City", "string", AssetEntry.DIMENSIONS)).build();
      TreeNodeModel table = TreeNodeModel.builder()
         .label("ORDERS1").data(entry(AssetEntry.Type.TABLE, "ORDERS1", null))
         .addChildren(city).addChildren(qty).build();
      TreeNodeModel root = TreeNodeModel.builder().label("root").addChildren(table).build();

      List<BindableField> fields = serviceReturning(root).list("rt1", null, principal())
         .get(0).fields();

      assertEquals("dimension", fields.get(0).role());
      assertEquals("measure", fields.get(1).role());
   }

   /** Without CUBE_COL_TYPE, fall back exactly as VSChartBindingHandler.isDimension does. */
   @Test
   void fallsBackToTheDataTypeWhenTheTreeCarriesNoCubeColumnType() throws Exception {
      TreeNodeModel amount = TreeNodeModel.builder().label("Amount").leaf(true)
         .data(entry(AssetEntry.Type.COLUMN, "Amount", "double")).build();
      TreeNodeModel name = TreeNodeModel.builder().label("Name").leaf(true)
         .data(entry(AssetEntry.Type.COLUMN, "Name", "string")).build();
      TreeNodeModel table = TreeNodeModel.builder()
         .label("T").data(entry(AssetEntry.Type.TABLE, "T", null))
         .addChildren(name).addChildren(amount).build();
      TreeNodeModel root = TreeNodeModel.builder().label("root").addChildren(table).build();

      List<BindableField> fields = serviceReturning(root).list("rt1", null, principal())
         .get(0).fields();

      assertEquals("dimension", fields.get(0).role());
      assertEquals("measure", fields.get(1).role());
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
