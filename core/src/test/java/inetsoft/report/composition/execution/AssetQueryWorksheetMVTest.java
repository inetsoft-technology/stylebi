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
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.Organization;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
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
      customers = new PhysicalBoundTableAssembly(ws, "CUSTOMERS");
      customers.setSourceInfo(new SourceInfo(SourceInfo.PHYSICAL_TABLE, "Orders", "CUSTOMERS"));
      ColumnSelection cols = new ColumnSelection();
      cols.addAttribute(column("CUSTOMERS", "CUSTOMER_ID", XSchema.INTEGER));
      cols.addAttribute(column("CUSTOMERS", "CITY", XSchema.STRING));
      customers.setColumnSelection(cols);
      ws.addAssembly(customers);
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

   private static ColumnRef column(String entity, String name, String type) {
      ColumnRef col = new ColumnRef(new AttributeRef(entity, name));
      col.setDataType(type);
      return col;
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
