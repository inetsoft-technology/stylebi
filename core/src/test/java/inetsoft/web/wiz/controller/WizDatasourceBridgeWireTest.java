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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.report.internal.Util;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.XRepository;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.xmla.XMLADataSource;
import inetsoft.web.admin.content.database.AuthenticationDetails;
import inetsoft.web.admin.content.database.DatabaseDefinition;
import inetsoft.web.admin.content.database.DatabaseTypeService;
import inetsoft.web.admin.content.database.NetworkLocation;
import inetsoft.web.admin.content.database.types.MySQLDatabaseType;
import inetsoft.web.admin.content.repository.DatabaseDatasourcesService;
import inetsoft.web.admin.security.ConnectionStatus;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.portal.data.DataSourceXmlaDefinition;
import inetsoft.web.portal.service.datasource.XmlaDatasourceService;
import inetsoft.web.wiz.WizControllerErrorHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The new bridge endpoints through MockMvc with {@code WizControllerErrorHandler}: that the request
 * JSON binds to the records, that the responses have the published shape, and that a denial is the
 * generic 403 the plugin expects. This is also the source of the request/response JSON listed in
 * {@code 04-build-g.md}.
 */
@Tag("core")
class WizDatasourceBridgeWireTest {
   @BeforeEach
   void setUp() throws Exception {
      allow = true;
      when(security.checkPermission(any(), any(ResourceType.class), anyString(),
                                    any(ResourceAction.class))).thenAnswer(inv -> allow);
      doReturn(new MySQLDatabaseType()).when(types).getDatabaseType(MySQLDatabaseType.TYPE);
      when(repository.getDataSource("olap/sales")).thenReturn(mock(XMLADataSource.class));
      when(repository.getDataSource("db/orders")).thenReturn(mock(JDBCDataSource.class));
      when(databaseService.checkAdditionalPermission(anyString(), anyString(), any(), any()))
         .thenReturn(true);

      Principal principal = () -> "tester";
      mvc = MockMvcBuilders.standaloneSetup(
            new WizXmlaController(xmlaService, security, repository),
            new WizAdditionalConnectionController(databaseService, types, security, repository),
            new WizDatasourceMetadataController(repository, mock(DataSourceService.class), security))
         .setControllerAdvice(new WizControllerErrorHandler())
         .setPatternParser(null)
         .defaultRequest(get("/").principal(principal))
         .build();
   }

   @Test
   void xmlaDefinitionHasThePublishedShapeAndNoPasswordValue() throws Exception {
      DataSourceXmlaDefinition model = new DataSourceXmlaDefinition();
      model.setName("sales");
      model.setUrl("http://olap.example/xmla");
      model.setLogin(true);
      model.setUser("analyst");
      model.setPassword("secret");
      when(xmlaService.getDataSourceModel(eq("olap/sales"), any())).thenReturn(model);

      JsonNode body = json(mvc.perform(get("/api/wiz/xmla/definition").param("path", "olap/sales"))
                              .andExpect(status().isOk()).andReturn());

      assertEquals("sales", body.get("name").asText());
      assertEquals("http://olap.example/xmla", body.get("url").asText());
      assertTrue(body.get("login").asBoolean());
      assertEquals("analyst", body.get("authentication").get("userName").asText());
      assertTrue(body.get("authentication").get("password").isNull());
      assertTrue(body.has("deletable"));
      assertFalse(body.toString().contains("secret"));
   }

   @Test
   void xmlaCreateBindsTheRequestAndAnswersOkReasonPath() throws Exception {
      when(xmlaService.checkDuplicate("olap/new")).thenReturn(false, true);

      JsonNode body = json(mvc.perform(post("/api/wiz/xmla/create")
         .contentType(MediaType.APPLICATION_JSON)
         .content("{\"parentPath\":\"olap\",\"definition\":{\"name\":\"new\",\"url\":\"http://x\"," +
                  "\"login\":true,\"authentication\":{\"userName\":\"u\",\"password\":\"p\"}," +
                  "\"useCredential\":false}}"))
         .andExpect(status().isOk()).andReturn());

      assertTrue(body.get("ok").asBoolean());
      assertEquals("olap/new", body.get("path").asText());
      assertTrue(body.get("reason").isNull());
   }

   @Test
   void xmlaCatalogsAndTestHaveThePublishedShapes() throws Exception {
      when(xmlaService.getCatalogs(any())).thenReturn(List.of("Sales", "HR"));
      when(xmlaService.testConnect(any())).thenReturn(new ConnectionStatus("connected", true));
      String request = "{\"definition\":{\"name\":\"n\",\"url\":\"http://x\"}}";

      JsonNode catalogs = json(mvc.perform(post("/api/wiz/xmla/catalogs")
         .contentType(MediaType.APPLICATION_JSON).content(request)).andReturn());
      JsonNode test = json(mvc.perform(post("/api/wiz/xmla/test")
         .contentType(MediaType.APPLICATION_JSON).content(request)).andReturn());

      assertEquals("Sales", catalogs.get("catalogs").get(0).asText());
      assertTrue(test.get("connected").asBoolean());
      assertEquals("connected", test.get("message").asText());
   }

   @Test
   void aDenialIsTheGeneric403TheBridgeAlwaysSends() throws Exception {
      allow = false;

      mvc.perform(get("/api/wiz/xmla/definition").param("path", "olap/sales"))
         .andExpect(status().isForbidden());
      mvc.perform(post("/api/wiz/databases/additional/add").param("path", "db/orders")
         .contentType(MediaType.APPLICATION_JSON).content("{\"definition\":{\"name\":\"a\"}}"))
         .andExpect(status().isForbidden());
      mvc.perform(post("/api/wiz/datasources/refresh-metadata")
         .contentType(MediaType.APPLICATION_JSON).content("{\"path\":\"db/orders\"}"))
         .andExpect(status().isForbidden());
   }

