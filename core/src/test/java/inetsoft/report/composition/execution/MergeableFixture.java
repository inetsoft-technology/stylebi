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
package inetsoft.report.composition.execution;

import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XNode;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Builds the worksheet tables of the tests of the push down rules of {@link PreAssetQuery}: a SQL
 * bound table on a data source of a given database, with a parsed SQL, as the composer builds it.
 * The rules only look at the definition of the data source (the SQL helper of its driver), no
 * connection is opened and no SQL is executed.
 */
final class MergeableFixture {
   /** The version of the database that the mocked repository reports. */
   private static final ThreadLocal<String> REPORTED_VERSION = new ThreadLocal<>();

   private MergeableFixture() {
   }

   /** The beans a SQL bound query needs: the credential service, the driver types, a cache. */
   @Configuration
   @Import(CredentialService.class)
   static class Config {
      @Bean
      public inetsoft.uql.util.Config config(Plugins plugins) {
         return new inetsoft.uql.util.Config(plugins);
      }

      // the SQL helper asks the repository for the version of the database when the data source
      // has none (Oracle, MySQL), and always for Derby
      @Bean
      public XRepository repository() throws Exception {
         XRepository repository = mock(XRepository.class);
         when(repository.getMetaData(any(), any(), any(), anyBoolean(), any()))
            .thenAnswer(inv -> {
               String version = REPORTED_VERSION.get();

               if(version == null) {
                  return null;
               }

               XNode node = new XNode();
               node.setAttribute("DBProductVersion", version);
               return node;
            });
         return repository;
      }

      @Bean
      @SuppressWarnings("unchecked")
      public AssetDataCache assetDataCache() {
         ObjectProvider<DistributedTableCacheStore> provider = mock(ObjectProvider.class);
         when(provider.getObject()).thenReturn(mock(DistributedTableCacheStore.class));
         return new AssetDataCache(mock(DataSourceRegistry.class), provider);
      }
   }

   /** A data source of a database; its SQL helper is checked to be the one of that database. */
   static JDBCDataSource dataSource(String type) {
      return dataSource(type, null);
   }

   /**
    * The same, with the version that the database reports when it is given: the data source then
    * has no version, so the SQL helper asks the repository for it, as it does for a database that
    * is connected.
    */
   static JDBCDataSource dataSource(String type, String version) {
      if(version == null) {
         REPORTED_VERSION.remove();
      }
      else {
         REPORTED_VERSION.set(version);
      }

      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("mergeable_" + type.replace(' ', '_'));
      ds.setProductVersion("19.0");
      ds.setRequireLogin(false);

      switch(type) {
      case "derby" -> {
         ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
         ds.setURL("jdbc:derby:memory:mergeable");
         ds.setProductVersion("10.17");
      }
      case "h2" -> {
         ds.setDriver("org.h2.Driver");
         ds.setURL("jdbc:h2:mem:mergeable");
      }
      case "postgresql" -> {
         ds.setDriver("org.postgresql.Driver");
         ds.setURL("jdbc:postgresql://localhost:5432/test");
      }
      case "mysql" -> {
         ds.setDriver("com.mysql.cj.jdbc.Driver");
         ds.setURL("jdbc:mysql://localhost/test");
         ds.setProductVersion("8.0");
      }
      case "oracle" -> {
         ds.setDriver("oracle.jdbc.OracleDriver");
         ds.setURL("jdbc:oracle:thin:@localhost:1521:test");
      }
      case "sql server" -> {
         ds.setDriver("com.microsoft.sqlserver.jdbc.SQLServerDriver");
         ds.setURL("jdbc:sqlserver://localhost:1433;databaseName=test");
      }
      case "sybase" -> {
         ds.setDriver("net.sourceforge.jtds.jdbc.Driver");
         ds.setURL("jdbc:jtds:sybase://localhost/test");
      }
      case "sqlite" -> {
         ds.setDriver("org.sqlite.JDBC");
         ds.setURL("jdbc:sqlite:test.db");
      }
      case "informix" -> {
         ds.setDriver("com.informix.jdbc.IfxDriver");
         ds.setURL("jdbc:informix-sqli://localhost:9088/test:INFORMIXSERVER=ol");
      }
      default -> throw new IllegalArgumentException(type);
      }

      if(version != null) {
         ds.setProductVersion(null);
      }

      // SQLite has no helper of its own, the product knows it by its driver
      assertEquals("sqlite".equals(type) ? "default" : type,
                   SQLHelper.getSQLHelper(ds).getSQLHelperType(), "helper for " + type);
      return ds;
   }

