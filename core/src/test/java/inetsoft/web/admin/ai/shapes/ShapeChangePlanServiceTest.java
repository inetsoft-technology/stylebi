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
package inetsoft.web.admin.ai.shapes;

import inetsoft.sree.security.OrganizationManager;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.viewsheet.graph.aesthetic.ImageShapes;
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
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class ShapeChangePlanServiceTest {
   private static final String GLOBAL_DIR = "portal/shapes";
   private static final String ORG_DIR = "portal/myorg/shapes";

   @Mock private DataSpace dataSpace;
   @Mock private SecurityEngine securityEngine;
   @Mock private Principal principal;
   @Mock private OrganizationManager orgManager;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<ImageShapes> imageShapes;
   private MockedStatic<Tool> tool;
   private ShapeChangePlanService service;

   @BeforeEach
   void setup() throws Exception {
      service = new ShapeChangePlanService(dataSpace, securityEngine);

      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      lenient().when(orgManager.getCurrentOrgID()).thenReturn("host-org");

      imageShapes = mockStatic(ImageShapes.class, withSettings().lenient());
      imageShapes.when(ImageShapes::getGlobalShapesDirectory).thenReturn(GLOBAL_DIR);
      imageShapes.when(ImageShapes::getShapesDirectory).thenReturn(ORG_DIR);

      // ResolvedPlan.taskToken() is issued via TaskAuditToken.issue -> Tool.encryptPassword, which
      // needs a live Spring-managed keystore outside this unit test -- stub it, same as every other
      // area's own ChangePlanServiceTest (e.g. StoredAssetChangePlanServiceTest).
      tool = mockStatic(Tool.class, withSettings().strictness(Strictness.LENIENT)
         .defaultAnswer(Answers.CALLS_REAL_METHODS));
      tool.when(() -> Tool.encryptPassword(anyString()))
         .thenAnswer(inv -> "TKN:" + inv.getArgument(0));

      // Default every caller to holding both presentation permissions -- individual permission
      // tests override this to specific combinations.
      lenient().when(securityEngine.checkPermission(
         eq(principal), eq(ResourceType.EM_COMPONENT), eq("settings/presentation/settings"),
         eq(ResourceAction.ACCESS))).thenReturn(true);
      lenient().when(securityEngine.checkPermission(
         eq(principal), eq(ResourceType.EM_COMPONENT), eq("settings/presentation/org-settings"),
         eq(ResourceAction.ACCESS))).thenReturn(true);
   }

   @AfterEach
   void tearDown() {
      orgManagerStatic.close();
      imageShapes.close();
      tool.close();
   }

   private static ShapeChangePlanRequest request(ShapeChangeRequest... changes) {
      ShapeChangePlanRequest req = new ShapeChangePlanRequest();
      req.setTask("test task");
      req.setChanges(List.of(changes));
      return req;
   }

   private static ShapeChangeRequest change(String verb, String scope, String name) {
      ShapeChangeRequest c = new ShapeChangeRequest();
      c.setVerb(verb);
      c.setScope(scope);
      c.setName(name);
      return c;
   }

   private static String base64(String text) {
      return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
   }

   // -------------------------------------------------------------------------
   // request-shape validation
   // -------------------------------------------------------------------------

   @Test void resolveRejectsBlankTask() {
      ShapeChangePlanRequest req = request(
         change(ShapeChangeRequest.VERB_UPLOAD, "global", "a.svg"));
      req.setTask("  ");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(req, principal));
      assertTrue(ex.getMessage().contains("task"), ex.getMessage());
   }

   @Test void resolveRejectsEmptyChangeList() {
      ShapeChangePlanRequest req = request();

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(req, principal));
      assertTrue(ex.getMessage().contains("changes"), ex.getMessage());
   }

   @Test void resolveRejectsAnUnrecognizedVerb() {
      ShapeChangeRequest c = change("rename", "global", "a.svg");
      ShapeChangePlanRequest req = request(c);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(req, principal));
      assertTrue(ex.getMessage().contains("verb"), ex.getMessage());
      verifyNoInteractions(dataSpace);
   }

   @Test void resolveRejectsAnInvalidScope() {
      ShapeChangeRequest c = change(ShapeChangeRequest.VERB_UPLOAD, "worldwide", "a.svg");
      ShapeChangePlanRequest req = request(c);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(req, principal));
      assertTrue(ex.getMessage().contains("scope"), ex.getMessage());
      verifyNoInteractions(dataSpace);
   }

   @Test void resolveRejectsANameContainingAPathSeparatorBeforeTouchingDataSpace() {
      ShapeChangeRequest c = change(ShapeChangeRequest.VERB_DELETE, "global", "../evil.svg");
      ShapeChangePlanRequest req = request(c);

      assertThrows(IllegalArgumentException.class, () -> service.resolve(req, principal));
      verifyNoInteractions(dataSpace);
   }

   @Test void resolveRejectsDuplicatePathEntries() throws Exception {
      when(dataSpace.exists(null, GLOBAL_DIR + "/a.svg")).thenReturn(true);
      when(dataSpace.getInputStream(null, GLOBAL_DIR + "/a.svg"))
         .thenAnswer(inv -> new ByteArrayInputStream("<svg/>".getBytes(StandardCharsets.UTF_8)));
      ShapeChangeRequest c1 = change(ShapeChangeRequest.VERB_DELETE, "global", "a.svg");
      ShapeChangeRequest c2 = change(ShapeChangeRequest.VERB_DELETE, "global", "a.svg");
      ShapeChangePlanRequest req = request(c1, c2);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(req, principal));
      assertTrue(ex.getMessage().contains("duplicate"), ex.getMessage());
   }

   // -------------------------------------------------------------------------
   // upload resolution
   // -------------------------------------------------------------------------

   @Test void resolveUploadRequiresContent() {
      ShapeChangeRequest c = change(ShapeChangeRequest.VERB_UPLOAD, "global", "a.svg");
      ShapeChangePlanRequest req = request(c);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(req, principal));
      assertTrue(ex.getMessage().contains("content"), ex.getMessage());
   }

   @Test void resolveUploadRejectsInvalidBase64() {
      ShapeChangeRequest c = change(ShapeChangeRequest.VERB_UPLOAD, "global", "a.svg");
      c.setContent("not valid base64!!!");
      ShapeChangePlanRequest req = request(c);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(req, principal));
      assertTrue(ex.getMessage().contains("base64"), ex.getMessage());
   }

   @Test void resolveUploadOfANewShapeHasNoPriorBytesAndIsStillCompensable() throws Exception {
      when(dataSpace.exists(null, GLOBAL_DIR + "/a.svg")).thenReturn(false);
      ShapeChangeRequest c = change(ShapeChangeRequest.VERB_UPLOAD, "global", "a.svg");
      c.setContent(base64("<svg/>"));

      List<ShapeChangePlanService.ResolvedChange> resolved =
         service.resolveEntries(request(c), principal);

      assertNull(resolved.get(0).priorContentBase64());
      assertEquals("exists=false", resolved.get(0).planChange().currentValue());
      assertTrue(resolved.get(0).planChange().proposedValue().contains("exists=true"));
      assertTrue(resolved.get(0).planChange().description().startsWith("upload shape"));
   }

   @Test void resolveUploadOverAnExistingShapeCapturesPriorBytesAndDescribesOverwrite()
      throws Exception
   {
      when(dataSpace.exists(null, GLOBAL_DIR + "/a.svg")).thenReturn(true);
      when(dataSpace.getInputStream(null, GLOBAL_DIR + "/a.svg"))
         .thenAnswer(inv -> new ByteArrayInputStream("<old/>".getBytes(StandardCharsets.UTF_8)));
      ShapeChangeRequest c = change(ShapeChangeRequest.VERB_UPLOAD, "global", "a.svg");
      c.setContent(base64("<svg/>"));

      List<ShapeChangePlanService.ResolvedChange> resolved =
         service.resolveEntries(request(c), principal);

      assertEquals(base64("<old/>"), resolved.get(0).priorContentBase64());
      assertTrue(resolved.get(0).planChange().description().startsWith("overwrite shape"),
         resolved.get(0).planChange().description());
   }

   // -------------------------------------------------------------------------
   // delete resolution
   // -------------------------------------------------------------------------

   @Test void resolveDeleteRefusesWhenTheShapeDoesNotExist() {
      when(dataSpace.exists(null, GLOBAL_DIR + "/missing.svg")).thenReturn(false);
      ShapeChangeRequest c = change(ShapeChangeRequest.VERB_DELETE, "global", "missing.svg");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request(c), principal));
      assertTrue(ex.getMessage().contains("does not exist"), ex.getMessage());
   }

   @Test void resolveDeleteOfAnExistingShapeCapturesPriorBytesUnconditionally() throws Exception {
      when(dataSpace.exists(null, GLOBAL_DIR + "/a.svg")).thenReturn(true);
      when(dataSpace.getInputStream(null, GLOBAL_DIR + "/a.svg"))
         .thenAnswer(inv -> new ByteArrayInputStream("<svg/>".getBytes(StandardCharsets.UTF_8)));
      ShapeChangeRequest c = change(ShapeChangeRequest.VERB_DELETE, "global", "a.svg");

      List<ShapeChangePlanService.ResolvedChange> resolved =
         service.resolveEntries(request(c), principal);

      assertEquals(base64("<svg/>"), resolved.get(0).priorContentBase64());
      assertEquals("exists=false", resolved.get(0).planChange().proposedValue());
   }

   // -------------------------------------------------------------------------
   // plan-level shape: always high risk / signoff / backup
   // -------------------------------------------------------------------------

   @Test void resolveAlwaysRequiresSignoffAndBackup() throws Exception {
      when(dataSpace.exists(null, GLOBAL_DIR + "/a.svg")).thenReturn(false);
      ShapeChangeRequest c = change(ShapeChangeRequest.VERB_UPLOAD, "global", "a.svg");
      c.setContent(base64("<svg/>"));

      ResolvedPlan plan = service.resolve(request(c), principal);

      assertTrue(plan.requiresAgentSignoff(), "every custom-shape change is high risk");
      assertTrue(plan.requiresStorageBackup());
      assertNotNull(plan.planHash());
      assertNotNull(plan.taskToken());
   }

   @Test void hashIsStableForTheSameCanonicalInputAndChangesWhenAPathDiffers() throws Exception {
      when(dataSpace.exists(null, GLOBAL_DIR + "/a.svg")).thenReturn(false);
      ShapeChangeRequest c1 = change(ShapeChangeRequest.VERB_UPLOAD, "global", "a.svg");
      c1.setContent(base64("<svg/>"));
      ResolvedPlan plan1 = service.resolve(request(c1), principal);

      when(dataSpace.exists(null, GLOBAL_DIR + "/a.svg")).thenReturn(false);
      ShapeChangeRequest c2 = change(ShapeChangeRequest.VERB_UPLOAD, "global", "a.svg");
      c2.setContent(base64("<svg/>"));
      ShapeChangePlanRequest req2 = request(c2);
      req2.setTask("a completely different task narrative");
      ResolvedPlan plan2 = service.resolve(req2, principal);

      assertEquals(plan1.planHash(), plan2.planHash(),
                  "planHash must not depend on the free-text task narrative");

      when(dataSpace.exists(null, GLOBAL_DIR + "/b.svg")).thenReturn(false);
      ShapeChangeRequest c3 = change(ShapeChangeRequest.VERB_UPLOAD, "global", "b.svg");
      c3.setContent(base64("<svg/>"));
      ResolvedPlan plan3 = service.resolve(request(c3), principal);

      assertNotEquals(plan1.planHash(), plan3.planHash());
   }

   // -------------------------------------------------------------------------
   // requireShapesPermission's OR-branching (charter assertions 6/7, distinct claims per
   // 03-reconcile.md ambiguity 6): global scope requires settings/presentation/settings ALONE;
   // organization scope accepts EITHER that OR settings/presentation/org-settings.
   // -------------------------------------------------------------------------

   private void stubPermission(String resource, boolean granted) throws Exception {
      // lenient: depending on isGlobalRoot's short-circuiting, not every case below actually
      // evaluates both the settings and org-settings checks.
      lenient().when(securityEngine.checkPermission(
         eq(principal), eq(ResourceType.EM_COMPONENT), eq(resource), eq(ResourceAction.ACCESS)))
         .thenReturn(granted);
   }

   @Test void globalScopeIsGrantedBySettingsPermissionAlone() throws Exception {
      stubPermission("settings/presentation/settings", true);
      stubPermission("settings/presentation/org-settings", false);

      assertDoesNotThrow(() -> service.requireShapesPermission(principal, GLOBAL_DIR));
   }

   @Test void globalScopeIsRefusedByOrgSettingsPermissionAlone() throws Exception {
      // Assertion 6: the OR check fails closed when the caller holds neither the resource that
      // actually gates the GLOBAL root nor... but for global specifically, org-settings alone must
      // NOT be sufficient (only settings/presentation/settings gates the global root).
      stubPermission("settings/presentation/settings", false);
      stubPermission("settings/presentation/org-settings", true);

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> service.requireShapesPermission(principal, GLOBAL_DIR));
      assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, ex.getStatusCode());
   }

   @Test void globalScopeIsRefusedWhenNeitherPermissionIsGranted() throws Exception {
      stubPermission("settings/presentation/settings", false);
      stubPermission("settings/presentation/org-settings", false);

      assertThrows(ResponseStatusException.class,
         () -> service.requireShapesPermission(principal, GLOBAL_DIR));
   }

   @Test void organizationScopeIsGrantedBySettingsPermissionAlone() throws Exception {
      stubPermission("settings/presentation/settings", true);
      stubPermission("settings/presentation/org-settings", false);

      assertDoesNotThrow(() -> service.requireShapesPermission(principal, ORG_DIR));
   }

   @Test void organizationScopeIsGrantedByOrgSettingsPermissionAlone() throws Exception {
      // Assertion 7: the OR check's second branch is sufficient on its own for an org-scoped root.
      stubPermission("settings/presentation/settings", false);
      stubPermission("settings/presentation/org-settings", true);

      assertDoesNotThrow(() -> service.requireShapesPermission(principal, ORG_DIR));
   }

   @Test void organizationScopeIsRefusedWhenNeitherPermissionIsGranted() throws Exception {
      stubPermission("settings/presentation/settings", false);
      stubPermission("settings/presentation/org-settings", false);

      assertThrows(ResponseStatusException.class,
         () -> service.requireShapesPermission(principal, ORG_DIR));
   }
}
