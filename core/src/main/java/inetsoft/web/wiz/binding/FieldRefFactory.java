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
package inetsoft.web.wiz.binding;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.internal.binding.AssetNamedGroupInfo;
import inetsoft.report.internal.binding.SummaryAttr;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.DefaultNamedGroupAssembly;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.util.XNamedGroupInfo;
import inetsoft.uql.viewsheet.CalculateRef;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.binding.drm.ColumnRefModel;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.model.BAggregateRefModel;
import inetsoft.web.binding.model.BDimensionRefModel;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.model.GroupCondition;
import inetsoft.web.binding.model.NamedGroupInfoModel;
import inetsoft.web.binding.model.graph.ChartAggregateRefModel;
import inetsoft.web.binding.model.graph.ChartDimensionRefModel;
import inetsoft.web.binding.model.graph.ChartRefModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.model.condition.ConditionExpression;
import inetsoft.web.composer.model.condition.ConditionUtil;
import inetsoft.web.wiz.binding.model.FieldRef;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Converts between StyleBI's ref models and the agent-facing {@link FieldRef}. */
public final class FieldRefFactory {
   public static final String DIMENSION = "dimension";
   public static final String MEASURE = "measure";

   private static final List<String> TYPES = List.of(DIMENSION, MEASURE);

   private FieldRefFactory() {
   }

   /**
    * Builds the chart-side ref model a {@link FieldRef} describes.
    *
    * <p>Shared by 2b's shelf writes and 2c's aesthetic channels: both put the same kind of
    * field in different places, and two copies of this would drift the moment one of them
    * learned about a new field attribute.
    */
   public static ChartRefModel toChartRef(FieldRef field) {
      try {
         return toChartRef(field, null, null, null, null);
      }
      catch(RuntimeException e) {
         throw e; // preserve e.g. requireType's IllegalArgumentException as-is
      }
      catch(Exception e) {
         throw new RuntimeException(e);
      }
   }

   /**
    * Builds the chart-side ref model a {@link FieldRef} describes, resolving {@code namedGroup}
    * into a live {@link NamedGroupInfoModel} when the field carries one.
    *
    * @param model          the chart's binding model, for the bound source's reported column
    *                       data types when a measure arrives with no {@code aggregate}.
    * @param rvs            the runtime viewsheet, for a worksheet-local named group lookup
    *                       and the aggregate calc-field lookup.
    * @param source         the chart's own {@code SourceInfo}, so a worksheet-local name is
    *                       matched against groups attached to the same source.
    * @param refModelService needed to convert a worksheet-local group's conditions into the
    *                        model shape {@link NamedGroupInfoModel#createNamedGroupInfo} expects.
    */
   public static ChartRefModel toChartRef(FieldRef field, BindingModel model,
                                          RuntimeViewsheet rvs, SourceInfo source,
                                          DataRefModelFactoryService refModelService)
      throws Exception
   {
      requireType(field);

      if(MEASURE.equalsIgnoreCase(field.type())) {
         ChartAggregateRefModel ref = new ChartAggregateRefModel();
         ref.setColumnValue(field.column());
         ref.setName(field.column());

         if(field.aggregate() != null) {
            ref.setFormula(field.aggregate());
         }

         // Bug #76949 -- an omitted 'aggregate' must not reach the binding factory with a null
         // formula, and no aggregate may reach it with an unstamped refType. Unconditional: it
         // also supplies the ref type for an aggregate that did carry a formula. See
         // applyAggregateDefaults().
         applyAggregateDefaults(ref, model, rvs, source, field.column());

         if(field.calculateInfo() != null) {
            ref.setCalculateInfo(field.calculateInfo());
         }

         ref.setSecondaryY(Boolean.TRUE.equals(field.secondaryY()));

         return ref;
      }

      ChartDimensionRefModel ref = new ChartDimensionRefModel();
      ref.setColumnValue(field.column());
      ref.setName(field.column());

      if(field.dateLevel() != null) {
         ref.setDateLevel(DateLevels.normalize(field.dateLevel()));
      }

      NamedGroupInfoModel namedGroupInfo = resolveNamedGroupInfo(field, rvs, source, refModelService);

      if(namedGroupInfo != null) {
         ref.setNamedGroupInfo(namedGroupInfo);
         ref.setOrder(XConstants.SORT_SPECIFIC);
      }

      return ref;
   }

