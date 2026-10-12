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

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.web.admin.content.repository.model.RequiredAssetModelList;
import inetsoft.web.admin.content.repository.model.SelectedAssetModelList;
import inetsoft.web.admin.deploy.DeployService;
import inetsoft.web.service.BinaryTransferService;
import inetsoft.web.session.IgniteSessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import java.util.concurrent.CompletableFuture;

import static inetsoft.web.admin.content.repository.ExportAssetController.DEPS_ATTR;
import static inetsoft.web.admin.content.repository.ExportAssetController.PERM_ATTR;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #77505: GET .../check-permission/value and .../get-dependent-assets/value without a
 * pending future in the session (no prior POST, or a second GET after the value was consumed)
 * must not fail with a NullPointerException (HTTP 500). Like the sibling .../status endpoints,
 * they return 404.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class ExportAssetControllerMissingFutureTest {
   @Mock private DeployService deployService;
   @Mock private IgniteSessionRepository igniteSessionRepository;
   @Mock private ExportAssetServiceProxy exportAssetServiceProxy;
   @Mock private BinaryTransferService binaryTransferService;
   @Mock private Cluster cluster;

   private ExportAssetController controller;

   @BeforeEach
   void setUp() {
      controller = new ExportAssetController(
         deployService, igniteSessionRepository, exportAssetServiceProxy,
         binaryTransferService, cluster);
   }

   @Test
   void getAssetPermissionValue_noPriorPost_returns404() {
      HttpServletRequest request = newRequest();
      Throwable thrown = catchThrowable(() -> {
         assertEquals(404, controller.getAssetPermissionValue(request).getStatusCode().value());
      });

      assertFalse(thrown instanceof NullPointerException,
                  "check-permission/value without a pending future threw an NPE (HTTP 500): " +
                  thrown);
      assertNull(thrown);
   }

   @Test
   void getDependentAssetsValue_noPriorPost_returns404() {
      HttpServletRequest request = newRequest();
      Throwable thrown = catchThrowable(() -> {
         assertEquals(404, controller.getDependentAssetsValue(request).getStatusCode().value());
      });

      assertFalse(thrown instanceof NullPointerException,
                  "get-dependent-assets/value without a pending future threw an NPE (HTTP 500): " +
                  thrown);
      assertNull(thrown);
   }

   @Test
   void getAssetPermissionValue_calledTwice_secondReturns404() throws Exception {
      HttpServletRequest request = newRequest();
      SelectedAssetModelList value = mock(SelectedAssetModelList.class);
      request.getSession(true).setAttribute(PERM_ATTR, CompletableFuture.completedFuture(value));

      ResponseEntity<SelectedAssetModelList> first = controller.getAssetPermissionValue(request);
      assertEquals(200, first.getStatusCode().value());
      assertSame(value, first.getBody());
      assertNull(request.getSession(true).getAttribute(PERM_ATTR));

      assertEquals(404, controller.getAssetPermissionValue(request).getStatusCode().value());
   }

   @Test
   void getDependentAssetsValue_calledTwice_secondReturns404() throws Exception {
      HttpServletRequest request = newRequest();
      RequiredAssetModelList value = mock(RequiredAssetModelList.class);
      request.getSession(true).setAttribute(DEPS_ATTR, CompletableFuture.completedFuture(value));

      ResponseEntity<RequiredAssetModelList> first = controller.getDependentAssetsValue(request);
      assertEquals(200, first.getStatusCode().value());
      assertSame(value, first.getBody());
      assertNull(request.getSession(true).getAttribute(DEPS_ATTR));

      assertEquals(404, controller.getDependentAssetsValue(request).getStatusCode().value());
   }

   // status of the same missing attribute is a handled 404, for contrast
   @Test
   void getAssetPermissionStatus_noPriorPost_returns404() {
      assertEquals(404, controller.getAssetPermissionStatus(newRequest()).getStatusCode().value());
      assertEquals(404, controller.getDependentAssetsStatus(newRequest()).getStatusCode().value());
   }

   private static HttpServletRequest newRequest() {
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.setSession(new MockHttpSession());
      return request;
   }

   private static Throwable catchThrowable(Executable exec) {
      try {
         exec.run();
         return null;
      }
      catch(Throwable t) {
         return t;
      }
   }

   @FunctionalInterface
   private interface Executable {
      void run() throws Throwable;
   }
}
