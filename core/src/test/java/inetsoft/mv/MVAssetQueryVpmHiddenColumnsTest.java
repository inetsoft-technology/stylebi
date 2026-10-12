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
package inetsoft.mv;

import inetsoft.report.composition.execution.AssetQuery;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.Organization;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.vpm.VpmProcessor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Regression test for Redmine bug #77666. On a worksheet MV hit, the VPM hidden columns are
 * removed from the column selection by {@code MVAssetQuery.getDefaultColumnSelection0()}.
 * That removal must fail closed when the hidden-columns selector fails, and the selector must
 * see the query variables so a parameter-dependent trigger works as on the regular path.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class MVAssetQueryVpmHiddenColumnsTest {
   @BeforeEach
   void setUp() {
      user = new XPrincipal(new IdentityID("u1", Organization.getDefaultOrganizationID()));
      processor = mock(VpmProcessor.class);
      vpmProcessor = mockStatic(VpmProcessor.class, CALLS_REAL_METHODS);
      vpmProcessor.when(VpmProcessor::getInstance).thenReturn(processor);

      meta = new MVMetaData();
      meta.setBypassVPM(false);
      MVDef def = mock(MVDef.class);
      when(def.getMetaData()).thenReturn(meta);
      MVManager mgr = mock(MVManager.class);
      when(mgr.get("MV1")).thenReturn(def);
      mvManager = mockStatic(MVManager.class);
      mvManager.when(MVManager::getManager).thenReturn(mgr);
   }

   @AfterEach
   void tearDown() {
      mvManager.close();
      vpmProcessor.close();
   }

   @Test
   void selectorFailureFailsClosed() throws Exception {
      when(processor.getHiddenColumnsSelector(any(), any(), any(), any(), any(), any()))
         .thenThrow(new Exception("boom"));

      RuntimeException ex = assertThrows(
         RuntimeException.class, () -> validatedColumns(new VariableTable()));

      assertFalse(ex instanceof MVExecutionException,
                  "an MVExecutionException would recreate or skip the MV");
      assertEquals("boom", ex.getCause().getMessage());
   }

   @Test
   void hiddenColumnIsRemoved() throws Exception {
      when(processor.getHiddenColumnsSelector(any(), any(), any(), any(), any(), any()))
         .thenReturn((table, column) -> "T".equals(table) && "SSN".equals(column));

      assertEquals(List.of("ID"), validatedColumns(new VariableTable()));
   }

   @Test
   void selectorReceivesQueryVariables() throws Exception {
      when(processor.getHiddenColumnsSelector(any(), any(), any(), any(), any(), any()))
         .thenReturn((table, column) -> false);
      VariableTable vars = new VariableTable();
      vars.put("region", "east");

      assertEquals(List.of("ID", "SSN"), validatedColumns(vars));

      ArgumentCaptor<VariableTable> captor = ArgumentCaptor.forClass(VariableTable.class);
      verify(processor).getHiddenColumnsSelector(any(), any(), eq("ds1"), any(),
                                                 captor.capture(), any());
      assertNotNull(captor.getValue());
      assertEquals("east", captor.getValue().get("region"));
   }

   @Test
   void bypassVpmSkipsSelector() throws Exception {
      meta.setBypassVPM(true);

      assertEquals(List.of("ID", "SSN"), validatedColumns(new VariableTable()));
      verify(processor, never())
         .getHiddenColumnsSelector(any(), any(), any(), any(), any(), any());
   }

   /**
    * Builds the MV query and validates it as {@code AssetQuery.createAssetQuery()} does, then
    * returns the column selection of the query's table.
    */
   private List<String> validatedColumns(VariableTable vars) throws Exception {
      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, "T1");
      table.setSourceInfo(new SourceInfo(SourceInfo.PHYSICAL_TABLE, "ds1", "T"));
      ColumnSelection sel = new ColumnSelection();
      sel.addAttribute(new ColumnRef(new AttributeRef(null, "ID")));
      sel.addAttribute(new ColumnRef(new AttributeRef(null, "SSN")));
      table.setColumnSelection(sel);
      ws.addAssembly(table);

      AssetEntry wsEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                          AssetEntry.Type.WORKSHEET, "ws1", null);
      table.setRuntimeMV(new RuntimeMV(wsEntry, null, null, "T1", "MV1", false, 0L, null));
      AssetQuery query = MVAssetQuery.createQuery(table, user, vars, false,
                                                  AssetQuerySandbox.RUNTIME_MODE);
      invoke(query, "validate");
      ColumnSelection result = ((TableAssembly) invoke(query, "getTable")).getColumnSelection();
      List<String> names = new ArrayList<>();

      for(int i = 0; i < result.getAttributeCount(); i++) {
         names.add(result.getAttribute(i).getAttribute());
      }

      return names;
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
   private MVMetaData meta;
   private VpmProcessor processor;
   private MockedStatic<VpmProcessor> vpmProcessor;
   private MockedStatic<MVManager> mvManager;
}
