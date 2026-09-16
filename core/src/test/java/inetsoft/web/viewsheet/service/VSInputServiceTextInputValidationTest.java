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
 * Test strategy (VOF-005 / Redmine #76717)
 *
 * VSInputService.applySelection0 wrote a TextInput's value via
 * TextInputVSAssemblyInfo.setSelectedObject(obj) unconditionally, never consulting the
 * assembly's own Input Editor ColumnOption (FloatColumnOption/IntegerColumnOption min/max, or
 * TextColumnOption's pattern). The bound was enforced only by the Angular Composer form at
 * keystroke time; both the wiz-agent's InputValueService and the native browser's
 * VSTextInputController funnel into this exact same method, so a value bypassing the browser
 * form (the wiz-agent REST path, or a hand-crafted STOMP frame) was applied and persisted with no
 * server-side check at all.
 *
 * The fix calls option.validate(obj) before setSelectedObject and, on failure, reports a
 * MessageCommand.Type.ERROR via coreLifecycleService.sendMessage instead of applying the value --
 * mirroring the existing option.validate(val)/sendMessage(error, ERROR, dispatcher) pattern
 * VSFormTableService already uses for form-table cell edits.
 *
 * Behavioral guarantees covered:
 *
 * [G1] An out-of-range Float value is rejected: no setSelectedObject call, an ERROR
 *      MessageCommand is sent.
 * [G2] A non-numeric string against a Float-typed TextInput is rejected the same way.
 * [G3] A valid, in-range value still applies: setSelectedObject is called, no ERROR command is
 *      sent.
 */

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.viewsheet.FloatColumnOption;
import inetsoft.uql.viewsheet.TextInputVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TextInputVSAssemblyInfo;
import inetsoft.web.viewsheet.command.MessageCommand;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@Tag("core")
class VSInputServiceTextInputValidationTest {
   // [G1]
   @Test
   void outOfRangeFloatValueIsRejected() throws Exception {
      FloatColumnOption option = new FloatColumnOption("100", "0", "value {0} out of bounds", false);
      TextInputVSAssemblyInfo info = mock(TextInputVSAssemblyInfo.class);
      when(info.getColumnOption()).thenReturn(option);

      TextInputVSAssembly assembly = mock(TextInputVSAssembly.class);
      when(assembly.getVSAssemblyInfo()).thenReturn(info);
      when(assembly.getDataType()).thenReturn("string");

      CoreLifecycleService coreLifecycleService = mock(CoreLifecycleService.class);
      CommandDispatcher dispatcher = mock(CommandDispatcher.class);

      invokeApplySelection0(assembly, "15000", coreLifecycleService, dispatcher);

      verify(info, never()).setSelectedObject(any());
      verify(coreLifecycleService).sendMessage(
         eq("value 15000 out of bounds"), eq(MessageCommand.Type.ERROR), eq(dispatcher));
   }

   // [G2]
   @Test
   void nonNumericStringIsRejectedOnFloatTypedTextInput() throws Exception {
      FloatColumnOption option = new FloatColumnOption(null, null, null, false);
      TextInputVSAssemblyInfo info = mock(TextInputVSAssemblyInfo.class);
      when(info.getColumnOption()).thenReturn(option);

      TextInputVSAssembly assembly = mock(TextInputVSAssembly.class);
      when(assembly.getVSAssemblyInfo()).thenReturn(info);
      when(assembly.getDataType()).thenReturn("string");

      CoreLifecycleService coreLifecycleService = mock(CoreLifecycleService.class);
      CommandDispatcher dispatcher = mock(CommandDispatcher.class);

      invokeApplySelection0(assembly, "abc", coreLifecycleService, dispatcher);

      verify(info, never()).setSelectedObject(any());
      verify(coreLifecycleService).sendMessage(any(), eq(MessageCommand.Type.ERROR), eq(dispatcher));
   }

   // [G3]
   @Test
   void validInRangeValueStillApplies() throws Exception {
      FloatColumnOption option = new FloatColumnOption("100", "0", null, false);
      TextInputVSAssemblyInfo info = mock(TextInputVSAssemblyInfo.class);
      when(info.getColumnOption()).thenReturn(option);
      when(info.setSelectedObject(any())).thenReturn(0);
      when(info.getWriteBackValue()).thenReturn(false);

      Viewsheet vs = mock(Viewsheet.class);
      TextInputVSAssembly assembly = mock(TextInputVSAssembly.class);
      when(assembly.getVSAssemblyInfo()).thenReturn(info);
      when(assembly.getDataType()).thenReturn("string");
      when(assembly.getViewsheet()).thenReturn(vs);

      CoreLifecycleService coreLifecycleService = mock(CoreLifecycleService.class);
      CommandDispatcher dispatcher = mock(CommandDispatcher.class);

      invokeApplySelection0(assembly, "50", coreLifecycleService, dispatcher);

      verify(info).setSelectedObject("50");
      verify(coreLifecycleService, never())
         .sendMessage(any(), eq(MessageCommand.Type.ERROR), any());
   }

   private static void invokeApplySelection0(TextInputVSAssembly assembly, Object selectedObject,
                                             CoreLifecycleService coreLifecycleService,
                                             CommandDispatcher dispatcher) throws Exception
   {
      Viewsheet vs = assembly.getViewsheet();

      if(vs == null) {
         vs = mock(Viewsheet.class);
      }

      when(vs.getAssembly("Assembly1")).thenReturn(assembly);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));

      VSInputService service = new VSInputService(null, coreLifecycleService, null, null, null,
                                                    null, null, null, null);

      Method method = VSInputService.class.getDeclaredMethod(
         "applySelection0", RuntimeViewsheet.class, String.class, Object.class,
         CommandDispatcher.class);
      method.setAccessible(true);
      method.invoke(service, rvs, "Assembly1", selectedObject, dispatcher);
   }
}
