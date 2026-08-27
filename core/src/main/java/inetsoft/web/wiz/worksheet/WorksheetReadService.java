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
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XValueNode;
import inetsoft.web.wiz.worksheet.model.WorksheetModel;
import inetsoft.web.wiz.worksheet.model.WorksheetPropertiesModel;
import org.springframework.stereotype.Service;

import java.awt.Point;
import java.util.*;

/**
 * Reads a live {@link RuntimeWorksheet} and produces a structured
 * {@link WorksheetModel} DTO suitable for agent consumption.
 *
 * <p>The service has no injected dependencies; it operates purely on the
 * {@link RuntimeWorksheet} passed to {@link #read(RuntimeWorksheet)}.</p>
 */
@Service
public class WorksheetReadService {

   /**
    * Reads all table assemblies in the supplied runtime worksheet and returns
    * a {@link WorksheetModel} describing their columns, joins, filters,
    * aggregates, and sort directives.
    *
    * @param rws the live runtime worksheet; must not be {@code null}
    * @return a fully-populated worksheet model
    */
   public WorksheetModel read(RuntimeWorksheet rws) {
      Worksheet ws = rws.getWorksheet();
      Assembly[] assemblies = ws.getAssemblies();
      String primaryName = ws.getPrimaryAssemblyName();

      List<WorksheetModel.TableModel> tables = new ArrayList<>();
      List<WorksheetModel.VariableModel> variables = new ArrayList<>();
      List<WorksheetModel.NamedGroupModel> namedGroups = new ArrayList<>();

      for(Assembly assembly : assemblies) {
         if(assembly instanceof TableAssembly t) {
            boolean primary = t.getName().equals(primaryName);
            tables.add(readTable(t, primary));
         }
         else if(assembly instanceof DefaultVariableAssembly dva) {
            variables.add(readVariable(dva));
         }
         else if(assembly instanceof DefaultNamedGroupAssembly nga) {
            namedGroups.add(readNamedGroup(nga));
         }
      }

      return new WorksheetModel(tables, variables, namedGroups);
   }

   /**
    * Reads the worksheet's own properties -- the four fields behind the Composer's
    * Worksheet Property dialog.
    *
    * <p>The alias and description fallbacks are copied deliberately from
    * {@code WorksheetOptionPaneModel(RuntimeWorksheet)}: the {@link WorksheetInfo} value wins and
    * the {@link AssetEntry} is only consulted when it is {@code null}. Reading them any other way
    * would let this endpoint disagree with what the dialog shows the user for the same sheet.
    * The properties POST writes {@link WorksheetInfo}, i.e. the side that wins here, so a value
    * set through the agent is the value the dialog displays.</p>
    *
    * @param rws the live runtime worksheet; must not be {@code null}
    * @return the sheet's name, alias, description and data-source flag
    */
   public WorksheetPropertiesModel readProperties(RuntimeWorksheet rws) {
      WorksheetInfo winfo = rws.getWorksheet().getWorksheetInfo();
      AssetEntry entry = rws.getEntry();

      String alias = winfo.getAlias() != null ? winfo.getAlias() : entry.getAlias();
      String description = winfo.getDescription() != null
         ? winfo.getDescription() : entry.getProperty("description");

      return new WorksheetPropertiesModel(
         entry.getName(), alias, description, entry.isReportDataSource());
   }

   // -------------------------------------------------------------------------
   // Table
   // -------------------------------------------------------------------------

   private WorksheetModel.TableModel readTable(TableAssembly t, boolean primary) {
      String name = t.getName();
      String type = tableType(t);
      List<WorksheetModel.ColumnModel> columns = readColumns(t);
      List<WorksheetModel.JoinModel> joins = readJoins(t);
      List<WorksheetModel.FilterModel> preConditions =
         readConditions(t.getPreConditionList());
      List<WorksheetModel.FilterModel> postConditions =
         readConditions(t.getPostConditionList());
      List<WorksheetModel.FilterModel> rankingConditions =
         readConditions(t.getRankingConditionList());
      WorksheetModel.AggregateModel aggregates = readAggregates(t);
      List<WorksheetModel.SortModel> sorts = readSorts(t);

      Point offset = t.getPixelOffset();
      int maxRows = t.getMaxRows();

      return new WorksheetModel.TableModel(
         name, type, columns, joins,
         readSources(t), readConcatType(t), readConcatCompatible(t), readAutoUpdate(t),
         preConditions, postConditions, rankingConditions,
         aggregates, sorts, primary,
         t.getDescription(),
         // Anything <= 0 is "no limit" -- the engine applies one only when it is positive -- so
         // -1 and 0 both report null rather than reading back as a limit of -1 or of zero rows.
         // Note this is the effective limit, capped by query.runtime.maxrow; see TableModel.
         maxRows <= 0 ? null : maxRows,
         t.isDistinct(), t.isSQLMergeable(), t.isVisibleTable(), tableMode(t),
         offset == null ? null : offset.x, offset == null ? null : offset.y);
   }

