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

import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import inetsoft.web.wiz.viewsheet.AgentTrapWarning;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80958: {@link WorksheetTrapCheck} against the REAL detector. The tables are built on a real
 * {@code XPartition} where Customer is 1-n Orders and 1-n Returns, and the "after" state is
 * produced by the real {@link WorksheetMutationSupport} mutators, so mixing the two many-sides
 * (or measuring a one-side through a many-side group) is a chasm (fan) trap.
 */
@WizAgentTestSupport
class WorksheetTrapCheckTest {
   // ------------------------------------------------------------------- positives

   @Test
   void groupingByTheManySideAndSummingTheOneSideIsAFanTrap() throws Exception {
      BoundTableAssembly t = model("Customer.Credit.double", "Orders.Status.string",
                                   "Orders.Total.double", "Customer.Name.string");
      TableAssembly before = (TableAssembly) t.clone();

      groupStatusSum(t, "Credit");

      Optional<String> trap = WorksheetTrapCheck.newTrap(before, t, true, null, registry(true));

      assertTrue(trap.isPresent());
      assertEquals(AgentTrapWarning.forCondition("Fan Trap is on Orders, Customer"), trap.get());
      assertTrue(trap.get().startsWith("Data-model trap: "), trap.get());
   }

   @Test
   void aFilterOnAThirdEntityColumnOfAnAggregatedTableIsAChasmTrap() throws Exception {
      BoundTableAssembly t = model("Orders.Total.double", "Orders.Status.string",
                                   "Returns.Amount.double");
      groupStatusSum(t, "Total");
      TableAssembly before = (TableAssembly) t.clone();

      WorksheetMutationSupport.addFilter(t, "Amount", "=", "5");

      Optional<String> trap = WorksheetTrapCheck.newTrap(before, t, false, null, registry(true));

      assertTrue(trap.isPresent());
      assertEquals(AgentTrapWarning.forCondition("Chasm Trap is on Orders, Returns, Customer"),
                   trap.get());
   }

   @Test
   void aGroupedMirrorOfAModelBoundTableIsChecked() throws Exception {
      BoundTableAssembly base = model("Customer.Credit.double", "Orders.Status.string");
      Worksheet ws = base.getWorksheet();
      ws.addAssembly(base);
      MirrorTableAssembly mirror = new MirrorTableAssembly(ws, "M", base);
      ws.addAssembly(mirror);
      TableAssembly before = (TableAssembly) mirror.clone();
      ColumnSelection cols = mirror.getColumnSelection(true);
      AggregateInfo agg = new AggregateInfo();
      agg.addGroup(new GroupRef(cols.getAttribute(1)));
      agg.addAggregate(new AggregateRef(cols.getAttribute(0), AggregateFormula.SUM));
      mirror.setAggregateInfo(agg);
      mirror.setAggregate(true);

      Optional<String> trap = WorksheetTrapCheck.newTrap(before, mirror, true, null, registry(true));

      assertTrue(trap.isPresent());
      assertTrue(trap.get().contains("Fan Trap"), trap.get());
   }

   // ------------------------------------------------------------------- negatives

   @Test
   void aModelWithoutDeclaredCardinalityIsSilent() throws Exception {
      BoundTableAssembly t = model("Customer.Credit.double", "Orders.Status.string");
      TableAssembly before = (TableAssembly) t.clone();
      groupStatusSum(t, "Credit");

      assertTrue(WorksheetTrapCheck.newTrap(before, t, true, null, registry(false)).isEmpty());
   }

   @Test
   void aPhysicalTableIsSilent() throws Exception {
      BoundTableAssembly t = table(SourceInfo.PHYSICAL_TABLE, "T", "Customer.Credit.double",
                                   "Orders.Status.string");
      TableAssembly before = (TableAssembly) t.clone();
      groupStatusSum(t, "Credit");

      assertTrue(WorksheetTrapCheck.newTrap(before, t, true, null, registry(true)).isEmpty());
   }

   @Test
   void aTrapThatWasAlreadyThereIsSilent() throws Exception {
      BoundTableAssembly t = model("Orders.Total.double", "Orders.Status.string",
                                   "Returns.Amount.double");
      groupStatusSum(t, "Total");
      WorksheetMutationSupport.addFilter(t, "Amount", "=", "5");
      TableAssembly before = (TableAssembly) t.clone();

      WorksheetMutationSupport.replaceFilter(t, "Amount", "=", "6");

      assertTrue(WorksheetTrapCheck.newTrap(before, t, false, null, registry(true)).isEmpty());
   }

