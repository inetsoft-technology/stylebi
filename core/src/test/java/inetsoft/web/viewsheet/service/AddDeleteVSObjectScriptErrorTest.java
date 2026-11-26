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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetService;
import inetsoft.report.composition.execution.*;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.SelectionTreeVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.util.script.ScriptException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #82107: a selection tree whose script fails must not abort
 * {@code CoreLifecycleService.addDeleteVSObject} (and with it the whole viewsheet refresh).
 * The failure is recorded in the request's asset exceptions and the method goes on, while a
 * stopped script still propagates.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AddDeleteVSObjectScriptErrorTest {
   @BeforeEach
   void setUp() {
      oldExceptions = WorksheetService.ASSET_EXCEPTIONS.get();
      queryFactory = mockStatic(VSAQuery.class);
   }

   @AfterEach
   void tearDown() {
      queryFactory.close();

      if(oldExceptions == null) {
         WorksheetService.ASSET_EXCEPTIONS.remove();
      }
      else {
         WorksheetService.ASSET_EXCEPTIONS.set(oldExceptions);
      }
   }

   @Test
   void failingSelectionTreeScriptIsRecordedAndRefreshGoesOn() throws Exception {
      List<Exception> exs = new ArrayList<>();
      WorksheetService.ASSET_EXCEPTIONS.set(exs);
      ScriptException ex = new ScriptException(
         "Script execution error in assembly: SelectionTree1");

      Throwable thrown = addDeleteVSObject(ex);

      assertSame(REACHED_MODEL, thrown,
                 "addDeleteVSObject must go on past the selection tree's view refresh");
      assertEquals(List.of(ex), exs);
   }

   @Test
   void stoppedSelectionTreeScriptPropagates() throws Exception {
      List<Exception> exs = new ArrayList<>();
      WorksheetService.ASSET_EXCEPTIONS.set(exs);
      ScriptException ex = new ScriptException("Script timed out");
      ex.setStopped(true);

      assertSame(ex, addDeleteVSObject(ex));
      assertTrue(exs.isEmpty());
   }

   private Throwable addDeleteVSObject(ScriptException failure) throws Exception {
      CoreLifecycleService service =
         mock(CoreLifecycleService.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      // getSandbox() resolves the assembly's sandbox through the box
      when(box.getSandboxForAssembly(anyString())).thenReturn(box);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));

      SelectionTreeVSAssembly tree = mock(SelectionTreeVSAssembly.class);
      when(tree.getAbsoluteName()).thenReturn("SelectionTree1");
      // the first step after the view refresh; stop here instead of building the model
      when(tree.clone()).thenThrow(REACHED_MODEL);

      SelectionTreeVSAQuery query = mock(SelectionTreeVSAQuery.class);
      doThrow(failure).when(query).refreshViewSelectionValue();
      queryFactory.when(() -> VSAQuery.createVSAQuery(same(box), any(VSAssembly.class), anyInt()))
         .thenReturn(query);

      try {
         service.addDeleteVSObject(rvs, tree, mock(CommandDispatcher.class), false);
         return null;
      }
      catch(Throwable ex) {
         return ex;
      }
      finally {
         verify(query).refreshViewSelectionValue();
      }
   }

   private static final RuntimeException REACHED_MODEL = new IllegalStateException("reached model");
   private List<Exception> oldExceptions;
   private MockedStatic<VSAQuery> queryFactory;
}
