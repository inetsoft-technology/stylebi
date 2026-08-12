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
import inetsoft.web.portal.data.DatasourcesService;
import inetsoft.web.wiz.model.WizTabularSaveResult;
import inetsoft.web.wiz.request.WizTabularCreateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;

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
   private SecurityEngine securityEngine;
   private WizTabularController controller;
   private Principal principal;

   @BeforeEach
   void setUp() throws Exception {
      datasourcesService = mock(DatasourcesService.class);
      securityEngine = mock(SecurityEngine.class);
      controller = new WizTabularController(datasourcesService, securityEngine);
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
      when(datasourcesService.checkDuplicate("folder/mongo")).thenReturn(false);

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
      verify(datasourcesService).updateDataSource(eq("folder/old"), any(), eq(principal));
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
      verify(datasourcesService).updateDataSource(eq("folder/mongo"), any(), eq(principal));
   }

   /*
    * The caller increments this before each refresh and discards any response carrying a lower one.
    * Without the echo a slow response silently overwrites a newer form -- the field would come back
    * as 0 and every response would look current.
    */
   @Test
   void refreshEchoesTheSequenceNumberBack() {
      DataSourceDefinition sent = definition("mongo");
      sent.setSequenceNumber(7);

      DataSourceDefinition recomputed = definition("mongo");
      recomputed.setSequenceNumber(0);
      when(datasourcesService.refreshTabularView(sent)).thenReturn(recomputed);

      assertEquals(7, controller.refreshTabularView(sent).getSequenceNumber());
   }

   @Test
   void checkDuplicateAnswersInAnExtensibleShape() throws Exception {
      when(datasourcesService.checkDuplicate("mongo")).thenReturn(true);

      assertEquals(Boolean.TRUE, controller.checkDuplicate("mongo").get("duplicate"));
   }
}
