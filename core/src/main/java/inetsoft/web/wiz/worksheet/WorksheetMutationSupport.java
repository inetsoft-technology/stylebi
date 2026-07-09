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
import inetsoft.uql.jdbc.SelectTable;
import inetsoft.uql.jdbc.UniformSQL;
import inetsoft.uql.path.XSelection;
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

   /**
    * Table property recording the output aliases the LAST {@link #applyAggregateInfo}
    * call set on shared {@link ColumnRef}s (newline-separated; empty string when that
    * call applied none). Lets {@link #clearAggregateAliases} distinguish its own
    * bookkeeping aliases from deliberate {@code rename_column} aliases.
    */
   static final String AGGREGATE_OUTPUT_ALIASES = "wiz.aggregate.output.aliases";

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
      throws inetsoft.web.wiz.pairing.PairingException
   {
      // Clear aliases left on the column selection by a PRIOR call's aggregate
      // outputs before resolving anything new. Those aliases exist purely to label
      // aggregate results; once the AggregateInfo is being replaced they become
      // stale references that silently shadow the raw column underneath. Concretely:
      // calling set_group_aggregate a second time on the SAME table using the first
      // call's output alias as the new field (the standard "average of an average"
      // chaining attempt, before the caller realizes it needs a mirror) would resolve
      // that alias back to the un-aggregated raw column and silently compute a flat
      // aggregate over raw rows instead of failing loud or aggregating the prior
      // result — a plausible-looking but numerically wrong answer.
      clearAggregateAliases(t);

      if((groups == null || groups.isEmpty()) &&
         (aggregates == null || aggregates.isEmpty()))
      {
         t.setProperty(AGGREGATE_OUTPUT_ALIASES, "");
         t.setAggregateInfo(new AggregateInfo());
         t.setAggregate(false);
         return;
      }

      AggregateInfo ainfo = new AggregateInfo();
      ColumnSelection cs = t.getColumnSelection(false);

      // Aliases set by THIS call to label aggregate outputs; recorded on the table so
      // the next call's clearAggregateAliases() can tell them apart from aliases set
      // deliberately via rename_column on a column that also happens to be aggregated.
      List<String> appliedAliases = new ArrayList<>();

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

               // And by entity-qualified name (e.g. "customer1.first_name")
               if(!cr.isEntityBlank()) {
                  availableColumns.putIfAbsent(cr.getEntity() + "." + cr.getAttribute(), cr);
               }
            }
         }
      }

      for(String group : groups) {
         ColumnRef resolved = availableColumns.get(group);

         if(resolved == null) {
            // Fail loud: a silently invalid GroupRef would be dropped by the next
            // assembly refresh, producing a plausible-but-wrong result. This bites in
            // practice because setting an aggregate alias RENAMES the base column, so
            // a later call referencing the old name misses.
            throw new inetsoft.web.wiz.pairing.PairingException(
               "Column not found for group: '" + group + "'. Available columns: " +
               availableColumns.keySet());
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
            // Fail loud instead of creating an unresolvable AttributeRef that the
            // engine silently drops (see group comment above).
            throw new inetsoft.web.wiz.pairing.PairingException(
               "Column not found for aggregate: '" + spec.field() +
               "'. Available columns: " + availableColumns.keySet());
         }

         AggregateRef ar = new AggregateRef(colRef, formula);

         if(ainfo.containsAggregate(ar)) {
            // Second aggregate on the same column: clone the ref before aliasing.
            // The shared ref was (correctly) mutated by the first spec — that is how
            // the aggregate output column gets its name — so aliasing it again would
            // silently overwrite the first alias (Min(x) as a, Max(x) as b -> both b).
            // The clone carries this spec's own alias into the secondary-aggregate
            // conversion below, which creates a separate expression column for it.
            ColumnRef cloned = (ColumnRef) colRef.clone();

            if(spec.alias() != null) {
               cloned.setAlias(spec.alias());
               appliedAliases.add(spec.alias());
            }

            ainfo.addSecondaryAggregate(new AggregateRef(cloned, formula));
         }
         else {
            // First aggregate on this column: set the alias on the shared ref from the
            // column selection — the aggregate output column is named from it.
            if(spec.alias() != null) {
               colRef.setAlias(spec.alias());
               appliedAliases.add(spec.alias());
            }

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
            // Must scan attributes AND aliases: cs2.getAttribute(name) resolves by the
            // column's display name, which is the ALIAS when one is set — so a prior
            // secondary column named Amount_1 with alias max_amount would not be found
            // and the next secondary would collide on Amount_1 (third-aggregate bug).
            int suffix = 1;
            String exprName = base + "_1";

            while(containsColumnNamed(cs2, exprName)) {
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

      // Always set the property (empty string when no output aliases were applied):
      // its PRESENCE tells the next clearAggregateAliases() call that this
      // AggregateInfo came through here, so only the recorded aliases are cleared and
      // a rename_column alias on an aggregated column survives re-aggregation.
      t.setProperty(AGGREGATE_OUTPUT_ALIASES,
                    appliedAliases.isEmpty() ? "" : String.join("\n", appliedAliases));
      t.setAggregateInfo(ainfo);
      t.setAggregate(!ainfo.isEmpty());
   }

   // =========================================================================
   // SQL query helpers
   // =========================================================================

   /** Matches a trailing double-quoted identifier, e.g. the {@code title} in {@code "f"."title"}. */
   private static final java.util.regex.Pattern QUOTED_TAIL =
      java.util.regex.Pattern.compile("\"([^\"]+)\"$");

   /**
    * Cleans up column names mangled by the freeform-SQL parser for unaliased QUALIFIED
    * column references: {@code SELECT f.title} with no {@code AS} clause can come back
    * with the raw attribute name literally set to {@code "f"."title"}. An embedded
    * double-quote is never legitimate in a column name, so the wrapped ref is replaced
    * outright with a clean {@link AttributeRef} (same entity, trailing identifier) —
    * fixing only the display alias is not enough, because {@code SELECT *} expansion
    * over a derived table walks {@code getAttribute()}, not the alias.
    */
   static void sanitizeSqlColumnNames(ColumnSelection columns) {
      if(columns == null) {
         return;
      }

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         if(!(columns.getAttribute(i) instanceof ColumnRef cr)) {
            continue;
         }

         String attr = cr.getAttribute();

         if(attr == null || attr.indexOf('"') < 0) {
            continue;
         }

         java.util.regex.Matcher m = QUOTED_TAIL.matcher(attr);

         if(m.find()) {
            DataRef inner = cr.getDataRef();
            String entity = inner != null ? inner.getEntity() : null;
            cr.setDataRef(new AttributeRef(entity, m.group(1)));
         }
      }
   }

   /**
    * Clears the SAME mangled fallback alias described on {@link #sanitizeSqlColumnNames},
    * but on the {@code UniformSQL}'s own {@link XSelection} rather than the assembly's
    * {@link ColumnSelection} — two separate structures that both need to agree. Query
    * execution resolves output columns via {@code XSelection.indexOfColumn}, whose
    * qualified-suffix fallback is skipped for any entry with a non-null alias; the
    * mangled alias therefore shadows the fallback and the column is silently dropped
    * from the executed result. Clearing the alias (not replacing it) restores the
    * fallback.
    *
    * <p>Recurses into derived-table subqueries ({@code SelectTable.getName()} returning
    * a nested {@code UniformSQL}): for {@code SELECT * FROM (...) alias} the mangled
    * alias lives on the INNER subquery's selection, the outer one is just {@code *}.</p>
    */
   static void sanitizeSqlSelectionAliases(UniformSQL sql) {
      if(sql == null) {
         return;
      }

      sanitizeSelectionAliases(sql.getSelection());

      for(int i = 0; i < sql.getTableCount(); i++) {
         SelectTable table = sql.getSelectTable(i);
         Object name = table == null ? null : table.getName();

         if(name instanceof UniformSQL) {
            sanitizeSqlSelectionAliases((UniformSQL) name);
         }
      }
   }

   /**
    * Clears the mangled fallback alias (see {@link #sanitizeSqlSelectionAliases}) on a single
    * {@link XSelection}, without descending into nested subqueries.
    */
   private static void sanitizeSelectionAliases(XSelection selection) {
      if(selection == null) {
         return;
      }

      for(int i = 0; i < selection.getColumnCount(); i++) {
         String alias = selection.getAlias(i);

         if(alias == null || alias.indexOf('"') < 0) {
            continue;
         }

         if(QUOTED_TAIL.matcher(alias).find()) {
            selection.setAlias(i, null);
         }
      }
   }

   /**
    * Clears the alias on every primary aggregate's underlying {@link ColumnRef} from
    * the table's CURRENT {@link AggregateInfo}, before it gets replaced.
    *
    * <p>{@link #applyAggregateInfo} labels an aggregate's output column by setting an
    * alias directly on the shared {@link ColumnRef} in the column selection (see the
    * "First aggregate on this column" branch above). That alias is only meaningful
    * while THIS AggregateInfo is active — once the table is re-aggregated, the old
    * alias would otherwise keep pointing at the same, now un-aggregated, raw column.</p>
    *
    * <p>When the {@link #AGGREGATE_OUTPUT_ALIASES} property is present (i.e. the old
    * AggregateInfo was built by {@link #applyAggregateInfo}), only the aliases recorded
    * there are cleared, so an alias set deliberately via {@code rename_column} on a
    * column that also happens to be aggregated survives re-aggregation. Without the
    * property (AggregateInfo of unknown provenance, e.g. set through the Composer UI)
    * every aggregate alias is cleared, preferring a loud unresolved-column failure over
    * a silently wrong chained aggregate.</p>
    */
   private static void clearAggregateAliases(TableAssembly t) {
      AggregateInfo old = t.getAggregateInfo();
      // Read-only here: the property is only (over)written where a new
      // AggregateInfo/property pair is committed at the end of applyAggregateInfo. If
      // the current call throws before that point, the previous AggregateInfo stays
      // active and its tracking must stay with it — consuming the property up front
      // would send the NEXT successful call into the clear-all fallback and wipe a
      // deliberate rename_column alias.
      String recorded = t.getProperty(AGGREGATE_OUTPUT_ALIASES);

      if(old == null || old.isEmpty()) {
         return;
      }

      Set<String> ownAliases = recorded == null ? null :
         new HashSet<>(Arrays.asList(recorded.split("\n", -1)));

      for(int i = 0; i < old.getAggregateCount(); i++) {
         AggregateRef ar = old.getAggregate(i);

         if(ar.getDataRef() instanceof ColumnRef cr &&
            (ownAliases == null || ownAliases.contains(cr.getAlias())))
         {
            cr.setAlias(null);
         }
      }
   }

   /**
    * Checks whether the selection contains a column whose raw attribute name OR alias
    * matches {@code name}. Unlike {@link ColumnSelection#getAttribute(String)}, which
    * resolves by display name only, this catches both identities.
    */
   static boolean containsColumnNamed(ColumnSelection cs, String name) {
      for(int i = 0; i < cs.getAttributeCount(); i++) {
         if(cs.getAttribute(i) instanceof ColumnRef cr &&
            (name.equals(cr.getAttribute()) || name.equals(cr.getAlias())))
         {
            return true;
         }
      }

      return false;
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
      expression = normalizeDateArithmetic(t, expression, sql);
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
      expression = normalizeDateArithmetic(t, expression, sql);
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

   /** Matches {@code field['a'] - field['b']} with arbitrary whitespace around the minus. */
   private static final java.util.regex.Pattern DATE_DIFF_PATTERN =
      java.util.regex.Pattern.compile("field\\['([^']+)'\\]\\s*-\\s*field\\['([^']+)'\\]");

   /**
    * Rewrites date-to-date subtraction in script (non-SQL) expressions to use
    * {@code .getTime()}.
    *
    * <p>In the Rhino script engine, {@code java.util.Date} values do not subtract
    * numerically — {@code field['a'] - field['b']} on two date columns silently
    * evaluates to null. The subtraction intent is unambiguous, so normalize it to
    * {@code (field['a'].getTime() - field['b'].getTime())} (a millisecond difference)
    * instead of letting the query return a plausible-but-null column. Only applies
    * when BOTH operands resolve to date-typed columns; SQL-mode expressions are left
    * untouched because native date subtraction is valid on some databases.</p>
    */
   static String normalizeDateArithmetic(TableAssembly t, String expression, boolean sql) {
      if(sql || expression == null || !expression.contains("field[")) {
         return expression;
      }

      ColumnSelection cs = t.getColumnSelection(false);

      if(cs == null) {
         return expression;
      }

      java.util.regex.Matcher m = DATE_DIFF_PATTERN.matcher(expression);
      StringBuilder sb = new StringBuilder();
      boolean changed = false;

      while(m.find()) {
         String left = m.group(1);
         String right = m.group(2);

         if(isDateColumn(cs, left) && isDateColumn(cs, right)) {
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(
               "(field['" + left + "'].getTime() - field['" + right + "'].getTime())"));
            changed = true;
         }
         else {
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(m.group()));
         }
      }

      if(!changed) {
         return expression;
      }

      m.appendTail(sb);
      return sb.toString();
   }

   private static boolean isDateColumn(ColumnSelection cs, String field) {
      DataRef ref = cs.getAttribute(field);

      if(ref == null) {
         for(int i = 0; i < cs.getAttributeCount(); i++) {
            if(cs.getAttribute(i) instanceof ColumnRef cr && field.equals(cr.getAlias())) {
               ref = cr;
               break;
            }
         }
      }

      if(ref == null) {
         return false;
      }

      String dtype = ref.getDataType();
      return XSchema.DATE.equals(dtype) || XSchema.TIME_INSTANT.equals(dtype) ||
         XSchema.TIME.equals(dtype);
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
