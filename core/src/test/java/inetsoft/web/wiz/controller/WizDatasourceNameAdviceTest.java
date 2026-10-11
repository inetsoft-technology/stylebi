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

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.XRepository;
import inetsoft.uql.tabular.TabularDataSource;
import inetsoft.uql.util.Config;
import inetsoft.web.admin.content.database.DatabaseTypeService;
import inetsoft.web.admin.content.database.types.MySQLDatabaseType;
import inetsoft.web.admin.content.repository.DatabaseDatasourcesService;
import inetsoft.web.portal.data.DataSourceBrowserService;
import inetsoft.web.portal.data.DataSourceDefinition;
import inetsoft.web.portal.data.DatasourcesService;
import inetsoft.web.portal.service.datasource.DataSourceStatusService;
import inetsoft.web.wiz.WizControllerErrorHandler;
import inetsoft.web.wiz.model.WizAuthentication;
import inetsoft.web.wiz.model.WizDatabaseDefinition;
import inetsoft.web.wiz.model.WizNetworkLocation;
import inetsoft.web.wiz.request.WizDatabaseCreateRequest;
import inetsoft.web.wiz.request.WizFolderCreateRequest;
import inetsoft.web.wiz.request.WizFolderRenameRequest;
import inetsoft.web.wiz.request.WizTabularCreateRequest;
import inetsoft.web.wiz.service.EndpointCatalogReader;
import inetsoft.web.wiz.service.WorksheetTableService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.security.Principal;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * Bug #78347: a bad data source or folder name must come back as a 400 naming the rule, through
 * {@link WizControllerErrorHandler}. The controllers' own unit tests call the methods directly and
 * so never saw the advice turn their rejection into a 500.
 */
@Tag("core")
class WizDatasourceNameAdviceTest {
   @BeforeEach
   void setUp() throws Exception {
      doReturn(new MySQLDatabaseType())
         .when(databaseTypeService).getDatabaseType(MySQLDatabaseType.TYPE);
      when(securityEngine.checkPermission(any(), any(ResourceType.class), anyString(),
                                          any(ResourceAction.class))).thenReturn(true);
      when(xrepository.getDataSource(anyString())).thenReturn(mock(TabularDataSource.class));

      WizDatabaseController database = new WizDatabaseController(
         dataSourceBrowserService, mock(DataSourceStatusService.class), databaseDatasourcesService,
         databaseTypeService, securityEngine, mock(Config.class), xrepository,
         mock(EndpointCatalogReader.class), datasourcesService);
      WizTabularController tabular = new WizTabularController(
         datasourcesService, securityEngine, xrepository, mock(WorksheetTableService.class));

      mvc = standaloneSetup(database, tabular)
         .setControllerAdvice(new WizControllerErrorHandler())
         .build();
   }

   @Test
   void createDatabaseWithAnInvalidNameAnswersBadRequestNamingTheRule() throws Exception {
      send("/api/wiz/databases/create", new WizDatabaseCreateRequest(null, mysql("my.db")))
         .andExpect(status().isBadRequest())
         .andExpect(jsonPath("$.error").value(containsString("Data source name \"my.db\"")))
         .andExpect(jsonPath("$.error").value(containsString("contain any of")));
      verifyNoInteractions(databaseDatasourcesService);
   }

   @Test
   void createTabularWithAnInvalidNameAnswersBadRequestNamingTheRule() throws Exception {
      send("/api/wiz/tabular/create",
           new WizTabularCreateRequest("folder", tabularDefinition("a<b")))
         .andExpect(status().isBadRequest())
         .andExpect(jsonPath("$.error").value(containsString("Data source name \"a<b\"")));
      verify(datasourcesService, never()).createNewDataSource(any(), anyBoolean(), any());
   }

   // The pre-existing slash rejection was a ResponseStatusException and so a 500 as well.
   @Test
   void createTabularWithASlashAnswersBadRequestRatherThanServerError() throws Exception {
      send("/api/wiz/tabular/create",
           new WizTabularCreateRequest("folder", tabularDefinition("other/mongo")))
         .andExpect(status().isBadRequest())
         .andExpect(jsonPath("$.error").value(containsString("'/'")));
   }

