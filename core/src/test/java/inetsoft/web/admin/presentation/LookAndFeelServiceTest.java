/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.admin.presentation;

/*
 * Test strategy
 *
 * Bug 76360: setLogo/setFavicon/setViewsheet in LookAndFeelService wrote the caller-supplied
 * FileData.name() (setLogo/setFavicon: the substring from its last '.' onward) straight into
 * DataSpace.withOutputStream with no validation. DataSpace.sanitizePathComponent does not strip
 * or reject ".." segments, so a crafted name like "a.b/../../evil.png" survived unmodified to the
 * storage write call. The fix adds LookAndFeelService.requireSafeFileName(), called on the raw
 * FileData.name() before it is used, which rejects any name containing "/", "\\", "..", or a NUL
 * byte.
 *
 * These tests invoke the three private setLogo/setFavicon/setViewsheet methods directly via
 * reflection rather than through the public setModel() entry point: setModel() also drives
 * SreeEnv property writes, the userformat.xml branch, font handling, and manager.save()/
 * SreeEnv.save(), none of which are relevant to this fix, so exercising it would mean mocking a
 * lot of unrelated behavior for no added coverage of the actual defect. DataSpace is a
 * constructor parameter on these methods (so a plain Mockito mock is enough, no field injection
 * needed); PortalThemesManager is read from a private @Autowired field, injected here via
 * reflection, matching this class's plain (non-Spring-context) unit style.
 *
 * Behavioral guarantees covered:
 *
 * [G1] setViewsheet, org-scoped branch: a traversal-shaped viewsheet file name is rejected before
 *      any write happens.
 * [G2] setViewsheet, global branch: the same traversal-shaped name is accepted, because the
 *      global branch never uses the caller-supplied name (it always writes "format.css") -- this
 *      pins that the fix is correctly scope-conditional there, not a blanket rejection that would
 *      also need to change the global branch's behavior.
 * [G3]/[G4] setLogo/setFavicon: a traversal-shaped name (crafted so the naive last-dot substring
 *      still carries "../") is rejected on both the global and org-scoped branches.
 * [G5] A safe, ordinary file name still succeeds on all three methods.
 * [G6] A name with no dot at all (falls into the default-extension branch) is unaffected by the
 *      fix for setLogo/setFavicon.
 *
 * Bug 76360's fix left the font-upload path (updateFonts/saveFontFaceFiles/writeFontCSS/
 * deleteFontFiles) untouched even though it carries the identical unvalidated-caller-controlled-
 * filename pattern, keyed off FontFaceModel/UserFontModel.getFileNamePrefix() and the raw
 * userFont family string, reaching the same DataSpace write/delete/transaction calls. Bug 76362's
 * fix reuses the same requireSafeFileName() at each of the three call sites.
 *
 * [G7] saveFontFaceFiles rejects a traversal-shaped getFileNamePrefix() before any
 *      writeStyleFile/withOutputStream call.
 * [G8] saveFontFaceFiles still succeeds and writes the expected "<prefix>.ttf" for a safe name.
 * [G9] deleteFontFiles rejects a traversal-shaped name before any space.exists/space.delete call.
 * [G10] deleteFontFiles still succeeds for a safe name.
 * [G11] writeFontCSS rejects a traversal-shaped family/userFont string before
 *       beginTransaction()/newStream().
 * [G12] writeFontCSS still succeeds and writes "<family>.css" for a safe family name.
 * [G13] A name containing "^" (the FontFaceModel identifier-separator case) is still accepted by
 *       all three font-path methods -- pins that the fix does not regress the legitimate
 *       multi-variant-font case.
 */

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.portal.FontFaceModel;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.util.DataSpace;
import inetsoft.util.audit.ActionRecord;
import inetsoft.web.admin.model.FileData;
import inetsoft.web.admin.presentation.model.UserFontModel;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
class LookAndFeelServiceTest {
   private LookAndFeelService service;
   private DataSpace space;
   private PortalThemesManager manager;
   private Principal principal;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<SUtil> suStatic;

   @BeforeEach
   void setUp() throws Exception {
      service = new LookAndFeelService();
      space = mock(DataSpace.class);
      manager = mock(PortalThemesManager.class, withSettings().lenient());
      principal = mock(Principal.class, withSettings().lenient());

      setField("portalThemesManager", manager);

      OrganizationManager orgManager = mock(OrganizationManager.class, withSettings().lenient());
      when(orgManager.getCurrentOrgID()).thenReturn("host-org");
      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

      // writeStyleFile's ActionRecord construction resolves the local host via SreeEnv, which
      // needs a live Spring context this plain unit test does not stand up -- stub the audit
      // record itself instead, since the audit trail isn't what this fix is about.
      ActionRecord actionRecord = mock(ActionRecord.class, withSettings().lenient());
      suStatic = mockStatic(SUtil.class, withSettings().lenient());
      suStatic.when(() -> SUtil.getActionRecord(any(Principal.class), anyString(), anyString(), anyString()))
         .thenReturn(actionRecord);

      lenient().when(manager.getCssEntries()).thenReturn(Collections.emptyMap());
      lenient().when(manager.getLogoEntries()).thenReturn(Collections.emptyMap());
      lenient().when(manager.getFaviconEntries()).thenReturn(Collections.emptyMap());
   }

