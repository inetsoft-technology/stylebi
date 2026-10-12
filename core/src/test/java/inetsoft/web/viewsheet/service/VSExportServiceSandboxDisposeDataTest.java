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

package inetsoft.web.viewsheet.service;

import inetsoft.mv.MVManager;
import inetsoft.report.TableLens;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.*;
import inetsoft.report.io.viewsheet.VSExporter;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77609: writeViewsheetExport() disposes the bookmark sandboxes it creates, but a
 * print-layout PDF reads their table lenses only in exporter.write(). Disposing a sandbox
 * retires its worksheet script env, so a worksheet expression column that has not been read
 * yet comes back null. With real sandboxes, the expression cells must still have values when
 * write() reads them, and every bookmark sandbox must be disposed afterwards.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  PluginsTestConfiguration.class,
                                  VSExportServiceSandboxDisposeDataTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VSExportServiceSandboxDisposeDataTest {
   @Configuration
   static class TestConfig {
      @Bean
      @SuppressWarnings("unchecked")
      public AssetDataCache assetDataCache() {
         ObjectProvider<DistributedTableCacheStore> provider = mock(ObjectProvider.class);
         when(provider.getObject()).thenReturn(mock(DistributedTableCacheStore.class));
         return new AssetDataCache(mock(DataSourceRegistry.class), provider);
      }

      @Bean
      public MVManager mvManager() {
         return mock(MVManager.class);
      }
   }

   @Test
   void expressionColumnStillHasDataWhenWriteReadsBookmarkLenses() throws Exception {
      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "test/Bug77609", null,
         OrganizationManager.getInstance().getCurrentOrgID());
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      Viewsheet current = mock(Viewsheet.class);
      when(current.getRuntimeEntry()).thenReturn(mock(AssetEntry.class));
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(mock(ViewsheetSandbox.class)));
      when(rvs.getViewsheet()).thenReturn(current);
      when(rvs.getEntry()).thenReturn(entry);
      when(rvs.getOriginalBookmark(anyString(), any())).thenAnswer(inv -> viewsheet());

      List<ViewsheetSandbox> boxes = new ArrayList<>();
      List<VSTableLens> lenses = new ArrayList<>();
      List<Integer> blankCells = new ArrayList<>();
      VSExporter exporter = mock(VSExporter.class);

      // like a print-layout PDF, keep the lens at export() and read its cells only in write()
      doAnswer(inv -> {
         ViewsheetSandbox box = inv.getArgument(0);
         VSTableLens lens = box.getVSTableLens("TableV", false);
         lens.moreRows(1);
         boxes.add(box);
         lenses.add(lens);
         return null;
      }).when(exporter).export(any(ViewsheetSandbox.class), anyString(), anyInt(), any());

      doAnswer(inv -> {
         for(VSTableLens lens : lenses) {
            lens.moreRows(TableLens.EOT);
            assertEquals(ROWS + 1, lens.getRowCount());

            for(int r = 1; r < lens.getRowCount(); r++) {
               Object value = lens.getObject(r, 2);

               if(value == null) {
                  blankCells.add(r);
               }
            }
         }

         return null;
      }).when(exporter).write();

      invoke(rvs, exporter, "b1", "b2");

      assertEquals(2, boxes.size());
      assertTrue(blankCells.isEmpty(),
                 blankCells.size() + " expression cells were blank in write()");
      assertEquals(ROWS * 7 + 1.0, ((Number) lenses.get(1).getObject(ROWS, 2)).doubleValue());

      for(ViewsheetSandbox box : boxes) {
         assertTrue(isDisposed(box), "bookmark sandbox disposed after write()");
      }
   }

   private static void invoke(RuntimeViewsheet rvs, VSExporter exporter, String... bookmarks)
      throws Exception
   {
      Method method = VSExportService.class.getDeclaredMethod(
         "writeViewsheetExport", RuntimeViewsheet.class, VSExporter.class, Principal.class,
         boolean.class, boolean.class, boolean.class, String[].class, boolean.class,
         boolean.class);
      method.setAccessible(true);
      VSExportService service = new VSExportService(null, null, null, null, null, null);
      method.invoke(service, rvs, exporter, null, false, false, false, bookmarks, false, true);
   }

   private static boolean isDisposed(ViewsheetSandbox box) throws Exception {
      Field field = ViewsheetSandbox.class.getDeclaredField("disposed");
      field.setAccessible(true);
      return field.getBoolean(box);
   }

   // a viewsheet table bound to a worksheet table with a lazily evaluated expression column
   private static Viewsheet viewsheet() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly wsTable = new EmbeddedTableAssembly(ws, "A");
      Object[][] data = new Object[ROWS + 1][];
      data[0] = new Object[] { "value", "id" };

      for(int i = 1; i <= ROWS; i++) {
         data[i] = new Object[] { i * 7, i };
      }

      wsTable.setEmbeddedData(new XEmbeddedTable(
         new String[] { XSchema.INTEGER, XSchema.INTEGER }, data));
      ws.addAssembly(wsTable);
      ColumnSelection wsColumns = wsTable.getColumnSelection(false);
      ExpressionRef exp = new ExpressionRef(null, "out");
      exp.setExpression("field['value'] + 1");
      ColumnRef expColumn = new ColumnRef(exp);
      expColumn.setDataType(XSchema.DOUBLE);
      wsColumns.addAttribute(expColumn);
      wsTable.setColumnSelection(wsColumns, false);

      Viewsheet vs = new Viewsheet();
      Field wsField = Viewsheet.class.getDeclaredField("ws");
      wsField.setAccessible(true);
      wsField.set(vs, ws);
      TableVSAssembly table = new TableVSAssembly(vs, "TableV");
      table.setSourceInfo(new SourceInfo(XSourceInfo.ASSET, null, "A"));
      ColumnSelection columns = new ColumnSelection();

      for(String name : new String[] { "value", "id", "out" }) {
         columns.addAttribute(new ColumnRef(new AttributeRef(null, name)));
      }

      table.setColumnSelection(columns);
      vs.addAssembly(table);
      return vs;
   }

   private static final int ROWS = 5000;
}
