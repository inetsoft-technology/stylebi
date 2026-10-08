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
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.content.repository.model.ScriptSettingsModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77844, a script rename or save that fails is audited as a failure with its message.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RepositoryScriptControllerAuditTest {
   private final List<ActionRecord> records = new ArrayList<>();
   private MockedStatic<Audit> auditStatic;
   private MockedStatic<SecurityEngine> securityStatic;
   private LibManager manager;
   private RepositoryScriptController controller;
   private Principal principal;

   @BeforeEach
   void setUp() throws Exception {
      records.clear();
      SecurityEngine security = mock(SecurityEngine.class);
      when(security.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      // ActionRecord's constructor reads the user from the security provider
      when(security.getSecurityProvider()).thenReturn(mock(SecurityProvider.class));
      securityStatic = mockStatic(SecurityEngine.class, CALLS_REAL_METHODS);
      securityStatic.when(SecurityEngine::getSecurity).thenReturn(security);
      Audit audit = mock(Audit.class);
      doAnswer(inv -> records.add(inv.getArgument(0)))
         .when(audit).auditAction(any(ActionRecord.class), any());
      auditStatic = mockStatic(Audit.class);
      auditStatic.when(Audit::getInstance).thenReturn(audit);

      ResourcePermissionService permissions = mock(ResourcePermissionService.class);
      when(permissions.getRepositoryResourceType(anyInt(), anyString())).thenAnswer(
         inv -> new Resource(ResourceType.SCRIPT, inv.<String>getArgument(1)));
      manager = mock(LibManager.class);
      LibManagerProvider provider = mock(LibManagerProvider.class);
      when(provider.getManager(any(Principal.class))).thenReturn(manager);
      controller = new RepositoryScriptController(permissions, security, provider);
      principal = new SRPrincipal(new IdentityID("admin", Organization.getDefaultOrganizationID()),
                                  new IdentityID[0], new String[0],
                                  Organization.getDefaultOrganizationID(),
                                  Tool.getSecureRandom().nextLong());
   }

   @AfterEach
   void tearDown() {
      auditStatic.close();
      securityStatic.close();
   }

   @Test
   void failedRenameIsAuditedFailure() {
      doThrow(new RuntimeException("rename failed")).when(manager).renameScript("s1", "s2");
      ScriptSettingsModel model = ScriptSettingsModel.builder().name("s2").oname("s1").build();

      assertThrows(RuntimeException.class,
                   () -> controller.setScriptModel("s1", 0, model, principal));

      assertSingleRecord(ActionRecord.ACTION_STATUS_FAILURE, "rename failed");
   }

   @Test
   void failedSaveIsAuditedFailure() throws Exception {
      when(manager.getScriptComment("s1")).thenReturn("old");
      doThrow(new Exception("save failed")).when(manager).save();
      ScriptSettingsModel model = ScriptSettingsModel.builder().name("s1").oname("s1")
         .description("new").build();

      assertThrows(RuntimeException.class,
                   () -> controller.setScriptModel("s1", 0, model, principal));

      assertSingleRecord(ActionRecord.ACTION_STATUS_FAILURE, "save failed");
   }

   @Test
   void saveIsAuditedSuccess() throws Exception {
      when(manager.getScriptComment("s1")).thenReturn("old");
      ScriptSettingsModel model = ScriptSettingsModel.builder().name("s1").oname("s1")
         .description("new").build();

      controller.setScriptModel("s1", 0, model, principal);

      verify(manager).save();
      assertSingleRecord(ActionRecord.ACTION_STATUS_SUCCESS, "");
   }

   @Test
   void renameIsAuditedSuccessWithNewName() throws Exception {
      ScriptSettingsModel model = ScriptSettingsModel.builder().name("s2").oname("s1").build();

      controller.setScriptModel("s1", 0, model, principal);

      verify(manager).renameScript("s1", "s2");
      assertSingleRecord(ActionRecord.ACTION_STATUS_SUCCESS, "new name:s2");
   }

   private void assertSingleRecord(String status, String error) {
      assertEquals(1, records.size());
      ActionRecord record = records.get(0);
      assertEquals(status, record.getActionStatus());
      assertEquals(error, record.getActionError());
      assertEquals("Script Function/s1", record.getObjectName());
      assertEquals(ActionRecord.ACTION_NAME_EDIT, record.getActionName());
   }
}