   @AfterEach
   void tearDown() {
      orgManagerStatic.close();
      suStatic.close();
   }

   private void setField(String name, Object value) throws Exception {
      Field field = LookAndFeelService.class.getDeclaredField(name);
      field.setAccessible(true);
      field.set(service, value);
   }

   private FileData fileData(String name, String content) {
      return FileData.builder()
         .name(name)
         .content(Base64.getEncoder().encodeToString(content.getBytes()))
         .build();
   }

   private void invoke(String methodName, FileData data, String directory, boolean globalSettings)
      throws Throwable
   {
      Method method = LookAndFeelService.class.getDeclaredMethod(
         methodName, FileData.class, DataSpace.class, String.class, Principal.class, boolean.class);
      method.setAccessible(true);

      try {
         method.invoke(service, data, space, directory, principal, globalSettings);
      }
      catch(InvocationTargetException ex) {
         throw ex.getCause();
      }
   }

   @Test
   void setViewsheetOrgScopedRejectsTraversalName() throws Exception {
      FileData traversal = fileData("../../../etc/evil.css", "body {}");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> invoke("setViewsheet", traversal, "portal", false));
      assertTrue(ex.getMessage().contains("../../../etc/evil.css"));

      verify(space, never()).withOutputStream(anyString(), anyString(), any());
   }

   @Test
   void setViewsheetGlobalScopeIgnoresCallerNameAndStillSucceeds() throws Throwable {
      // The global branch never reads the caller-supplied name -- it always writes "format.css" --
      // so it is correctly unaffected by the fix even when given a traversal-shaped name.
      FileData traversal = fileData("../../../etc/evil.css", "body {}");

      invoke("setViewsheet", traversal, "portal", true);

      verify(space).withOutputStream(eq("portal"), eq("format.css"), any());
   }

   @Test
   void setViewsheetOrgScopedAcceptsSafeName() throws Throwable {
      FileData safe = fileData("mystyle.css", "body {}");

      invoke("setViewsheet", safe, "portal", false);

      verify(space).withOutputStream(eq("portal/host-org"), eq("mystyle.css"), any());
   }

   @Test
   void setLogoRejectsTraversalNameOnGlobalScope() throws Exception {
      FileData traversal = fileData("a.b/../../evil.png", "data");

      assertThrows(IllegalArgumentException.class,
         () -> invoke("setLogo", traversal, "portal", true));

      verify(space, never()).withOutputStream(anyString(), anyString(), any());
   }

   @Test
   void setLogoRejectsTraversalNameOnOrgScope() throws Exception {
      FileData traversal = fileData("a.b/../../evil.png", "data");

      assertThrows(IllegalArgumentException.class,
         () -> invoke("setLogo", traversal, "portal", false));

      verify(space, never()).withOutputStream(anyString(), anyString(), any());
   }

   @Test
   void setLogoAcceptsSafeName() throws Throwable {
      FileData safe = fileData("company-logo.png", "data");

      invoke("setLogo", safe, "portal", true);

      // setLogo writes directly and then again via writeStyleFile -- a pre-existing redundant-
      // write quirk noted in the diagnosis/refutation, unrelated to this fix.
      verify(space, times(2)).withOutputStream(eq("portal"), eq("logo.png"), any());
   }

   @Test
   void setLogoNameWithNoDotUsesDefaultExtensionAndIsUnaffected() throws Throwable {
      FileData noDot = fileData("logofile", "data");

      invoke("setLogo", noDot, "portal", true);

      verify(space, times(2)).withOutputStream(eq("portal"), eq("logo.gif"), any());
   }

   @Test
   void setFaviconRejectsTraversalNameOnGlobalScope() throws Exception {
      FileData traversal = fileData("a.b/../../evil.ico", "data");

      assertThrows(IllegalArgumentException.class,
         () -> invoke("setFavicon", traversal, "portal", true));

      verify(space, never()).withOutputStream(anyString(), anyString(), any());
   }

   @Test
   void setFaviconRejectsTraversalNameOnOrgScope() throws Exception {
      FileData traversal = fileData("a.b/../../evil.ico", "data");

      assertThrows(IllegalArgumentException.class,
         () -> invoke("setFavicon", traversal, "portal", false));

      verify(space, never()).withOutputStream(anyString(), anyString(), any());
   }

   @Test
   void setFaviconAcceptsSafeName() throws Throwable {
      FileData safe = fileData("company-favicon.ico", "data");

      invoke("setFavicon", safe, "portal", true);

      verify(space, times(2)).withOutputStream(eq("portal"), eq("favicon.ico"), any());
   }

   private UserFontModel userFontModel(String name, String identifier) {
      return UserFontModel.builder()
         .name(name)
         .identifier(identifier)
         .ttf(fileData("font.ttf", "data"))
         .build();
   }

   private void invokeDeleteFontFiles(List<String> fontNames, String fontPath) throws Throwable {
      Method method = LookAndFeelService.class.getDeclaredMethod(
         "deleteFontFiles", List.class, String.class, DataSpace.class);
      method.setAccessible(true);

      try {
         method.invoke(service, fontNames, fontPath, space);
      }
      catch(InvocationTargetException ex) {
         throw ex.getCause();
      }
   }

   private void invokeSaveFontFaceFiles(UserFontModel model, String fontPath) throws Throwable {
      Method method = LookAndFeelService.class.getDeclaredMethod(
         "saveFontFaceFiles", UserFontModel.class, String.class, DataSpace.class, Principal.class);
      method.setAccessible(true);

      try {
         method.invoke(service, model, fontPath, space, principal);
      }
      catch(InvocationTargetException ex) {
         throw ex.getCause();
      }
   }

   private void invokeWriteFontCSS(String family, List<FontFaceModel> fontFaces, String dir) throws Throwable {
      Method method = LookAndFeelService.class.getDeclaredMethod(
         "writeFontCSS", String.class, List.class, String.class, DataSpace.class);
      method.setAccessible(true);

      try {
         method.invoke(service, family, fontFaces, dir, space);
      }
      catch(InvocationTargetException ex) {
         throw ex.getCause();
      }
   }

   @Test
   void saveFontFaceFilesRejectsTraversalName() throws Exception {
      UserFontModel traversal = userFontModel("../../../etc/evil", FontFaceModel.EMPTY_IDENTIFIER);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> invokeSaveFontFaceFiles(traversal, "portal/font"));
      assertTrue(ex.getMessage().contains("../../../etc/evil"));

      verify(space, never()).withOutputStream(anyString(), anyString(), any());
   }

   @Test
   void saveFontFaceFilesAcceptsSafeName() throws Throwable {
      UserFontModel safe = userFontModel("myfont", FontFaceModel.EMPTY_IDENTIFIER);

      invokeSaveFontFaceFiles(safe, "portal/font");

      verify(space).withOutputStream(eq("portal/font"), eq("myfont.ttf"), any());
   }

   @Test
   void saveFontFaceFilesAcceptsNameWithIdentifierCaret() throws Throwable {
      UserFontModel safe = userFontModel("myfont", "bold");

      invokeSaveFontFaceFiles(safe, "portal/font");

      verify(space).withOutputStream(eq("portal/font"), eq("myfont^bold.ttf"), any());
   }

   @Test
   void deleteFontFilesRejectsTraversalName() {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> invokeDeleteFontFiles(Collections.singletonList("../../../etc/evil"), "portal/font"));
      assertTrue(ex.getMessage().contains("../../../etc/evil"));

      verify(space, never()).exists(anyString(), anyString());
      verify(space, never()).delete(anyString(), anyString());
   }

   @Test
   void deleteFontFilesAcceptsSafeName() throws Throwable {
      when(space.exists("portal/font", "myfont.ttf")).thenReturn(true);

      invokeDeleteFontFiles(Collections.singletonList("myfont"), "portal/font");

      verify(space).delete("portal/font", "myfont.ttf");
   }

   @Test
   void deleteFontFilesAcceptsNameWithIdentifierCaret() throws Throwable {
      when(space.exists("portal/font", "myfont^bold.ttf")).thenReturn(true);
      when(space.exists("portal/font", "myfont.css")).thenReturn(true);

      invokeDeleteFontFiles(Collections.singletonList("myfont^bold"), "portal/font");

      verify(space).delete("portal/font", "myfont^bold.ttf");
      verify(space).delete("portal/font", "myfont.css");
   }

   @Test
   void writeFontCSSRejectsTraversalFamilyName() {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> invokeWriteFontCSS("../../../etc/evil", Collections.emptyList(), "portal/font"));
      assertTrue(ex.getMessage().contains("../../../etc/evil"));

      verify(space, never()).beginTransaction();
   }

   @Test
   void writeFontCSSAcceptsSafeFamilyName() throws Throwable {
      DataSpace.Transaction tx = mock(DataSpace.Transaction.class, withSettings().lenient());
      when(space.beginTransaction()).thenReturn(tx);
      when(tx.newStream(eq("portal/font"), eq("myfont.css"))).thenReturn(new ByteArrayOutputStream());

      invokeWriteFontCSS("myfont", Collections.emptyList(), "portal/font");

      verify(tx).newStream("portal/font", "myfont.css");
      verify(tx).commit();
   }

   @Test
   void writeFontCSSAcceptsNameWithIdentifierCaret() throws Throwable {
      DataSpace.Transaction tx = mock(DataSpace.Transaction.class, withSettings().lenient());
      when(space.beginTransaction()).thenReturn(tx);
      when(tx.newStream(eq("portal/font"), eq("myfont^bold.css")))
         .thenReturn(new ByteArrayOutputStream());

      invokeWriteFontCSS("myfont^bold", Collections.emptyList(), "portal/font");

      verify(tx).newStream("portal/font", "myfont^bold.css");
   }
}
