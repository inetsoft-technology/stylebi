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

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XLogicalModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77459: renaming a foldered logical model moves its QUERY permission through the real
 * SecurityEngine and FileAuthorizationProvider, as in production. In a multi-tenant setup the
 * move must stay inside the current organization, leave the same key of another organization
 * alone, and replace a stale permission already stored under the new name.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class LogicalModelPermissionRekeyOrgTest {
   private static final String ORG = "orga";
   private static final String DS = "DS";
   private static final String OLD_KEY = "LM::DS^__^F";
   private static final String NEW_KEY = "New::DS^__^F";

   @BeforeEach
   void setUp() throws Exception {
      SreeEnv.setProperty("security.enabled", "true");
      SreeEnv.setProperty("security.users.multiTenant", "true");
      SreeEnv.save();
      AuthenticationChain authcChain = new AuthenticationChain();
      authcChain.setProviders(List.of(new FileAuthenticationProvider()));
      authcChain.saveConfiguration();
      FileAuthorizationProvider authz = new FileAuthorizationProvider();
      authz.setProviderName("Primary");
      AuthorizationChain authzChain = new AuthorizationChain();
      authzChain.setProviders(List.of(authz));
      authzChain.saveConfiguration();
      SecurityEngine.getSecurity().init();
      OrganizationContextHolder.setCurrentOrgId(ORG);
      authorization = SecurityEngine.getSecurity().getSecurityProvider().getAuthorizationProvider();
   }

   @AfterEach
   void tearDown() {
      for(String org : new String[] { ORG, Organization.getDefaultOrganizationID() }) {
         authorization.removePermission(ResourceType.QUERY, OLD_KEY, org);
         authorization.removePermission(ResourceType.QUERY, NEW_KEY, org);
      }

      OrganizationContextHolder.clear();
      SreeEnv.remove("security.enabled");
      SreeEnv.remove("security.users.multiTenant");
   }

   @Test
   void renameModel_inNonDefaultOrg_movesPermissionWithinOrgAndReplacesStaleOne()
      throws Exception
   {
      String hostOrg = Organization.getDefaultOrganizationID();
      authorization.setPermission(ResourceType.QUERY, OLD_KEY, grant("alice", ORG), ORG);
      authorization.setPermission(ResourceType.QUERY, NEW_KEY, grant("stale", ORG), ORG);
      authorization.setPermission(ResourceType.QUERY, OLD_KEY, grant("host", hostOrg), hostOrg);

      XLogicalModel model = new XLogicalModel("LM");
      model.setFolder("F");
      XDataModel dataModel = mock(XDataModel.class);
      when(dataModel.getDataSource()).thenReturn(DS);
      when(dataModel.getLogicalModel("LM")).thenReturn(model);
      XRepository repository = mock(XRepository.class);
      when(repository.getDataModel(DS)).thenReturn(dataModel);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.getDataModel(DS)).thenReturn(dataModel);
      when(dataSourceService.getModelAssetEntry(any())).thenAnswer(i -> i.getArgument(0));

      // the permission store is the production one; the access check goes to the mocked
      // AssetRepository, so the shared engine spy is not stubbed
      SecurityEngine engine = SecurityEngine.getSecurity();
      LogicalModelService service = new LogicalModelService(
         engine, repository, dataSourceService, mock(DataRefModelFactoryService.class),
         mock(LogicalModelTreeService.class), mock(AssetRepository.class),
         mock(DependencyHandler.class), mock(RenameTransformHandler.class));

      service.renameModel(DS, "F", "New", "LM", null, () -> "admin");

      assertEquals(Set.of("alice"), users(NEW_KEY, ORG));
      assertNull(authorization.getPermission(ResourceType.QUERY, OLD_KEY, ORG));
      assertEquals(Set.of("host"), users(OLD_KEY, hostOrg));
      assertNull(authorization.getPermission(ResourceType.QUERY, NEW_KEY, hostOrg));
   }

   private static Permission grant(String user, String org) {
      Permission permission = new Permission();
      permission.setUserGrantsForOrg(ResourceAction.READ, Set.of(user), org);
      return permission;
   }

   private Set<String> users(String resource, String org) {
      Permission permission = authorization.getPermission(ResourceType.QUERY, resource, org);
      assertNotNull(permission, resource + " in " + org);
      Set<String> names = new HashSet<>();
      permission.getUserGrants(ResourceAction.READ, org).forEach(g -> names.add(g.getName()));
      return names;
   }

   private AuthorizationProvider authorization;
}