   /**
    * Resolves whichever of {@code namedGroup} (by name) or {@code namedGroupValues} (inline) the
    * field carries, or {@code null} if it carries neither. Shared by the chart and table/crosstab
    * binding paths so both learned this at once rather than one at a time.
    */
   public static NamedGroupInfoModel resolveNamedGroupInfo(
      FieldRef field, RuntimeViewsheet rvs, SourceInfo source,
      DataRefModelFactoryService refModelService) throws Exception
   {
      if(field.namedGroup() != null && field.namedGroupValues() != null) {
         throw new IllegalArgumentException(
            "Field '" + field.column() + "' carries both 'namedGroup' (a reference to an " +
            "existing named group by name) and 'namedGroupValues' (an inline definition) -- " +
            "pass exactly one.");
      }

      if(field.namedGroupValues() != null) {
         return buildInlineNamedGroupInfo(field.namedGroupValues(), field.column());
      }

      if(field.namedGroup() != null) {
         return resolveNamedGroupInfo(
            field.namedGroup(), rvs, source, field.column(), refModelService);
      }

      return null;
   }

   /**
    * Builds an inline, value-list-defined named group ({@code SIMPLE_NAMEDGROUP_INFO}) directly
    * from the caller's own {@code {name, values}} pairs -- the same shape the Composer's
    * right-click "Group columns" feature builds, and the one {@code NamedGroupInfoModel}'s own
    * {@code createNamedGroupInfo} already knows how to turn into a live
    * {@code SimpleNamedGroupInfo}. Nothing here needs a worksheet or repository lookup, unlike
    * the by-name path above, because the caller supplied the membership directly.
    */
   public static NamedGroupInfoModel buildInlineNamedGroupInfo(
      FieldRef.NamedGroupValues spec, String column)
   {
      if(spec.others() != null) {
         throw new IllegalArgumentException(
            "Field '" + column + "'s 'namedGroupValues.others' is not supported -- an inline " +
            "named group here has no way to bucket unmatched values together (unlike a " +
            "calc-table cell's inline named group); a value not named in any group renders as " +
            "its own, ungrouped row. Remove 'others', or list every value that should be " +
            "grouped explicitly.");
      }

      if(spec.groups() == null || spec.groups().isEmpty()) {
         throw new IllegalArgumentException(
            "Field '" + column + "'s 'namedGroupValues.groups' must be a non-empty list of " +
            "{name, values} -- an inline named group with no groups in it buckets nothing.");
      }

      NamedGroupInfoModel model = new NamedGroupInfoModel();
      model.setType(XNamedGroupInfo.SIMPLE_NAMEDGROUP_INFO);
      Set<String> seenNames = new HashSet<>();

      for(FieldRef.NamedGroupValues.Clause clause : spec.groups()) {
         if(clause.name() == null || clause.name().isBlank()) {
            throw new IllegalArgumentException(
               "Field '" + column + "'s 'namedGroupValues.groups' has an entry with no " +
               "non-blank 'name'.");
         }

         if(clause.values() == null || clause.values().isEmpty()) {
            throw new IllegalArgumentException(
               "Field '" + column + "'s 'namedGroupValues' group '" + clause.name() + "' needs " +
               "a non-empty 'values' list -- a group with no member values matches nothing.");
         }

         if(!seenNames.add(clause.name())) {
            throw new IllegalArgumentException(
               "Field '" + column + "'s 'namedGroupValues.groups' has a duplicate name '" +
               clause.name() + "' -- list each group name at most once.");
         }

         model.addGroup(new GroupCondition(clause.name(), clause.values()));
      }

      return model;
   }

