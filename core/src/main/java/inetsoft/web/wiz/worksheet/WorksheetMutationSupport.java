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
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;

import java.util.List;

/**
 * Static helpers that implement the low-level structural mutations used by
 * {@link WorksheetEditService.Editor}.
 *
 * <p>Each method operates directly on the supplied {@link TableAssembly} object;
 * callers are responsible for broadcasting any required UI refresh after the
 * mutation completes.</p>
 */
public final class WorksheetMutationSupport {

   private WorksheetMutationSupport() {}

   // =========================================================================
   // AggregateSpec record
   // =========================================================================

   /**
    * Describes a single aggregate measure to apply via
    * {@link #applyAggregateInfo(TableAssembly, List, List)}.
    *
    * @param field   the source column name
    * @param formula a formula name recognised by {@link AggregateFormula#getFormula}
    *                (e.g. {@code "SUM"}, {@code "COUNT"}, {@code "AVG"})
    * @param alias   optional output alias; may be {@code null}
    */
   public record AggregateSpec(String field, String formula, String alias) {}

   // =========================================================================
   // Filter helpers
   // =========================================================================

   /**
    * Builds a simple equality pre-condition and appends it (AND-joined) to the
    * table's existing pre-condition list.
    *
    * <p>Only a single {@code operation} string is currently recognised:
    * <ul>
    *   <li>{@code "="} or {@code "EQUAL_TO"} — equality (default)</li>
    *   <li>{@code "!="} or {@code "NOT_EQUAL_TO"} — not-equal</li>
    *   <li>{@code "<"} or {@code "LESS_THAN"} — less-than</li>
    *   <li>{@code ">"} or {@code "GREATER_THAN"} — greater-than</li>
    * </ul>
    * Unrecognised strings fall back to equality.</p>
    *
    * @param t         the table assembly to mutate
    * @param field     the column name to filter on
    * @param operation the comparison operator (see above)
    * @param values    one or more literal string values
    */
   public static void addFilter(TableAssembly t, String field,
                                String operation, String... values)
   {
      boolean negate = isNegatedOperation(operation);
      int op = parseOperation(operation);
      AttributeRef ref = new AttributeRef(null, field);
      Condition c = new Condition(XSchema.STRING);
      c.setOperation(op);

      if(negate) {
         c.setNegated(true);
      }

      for(String v : values) {
         c.addValue(v);
      }

      ConditionItem item = new ConditionItem(ref, c, 0);
      ConditionListWrapper existing = t.getPreConditionList();

      if(existing != null && !existing.isEmpty()) {
         ConditionList cl = existing.getConditionList();
         cl.append(new JunctionOperator(JunctionOperator.AND, 0));
         cl.append(item);
         t.setPreConditionList(cl);
      }
      else {
         ConditionList cl = new ConditionList();
         cl.append(item);
         t.setPreConditionList(cl);
      }
   }

   /**
    * Removes every condition whose attribute name equals {@code field} from
    * the table's pre-condition list (including any orphaned junction operators
    * left behind).
    *
    * <p>This is a best-effort purge: it rebuilds the list by collecting the
    * remaining conditions and re-joining them with AND.</p>
    *
    * @param t     the table assembly to mutate
    * @param field the column name whose conditions should be removed
    */
   public static void removeFilter(TableAssembly t, String field) {
      ConditionListWrapper existing = t.getPreConditionList();

      if(existing == null || existing.isEmpty()) {
         return;
      }

      ConditionList src = existing.getConditionList();
      ConditionList result = new ConditionList();
      boolean first = true;

      for(int i = 0; i < src.getSize(); i++) {
         HierarchyItem hi = src.getItem(i);

         if(hi instanceof ConditionItem ci) {
            DataRef attr = ci.getAttribute();

            if(field.equals(attr.getName()) || field.equals(attr.getAttribute())) {
               // skip this condition
               continue;
            }

            if(!first) {
               result.append(new JunctionOperator(JunctionOperator.AND, 0));
            }

            result.append(ci);
            first = false;
         }
         // JunctionOperators are rebuilt above — skip originals
      }

      t.setPreConditionList(result.isEmpty() ? null : result);
   }

   // =========================================================================
   // Aggregate helpers
   // =========================================================================

   /**
    * Builds and sets a new {@link AggregateInfo} on the table from the supplied
    * group and aggregate specs.
    *
    * <p>The column selection is not modified; columns referenced here must already
    * exist in the private column selection.</p>
    *
    * @param t          the table assembly to mutate
    * @param groups     column names to group by
    * @param aggregates aggregate measures to apply
    */
   public static void applyAggregateInfo(TableAssembly t, List<String> groups,
                                         List<AggregateSpec> aggregates)
   {
      AggregateInfo ainfo = new AggregateInfo();

      for(String group : groups) {
         GroupRef gr = new GroupRef(new ColumnRef(new AttributeRef(null, group)));
         ainfo.addGroup(gr);
      }

      for(AggregateSpec spec : aggregates) {
         AggregateFormula formula = AggregateFormula.getFormula(spec.formula());

         if(formula == null) {
            formula = AggregateFormula.SUM;
         }

         ColumnRef colRef = new ColumnRef(new AttributeRef(null, spec.field()));
         AggregateRef ar = new AggregateRef(colRef, formula);

         if(spec.alias() != null) {
            colRef.setAlias(spec.alias());
         }

         ainfo.addAggregate(ar);
      }

      t.setAggregateInfo(ainfo);
   }

