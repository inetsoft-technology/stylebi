/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

/*
 * Test strategy
 *
 * ExportAssetController has real in-controller logic in five areas:
 *
 *   getStatus() [private helper] — null future → 404; pending → ready=false; done → ready=true.
 *     Exercised via getAssetPermissionStatus() (PERM_ATTR) and getDependentAssetsStatus() (DEPS_ATTR).
 *
 *   getData() [private helper] — happy path → casts and returns value; future fails with
 *     MessageException → rethrows it; fails with other exception → wrapped ExecutionException.
 *     Exercised via getAssetPermissionValue().
 *
 *   createExport() — generates a UUID job ID, passes it and the model's name() to the proxy,
 *     and returns the UUID to the caller.
 *
 *   getCreateExportStatus() — delegates to proxy.checkExportStatus(), wraps boolean in
 *     ResponseEntity<ExportStatusModel>.
 *
 *   downloadJar() — constructs filename, sets Content-Disposition, checks data null → 404.
 *     SUtil.isIE / isMozilla / isHttpHeadersValid and Tool.isFilePathValid are pure string
 *     utilities called for real. The success path (binaryTransferService.writeData()) is
 *     covered by E2E tests.
 *
 * @PostConstruct (cluster cache init) is not invoked without a Spring context.
 * checkAssetPermission / getDependentAssets use ThreadPool.addOnDemand() for async
 * execution and are covered by E2E tests.
 */

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.util.MessageException;
import inetsoft.util.cachefs.BinaryTransfer;
import inetsoft.web.admin.content.repository.model.*;
import inetsoft.web.admin.deploy.DeployService;
import inetsoft.web.service.BinaryTransferService;
import inetsoft.web.session.IgniteSessionRepository;
import jakarta.servlet.http.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;