   /**
    * Resolves a field's {@code namedGroup} name into a fully-formed, already-validated
    * {@link NamedGroupInfoModel} -- worksheet-local first (an {@code EXPERT_NAMEDGROUP_INFO}
    * built from the {@code DefaultNamedGroupAssembly}'s own per-group conditions, mirroring
    * {@code CalcTableService#worksheetLocalOrder}), then a repository-registered predefined
    * named group (an {@code ASSET_NAMEDGROUP_INFO_REF} by name, left for
    * {@code NamedGroupInfoModel#createNamedGroupInfo} to resolve against the field's own
    * {@code DataRef} at apply time). Neither matching throws, naming the field/column -- a name
    * that resolves to nothing would otherwise silently bind with no grouping at all.
    */
   public static NamedGroupInfoModel resolveNamedGroupInfo(
      String namedGroup, RuntimeViewsheet rvs, SourceInfo source, String column,
      DataRefModelFactoryService refModelService) throws Exception
   {
      for(DefaultNamedGroupAssembly ngAssembly : worksheetNamedGroups(rvs, source, column)) {
         if(namedGroup.equals(ngAssembly.getName())) {
            NamedGroupInfoModel model = new NamedGroupInfoModel();
            model.setType(XNamedGroupInfo.EXPERT_NAMEDGROUP_INFO);

            for(String group : ngAssembly.getNamedGroupInfo().getGroups(false)) {
               Object[] conditions = ConditionUtil.fromConditionListToModel(
                  ngAssembly.getNamedGroupInfo().getGroupCondition(group), refModelService);
               ConditionExpression conditionExpression = new ConditionExpression();
               conditionExpression.setName(group);
               conditionExpression.setList(conditions);
               model.addCondition(conditionExpression);
            }

            return model;
         }
      }

      AssetRepository rep = AssetUtil.getAssetRepository(false);

      if(rep != null) {
         DataRef fld = new AttributeRef(column);
         AssetNamedGroupInfo[] infos = SummaryAttr.getAssetNamedGroupInfos(fld, rep, null);

         for(AssetNamedGroupInfo info : infos) {
            if(namedGroup.equals(info.getName())) {
               NamedGroupInfoModel model = new NamedGroupInfoModel();
               model.setType(XNamedGroupInfo.ASSET_NAMEDGROUP_INFO_REF);
               model.setName(namedGroup);
               return model;
            }
         }
      }

      throw new IllegalArgumentException(
         "'" + namedGroup + "' is not a named group on column '" + column + "' -- it matches " +
         "neither a worksheet-local group created by add_named_group nor a repository-" +
         "registered predefined named group. list_named_groups reports what is available.");
   }

   /**
    * The worksheet-local {@code DefaultNamedGroupAssembly}(s) attached to this column of this
    * source -- mirrors {@code CalcTableService#worksheetNamedGroups}, generalized to any
    * {@code SourceInfo} rather than one calc table's own.
    */
   private static List<DefaultNamedGroupAssembly> worksheetNamedGroups(
      RuntimeViewsheet rvs, SourceInfo source, String column)
   {
      Worksheet ws = rvs == null || rvs.getViewsheet() == null
         ? null : rvs.getViewsheet().getBaseWorksheet();

      return WorksheetNamedGroupMatcher.worksheetNamedGroups(ws, source, column);
   }

   public static FieldRef from(DataRefModel ref) {
      if(ref instanceof BAggregateRefModel aggregate) {
         Boolean secondaryY = aggregate instanceof ChartAggregateRefModel chartAggregate
            ? chartAggregate.isSecondaryY() : null;

         return new FieldRef(aggregate.getColumnValue(), MEASURE, aggregate.getFormula(),
                             null, null, null, null, null, aggregate.getCalculateInfo(),
                             null, secondaryY, null, null, aggregate.getSecondaryColumnValue());
      }

      if(ref instanceof BDimensionRefModel dimension) {
         NamedGroupInfoModel ngInfo = dimension.getNamedGroupInfo();

         // An inline group (built by buildInlineNamedGroupInfo, on the write side) has no
         // real name -- getName() is the hardcoded "Custom" literal, so reporting it as
         // 'namedGroup' would read back as a reference to a group called "Custom" that
         // doesn't exist. Reconstruct the inline definition instead, from the same
         // {name, values} pairs the write side accepted, so a read-modify-write round trip
         // through this field doesn't silently drop the grouping.
         if(ngInfo != null && ngInfo.getType() == XNamedGroupInfo.SIMPLE_NAMEDGROUP_INFO) {
            List<FieldRef.NamedGroupValues.Clause> clauses = new ArrayList<>();

            if(ngInfo.getGroups() != null) {
               for(GroupCondition group : ngInfo.getGroups()) {
                  clauses.add(new FieldRef.NamedGroupValues.Clause(group.getName(), group.getValue()));
               }
            }

            return new FieldRef(dimension.getColumnValue(), DIMENSION, null,
                                dimension.getDateLevel(), null, null, null,
                                new FieldRef.NamedGroupValues(clauses, null), null, null, null,
                                null, dimension.isTimeSeries());
         }

         return new FieldRef(dimension.getColumnValue(), DIMENSION, null,
                             dimension.getDateLevel(), ngInfo == null ? null : ngInfo.getName(),
                             null, null, null, null, null, null, null, dimension.isTimeSeries());
      }

      // A bare ColumnRefModel is a Table detail column, and its alias round-trips through this
      // model field already (see TableBindingMutator.setColumnLabels' Table branch) -- no live
      // assembly needed to report it, unlike Crosstab's FormatInfo-based label (see
      // TableBindingService.read, which resolves that one against the live, rendered lens).
      String label = ref instanceof ColumnRefModel column && column.getAlias() != null
         && !column.getAlias().isBlank() ? column.getAlias() : null;

      return new FieldRef(ref == null ? null : ref.getName(), null, null, null, null, null, null,
                          null, null, label);
   }

