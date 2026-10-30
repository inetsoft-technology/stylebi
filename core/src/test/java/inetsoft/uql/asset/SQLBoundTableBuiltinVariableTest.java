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
package inetsoft.uql.asset;

import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.credential.CredentialService;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;
import java.sql.Connection;
import java.sql.Statement;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Bug #79841. A built-in variable ($(_USER_), $(_ROLES_), ...) in the where clause of a
 * sql edited worksheet table is never in the prompted variables of the query, so its lookup
 * in SQLBoundTableAssembly.fixConditionVariable returned null and the variables of the
 * worksheet failed with a NullPointerException once the list wasn't empty (another variable
 * in the sql, or a worksheet condition with a variable).
 * <p>
 * The table is built the way the sql query dialog builds it: the sql string is parsed, and
 * the columns are fixed from the metadata of a Derby database, through the real XEngine.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  SQLBoundTableBuiltinVariableTest.Credentials.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SQLBoundTableBuiltinVariableTest {
   private static final String DB = "memory:bug79841";
   private static final String SELECT = "select STATE, CITY from CUSTOMERS where ";

   @Configuration
   static class Credentials {
      @Bean
      public CredentialService credentialService() throws Exception {
         Constructor<CredentialService> ctor = CredentialService.class.getDeclaredConstructor();
         ctor.setAccessible(true);
         return ctor.newInstance();
      }
   }

   @Autowired
   ConnectionPoolFactory connectionPoolFactory;

   @Autowired
   DataSourceRegistry registry;

   @BeforeAll
   static void createTable() throws Exception {
      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         try {
            stmt.executeUpdate("drop table CUSTOMERS");
         }
         catch(Exception ignore) {
            // first run
         }

         stmt.executeUpdate("create table CUSTOMERS (STATE varchar(20), CITY varchar(20))");
      }
   }

   @BeforeEach
   void setUp() throws Exception {
      when(connectionPoolFactory.getConnectionPool(any(), any())).thenReturn(derby());
      registry.setDataSource(dataSource(), false);
   }

   // = $(_USER_) with another prompt variable, the column of the condition is selected
   @Test
   void equalBuiltinWithAnotherVariable() throws Exception {
      Worksheet ws = worksheet(SELECT + "STATE = $(_USER_) and CITY = $(city)");

      assertEquals(List.of("city"), names(ws.getAllVariables()));
   }

   // IN ($(_ROLES_)) with another prompt variable
   @Test
   void inBuiltinWithAnotherVariable() throws Exception {
      Worksheet ws = worksheet(SELECT + "STATE in ($(_ROLES_)) and CITY = $(city)");

      assertEquals(List.of("city"), names(ws.getAllVariables()));
   }

   // other built-ins fail the same way
   @Test
   void otherBuiltinsWithAnotherVariable() throws Exception {
      assertEquals(List.of("city"), names(worksheet(
         SELECT + "STATE = $(_TODAY) and CITY = $(city)").getAllVariables()));
      assertEquals(List.of("city"), names(worksheet(
         SELECT + "STATE in ($(_GROUPS_)) and CITY = $(city)").getAllVariables()));
   }

   // the only variable of the sql is a built-in, the list isn't empty because of a variable
   // of a worksheet condition on the table
   @Test
   void builtinWithWorksheetConditionVariable() throws Exception {
      for(String where : new String[] { "STATE = $(_USER_)", "STATE in ($(_ROLES_))" }) {
         Worksheet ws = worksheet(SELECT + where);
         SQLBoundTableAssembly table = (SQLBoundTableAssembly) ws.getAssembly("T1");
         AssetCondition cond = new AssetCondition();
         cond.setOperation(XCondition.EQUAL_TO);
         cond.addValue("$(wsvar)");
         ConditionList conds = new ConditionList();
         conds.append(new ConditionItem(new AttributeRef(null, "CITY"), cond, 0));
         table.setPreConditionList(conds);

         assertEquals(List.of("wsvar"), names(ws.getAllVariables()), where);
      }
   }

   // a variable of the query is still fixed: it takes multiple values when used with IN
   @Test
   void promptVariableIsStillFixed() throws Exception {
      UserVariable[] vars = worksheet(SELECT + "STATE = $(_USER_) and CITY in ($(city))")
         .getAllVariables();
      assertEquals(List.of("city"), names(vars));
      assertTrue(vars[0].isMultipleSelection());

      vars = worksheet(SELECT + "CITY = $(city) and STATE in ($(_ROLES_))").getAllVariables();
      assertEquals(List.of("city"), names(vars));
      assertFalse(vars[0].isMultipleSelection());
   }

   // a worksheet with a table made in the sql query dialog from the sql string
   private static Worksheet worksheet(String text) throws Exception {
      JDBCDataSource ds = dataSource();
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);

      // as SQLQueryDialogService.setUpTableWithSQLString parses the sql string
      synchronized(sql) {
         sql.setParseSQL(true);
         sql.setSQLString(text, true);
         sql.wait(30000);
      }

      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      assertNotNull(sql.getWhere(), text);
      // the metadata step of the dialog, without it the fields of the sql are empty
      JDBCUtil.fixUniformSQLInfo(sql, XRepository.getRepository(), null, ds);
      assertTrue(sql.getFieldList().length > 0, text);

      JDBCQuery query = new JDBCQuery();
      query.setName("bug79841");
      query.setUserQuery(true);
      query.setDataSource(ds);
      query.setSQLDefinition(sql);

      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly table = new SQLBoundTableAssembly(ws, "T1");
      SQLBoundTableAssemblyInfo info = (SQLBoundTableAssemblyInfo) table.getTableInfo();
      info.setQuery(query);
      info.setSourceInfo(new SourceInfo(SourceInfo.PHYSICAL_TABLE, ds.getName(), ds.getName()));
      table.setSQLEdited(true);
      ws.addAssembly(table);
      return ws;
   }

   private static List<String> names(UserVariable[] vars) {
      List<String> names = new ArrayList<>();

      for(UserVariable var : vars) {
         names.add(var.getName());
      }

      return names;
   }

   private static JDBCDataSource dataSource() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug79841");
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:" + DB);
      ds.setRequireLogin(false);
      return ds;
   }

   private static EmbeddedDataSource derby() {
      EmbeddedDataSource ds = new EmbeddedDataSource();
      ds.setDatabaseName(DB);
      ds.setCreateDatabase("create");
      return ds;
   }
}
