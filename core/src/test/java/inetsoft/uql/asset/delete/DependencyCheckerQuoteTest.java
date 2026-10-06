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
package inetsoft.uql.asset.delete;

import inetsoft.storage.KeyValueStorageManager;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.asset.sync.RenameInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77893: deleting a worksheet column must warn when another asset uses it. The column name
 * was put into an XPath string literal, so a name with an apostrophe made the XPath fail
 * silently and no dependency was reported. Dependents are saved through the real
 * {@link AssetRepository} and checked through {@link DeleteDependencyHandler}, with the delete
 * info built as {@code DeleteColumnsService.hasDependency} builds it. The context-free check of
 * every data ref form is in {@link DependencyCheckerDataRefTest}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  DependencyCheckerQuoteTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow") // over 10 s alone: the Spring context with the real asset repository
class DependencyCheckerQuoteTest {
   private static final String NAMES_SOURCE = "names";
   private static int count = 0;

   @ParameterizedTest
   @ValueSource(strings = { "Customers", "Customer's", "Say \"hi\"", "Mix'\"" })
   void worksheetMirrorColumnIsADependency(String column) throws Exception {
      AssetEntry src = wsEntry("src");
      AssetEntry dep = wsEntry("dep");
      Worksheet ws = new Worksheet();
      ws.addAssembly(new MirrorTableAssembly(ws, "M", src, true, sourceTable(column)));
      save(dep, ws);

      assertDependency(dep, src, column, true);
   }

   @ParameterizedTest
   @ValueSource(strings = { "Customers", "Customer's", "Say \"hi\"", "Mix'\"" })
   void viewsheetTableColumnIsADependency(String column) throws Exception {
      AssetEntry src = wsEntry("src");
      AssetEntry dep = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                      "vs77893_" + (++count), null);
      Viewsheet vs = new Viewsheet(src);
      TableVSAssembly table = new TableVSAssembly(vs, "Table1");
      table.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "T"));
      ColumnSelection cols = new ColumnSelection();
      cols.addAttribute(new ColumnRef(new AttributeRef(null, column)));
      ((TableVSAssemblyInfo) table.getInfo()).setColumnSelection(cols);
      vs.addAssembly(table);
      save(dep, vs);

      assertDependency(dep, src, column, true);
   }

   // a dependent that uses another column of the same table is not reported
   @ParameterizedTest
   @ValueSource(strings = { "Customers", "Customer's" })
   void otherColumnIsNotADependency(String column) throws Exception {
      AssetEntry src = wsEntry("src");
      AssetEntry dep = wsEntry("dep");
      Worksheet ws = new Worksheet();
      ws.addAssembly(new MirrorTableAssembly(ws, "M", src, true, sourceTable(column + "2")));
      save(dep, ws);

      assertDependency(dep, src, column, false);
   }

   private static void assertDependency(AssetEntry dep, AssetEntry src, String column,
                                        boolean expected)
   {
      DeleteInfo info = new DeleteInfo(column, RenameInfo.ASSET | RenameInfo.COLUMN,
                                       src.toIdentifier(), "T");
      DeleteDependencyInfo dinfo = new DeleteDependencyInfo();
      dinfo.setDeleteInfo(dep, List.of(info));

      assertEquals(expected, DeleteDependencyHandler.hasDependency(dinfo));
      String status = DeleteDependencyHandler.checkDependencyStatus(dinfo);

      if(expected) {
         assertNotNull(status);
         assertTrue(status.contains(column), status);
      }
      else {
         assertNull(status);
      }
   }

   private static BoundTableAssembly sourceTable(String column) {
      Worksheet ws = new Worksheet();
      BoundTableAssembly table = new BoundTableAssembly(ws, "T");
      table.setSourceInfo(new SourceInfo(SourceInfo.MODEL, "ds", NAMES_SOURCE));
      ColumnSelection cols = new ColumnSelection();
      cols.addAttribute(new ColumnRef(new AttributeRef("E", column)));
      table.setColumnSelection(cols, false);
      ws.addAssembly(table);
      return table;
   }

   private static AssetEntry wsEntry(String prefix) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
                            prefix + "77893_" + (++count), null);
   }

   private static void save(AssetEntry entry, AbstractSheet sheet) throws Exception {
      AssetUtil.getAssetRepository(false).setSheet(entry, sheet, null, true);
   }

   @Configuration
   static class Beans {
      // the constructor is package private; saving a sheet updates the dependency storage
      @Bean
      public DependencyStorageService dependencyStorageService(KeyValueStorageManager storage)
         throws Exception
      {
         Constructor<DependencyStorageService> ctor =
            DependencyStorageService.class.getDeclaredConstructor(KeyValueStorageManager.class);
         ctor.setAccessible(true);
         return ctor.newInstance(storage);
      }
   }
}
