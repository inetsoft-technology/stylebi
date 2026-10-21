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
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.AggregateRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.viewsheet.CalculateRef;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.VSAggregateRef;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.web.binding.controller.ModifyCalculateFieldService;
import inetsoft.web.wiz.pairing.*;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #78969 (VCF-029): the sub-aggregates of an aggregate calc field ({@code field['Sum(Total)']})
 * are resolved from the live bindings of the table, so a calc field created before anything is
 * bound to them loses them the moment the helper that later bound them is removed. The sweep
 * persists them as user aggregate fields while a binder is still present.
 */
@WizAgentTestSupport
class CalcFieldAggregatePersistenceTest {
   private static final String TABLE = "Orders";
   private static final String EXPR = "field['Sum(Total)'] * 2";

   private static CalculateRef calc(String name, String expression, boolean baseOnDetail) {
      ExpressionRef exprRef = new ExpressionRef();
      exprRef.setName(name);
      exprRef.setExpression(expression);
      CalculateRef cref = new CalculateRef(baseOnDetail);
      cref.setDataRef(exprRef);
      cref.setSQL(false);
      return cref;
   }

   private static void helper(Viewsheet vs, String name, String column) {
      CrosstabVSAssembly cross = new CrosstabVSAssembly(vs, name);
      cross.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, TABLE));
      VSAggregateRef ref = new VSAggregateRef();
      AttributeRef attr = new AttributeRef(null, column);
      attr.setDataType("double");
      ref.setDataRef(attr);
      ref.setColumnValue(column);
      ref.setFormula(AggregateFormula.SUM);
      cross.getVSCrosstabInfo().setDesignAggregates(new DataRef[] { ref });
      vs.addAssembly(cross);
   }

   private static boolean resolves(Viewsheet vs, String expression) {
      AggregateRef[] all = VSUtil.getAggregates(vs, TABLE, false);
      return !VSUtil.findAggregate(all, new ArrayList<>(), expression).isEmpty();
   }

   private static AggregateRef aggregate(String column) {
      AttributeRef attr = new AttributeRef(null, column);
      attr.setDataType("double");
      return new AggregateRef(attr, AggregateFormula.SUM);
   }

   private static List<String> names(AggregateRef[] refs) {
      List<String> list = new ArrayList<>();

      for(AggregateRef ref : refs) {
         list.add(ref.toString());
      }

      return list;
   }

   private static Principal agent() {
      return TestPrincipals.user("alice", "host-org");
   }

   private static ViewsheetSessionService service(Viewsheet vs) throws Exception {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      SheetAgentBroadcastService broadcast = mock(SheetAgentBroadcastService.class);
      Principal agent = agent();
      JoinSession s = new JoinSession("TOK", "Viewsheet/foo-7", "alice~;~host-org",
                                      SheetType.VIEWSHEET, 0L, Long.MAX_VALUE,
                                      JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(eq(SheetType.VIEWSHEET), eq("Viewsheet/foo-7"),
                                            eq(agent)))
         .thenReturn(rvs);
      return new ViewsheetSessionService(sessions, runtimeAccess, broadcast);
   }

   @Test
   void persistsAggregatesBoundOnlyByAnAssemblyThatIsThenRemoved() {
      Viewsheet vs = new Viewsheet();
      vs.addCalcField(TABLE, calc("Double", EXPR, false));
      helper(vs, "Helper", "Total");
      assertNull(vs.getAggrFields(TABLE));

      ModifyCalculateFieldService.persistUsedAggregateRefs(vs);
      vs.removeAssembly("Helper");

      assertEquals(1, vs.getAggrFields(TABLE).length);
      assertTrue(resolves(vs, EXPR));
   }

   @Test
   void withoutTheSweepRemovingTheLastBinderLosesTheAggregate() {
      Viewsheet vs = new Viewsheet();
      vs.addCalcField(TABLE, calc("Double", EXPR, false));
      helper(vs, "Helper", "Total");
      assertTrue(resolves(vs, EXPR));

      vs.removeAssembly("Helper");

      assertFalse(resolves(vs, EXPR));
   }

   @Test
   void isIdempotentAndDoesNotReorderRefsThatArePresent() {
      Viewsheet vs = new Viewsheet();
      vs.addCalcField(TABLE, calc("Ratio", "field['Sum(Total)'] / field['Sum(Qty)']", false));
      helper(vs, "H1", "Total");
      helper(vs, "H2", "Qty");

      ModifyCalculateFieldService.persistUsedAggregateRefs(vs);
      List<String> first = names(vs.getAggrFields(TABLE));
      assertEquals(2, first.size());

      ModifyCalculateFieldService.persistUsedAggregateRefs(vs);
      ModifyCalculateFieldService.persistUsedAggregateRefs(vs);

      assertEquals(first, names(vs.getAggrFields(TABLE)));
   }

   @Test
   void leavesAnAlreadyPersistedRefInItsPosition() {
      Viewsheet vs = new Viewsheet();
      vs.addCalcField(TABLE, calc("Double", EXPR, false));
      vs.addAggrField(TABLE, aggregate("Total"));
      vs.addAggrField(TABLE, aggregate("Other"));
      List<String> before = names(vs.getAggrFields(TABLE));

      ModifyCalculateFieldService.persistUsedAggregateRefs(vs);

      assertEquals(before, names(vs.getAggrFields(TABLE)));
   }

   @Test
   void ignoresTablesWithoutAnAggregateCalcField() {
      Viewsheet vs = new Viewsheet();
      vs.addCalcField(TABLE, calc("Detail", "field['Total'] * 2", true));
      helper(vs, "Helper", "Total");

      ModifyCalculateFieldService.persistUsedAggregateRefs(vs);

      assertNull(vs.getAggrFields(TABLE));
   }

   @Test
   void ignoresSqlCalcFields() {
      Viewsheet vs = new Viewsheet();
      CalculateRef sql = calc("SqlAgg", "sum(Total)", false);
      sql.setSQL(true);
      vs.addCalcField(TABLE, sql);
      helper(vs, "Helper", "Total");

      ModifyCalculateFieldService.persistUsedAggregateRefs(vs);

      assertNull(vs.getAggrFields(TABLE));
   }

   @Test
   void mutateSweepsBeforeAMutationThatDropsTheLastBinder() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.addCalcField(TABLE, calc("Double", EXPR, false));
      helper(vs, "Helper", "Total");

      service(vs).mutate("TOK", agent(), (r, id, dispatcher) -> vs.removeAssembly("Helper"));

      assertTrue(resolves(vs, EXPR));
   }

   @Test
   void mutateSweepsAfterAMutationThatAddsTheBinder() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.addCalcField(TABLE, calc("Double", EXPR, false));

      service(vs).mutate("TOK", agent(), (r, id, dispatcher) -> helper(vs, "Helper", "Total"));
      vs.removeAssembly("Helper");

      assertTrue(resolves(vs, EXPR));
   }

   @Test
   void mutateSweepsEvenWhenTheMutationThrowsAndTheThrowIsNotMasked() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.addCalcField(TABLE, calc("Double", EXPR, false));
      helper(vs, "Helper", "Total");
      ViewsheetSessionService svc = service(vs);

      IllegalStateException e = assertThrows(IllegalStateException.class,
         () -> svc.mutate("TOK", agent(), (r, id, dispatcher) -> {
            vs.removeAssembly("Helper");
            throw new IllegalStateException("boom");
         }));

      assertEquals("boom", e.getMessage());
      assertTrue(resolves(vs, EXPR));
   }

   @Test
   void aFailingSweepNeverBreaksTheMutation() throws Exception {
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getCalcFieldSources()).thenThrow(new IllegalStateException("sweep failure"));
      boolean[] ran = { false };

      service(vs).mutate("TOK", agent(), (r, id, dispatcher) -> ran[0] = true);

      assertTrue(ran[0]);
   }
}
