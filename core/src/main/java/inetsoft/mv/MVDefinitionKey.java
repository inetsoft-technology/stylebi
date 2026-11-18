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

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.ConditionListWrapper;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.ConditionUtil;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.asset.internal.TabularTableAssemblyInfo;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.viewsheet.CalculateRef;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.jdbc.JDBCQuery;
import inetsoft.uql.tabular.*;
import inetsoft.uql.util.XEmbeddedTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * A fingerprint of the data-defining definition of a worksheet table (Bug #81366). An MV holds
 * the data of the table as it was defined when the MV was built, so the MV is stale once the
 * definition changes in a way that changes the rows or the values. The fingerprint is taken
 * from the pristine worksheet table when the MV is analyzed and compared, taken the same way,
 * with the current table when the MV is checked.
 *
 * <p>It covers the table and, recursively, the tables it is composed of: the source, the
 * conditions (pre, post, ranking, MV update and delete), the joins and concatenation operators,
 * the group and aggregate definition, distinct, max rows, the columns (names, types and
 * expressions), embedded data. It does not cover what doesn't change the data: the position and
 * description of the assembly, table and column visibility, runtime conditions (those of a
 * viewsheet's selections), the sort (unless rows are limited), the names of the tables, and
 * what is viewsheet specific (calculate fields, the aggregates and selections of the
 * viewsheet). It doesn't follow a mirror of another worksheet to the definition of that
 * worksheet.
 */
public final class MVDefinitionKey {
   private MVDefinitionKey() {
   }

   /**
    * Create the fingerprint of a table.
    *
    * @param table the table, as the author defined it. The mirror table a viewsheet puts over
    *              the worksheet table (the worksheet table is renamed T_O under a mirror T) is
    *              looked through.
    *
    * @return the fingerprint, or null if it can't be created.
    */
   public static String create(TableAssembly table) {
      if(table == null) {
         return null;
      }

      try {
         StringWriter buf = new StringWriter();
         PrintWriter writer = new PrintWriter(buf);
         print(unwrap(table), writer, 0);
         writer.flush();

         byte[] digest = MessageDigest.getInstance("SHA-256")
            .digest(buf.toString().getBytes(StandardCharsets.UTF_8));
         StringBuilder hex = new StringBuilder();

         for(byte b : digest) {
            hex.append(String.format("%02x", b));
         }

         return hex.toString();
      }
      catch(Exception ex) {
         LOG.debug("Failed to create the definition key of table {}", table.getName(), ex);
         return null;
      }
   }

   /**
    * Get the worksheet table of a table the viewsheet put a mirror over.
    */
   static TableAssembly unwrap(TableAssembly table) {
      if(table instanceof MirrorTableAssembly) {
         MirrorTableAssembly mirror = (MirrorTableAssembly) table;
         String inner = mirror.getAssemblyName();

         if(inner != null && inner.equals(table.getName() + Viewsheet.OUTER_TABLE_SUFFIX)) {
            TableAssembly itable = mirror.getTableAssembly();

            if(itable != null) {
               return itable;
            }
         }
      }

      return table;
   }

   private static void print(TableAssembly table, PrintWriter writer, int depth)
      throws Exception
   {
      if(depth > MAX_DEPTH) {
         throw new IllegalStateException("The table is nested too deep");
      }

      writer.print(table.getClass().getSimpleName());
      writer.print("[");

      if(table instanceof BoundTableAssembly) {
         printBound((BoundTableAssembly) table, writer);
      }

      printConditions("PRE", table.getPreConditionList(), writer);
      printConditions("POST", table.getPostConditionList(), writer);
      printConditions("RANKING", table.getRankingConditionList(), writer);
      printConditions("MVUPRE", table.getMVUpdatePreConditionList(), writer);
      printConditions("MVUPOST", table.getMVUpdatePostConditionList(), writer);
      printConditions("MVDPRE", table.getMVDeletePreConditionList(), writer);
      printConditions("MVDPOST", table.getMVDeletePostConditionList(), writer);

      AggregateInfo ainfo = table.getAggregateInfo();

      if(ainfo != null && !ainfo.isEmpty()) {
         writer.print("AGG[");
         ainfo.printKey(writer);
         writer.print("]");
      }

      writer.print("MAX" + table.getMaxRows());
      writer.print(table.isDistinct() ? "DISTINCT" : "ALL");

      // the sort decides which rows are kept only if the rows are limited
      if(table.getMaxRows() > 0 && table.getSortInfo() != null) {
         writer.print("SORT[");
         table.getSortInfo().printKey(writer);
         writer.print("]");
      }

      printColumns(table.getColumnSelection(false), writer);

      if(table instanceof EmbeddedTableAssembly) {
         XEmbeddedTable data = ((EmbeddedTableAssembly) table).getEmbeddedData();

         if(data != null) {
            writer.print("DATA[");
            data.printKey(writer);
            writer.print("]");
         }
      }

      if(table instanceof UnpivotTableAssembly) {
         writer.print("UNPIVOT" + ((UnpivotTableAssembly) table).getHeaderColumns());
      }

      if(table instanceof CompositeTableAssembly) {
         CompositeTableAssembly ctable = (CompositeTableAssembly) table;
         String[] tnames = ctable.getTableNames();

         for(int i = 0; i < tnames.length; i++) {
            for(int j = i + 1; j < tnames.length; j++) {
               TableAssemblyOperator top = ctable.getOperator(tnames[i], tnames[j]);

               if(top != null) {
                  writer.print("OP[");
                  top.printKey(writer);
                  writer.print("]");
               }
            }
         }
      }

      if(table instanceof ComposedTableAssembly) {
         TableAssembly[] subs = ((ComposedTableAssembly) table).getTableAssemblies(false);

         if(subs != null) {
            for(TableAssembly sub : subs) {
               writer.print("SUB");
               print(sub, writer, depth + 1);
            }
         }
      }

      writer.print("]");
   }

   private static void printBound(BoundTableAssembly table, PrintWriter writer)
      throws Exception
   {
      SourceInfo source = table.getSourceInfo();

      if(source != null) {
         writer.print("SRC[" + source.getType() + "," + source.getPrefix() + "," +
                      source.getSource() + "]");
      }

      int count = table.getConditionAssemblyCount();

      for(int i = 0; i < count; i++) {
         printConditions("CA", table.getConditionAssembly(i), writer);
      }

      if(table instanceof SQLBoundTableAssembly) {
         JDBCQuery query =
            ((SQLBoundTableAssemblyInfo) table.getInfo()).getQuery();

         if(query != null && query.getSQLDefinition() != null) {
            writer.print("SQL[" + query.getSQLDefinition().getSQLString() + "]");
         }
      }

      if(table instanceof TabularTableAssembly) {
         TabularQuery query = ((TabularTableAssemblyInfo) table.getInfo()).getQuery();

         if(query != null) {
            writer.print("TAB[");

            for(PropertyMeta prop : TabularUtil.findProperties(query.getClass())) {
               writer.print("," + prop.getName() + "=" + prop.getValue(query));
            }

            writer.print("]");
         }
      }
   }

   private static void printConditions(String label, ConditionListWrapper conds,
                                       PrintWriter writer) throws Exception
   {
      if(conds == null || conds.getConditionList() == null ||
         conds.getConditionList().getSize() == 0)
      {
         return;
      }

      writer.print(label + "[");
      ConditionUtil.printConditionsKey(conds, writer);
      writer.print("]");
   }

   /**
    * Print the expression columns and the aliased columns, in an order that doesn't depend on
    * the column order or visibility. A calculate field is of the viewsheet, not of the table.
    */
   private static void printColumns(ColumnSelection cols, PrintWriter writer) {
      if(cols == null) {
         return;
      }

      List<String> keys = new ArrayList<>();

      for(int i = 0; i < cols.getAttributeCount(); i++) {
         DataRef ref = cols.getAttribute(i);

         if(isCalculate(ref)) {
            continue;
         }

         StringBuilder key = new StringBuilder();

         if(ref instanceof ColumnRef) {
            ColumnRef col = (ColumnRef) ref;
            DataRef base = col.getDataRef();

            if(base instanceof ExpressionRef) {
               key.append(ref.getName()).append(':').append(ref.getDataType());
               key.append(':').append(col.isSQL() ? "SQL" : "JS").append(':');

               try {
                  key.append(((ExpressionRef) base).getExpression());
               }
               catch(UnsupportedOperationException ex) {
                  key.append(((ExpressionRef) base).getScriptExpression());
               }
            }
            else if(col.getAlias() != null && !col.getAlias().isEmpty() && base != null) {
               // an alias hides which column the data comes from
               key.append(col.getAlias()).append("=>").append(base.getEntity()).append('.')
                  .append(base.getAttribute());
            }
            else {
               // a plain column is defined by the table's source, conditions and aggregates;
               // its type and presence are re-derived when the worksheet is refreshed, so they
               // would make a freshly built MV read stale
               continue;
            }
         }
         else {
            continue;
         }

         keys.add(key.toString());
      }

      Collections.sort(keys);
      writer.print("COLS" + keys);
   }

   private static boolean isCalculate(DataRef ref) {
      if(ref instanceof CalculateRef) {
         return true;
      }

      return ref instanceof ColumnRef && ((ColumnRef) ref).getDataRef() instanceof CalculateRef;
   }

   private static final int MAX_DEPTH = 100;
   private static final Logger LOG = LoggerFactory.getLogger(MVDefinitionKey.class);
}
