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
package inetsoft.uql.jdbc;

import inetsoft.test.*;
import inetsoft.uql.XNode;
import inetsoft.uql.XRepository;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.util.Config;
import inetsoft.util.Tool;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import java.io.*;
import java.lang.reflect.Constructor;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #82116. Older versions saved a derived table as its sql wrapped in double quotes with
 * issql=false. It was loaded as a physical table name, so opening the worksheet asked the
 * database for the columns of a table named after the sql (an all-schema scan on Oracle).
 * Such a name is now loaded as a derived table; a quoted physical table name is not.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  UniformSQLLegacyQuotedSubQueryTest.TestBeans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLLegacyQuotedSubQueryTest {
   // the text of the reported worksheet, a derived table saved by an older version
   private static final String LEGACY_SQL =
      "SELECT ORDERS.ORDERS.CUSTOMER_ID AS CUSTOMER_ID,\n" +
      "       TO_NUMBER(TO_CHAR(ORDERS.ORDERS.ORDER_DATE,'WW')) AS WEEKOFYEAR \n" +
      "FROM   ORDERS.ORDERS     ";

   @Test
   void legacyQuotedSubQueryIsLoadedAsDerivedTable() throws Exception {
      UniformSQL sql = load(xml("SQL Query2", "\"" + LEGACY_SQL + "\""));
      Object name = sql.getSelectTable(0).getName();

      assertInstanceOf(UniformSQL.class, name);
      assertEquals(LEGACY_SQL, name.toString());

      // saved back as a derived table
      String saved = save(sql);
      assertTrue(saved.contains("<issql><![CDATA[true]]></issql>"), saved);
      assertInstanceOf(UniformSQL.class,
                       load(Tool.parseXML(new StringReader(saved)).getDocumentElement())
                          .getSelectTable(0).getName());

      // the metadata step treats it as a derived table, no table lookup
      assertEquals(List.of(), lookups(sql));
   }

   @Test
   void legacyQuotedSubQueryWithInnerQuotesIsLoadedAsDerivedTable() throws Exception {
      String inner = "select convert(datetime, datepart(\"year\", o.d)) as d \nfrom   o";
      UniformSQL sql = load(xml("orders1", "\"" + inner + "\""));
      Object name = sql.getSelectTable(0).getName();

      assertInstanceOf(UniformSQL.class, name);
      assertEquals(inner, name.toString());
   }

   @ParameterizedTest
   @ValueSource(strings = { "\"Select Items\"", "\"My Table\"", "\"select\"",
                            "\"" })
   void quotedTableNameStaysTable(String table) throws Exception {
      UniformSQL sql = load(xml("t", table));
      Object name = sql.getSelectTable(0).getName();

      assertInstanceOf(String.class, name);
      assertEquals(table, name);
   }

   @Test
   void quotedTableNameIsStillLookedUp() throws Exception {
      UniformSQL sql = load(xml("t", "\"Select Items\""));
      assertEquals(List.of("Select Items"), lookups(sql));
   }

   @Test
   void quotedSegmentsNameStaysTable() throws Exception {
      String table = "\"select a from b\"";
      String xml = xml("t", table).replace("<name>", "<name quotedSegments=\"0\">");
      Object name = load(Tool.parseXML(new StringReader(xml)).getDocumentElement())
         .getSelectTable(0).getName();

      assertEquals(table, name);
   }

   private static String xml(String alias, String name) {
      return "<uniform_sql parse=\"false\"><table>" +
         "<alias><![CDATA[" + alias + "]]></alias>" +
         "<name><![CDATA[" + name + "]]></name>" +
         "<issql><![CDATA[false]]></issql>" +
         "<xlocation><![CDATA[15]]></xlocation><ylocation><![CDATA[15]]></ylocation>" +
         "<xScroll><![CDATA[0]]></xScroll><yScroll><![CDATA[0]]></yScroll>" +
         "</table></uniform_sql>";
   }

   private static UniformSQL load(String xml) throws Exception {
      return load(Tool.parseXML(new StringReader(xml)).getDocumentElement());
   }

   private static UniformSQL load(Element xml) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.parseXML(xml);
      return sql;
   }

   private static String save(UniformSQL sql) {
      StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         sql.writeXML(writer);
      }

      return buffer.toString();
   }

   // the table names the metadata step of JDBCUtil.fixUniformSQLInfo asks for
   private static List<String> lookups(UniformSQL sql) throws Exception {
      JDBCDataSource xds = new JDBCDataSource();
      xds.setName("bug82116");
      xds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      xds.setURL("jdbc:derby:memory:bug82116");
      sql.setDataSource(xds);

      List<String> looked = new ArrayList<>();
      XRepository repository = mock(XRepository.class);
      when(repository.getMetaData(any(), any(), any(), anyBoolean(), any())).thenAnswer(inv -> {
         XNode node = inv.getArgument(2);

         if("DBPROPERTIES".equals(node.getAttribute("type"))) {
            XNode props = new XNode();
            props.setAttribute("hasSchema", "true");
            props.setAttribute("hasCatalog", "false");
            return props;
         }

         looked.add(node.getName());
         return new XNode();
      });

      try {
         JDBCUtil.fixUniformSQLInfo(sql, repository, "u", xds);
      }
      catch(Exception ignore) {
         // the mocked metadata is incomplete after the lookup, only the lookups are checked
      }

      return looked;
   }

   @Configuration
   static class TestBeans {
      @Bean
      public CredentialService credentialService() throws Exception {
         Constructor<CredentialService> ctor = CredentialService.class.getDeclaredConstructor();
         ctor.setAccessible(true);
         return ctor.newInstance();
      }

      @Bean
      public XRepository repository() throws Exception {
         XRepository repository = mock(XRepository.class);
         when(repository.getMetaData(any(), any(), any(), anyBoolean(), any()))
            .thenReturn(new XNode());
         return repository;
      }

      @Bean
      public Config config() {
         Config config = mock(Config.class);
         when(config.getJDBCType(anyString())).thenReturn("Derby");
         return config;
      }
   }
}
