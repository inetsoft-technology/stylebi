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
package inetsoft.web.admin.content.dataspace;

/*
 * Test strategy (Bug #77388)
 *
 * The EM Data Space apply/upload endpoints must check names on the server:
 *   - a new or changed file/folder name follows the EM client rule (isValidDataSpaceFileName)
 *     and is required, so "a/b", "a\\b" and blank names are rejected before any write;
 *   - a rename onto an existing target is refused with ResourceExistsException instead of
 *     letting DataSpace.rename() overwrite the target;
 *   - an uploaded file name may not contain '/' or '\\' or be ".", ".." or blank.
 * An unchanged name (a content edit, including community mode where newName is the display
 * name) must not be validated or renamed.
 *
 * The controllers run against the real DataSpace of the test home. The content settings
 * service is a mock whose getPath()/getNewPath() call the real code.
 */

import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.graph.aesthetic.ImageShapes;
import inetsoft.util.DataSpace;
import inetsoft.web.admin.content.dataspace.model.*;
import inetsoft.web.admin.upload.UploadService;
import inetsoft.web.admin.upload.UploadedFile;
import inetsoft.web.security.auth.ResourceExistsException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DataSpaceNameValidationTest {
   private DataSpace space;
   private DataSpaceContentSettingsService service;
   private UploadService uploadService;
   private SecurityEngine securityEngine;
   private DataSpaceFileSettingsController fileController;
   private DataSpaceFolderSettingsController folderController;
   private String dir;

   @BeforeEach
   void setUp() throws Exception {
      space = DataSpace.getDataSpace();
      service = mock(DataSpaceContentSettingsService.class);
      when(service.getPath(any(), any())).thenCallRealMethod();
      when(service.getNewPath(any(), any(), any())).thenCallRealMethod();
      when(service.getFileName(any())).thenCallRealMethod();
      uploadService = mock(UploadService.class);
      securityEngine = mock(SecurityEngine.class);
      fileController = spy(new DataSpaceFileSettingsController(service, space));
      // getModel() formats dates from SreeEnv, which is not what is under test here
      doReturn(null).when(fileController).getModel(any(), any());
      folderController = new DataSpaceFolderSettingsController(
         service, mock(DataSpaceFolderSettingsService.class), uploadService, space, securityEngine);

      dir = "b77388-" + UUID.randomUUID();
      space.makeDirectory(dir);
      write(dir, "a.txt", "A");
      write(dir, "b.txt", "B");
      space.makeDirectory(dir + "/f");
      write(dir + "/f", "c.txt", "C");
      space.makeDirectory(dir + "/g");
   }

   @AfterEach
   void tearDown() {
      space.delete(null, dir);
   }

   // ---------------------------------------------------------------------------------------
   // file apply
   // ---------------------------------------------------------------------------------------

   @ParameterizedTest
   @ValueSource(strings = { "sub/a.txt", "bs\\c.txt", "", "  ", "a2.txt.", ".hidden", "a#b.txt" })
   void fileRename_invalidNewName_rejected(String newName) throws Exception {
      assertThrows(IllegalArgumentException.class,
         () -> fileController.apply(fileRename(dir + "/a.txt", "a.txt", newName), null));
      assertEquals("A", read(dir + "/a.txt"));
      assertTrue(space.isDirectory(dir));
      assertFalse(space.exists(null, dir + "/sub"));
      assertFalse(space.exists(null, dir + "/bs"));
   }

   @Test
   void fileRename_existingTarget_throwsResourceExistsAndKeepsBoth() throws Exception {
      assertThrows(ResourceExistsException.class,
         () -> fileController.apply(fileRename(dir + "/a.txt", "a.txt", "b.txt"), null));
      assertEquals("A", read(dir + "/a.txt"));
      assertEquals("B", read(dir + "/b.txt"));
   }

   @Test
   void fileRename_validNewName_renames() throws Exception {
      fileController.apply(fileRename(dir + "/a.txt", "a.txt", "a2.txt"), null);

      assertFalse(space.exists(null, dir + "/a.txt"));
      assertEquals("A", read(dir + "/a2.txt"));
   }

   // community mode: the name field holds the display name, which getNewPath() maps back to
   // the same path, so a content edit of a file whose display name breaks the rule still works
   @Test
   void fileEdit_newNameMapsToSamePath_notValidatedOrRenamed() throws Exception {
      write(dir, "host-org__a,b.txt", "X");
      String path = dir + "/host-org__a,b.txt";
      doReturn(path).when(service).getNewPath(path, "host-org__a,b.txt", "a,b.txt");
      ChangeDataSpaceFileRequest request = ChangeDataSpaceFileRequest.builder()
         .path(path).name("host-org__a,b.txt").newName("a,b.txt").timeZone("UTC").newFile(false)
         .fileData(Base64.getEncoder().encodeToString("Y".getBytes(StandardCharsets.UTF_8)))
         .build();

      fileController.apply(request, null);

      assertEquals("Y", read(path));
   }

   @Test
   void newFile_nameWithSlash_rejected() {
      ChangeDataSpaceFileRequest request = ChangeDataSpaceFileRequest.builder()
         .path(dir).name("n/b.txt").newName("n/b.txt").timeZone("UTC").newFile(true)
         .build();

      assertThrows(IllegalArgumentException.class, () -> fileController.apply(request, null));
      assertFalse(space.exists(null, dir + "/n"));
   }

   // Bug #78206: a `name` that is not the path's own last segment must be refused instead of
   // being spliced in verbatim, which silently moves the item to the wrong parent folder.
   @Test
   void fileRename_nameMismatchedWithPath_rejectedNotMoved() throws Exception {
      assertThrows(IllegalArgumentException.class,
         () -> fileController.apply(fileRename(dir + "/f/c.txt", "b.txt", "z.txt"), null));
      assertEquals("C", read(dir + "/f/c.txt"));
      assertFalse(space.exists(null, dir + "/z.txt"));
      assertFalse(space.exists(null, dir + "/f/z.txt"));
   }

   // Bug #78206: when `name` does not occur in `path` at all, the old code's
   // `path.substring(0, path.lastIndexOf(name))` threw StringIndexOutOfBoundsException
   // (lastIndexOf returns -1). It must instead be a clean IllegalArgumentException.
   @Test
   void fileRename_nameNotInPath_rejectedNotCrashed() throws Exception {
      assertThrows(IllegalArgumentException.class,
         () -> fileController.apply(fileRename(dir + "/a.txt", "NOPE_NOT_IN_PATH", "z.txt"), null));
      assertEquals("A", read(dir + "/a.txt"));
   }

   // ---------------------------------------------------------------------------------------
   // folder apply
   // ---------------------------------------------------------------------------------------

   // Bug #78206: folder rename's own inline splice has the identical bug, fixed independently.
   @Test
   void folderRename_nameMismatchedWithPath_rejectedNotMoved() throws Exception {
      DataSpaceFolderSettingsModel model = DataSpaceFolderSettingsModel.builder()
         .path(dir + "/f").name("g").newName("moved").build();

      assertThrows(IllegalArgumentException.class, () -> folderController.apply(model, null));
      assertEquals("C", read(dir + "/f/c.txt"));
      assertFalse(space.exists(null, dir + "/moved"));
      assertFalse(space.exists(null, dir + "/g/moved"));
   }

   @Test
   void folderRename_nameNotInPath_rejectedNotCrashed() throws Exception {
      DataSpaceFolderSettingsModel model = DataSpaceFolderSettingsModel.builder()
         .path(dir + "/f").name("NOPE_NOT_IN_PATH").newName("moved").build();

      assertThrows(IllegalArgumentException.class, () -> folderController.apply(model, null));
      assertEquals("C", read(dir + "/f/c.txt"));
   }

   // Bug #78206 regression guard: the old buggy code happened to get this right by luck
   // (lastIndexOf finds the rightmost "A"); the fixed getFileName()-based check must too.
   @Test
   void folderRename_duplicateSegmentName_stillRenamesCorrectly() throws Exception {
      space.makeDirectory(dir + "/g/g");
      write(dir + "/g/g", "d.txt", "D");
      DataSpaceFolderSettingsModel model = DataSpaceFolderSettingsModel.builder()
         .path(dir + "/g/g").name("g").newName("renamed").build();

      folderController.apply(model, null);

      assertFalse(space.exists(null, dir + "/g/g"));
      assertEquals("D", read(dir + "/g/renamed/d.txt"));
   }

   @ParameterizedTest
   @ValueSource(strings = { "n/m", "n\\m", "", " " })
   void newFolder_invalidName_rejected(String newName) {
      DataSpaceFolderSettingsModel model = DataSpaceFolderSettingsModel.builder()
         .path(dir).name("x").newName(newName).newFolder(true).build();

      assertThrows(IllegalArgumentException.class, () -> folderController.apply(model, null));
      assertFalse(space.exists(null, dir + "/n/m"));
      assertEquals(Set.of("a.txt", "b.txt", "f", "g"), Set.of(space.list(dir)));
   }

   @ParameterizedTest
   @ValueSource(strings = { "g/moved", "g\\moved", "" })
   void folderRename_invalidNewName_rejected(String newName) throws Exception {
      DataSpaceFolderSettingsModel model = DataSpaceFolderSettingsModel.builder()
         .path(dir + "/f").name("f").newName(newName).build();

      assertThrows(IllegalArgumentException.class, () -> folderController.apply(model, null));
      assertEquals("C", read(dir + "/f/c.txt"));
      assertFalse(space.exists(null, dir + "/g/moved"));
   }

   @Test
   void folderRename_existingFile_throwsResourceExistsAndKeepsBoth() throws Exception {
      DataSpaceFolderSettingsModel model = DataSpaceFolderSettingsModel.builder()
         .path(dir + "/f").name("f").newName("a.txt").build();

      assertThrows(ResourceExistsException.class, () -> folderController.apply(model, null));
      assertEquals("A", read(dir + "/a.txt"));
      assertEquals("C", read(dir + "/f/c.txt"));
   }

   @Test
   void folderRename_existingFolder_throwsResourceExists() throws Exception {
      DataSpaceFolderSettingsModel model = DataSpaceFolderSettingsModel.builder()
         .path(dir + "/f").name("f").newName("g").build();

      assertThrows(ResourceExistsException.class, () -> folderController.apply(model, null));
      assertEquals("C", read(dir + "/f/c.txt"));
      assertFalse(space.exists(null, dir + "/g/c.txt"));
   }

   // ---------------------------------------------------------------------------------------
   // folder upload
   // ---------------------------------------------------------------------------------------

   @ParameterizedTest
   @ValueSource(strings = { "sub/u.txt", "..\\u.txt", ".", "..", "" })
   void upload_invalidFileName_rejected(String fileName, @TempDir Path tmp) throws Exception {
      File file = Files.writeString(tmp.resolve("u.txt"), "U").toFile();
      when(securityEngine.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      UploadedFile ok = UploadedFile.builder().fileName("ok.txt").file(file).build();
      UploadedFile bad = UploadedFile.builder().fileName(fileName).file(file).build();
      when(uploadService.get("upload-id")).thenReturn(Optional.of(List.of(ok, bad)));
      DataSpaceFolderUploadModel model = DataSpaceFolderUploadModel.builder()
         .path(dir).files("upload-id").build();

      try(MockedStatic<ImageShapes> shapes = mockStatic(ImageShapes.class)) {
         shapes.when(ImageShapes::getGlobalShapesDirectory).thenReturn("portal/shapes");
         shapes.when(ImageShapes::getShapesDirectory).thenReturn("portal/orgA/shapes");

         assertThrows(IllegalArgumentException.class,
            () -> folderController.uploadDataSpaceFiles(model, null, mock(HttpServletRequest.class)));
      }

      // no file of the batch is written
      assertEquals(Set.of("a.txt", "b.txt", "f", "g"), Set.of(space.list(dir)));
      assertTrue(space.isDirectory(dir));
   }

   // ---------------------------------------------------------------------------------------
   // validators
   // ---------------------------------------------------------------------------------------

   @ParameterizedTest
   @ValueSource(strings = { "a.txt", "a b.txt", "a-b_c(1).txt", ".stylereport", "folder" })
   void validateName_validNames_accepted(String name) {
      assertDoesNotThrow(() -> DataSpaceContentSettingsService.validateName(name));
   }

   @ParameterizedTest
   @ValueSource(strings = { ".env", "a,b.csv", "50%.txt", "a#b'c.txt", "a.txt" })
   void validateUploadFileName_osFileNames_accepted(String name) {
      assertDoesNotThrow(() -> DataSpaceContentSettingsService.validateUploadFileName(name));
   }

   @Test
   void validateNames_null_rejected() {
      assertThrows(IllegalArgumentException.class,
         () -> DataSpaceContentSettingsService.validateName(null));
      assertThrows(IllegalArgumentException.class,
         () -> DataSpaceContentSettingsService.validateUploadFileName(null));
   }

   private void write(String parent, String name, String content) throws Exception {
      space.withOutputStream(parent, name, out -> out.write(content.getBytes(StandardCharsets.UTF_8)));
   }

   private String read(String path) throws IOException {
      try(InputStream in = space.getInputStream(null, path)) {
         return new String(in.readAllBytes(), StandardCharsets.UTF_8);
      }
   }

   private static ChangeDataSpaceFileRequest fileRename(String path, String name, String newName) {
      return ChangeDataSpaceFileRequest.builder()
         .path(path).name(name).newName(newName).timeZone("UTC").newFile(false)
         .build();
   }
}
