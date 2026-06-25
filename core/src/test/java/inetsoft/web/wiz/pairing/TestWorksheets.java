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
package inetsoft.web.wiz.pairing;

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;

/**
 * Factory helpers for building test worksheet/table structures.
 *
 * <p>All factory methods operate on plain Java domain objects and require no
 * Spring context or live datasource connection.</p>
 */
public final class TestWorksheets {

   private TestWorksheets() {}

   /**
    * Creates an {@link EmbeddedTableAssembly} named {@code name} whose private
    * {@link ColumnSelection} holds a {@link ColumnRef} for each of the given column names.
    *
    * <p>{@link EmbeddedTableAssembly} is chosen because it holds in-memory data and
    * can be constructed with just a {@link Worksheet} and a name — no live datasource
    * or query binding is required.</p>
    *
    * <p>The assembly is <em>not</em> added to the worksheet; the caller decides whether
    * to do that via {@code ws.addAssembly(t)}.</p>
    *
    * @param ws   the worksheet the table logically belongs to (may be empty)
    * @param name the assembly name
    * @param cols column names to include in the private column selection
    * @return the configured table assembly
    */
   public static EmbeddedTableAssembly tableWithColumns(Worksheet ws, String name,
                                                        String... cols)
   {
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, name);

      ColumnSelection cs = new ColumnSelection();
      for(String col : cols) {
         cs.addAttribute(new ColumnRef(new AttributeRef(null, col)));
      }
      table.setColumnSelection(cs, false);

      return table;
   }

   /**
    * Configures an existing {@link TableAssembly} with a group-by / sum aggregate and
    * an ascending sort on the group column.
    *
    * <p>Sets an {@link AggregateInfo} with {@code groupCol} as the group dimension and
    * {@code sumCol} aggregated via {@link AggregateFormula#SUM}, then sets a
    * {@link SortInfo} sorting by {@code groupCol} ascending.</p>
    *
    * @param t        the assembly to configure (mutated in-place)
    * @param groupCol name of the column to group by (must already exist in the selection)
    * @param sumCol   name of the column to sum (must already exist in the selection)
    * @return the same assembly (for chaining)
    */
   public static TableAssembly withGroupSumAndSort(TableAssembly t,
                                                   String groupCol, String sumCol)
   {
      ColumnRef groupRef = new ColumnRef(new AttributeRef(null, groupCol));
      ColumnRef sumRef   = new ColumnRef(new AttributeRef(null, sumCol));

      AggregateInfo ainfo = new AggregateInfo();
      ainfo.addGroup(new GroupRef(groupRef));
      ainfo.addAggregate(new AggregateRef(sumRef, AggregateFormula.SUM));
      t.setAggregateInfo(ainfo);

      SortInfo sinfo = new SortInfo();
      SortRef sortRef = new SortRef(groupRef);
      sortRef.setOrder(XConstants.SORT_ASC);
      sinfo.addSort(sortRef);
      t.setSortInfo(sinfo);

      return t;
   }
}
