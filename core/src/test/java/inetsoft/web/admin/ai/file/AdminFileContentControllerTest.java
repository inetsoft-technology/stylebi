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
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.PlanChange;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.content.dataspace.DataSpaceContentSettingsService;
import inetsoft.web.admin.content.dataspace.DataSpaceFolderSettingsController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Every endpoint's own {@code requireSiteAdmin} gate (01-design.md section 7's open item on
 * {@code DataSpaceFileSettingsController}'s own lack of a confirmed method-level {@code @Secured}
 * -- this controller's own gate is the real, load-bearing one, mirroring {@code
 * AdminFileBackupControllerTest}), plus delegation to {@link AdminFileContentService}/{@link
 * StoredAssetChangePlanService}/{@link StoredAssetChangesetApplyService} and the download endpoints'
 * own existence pre-checks before delegating to {@link DataSpaceContentSettingsService}/{@link
 * DataSpaceFolderSettingsController}.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class AdminFileContentControllerTest {
   @Mock private AdminFileContentService contentService;
   @Mock private StoredAssetChangePlanService planService;
   @Mock private StoredAssetChangesetApplyService applyService;
   @Mock private DataSpaceContentSettingsService dataSpaceContentSettingsService;
   @Mock private DataSpaceFolderSettingsController folderSettingsController;
   @Mock private OrganizationManager orgManager;
   @Mock private Principal principal;
   @Mock private HttpServletRequest servletRequest;
   @Mock private HttpServletResponse servletResponse;

   private AdminFileContentController controller;
   private MockedStatic<OrganizationManager> orgManagerStatic;

   @BeforeEach
   void setup() {
      controller = new AdminFileContentController(contentService, planService, applyService,
         dataSpaceContentSettingsService, folderSettingsController);

      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

      // default to a site-admin caller so the delegation tests exercise delegation; individual
      // tests override this to false to cover the FORBIDDEN gate
      lenient().when(orgManager.isSiteAdmin(principal)).thenReturn(true);

      // Every endpoint also requires a bearer-authenticated request (AdminAiCallerGuard); bind a
      // request carrying one so these tests exercise the site-admin gate rather than that guard.
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("Authorization", "Bearer test-jwt");
      RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
   }

   @AfterEach
   void tearDown() {
      orgManagerStatic.close();
      RequestContextHolder.resetRequestAttributes();
   }

   private void asNonBearerRequest() {
      RequestContextHolder.setRequestAttributes(
         new ServletRequestAttributes(new MockHttpServletRequest()));
   }

   // -------------------------------------------------------------------------
   // requireSiteAdmin gate -- exercised once per HTTP verb shape (GET/POST), not per endpoint,
   // since every endpoint calls the exact same private helper.
   // -------------------------------------------------------------------------

   @Test void listThrowsForbiddenWithoutBearerToken() {
      asNonBearerRequest();

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> controller.list("", principal));

      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      verifyNoInteractions(contentService);
   }

   @Test void listThrowsForbiddenForNonSiteAdmin() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> controller.list("", principal));

      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      verifyNoInteractions(contentService);
   }

   @Test void previewThrowsForbiddenForNonSiteAdmin() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);
      StoredAssetChangePlanRequest req = new StoredAssetChangePlanRequest();

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> controller.preview(req, principal));

      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      verifyNoInteractions(planService);
   }

   @Test void applyThrowsForbiddenForNonSiteAdmin() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);
      StoredAssetApplyRequest req = new StoredAssetApplyRequest();

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> controller.apply(req, principal));

      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      verifyNoInteractions(applyService);
   }

   // -------------------------------------------------------------------------
   // read delegation
   // -------------------------------------------------------------------------

   @Test void listDelegatesToContentService() {
      StoredAssetListResult expected = new StoredAssetListResult("scripts",
         List.of(new StoredAssetEntry("a.js", "scripts/a.js", false)));
      when(contentService.list("scripts")).thenReturn(expected);

      assertSame(expected, controller.list("scripts", principal));
      verify(contentService).list("scripts");
   }

   @Test void getNodeDelegatesToContentService() {
      StoredAssetNode expected = new StoredAssetNode("scripts/a.js", "a.js", false, 10L, "now", true);
      when(contentService.getNode("scripts/a.js")).thenReturn(expected);

      assertSame(expected, controller.getNode("scripts/a.js", principal));
      verify(contentService).getNode("scripts/a.js");
   }

   @Test void getContentDelegatesToContentServiceWithThePreviewFlag() {
      StoredAssetContent expected = new StoredAssetContent("scripts/a.js", "console.log(1);", true);
      when(contentService.getContent("scripts/a.js", true)).thenReturn(expected);

      assertSame(expected, controller.getContent("scripts/a.js", true, principal));
      verify(contentService).getContent("scripts/a.js", true);
   }

   // -------------------------------------------------------------------------
   // download / download-folder -- own existence pre-check before delegating
   // -------------------------------------------------------------------------

   @Test void downloadRejectsTheEmptyPathBeforeTouchingAnyService() {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> controller.download("", principal, servletRequest, servletResponse));

      assertTrue(ex.getMessage().contains("path"), ex.getMessage());
      verifyNoInteractions(contentService, dataSpaceContentSettingsService);
   }

   @Test void downloadDelegatesToDataSpaceContentSettingsServiceAfterExistenceCheck() throws Exception {
      controller.download("scripts/a.js", principal, servletRequest, servletResponse);

      verify(contentService).requireExistingFile("scripts/a.js");
      verify(dataSpaceContentSettingsService)
         .downloadFile("scripts/a.js", "a.js", servletResponse, servletRequest);
   }

   @Test void downloadPropagatesNotFoundFromTheExistenceCheckWithoutCallingDataSpaceService()
      throws Exception
   {
      doThrow(new StoredAssetNotFoundException("scripts/missing.js"))
         .when(contentService).requireExistingFile("scripts/missing.js");

      assertThrows(StoredAssetNotFoundException.class,
         () -> controller.download("scripts/missing.js", principal, servletRequest, servletResponse));

      verifyNoInteractions(dataSpaceContentSettingsService);
   }

   @Test void downloadFolderDelegatesToFolderSettingsControllerAfterExistenceCheck() throws Exception {
      controller.downloadFolder("scripts", principal, servletResponse);

      verify(contentService).requireExistingFolder("scripts");
      verify(folderSettingsController).downloadDataSpaceFolder("scripts", "scripts", servletResponse);
   }

   @Test void downloadFolderOfTheRootUsesTheStorageNameAndRootPath() throws Exception {
      controller.downloadFolder("", principal, servletResponse);

      verify(contentService).requireExistingFolder("");
      verify(folderSettingsController).downloadDataSpaceFolder("/", "Storage", servletResponse);
   }

   // -------------------------------------------------------------------------
   // preview / apply delegation
   // -------------------------------------------------------------------------

   @Test void previewDelegatesToPlanService() {
      StoredAssetChangePlanRequest req = new StoredAssetChangePlanRequest();
      ResolvedPlan expected = new ResolvedPlan("task", List.of(), true, false, "hash", "token");
      when(planService.resolve(req, principal)).thenReturn(expected);

      assertSame(expected, controller.preview(req, principal));
      verify(planService).resolve(req, principal);
   }

   @Test void applyDelegatesToApplyService() throws Exception {
      StoredAssetApplyRequest req = new StoredAssetApplyRequest();
      StoredAssetApplyResult expected = new StoredAssetApplyResult(
         "storedasset-1", AdminChangesetApplyService.STATUS_APPLIED, "backup/ref", List.of(), null);
      when(applyService.apply(req, principal)).thenReturn(expected);

      assertSame(expected, controller.apply(req, principal));
      verify(applyService).apply(req, principal);
   }

   // -------------------------------------------------------------------------
   // exception handlers
   // -------------------------------------------------------------------------

   @Test void handleIllegalArgumentReturnsFailedStatusWithMessage() {
      Map<String, String> actual =
         controller.handleIllegalArgument(new IllegalArgumentException("path: invalid path"));

      assertEquals("failed", actual.get("status"));
      assertEquals("path: invalid path", actual.get("error"));
   }

   @Test void handleIllegalArgumentIsAnnotatedBadRequest() throws NoSuchMethodException {
      ResponseStatus annotation = AdminFileContentController.class
         .getMethod("handleIllegalArgument", IllegalArgumentException.class)
         .getAnnotation(ResponseStatus.class);

      assertNotNull(annotation, "handleIllegalArgument must be annotated @ResponseStatus");
      assertEquals(HttpStatus.BAD_REQUEST, annotation.value());
   }

   @Test void handleNotFoundReturnsPathAndIsAnnotatedNotFound() throws NoSuchMethodException {
      Map<String, String> actual =
         controller.handleNotFound(new StoredAssetNotFoundException("scripts/missing.js"));

      assertEquals("not-found", actual.get("status"));
      assertEquals("scripts/missing.js", actual.get("path"));

      ResponseStatus annotation = AdminFileContentController.class
         .getMethod("handleNotFound", StoredAssetNotFoundException.class)
         .getAnnotation(ResponseStatus.class);
      assertEquals(HttpStatus.NOT_FOUND, annotation.value());
   }

   @Test void handlePlanHashMismatchReturnsConflictWithTheCurrentPlan() throws NoSuchMethodException {
      // PlanHashMismatchException strips taskToken from the plan it carries (a caller must
      // re-preview, not resubmit a stale token) -- build `current` with it already null so the
      // equality check reflects that, matching every prior area's own handler test.
      ResolvedPlan current = new ResolvedPlan("task",
         List.of(new PlanChange("a", null, "exists=false", "exists=true", "low", "storage", true,
                                "create a")),
         true, false, "current-hash", null);
      AdminChangesetApplyService.PlanHashMismatchException ex =
         new AdminChangesetApplyService.PlanHashMismatchException(current);

      Map<String, Object> actual = controller.handlePlanHashMismatch(ex);

      assertEquals("conflict", actual.get("status"));
      assertEquals(current, actual.get("plan"));

      ResponseStatus annotation = AdminFileContentController.class
         .getMethod("handlePlanHashMismatch", AdminChangesetApplyService.PlanHashMismatchException.class)
         .getAnnotation(ResponseStatus.class);
      assertEquals(HttpStatus.CONFLICT, annotation.value());
   }

   @Test void handleTaskTokenMismatchReturnsConflictWithTheCurrentPlan() throws NoSuchMethodException {
      ResolvedPlan current = new ResolvedPlan("task", List.of(), true, false, "current-hash", null);
      AdminChangesetApplyService.TaskTokenMismatchException ex =
         new AdminChangesetApplyService.TaskTokenMismatchException(current, "taskToken: required");

      Map<String, Object> actual = controller.handleTaskTokenMismatch(ex);

      assertEquals("conflict", actual.get("status"));
      assertEquals("taskToken: required", actual.get("error"));
      assertEquals(current, actual.get("plan"));

      ResponseStatus annotation = AdminFileContentController.class
         .getMethod("handleTaskTokenMismatch", AdminChangesetApplyService.TaskTokenMismatchException.class)
         .getAnnotation(ResponseStatus.class);
      assertEquals(HttpStatus.CONFLICT, annotation.value());
   }
}
