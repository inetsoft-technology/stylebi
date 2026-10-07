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

import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.web.admin.content.database.DatabaseDefinition;
import inetsoft.web.admin.content.database.DatabaseTypeService;
import inetsoft.web.admin.content.database.types.AccessDatabaseType;
import inetsoft.web.admin.content.database.types.CustomDatabaseType;
import inetsoft.web.admin.general.DatabaseSettingsService;
import inetsoft.web.portal.service.datasource.DataSourceStatusService;
import inetsoft.web.session.IgniteSessionRepository;
import org.apache.commons.io.FileExistsException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77798: the private grant of the creator of a new data source is written after the rest of
 * the save, so that a failed permission write doesn't skip it. It must still be written when a
 * later step of the save fails once the data source is saved, and a failed grant must not hide
 * the failure of that step.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  DatabaseDatasourcesServiceAdditionalNodeSaveTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DatabaseDatasourcesServiceCreatorGrantTest {
   private static final String URL = "jdbc:derby:memory:bug77798;create=true";

   @Autowired
   private DataSourceRegistry registry;
   @Autowired
   private XRepository repository;
   private SecurityEngine security;
   private DatabaseDatasourcesService service;
   private Principal principal;

   @BeforeEach
   void setUp() throws Exception {
      registry.init();
      security = mock(SecurityEngine.class);
      when(security.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      // the creator may only create data sources, so the new one gets a private grant
      when(security.checkPermission(any(), eq(ResourceType.DATA_SOURCE_FOLDER), anyString(),
                                    any())).thenReturn(false);
      service = new DatabaseDatasourcesService(
         new DatabaseTypeService(List.of(new CustomDatabaseType(), new AccessDatabaseType())),
         security, mock(DatabaseSettingsService.class), repository,
         mock(ResourcePermissionService.class), mock(DataSourceStatusService.class),
         mock(IgniteSessionRepository.class), registry, mock(RenameTransformHandler.class));
      principal = new SRPrincipal(new IdentityID("alice", Organization.getDefaultOrganizationID()),
                                  new IdentityID[0], new String[0],
                                  Organization.getDefaultOrganizationID(),
                                  Tool.getSecureRandom().nextLong());
   }

   // two additional connections with the same name fail the save once the data source is saved
   @Test
   void grantIsWrittenWhenALaterStepFails() {
      assertThrows(FileExistsException.class, () -> create("grantLater"));

      assertNotNull(registry.getDataSource("grantLater"));
      verify(security).setPermission(eq(ResourceType.DATA_SOURCE), eq("grantLater"),
                                     argThat(this::grantsAlice));
   }

   @Test
   void failedGrantDoesNotHideTheFailureOfALaterStep() throws Exception {
      IllegalStateException grantFailure = new IllegalStateException("simulated");
      doThrow(grantFailure).when(security)
         .setPermission(eq(ResourceType.DATA_SOURCE), eq("grantBoth"), any());

      FileExistsException thrown =
         assertThrows(FileExistsException.class, () -> create("grantBoth"));

      assertArrayEquals(new Throwable[] { grantFailure }, thrown.getSuppressed());
      assertNotNull(registry.getDataSource("grantBoth"));
   }

   private void create(String name) throws Exception {
      DatabaseDefinition definition = definition(name);
      DatabaseDefinition first = definition(name + "Dup");
      DatabaseDefinition second = definition(name + "Dup");
      service.saveDatabase("", DataSourceSettingsModel.builder()
         .uploadEnabled(false).dataSource(definition).additionalDataSources(first, second)
         .build(), ActionRecord.ACTION_NAME_CREATE, principal);
   }

   private boolean grantsAlice(Permission permission) {
      return permission != null && permission.getUserGrants(ResourceAction.READ).stream()
         .anyMatch(identity -> "alice".equals(identity.getName()));
   }

   private static DatabaseDefinition definition(String name) {
      JDBCDataSource dataSource = new JDBCDataSource();
      dataSource.setName(name);
      dataSource.setCustom(true);
      dataSource.setCustomEditMode(true);
      dataSource.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      dataSource.setURL(URL);
      dataSource.setCustomUrl(URL);
      return JDBCUtil.buildDatabaseDefinition(
         dataSource, JDBCUtil.getJDBCDatabaseType(CustomDatabaseType.TYPE));
   }
}
