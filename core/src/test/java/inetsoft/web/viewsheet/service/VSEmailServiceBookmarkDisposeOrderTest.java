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
import inetsoft.report.io.viewsheet.AbstractVSExporter;
import inetsoft.report.io.viewsheet.VSExporter;
import inetsoft.sree.internal.Mailer;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.FileFormatInfo;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.FileSystemService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77621: exportViewsheet() disposed each bookmark sandbox right after its export(), before
 * exporter.write(). A print-layout PDF paints in write(), and its table highlights evaluate
 * script condition values against the bookmark sandbox, so the sandboxes must be disposed only
 * after write(), and still be disposed when export() or write() throws.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VSEmailServiceBookmarkDisposeOrderTest {
   @Test
   void disposesEveryBookmarkSandboxAfterWrite(@TempDir Path dir) throws Exception {
      VSExporter exporter = mock(VSExporter.class);
      List<ViewsheetSandbox> boxes = new ArrayList<>();

      // the mailer stops the email right after the export
      assertEquals("stop77621", email(dir, exporter, boxes).getMessage());

      assertEquals(2, boxes.size());
      assertWrittenBeforeDisposed(exporter, boxes);
   }

   @Test
   void writeThrows_stillDisposesEveryBookmarkSandbox(@TempDir Path dir) throws Exception {
      VSExporter exporter = mock(VSExporter.class);
      doThrow(new IllegalStateException("sentinel77621")).when(exporter).write();
      List<ViewsheetSandbox> boxes = new ArrayList<>();

      assertEquals("sentinel77621", email(dir, exporter, boxes).getMessage());

      assertEquals(2, boxes.size());
      assertWrittenBeforeDisposed(exporter, boxes);
   }

   @Test
   void exportThrows_disposesSandboxesCreatedSoFar(@TempDir Path dir) throws Exception {
      VSExporter exporter = mock(VSExporter.class);
      doThrow(new IllegalStateException("sentinel77621"))
         .when(exporter).export(any(ViewsheetSandbox.class), eq("b2"), eq(2), any());
      List<ViewsheetSandbox> boxes = new ArrayList<>();

      assertEquals("sentinel77621", email(dir, exporter, boxes).getMessage());

      assertEquals(2, boxes.size());
      verify(exporter, never()).write();

      for(ViewsheetSandbox box : boxes) {
         verify(box, times(1)).dispose();
      }
   }

   private static void assertWrittenBeforeDisposed(VSExporter exporter,
                                                   List<ViewsheetSandbox> boxes)
      throws Exception
   {
      for(ViewsheetSandbox box : boxes) {
         InOrder order = inOrder(exporter, box);
         order.verify(exporter).export(same(box), anyString(), anyInt(), any());
         order.verify(exporter).write();
         order.verify(box).dispose();
         verify(box, times(1)).dispose();
      }
   }

   private static Exception email(Path dir, VSExporter exporter, List<ViewsheetSandbox> boxes)
      throws Exception
   {
      VSEmailService service = new VSEmailService(cacheIn(dir)) {
         @Override
         protected ViewsheetSandbox createSandbox(Viewsheet bookmark, int mode,
                                                  Principal principal, AssetEntry entry,
                                                  VariableTable vars)
         {
            ViewsheetSandbox box = mock(ViewsheetSandbox.class);
            boxes.add(box);
            return box;
         }

         @Override
         protected Mailer createMailer() {
            throw new IllegalStateException("stop77621");
         }
      };

      ViewsheetSandbox liveBox = mock(ViewsheetSandbox.class);
      when(liveBox.getVariableTable()).thenReturn(new VariableTable());
      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "test/Bug77621", null);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(mock(Viewsheet.class));
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(liveBox));
      when(rvs.getEntry()).thenReturn(entry);
      when(rvs.getOriginalBookmark(anyString())).thenAnswer(inv -> mock(Viewsheet.class));

      try(MockedStatic<SUtil> sutil = mockStatic(SUtil.class, CALLS_REAL_METHODS);
          MockedStatic<PortalThemesManager> themes = mockStatic(PortalThemesManager.class);
          MockedStatic<AbstractVSExporter> exporters =
             mockStatic(AbstractVSExporter.class, CALLS_REAL_METHODS))
      {
         sutil.when(() -> SUtil.localize(anyString(), any(), anyBoolean(), any()))
            .thenReturn("vs77621");
         themes.when(PortalThemesManager::getColorTheme).thenReturn(null);
         exporters.when(() -> AbstractVSExporter.getVSExporter(
               anyInt(), any(), any(), anyBoolean(), any()))
            .thenReturn(exporter);

         return assertThrows(IllegalStateException.class, () ->
            service.emailViewsheet(rvs, FileFormatInfo.EXPORT_TYPE_PDF,
                                   new String[] { "b1", "b2" }, false, false, false, "a@b.c",
                                   null, null, "s", "b", false, null, null));
      }
   }

   private static FileSystemService cacheIn(Path dir) {
      FileSystemService fs = mock(FileSystemService.class);
      when(fs.getCacheFile(anyString()))
         .thenAnswer(inv -> dir.resolve((String) inv.getArgument(0)).toFile());

      try {
         when(fs.getCacheDirectory()).thenReturn(dir.toString());
      }
      catch(IOException e) {
         throw new RuntimeException(e);
      }

      when(fs.getFile(anyString())).thenAnswer(inv -> new File((String) inv.getArgument(0)));
      return fs;
   }
}