   // -------------------------------------------------------------------------
   // Composition — which assemblies a table is built from
   // -------------------------------------------------------------------------

   /**
    * The assemblies this table is built from, in order.
    *
    * <p>Order is not cosmetic. A concatenation takes its entire column list from its first
    * subtable alone — {@link ConcatenatedTableAssembly#getDefaultColumnSelection} walks
    * {@code subtables[0]}'s columns and consults the rest only to merge numeric types, by
    * position — so the first entry is what decides the shape, and for a MINUS the order decides
    * which table is subtracted from which. Reporting the names without their order would be
    * useless for both.</p>
    *
    * <p>Covers every kind of table built on another: {@link ComposedTableAssembly} declares
    * {@code getTableNames()} and joins, concatenations, mirrors, rotates and unpivots all implement
    * it. Branching on {@link CompositeTableAssembly} instead would miss rotates and unpivots, which
    * extend {@code ComposedTableAssembly} directly and would then be reported as having no sources
    * at all. It matters most for cross and merge joins, which carry no join predicates and therefore
    * report an empty {@code joins} list — nothing else in the model distinguishes those from a table
    * with no sources.</p>
    *
    * <p>Empty for a table whose source has since been deleted: {@code getTableNames()} resolves the
    * name against the worksheet, so a dangling reference reports nothing rather than a name that no
    * longer exists.</p>
    */
   private List<String> readSources(TableAssembly t) {
      if(!(t instanceof ComposedTableAssembly composed)) {
         return List.of();
      }

      String[] names = composed.getTableNames();

      if(names == null) {
         return List.of();
      }

      // MirrorTableAssembly.getTableNames() wraps its source name without checking it, so a
      // half-initialized mirror can produce a null entry — and List.of rejects those outright.
      return Arrays.stream(names).filter(Objects::nonNull).toList();
   }

   /**
    * {@code UNION}, {@code INTERSECT} or {@code MINUS} for a concatenation, {@code "MIXED"} when its
    * pairs do not all use the same operation, {@code null} for anything else.
    *
    * <p>The three behave very differently — adding a source to a UNION can add rows while adding
    * one to an INTERSECT can only remove them — so a caller reasoning about a concatenation needs
    * to know which it is looking at.</p>
    *
    * <p>One operation is held per <em>adjacent pair</em> of subtables, and Composer sets it per
    * connection, so {@code A UNION B MINUS C} is a legal assembly. Reporting the first pair's
    * operation as though it described the whole thing would hand the caller a confidently wrong
    * answer, which is worse than none — hence {@code "MIXED"}. The individual per-pair operations
    * are deliberately not exposed: no agent tool can set them separately
    * ({@code add_concatenation} applies one operation to every pair), so naming which pair is which
    * would not let a caller act on it.</p>
    */
   private String readConcatType(TableAssembly t) {
      if(!(t instanceof ConcatenatedTableAssembly concat)) {
         return null;
      }

      Set<String> operations = new LinkedHashSet<>();
      String[] names = concat.getTableNames();

      // Bound by the SUBTABLES, not by getOperatorCount(): that reports the operator map's size
      // while getOperator(int) indexes the subtable names, so the two disagree the moment the map
      // holds a pair that is not adjacent, and iterating the count reads past the end of the
      // names. One malformed assembly must not cost the caller every other table in the
      // worksheet -- this is the call it makes to find out what is wrong. getOperator returns
      // null for a pair with nothing stored, which concatOperation already handles.
      int pairs = names == null ? 0 : names.length - 1;

      for(int i = 0; i < pairs; i++) {
         operations.add(concatOperation(concat.getOperator(i)));
      }

      if(operations.isEmpty()) {
         return null;
      }

      // One operation, and it was recognized — otherwise the pairs disagree, or the single
      // operation is not a concatenation at all and reports nothing rather than a guess.
      return operations.size() == 1 ? operations.iterator().next() : "MIXED";
   }

