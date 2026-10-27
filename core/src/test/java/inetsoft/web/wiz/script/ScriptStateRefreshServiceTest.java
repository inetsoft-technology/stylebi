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
package inetsoft.web.wiz.script;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.viewsheet.SelectionList;
import inetsoft.uql.viewsheet.SelectionListVSAssembly;
import inetsoft.uql.viewsheet.SelectionTreeVSAssembly;
import inetsoft.uql.viewsheet.SelectionValue;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.viewsheet.controller.VSRefreshService;
import inetsoft.web.viewsheet.event.VSRefreshEvent;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import inetsoft.web.wiz.script.model.ScriptError;
import inetsoft.web.wiz.script.model.ScriptExecResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.Principal;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Bug #79370: a wiz script run must leave the runtime in the state the script wrote -- selection
 * pins resolved, dependents refreshed -- without adding an undo checkpoint.
 */
@WizAgentTestSupport
class ScriptStateRefreshServiceTest {
   private static final String RUNTIME_ID = "rt-79370";
   private static final ScriptExecResult OK =
      new ScriptExecResult(true, null, null, List.of("t"), false, null, "ok", null);

   private VSRefreshService refreshService;
   private ScriptStateRefreshService service;
   private RuntimeViewsheet rvs;
   private Viewsheet vs;
   private final Principal user = () -> "admin";

