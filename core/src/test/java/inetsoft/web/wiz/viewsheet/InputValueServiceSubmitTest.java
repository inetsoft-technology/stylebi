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

import inetsoft.report.composition.ExpiredSheetException;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TextInputVSAssemblyInfo;
import inetsoft.util.CancelledException;
import inetsoft.util.script.ScriptException;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.controller.OnClickService;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import inetsoft.web.viewsheet.service.VSInputService;
import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import inetsoft.web.wiz.dispatch.CommandErrorException;
import inetsoft.web.wiz.pairing.PairingException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Bug 80465 PR-B: {@code set_input_value submit:true} fires a text input's onClick script the way
 * the Viewer's Enter does. Uses the real capturing dispatcher and real {@code MessageCommand}s;
 * the viewsheet, assembly info, runtime, sandbox and {@link OnClickService} are stubbed (a real
 * assembly info cannot be built without a Sree context).
 */
@Tag("core")
class InputValueServiceSubmitTest {
   private static final String SCRIPT = "Text1.text = TextInput1.value";

   private Viewsheet vs;
   private TextInputVSAssembly text;
   private TextInputVSAssemblyInfo info;
   private final AtomicReference<Object> stored = new AtomicReference<>();
   private OnClickService onClick;
   private VSInputService inputs;
   private InputValueService service;
   private boolean sandbox = true;

   /**
    * Info state is held in the stubs: a real VSAssemblyInfo (or Viewsheet) cannot be constructed
    * outside a Sree/Spring context in this environment, which is why the sibling tests mock too.
    */
   private void setUp(String onClickScript, boolean scriptEnabled) throws Exception {
      stored.set(null);
      sandbox = true;
      vs = mock(Viewsheet.class);
      info = mock(TextInputVSAssemblyInfo.class);
      when(info.getOnClick()).thenReturn(onClickScript);
      when(info.isScriptEnabled()).thenReturn(scriptEnabled);
      when(info.isSubmitOnChange()).thenReturn(true);
      when(info.isPassword()).thenCallRealMethod();
      when(info.getSelectedObjects()).thenAnswer(inv -> new Object[] { stored.get() });
      text = mock(TextInputVSAssembly.class);
      when(text.getVSAssemblyInfo()).thenReturn(info);
      when(text.getAbsoluteName()).thenReturn("TextInput1");
      when(vs.getAssembly("TextInput1")).thenReturn(text);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenAnswer(
         inv -> sandbox ? Optional.of(mock(ViewsheetSandbox.class)) : Optional.empty());

      inputs = mock(VSInputService.class);
      doAnswer(inv -> {
         stored.set(inv.getArgument(2));
         return null;
      }).when(inputs).singleApplySelection(any(), any(), any(), any(), any(), any());

      onClick = mock(OnClickService.class);
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      when(sessions.mutate(any(), any(), any())).thenAnswer(inv -> {
         ViewsheetSessionService.Mutation m = inv.getArgument(2);
         return CapturingCommandDispatcher.withCapturingDispatcher(principal(), d -> {
            m.run(rvs, "rt", d);
            return d.getWarnings();
         });
      });
      service = new InputValueService(sessions, inputs, mock(CoreLifecycleService.class), onClick);
   }

   private static Principal principal() {
      return () -> "u";
   }

   private Map<String, Object> submit(String value) throws Exception {
      return service.setValue("tok", principal(), "TextInput1", List.of(value), "", true);
   }

   private static MessageCommand message(MessageCommand.Type type, String text) {
      MessageCommand cmd = new MessageCommand();
      cmd.setType(type);
      cmd.setMessage(text);
      return cmd;
   }

   @Test
   void firesOnClickAfterStoringAndReportsIt() throws Exception {
      setUp(SCRIPT, true);

      Map<String, Object> r = submit("hello");

      verify(onClick).onClick(eq("rt"), eq("TextInput1"), eq("0"), eq("0"), isNull(), eq(""), any(),
                              any(CapturingCommandDispatcher.class));
      assertEquals(true, r.get("scriptFired"));
      assertEquals(true, r.get("valueStored"));
      assertEquals(List.of("hello"), r.get("values"));
      assertNull(r.get("scriptFailed"));
      assertNull(r.get("adjustedByScript"));
   }

