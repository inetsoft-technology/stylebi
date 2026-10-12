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
import inetsoft.sree.security.OrganizationManager;
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
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77621: emailing bookmarks as a print-layout PDF disposed each bookmark sandbox before
 * the PDF exporter's write(), which paints the queued reports. A table highlight whose
 * JavaScript condition value reads viewsheet data (TableV.table[1][0]) then ran against a
 * disposed sandbox, the script failed, and the highlight was lost. Uses real bookmark
 * sandboxes and the real PDF exporter.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  PluginsTestConfiguration.class,
                                  PrintLayoutHighlightFixture.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VSEmailServicePrintLayoutHighlightTest {
   @Test
   void bookmarkHighlightReadingViewsheetDataIsPaintedInWrite(@TempDir Path dir)
      throws Exception
   {
      List<Integer> counts = new ArrayList<>();
      VSEmailService service = new VSEmailService(cacheIn(dir)) {
         @Override
         protected Mailer createMailer() {
            throw new IllegalStateException("stop77621");
         }
      };

      ViewsheetSandbox liveBox = mock(ViewsheetSandbox.class);
      when(liveBox.getVariableTable()).thenReturn(new VariableTable());
      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "test/Bug77621", null,
         OrganizationManager.getInstance().getCurrentOrgID());
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(mock(Viewsheet.class));
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(liveBox));
      when(rvs.getEntry()).thenReturn(entry);
      when(rvs.getOriginalBookmark(anyString()))
         .thenAnswer(inv -> PrintLayoutHighlightFixture.viewsheet());

      try(MockedStatic<SUtil> sutil = mockStatic(SUtil.class, CALLS_REAL_METHODS);
          MockedStatic<PortalThemesManager> themes =
             mockStatic(PortalThemesManager.class, CALLS_REAL_METHODS);
          MockedStatic<AbstractVSExporter> exporters =
             mockStatic(AbstractVSExporter.class, CALLS_REAL_METHODS))
      {
         sutil.when(() -> SUtil.localize(anyString(), any(), anyBoolean(), any()))
            .thenReturn("vs77621");
         themes.when(PortalThemesManager::getColorTheme).thenReturn(null);
         exporters.when(() -> AbstractVSExporter.getVSExporter(
               anyInt(), any(), any(), anyBoolean(), any()))
            .thenAnswer(inv -> PrintLayoutHighlightFixture.recordHighlightsAtWrite(
               (VSExporter) inv.callRealMethod(), counts));

         // the mailer stops the email right after the export
         IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            service.emailViewsheet(rvs, FileFormatInfo.EXPORT_TYPE_PDF,
                                   new String[] { "b1", "b2" }, false, false, false, "a@b.c",
                                   null, null, "s", "b", false, null, null));
         assertEquals("stop77621", ex.getMessage());
      }

      int expected = PrintLayoutHighlightFixture.expectedHighlightedRows();
      assertEquals(List.of(expected, expected), counts,
                   "highlighted rows of each bookmark's print-layout table in write()");
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

      return fs;
   }
}