   @Test
   void createFolderWithAnInvalidNameAnswersBadRequestNamingTheRule() throws Exception {
      send("/api/wiz/datasources/folders/create", new WizFolderCreateRequest("Examples", ">"))
         .andExpect(status().isBadRequest())
         .andExpect(jsonPath("$.error").value(containsString("Folder name \">\"")));
      verifyNoInteractions(dataSourceBrowserService);
   }

   @Test
   void createFolderNamedCubesAnswersBadRequest() throws Exception {
      send("/api/wiz/datasources/folders/create", new WizFolderCreateRequest("Examples", "Cubes"))
         .andExpect(status().isBadRequest())
         .andExpect(jsonPath("$.error").value(containsString("Cubes")));
   }

   @Test
   void createFolderWithABlankNameKeepsItsOwnReason() throws Exception {
      send("/api/wiz/datasources/folders/create", new WizFolderCreateRequest("Examples", "  "))
         .andExpect(status().isBadRequest())
         .andExpect(jsonPath("$.error").value("name is required"));
   }

   @Test
   void renameFolderToAnInvalidNameAnswersBadRequest() throws Exception {
      send("/api/wiz/datasources/folders/rename", new WizFolderRenameRequest("Examples/Old", "a|b"))
         .andExpect(status().isBadRequest())
         .andExpect(jsonPath("$.error").value(containsString("Folder name \"a|b\"")));
      verifyNoInteractions(dataSourceBrowserService);
   }

   @Test
   void renamingADatabaseToAnInvalidNameAnswersBadRequest() throws Exception {
      send("/api/wiz/databases/update?path=Examples/orders", mysql("new*name"))
         .andExpect(status().isBadRequest())
         .andExpect(jsonPath("$.error").value(containsString("Data source name \"new*name\"")));
      verifyNoInteractions(databaseDatasourcesService);
   }

   // A stored name that already breaks the rule must stay re-savable unchanged.
   @Test
   void savingALegacyDatabaseNameUnchangedIsNotRejected() throws Exception {
      send("/api/wiz/databases/update?path=Examples/legacy.db", mysql("legacy.db"))
         .andExpect(status().isOk());
      verify(databaseDatasourcesService).getActionName("Examples/legacy.db", "legacy.db");
   }

   @Test
   void renamingATabularSourceToAnInvalidNameAnswersBadRequest() throws Exception {
      send("/api/wiz/tabular/update?path=folder/mongo", tabularDefinition("mo?ngo"))
         .andExpect(status().isBadRequest())
         .andExpect(jsonPath("$.error").value(containsString("Data source name \"mo?ngo\"")));
      verify(datasourcesService, never()).updateDataSource(anyString(), any(), any());
   }

   @Test
   void savingALegacyTabularNameUnchangedIsNotRejected() throws Exception {
      when(datasourcesService.checkDuplicate("folder/old.name")).thenReturn(true);

      send("/api/wiz/tabular/update?path=folder/old.name", tabularDefinition("old.name"))
         .andExpect(status().isOk());
      verify(datasourcesService).checkDuplicate("folder/old.name");
   }

   // Accept as the wiz-services client sends it; the handlers answer a JSON body for it.
   private ResultActions send(String url, Object body) throws Exception {
      return mvc.perform(post(url).principal(principal).contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON)
                            .content(new ObjectMapper().writeValueAsString(body)));
   }

   private static WizDatabaseDefinition mysql(String name) {
      return new WizDatabaseDefinition(
         name, null, MySQLDatabaseType.TYPE, new WizNetworkLocation("db.example", 3306),
         new WizAuthentication(true, "user", null), null, false, -1, 3, null, false, false);
   }

   private static DataSourceDefinition tabularDefinition(String name) {
      DataSourceDefinition definition = new DataSourceDefinition();
      definition.setName(name);
      definition.setType("MongoDB");
      return definition;
   }

   private final DataSourceBrowserService dataSourceBrowserService =
      mock(DataSourceBrowserService.class);
   private final DatabaseDatasourcesService databaseDatasourcesService =
      mock(DatabaseDatasourcesService.class);
   private final DatabaseTypeService databaseTypeService = mock(DatabaseTypeService.class);
   private final SecurityEngine securityEngine = mock(SecurityEngine.class);
   private final XRepository xrepository = mock(XRepository.class);
   private final DatasourcesService datasourcesService = mock(DatasourcesService.class);
   private final Principal principal = mock(Principal.class);
   private MockMvc mvc;
}