   static ColumnRef column(String name, String type) {
      ColumnRef column = new ColumnRef(new AttributeRef("a", name));
      column.setDataType(type);
      return column;
   }

   static ColumnRef integer(String name) {
      return column(name, XSchema.INTEGER);
   }

   static ColumnRef string(String name) {
      return column(name, XSchema.STRING);
   }

   /** A column that a script computes from the other columns. */
   static ColumnRef scriptColumn(String name, String expression) {
      ExpressionRef exp = new ExpressionRef();
      exp.setName(name);
      exp.setExpression(expression);
      ColumnRef column = new ColumnRef(exp);
      column.setDataType(XSchema.INTEGER);
      // an expression column is a SQL expression by default; this one is a script
      column.setSQL(false);
      return column;
   }

   /** A table T1 with its worksheet, the data source and the columns it selects. */
   static final class Built {
      final Worksheet ws = new Worksheet();
      final SQLBoundTableAssembly table;
      final JDBCDataSource ds;

      private Built(String type, String version, ColumnRef... columns) throws Exception {
         ds = dataSource(type, version);
         table = newTable("T1", ds, columns);
      }

      /**
       * Another table of the worksheet, on a database; with the same type of database it has the
       * same data source as T1.
       */
      SQLBoundTableAssembly addTable(String name, String type, ColumnRef... columns)
         throws Exception
      {
         return newTable(name, dataSource(type, null), columns);
      }

      /**
       * Another table of the worksheet on a data source with the same name as the one of T1 but
       * another definition (here another URL), as a data source that was changed.
       */
      SQLBoundTableAssembly addTableOfChangedDataSource(String name, String type, String url,
                                                        ColumnRef... columns)
         throws Exception
      {
         JDBCDataSource other = dataSource(type, null);
         other.setURL(url);
         return newTable(name, other, columns);
      }

      private SQLBoundTableAssembly newTable(String name, JDBCDataSource ds,
                                             ColumnRef... columns)
         throws Exception
      {
         // the columns are the columns of the table a, as of a table bound to a physical table
         UniformSQL usql = new UniformSQL();
         usql.setDataSource(ds);
         usql.addTable("a", "a");
         JDBCSelection jselection = (JDBCSelection) usql.getSelection();

         for(ColumnRef column : columns) {
            // an expression column is computed, it is not a column of the table
            if(column.isExpression()) {
               continue;
            }

            String path = "a." + column.getAttribute();
            jselection.addColumn(path);
            jselection.setTable(path, "a");
         }

         JDBCQuery query = new JDBCQuery();
         query.setName("mergeable_" + name);
         query.setDataSource(ds);
         query.setSQLDefinition(usql);

         SQLBoundTableAssembly table = new SQLBoundTableAssembly(ws, name);
         SQLBoundTableAssemblyInfo info = (SQLBoundTableAssemblyInfo) table.getTableInfo();
         info.setQuery(query);
         info.setSourceInfo(
            new SourceInfo(SourceInfo.PHYSICAL_TABLE, ds.getName(), ds.getName()));

         ColumnSelection selection = new ColumnSelection();

         for(ColumnRef column : columns) {
            selection.addAttribute((ColumnRef) column.clone());
         }

         table.setColumnSelection(selection, false);
         table.setColumnSelection(selection, true);
         ws.addAssembly(table);
         return table;
      }

      /**
       * A table whose SQL was written by the user (as the dialog of a SQL bound table makes it),
       * on the database. The SQL is parsed, or not.
       */
      SQLBoundTableAssembly edited(String name, String type, String sql, boolean parse)
         throws Exception
      {
         JDBCDataSource ds = dataSource(type, null);
         UniformSQL usql = new UniformSQL();
         usql.setDataSource(ds);

         if(parse) {
            // the parse runs in another thread, as when the SQL of a table is edited
            synchronized(usql) {
               usql.setParseSQL(true);
               usql.setSQLString(sql, true);
               usql.wait(10000);
            }
         }
         else {
            usql.setParseSQL(false);
            usql.setSQLString(sql, false);
         }

         JDBCQuery query = new JDBCQuery();
         query.setName("mergeable_" + name);
         query.setUserQuery(true);
         query.setDataSource(ds);
         query.setSQLDefinition(usql);

         SQLBoundTableAssembly table = new SQLBoundTableAssembly(ws, name);
         SQLBoundTableAssemblyInfo info = (SQLBoundTableAssemblyInfo) table.getTableInfo();
         info.setQuery(query);
         info.setSourceInfo(
            new SourceInfo(SourceInfo.PHYSICAL_TABLE, ds.getName(), ds.getName()));
         table.setSQLEdited(true);

         ColumnSelection selection = new ColumnSelection();
         selection.addAttribute(integer("k"));
         selection.addAttribute(integer("v"));
         table.setColumnSelection(selection, false);
         table.setColumnSelection(selection, true);
         ws.addAssembly(table);
         return table;
      }

