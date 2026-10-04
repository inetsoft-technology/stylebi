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
package inetsoft.web.composer.ws.dialog;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.util.ColumnCache;
import inetsoft.uql.util.Config;
import inetsoft.util.credential.CredentialService;
import inetsoft.util.credential.LocalPasswordCredential;
import inetsoft.web.composer.model.ws.*;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.portal.controller.database.*;
import inetsoft.web.portal.model.database.AdvancedSQLQueryModel;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * Bug #77711. Before the fix the editor stored the ALIAS_n generated for a name the database
 * can't take, so a SQL-bound table made in the SQL query dialog's simple mode has a column
 * ALIAS_0. When the dialog rebuilds that table's query (OK in simple mode, or the switch to
 * advanced mode), the rebuilt query stores the name. The old column is mapped from ALIAS_0 to
 * the name, so it keeps its description, groups, aggregates and conditions, and its old name
 * is ALIAS_0 for the rename of its dependents.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SQLQueryDialogGeneratedAliasTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SQLQueryDialogGeneratedAliasTest {
   // over the 28 bytes of isValidAlias, and not the same name
   private static final String LONG = "CUSTOMER_ACCOUNT_OPENING_DATE_LOCAL";
   private static final String LONG2 = "CUSTOMER_ACCOUNT_CLOSING_DATE_LOCAL";
   private static final String WS = "ws-77711";
   private static final String RID = "rq-77711";
   private static final ObjectMapper MAPPER = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

   private static final XRepository REPOSITORY = repositoryOrNull();
   private final Principal principal = () -> "admin";
   private JDBCDataSource ds;
   private RuntimeQueryService rqs;
   private QueryManagerService qms;
   private SQLQueryDialogService service;
   private Worksheet ws;
   private RuntimeWorksheet rws;
   private SQLBoundTableAssembly table;
   private MockedStatic<WorksheetEventUtil> wsEvents;
   private MockedStatic<AssetEventUtil> assetEvents;

   @BeforeEach
   void setUp() throws Exception {
      ds = new JDBCDataSource();
      ds.setName("ds77711dialog" + System.nanoTime());
      ds.setDriver("oracle.jdbc.OracleDriver");
      ds.setURL("jdbc:oracle:thin:@localhost:1521:x");
      ds.setRuntimeProductName("oracle");
      ds.setProductVersion("10.0");

      XRepository repository = REPOSITORY;
      when(repository.getDataSource(ds.getFullName())).thenReturn(ds);
      DataSourceService dss = mock(DataSourceService.class);
      when(dss.getDataSource(ds.getFullName())).thenReturn(ds);
      SecurityEngine security = mock(SecurityEngine.class);
      when(security.checkPermission(any(), any(), any(String.class), any())).thenReturn(true);
      rqs = mock(RuntimeQueryService.class);
      qms = new QueryManagerService(rqs, repository, dss, security, mock(ColumnCache.class));

      ViewsheetService wsEngine = mock(ViewsheetService.class);
      AssetRepository assets = mock(AssetRepository.class);
      when(wsEngine.getAssetRepository()).thenReturn(assets);
      service = new SQLQueryDialogService(wsEngine, qms, mock(QueryGraphModelService.class),
                                          repository, security);

      ws = new Worksheet();
      table = oldTable();
      ws.addAssembly(table);
      rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getVariableTable()).thenReturn(new VariableTable());
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      when(wsEngine.getWorksheet(WS, principal)).thenReturn(rws);

      // the refreshes after the edit need a running worksheet
      wsEvents = mockStatic(WorksheetEventUtil.class);
      assetEvents = mockStatic(AssetEventUtil.class);
   }

   @AfterEach
   void tearDown() {
      wsEvents.close();
      assetEvents.close();
   }

   @Test
   void simpleOkRenamesTheGeneratedAlias() throws Exception {
      openDialog();

      service.setModel(WS, simpleModel(), principal, mock(CommandDispatcher.class));

      assertRenamed();
   }

   // the dialog stays open (Apply), and OK is pressed again before the worksheet is saved
   @Test
   void simpleOkTwice() throws Exception {
      openDialog();

      service.setModel(WS, simpleModel(), principal, mock(CommandDispatcher.class));
      service.setModel(WS, simpleModel(), principal, mock(CommandDispatcher.class));

      assertRenamed();
   }

   @Test
   void advancedSwitchRenamesTheGeneratedAlias() throws Exception {
      openDialog();
      SQLQueryDialogModel model = simpleModel();
      AdvancedSQLQueryModel advanced = qms.convertToAdvancedQueryModel(rws, model, principal);
      model.setAdvancedEdit(true);
      // as the client sends it back
      model.setAdvancedModel(MAPPER.readValue(MAPPER.writeValueAsString(advanced),
                                              AdvancedSQLQueryModel.class));

      service.setModel(WS, model, principal, mock(CommandDispatcher.class));

      assertRenamed();
   }

   // Apply in advanced mode, then OK: the second edit maps the columns the first one made
   @Test
   void advancedApplyThenOk() throws Exception {
      openDialog();
      SQLQueryDialogModel model = advancedModel(simpleModel());

      service.setModel(WS, model, principal, mock(CommandDispatcher.class));
      assertRenamed();
      model.setAdvancedModel(echo(qms.getAdvancedQueryModel(qms.getRuntimeQuery(RID), principal)));
      model.setCloseDialog(true);
      service.setModel(WS, model, principal, mock(CommandDispatcher.class));

      assertRenamed();
   }

   // Apply in simple mode, then switch to advanced mode and OK
   @Test
   void simpleApplyThenAdvancedOk() throws Exception {
      openDialog();

      service.setModel(WS, simpleModel(), principal, mock(CommandDispatcher.class));
      assertRenamed();
      SQLQueryDialogModel model = advancedModel(simpleModel());
      model.setCloseDialog(true);
      service.setModel(WS, model, principal, mock(CommandDispatcher.class));

      assertRenamed();
   }

   // the switch to advanced mode of the dialog, with the model the client sends back
   private SQLQueryDialogModel advancedModel(SQLQueryDialogModel model) throws Exception {
      AdvancedSQLQueryModel advanced = qms.convertToAdvancedQueryModel(rws, model, principal);
      model.setAdvancedEdit(true);
      model.setAdvancedModel(echo(advanced));
      return model;
   }

   private static AdvancedSQLQueryModel echo(AdvancedSQLQueryModel model) throws Exception {
      return MAPPER.readValue(MAPPER.writeValueAsString(model), AdvancedSQLQueryModel.class);
   }

   /**
    * A new table is named by the database's labels of the generated sql, which are the
    * ALIAS_n of the long names. Its columns are named by the stored names, as the result
    * header of the query is, or the merged query loses them.
    */
   @Test
   void newTableIsNamedByTheStoredNames() throws Exception {
      openDialog();
      SQLQueryDialogModel model = simpleModel();
      model.setName("T2");

      service.setModel(WS, model, principal, mock(CommandDispatcher.class));

      SQLBoundTableAssembly created = (SQLBoundTableAssembly) ws.getAssembly("T2");
      ColumnSelection columns = created.getColumnSelection();
      List<String> names = new ArrayList<>();

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         names.add(columns.getAttribute(i).getAttribute());
      }

      // the generated sql sorts its columns, the columns come in the order of the metadata
      Collections.sort(names);
      assertEquals(List.of(LONG2, LONG, "ID"), names);
      UniformSQL sql = (UniformSQL) ((SQLBoundTableAssemblyInfo) created.getInfo()).getQuery()
         .getSQLDefinition();
      assertEquals(LONG, sql.getSelection().getAlias(1));
      assertEquals(LONG2, sql.getSelection().getAlias(2));
   }

   // a valid alias that differs (a typed alias, or the numbering of a twin) is not renamed
   @Test
   void validAliasIsNotMapped() throws Exception {
      UniformSQL sql = createSQL();
      UniformSQL oldSql = createSQL();
      oldSql.getSelection().setAlias(1, "L");
      JDBCQuery old = query(oldSql);
      Map<String, String> mapping = new HashMap<>(Map.of("ID", "ID", "L", "L", LONG2, LONG2));
      sql.getSelection().setAlias(1, "L2");

      qms.mapRebuiltColumnAliases(old, sql, mapping);

      assertEquals(Map.of("ID", "ID", "L", "L", LONG2, LONG2), mapping);
   }

   private void assertRenamed() {
      ColumnSelection columns = table.getColumnSelection();
      List<String> names = new ArrayList<>();

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         names.add(columns.getAttribute(i).getAttribute());
      }

      // the generated sql sorts its columns, the columns come in the order of the metadata
      Collections.sort(names);
      assertEquals(List.of(LONG2, LONG, "ID"), names);
      ColumnRef longCol = (ColumnRef) columns.getAttribute(LONG);
      assertEquals("opening date", longCol.getDescription());
      assertEquals("ALIAS_0", longCol.getOldName());
      ColumnRef long2Col = (ColumnRef) columns.getAttribute(LONG2);
      assertEquals("closing date", long2Col.getDescription());
      assertEquals("ALIAS_1", long2Col.getOldName());

      AggregateInfo info = table.getAggregateInfo();
      assertEquals(1, info.getGroups().length, info.toString());
      assertEquals(LONG, info.getGroups()[0].getDataRef().getAttribute());
      assertEquals(1, info.getAggregates().length, info.toString());
      assertEquals(LONG2, info.getAggregates()[0].getDataRef().getAttribute());

      // the condition follows the column, it would be dropped on the old name
      ConditionList conds = (ConditionList) table.getPreConditionList();
      assertEquals(1, conds.getSize());
      assertEquals(LONG2, conds.getConditionItem(0).getAttribute().getAttribute());
   }

   // what getSqlQueryDialogModel does for the table's runtime query
   private void openDialog() throws Exception {
      SQLBoundTableAssemblyInfo info = (SQLBoundTableAssemblyInfo) table.getInfo();
      RuntimeQueryService.RuntimeXQuery runtimeQuery =
         new RuntimeQueryService.RuntimeXQuery(info.getQuery().clone(), RID, ds.getFullName());
      runtimeQuery.setVariables(new VariableTable());
      runtimeQuery.initQueryAliasMapping();
      when(rqs.getRuntimeQuery(RID)).thenReturn(runtimeQuery);
      assertEquals(Map.of("ID", "ID", "ALIAS_0", "ALIAS_0", "ALIAS_1", "ALIAS_1"),
                   runtimeQuery.getAliasMapping());
   }

   private SQLQueryDialogModel simpleModel() {
      BasicSQLQueryModel simple = new BasicSQLQueryModel();
      simple.setTables(tables());
      simple.setColumns(Arrays.stream(columns())
         .map(c -> SQLQueryDialogColumnModel.builder().name(c).build())
         .toArray(SQLQueryDialogColumnModel[]::new));
      simple.setJoins(new JoinItemModel[0]);
      simple.setConditionList(new ArrayList<>());
      SQLQueryDialogModel model = new SQLQueryDialogModel();
      model.setName(table.getName());
      model.setDataSource(ds.getFullName());
      model.setRuntimeId(RID);
      model.setSimpleModel(simple);
      model.setCloseDialog(false);
      return model;
   }

   // a table saved before the fix: the long names are stored as ALIAS_0 and ALIAS_1
   private SQLBoundTableAssembly oldTable() throws Exception {
      UniformSQL sql = createSQL();
      sql.getSelection().setAlias(1, "ALIAS_0");
      sql.getSelection().setAlias(2, "ALIAS_1");
      SQLBoundTableAssembly assembly = new SQLBoundTableAssembly(ws, "T1");
      SQLBoundTableAssemblyInfo info = (SQLBoundTableAssemblyInfo) assembly.getInfo();
      info.setQuery(query(sql));
      info.setSourceInfo(new SourceInfo(SourceInfo.DATASOURCE, ds.getFullName(), ds.getFullName()));

      ColumnSelection columns = new ColumnSelection();
      columns.addAttribute(new ColumnRef(new AttributeRef("ID")));
      ColumnRef c0 = new ColumnRef(new AttributeRef("ALIAS_0"));
      c0.setDescription("opening date");
      columns.addAttribute(c0);
      ColumnRef c1 = new ColumnRef(new AttributeRef("ALIAS_1"));
      c1.setDescription("closing date");
      columns.addAttribute(c1);

      // as a worksheet that was opened
      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ((ColumnRef) columns.getAttribute(i)).setOldName(columns.getAttribute(i).getAttribute());
      }

      assembly.setColumnSelection(columns);

      AggregateInfo aggregates = new AggregateInfo();
      aggregates.addGroup(new GroupRef(new ColumnRef(new AttributeRef("ALIAS_0"))));
      aggregates.addAggregate(new AggregateRef(new ColumnRef(new AttributeRef("ALIAS_1")),
                                               AggregateFormula.MAX));
      assembly.setAggregateInfo(aggregates);

      AssetCondition condition = new AssetCondition(XSchema.INTEGER);
      condition.setOperation(XCondition.EQUAL_TO);
      condition.addValue(1);
      ConditionList conds = new ConditionList();
      conds.append(new ConditionItem(new ColumnRef(new AttributeRef("ALIAS_1")), condition, 0));
      assembly.setPreConditionList(conds);
      return assembly;
   }

   private JDBCQuery query(UniformSQL sql) {
      JDBCQuery query = new JDBCQuery();
      query.setUserQuery(true);
      query.setDataSource(ds);
      query.setSQLDefinition(sql);
      return query;
   }

   private UniformSQL createSQL() throws Exception {
      return JDBCUtil.createSQL(ds, tables(), columns(), new XJoin[0], new ArrayList<>(), null);
   }

   private static String[] columns() {
      return new String[] { "EMP.ID", "EMP." + LONG, "EMP." + LONG2 };
   }

   private static Map<String, AssetEntry> tables() {
      AssetEntry table = new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.PHYSICAL_TABLE,
                                        "EMP", null);
      table.setProperty("source", "EMP");
      Map<String, AssetEntry> tables = new LinkedHashMap<>();
      // not keyed by the table name, so the columns' types are not looked up
      tables.put("not-a-table", table);
      return tables;
   }

   // the metadata of EMP, for JDBCUtil.fixUniformSQLInfo, and the columns of a generated sql
   // (QueryManagerService.getColumnSelection of a query that wasn't parsed), which are
   // read from a prepared statement on derby as JDBCHandler does
   private static XRepository repository() throws Exception {
      XRepository repository = mock(XRepository.class);
      when(repository.getMetaData(any(), any(), any(), anyBoolean(), any())).thenAnswer(inv -> {
         XNode mtype = inv.getArgument(2);

         if("DBPROPERTIES".equals(mtype.getAttribute("type"))) {
            return new XNode("properties");
         }

         if("SQL".equals(mtype.getAttribute("type"))) {
            return sqlColumns((String) mtype.getAttribute("sql"));
         }

         XTypeNode result = new XTypeNode("Result");

         for(String column : new String[] { "ID", LONG, LONG2 }) {
            result.addChild(XSchema.createPrimitiveType(column, Integer.class));
         }

         XTypeNode meta = new XTypeNode("meta");
         meta.addChild(result);
         return meta;
      });

      return repository;
   }

   private static XRepository repositoryOrNull() {
      try {
         return repository();
      }
      catch(Exception ex) {
         throw new IllegalStateException(ex);
      }
   }

   private static XTypeNode sqlColumns(String sql) throws Exception {
      XTypeNode root = new XTypeNode("table");

      try(Connection conn = DriverManager.getConnection("jdbc:derby:memory:bug77711dialog;create=true")) {
         try(Statement stmt = conn.createStatement()) {
            stmt.execute("create table EMP (ID int, " + LONG + " int, " + LONG2 + " int)");
         }
         catch(SQLException ignore) {
            // created by an earlier test
         }

         try(PreparedStatement stmt = conn.prepareStatement(sql)) {
            ResultSetMetaData meta = stmt.getMetaData();

            for(int i = 1; i <= meta.getColumnCount(); i++) {
               XTypeNode node = XSchema.createPrimitiveType(XSchema.INTEGER);
               node.setName(meta.getColumnLabel(i));
               root.addChild(node, false, false);
            }
         }
      }

      return root;
   }

   // a JDBCDataSource creates its credential when it is constructed, and
   // JDBCUtil.fixUniformSQLInfo looks up the driver type
   @Configuration
   static class Beans {
      @Bean
      Config config() {
         Config config = mock(Config.class);
         when(config.getJDBCType(any())).thenReturn("oracle");
         return config;
      }

      @Bean
      XRepository xRepository() throws Exception {
         return REPOSITORY;
      }

      @Bean
      CredentialService credentialService() {
         CredentialService service = mock(CredentialService.class);
         when(service.createCredential(any(), anyBoolean())).thenAnswer(inv -> new LocalPasswordCredential());
         return service;
      }
   }
}