   @Test
   void aFilterOnASelectedColumnOfAnUngroupedTableIsSilent() throws Exception {
      BoundTableAssembly t = model("Orders.Total.double", "Customer.Name.string");
      TableAssembly before = (TableAssembly) t.clone();

      WorksheetMutationSupport.addFilter(t, "Name", "=", "x");

      assertTrue(WorksheetTrapCheck.newTrap(before, t, false, null, registry(true)).isEmpty());
   }

   @Test
   void aFilterRoutedToThePostAggregateListIsNotChecked() throws Exception {
      BoundTableAssembly t = model("Orders.Total.double", "Orders.Status.string",
                                   "Returns.Amount.double");
      groupStatusSum(t, "Total");
      TableAssembly before = (TableAssembly) t.clone();

      WorksheetMutationSupport.addFilter(t, "AggTotal", ">", "5");

      assertTrue(t.getPostConditionList().getConditionSize() > 0, "the filter went to the post list");
      assertTrue(WorksheetTrapCheck.newTrap(before, t, false, null, registry(true)).isEmpty());
   }

   @Test
   void aJoinTableIsSilent() throws Exception {
      BoundTableAssembly left = model("Customer.Credit.double", "Orders.Status.string");
      BoundTableAssembly right = table(SourceInfo.MODEL, "R", "Returns.Amount.double");
      Worksheet ws = left.getWorksheet();
      ws.addAssembly(left);
      ws.addAssembly(right);
      RelationalJoinTableAssembly join = new RelationalJoinTableAssembly(
         ws, "J", new TableAssembly[]{ left, right },
         new TableAssemblyOperator[]{ new TableAssemblyOperator() });
      ws.addAssembly(join);
      ColumnSelection cols = new ColumnSelection();
      cols.addAttribute(left.getColumnSelection(true).getAttribute(0));
      cols.addAttribute(left.getColumnSelection(true).getAttribute(1));
      join.setColumnSelection(cols);
      TableAssembly before = (TableAssembly) join.clone();
      AggregateInfo agg = new AggregateInfo();
      agg.addGroup(new GroupRef(cols.getAttribute(1)));
      agg.addAggregate(new AggregateRef(cols.getAttribute(0), AggregateFormula.SUM));
      join.setAggregateInfo(agg);
      join.setAggregate(true);

      assertTrue(WorksheetTrapCheck.newTrap(before, join, true, null, registry(true)).isEmpty());
   }

   @Test
   void aMissingTableIsSilent() throws Exception {
      BoundTableAssembly t = model("Customer.Credit.double");

      assertTrue(WorksheetTrapCheck.newTrap(null, t, true, null, registry(true)).isEmpty());
      assertTrue(WorksheetTrapCheck.newTrap(t, null, true, null, registry(true)).isEmpty());
   }

   @Test
   void aFailingDetectorIsSwallowed() throws Exception {
      BoundTableAssembly t = model("Customer.Credit.double", "Orders.Status.string");
      TableAssembly before = (TableAssembly) t.clone();
      groupStatusSum(t, "Credit");
      DataSourceRegistry failing = Mockito.mock(DataSourceRegistry.class);
      Mockito.when(failing.getDataModel(Mockito.anyString()))
         .thenThrow(new IllegalStateException("boom"));

      assertTrue(WorksheetTrapCheck.newTrap(before, t, true, null, failing).isEmpty());
   }

   // ------------------------------------------------------------------- fixtures

   private static void groupStatusSum(BoundTableAssembly t, String measure)
      throws Exception
   {
      WorksheetMutationSupport.applyAggregateInfo(
         t, List.of(new WorksheetMutationSupport.GroupSpec("Status")),
         List.of(new WorksheetMutationSupport.AggregateSpec(measure, "Sum", "Agg" + measure)));
   }

