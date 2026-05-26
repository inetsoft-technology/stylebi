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
package inetsoft.web.admin.content.dataspace;

/*
 * Test strategy
 *
 * DataSpaceFileSettingsController has real in-controller logic in two areas:
 *
 *   getFileContent(String, boolean):
 *     - path == null → getEditable() is skipped → content="" editable=false
 *     - path ends with ".db" or ".dat" → probeContentType() returns false immediately
 *       → editable=false, content=""
 *     - editable=false → getContentFromPath() is never called
 *
 *   saveFileContent(DataSpaceFileContentModel):
 *     - converts model.content() to InputStream and calls
 *       dataSpace.withOutputStream(null, model.path(), lambda)
 *     - errors are caught and logged; they do not propagate
 *
 * getModel() calls SreeEnv.getProperty() which requires Spring; covered by E2E tests.
 * apply() calls Catalog / Audit / DataSpace with complex branching; covered by E2E tests.
 * downloadFile() / deleteDataSpaceFile() are pure delegation; covered by E2E tests.
 *
 * Coverage scope:
 *   [null path]           path=null → editable=false, content=""
 *   [binary file path]    path ending ".db" → probeContentType returns false → editable=false
 *   [saveFileContent]     withOutputStream called with correct path; errors caught internally
 */

import inetsoft.util.DataSpace;
import inetsoft.web.admin.content.dataspace.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class DataSpaceFileSettingsControllerTest {

   @Mock private DataSpaceContentSettingsService dataSpaceContentSettingsService;
   @Mock private DataSpace dataSpace;

   private DataSpaceFileSettingsController controller;

   @BeforeEach
   void setUp() {
      controller = new DataSpaceFileSettingsController(dataSpaceContentSettingsService, dataSpace);
   }

   // -------------------------------------------------------------------------
   // getFileContent()
   // -------------------------------------------------------------------------

   // [null path] path=null → getEditable() skipped → editable=false, content=""
   @Test
   void getFileContent_nullPath_returnsEmptyNotEditable() {
      DataSpaceFileContentModel result = controller.getFileContent(null, false);

      assertFalse(result.editable());
      assertEquals("", result.content());
      assertNull(result.path());
   }

   // [binary file path] path ends with ".db" → probeContentType returns false → editable=false
   @Test
   void getFileContent_binaryFilePath_returnsEmptyNotEditable() {
      DataSpaceFileContentModel result = controller.getFileContent("config/data.db", false);

      assertFalse(result.editable());
      assertEquals("", result.content());
   }

   // -------------------------------------------------------------------------
   // saveFileContent()
   // -------------------------------------------------------------------------

   // [write path] content written via dataSpace.withOutputStream; errors caught internally
   @Test
   void saveFileContent_writesContentViaDataSpace() throws Exception {
      DataSpaceFileContentModel model = DataSpaceFileContentModel.builder()
         .content("hello world")
         .path("data/file.txt")
         .build();

      controller.saveFileContent(model);

      verify(dataSpace).withOutputStream(eq(null), eq("data/file.txt"), any());
   }
}
