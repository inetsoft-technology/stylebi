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
package inetsoft.web.binding.service;

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.SortRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.web.binding.drm.ColumnRefModel;
import inetsoft.web.binding.model.table.TableBindingModel;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #79281: a column dropped from a Table's binding must not leave its sort behind.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSTableBindingFactorySortTest {
   private static ColumnRef column(String name) {
      return new ColumnRef(new AttributeRef(null, name));
   }

   private static TableVSAssembly tableWithSorts(String... sortedColumns) {
      TableVSAssembly table = new TableVSAssembly(mock(Viewsheet.class), "Table1");
      TableVSAssemblyInfo info = (TableVSAssemblyInfo) table.getVSAssemblyInfo();
      ColumnSelection cols = new ColumnSelection();
      cols.addAttribute(column("Category"));
      cols.addAttribute(column("Region"));
      info.setColumnSelection(cols);
      ColumnSelection hidden = new ColumnSelection();
      hidden.addAttribute(column("Company"));
      info.setHiddenColumns(hidden);

      for(String name : sortedColumns) {
         SortRef sort = new SortRef(new AttributeRef(null, name));
         sort.setOrder(XConstants.SORT_ASC);
         info.getSortInfo().addSort(sort);
      }

      return table;
   }

   private static TableBindingModel detailsOf(String... names) {
      TableBindingModel model = new TableBindingModel();

      for(String name : names) {
         model.getDetails().add(new ColumnRefModel(column(name)));
      }

      return model;
   }

   @Test
   void removedShownColumnLosesItsSort() {
      TableVSAssembly table = tableWithSorts("Category", "Region");

      VSTableBindingFactory.updateTableAssembly(detailsOf("Region"), table);

      TableVSAssemblyInfo info = (TableVSAssemblyInfo) table.getVSAssemblyInfo();
      assertEquals(1, info.getSortInfo().getSortCount());
      assertEquals("Region", info.getSortInfo().getSort(0).getName());
   }

   @Test
   void hiddenSortedColumnKeepsItsSort() {
      TableVSAssembly table = tableWithSorts("Company", "Category");

      VSTableBindingFactory.updateTableAssembly(detailsOf("Category", "Region"), table);

      TableVSAssemblyInfo info = (TableVSAssemblyInfo) table.getVSAssemblyInfo();
      assertEquals(2, info.getSortInfo().getSortCount());
   }

   @Test
   void removedHiddenSortedColumnLosesItsSort() {
      TableVSAssembly table = tableWithSorts("Company", "Category");
      ((TableVSAssemblyInfo) table.getVSAssemblyInfo()).getHiddenColumns().removeAllAttributes();

      VSTableBindingFactory.updateTableAssembly(detailsOf("Category", "Region"), table);

      TableVSAssemblyInfo info = (TableVSAssemblyInfo) table.getVSAssemblyInfo();
      assertEquals(1, info.getSortInfo().getSortCount());
      assertEquals("Category", info.getSortInfo().getSort(0).getName());
   }

   @Test
   void unrelatedRemovalKeepsSortOnShownColumn() {
      TableVSAssembly table = tableWithSorts("Category");

      VSTableBindingFactory.updateTableAssembly(detailsOf("Category"), table);

      TableVSAssemblyInfo info = (TableVSAssemblyInfo) table.getVSAssemblyInfo();
      assertEquals(1, info.getSortInfo().getSortCount());
   }

   @Test
   void expressionColumnKeepsItsSort() {
      TableVSAssembly table = tableWithSorts();
      ExpressionRef expression = new ExpressionRef(null, null, "Margin");
      ColumnRef margin = new ColumnRef(expression);
      TableVSAssemblyInfo info = (TableVSAssemblyInfo) table.getVSAssemblyInfo();
      info.getSortInfo().addSort(new SortRef(expression));
      TableBindingModel model = detailsOf("Category");
      model.getDetails().add(new ColumnRefModel(margin));

      VSTableBindingFactory.updateTableAssembly(model, table);

      assertEquals(1, info.getSortInfo().getSortCount());
   }

   @Test
   void emptyDetailsLeavesOnlyTheHiddenColumnSort() {
      TableVSAssembly table = tableWithSorts("Category", "Company");

      VSTableBindingFactory.updateTableAssembly(detailsOf(), table);

      TableVSAssemblyInfo info = (TableVSAssemblyInfo) table.getVSAssemblyInfo();
      assertEquals(1, info.getSortInfo().getSortCount());
      assertEquals("Company", info.getSortInfo().getSort(0).getName());
   }
}
