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

import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.event.VSRefreshEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

   /**
    * Bug #76674. The two overloads do the same whole-viewsheet refresh, but only the
    * STOMP-mapped one carried {@code @LoadingMask}, so every site that moved off the 4-arg
    * method to fix the null-id NPE (#5252's {@code ModifyCalculateFieldService} and
    * {@code ComposerViewsheetService.checkMV}, then #76674's
    * {@code VSChartRefreshService.refreshChart}) silently lost its busy indicator -- an
    * aspect-driven behaviour that no functional test would notice going missing.
    *
    * <p>Asserting the annotation directly is the point: the advice is applied by Spring AOP at
    * the proxy boundary, so a unit test invoking the method cannot observe it at all.
    */
   @Test
   void theExplicitIdOverloadCarriesTheSameLoadingMaskAsTheStompEntryPoint() throws Exception {
      Method explicitId = VSRefreshController.class.getMethod(
         "refreshViewsheet", String.class, VSRefreshEvent.class, Principal.class,
         CommandDispatcher.class, String.class);
      Method stompMapped = VSRefreshController.class.getMethod(
         "refreshViewsheet", VSRefreshEvent.class, Principal.class, CommandDispatcher.class,
         String.class);

      LoadingMask explicitMask = explicitId.getAnnotation(LoadingMask.class);
      LoadingMask stompMask = stompMapped.getAnnotation(LoadingMask.class);

      assertNotNull(stompMask, "precondition: the STOMP entry point is the one being mirrored");
      assertNotNull(explicitMask,
                    "a session-less caller does the same refresh and needs the same mask");
      assertEquals(stompMask.value(), explicitMask.value(),
                   "force flag must match, or the mask is applied on different terms");
      assertEquals(stompMask.watchdogTimeout(), explicitMask.watchdogTimeout(),
                   "a refresh re-runs unbounded runtime queries on either route");
   }
}
