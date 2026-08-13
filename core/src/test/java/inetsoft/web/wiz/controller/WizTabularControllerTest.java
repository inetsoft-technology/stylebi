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
package inetsoft.web.wiz.controller;

import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.util.MessageException;
import inetsoft.web.portal.data.DataSourceDefinition;
import inetsoft.uql.XRepository;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.tabular.TabularUtil;
import inetsoft.web.portal.data.DatasourcesService;
import inetsoft.web.wiz.model.WizTabularSaveResult;
import inetsoft.web.wiz.request.WizTabularCreateRequest;
import inetsoft.web.wiz.service.UnsupportedDatasourceException;
import org.springframework.web.server.ResponseStatusException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The save paths, where a wrong answer is worst: a client branches on {@code reason} and cannot see
 * the sentence behind it, so a mis-mapped failure becomes a message about the wrong problem.
 */
@Tag("core")
class WizTabularControllerTest {
   private DatasourcesService datasourcesService;
   private XRepository xrepository;
   private SecurityEngine securityEngine;
   private WizTabularController controller;
   private Principal principal;

   @BeforeEach
   void setUp() throws Exception {
      datasourcesService = mock(DatasourcesService.class);
      xrepository = mock(XRepository.class);
      securityEngine = mock(SecurityEngine.class);
      controller = new WizTabularController(datasourcesService, xrepository, securityEngine);
      principal = mock(Principal.class);

      when(securityEngine.checkPermission(any(), any(ResourceType.class), anyString(),
                                          any(ResourceAction.class))).thenReturn(true);
   }

   private DataSourceDefinition definition(String name) {
      DataSourceDefinition definition = new DataSourceDefinition();
      definition.setName(name);
      definition.setType("MongoDB");

      return definition;
   }

   @Test
   void createReturnsTheSavedPath() throws Exception {
      // Absent before the write, present after: the second answer is what proves the write landed,
      // since createNewDataSource no-ops silently on an unavailable type.
      when(datasourcesService.checkDuplicate("folder/mongo")).thenReturn(false, true);

      WizTabularSaveResult result = controller.createTabularDataSource(
         new WizTabularCreateRequest("folder", definition("mongo")), principal);

      assertTrue(result.ok());
      assertNull(result.reason());
      assertEquals("folder/mongo", result.path());
      verify(datasourcesService).createNewDataSource(any(), eq(false), eq(principal));
   }

   // Checked before the write rather than inferred from the failure sentence, which is translated.
   @Test
   void createRejectsADuplicateWithoutWriting() throws Exception {
      when(datasourcesService.checkDuplicate("mongo")).thenReturn(true);

      WizTabularSaveResult result = controller.createTabularDataSource(
         new WizTabularCreateRequest("", definition("mongo")), principal);

      assertFalse(result.ok());
      assertEquals(WizTabularSaveResult.DUPLICATE_NAME, result.reason());
      assertNull(result.path());
      verify(datasourcesService, never()).createNewDataSource(any(), anyBoolean(), any());
   }

   @Test
   void createMapsAnUnrecognizedRefusalToUnknownRatherThanFailing() throws Exception {
      when(datasourcesService.checkDuplicate(anyString())).thenReturn(false);
      doThrow(new MessageException("some wording this mapping does not know"))
         .when(datasourcesService).createNewDataSource(any(), anyBoolean(), any());

      WizTabularSaveResult result = controller.createTabularDataSource(
         new WizTabularCreateRequest("", definition("mongo")), principal);

      assertFalse(result.ok());
      assertEquals(WizTabularSaveResult.UNKNOWN, result.reason());
   }

   /*
    * A denial must leave as SecurityException so WizControllerErrorHandler can turn it into a 403.
    * Returning a save result here would report "could not save" for what is really "not allowed",
    * and nothing would be written either way -- the two are indistinguishable to the caller.
    */
   @Test
   void createRefusesWithoutFolderPermission() throws Exception {
      when(securityEngine.checkPermission(any(), any(ResourceType.class), anyString(),
                                          any(ResourceAction.class))).thenReturn(false);

      assertThrows(SecurityException.class, () -> controller.createTabularDataSource(
         new WizTabularCreateRequest("folder", definition("mongo")), principal));

      verify(datasourcesService, never()).createNewDataSource(any(), anyBoolean(), any());
   }

