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

import inetsoft.report.LibManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.Organization;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.sree.security.SecurityProvider;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.web.RecycleBin;
import inetsoft.web.admin.content.repository.model.TreeNodeInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Bug #78735: the EM Repository delete of a library function must drop the function's outgoing
 * dependency edges, or its callees are later refused as "used by" a function that no longer exists.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RepositoryObjectServiceScriptDeleteTest {
   private static final String ORG = Organization.getDefaultOrganizationID();

   @Test
   void deletingAScriptNodeDropsItsOutgoingDependencyEdgesBeforeRemovingIt() throws Exception {
      LibManager lib = mock(LibManager.class);
      LibManagerProvider libProvider = mock(LibManagerProvider.class);
      DependencyHandler handler = mock(DependencyHandler.class);
      Principal principal = new SRPrincipal(
         new IdentityID("admin", ORG), new IdentityID[0], new String[0],
         ORG, 0L);
      when(libProvider.getManager(any(Principal.class))).thenReturn(lib);
      when(lib.getScript("caller")).thenReturn("return callee();");
      RepositoryObjectService service = new RepositoryObjectService(
         mock(RepletRegistryService.class), mock(ContentRepositoryTreeService.class),
         mock(SecurityProvider.class), mock(ResourcePermissionService.class),
         mock(inetsoft.uql.XRepository.class), mock(RepositoryDashboardService.class),
         mock(inetsoft.web.admin.content.database.model.DataModelFolderManagerService.class),
         mock(DataSourceRegistry.class), libProvider, mock(RecycleBin.class), handler,
         mock(RenameTransformHandler.class), mock(RepletRegistryManager.class),
         mock(inetsoft.sree.web.dashboard.DashboardRegistryManager.class));
      TreeNodeInfo[] nodes = {
         TreeNodeInfo.builder().label("caller").path("caller").type(RepositoryEntry.SCRIPT).build()
      };

      service.deleteNodes(nodes, principal, true, true);

      AssetEntry expected = new AssetEntry(
         AssetRepository.COMPONENT_SCOPE, AssetEntry.Type.SCRIPT, "caller", null);
      InOrder order = inOrder(handler, lib);
      order.verify(handler).updateScriptDependencies(eq("return callee();"), isNull(), eq(expected));
      order.verify(lib).removeScript("caller");
   }
}