   // =========================================================================
   // Expression column helpers
   // =========================================================================

   /**
    * Adds an expression column to the table's public column selection.
    *
    * @param t          the table assembly to mutate
    * @param name       the column name (used as the {@link ExpressionRef} name)
    * @param expression the expression body
    * @param type       the data type string (e.g. {@code "string"}), or {@code null}
    * @param sql        {@code true} if the expression is SQL rather than script
    */
   public static void addExpressionColumn(TableAssembly t, String name,
                                          String expression, String type, boolean sql)
   {
      ExpressionRef expr = new ExpressionRef(null, name);
      expr.setExpression(expression != null ? expression : "");
      ColumnRef colRef = new ColumnRef(expr);
      colRef.setSQL(sql);

      if(type != null) {
         colRef.setDataType(type);
      }

      ColumnSelection cs = t.getColumnSelection(false);
      cs.addAttribute(colRef);
      t.setColumnSelection(cs, false);
   }

   /**
    * Updates the expression and type of an existing expression column in the table's
    * public column selection, identified by {@code name}.
    *
    * <p>If no expression column with that name exists, a new one is added (same
    * behaviour as {@link #addExpressionColumn}).</p>
    *
    * @param t          the table assembly to mutate
    * @param name       the column name to find and update
    * @param expression the new expression body
    * @param type       the new data type string, or {@code null} to leave unchanged
    * @param sql        {@code true} if the expression is SQL rather than script
    */
   public static void editExpression(TableAssembly t, String name,
                                     String expression, String type, boolean sql)
   {
      ColumnSelection cs = t.getColumnSelection(false);

      for(int i = 0; i < cs.getAttributeCount(); i++) {
         DataRef ref = cs.getAttribute(i);

         if(ref instanceof ColumnRef cr && cr.getDataRef() instanceof ExpressionRef er) {
            if(name.equals(er.getName()) || name.equals(er.getAttribute())) {
               er.setExpression(expression != null ? expression : "");
               cr.setSQL(sql);

               if(type != null) {
                  cr.setDataType(type);
               }

               t.setColumnSelection(cs, false);
               return;
            }
         }
      }

      // Not found — add as new expression column.
      addExpressionColumn(t, name, expression, type, sql);
   }

   // =========================================================================
   // Sort helpers
   // =========================================================================

   /**
    * Sets (or replaces) the sort direction on the named column.
    *
    * <p>If a {@link SortRef} for {@code field} already exists it is removed first
    * so that only one sort entry per column is present.</p>
    *
    * @param t         the table assembly to mutate
    * @param field     the column name to sort on
    * @param direction {@code "ASC"} or {@code "DESC"} (case-insensitive)
    */
   public static void setSort(TableAssembly t, String field, String direction) {
      SortInfo si = t.getSortInfo();

      if(si == null) {
         si = new SortInfo();
      }
      else {
         // Remove any existing sort entry for this field so we don't get duplicates.
         for(SortRef existing : si.getSorts()) {
            if(field.equals(existing.getName()) || field.equals(existing.getAttribute())) {
               si.removeSort(existing);
               break;
            }
         }
      }

      AttributeRef ref = new AttributeRef(null, field);
      SortRef sr = new SortRef(ref);
      sr.setOrder("DESC".equalsIgnoreCase(direction) ? XConstants.SORT_DESC : XConstants.SORT_ASC);
      si.addSort(sr);
      t.setSortInfo(si);
   }

   // =========================================================================
   // Internal helpers
   // =========================================================================

   private static int parseOperation(String operation) {
      if(operation == null) {
         return XCondition.EQUAL_TO;
      }

      return switch(operation.toUpperCase()) {
         case "!=", "NOT_EQUAL_TO", "<>" -> XCondition.EQUAL_TO; // negated via setNegated
         case "<", "LESS_THAN"           -> XCondition.LESS_THAN;
         case ">", "GREATER_THAN"        -> XCondition.GREATER_THAN;
         default                         -> XCondition.EQUAL_TO;
      };
   }

   private static boolean isNegatedOperation(String operation) {
      if(operation == null) {
         return false;
      }

      return switch(operation.toUpperCase()) {
         case "!=", "NOT_EQUAL_TO", "<>" -> true;
         default                         -> false;
      };
   }
}
