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
package inetsoft.report.composition.execution;

import inetsoft.mv.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.Organization;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Regression test for Redmine bug #78053. The query of a viewsheet over a worksheet must use the
 * worksheet MV of the bound table under it, while the runtime worksheet of the worksheet
 * composer (#33943) and the sandboxes that create an MV must not.
 * <p>
 * The MV lookup is stubbed ({@link MVManager}), so this checks which query
 * {@link AssetQuery#createAssetQuery} builds and the conditions it hands to the MV query. It
 * doesn't build and read a real MV.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                  SwapperTestConfiguration.class,
                                  AssetQueryWorksheetMVTest.RepositoryConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AssetQueryWorksheetMVTest {
   /**
    * The repository the live query of the bound table asks for its data source. The query is
    * only built, never run.
    */
   @Configuration
   static class RepositoryConfig {
      @Bean
      XRepository xRepository() {
         return mock(XRepository.class);
      }
   }

   @BeforeEach
   void setUp() {
      user = new XPrincipal(new IdentityID("u1", Organization.getDefaultOrganizationID()));
      wsEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
                               "ws1", null);
      mvEnabled = SreeEnv.getProperty("ws.mv.enabled");
      SreeEnv.setProperty("ws.mv.enabled", "true");

      MVMetaData meta = new MVMetaData();
      meta.setBypassVPM(true);
      MVDef def = mock(MVDef.class);
      when(def.getMetaData()).thenReturn(meta);
      when(def.isValidMV(any(TableAssembly.class))).thenReturn(true);
      when(def.canHitMV(any(TableAssembly.class))).thenReturn(true);
      mgr = mock(MVManager.class);
      when(mgr.get("MV1")).thenReturn(def);
      when(mgr.findRuntimeMV(argThat(e -> e != null && e.isWorksheet()), isNull(), isNull(),
                             eq("CUSTOMERS"), any(), isNull(), anyBoolean(), anyBoolean()))
         .thenAnswer(inv -> new RuntimeMV(inv.getArgument(0), null, null, "CUSTOMERS", "MV1",
                                          false, 0L, null));
      mvManager = mockStatic(MVManager.class);
      mvManager.when(MVManager::getManager).thenReturn(mgr);

      ws = new Worksheet();
      customers = customersTable(ws, "CUSTOMERS");
      // the table a viewsheet queries is a copy of the mirror of the worksheet table, see
      // VSAQuery.getVSTableAssembly and ViewsheetSandbox.getBoundTable
      TableAssembly vtable = ws.getVSTableAssembly("CUSTOMERS");
      root = (TableAssembly) vtable.copyAssembly("V_MCUSTOMERS_Table1");
      ws.addAssembly(root);
   }

   @AfterEach
   void tearDown() {
      mvManager.close();

      if(mvEnabled == null) {
         SreeEnv.remove("ws.mv.enabled");
      }
      else {
         SreeEnv.setProperty("ws.mv.enabled", mvEnabled);
      }
   }

   @Test
   void viewsheetQueryUsesTheWorksheetMV() throws Exception {
      AssetQuery query = createQuery(viewsheetBox(), root, AssetQuerySandbox.RUNTIME_MODE, false);

      assertInstanceOf(MVAssetQuery.class, subQuery(query),
                       "the bound table must be read from the worksheet mv, not the database");
      // the lookup is made for the user running the query (mvs are per identity)
      ArgumentCaptor<XPrincipal> users = ArgumentCaptor.forClass(XPrincipal.class);
      verify(mgr).findRuntimeMV(any(), any(), any(), eq("CUSTOMERS"), users.capture(), any(),
                                anyBoolean(), anyBoolean());
      assertSame(user, users.getValue());
      // the shared tables of the sandbox worksheet don't keep the mv
      assertNull(((TableAssembly) ws.getAssembly("CUSTOMERS")).getRuntimeMV());
      assertNull(root.getRuntimeMV());
      assertFalse(WSMVTransformer.containsWSRuntimeMV(root));
   }

   @Test
   void viewsheetConditionReachesTheMVQuery() throws Exception {
      // ViewsheetSandbox.getBoundTable puts the viewsheet conditions and selections on the
      // worksheet table as its pre runtime conditions
      ConditionList conds = new ConditionList();
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.EQUAL_TO);
      cond.addValue("Paris");
      conds.append(new ConditionItem(column("CUSTOMERS", "CITY", XSchema.STRING), cond, 0));
      customers.setPreRuntimeConditionList(conds);

      AssetQuery query = createQuery(viewsheetBox(), root, AssetQuerySandbox.RUNTIME_MODE, false);
      AssetQuery mvQuery = subQuery(query);

      assertInstanceOf(MVAssetQuery.class, mvQuery);
      // MVQueryBuilder turns the pre runtime conditions of this table into the mv filter
      TableAssembly mvTable = (TableAssembly) invoke(mvQuery, "getRuntimeTable");
      String filter = String.valueOf(findCondition(mvTable));
      assertTrue(filter.contains("CITY") && filter.contains("Paris"),
                 "the viewsheet condition must not be dropped: " + filter);
   }

   @Test
   void conditionWithAVariableIsNotGivenTheMV() throws Exception {
      // the mv holds the rows for the default value of the variable (MVCreatorUtil)
      customers.setPreConditionList(cityCondition("$(pCity)"));

      assertFalse(subQuery(createQuery(viewsheetBox(), root, AssetQuerySandbox.RUNTIME_MODE,
                                       false)) instanceof MVAssetQuery,
                  "a worksheet condition on a parameter must be evaluated live");
      verifyNoLookup();
   }

   @Test
   void conditionWithASessionVariableIsNotGivenTheMV() throws Exception {
      // the mv holds the rows for the session of the identity it was created for
      customers.setPreConditionList(cityCondition("$(_USER_)"));

      assertFalse(subQuery(createQuery(viewsheetBox(), root, AssetQuerySandbox.RUNTIME_MODE,
                                       false)) instanceof MVAssetQuery,
                  "a worksheet condition on a session variable must be evaluated live");
      verifyNoLookup();
   }

   @Test
   void conditionWithoutAVariableUsesTheMV() throws Exception {
      // a constant condition of the worksheet is in the mv already
      customers.setPreConditionList(cityCondition("Paris"));

      assertInstanceOf(MVAssetQuery.class,
                       subQuery(createQuery(viewsheetBox(), root, AssetQuerySandbox.RUNTIME_MODE,
                                            false)));
   }

   @Test
   void aggregatedViewsheetQueryUsesTheWorksheetMV() throws Exception {
      // a chart or crosstab groups the viewsheet table (WSMVAggregateDownTransformer)
      ColumnSelection cols = root.getColumnSelection(false);
      AggregateInfo ainfo = new AggregateInfo();
      ainfo.addGroup(new GroupRef(cols.getAttribute(1)));
      ainfo.addAggregate(new AggregateRef(cols.getAttribute(0), AggregateFormula.COUNT_ALL));
      root.setAggregateInfo(ainfo);

      AssetQuery query = createQuery(viewsheetBox(), root, AssetQuerySandbox.RUNTIME_MODE, false);

      assertInstanceOf(MVAssetQuery.class, subQuery(query));
   }

   @Test
   void boundRootIsNotGivenTheMV() throws Exception {
      // WSMVTransformer drops the runtime conditions of a bound root
      AssetQuery query = createQuery(viewsheetBox(), customers, AssetQuerySandbox.RUNTIME_MODE,
                                     false);

      assertFalse(query instanceof MVAssetQuery);
      verifyNoLookup();
   }

   @Test
   void runtimeWorksheetDoesNotUseTheMV() throws Exception {
      RuntimeWorksheet rws = new RuntimeWorksheet(wsEntry, ws, user, false);
      AssetQuerySandbox box = rws.getAssetQuerySandbox();

      assertTrue(box.isRuntimeWorksheet());
      assertFalse(subQuery(createQuery(box, root, AssetQuerySandbox.RUNTIME_MODE, false))
                     instanceof MVAssetQuery);
      verifyNoLookup();
   }

   @Test
   void runtimeWorksheetOfAViewsheetKeepsTheMV() throws Exception {
      // the viewer and the export wrap the viewsheet box in a runtime worksheet before the
      // queries, e.g. CoreLifecycleService.executeVariablesQuery
      AssetQuerySandbox box = viewsheetBox();
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getBaseWorksheet()).thenReturn(ws);
      when(vs.getBaseEntry()).thenReturn(wsEntry);
      ViewsheetSandbox vbox = mock(ViewsheetSandbox.class);
      when(vbox.getAssetQuerySandbox()).thenReturn(box);
      when(vbox.getUser()).thenReturn(user);
      RuntimeViewsheet rvs = new RuntimeViewsheet();
      setField(rvs, "vs", vs);
      setField(rvs, "box", vbox);

      RuntimeWorksheet rws = rvs.getRuntimeWorksheet();

      assertSame(box, rws.getAssetQuerySandbox());
      assertFalse(box.isRuntimeWorksheet());
      assertInstanceOf(MVAssetQuery.class,
                       subQuery(createQuery(box, root, AssetQuerySandbox.RUNTIME_MODE, false)));
   }

   @Test
   void previewAndExportBoxesUseTheMV() throws Exception {
      // the viewsheet preview and export force the box active, unlike a runtime worksheet
      AssetQuerySandbox box = viewsheetBox();
      box.setActive(true);

      assertInstanceOf(MVAssetQuery.class,
                       subQuery(createQuery(box, root, AssetQuerySandbox.RUNTIME_MODE, false)));
   }

   @Test
   void mvCreationDoesNotUseTheMV() throws Exception {
      // the sandboxes creating an mv have no worksheet entry
      AssetQuerySandbox box = new AssetQuerySandbox(ws, user, new VariableTable());
      box.setWSName("ws1");
      assertFalse(subQuery(createQuery(box, root, AssetQuerySandbox.RUNTIME_MODE, false))
                     instanceof MVAssetQuery);

      box = viewsheetBox();
      box.setCreatingMV(true);
      assertFalse(subQuery(createQuery(box, root, AssetQuerySandbox.RUNTIME_MODE, false))
                     instanceof MVAssetQuery);
      verifyNoLookup();
   }

   @Test
   void logicalModelBaseIsNotLookedUp() throws Exception {
      AssetQuerySandbox box = viewsheetBox();
      box.setWSEntry(new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.LOGIC_MODEL,
                                    "Orders/Model", null));

      assertFalse(subQuery(createQuery(box, root, AssetQuerySandbox.RUNTIME_MODE, false))
                     instanceof MVAssetQuery);
      verifyNoLookup();
   }

   @Test
   void designModeMetadataAndDisabledPropertyDoNotUseTheMV() throws Exception {
      assertFalse(subQuery(createQuery(viewsheetBox(), root, AssetQuerySandbox.DESIGN_MODE,
                                       false)) instanceof MVAssetQuery);
      assertFalse(subQuery(createQuery(viewsheetBox(), root, AssetQuerySandbox.RUNTIME_MODE,
                                       true)) instanceof MVAssetQuery);
      SreeEnv.setProperty("ws.mv.enabled", "false");
      assertFalse(subQuery(createQuery(viewsheetBox(), root, AssetQuerySandbox.RUNTIME_MODE,
                                       false)) instanceof MVAssetQuery);
      verifyNoLookup();
   }

   @Test
   void failedTransformQueriesLiveWithoutAnyMV() throws Exception {
      try(MockedStatic<WSMVTransformer> transformer =
             mockStatic(WSMVTransformer.class, CALLS_REAL_METHODS))
      {
         transformer.when(() -> WSMVTransformer.transform(any()))
            .thenThrow(new RuntimeException("transform failed"));

         AssetQuery query =
            createQuery(viewsheetBox(), root, AssetQuerySandbox.RUNTIME_MODE, false);
         AssetQuery sub = subQuery(query);

         assertFalse(sub instanceof MVAssetQuery);
         assertFalse(WSMVTransformer.containsWSRuntimeMV((TableAssembly) invoke(query, "getTable")),
                     "no worksheet mv may be left on the live query");
      }
   }

   @Test
   void tableBoundByAViewsheetAssemblyUsesTheWorksheetMV() throws Exception {
      // the viewsheet renames the bound table CUSTOMERS to CUSTOMERS_O under a mirror CUSTOMERS
      TableAssembly vroot = viewsheetRoot();
      assertSame(customers, ws.getAssembly("CUSTOMERS_O"));
      assertInstanceOf(MirrorTableAssembly.class, ws.getAssembly("CUSTOMERS"));

      AssetQuery query = createQuery(viewsheetBox(), vroot, AssetQuerySandbox.RUNTIME_MODE,
                                     false);

      assertInstanceOf(MVAssetQuery.class, deepestQuery(query),
                       "the bound table must be read from the worksheet mv, not the database");
      // the mv is registered with the name of the table in the worksheet
      verify(mgr).findRuntimeMV(any(), any(), any(), eq("CUSTOMERS"), any(), any(),
                                anyBoolean(), anyBoolean());
      assertNull(customers.getRuntimeMV());
   }

   @Test
   void viewsheetConditionOnTheMirrorIsKeptWithTheMV() throws Exception {
      TableAssembly vroot = viewsheetRoot();
      // ViewsheetSandbox.getBoundTable puts the viewsheet conditions and selections on the
      // table the viewsheet table mirrors, which is the mirror CUSTOMERS here
      ConditionList conds = new ConditionList();
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.EQUAL_TO);
      cond.addValue("Paris");
      conds.append(new ConditionItem(column("CUSTOMERS", "CITY", XSchema.STRING), cond, 0));
      ((TableAssembly) ws.getAssembly("CUSTOMERS")).setPreRuntimeConditionList(conds);

      AssetQuery query = createQuery(viewsheetBox(), vroot, AssetQuerySandbox.RUNTIME_MODE,
                                     false);

      assertInstanceOf(MVAssetQuery.class, deepestQuery(query));
      String filter = String.valueOf(findCondition((TableAssembly) invoke(query, "getTable")));
      assertTrue(filter.contains("CITY") && filter.contains("Paris"),
                 "the viewsheet condition must not be dropped: " + filter);
   }

   @Test
   void variableConditionOfATableBoundByAViewsheetAssemblyIsNotGivenTheMV() throws Exception {
      // the renamed table is the worksheet table, with its conditions
      customers.setPreConditionList(cityCondition("$(pCity)"));
      TableAssembly vroot = viewsheetRoot();

      assertFalse(deepestQuery(createQuery(viewsheetBox(), vroot,
                                           AssetQuerySandbox.RUNTIME_MODE, false))
                     instanceof MVAssetQuery);
      verifyNoLookup();
   }

   @Test
   void worksheetTableNamedLikeARenamedTableIsLookedUpByItsName() throws Exception {
      // a worksheet table named CUSTOMERS_O by its author is not the viewsheet copy of CUSTOMERS
      Worksheet ws2 = new Worksheet();
      customersTable(ws2, "CUSTOMERS");
      customersTable(ws2, "CUSTOMERS_O");
      TableAssembly vroot = (TableAssembly) ws2.getVSTableAssembly("CUSTOMERS_O")
         .copyAssembly("V_MCUSTOMERS_O_Table1");
      ws2.addAssembly(vroot);
      AssetQuerySandbox box = new AssetQuerySandbox(ws2, user, new VariableTable());
      box.setWSName("ws1");
      box.setWSEntry(wsEntry);

      assertFalse(subQuery(createQuery(box, vroot, AssetQuerySandbox.RUNTIME_MODE, false))
                     instanceof MVAssetQuery);
      verify(mgr).findRuntimeMV(any(), any(), any(), eq("CUSTOMERS_O"), any(), any(),
                                anyBoolean(), anyBoolean());
      verify(mgr, never()).findRuntimeMV(any(), any(), any(), eq("CUSTOMERS"), any(), any(),
                                         anyBoolean(), anyBoolean());
   }

   /**
    * Bind a table assembly of a viewsheet to CUSTOMERS and give the viewsheet the worksheet,
    * which renames the bound table like a viewsheet opened in the viewer
    * (Viewsheet.setBaseWorksheet, resetWS and createMirrorTables).
    * @return the table the query of the viewsheet table assembly runs.
    */
   private TableAssembly viewsheetRoot() throws Exception {
      Viewsheet vs = new Viewsheet(wsEntry);
      TableVSAssembly table = new TableVSAssembly(vs, "TableView1");
      table.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "CUSTOMERS"));
      vs.addAssembly(table);

      Worksheet vws = new Worksheet();
      vws.addAssembly(customers);
      customers.setWorksheet(vws);
      Method setBase = Viewsheet.class.getDeclaredMethod("setBaseWorksheet", Worksheet.class);
      setBase.setAccessible(true);
      setBase.invoke(vs, vws);
      ws = vs.getBaseWorksheet();

      TableAssembly vroot = (TableAssembly) ws.getVSTableAssembly("CUSTOMERS")
         .copyAssembly("V_MCUSTOMERS_TableView1");
      ws.addAssembly(vroot);
      return vroot;
   }

   /**
    * The query of the bound table, under the mirror queries.
    */
   private static AssetQuery deepestQuery(AssetQuery query) throws Exception {
      while(query instanceof MirrorQuery) {
         query = subQuery(query);
      }

      return query;
   }

   private static PhysicalBoundTableAssembly customersTable(Worksheet ws, String name) {
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, name);
      table.setSourceInfo(new SourceInfo(SourceInfo.PHYSICAL_TABLE, "Orders", "CUSTOMERS"));
      ColumnSelection cols = new ColumnSelection();
      cols.addAttribute(column("CUSTOMERS", "CUSTOMER_ID", XSchema.INTEGER));
      cols.addAttribute(column("CUSTOMERS", "CITY", XSchema.STRING));
      table.setColumnSelection(cols);
      ws.addAssembly(table);
      return table;
   }

   /**
    * A box like the one ViewsheetSandbox.createAssetQuerySandbox creates for a viewsheet over
    * the worksheet.
    */
   private AssetQuerySandbox viewsheetBox() {
      AssetQuerySandbox box = new AssetQuerySandbox(ws, user, new VariableTable());
      box.setWSName("ws1");
      box.setWSEntry(wsEntry);
      return box;
   }

   private static AssetQuery createQuery(AssetQuerySandbox box, TableAssembly table, int mode,
                                         boolean metadata)
      throws Exception
   {
      return AssetQuery.createAssetQuery((TableAssembly) table.clone(), mode, box, false, -1L,
                                         true, metadata);
   }

   private void verifyNoLookup() {
      verify(mgr, never()).findRuntimeMV(any(), any(), any(), any(), any(), any(), anyBoolean(),
                                         anyBoolean());
   }

   private static AssetQuery subQuery(AssetQuery query) throws Exception {
      assertInstanceOf(MirrorQuery.class, query);
      Field field = MirrorQuery.class.getDeclaredField("query");
      field.setAccessible(true);
      return (AssetQuery) field.get(query);
   }

   private static ConditionList findCondition(TableAssembly table) {
      ConditionListWrapper wrapper = table.getPreRuntimeConditionList();

      if(wrapper != null && !wrapper.isEmpty()) {
         return wrapper.getConditionList();
      }

      if(table instanceof ComposedTableAssembly) {
         for(TableAssembly sub : ((ComposedTableAssembly) table).getTableAssemblies(false)) {
            ConditionList conds = findCondition(sub);

            if(conds != null) {
               return conds;
            }
         }
      }

      return null;
   }

   private static ConditionList cityCondition(String value) {
      ConditionList conds = new ConditionList();
      AssetCondition cond = new AssetCondition(XSchema.STRING);
      cond.setOperation(XCondition.EQUAL_TO);
      cond.addValue(value);
      conds.append(new ConditionItem(column("CUSTOMERS", "CITY", XSchema.STRING), cond, 0));
      return conds;
   }

   private static ColumnRef column(String entity, String name, String type) {
      ColumnRef col = new ColumnRef(new AttributeRef(entity, name));
      col.setDataType(type);
      return col;
   }

   private static void setField(Object obj, String name, Object value) throws Exception {
      Field field = obj.getClass().getDeclaredField(name);
      field.setAccessible(true);
      field.set(obj, value);
   }

   private static Object invoke(Object obj, String name) throws Exception {
      for(Class<?> c = obj.getClass(); c != null; c = c.getSuperclass()) {
         for(Method m : c.getDeclaredMethods()) {
            if(m.getName().equals(name) && m.getParameterCount() == 0) {
               m.setAccessible(true);

               try {
                  return m.invoke(obj);
               }
               catch(InvocationTargetException e) {
                  if(e.getCause() instanceof Exception cause) {
                     throw cause;
                  }

                  throw e;
               }
            }
         }
      }

      throw new NoSuchMethodException(name);
   }

   private XPrincipal user;
   private AssetEntry wsEntry;
   private String mvEnabled;
   private MVManager mgr;
   private MockedStatic<MVManager> mvManager;
   private Worksheet ws;
   private PhysicalBoundTableAssembly customers;
   private TableAssembly root;
}