   @BeforeEach
   void setUp() {
      refreshService = mock(VSRefreshService.class);
      service = new ScriptStateRefreshService(refreshService);
      vs = new Viewsheet();
      rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getID()).thenReturn(RUNTIME_ID);
   }

   @Test
   void refreshesTheWholeSheetAfterTheScriptRuns() throws Exception {
      AtomicBoolean ran = new AtomicBoolean();
      doAnswer(inv -> {
         assertTrue(ran.get(), "the refresh must follow the script");
         return null;
      }).when(refreshService).refreshViewsheet(any(), any(), any(), any(), any());

      ScriptExecResult result = service.runAndRefresh(rvs, user, "", () -> {
         ran.set(true);
         return OK;
      });

      assertSame(OK, result);
      ArgumentCaptor<VSRefreshEvent> event = ArgumentCaptor.forClass(VSRefreshEvent.class);
      verify(refreshService).refreshViewsheet(
         eq(RUNTIME_ID), event.capture(), eq(user), any(), eq(""));
      assertFalse(event.getValue().confirmed());
   }

   @Test
   void resolvesAListPinBeforeTheRefreshAndClearsItAfter() throws Exception {
      SelectionListVSAssembly list = selectionList("Filter", "East", "West");
      list.setScriptSelectedValues(new Object[] { "East" });
      vs.addAssembly(list);
      AtomicBoolean checked = new AtomicBoolean();

      doAnswer(inv -> {
         // the pin has been resolved into the selection list the refresh reads
         assertTrue(selected(list, "East"));
         assertFalse(selected(list, "West"));
         assertNotNull(list.getScriptSelectedValues(), "the pin is cleared only after the refresh");
         checked.set(true);
         return null;
      }).when(refreshService).refreshViewsheet(any(), any(), any(), any(), any());

      service.runAndRefresh(rvs, user, "", () -> OK);

      assertTrue(checked.get());
      assertNull(list.getScriptSelectedValues());
      assertTrue(selected(list, "East"));
   }

   @Test
   void keepsATreePinThatWasNotApplied() throws Exception {
      // a tree that still uses metadata (a design-mode runtime) never applies its pin
      SelectionTreeVSAssembly tree = new SelectionTreeVSAssembly(vs, "Tree");
      Object[] pin = { "East" };
      tree.setScriptSelectedValues(pin);
      vs.addAssembly(tree);

      service.runAndRefresh(rvs, user, "", () -> OK);

      verify(refreshService).refreshViewsheet(any(), any(), any(), any(), any());
      assertSame(pin, tree.getScriptSelectedValues());
   }

   @Test
   void keepsAListPinThatHasNoDataToApplyTo() throws Exception {
      SelectionListVSAssembly list = new SelectionListVSAssembly(vs, "Filter");
      Object[] pin = { "East" };
      list.setScriptSelectedValues(pin);
      vs.addAssembly(list);

      service.runAndRefresh(rvs, user, "", () -> OK);

      assertSame(pin, list.getScriptSelectedValues());
   }

   @Test
   void refreshesWhenNoAssemblyIsPinned() throws Exception {
      vs.addAssembly(selectionList("Filter", "East", "West"));

      service.runAndRefresh(rvs, user, "", () -> OK);

      verify(refreshService).refreshViewsheet(any(), any(), any(), any(), any());
   }

   @Test
   void keepsRuntimeValuesDuringTheRefreshAndRestoresTheFlag() throws Exception {
      AtomicBoolean during = new AtomicBoolean();
      doAnswer(inv -> {
         during.set(ViewsheetSandbox.keepRuntimeValuesRefresh.get());
         throw new IllegalStateException("refresh failed");
      }).when(refreshService).refreshViewsheet(any(), any(), any(), any(), any());

      service.runAndRefresh(rvs, user, "", () -> OK);

      assertTrue(during.get(), "the refresh must keep the script-written runtime values");
      assertFalse(ViewsheetSandbox.keepRuntimeValuesRefresh.get(), "flag left set");
   }

   @Test
   void addsNoCheckpointOrRevision() throws Exception {
      SelectionListVSAssembly list = selectionList("Filter", "East", "West");
      list.setScriptSelectedValues(new Object[] { "East" });
      vs.addAssembly(list);

      service.runAndRefresh(rvs, user, "", () -> OK);

      verify(rvs, never()).addCheckpoint(any());
      verify(rvs, never()).bumpWriteRevision();
   }

   @Test
   void skipsTheRefreshWhenTheRunWasRefusedForConfirmation() throws Exception {
      ScriptExecResult refused =
         new ScriptExecResult(false, null, null, null, true, "destructive", null, null);

      assertSame(refused, service.runAndRefresh(rvs, user, "", () -> refused));

      verify(refreshService, never()).refreshViewsheet(any(), any(), any(), any(), any());
   }

   @Test
   void aScriptErrorResultIsReportedAndStillRefreshed() throws Exception {
      SelectionListVSAssembly list = selectionList("Filter", "East", "West");
      list.setScriptSelectedValues(new Object[] { "East" });
      vs.addAssembly(list);
      ScriptExecResult failed = new ScriptExecResult(
         false, null, new ScriptError("boom", null, null), null, false, null, null,
         null);

      assertSame(failed, service.runAndRefresh(rvs, user, "", () -> failed));

      verify(refreshService).refreshViewsheet(any(), any(), any(), any(), any());
      assertNull(list.getScriptSelectedValues());
   }

   @Test
   void aThrownRunIsRethrownAndStillRefreshed() throws Exception {
      IllegalStateException boom = new IllegalStateException("boom");

      IllegalStateException thrown = assertThrows(IllegalStateException.class,
         () -> service.runAndRefresh(rvs, user, "", () -> { throw boom; }));

      assertSame(boom, thrown);
      verify(refreshService).refreshViewsheet(any(), any(), any(), any(), any());
   }

   @Test
   void aRefreshFailureNeverMasksTheScriptError() throws Exception {
      doThrow(new IllegalStateException("refresh failed")).when(refreshService)
         .refreshViewsheet(any(), any(), any(), any(), any());
      ScriptExecResult failed = new ScriptExecResult(
         false, null, new ScriptError("boom", null, null), null, false, null, null,
         null);

      assertSame(failed, service.runAndRefresh(rvs, user, "", () -> failed));

      IllegalStateException script = new IllegalStateException("script");
      IllegalStateException thrown = assertThrows(IllegalStateException.class,
         () -> service.runAndRefresh(rvs, user, "", () -> { throw script; }));
      assertSame(script, thrown);
      assertEquals(1, thrown.getSuppressed().length);
   }

   @Test
   void aRefreshFailureAfterACleanRunStillReturnsTheScriptResult() throws Exception {
      doThrow(new IllegalStateException("refresh failed")).when(refreshService)
         .refreshViewsheet(any(), any(), any(), any(), any());

      assertSame(OK, service.runAndRefresh(rvs, user, "", () -> OK));
   }

   private static SelectionListVSAssembly selectionList(String name, String... values) {
      SelectionListVSAssembly list = new SelectionListVSAssembly(new Viewsheet(), name);
      SelectionList selection = new SelectionList();

      for(String value : values) {
         selection.addSelectionValue(new SelectionValue(value, value));
      }

      list.setSelectionList(selection);
      return list;
   }

   private static boolean selected(SelectionListVSAssembly list, String value) {
      for(SelectionValue val : list.getStateSelectionList().getSelectionValues()) {
         if(value.equals(val.getValue())) {
            return val.isSelected();
         }
      }

      return false;
   }
}
