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
package inetsoft.web.wiz.controller;

import inetsoft.uql.DrillPath;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.erm.XEntity;
import inetsoft.uql.erm.XLogicalModel;
import inetsoft.util.Tool;
import inetsoft.util.data.CommonKVModel;
import inetsoft.web.portal.model.database.*;
import inetsoft.web.wiz.controller.WizLogicalModelWire.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps the logical model wire shape to the Portal's {@link LogicalModelDefinition} and back.
 *
 * <p>Three rules of the service decide the shape of the mapping. The service builds an expression
 * attribute for ANY {@code type} other than exactly {@code "column"}, so the mapper always sets
 * {@code type} from which of {@code column}/{@code expression} is present; it carries every
 * wire field to the DTO (a sentinel test pins that); and dependents migrate only for an element
 * whose {@code oldName} is non-null, so the markers are resolved here against the stored model
 * (see {@link WizLogicalModelWire}).</p>
 */
final class WizLogicalModelMapper {
   static final String NO_FORMULA = "None";

   WizLogicalModelMapper() {
   }

   // -- wire -> DTO ----------------------------------------------------------------------------

   /**
    * Builds the DTO the service consumes.
    *
    * @param definition the (validated) wire definition.
    * @param stored     the stored model on an update, {@code null} on a create (where markers
    *                   mean nothing and every {@code oldName} is left null).
    * @param view       the physical view name to use.
    */
   LogicalModelDefinition toDto(Definition definition, XLogicalModel stored, String view) {
      LogicalModelDefinition dto = new LogicalModelDefinition();
      dto.setName(definition.name());
      dto.setPartition(view);
      dto.setDescription(definition.description());
      dto.setConnection(blankToNull(definition.connection()));
      dto.setFolder(blankToNull(definition.folder()));
      dto.setParent(null);
      List<EntityModel> entities = new ArrayList<>();

      for(Entity entity : nullToEmpty(definition.entities())) {
         String entityOld = resolveOldName(entity.oldName(), entity.name(), stored != null,
            stored != null && stored.getEntity(entity.name()) != null);
         entities.add(toDto(entity, stored, entityOld));
      }

      dto.setEntities(entities);
      return dto;
   }

   private EntityModel toDto(Entity entity, XLogicalModel stored, String entityOldName) {
      EntityModel model = new EntityModel();
      model.setName(entity.name());
      model.setOldName(entityOldName);
      model.setDescription(entity.description());
      model.setVisible(entity.visible() == null || entity.visible());
      XEntity storedEntity = stored == null || entityOldName == null ? null :
         stored.getEntity(entityOldName);
      List<XAttributeModel> attributes = new ArrayList<>();

      for(Attribute attribute : nullToEmpty(entity.attributes())) {
         String oldName = resolveOldName(attribute.oldName(), attribute.name(), stored != null,
            storedEntity != null && storedEntity.getAttribute(attribute.name()) != null);
         attributes.add(toDto(attribute, entity.name(), oldName));
      }

      model.setAttributes(attributes);
      return model;
   }

   private XAttributeModel toDto(Attribute attribute, String entityName, String oldName) {
      XAttributeModel model = new XAttributeModel();
      boolean expression = isExpression(attribute);
      model.setName(attribute.name());
      model.setOldName(oldName);
      model.setParentEntity(entityName);
      model.setDescription(attribute.description());
      model.setVisible(attribute.visible() == null || attribute.visible());
      model.setType(expression ? WizLogicalModelWire.TYPE_EXPRESSION :
                       WizLogicalModelWire.TYPE_COLUMN);
      model.setTable(expression ? null : attribute.table());
      model.setColumn(expression ? null : attribute.column());
      model.setExpression(expression ? attribute.expression() : null);
      model.setAggregate(expression && Boolean.TRUE.equals(attribute.aggregate()));
      model.setParseable(!expression || attribute.parseable() == null || attribute.parseable());
      model.setDataType(attribute.dataType());
      RefTypeModel refType = new RefTypeModel();
      refType.setType(refTypeCode(attribute.refType()));
      refType.setFormula(formulaToStored(attribute.formula()));
      model.setRefType(refType);
      model.setBrowseData(Boolean.TRUE.equals(attribute.browseData()));
      model.setBrowseQuery(attribute.browseQuery());

      if(attribute.format() != null) {
         XFormatInfoModel format = new XFormatInfoModel();
         format.setFormat(attribute.format().format());
         format.setFormatSpec(attribute.format().formatSpec());
         format.setDurationPadZeros(attribute.format().durationPadZeros() == null ||
                                       attribute.format().durationPadZeros());
         model.setFormat(format);
      }

      model.setDrillInfo(toDrill(attribute.drill()));
      return model;
   }

