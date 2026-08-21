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
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.AttachedAssembly;
import inetsoft.uql.asset.DefaultNamedGroupAssembly;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.util.XNamedGroupInfo;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.model.BAggregateRefModel;
import inetsoft.web.binding.model.BDimensionRefModel;
import inetsoft.web.binding.model.NamedGroupInfoModel;
import inetsoft.web.binding.model.graph.ChartAggregateRefModel;
import inetsoft.web.binding.model.graph.ChartDimensionRefModel;
import inetsoft.web.binding.model.graph.ChartRefModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.model.condition.ConditionExpression;
import inetsoft.web.composer.model.condition.ConditionUtil;
import inetsoft.web.wiz.binding.model.FieldRef;

import java.util.ArrayList;
import java.util.List;

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
         return toChartRef(field, null, null, null);
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
    * @param rvs            the runtime viewsheet, for a worksheet-local named group lookup.
    * @param source         the chart's own {@code SourceInfo}, so a worksheet-local name is
    *                       matched against groups attached to the same source.
    * @param refModelService needed to convert a worksheet-local group's conditions into the
    *                        model shape {@link NamedGroupInfoModel#createNamedGroupInfo} expects.
    */
   public static ChartRefModel toChartRef(FieldRef field, RuntimeViewsheet rvs, SourceInfo source,
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

         return ref;
      }

      ChartDimensionRefModel ref = new ChartDimensionRefModel();
      ref.setColumnValue(field.column());
      ref.setName(field.column());

      if(field.dateLevel() != null) {
         ref.setDateLevel(DateLevels.normalize(field.dateLevel()));
      }

      if(field.namedGroup() != null) {
         ref.setNamedGroupInfo(resolveNamedGroupInfo(
            field.namedGroup(), rvs, source, field.column(), refModelService));
         ref.setOrder(XConstants.SORT_SPECIFIC);
      }

      return ref;
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
      List<DefaultNamedGroupAssembly> matches = new ArrayList<>();
      Worksheet ws = rvs == null || rvs.getViewsheet() == null
         ? null : rvs.getViewsheet().getBaseWorksheet();

      if(source == null || source.getSource() == null || ws == null) {
         return matches;
      }

      for(Assembly wsAssembly : ws.getAssemblies()) {
         if(!(wsAssembly instanceof DefaultNamedGroupAssembly ngAssembly) ||
            ngAssembly.getAttachedType() != AttachedAssembly.COLUMN_ATTACHED)
         {
            continue;
         }

         SourceInfo attachedSource = ngAssembly.getAttachedSource();
         DataRef attr = ngAssembly.getAttachedAttribute();

         if(attachedSource == null || attr == null ||
            !source.getSource().equals(attachedSource.getSource()) ||
            !column.equals(attr.getAttribute()))
         {
            continue;
         }

         matches.add(ngAssembly);
      }

      return matches;
   }

   public static FieldRef from(DataRefModel ref) {
      if(ref instanceof BAggregateRefModel aggregate) {
         return new FieldRef(aggregate.getColumnValue(), MEASURE, aggregate.getFormula(),
                             null, null);
      }

      if(ref instanceof BDimensionRefModel dimension) {
         return new FieldRef(dimension.getColumnValue(), DIMENSION, null,
                             dimension.getDateLevel(),
                             dimension.getNamedGroupInfo() == null
                                ? null : dimension.getNamedGroupInfo().getName());
      }

      return new FieldRef(ref == null ? null : ref.getName(), null, null, null, null);
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
   }
}