import java.security.Principal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static inetsoft.web.admin.content.repository.ExportAssetController.DEPS_ATTR;
import static inetsoft.web.admin.content.repository.ExportAssetController.PERM_ATTR;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class ExportAssetControllerTest {

   @Mock private DeployService deployService;
   @Mock private IgniteSessionRepository igniteSessionRepository;
   @Mock private ExportAssetServiceProxy exportAssetServiceProxy;
   @Mock private BinaryTransferService binaryTransferService;
   @Mock private Cluster cluster;
   @Mock private ExportedAssetsModel exportedAssetsModel;
   @Mock private SelectedAssetModelList permissionResult;
   @Mock private HttpServletRequest request;
   @Mock private HttpServletResponse response;
   @Mock private HttpSession session;
   @Mock private Principal principal;

   private ExportAssetController controller;

   @BeforeEach
   void setUp() {
      controller = new ExportAssetController(
         deployService, igniteSessionRepository, exportAssetServiceProxy,
         binaryTransferService, cluster);

      lenient().when(request.getSession(true)).thenReturn(session);
   }

   // -------------------------------------------------------------------------
   // getStatus() — exercised via getAssetPermissionStatus()
   // -------------------------------------------------------------------------

   // [no future in session] attribute absent → 404 Not Found
   @Test
   void getAssetPermissionStatus_noFuture_returnsNotFound() {
      when(session.getAttribute(PERM_ATTR)).thenReturn(null);

      ResponseEntity<ExportStatusModel> result = controller.getAssetPermissionStatus(request);

      assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
   }

   // [future pending] future not yet done → ready=false
   @Test
   void getAssetPermissionStatus_futurePending_returnsReadyFalse() {
      when(session.getAttribute(PERM_ATTR)).thenReturn(new CompletableFuture<>());

      ResponseEntity<ExportStatusModel> result = controller.getAssetPermissionStatus(request);

      assertEquals(HttpStatus.OK, result.getStatusCode());
      assertFalse(result.getBody().ready());
   }

   // [future done] completed future → ready=true
   @Test
   void getAssetPermissionStatus_futureDone_returnsReadyTrue() {
      when(session.getAttribute(PERM_ATTR))
         .thenReturn(CompletableFuture.completedFuture("done"));

      ResponseEntity<ExportStatusModel> result = controller.getAssetPermissionStatus(request);

      assertEquals(HttpStatus.OK, result.getStatusCode());
      assertTrue(result.getBody().ready());
   }

   // -------------------------------------------------------------------------
   // getStatus() — exercised via getDependentAssetsStatus()
   // -------------------------------------------------------------------------

   // [no future in session] DEPS_ATTR absent → 404
   @Test
   void getDependentAssetsStatus_noFuture_returnsNotFound() {
      when(session.getAttribute(DEPS_ATTR)).thenReturn(null);

      ResponseEntity<ExportStatusModel> result = controller.getDependentAssetsStatus(request);

      assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
   }

   // -------------------------------------------------------------------------
   // getData() — exercised via getAssetPermissionValue()
   // -------------------------------------------------------------------------

   // [success] future completes normally → value returned and attribute removed
   @Test
   void getAssetPermissionValue_futureCompleted_returnsValue() throws Exception {
      when(session.getAttribute(PERM_ATTR))
         .thenReturn(CompletableFuture.completedFuture(permissionResult));

      SelectedAssetModelList result = controller.getAssetPermissionValue(request);

      assertSame(permissionResult, result);
      verify(session).removeAttribute(PERM_ATTR);
   }

   // [MessageException] future fails with MessageException → rethrown directly
   @Test
   void getAssetPermissionValue_futureFailsWithMessageException_rethrows() {
      CompletableFuture<SelectedAssetModelList> future = new CompletableFuture<>();
      future.completeExceptionally(new MessageException("export failed"));
      when(session.getAttribute(PERM_ATTR)).thenReturn(future);

      assertThrows(MessageException.class,
         () -> controller.getAssetPermissionValue(request));
   }

   // [other exception] future fails with non-MessageException → wrapped in ExecutionException
   @Test
   void getAssetPermissionValue_futureFailsWithOtherException_throwsExecutionException() {
      CompletableFuture<SelectedAssetModelList> future = new CompletableFuture<>();
      future.completeExceptionally(new RuntimeException("unexpected"));
      when(session.getAttribute(PERM_ATTR)).thenReturn(future);

      assertThrows(ExecutionException.class,
         () -> controller.getAssetPermissionValue(request));
   }

   // -------------------------------------------------------------------------
   // createExport()
   // -------------------------------------------------------------------------

   // [UUID generation] generates a job ID and passes it + filename to the proxy
   @Test
   void createExport_generatesJobIdAndDelegatesToProxy() {
      when(exportedAssetsModel.name()).thenReturn("myExport");

      String jobId = controller.createExport(request, exportedAssetsModel, principal);

      assertNotNull(jobId);
      assertFalse(jobId.isEmpty());
      verify(exportAssetServiceProxy).createExport(
         eq(jobId), eq("myExport"), eq(exportedAssetsModel), eq(principal));
   }

   // -------------------------------------------------------------------------
   // getCreateExportStatus()
   // -------------------------------------------------------------------------

   // [delegation] proxy.checkExportStatus() → wrapped in ExportStatusModel
   @Test
   void getCreateExportStatus_delegatesToProxy() {
      when(exportAssetServiceProxy.checkExportStatus("job123")).thenReturn(true);

      ResponseEntity<ExportStatusModel> result = controller.getCreateExportStatus("job123");

      assertEquals(HttpStatus.OK, result.getStatusCode());
      assertTrue(result.getBody().ready());
   }

   // -------------------------------------------------------------------------
   // downloadJar()
   // -------------------------------------------------------------------------

   // [null data] getJarFileBytes returns null → 404 status set; writeData never called
   @Test
   void downloadJar_nullData_setsNotFoundStatus() throws Exception {
      when(exportAssetServiceProxy.getFileNameFromID("job123")).thenReturn("myFile");
      when(request.getHeader("USER-AGENT")).thenReturn("NoAgent");
      when(exportAssetServiceProxy.getJarFileBytes("job123")).thenReturn(null);

      controller.downloadJar("job123", request, response);

      verify(response).setStatus(HttpStatus.NOT_FOUND.value());
      verify(binaryTransferService, never()).writeData(any(BinaryTransfer.class), any());
   }
}