   @Test
   void updateReportsDatasourceLostWhenItIsGone() throws Exception {
      when(datasourcesService.checkDuplicate("folder/mongo")).thenReturn(false);

      WizTabularSaveResult result =
         controller.updateTabularDataSource("folder/mongo", definition("mongo"), principal);

      assertFalse(result.ok());
      assertEquals(WizTabularSaveResult.DATASOURCE_LOST, result.reason());
      verify(datasourcesService, never()).updateDataSource(anyString(), any(), any());
   }

   @Test
   void updateReturnsTheNewPathAfterARename() throws Exception {
      when(datasourcesService.checkDuplicate("folder/old")).thenReturn(true);
      when(datasourcesService.checkDuplicate("folder/new")).thenReturn(false);

      WizTabularSaveResult result =
         controller.updateTabularDataSource("folder/old", definition("new"), principal);

      assertTrue(result.ok());
      assertEquals("folder/new", result.path());
      // The BARE name. updateDataSource re-prefixes definition.parentPath itself, so passing the
      // full path here is what made every folder-scoped edit fail.
      verify(datasourcesService).updateDataSource(eq("old"), any(), eq(principal));
   }

   /*
    * The regression test for the folder-path bug, and the reason it is written this way.
    *
    * The other tests mock DatasourcesService, so they assert the ARGUMENTS of the call rather than
    * its effect -- which is exactly how the bug survived: the old assertion pinned the broken
    * full-path call and passed *because* production was wrong. This one reproduces the callee's own
    * key arithmetic (DatasourcesBaseService.updateDataSource: `oldName = parentPath + name`) and
    * asserts the source is actually found, so it fails if the controller ever passes a full path
    * again.
    */
   @Test
   void folderScopedUpdateResolvesTheStoredSource() throws Exception {
      Map<String, String> repository = new HashMap<>();
      repository.put("folder/mongo", "the stored source");
      List<String> resolved = new ArrayList<>();

      when(datasourcesService.checkDuplicate(anyString()))
         .thenAnswer((inv) -> repository.containsKey(inv.getArgument(0, String.class)));

      doAnswer((inv) -> {
         String name = inv.getArgument(0, String.class);
         DataSourceDefinition def = inv.getArgument(1, DataSourceDefinition.class);
         String parent = "".equals(def.getParentPath()) ? "" : def.getParentPath() + "/";
         String oldName = parent + name;

         if(!repository.containsKey(oldName)) {
            throw new MessageException("data.datasources.saveDataSourceLost");
         }

         resolved.add(oldName);

         return null;
      }).when(datasourcesService).updateDataSource(anyString(), any(), any());

      DataSourceDefinition definition = definition("mongo");
      definition.setDescription("edited");

      WizTabularSaveResult result =
         controller.updateTabularDataSource("folder/mongo", definition, principal);

      assertTrue(result.ok(), "folder-scoped update must resolve the stored source");
      assertEquals("folder/mongo", result.path());
      assertEquals(List.of("folder/mongo"), resolved);
   }

   @Test
   void rootScopedUpdateStillResolves() throws Exception {
      Map<String, String> repository = new HashMap<>();
      repository.put("mongo", "the stored source");
      List<String> resolved = new ArrayList<>();

      when(datasourcesService.checkDuplicate(anyString()))
         .thenAnswer((inv) -> repository.containsKey(inv.getArgument(0, String.class)));

      doAnswer((inv) -> {
         String name = inv.getArgument(0, String.class);
         DataSourceDefinition def = inv.getArgument(1, DataSourceDefinition.class);
         String parent = "".equals(def.getParentPath()) ? "" : def.getParentPath() + "/";
         resolved.add(parent + name);

         return null;
      }).when(datasourcesService).updateDataSource(anyString(), any(), any());

      assertTrue(controller.updateTabularDataSource("mongo", definition("mongo"), principal).ok());
      assertEquals(List.of("mongo"), resolved);
   }