   /**
    * The concatenation operation a single pair uses, or {@code null} if it is not one of the three.
    */
   private String concatOperation(TableAssemblyOperator operator) {
      if(operator == null) {
         return null;
      }

      // getKeyOperator() never returns null: with no operator marked as the key one it synthesizes
      // a JOIN, which falls through to null below.
      return switch(operator.getKeyOperator().getOperation()) {
         case TableAssemblyOperator.UNION -> "UNION";
         case TableAssemblyOperator.INTERSECT -> "INTERSECT";
         case TableAssemblyOperator.MINUS -> "MINUS";
         default -> null;
      };
   }

   /**
    * For a concatenation, whether its sources line up by type as well as by count; {@code null} for
    * anything else.
    *
    * <p>Sources are combined by position, so a pair that lines up numerically but not by type
    * produces a column carrying two unrelated kinds of value — and it renders as an ordinary column
    * and reports no error anywhere. Composer computes this same predicate and draws it as a warning
    * on the connection ({@code ConcatenatedTableAssemblyModel.concatenationWarning}), and
    * {@code add_concatenation} now refuses to build one, so reading it here is the only way an agent
    * can see the problem in a concatenation it did not create.</p>
    */
   private Boolean readConcatCompatible(TableAssembly t) {
      if(!(t instanceof ConcatenatedTableAssembly concat)) {
         return null;
      }

      // getTableAssemblies() returns null once any subtable has gone missing from the worksheet,
      // and tableAssembliesAreCompatible() would then throw. Reading one dangling concatenation
      // must not fail the read for the whole worksheet.
      return concat.getTableAssemblies() == null ? null : concat.tableAssembliesAreCompatible();
   }

   /**
    * A mirror's <b>effective</b> auto-update flag, or {@code null} for anything that is not a
    * mirror.
    *
    * <p>Effective, not stored: {@code MirrorAssemblyImpl} answers {@code auto || !isOuterMirror()},
    * so a mirror whose source lives in the same worksheet always reports {@code true} however the
    * flag was set. Settable through the agent API but, until now, not readable through it — so a
    * caller had no way to tell whether a mirror it did not create is tracking its source.</p>
    */
   private Boolean readAutoUpdate(TableAssembly t) {
      return t instanceof MirrorTableAssembly mirror ? mirror.isAutoUpdate() : null;
   }

   /**
    * The table's display mode, in four of the five words {@code set_table_mode} accepts --
    * {@code live}, {@code full}, {@code detail} and {@code edit}. The fifth, {@code default},
    * is not a state and never appears here; see the note below.
    *
    * <p>No field stores it: the mode is a combination of {@code liveData}, {@code runtime} and
    * {@code editMode}, and {@code set_table_mode} writes all three per mode. Deriving it here is
    * what lets a caller read back what it just set — without this, the write is verifiable only by
    * its side effects, which is what left case 1.19 unable to round-trip.
    *
    * <p>Checked in the order the writer distinguishes them: {@code edit} owns editMode, and among
    * the live modes only {@code live} sets runtime, so live-without-runtime reads as
    * {@code detail}.
    *
    * <p><b>This reports the resulting state, not the word that was written, and the two can
    * differ.</b> {@code set_table_mode("live")} sets runtime from {@code isRuntimeSelected()},
    * so on a table whose runtime selection is off it lands in the same state as
    * {@code "detail"} and reads back as {@code detail}. Likewise {@code "default"} is not a
    * state of its own — the writer resolves it to live for an embedded table and metadata for a
    * bound one — so it reads back as {@code detail} or {@code full}. Neither is a lost write;
    * both are the mode the table is actually in. A caller wanting to confirm a specific word was
    * honoured should compare states, not strings.
    */
   private String tableMode(TableAssembly t) {
      if(t.isEditMode()) {
         return "edit";
      }

      if(t.isLiveData()) {
         return t.isRuntime() ? "live" : "detail";
      }

      return "full";
   }

