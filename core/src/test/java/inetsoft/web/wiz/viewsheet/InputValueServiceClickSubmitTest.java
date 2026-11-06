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
package inetsoft.web.wiz.viewsheet;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.SubmitVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TextInputVSAssemblyInfo;
import inetsoft.util.script.ScriptException;
import inetsoft.web.binding.event.VSOnClickEvent;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import inetsoft.web.viewsheet.service.VSInputService;
import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import inetsoft.web.wiz.dispatch.CommandErrorException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Bug 80448: {@code click_submit} clicks a Submit button the way the Viewer does. The viewsheet,
 * assembly infos, runtime, sandbox and {@link VSInputService} are stubbed (a real assembly info
 * cannot be built without a Sree context); the capturing dispatcher and message commands are real.
 */
@Tag("core")
class InputValueServiceClickSubmitTest {
   private Viewsheet vs;
   private SubmitVSAssembly submit;
   private SubmitVSAssemblyInfo submitInfo;
   private TextInputVSAssembly text;
   private TextInputVSAssemblyInfo textInfo;
   private VSInputService inputs;
   private InputValueService service;
   private ViewsheetSandbox box;

   private void setUp(String onClick, boolean scriptEnabled, boolean refresh) throws Exception {
      vs = mock(Viewsheet.class);
      submitInfo = mock(SubmitVSAssemblyInfo.class);
      when(submitInfo.getOnClick()).thenReturn(onClick);
      when(submitInfo.isScriptEnabled()).thenReturn(scriptEnabled);
      when(submitInfo.isRefresh()).thenReturn(refresh);
      when(submitInfo.isEnabled()).thenReturn(true);
      when(submitInfo.isVisible()).thenReturn(true);
      submit = mock(SubmitVSAssembly.class);
      when(submit.getVSAssemblyInfo()).thenReturn(submitInfo);
      when(vs.getAssembly("Submit1")).thenReturn(submit);
      when(vs.getAssembly("EV.Submit1")).thenReturn(submit);

      textInfo = mock(TextInputVSAssemblyInfo.class);
      when(textInfo.isSubmitOnChange()).thenReturn(false);
      when(textInfo.isPassword()).thenCallRealMethod();
      when(textInfo.getSelectedObjects()).thenReturn(new Object[] { "hello" });
      text = mock(TextInputVSAssembly.class);
      when(text.getVSAssemblyInfo()).thenReturn(textInfo);
      when(text.getAbsoluteName()).thenReturn("TextInput1");
      when(vs.getAssembly("TextInput1")).thenReturn(text);

      box = mock(ViewsheetSandbox.class);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));

      inputs = mock(VSInputService.class);
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      when(sessions.mutate(any(), any(), any())).thenAnswer(inv -> {
         ViewsheetSessionService.Mutation m = inv.getArgument(2);
         return CapturingCommandDispatcher.withCapturingDispatcher(principal(), d -> {
            m.run(rvs, "rt", d);
            return d.getWarnings();
         });
      });
      service = new InputValueService(sessions, inputs, mock(CoreLifecycleService.class), null);
   }

   private static Principal principal() {
      return () -> "u";
   }

   private static MessageCommand message(MessageCommand.Type type, String text) {
      MessageCommand cmd = new MessageCommand();
      cmd.setType(type);
      cmd.setMessage(text);
      return cmd;
   }

   private Map<String, Object> click(String name, List<InputValueService.SubmitValue> values,
                                     boolean confirmed)
      throws Exception
   {
      return service.clickSubmit("tok", principal(), name, values, confirmed, "");
   }

   private static InputValueService.SubmitValue val(String assembly, Object... v) {
      return new InputValueService.SubmitValue(assembly, List.of(v));
   }

   private void verifyClicked(int times) throws Exception {
      verify(inputs, times(times)).onClick(eq("rt"), eq("Submit1"), eq("0"), eq("0"), isNull(),
                                           eq(""), any(), any(CapturingCommandDispatcher.class));
   }

   @Test
   void emptyOnClickStillRefreshesAndReportsItSeparately() throws Exception {
      for(String empty : new String[] { null, "" }) {
         setUp(empty, true, true);

         Map<String, Object> r = click("Submit1", null, false);

         verifyClicked(1);
         assertEquals(false, r.get("scriptFired"));
         assertEquals(true, r.get("refreshed"));
         assertTrue(String.valueOf(r.get("scriptSkippedReason")).contains("no onClick"),
                    r.toString());
      }
   }

   @Test
   void scriptAndRefreshAreReportedIndependently() throws Exception {
      setUp("Text1.text = 'x'", true, false);

      Map<String, Object> r = click("Submit1", List.of(), false);

      assertEquals(true, r.get("scriptFired"));
      assertEquals(false, r.get("refreshed"));
      assertEquals(List.of(), r.get("applied"));
      verifyClicked(1);
   }

   @Test
   void disabledScriptSkipsBothScriptAndRefresh() throws Exception {
      setUp("x", false, true);

      Map<String, Object> r = click("Submit1", null, false);

      assertEquals(false, r.get("scriptFired"));
      assertEquals(false, r.get("refreshed"));
      assertTrue(String.valueOf(r.get("scriptSkippedReason")).contains("disabled"), r.toString());
   }

   @Test
   void valuesAreWrittenCommittedThenTheButtonIsClickedWithNoValues() throws Exception {
      setUp("x", true, true);
      List<String> order = new ArrayList<>();
      doAnswer(inv -> { order.add("apply"); return null; }).when(inputs)
         .multiApplySelection(any(), any(), any(), any(), any(), any());
      doAnswer(inv -> { order.add("commit"); return null; }).when(box)
         .processChange(eq("TextInput1"), anyInt(), any());
      doAnswer(inv -> { order.add("click"); return null; }).when(inputs)
         .onClick(any(), any(), any(), any(), any(), any(), any(), any());

      Map<String, Object> r = click("Submit1", List.of(val("TextInput1", "hello")), false);

      assertEquals(List.of("apply", "commit", "click"), order);
      verify(inputs).multiApplySelection(eq("rt"), eq(new String[] { "TextInput1" }),
                                         eq(new Object[] { "hello" }), any(), any(), eq(""));
      List<?> applied = (List<?>) r.get("applied");
      assertEquals(1, applied.size());
      assertEquals("TextInput1", ((Map<?, ?>) applied.get(0)).get("assembly"));
      assertEquals(List.of("hello"), ((Map<?, ?>) applied.get(0)).get("values"));
      assertEquals(true, r.get("scriptFired"));
   }

   @Test
   void writeErrorMeansTheScriptIsNeverFired() throws Exception {
      setUp("x", true, true);
      doAnswer(inv -> {
         ((CapturingCommandDispatcher) inv.getArgument(4))
            .sendCommand(message(MessageCommand.Type.ERROR, "write refused"));
         return null;
      }).when(inputs).multiApplySelection(any(), any(), any(), any(), any(), any());

      CommandErrorException ex = assertThrows(CommandErrorException.class,
         () -> click("Submit1", List.of(val("TextInput1", "v")), false));

      assertTrue(ex.getErrors().contains("write refused"), ex.getErrors().toString());
      verifyClicked(0);
   }

   @Test
   void confirmedRunsTheYesBranchThroughOnConfirm() throws Exception {
      setUp("if(confirm('sure?')) {}", true, true);

      Map<String, Object> r = click("Submit1", null, true);

      ArgumentCaptor<VSOnClickEvent> event = ArgumentCaptor.forClass(VSOnClickEvent.class);
      verify(inputs).onConfirm(eq("rt"), eq("Submit1"), eq("0"), eq("0"), eq(true),
                               event.capture(), eq(""), any(),
                               any(CapturingCommandDispatcher.class));
      assertTrue(event.getValue().isConfirmed());
      verifyClicked(0);
      assertEquals(true, r.get("scriptFired"));
   }

   @Test
   void confirmedTogetherWithValuesIsRefused() throws Exception {
      setUp("x", true, true);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> click("Submit1", List.of(val("TextInput1", "v")), true));

      assertTrue(ex.getMessage().contains("confirmed") && ex.getMessage().contains("values"),
                 ex.getMessage());
      verifyNoInteractions(inputs);
   }

   @Test
   void unansweredConfirmIsReportedAsPending() throws Exception {
      setUp("x", true, true);
      doAnswer(inv -> {
         ((CapturingCommandDispatcher) inv.getArgument(7))
            .sendCommand(message(MessageCommand.Type.CONFIRM, "Are you sure?"));
         return null;
      }).when(inputs).onClick(any(), any(), any(), any(), any(), any(), any(), any());

      Map<String, Object> r = click("Submit1", null, false);

      assertEquals("Are you sure?", r.get("confirmPending"));
      assertTrue(r.get("warnings").toString().contains("confirmed:true"), r.toString());
   }

   @Test
   void alertAndInfoMessagesAreSurfaced() throws Exception {
      setUp("alert('Saved')", true, true);
      doAnswer(inv -> {
         ((CapturingCommandDispatcher) inv.getArgument(7))
            .sendCommand(message(MessageCommand.Type.INFO, "Saved"));
         return null;
      }).when(inputs).onClick(any(), any(), any(), any(), any(), any(), any(), any());

      Map<String, Object> r = click("Submit1", null, false);

      assertEquals(List.of("Saved"), r.get("messages"));
   }

   @Test
   void scriptErrorKeepsTheValuesAndIsReportedNotThrown() throws Exception {
      setUp("throw 'x'", true, true);
      doThrow(new ScriptException("boom")).when(inputs)
         .onClick(any(), any(), any(), any(), any(), any(), any(), any());

      Map<String, Object> r = click("Submit1", List.of(val("TextInput1", "v")), false);

      assertEquals(true, r.get("scriptFailed"));
      assertTrue(String.valueOf(r.get("scriptError")).contains("boom"), r.toString());
      assertEquals(1, ((List<?>) r.get("applied")).size());
      verify(inputs, times(1)).multiApplySelection(any(), any(), any(), any(), any(), any());
   }

   @Test
   void disabledButtonIsRefusedAndNothingChanges() throws Exception {
      setUp("x", true, true);
      when(submitInfo.isEnabled()).thenReturn(false);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> click("Submit1", List.of(val("TextInput1", "v")), false));

      assertTrue(ex.getMessage().contains("disabled") &&
                 ex.getMessage().contains("Nothing was changed"), ex.getMessage());
      verifyNoInteractions(inputs);
   }

   @Test
   void hiddenButtonIsClickedWithAWarning() throws Exception {
      setUp("x", true, true);
      when(submitInfo.isVisible()).thenReturn(false);

      Map<String, Object> r = click("Submit1", null, false);

      verifyClicked(1);
      assertTrue(r.get("warnings").toString().contains("hidden"), r.toString());
   }

   @Test
   void nonSubmitAssemblyIsRefusedNamingItsType() throws Exception {
      setUp("x", true, true);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> click("TextInput1", null, false));

      assertTrue(ex.getMessage().contains("not a submit button"), ex.getMessage());
      verifyNoInteractions(inputs);
   }

   @Test
   void unknownAssemblyAndBlankNameAreRefused() throws Exception {
      setUp("x", true, true);

      assertTrue(assertThrows(IllegalArgumentException.class,
         () -> click("Nope", null, false)).getMessage().contains("Unknown assembly 'Nope'"));
      assertTrue(assertThrows(IllegalArgumentException.class,
         () -> click(" ", null, false)).getMessage().contains("'assembly' is required"));
   }

   @Test
   void filterInValuesIsRefusedPointingAtTheSelectionTools() throws Exception {
      setUp("x", true, true);
      SelectionListVSAssembly list = mock(SelectionListVSAssembly.class);
      when(vs.getAssembly("List1")).thenReturn(list);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> click("Submit1", List.of(val("List1", "East")), false));

      assertTrue(ex.getMessage().contains("List1") && ex.getMessage().contains("set_selection") &&
                 ex.getMessage().contains("set_calendar_dates"), ex.getMessage());
      verifyNoInteractions(inputs);
   }

   @Test
   void duplicateAndMalformedValuesAreRefusedBeforeAnyWrite() throws Exception {
      setUp("x", true, true);

      assertTrue(assertThrows(IllegalArgumentException.class,
         () -> click("Submit1", List.of(val("TextInput1", "a"), val("TextInput1", "b")), false))
         .getMessage().contains("more than once"));
      assertTrue(assertThrows(IllegalArgumentException.class,
         () -> click("Submit1", List.of(new InputValueService.SubmitValue("TextInput1", null)),
                     false)).getMessage().contains("'value'"));
      assertTrue(assertThrows(IllegalArgumentException.class,
         () -> click("Submit1", List.of(new InputValueService.SubmitValue(" ", List.of("a"))),
                     false)).getMessage().contains("'assembly'"));
      verifyNoInteractions(inputs);
   }

   @Test
   void submitOnChangeInputIsAcceptedWithANoteAndNotCommittedTwice() throws Exception {
      setUp("x", true, true);
      when(textInfo.isSubmitOnChange()).thenReturn(true);

      Map<String, Object> r = click("Submit1", List.of(val("TextInput1", "hello")), false);

      verify(box, never()).processChange(eq("TextInput1"), anyInt(), any());
      assertTrue(r.get("warnings").toString().contains("submits on change"), r.toString());
      verifyClicked(1);
   }

   @Test
   void embeddedViewsheetSubmitByDottedNameIsAccepted() throws Exception {
      setUp("x", true, true);

      click("EV.Submit1", null, false);

      verify(inputs).onClick(eq("rt"), eq("EV.Submit1"), eq("0"), eq("0"), isNull(), eq(""),
                             any(), any(CapturingCommandDispatcher.class));
   }
}