   @Test
   void withoutSubmitTheScriptIsNeverCalled() throws Exception {
      setUp(SCRIPT, true);

      Map<String, Object> r = service.setValue("tok", principal(), "TextInput1", List.of("x"), "");

      verifyNoInteractions(onClick);
      assertNull(r.get("scriptFired"));
   }

   @Test
   void disabledScriptIsNotFiredAndSaysWhy() throws Exception {
      setUp(SCRIPT, false);

      Map<String, Object> r = submit("hello");

      verifyNoInteractions(onClick);
      assertEquals(false, r.get("scriptFired"));
      assertTrue(String.valueOf(r.get("scriptSkippedReason")).contains("disabled"), r.toString());
      assertEquals(List.of("hello"), r.get("values"));
   }

   @Test
   void emptyOnClickIsNotFiredAndSaysWhy() throws Exception {
      for(String empty : new String[] { null, "" }) {
         setUp(empty, true);

         Map<String, Object> r = submit("hello");

         verifyNoInteractions(onClick);
         assertEquals(false, r.get("scriptFired"), String.valueOf(empty));
         assertTrue(String.valueOf(r.get("scriptSkippedReason")).contains("no onClick"),
                    r.toString());
      }
   }

   @Test
   void throwingScriptKeepsTheValueAndIsReported() throws Exception {
      setUp("throw 'x'", true);
      doThrow(new ScriptException("boom")).when(onClick)
         .onClick(any(), any(), any(), any(), any(), any(), any(), any());

      Map<String, Object> r = submit("kept");

      assertEquals(true, r.get("ok") == null ? true : r.get("ok"));
      assertEquals(true, r.get("scriptFired"));
      assertEquals(true, r.get("scriptFailed"));
      assertTrue(String.valueOf(r.get("scriptError")).contains("boom"), r.toString());
      assertEquals(List.of("kept"), r.get("values"));
      assertTrue(r.get("warnings").toString().contains("boom"), r.toString());
   }

   @Test
   void throwingScriptWrappedInAnotherExceptionIsStillRecognised() throws Exception {
      setUp("throw 'x'", true);
      doThrow(new RuntimeException("wrapped", new ScriptException("inner"))).when(onClick)
         .onClick(any(), any(), any(), any(), any(), any(), any(), any());

      Map<String, Object> r = submit("kept");

      assertEquals(true, r.get("scriptFailed"));
      assertTrue(String.valueOf(r.get("scriptError")).contains("inner"), r.toString());
   }

   @Test
   void nonScriptFailureAfterTheWriteIsReportedNotThrown() throws Exception {
      setUp(SCRIPT, true);
      doThrow(new IllegalStateException("db down")).when(onClick)
         .onClick(any(), any(), any(), any(), any(), any(), any(), any());

      Map<String, Object> r = submit("v");

      assertEquals(true, r.get("scriptFailed"));
      assertTrue(String.valueOf(r.get("scriptError")).contains("db down"), r.toString());
      assertEquals(List.of("v"), r.get("values"));
   }

   /**
    * The capturing dispatcher fails a call on any ERROR it captured, which after the write and the
    * script would read as a failure and invite a retry that runs the script a second time.
    */
   @Test
   void errorDispatchedByTheScriptIsReportedAndTheCallSucceeds() throws Exception {
      setUp(SCRIPT, true);
      doAnswer(inv -> {
         CapturingCommandDispatcher d = inv.getArgument(7);
         d.sendCommand(message(MessageCommand.Type.ERROR, "query failed"));
         return null;
      }).when(onClick).onClick(any(), any(), any(), any(), any(), any(), any(), any());

      Map<String, Object> r = submit("kept");

      assertEquals(true, r.get("valueStored"));
      assertEquals(true, r.get("scriptFired"));
      assertEquals(true, r.get("scriptFailed"));
      assertTrue(String.valueOf(r.get("scriptError")).contains("query failed"), r.toString());
      assertEquals(List.of("kept"), r.get("values"));
      assertTrue(r.get("warnings").toString().contains("query failed"), r.toString());
      // no retry hazard: the write and the script each happened exactly once
      verify(inputs, times(1)).singleApplySelection(any(), any(), any(), any(), any(), any());
      verify(onClick, times(1)).onClick(any(), any(), any(), any(), any(), any(), any(), any());
   }