   private String tableType(TableAssembly t) {
      // The snapshot check MUST stay ahead of the EmbeddedTableAssembly branch:
      // SnapshotEmbeddedTableAssembly extends it, so reordering these two silently
      // collapses the distinction again and an agent can no longer tell, before a
      // write, that it is looking at a table whose cell/row edits are refused.
      if(t instanceof SnapshotEmbeddedTableAssembly) {
         return "EMBEDDED_SNAPSHOT";
      }
      else if(t instanceof EmbeddedTableAssembly) {
         return "EMBEDDED";
      }
      else if(t instanceof AbstractJoinTableAssembly) {
         return "JOIN";
      }
      else if(t instanceof MirrorTableAssembly) {
         return "MIRROR";
      }
      else if(t instanceof UnpivotTableAssembly) {
         return "UNPIVOT";
      }
      else if(t instanceof RotatedTableAssembly) {
         return "ROTATED";
      }
      else if(t instanceof ConcatenatedTableAssembly) {
         return "CONCAT";
      }
      else {
         return "TABLE";
      }
   }

   // -------------------------------------------------------------------------
   // Columns
   // -------------------------------------------------------------------------

   private List<WorksheetModel.ColumnModel> readColumns(TableAssembly t) {
      ColumnSelection cs = t.getColumnSelection(false);

      if(cs == null) {
         return Collections.emptyList();
      }

      List<WorksheetModel.ColumnModel> columns = new ArrayList<>();
      int count = cs.getAttributeCount();

      for(int i = 0; i < count; i++) {
         DataRef ref = cs.getAttribute(i);

         if(ref == null) {
            continue;
         }

         if(ref instanceof ColumnRef cr) {
            columns.add(readColumn(cr));
         }
      }

      return columns;
   }

   private WorksheetModel.ColumnModel readColumn(ColumnRef ref) {
      String name = ref.getAttribute();
      String type = ref.getTypeNode() != null ? ref.getTypeNode().getType() : null;
      String alias = ref.getAlias();
      String expression = null;
      String description = ref.getDescription();

      if(ref.isExpression() && ref.getDataRef() instanceof ExpressionRef exprRef) {
         expression = exprRef.getExpression();
      }

      return new WorksheetModel.ColumnModel(name, type, alias, expression, description,
                                            ref.isVisible());
   }

   // -------------------------------------------------------------------------
   // Variables
   // -------------------------------------------------------------------------

   private WorksheetModel.VariableModel readVariable(DefaultVariableAssembly dva) {
      AssetVariable var = dva.getVariable();

      if(var == null) {
         return new WorksheetModel.VariableModel(dva.getName(), null, null, null);
      }

      String label = var.getAlias();
      String type = var.getTypeNode() != null ? var.getTypeNode().getType() : null;
      // AssetVariable extends UserVariable; getValueNode() holds the default value.
      XValueNode valueNode = var.getValueNode();
      String defaultValue = valueNode != null ? valueNode.getValue() != null
         ? valueNode.getValue().toString() : null : null;

      return new WorksheetModel.VariableModel(var.getName(), label, type, defaultValue);
   }

   // -------------------------------------------------------------------------
   // Named groups
   // -------------------------------------------------------------------------

