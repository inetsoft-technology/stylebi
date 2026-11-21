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
package inetsoft.web.portal.controller.database;

import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.sync.RenameDependencyInfo;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.web.admin.content.database.model.DataModelFolderManagerService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.FileNotFoundException;
import java.security.Principal;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Design H-B: the Portal's {@code renameDataModelFolder} swallows a failure of the rename (it logs
 * it, records FAILURE in the audit record and returns normally), so the bridge calls
 * {@code renameDataModelFolderStrict}. The public method keeps its behavior; the strict one throws.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
@Tag("integration")
class DatabaseModelBrowserServiceRenameFolderStrictTest {
   private static int count = 0;
   private final Principal bob = () -> "bob";
   private String ds;
   private XDataModel dataModel;
   private XRepository repository;
   private DatabaseModelBrowserService service;

   @BeforeEach
   void setUp() throws Exception {
      DataSourceRegistry.getRegistry().init();
      ds = "renstrict_" + (++count);
      dataModel = new XDataModel(ds);
      dataModel.addFolder("Old");
      repository = mock(XRepository.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.getDataModel(ds)).thenReturn(dataModel);
      SecurityEngine security = mock(SecurityEngine.class);
      when(security.checkPermission(any(), any(), anyString(), any(inetsoft.sree.security.ResourceAction.class)))
         .thenReturn(true);
      service = new DatabaseModelBrowserService(
         dataSourceService, repository, null, null, security,
         mock(DataModelFolderManagerService.class), mock(RenameTransformHandler.class));
   }

   @Test
   void publicRenameStillSwallowsAServerFailure() throws Exception {
      doThrow(new IllegalStateException("registry write failed"))
         .when(repository).updateDataModel(any());

      assertDoesNotThrow(() -> service.renameDataModelFolder(ds, "New", "Old", bob),
                         "the Portal behavior is unchanged");
   }

   @Test
   void strictRenameThrowsTheServerFailure() throws Exception {
      doThrow(new IllegalStateException("registry write failed"))
         .when(repository).updateDataModel(any());

      IllegalStateException e = assertThrows(IllegalStateException.class,
         () -> service.renameDataModelFolderStrict(ds, "New", "Old", bob));
      assertEquals("registry write failed", e.getMessage());
   }

   @Test
   void strictRenameAppliesTheRenameLikeThePublicOne() throws Exception {
      service.renameDataModelFolderStrict(ds, "New", "Old", bob);

      assertTrue(Arrays.asList(dataModel.getFolders()).contains("New"));
      assertFalse(Arrays.asList(dataModel.getFolders()).contains("Old"));
      verify(repository).updateDataModel(dataModel);
   }

   @Test
   void aDataSourceWithoutAFolderListIsSilentInThePublicMethodAndAFailureInTheStrictOne()
      throws Exception
   {
      DataSourceService none = mock(DataSourceService.class);
      when(none.getDataModel("nofolders")).thenReturn(null);
      DatabaseModelBrowserService other = new DatabaseModelBrowserService(
         none, repository, null, null, mock(SecurityEngine.class),
         mock(DataModelFolderManagerService.class), mock(RenameTransformHandler.class));

      assertDoesNotThrow(() -> other.renameDataModelFolder("nofolders", "New", "Old", bob));
      assertThrows(FileNotFoundException.class,
                   () -> other.renameDataModelFolderStrict("nofolders", "New", "Old", bob));
   }

   @Test
   void bothRefuseAnUnknownOldFolder() {
      assertThrows(FileNotFoundException.class,
                   () -> service.renameDataModelFolder(ds, "New", "Missing", bob));
      assertThrows(FileNotFoundException.class,
                   () -> service.renameDataModelFolderStrict(ds, "New", "Missing", bob));
   }
}
