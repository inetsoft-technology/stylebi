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
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;

import java.util.*;

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

   /**
    * A named group mapping — group name to list of values that belong to that group.
    */
   public record GroupMapping(String name, java.util.List<String> values) {}

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
      DataRef ref = resolveField(t, field);
      // Infer type from the resolved column so numeric comparisons work correctly.
      String dtype = ref.getDataType() != null && !ref.getDataType().isBlank()
         ? ref.getDataType() : XSchema.STRING;

      Condition c = new Condition(dtype);
      c.setOperation(op);

      if(isEqualInclusive(operation)) {
         c.setEqual(true);
      }

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

      // Collect indices of conditions to remove.
      java.util.Set<Integer> removeIdx = new java.util.HashSet<>();

      for(int i = 0; i < src.getSize(); i++) {
         HierarchyItem hi = src.getItem(i);

         if(hi instanceof ConditionItem ci) {
            DataRef attr = ci.getAttribute();

            if(field.equals(attr.getName()) || field.equals(attr.getAttribute())) {
               removeIdx.add(i);
            }
         }
      }

      if(removeIdx.isEmpty()) {
         return;
      }

      // Rebuild the list keeping surviving conditions with their original junctions.
      ConditionList result = new ConditionList();

      for(int i = 0; i < src.getSize(); i++) {
         if(removeIdx.contains(i)) {
            continue;
         }

         HierarchyItem hi = src.getItem(i);

         if(hi instanceof JunctionOperator) {
            // Only keep a junction if it sits between two surviving conditions.
            boolean prevSurvived = result.getSize() > 0
               && result.getItem(result.getSize() - 1) instanceof ConditionItem;
            boolean nextSurvives = false;

            for(int j = i + 1; j < src.getSize(); j++) {
               if(src.getItem(j) instanceof ConditionItem && !removeIdx.contains(j)) {
                  nextSurvives = true;
                  break;
               }
            }

            if(prevSurvived && nextSurvives) {
               result.append(hi);
            }
         }
         else {
            result.append(hi);
         }
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
      if((groups == null || groups.isEmpty()) &&
         (aggregates == null || aggregates.isEmpty()))
      {
         t.setAggregateInfo(new AggregateInfo());
         t.setAggregate(false);
         return;
      }

      AggregateInfo ainfo = new AggregateInfo();
      ColumnSelection cs = t.getColumnSelection(false);

      // Build a lookup map of available columns keyed by both raw name and alias,
      // matching the pattern used by AggregateDialogService.getAggregateInfo().
      Map<String, ColumnRef> availableColumns = new LinkedHashMap<>();

      if(cs != null) {
         for(int i = 0; i < cs.getAttributeCount(); i++) {
            DataRef ref = cs.getAttribute(i);

            if(ref instanceof ColumnRef cr) {
               availableColumns.put(cr.getName(), cr);

               if(cr.getAlias() != null && !cr.getAlias().isEmpty()) {
                  availableColumns.putIfAbsent(cr.getAlias(), cr);
               }

               // Also index by raw attribute name (without entity prefix)
               availableColumns.putIfAbsent(cr.getAttribute(), cr);
            }
         }
      }

      for(String group : groups) {
         ColumnRef resolved = availableColumns.get(group);

         if(resolved == null) {
            resolved = new ColumnRef(new AttributeRef(null, group));
         }

         GroupRef gr = new GroupRef(resolved);
         ainfo.addGroup(gr);
      }

      for(AggregateSpec spec : aggregates) {
         AggregateFormula formula = AggregateFormula.getFormula(spec.formula());

         if(formula == null) {
            formula = AggregateFormula.SUM;
         }

         ColumnRef colRef = availableColumns.get(spec.field());

         if(colRef == null) {
            colRef = new ColumnRef(new AttributeRef(null, spec.field()));
         }

         AggregateRef ar = new AggregateRef(colRef, formula);

         if(spec.alias() != null) {
            colRef.setAlias(spec.alias());
         }

         if(ainfo.containsAggregate(ar)) {
            ainfo.addSecondaryAggregate(ar);
         }
         else {
            ainfo.addAggregate(ar, false);
         }
      }

      // Convert secondary aggregates into expression columns + primary aggregates.
      // This matches the standard UI pattern (AggregateDialogService.updateAggregateInfo):
      // each secondary aggregate gets a new expression column (field['Amount']) added to
      // the column selection with a unique name, then a new primary aggregate is created
      // on that expression column so the query engine produces a separate output column.
      AggregateRef[] secondaryAggs = ainfo.getSecondaryAggregates();

      if(secondaryAggs.length > 0) {
         ColumnSelection cs2 = t.getColumnSelection();

         for(AggregateRef sref : secondaryAggs) {
            ColumnRef cref = (ColumnRef) sref.getDataRef();
            String base = cref.getAttribute();

            // Generate a unique column name (e.g. Amount_1, Amount_2).
            int suffix = 1;
            String exprName = base + "_1";

            while(cs2.getAttribute(exprName) != null) {
               exprName = base + "_" + (++suffix);
            }

            // Create an expression column that references the original column.
            ExpressionRef exp = new ExpressionRef(null, exprName);
            String fieldRef = cref.isEntityBlank() ? cref.getAttribute()
               : cref.getEntity() + "." + cref.getAttribute();
            exp.setExpression("field['" + fieldRef + "']");
            ColumnRef exprCol = new ColumnRef(exp);
            exprCol.setDataType(cref.getDataType());

            // Carry over the alias from the secondary aggregate.
            if(cref.getAlias() != null) {
               exprCol.setAlias(cref.getAlias());
            }

            cs2.addAttribute(exprCol);

            // Create a new primary aggregate on the expression column.
            AggregateRef newAgg = new AggregateRef(exprCol, sref.getFormula());
            ainfo.addAggregate(newAgg, false);
         }

         ainfo.removeSecondaryAggregates();
         t.setColumnSelection(cs2);
      }

      t.setAggregateInfo(ainfo);
      t.setAggregate(!ainfo.isEmpty());
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

      DataRef ref = resolveField(t, field);
      SortRef sr = new SortRef(ref);
      sr.setOrder("DESC".equalsIgnoreCase(direction) ? XConstants.SORT_DESC : XConstants.SORT_ASC);
      si.addSort(sr);
      t.setSortInfo(si);
   }

   // =========================================================================
   // Advanced conditions
   // =========================================================================

   /**
    * Describes a single condition in a condition tree (for {@code set_conditions}
    * and {@code set_post_conditions}).
    *
    * @param field     column name
    * @param operation comparison operator string
    * @param values    literal values
    * @param negated   {@code true} to negate the condition
    * @param type      optional XSchema data type (e.g. {@code "integer"}, {@code "double"},
    *                  {@code "date"}).  When {@code null}, the type is inferred from the
    *                  column's declared type in the table's column selection; falls back to
    *                  {@code "string"} if the column is unknown.  Agents should set this
    *                  explicitly for numeric or date fields to avoid lexicographic comparisons.
    */
   public record ConditionSpec(String field, String operation,
                               List<String> values, boolean negated,
                               String type) {}

   /**
    * Describes a junction (AND/OR) between conditions in a condition tree.
    *
    * @param junction {@code "AND"} or {@code "OR"}
    * @param level    nesting level (0 = root)
    */
   public record JunctionSpec(String junction, int level) {}

   /**
    * A single node in a condition tree — either a condition or a junction.
    * Exactly one of {@code condition} or {@code junction} should be non-null.
    *
    * @param condition non-null for a condition item
    * @param junction  non-null for a junction operator
    * @param level     nesting level for this node (0 = root)
    */
   public record ConditionNode(ConditionSpec condition, JunctionSpec junction,
                               int level) {}

   /**
    * Builds a {@link ConditionList} from a flat list of alternating condition
    * and junction nodes, then applies it to the table.
    *
    * <p>Nodes must alternate: condition, junction, condition, junction, ...
    * (same structure as {@link ConditionList} internally).</p>
    *
    * @param t     the table assembly
    * @param nodes the condition tree nodes
    * @param post  {@code true} to set as post-aggregate conditions (HAVING),
    *              {@code false} for pre-aggregate conditions (WHERE)
    */
   public static void setConditions(TableAssembly t, List<ConditionNode> nodes,
                                    boolean post)
   {
      if(nodes == null || nodes.isEmpty()) {
         if(post) {
            t.setPostConditionList(null);
         }
         else {
            t.setPreConditionList(null);
         }

         return;
      }

      ConditionList cl = new ConditionList();

      for(ConditionNode node : nodes) {
         if(node.condition() != null) {
            ConditionSpec spec = node.condition();
            int op = parseOperation(spec.operation());
            boolean negate = spec.negated() || isNegatedOperation(spec.operation());
            String dtype = spec.type() != null ? spec.type() : inferColumnType(t, spec.field(), post);

            DataRef ref = resolveField(t, spec.field(), post);
            Condition c = new Condition(dtype);
            c.setOperation(op);

            if(isEqualInclusive(spec.operation())) {
               c.setEqual(true);
            }

            if(negate) {
               c.setNegated(true);
            }

            if(spec.values() != null) {
               for(String v : spec.values()) {
                  // $(varName) syntax maps to a UserVariable reference.
                  if(v != null && v.startsWith("$(") && v.endsWith(")")) {
                     c.addValue(new UserVariable(v.substring(2, v.length() - 1)));
                  }
                  else {
                     c.addValue(v);
                  }
               }
            }

            cl.append(new ConditionItem(ref, c, node.level()));
         }
         else if(node.junction() != null) {
            JunctionSpec js = node.junction();
            int jop = "OR".equalsIgnoreCase(js.junction())
               ? JunctionOperator.OR : JunctionOperator.AND;
            cl.append(new JunctionOperator(jop, js.level()));
         }
      }

      if(post) {
         t.setPostConditionList(cl.isEmpty() ? null : cl);
      }
      else {
         t.setPreConditionList(cl.isEmpty() ? null : cl);
      }
   }

   /**
    * Describes a ranking condition.
    *
    * @param field     column to rank by
    * @param n         number of rows (top/bottom N)
    * @param operation {@code "TOP_N"} or {@code "BOTTOM_N"}
    * @param groupOthers {@code true} to group remaining rows as "Others"
    */
   public record RankingSpec(String field, int n, String operation,
                             boolean groupOthers) {}

   /**
    * Sets a ranking condition on the table.
    */
   public static void setRanking(TableAssembly t, RankingSpec spec) {
      if(spec == null) {
         t.setRankingConditionList(new ConditionList());
         return;
      }

      int op = "BOTTOM_N".equalsIgnoreCase(spec.operation())
         ? XCondition.BOTTOM_N : XCondition.TOP_N;

      DataRef ref = resolveField(t, spec.field());
      inetsoft.uql.asset.RankingCondition rc = new inetsoft.uql.asset.RankingCondition();
      rc.setOperation(op);
      rc.setN(spec.n());
      rc.setGroupOthers(spec.groupOthers());

      ConditionList cl = new ConditionList();
      cl.append(new ConditionItem(ref, rc, 0));
      t.setRankingConditionList(cl);
   }

   // =========================================================================
   // Internal helpers
   // =========================================================================

   /**
    * Resolves a field name (which may be a column name or alias) against the table's
    * column selection.  Returns the matching {@link DataRef} from the selection, or
    * a new {@link AttributeRef} as fallback.  This mirrors the lookup pattern used by
    * {@code AggregateDialogService.getAggregateInfo()} so that conditions, ranking,
    * and aggregates reference the real column objects.
    */
   static DataRef resolveField(TableAssembly t, String field) {
      return resolveField(t, field, false);
   }

   /**
    * Resolves a field name against the table's column selection.
    *
    * @param t     the table assembly
    * @param field the field name or alias to resolve
    * @param post  {@code true} to search the PUBLIC column selection (includes
    *              aggregate output aliases), {@code false} for the PRIVATE selection
    */
   static DataRef resolveField(TableAssembly t, String field, boolean post) {
      // Post-aggregate (HAVING) conditions must reference the AggregateInfo refs, not the
      // column selection: the condition dialog builds its post-aggregate field list from
      // AggregateInfo (AggregateRef/GroupRef), and a condition stored with a plain ColumnRef
      // is displayed as a pre-aggregate condition. The alias set by set_group_aggregate lives
      // on the column selection's ColumnRef too, so AggregateInfo must be searched FIRST or
      // the ColumnRef alias match below would win.
      if(post && field != null) {
         AggregateInfo ainfo = t.getAggregateInfo();

         if(ainfo != null && !ainfo.isEmpty()) {
            for(int i = 0; i < ainfo.getAggregateCount(); i++) {
               AggregateRef ar = ainfo.getAggregate(i);

               // Match the alias (e.g. "total_paid"), the view ("Sum(total_paid)"),
               // or the base attribute name.
               if(field.equals(ar.toView()) ||
                  ar.getDataRef() instanceof ColumnRef cr &&
                     (field.equals(cr.getAlias()) || field.equals(cr.getAttribute())))
               {
                  return ar;
               }
            }

            for(int i = 0; i < ainfo.getGroupCount(); i++) {
               GroupRef gr = ainfo.getGroup(i);

               if(field.equals(gr.getAttribute()) ||
                  gr.getDataRef() instanceof ColumnRef cr && field.equals(cr.getAlias()))
               {
                  return gr;
               }
            }
         }
      }

      ColumnSelection cs = t.getColumnSelection(post);

      if(cs != null && field != null) {
         // Try direct lookup first (handles both raw name and alias via ColumnSelection).
         DataRef col = cs.getAttribute(field);

         if(col != null) {
            return col;
         }

         // Fallback: scan for alias match (getAttribute may not check aliases on all paths).
         for(int i = 0; i < cs.getAttributeCount(); i++) {
            DataRef ref = cs.getAttribute(i);

            if(ref instanceof ColumnRef cr &&
               field.equals(cr.getAlias()))
            {
               return cr;
            }
         }
      }

      return new AttributeRef(null, field);
   }

   /**
    * Looks up the XSchema data type for {@code field} in the table's column selection.
    * Falls back to {@link XSchema#STRING} when the column is not found or has no type.
    */
   private static String inferColumnType(TableAssembly t, String field) {
      return inferColumnType(t, field, false);
   }

   private static String inferColumnType(TableAssembly t, String field, boolean post) {
      DataRef col = resolveField(t, field, post);

      if(col.getDataType() != null && !col.getDataType().isBlank()) {
         return col.getDataType();
      }

      return XSchema.STRING;
   }

   static int parseOperation(String operation) {
      if(operation == null) {
         return XCondition.EQUAL_TO;
      }

      return switch(operation.toUpperCase().replace(' ', '_')) {
         case "!=", "NOT_EQUAL_TO", "<>" -> XCondition.EQUAL_TO; // negated via setNegated
         case "<", "LESS_THAN"           -> XCondition.LESS_THAN;
         case ">", "GREATER_THAN"        -> XCondition.GREATER_THAN;
         case "<=", "LESS_THAN_OR_EQUAL" -> XCondition.LESS_THAN;
         case ">=", "GREATER_THAN_OR_EQUAL" -> XCondition.GREATER_THAN;
         case "BETWEEN"                  -> XCondition.BETWEEN;
         case "ONE_OF", "IN"             -> XCondition.ONE_OF;
         case "NOT_ONE_OF"               -> XCondition.ONE_OF;  // negated via setNegated
         case "STARTING_WITH"            -> XCondition.STARTING_WITH;
         case "CONTAINS"                 -> XCondition.CONTAINS;
         case "LIKE"                     -> XCondition.LIKE;
         case "NULL", "IS_NULL"          -> XCondition.NULL;
         case "NOT_NULL"                 -> XCondition.NULL;    // negated via setNegated
         default                         -> XCondition.EQUAL_TO;
      };
   }

   /**
    * Returns {@code true} if the operation string represents a "less-than-or-equal"
    * or "greater-than-or-equal" comparison, which requires {@link Condition#setEqual(boolean)}
    * to be set to {@code true} in addition to the base LESS_THAN / GREATER_THAN operation.
    */
   private static boolean isEqualInclusive(String operation) {
      if(operation == null) {
         return false;
      }

      return switch(operation.toUpperCase().replace(' ', '_')) {
         case "<=", "LESS_THAN_OR_EQUAL", ">=", "GREATER_THAN_OR_EQUAL" -> true;
         default -> false;
      };
   }

   private static boolean isNegatedOperation(String operation) {
      if(operation == null) {
         return false;
      }

      return switch(operation.toUpperCase().replace(' ', '_')) {
         case "!=", "NOT_EQUAL_TO", "<>",
              "NOT_ONE_OF", "NOT_NULL"  -> true;
         default                        -> false;
      };
   }
}
