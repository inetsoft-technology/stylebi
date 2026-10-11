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
package inetsoft.uql.util;

import inetsoft.report.composition.execution.ReportWorksheetProcessor;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.erm.vpm.VpmProcessor;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.util.audit.Audit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Bug #78285: the opt-in ws: runQuery outcome observer. */
@Tag("core")
class XUtilRunQueryObserverTest {
   private final List<XUtil.RunQueryOutcome> seen = new ArrayList<>();

   private Object run(XTable table, Throwable failure, boolean observe) throws Exception {
      return run(table, failure, observe, false);
   }

   private Object run(XTable table, Throwable failure, boolean observe, boolean deny)
      throws Exception
   {
      XPrincipal user = mock(XPrincipal.class);
      when(user.getOrgId()).thenReturn("host-org");
      BiConsumer<AssetEntry, XUtil.RunQueryOutcome> previous = null;

      try(MockedStatic<SreeEnv> env = mockStatic(SreeEnv.class);
          MockedStatic<XSessionService> sessions = mockStatic(XSessionService.class);
          MockedStatic<Audit> audit = mockStatic(Audit.class);
          MockedStatic<VpmProcessor> vpm = mockStatic(VpmProcessor.class);
          MockedStatic<SecurityEngine> security = mockStatic(SecurityEngine.class);
          MockedConstruction<ReportWorksheetProcessor> proc = mockConstruction(
             ReportWorksheetProcessor.class, (mock, ctx) -> {
                try {
                   if(failure != null) {
                      when(mock.execute(any(), any(), any())).thenThrow(failure);
                   }
                   else {
                      when(mock.execute(any(), any(), any())).thenReturn(table);
                   }
                }
                catch(Exception ex) {
                   throw new IllegalStateException(ex);
                }
             }))
      {
         env.when(() -> SreeEnv.getProperty("security.provider")).thenReturn(deny ? "custom" : "");
         vpm.when(VpmProcessor::useVpmSecurity).thenReturn(false);

         if(deny) {
            SecurityEngine engine = mock(SecurityEngine.class);
            when(engine.checkPermission(any(java.security.Principal.class), any(inetsoft.sree.security.ResourceType.class), anyString(), any(inetsoft.sree.security.ResourceAction.class))).thenReturn(false);
            security.when(SecurityEngine::getSecurity).thenReturn(engine);
         }

         sessions.when(XSessionService::getService).thenReturn(mock(XSessionService.class));
         audit.when(Audit::getInstance).thenReturn(mock(Audit.class));

         if(observe) {
            previous = XUtil.setRunQueryObserver((e, o) -> seen.add(o));
         }

         try {
            return XUtil.runQuery("ws:global:w", null, user, null, false);
         }
         finally {
            if(observe) {
               XUtil.restoreRunQueryObserver(previous);
            }
         }
      }
   }

   @Test
   void reportsFoundAndNotFound() throws Exception {
      run(mock(XTable.class), null, true);
      run(null, null, true);
      assertEquals(List.of(XUtil.RunQueryOutcome.FOUND, XUtil.RunQueryOutcome.NOT_FOUND), seen);
   }

   @Test
   void reportsDeniedWhenPermissionCheckFails() throws Exception {
      run(mock(XTable.class), null, true, true);
      assertEquals(List.of(XUtil.RunQueryOutcome.DENIED), seen);
   }

   @Test
   void reportsErrorWhenResolutionThrows() throws Exception {
      run(null, new IllegalStateException("x"), true);
      assertEquals(List.of(XUtil.RunQueryOutcome.ERROR), seen);
   }

   @Test
   void unsetObserverChangesNothing() throws Exception {
      assertNotNull(run(null, null, false));
      assertTrue(seen.isEmpty());
   }

   @Test
   void faultyObserverDoesNotChangeTheResult() throws Exception {
      BiConsumer<AssetEntry, XUtil.RunQueryOutcome> previous =
         XUtil.setRunQueryObserver((e, o) -> { throw new RuntimeException("observer fault"); });

      try {
         assertNotNull(run(mock(XTable.class), null, false));
      }
      finally {
         XUtil.restoreRunQueryObserver(previous);
      }
   }

   @Test
   void restoreRemovesTheObserver() throws Exception {
      XUtil.restoreRunQueryObserver(XUtil.setRunQueryObserver((e, o) -> seen.add(o)));
      run(null, null, false);
      assertTrue(seen.isEmpty());
   }
}
