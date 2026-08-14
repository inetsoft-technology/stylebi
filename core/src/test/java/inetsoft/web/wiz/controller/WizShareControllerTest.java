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
package inetsoft.web.wiz.controller;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class WizShareControllerTest {
   private MockedStatic<SreeEnv> sreeEnv;

   @BeforeEach
   void setUp() {
      sreeEnv = mockStatic(SreeEnv.class, withSettings().lenient());
   }

   @AfterEach
   void tearDown() {
      sreeEnv.close();
   }

   private void configureLinkEnabledProperty(String value) {
      sreeEnv.when(() -> SreeEnv.getProperty("share.link.enabled")).thenReturn(value);
   }

   @Test
   void enabledWhenPropertyTrueAndPermissionGranted() throws Exception {
      configureLinkEnabledProperty("true");
      SecurityEngine sec = mock(SecurityEngine.class);
      Principal principal = mock(Principal.class);
      when(sec.checkPermission(eq(principal), eq(ResourceType.SHARE), eq("link"),
         eq(ResourceAction.ACCESS))).thenReturn(true);

      WizShareController ctrl = new WizShareController(sec);

      assertTrue(ctrl.isShareLinkEnabled(principal));
   }

   @Test
   void disabledWhenPropertyFalse() throws Exception {
      configureLinkEnabledProperty("false");
      SecurityEngine sec = mock(SecurityEngine.class);
      Principal principal = mock(Principal.class);
      when(sec.checkPermission(any(), any(), anyString(), any())).thenReturn(true);

      WizShareController ctrl = new WizShareController(sec);

      assertFalse(ctrl.isShareLinkEnabled(principal));
   }

   @Test
   void disabledWhenPermissionDenied() throws Exception {
      configureLinkEnabledProperty("true");
      SecurityEngine sec = mock(SecurityEngine.class);
      Principal principal = mock(Principal.class);
      when(sec.checkPermission(eq(principal), eq(ResourceType.SHARE), eq("link"),
         eq(ResourceAction.ACCESS))).thenReturn(false);

      WizShareController ctrl = new WizShareController(sec);

      assertFalse(ctrl.isShareLinkEnabled(principal));
   }

   @Test
   void disabledWhenPermissionCheckThrows() throws Exception {
      configureLinkEnabledProperty("true");
      SecurityEngine sec = mock(SecurityEngine.class);
      Principal principal = mock(Principal.class);
      when(sec.checkPermission(any(), any(), anyString(), any()))
         .thenThrow(new SecurityException("not logged in"));

      WizShareController ctrl = new WizShareController(sec);

      assertFalse(ctrl.isShareLinkEnabled(principal));
   }
}