   @Test
   void errorBeforeTheScriptStillFailsTheCall() throws Exception {
      setUp(SCRIPT, true);
      doAnswer(inv -> {
         stored.set(inv.getArgument(2));
         ((CapturingCommandDispatcher) inv.getArgument(4))
            .sendCommand(message(MessageCommand.Type.ERROR, "write refused"));
         return null;
      }).when(inputs).singleApplySelection(any(), any(), any(), any(), any(), any());

      CommandErrorException ex = assertThrows(CommandErrorException.class, () -> submit("v"));

      assertTrue(ex.getErrors().contains("write refused"), ex.getErrors().toString());
      // so "an error response means the script did not run" is true
      verify(onClick, never()).onClick(any(), any(), any(), any(), any(), any(), any(), any());
   }

   @Test
   void securityAndSessionExceptionsKeepTheirStatusAndPropagate() throws Exception {
      Throwable[] control = {
         new SecurityException("denied"),
         new RuntimeException("wrapped", new SecurityException("denied deep")),
         new CancelledException("cancelled"),
         new ExpiredSheetException("rt", principal()),
      };

      for(Throwable t : control) {
         setUp(SCRIPT, true);
         doThrow(t).when(onClick).onClick(any(), any(), any(), any(), any(), any(), any(), any());

         Exception ex = assertThrows(Exception.class, () -> submit("v"), t.toString());

         assertTrue(InputValueService.isControlFlow(ex), ex.toString());
      }
   }