   private WorksheetModel.NamedGroupModel readNamedGroup(DefaultNamedGroupAssembly nga) {
      String name = nga.getName();

      DataRef attachedAttr = nga.getAttachedAttribute();
      SourceInfo attachedSource = nga.getAttachedSource();

      // COLUMN_ATTACHED covers two different, mutually exclusive attachment kinds that share the
      // same Java fields: a worksheet-table column (SourceInfo.ASSET, source = the worksheet
      // assembly's own name) versus a datasource/logical-model or physical-table path (any other
      // SourceInfo type). Reporting both under the same table/column fields would make a
      // datasource-scoped group indistinguishable from a worksheet-table-attached one, even though
      // no such worksheet table exists for it.
      String table = null;
      String column = null;
      String datasource = null;
      String logicalModel = null;
      String sourceTable = null;
      String attribute = null;

      if(attachedSource != null && attachedSource.getType() == SourceInfo.ASSET) {
         table = attachedSource.getSource();
         column = attachedAttr != null ? attachedAttr.getAttribute() : null;
      }
      else if(attachedSource != null) {
         datasource = attachedSource.getPrefix();

         if(attachedSource.getType() == SourceInfo.MODEL) {
            logicalModel = attachedSource.getSource();

            if(attachedAttr instanceof ColumnRef cr && cr.getDataRef() instanceof AttributeRef ar) {
               sourceTable = ar.getEntity();
               attribute = ar.getAttribute();
            }
         }
         else {
            sourceTable = attachedSource.getSource();
            attribute = attachedAttr != null ? attachedAttr.getAttribute() : null;
         }
      }

      NamedGroupInfo ngi = nga.getNamedGroupInfo();
      boolean groupOthers = ngi != null && ngi.getOthers() == XConstants.GROUP_OTHERS;

      List<WorksheetModel.GroupMappingModel> mappings = new ArrayList<>();

      if(ngi != null) {
         String[] groups = ngi.getGroups(false);

         for(String group : groups) {
            ConditionList conds = ngi.getGroupCondition(group);
            List<String> values = new ArrayList<>();
            String operation = null;

            if(conds != null) {
               int size = conds.getConditionSize();

               for(int i = 0; i < size; i++) {
                  if(!conds.isConditionItem(i)) {
                     continue;
                  }

                  ConditionItem item = conds.getConditionItem(i);

                  if(item == null) {
                     continue;
                  }

                  XCondition xc = item.getXCondition();

                  if(xc instanceof Condition c) {
                     if(operation == null) {
                        operation = groupMappingOperation(c);
                     }

                     for(int v = 0; v < c.getValueCount(); v++) {
                        Object val = c.getValue(v);
                        values.add(val != null ? val.toString() : null);
                     }
                  }
               }
            }

            mappings.add(new WorksheetModel.GroupMappingModel(group, values, operation));
         }
      }

      return new WorksheetModel.NamedGroupModel(
         name, table, column, datasource, logicalModel, sourceTable, attribute, mappings,
         groupOthers);
   }

   /**
    * The inverse of {@link WorksheetMutationSupport#parseOperation}/{@code isNegatedOperation}/
    * {@code isEqualInclusive} — recovers the {@code operation} string {@code add_named_group}
    * would need to recreate this exact condition, so a group read back and then edited doesn't
    * silently lose e.g. a {@code STARTING_WITH} match down to plain equality. Returns {@code null}
    * (omitted on the wire) for an operation this vocabulary cannot express.
    */
   private String groupMappingOperation(Condition c) {
      boolean negated = c.isNegated();

      // Only EQUAL_TO/ONE_OF/NULL have a negated string in this vocabulary ("!=", "NOT_ONE_OF",
      // "NOT_NULL"); the rest have no "NOT_..." counterpart add_named_group accepts. A negated
      // LESS_THAN/GREATER_THAN/BETWEEN/STARTING_WITH/CONTAINS/LIKE is reachable here even though
      // this tool never creates one -- a human can build one via the Composer's general condition
      // editor (Condition.isNegatedChangeable() is unconditionally true) -- so reporting the
      // positive string for a negated condition would silently flip its meaning if fed back into
      // add_named_group/edit_named_group. Returning null (per this method's own contract) is
      // correct there: it says "can't be expressed", not "not negated".
      return switch(c.getOperation()) {
         case XCondition.EQUAL_TO -> negated ? "NOT_EQUAL_TO" : "EQUAL_TO";
         case XCondition.LESS_THAN -> negated ? null : c.isEqual() ? "LESS_THAN_OR_EQUAL" : "LESS_THAN";
         case XCondition.GREATER_THAN -> negated ? null : c.isEqual() ? "GREATER_THAN_OR_EQUAL" : "GREATER_THAN";
         case XCondition.BETWEEN -> negated ? null : "BETWEEN";
         case XCondition.ONE_OF -> negated ? "NOT_ONE_OF" : "ONE_OF";
         case XCondition.STARTING_WITH -> negated ? null : "STARTING_WITH";
         case XCondition.CONTAINS -> negated ? null : "CONTAINS";
         case XCondition.LIKE -> negated ? null : "LIKE";
         case XCondition.NULL -> negated ? "NOT_NULL" : "NULL";
         default -> null;
      };
   }

