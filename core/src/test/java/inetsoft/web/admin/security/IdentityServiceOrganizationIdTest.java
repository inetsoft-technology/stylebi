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
package inetsoft.web.admin.security;

/*
 * Bug #77136: setIdentity is the shared rename path of the EM, the REST API, the shell and
 * admin-chat, so it must reject a rename to a reserved or unsafe organization id before any
 * change. updateTaskSaveFiles must not move a system folder of external storage when the old or
 * new id is reserved (a legacy organization named "backup").
 */

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.*;
import inetsoft.storage.ExternalStorageService;
import inetsoft.util.Catalog;
import inetsoft.util.MessageException;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.util.config.InetsoftConfig;
import inetsoft.web.admin.security.user.EditOrganizationPaneModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class IdentityServiceOrganizationIdTest {
   @BeforeEach
   void setUp() {
      sUtilStatic = mockStatic(SUtil.class, withSettings().strictness(Strictness.LENIENT));
      sUtilStatic.when(() -> SUtil.getActionRecord(any(Principal.class), anyString(), any(), anyString()))
         .thenReturn(mock(ActionRecord.class));
      sUtilStatic.when(SUtil::isMultiTenant).thenReturn(true);
      auditStatic = mockStatic(Audit.class, withSettings().strictness(Strictness.LENIENT));
      auditStatic.when(Audit::getInstance).thenReturn(mock(Audit.class));
      securityEngineStatic = mockStatic(SecurityEngine.class, withSettings().strictness(Strictness.LENIENT));
      InetsoftConfig config = mock(InetsoftConfig.class, withSettings().lenient());
      configStatic = mockStatic(InetsoftConfig.class, withSettings().strictness(Strictness.LENIENT));
      configStatic.when(InetsoftConfig::getInstance).thenReturn(config);

      SecurityEngine securityEngine = mock(SecurityEngine.class, withSettings().lenient());
      when(securityEngine.isSecurityEnabled()).thenReturn(false);
      externalStorageService = mock(ExternalStorageService.class);
      cluster = mock(Cluster.class);

      service = mock(IdentityService.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
      ReflectionTestUtils.setField(service, "securityEngine", securityEngine);
      ReflectionTestUtils.setField(service, "externalStorageService", externalStorageService);
      ReflectionTestUtils.setField(service, "cluster", cluster);
      ReflectionTestUtils.setField(service, "LOG", LoggerFactory.getLogger(IdentityService.class));

      eprovider = mock(EditableAuthenticationProvider.class, withSettings().lenient());
      principal = mock(Principal.class);
   }

   @AfterEach
   void tearDown() {
      sUtilStatic.close();
      auditStatic.close();
      securityEngineStatic.close();
      configStatic.close();
   }

   @ParameterizedTest
   @CsvSource({ "orgA,backup", "orgA,HEAPDUMP", "orgA,..", "orgA,a/b", "backup,status" })
   void setIdentity_renameToReservedOrUnsafeId_rejectedBeforeAnyChange(String oldId, String newId) {
      FSOrganization org = new FSOrganization(new IdentityID("Org A", oldId));
      EditOrganizationPaneModel model = orgModel("Org A", newId);

      MessageException thrown = assertThrows(MessageException.class,
                                             () -> service.setIdentity(org, model, eprovider, principal));

      assertEquals(Catalog.getCatalog().getString("em.security.reservedOrganizationID", newId),
                   thrown.getMessage());
      verify(eprovider, never()).getUsers();
      verify(eprovider, never()).setOrganization(any(), any());
      verify(eprovider, never()).copyOrganization(any(), any(), any(), any(), any(), any(), any(),
                                                  anyBoolean(), any());
      verifyNoInteractions(cluster);
   }

   @Test
   void setIdentity_legacyReservedIdUnchanged_nameEditStillSaved() throws Exception {
      FSOrganization org = new FSOrganization(new IdentityID("Backup Org", "backup"));
      when(eprovider.getOrganizationNames()).thenReturn(new String[]{ "Backup Org" });
      when(eprovider.getOrganization("backup")).thenReturn(org);

      service.setIdentity(org, orgModel("Backup Renamed", "backup"), eprovider, principal);

      verify(eprovider).setOrganization(eq("backup"), argThat(o -> "Backup Renamed".equals(o.getName())));
   }

   @Test
   void setIdentity_nullNewId_isNoChange() throws Exception {
      FSOrganization org = new FSOrganization(new IdentityID("Org A", "orgA"));
      EditOrganizationPaneModel model = EditOrganizationPaneModel.builder()
         .name("Org A").oldName("Org A").id(null).theme(null).build();

      try {
         service.setIdentity(org, model, eprovider, principal);
      }
      catch(MessageException e) {
         fail("a null id must not be checked as a rename: " + e.getMessage());
      }
      catch(Exception ignore) {
         // later steps of the rename are out of scope, only the id gate matters here
      }

      // the save went past the id gate
      verify(eprovider, atLeastOnce()).getUsers();
   }

   // Bug #77169: the default and self organization ids and names are hard-coded constants, so
   // setIdentity must refuse to change them on every save path, not only in the EM.
   @ParameterizedTest
   @CsvSource(value = { "Host Organization,host-org,renamed-org", "Self Organization,SELF,renamed-self",
                        "Host Organization,host-org,HOST-ORG", "Host Organization,host-org,NULL",
                        "Self Organization,SELF,NULL" }, nullValues = "NULL")
   void setIdentity_changeDefaultOrSelfOrgId_rejectedBeforeAnyChange(String name, String oldId,
                                                                    String newId)
   {
      FSOrganization org = new FSOrganization(new IdentityID(name, oldId));

      MessageException thrown = assertThrows(
         MessageException.class, () -> service.setIdentity(org, orgModel(name, newId), eprovider, principal));

      assertEquals(Catalog.getCatalog().getString("em.security.writeDefaultOrgId"), thrown.getMessage());
      assertNothingSaved();
   }

   @ParameterizedTest
   @CsvSource({ "Host Organization,host-org", "Self Organization,SELF" })
   void setIdentity_changeDefaultOrSelfOrgName_rejectedBeforeAnyChange(String name, String id) {
      FSOrganization org = new FSOrganization(new IdentityID(name, id));
      when(eprovider.getOrganizationNames()).thenReturn(new String[]{ name });
      when(eprovider.getOrganization(id)).thenReturn(org);
      EditOrganizationPaneModel model = EditOrganizationPaneModel.builder()
         .name("Renamed").oldName(name).id(id).theme(null).build();

      MessageException thrown = assertThrows(
         MessageException.class, () -> service.setIdentity(org, model, eprovider, principal));

      assertEquals(Catalog.getCatalog().getString("em.security.writeDefaultOrgName"), thrown.getMessage());
      assertNothingSaved();
   }

   @ParameterizedTest
   @CsvSource({ "Host Organization,host-org", "Self Organization,SELF", "Org A,orgB" })
   void setIdentity_defaultOrgUnchangedOrNormalOrgRenamed_passesTheGate(String name, String newId) {
      String oldId = "Org A".equals(name) ? "orgA" : "host-org";
      oldId = "Self Organization".equals(name) ? "SELF" : oldId;
      FSOrganization org = new FSOrganization(new IdentityID(name, oldId));

      try {
         // a member, theme or locale edit keeps the id and name
         service.setIdentity(org, orgModel(name, newId), eprovider, principal);
      }
      catch(MessageException e) {
         fail("the edit must not be refused: " + e.getMessage());
      }
      catch(Exception ignore) {
         // later steps of the save are out of scope, only the gate matters here
      }

      verify(eprovider, atLeastOnce()).getUsers();
   }

   private void assertNothingSaved() {
      verify(eprovider, never()).getUsers();
      verify(eprovider, never()).setOrganization(any(), any());
      verify(eprovider, never()).copyOrganization(any(), any(), any(), any(), any(), any(), any(),
                                                  anyBoolean(), any());
      verifyNoInteractions(cluster);
   }

   @ParameterizedTest
   @CsvSource({ "backup,orgB", "orgA,backup", "status,orgB", "orgA,HeapDump" })
   void updateTaskSaveFiles_reservedOldOrNewId_doesNotMoveFolder(String oldId, String newId)
      throws Exception
   {
      service.updateTaskSaveFiles(new FSOrganization(oldId), new FSOrganization(newId));

      verify(externalStorageService, never()).renameFolder(any(), any());
   }

   @Test
   void updateTaskSaveFiles_normalRename_movesFolder() throws Exception {
      service.updateTaskSaveFiles(new FSOrganization("orgA"), new FSOrganization("orgB"));

      verify(externalStorageService).renameFolder("orgA", "orgB");
   }

   private static EditOrganizationPaneModel orgModel(String name, String id) {
      return EditOrganizationPaneModel.builder()
         .name(name)
         .oldName(name.equals("Backup Renamed") ? "Backup Org" : name)
         .id(id)
         .theme(null)
         .build();
   }

   private IdentityService service;
   private EditableAuthenticationProvider eprovider;
   private ExternalStorageService externalStorageService;
   private Cluster cluster;
   private Principal principal;
   private MockedStatic<SUtil> sUtilStatic;
   private MockedStatic<Audit> auditStatic;
   private MockedStatic<SecurityEngine> securityEngineStatic;
   private MockedStatic<InetsoftConfig> configStatic;
}
