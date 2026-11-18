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
package inetsoft.web.admin.content.repository;

import inetsoft.report.LibManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.report.style.XTableStyle;
import inetsoft.sree.RepletRegistry;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.security.*;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.audit.Audit;
import inetsoft.web.RecycleBin;
import inetsoft.web.admin.content.database.model.DataModelFolderManagerService;
import inetsoft.web.admin.content.repository.model.TreeNodeInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #80960: the EM repository delete of a table style or a table style folder removes the
 * TABLE_STYLE permission of it, as the composer delete does, so a style created later with the
 * same name doesn't inherit the grants.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RepositoryTableStylePermissionRemovalTest {
   @Test
   void tableStyleDelete_removesPermission() throws Exception {
      XTableStyle style = style("s1", "folderA~T1");
      when(manager.getTableStyles("folderA")).thenReturn(new XTableStyle[] { style });

      assertNull(delete(RepositoryEntry.TABLE_STYLE, "folderA~T1", "T1"));

      verify(manager).removeTableStyle("s1");
      verify(manager).save();
      verify(provider).removePermission(ResourceType.TABLE_STYLE, "folderA~T1");
   }

   @Test
   void tableStyleDelete_rootStyle_removesPermission() throws Exception {
      XTableStyle style = style("s1", "T1");
      when(manager.getTableStyles(null)).thenReturn(new XTableStyle[] { style });

      delete(RepositoryEntry.TABLE_STYLE, "T1", "T1");

      verify(provider).removePermission(ResourceType.TABLE_STYLE, "T1");
   }

   // a failed permission write must not stop the delete of the other selected nodes
   @Test
   void tableStyleDelete_removeFails_doesNotThrow() throws Exception {
      XTableStyle style = style("s1", "T1");
      when(manager.getTableStyles(null)).thenReturn(new XTableStyle[] { style });
      doThrow(new RuntimeException("storage down"))
         .when(provider).removePermission(ResourceType.TABLE_STYLE, "T1");

      delete(RepositoryEntry.TABLE_STYLE, "T1", "T1");

      verify(manager).removeTableStyle("s1");
   }

   // the folder removal is recursive, the permissions of everything under it go too
   @Test
   void tableStyleFolderDelete_removesPermissionsOfFolderStylesAndSubfolders() throws Exception {
      XTableStyle inFolder = style("s1", "F~T1");
      XTableStyle inSub = style("s2", "F~S~T2");
      when(manager.getTableStyles("F")).thenReturn(new XTableStyle[] { inFolder });
      when(manager.getTableStyleFolders("F")).thenReturn(new String[] { "F~S" });
      when(manager.getTableStyles("F~S")).thenReturn(new XTableStyle[] { inSub });
      when(manager.getTableStyleFolders("F~S")).thenReturn(new String[0]);

      delete(RepositoryEntry.TABLE_STYLE | RepositoryEntry.FOLDER, "F", "F");

      verify(manager).removeTableStyleFolder("F");
      verify(manager).save();
      verify(provider).removePermission(ResourceType.TABLE_STYLE, "F");
      verify(provider).removePermission(ResourceType.TABLE_STYLE, "F~T1");
      verify(provider).removePermission(ResourceType.TABLE_STYLE, "F~S");
      verify(provider).removePermission(ResourceType.TABLE_STYLE, "F~S~T2");
   }

   private XTableStyle style(String id, String name) {
      XTableStyle style = mock(XTableStyle.class);
      when(style.getID()).thenReturn(id);
      when(style.getName()).thenReturn(name);
      return style;
   }

   private Object delete(int type, String path, String label) throws Exception {
      TreeNodeInfo node = TreeNodeInfo.builder().label(label).path(path).type(type).build();
      Principal admin = new SRPrincipal(new IdentityID("admin", "host-org"),
                                        new IdentityID[] { new IdentityID("Administrator", null) },
                                        new String[0], "host-org", 1L);

      try(MockedStatic<Audit> audit = mockStatic(Audit.class)) {
         audit.when(Audit::getInstance).thenReturn(mock(Audit.class));
         return service().deleteNodes(new TreeNodeInfo[] { node }, admin, true, false);
      }
   }

   private RepositoryObjectService service() throws Exception {
      // permission checks are not under test
      when(provider.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      ResourcePermissionService permissions = mock(ResourcePermissionService.class);
      when(permissions.getRepositoryResourceType(anyInt(), anyString())).thenAnswer(
         inv -> new Resource(ResourceType.TABLE_STYLE, inv.getArgument(1)));
      RepletRegistryManager repletRegistries = mock(RepletRegistryManager.class);
      when(repletRegistries.getRegistry(nullable(IdentityID.class)))
         .thenReturn(mock(RepletRegistry.class));
      LibManagerProvider libs = mock(LibManagerProvider.class);
      when(libs.getManager()).thenReturn(manager);
      when(libs.getManager(any(Principal.class))).thenReturn(manager);

      return new RepositoryObjectService(
         mock(RepletRegistryService.class), mock(ContentRepositoryTreeService.class), provider,
         permissions, mock(XRepository.class), mock(RepositoryDashboardService.class),
         mock(DataModelFolderManagerService.class), mock(DataSourceRegistry.class), libs,
         mock(RecycleBin.class), mock(DependencyHandler.class), mock(RenameTransformHandler.class),
         repletRegistries, mock(DashboardRegistryManager.class));
   }

   private final SecurityProvider provider = mock(SecurityProvider.class);
   private final LibManager manager = mock(LibManager.class);
}