   // -------------------------------------------------------------------------
   // Joins
   // -------------------------------------------------------------------------

   private List<WorksheetModel.JoinModel> readJoins(TableAssembly t) {
      if(!(t instanceof AbstractJoinTableAssembly joinTable)) {
         return Collections.emptyList();
      }

      Enumeration<TableAssemblyOperator> operators = joinTable.getOperators();

      if(operators == null) {
         return Collections.emptyList();
      }

      List<WorksheetModel.JoinModel> joins = new ArrayList<>();

      while(operators.hasMoreElements()) {
         TableAssemblyOperator operator = operators.nextElement();

         if(operator == null) {
            continue;
         }

         for(TableAssemblyOperator.Operator op : operator.getOperators()) {
            if(op == null) {
               continue;
            }

            String leftTable = op.getLeftTable();
            String rightTable = op.getRightTable();

            if(leftTable == null || rightTable == null) {
               continue;
            }

            String leftKey = op.getLeftAttribute() != null
               ? op.getLeftAttribute().getAttribute() : null;
            String rightKey = op.getRightAttribute() != null
               ? op.getRightAttribute().getAttribute() : null;
            String opName = op.getName();

            joins.add(new WorksheetModel.JoinModel(leftTable, leftKey, rightTable, rightKey, opName));
         }
      }

      return joins;
   }

   // -------------------------------------------------------------------------
   // Filters / conditions
   // -------------------------------------------------------------------------

   private List<WorksheetModel.FilterModel> readConditions(ConditionListWrapper wrapper) {
      if(wrapper == null || wrapper.isEmpty()) {
         return Collections.emptyList();
      }

      List<WorksheetModel.FilterModel> result = new ArrayList<>();
      int size = wrapper.getConditionSize();
      String pendingJunction = null;

      for(int i = 0; i < size; i++) {
         if(wrapper.isJunctionOperator(i)) {
            JunctionOperator jop = wrapper.getJunctionOperator(i);
            pendingJunction = jop.getJunction() == JunctionOperator.AND ? "AND" : "OR";
         }
         else if(wrapper.isConditionItem(i)) {
            ConditionItem item = wrapper.getConditionItem(i);

            if(item == null) {
               continue;
            }

            DataRef dataRef = item.getAttribute();
            String field = dataRef != null ? dataRef.getAttribute() : null;

            XCondition xc = item.getXCondition();
            String operation = xc != null ? operationName(xc) : null;
            List<String> values = extractValues(xc);

            result.add(new WorksheetModel.FilterModel(field, operation, values, pendingJunction));
            pendingJunction = null;
         }
      }

      return result;
   }

   /**
    * Converts an {@link XCondition} operation integer + negated/equal flags to the
    * human-readable string that {@code WorksheetMutationSupport.parseOperation()} accepts.
    * This keeps the read-model round-trippable: an agent can read a condition and
    * reproduce or edit it using exactly the string it received.
    */
   private static String operationName(XCondition xc) {
      boolean negated = xc.isNegated();
      boolean equal   = xc.isEqual();
      return switch(xc.getOperation()) {
         case XCondition.EQUAL_TO      -> negated ? "!=" : "=";
         case XCondition.LESS_THAN     -> equal ? "<=" : "<";
         case XCondition.GREATER_THAN  -> equal ? ">=" : ">";
         case XCondition.BETWEEN       -> "BETWEEN";
         case XCondition.ONE_OF        -> negated ? "NOT_ONE_OF" : "ONE_OF";
         case XCondition.STARTING_WITH -> "STARTING_WITH";
         case XCondition.CONTAINS      -> "CONTAINS";
         case XCondition.LIKE          -> "LIKE";
         case XCondition.NULL          -> negated ? "NOT_NULL" : "NULL";
         default                       -> String.valueOf(xc.getOperation());
      };
   }

