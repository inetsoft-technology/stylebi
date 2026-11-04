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
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.MessageException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #79993: createBookmarkSandbox sets keepAnnoVis on the live runtime entry around
 * getOriginalBookmark. getOriginalBookmark may now refuse a (Home) export (never-saved sheet),
 * so the flag must be reset on that path too, and a null result must be a named error.
 */
@Tag("core")
class VSExportServiceBookmarkSandboxKeepAnnoVisTest {
   @Test
   void resetsKeepAnnoVisWhenBookmarkLookupThrows() throws Exception {
      RuntimeViewsheet rvs = rvs();
      when(rvs.getOriginalBookmark(anyString(), any()))
         .thenThrow(new MessageException("sentinel79993"));

      Throwable ex = assertThrows(InvocationTargetException.class, () -> invoke(rvs)).getCause();

      assertEquals("sentinel79993", ex.getMessage());
      verify(rvs.getViewsheet().getRuntimeEntry()).setProperty("keepAnnoVis", "true");
      verify(rvs.getViewsheet().getRuntimeEntry()).setProperty("keepAnnoVis", null);
   }

   @Test
   void nullBookmarkIsNamedMessageExceptionAndResetsFlag() throws Exception {
      RuntimeViewsheet rvs = rvs();
      when(rvs.getOriginalBookmark(anyString(), any())).thenReturn(null);

      Throwable ex = assertThrows(InvocationTargetException.class, () -> invoke(rvs)).getCause();

      assertInstanceOf(MessageException.class, ex);
      assertTrue(ex.getMessage().contains("could not be found"), ex.getMessage());
      verify(rvs.getViewsheet().getRuntimeEntry()).setProperty("keepAnnoVis", null);
   }

   private static RuntimeViewsheet rvs() {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getRuntimeEntry()).thenReturn(mock(AssetEntry.class));
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getEntry()).thenReturn(mock(AssetEntry.class));
      return rvs;
   }

   private static void invoke(RuntimeViewsheet rvs) throws Exception {
      Method method = VSExportService.class.getDeclaredMethod(
         "createBookmarkSandbox", RuntimeViewsheet.class, String.class, int.class,
         java.security.Principal.class, ViewsheetSandbox.class, java.util.List.class);
      method.setAccessible(true);
      method.invoke(new VSExportService(null, null, null, null, null, null), rvs, "(Home)", 0, null, mock(ViewsheetSandbox.class),
                    new ArrayList<ViewsheetSandbox>());
   }
}
