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
package inetsoft.web.composer.ws.dialog;

import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage for the subquery condition dialog column selection (Bug #75323):
 * candidate tables must expose their public (output) columns, while the current table
 * keeps its full (private) selection.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class AssemblyConditionDialogServiceTest {
   private static ColumnRef col(String name) {
      ColumnRef ref = new ColumnRef(new AttributeRef(name));
      ref.setVisible(true);
      return ref;
   }

   private static Set<String> names(ColumnSelection sel) {
      Set<String> names = new HashSet<>();

      for(int i = 0; i < sel.getAttributeCount(); i++) {
         names.add(sel.getAttribute(i).getName());
      }

      return names;
   }

   @Test
   void currentTableUsesPrivateCandidatesUsePublic() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = new EmbeddedTableAssembly(ws, "t");
      ColumnSelection priv = new ColumnSelection();
      priv.addAttribute(col("a"));
      priv.addAttribute(col("b"));
      t.setColumnSelection(priv, false);

      // The current table keeps the private selection; candidates use the public one.
      // Guards against reverting to the no-arg getColumnSelection() (== private) for candidates.
      assertSame(t.getColumnSelection(false),
                 AssemblyConditionDialogService.getSubqueryColumns(t, true),
                 "current table must use the private (full) column selection");
      assertSame(t.getColumnSelection(true),
                 AssemblyConditionDialogService.getSubqueryColumns(t, false),
                 "candidate tables must use the public (output) column selection");
   }

   @Test
   void aggregatedCandidateListsOutputColumnsNotRawDetail() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly agg = new EmbeddedTableAssembly(ws, "agg");
      ColumnRef state = col("state");
      ColumnRef id = col("id");
      ColumnRef sales = col("sales");

      ColumnSelection priv = new ColumnSelection();
      priv.addAttribute(state);
      priv.addAttribute(id);
      priv.addAttribute(sales);

      AggregateInfo aggInfo = new AggregateInfo();
      aggInfo.addGroup(new GroupRef(state));
      aggInfo.addAggregate(new AggregateRef(sales, AggregateFormula.SUM));
      agg.setAggregateInfo(aggInfo);
      agg.setColumnSelection(priv, false);   // regenerates the public (output) selection

      // Candidate → public output columns: the grouped/aggregated columns, not raw "id".
      Set<String> candidate = names(AssemblyConditionDialogService.getSubqueryColumns(agg, false));
      assertTrue(candidate.contains("state"), "grouped column should be an output column");
      assertTrue(candidate.contains("sales"), "aggregated column should be an output column");
      assertFalse(candidate.contains("id"),
                  "non-aggregated detail column must not appear in subquery output columns");

      // Current table → full private selection still includes the raw detail column.
      Set<String> current = names(AssemblyConditionDialogService.getSubqueryColumns(agg, true));
      assertTrue(current.contains("id"), "current table must keep its full detail columns");
   }

   @Test
   void crosstabCandidateFallsBackToVisibleColumns() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly cross = new EmbeddedTableAssembly(ws, "cross");
      ColumnSelection priv = new ColumnSelection();
      priv.addAttribute(col("state"));
      priv.addAttribute(col("city"));
      priv.addAttribute(col("sales"));

      AggregateInfo cInfo = new AggregateInfo();
      cInfo.setCrosstab(true);
      cInfo.addGroup(new GroupRef(col("state")));
      cInfo.addGroup(new GroupRef(col("city")));   // >1 group required by isCrosstab()
      cInfo.addAggregate(new AggregateRef(col("sales"), AggregateFormula.SUM));
      cross.setAggregateInfo(cInfo);
      cross.setColumnSelection(priv, false);

      assertTrue(cross.isCrosstab(), "table should be a crosstab");

      // A crosstab has no eagerly-built public selection, but getPublicColumnSelection()
      // falls back to the visible private columns, so the candidate is not empty.
      ColumnSelection candidate = AssemblyConditionDialogService.getSubqueryColumns(cross, false);
      assertTrue(candidate.getAttributeCount() > 0,
                 "crosstab candidate must fall back to visible columns, not an empty list");
   }
}