   @Test
   void updateRejectsARenameOntoAnExistingName() throws Exception {
      when(datasourcesService.checkDuplicate("folder/old")).thenReturn(true);
      when(datasourcesService.checkDuplicate("folder/taken")).thenReturn(true);

      WizTabularSaveResult result =
         controller.updateTabularDataSource("folder/old", definition("taken"), principal);

      assertFalse(result.ok());
      assertEquals(WizTabularSaveResult.DUPLICATE_NAME, result.reason());
      verify(datasourcesService, never()).updateDataSource(anyString(), any(), any());
   }

   // Saving under the same name is not a duplicate of itself.
   @Test
   void updateAllowsSavingUnderTheSameName() throws Exception {
      when(datasourcesService.checkDuplicate("folder/mongo")).thenReturn(true);

      WizTabularSaveResult result =
         controller.updateTabularDataSource("folder/mongo", definition("mongo"), principal);

      assertTrue(result.ok());
      assertEquals("folder/mongo", result.path());
      // Bare name, not the full path — see folderScopedUpdateResolvesTheStoredSource.
      verify(datasourcesService).updateDataSource(eq("mongo"), any(), eq(principal));
   }

   /*
    * The caller increments this before each refresh and discards any response carrying a lower one.
    * Without the echo a slow response silently overwrites a newer form -- the field would come back
    * as 0 and every response would look current.
    */
   @Test
   void refreshEchoesTheSequenceNumberBack() throws Exception {
      DataSourceDefinition sent = definition("mongo");
      sent.setSequenceNumber(7);

      DataSourceDefinition recomputed = definition("mongo");
      recomputed.setSequenceNumber(0);
      when(datasourcesService.refreshTabularView(sent)).thenReturn(recomputed);

      assertEquals(7, controller.refreshTabularView(sent, null, principal).getSequenceNumber());
   }

   @Test
   void checkDuplicateAnswersInAnExtensibleShape() throws Exception {
      when(datasourcesService.checkDuplicate("mongo")).thenReturn(true);

      assertEquals(Boolean.TRUE, controller.checkDuplicate("mongo", principal).get("duplicate"));
   }

   // Ungated it answers "does a data source exist at this arbitrary path" for any logged-in user.
   @Test
   void checkDuplicateRefusesWithoutWriteOnTheTargetFolder() throws Exception {
      when(securityEngine.checkPermission(any(), any(ResourceType.class), anyString(),
                                          any(ResourceAction.class))).thenReturn(false);

      assertThrows(SecurityException.class, () -> controller.checkDuplicate("secret/thing",
                                                                           principal));
      verify(datasourcesService, never()).checkDuplicate(anyString());
   }

   @Test
   void createRejectsAMissingName() {
      assertThrows(ResponseStatusException.class, () -> controller.createTabularDataSource(
         new WizTabularCreateRequest("", definition(null)), principal));
   }

   @Test
   void createRejectsANameContainingASlash() {
      assertThrows(ResponseStatusException.class, () -> controller.createTabularDataSource(
         new WizTabularCreateRequest("", definition("a/b")), principal));
   }

   // A literal null body would otherwise NPE on request.definition() and become a 500.
   @Test
   void createSurvivesANullBody() throws Exception {
      WizTabularSaveResult result = controller.createTabularDataSource(null, principal);

      assertFalse(result.ok());
      assertEquals(WizTabularSaveResult.UNKNOWN, result.reason());
   }

   /*
    * createNewDataSource no-ops silently when the type is unknown or its connector is unavailable.
    * Reporting ok:true there sends the editor to a data source that does not exist.
    */
   @Test
   void createReportsFailureWhenNothingWasWritten() throws Exception {
      when(datasourcesService.checkDuplicate("ghost")).thenReturn(false);

      WizTabularSaveResult result = controller.createTabularDataSource(
         new WizTabularCreateRequest("", definition("ghost")), principal);

      assertFalse(result.ok(), "a write that landed nothing must not report success");
      assertEquals(WizTabularSaveResult.UNKNOWN, result.reason());
   }

