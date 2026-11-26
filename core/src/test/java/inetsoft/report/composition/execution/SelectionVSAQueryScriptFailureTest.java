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
package inetsoft.report.composition.execution;

import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.util.script.ScriptException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #82105/#82107: the selection queries sort and format their values after the assembly
 * script even when the script fails with an ordinary error, then rethrow that error; a
 * stopped script ends the refresh at once. Covers the list, the column-mode tree and the
 * ID-mode tree ({@link SelectionTreeVSAQuery2}).
 */
@Tag("core")
class SelectionVSAQueryScriptFailureTest {
   @ParameterizedTest
   @ValueSource(classes = { SelectionListVSAQuery.class, SelectionTreeVSAQuery.class,
                            SelectionTreeVSAQuery2.class })
   void failingScriptStillSortsAndFormats(Class<? extends AbstractSelectionVSAQuery> type)
      throws Exception
   {
      ScriptException failure = new ScriptException("Script execution error in assembly: A1");
      AtomicInteger formatted = new AtomicInteger();
      AbstractSelectionVSAQuery query = query(type, failure, formatted);

      ScriptException thrown =
         assertThrows(ScriptException.class, query::refreshViewSelectionValue0);

      assertSame(failure, thrown, "the script error is still reported");
      assertEquals(1, formatted.get(), "the values are sorted and formatted");
   }

   @ParameterizedTest
   @ValueSource(classes = { SelectionListVSAQuery.class, SelectionTreeVSAQuery.class,
                            SelectionTreeVSAQuery2.class })
   void formatFailureIsSuppressedIntoTheScriptError(
      Class<? extends AbstractSelectionVSAQuery> type) throws Exception
   {
      ScriptException failure = new ScriptException("Script execution error in assembly: A1");
      AbstractSelectionVSAQuery query = query(type, failure, null);
      IllegalStateException formatError = new IllegalStateException("format");
      doThrow(formatError).when(query).refreshViewSortAndFormat();

      ScriptException thrown =
         assertThrows(ScriptException.class, query::refreshViewSelectionValue0);

      assertSame(failure, thrown);
      assertArrayEquals(new Throwable[] { formatError }, thrown.getSuppressed());
   }

   @ParameterizedTest
   @ValueSource(classes = { SelectionListVSAQuery.class, SelectionTreeVSAQuery.class,
                            SelectionTreeVSAQuery2.class })
   void stoppedScriptSkipsSortAndFormat(Class<? extends AbstractSelectionVSAQuery> type)
      throws Exception
   {
      ScriptException failure = new ScriptException("Script timed out");
      failure.setStopped(true);
      AtomicInteger formatted = new AtomicInteger();
      AbstractSelectionVSAQuery query = query(type, failure, formatted);

      ScriptException thrown =
         assertThrows(ScriptException.class, query::refreshViewSelectionValue0);

      assertSame(failure, thrown);
      assertEquals(0, formatted.get());
   }

   @ParameterizedTest
   @ValueSource(classes = { SelectionListVSAQuery.class, SelectionTreeVSAQuery.class,
                            SelectionTreeVSAQuery2.class })
   void successfulScriptSortsAndFormats(Class<? extends AbstractSelectionVSAQuery> type)
      throws Exception
   {
      AtomicInteger formatted = new AtomicInteger();
      AbstractSelectionVSAQuery query = query(type, null, formatted);

      assertDoesNotThrow(query::refreshViewSelectionValue0);
      assertEquals(1, formatted.get());
   }

   private static AbstractSelectionVSAQuery query(Class<? extends AbstractSelectionVSAQuery> type,
                                                  ScriptException failure,
                                                  AtomicInteger formatted) throws Exception
   {
      AbstractSelectionVSAQuery query = mock(type, CALLS_REAL_METHODS);
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      VSAssembly assembly = mock(VSAssembly.class);
      query.box = box;
      doReturn(assembly).when(query).getAssembly();

      if(failure != null) {
         when(box.executeScript(assembly)).thenThrow(failure);
         // the refresh runs the script through executeScriptForExport (#76765), which is
         // executeScript outside of an export
         when(box.executeScriptForExport(assembly)).thenThrow(failure);
      }

      if(formatted != null) {
         doAnswer(inv -> formatted.incrementAndGet()).when(query).refreshViewSortAndFormat();
      }

      return query;
   }
}
