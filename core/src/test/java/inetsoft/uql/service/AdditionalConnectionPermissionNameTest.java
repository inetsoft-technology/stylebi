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
package inetsoft.uql.service;

import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.DataSourceFolder;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.util.ColumnCache;
import inetsoft.util.Tool;
import inetsoft.web.portal.controller.database.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.time.LocalDateTime;

import static inetsoft.web.admin.content.repository.ResourcePermissionService.getDataSourcePermissionName;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78249: the registry stores an additional connection add of P at P/add, but its
 * permission is keyed P::add. A DATA_SOURCE check on the raw P/add took it for a data source in
 * a folder P, which falls back to the root folder's READ for everyone, so a user denied P could
 * use P's additional connections through the client-path READ checks. The checks must use
 * P::add, and leave data sources in folders and permission names unchanged. The registry is the
 * real one.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  XEngineAdditionalConnectionSaveTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AdditionalConnectionPermissionNameTest {
   private static final String URL = "jdbc:derby:memory:bug78249;create=true";

   @Autowired
   private DataSourceRegistry registry;
   @Autowired
   private XRepository repository;

   @BeforeEach
   void setUp() throws Exception {
      registry.init();
      registry.setDataSourceFolder(new DataSourceFolder("pnF", LocalDateTime.now(), null));
      addParent("pnP", "pnAdd");
      addParent("pnF/pnQ", "pnAdd2");
   }

   // every path shape a client sends: P, P/add, F/P, F/P/add, and the permission names
   @Test
   void registryPathsAreCheckedByTheirPermissionNames() {
      assertEquals("pnP", getDataSourcePermissionName("pnP", registry));
      assertEquals("pnP::pnAdd", getDataSourcePermissionName("pnP/pnAdd", registry));
      assertEquals("pnF/pnQ", getDataSourcePermissionName("pnF/pnQ", registry));
      assertEquals("pnF/pnQ::pnAdd2", getDataSourcePermissionName("pnF/pnQ/pnAdd2", registry));
      assertEquals("pnP::pnAdd", getDataSourcePermissionName("pnP::pnAdd", registry));
      assertEquals("pnF/pnQ::pnAdd2", getDataSourcePermissionName("pnF/pnQ::pnAdd2", registry));
      assertEquals("pnP^_^pnAdd", getDataSourcePermissionName("pnP^_^pnAdd", registry));
      assertEquals("pnF/noSuch", getDataSourcePermissionName("pnF/noSuch", registry));
      assertNull(getDataSourcePermissionName(null, registry));
      // the registry of the Spring context is used when none is given
      assertEquals("pnP::pnAdd", getDataSourcePermissionName("pnP/pnAdd"));
      assertEquals("pnF/pnQ::pnAdd2", getDataSourcePermissionName("pnF/pnQ/pnAdd2"));
   }

   // a permission name is not rewritten again, even where a data source F lies at the start of
   // a folder data source F/P (older data), and a registry without names leaves the path as is
   @Test
   void permissionNamesAndEmptyRegistriesAreLeftAsIs() {
      DataSourceRegistry clash = mock(DataSourceRegistry.class);
      when(clash.getDataSourceFullNames()).thenReturn(new String[] { "F", "F/P" });

      assertEquals("F/P::add", getDataSourcePermissionName("F/P::add", clash));
      assertEquals("F/P::add", getDataSourcePermissionName("F/P/add", clash));
      assertEquals("P/add", getDataSourcePermissionName("P/add", mock(DataSourceRegistry.class)));
      assertEquals("P/add", getDataSourcePermissionName("P/add", null));
   }

   // the READ check of the SQL query dialog, the connection variables and the asset tree
   @Test
   void queryManagerReadCheckUsesThePermissionName() throws Exception {
      SecurityEngine security = deniedOnParentSecurity();
      QueryManagerService service = new QueryManagerService(
         mock(RuntimeQueryService.class), repository, mock(DataSourceService.class), security,
         mock(ColumnCache.class));

      for(String path : new String[] { "pnP/pnAdd", "pnP", "pnP::pnAdd" }) {
         assertThrows(java.lang.SecurityException.class,
                      () -> service.checkDataSourceReadPermission(path, principal()), path);
      }

      assertThrows(java.lang.SecurityException.class,
                   () -> service.checkDataSourceReadPermission("pnF/pnQ/pnAdd2", principal()));
      // a data source in a folder is checked at its path
      service.checkDataSourceReadPermission("pnF/pnQ", principal());
   }

   // the READ check of the data model editors
   @Test
   void dataSourceServiceCheckUsesThePermissionName() throws Exception {
      SecurityEngine security = deniedOnParentSecurity();
      DataSourceService service = new DataSourceService(
         mock(AssetRepository.class), security, repository, registry);

      assertFalse(service.checkPermission("pnP/pnAdd", ResourceAction.READ, principal()));
      assertFalse(service.checkPermission("pnF/pnQ/pnAdd2", ResourceAction.READ, principal()));
      assertTrue(service.checkPermission("pnF/pnQ", ResourceAction.READ, principal()));
      assertThrows(inetsoft.sree.security.SecurityException.class,
                   () -> service.checkDataSourceReadPermission("pnP/pnAdd", principal()));
   }

   // P and F/P/... are restricted; any other name (a raw P/add or F/P/add included) gets the
   // root folder's READ for everyone, as SecurityEngine does under the default configuration
   private static SecurityEngine deniedOnParentSecurity() throws Exception {
      SecurityEngine security = mock(SecurityEngine.class);
      when(security.checkPermission(any(), eq(ResourceType.DATA_SOURCE), anyString(),
                                    eq(ResourceAction.READ)))
         .thenAnswer(inv -> {
            String name = inv.getArgument(2);
            return !name.equals("pnP") && !name.startsWith("pnP::") &&
               !name.startsWith("pnF/pnQ::");
         });
      return security;
   }

   private void addParent(String path, String... additionals) {
      registry.setDataSource(source(path), false);
      JDBCDataSource parent = (JDBCDataSource) registry.getDataSource(path);

      for(String name : additionals) {
         JDBCDataSource additional = source(name);
         additional.setLastModified(System.currentTimeMillis());
         parent.addDatasource(additional);
      }
   }

   private static Principal principal() {
      return new SRPrincipal(new IdentityID("alice", Organization.getDefaultOrganizationID()),
                             new IdentityID[0], new String[0],
                             Organization.getDefaultOrganizationID(),
                             Tool.getSecureRandom().nextLong());
   }

   private static JDBCDataSource source(String name) {
      JDBCDataSource dataSource = new JDBCDataSource();
      dataSource.setName(name);
      dataSource.setCustom(true);
      dataSource.setCustomEditMode(true);
      dataSource.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      dataSource.setURL(URL);
      dataSource.setCustomUrl(URL);
      return dataSource;
   }
}