   /**
    * Fails loud when the discriminator is absent or unrecognized. Never defaults it: a ref
    * with a guessed role lands on the wrong shelf and renders plausibly wrong, which is the
    * failure this vocabulary exists to prevent.
    */
   public static void requireType(FieldRef ref) {
      String type = ref == null || ref.type() == null ? null : ref.type().trim().toLowerCase();

      if(type == null || !TYPES.contains(type)) {
         throw new IllegalArgumentException(
            "Field '" + (ref == null ? "?" : ref.column()) + "' needs a 'type' of " +
            String.join(" or ", TYPES) + ", got '" +
            (ref == null ? "null" : String.valueOf(ref.type())) + "'.");
      }

      requireNoInboundChartType(ref);
   }

   /**
    * Refuses a field that arrived carrying a chart type.
    *
    * <p>The chart read reports one per measure on a multi-style chart, which makes handing one of
    * its field refs straight back to a write the obvious next move — and no write takes it. Writing
    * a per-measure type is {@code set_chart_type}'s {@code field} argument.
    *
    * <p>Here rather than only in the plugin. The plugin refuses it too, but that is the outermost
    * tier and the most bypassable one: wiz-services, a script, or any future client reaching
    * {@code POST chart/shelf} directly would get the silent drop this whole surface is written
    * against. Placed on {@code requireType} because every inbound ref passes through it — the chart
    * path through {@code toChartRef}, the table and calc paths on their own — so one check covers
    * all six writes instead of six checks drifting apart.
    */
   private static void requireNoInboundChartType(FieldRef ref) {
      if(ref.chartType() == null && ref.runtimeChartType() == null) {
         return;
      }

      throw new IllegalArgumentException(
         "Field '" + ref.column() + "' carries a chart type, which no binding write can set — " +
         "that is set_chart_type's 'field' argument, on a multi-style chart. The chart read " +
         "reports it, so a ref read from there has to have it removed before it is written back; " +
         "accepting it here would drop it silently and report success.");
   }

   // ── default aggregate for a measure the caller gave no 'aggregate' for ────────

   /**
    * The {@code SourceTableColumn.getDataType()} values {@code AssetUtil.isNumberType()}
    * recognizes, kept as a literal set rather than delegating to {@code AssetUtil} for the same
    * reason the rest of this package does: touching {@code AssetUtil}/{@code AggregateFormula}
    * from wiz code is unsafe under plain JUnit.
    */
   private static final Set<String> NUMERIC_TYPES =
      Set.of("float", "double", "byte", "short", "integer", "long");

   /**
    * Formula <i>values</i>, not formula identifiers -- {@code BAggregateRefModel.formula} is
    * written straight through to {@code VSAggregateRef#setFormulaValue}, so these are the
    * display-cased spellings {@code VSCrosstabBindingHandler#createAgg()} writes and the agent
    * vocabulary accepts, not {@code SummaryAttr.NONE_FORMULA} ("none", the identifier).
    */
   private static final String NONE = "None";
   private static final String SUM = "Sum";
   private static final String COUNT = "Count";