      /** A table with its data in the worksheet: the columns k and v and two rows. */
      EmbeddedTableAssembly embedded(String name) {
         EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, name);
         table.setEmbeddedData(new XEmbeddedTable(
            new String[] { XSchema.INTEGER, XSchema.INTEGER },
            new Object[][] { { "k", "v" }, { 1, 10 }, { 2, 20 } }));
         ws.addAssembly(table);
         return table;
      }

      /** The table that mirrors another one. */
      MirrorTableAssembly mirror(String name, TableAssembly base) {
         MirrorTableAssembly mirror = new MirrorTableAssembly(ws, name, base);
         ws.addAssembly(mirror);
         mirror.update();
         return mirror;
      }

      /** {@code left} joined with {@code right} on their first columns, with the operation. */
      RelationalJoinTableAssembly join(String name, int operation, TableAssembly left,
                                       TableAssembly right) throws Exception
      {
         TableAssemblyOperator operator = new TableAssemblyOperator();
         TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
         op.setOperation(operation);
         op.setLeftTable(left.getName());
         op.setRightTable(right.getName());
         op.setLeftAttribute(left.getColumnSelection(false).getAttribute(0));
         op.setRightAttribute(right.getColumnSelection(false).getAttribute(0));
         operator.addOperator(op);
         RelationalJoinTableAssembly join = new RelationalJoinTableAssembly(
            ws, name, new TableAssembly[] { left, right },
            new TableAssemblyOperator[] { operator });
         ws.addAssembly(join);
         join.update();
         initColumns(join);
         return join;
      }

      /** {@code top} and {@code bottom} put together: a union, an intersect or a minus. */
      ConcatenatedTableAssembly concatenate(String name, int operation, TableAssembly top,
                                            TableAssembly bottom) throws Exception
      {
         TableAssemblyOperator operator = new TableAssemblyOperator();
         TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
         op.setOperation(operation);
         op.setLeftTable(top.getName());
         op.setRightTable(bottom.getName());
         operator.addOperator(op);
         ConcatenatedTableAssembly table = new ConcatenatedTableAssembly(
            ws, name, new TableAssembly[] { top, bottom },
            new TableAssemblyOperator[] { operator });
         ws.addAssembly(table);
         table.update();
         initColumns(table);
         return table;
      }

      /** Give a composed table the default column selection of its bases, as the composer does. */
      private void initColumns(TableAssembly table) throws Exception {
         AssetQuerySandbox box = new AssetQuerySandbox(ws);

         try {
            AssetEventUtil.initColumnSelection(box, table);
         }
         finally {
            box.dispose();
         }
      }

      /** The query of any table of the worksheet, as the worksheet makes it. */
      AssetQuery queryOf(TableAssembly table) throws Exception {
         return AssetQuery.createAssetQuery(table, AssetQuerySandbox.RUNTIME_MODE,
                                            new AssetQuerySandbox(ws), false, -1L, true, true);
      }

      /** A new query of the table: the answer of a query is kept after the first question. */
      SQLBoundQuery query() throws Exception {
         return new SQLBoundQuery(AssetQuerySandbox.RUNTIME_MODE, new AssetQuerySandbox(ws),
                                  table, false, false);
      }
   }

   /** A table of the columns k, v (integers) and name (string) of the table a. */
   static Built simple(String type) throws Exception {
      return simple(type, null);
   }

   static Built simple(String type, String version) throws Exception {
      return new Built(type, version, integer("k"), integer("v"), string("name"));
   }

   static Built build(String type, ColumnRef... columns) throws Exception {
      return new Built(type, null, columns);
   }
}
