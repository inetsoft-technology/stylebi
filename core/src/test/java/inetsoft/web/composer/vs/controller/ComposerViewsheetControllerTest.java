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
package inetsoft.web.composer.vs.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.web.composer.ws.event.SaveSheetEvent;
import inetsoft.web.viewsheet.event.OpenPreviewViewsheetEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("core")
class ComposerViewsheetControllerTest {
   @BeforeEach
   void setup() {
      controller = new ComposerViewsheetController(
         runtimeViewsheetRef, viewsheetService, composerViewsheetService, securityEngine);
   }

   // Bug #76615, saving without an attached runtime viewsheet must not NPE.
   @Test
   void saveViewsheetReturnsFalseWhenRuntimeIdIsNull() throws Exception {
      when(runtimeViewsheetRef.getRuntimeId()).thenReturn(null);

      boolean result = controller.saveViewsheet(new SaveSheetEvent(), principal, dispatcher, "/");

      assertFalse(result);
      verifyNoInteractions(composerViewsheetService, securityEngine);
   }

   // Bug #76835, a preview-refresh request against an already-closed runtime must not NPE
   // on the proxy's null return.
   @Test
   void refreshPreviewViewsheetSkipsLastModifiedWhenProxyReturnsNull() throws Exception {
      when(securityEngine.checkPermission(eq(principal), eq(ResourceType.VIEWSHEET),
         anyString(), eq(ResourceAction.ACCESS))).thenReturn(true);
      when(runtimeViewsheetRef.getRuntimeId()).thenReturn("closed-runtime-id");
      when(composerViewsheetService.refreshPreviewViewsheet(
         eq("closed-runtime-id"), any(), eq(principal), eq(dispatcher), any(), eq(false)))
         .thenReturn(null);

      assertDoesNotThrow(() -> controller.refreshPreviewViewsheet(
         new OpenPreviewViewsheetEvent(), principal, dispatcher, "/"));

      verify(runtimeViewsheetRef, never()).setLastModified(anyLong());
   }

   @Test
   void refreshPreviewViewsheetSkipsLastModifiedWhenProxyReturnsFalse() throws Exception {
      when(securityEngine.checkPermission(eq(principal), eq(ResourceType.VIEWSHEET),
         anyString(), eq(ResourceAction.ACCESS))).thenReturn(true);
      when(runtimeViewsheetRef.getRuntimeId()).thenReturn("runtime-id");
      when(composerViewsheetService.refreshPreviewViewsheet(
         eq("runtime-id"), any(), eq(principal), eq(dispatcher), any(), eq(false)))
         .thenReturn(false);

      controller.refreshPreviewViewsheet(new OpenPreviewViewsheetEvent(), principal, dispatcher, "/");

      verify(runtimeViewsheetRef, never()).setLastModified(anyLong());
   }

   @Test
   void refreshPreviewViewsheetSetsLastModifiedWhenProxyReturnsTrue() throws Exception {
      when(securityEngine.checkPermission(eq(principal), eq(ResourceType.VIEWSHEET),
         anyString(), eq(ResourceAction.ACCESS))).thenReturn(true);
      when(runtimeViewsheetRef.getRuntimeId()).thenReturn("runtime-id");
      when(composerViewsheetService.refreshPreviewViewsheet(
         eq("runtime-id"), any(), eq(principal), eq(dispatcher), any(), eq(false)))
         .thenReturn(true);

      controller.refreshPreviewViewsheet(new OpenPreviewViewsheetEvent(), principal, dispatcher, "/");

      verify(runtimeViewsheetRef).setLastModified(anyLong());
   }

   @Mock private RuntimeViewsheetRef runtimeViewsheetRef;
   @Mock private ViewsheetService viewsheetService;
   @Mock private ComposerViewsheetServiceProxy composerViewsheetService;
   @Mock private SecurityEngine securityEngine;
   @Mock private Principal principal;
   @Mock private CommandDispatcher dispatcher;
   private ComposerViewsheetController controller;
}