   /**
    * Stamps the ref type every aggregate needs, and the formula a measure needs when the caller
    * supplied no explicit {@code aggregate}, mirroring the composer's own drag-and-drop default
    * in {@code VSCrosstabBindingHandler#createAgg()}.
    *
    * <p>Bug #76949. Every aggregate this package builds is constructed from scratch, so unlike
    * the composer's drop handler -- which reads {@code refType} off the dragged tree {@code
    * AssetEntry} and stamps it before binding -- nothing here ever set it. The resulting model
    * reached {@code BAggregateRefModel#createDataRef()} with {@code refType} 0 and a null
    * wrapped ref, which made {@code VSCrosstabBindingFactory#getDefaultFormula()} miss its
    * {@code AGG_CALC}/{@code AGG_EXPR} branch and throw -- surfacing as an opaque 500. That is
    * also why bug #76650's fix, which added exactly that branch, had no effect on this path:
    * the branch was correct, but the bit it tests was never set here.
    *
    * <p>An aggregate-mode calc field is already an aggregated value, so it takes {@code None}
    * rather than being wrapped in a second formula (which would aggregate it twice). Anything
    * else takes the data type's default, {@code Sum} for a number and {@code Count} otherwise
    * -- the same choice {@code TableBindingMutator#convertForShelf()} already made on the
    * {@code move_table_field} path, which is why moving a field onto the aggregates shelf
    * worked while writing the identical field with {@code set_table_fields} did not.
    *
    * @param ref    the aggregate being built; its formula is left alone when it already has one.
    * @param model  the binding model, for the bound source's reported column data types.
    * @param rvs    the runtime viewsheet, for the calc-field lookup.
    * @param source the assembly's own {@code SourceInfo}, naming the table the calc field
    *               would be attached to.
    */
   public static void applyAggregateDefaults(BAggregateRefModel ref, BindingModel model,
                                             RuntimeViewsheet rvs, SourceInfo source,
                                             String column)
   {
      CalculateRef calc = aggregateCalcField(rvs, source, column);

      if(calc != null) {
         // Stamped whether or not a formula was supplied. A FieldRef carries no ref type, so a
         // shelf that is merely read and written back -- which every add/remove/move does, via
         // setShelf -- would otherwise drop the bit on the aggregates that were already there
         // and were not the one being changed.
         //
         // getRefType() already ORs in AGG_CALC for a non-detail calc field, and preserves a
         // composite such as CUBE_MEASURE | AGG_CALC -- so read it rather than hardcoding the
         // bit, which would drop the cube half.
         ref.setRefType(calc.getRefType());

         if(ref.getFormula() == null) {
            ref.setFormula(NONE);
         }

         return;
      }

      if(ref.getFormula() == null) {
         String dataType = dataTypeOf(model, column);
         ref.setFormula(dataType != null && NUMERIC_TYPES.contains(dataType) ? SUM : COUNT);
      }
   }

   /**
    * The aggregate-mode calc field {@code column} names on the bound source, or {@code null}
    * if it is not one.
    *
    * <p>Looked up on the viewsheet rather than read off {@code model.getTables()}: a calc field
    * lives in {@code Viewsheet.calcmap}, not in the worksheet table's {@code ColumnSelection}
    * the source tables are built from, so it does not appear there at all -- and {@code
    * SourceTableColumn} carries only a name, data type and description, with nowhere to record
    * that it is one. Same lookup {@code CalcFieldAgentService} already does.
    *
    * <p>Falls back to a scan of every source that has calc fields when the assembly's own
    * source name does not match a {@code calcmap} key. A miss here is not harmless -- it would
    * default an aggregate calc field to {@code Sum} and aggregate an already-aggregated value
    * twice, which renders plausibly wrong rather than failing.
    */
   public static CalculateRef aggregateCalcField(RuntimeViewsheet rvs, SourceInfo source,
                                                 String column)
   {
      Viewsheet vs = rvs == null ? null : rvs.getViewsheet();

      if(vs == null || column == null) {
         return null;
      }

      CalculateRef calc = source == null
         ? null : vs.getCalcField(source.getSource(), column);

      if(calc == null) {
         Collection<String> sources = vs.getCalcFieldSources();

         for(String table : sources == null ? List.<String>of() : sources) {
            calc = vs.getCalcField(table, column);

            if(calc != null) {
               break;
            }
         }
      }

      return calc != null && !calc.isBaseOnDetail() ? calc : null;
   }

   /**
    * The bound source's own reported data type for {@code column}, or {@code null} if unknown.
    *
    * <p>Matches {@code column} against a reported column name either exactly or with either
    * side's {@code "table.attribute"} qualifier stripped -- the same symmetric matching {@link
    * TableBindingService#unqualified} exists for, since a column from a joined/merged worksheet
    * table can be qualified while the field being bound names it bare (or vice versa). Without
    * this, a qualified numeric column silently defaulted to {@code Count} instead of {@code
    * Sum}, since the exact-match-only lookup never found its data type.
    */
   public static String dataTypeOf(BindingModel model, String column) {
      List<BindingModel.SourceTable> tables = model == null ? null : model.getTables();

      if(tables == null || column == null) {
         return null;
      }

      String bareColumn = TableBindingService.unqualified(column);

      for(BindingModel.SourceTable table : tables) {
         if(table.getColumns() == null) {
            continue;
         }

         for(BindingModel.SourceTableColumn col : table.getColumns()) {
            String name = col.getName();

            if(column.equalsIgnoreCase(name) || bareColumn.equalsIgnoreCase(name) ||
               column.equalsIgnoreCase(TableBindingService.unqualified(name)))
            {
               return col.getDataType();
            }
         }
      }

      return null;
   }
}
