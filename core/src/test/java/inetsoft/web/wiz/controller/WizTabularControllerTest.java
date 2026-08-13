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
import inetsoft.uql.DataSourceListing;
import inetsoft.uql.DataSourceListingService;
import inetsoft.uql.XDataSource;
import inetsoft.uql.XRepository;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.tabular.TabularDataSource;
import inetsoft.util.MessageException;
import inetsoft.web.portal.data.DataSourceDefinition;
import inetsoft.web.portal.data.DatasourcesService;
import inetsoft.web.wiz.model.WizTabularListing;
import inetsoft.web.wiz.model.WizTabularListings;
import inetsoft.web.wiz.model.WizTabularSaveResult;
import inetsoft.web.wiz.request.WizTabularCreateRequest;
import inetsoft.web.wiz.service.UnsupportedDatasourceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

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
   private XRepository xrepository;
   private WizTabularController controller;
   private Principal principal;

   @BeforeEach
   void setUp() throws Exception {
      datasourcesService = mock(DatasourcesService.class);
      securityEngine = mock(SecurityEngine.class);
      xrepository = mock(XRepository.class);
      controller = new WizTabularController(datasourcesService, securityEngine, xrepository);
      principal = mock(Principal.class);

      when(securityEngine.checkPermission(any(), any(ResourceType.class), anyString(),
                                          any(ResourceAction.class))).thenReturn(true);

      // The update path refuses anything that is not tabular, so the stored source has to be one
      // for every test whose subject is something else.
      when(xrepository.getDataSource(anyString())).thenReturn(mock(TabularDataSource.class));
   }

   private DataSourceDefinition definition(String name) {
      DataSourceDefinition definition = new DataSourceDefinition();
      definition.setName(name);
      definition.setType("MongoDB");

      return definition;
   }

   /** A listing of the given name whose data source is, or is not, a tabular one. */
   private DataSourceListing listing(String name, String category, boolean tabular)
      throws Exception
   {
      DataSourceListing listing = mock(DataSourceListing.class);
      when(listing.getName()).thenReturn(name);
      when(listing.getDisplayName()).thenReturn(name + " (translated)");
      when(listing.getCategory()).thenReturn(category);
      when(listing.getIcon()).thenReturn("/icon.svg");
      when(listing.getKeywords()).thenReturn(null);
      when(listing.createDataSource()).thenReturn(
         tabular ? mock(TabularDataSource.class) : mock(JDBCDataSource.class));

      return listing;
   }

   @Test
   void createReturnsTheSavedPath() throws Exception {
      // Absent before the save and present after it, which is what the create path now verifies.
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
    * createNewDataSource writes nothing and throws nothing when the type is unknown or its
    * connector is unavailable. Reported as a success, that sends the editor to a data source that
    * does not exist -- the path it navigates to is the one nothing was written to.
    */
   @Test
   void createReportsAFailureWhenTheSaveWasASilentNoOp() throws Exception {
      when(datasourcesService.checkDuplicate("mongo")).thenReturn(false);

      WizTabularSaveResult result = controller.createTabularDataSource(
         new WizTabularCreateRequest("", definition("mongo")), principal);

      assertFalse(result.ok(), "a create that wrote nothing must not report success");
      assertEquals(WizTabularSaveResult.UNKNOWN, result.reason());
      assertNull(result.path());
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

   // A body that deserializes to a literal null must answer, not throw on the way to the guard.
   @Test
   void createAnswersRatherThanFailingOnANullBody() throws Exception {
      WizTabularSaveResult result = controller.createTabularDataSource(null, principal);

      assertFalse(result.ok());
      assertEquals(WizTabularSaveResult.UNKNOWN, result.reason());
      verifyNoInteractions(datasourcesService);
   }

   @Test
   void createRejectsAMissingName() {
      ResponseStatusException e = assertThrows(
         ResponseStatusException.class, () -> controller.createTabularDataSource(
            new WizTabularCreateRequest("folder", definition("  ")), principal));

      assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
   }

   // A name carrying a slash is a path: without this the source is relocated silently.
   @Test
   void createRejectsANameCarryingASlash() throws Exception {
      ResponseStatusException e = assertThrows(
         ResponseStatusException.class, () -> controller.createTabularDataSource(
            new WizTabularCreateRequest("folder", definition("other/mongo")), principal));

      assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
      verify(datasourcesService, never()).createNewDataSource(any(), anyBoolean(), any());
   }

   /*
    * The portal sends the folder beside the definition, but a caller that set it only on the
    * definition used to land at the repository root with no error at all.
    */
   @Test
   void createFallsBackToTheParentPathOnTheDefinition() throws Exception {
      DataSourceDefinition definition = definition("mongo");
      definition.setParentPath("folder");
      when(datasourcesService.checkDuplicate("folder/mongo")).thenReturn(false, true);

      WizTabularSaveResult result = controller.createTabularDataSource(
         new WizTabularCreateRequest(null, definition), principal);

      assertTrue(result.ok());
      assertEquals("folder/mongo", result.path());
      verify(securityEngine).checkPermission(
         eq(principal), eq(ResourceType.DATA_SOURCE_FOLDER), eq("folder"),
         eq(ResourceAction.WRITE));
   }

   /*
    * LayoutCreator produces a form with nothing in it for a source that has no TabularView, so
    * without this the editor opens a blank page on a JDBC database and offers to save it -- which
    * the update path would then honour by rewriting the database from that empty form.
    */
   @Test
   void definitionRefusesADataSourceThatIsNotTabular() throws Exception {
      JDBCDataSource jdbc = mock(JDBCDataSource.class);
      when(jdbc.getType()).thenReturn(XDataSource.JDBC);
      when(xrepository.getDataSource("folder/orders")).thenReturn(jdbc);

      UnsupportedDatasourceException e = assertThrows(UnsupportedDatasourceException.class,
         () -> controller.getTabularDefinition("folder/orders", principal));

      assertEquals(XDataSource.JDBC, e.getDatasourceType());
      verify(datasourcesService, never()).getDataSourceDefinition(anyString(), any());
   }

   /*
    * A path with nothing behind it is not this guard's business: the service's own lookup answers
    * it, and reporting it as an untypeable data source would be the wrong complaint.
    */
   @Test
   void definitionLeavesAPathWithNothingBehindItToTheService() throws Exception {
      DataSourceDefinition stored = definition("mongo");
      when(xrepository.getDataSource("folder/mongo")).thenReturn(null);
      when(datasourcesService.getDataSourceDefinition("folder/mongo", principal))
         .thenReturn(stored);

      assertSame(stored, controller.getTabularDefinition("folder/mongo", principal));
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

      // The bare name, not the full path: see updateLooksTheSourceUpWhereItActuallyLives.
      verify(datasourcesService).updateDataSource(eq("old"), any(), eq(principal));
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
      verify(datasourcesService).updateDataSource(eq("mongo"), any(), eq(principal));
   }

   /*
    * The one case a mocked service cannot catch by itself. DatasourcesBaseService.updateDataSource
    * does not look the source up by the name it is handed -- it re-prefixes the definition's own
    * parent path first -- so this fake reproduces that arithmetic and asserts the key it lands on.
    * Handed the full path, it looks for "folder/folder/mongo", finds nothing and reports the source
    * lost, which is every folder-scoped edit in the product.
    */
   @Test
   void updateLooksTheSourceUpWhereItActuallyLives() throws Exception {
      AtomicReference<String> lookupKey = new AtomicReference<>();
      when(datasourcesService.checkDuplicate("folder/mongo")).thenReturn(true);
      doAnswer(invocation -> {
         String name = invocation.getArgument(0);
         DataSourceDefinition saved = invocation.getArgument(1);
         String parentPath = "".equals(saved.getParentPath())
            ? "" : saved.getParentPath() + "/";
         lookupKey.set(parentPath + name);

         return null;
      }).when(datasourcesService).updateDataSource(anyString(), any(), any());

      controller.updateTabularDataSource("folder/mongo", definition("mongo"), principal);

      assertEquals("folder/mongo", lookupKey.get(),
                   "the service must end up looking for the data source's own path");
   }

   /*
    * Pointed at a JDBC database the save rebuilds it from the tabular definition, discarding every
    * connection setting it has, and used to report success.
    */
   @Test
   void updateRefusesADataSourceThatIsNotTabular() throws Exception {
      when(datasourcesService.checkDuplicate("folder/orders")).thenReturn(true);
      JDBCDataSource jdbc = mock(JDBCDataSource.class);
      when(jdbc.getType()).thenReturn(XDataSource.JDBC);
      when(xrepository.getDataSource("folder/orders")).thenReturn(jdbc);

      assertThrows(UnsupportedDatasourceException.class, () ->
         controller.updateTabularDataSource("folder/orders", definition("orders"), principal));

      verify(datasourcesService, never()).updateDataSource(anyString(), any(), any());
   }

   @Test
   void updateRejectsAMissingName() throws Exception {
      ResponseStatusException e = assertThrows(
         ResponseStatusException.class, () -> controller.updateTabularDataSource(
            "folder/mongo", definition(null), principal));

      assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
      verify(datasourcesService, never()).updateDataSource(anyString(), any(), any());
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

      assertEquals(7, controller.refreshTabularView(sent, principal).getSequenceNumber());
   }

   @Test
   void checkDuplicateAnswersInAnExtensibleShape() throws Exception {
      when(datasourcesService.checkDuplicate("mongo")).thenReturn(true);

      assertEquals(Boolean.TRUE, controller.checkDuplicate("mongo", principal).get("duplicate"));
   }

   /*
    * JDBC listings have no TabularView to render and belong to the databases editor, and the key is
    * the stable getName() rather than the translated display name.
    */
   @Test
   void listingsOfferTabularTypesOnly() throws Exception {
      // Built before the static stubbing opens: mocking inside it would be read as an attempt to
      // stub the static call itself.
      List<DataSourceListing> offered =
         List.of(listing("MongoDB", "NoSQL", true), listing("MySQL", "Database", false));

      try(MockedStatic<DataSourceListingService> listings =
             mockStatic(DataSourceListingService.class))
      {
         listings.when(() -> DataSourceListingService.getAllDataSourceListings(true))
            .thenReturn(offered);

         WizTabularListings result = controller.getTabularListings(principal);

         assertEquals(List.of("MongoDB"), result.listings().stream()
            .map(WizTabularListing::name).toList());
         assertEquals(List.of("NoSQL"), result.categories());
      }
   }

   @Test
   void listingIsSeededFromTheStableNameAndLookedUpByTheDisplayName() throws Exception {
      DataSourceDefinition seeded = definition("MongoDB");
      List<DataSourceListing> offered = List.of(listing("MongoDB", "NoSQL", true));

      try(MockedStatic<DataSourceListingService> listings =
             mockStatic(DataSourceListingService.class))
      {
         listings.when(() -> DataSourceListingService.getAllDataSourceListings(true))
            .thenReturn(offered);
         when(datasourcesService.getDataSourceFromListing("MongoDB (translated)"))
            .thenReturn(seeded);

         assertSame(seeded, controller.getTabularListing("MongoDB", principal));
      }
   }

   /*
    * Measured: a JDBC name such as "MySQL" reached the service and came back as an opaque 500 with
    * an empty body, logged as a server fault. Asking for a type that does not exist is the caller's
    * mistake.
    */
   @Test
   void listingAnswers404ForANameThatIsNotOffered() throws Exception {
      List<DataSourceListing> offered = List.of(listing("MySQL", "Database", false));

      try(MockedStatic<DataSourceListingService> listings =
             mockStatic(DataSourceListingService.class))
      {
         listings.when(() -> DataSourceListingService.getAllDataSourceListings(true))
            .thenReturn(offered);

         ResponseStatusException e = assertThrows(
            ResponseStatusException.class, () -> controller.getTabularListing("MySQL", principal));

         assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
         verify(datasourcesService, never()).getDataSourceFromListing(anyString());
      }
   }
}
