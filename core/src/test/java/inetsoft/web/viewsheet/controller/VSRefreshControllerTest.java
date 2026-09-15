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
package inetsoft.web.viewsheet.controller;

import inetsoft.web.viewsheet.event.VSRefreshEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for bug #76666 (NPE in {@code RuntimeSheetCache.getAffinityKey}) and its
 * review-round-1 follow-up: {@link VSRefreshController#refreshViewsheet(String, VSRefreshEvent,
 * Principal, CommandDispatcher, String)} is the single choke point every session-less caller
 * (and the STOMP-mapped entry point) now funnels through, so it must fail fast and by name on a
 * null runtime id rather than letting it travel unguarded into Ignite's affinity routing.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
public class VSRefreshControllerTest {
   @Mock
   private RuntimeViewsheetRef runtimeViewsheetRef;
   @Mock
   private VSRefreshServiceProxy vsRefreshServiceProxy;
   @Mock
   private Principal principal;
   @Mock
   private CommandDispatcher commandDispatcher;

   @Test
   void refreshViewsheetRejectsNullRuntimeIdWithNamedException() {
      VSRefreshController controller =
         new VSRefreshController(runtimeViewsheetRef, vsRefreshServiceProxy);
      VSRefreshEvent event = VSRefreshEvent.builder().confirmed(false).initing(false).build();

      NullPointerException ex = assertThrows(NullPointerException.class, () ->
         controller.refreshViewsheet(null, event, principal, commandDispatcher, "linkUri"));

      // Confirms the diagnostic names the actual failing parameter -- the whole point of this
      // guard is to replace a cryptic, decompiled Ignite error ("Ouch! Argument cannot be null:
      // key") with a message that points straight at the cause without needing a jar
      // decompilation to interpret.
      assertEquals("runtimeId (no live session and no explicit id supplied)", ex.getMessage());
   }

   @Test
   void refreshViewsheetPassesExplicitRuntimeIdThrough() throws Exception {
      VSRefreshController controller =
         new VSRefreshController(runtimeViewsheetRef, vsRefreshServiceProxy);
      VSRefreshEvent event = VSRefreshEvent.builder().confirmed(false).initing(false).build();

      controller.refreshViewsheet("explicit-id", event, principal, commandDispatcher, "linkUri");

      // The explicit id is used as-is; RuntimeViewsheetRef (the session-scoped bean this
      // overload exists to bypass) is never consulted.
      verify(vsRefreshServiceProxy)
         .refreshViewsheetAsync("explicit-id", event, principal, commandDispatcher, "linkUri");
      verify(runtimeViewsheetRef, org.mockito.Mockito.never()).getRuntimeId();
   }

   @Test
   void stompMappedEntryPointDelegatesUsingItsOwnSessionScopedId() throws Exception {
      VSRefreshController controller =
         new VSRefreshController(runtimeViewsheetRef, vsRefreshServiceProxy);
      VSRefreshEvent event = VSRefreshEvent.builder().confirmed(false).initing(false).build();
      when(runtimeViewsheetRef.getRuntimeId()).thenReturn("session-id");

      controller.refreshViewsheet(event, principal, commandDispatcher, "linkUri");

      verify(vsRefreshServiceProxy)
         .refreshViewsheetAsync("session-id", event, principal, commandDispatcher, "linkUri");
   }
}
