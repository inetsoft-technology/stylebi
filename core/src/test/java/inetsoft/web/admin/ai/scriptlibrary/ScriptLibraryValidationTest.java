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
package inetsoft.web.admin.ai.scriptlibrary;

import inetsoft.report.LibManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.util.Tool;
import inetsoft.web.admin.ai.AdminBackupService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #80071 (B): the admin-chat Script Library create/rename must refuse what composer-chat has
 * refused since stylebi#7534 -- a non-identifier or reserved-word name, and text that does not
 * compile -- at plan time (which apply re-runs before any backup or mutation), storing nothing.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class ScriptLibraryValidationTest {
   @Mock private LibManagerProvider libManagerProvider;
   @Mock private LibManager lib;
   @Mock private SecurityEngine securityEngine;
   @Mock private Principal user;

   // stand-in for the parser: any text containing BAD does not compile
   private static final ScriptLibraryValidator.SyntaxChecker CHECKER = text -> {
      if(text.contains("BAD")) {
         throw new RuntimeException("SyntaxError: Unexpected token\n  at line 1");
      }
   };

   private ScriptLibraryService scriptLibraryService;
   private ScriptLibraryChangePlanService service;
   private MockedStatic<Tool> tool;

   @BeforeEach
   void setUp() throws Exception {
      lenient().when(libManagerProvider.getManager(any(Principal.class))).thenReturn(lib);
      scriptLibraryService = new ScriptLibraryService(libManagerProvider, securityEngine);
      service = new ScriptLibraryChangePlanService(scriptLibraryService);
      service.setSyntaxChecker(CHECKER);
      lenient().when(securityEngine.checkPermission(
         eq(user), eq(ResourceType.SCRIPT), anyString(), eq(ResourceAction.ADMIN)))
         .thenReturn(true);
      tool = mockStatic(Tool.class, withSettings().strictness(Strictness.LENIENT)
         .defaultAnswer(Answers.CALLS_REAL_METHODS));
      tool.when(() -> Tool.encryptPassword(anyString()))
         .thenAnswer(inv -> "TKN:" + inv.getArgument(0));
   }

   @AfterEach
   void tearDown() {
      tool.close();
   }

   private static ScriptLibraryChangePlanRequest request(ScriptLibraryChangeRequest... changes) {
      ScriptLibraryChangePlanRequest req = new ScriptLibraryChangePlanRequest();
      req.setTask("test task");
      req.setChanges(List.of(changes));
      return req;
   }

   private static ScriptLibraryChangeRequest create(String name, String text) {
      ScriptLibraryChangeRequest c = new ScriptLibraryChangeRequest();
      c.setVerb(ScriptLibraryChangeRequest.VERB_CREATE);
      c.setName(name);
      c.setText(text);
      return c;
   }

   private static ScriptLibraryChangeRequest rename(String name, String newName) {
      ScriptLibraryChangeRequest c = new ScriptLibraryChangeRequest();
      c.setVerb(ScriptLibraryChangeRequest.VERB_RENAME);
      c.setName(name);
      c.setNewName(newName);
      return c;
   }

   private IllegalArgumentException refused(ScriptLibraryChangeRequest c) {
      return assertThrows(IllegalArgumentException.class,
                          () -> service.resolve(request(c), user));
   }

   // ------------------------------------------------------------------ create

   @Test void createRefusesANonIdentifierName() {
      String msg = refused(create("qa-scr-bad-name", "function f() { return 1; }")).getMessage();

      assertTrue(msg.contains("changes[0].name"), msg);
      assertTrue(msg.contains("qa-scr-bad-name"), msg);
      assertTrue(msg.matches("(?is).*(name|identifier).*"), msg);
      assertTrue(msg.endsWith("Nothing was saved."), msg);
   }

   @Test void createRefusesAReservedWordName() {
      String msg = refused(create("class", "function f() { return 1; }")).getMessage();

      assertTrue(msg.contains("changes[0].name"), msg);
      assertTrue(msg.contains("\"class\""), msg);
      assertTrue(msg.contains("reserved"), msg);
   }

   // apply stores the raw name, so a padded name must not pass a check run on the trimmed one
   @Test void createRefusesANameWithSurroundingWhitespace() {
      assertTrue(refused(create(" class", "function f() { return 1; }"))
                    .getMessage().contains("changes[0].name"));
      assertTrue(refused(create("fn ", "function fn() { return 1; }"))
                    .getMessage().contains("changes[0].name"));
   }

   @Test void createRefusesTextThatDoesNotCompile() {
      String msg = refused(create("goodName", "function goodName( BAD {")).getMessage();

      assertTrue(msg.contains("changes[0].text"), msg);
      assertTrue(msg.matches("(?is).*(compile|syntax).*"), msg);
      assertTrue(msg.contains("Unexpected token"), msg);
      assertTrue(msg.endsWith("Nothing was saved."), msg);
   }

   @Test void createAcceptsAValidNameAndCompilingText() throws Exception {
      assertEquals(1, service.resolve(
         request(create("goodName", "function goodName() { return 1; }")), user)
                      .changes().size());
   }

   @Test void createWithEngineDownSurfacesTheReasonAsAnIllegalArgument() {
      ScriptLibraryChangePlanService down = new ScriptLibraryChangePlanService(scriptLibraryService);
      down.setSyntaxChecker(text -> {
         throw new IllegalStateException("Engine not initialized");
      });

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> down.resolve(request(create("goodName", "function goodName() {}")), user));
      assertTrue(ex.getMessage().contains("script engine is not available"), ex.getMessage());
      assertTrue(ex.getMessage().contains("Engine not initialized"), ex.getMessage());
   }

   // ------------------------------------------------------------------ rename

   @Test void renameRefusesANonIdentifierNewName() {
      String msg = refused(rename("old", "qa-scr-bad-name")).getMessage();

      assertTrue(msg.contains("changes[0].newName"), msg);
      assertTrue(msg.contains("qa-scr-bad-name"), msg);
   }

   @Test void renameRefusesAReservedWordAndAPaddedNewName() {
      assertTrue(refused(rename("old", "class")).getMessage().contains("reserved"));
      assertTrue(refused(rename("old", "fn ")).getMessage().contains("changes[0].newName"));
   }

   @Test void renameRefusesWhenTheRewrittenTextDoesNotCompile() {
      when(lib.getScript("old")).thenReturn("function old() { return BAD; }");

      String msg = refused(rename("old", "fresh")).getMessage();

      assertTrue(msg.contains("changes[0].newName"), msg);
      assertTrue(msg.contains("does not compile"), msg);
      assertTrue(msg.endsWith("Nothing was saved."), msg);
   }

   // existing names are never validated: an entry with a legacy name can still be renamed away
   @Test void renameFromALegacyNonIdentifierNameIsStillAllowed() throws Exception {
      when(lib.getScript("legacy-name")).thenReturn("function fresh() { return 1; }");

      assertEquals(1, service.resolve(request(rename("legacy-name", "fresh")), user)
                         .changes().size());
   }

   // ------------------------------------------------------------------- apply

   @Test void applyOfABadCreateStoresNothing() throws Exception {
      AdminBackupService backup = mock(AdminBackupService.class);
      ScriptLibraryChangesetApplyService apply = new ScriptLibraryChangesetApplyService(
         service, scriptLibraryService, libManagerProvider, backup);
      ScriptLibraryApplyRequest req = new ScriptLibraryApplyRequest();
      req.setTask("test task");
      req.setChanges(List.of(create("qa-scr-bad-name", "function qaScrBadText( BAD {")));
      req.setPlanHash("whatever");

      assertThrows(IllegalArgumentException.class, () -> apply.apply(req, user));
      verify(lib, never()).setScript(anyString(), anyString());
      verify(lib, never()).save();
      verifyNoInteractions(backup);
   }
}
