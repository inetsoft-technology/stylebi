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
package inetsoft.web.admin.ai.file;

import inetsoft.sree.security.OrganizationManager;
import inetsoft.util.DataSpace;
import inetsoft.util.Tool;
import inetsoft.web.admin.ai.ResolvedPlan;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class StoredAssetChangePlanServiceTest {
   @Mock private DataSpace dataSpace;
   @Mock private Principal principal;
   @Mock private OrganizationManager orgManager;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<Tool> tool;
   private StoredAssetChangePlanService service;

   @BeforeEach
   void setup() {
      service = new StoredAssetChangePlanService(dataSpace);
      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      lenient().when(orgManager.getCurrentOrgID()).thenReturn("host-org");
      // ResolvedPlan.taskToken() is issued via TaskAuditToken.issue -> Tool.encryptPassword,
      // which needs a live Spring-managed keystore outside this unit test -- stub it, same as
      // every other area's own ChangePlanServiceTest (e.g. ClusterChangePlanServiceTest).
      tool = mockStatic(Tool.class, withSettings().strictness(Strictness.LENIENT)
         .defaultAnswer(Answers.CALLS_REAL_METHODS));
      tool.when(() -> Tool.encryptPassword(anyString()))
         .thenAnswer(inv -> "TKN:" + inv.getArgument(0));
   }

   @AfterEach
   void tearDown() {
      orgManagerStatic.close();
      tool.close();
   }

   private static StoredAssetChangePlanRequest request(StoredAssetChangeRequest... changes) {
      StoredAssetChangePlanRequest req = new StoredAssetChangePlanRequest();
      req.setTask("test task");
      req.setChanges(List.of(changes));
      return req;
   }

   private static StoredAssetChangeRequest change(String unitType, String verb, String path) {
      StoredAssetChangeRequest c = new StoredAssetChangeRequest();
      c.setUnitType(unitType);
      c.setVerb(verb);
      c.setPath(path);
      return c;
   }

   @Test
   void resolveRejectsBlankTask() {
      StoredAssetChangePlanRequest req = request(
         change(StoredAssetChangeRequest.UNIT_FOLDER, StoredAssetChangeRequest.VERB_CREATE, "a"));
      req.setTask("  ");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(req, principal));
      assertTrue(ex.getMessage().contains("task"), ex.getMessage());
   }

   @Test
   void resolveRejectsEmptyChangeList() {
      StoredAssetChangePlanRequest req = request();
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(req, principal));
      assertTrue(ex.getMessage().contains("changes"), ex.getMessage());
   }

   @Test
   void resolveRejectsAVerbNotValidForTheUnitType() {
      StoredAssetChangePlanRequest req = request(
         change(StoredAssetChangeRequest.UNIT_FOLDER, StoredAssetChangeRequest.VERB_WRITE, "a"));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(req, principal));
      assertTrue(ex.getMessage().contains("verb"), ex.getMessage());
   }

   @Test
   void resolveRejectsAnUnrecognizedUnitType() {
      StoredAssetChangePlanRequest req = request(
         change("directory", StoredAssetChangeRequest.VERB_CREATE, "a"));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(req, principal));
      assertTrue(ex.getMessage().contains("unitType"), ex.getMessage());
   }

   @Test
   void resolveRejectsATraversalPathBeforeTouchingDataSpace() {
      StoredAssetChangePlanRequest req = request(
         change(StoredAssetChangeRequest.UNIT_FOLDER, StoredAssetChangeRequest.VERB_CREATE,
                "../etc/passwd"));

      assertThrows(IllegalArgumentException.class, () -> service.resolve(req, principal));
      verifyNoInteractions(dataSpace);
   }

   @Test
   void resolveRejectsDuplicatePathEntries() {
      StoredAssetChangePlanRequest req = request(
         change(StoredAssetChangeRequest.UNIT_FOLDER, StoredAssetChangeRequest.VERB_CREATE, "a"),
         change(StoredAssetChangeRequest.UNIT_FOLDER, StoredAssetChangeRequest.VERB_DELETE, "a"));
      when(dataSpace.exists(null, "a")).thenReturn(false);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(req, principal));
      assertTrue(ex.getMessage().contains("duplicate"), ex.getMessage());
   }

   @Test
   void resolveCreateRefusesWhenThePathAlreadyExists() {
      when(dataSpace.exists(null, "scripts/lib")).thenReturn(true);
      StoredAssetChangePlanRequest req = request(
         change(StoredAssetChangeRequest.UNIT_FOLDER, StoredAssetChangeRequest.VERB_CREATE,
                "scripts/lib"));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(req, principal));
      assertTrue(ex.getMessage().contains("already exists"), ex.getMessage());
   }

   @Test
   void resolveCreateSucceedsAsLowRiskForANewPath() {
      when(dataSpace.exists(null, "scripts/lib")).thenReturn(false);
      StoredAssetChangePlanRequest req = request(
         change(StoredAssetChangeRequest.UNIT_FOLDER, StoredAssetChangeRequest.VERB_CREATE,
                "scripts/lib"));

      ResolvedPlan plan = service.resolve(req, principal);

      assertFalse(plan.requiresAgentSignoff(), "a bare create is low risk");
      assertTrue(plan.requiresStorageBackup());
      assertNotNull(plan.planHash());
      assertNotNull(plan.taskToken());
   }

   @Test
   void resolveWriteRequiresContent() {
      StoredAssetChangeRequest c =
         change(StoredAssetChangeRequest.UNIT_FILE, StoredAssetChangeRequest.VERB_WRITE, "a.js");
      // content is validated before any DataSpace call, so no stub is exercised here.
      StoredAssetChangePlanRequest req = request(c);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(req, principal));
      assertTrue(ex.getMessage().contains("content"), ex.getMessage());
   }

   @Test
   void resolveWriteOverExistingFileIsHighRiskAndRequiresSignoff() {
      StoredAssetChangeRequest c =
         change(StoredAssetChangeRequest.UNIT_FILE, StoredAssetChangeRequest.VERB_WRITE, "a.js");
      c.setContent("console.log(1);");
      when(dataSpace.exists(null, "a.js")).thenReturn(true);
      when(dataSpace.isDirectory("a.js")).thenReturn(false);
      when(dataSpace.getFileLength(null, "a.js")).thenReturn(10L);

      ResolvedPlan plan = service.resolve(request(c), principal);

      assertTrue(plan.requiresAgentSignoff(), "overwriting an existing file is high risk");
   }

   @Test
   void resolveRenameRefusesWhenDestinationAlreadyExists() {
      when(dataSpace.exists(null, "a.js")).thenReturn(true);
      when(dataSpace.isDirectory("a.js")).thenReturn(false);
      when(dataSpace.exists(null, "b.js")).thenReturn(true);
      StoredAssetChangeRequest c =
         change(StoredAssetChangeRequest.UNIT_FILE, StoredAssetChangeRequest.VERB_RENAME, "a.js");
      c.setNewName("b.js");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request(c), principal));
      assertTrue(ex.getMessage().contains("already exists"), ex.getMessage());
   }

   @Test
   void resolveRenameRejectsANewNameContainingASeparator() {
      // newName is validated before any DataSpace call, so no stub is exercised here.
      StoredAssetChangeRequest c =
         change(StoredAssetChangeRequest.UNIT_FILE, StoredAssetChangeRequest.VERB_RENAME, "a.js");
      c.setNewName("sub/b.js");

      assertThrows(IllegalArgumentException.class, () -> service.resolve(request(c), principal));
   }

   @Test
   void resolveDeleteRefusesTheDataSpaceRoot() {
      StoredAssetChangeRequest c =
         change(StoredAssetChangeRequest.UNIT_FOLDER, StoredAssetChangeRequest.VERB_DELETE, "/");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request(c), principal));
      assertTrue(ex.getMessage().contains("root"), ex.getMessage());
      verifyNoInteractions(dataSpace);
   }

   @Test
   void resolveDeleteOfAFolderIsAlwaysNonCompensable() {
      when(dataSpace.exists(null, "old-folder")).thenReturn(true);
      when(dataSpace.isDirectory("old-folder")).thenReturn(true);
      StoredAssetChangeRequest c =
         change(StoredAssetChangeRequest.UNIT_FOLDER, StoredAssetChangeRequest.VERB_DELETE, "old-folder");

      List<StoredAssetChangePlanService.ResolvedChange> resolved =
         service.resolveEntries(request(c));

      assertFalse(resolved.get(0).compensable());
   }

   @Test
   void resolveDeleteOfASmallTextFileIsCompensable() throws Exception {
      when(dataSpace.exists(null, "a.js")).thenReturn(true);
      when(dataSpace.isDirectory("a.js")).thenReturn(false);
      when(dataSpace.getFileLength(null, "a.js")).thenReturn(20L);
      when(dataSpace.getInputStream(null, "a.js"))
         .thenAnswer(inv -> new java.io.ByteArrayInputStream("console.log(1);".getBytes()));
      StoredAssetChangeRequest c =
         change(StoredAssetChangeRequest.UNIT_FILE, StoredAssetChangeRequest.VERB_DELETE, "a.js");

      List<StoredAssetChangePlanService.ResolvedChange> resolved =
         service.resolveEntries(request(c));

      assertTrue(resolved.get(0).compensable());
      assertEquals("console.log(1);", resolved.get(0).priorText());
   }

   @Test
   void hashIsStableForTheSameCanonicalInputAndChangesWhenAPropertyDiffers() {
      when(dataSpace.exists(null, "a")).thenReturn(false);
      StoredAssetChangePlanRequest req1 = request(
         change(StoredAssetChangeRequest.UNIT_FOLDER, StoredAssetChangeRequest.VERB_CREATE, "a"));
      ResolvedPlan plan1 = service.resolve(req1, principal);

      when(dataSpace.exists(null, "a")).thenReturn(false);
      StoredAssetChangePlanRequest req2 = request(
         change(StoredAssetChangeRequest.UNIT_FOLDER, StoredAssetChangeRequest.VERB_CREATE, "a"));
      req2.setTask("a completely different task narrative");
      ResolvedPlan plan2 = service.resolve(req2, principal);

      assertEquals(plan1.planHash(), plan2.planHash(),
                  "planHash must not depend on the free-text task narrative");

      when(dataSpace.exists(null, "b")).thenReturn(false);
      StoredAssetChangePlanRequest req3 = request(
         change(StoredAssetChangeRequest.UNIT_FOLDER, StoredAssetChangeRequest.VERB_CREATE, "b"));
      ResolvedPlan plan3 = service.resolve(req3, principal);

      assertNotEquals(plan1.planHash(), plan3.planHash());
   }
}