   /** Whether the attribute is an expression attribute (the discriminator the service reads). */
   static boolean isExpression(Attribute attribute) {
      return !Tool.isEmptyString(attribute.expression());
   }

   /**
    * Resolves a rename marker to the {@code oldName} the service expects. Absent: identity when
    * the stored model has the element, otherwise a new element; explicit null: a new element;
    * a name: that name. On a create ({@code update == false}) nothing is migrated, so the marker
    * is null.
    */
   static String resolveOldName(String raw, String name, boolean update, boolean storedHasName) {
      if(!update) {
         return null;
      }

      if(raw == null) {
         return storedHasName ? name : null;
      }

      return raw.isEmpty() ? null : raw;
   }

   /**
    * No formula is stored as {@code "None"} (the attribute's own default); writing a null would be
    * stored as the literal text {@code "null"} by the XML round trip.
    */
   static String formulaToStored(String formula) {
      return Tool.isEmptyString(formula) ? NO_FORMULA : formula;
   }

   /** "None", "null" and blank all mean no default formula and read back as null. */
   static String formulaFromStored(String formula) {
      return Tool.isEmptyString(formula) || NO_FORMULA.equals(formula) ||
         "null".equals(formula) ? null : formula;
   }

   static int refTypeCode(String refType) {
      int index = refType == null ? 0 : WizLogicalModelWire.REF_TYPES.indexOf(refType);
      return Math.max(index, 0);
   }

   private AutoDrillInfo toDrill(Drill drill) {
      AutoDrillInfo info = new AutoDrillInfo();
      List<AutoDrillPathModel> paths = new ArrayList<>();
      List<WizLogicalModelWire.DrillPath> wirePaths =
         drill == null ? List.of() : nullToEmpty(drill.paths());

      for(WizLogicalModelWire.DrillPath path : wirePaths) {
         AutoDrillPathModel model = new AutoDrillPathModel();
         model.setName(path.name());
         model.setLinkType(WizLogicalModelWire.LINK_VIEWSHEET.equals(path.linkType()) ?
                              DrillPath.VIEWSHEET_LINK : DrillPath.WEB_LINK);
         model.setLink(path.link() == null ? "" : path.link());
         model.setTargetFrame(path.targetFrame() == null ? "" : path.targetFrame());
         model.setTip(path.tip() == null ? "" : path.tip());
         model.setPassParams(path.passParams() == null || path.passParams());
         model.setDisablePrompting(Boolean.TRUE.equals(path.disablePrompting()));
         List<DrillParameterModel> params = new ArrayList<>();

         for(DrillParam param : nullToEmpty(path.params())) {
            DrillParameterModel parameter = new DrillParameterModel();
            parameter.setName(param.name());
            parameter.setField(param.field());
            parameter.setType(param.type());
            params.add(parameter);
         }

         model.setParams(params);

         if(path.query() != null) {
            DrillSubQueryModel query = new DrillSubQueryModel();
            query.setEntry(AssetEntry.createAssetEntry(path.query().entry()));
            List<CommonKVModel<String, String>> kv = new ArrayList<>();

            for(DrillQueryParam param : nullToEmpty(path.query().params())) {
               kv.add(new CommonKVModel<>(param.key(), param.value()));
            }

            query.setParams(kv);
            model.setQuery(query);
         }

         paths.add(model);
      }

      info.setPaths(paths);
      return info;
   }

   // -- DTO -> wire ----------------------------------------------------------------------------

