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
package inetsoft.uql.xmla;

import com.sun.net.httpserver.HttpServer;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.VariableTable;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79614: names (catalog, cube, dimension, hierarchy, level, data source info) were
 * concatenated into XMLA request bodies without XML escaping, so a name containing an
 * ampersand or a less-than sign produced a malformed request that the server rejected.
 * Runs the real {@link XMLAHandler} against a local endpoint that strictly parses every
 * request body and answers 500 on malformed XML.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  XMLAHandlerRequestEscapingTest.CredentialConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class XMLAHandlerRequestEscapingTest {
   // XMLADataSource's constructor resolves a CredentialService bean, which
   // BaseTestConfiguration does not provide
   @Configuration
   static class CredentialConfig {
      @Bean
      public CredentialService credentialService() throws Exception {
         Constructor<CredentialService> ctor = CredentialService.class.getDeclaredConstructor();
         ctor.setAccessible(true);
         return ctor.newInstance();
      }
   }

   private static final String[] TEXT_TAGS = {
      "CATALOG_NAME", "Catalog", "DataSourceInfo", "CUBE_NAME", "DIMENSION_UNIQUE_NAME",
      "HIERARCHY_UNIQUE_NAME", "LEVEL_UNIQUE_NAME"
   };

   private HttpServer server;
   private final List<Map<String, String>> received = new CopyOnWriteArrayList<>();
   private final List<String> malformed = new CopyOnWriteArrayList<>();
   private volatile String statementSeen;

   @BeforeEach
   void startServer() throws Exception {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/", ex -> {
         byte[] req = ex.getRequestBody().readAllBytes();
         Map<String, String> values = new HashMap<>();
         byte[] resp;
         int code = 200;

         try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            Document doc = f.newDocumentBuilder().parse(new ByteArrayInputStream(req));

            for(String tag : TEXT_TAGS) {
               NodeList list = doc.getElementsByTagNameNS("*", tag);

               if(list.getLength() > 0) {
                  values.put(tag, list.item(0).getTextContent());
               }
            }

            NodeList stmt = doc.getElementsByTagNameNS("*", "Statement");

            if(stmt.getLength() > 0) {
               statementSeen = stmt.item(0).getTextContent();
            }

            NodeList type = doc.getElementsByTagNameNS("*", "RequestType");
            resp = response(type.getLength() == 0 ? "" : type.item(0).getTextContent(),
                            values.get("CATALOG_NAME")).getBytes(StandardCharsets.UTF_8);
            received.add(values);
         }
         catch(Exception e) {
            malformed.add(new String(req, StandardCharsets.UTF_8));
            code = 500;
            resp = "<fault>malformed</fault>".getBytes(StandardCharsets.UTF_8);
         }

         ex.sendResponseHeaders(code, resp.length);
         ex.getResponseBody().write(resp);
         ex.close();
      });
      server.start();
   }

   @AfterEach
   void stopServer() {
      server.stop(0);
   }

   @ParameterizedTest
   @ValueSource(strings = {
      "R&D", "Sales & Marketing", "a<b", "R&amp;D", "[Dim & <x>].[H]",
      "Café Été (EU)", "Sales-Marketing_2024 (EU)"
   })
   void metadataRequestsAreWellFormedAndCarryNameAsTyped(String name) throws Exception {
      XMLAHandler handler = new XMLAHandler();
      handler.connect(dataSource(name), new VariableTable());

      handler.getMetaData(null);

      assertTrue(malformed.isEmpty(), "malformed request bodies: " + malformed);
      assertTrue(received.size() >= 5,
                 "expected cube/dimension/measure/hierarchy/level requests");

      for(Map<String, String> values : received) {
         for(Map.Entry<String, String> e : values.entrySet()) {
            assertEquals(name, e.getValue(), e.getKey() + " must reach the server as typed");
         }
      }

      assertTrue(received.stream().anyMatch(v -> name.equals(v.get("HIERARCHY_UNIQUE_NAME"))));
   }

   @ParameterizedTest
   @ValueSource(strings = { "R&D", "a<b", "R&amp;D", "Plain" })
   void testDataSourceCatalogRequestIsWellFormed(String name) throws Exception {
      new XMLAHandler().testDataSource(dataSource(name), new VariableTable());

      assertTrue(malformed.isEmpty(), "malformed request bodies: " + malformed);
      assertEquals(name, received.get(0).get("CATALOG_NAME"));
   }

   @ParameterizedTest
   @ValueSource(strings = { "R&D", "a<b", "R&amp;D" })
   void executeCatalogAndDataSourceInfoAreEscapedStatementIsNot(String name) throws Exception {
      XMLAHandler handler = new XMLAHandler();
      handler.connect(dataSource(name), new VariableTable());
      Method m = XMLAHandler.class.getDeclaredMethod(
         "execute", String.class, VariableTable.class, java.security.Principal.class,
         String.class, XMLAHandler.CancelHandler.class);
      m.setAccessible(true);

      // the MDX statement arrives already escaped from MDXHelper and must not be escaped again
      m.invoke(handler, "SELECT [R&amp;D] FROM [c]", new VariableTable(), null, "Tabular",
               null);

      assertTrue(malformed.isEmpty(), "malformed request bodies: " + malformed);
      assertEquals(name, received.get(0).get("Catalog"));
      assertEquals(name, received.get(0).get("DataSourceInfo"));
      assertEquals("SELECT [R&D] FROM [c]", statementSeen);
   }

   private XMLADataSource dataSource(String name) {
      XMLADataSource ds = new XMLADataSource();
      ds.setURL("http://127.0.0.1:" + server.getAddress().getPort() + "/");
      ds.setCatalogName(name);
      ds.setDatasourceInfo(name);
      return ds;
   }

   private static String esc(String s) {
      return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
   }

   /** Minimal XMLA rowset response; the name is echoed back escaped like a real server. */
   private static String response(String type, String name) {
      String n = esc(name);
      String row;

      switch(type) {
      case "DBSCHEMA_CATALOGS":
         row = "<row><CATALOG_NAME>" + n + "</CATALOG_NAME></row>";
         break;
      case "MDSCHEMA_CUBES":
         row = "<row><CUBE_NAME>" + n + "</CUBE_NAME></row>";
         break;
      case "MDSCHEMA_DIMENSIONS":
         row = "<row><DIMENSION_NAME>" + n + "</DIMENSION_NAME><DIMENSION_UNIQUE_NAME>" + n +
            "</DIMENSION_UNIQUE_NAME><DIMENSION_TYPE>3</DIMENSION_TYPE></row>";
         break;
      case "MDSCHEMA_HIERARCHIES":
         row = "<row><HIERARCHY_NAME>" + n + "</HIERARCHY_NAME><HIERARCHY_UNIQUE_NAME>" + n +
            "</HIERARCHY_UNIQUE_NAME></row>";
         break;
      case "MDSCHEMA_LEVELS":
         row = "<row><LEVEL_NAME>" + n + "</LEVEL_NAME><LEVEL_UNIQUE_NAME>" + n +
            "</LEVEL_UNIQUE_NAME><LEVEL_NUMBER>0</LEVEL_NUMBER></row>";
         break;
      default:
         row = "";
      }

      return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><soap:Envelope " +
         "xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>" +
         "<DiscoverResponse xmlns=\"urn:schemas-microsoft-com:xml-analysis\"><return>" +
         "<root xmlns=\"urn:schemas-microsoft-com:xml-analysis:rowset\">" + row +
         "</root></return></DiscoverResponse></soap:Body></soap:Envelope>";
   }
}