   @Test
   void aWrongKindPathIs422AndAMissingOneIs404() throws Exception {
      mvc.perform(get("/api/wiz/xmla/definition").param("path", "db/orders"))
         .andExpect(status().isUnprocessableEntity());
      mvc.perform(get("/api/wiz/xmla/definition").param("path", "olap/none"))
         .andExpect(status().isNotFound());
      mvc.perform(get("/api/wiz/databases/additional").param("path", "olap/sales"))
         .andExpect(status().isUnprocessableEntity());
   }

   @Test
   void additionalConnectionsListAddUpdateRemoveAndTestHaveThePublishedShapes() throws Exception {
      DatabaseDefinition stored = new DatabaseDefinition();
      stored.setName("reports");
      stored.setOldName("reports");
      stored.setType(MySQLDatabaseType.TYPE);
      AuthenticationDetails auth = new AuthenticationDetails();
      auth.setRequired(true);
      auth.setUserName("svc");
      auth.setPassword(Util.PLACEHOLDER_PASSWORD);
      stored.setAuthentication(auth);
      stored.setInfo(new MySQLDatabaseType().createDatabaseInfo());
      stored.setNetwork(new NetworkLocation());
      when(databaseService.getAdditionalDatabaseDefinition(eq("db/orders"), any()))
         .thenReturn(new DatabaseDefinition[]{ stored });
      // A fresh base each time, as the service returns: save() renames the object it is handed.
      when(databaseService.getDatabaseDefinition(eq("db/orders"), any())).thenAnswer(inv -> {
         DatabaseDefinition base = new DatabaseDefinition();
         base.setName("orders");
         base.setType(MySQLDatabaseType.TYPE);
         base.setAuthentication(auth);
         base.setInfo(new MySQLDatabaseType().createDatabaseInfo());
         base.setNetwork(new NetworkLocation());
         return base;
      });
      when(databaseService.testDataSourceConnection(anyString(), any(DatabaseDefinition.class),
                                                    any(), anyBoolean()))
         .thenReturn(new ConnectionStatus("ok", true));
      String definition = "{\"name\":\"warehouse\",\"type\":\"MYSQL\",\"network\":" +
         "{\"hostName\":\"h\",\"portNumber\":3306},\"authentication\":{\"required\":true," +
         "\"userName\":\"svc\"},\"info\":null,\"ansiJoin\":false,\"transactionIsolation\":-1," +
         "\"tableNameOption\":3,\"changeDefaultDB\":false,\"deletable\":false}";

      JsonNode list = json(mvc.perform(get("/api/wiz/databases/additional").param("path", "db/orders"))
                              .andExpect(status().isOk()).andReturn());
      JsonNode added = json(mvc.perform(post("/api/wiz/databases/additional/add")
         .param("path", "db/orders").contentType(MediaType.APPLICATION_JSON)
         .content("{\"definition\":" + definition + "}")).andExpect(status().isOk()).andReturn());
      JsonNode updated = json(mvc.perform(post("/api/wiz/databases/additional/update")
         .param("path", "db/orders").param("name", "reports").contentType(MediaType.APPLICATION_JSON)
         .content("{\"definition\":" + definition + "}")).andExpect(status().isOk()).andReturn());
      JsonNode removed = json(mvc.perform(post("/api/wiz/databases/additional/remove")
         .param("path", "db/orders").contentType(MediaType.APPLICATION_JSON)
         .content("{\"names\":[\"reports\"],\"force\":false}"))
         .andExpect(status().isOk()).andReturn());
      JsonNode tested = json(mvc.perform(post("/api/wiz/databases/additional/test")
         .param("path", "db/orders").contentType(MediaType.APPLICATION_JSON)
         .content("{\"name\":\"reports\",\"definition\":" + definition + "}"))
         .andExpect(status().isOk()).andReturn());

      JsonNode first = list.get("connections").get(0);
      assertEquals("reports", first.get("name").asText());
      assertTrue(first.get("definition").get("authentication").get("password").isNull());
      assertTrue(first.has("deletable"));
      assertTrue(added.get("ok").asBoolean());
      assertEquals("db/orders/warehouse", added.get("path").asText());
      assertTrue(updated.get("ok").asBoolean());
      assertEquals("reports", removed.get("results").get(0).get("name").asText());
      assertTrue(removed.get("results").get(0).get("ok").asBoolean());
      assertTrue(tested.get("connected").asBoolean());
   }

   @Test
   void refreshMetadataAnswersOkReasonMessage() throws Exception {
      JsonNode body = json(mvc.perform(post("/api/wiz/datasources/refresh-metadata")
         .contentType(MediaType.APPLICATION_JSON).content("{\"path\":\"db/orders\"}"))
         .andExpect(status().isOk()).andReturn());

      assertTrue(body.get("ok").asBoolean());
      assertTrue(body.get("reason").isNull());
      assertTrue(body.get("message").isNull());
   }

   private static JsonNode json(MvcResult result) throws Exception {
      return new ObjectMapper().readTree(result.getResponse().getContentAsString());
   }

   private boolean allow;
   private MockMvc mvc;
   private final XmlaDatasourceService xmlaService = mock(XmlaDatasourceService.class);
   private final DatabaseDatasourcesService databaseService = mock(DatabaseDatasourcesService.class);
   private final DatabaseTypeService types = mock(DatabaseTypeService.class);
   private final SecurityEngine security = mock(SecurityEngine.class);
   private final XRepository repository = mock(XRepository.class);
}