   @Test
   void createRejectsAParentPathThatDisagreesWithTheDefinition() {
      DataSourceDefinition definition = definition("mongo");
      definition.setParentPath("other");

      assertThrows(ResponseStatusException.class, () -> controller.createTabularDataSource(
         new WizTabularCreateRequest("folder", definition), principal));
   }

   // Reading only the request field created at the root for any client that set it on the
   // definition -- the same field update reads.
   @Test
   void createHonorsTheDefinitionParentWhenTheRequestOmitsOne() throws Exception {
      DataSourceDefinition definition = definition("mongo");
      definition.setParentPath("folder");
      when(datasourcesService.checkDuplicate("folder/mongo")).thenReturn(false, true);

      WizTabularSaveResult result = controller.createTabularDataSource(
         new WizTabularCreateRequest(null, definition), principal);

      assertTrue(result.ok());
      assertEquals("folder/mongo", result.path());
   }

   /*
    * Pointing update at a JDBC database would otherwise rewrite it from a tabular definition --
    * renaming it, discarding every JDBC setting -- and report ok. The read side already 422s on a
    * non-tabular path; the write side must be at least as strict.
    */
   @Test
   void updateRefusesToRewriteANonTabularSource() throws Exception {
      when(xrepository.getDataSource("sakila")).thenReturn(mock(JDBCDataSource.class));

      assertThrows(UnsupportedDatasourceException.class,
                   () -> controller.updateTabularDataSource("sakila", definition("sakila"),
                                                            principal));
      verify(datasourcesService, never()).updateDataSource(anyString(), any(), any());
   }

   /*
    * A rename needs DELETE on the old path. The service checks it but reports the denial as a
    * MessageException, which toFailure would map to a 200 carrying UNKNOWN -- a permission denial
    * disguised as a save failure.
    */
   @Test
   void renameWithoutDeletePermissionIsA403NotASaveFailure() throws Exception {
      when(datasourcesService.checkDuplicate("folder/old")).thenReturn(true);
      when(datasourcesService.checkDuplicate("folder/new")).thenReturn(false);
      when(securityEngine.checkPermission(any(), eq(ResourceType.DATA_SOURCE), eq("folder/old"),
                                          eq(ResourceAction.DELETE))).thenReturn(false);

      assertThrows(SecurityException.class,
                   () -> controller.updateTabularDataSource("folder/old", definition("new"),
                                                            principal));
      verify(datasourcesService, never()).updateDataSource(anyString(), any(), any());
   }

   /*
    * TabularUtil.sessionId is a static ThreadLocal that is only ever set, never cleared, and is
    * handed to connector button methods. On a pooled thread, not setting it means inheriting
    * whatever the previous request left -- possibly another user's. Refresh must always overwrite.
    */
   @Test
   void refreshAlwaysOverwritesTheThreadLocalSessionId() throws Exception {
      TabularUtil.setSessionId("a-previous-request-on-this-thread");

      DataSourceDefinition sent = definition("mongo");
      when(datasourcesService.refreshTabularView(sent)).thenReturn(definition("mongo"));

      controller.refreshTabularView(sent, (jakarta.servlet.http.HttpServletRequest) null, principal);

      assertNull(TabularUtil.getSessionId(),
                 "a stale session id must not survive into a connector call");
   }

   @Test
   void refreshRefusesWithoutPermission() throws Exception {
      when(securityEngine.checkPermission(any(), any(ResourceType.class), anyString(),
                                          any(ResourceAction.class))).thenReturn(false);

      assertThrows(SecurityException.class,
                   () -> controller.refreshTabularView(definition("mongo"), (jakarta.servlet.http.HttpServletRequest) null, principal));
      verify(datasourcesService, never()).refreshTabularView(any());
   }
}
