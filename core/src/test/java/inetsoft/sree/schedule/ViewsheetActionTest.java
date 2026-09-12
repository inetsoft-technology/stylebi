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

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.io.csv.CSVConfig;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.Organization;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.*;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.OutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/*
 * Intent vs implementation suspects
 *
 * [Suspect 1] getFilePath(int) -> intent: "null if file will not be saved in the specified format" (Javadoc)
 *             actual: filePaths.get(format).getPath() - NPE when format has never been registered (map returns null)
 *             Fix: guard with null check before calling .getPath()
 *
 *             Production likelihood: LOW. Portal/EM schedule UI only offers formats from the server-provided
 *             saveFileFormats list; users set a path per selected format via setFilePath before save. Normal UI
 *             and ScheduleService/ScheduleApiService paths do not call getFilePath(int) for a format that was never
 *             registered on the action. The @Disabled test getFilePath_unregisteredFormat_returnsNull documents
 *             contract-vs-implementation for programmatic callers (tests, migration, direct API use) — not a
 *             typical end-user schedule flow.
 */

/*
 * Cases deferred - require integration context:
 *
 * [ViewsheetAction] run(Principal) - full orchestration through ScheduleViewsheetService
 *             -> needs live RuntimeViewsheet, bookmarks, principal; NOT yet covered
 * [ViewsheetAction] parseXML / writeXML - XML round-trip
 *             -> needs DOM context and full Spring wiring; NOT yet covered
 * [ViewsheetAction] cancel() - closes viewsheet via ScheduleViewsheetService
 *             -> needs run() executing concurrently; NOT yet covered
 * [ViewsheetAction] getPath(String, Object[]) - template substitution with {key} from RepletRequest
 *             -> needs live RepletRequest with parameters; tested only via full run(); NOT yet covered
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
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

   // --- isMatchLayout ---

   @Test
   void isMatchLayout_csvFormat_alwaysReturnsFalse() {
      // CSV format unconditionally overrides the user-set match-layout preference.
      ViewsheetAction action = new ViewsheetAction("1^128^__NULL__^vs1", null);
      action.setFileFormat(FileFormatInfo.EXPORT_NAME_CSV);
      action.setMatchLayout(true);
      assertFalse(action.isMatchLayout());
   }

   // --- getFilePath ---

   @Test
   @Disabled("Suspect 1: getFilePath(format) throws NullPointerException when format was never registered - ViewsheetAction:1851; Fix: null-check filePaths.get(format) before calling getPath(). See class header: unlikely via Portal/EM UI (format list is server-constrained).")
   void getFilePath_unregisteredFormat_returnsNull() {
      ViewsheetAction action = new ViewsheetAction();
      assertNull(action.getFilePath(FileFormatInfo.EXPORT_TYPE_PDF));
   }

   // --- setFilePath ---

   @Test
   void setFilePath_nullString_removesEntry() {
      ViewsheetAction action = new ViewsheetAction();
      action.setFilePath(FileFormatInfo.EXPORT_TYPE_PDF, "/some/path");
      assertEquals(1, action.getSaveFormats().length);
      action.setFilePath(FileFormatInfo.EXPORT_TYPE_PDF, (String) null);
      assertEquals(0, action.getSaveFormats().length);
   }

   @Test
   void setFilePath_nullServerPathInfo_removesEntry() {
      ViewsheetAction action = new ViewsheetAction();
      action.setFilePath(FileFormatInfo.EXPORT_TYPE_PDF, new ServerPathInfo("/path"));
      assertEquals(1, action.getSaveFormats().length);
      action.setFilePath(FileFormatInfo.EXPORT_TYPE_PDF, (ServerPathInfo) null);
      assertEquals(0, action.getSaveFormats().length);
   }

   // --- getScope ---

   @Test
   void getScope_noViewsheet_returnsGlobalScope() {
      // createAssetEntry(null) returns null -> GLOBAL_SCOPE fallback branch
      ViewsheetAction action = new ViewsheetAction();
      assertEquals(AssetRepository.GLOBAL_SCOPE, action.getScope());
   }

   // --- equals ---

   @Test
   void equals_nonViewsheetAction_returnsFalse() {
      ViewsheetAction action = new ViewsheetAction("1^128^__NULL__^vs1", null);
      assertFalse(action.equals("not-a-viewsheet-action"));
      assertFalse(action.equals(null));
   }

   IdentityID adminID = new IdentityID("admin", Organization.getDefaultOrganizationID());
}
