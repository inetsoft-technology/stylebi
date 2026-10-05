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

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.DataSourceFolder;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.service.DataSourceRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77700: the registry removes the permission of a removed data source in the current
 * organization only. A data source of the same name in another organization keeps its
 * permission. The registry and the permission store are the production ones.
 * <p>
 * Bug #77731: the same for the permission of a subfolder of a removed data source folder.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  AdditionalConnectionPermissionRemovalTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DataSourcePermissionRemovalOrgTest {
   private static final String ORG_A = "orga";
   private static final String ORG_B = "orgb";
   private static final String DS = "orgScopedDs";
   private static final String FOLDER = "orgScopedF";
   private static final String SUBFOLDER = FOLDER + "/G";
   private static final String URL = "jdbc:derby:memory:bug77700org;create=true";

   @Autowired
   private DataSourceRegistry registry;
   private AuthorizationProvider authorization;

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
      // only the access check is stubbed (the engine bean is a spy of a real SecurityEngine)
      doReturn(true).when(SecurityEngine.getSecurity()).checkPermission(
         any(Principal.class), any(ResourceType.class), anyString(), any(ResourceAction.class));
      authorization = SecurityEngine.getSecurity().getSecurityProvider().getAuthorizationProvider();
   }

   @AfterEach
   void tearDown() {
      for(String org : new String[] { ORG_A, ORG_B }) {
         authorization.removePermission(ResourceType.DATA_SOURCE, DS, org);
         authorization.removePermission(ResourceType.DATA_SOURCE_FOLDER, SUBFOLDER, org);
      }

      reset(SecurityEngine.getSecurity());
      OrganizationContextHolder.clear();
      SreeEnv.remove("security.enabled");
      SreeEnv.remove("security.users.multiTenant");
   }

   @Test
   void removeInOneOrgKeepsThePermissionOfTheOtherOrg() throws Exception {
      for(String org : new String[] { ORG_A, ORG_B }) {
         OrganizationContextHolder.setCurrentOrgId(org);
         registry.init();
         registry.setDataSource(source(), false);
         authorization.setPermission(ResourceType.DATA_SOURCE, DS, grant(org), org);
      }

      OrganizationContextHolder.setCurrentOrgId(ORG_A);
      registry.removeDataSource(DS);
      registry.clearCache();

      assertNull(registry.getDataSource(DS), "the data source of " + ORG_A);
      assertNull(authorization.getPermission(ResourceType.DATA_SOURCE, DS, ORG_A));

      OrganizationContextHolder.setCurrentOrgId(ORG_B);
      registry.clearCache();
      assertNotNull(registry.getDataSource(DS), "the data source of " + ORG_B);
      Permission kept = authorization.getPermission(ResourceType.DATA_SOURCE, DS, ORG_B);
      assertNotNull(kept, "the permission of " + ORG_B + " was removed");
      assertFalse(kept.getUserGrants(ResourceAction.READ, ORG_B).isEmpty());
   }

   // Bug #77731: the same for the permission of a subfolder of a removed folder
   @Test
   void removeFolderInOneOrgKeepsThePermissionOfTheOtherOrg() throws Exception {
      for(String org : new String[] { ORG_A, ORG_B }) {
         OrganizationContextHolder.setCurrentOrgId(org);
         registry.init();
         registry.setDataSourceFolder(new DataSourceFolder(FOLDER, LocalDateTime.now(), null));
         registry.setDataSourceFolder(
            new DataSourceFolder(SUBFOLDER, LocalDateTime.now(), null));
         authorization.setPermission(ResourceType.DATA_SOURCE_FOLDER, SUBFOLDER, grant(org), org);
      }

      OrganizationContextHolder.setCurrentOrgId(ORG_A);
      registry.removeDataSourceFolder(FOLDER);
      registry.clearCache();

      assertNull(registry.getDataSourceFolder(SUBFOLDER), "the subfolder of " + ORG_A);
      assertNull(authorization.getPermission(ResourceType.DATA_SOURCE_FOLDER, SUBFOLDER, ORG_A));

      OrganizationContextHolder.setCurrentOrgId(ORG_B);
      registry.clearCache();
      assertNotNull(registry.getDataSourceFolder(SUBFOLDER), "the subfolder of " + ORG_B);
      Permission kept =
         authorization.getPermission(ResourceType.DATA_SOURCE_FOLDER, SUBFOLDER, ORG_B);
      assertNotNull(kept, "the permission of " + ORG_B + " was removed");
      assertFalse(kept.getUserGrants(ResourceAction.READ, ORG_B).isEmpty());
   }

   private static Permission grant(String org) {
      Permission permission = new Permission();
      permission.setUserGrantsForOrg(ResourceAction.READ, Set.of("alice"), org);
      return permission;
   }

   private static JDBCDataSource source() {
      JDBCDataSource dataSource = new JDBCDataSource();
      dataSource.setName(DS);
      dataSource.setCustom(true);
      dataSource.setCustomEditMode(true);
      dataSource.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      dataSource.setURL(URL);
      dataSource.setCustomUrl(URL);
      return dataSource;
   }
}
