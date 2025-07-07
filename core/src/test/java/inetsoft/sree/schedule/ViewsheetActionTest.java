/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
import inetsoft.report.io.csv.CSVConfig;
import inetsoft.sree.security.*;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.tabular.ViewAlign;
import inetsoft.uql.viewsheet.*;

import inetsoft.util.Tool;
import org.junit.jupiter.api.Test;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 *  Only test some basic set and get methods.
 *  Due to too many dependencies, there is currently no suitable testing approach for the run method. We will handle it later. todo
 *  Perhaps we can use a spy for run method..
 */
public class ViewsheetActionTest {
   ViewsheetAction viewsheetAction;

   /**
    * some set and get functions have test on other testcase, here only check uncovered.
    */
   @Test
   void testSetGetFuinctions() {
      viewsheetAction = new ViewsheetAction("1^128^__NULL__^vs1", null);

      assertNotNull(viewsheetAction.getViewsheetRequest());
      assertEquals(AssetRepository.GLOBAL_SCOPE, viewsheetAction.getScope());

      // check csvconfig
      CSVConfig csvConfig = new CSVConfig(",", "", true, true);
      viewsheetAction.setEmailCSVConfig(csvConfig);
      assertEquals(csvConfig, viewsheetAction.getEmailCSVConfig());

      viewsheetAction.setSaveCSVConfig(csvConfig);
      assertEquals(csvConfig, viewsheetAction.getSaveCSVConfig());

      //check bookmark info
      viewsheetAction.setBookmarks(
         new String[] { VSBookmark.HOME_BOOKMARK, "abk1", "gbk2", "pbk3"});
      viewsheetAction.setBookmarkTypes(
         new int[] { VSBookmarkInfo.ALLSHARE, VSBookmarkInfo.ALLSHARE, VSBookmarkInfo.GROUPSHARE,
                     VSBookmarkInfo.PRIVATE });
      IdentityID[] bookmarkUsers = new IdentityID[] {adminID, adminID, adminID, adminID};
      viewsheetAction.setBookmarkUsers(bookmarkUsers);

      assertEquals(4, viewsheetAction.getBookmarks().length);
      assertArrayEquals(new int[] {1, 1, 2, 0}, viewsheetAction.getBookmarkTypes());
      assertEquals(bookmarkUsers, viewsheetAction.getBookmarkUsers());

      viewsheetAction.clearBookmarks();
      assertNull(viewsheetAction.getBookmarks());

      assertEquals("1^128^__NULL__^vs1", viewsheetAction.getViewsheet());
      ViewsheetAction viewsheetAction2 = (ViewsheetAction) viewsheetAction.clone();
      assertEquals(viewsheetAction2, viewsheetAction);
   }

   @Test
   void testSetGetExportInfo() {
      viewsheetAction = new ViewsheetAction("1^128^__NULL__^vs1", null);
      // check setGet export info
      assertEquals(2, viewsheetAction.getFileType());
      assertEquals("pdf", viewsheetAction.getFileExtend(2));
      assertNotNull(viewsheetAction.getVSExporter(FileFormatInfo.EXPORT_TYPE_PDF, mock(OutputStream.class)));
      viewsheetAction.setMatchLayout(true);
      assertTrue(viewsheetAction.isMatchLayout());
      viewsheetAction.setExpandSelections(true);
      assertTrue(viewsheetAction.isExpandSelections());
      viewsheetAction.setOnlyDataComponents(true);
      assertTrue(viewsheetAction.isOnlyDataComponents());
      viewsheetAction.setExportAllTabbedTables(true);
      assertTrue(viewsheetAction.isExportAllTabbedTables());
      viewsheetAction.setSaveToServerMatch(true);
      assertTrue(viewsheetAction.isSaveToServerMatch());
      viewsheetAction.setSaveToServerExpandSelections(true);
      assertTrue(viewsheetAction.isSaveToServerExpandSelections());
      viewsheetAction.setSaveToServerOnlyDataComponents(true);
      assertTrue(viewsheetAction.isSaveToServerOnlyDataComponents());
      viewsheetAction.setSaveExportAllTabbedTables(true);
      assertTrue(viewsheetAction.isSaveExportAllTabbedTables());
      viewsheetAction.setIsBookmarkReadOnly(true);
      assertTrue(viewsheetAction.isBookmarkReadOnly());

      viewsheetAction.setFilePath(2, "/test/path1");
      assertEquals("/test/path1", viewsheetAction.getFilePath(2));

      viewsheetAction.setFilePath(1,  new ServerPathInfo("/test/path2"));
      assertEquals("/test/path2", viewsheetAction.getFilePathInfo(1).getPath());

      ScheduleAlert[] scheduleAlerts = new ScheduleAlert[] {mock(ScheduleAlert.class)};
      viewsheetAction.setAlerts(scheduleAlerts);
      assertEquals(scheduleAlerts, viewsheetAction.getAlerts());
   }

   @Test
   void testGetScheduleEmail() {
      viewsheetAction = new ViewsheetAction("1^128^__NULL__^vs1", null);

      assertEquals("", viewsheetAction.getScheduleEmails(null));

      ViewsheetSandbox viewsheetSandbox = mock(ViewsheetSandbox.class);
      ScheduleInfo scheduleInfo = mock(ScheduleInfo.class);
      when(viewsheetSandbox.getScheduleInfo()).thenReturn(scheduleInfo);
      when(scheduleInfo.getEmails()).thenReturn("1@1,2@2");
      when(viewsheetSandbox.getScheduleInfo()).thenReturn(null);
      viewsheetAction.setEmails("3@3");

      assertEquals("3@3",
                   viewsheetAction.getScheduleEmails(viewsheetSandbox));

      when(viewsheetSandbox.getScheduleInfo()).thenReturn(scheduleInfo);
      assertEquals("1@1,2@2",
                   viewsheetAction.getScheduleEmails(viewsheetSandbox));
   }

   void testRunWithBK() {
      ViewsheetAction vsActions = spy(ViewsheetAction.class);
      RuntimeViewsheet mockRuntimeViewsheet = spy(RuntimeViewsheet.class);

      try {
         when(vsActions.getRuntimeViewsheet(admin)).thenReturn(mockRuntimeViewsheet);
         Method method = ViewsheetAction.class.getDeclaredMethod("runViewsheetAction", Principal.class);
         method.setAccessible(true);
         //method.invoke(admin);

         System.err.println("========" + method.invoke(admin));

      }catch(Throwable e) {
         fail("Unexpected exception: " + e.getMessage());
      }


   }

   IdentityID adminID = new IdentityID("admin", Organization.getDefaultOrganizationID());
   SRPrincipal admin = new SRPrincipal(new IdentityID("admin", Organization.getDefaultOrganizationID()),
                                       new IdentityID[] { new IdentityID("Administrator", null)},
                                       new String[] {"g0"}, "host-org",
                                       Tool.getSecureRandom().nextLong());
}
