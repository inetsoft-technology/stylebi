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
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.io.viewsheet.VSExporter;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.storage.ExternalStorageService;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.web.viewsheet.service.PrintLayoutHighlightFixture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77621: a scheduled print-layout PDF (email or save to server) disposed each bookmark
 * sandbox before the PDF exporter's write(), which paints the queued reports. A table highlight
 * whose JavaScript condition value reads viewsheet data (TableV.table[1][0]) then ran against a
 * disposed sandbox, the script failed, and the highlight was lost. Uses real bookmark sandboxes
 * and the real PDF exporter.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  PluginsTestConfiguration.class,
                                  PrintLayoutHighlightFixture.TestConfig.class,
                                  ViewsheetActionPrintLayoutHighlightTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ViewsheetActionPrintLayoutHighlightTest {
   @Configuration
   static class TestConfig {
      @Bean
      public ExternalStorageService externalStorageService() {
         return mock(ExternalStorageService.class);
      }
   }

   @ParameterizedTest
   @ValueSource(booleans = { false, true })
   void bookmarkHighlightReadingViewsheetDataIsPaintedInWrite(boolean save, @TempDir Path dir)
      throws Throwable
   {
      List<Integer> counts = new ArrayList<>();
      ViewsheetAction action = action(save, dir);
      doAnswer(inv -> PrintLayoutHighlightFixture.recordHighlightsAtWrite(
         (VSExporter) inv.callRealMethod(), counts))
         .when(action).getVSExporter(eq(FileFormatInfo.EXPORT_TYPE_PDF),
                                     any(OutputStream.class), any());

      VSBookmarkInfo[] infos = { bookmark("b1"), bookmark("b2") };

      try(MockedStatic<VSUtil> vsUtil = mockStatic(VSUtil.class, CALLS_REAL_METHODS);
          MockedConstruction<DefaultScheduleMailService> mail =
             mockConstruction(DefaultScheduleMailService.class))
      {
         vsUtil.when(() -> VSUtil.getBookmarks(any(AssetEntry.class), any())).thenReturn(infos);
         Method method = ViewsheetAction.class.getDeclaredMethod(
            "runViewsheetAction", Principal.class, ScheduleViewsheetService.class);
         method.setAccessible(true);

         try {
            method.invoke(action, new XPrincipal(OWNER), mock(ScheduleViewsheetService.class));
         }
         catch(InvocationTargetException ex) {
            throw ex.getCause();
         }
      }

      int expected = PrintLayoutHighlightFixture.expectedHighlightedRows();
      assertEquals(List.of(expected, expected), counts,
                   "highlighted rows of each bookmark's print-layout table in write()");
   }

   private static ViewsheetAction action(boolean save, Path dir) throws Throwable {
      ViewsheetAction action = spy(new ViewsheetAction());
      action.setBookmarks(new String[] { "b1", "b2" });
      action.setBookmarkTypes(new int[] { VSBookmarkInfo.ALLSHARE, VSBookmarkInfo.ALLSHARE });
      action.setBookmarkUsers(new IdentityID[] { OWNER, OWNER });

      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "test/Bug77621", null,
         OrganizationManager.getInstance().getCurrentOrgID());
      doReturn(entry).when(action).buildAssetEntry(any());
      doReturn(entry).when(action).getViewsheetEntry();

      AssetQuerySandbox liveAbox = mock(AssetQuerySandbox.class);
      when(liveAbox.getVariableTable()).thenReturn(new VariableTable());
      ViewsheetSandbox liveBox = mock(ViewsheetSandbox.class);
      when(liveBox.getVariableTable()).thenReturn(new VariableTable());
      when(liveBox.getAssetQuerySandbox()).thenReturn(liveAbox);
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssemblies()).thenReturn(new Assembly[0]);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getID()).thenReturn("rvs77621");
      when(rvs.getEntry()).thenReturn(entry);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(liveBox));
      when(rvs.getOriginalBookmark(anyString(), any()))
         .thenAnswer(inv -> PrintLayoutHighlightFixture.viewsheet());
      doReturn(rvs).when(action).getRuntimeViewsheet(any(), any());

      if(save) {
         doReturn("").when(action).getScheduleEmails(any());
         action.setFilePath(FileFormatInfo.EXPORT_TYPE_PDF, new ServerPathInfo(
            dir.resolve("out77621").toString().replace('\\', '/')));
      }
      else {
         doReturn("a@b.c").when(action).getScheduleEmails(any());
         doReturn(FileFormatInfo.EXPORT_TYPE_PDF).when(action).getFileType();
      }

      return action;
   }

   private static VSBookmarkInfo bookmark(String name) {
      VSBookmarkInfo bookmark = mock(VSBookmarkInfo.class);
      when(bookmark.getName()).thenReturn(name);
      when(bookmark.getType()).thenReturn(VSBookmarkInfo.ALLSHARE);
      when(bookmark.getOwner()).thenReturn(OWNER);
      return bookmark;
   }

   private static final IdentityID OWNER = new IdentityID("admin", "host-org");
}
