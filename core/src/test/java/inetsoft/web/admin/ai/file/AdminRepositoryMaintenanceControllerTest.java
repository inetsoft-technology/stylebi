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

import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.sree.security.SecurityProvider;
import inetsoft.util.IndexedStorage;
import inetsoft.util.Tool;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.content.repository.ContentRepositoryTreeService;
import inetsoft.web.admin.file.FileService;
import inetsoft.web.admin.file.RebuildDependenciesStatus;
import inetsoft.web.admin.file.RepairRepositoryFoldersStatus;
import inetsoft.web.security.auth.MissingResourceException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.security.Principal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Also covers the single-flight guard {@code repair_repository_folders} adds on top of
 * {@code FileService} and the clean-error-message extraction both status-poll endpoints apply. */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class AdminRepositoryMaintenanceControllerTest {
   @Mock private FileService fileService;
   @Mock private Principal principal;
   @Mock private OrganizationManager orgManager;
   private AdminRepositoryMaintenanceController controller;
   private MockedStatic<OrganizationManager> orgManagerStatic;

   @BeforeEach void setup() {
      controller = new AdminRepositoryMaintenanceController(fileService);

      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      lenient().when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      lenient().when(principal.getName()).thenReturn("admin");

      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("Authorization", "Bearer test-jwt");
      RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
   }

   @AfterEach void tearDown() {
      orgManagerStatic.close();
      RequestContextHolder.resetRequestAttributes();
   }

   private MockedStatic<Audit> mockAudit() {
      MockedStatic<Audit> audit = mockStatic(Audit.class);
      Audit instance = mock(Audit.class);
      audit.when(Audit::getInstance).thenReturn(instance);
      return audit;
   }

   // -- requireSiteAdmin gate, all four endpoints --------------------------------------------

   @Test void rebuildDependenciesThrowsForbiddenWithoutBearerToken() {
      RequestContextHolder.setRequestAttributes(
         new ServletRequestAttributes(new MockHttpServletRequest()));

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> controller.rebuildDependencies(null, principal));
      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      verifyNoInteractions(fileService);
   }

   @Test void rebuildDependenciesThrowsForbiddenForNonSiteAdmin() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> controller.rebuildDependencies(null, principal));
      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      verifyNoInteractions(fileService);
   }

   @Test void getRebuildDependenciesStatusThrowsForbiddenForNonSiteAdmin() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> controller.getRebuildDependenciesStatus("tok", principal));
      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      verifyNoInteractions(fileService);
   }

   @Test void repairRepositoryFoldersThrowsForbiddenForNonSiteAdmin() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> controller.repairRepositoryFolders(principal));
      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      verifyNoInteractions(fileService);
   }

   @Test void getRepairRepositoryFoldersStatusThrowsForbiddenForNonSiteAdmin() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> controller.getRepairRepositoryFoldersStatus("tok", principal));
      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      verifyNoInteractions(fileService);
   }

   // -- rebuild_dependencies: kick-off + status reflects completion/failure -------------------

   @Test void rebuildDependenciesReturnsTokenAndDefaultsTimeout() {
      when(fileService.rebuildDependencies(eq(300000L), eq(principal))).thenReturn("tok-1");

      try(MockedStatic<Audit> audit = mockAudit()) {
         RepositoryMaintenanceToken result = controller.rebuildDependencies(null, principal);
         assertEquals("tok-1", result.token());
      }
   }

   @Test void rebuildDependenciesUsesSuppliedTimeout() {
      RebuildDependenciesKickoffRequest req = new RebuildDependenciesKickoffRequest();
      req.setTimeoutMs(5000L);
      when(fileService.rebuildDependencies(eq(5000L), eq(principal))).thenReturn("tok-2");

      try(MockedStatic<Audit> audit = mockAudit()) {
         RepositoryMaintenanceToken result = controller.rebuildDependencies(req, principal);
         assertEquals("tok-2", result.token());
      }
   }

   @Test void getRebuildDependenciesStatusReflectsCompletion() throws Exception {
      RebuildDependenciesStatus raw = new RebuildDependenciesStatus();
      raw.setToken("tok-1");
      raw.setComplete(true);
      raw.setFailed(false);
      when(fileService.getRebuildDependenciesStatus("tok-1")).thenReturn(raw);

      try(MockedStatic<Audit> audit = mockAudit()) {
         RepositoryMaintenanceStatus result =
            controller.getRebuildDependenciesStatus("tok-1", principal);

         assertEquals("tok-1", result.token());
         assertTrue(result.complete());
         assertFalse(result.failed());
         assertNull(result.error());
      }
   }

   @Test void getRebuildDependenciesStatusClassifiesTimeoutFailure() throws Exception {
      RebuildDependenciesStatus raw = new RebuildDependenciesStatus();
      raw.setToken("tok-1");
      raw.setComplete(true);
      raw.setFailed(true);
      raw.setError("java.util.concurrent.ExecutionException: " +
         "java.util.concurrent.TimeoutException: Could not update dependencies\n" +
         "\tat java.base/java.util.concurrent.CompletableFuture.reportGet(CompletableFuture.java:395)\n");
      when(fileService.getRebuildDependenciesStatus("tok-1")).thenReturn(raw);

      try(MockedStatic<Audit> audit = mockAudit()) {
         RepositoryMaintenanceStatus result =
            controller.getRebuildDependenciesStatus("tok-1", principal);

         assertTrue(result.failed());
         assertFalse(result.error().contains("Exception"));
         assertFalse(result.error().contains("\tat "));
         assertTrue(result.error().toLowerCase().contains("already in progress"));
      }
   }

   @Test void getRebuildDependenciesStatusCleansGenericFailure() throws Exception {
      RebuildDependenciesStatus raw = new RebuildDependenciesStatus();
      raw.setToken("tok-1");
      raw.setComplete(true);
      raw.setFailed(true);
      raw.setError("java.lang.IllegalStateException: dependency graph store is unavailable\n" +
         "\tat inetsoft.uql.asset.UpdateAssetDependenciesHandler.rebuild(UpdateAssetDependenciesHandler.java:451)\n");
      when(fileService.getRebuildDependenciesStatus("tok-1")).thenReturn(raw);

      try(MockedStatic<Audit> audit = mockAudit()) {
         RepositoryMaintenanceStatus result =
            controller.getRebuildDependenciesStatus("tok-1", principal);

         assertTrue(result.failed());
         assertEquals("dependency graph store is unavailable", result.error());
         assertFalse(result.error().contains("\tat "));
      }
   }

   @Test void getRebuildDependenciesStatusUnrecognizedTokenPropagatesMissingResource() throws Exception {
      when(fileService.getRebuildDependenciesStatus("bad-token"))
         .thenThrow(new MissingResourceException("bad-token"));

      assertThrows(MissingResourceException.class,
         () -> controller.getRebuildDependenciesStatus("bad-token", principal));
   }

   @Test void handleMissingResourceReturnsNotFound() {
      var body = controller.handleMissingResource(new MissingResourceException("bad-token"));
      assertEquals("not-found", body.get("status"));
   }

   // -- writeAudit shape ------------------------------------------------------------------------

   @Test void rebuildDependenciesWritesStartAudit() {
      when(fileService.rebuildDependencies(anyLong(), eq(principal))).thenReturn("tok-1");

      // Tool.getHost() reads SreeEnv, which throws ShutdownException with no Spring context
      // available -- stubbed here too so the write actually reaches
      // Audit.getInstance().auditAdminChange(...) and this test can assert on the record's real
      // field values.
      try(MockedStatic<Audit> audit = mockAudit();
          MockedStatic<Tool> tool = mockStatic(Tool.class, CALLS_REAL_METHODS))
      {
         tool.when(Tool::getHost).thenReturn("test-host");
         Audit instance = Audit.getInstance();
         controller.rebuildDependencies(null, principal);

         ArgumentCaptor<AdminChangeRecord> captor = ArgumentCaptor.forClass(AdminChangeRecord.class);
         verify(instance).auditAdminChange(captor.capture(), eq(principal));
         AdminChangeRecord record = captor.getValue();
         assertEquals("tok-1", record.getTransactionId());
         assertEquals("dependency-graph", record.getProperty());
         assertEquals("start", record.getAction());
         assertNull(record.getOrganizationId());
      }
   }

   @Test void getRebuildDependenciesStatusWritesCompleteAuditOnlyWhenDone() throws Exception {
      RebuildDependenciesStatus notDone = new RebuildDependenciesStatus();
      notDone.setToken("tok-1");
      notDone.setComplete(false);
      when(fileService.getRebuildDependenciesStatus("tok-1")).thenReturn(notDone);

      try(MockedStatic<Audit> audit = mockAudit()) {
         Audit instance = Audit.getInstance();
         controller.getRebuildDependenciesStatus("tok-1", principal);
         verifyNoInteractions(instance);
      }

      RebuildDependenciesStatus done = new RebuildDependenciesStatus();
      done.setToken("tok-1");
      done.setComplete(true);
      done.setFailed(false);
      when(fileService.getRebuildDependenciesStatus("tok-1")).thenReturn(done);

      try(MockedStatic<Audit> audit = mockAudit();
          MockedStatic<Tool> tool = mockStatic(Tool.class, CALLS_REAL_METHODS))
      {
         tool.when(Tool::getHost).thenReturn("test-host");
         Audit instance = Audit.getInstance();
         controller.getRebuildDependenciesStatus("tok-1", principal);

         ArgumentCaptor<AdminChangeRecord> captor = ArgumentCaptor.forClass(AdminChangeRecord.class);
         verify(instance).auditAdminChange(captor.capture(), eq(principal));
         assertEquals("complete", captor.getValue().getAction());
         assertEquals(AdminChangeRecord.STATUS_VERIFIED, captor.getValue().getStatus());
      }
   }

   // -- repair_repository_folders: single-flight guard -----------------------------------------

   @Test void repairRepositoryFoldersReturnsToken() {
      when(fileService.repairRepositoryFolders(principal)).thenReturn("tok-r1");

      try(MockedStatic<Audit> audit = mockAudit()) {
         RepositoryMaintenanceToken result = controller.repairRepositoryFolders(principal);
         assertEquals("tok-r1", result.token());
      }
   }

   @Test void repairRepositoryFoldersRefusesSecondKickoffWhileFirstOutstanding() throws Exception {
      when(fileService.repairRepositoryFolders(principal)).thenReturn("tok-r1");
      when(fileService.isRepairRepositoryFoldersComplete("tok-r1")).thenReturn(false);

      try(MockedStatic<Audit> audit = mockAudit()) {
         controller.repairRepositoryFolders(principal);

         RepositoryRepairInProgressException ex = assertThrows(
            RepositoryRepairInProgressException.class,
            () -> controller.repairRepositoryFolders(principal));
         assertTrue(ex.getMessage().contains("tok-r1"));
      }

      // Only the first kickoff should have reached the wrapped service.
      verify(fileService, times(1)).repairRepositoryFolders(principal);
   }

   @Test void repairRepositoryFoldersSelfHealsWhenOutstandingTokenAlreadyCompleteButUnpolled() throws Exception {
      when(fileService.repairRepositoryFolders(principal)).thenReturn("tok-r1", "tok-r2");
      when(fileService.isRepairRepositoryFoldersComplete("tok-r1")).thenReturn(true);

      try(MockedStatic<Audit> audit = mockAudit()) {
         controller.repairRepositoryFolders(principal);

         // No intervening call to getRepairRepositoryFoldersStatus -- the guard must self-heal by
         // checking the outstanding token's live status itself, not rely on a poll ever happening.
         RepositoryMaintenanceToken second = controller.repairRepositoryFolders(principal);
         assertEquals("tok-r2", second.token());
      }

      verify(fileService, times(2)).repairRepositoryFolders(principal);
      // The self-heal check must use the non-consuming peek, never the real status-poll method --
      // that method removes the task's entry from FileService's own tracking map on first
      // observation of completion, which would rob the token's actual owner of their own result.
      verify(fileService, never()).getRepairRepositoryFoldersStatus(anyString());
   }

   @Test void repairRepositoryFoldersAllowsKickoffOnceFirstCompletes() throws Exception {
      when(fileService.repairRepositoryFolders(principal)).thenReturn("tok-r1", "tok-r2");
      // The second kickoff's own self-heal peek: still running, so that attempt is refused as
      // before. This must go through the non-consuming peek, not the real status-poll method.
      when(fileService.isRepairRepositoryFoldersComplete("tok-r1")).thenReturn(false);
      RepairRepositoryFoldersStatus done = new RepairRepositoryFoldersStatus();
      done.setToken("tok-r1");
      done.setComplete(true);
      done.setFailed(false);
      // The explicit status poll below (complete) is the "poll releases the guard" path this
      // test is actually about.
      when(fileService.getRepairRepositoryFoldersStatus("tok-r1")).thenReturn(done);

      try(MockedStatic<Audit> audit = mockAudit()) {
         controller.repairRepositoryFolders(principal);

         assertThrows(RepositoryRepairInProgressException.class,
            () -> controller.repairRepositoryFolders(principal));

         // Observing completion via the status poll releases the guard.
         controller.getRepairRepositoryFoldersStatus("tok-r1", principal);

         RepositoryMaintenanceToken second = controller.repairRepositoryFolders(principal);
         assertEquals("tok-r2", second.token());
      }
   }

   @Test void getRepairRepositoryFoldersStatusUnrecognizedTokenPropagatesMissingResource() throws Exception {
      when(fileService.getRepairRepositoryFoldersStatus("bad-token"))
         .thenThrow(new MissingResourceException("bad-token"));

      assertThrows(MissingResourceException.class,
         () -> controller.getRepairRepositoryFoldersStatus("bad-token", principal));
   }

   @Test void getRepairRepositoryFoldersStatusCleansFailureMessage() throws Exception {
      RepairRepositoryFoldersStatus raw = new RepairRepositoryFoldersStatus();
      raw.setToken("tok-r1");
      raw.setComplete(true);
      raw.setFailed(true);
      raw.setError("java.lang.NullPointerException: folder entry missing\n" +
         "\tat inetsoft.web.admin.file.FileService.repairRepositoryFolders(FileService.java:590)\n");
      when(fileService.getRepairRepositoryFoldersStatus("tok-r1")).thenReturn(raw);

      try(MockedStatic<Audit> audit = mockAudit()) {
         RepositoryMaintenanceStatus result =
            controller.getRepairRepositoryFoldersStatus("tok-r1", principal);

         assertTrue(result.failed());
         assertEquals("folder entry missing", result.error());
         assertFalse(result.error().contains("\tat "));
      }
   }

   /*
    * Round-2 reviewer finding on enterprise PR #742 (see the enterprise-side history of this
    * test, before the repository-maintenance logic moved into this community FileService):
    * isStaleCompletedRepair() peeked at an outstanding token's status via
    * FileService.getRepairRepositoryFoldersStatus(...), which removes the task's entry from
    * FileService's own repairTasks map on the FIRST call that observes completion -- regardless
    * of caller. An unrelated session's self-heal check could therefore silently consume a
    * *different* session's own completion record before that session ever got to poll it for
    * real, turning its later genuine poll into a MissingResourceException.
    *
    * Exercises a real FileService (only its constructor dependencies are mocked, since the
    * constructor merely stores them) wired into its own controller instance, not the class-level
    * fully-mocked fileService every other test in this file uses -- a full mock cannot reproduce
    * this bug at all, since it lives entirely in the real repairTasks map's shared, stateful
    * remove-on-first-observation behavior. Session A's kickoff call and session B's kickoff call
    * are stubbed only enough to avoid the real background repair thread (irrelevant here);
    * everything downstream of "is this outstanding token done" runs the real code.
    */
   @Test void repairRepositoryFoldersSelfHealDoesNotStealAnotherSessionsCompletionRecord()
      throws Exception
   {
      FileService realFileService = spy(new FileService(
         mock(ContentRepositoryTreeService.class), mock(SecurityProvider.class),
         mock(IndexedStorage.class), mock(RepletRegistryManager.class)));
      AdminRepositoryMaintenanceController realController =
         new AdminRepositoryMaintenanceController(realFileService);

      // Session A's kickoff: already-complete by the time session B races in, matching the
      // reproduction scenario ("hasn't polled yet", not "still running").
      doAnswer(invocation -> {
         putRepairTask(realFileService, "tok-a", CompletableFuture.completedFuture(null));
         return "tok-a";
      }).when(realFileService).repairRepositoryFolders(principal);

      try(MockedStatic<Audit> audit = mockAudit()) {
         RepositoryMaintenanceToken first = realController.repairRepositoryFolders(principal);
         assertEquals("tok-a", first.token());
      }

      // Session B's unrelated kickoff attempt: loses the CAS against tok-a, self-heals via the
      // real repairTasks map, and must be allowed to proceed -- without disturbing tok-a's entry.
      doReturn("tok-b").when(realFileService).repairRepositoryFolders(principal);

      try(MockedStatic<Audit> audit = mockAudit()) {
         RepositoryMaintenanceToken second = realController.repairRepositoryFolders(principal);
         assertEquals("tok-b", second.token());
      }

      // Session A must still be able to poll its own token for the genuine result. Before the
      // fix, session B's self-heal peek already removed tok-a from the real map, so this would
      // throw MissingResourceException instead.
      RepositoryMaintenanceStatus status;
      try(MockedStatic<Audit> audit = mockAudit()) {
         status = realController.getRepairRepositoryFoldersStatus("tok-a", principal);
      }
      assertTrue(status.complete());
      assertFalse(status.failed());
   }

   @SuppressWarnings("unchecked")
   private static void putRepairTask(FileService service, String token,
                                      CompletableFuture<?> future) throws Exception
   {
      Field field = FileService.class.getDeclaredField("repairTasks");
      field.setAccessible(true);
      ((ConcurrentMap<String, CompletableFuture<?>>) field.get(service)).put(token, future);
   }

   @Test void handleRepairInProgressReturnsConflict() {
      var body = controller.handleRepairInProgress(
         new RepositoryRepairInProgressException("tok-r1"));
      assertEquals("conflict", body.get("status"));
      assertTrue(body.get("error").contains("tok-r1"));
   }
}
