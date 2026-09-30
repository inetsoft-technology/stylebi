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
package inetsoft.web.admin.datasource;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.XDataSource;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.XRepository;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.MessageException;
import inetsoft.util.Tool;
import inetsoft.util.audit.Audit;
import inetsoft.util.credential.CloudCredential;
import inetsoft.util.credential.PasswordCredential;
import inetsoft.web.admin.content.database.DatabaseTypeService;
import inetsoft.web.admin.idgen.MD5IdentifierGenerator;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77157: the public data source API must not let a caller point a JDBC data source at a
 * cloud secret id that the caller does not already manage. (Ported from the enterprise
 * DataSourceApiServiceSecretIdTest; the business logic lives in this community class and
 * DataSourceApiService only delegates to it.)
 */
@Tag("core")
class DataSourceServiceSecretIdTest {
   @BeforeEach
   void setUp() throws Exception {
      sreeEnv = mockStatic(SreeEnv.class);
      tool = mockStatic(Tool.class, CALLS_REAL_METHODS);
      tool.when(Tool::isCloudSecrets).thenReturn(true);
      sutil = mockStatic(SUtil.class);
      sutil.when(() -> SUtil.getUserName(any())).thenReturn("alice");
      audit = mockStatic(Audit.class);
      audit.when(Audit::getInstance).thenReturn(mock(Audit.class));
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance)
         .thenReturn(mock(OrganizationManager.class));

      stored = mock(JDBCDataSource.class);
      PasswordCredential credential = mock(PasswordCredential.class,
         withSettings().extraInterfaces(CloudCredential.class));
      when(credential.getId()).thenReturn(OWN_ID);
      when(stored.getCredential()).thenReturn(credential);
      when(stored.getDataSourceNames()).thenReturn(new String[0]);
      when(stored.getType()).thenReturn(XDataSource.JDBC);
      when(stored.getFullName()).thenReturn("db");

      repository = mock(XRepository.class);
      when(repository.getDataSource("db")).thenReturn(stored);
      securityEngine = mock(SecurityEngine.class);
      when(securityEngine.isSecurityEnabled()).thenReturn(true);
      when(securityEngine.getSecurityProvider()).thenReturn(mock(SecurityProvider.class));
      securityEngineStatic = mockStatic(SecurityEngine.class);
      securityEngineStatic.when(SecurityEngine::getSecurity).thenReturn(securityEngine);
      when(securityEngine.checkPermission(any(), eq(ResourceType.DATA_SOURCE), eq("db"),
                                          eq(ResourceAction.WRITE))).thenReturn(true);
      MD5IdentifierGenerator ids = mock(MD5IdentifierGenerator.class);
      when(ids.getName("id1")).thenReturn("db");
      DataSourceRegistry registry = mock(DataSourceRegistry.class);
      when(registry.getDataSourceFullNames()).thenReturn(new String[] { "db" });
      when(registry.getDataSource("db")).thenReturn(stored);

      service = new DataSourceService(
         repository, securityEngine, mock(DatabaseTypeService.class), ids, registry);
      principal = mock(XPrincipal.class);
      when(principal.getName()).thenReturn(new IdentityID("alice", "orga").convertToKey());
   }

   @AfterEach
   void tearDown() {
      securityEngineStatic.close();
      orgManagerStatic.close();
      audit.close();
      sutil.close();
      tool.close();
      sreeEnv.close();
   }

   @Test
   void updateRejectsForeignSecretId() {
      assertThrows(MessageException.class, () -> service.updateDataSource(
         "id1", properties(FOREIGN_ID), principal));

      verify(stored, never()).setCredentialId(any());
      verify(stored, never()).setName(any());
      verifyNoSave();
   }

   @Test
   void updateAcceptsUnchangedSecretId() throws Exception {
      service.updateDataSource("id1", properties(OWN_ID), principal);

      verify(stored).setCredentialId(OWN_ID);
      verify(repository).updateDataSource(stored, "db", false);
   }

   private void verifyNoSave() {
      try {
         verify(repository, never()).updateDataSource(any(), any(), anyBoolean());
      }
      catch(Exception e) {
         throw new RuntimeException(e);
      }
   }

   private static JdbcDataSourceProperties properties(String secretId) {
      JdbcDataSourceProperties properties = new JdbcDataSourceProperties();
      properties.setName("db");
      properties.setUrl("jdbc:h2:mem:test");
      properties.setDriver("org.h2.Driver");
      properties.setTableName(JdbcDataSourceProperties.DEFAULT_OPTION);
      properties.setRequireLogin(true);
      properties.setUseCredentialId(true);
      properties.setCredentialID(secretId);
      return properties;
   }

   private static final String OWN_ID = "own-db-secret";
   private static final String FOREIGN_ID = "org-b-db-secret";

   private DataSourceService service;
   private JDBCDataSource stored;
   private XRepository repository;
   private SecurityEngine securityEngine;
   private XPrincipal principal;
   private MockedStatic<SreeEnv> sreeEnv;
   private MockedStatic<Tool> tool;
   private MockedStatic<SUtil> sutil;
   private MockedStatic<Audit> audit;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<SecurityEngine> securityEngineStatic;
}
