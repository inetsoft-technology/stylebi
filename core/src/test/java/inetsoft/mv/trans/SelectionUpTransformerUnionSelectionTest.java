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
package inetsoft.mv.trans;

import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82210. With {@code mv.union.moveUp=true} the selections of the children of a distinct
 * union move up to the union and are ORed. A child that has nothing selected carries a pseudo
 * condition, and the pseudo conditions are removed after the transformation. So a union
 * condition of "real OR pseudo" turned into the real condition alone and dropped every row of
 * the child without a selection. At runtime the selections must therefore move up only when
 * every child has a real condition on the column, or none has; otherwise the transformer
 * reports a union fault and asks for no MV recreation (the query runs live).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SelectionUpTransformerUnionSelectionTest {
   private static final String UNION_FAULT = "union table";

   @BeforeEach
   void enableMoveUp() {
      SreeEnv.setProperty("mv.union.moveUp", "true");
   }

   @AfterEach
   void resetProperties() {
      SreeEnv.resetProperty("mv.union.moveUp", false);
   }

   // ---------------------------------------------------------------- runtime

   @ParameterizedTest
   @ValueSource(ints = { 0, 1 })
   void nothingSelectedMovesUp(int col) throws Exception {
      Fixture fx = new Fixture(2);
      fx.selectAll(col);

      Result r = fx.run(TransformationDescriptor.RUN_MODE);

      assertNoFault(r);
      assertEquals(0, r.unionConditionSize());
   }

   @ParameterizedTest
   @ValueSource(ints = { 0, 1 })
   void onlyFirstChildSelectedFallsBack(int col) throws Exception {
      Fixture fx = new Fixture(2);
      fx.selectAll(col);
      fx.real("T1", col, "A");

      Result r = fx.run(TransformationDescriptor.RUN_MODE);

      assertFault(r);
      assertFalse(r.transformer.isNeedRecreateMV());
   }

   @ParameterizedTest
   @ValueSource(ints = { 0, 1 })
   void onlySecondChildSelectedFallsBack(int col) throws Exception {
      Fixture fx = new Fixture(2);
      fx.selectAll(col);
      fx.real("T2", col, "B");

      Result r = fx.run(TransformationDescriptor.RUN_MODE);

      assertFault(r);
      assertFalse(r.transformer.isNeedRecreateMV());
   }

   @ParameterizedTest
   @ValueSource(ints = { 0, 1 })
   void bothChildrenSelectedMoveUp(int col) throws Exception {
      Fixture fx = new Fixture(2);
      fx.selectAll(col);
      fx.real("T1", col, "A");
      fx.real("T2", col, "B");

      Result r = fx.run(TransformationDescriptor.RUN_MODE);

      assertNoFault(r);
      // "col = A OR col = B"
      assertEquals(3, r.unionConditionSize());
      assertTrue(r.transformer.isNeedRecreateMV());
   }

   @Test
   void threeChildrenOneSelectedFallsBack() throws Exception {
      Fixture fx = new Fixture(3);
      fx.selectAll(0);
      fx.real("T2", 0, "B");

      Result r = fx.run(TransformationDescriptor.RUN_MODE);

      assertFault(r);
      assertFalse(r.transformer.isNeedRecreateMV());
   }

   @Test
   void threeChildrenNothingSelectedMovesUp() throws Exception {
      Fixture fx = new Fixture(3);
      fx.selectAll(0);

      Result r = fx.run(TransformationDescriptor.RUN_MODE);

      assertNoFault(r);
      assertEquals(0, r.unionConditionSize());
   }

   @Test
   void threeChildrenAllSelectedMoveUp() throws Exception {
      Fixture fx = new Fixture(3);
      fx.selectAll(0);
      fx.real("T1", 0, "A");
      fx.real("T2", 0, "B");
      fx.real("T3", 0, "C");

      Result r = fx.run(TransformationDescriptor.RUN_MODE);

      assertNoFault(r);
      assertEquals(5, r.unionConditionSize());
   }

   @Test
   void selectionOnDifferentColumnsOfTheChildrenFallsBack() throws Exception {
      // the first child is restricted on column 0, the second only on column 1: neither
      // column is restricted on both children
      Fixture fx = new Fixture(2);
      fx.selectAll(0);
      fx.selectAll(1);
      fx.real("T1", 0, "A");
      fx.real("T2", 1, "B");

      Result r = fx.run(TransformationDescriptor.RUN_MODE);

      assertFault(r);
      assertFalse(r.transformer.isNeedRecreateMV());
   }

   @Test
   void pseudoPostConditionMixedWithARealOneFallsBack() throws Exception {
      Fixture fx = new Fixture(2);
      fx.selectAll(0);
      fx.realPost("T1", 0, "A");
      fx.pseudoPost("T2", 0);

      Result r = fx.run(TransformationDescriptor.RUN_MODE);

      assertFault(r);
      assertFalse(r.transformer.isNeedRecreateMV());
   }

   @Test
   void pseudoPostConditionOnBothChildrenMovesUp() throws Exception {
      Fixture fx = new Fixture(2);
      fx.selectAll(0);
      fx.pseudoPost("T1", 0);
      fx.pseudoPost("T2", 0);

      assertNoFault(fx.run(TransformationDescriptor.RUN_MODE));
   }

   // ---------------------------------------------------------------- unchanged

   @Test
   void analysisModeStillMovesUpWithAPseudoCondition() throws Exception {
      // MV creation: the child without a (real) selection carries the pseudo condition and
      // the selection of the other child still moves up, otherwise the MV could not be created
      Fixture fx = new Fixture(2);
      fx.selectAll(0);
      fx.real("T1", 0, "A");

      Result r = fx.run(TransformationDescriptor.ANALYTIC_MODE);

      assertNoFault(r);
   }

   @Test
   void moveUpDisabledStillFaultsAndRecreates() throws Exception {
      SreeEnv.setProperty("mv.union.moveUp", "false");
      Fixture fx = new Fixture(2);
      fx.selectAll(0);

      Result r = fx.run(TransformationDescriptor.RUN_MODE);

      assertFault(r);
      // not caused by the pseudo condition: the existing behavior is kept
      assertTrue(r.transformer.isNeedRecreateMV());
   }

   // ---------------------------------------------------------------- helpers

   private static void assertFault(Result r) {
      assertTrue(r.reasons().stream().anyMatch(s -> s.contains(UNION_FAULT)),
                 "no union fault: " + r.reasons());
      assertFalse(r.moved, "the selection must not move up");
   }

   private static void assertNoFault(Result r) {
      assertEquals(List.of(), r.reasons());
      assertTrue(r.moved, "the selection must move up");
   }

   private static ColumnRef column(String name) {
      ColumnRef ref = new ColumnRef(new AttributeRef(null, name));
      ref.setDataType(XSchema.STRING);
      return ref;
   }

   private static final class Result {
      final TransformationDescriptor desc;
      final SelectionUpTransformer transformer;
      final boolean moved;

      Result(TransformationDescriptor desc, SelectionUpTransformer transformer,
             boolean moved)
      {
         this.desc = desc;
         this.transformer = transformer;
         this.moved = moved;
      }

      List<String> reasons() {
         List<?> faults = desc.getFaults(Fixture.VS_ASSEMBLY);
         List<String> reasons = new ArrayList<>();

         if(faults != null) {
            for(Object fault : faults) {
               reasons.add(((TransformationFault) fault).getReason());
            }
         }

         return reasons;
      }

      /** Size of the union's pre runtime condition once the pseudo conditions are removed. */
      int unionConditionSize() {
         TableAssembly union = (TableAssembly) desc.getWorksheet().getAssembly(Fixture.UNION);
         TransformationDescriptor.clearPseudoFilter(union, TransformationDescriptor.RUN_MODE);
         ConditionListWrapper wrapper = union.getPreRuntimeConditionList();
         return wrapper == null ? 0 : wrapper.getConditionList().getSize();
      }
   }

   /**
    * A distinct union of {@code n} bound tables T1..Tn, each with a selection list on every
    * column that {@link #selectAll(int)} was called for.
    */
   private static final class Fixture {
      static final String VS_ASSEMBLY = "Table1";
      static final String UNION = "C1";

      final Worksheet ws = new Worksheet();
      final Viewsheet vs = new Viewsheet() {
         @Override
         public Worksheet getBaseWorksheet() {
            return ws;
         }
      };
      final int n;
      final String[] names = { "Region", "State", "Amount" };

      Fixture(int n) {
         this.n = n;
         TableAssembly[] tables = new TableAssembly[n];
         TableAssemblyOperator[] ops = new TableAssemblyOperator[n - 1];

         for(int i = 0; i < n; i++) {
            tables[i] = bound("T" + (i + 1));
         }

         for(int i = 0; i < n - 1; i++) {
            ops[i] = new TableAssemblyOperator(null, null, null, null,
                                               TableAssemblyOperator.UNION);
            ops[i].getOperator(0).setDistinct(true);
         }

         ws.addAssembly(new ConcatenatedTableAssembly(ws, UNION, tables, ops));
      }

      private TableAssembly bound(String name) {
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

      /** A selection list on the column of every child. */
      void selectAll(int col) {
         for(int i = 1; i <= n; i++) {
            SelectionListVSAssembly list =
               new SelectionListVSAssembly(vs, "List_" + col + "_T" + i);
            list.setTableName("T" + i);
            list.setDataRef(column(names[col]));
            vs.addAssembly(list);
         }
      }

      /** A real runtime condition (a value selected) on the column of a child. */
      void real(String table, int col, String value) {
         TableAssembly t = (TableAssembly) ws.getAssembly(table);
         t.setPreRuntimeConditionList(merge(t.getPreRuntimeConditionList(), equalTo(t, col, value)));
      }

      void realPost(String table, int col, String value) {
         TableAssembly t = (TableAssembly) ws.getAssembly(table);
         t.setPostRuntimeConditionList(
            merge(t.getPostRuntimeConditionList(), equalTo(t, col, value)));
      }

      void pseudoPost(String table, int col) {
         TableAssembly t = (TableAssembly) ws.getAssembly(table);
         AssetCondition cond = new AssetCondition();
         cond.setOperation(XCondition.PSEUDO);
         cond.addValue(null);
         cond.setType(XSchema.STRING);
         ConditionList list = new ConditionList();
         list.append(new ConditionItem(t.getColumnSelection(false).getAttribute(col), cond, 0));
         t.setPostRuntimeConditionList(merge(t.getPostRuntimeConditionList(), list));
      }

      private static ConditionList equalTo(TableAssembly t, int col, String value) {
         AssetCondition cond = new AssetCondition();
         cond.setOperation(XCondition.ONE_OF);
         cond.addValue(value);
         cond.setType(XSchema.STRING);
         ConditionList list = new ConditionList();
         list.append(new ConditionItem(t.getColumnSelection(false).getAttribute(col), cond, 0));
         return list;
      }

      private static ConditionListWrapper merge(ConditionListWrapper old, ConditionList add) {
         ConditionList conds = old == null ? null : old.getConditionList();

         if(conds == null || conds.isEmpty()) {
            return add;
         }

         // AND the new condition after the existing ones
         ConditionList merged = (ConditionList) conds.clone();
         merged.append(new JunctionOperator(JunctionOperator.AND, 0));
         merged.append(add.getConditionItem(0));
         return merged;
      }

      Result run(int mode) {
         TableVSAssembly table = new TableVSAssembly(vs, VS_ASSEMBLY);
         table.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, UNION));
         vs.addAssembly(table);

         TransformationDescriptor desc = new TransformationDescriptor(vs, ws, mode);
         desc.reset(VS_ASSEMBLY, UNION, UNION, UNION);
         SelectionUpTransformer transformer = new SelectionUpTransformer();
         boolean moved = transformer.transform(desc);
         return new Result(desc, transformer, moved);
      }
   }
}