   private List<String> extractValues(XCondition xc) {
      if(xc instanceof RankingCondition rc) {
         // RankingCondition.getN() carries the ranking's row count, which -- since Bug #75950
         // -- can itself be a "$(variableName)" reference (a raw String, or a UserVariable if
         // this condition round-tripped through XML). Surfacing it here, the same way a filter
         // condition's values are surfaced, is what lets rename_variable/delete_variable's
         // findVariableReferences scan (stylebi-wiz's worksheetTools.ts) actually see it --
         // without this, that scan finds every $(name) EXCEPT one used only as a ranking count,
         // and rename/delete reports no dangling references while leaving a live one behind.
         Object n = rc.getN();

         if(n == null) {
            return Collections.emptyList();
         }

         return Collections.singletonList(
            n instanceof UserVariable uvar ? "$(" + uvar.getName() + ")" : n.toString());
      }

      if(!(xc instanceof Condition c)) {
         return Collections.emptyList();
      }

      int count = c.getValueCount();
      List<String> values = new ArrayList<>(count);

      for(int i = 0; i < count; i++) {
         Object v = c.getValue(i);
         values.add(v != null ? v.toString() : null);
      }

      return values;
   }

   // -------------------------------------------------------------------------
   // Aggregates
   // -------------------------------------------------------------------------

   private WorksheetModel.AggregateModel readAggregates(TableAssembly t) {
      AggregateInfo info = t.getAggregateInfo();

      if(info == null || info.isEmpty()) {
         return null;
      }

      // Groups
      GroupRef[] groupRefs = info.getGroups();
      List<WorksheetModel.AggregateModel.GroupModel> groups = new ArrayList<>(groupRefs.length);

      for(GroupRef gr : groupRefs) {
         String dateLevel = WorksheetEditService.Editor.dateOptionName(gr.getDateGroup());

         // set_group_aggregate materializes a date-level group as a DateRangeRef-wrapped
         // column (see WorksheetMutationSupport#applyAggregateInfo) — gr.getName() on that
         // would return the internal bucketing name (e.g. "Quarter(orderDate)"), not the
         // field the caller originally asked to group by. Report the wrapped base column's
         // name instead so the round trip is faithful to what set_group_aggregate accepted.
         DataRef base = gr.getDataRef();
         String field =
            base instanceof ColumnRef cr && cr.getDataRef() instanceof DateRangeRef dr
               ? dr.getDataRef().getName() : gr.getName();
         groups.add(new WorksheetModel.AggregateModel.GroupModel(field, dateLevel));
      }

      // Aggregates (primary + secondary)
      AggregateRef[] aggRefs = info.getAggregates();
      AggregateRef[] secondaryRefs = info.getSecondaryAggregates();
      List<WorksheetModel.AggregateModel.AggregateRefModel> aggregates =
         new ArrayList<>(aggRefs.length + secondaryRefs.length);

      for(AggregateRef ar : aggRefs) {
         aggregates.add(readAggregateRef(ar));
      }

      for(AggregateRef ar : secondaryRefs) {
         aggregates.add(readAggregateRef(ar));
      }

      return new WorksheetModel.AggregateModel(groups, aggregates, info.isCrosstab());
   }

   private WorksheetModel.AggregateModel.AggregateRefModel readAggregateRef(AggregateRef ar) {
      String column = ar.getAttribute();
      AggregateFormula formula = ar.getFormula();
      String formulaName = formula != null ? formula.getName() : null;
      String alias = null;

      if(ar.getDataRef() instanceof ColumnRef cr) {
         alias = cr.getAlias();
      }

      Integer n = formula != null && formula.hasN() ? ar.getN() : null;

      return new WorksheetModel.AggregateModel.AggregateRefModel(column, formulaName, alias, n);
   }

   // -------------------------------------------------------------------------
   // Sorts
   // -------------------------------------------------------------------------

   private List<WorksheetModel.SortModel> readSorts(TableAssembly t) {
      SortInfo sortInfo = t.getSortInfo();

      if(sortInfo == null || sortInfo.getSortCount() == 0) {
         return Collections.emptyList();
      }

      SortRef[] sortRefs = sortInfo.getSorts();
      List<WorksheetModel.SortModel> sorts = new ArrayList<>(sortRefs.length);

      for(SortRef sr : sortRefs) {
         String field = sr.getAttribute();
         String order = sr.getOrder() == XConstants.SORT_ASC ? "ASC" : "DESC";
         sorts.add(new WorksheetModel.SortModel(field, order));
      }

      return sorts;
   }
}
