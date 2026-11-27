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

import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.sree.security.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #82210, the fallback of {@link MVTransformer#transform} when a union has a selection on
 * only one of its children and {@code mv.union.moveUp=true}. The selection cannot move up to
 * the union, so the transformer falls back to the sub MVs. The top MV must not be attached to
 * a child (its columns are those of the union, so the column check failed with "Table column
 * doesn't exist in MV" and, with the recreate flag set, the viewer got an error). Without a
 * usable sub MV the query must run without the MV: the exception does not ask for a
 * recreation.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class MVTransformerUnionSelectionFallbackTest {
   @BeforeEach
   void setUp() {
      SreeEnv.setProperty("mv.union.moveUp", "true");
   }

   @AfterEach
   void tearDown() {
      SreeEnv.resetProperty("mv.union.moveUp", false);
   }

   @Test
   void oneSidedSelectionWithOnlyTheTopMvRunsWithoutMv() throws Exception {
      Fixture fx = new Fixture();
      fx.real("T1", 0, "A");

      MVExecutionException mex = fx.transformFails(false);

      assertFalse(mex.isNeedRecreateMV(), mex.getMessage());
   }

   @Test
   void oneSidedSelectionOnTheSecondChildRunsWithoutMv() throws Exception {
      Fixture fx = new Fixture();
      fx.real("T2", 0, "B");

      MVExecutionException mex = fx.transformFails(false);

      assertFalse(mex.isNeedRecreateMV(), mex.getMessage());
   }

   @Test
   void oneSidedSelectionUsesTheSubMvsOfTheChildren() throws Exception {
      Fixture fx = new Fixture();
      fx.real("T1", 0, "A");

      TableAssembly top = fx.transform(true);

      // the sub mv of each child is used, the top mv is not attached to a child
      for(TableAssembly child : ((ComposedTableAssembly) top).getTableAssemblies(false)) {
         assertNotNull(child.getRuntimeMV(), child.getName());
         assertEquals("SUB_" + child.getName(), child.getRuntimeMV().getMV());
      }
   }

   @Test
   void oneSidedSelectionWithASubMvWithoutTheColumnsRunsWithoutMv() throws Exception {
      Fixture fx = new Fixture();
      fx.real("T1", 0, "A");
      fx.subMvColumns = Set.of("Nothing");

      MVExecutionException mex = fx.transformFails(true);

      assertFalse(mex.isNeedRecreateMV(), mex.getMessage());
   }

   @Test
   void nothingSelectedStillHitsTheTopMv() throws Exception {
      Fixture fx = new Fixture();

      TableAssembly top = fx.transform(false);

      assertNotNull(top.getRuntimeMV());
      assertEquals("TOPMV", top.getRuntimeMV().getMV());
   }

   @Test
   void bothChildrenSelectedStillHitTheTopMv() throws Exception {
      Fixture fx = new Fixture();
      fx.real("T1", 0, "A");
      fx.real("T2", 0, "B");

      TableAssembly top = fx.transform(false);

      assertNotNull(top.getRuntimeMV());
      assertEquals("TOPMV", top.getRuntimeMV().getMV());
   }

   @Test
   void otherUnionFaultsKeepRequestingARecreation() throws Exception {
      // moveUp=false is not the mixed selection fault: the behavior is unchanged
      SreeEnv.setProperty("mv.union.moveUp", "false");
      Fixture fx = new Fixture();
      fx.real("T1", 0, "A");

      MVExecutionException mex = fx.transformFails(false);

      assertTrue(mex.isNeedRecreateMV(), mex.getMessage());
   }

   private static ColumnRef column(String name) {
      ColumnRef ref = new ColumnRef(new AttributeRef(null, name));
      ref.setDataType(XSchema.STRING);
      return ref;
   }

   /**
    * A distinct union C1 (the MV table) of T1 (Region, State) and T2 (Region2, State2), a
    * selection list on the first column of both, and the MV manager stubbed with a top MV
    * (the columns of the union) and, on request, a sub MV for each child.
    */
   private static final class Fixture {
      static final String VS_ASSEMBLY = "Table1";

      final Worksheet ws = new Worksheet();
      final Viewsheet vs = new Viewsheet() {
         @Override
         public Worksheet getBaseWorksheet() {
            return ws;
         }
      };
      final AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                              AssetEntry.Type.VIEWSHEET, "vs82210", null);
      final TableAssembly union;
      Set<String> subMvColumns = Set.of("Region", "Region2", "State", "State2");

      Fixture() {
         TableAssembly t1 = bound("T1", "Region", "State");
         TableAssembly t2 = bound("T2", "Region2", "State2");
         TableAssemblyOperator op = new TableAssemblyOperator(
            null, null, null, null, TableAssemblyOperator.UNION);
         op.getOperator(0).setDistinct(true);
         union = new ConcatenatedTableAssembly(
            ws, "C1", new TableAssembly[] { t1, t2 }, new TableAssemblyOperator[] { op });
         ws.addAssembly(union);

         for(String t : new String[] { "T1", "T2" }) {
            SelectionListVSAssembly list = new SelectionListVSAssembly(vs, "List_" + t);
            list.setTableName(t);
            list.setDataRef(column("T1".equals(t) ? "Region" : "Region2"));
            vs.addAssembly(list);
         }

         TableVSAssembly table = new TableVSAssembly(vs, VS_ASSEMBLY);
         table.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "C1"));
         vs.addAssembly(table);
      }

      private TableAssembly bound(String name, String... names) {
         PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, name);
         table.setSourceInfo(new SourceInfo(SourceInfo.PHYSICAL_TABLE, "ds1", "ORDERS"));
         ColumnSelection cols = new ColumnSelection();

         for(String col : names) {
            cols.addAttribute(column(col));
         }

         table.setColumnSelection(cols);
         ws.addAssembly(table);
         return table;
      }

      /** A real runtime condition (a value selected) on the column of a child. */
      void real(String table, int col, String value) {
         TableAssembly t = (TableAssembly) ws.getAssembly(table);
         AssetCondition cond = new AssetCondition();
         cond.setOperation(XCondition.ONE_OF);
         cond.addValue(value);
         cond.setType(XSchema.STRING);
         ConditionList list = new ConditionList();
         list.append(new ConditionItem(t.getColumnSelection(false).getAttribute(col), cond, 0));
         t.setPreRuntimeConditionList(list);
      }

      private MVColumn column0(Set<String> names) {
         MVColumn col = mock(MVColumn.class);
         when(col.matches(anyString(), anyBoolean())).thenAnswer(
            inv -> names.contains((String) inv.getArgument(0)));
         return col;
      }

      private MVDef def(String name, Set<String> names) {
         MVDef def = mock(MVDef.class);
         when(def.getName()).thenReturn(name);
         when(def.getBoundTable()).thenReturn("C1");
         when(def.getLastUpdateTime()).thenReturn(1L);
         MVColumn col = column0(names);
         when(def.getColumns()).thenReturn(List.of(col));
         return def;
      }

      private MVManager manager(boolean subMvs) {
         MVManager mgr = mock(MVManager.class);
         MVDef top = def("TOPMV", Set.of("Region", "State"));
         when(mgr.findMV(any(), any(), any(), any(), any())).thenAnswer(inv -> {
            String ptname = inv.getArgument(3);

            if(ptname == null) {
               return top;
            }

            return subMvs && ptname.startsWith("T") ? def("SUB_" + ptname, subMvColumns) : null;
         });
         return mgr;
      }

      private TableAssembly prepare() {
         // the viewsheet is held weakly by the runtime mv, vs is a field of this fixture
         union.setRuntimeMV(new RuntimeMV(entry, vs, VS_ASSEMBLY, "C1", "TOPMV", false, 1L,
                                          null));
         return union;
      }

      private XPrincipal user() {
         return new XPrincipal(new IdentityID("admin", Organization.getDefaultOrganizationID()));
      }

      TableAssembly transform(boolean subMvs) throws Exception {
         TableAssembly top = prepare();

         MVManager mgr = manager(subMvs);

         try(MockedStatic<MVManager> statics = mockStatic(MVManager.class)) {
            statics.when(MVManager::getManager).thenReturn(mgr);
            return MVTransformer.transform(user(), top);
         }
      }

      MVExecutionException transformFails(boolean subMvs) {
         TableAssembly top = prepare();

         MVManager mgr = manager(subMvs);

         try(MockedStatic<MVManager> statics = mockStatic(MVManager.class)) {
            statics.when(MVManager::getManager).thenReturn(mgr);
            return assertThrows(MVExecutionException.class,
                                () -> MVTransformer.transform(user(), top));
         }
      }
   }
}
