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
package inetsoft.uql.util;

import inetsoft.test.*;
import inetsoft.uql.XNode;
import inetsoft.uql.erm.XPartition;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.util.JDBCAgent;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.service.XEngine;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #82337: a physical view table stored without a schema (ORDERS) is resolved by the database
 * in its default schema. The listed table nodes of a data source that qualifies its tables carry
 * the schema (SA.ORDERS), so the physical tree used to drop the stored table.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  DefaultMetaDataProviderBareNameTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
@SuppressWarnings("unchecked")
class DefaultMetaDataProviderBareNameTest {
   @Configuration
   static class TestConfig {
      @Bean
      public CredentialService credentialService() throws Exception {
         // package-private constructor, normally reached by component scanning
         Constructor<CredentialService> ctor = CredentialService.class.getDeclaredConstructor();
         ctor.setAccessible(true);
         return ctor.newInstance();
      }

      // XAgent.getAgent() reads Config
      @Bean
      public Config config() {
         return mock(Config.class);
      }
   }

   // the agent of the data source type answers the qualified name (SA.ORDERS); the mocked Config
   // would give the default agent, which answers the bare name and hides the defect
   @BeforeEach
   void installJdbcAgent() throws Exception {
      Field field = XAgent.class.getDeclaredField("agentmap");
      field.setAccessible(true);
      agents = (Map<Object, Object>) field.get(null);
      jdbcType = new JDBCDataSource().getType();
      previousAgent = agents.put(jdbcType, new JDBCAgent());
   }

   @AfterEach
   void restoreAgent() {
      if(previousAgent == null) {
         agents.remove(jdbcType);
      }
      else {
         agents.put(jdbcType, previousAgent);
      }
   }

   @Test
   void bareTableResolvesInDefaultSchema() throws Exception {
      XNode tree = tree("ORDERS", null, "SA");

      assertEquals(List.of("ORDERS"), names(tree));
   }

   @Test
   void bareTableOfAnotherSchemaStaysUnresolved() throws Exception {
      // PUBLIC is not the default schema, so the table must not be guessed
      XNode tree = tree("ORDERS", null, "PUBLIC");

      assertEquals(List.of(), names(tree));
   }

   @Test
   void noDefaultSchemaKeepsTheTableUnresolved() throws Exception {
      XNode tree = tree("ORDERS", null, null);

      assertEquals(List.of(), names(tree));
   }

   @Test
   void tableStoredWithSchemaStillResolves() throws Exception {
      XNode tree = tree("SA.ORDERS", "SA", "SA");

      assertEquals(List.of("SA.ORDERS"), names(tree));
   }

   @Test
   void tableLookupByBareNameResolvesInDefaultSchema() throws Exception {
      XNode node = provider("SA").getTable("ORDERS", null);

      assertNotNull(node);
      assertEquals("ORDERS", node.getName());
      assertEquals("SA", node.getAttribute("schema"));
   }

   @Test
   void tableLookupByBareNameOfAnotherSchemaStaysUnresolved() throws Exception {
      assertNull(provider("PUBLIC").getTable("ORDERS", null));
      assertNull(provider(null).getTable("ORDERS", null));
   }

   @Test
   void tableLookupByQualifiedNameStillResolves() throws Exception {
      XNode node = provider("SA").getTable("SA.ORDERS", null);

      assertNotNull(node);
      assertEquals("ORDERS", node.getName());
   }

   /**
    * @param table         the name of the table stored in the view.
    * @param schema        the schema stored with the table, or null.
    * @param defaultSchema the schema the metadata reports as the default one, or null.
    */
   private XNode tree(String table, String schema, String defaultSchema) throws Exception {
      DefaultMetaDataProvider provider = provider(defaultSchema);
      XPartition partition = new XPartition("Order View");
      partition.addTable(table, null, schema);
      return provider.getMetaData(partition, true, null);
   }

   private DefaultMetaDataProvider provider(String defaultSchema) throws Exception {
      XEngine engine = mock(XEngine.class);
      when(engine.bind(any())).thenAnswer(inv -> inv.getArgument(0));
      when(engine.getMetaData(any(), any(), any(), anyBoolean(), any()))
         .thenAnswer(inv -> answer(inv.getArgument(2), defaultSchema));

      JDBCDataSource xds = new JDBCDataSource();
      xds.setName("orders");
      xds.setDriver("org.h2.Driver");
      xds.setURL("jdbc:h2:mem:bug82337");

      DefaultMetaDataProvider provider = new DefaultMetaDataProvider(engine);
      provider.setDataSource(xds);
      return provider;
   }

   // the mocked engine acts as the database: schemas SA and PUBLIC, ORDERS is in SA
   private XNode answer(XNode query, String defaultSchema) {
      String type = (String) query.getAttribute("type");
      XNode root = new XNode("root");

      if("SCHEMAS".equals(type)) {
         root.setAttribute("hasSchema", "true");

         if(defaultSchema != null) {
            root.setAttribute("defaultSchema", defaultSchema);
         }

         for(String schema : List.of("SA", "PUBLIC")) {
            XNode node = new XNode(schema);
            node.setAttribute("schema", schema);
            root.addChild(node);
         }
      }
      else if("TABLETYPES".equals(type)) {
         root.addChild(new XNode("TABLE"));
      }
      else if("SCHEMATABLES_TABLE".equals(type)) {
         Object schema = query.getAttribute("schema");

         // no schema in the query lists the tables of every schema
         if(schema == null || "SA".equals(schema)) {
            XNode table = new XNode("ORDERS");
            table.setAttribute("schema", "SA");
            root.addChild(table);
         }
      }
      else if(type == null) {
         // the columns of a table
         return new XTypeNode();
      }

      return root;
   }

   private Map<Object, Object> agents;
   private String jdbcType;
   private Object previousAgent;

   private static List<String> names(XNode tree) {
      List<String> names = new ArrayList<>();

      for(int i = 0; i < tree.getChildCount(); i++) {
         names.add(tree.getChild(i).getName());
      }

      return names;
   }
}
