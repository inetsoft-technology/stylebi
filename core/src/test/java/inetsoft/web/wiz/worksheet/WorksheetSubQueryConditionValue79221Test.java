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
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.uql.Condition;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.web.wiz.pairing.TestWorksheets;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import inetsoft.web.wiz.worksheet.WorksheetMutationSupport.ConditionNode;
import inetsoft.web.wiz.worksheet.WorksheetMutationSupport.ConditionSpec;
import inetsoft.web.wiz.worksheet.WorksheetMutationSupport.ConditionValueSpec;
import inetsoft.web.wiz.worksheet.model.WorksheetModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #79221: a sub query condition value is read back as a {@code "subquery"} valueSpec, and the
 * {@code "Query[name]"} text it leaves in {@code values} is refused as a literal on write.
 */
@Tag("core")
@WizAgentTestSupport
class WorksheetSubQueryConditionValue79221Test {
   private Worksheet ws;
   private BoundTableAssembly t;

   private void fixture(String... otherTables) {
      ws = new Worksheet();
      t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "STATE", "N", "D");
      ((ColumnRef) t.getColumnSelection(false).getAttribute("N")).setDataType(XSchema.INTEGER);
      ((ColumnRef) t.getColumnSelection(false).getAttribute("D")).setDataType(XSchema.DATE);
      ws.addAssembly(t);