   @Test
   void unexpectedRuntimeExceptionIsReportedAndLogged() throws Exception {
      setUp(SCRIPT, true);
      IllegalStateException boom = new IllegalStateException("refresh blew up");
      doThrow(boom).when(onClick).onClick(any(), any(), any(), any(), any(), any(), any(), any());
      ch.qos.logback.classic.Logger logger =
         (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(InputValueService.class);
      ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
         new ch.qos.logback.core.read.ListAppender<>();
      appender.start();
      logger.addAppender(appender);

      try {
         Map<String, Object> r = submit("kept");

         assertEquals(true, r.get("scriptFailed"));
         assertTrue(String.valueOf(r.get("scriptError")).contains("refresh blew up"), r.toString());
         assertEquals(1, appender.list.size(), appender.list.toString());
         assertSame(boom, appender.list.get(0).getThrowableProxy() == null ? null :
            ((ch.qos.logback.classic.spi.ThrowableProxy) appender.list.get(0).getThrowableProxy())
               .getThrowable());
      }
      finally {
         logger.detachAppender(appender);
      }
   }

   @Test
   void submitWorksOnAnInputThatDoesNotSubmitOnChange() throws Exception {
      setUp(SCRIPT, true);
      when(info.isSubmitOnChange()).thenReturn(false);

      Map<String, Object> r = submit("hello");

      assertEquals(true, r.get("scriptFired"));
      assertEquals(List.of("hello"), r.get("values"));
      verify(onClick, times(1)).onClick(any(), any(), any(), any(), any(), any(), any(), any());
   }

   private static final String SECRET = "s3cr3t-Hunter2";

   private void secretInput(String script) throws Exception {
      setUp(script, true);
      when(info.getColumnOption()).thenReturn(new PasswordColumnOption());
   }

   @Test
   void secretNeverAppearsInAThrownScriptMessage() throws Exception {
      secretInput("throw 'bad ' + TextInput1.value");
      doThrow(new ScriptException("bad " + SECRET)).when(onClick)
         .onClick(any(), any(), any(), any(), any(), any(), any(), any());

      Map<String, Object> r = submit(SECRET);

      assertEquals(true, r.get("scriptFailed"));
      assertFalse(r.toString().contains(SECRET), r.toString());
      assertTrue(String.valueOf(r.get("scriptError")).contains(InputValueService.SECRET_MASK),
                 r.toString());
   }

   @Test
   void secretNeverAppearsInAConfirmMessageOrScriptError() throws Exception {
      secretInput("confirm('Log in with ' + TextInput1.value + '?')");
      doAnswer(inv -> {
         CapturingCommandDispatcher d = inv.getArgument(7);
         d.sendCommand(message(MessageCommand.Type.CONFIRM, "Log in with " + SECRET + "?"));
         d.sendCommand(message(MessageCommand.Type.ERROR, "rejected " + SECRET));
         d.sendCommand(message(MessageCommand.Type.WARNING, "warned " + SECRET));
         return null;
      }).when(onClick).onClick(any(), any(), any(), any(), any(), any(), any(), any());

      Map<String, Object> r = submit(SECRET);

      assertFalse(r.toString().contains(SECRET), r.toString());
      assertEquals("Log in with " + InputValueService.SECRET_MASK + "?", r.get("confirmPending"));
      assertEquals(true, r.get("masked"));
   }

   @Test
   void secretHeldBeforeTheScriptIsAlsoScrubbed() throws Exception {
      secretInput(SCRIPT);
      doAnswer(inv -> {
         stored.set("other-secret");
         throw new ScriptException("saw other-secret and " + SECRET);
      }).when(onClick).onClick(any(), any(), any(), any(), any(), any(), any(), any());

      Map<String, Object> r = submit(SECRET);

      assertFalse(r.toString().contains(SECRET), r.toString());
      assertFalse(r.toString().contains("other-secret"), r.toString());
   }

   @Test
   void confirmIsReportedNotAnswered() throws Exception {
      setUp("confirm('ok?')", true);
      doAnswer(inv -> {
         CapturingCommandDispatcher d = inv.getArgument(7);
         d.sendCommand(message(MessageCommand.Type.CONFIRM, "ok?"));
         return null;
      }).when(onClick).onClick(any(), any(), any(), any(), any(), any(), any(), any());

      Map<String, Object> r = submit("v");

      assertEquals(true, r.get("scriptFired"));
      assertEquals("ok?", r.get("confirmPending"));
      assertTrue(r.get("warnings").toString().contains("not answered"), r.toString());
   }

   @Test
   void scriptThatChangesTheValueIsReported() throws Exception {
      setUp(SCRIPT, true);
      doAnswer(inv -> {
         stored.set("");
         return null;
      }).when(onClick).onClick(any(), any(), any(), any(), any(), any(), any(), any());

      Map<String, Object> r = submit("typed");

      assertEquals(List.of("typed"), r.get("valuesBeforeScript"));
      assertEquals(true, r.get("adjustedByScript"));
      assertNotEquals(List.of("typed"), r.get("values"));
   }

   @Test
   void passwordEditorStaysMaskedIncludingBeforeScript() throws Exception {
      setUp(SCRIPT, true);
      when(info.getColumnOption()).thenReturn(new PasswordColumnOption());
      doAnswer(inv -> {
         stored.set("changed");
         return null;
      }).when(onClick).onClick(any(), any(), any(), any(), any(), any(), any(), any());

      Map<String, Object> r = submit("s3cr3t-Hunter2");

      assertFalse(r.toString().contains("s3cr3t-Hunter2"), r.toString());
      assertFalse(r.toString().contains("changed"), r.toString());
      assertEquals(true, r.get("masked"));
      assertEquals(true, r.get("adjustedByScript"));
   }

   @Test
   void everyOtherInputTypeIsRefusedBeforeAnyWrite() throws Exception {
      setUp(SCRIPT, true);
      when(vs.getAssembly("Pick")).thenReturn(mock(CheckBoxVSAssembly.class));
      when(vs.getAssembly("Spin")).thenReturn(mock(SpinnerVSAssembly.class));

      for(String name : new String[] { "Pick", "Spin" }) {
         IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> service.setValue("tok", principal(), name, List.of("1"), "", true));
         assertTrue(ex.getMessage().contains("'submit'") && ex.getMessage().contains("'" + name + "'"),
                    ex.getMessage());
      }

      verifyNoInteractions(inputs);
      verifyNoInteractions(onClick);
   }

   @Test
   void missingSandboxIsANamedErrorBeforeAnyWrite() throws Exception {
      setUp(SCRIPT, true);
      sandbox = false;

      PairingException ex = assertThrows(PairingException.class, () -> submit("v"));

      assertTrue(ex.getMessage().contains("sandbox"), ex.getMessage());
      verifyNoInteractions(inputs);
      verifyNoInteractions(onClick);
   }
}