   private static XDataModel dataModel(boolean cardinality) {
      XPartition p = new XPartition("p");
      p.addTable("Customer", null, null);
      p.addTable("Orders", null, null);
      p.addTable("Returns", null, null);
      XRelationship r1 = new XRelationship("Orders", "cid", "Customer", "id");
      XRelationship r2 = new XRelationship("Returns", "cid", "Customer", "id");

      if(cardinality) {
         for(XRelationship r : List.of(r1, r2)) {
            r.setDependentCardinality(XRelationship.MANY);
            r.setIndependentCardinality(XRelationship.ONE);
         }
      }

      p.addRelationship(r1);
      p.addRelationship(r2);
      XLogicalModel lm = new XLogicalModel("lm");
      lm.setPartition("p");
      String[][] attrs = {{"Customer", "Name"}, {"Customer", "Credit"}, {"Orders", "Total"},
                          {"Orders", "Status"}, {"Returns", "Amount"}};
      Map<String, XEntity> entities = new LinkedHashMap<>();

      for(String[] a : attrs) {
         XEntity entity = entities.computeIfAbsent(a[0], XEntity::new);
         XAttribute attr = new XAttribute(a[1], a[0], a[1] + "c");

         if(a[1].equals("Credit") || a[1].equals("Total") || a[1].equals("Amount")) {
            attr.setDataType("double");
            attr.setRefType(DataRef.MEASURE);
         }
         else {
            attr.setDataType("string");
         }

         entity.addAttribute(attr);
      }

      entities.values().forEach(lm::addEntity);
      XDataModel model = Mockito.mock(XDataModel.class);
      Mockito.when(model.getLogicalModel(Mockito.anyString(), Mockito.any())).thenReturn(lm);
      Mockito.when(model.getPartition(Mockito.anyString(), Mockito.any())).thenReturn(p);
      return model;
   }

   private static DataSourceRegistry registry(boolean cardinality) {
      XDataModel model = dataModel(cardinality);
      DataSourceRegistry registry = Mockito.mock(DataSourceRegistry.class);
      Mockito.when(registry.getDataModel("ds")).thenReturn(model);
      return registry;
   }

   private static BoundTableAssembly model(String... cols) {
      return table(SourceInfo.MODEL, "T", cols);
   }

   /** Columns are given as {@code Entity.Attribute.type}. */
   private static BoundTableAssembly table(int sourceType, String name, String... cols) {
      BoundTableAssembly t = new BoundTableAssembly(new Worksheet(), name);
      t.setSourceInfo(new SourceInfo(sourceType, "ds", "lm"));
      ColumnSelection selection = new ColumnSelection();

      for(String col : cols) {
         String[] s = col.split("\\.");
         AttributeRef ref = new AttributeRef(s[0], s[1]);

         if(s[2].equals("double")) {
            ref.setRefType(DataRef.MEASURE);
         }

         ColumnRef column = new ColumnRef(ref);
         column.setDataType(s[2]);
         selection.addAttribute(column);
      }

      t.setColumnSelection(selection);
      return t;
   }

   // ------------------------------------------------------------------- new tables (#81176)

   @Test
   void aNewTableMixingTheManySideAndTheOneSideIsAFanTrap() throws Exception {
      BoundTableAssembly t = model("Customer.Credit.double", "Orders.Status.string",
                                   "Orders.Total.double");
      groupStatusSum(t, "Credit");

      Optional<String> trap = WorksheetTrapCheck.newTableTrap(t, null, registry(true));

      assertTrue(trap.isPresent());
      assertTrue(trap.get().startsWith("Data-model trap: "), trap.get());
   }

   @Test
   void aNewTableOverASingleEntityIsSilent() throws Exception {
      BoundTableAssembly t = model("Orders.Total.double", "Orders.Status.string");

      assertTrue(WorksheetTrapCheck.newTableTrap(t, null, registry(true)).isEmpty());
   }

   @Test
   void aNewTableOfAModelWithoutDeclaredCardinalityIsSilent() throws Exception {
      BoundTableAssembly t = model("Customer.Credit.double", "Orders.Status.string",
                                   "Orders.Total.double");
      groupStatusSum(t, "Credit");

      assertTrue(WorksheetTrapCheck.newTableTrap(t, null, registry(false)).isEmpty());
   }

   @Test
   void aNewTableCheckThatFailsIsSwallowed() throws Exception {
      BoundTableAssembly t = model("Customer.Credit.double", "Orders.Status.string");
      DataSourceRegistry failing = Mockito.mock(DataSourceRegistry.class);
      Mockito.when(failing.getDataModel(Mockito.anyString()))
         .thenThrow(new IllegalStateException("boom"));

      assertTrue(WorksheetTrapCheck.newTableTrap(t, null, failing).isEmpty());
      assertTrue(WorksheetTrapCheck.newTableTrap(null, null, failing).isEmpty());
   }
}
