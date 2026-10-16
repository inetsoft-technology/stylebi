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
package inetsoft.web.admin.content.repository;

import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.sync.DependencyTool;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XLogicalModel;
import inetsoft.web.admin.security.ConnectionStatus;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78675 (P3): the dependency check of a data source delete returned the result of its first
 * logical model, so the dependents of the second and later models were never checked.
 */
@Tag("core")
class RepositoryObjectServiceModelDependenciesTest {
   private static final String DS = "ds";

   private MockedStatic<DependencyTool> dependencyStatic;
   private RepositoryObjectService service;

   @BeforeEach
   void setUp() throws Exception {
      XDataModel dataModel = mock(XDataModel.class);
      when(dataModel.getLogicalModelNames()).thenReturn(new String[] { "m1", "m2", "m3" });

      for(String name : new String[] { "m1", "m2", "m3" }) {
         XLogicalModel model = mock(XLogicalModel.class);
         when(model.getDataSource()).thenReturn(DS);
         when(model.getName()).thenReturn(name);
         when(dataModel.getLogicalModel(name)).thenReturn(model);
      }

      XRepository xRepository = mock(XRepository.class);
      when(xRepository.getDataModel(DS)).thenReturn(dataModel);
      service = mock(RepositoryObjectService.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
      ReflectionTestUtils.setField(service, "xRepository", xRepository);
      dependencyStatic = mockStatic(DependencyTool.class);
      dependencyStatic.when(() -> DependencyTool.getDependencies(anyString())).thenReturn(null);
   }

   @AfterEach
   void tearDown() {
      dependencyStatic.close();
   }

   private static String modelId(String name) {
      return new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.LOGIC_MODEL,
                            DS + "/" + name, null).toIdentifier();
   }

   private void dependOn(String model) {
      dependencyStatic.when(() -> DependencyTool.getDependencies(modelId(model)))
         .thenReturn(List.of(mock(AssetObject.class)));
   }

   private ConnectionStatus check(boolean force) {
      return ReflectionTestUtils.invokeMethod(
         service, "checkAssetEntryDependencies", DS, AssetEntry.Type.DATA_SOURCE, force);
   }

   @Test
   void dependentOfASecondModelBlocksTheDelete() {
      dependOn("m2");
      assertNotNull(check(false));
   }

   @Test
   void dependentOfALaterModelBlocksTheDelete() {
      dependOn("m3");
      assertNotNull(check(false));
   }

   @Test
   void dependentOfTheFirstModelStillBlocksTheDelete() {
      dependOn("m1");
      assertNotNull(check(false));
   }

   @Test
   void noDependentsDoesNotBlock() {
      assertNull(check(false));
   }

   @Test
   void forceDoesNotBlock() {
      dependOn("m2");
      assertNull(check(true));
   }
}
