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
package inetsoft.web.wiz.worksheet;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.report.XSessionManager;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XNode;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.jdbc.util.SQLTypes;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.service.XEngine;
import inetsoft.uql.util.Config;
import inetsoft.uql.util.DefaultMetaDataProvider;
import inetsoft.uql.util.Drivers;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import inetsoft.web.composer.AssetTreeService;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.service.MetadataApiService;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;
import java.security.Principal;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug #81675 round 2: add_expression_column on a table added from a physical database table
 * must refuse the name of a source column that was removed from the table. Round 1 stubbed the
 * source-column lookup, which hid that the real lookup returned nothing for such a table. This
 * runs the real {@link MetadataApiService}, {@link DefaultMetaDataProvider}, {@link XEngine}
 * and JDBC handler against embedded Derby; only the permission check and the data source lookup
 * are stood in for.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  AddExpressionPhysicalSourceDerby81675Test.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AddExpressionPhysicalSourceDerby81675Test {
   private static final String DB = "memory:bug81675orders";
   private static final String DS = "bug81675";

   @Configuration
   static class JdbcConfig {
      @Bean
      public CredentialService credentialService() throws Exception {
         Constructor<CredentialService> ctor = CredentialService.class.getDeclaredConstructor();
         ctor.setAccessible(true);
         return ctor.newInstance();
      }

      @Bean
      public Plugins plugins(BlobStorageManager blobStorageManager, Cluster cluster,
                             ApplicationEventPublisher eventPublisher)
      {
         return new Plugins(blobStorageManager.getStorage("plugins", true), cluster,
                            eventPublisher);
      }

      @Bean
      public ConnectionPoolFactory connectionPoolFactory() {
         EmbeddedDataSource derby = new EmbeddedDataSource();
         derby.setDatabaseName(DB);
         derby.setCreateDatabase("create");
         ConnectionPoolFactory factory = mock(ConnectionPoolFactory.class);
         when(factory.getConnectionPool(any(), any())).thenReturn((DataSource) derby);
         return factory;
      }

      @Bean
      public Drivers drivers(Plugins plugins, ConnectionPoolFactory connectionPoolFactory)
         throws Exception
      {
         // the driver-plugin class loader is empty in the unit test JVM
         Drivers drivers = spy(new Drivers(plugins, connectionPoolFactory));
         doAnswer(inv -> {
            Object r = inv.callRealMethod();
            return r != null ? r : Class.forName(inv.getArgument(0));
         }).when(drivers).getDriverClass(anyString());
         return drivers;
      }

      @Bean
      public Config config(Plugins plugins) {
         return new Config(plugins);
      }

      @Bean
      public XEngine xEngine(Cluster cluster, Config config,
                             ConnectionPoolFactory connectionPoolFactory)
      {
         return new XEngine(cluster, config, mock(DataSourceRegistry.class),
                            connectionPoolFactory);
      }

      @Bean
      public XSessionManager xSessionManager() {
         return mock(XSessionManager.class);
      }
   }

   @Autowired
   private XEngine engine;
   private final Principal agent = TestPrincipals.user("alice", "host-org");
   private JDBCDataSource ds;
   private MetadataApiService metadata;
   private WorksheetEditService service;
   private RuntimeWorksheet rws;

   @BeforeEach
   void setUp() throws Exception {
      ds = new JDBCDataSource();
      ds.setName(DS);
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:" + DB);
      ds.setRequireLogin(false);

      try(Connection c = new JDBCHandler().getConnection(ds, null);
          Statement s = c.createStatement())
      {
         for(String sql : new String[] {
            "create schema SA",
            "create table SA.ORDERS (ORDER_ID int, ORDER_DATE timestamp, TOTAL double)"
         }) {
            try {
               s.executeUpdate(sql);
            }
            catch(Exception ignore) {
               // already created by an earlier test
            }
         }
      }

      DataSourceService dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.checkPermission(anyString(), any(ResourceAction.class), any()))
         .thenReturn(true);
      when(dataSourceService.getDataSource(DS)).thenReturn(ds);
      when(dataSourceService.getDefaultMetaDataProvider(any(), any())).thenAnswer(inv -> {
         DefaultMetaDataProvider provider = new DefaultMetaDataProvider(engine);
         provider.setDataSource(inv.getArgument(0));
         return provider;
      });
      XRepository repository = mock(XRepository.class);
      when(repository.getDataSource(DS)).thenReturn(ds);
      metadata = new MetadataApiService(repository, dataSourceService,
         mock(AssetRepository.class), mock(AssetTreeService.class), new ObjectMapper());
      service = new WorksheetEditService(mock(SheetSessionService.class),
         mock(SheetRuntimeAccess.class), mock(SheetAgentBroadcastService.class),
         securityEngine(), new InnerJoinService(null, null), null, null, metadata);
      rws = mock(RuntimeWorksheet.class);
      when(rws.getUser()).thenReturn(agent);
   }

   private static SecurityEngine securityEngine() throws Exception {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(
         any(), any(ResourceType.class), any(String.class), any(ResourceAction.class)))
         .thenReturn(true);
      return securityEngine;
   }

   /** The table as add_table binds it, selecting only ORDER_ID. */
   private PhysicalBoundTableAssembly addTable(Worksheet ws, String tablePath, boolean withPath)
      throws Exception
   {
      JDBCDataSource jdbc = metadata.getJDBCDatasource(DS);
      XNode node = metadata.getTableMetaData(jdbc, null, "SA", tablePath);
      String qname = SQLTypes.getSQLTypes(jdbc).getQualifiedName(node, jdbc);

      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, "ORDERS");
      SourceInfo info = new SourceInfo(SourceInfo.PHYSICAL_TABLE, DS, qname);
      info.setProperty(SourceInfo.SCHEMA, "SA");

      if(withPath) {
         info.setProperty(WorksheetEditService.SOURCE_TABLE_PATH, tablePath);
      }

      table.setSourceInfo(info);
      ColumnSelection selection = new ColumnSelection();
      selection.addAttribute(new ColumnRef(new AttributeRef(null, "ORDER_ID")));
      table.setColumnSelection(selection, false);
      ws.addAssembly(table);
      return table;
   }

   private WorksheetEditService.Editor editor(Worksheet ws) throws Exception {
      return new WorksheetEditService.Editor(ws, agent, securityEngine(),
         new InnerJoinService(null, null), null, () -> null,
         t -> service.sourceColumnsOf(rws, t));
   }

   private static List<String> names(ColumnSelection cs) {
      List<String> out = new ArrayList<>();

      for(int i = 0; i < cs.getAttributeCount(); i++) {
         out.add(((ColumnRef) cs.getAttribute(i)).getAttribute());
      }

      return out;
   }

   private static List<String> selected(Worksheet ws) {
      return names(((TableAssembly) ws.getAssembly("ORDERS")).getColumnSelection(false));
   }

   @Test
   void realLookupReadsTheTableColumnsOfAPhysicalSource() throws Exception {
      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly table = addTable(ws, "ORDERS", true);

      assertEquals(List.of("ORDER_ID", "ORDER_DATE", "TOTAL"),
                   names(service.sourceColumnsOf(rws, table)));
   }

   @Test
   void realLookupWorksFromTheQualifiedNameWhenNoTablePathIsStored() throws Exception {
      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly table = addTable(ws, "ORDERS", false);

      assertEquals(List.of("ORDER_ID", "ORDER_DATE", "TOTAL"),
                   names(service.sourceColumnsOf(rws, table)));
   }

   @Test
   void removedSourceColumnNameIsRefusedAndNothingIsCreated() throws Exception {
      Worksheet ws = new Worksheet();
      addTable(ws, "ORDERS", true);
      WorksheetEditService.Editor ed = editor(ws);

      PairingException ex = assertThrows(PairingException.class,
         () -> ed.addExpressionColumn("ORDERS", "ORDER_DATE", "1", "integer", false));

      assertTrue(ex.getMessage().contains("ORDER_DATE"), ex.getMessage());
      assertEquals(List.of("ORDER_ID"), selected(ws));
   }

   @Test
   void freshNameIsAcceptedAndTheRemovedColumnCanBeRestored() throws Exception {
      Worksheet ws = new Worksheet();
      addTable(ws, "ORDERS", true);
      WorksheetEditService.Editor ed = editor(ws);

      ed.addExpressionColumn("ORDERS", "Total2", "1", "integer", false);
      ed.addColumn("ORDERS", "ORDER_DATE", null);

      assertEquals(List.of("ORDER_ID", "Total2", "ORDER_DATE"), selected(ws));
   }

   @Test
   void unreadableTableFailsClosed() throws Exception {
      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly table = addTable(ws, "ORDERS", true);
      // the table the source info names no longer resolves in the datasource
      table.getSourceInfo().setProperty(WorksheetEditService.SOURCE_TABLE_PATH, "NO_SUCH_TABLE");
      WorksheetEditService.Editor ed = editor(ws);

      PairingException ex = assertThrows(PairingException.class,
         () -> ed.addExpressionColumn("ORDERS", "ORDER_DATE", "1", "integer", false));

      assertTrue(ex.getMessage().contains("Could not read the source columns"), ex.getMessage());
      assertTrue(ex.getMessage().contains("ORDER_DATE"), ex.getMessage());
      assertEquals(List.of("ORDER_ID"), selected(ws));
   }
}
