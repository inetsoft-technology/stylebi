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
package inetsoft.web.portal.data;

import inetsoft.uql.XDataSource;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.sync.DependencyTool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug #82091: the dependency check of a data source, which the delete confirmation and the wiz
 * delete dry run call, walked only its logical models, so a worksheet built directly on the
 * data source was never reported.
 */
@Tag("core")
class DataSourceSheetDependencyCheckTest {
   private static final String SOURCE = "ds82091";

   @Test
   void reportsWorksheetBuiltDirectlyOnSource() throws Exception {
      AssetEntry ws = entry(AssetEntry.Type.WORKSHEET, "ws82091");

      try(MockedStatic<DependencyTool> tool = dependents(ws);
          MockedStatic<AssetUtil> util = repositoryContaining(true))
      {
         DependencyException ex = assertThrows(DependencyException.class,
                                               () -> service().checkDataSourceOuterDependencies(SOURCE));
         assertEquals(List.of(ws), ex.getEntries());
         assertTrue(ex.getMessage().contains("ws82091"), ex.getMessage());
      }
   }

   @Test
   void ignoresWorksheetThatNoLongerExists() throws Exception {
      AssetEntry ws = entry(AssetEntry.Type.WORKSHEET, "gone82091");

      try(MockedStatic<DependencyTool> tool = dependents(ws);
          MockedStatic<AssetUtil> util = repositoryContaining(false))
      {
         assertDoesNotThrow(() -> service().checkDataSourceOuterDependencies(SOURCE));
      }
   }

   @Test
   void ignoresNonSheetDependents() throws Exception {
      AssetEntry model = entry(AssetEntry.Type.LOGIC_MODEL, SOURCE + "/lm");

      try(MockedStatic<DependencyTool> tool = dependents(model);
          MockedStatic<AssetUtil> util = repositoryContaining(true))
      {
         assertDoesNotThrow(() -> service().checkDataSourceOuterDependencies(SOURCE));
      }
   }

   @Test
   void passesWithoutDependents() throws Exception {
      try(MockedStatic<DependencyTool> tool = dependents();
          MockedStatic<AssetUtil> util = repositoryContaining(true))
      {
         assertDoesNotThrow(() -> service().checkDataSourceOuterDependencies(SOURCE));
      }
   }

   private static AssetEntry entry(AssetEntry.Type type, String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, type, path, null);
   }

   private static MockedStatic<DependencyTool> dependents(AssetObject... entries) {
      MockedStatic<DependencyTool> tool = mockStatic(DependencyTool.class);
      tool.when(() -> DependencyTool.getAssetId(eq(SOURCE), any(AssetEntry.Type.class)))
         .thenReturn("source-key");
      tool.when(() -> DependencyTool.getDependencies("source-key")).thenReturn(List.of(entries));
      return tool;
   }

   private static MockedStatic<AssetUtil> repositoryContaining(boolean contains) throws Exception {
      AssetRepository repository = mock(AssetRepository.class);
      when(repository.containsEntry(any(AssetEntry.class))).thenReturn(contains);
      MockedStatic<AssetUtil> util = mockStatic(AssetUtil.class);
      util.when(() -> AssetUtil.getAssetRepository(false)).thenReturn(repository);
      return util;
   }

   private static DatasourcesBaseService service() {
      return new DatasourcesBaseService(mock(XRepository.class), null, null, null, null) {
         @Override
         public XDataSource createDataSource(BaseDataSourceDefinition definition, XDataSource ds) {
            return ds;
         }
      };
   }
}
