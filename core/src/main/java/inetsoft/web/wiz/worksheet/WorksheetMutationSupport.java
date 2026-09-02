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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.DataRefWrapper;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.jdbc.SelectTable;
import inetsoft.uql.jdbc.UniformSQL;
import inetsoft.uql.path.XSelection;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.Tool;
import inetsoft.web.wiz.pairing.PairingException;

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
    * @param n       the N/P operand for a parametrized formula (e.g. {@code "NthLargest"},
    *                {@code "NthSmallest"}, {@code "NthMostFrequent"}, {@code "PthPercentile"});
    *                {@code null} (or a formula that doesn't take one, per
    *                {@link AggregateFormula#hasN}) is ignored
    * @param secondaryColumn the "With"/"By" second column a two-column formula reads (e.g.
    *                {@code First}, {@code Last}, {@code Correlation}, {@code Covariance},
    *                {@code WeightedAverage} — see {@code aggregate-formula.ts}'s own list of
    *                which formulas need one). Applied uniformly, with no formula-specific
    *                branching, matching {@code AggregateDialogService.java:311-319}; {@code null}
    *                leaves the aggregate single-column
    * @param percentageOf {@code "group"} (the UI's "Sub Total", {@link
    *                XConstants#PERCENTAGE_OF_GROUP}), {@code "grand_total"} ({@link
    *                XConstants#PERCENTAGE_OF_GRANDTOTAL}), or {@code "none"} (case-insensitive);
    *                {@code null} leaves the aggregate's existing percentage setting untouched.
    *                Changes the value the worksheet query itself returns (wrapped in a {@code
    *                PercentageFormula}), not merely chart-rendering presentation — see
    *                {@code aggregate-pane.component.html:120-133}
    */
   public record AggregateSpec(String field, String formula, String alias, Integer n,
                               String secondaryColumn, String percentageOf) {
      public AggregateSpec(String field, String formula, String alias, Integer n) {
         this(field, formula, alias, n, null, null);
      }

      public AggregateSpec(String field, String formula, String alias) {
         this(field, formula, alias, null, null, null);
      }
   }

   /**
    * Describes a single group-by column for
    * {@link #applyAggregateInfo(TableAssembly, List, List)}.
    *
    * <p>Accepted on the wire as either a bare column name string (equivalent to
    * {@code dateLevel == null}) or an object {@code {"field": ..., "dateLevel": ...}} —
    * see {@link Deserializer}.</p>
    *
    * @param field     the source column name
    * @param dateLevel optional date grouping level applied directly to the group (no
    *                  derived column needed) for date-type columns — one of the same
    *                  option strings accepted by {@code add_date_range_column}'s
    *                  {@code dateOption} (e.g. {@code "YEAR"}, {@code "QUARTER"},
    *                  {@code "MONTH"}); {@code null} for a plain (non-date-bucketed) group
    * @param timeSeries mirrors the Composer's own "As time series" checkbox
    *                  ({@code GroupRef.timeSeries} -- {@code aggregate-pane.component.html:134-144}):
    *                  when {@code true}, the query engine fills in empty periods between the
    *                  first and last observed date bucket instead of only emitting buckets that
    *                  have data, matching {@code AggregateDialogService.java:455-467}'s own
    *                  handling, including clearing a pre-existing sort on this column ({@link
    *                  #applyAggregateInfo} does the same). {@code null}/{@code false} leaves the
    *                  group's existing time-series flag alone/unset.
    * @param namedGroup optional name of a predefined named group (created via
    *                  {@code add_named_group}) to apply to this group-by column instead of
    *                  grouping by exact value; {@code null} for a plain group. Mutually
    *                  exclusive with {@code dateLevel} on the same entry.
    */
   @JsonDeserialize(using = GroupSpec.Deserializer.class)
   public record GroupSpec(String field, String dateLevel, Boolean timeSeries, String namedGroup) {
      public GroupSpec(String field) {
         this(field, null, null, null);
      }

      public GroupSpec(String field, String dateLevel) {
         this(field, dateLevel, null, null);
      }

      /**
       * Compatibility constructor for callers built before {@code namedGroup} was added —
       * defaults it to {@code null}.
       */
      public GroupSpec(String field, String dateLevel, Boolean timeSeries) {
         this(field, dateLevel, timeSeries, null);
      }

      /**
       * Compatibility constructor for callers built before {@code timeSeries} was added —
       * defaults it to {@code null}.
       */
      public GroupSpec(String field, String dateLevel, String namedGroup) {
         this(field, dateLevel, null, namedGroup);
      }

      /**
       * Accepts either a plain JSON string (column name) or a
       * {@code {field, dateLevel, timeSeries, namedGroup}} object.
       */
      static final class Deserializer extends JsonDeserializer<GroupSpec> {
         @Override
         public GroupSpec deserialize(JsonParser p, DeserializationContext ctxt) throws java.io.IOException {
            JsonNode node = p.getCodec().readTree(p);

            if(node.isTextual()) {
               return new GroupSpec(node.asText());
            }

            if(!node.isObject()) {
               return ctxt.reportInputMismatch(GroupSpec.class,
                  "Expected a group-by column name string or a {field, dateLevel} object, got: " +
                  node);
            }

            String field = node.hasNonNull("field") ? node.get("field").asText() : null;
            // "dateOption" is add_date_range_column's spelling of this same concept;
            // accept it as an alias so that near-miss doesn't silently deserialize to an
            // ungrouped date column.
            String dateLevel = node.hasNonNull("dateLevel") ? node.get("dateLevel").asText() :
               node.hasNonNull("dateOption") ? node.get("dateOption").asText() : null;
            Boolean timeSeries = node.hasNonNull("timeSeries") ?
               node.get("timeSeries").asBoolean() : null;
            String namedGroup = node.hasNonNull("namedGroup") ? node.get("namedGroup").asText() : null;
            return new GroupSpec(field, dateLevel, timeSeries, namedGroup);
         }
      }
   }

   /**
    * A named group mapping — group name to list of values that belong to that group, matched
    * using {@code operation} (any operator accepted by {@link #parseOperation}, e.g.
    * {@code "STARTING_WITH"}, {@code "!="}, {@code "ONE_OF"}; {@code null} defaults to
    * {@code EQUAL_TO}, preserving the historical behavior of this record).
    */
   public record GroupMapping(String name, java.util.List<String> values, String operation) {
      public GroupMapping(String name, java.util.List<String> values) {
         this(name, values, null);
      }
   }

   /**
    * Describes one edge of an N-ary {@code add_join} (three or more tables joined into a single
    * assembly in one call, as opposed to the pairwise {@code leftTable}/{@code rightTable} form).
    *
    * @param leftTable  a source table assembly name — either side of any edge may name a table
    *                    introduced by another edge, so the edges need not form a single left-to-
    *                    right chain (e.g. a hub table joined to two others)
    * @param leftKey    the column name from {@code leftTable} to join on; ignored for
    *                    {@code joinType == "CROSS"}
    * @param rightTable the other source table assembly name
    * @param rightKey   the column name from {@code rightTable} to join on; ignored for
    *                    {@code joinType == "CROSS"}
    * @param joinType   one of {@code "INNER"}, {@code "LEFT"}, {@code "RIGHT"}, {@code "FULL"},
    *                   {@code "CROSS"} (case-insensitive; defaults to {@code "INNER"}).
    *                   {@code "MERGE"} is not valid per-edge here — a merge join is a distinct
    *                   assembly type ({@code MergeJoinTableAssembly}) that cannot be mixed into a
    *                   multi-table {@code RelationalJoinTableAssembly}; use {@code add_merge_join}.
    *                   {@code "CROSS"} is likewise an exclusive operation and may only appear
    *                   when it is the SOLE edge in the call — combined with any other edge it is
    *                   rejected, since {@link TableAssemblyOperator#checkValidity} refuses an
    *                   exclusive operator once more than one edge is present.
    */
   public record JoinPathSpec(String leftTable, String leftKey, String rightTable, String rightKey,
                              String joinType) {}

   /**
    * Builds the {@link ConditionList} for one named-group mapping, honoring the mapping's
    * {@code operation} (any operator accepted by {@link #parseOperation}, defaulting to
    * {@code EQUAL_TO} when omitted, matching this method's historical behavior).
    *
    * <p>{@code ONE_OF}/{@code NOT_ONE_OF} and {@code BETWEEN} test all of {@code m.values()}
    * within a single {@link Condition}, matching how {@link Condition#evaluate} reads those
    * operators — splitting them into OR'd single-value conditions the way the other operators
    * below are handled would silently test only the first value. {@code NULL}/{@code NOT_NULL}
    * take no value. Negated equality ({@code !=}/{@code NOT_EQUAL_TO}/{@code <>}) is routed
    * through the same single-condition path as {@code NOT_ONE_OF} rather than per-value OR'd
    * negation: {@code EQUAL_TO} only ever reads a condition's first value (see
    * {@link Condition#evaluate}), so OR'ing several separately-negated single-value EQUAL_TO
    * conditions together (e.g. {@code v != A OR v != B}) is a near-tautology — true for every
    * {@code v} except one that somehow equals both {@code A} and {@code B} at once — instead of
    * the intended "equal to none of these" ({@code NOT(v == A OR v == B)}, i.e. a negated
    * {@code ONE_OF} over the same values). Every remaining operator (plain equality, comparisons,
    * {@code STARTING_WITH}, {@code CONTAINS}, {@code LIKE}) only ever looks at its condition's
    * first value, so each of {@code m.values()} becomes its own condition, OR'd together — e.g.
    * {@code STARTING_WITH ["N", "S"]} means "starts with N or starts with S".</p>
    *
    * <p>Shared by {@code WorksheetEditService.Editor.addNamedGroup}/{@code editNamedGroup} (a
    * grouping attached to a worksheet table's column) and
    * {@code WorksheetAgentController.addDatasourceScopedNamedGroup} (a grouping scoped directly
    * to a datasource/logical-model or physical-table attribute) — the condition-building logic is
    * identical either way; only how {@code conditionRef}/{@code conditionType} were resolved
    * differs.</p>
    *
    * @param ws the worksheet the group is being built against, used only to resolve a
    *           {@code DATE_IN} mapping's value against a worksheet-level {@link DateRangeAssembly}
    *           when it does not name a built-in date range; see {@link #resolveDateInCondition}.
    */
   static ConditionList buildGroupConditionList(
      String conditionType, DataRef conditionRef, GroupMapping m, Worksheet ws)
      throws PairingException
   {
      String operation = m.operation();
      int op = parseOperation(operation);
      boolean negate = isNegatedOperation(operation);
      boolean equalInclusive = isEqualInclusive(operation);
      ConditionList conds = new ConditionList();

      if(op == XCondition.NULL) {
         Condition c = new Condition(conditionType);
         c.setOperation(op);
         c.setNegated(negate);
         conds.append(new ConditionItem(conditionRef, c, 0));
         return conds;
      }

      boolean negatedEquality = op == XCondition.EQUAL_TO && negate;

      if(op == XCondition.ONE_OF || negatedEquality) {
         // Condition.evaluate() for ONE_OF/BETWEEN reads the raw values list directly (no
         // per-value OR'ing like the fallback branch below), so an empty or wrong-sized list here
         // is not "no values matched" -- it is a silently wrong condition. An empty ONE_OF matches
         // nothing; a NEGATED empty ONE_OF (e.g. "!=" with no values) matches EVERYTHING.
         if(m.values() == null || m.values().isEmpty()) {
            throw new PairingException(
               "Group \"" + m.name() + "\": operation \"" + operation +
                  "\" requires at least one value.");
         }

         Condition c = new Condition(conditionType);
         c.setOperation(XCondition.ONE_OF);
         c.setNegated(negate);

         for(String v : m.values()) {
            c.addValue(v);
         }

         conds.append(new ConditionItem(conditionRef, c, 0));
         return conds;
      }

      if(op == XCondition.BETWEEN) {
         if(m.values() == null || m.values().size() != 2) {
            throw new PairingException(
               "Group \"" + m.name() + "\": BETWEEN requires exactly two values (low, high), got " +
                  (m.values() == null ? 0 : m.values().size()) + ".");
         }

         Condition c = new Condition(conditionType);
         c.setOperation(op);
         c.setNegated(negate);

         for(String v : m.values()) {
            c.addValue(v);
         }

         conds.append(new ConditionItem(conditionRef, c, 0));
         return conds;
      }

      if(op == XCondition.DATE_IN) {
         // Same single-condition shape as NULL/ONE_OF/BETWEEN above -- DATE_IN is one range name,
         // not a per-value OR list. See resolveDateInCondition.
         XCondition resolved = resolveDateInCondition(
            ws, m.values() != null && !m.values().isEmpty() ? m.values().get(0) : null);
         resolved.setNegated(negate);
         conds.append(new ConditionItem(conditionRef, resolved, 0));
         return conds;
      }

      for(int i = 0; i < m.values().size(); i++) {
         if(i > 0) {
            conds.append(new JunctionOperator(JunctionOperator.OR, 0));
         }

         Condition c = new Condition(conditionType);
         c.setOperation(op);

         if(equalInclusive) {
            c.setEqual(true);
         }

         if(negate) {
            c.setNegated(true);
         }

         c.addValue(m.values().get(i));
         conds.append(new ConditionItem(conditionRef, c, 0));
      }

      return conds;
   }

   // =========================================================================
   // Filter helpers
   // =========================================================================

   /**
    * Builds a pre-condition and appends it (AND-joined) to the table's existing
    * pre-condition list.
    *
    * <p>{@link #parseOperation} owns the operator vocabulary and is the single place it is
    * written down -- deliberately not restated here, since the copy that used to live in this
    * javadoc listed four of the fifteen forms and said unrecognised strings fell back to
    * equality, which is the defect parseOperation now refuses. An <i>absent</i> operator still
    * means equality; a supplied one that is not recognised throws.</p>
    *
    * @param t         the table assembly to mutate
    * @param field     the column name to filter on
    * @param operation the comparison operator; see {@link #parseOperation} for the accepted forms
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

      ConditionItem item;

      if(op == XCondition.DATE_IN) {
         // DATE_IN is inherently single-valued (a range name, not a list) and is never negated
         // through this operator token -- isNegatedOperation("date_in") is always false, there is
         // no "not_date_in" form -- so the equal-inclusive/negate/value-loop logic below does not
         // apply here.
         XCondition resolved = resolveDateInCondition(
            t.getWorksheet(), values.length > 0 ? values[0] : null);
         item = new ConditionItem(ref, resolved, 0);
      }
      else {
         Condition c = new Condition(dtype);
         c.setOperation(op);

         if(isEqualInclusive(operation)) {
            c.setEqual(true);
         }

         if(negate) {
            c.setNegated(true);
         }

         for(String v : values) {
            c.addValue(conditionValue(dtype, v));
         }

         item = new ConditionItem(ref, c, 0);
      }

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
    * <p>Columns referenced here must already exist in the private column selection,
    * except that a group with a {@code dateLevel} replaces its base column with a
    * {@link DateRangeRef}-wrapped one (see below).</p>
    *
    * @param t          the table assembly to mutate
    * @param groups     group-by column specs (name, plus optional date grouping level);
    *                   {@code null} is treated as empty
    * @param aggregates aggregate measures to apply; {@code null} is treated as empty
    */
   public static void applyAggregateInfo(TableAssembly t, List<GroupSpec> groups,
                                         List<AggregateSpec> aggregates)
      throws inetsoft.web.wiz.pairing.PairingException
   {
      applyAggregateInfo(t, groups, aggregates, false);
   }

   /**
    * Builds and sets a new {@link AggregateInfo} on the table from the supplied
    * group and aggregate specs, optionally in crosstab mode — the same "Switch to
    * Crosstab" toggle as the Composer's own Group and Aggregate dialog.
    *
    * @param t          the table assembly to mutate
    * @param groups     group-by column specs (name, plus optional date grouping level);
    *                   {@code null} is treated as empty
    * @param aggregates aggregate measures to apply; {@code null} is treated as empty
    * @param crosstab   {@code true} to display the result as a crosstab (row/column
    *                   headers) rather than a flat grouped table. {@link AggregateInfo#isCrosstab}
    *                   only reports {@code true} back once the table has at least 2 groups and 1
    *                   aggregate — with fewer, this is accepted but silently has no visible
    *                   effect, matching the Composer dialog's own {@code setCrosstab} call
    */
   public static void applyAggregateInfo(TableAssembly t, List<GroupSpec> groups,
                                         List<AggregateSpec> aggregates, boolean crosstab)
      throws inetsoft.web.wiz.pairing.PairingException
   {
      // Callers (e.g. WorksheetAgentController) may pass a null groups or aggregates
      // list when the request omits that key entirely; normalize before either the
      // emptiness check below or the per-spec loops run, so a null groups list paired
      // with a non-empty aggregates list (or vice versa) can't NPE instead of being
      // treated as "no groups".
      groups = groups == null ? List.of() : groups;
      aggregates = aggregates == null ? List.of() : aggregates;

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

      // Deliberately no early return for groups.isEmpty() && aggregates.isEmpty():
      // AggregateDialogService#applyAggregateInfo runs its stale-range-column cleanup
      // sweep and AssetUtil.validateConditions unconditionally, even when the new
      // AggregateInfo is completely empty (a full clear), so a full clear here must
      // too - otherwise a DateRangeRef-wrapped column materialized by an earlier
      // dateLevel grouping (e.g. "Quarter(orderDate)") would be left behind forever,
      // since the loops below produce the same empty-AggregateInfo end state either way.
      AggregateInfo ainfo = new AggregateInfo();
      ColumnSelection cs = t.getColumnSelection(false);

      // The table's AggregateInfo as it stood before this call, captured before any
      // mutation below — needed by the stale-range-column cleanup at the end, which
      // (matching AggregateDialogService#processDateGrouping) treats a column as a
      // candidate for removal only if it was one of THESE previous groups, not any
      // DateRangeRef-wrapped column anywhere in the table's column selection.
      AggregateInfo oaginfo = t.getAggregateInfo();
      oaginfo = oaginfo == null ? new AggregateInfo() : oaginfo;

      // The final DataRef for each of THIS call's groups, in order — mirrors
      // AggregateDialogService's own `list`, used by the same cleanup to recognize a
      // range column as still active even though it isn't reachable via `oaginfo`
      // (e.g. it was just created a few lines above for this very call).
      List<DataRef> activeGroupColumns = new ArrayList<>();

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

      for(GroupSpec spec : groups) {
         ColumnRef resolved = availableColumns.get(spec.field());

         if(resolved == null) {
            // Fail loud: a silently invalid GroupRef would be dropped by the next
            // assembly refresh, producing a plausible-but-wrong result. This bites in
            // practice because setting an aggregate alias RENAMES the base column, so
            // a later call referencing the old name misses.
            throw new inetsoft.web.wiz.pairing.PairingException(
               "Column not found for group: '" + spec.field() + "'. Available columns: " +
               availableColumns.keySet());
         }

         if(spec.namedGroup() != null && spec.dateLevel() != null) {
            throw new inetsoft.web.wiz.pairing.PairingException(
               "Group for column '" + spec.field() + "' cannot combine namedGroup ('" +
               spec.namedGroup() + "') with dateLevel ('" + spec.dateLevel() +
               "') -- they are mutually exclusive.");
         }

         GroupRef gr;

         if(spec.dateLevel() == null) {
            gr = new GroupRef(resolved);
         }
         else {
            // A GroupRef whose DataRef is still the raw column, with only setDateGroup()
            // called, has no effect once the aggregate is mergeable into SQL:
            // PreAssetQuery.mergeGroupBy() builds the GROUP BY list purely from
            // findColumn()/getColumn() on the GroupRef's DataRef and never consults
            // getDateGroup(), and isMergeableDataType() only excludes date columns on
            // SQLite — so on any regular JDBC-backed table this would silently group by
            // the raw date value instead of the requested bucket. The Composer's Group
            // and Aggregate dialog (AggregateDialogService#processDateGrouping) avoids
            // this by INSERTING a DateRangeRef-wrapped column as the group's actual
            // DataRef right alongside the raw base column — it does not remove the raw
            // column, so it stays available (e.g. still shows in the Group and Aggregate
            // dialog's column list, still filterable). setDateGroup() is then only a
            // round-trip marker so the level survives a reload. Mirror that mechanism
            // exactly here, including keeping the raw column.
            if(!XSchema.isDateType(resolved.getDataType())) {
               throw new inetsoft.web.wiz.pairing.PairingException(
                  "Column '" + spec.field() + "' is not a date type (type=" +
                  resolved.getDataType() + "). dateLevel requires a date, time, or " +
                  "timeInstant column.");
            }

            int dgroup = WorksheetEditService.Editor.parseDateOption(spec.dateLevel());
            String colName = resolved.getName();
            DateRangeRef dateRef = new DateRangeRef(
               DateRangeRef.getName(colName, dgroup), resolved.getDataRef(), dgroup);
            dateRef.setOriginalType(resolved.getDataType());

            String dtype = dateRef.getDataType();

            // Match AggregateDialogService's TIME special case: a plain time-of-day
            // column keeps its TIME type for interval-style levels — DateRangeRef's
            // default of TIME_INSTANT would be wrong since there is no date component.
            if(XSchema.TIME.equals(dateRef.getOriginalType()) &&
               dateRef.getDateOption() != DateRangeRef.HOUR_OF_DAY_DATE_GROUP)
            {
               dtype = dateRef.getOriginalType();
            }

            ColumnRef dateColumn = new ColumnRef(dateRef);
            dateColumn.setDataType(dtype);

            // Insert (not replace) at the raw column's position, exactly like
            // AggregateDialogService#processDateGrouping — the raw column is left in
            // the selection so it remains independently usable (e.g. add_filter on the
            // raw date, or grouping it a second way later) and keeps showing up in the
            // Composer's Group and Aggregate dialog the same way it would after a user
            // applies a date level through the UI.
            int bidx = cs.indexOfAttribute(resolved);

            if(bidx >= 0) {
               cs.addAttribute(bidx, dateColumn);
            }
            else {
               cs.addAttribute(dateColumn);
            }

            gr = new GroupRef(dateColumn);
            gr.setDateGroup(dgroup);
         }

         if(Boolean.TRUE.equals(spec.timeSeries())) {
            // Matches AggregateDialogService.java:455-467: a time-series group implies gap
            // filling in engine-determined date order, which a manual sort on the same
            // column would conflict with -- the native dialog clears it for exactly this
            // reason when (re)applying a time-series group.
            gr.setTimeSeries(true);

            if(t.getSortInfo() != null && t.getSortInfo().getSort(gr.getDataRef()) != null) {
               t.getSortInfo().removeSort(gr.getDataRef());
            }
         }

         if(spec.namedGroup() != null) {
            // GroupRef.update() itself fails silently (returns true, but leaves
            // getNamedGroupInfo() == null) on an unknown assembly name, a
            // DATA_TYPE_ATTACHED type mismatch, or a COLUMN_ATTACHED attribute-name
            // mismatch -- resolve and validate up front so an unresolvable name is a
            // loud PairingException here rather than a silently ungrouped column, and
            // re-check after update() below for the two mismatch cases it can't catch
            // ahead of time.
            Assembly namedGroupAssembly = t.getWorksheet() == null ? null :
               t.getWorksheet().getAssembly(spec.namedGroup());

            if(!(namedGroupAssembly instanceof NamedGroupAssembly)) {
               throw new inetsoft.web.wiz.pairing.PairingException(
                  "Named group not found: '" + spec.namedGroup() +
                  "'. Create it first with add_named_group.");
            }

            gr.setNamedGroupAssembly(spec.namedGroup());
            gr.update(t.getWorksheet());

            if(gr.getNamedGroupInfo() == null) {
               throw new inetsoft.web.wiz.pairing.PairingException(
                  "Named group '" + spec.namedGroup() + "' does not apply to column '" +
                  spec.field() + "' (attached column or data type does not match).");
            }
         }

         ainfo.addGroup(gr);
         activeGroupColumns.add(gr.getDataRef());
      }

      // Remove a range column left over from a PRIOR call's grouping that is no longer
      // referenced — ported directly from AggregateDialogService#processDateGrouping's
      // "remove useless range column" pass (same two conditions, same oaginfo/list
      // scoping), so this only ever touches a column this mechanism itself put there,
      // never one created by an unrelated operation (e.g. add_date_range_column, or a
      // range column serving some other still-active purpose).
      for(int i = cs.getAttributeCount() - 1; i >= 0; i--) {
         DataRef ref0 = cs.getAttribute(i);
         String name0 = ref0.getName();
         boolean range = ref0 instanceof DataRefWrapper &&
            ((DataRefWrapper) ref0).getDataRef() instanceof DateRangeRef;

         if(!range) {
            for(int j = 0; j < oaginfo.getGroupCount(); j++) {
               GroupRef gref = oaginfo.getGroup(j);
               DateRangeRef dateRef = getDateRangeRef(gref);

               if(dateRef != null && dateRef.isAutoCreate() && name0.equals(dateRef.getName())) {
                  range = true;
                  break;
               }
            }
         }

         if(range) {
            if(activeGroupColumns.contains(ref0)) {
               continue;
            }

            DateRangeRef dateRangeRef = getInnerDateRangeRef(ref0);

            if(dateRangeRef != null && !isAutoRangeColumn(ref0) &&
               cs.containsAttribute(dateRangeRef.getDataRef()))
            {
               continue;
            }

            cs.removeAttribute(i);
         }
      }

      // Matches AggregateDialogService's own unconditional post-cleanup call: a filter/
      // ranking condition that referenced a just-removed column's synthetic name (e.g.
      // add_filter on "Quarter(orderDate)", which read_worksheet_model does list) would
      // otherwise be left pointing at a DataRef no longer in the selection.
      inetsoft.uql.asset.internal.AssetUtil.validateConditions(cs, t);

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

         ColumnRef secondaryColRef = null;

         if(spec.secondaryColumn() != null && !spec.secondaryColumn().isBlank()) {
            secondaryColRef = availableColumns.get(spec.secondaryColumn());

            if(secondaryColRef == null) {
               throw new inetsoft.web.wiz.pairing.PairingException(
                  "Column not found for aggregate's secondary column: '" +
                  spec.secondaryColumn() + "'. Available columns: " + availableColumns.keySet());
            }
         }

         AggregateRef ar = new AggregateRef(colRef, formula);

         if(spec.n() != null && formula.hasN()) {
            ar.setN(spec.n());
         }

         if(secondaryColRef != null) {
            ar.setSecondaryColumn(secondaryColRef);
         }

         applyPercentageOption(ar, spec.percentageOf());

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

            AggregateRef secondary = new AggregateRef(cloned, formula);

            if(spec.n() != null && formula.hasN()) {
               secondary.setN(spec.n());
            }

            if(secondaryColRef != null) {
               secondary.setSecondaryColumn(secondaryColRef);
            }

            applyPercentageOption(secondary, spec.percentageOf());

            ainfo.addSecondaryAggregate(secondary);
         }
         else {
            // First aggregate on this column: set the alias on the shared ref from the
            // column selection — the aggregate output column is named from it.
            if(spec.alias() != null) {
               colRef.setAlias(spec.alias());
               appliedAliases.add(spec.alias());
            }

            if(!ainfo.addAggregate(ar, false)) {
               // AggregateInfo.addAggregate returns false when the same column is already a
               // group-by key -- a column cannot be both at once (matching the Composer's own
               // "Group and Aggregate" dialog's AggregatePane.verify(), which blocks this in
               // the UI before it can even be submitted). Fail loud instead of silently
               // dropping the aggregate, which previously left both a GroupRef and an
               // AggregateRef for the same column on the table and produced ungrouped,
               // unaggregated raw rows with no error.
               throw new inetsoft.web.wiz.pairing.PairingException(
                  "Column '" + spec.field() + "' is used as both a group and an aggregate. " +
                  "A column cannot be grouped by and aggregated at the same time -- remove it " +
                  "from one of groups or aggregates.");
            }
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

            // Create a new primary aggregate on the expression column. Carries over the
            // secondary column and percentage settings applied to `sref` above -- without
            // this, a second aggregate on the same column (the "third-aggregate" path) would
            // silently lose a "With"/percentage setting the first aggregate on that column
            // kept.
            AggregateRef newAgg = new AggregateRef(exprCol, sref.getSecondaryColumn(),
                                                   sref.getFormula());

            if(sref.getFormula() != null && sref.getFormula().hasN()) {
               newAgg.setN(sref.getN());
            }

            newAgg.setPercentageOption(sref.getPercentageOption());

            if(!ainfo.addAggregate(newAgg, false)) {
               throw new inetsoft.web.wiz.pairing.PairingException(
                  "Column '" + exprCol.getAttribute() + "' is used as both a group and an " +
                  "aggregate. A column cannot be grouped by and aggregated at the same time.");
            }
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
      ainfo.setCrosstab(crosstab);
      t.setAggregateInfo(ainfo);
      t.setAggregate(!ainfo.isEmpty());
   }

   /**
    * Applies an {@link AggregateSpec#percentageOf} token to an {@link AggregateRef}, matching
    * the Composer's own "Grand Total"/"Sub Total"/(blank) percentage dropdown
    * (`aggregate-pane.component.ts:73-74`). {@code null} leaves the aggregate's existing
    * percentage setting untouched, so a caller updating an unrelated field on an already-
    * percentage-of aggregate doesn't need to restate it.
    */
   private static void applyPercentageOption(AggregateRef ref, String percentageOf) {
      if(percentageOf == null) {
         return;
      }

      ref.setPercentageOption(switch(percentageOf.toLowerCase()) {
         case "none" -> XConstants.PERCENTAGE_NONE;
         case "group", "sub_total", "subtotal" -> XConstants.PERCENTAGE_OF_GROUP;
         case "grand_total", "grandtotal" -> XConstants.PERCENTAGE_OF_GRANDTOTAL;
         default -> throw new IllegalArgumentException(
            "'" + percentageOf + "' is not a percentage-of option. Accepted: none, group, " +
            "grand_total.");
      });
   }

   /**
    * Ported from {@code AggregateDialogService#getDateRangeRef}: walks a
    * {@link DataRefWrapper} chain looking for a {@link DateRangeRef}, stopping at the
    * outermost one found.
    */
   private static DateRangeRef getDateRangeRef(DataRef ref0) {
      while(ref0 instanceof DataRefWrapper) {
         if(ref0 instanceof DateRangeRef) {
            break;
         }

         ref0 = ((DataRefWrapper) ref0).getDataRef();
      }

      return ref0 instanceof DateRangeRef ? (DateRangeRef) ref0 : null;
   }

   /**
    * Ported from {@code AggregateDialogService#getInnerDateRangeRef}: walks a
    * {@link DataRefWrapper} chain looking for the innermost {@link DateRangeRef} that
    * does not itself wrap another one (relevant for chained/nested date ranges).
    */
   private static DateRangeRef getInnerDateRangeRef(DataRef ref0) {
      while(ref0 instanceof DataRefWrapper) {
         if(ref0 instanceof DateRangeRef && !(((DateRangeRef) ref0).getDataRef() instanceof DateRangeRef)) {
            break;
         }

         ref0 = ((DataRefWrapper) ref0).getDataRef();
      }

      return ref0 instanceof DateRangeRef ? (DateRangeRef) ref0 : null;
   }

   /** Ported from {@code AggregateDialogService#isAutoRangeColumn}. */
   private static boolean isAutoRangeColumn(DataRef group) {
      DateRangeRef dateRangeRef = getDateRangeRef(group);
      return dateRangeRef != null && dateRangeRef.isAutoCreate();
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
    *
    * <p>The alias is cleared on the column selection by name as well as on the
    * AggregateInfo's own ref, because the two are only the same object until something
    * clones the AggregateInfo and installs the copy. A preview does exactly that --
    * {@code WorksheetPreviewService} snapshots and restores the AggregateInfo around
    * RUNTIME_MODE execution, since {@code replaceVariables} rewrites it in place -- and
    * {@link AggregateInfo#clone()} deep-clones every DataRef. Clearing only the
    * AggregateInfo's ref then nulls an alias nothing reads, and the column the model
    * reports keeps a name like {@code SUM_REVENUE} over un-aggregated rows for good.
    * Live-confirmed 2026-08-25 (L2 Finding 20): the alias survived a clear only when a
    * preview had run in between.</p>
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

      ColumnSelection cs = t.getColumnSelection(false);

      for(int i = 0; i < old.getAggregateCount(); i++) {
         AggregateRef ar = old.getAggregate(i);

         if(ar.getDataRef() instanceof ColumnRef cr &&
            (ownAliases == null || ownAliases.contains(cr.getAlias())))
         {
            String alias = cr.getAlias();
            cr.setAlias(null);
            clearAliasInSelection(cs, cr, alias);
         }
      }
   }

   /**
    * Clears {@code alias} from the column selection's own ref for the same base column.
    *
    * <p>A no-op in the ordinary case, where the column selection holds the very ref just
    * cleared. It earns its place when the two have been separated by a clone -- matching
    * on the base attribute AND the exact alias, so it cannot reach a different column
    * that merely shares one of the two.</p>
    */
   private static void clearAliasInSelection(ColumnSelection cs, ColumnRef cleared,
                                             String alias)
   {
      if(cs == null || alias == null || alias.isEmpty()) {
         return;
      }

      for(int i = 0; i < cs.getAttributeCount(); i++) {
         if(cs.getAttribute(i) instanceof ColumnRef cr && cr != cleared &&
            alias.equals(cr.getAlias()) &&
            Objects.equals(cr.getAttribute(), cleared.getAttribute()))
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
   // Snapshot column guards
   // =========================================================================

   /**
    * Refuses adding a data column to a snapshot embedded table.
    *
    * <p>Shared by {@code add_column} and {@code insert_column}, which live in different classes
    * but strand a snapshot the same way -- see {@link #snapshotColumnMessage}.
    *
    * @param t     the target table
    * @param table the assembly name, for the message
    * @param op    the op name to quote back, e.g. {@code "add_column"}
    * @throws inetsoft.web.wiz.pairing.PairingException if {@code t} is a snapshot
    */
   public static void assertSnapshotAllowsColumnAdd(TableAssembly t, String table, String op)
      throws inetsoft.web.wiz.pairing.PairingException
   {
      if(!(t instanceof SnapshotEmbeddedTableAssembly)) {
         return;
      }

      throw new inetsoft.web.wiz.pairing.PairingException(
         snapshotColumnMessage(op, table) +
         " An expression column is the exception, because it lives only in the column selection " +
         "and never in the data: add_expression_column works on a snapshot. For a column that " +
         "has to hold data, re-import the source file with import_csv_table or " +
         "import_excel_table, which builds a plain embedded table.");
   }

   /**
    * Refuses removing a snapshot embedded table's data column, while allowing an expression one.
    *
    * <p>The carve-out is not arbitrary. An expression column exists only in the
    * {@link ColumnSelection}, never in the data, so removing it touches nothing that could be
    * lost. That is also exactly where the Composer draws the line: its
    * {@code updateCanRemoveSelectedHeaders()} permits removal on a snapshot only when every
    * selected column is an expression.
    *
    * @param t      the target table
    * @param table  the assembly name, for the message
    * @param column the column name, for the message
    * @param target the column being removed; {@code null} is treated as not an expression
    * @throws inetsoft.web.wiz.pairing.PairingException if the column is a snapshot's data column
    */
   public static void assertSnapshotAllowsColumnRemove(TableAssembly t, String table,
                                                       String column, DataRef target)
      throws inetsoft.web.wiz.pairing.PairingException
   {
      if(!(t instanceof SnapshotEmbeddedTableAssembly) || isExpressionColumn(target)) {
         return;
      }

      throw new inetsoft.web.wiz.pairing.PairingException(
         snapshotColumnMessage("remove_column", table) +
         " Only an expression column can be removed from a snapshot -- \"" + column + "\" is a " +
         "data column, and the Composer refuses this the same way. To drop a data column, " +
         "re-import the source file with import_csv_table or import_excel_table, which builds a " +
         "plain embedded table.");
   }

   /**
    * Whether {@code ref} is an expression column, i.e. one with no backing data column.
    */
   private static boolean isExpressionColumn(DataRef ref) {
      return ref instanceof ColumnRef cr && cr.isExpression();
   }

   /**
    * The half of a snapshot column refusal that is the same whichever op asked.
    *
    * <p>Says what the caller cannot see: the op would half-apply. A snapshot's data lives in a
    * swapped-out data file, every {@link inetsoft.uql.util.XEmbeddedTable} mutator is
    * copy-and-swap, and {@link SnapshotEmbeddedTableAssembly#getEmbeddedData()} returns a fresh
    * wrapper on each call -- so the column-selection change would persist while the data-layer
    * change was discarded, leaving the table one column out of step with its own data and no
    * error to show for it.
    */
   private static String snapshotColumnMessage(String op, String table) {
      return op + " is not supported on a snapshot embedded table: " + table +
         ". Every table imported through the Composer's own file-import wizard is a snapshot, and " +
         "its data cannot be restructured in place -- the column would change while the data " +
         "behind it did not. read_worksheet_model reports these tables as type " +
         "\"EMBEDDED_SNAPSHOT\".";
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
    * <p>If a {@link SortRef} for {@code field} already exists, its direction is updated
    * <b>in place</b> rather than removed and re-added, preserving this column's existing sort
    * priority (position among the other sorted columns) -- matching the native sort editor
    * ({@code sort-column-editor.component.ts:154-157}), which mutates {@code sortRefs[index]
    * .order} directly and never reorders the row on a direction-only change. A remove-then-
    * re-add sequence would always move the column to last priority instead, a semantic
    * divergence from the native dialog that has nothing to do with the direction requested.</p>
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

      int order = "DESC".equalsIgnoreCase(direction) ? XConstants.SORT_DESC : XConstants.SORT_ASC;
      SortRef existing = null;

      for(SortRef sr : si.getSorts()) {
         if(field.equals(sr.getName()) || field.equals(sr.getAttribute())) {
            existing = sr;
            break;
         }
      }

      if(existing != null) {
         existing.setOrder(order);
      }
      else {
         DataRef ref = resolveField(t, field);
         SortRef sr = new SortRef(ref);
         sr.setOrder(order);
         si.addSort(sr);
      }

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
    * @param choiceQuery a {@code "table]:[column"} browse-source marker applied to any
    *                  {@code $(name)} variable reference among {@code values}, matching the
    *                  native condition dialog's "Use list" checkbox
    *                  ({@code ConditionUtil.java:239-242}'s {@code setChoiceQuery}). Without
    *                  this, a variable reference is still stored correctly, but the Composer's
    *                  own condition dialog would render it without the pre-fetched browse list a
    *                  human checking "Use list" would have attached. {@code null} leaves the
    *                  variable's choice source unset.
    */
   public record ConditionSpec(String field, String operation,
                               List<String> values, boolean negated,
                               String type, List<ConditionValueSpec> valueSpecs,
                               String choiceQuery) {
      /**
       * Compact form for callers that only need FIELD/EXPRESSION-typed values, with no
       * variable choice-list marker.
       */
      public ConditionSpec(String field, String operation, List<String> values,
                           boolean negated, String type, List<ConditionValueSpec> valueSpecs)
      {
         this(field, operation, values, negated, type, valueSpecs, null);
      }

      /**
       * Compact form for callers that only need literal/variable values, matching
       * {@link RankingSpec}'s own trailing-optional-field pattern -- most conditions have no
       * FIELD/EXPRESSION-typed value or choice-list marker, and this keeps every existing
       * caller unchanged.
       */
      public ConditionSpec(String field, String operation, List<String> values,
                           boolean negated, String type)
      {
         this(field, operation, values, negated, type, null, null);
      }
   }

   /**
    * Describes one condition value that is neither a literal nor a {@code $(name)} variable
    * reference -- a comparison against another column (FIELD) or a computed expression
    * (EXPRESSION), matching the native condition dialog's own value-type discriminator
    * ({@code ConditionValueModel.getType()}, see
    * {@code pre-post-condition-item-pane-provider.ts:90-128} for which operators offer which
    * types, and {@code ConditionUtil.fromModelToConditionList}
    * (`core/src/main/java/inetsoft/web/composer/model/condition/ConditionUtil.java:212-223`) for
    * how each is converted -- this mirrors that conversion exactly, so a value built here is
    * indistinguishable to the query engine from one built by the native dialog).
    *
    * <p>A {@link ConditionSpec} may combine {@code values} (literals/variables) and
    * {@code valueSpecs} (field/expression) in the same condition -- {@code values} entries are
    * appended first, then {@code valueSpecs} entries -- for a mixed multi-value condition (e.g.
    * a {@code ONE_OF} list combining a literal with a computed expression), the same way the
    * native dialog's multi-value editor allows.
    *
    * <p>SUBQUERY is intentionally not covered here: it requires resolving a whole second
    * {@link TableAssembly} as the subquery source, a materially larger mechanism than a single
    * value conversion -- left for separate work.
    *
    * @param valueType      {@code "field"} or {@code "expression"} (case-insensitive)
    * @param field          the column name to compare against; required when {@code valueType}
    *                       is {@code "field"}, resolved the same way {@code ConditionSpec.field}
    *                       itself is (private selection for pre-aggregate conditions, or
    *                       {@link AggregateInfo}-then-public-selection for post-aggregate)
    * @param expression     the expression body; required when {@code valueType} is
    *                       {@code "expression"}
    * @param expressionType {@code "sql"} or {@code "js"}/{@code "javascript"} (case-insensitive);
    *                       defaults to {@code "sql"} when {@code null}, matching
    *                       {@link ExpressionValue}'s own SQL/JavaScript duality
    */
   public record ConditionValueSpec(String valueType, String field, String expression,
                                    String expressionType) {}

   /**
    * Converts one {@link ConditionValueSpec} into the object the engine stores, mirroring
    * {@code ConditionUtil.fromModelToConditionList}'s FIELD/EXPRESSION branches.
    *
    * @param t    the table the value is resolved against (FIELD only)
    * @param post {@code true} to resolve a FIELD value the same way post-aggregate condition
    *             fields are resolved (see {@link #resolveField})
    */
   private static Object conditionValue(TableAssembly t, boolean post, ConditionValueSpec spec) {
      if(spec == null || spec.valueType() == null || spec.valueType().isBlank()) {
         throw new IllegalArgumentException(
            "A condition valueSpec needs a 'valueType' of 'field' or 'expression'.");
      }

      return switch(spec.valueType().toLowerCase()) {
         case "field" -> {
            if(spec.field() == null || spec.field().isBlank()) {
               throw new IllegalArgumentException(
                  "A 'field' valueSpec needs a non-blank 'field' naming the column to compare " +
                  "against.");
            }

            yield resolveField(t, spec.field(), post);
         }
         case "expression" -> {
            if(spec.expression() == null || spec.expression().isBlank()) {
               throw new IllegalArgumentException(
                  "An 'expression' valueSpec needs a non-blank 'expression'.");
            }

            ExpressionValue expr = new ExpressionValue();
            expr.setExpression(spec.expression());
            expr.setType("js".equalsIgnoreCase(spec.expressionType()) ||
                          "javascript".equalsIgnoreCase(spec.expressionType())
                          ? ExpressionValue.JAVASCRIPT : ExpressionValue.SQL);
            yield expr;
         }
         default -> throw new IllegalArgumentException(
            "'" + spec.valueType() + "' is not a condition value type. Accepted: field, " +
            "expression.");
      };
   }

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

            if(op == XCondition.DATE_IN) {
               XCondition resolved = resolveDateInCondition(t.getWorksheet(),
                  spec.values() != null && !spec.values().isEmpty() ? spec.values().get(0) : null);
               resolved.setNegated(negate);
               cl.append(new ConditionItem(ref, resolved, node.level()));
            }
            else {
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
                     // Shared with addFilter rather than parsed a second time here. The local copy
                     // had no lower bound on the name, so "$()" became a variable named "" -- one
                     // nothing can ever resolve, reported as ok. Typing was NOT the difference,
                     // though the bare constructor reads as though it would be: Condition.addValue
                     // routes every value through convertType, whose UserVariable branch
                     // (Condition:2129-2149) sets the type node from the condition's own type, so
                     // the variable was typed from the column either way.
                     Object value = conditionValue(dtype, v);

                     if(value instanceof UserVariable uv &&
                        spec.choiceQuery() != null && !spec.choiceQuery().isBlank())
                     {
                        uv.setChoiceQuery(spec.choiceQuery());
                     }

                     c.addValue(value);
                  }
               }

               if(spec.valueSpecs() != null) {
                  for(ConditionValueSpec vs : spec.valueSpecs()) {
                     c.addValue(conditionValue(t, post, vs));
                  }
               }

               cl.append(new ConditionItem(ref, c, node.level()));
            }
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
    * @param field     column to rank — a group/dimension column or an aggregate column
    * @param n         number of rows (top/bottom N) — an {@link Integer}, a numeric
    *                  {@link String}, or a {@code "$(variableName)"} reference to bind the
    *                  count to a worksheet variable (same syntax filter condition values
    *                  support). See {@link RankingCondition#setN}.
    * @param operation {@code "TOP_N"} or {@code "BOTTOM_N"}
    * @param groupOthers {@code true} to group remaining rows as "Others"
    * @param of        optional aggregate column to rank {@code field} by (e.g. {@code field}
    *                  {@code ="CITY"}, {@code of="CUSTOMER_COUNT"} ranks CITY rows by the
    *                  CUSTOMER_COUNT aggregate). Mirrors the "Of" picker in the composer's own
    *                  ranking-condition editor. Only meaningful when {@code field} is a group
    *                  column; omit when {@code field} is itself the aggregate to rank by.
    */
   public record RankingSpec(String field, Object n, String operation,
                             boolean groupOthers, String of) {
      /**
       * Compact form for callers that don't need {@code of} (e.g. ranking directly by an
       * aggregate or group column with no separate "of" value).
       */
      public RankingSpec(String field, Object n, String operation, boolean groupOthers) {
         this(field, n, operation, groupOthers, null);
      }
   }

   /**
    * Maps a ranking operation token to its {@link XCondition} constant.
    *
    * <p>An absent operation still means {@code TOP_N} -- the dropdown's own default -- but a
    * <i>supplied</i> value that is neither spelling is refused instead of silently collapsing to
    * {@code TOP_N}, matching {@link #parseOperation}'s "absent defaults, supplied-and-unknown
    * throws" rule. Before this, anything that was not literally {@code "BOTTOM_N"} (any case, a
    * typo, {@code null} handled separately above) became {@code TOP_N} with no error -- a ranking
    * requested as "bottom 10" would silently return the top 10 instead, with {@code ok:true} and
    * no signal anything was wrong.
    */
   private static int parseRankingOperation(String operation) {
      if(operation == null || operation.isBlank()) {
         return XCondition.TOP_N;
      }

      return switch(operation.toUpperCase()) {
         case "TOP_N" -> XCondition.TOP_N;
         case "BOTTOM_N" -> XCondition.BOTTOM_N;
         default -> throw new IllegalArgumentException(
            "'" + operation + "' is not a ranking operation. Accepted: TOP_N, BOTTOM_N. Omit " +
            "the operation entirely to mean TOP_N.");
      };
   }

   /**
    * Sets a ranking condition on the table, replacing any existing ranking with this single
    * one. See {@link #setRankings} to establish more than one independent ranking in a single
    * call instead of the second replacing the first.
    */
   public static void setRanking(TableAssembly t, RankingSpec spec) {
      if(spec == null) {
         t.setRankingConditionList(new ConditionList());
         return;
      }

      ConditionList cl = new ConditionList();
      cl.append(buildRankingConditionItem(t, spec));
      t.setRankingConditionList(cl);
   }

   /**
    * Sets the table's whole ranking condition list from {@code specs}, in order — the plural
    * counterpart to {@link #setRanking}, for establishing more than one independent ranked
    * field (e.g. top 3 customers by revenue AND top 3 employees by order count) in a single
    * call. {@code null} or empty clears all ranking, matching {@code setRanking(t, null)}.
    */
   public static void setRankings(TableAssembly t, List<RankingSpec> specs) {
      if(specs == null || specs.isEmpty()) {
         t.setRankingConditionList(new ConditionList());
         return;
      }

      ConditionList cl = new ConditionList();
      boolean first = true;

      for(RankingSpec spec : specs) {
         // ConditionList's internal representation alternates condition/junction/condition/...
         // (ConditionList.isConditionItem checks slot parity) -- the same shape
         // AssemblyConditionDialogService's own ranking-model converter produces via the
         // shared fromModelToConditionList, so an AND junction between each ranking keeps
         // this a well-formed list the native dialog can read back, not just a StyleBI-Wiz-
         // only convention.
         if(!first) {
            cl.append(new JunctionOperator(JunctionOperator.AND, 0));
         }

         cl.append(buildRankingConditionItem(t, spec));
         first = false;
      }

      t.setRankingConditionList(cl);
   }

   /**
    * Builds the single {@link ConditionItem} one {@link RankingSpec} contributes to a ranking
    * condition list — shared by {@link #setRanking} (a one-item list) and {@link #setRankings}
    * (an N-item list), so the per-item resolution/validation logic is written exactly once.
    */
   private static ConditionItem buildRankingConditionItem(TableAssembly t, RankingSpec spec) {
      int op = parseRankingOperation(spec.operation());

      // The RankingCondition itself always runs after aggregation (see
      // AssetQuery.getRankingTableLens), so prefer AggregateInfo — matching either an
      // aggregate (e.g. "Sum(Total)") or a group-by dimension (e.g. "Employee") — over a
      // plain column, otherwise a rank on "Sum(Total)" binds to the raw "Total" column
      // instead of the aggregate ref. Unlike set_post_conditions, the fallback stays on
      // the PRIVATE column selection (not the public one used by resolveField(t, f, true))
      // so ranking on a non-aggregated table still finds columns hidden via
      // set_column_visibility.
      DataRef ref = resolveAggregateOrGroupField(t, spec.field());

      if(ref == null) {
         ref = resolveField(t, spec.field(), false);
      }

      inetsoft.uql.asset.RankingCondition rc = new inetsoft.uql.asset.RankingCondition();
      rc.setOperation(op);

      if(!rc.setN(spec.n())) {
         throw new IllegalArgumentException(
            "'n' must be an integer or a \"$(variableName)\" reference, got: " + spec.n());
      }

      rc.setGroupOthers(spec.groupOthers());

      if(spec.of() != null && !spec.of().isBlank()) {
         // 'of' names an aggregate output (e.g. "Sum(Total)"); search AggregateInfo's
         // aggregates first so it resolves to the AggregateRef, not the private column
         // selection's raw base column of the same name (see resolveField's post-aggregate
         // lookup note above).
         DataRef ofRef = resolveField(t, spec.of(), true);

         // Matches the Composer's own Top N/Bottom N editor (top-n-editor.component.ts),
         // which excludes boolean-typed aggregates from the "Of" dropdown entirely -- a
         // boolean has no meaningful ranking order beyond true/false, so a boolean "of"
         // silently produces a degenerate two-bucket rank rather than the numeric ranking
         // the operation implies.
         if(XSchema.BOOLEAN.equals(ofRef.getDataType())) {
            throw new IllegalArgumentException(
               "'" + spec.of() + "' is a boolean column and cannot be used as ranking's " +
               "'of' aggregate -- matching the Composer's own Top N/Bottom N editor, which " +
               "excludes boolean-typed aggregates from the \"Of\" picker.");
         }

         rc.setDataRef(ofRef);
      }

      return new ConditionItem(ref, rc, 0);
   }

   /**
    * Describes a variable's enumerated "Values" picker. The Composer's own Variable dialog
    * offers two mutually exclusive ways to populate it: a fixed embedded list ({@code values},
    * optionally paired with {@code labels}), or a query against an existing worksheet table's
    * columns ({@code table} + {@code labelColumn} + {@code valueColumn}). Specifying both
    * {@code values} and {@code table} in the same spec is rejected rather than silently
    * preferring one.
    *
    * @param values       embedded picker values; empty clears the picker back to free-form,
    *                     {@code null} leaves any existing embedded list untouched. Mutually
    *                     exclusive with {@code table}.
    * @param labels       display labels parallel to {@code values}; must be the same length
    *                     if given, otherwise {@code values} double as their own labels
    * @param table        worksheet table supplying picker rows (query mode); {@code null}
    *                     leaves any existing query source untouched. Mutually exclusive with
    *                     {@code values}.
    * @param labelColumn  column on {@code table} supplying each row's display label; required
    *                     when {@code table} is given
    * @param valueColumn  column on {@code table} supplying each row's underlying value;
    *                     required when {@code table} is given
    * @param displayStyle {@code "none"}, {@code "combobox"}, {@code "list"}, {@code "radio"},
    *                     or {@code "checkboxes"} — matches the Composer's own picker style
    *                     choices. {@code null} leaves the existing style untouched unless
    *                     {@code values} or {@code table} was also given this call, in which
    *                     case it defaults to {@code "combobox"}.
    */
   public record VariableChoicesSpec(List<String> values, List<String> labels, String table,
                                     String labelColumn, String valueColumn,
                                     String displayStyle) {}

   /**
    * Populates a variable's enumerated value list from a {@link VariableChoicesSpec}, matching
    * {@link UserVariable#setValues}/{@link UserVariable#setChoices} (embedded mode) or
    * {@link AssetVariable#setTableName}/{@link AssetVariable#setLabelAttribute}/
    * {@link AssetVariable#setValueAttribute} (query mode). Mirrors
    * {@code VariableAssemblyDialogService.convertModelToAssetVariable}, the Composer dialog's
    * own implementation of the same conversion.
    *
    * <p>Also sets {@link AssetVariable#setDisplayStyle}: unlike the base
    * {@link UserVariable#getDisplayStyle}, {@link AssetVariable} does not auto-derive its
    * display style from whether choices/values are present, it only returns whatever was last
    * explicitly stored — so a picker populated without this would carry values the Composer's
    * own Variable UI never renders a control for.
    *
    * @param ws   the worksheet {@code spec.table()} is resolved against (query mode only)
    * @param var  the variable to populate; if its type node is already set, that determines
    *             how each entry in embedded {@code values} is typed, otherwise entries are
    *             typed as {@link XSchema#STRING}
    * @param spec the picker to apply, or {@code null} to leave the variable's picker untouched
    */
   public static void applyVariableChoices(Worksheet ws, AssetVariable var,
                                           VariableChoicesSpec spec)
   {
      if(spec == null) {
         return;
      }

      boolean hasValues = spec.values() != null;
      boolean hasTable = spec.table() != null;

      if(hasValues && hasTable) {
         throw new IllegalArgumentException(
            "'values' and 'table' are mutually exclusive ways to populate a variable's " +
            "picker -- specify one or the other, not both.");
      }

      // Parsed up front, before any mutation below: applyOnRuntime mutates the live worksheet
      // directly with no rollback on a thrown exception (see WorksheetEditService.apply), so an
      // invalid displayStyle discovered only after values/table were already applied would
      // leave the variable half-updated -- state committed, call still reported as failed.
      Integer explicitStyle = spec.displayStyle() != null
         ? parseVariableDisplayStyle(spec.displayStyle()) : null;

      if(hasValues) {
         applyEmbeddedVariableChoices(var, spec.values(), spec.labels());
      }
      else if(hasTable) {
         applyQueryVariableChoices(ws, var, spec.table(), spec.labelColumn(), spec.valueColumn());
      }

      if(explicitStyle != null) {
         var.setDisplayStyle(explicitStyle);
         var.setMultipleSelection(explicitStyle == UserVariable.LIST);
      }
      else if(hasValues || hasTable) {
         // A picker source was (re)supplied but no explicit style was given -- default to a
         // single-select combobox unless the source ended up empty (no picker at all), which
         // must read back as NONE or the Composer UI shows a combobox with nothing in it.
         boolean hasPicker = var.getTableName() != null ||
            var.getChoices() != null && var.getValues() != null;
         var.setDisplayStyle(hasPicker ? UserVariable.COMBOBOX : UserVariable.NONE);
         var.setMultipleSelection(false);
      }
   }

   private static void applyEmbeddedVariableChoices(AssetVariable var, List<String> values,
                                                     List<String> labels)
   {
      // Validated before any mutation below -- see the comment on applyVariableChoices'
      // displayStyle parse for why a validation failure must never land after a partial write.
      if(!values.isEmpty() && labels != null && !labels.isEmpty() &&
         labels.size() != values.size())
      {
         throw new IllegalArgumentException(
            "'labels' must have the same number of entries as 'values' (" + values.size() +
            "), got " + labels.size() + ".");
      }

      String type = var.getTypeNode() != null ? var.getTypeNode().getType() : XSchema.STRING;
      Object[] typedValues = new Object[values.size()];

      for(int i = 0; i < values.size(); i++) {
         String v = values.get(i);
         Object typed = Tool.getData(type, v);

         // Tool.getData silently returns null for an entry that doesn't parse as 'type' (e.g.
         // "abc" for an integer variable) instead of throwing -- fail loud here instead,
         // matching every other validation in this method, rather than writing a value whose
         // label ('v') and underlying null value end up silently out of sync.
         if(typed == null && v != null && !v.isEmpty()) {
            throw new IllegalArgumentException(
               "'values' entry \"" + v + "\" is not a valid " + type + " value.");
         }

         typedValues[i] = typed;
      }

      // Switching to embedded mode must clear any leftover query-mode source -- otherwise
      // AssetVariable carries both a non-null tableName AND populated choices/values, and the
      // Composer's own read path (VariableAssemblyDialogService) treats a non-null tableName as
      // query mode unconditionally, silently ignoring the embedded list this call just set.
      var.setTableName(null);
      var.setLabelAttribute(null);
      var.setValueAttribute(null);

      if(values.isEmpty()) {
         var.setChoices(null);
         var.setValues(null);
         return;
      }

      List<String> effectiveLabels = labels != null && !labels.isEmpty() ? labels : values;

      // UserVariable defaults sortValue to true, which silently re-sorts choices/values
      // alphabetically by label the moment both arrays are set -- discarding the caller's
      // intended order with no error. VariableAssemblyDialogService (the Composer's own
      // Variable dialog) always disables it for the same reason; match that here.
      var.setSortValue(false);
      var.setChoices(effectiveLabels.toArray());
      var.setValues(typedValues);
   }

   private static void applyQueryVariableChoices(Worksheet ws, AssetVariable var, String table,
                                                  String labelColumn, String valueColumn)
   {
      if(labelColumn == null || valueColumn == null) {
         throw new IllegalArgumentException(
            "'labelColumn' and 'valueColumn' are both required when 'table' is given.");
      }

      Assembly a = ws.getAssembly(table);

      if(!(a instanceof TableAssembly t)) {
         throw new IllegalArgumentException("Worksheet table not found: " + table);
      }

      DataRef labelRef = resolveField(t, labelColumn);
      DataRef valueRef = resolveField(t, valueColumn);

      if(labelRef == null) {
         throw new IllegalArgumentException(
            "'labelColumn' not found on table '" + table + "': " + labelColumn);
      }

      if(valueRef == null) {
         throw new IllegalArgumentException(
            "'valueColumn' not found on table '" + table + "': " + valueColumn);
      }

      checkNoCircularVariableDependency(ws, var, t);

      // Switching to query mode must clear any leftover embedded list for the same reason
      // applyEmbeddedVariableChoices clears the table name -- the two sources are mutually
      // exclusive in how the Composer's own dialog reads a variable back.
      var.setChoices(null);
      var.setValues(null);
      var.setTableName(table);
      var.setLabelAttribute(labelRef);
      var.setValueAttribute(valueRef);
   }

   /**
    * Rejects a query-mode picker source that would create a circular dependency:
    * {@code table} (or anything it depends on -- mirrors, joins, concatenations, recursively,
    * via {@link AssetUtil#getDependedAssemblies}) carrying a filter or ranking condition that
    * reads {@code $(var.getName())}. Computing the picker's own values would then require the
    * variable's value first, which is exactly what the picker exists to supply.
    *
    * <p>Confirmed live (2026-08-26): mirroring a table whose own ranking condition read
    * {@code $(TopN)}, then pointing {@code $(TopN)}'s picker at that mirror, silently built
    * this cycle -- a query-mode variable picker is a worksheet-chat-only capability with no
    * Composer dialog equivalent to catch it by construction, so it needs its own guard here.
    */
   private static void checkNoCircularVariableDependency(Worksheet ws, AssetVariable var,
                                                          TableAssembly table)
   {
      String varName = var.getName();
      Assembly[] deps = AssetUtil.getDependedAssemblies(ws, table, true);

      for(Assembly a : deps) {
         if(!(a instanceof TableAssembly t)) {
            continue;
         }

         boolean referenced = referencesVariable(t.getPreConditionList(), varName) ||
            referencesVariable(t.getPostConditionList(), varName) ||
            referencesVariable(t.getRankingConditionList(), varName);

         if(referenced) {
            String via = t == table ? "" : " (depended on by '" + table.getName() + "')";
            throw new IllegalArgumentException(
               "'table' creates a circular dependency: '" + t.getName() + "'" + via +
               " has a condition that reads $(" + varName + ") -- computing the picker's own " +
               "values would require the variable's value first. Point the picker at a table " +
               "that does not depend on this variable, e.g. an independent copy with its own " +
               "conditions cleared, not a mirror of the filtered table.");
         }
      }
   }

   /**
    * {@code true} if any condition in {@code wrapper} reads {@code $(varName)}.
    */
   private static boolean referencesVariable(ConditionListWrapper wrapper, String varName) {
      if(wrapper == null || wrapper.isEmpty()) {
         return false;
      }

      int size = wrapper.getConditionSize();

      for(int i = 0; i < size; i++) {
         if(!wrapper.isConditionItem(i)) {
            continue;
         }

         ConditionItem item = wrapper.getConditionItem(i);
         XCondition xc = item == null ? null : item.getXCondition();

         if(xc == null) {
            continue;
         }

         for(UserVariable uvar : xc.getAllVariables()) {
            if(varName.equals(uvar.getName())) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Maps a display-style token to its {@link UserVariable} constant.
    */
   private static int parseVariableDisplayStyle(String style) {
      return switch(style.toLowerCase()) {
         case "none" -> UserVariable.NONE;
         case "combobox" -> UserVariable.COMBOBOX;
         case "list" -> UserVariable.LIST;
         case "radio", "radio_buttons" -> UserVariable.RADIO_BUTTONS;
         case "checkboxes" -> UserVariable.CHECKBOXES;
         default -> throw new IllegalArgumentException(
            "'" + style + "' is not a display style. Accepted: none, combobox, list, radio, " +
            "checkboxes.");
      };
   }

   /**
    * Describes one hand-authored "Join With" lookup level for a GENERIC/CUSTOM REST-JSON
    * datasource's {@code add_table} binding (see {@code TabularEndpointBindingSupport
    * #applyCustomLookupChain}), as opposed to {@code lookup} (a named connector's pre-built
    * lookup chain, selected by endpoint name only).
    *
    * @param url          URL template for this level. Must contain the literal placeholder
    *                     {@code {paramN}} (1-indexed by this level's position in the chain,
    *                     e.g. {@code {param1}} for the first level) so the id extracted via
    *                     {@code jsonPath}/{@code key} from the PARENT level's row lands in the
    *                     request StyleBI issues for this level.
    * @param jsonPath     selects the parent row's array/entity to iterate for this level.
    * @param key          extracts each item's id from that {@code jsonPath}.
    * @param ignoreBaseUrl {@code true} if {@code url} is a full URL rather than a suffix
    *                     appended to the datasource's base URL; omit/{@code null} for
    *                     {@code false}.
    */
   public record CustomLookupSpec(String url, String jsonPath, String key, Boolean ignoreBaseUrl) {}

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
      if(post) {
         DataRef aggregateRef = resolveAggregateOrGroupField(t, field);

         if(aggregateRef != null) {
            return aggregateRef;
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
    * Returns {@code true} if {@code field} resolves to a real column, alias, aggregate ref, or
    * group ref on {@code t} -- i.e. {@link #resolveField} would return something other than its
    * unresolvable {@code new AttributeRef(null, field)} fallback.
    *
    * <p>Mirrors the native UI's closed field picker: a human editing the same condition/sort can
    * only ever submit a name this returns {@code true} for, because the dropdown is populated
    * from exactly the same {@link ColumnSelection}/{@link AggregateInfo} sources this checks.
    * A caller driving the agent path with a free-text field name has no such structural limit,
    * so this is the guard that gives it one.
    *
    * @param post {@code true} to also match against {@link AggregateInfo} (post-aggregate/HAVING
    *             conditions and ranking), {@code false} to check the private column selection only
    */
   static boolean fieldExists(TableAssembly t, String field, boolean post) {
      if(field == null || field.isBlank()) {
         return false;
      }

      if(post && resolveAggregateOrGroupField(t, field) != null) {
         return true;
      }

      ColumnSelection cs = t.getColumnSelection(post);

      if(cs == null) {
         return false;
      }

      if(cs.getAttribute(field) != null) {
         return true;
      }

      for(int i = 0; i < cs.getAttributeCount(); i++) {
         DataRef ref = cs.getAttribute(i);

         if(ref instanceof ColumnRef cr && field.equals(cr.getAlias())) {
            return true;
         }
      }

      return false;
   }

   /**
    * Same existence check as {@link #fieldExists}, but for a ranking condition's own field
    * resolution order in {@link #setRanking}: an {@link AggregateInfo} match (aggregate or
    * group-by dimension) first, then the table's PRIVATE column selection -- never the public
    * one, so ranking on a non-aggregated table still recognizes columns hidden via
    * {@code set_column_visibility}.
    */
   static boolean rankingFieldExists(TableAssembly t, String field) {
      return resolveAggregateOrGroupField(t, field) != null || fieldExists(t, field, false);
   }

   /**
    * Matches {@code field} against the table's {@link AggregateInfo} — an aggregate ref (by
    * alias, view, or base attribute) or a group-by dimension (by attribute or alias).
    * Returns {@code null} if the table isn't aggregated or nothing matches, leaving the
    * caller to fall back to a plain column lookup.
    */
   private static DataRef resolveAggregateOrGroupField(TableAssembly t, String field) {
      if(field == null) {
         return null;
      }

      AggregateInfo ainfo = t.getAggregateInfo();

      if(ainfo == null || ainfo.isEmpty()) {
         return null;
      }

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

      return null;
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

   /**
    * Turns one condition value into what the engine stores: a {@link UserVariable} for a
    * {@code $(name)} reference, otherwise the value coerced to the column's type.
    *
    * <p>Without the first case a variable reference is destroyed on the way in. The condition is
    * typed from the resolved column, so on a numeric column {@code "$(Floor)"} does not parse and
    * lands as {@code 0.0} -- not even the variable's default. The filter still reads as valid and
    * still returns rows, so a parameterised filter silently becomes a constant one.
    *
    * <p>{@code AbstractCondition.getObject(type, value, true)} is what the engine itself uses, and
    * {@code Condition.toString} renders such a value back as {@code $(name)}, so the round-trip is
    * the same one the Composer's own condition dialog performs.
    */
   private static Object conditionValue(String dtype, String value) {
      if(value != null && value.startsWith("$(") && value.endsWith(")") && value.length() > 3) {
         return AbstractCondition.getObject(dtype, value.substring(2, value.length() - 1), true);
      }

      return value;
   }

   /**
    * Resolves a {@code DATE_IN} clause's value -- a named range such as {@code "Last month"} --
    * into the {@link XCondition} that actually carries date-range semantics, mirroring
    * {@code ConditionUtil.fromModelToConditionList}'s {@code DATE_IN} branch: the built-in ranges
    * from {@code dateConditions.xml} ({@link DateCondition#getBuiltinDateConditions()}) are tried
    * first by exact name, then a worksheet-level {@link DateRangeAssembly} of that name.
    *
    * <p>This is deliberately eager rather than left to query time. {@code addFilter}/
    * {@code setConditions} built a plain {@link Condition} with the literal string as its value;
    * that string is only ever rescued later by {@code Condition.toSqlCondition(boolean, String)},
    * which (a) has no {@code DateRangeAssembly} fallback at all -- a user-defined named range never
    * resolves through it -- and (b) on any other unmatched/typo'd name silently returns
    * {@code toNullSqlCondition(1)}, a hardcoded "one year ago" range, with no error. Resolving here
    * closes both gaps and, as a side effect, keeps the non-SQL-mergeable evaluate() path correct
    * too: a {@link DateCondition} is not a {@link Condition} subclass, so it can never reach
    * {@code Condition.evaluate()}'s separate, hardcoded {@code isInDateRange} name table.
    *
    * @param ws    the worksheet to check for a matching {@link DateRangeAssembly}; may be
    *              {@code null} if no worksheet is available, in which case only built-in ranges
    *              are checked
    * @param value the named range, e.g. {@code "Last month"}
    * @throws IllegalArgumentException if {@code value} is blank or matches neither a built-in
    *                                  range nor a worksheet {@link DateRangeAssembly}
    */
   private static XCondition resolveDateInCondition(Worksheet ws, String value) {
      if(value == null || value.isBlank()) {
         throw new IllegalArgumentException(
            "'date_in' needs a value naming a built-in range (e.g. \"Last month\") or a worksheet " +
            "date-range assembly -- call list_condition_date_ranges for the exact names.");
      }

      for(DateCondition dc : DateCondition.getBuiltinDateConditions()) {
         if(dc.getName().equalsIgnoreCase(value)) {
            return dc.clone();
         }
      }

      if(ws != null && ws.getAssembly(value) instanceof DateRangeAssembly dra) {
         return dra.getDateRange().clone();
      }

      throw new IllegalArgumentException(
         "'" + value + "' is not a known date range. Call list_condition_date_ranges for the exact " +
         "built-in names (e.g. \"Last month\"), or name a worksheet date-range assembly.");
   }

   /**
    * Maps an operator token to its XCondition constant.
    *
    * <p>An absent operator still means equals -- callers such as add_named_group leave it out on
    * purpose. An operator that was <i>supplied</i> and is not recognised is refused instead,
    * because defaulting it to equals silently returns a different data set: a filter written as
    * greater-than becomes an equality test, the call answers ok, and nothing on screen marks it.
    */
   static int parseOperation(String operation) {
      if(operation == null || operation.isBlank()) {
         return XCondition.EQUAL_TO;
      }

      return switch(operation.toUpperCase().replace(' ', '_')) {
         // "=" had no case of its own -- it reached EQUAL_TO through the default branch, which is
         // exactly why that branch could not simply be turned into a refusal.
         case "=", "EQUAL_TO", "EQUALS" -> XCondition.EQUAL_TO;
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
         // DATE_IN was already a first-class viewsheet-side operator (ConditionVocabulary maps
         // it to XCondition.DATE_IN and advertises it via list_condition_operators), but this
         // worksheet-side parser never got the same case -- add_filter/set_conditions rejected an
         // operator the vocabulary endpoint told the caller was legal. Value semantics (resolving
         // a named range like "Last month" into a literal) are a separate, still-open piece of
         // work; this only stops the operator name itself from being rejected.
         case "DATE_IN"                  -> XCondition.DATE_IN;
         default -> throw new IllegalArgumentException(
            "'" + operation + "' is not a condition operator. Accepted: =, !=, <, <=, >, >=, " +
            "BETWEEN, ONE_OF, NOT_ONE_OF, STARTING_WITH, CONTAINS, LIKE, NULL, NOT_NULL, " +
            "DATE_IN. Omit the operator entirely to mean equals -- an unrecognised one used to " +
            "be applied as equals, which quietly returns a different data set.");
      };
   }

   /**
    * Returns {@code true} if the operation string represents a "less-than-or-equal"
    * or "greater-than-or-equal" comparison, which requires {@link Condition#setEqual(boolean)}
    * to be set to {@code true} in addition to the base LESS_THAN / GREATER_THAN operation.
    */
   static boolean isEqualInclusive(String operation) {
      if(operation == null) {
         return false;
      }

      return switch(operation.toUpperCase().replace(' ', '_')) {
         case "<=", "LESS_THAN_OR_EQUAL", ">=", "GREATER_THAN_OR_EQUAL" -> true;
         default -> false;
      };
   }

   static boolean isNegatedOperation(String operation) {
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
