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

import inetsoft.uql.VariableTable;
import inetsoft.uql.XQuery;
import inetsoft.uql.jdbc.UniformSQL;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82290: every parallel member query of a join or concatenated table gets its own
 * variable table, and only the non-hint variables it changes (for example a parameter set by
 * a VPM trigger script) reach the parent's table afterwards.
 */
@Tag("core")
class MemberQueryVariablesTest {
   @Test
   void membersDoNotSeeEachOthersHints() throws Exception {
      VariableTable vars = new VariableTable();
      vars.put(XQuery.HINT_MAX_ROWS, "100");
      MemberQueryVariables members = new MemberQueryVariables(vars, 2, true);
      VariableTable t = members.get(0);
      VariableTable c = members.get(1);

      assertNotSame(vars, t);
      assertNotSame(t, c);

      t.put(XQuery.HINT_MAX_ROWS, "5");
      c.remove(XQuery.HINT_MAX_ROWS);

      assertEquals("5", t.get(XQuery.HINT_MAX_ROWS));
      assertNull(c.get(XQuery.HINT_MAX_ROWS));
      assertEquals("100", vars.get(XQuery.HINT_MAX_ROWS));
   }

   @Test
   void writeBackCopiesParametersButNotHints() throws Exception {
      VariableTable base = new VariableTable();
      base.put("region", "west");
      VariableTable vars = new VariableTable();
      vars.setBaseTable(base);
      vars.put("keep", "k");
      vars.put("drop", "d");
      vars.put(XQuery.HINT_MAX_ROWS, "100");
      MemberQueryVariables members = new MemberQueryVariables(vars, 2, true);

      // member 0, like a VPM trigger script: sets parameters, and writes its own hints
      VariableTable m0 = members.get(0);
      m0.put("region", "east");
      m0.setAsIs("region", true);
      m0.put("added", "a");
      m0.remove("drop");
      m0.put(XQuery.HINT_MAX_ROWS, "5");
      m0.put(XQuery.HINT_TIMEOUT, "30");
      m0.put(UniformSQL.HINT_INPUT_MAXROWS, "7");
      m0.put("__HINT_CACHE_SAMPLES__", "7");

      // member 1 changes no parameter, only removes the max rows hint
      members.get(1).remove(XQuery.HINT_MAX_ROWS);

      members.writeBack(0);
      members.writeBack(1);

      assertEquals("east", vars.get("region"));
      assertTrue(vars.isAsIs("region"));
      assertEquals("west", base.get("region"), "the base table is left alone");
      assertEquals("a", vars.get("added"));
      assertEquals("k", vars.get("keep"));
      assertFalse(vars.contains("drop"));
      assertEquals("100", vars.get(XQuery.HINT_MAX_ROWS));
      assertFalse(vars.contains(XQuery.HINT_TIMEOUT));
      assertFalse(vars.contains(UniformSQL.HINT_INPUT_MAXROWS));
      assertFalse(vars.contains("__HINT_CACHE_SAMPLES__"));
   }

   @Test
   void writeBackIgnoresTablesAddedToTheMembersBaseChain() throws Exception {
      VariableTable base = new VariableTable();
      base.put("region", "west");
      VariableTable vars = new VariableTable();
      vars.setBaseTable(base);
      MemberQueryVariables members = new MemberQueryVariables(vars, 1, true);

      // like XHandler.prepareVariableTable during the member's JDBC run: a table with the
      // query's unset variables is appended to the end of the member's base chain
      VariableTable queryVariables = new VariableTable();
      queryVariables.put("q", "x");
      members.get(0).addBaseTable(queryVariables);

      members.writeBack(0);

      VariableTable topLevel = vars.clone();
      topLevel.setBaseTable(null);
      assertFalse(topLevel.contains("q"), "the member's base chain must not reach the caller");
      assertFalse(topLevel.contains("region"));
      assertFalse(vars.contains("q"));
      assertEquals("west", vars.get("region"));
   }

   @Test
   void writeBackRemovesTopLevelEntryThatShadowsTheBase() throws Exception {
      VariableTable base = new VariableTable();
      base.put("region", "west");
      VariableTable vars = new VariableTable();
      vars.setBaseTable(base);
      vars.put("region", "east");
      MemberQueryVariables members = new MemberQueryVariables(vars, 1, true);

      members.get(0).remove("region");
      assertEquals("west", members.get(0).get("region"));

      members.writeBack(0);

      assertEquals("west", vars.get("region"), "the parent sees what the member saw");
      VariableTable topLevel = vars.clone();
      topLevel.setBaseTable(null);
      assertFalse(topLevel.contains("region"), "removed, not shadowed by the base value");
   }

   @Test
   void serialMembersShareTheParentTable() throws Exception {
      VariableTable vars = new VariableTable();
      MemberQueryVariables members = new MemberQueryVariables(vars, 2, false);

      assertSame(vars, members.get(0));
      assertSame(vars, members.get(1));

      members.get(0).put("p", "1");
      members.writeBack(0);
      assertEquals("1", vars.get("p"));
   }

   @Test
   void nullVariables() throws Exception {
      MemberQueryVariables members = new MemberQueryVariables(null, 2, true);

      assertNull(members.get(0));
      members.writeBack(0);
   }
}
