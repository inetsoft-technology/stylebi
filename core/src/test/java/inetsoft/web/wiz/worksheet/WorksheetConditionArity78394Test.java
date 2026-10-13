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

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.web.wiz.pairing.PairingException;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import inetsoft.web.wiz.worksheet.model.WorksheetModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static inetsoft.web.wiz.worksheet.WorksheetMutationSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78394: worksheet conditions whose value count does not fit the operation, whose declared
 * type switches off the column type's literal checks, or whose named-group literal would be
 * coerced, are refused instead of being stored and evaluated as something else.
 */
@Tag("core")
@WizAgentTestSupport
class WorksheetConditionArity78394Test {
   private static BoundTableAssembly table() {
      Worksheet ws = new Worksheet();
      BoundTableAssembly t = new BoundTableAssembly(ws, "T");
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(column("flag", XSchema.BOOLEAN));
      cs.addAttribute(column("amt", XSchema.INTEGER));
      cs.addAttribute(column("amt2", XSchema.INTEGER));
      cs.addAttribute(column("d", XSchema.DATE));
      cs.addAttribute(column("State", XSchema.STRING));
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "s"))); // no type of its own
      t.setColumnSelection(cs, false);
      ws.addAssembly(t);
      return t;
   }

   private static ColumnRef column(String name, String type) {
      ColumnRef col = new ColumnRef(new AttributeRef(null, name));
      col.setDataType(type);
      return col;
   }

   private static List<ConditionNode> cond(String field, String op, String type,
                                           String... values)
   {
      return List.of(new ConditionNode(
         new ConditionSpec(field, op, Arrays.asList(values), false, type), null, 0));
   }

   private static List<ConditionNode> cond(String field, String op, List<String> values,
                                           ConditionValueSpec... specs)
   {
      return List.of(new ConditionNode(
         new ConditionSpec(field, op, values, false, null, List.of(specs)), null, 0));
   }

   private static void refused(Executable call, String... fragments) {
      Throwable e = assertThrows(Exception.class, call);
      assertTrue(e instanceof IllegalArgumentException || e instanceof PairingException,
                 e.toString());

      for(String fragment : fragments) {
         assertTrue(e.getMessage().contains(fragment), e.getMessage() + " / " + fragment);
      }
   }

   private static XCondition first(ConditionListWrapper list) {
      return ((ConditionItem) list.getConditionList().getItem(0)).getXCondition();
   }

   // item 1 -- DATE_IN takes exactly one range name

   @Test
   void dateInWithTwoValuesRefusedOnEveryFilterTool() {
      BoundTableAssembly t = table();
      refused(() -> setConditions(t, cond("d", "DATE_IN", null, "Last year", "This year"), false),
              "\"d\"", "DATE_IN", "got 2");
      refused(() -> setConditions(t, cond("d", "DATE_IN", null, "Last year", "No such"), false),
              "DATE_IN", "got 2");
      refused(() -> setMVConditions(t, cond("d", "DATE_IN", null, "Last year", "This year"),
                                    null, null, null, null), "DATE_IN", "got 2");
      refused(() -> addFilter(t, "d", "DATE_IN", "Last year", "This year"), "DATE_IN", "got 2");
      assertTrue(t.getPreConditionList() == null || t.getPreConditionList().isEmpty());

      addFilter(t, "d", "DATE_IN", "Last year");
      refused(() -> replaceFilter(t, "d", "DATE_IN", "This year", "No such"), "got 2");
      assertEquals("Last year", ((DateCondition) first(t.getPreConditionList())).getName());
   }

   @Test
   void dateInWithValueSpecsRefused() {
      refused(() -> setConditions(table(), cond("d", "DATE_IN", List.of("Last year"),
         new ConditionValueSpec("expression", null, "1", "sql")), false), "valueSpecs");
   }

   @Test
   void dateInWithOneValueStillAccepted() {
      BoundTableAssembly t = table();
      setConditions(t, cond("d", "DATE_IN", null, "Last year"), false);
      assertEquals("Last year", ((DateCondition) first(t.getPreConditionList())).getName());
   }

   // item 2 -- add_filter/edit_condition value counts

   @Test
   void addFilterAndEditConditionCheckTheValueCount() {
      BoundTableAssembly t = table();
      refused(() -> addFilter(t, "amt", "BETWEEN", "10"), "BETWEEN", "exactly two", "got 1");
      refused(() -> addFilter(t, "amt", "BETWEEN", "5", "10", "20"), "got 3");
      refused(() -> addFilter(t, "State", "="), "exactly one value", "got 0");
      refused(() -> addFilter(t, "State", "NULL", "NJ"), "takes no values");
      refused(() -> addFilter(t, "State", "ONE_OF"), "at least one value");
      refused(() -> replaceFilter(t, "amt", "BETWEEN", "10"), "got 1");
      assertTrue(t.getPreConditionList() == null || t.getPreConditionList().isEmpty());

      addFilter(t, "amt", "BETWEEN", "5", "10");
      addFilter(t, "State", "NOT_NULL");
      addFilter(t, "State", "ONE_OF", "NJ", "NY");
      assertEquals(5, t.getPreConditionList().getConditionList().getConditionSize()); // 3 conditions, 2 ANDs
   }

   @Test
   void setConditionsCountsAppendedValueSpecs() {
      BoundTableAssembly t = table();
      ConditionValueSpec field = new ConditionValueSpec("field", "amt2", null, null, null);
      // a field comparison with no literal: one value, from valueSpecs
      setConditions(t, cond("amt", "=", List.of(), field), false);
      // literal low + field high
      setConditions(t, cond("amt", "BETWEEN", List.of("1"), field), false);
      assertEquals(2, ((Condition) first(t.getPreConditionList())).getValueCount());

      refused(() -> setConditions(t, cond("amt", "BETWEEN", List.of("1", "2"), field), false),
              "got 3");
      refused(() -> setConditions(t, cond("amt", "=", List.of("1"), field), false), "got 2");
      refused(() -> setConditions(t, cond("State", "NULL", null, "NJ"), false), "no values");
   }

   // item 3 -- a declared type cannot switch off the column type's checks

   @Test
   void declaredTypeThatContradictsTheColumnRefused() {
      BoundTableAssembly t = table();
      refused(() -> setConditions(t, cond("flag", "=", "string", "yes"), false),
              "\"flag\"", "string", "boolean");
      refused(() -> setConditions(t, cond("flag", "=", "bool", "yes"), false),
              "\"bool\"", "not a data type");
      refused(() -> setConditions(t, cond("amt", "=", "string", "abc"), false), "integer");
      refused(() -> setConditions(t, cond("d", "=", "string", "notadate"), false), "date");
      refused(() -> setMVConditions(t, cond("flag", "=", "string", "yes"), null, null, null,
                                    null), "boolean");
      // a case variant is the column's own type, so its checks run
      refused(() -> setConditions(t, cond("flag", "=", "Boolean", "yes"), false), "yes");
      refused(() -> setConditions(t, cond("flag", "=", "BOOLEAN", "yes"), false), "yes");

      setConditions(t, cond("flag", "=", "BOOLEAN", "true"), false);
      assertEquals(XSchema.BOOLEAN, ((Condition) first(t.getPreConditionList())).getType());
   }

   @Test
   void declaredTypeStillAppliesToAColumnWithNoTypeOfItsOwn() {
      BoundTableAssembly t = table();
      setConditions(t, cond("s", ">", "Integer", "10"), false);
      assertEquals(XSchema.INTEGER, ((Condition) first(t.getPreConditionList())).getType());
      refused(() -> setConditions(t, cond("s", ">", "integer", "abc"), false), "abc");
   }

   // item 4 -- an omitted or blank operation is equality, with equality's count

   @Test
   void omittedOrBlankOperationWithTwoValuesRefused() {
      BoundTableAssembly t = table();
      refused(() -> setConditions(t, cond("State", null, null, "NJ", "NY"), false),
              "\"State\"", "default", "got 2", "ONE_OF");
      refused(() -> setConditions(t, cond("State", "", null, "NJ", "NY"), false), "got 2");
      refused(() -> setMVConditions(t, cond("State", null, null, "NJ", "NY"), null, null, null,
                                    null), "got 2");
      refused(() -> setConditions(t, cond("State", "!=", null, "NJ", "NY"), false),
              "NOT_ONE_OF");
      setConditions(t, cond("State", null, null, "NJ"), false);
      assertNotNull(t.getPreConditionList());
   }

   // item 5 -- named groups

   private static ConditionList group(String type, String op, String... values)
      throws PairingException
   {
      return buildGroupConditionList(type, new ColumnRef(new AttributeRef(null, "c")),
         new GroupMapping("G", List.of(values), op), new Worksheet());
   }

   @Test
   void namedGroupLiteralsAreChecked() throws Exception {
      refused(() -> group(XSchema.BOOLEAN, null, "yes"), "\"G\"", "yes");
      refused(() -> group(XSchema.BOOLEAN, "ONE_OF", "true", "yes"), "yes");
      refused(() -> group(XSchema.INTEGER, null, "abc"), "abc");
      refused(() -> group(XSchema.INTEGER, "BETWEEN", "1", "x"), "x");

      Condition c = (Condition) group(XSchema.BOOLEAN, null, " TRUE ").getXCondition(0);
      assertEquals(Boolean.TRUE, c.getValue(0));
      assertEquals(7, ((Condition) group(XSchema.INTEGER, null, " 7 ").getXCondition(0))
         .getValue(0));
      // variable references are left as they were
      assertDoesNotThrow(() -> group(XSchema.BOOLEAN, null, "$(v)"));
   }

   @Test
   void namedGroupCounts() throws Exception {
      refused(() -> group(XSchema.DATE, "DATE_IN", "Last year", "No such"),
              "\"G\"", "DATE_IN", "got 2");
      refused(() -> group(XSchema.DATE, "DATE_IN"), "got 0");

      // groups OR their values, so several values on a single-value operation stay legal
      ConditionList cl = group(XSchema.STRING, null, "NJ", "NY", "NV");
      assertEquals(5, cl.getConditionSize()); // 3 conditions, 2 ORs
      assertEquals(5, group(XSchema.STRING, "STARTING_WITH", "N", "C", "W").getConditionSize());
   }

   @Test
   void dateInGroupReadsBackItsRangeAndRoundTrips() throws Exception {
      Worksheet ws = new Worksheet();
      ColumnRef col = column("d", XSchema.DATE);
      NamedGroupInfo ngi = new NamedGroupInfo();
      ngi.setOthers(XConstants.LEAVE_OTHERS);
      ngi.setGroupCondition("LY", buildGroupConditionList(
         XSchema.DATE, col, new GroupMapping("LY", List.of("Last year"), "DATE_IN"), ws));
      DefaultNamedGroupAssembly nga = new DefaultNamedGroupAssembly(ws, "DG");
      nga.setNamedGroupInfo(ngi);
      nga.setAttachedType(AttachedAssembly.DATA_TYPE_ATTACHED);
      nga.setAttachedDataType(XSchema.DATE);
      ws.addAssembly(nga);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      WorksheetModel m = new WorksheetReadService().read(rws);
      WorksheetModel.GroupMappingModel gm = m.namedGroups().stream()
         .filter(g -> "DG".equals(g.name())).findFirst().orElseThrow().groupMappings().get(0);
      assertEquals(List.of("Last year"), gm.values());
      assertEquals("DATE_IN", gm.operation());

      // the readback is accepted by edit_named_group's builder
      ConditionList rebuilt = buildGroupConditionList(XSchema.DATE, col,
         new GroupMapping(gm.groupName(), new ArrayList<>(gm.values()), gm.operation()), ws);
      assertEquals("Last year", ((DateCondition) rebuilt.getXCondition(0)).getName());
   }
}