   /**
    * Builds the wire definition from the service's read model. {@code oldName} is each element's
    * current name (the service sets it), {@code errorMessage} is the service's per-element check.
    */
   Definition toWire(LogicalModelDefinition dto, boolean extended) {
      List<Entity> entities = new ArrayList<>();

      for(EntityModel entity : nullToEmpty(dto.getEntities())) {
         List<Attribute> attributes = new ArrayList<>();

         for(XAttributeModel attribute : nullToEmpty(entity.getAttributes())) {
            attributes.add(toWire(attribute));
         }

         entities.add(new Entity(entity.getName(), entity.getOldName(), entity.getDescription(),
                                 entity.isVisible(), entity.isBaseElement(),
                                 entity.getErrorMessage(), attributes));
      }

      return new Definition(dto.getName(), dto.getDescription(), dto.getPartition(),
                            blankToNull(dto.getFolder()), blankToNull(dto.getConnection()),
                            blankToNull(dto.getParent()), extended, entities);
   }

   private Attribute toWire(XAttributeModel model) {
      boolean expression = WizLogicalModelWire.TYPE_EXPRESSION.equals(model.getType());
      RefTypeModel refType = model.getRefType();
      int code = refType == null ? 0 : refType.getType();
      String refTypeName = WizLogicalModelWire.REF_TYPES.get(
         code >= 0 && code < WizLogicalModelWire.REF_TYPES.size() ? code : 0);

      return new Attribute(
         model.getName(), model.getOldName(), model.getDescription(), model.isVisible(),
         expression ? WizLogicalModelWire.TYPE_EXPRESSION : WizLogicalModelWire.TYPE_COLUMN,
         expression ? null : model.getTable(), expression ? null : model.getColumn(),
         expression ? model.getExpression() : null,
         expression ? model.isAggregate() : null, expression ? model.isParseable() : null,
         model.getDataType(), refTypeName,
         refType == null ? null : formulaFromStored(refType.getFormula()),
         model.isBrowseData(), model.getBrowseQuery(), toWire(model.getFormat()),
         toWire(model.getDrillInfo()), model.isBaseElement(), model.getErrorMessage());
   }

   /** A format with neither a format nor a spec is "no format" (the service always creates one). */
   private Format toWire(XFormatInfoModel format) {
      if(format == null || format.getFormat() == null && format.getFormatSpec() == null) {
         return null;
      }

      // the model already folds the unpadded duration spelling into durationPadZeros=false
      boolean duration = XConstants.DURATION_FORMAT.equals(format.getFormat());
      return new Format(format.getFormat(), format.getFormatSpec(),
                        duration ? format.isDurationPadZeros() : null);
   }

   private Drill toWire(AutoDrillInfo info) {
      if(info == null || info.getPaths() == null || info.getPaths().isEmpty()) {
         return null;
      }

      List<WizLogicalModelWire.DrillPath> paths = new ArrayList<>();

      for(AutoDrillPathModel path : info.getPaths()) {
         List<DrillParam> params = new ArrayList<>();

         for(DrillParameterModel param : nullToEmpty(path.getParams())) {
            params.add(new DrillParam(param.getName(), param.getField(), param.getType()));
         }

         DrillQuery query = null;

         if(path.getQuery() != null && path.getQuery().getEntry() != null) {
            List<DrillQueryParam> kv = new ArrayList<>();

            for(CommonKVModel<String, String> param : nullToEmpty(path.getQuery().getParams())) {
               kv.add(new DrillQueryParam(param.getKey(), param.getValue()));
            }

            query = new DrillQuery(path.getQuery().getEntry().toIdentifier(), kv);
         }

         paths.add(new WizLogicalModelWire.DrillPath(
            path.getName(),
            path.getLinkType() == DrillPath.VIEWSHEET_LINK ? WizLogicalModelWire.LINK_VIEWSHEET :
               WizLogicalModelWire.LINK_WEB,
            path.getLink(), path.getTargetFrame(), path.getTip(), path.isPassParams(),
            path.isDisablePrompting(), params, query, null));
      }

      return new Drill(paths);
   }

   private static String blankToNull(String value) {
      return Tool.isEmptyString(value) ? null : value;
   }

   private static <T> List<T> nullToEmpty(List<T> list) {
      return list == null ? List.of() : list;
   }
}
