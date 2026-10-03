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

package inetsoft.sree.schedule;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.io.viewsheet.VSExporter;
import inetsoft.report.script.viewsheet.ViewsheetScope;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.VSBookmarkInfo;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77590: ViewsheetAction creates a per-bookmark ViewsheetSandbox in exportBookmarks() and
 * checkAlerts() and disposed it only on the straight-line success path. A throw from the export
 * (or any earlier step after the sandbox is constructed), or checkAlerts()'s
 * {@code executeViewsheet(...) -> continue}, skipped dispose(), which skips the sandbox's
 * QueryManager.cancel()/AssetDataCache cancellation.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ViewsheetActionSandboxDisposeTest {
   @Test
   void exportBookmarks_disposesSandboxWhenExportThrows() throws Exception {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getOriginalBookmark(anyString(), any())).thenReturn(mock(Viewsheet.class));
      VSExporter exporter = mock(VSExporter.class);
      doThrow(new IllegalStateException("sentinel77590"))
         .when(exporter).export(any(ViewsheetSandbox.class), anyString(), any());
      VSBookmarkInfo[] bookmarks = { bookmark("b1"), bookmark("b2") };

      try(MockedConstruction<ViewsheetSandbox> boxes = mockConstruction(ViewsheetSandbox.class)) {
         Method method = ViewsheetAction.class.getDeclaredMethod(
            "exportBookmarksAndWrite", VSExporter.class, RuntimeViewsheet.class,
            VariableTable.class, VSBookmarkInfo[].class, List.class);
         method.setAccessible(true);

         Throwable ex = invokeAndUnwrap(
            () -> method.invoke(new ViewsheetAction(), exporter, rvs, new VariableTable(),
                                bookmarks, null));
         assertInstanceOf(IllegalStateException.class, ex);
         assertEquals("sentinel77590", ex.getMessage());

         assertEquals(1, boxes.constructed().size(),
                      "export must fail on the first bookmark, before a second sandbox is created");
         verify(boxes.constructed().get(0)).dispose();
      }
   }

   @Test
   void checkAlerts_disposesSandboxWhenAlertLookupThrows() throws Throwable {
      ViewsheetAction action = alertAction(new Assembly[0]);

      try(MockedConstruction<ViewsheetSandbox> boxes = mockConstruction(ViewsheetSandbox.class)) {
         Throwable ex = invokeAndUnwrap(() -> invokeCheckAlerts(action));
         assertTrue(ex.getMessage().startsWith("Did not find alert highlight"), ex.getMessage());

         assertEquals(1, boxes.constructed().size());
         verify(boxes.constructed().get(0)).dispose();
      }
   }

   @Test
   void checkAlerts_disposesSandboxWhenViewsheetScriptSkipsBookmark() throws Throwable {
      // a script-enabled assembly with a sandbox that is not a schedule action makes
      // executeViewsheet() return true, and checkAlerts() continues to the next bookmark
      VSAssemblyInfo info = mock(VSAssemblyInfo.class);
      when(info.isScriptEnabled()).thenReturn(true);
      when(info.getScript()).thenReturn("x = 1;");
      Assembly assembly = mock(Assembly.class);
      when(assembly.getName()).thenReturn("Text1");
      when(assembly.getInfo()).thenReturn(info);
      ViewsheetAction action = alertAction(new Assembly[] { assembly });

      try(MockedConstruction<ViewsheetSandbox> boxes = mockConstruction(
         ViewsheetSandbox.class,
         (box, context) -> when(box.getScope()).thenReturn(mock(ViewsheetScope.class))))
      {
         assertEquals(List.of(), invokeCheckAlerts(action));

         assertEquals(1, boxes.constructed().size());
         verify(boxes.constructed().get(0)).dispose();
      }
   }

   private static ViewsheetAction alertAction(Assembly[] assemblies) throws Throwable {
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.clone()).thenReturn(vs);
      when(vs.getAssemblies()).thenReturn(assemblies);
      when(vs.getAssemblies(true)).thenReturn(assemblies);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getID()).thenReturn("rvs77590");
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.empty());
      when(rvs.getOriginalBookmark(anyString())).thenReturn(mock(Viewsheet.class));

      ScheduleAlert alert = new ScheduleAlert();
      alert.setElementId("Missing1");
      alert.setHighlightName("highlight1");

      ViewsheetAction action = spy(new ViewsheetAction());
      action.setAlerts(new ScheduleAlert[] { alert });
      doReturn(rvs).when(action).getRuntimeViewsheet(any(), any());
      doReturn(null).when(action).getViewsheetEntry();
      doNothing().when(action).closeViewsheet(any(), any(), any());
      return action;
   }

   @SuppressWarnings("unchecked")
   private static List<String> invokeCheckAlerts(ViewsheetAction action) throws Exception {
      Method method = ViewsheetAction.class.getDeclaredMethod(
         "checkAlerts", VSBookmarkInfo[].class, java.security.Principal.class,
         ScheduleViewsheetService.class);
      method.setAccessible(true);
      return (List<String>) method.invoke(
         action, new VSBookmarkInfo[] { bookmark("b1") }, null,
         mock(ScheduleViewsheetService.class));
   }

   private static Throwable invokeAndUnwrap(ThrowingCall call) {
      InvocationTargetException ex = assertThrows(InvocationTargetException.class, call::run);
      return ex.getCause();
   }

   private static VSBookmarkInfo bookmark(String name) {
      VSBookmarkInfo bookmark = mock(VSBookmarkInfo.class);
      when(bookmark.getName()).thenReturn(name);
      return bookmark;
   }

   @FunctionalInterface
   private interface ThrowingCall {
      void run() throws Exception;
   }
}
