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
package inetsoft.web.viewsheet.service;

/*
 * Test strategy (VTB-009 / Redmine #76574)
 *
 * VSInputService.applySelection unconditionally overwrote rvs's paired-browser socket session
 * identity with whatever CommandDispatcher happened to be in play. The wiz agent's
 * InputValueService.setValue (the sole agent-side caller of singleApplySelection/
 * multiApplySelection/applySelection, serving CheckBox/ComboBox/RadioButton/TextInput/Spinner)
 * always runs under a CapturingCommandDispatcher, whose getSessionId() deterministically returns
 * null (it wraps a synthetic, non-STOMP message with no simpSessionId header). That null then
 * clobbered rvs's already-correct socket session id, so
 * SheetAgentBroadcastService.broadcastRefresh's "sessionId == null" guard silently skipped the
 * paired browser's refresh broadcast on every single agent-driven input write.
 *
 * The fix only adopts the dispatcher's session id/user name when the dispatcher actually has one,
 * so a CapturingCommandDispatcher (or any dispatcher with no real session) leaves an
 * already-recorded socket session alone, while a real STOMP dispatcher (every native browser
 * controller: VSSliderController, VSSpinnerController, VSCheckBoxController, OnClickService,
 * VSRadioButtonController, VSTextInputController) still updates it exactly as before.
 *
 * Behavioral guarantees covered:
 *
 * [G1] A dispatcher with no session id (CapturingCommandDispatcher's shape) must NOT overwrite an
 *      already-recorded socket session id/user name on the runtime viewsheet.
 * [G2] A dispatcher with a real session id (native STOMP controller's shape) still updates the
 *      runtime viewsheet's socket session id/user name, unchanged from before the fix.
 */

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.viewsheet.Viewsheet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class VSInputServiceSocketSessionTest {
   // [G1]
   @Test
   void aSessionlessDispatcherDoesNotClobberAnAlreadyRecordedSocketSession() throws Exception {
      RuntimeViewsheet rvs = newMockRuntimeViewsheet();
      when(rvs.getSocketSessionId()).thenReturn("already-correct-session");
      when(rvs.getSocketUserName()).thenReturn("already-correct-user");

      CommandDispatcher dispatcher = mock(CommandDispatcher.class);
      when(dispatcher.getSessionId()).thenReturn(null);

      invokeApplySelection(rvs, dispatcher);

      verify(rvs, never()).setSocketSessionId(any());
      verify(rvs, never()).setSocketUserName(any());
   }

   // [G2]
   @Test
   void aRealDispatchersSessionStillUpdatesTheSocketSession() throws Exception {
      RuntimeViewsheet rvs = newMockRuntimeViewsheet();

      CommandDispatcher dispatcher = mock(CommandDispatcher.class);
      when(dispatcher.getSessionId()).thenReturn("real-stomp-session");
      when(dispatcher.getUserName()).thenReturn("real-browser-user");

      invokeApplySelection(rvs, dispatcher);

      verify(rvs).setSocketSessionId("real-stomp-session");
      verify(rvs).setSocketUserName("real-browser-user");
   }

   /** A viewsheet whose sandbox is present but which has no assembly named "Assembly1", so
    *  applySelection0 takes its early "not an InputVSAssembly" return -- the socket-session
    *  clobber under test happens before that point regardless of the outcome. */
   private static RuntimeViewsheet newMockRuntimeViewsheet() {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      Viewsheet vs = mock(Viewsheet.class);
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));
      when(vs.getAssembly("Assembly1")).thenReturn(null);

      return rvs;
   }

   private static void invokeApplySelection(RuntimeViewsheet rvs, CommandDispatcher dispatcher)
      throws Exception
   {
      VSObjectService vsObjectService = mock(VSObjectService.class);
      Principal principal = mock(Principal.class);
      when(vsObjectService.getRuntimeViewsheet(eq("vs-1"), eq(principal))).thenReturn(rvs);

      VSInputService service = new VSInputService(vsObjectService, null, null, null, null, null,
                                                    null, null, null);

      Method method = VSInputService.class.getDeclaredMethod(
         "applySelection", String.class, String.class, Object.class, Principal.class,
         CommandDispatcher.class);
      method.setAccessible(true);
      method.invoke(service, "vs-1", "Assembly1", "value", principal, dispatcher);
   }
}
