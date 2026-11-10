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
package inetsoft.web.wiz.viewsheet;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.VSModelTrapContext;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.erm.*;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.test.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80476: {@link AgentTrapCheck} against the REAL detector. The context under test extracts
 * data refs from the two infos by lookup (rather than from a live viewsheet) and then runs
 * {@code AbstractModelTrapContext.checkTrap()} unchanged, on a real {@code XPartition} where
 * Customer is 1-n Orders and 1-n Returns, so mixing the two many-sides is a chasm trap.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
class AgentTrapCheckTest {
   @Test
   void aNewChasmTrapIsReportedWithTheTableNames() {
      Optional<String> trap = check(new DataRef[]{ ORDERS }, new DataRef[]{ ORDERS }, new DataRef[]{ ORDERS, RETURNS },
                                new DataRef[]{ ORDERS, RETURNS });

      assertTrue(trap.isPresent());
      assertTrue(trap.get().startsWith("Chasm Trap is on"), trap.get());
      assertTrue(trap.get().contains("Orders") && trap.get().contains("Returns"), trap.get());
      assertTrue(AgentTrapWarning.forCondition(trap.get()).startsWith(AgentTrapWarning.PREFIX));
   }

   @Test
   void aChangeThatAddsNoTrapIsSilent() {
      assertTrue(check(new DataRef[]{ ORDERS }, new DataRef[]{ ORDERS },
                       new DataRef[]{ ORDERS, CUSTOMER }, new DataRef[]{ ORDERS }).isEmpty());
   }

   @Test
   void aPreExistingTrapIsSilent() {
      assertTrue(check(new DataRef[]{ ORDERS, RETURNS }, new DataRef[]{ ORDERS, RETURNS },
                       new DataRef[]{ ORDERS, RETURNS, CUSTOMER },
                       new DataRef[]{ ORDERS, RETURNS }).isEmpty());
   }

   @Test
   void anUnchangedBindingIsSilent() {
      assertTrue(check(new DataRef[]{ ORDERS }, new DataRef[]{ ORDERS }, new DataRef[]{ ORDERS },
                       new DataRef[]{ ORDERS }).isEmpty());
   }

   @Test
   void aModelWithoutCardinalityIsNotChecked() {
      try(MockedStatic<SreeEnv> env = Mockito.mockStatic(SreeEnv.class)) {
         Ctx context = new Ctx(model(), partition(false));
         TableVSAssemblyInfo oinfo = new TableVSAssemblyInfo();
         TableVSAssemblyInfo ninfo = new TableVSAssemblyInfo();
         context.refs.put(oinfo, new DataRef[][]{ { ORDERS }, { ORDERS } });
         context.refs.put(ninfo, new DataRef[][]{ { ORDERS, RETURNS }, { ORDERS, RETURNS } });

         assertTrue(AgentTrapCheck.newTrap(context, oinfo, ninfo).isEmpty());
      }
   }

   /** A failing detector must not fail the write: the warning-only check swallows it. */
   @Test
   void aFailingCheckIsSwallowed() {
      RuntimeViewsheet rvs = Mockito.mock(RuntimeViewsheet.class);
      Mockito.when(rvs.getViewsheet()).thenThrow(new IllegalStateException("boom"));

      assertTrue(AgentTrapCheck.newTrap(rvs, null, new TableVSAssemblyInfo(),
                                        new TableVSAssemblyInfo()).isEmpty());
   }

   @Test
   void thePrefixIsTheDocumentedContract() {
      assertEquals("Data-model trap: ", AgentTrapWarning.PREFIX);
      assertTrue(AgentTrapWarning.genericTrap().startsWith("Data-model trap: "));
      assertTrue(AgentTrapWarning.genericTrap().contains("undo"));
      assertTrue(AgentTrapWarning.isTrapWarning(AgentTrapWarning.forCondition("Fan Trap is on A")));
      assertFalse(AgentTrapWarning.isTrapWarning("Binding reset: x"));
   }

   private static Optional<String> check(DataRef[] beforeAll, DataRef[] beforeAggs,
                                       DataRef[] afterAll, DataRef[] afterAggs)
   {
      try(MockedStatic<SreeEnv> env = Mockito.mockStatic(SreeEnv.class)) {
         Ctx context = new Ctx(model(), partition(true));
         TableVSAssemblyInfo oinfo = new TableVSAssemblyInfo();
         TableVSAssemblyInfo ninfo = new TableVSAssemblyInfo();
         context.refs.put(oinfo, new DataRef[][]{ beforeAll, beforeAggs });
         context.refs.put(ninfo, new DataRef[][]{ afterAll, afterAggs });
         return AgentTrapCheck.newTrap(context, oinfo, ninfo);
      }
   }

   private static XPartition partition(boolean cardinality) {
      XPartition p = new XPartition("p");
      p.addTable("Customer", null, null);
      p.addTable("Orders", null, null);
      p.addTable("Returns", null, null);

      for(String many : new String[]{ "Orders", "Returns" }) {
         XRelationship r = new XRelationship(many, "cid", "Customer", "id");

         if(cardinality) {
            r.setDependentCardinality(XRelationship.MANY);
            r.setIndependentCardinality(XRelationship.ONE);
         }

         p.addRelationship(r);
      }

      return p;
   }

   private static XLogicalModel model() {
      XLogicalModel lm = new XLogicalModel("lm");

      for(String[] e : new String[][]{ { "Customer", "Name" }, { "Orders", "Total" },
                                      { "Returns", "Amount" } })
      {
         XEntity entity = new XEntity(e[0]);
         entity.addAttribute(new XAttribute(e[1], e[0], e[1] + "c"));
         lm.addEntity(entity);
      }

      return lm;
   }

   /**
    * The real {@code VSModelTrapContext.checkTrap(old, new)} reads the refs from a live
    * viewsheet; this one reads them from a lookup and runs the same detector.
    */
   private static final class Ctx extends VSModelTrapContext {
      Ctx(XLogicalModel model, XPartition partition) {
         super(Mockito.mock(RuntimeViewsheet.class), null);
         lm = model;
         this.partition = partition;
         isCheckTrap = partition.getRelationships().hasMoreElements() &&
            hasCardinality(partition);
      }

      private static boolean hasCardinality(XPartition p) {
         var relations = p.getRelationships();

         while(relations.hasMoreElements()) {
            XRelationship r = (XRelationship) relations.nextElement();

            if(r.getIndependentCardinality() != 0 || r.getDependentCardinality() != 0) {
               return true;
            }
         }

         return false;
      }

      @Override
      public TrapInfo checkTrap(VSAssemblyInfo oinfo, VSAssemblyInfo ninfo) {
         DataRef[][] before = refs.get(oinfo);
         DataRef[][] after = refs.get(ninfo);
         otables = getTables(before[0]);
         oaggs = before[1];
         tables = getTables(after[0]);
         aggs = after[1];
         return checkTrap();
      }

      final Map<VSAssemblyInfo, DataRef[][]> refs = new IdentityHashMap<>();
   }

   private static final DataRef ORDERS = new AttributeRef("Orders", "Total");
   private static final DataRef RETURNS = new AttributeRef("Returns", "Amount");
   private static final DataRef CUSTOMER = new AttributeRef("Customer", "Name");
}