      for(String name : otherTables) {
         ws.addAssembly(TestWorksheets.nonEmbeddedTableWithColumns(ws, name, "STATE"));
      }
   }

   private static SubQueryValue subQuery(String query, String column, String subColumn,
                                         String mainColumn)
   {
      SubQueryValue sq = new SubQueryValue();
      sq.setQuery(query);
      sq.setAttribute(new ColumnRef(new AttributeRef(null, column)));

      if(subColumn != null) {
         sq.setSubAttribute(new ColumnRef(new AttributeRef(null, subColumn)));
         sq.setMainAttribute(new ColumnRef(new AttributeRef(null, mainColumn)));
      }

      return sq;
   }

   private ConditionList conditionOn(String column, Object... values) {
      Condition c = new Condition(XSchema.STRING);
      c.setOperation(XCondition.ONE_OF);

      for(Object value : values) {
         c.addValue(value);
      }

      ConditionList cl = new ConditionList();
      cl.append(new ConditionItem(t.getColumnSelection(false).getAttribute(column), c, 0));
      return cl;
   }

   private WorksheetModel.TableModel read() {
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      WorksheetModel m = new WorksheetReadService().read(rws);
      return m.tables().stream().filter(tm -> "T".equals(tm.name())).findFirst().orElseThrow();
   }

   private void set(String field, List<String> values, ConditionValueSpec... specs) {
      WorksheetMutationSupport.setConditions(
         t, List.of(new ConditionNode(
            new ConditionSpec(field, "ONE_OF", values, false, null, List.of(specs)), null, 0)),
         false);
   }

   private static String refusal(Runnable call) {
      return assertThrows(IllegalArgumentException.class, call::run).getMessage();
   }

   private static void assertSubQueryRefusal(String message, String field, String table,
                                             boolean specsAvailable)
   {
      String spec = "valueSpecs entry {index: ";
      String remedy = specsAvailable
         ? "carry it as a " + spec
         : "take literal values only -- use set_conditions with a " + spec;

      assertTrue(message.contains("\"" + field + "\"") && message.contains("sub query value") &&
                 message.contains("\"" + table + "\"") && message.contains(remedy) &&
                 message.contains("valueType: \"subquery\", subQueryName: \"" + table + "\"") &&
                 !message.contains("recreate") && !message.contains("cannot be created"),
                 message);
   }

   // ----- read side

   @Test
   void readReportsUncorrelatedSubQueryAsSubqueryValueSpec() {
      fixture("SUP");
      t.setPreConditionList(conditionOn("STATE", subQuery("SUP", "STATE", null, null)));

      WorksheetModel.FilterModel filter = read().preConditions().get(0);

      assertEquals(List.of("Query[SUP]"), filter.values());
      assertEquals(
         List.of(new WorksheetModel.ValueSpecModel(
            0, "subquery", null, null, null, "SUP", "STATE", null)),
         filter.valueSpecs());
   }

   @Test
   void readReportsCorrelatedSubQueryWithWhere() {
      fixture("SUP");
      t.setPreConditionList(conditionOn("STATE", subQuery("SUP", "STATE", "CODE", "STATE")));

      assertEquals(
         List.of(new WorksheetModel.ValueSpecModel(
            0, "subquery", null, null, null, "SUP", "STATE",
            new WorksheetModel.SubQueryWhereModel("CODE", "STATE"))),
         read().preConditions().get(0).valueSpecs());
   }

   @Test
   void readReportsSubQueryInPostConditionAtItsValueIndex() {
      fixture("SUP");
      t.setPostConditionList(conditionOn("STATE", "x", subQuery("SUP", "STATE", null, null)));

      WorksheetModel.FilterModel filter = read().postConditions().get(0);

      assertEquals(List.of("x", "Query[SUP]"), filter.values());
      assertEquals(1, filter.valueSpecs().get(0).index());
      assertEquals("subquery", filter.valueSpecs().get(0).valueType());
   }

   @Test
   void literalLookingLikeSubQueryTextGetsNoValueSpec() {
      fixture("SUP");
      t.setPreConditionList(conditionOn("STATE", "Query[SUP]"));

      assertNull(read().preConditions().get(0).valueSpecs());
   }

   @Test
   void subqueryValueSpecSerializesItsIdentity() throws Exception {
      String json = new ObjectMapper().writeValueAsString(
         new WorksheetModel.ValueSpecModel(
            0, "subquery", null, null, null, "SUP", "STATE",
            new WorksheetModel.SubQueryWhereModel("CODE", "STATE")));

      assertEquals("{\"index\":0,\"valueType\":\"subquery\",\"subQueryName\":\"SUP\"," +
                   "\"inSubQueryColumn\":\"STATE\",\"where\":{\"subQueryColumn\":\"CODE\"," +
                   "\"currentTableColumn\":\"STATE\"}}", json);
   }

   // ----- write side: the Query[name] text is refused as a literal

   @Test
   void setConditionsRefusesSubQueryTextOnStringIntegerAndDateColumns() {
      fixture("SUP");

      for(String field : List.of("STATE", "N", "D")) {
         assertSubQueryRefusal(
            refusal(() -> set(field, List.of("Query[SUP]"))), field, "SUP", true);
      }

      assertTrue(t.getPreConditionList() == null || t.getPreConditionList().isEmpty());
   }

   @Test
   void setConditionsRefusesSubQueryTextAmongOtherValues() {
      fixture("SUP");

      assertSubQueryRefusal(
         refusal(() -> set("STATE", List.of("CA", "Query[SUP]"))), "STATE", "SUP", true);
   }

   @Test
   void addFilterAndReplaceFilterRefuseSubQueryText() {
      fixture("SUP");

      for(String field : List.of("STATE", "N", "D")) {
         assertSubQueryRefusal(
            refusal(() -> WorksheetMutationSupport.addFilter(t, field, "=", "Query[SUP]")),
            field, "SUP", false);
         assertSubQueryRefusal(
            refusal(() -> WorksheetMutationSupport.replaceFilter(t, field, "=", "Query[SUP]")),
            field, "SUP", false);
      }
   }

   @Test
   void tableNameContainingBracketsIsResolved() {
      fixture("S[1]");

      assertSubQueryRefusal(
         refusal(() -> set("STATE", List.of("Query[S[1]]"))), "STATE", "S[1]", true);
   }

   @Test
   void literalWithoutMatchingTableStaysAccepted() {
      fixture("SUP");

      set("STATE", List.of("Query[OTHER]"));

      Condition first = (Condition) ((ConditionItem)
         ((ConditionList) t.getPreConditionList()).getItem(0)).getXCondition();
      assertEquals("Query[OTHER]", first.getValue(0));
   }

   @Test
   void tableNameMustBeTheWholeInnerText() {
      fixture("SUP");

      set("STATE", List.of("Query[SUP] extra"));
      set("STATE", List.of("my Query[SUP]"));
   }

   // ----- write side: a resubmitted subquery valueSpec

   // Bug #79224 upgraded "subquery" from a read-only marker to a real, writable valueSpec (see
   // WorksheetEditServiceMutatorsTest's own "Bug #79224" section for the full write-side coverage
   // -- construction, correlation, alias resolution, and the sv.update(ws)-dependent evaluation
   // test in WorksheetMutationSupportSubqueryEvaluationTest). The two tests below, originally
   // written for the pre-#79224 read-only design, are kept here (not deleted) because they still
   // exercise a real, still-correct boundary: an INCOMPLETE "subquery" valueSpec (missing the
   // table/column identity #79224 actually requires) is refused, just with a different, more
   // specific message than the old blanket "read-only marker" refusal.

   @Test
   void incompleteSubqueryValueSpecMissingSubQueryNameIsRefused() {
      fixture("SUP");
      ConditionValueSpec spec = new ConditionValueSpec("subquery", null, null, null, 0);

      for(String field : List.of("STATE", "N")) {
         String message = refusal(() -> set(field, List.of("Query[SUP]"), spec));
         assertTrue(message.contains("'subquery' valueSpec needs a non-blank subQueryName"),
                    message);
      }
   }

   @Test
   void conditionValueSpecDeserializationCapturesSubQueryIdentityFields() throws Exception {
      ConditionValueSpec spec = new ObjectMapper().readValue(
         "{\"index\":0,\"valueType\":\"subquery\",\"subQueryName\":\"SUP\"," +
         "\"inSubQueryColumn\":\"STATE\",\"where\":{\"subQueryColumn\":\"CODE\"}}",
         ConditionValueSpec.class);

      assertEquals("subquery", spec.valueType());
      assertEquals(0, spec.index());
      // Bug #79224: these identity fields used to be ignored (@JsonIgnoreProperties) because the
      // write side only ever refused "subquery" outright; now that it actually resolves and
      // constructs a SubQueryValue from them, they must deserialize for real.
      assertEquals("SUP", spec.subQueryName());
      assertEquals("STATE", spec.inSubQueryColumn());
      assertEquals("CODE", spec.where().subQueryColumn());
      assertNull(spec.where().currentTableColumn());
   }
}
