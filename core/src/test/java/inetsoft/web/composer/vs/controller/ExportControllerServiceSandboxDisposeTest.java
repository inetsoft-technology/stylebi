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

package inetsoft.web.composer.vs.controller;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.io.viewsheet.VSExporter;
import inetsoft.report.script.viewsheet.ViewsheetScope;
import inetsoft.test.*;
import inetsoft.uql.asset.AbstractSheet;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.MockedConstruction;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77609: writeViewsheetExport() created a sandbox per bookmark (and one for a design-mode
 * print-layout preview of the current view) but disposed only the last bookmark's, and none
 * when the export or write threw. Every sandbox the method creates must be disposed exactly
 * once, and only after exporter.write(), because a print-layout PDF renders the sandboxes'
 * table lenses in write(). The runtime viewsheet's own sandbox must never be disposed.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ExportControllerServiceSandboxDisposeTest {
   @BeforeEach
   void setUp() {
      rbox = mock(ViewsheetSandbox.class);
      exporter = mock(VSExporter.class);
      rvs = mock(RuntimeViewsheet.class);
      Viewsheet viewsheet = viewsheet();
      when(viewsheet.getRuntimeEntry()).thenReturn(mock(AssetEntry.class));
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(rbox));
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(rvs.getEntry()).thenReturn(mock(AssetEntry.class));
      when(rvs.getOriginalBookmark(anyString(), any())).thenAnswer(inv -> viewsheet());
   }

   @Test
   void disposesEveryBookmarkSandboxAfterWrite() throws Exception {
      try(MockedConstruction<ViewsheetSandbox> boxes = mockSandboxes()) {
         invoke(false, false, "b1", "b2", "b3");

         assertEquals(3, boxes.constructed().size());
         assertDisposedOnceAfterWrite(boxes);
      }
   }

   @Test
   void disposesEveryBookmarkSandboxWhenExportThrows() throws Exception {
      doNothing().doThrow(new IllegalStateException("sentinel77609"))
         .when(exporter).export(any(ViewsheetSandbox.class), anyString(), anyInt(), any());

      try(MockedConstruction<ViewsheetSandbox> boxes = mockSandboxes()) {
         Throwable ex = assertThrows(InvocationTargetException.class,
                                     () -> invoke(false, false, "b1", "b2", "b3")).getCause();
         assertEquals("sentinel77609", ex.getMessage());

         assertEquals(2, boxes.constructed().size());
         verify(exporter, never()).write();

         for(ViewsheetSandbox box : boxes.constructed()) {
            verify(box).dispose();
         }

         verify(rbox, never()).dispose();
      }
   }

   @Test
   void disposesEveryBookmarkSandboxWhenWriteThrows() throws Exception {
      doThrow(new IllegalStateException("sentinel77609")).when(exporter).write();

      try(MockedConstruction<ViewsheetSandbox> boxes = mockSandboxes()) {
         Throwable ex = assertThrows(InvocationTargetException.class,
                                     () -> invoke(false, false, "b1", "b2", "b3")).getCause();
         assertEquals("sentinel77609", ex.getMessage());

         assertEquals(3, boxes.constructed().size());
         assertDisposedOnceAfterWrite(boxes);
      }
   }

   @Test
   void disposesDesignModePrintLayoutBoxAfterWrite() throws Exception {
      when(rbox.getMode()).thenReturn(AbstractSheet.SHEET_DESIGN_MODE);

      try(MockedConstruction<ViewsheetSandbox> boxes = mockSandboxes()) {
         invoke(true, true, "b1");

         assertEquals(2, boxes.constructed().size(), "export box and one bookmark box");
         verify(exporter).export(same(boxes.constructed().get(0)), anyString(), any());
         assertDisposedOnceAfterWrite(boxes);
      }
   }

   @Test
   void neverDisposesRuntimeSandboxForCurrentView() throws Exception {
      try(MockedConstruction<ViewsheetSandbox> boxes = mockSandboxes()) {
         invoke(true, false);

         assertEquals(0, boxes.constructed().size());
         verify(exporter).export(same(rbox), anyString(), any());
         verify(exporter).write();
         verify(rbox, never()).dispose();
      }
   }

   private void assertDisposedOnceAfterWrite(MockedConstruction<ViewsheetSandbox> boxes)
      throws Exception
   {
      for(ViewsheetSandbox box : boxes.constructed()) {
         InOrder order = inOrder(exporter, box);
         order.verify(exporter).write();
         order.verify(box).dispose();
         verify(box).dispose();
      }

      verify(rbox, never()).dispose();
   }

   private void invoke(boolean current, boolean previewPrintLayout, String... bookmarks)
      throws Exception
   {
      Method method = ExportControllerService.class.getDeclaredMethod(
         "writeViewsheetExport", RuntimeViewsheet.class, VSExporter.class, Principal.class,
         boolean.class, boolean.class, boolean.class, String[].class, boolean.class,
         boolean.class);
      method.setAccessible(true);
      ExportControllerService service = new ExportControllerService(null, null, null);
      method.invoke(service, rvs, exporter, null, false, false, current, bookmarks, false,
                    previewPrintLayout);
   }

   private static MockedConstruction<ViewsheetSandbox> mockSandboxes() {
      return mockConstruction(
         ViewsheetSandbox.class,
         (box, context) -> when(box.getScope()).thenReturn(mock(ViewsheetScope.class)));
   }

   private static Viewsheet viewsheet() {
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getVSAssemblyInfo()).thenReturn(mock(VSAssemblyInfo.class));
      when(vs.getAssemblies()).thenReturn(new Assembly[0]);
      when(vs.getAssemblies(anyBoolean())).thenReturn(new Assembly[0]);
      when(vs.clone()).thenReturn(vs);
      return vs;
   }

   private ViewsheetSandbox rbox;
   private VSExporter exporter;
   private RuntimeViewsheet rvs;
}
