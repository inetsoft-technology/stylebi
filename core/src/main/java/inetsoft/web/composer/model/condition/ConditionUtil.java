/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.composer.model.condition;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.WorksheetService;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.composition.graph.calc.ChangeCalc;
import inetsoft.report.internal.binding.AggregateField;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.jdbc.XFilterNode;
import inetsoft.uql.schema.*;
import inetsoft.uql.viewsheet.graph.Calculator;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.model.BAggregateRefModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.BrowseDataController;
import inetsoft.web.composer.model.BrowseDataModel;
import inetsoft.web.portal.model.database.Operation;

import java.security.Principal;
import java.util.*;

/**
 * Common utility methods for converting a condition list to and from a model
 */
public class ConditionUtil {
   /**
    * Converts the model to an actual ConditionList object
    *
    * @param conditionListModel condition list model
    * @param sourceInfo         source info of the assembly that contains the given condition list
    * @param principal          user
    * @return a ConditionList object
    * @throws Exception
    */
   public static ConditionList fromModelToConditionList(
      Object[] conditionListModel, SourceInfo sourceInfo, WorksheetService worksheetService,
      Principal principal) throws Exception
   {
      return fromModelToConditionList(
         conditionListModel, sourceInfo, worksheetService, principal, null);
   }

   /**
    * Converts the model to an actual ConditionList object
    *
    * @param conditionListModel condition list model
    * @param sourceInfo         source info of the assembly that contains the given condition list
    * @param principal          user
    * @param rws                runtime worksheet
    * @return a ConditionList object
    * @throws Exception
    */
   public static ConditionList fromModelToConditionList(
      Object[] conditionListModel, SourceInfo sourceInfo, WorksheetService worksheetService,
      Principal principal, RuntimeWorksheet rws) throws Exception
   {
      return fromModelToConditionList(
         conditionListModel, sourceInfo, worksheetService, principal, rws, null);
   }

   /**
    * Converts the model to an actual ConditionList object
    *
    * @param conditionListModel condition list model
    * @param sourceInfo         source info of the assembly that contains the given condition list
    * @param principal          user
    * @param rws                runtime worksheet
    * @param tName              table name
    * @return a ConditionList object
    * @throws Exception
    */
   public static ConditionList fromModelToConditionList(
      Object[] conditionListModel, SourceInfo sourceInfo, WorksheetService worksheetService,
      Principal principal, RuntimeWorksheet rws, String tName) throws Exception
   {
      return fromModelToConditionList(conditionListModel, sourceInfo, worksheetService,
         principal, rws, tName, false);
   }

   /**
    * Converts the model to an actual ConditionList object
    *
    * @param conditionListModel condition list model
    * @param sourceInfo         source info of the assembly that contains the given condition list
    * @param principal          user
    * @param rws                runtime worksheet
    * @param tName              table name
    * @return a ConditionList object
    * @throws Exception
    */
   public static ConditionList fromModelToConditionList(Object[] conditionListModel,
      SourceInfo sourceInfo, WorksheetService worksheetService, Principal principal,
      RuntimeWorksheet rws, String tName, boolean aggUseAggField) throws Exception
   {
      ConditionList conditionList = new ConditionList();

      for(int i = 0; i < conditionListModel.length; i++) {
         if(i % 2 == 0) {
            ConditionModel conditionModel = (ConditionModel) conditionListModel[i];
            DataRefModel conditionField = conditionModel.getField();
            String dataType = conditionField.getDataType();
            ConditionValueModel[] valueModels = conditionModel.getValues();
            XCondition condition = null;

            if(valueModels != null && valueModels.length > 0 && valueModels[0] != null) {
               // if condition operation is DATE_IN then we need to create a DateCondition
               if(conditionModel.getOperation() == AbstractCondition.DATE_IN
                  && valueModels[0].getType().equals(ConditionValueModel.VALUE))
               {
                  String value = (String) valueModels[0].getValue();

                  // look for a built in condition with this name and clone it
                  for(DateCondition dateCondition : DateCondition.getBuiltinDateConditions()) {
                     if(dateCondition.getName().equals(value)) {
                        condition = (XCondition) dateCondition.clone();
                        break;
                     }
                  }

                  // if no built in condition found then it must be a DateRangeAssembly
                  if(condition == null && rws != null) {
                     Worksheet ws = rws.getWorksheet();

                     if(ws.getAssembly(value) instanceof DateRangeAssembly) {
                        condition = ((DateRangeAssembly) ws.getAssembly(value))
                           .getDateRange().clone();
                     }
                  }
               }
               else if(conditionModel.getOperation() == AbstractCondition.TOP_N ||
                  conditionModel.getOperation() == AbstractCondition.BOTTOM_N)
               {
                  RankingValueModel rankingValueModel = (RankingValueModel) valueModels[0]
                     .getValue();
                  RankingCondition rankingCondition = new RankingCondition();
                  rankingCondition.setN(rankingValueModel.getN());
                  rankingCondition.setDataRef(getDataRef(rankingValueModel.getDataRef(), sourceInfo,
                                                         worksheetService, principal));
                  condition = rankingCondition;
               }
            }

            if(condition == null) {
               List<Object> values = new ArrayList<>();

               if(valueModels != null) {
                  for(ConditionValueModel valueModel : valueModels) {
                     Object value = valueModel.getValue();

                     if(valueModel.getType().equals(ConditionValueModel.SUBQUERY)) {
                        SubqueryValueModel subqueryModel = (SubqueryValueModel) value;
                        SubQueryValue subquery = new SubQueryValue();
                        subquery.setQuery(subqueryModel.getQuery());

                        if(rws != null) {
                           Worksheet ws = rws.getWorksheet();

                           TableAssembly queryAssembly =
                              (TableAssembly) ws.getAssembly(subqueryModel.getQuery());
                           ColumnSelection queryColumns = queryAssembly != null ?
                              queryAssembly.getColumnSelection() : null;

                           if(queryColumns != null) {
                              subquery.setAttribute(queryColumns.getAttribute(
                                 subqueryModel.getAttribute().getName()));

                              if(subquery.getAttribute() == null) {
                                 subquery.setAttribute(getDataRef(subqueryModel.getAttribute(), queryColumns));
                              }

                              if(subqueryModel.getSubAttribute() != null) {
                                 subquery.setSubAttribute(queryColumns.getAttribute(
                                    subqueryModel.getSubAttribute().getName()));

                                 if(subquery.getSubAttribute() == null) {
                                    subquery.setSubAttribute(getDataRef(subqueryModel.getSubAttribute(), queryColumns));
                                 }
                              }
                           }

                           if(subqueryModel.getMainAttribute() != null) {
                              subquery.setMainAttribute(getDataRef(
                                 subqueryModel.getMainAttribute(), sourceInfo,
                                 worksheetService, principal));
                           }
                        }

                        value = subquery;
                     }
                     else if(valueModel.getType().equals(ConditionValueModel.EXPRESSION)) {
                        ExpressionValueModel exprModel = (ExpressionValueModel) value;
                        ExpressionValue expr = new ExpressionValue();
                        expr.setExpression(exprModel.getExpression());
                        expr.setType(exprModel.getType() == ExpressionValueModel.SQL ?
                           ExpressionValue.SQL : ExpressionValue.JAVASCRIPT);
                        value = expr;
                     }
                     else if(valueModel.getType().equals(ConditionValueModel.FIELD)) {
                        value = getDataRef((DataRefModel) value, sourceInfo,
                                           worksheetService, principal);
                     }
                     else if(valueModel.getType().equals(ConditionValueModel.VALUE) &&
                        XSchema.isDateType(dataType))
                     {
                        value = AbstractCondition.getObject(dataType, (String) value);
                     }
                     else if((valueModel.getType().equals(ConditionValueModel.VARIABLE) ||
                        valueModel.getType().equals(ConditionValueModel.SESSION_DATA)) &&
                        value instanceof String && ((String) value).startsWith("$(") &&
                        ((String) value).endsWith(")"))
                     {
                        String variableName = ((String) value)
                           .substring(2, ((String) value).length() - 1);
                        UserVariable userVariable = new UserVariable();
                        userVariable.setName(variableName);
                        userVariable.setAlias(variableName);
                        userVariable.setChoiceQuery(
                           valueModel.getChoiceQuery() != null &&
                           !"".equals(valueModel.getChoiceQuery()) ?
                           valueModel.getChoiceQuery() : null);

                        if(XSchema.DATE.equals(dataType)) {
                           userVariable.setValueNode(new DateValue(variableName));
                        }
                        else {
                           userVariable.setValueNode(new StringValue(variableName));
                        }

                        userVariable.setUsedInOneOf(
                           conditionModel.getOperation() == XCondition.ONE_OF);

                        if(userVariable.getChoiceQuery() != null && rws != null && tName != null) {
                           BrowseDataController browseDataController = new BrowseDataController();
                           DataRef dataRef =
                              conditionField instanceof BAggregateRefModel && aggUseAggField ?
                                 ((BAggregateRefModel) conditionField).createAggregateField() :
                                 conditionField.createDataRef();

                           if(!(dataRef instanceof ColumnRef)) {
                              dataRef = new ColumnRef(dataRef);
                           }

                           browseDataController.setColumn((ColumnRef) dataRef);
                           browseDataController.setName(tName);
                           browseDataController.setSourceInfo(sourceInfo);
                           final BrowseDataModel data =
                              browseDataController.process(rws.getAssetQuerySandbox());

                           userVariable.setValues(data.values());
                           userVariable.setChoices(data.values());
                           userVariable.setDataTruncated(data.dataTruncated());
                           userVariable.setExecuted(true);
                        }

                        value = userVariable;
                     }

                     if(Tool.FLOAT.equals(dataType) && value instanceof Double) {
                        value = Float.parseFloat(value.toString());
                     }

                     if(value instanceof String && !Tool.STRING.equals(dataType)) {
                        value = Tool.getData(dataType, value);
                     }

                     values.add(value);
                  }
               }

               condition = new AssetCondition();
               ((AssetCondition) condition).setValues(values);
               ((AssetCondition) condition).setMillisInFormatRequired(Tool.useDatetimeWithMillisFormat.get());
            }

            condition.setOperation(conditionModel.getOperation());
            condition.setEqual(conditionModel.isEqual());
            condition.setNegated(conditionModel.isNegated());
            condition.setType(conditionModel.getField().getDataType());
            DataRef field = getDataRef(conditionModel.getField(), sourceInfo,
                                       worksheetService, principal, aggUseAggField);

            if(field instanceof AggregateField) {
               AggregateField agg = (AggregateField) field;
               Calculator calculator = agg.getCalculator();

               if(calculator instanceof ChangeCalc && calculator.isPercent()) {
                  condition.setType(XSchema.DOUBLE);
               }
            }

            ConditionItem conditionItem = new ConditionItem();
            conditionItem.setLevel(conditionModel.getLevel());
            conditionItem.setXCondition(condition);
            conditionItem.setAttribute(field);
            conditionList.append(conditionItem);
         }
         else {
            JunctionOperatorModel operatorModel =
               (JunctionOperatorModel) conditionListModel[i];
            JunctionOperator operator = new JunctionOperator(operatorModel.getType(),
               operatorModel.getLevel());
            conditionList.append(operator);
         }
      }

      return conditionList;
   }

   private static DataRef getDataRef(DataRefModel dataRefModel, ColumnSelection columns) {
      if(dataRefModel == null || columns == null) {
         return null;
      }

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         DataRef attribute = columns.getAttribute(i);

         if(attribute == null) {
            continue;
         }

         if(Tool.equals(attribute.getEntity(), dataRefModel.getEntity()) &&
            Tool.equals(dataRefModel.getAttribute(), attribute.getAttribute()))
         {
            return attribute;
         }
      }

      return null;
   }

   /**
    * Converts a condition list to a condition list model that is represented by
    * an object array where even indexes contain condition models and odd indexes
    * contain junction models.
    *
    * @param conditionList          a condition list object
    * @param refModelFactoryService responsible for creating models from data refs
    * @return
    */
   public static Object[] fromConditionListToModel(ConditionList conditionList,
      DataRefModelFactoryService refModelFactoryService)
   {
      Object[] conditionListModel = new Object[conditionList.getSize()];

      for(int i = 0; i < conditionList.getSize(); i++) {
         if(i % 2 == 0) {
            ConditionItem conditionItem = conditionList.getConditionItem(i);
            DataRef field = conditionItem.getAttribute();
            XCondition condition = conditionItem.getXCondition();

            DataRefModel fieldModel = refModelFactoryService.createDataRefModel(field);
            ConditionValueModel[] valueModels = null;

            if(condition instanceof Condition) {
               List values = ((Condition) condition).getValues();
               valueModels = new ConditionValueModel[values.size()];

               for(int j = 0; j < values.size(); j++) {
                  Object value = values.get(j);
                  ConditionValueModel valueModel = new ConditionValueModel();

                  if(value instanceof SubQueryValue) {
                     SubQueryValue subquery = (SubQueryValue) value;
                     SubqueryValueModel subqueryModel = new SubqueryValueModel();
                     DataRefModel attrRef = refModelFactoryService
                        .createDataRefModel(subquery.getAttribute());
                     DataRefModel subAttrRef = refModelFactoryService
                        .createDataRefModel(subquery.getSubAttribute());
                     DataRefModel mainAttrRef = refModelFactoryService
                        .createDataRefModel(subquery.getMainAttribute());
                     subqueryModel.setQuery(subquery.getQuery());
                     subqueryModel.setAttribute(attrRef);
                     subqueryModel.setSubAttribute(subAttrRef);
                     subqueryModel.setMainAttribute(mainAttrRef);
                     valueModel.setValue(subqueryModel);
                     valueModel.setType(ConditionValueModel.SUBQUERY);
                  }
                  else if(value instanceof ExpressionValue) {
                     ExpressionValue expr = (ExpressionValue) value;
                     ExpressionValueModel exprModel = new ExpressionValueModel();
                     exprModel.setExpression(expr.getExpression());
                     exprModel.setType(expr.getType().equals(ExpressionValue.SQL) ?
                        ExpressionValueModel.SQL : ExpressionValueModel.JS);
                     valueModel.setValue(exprModel);
                     valueModel.setType(ConditionValueModel.EXPRESSION);
                  }
                  else if(Condition.isSessionVariable(value)) {
                     if(value instanceof UserVariable) {
                        valueModel.setValue(value.toString());
                     }
                     else {
                        valueModel.setValue(value);
                     }

                     valueModel.setType(ConditionValueModel.SESSION_DATA);
                  }
                  else if(Condition.isVariable(value)) {
                     if(value instanceof UserVariable) {
                        valueModel
                           .setValue("$(" + ((UserVariable) value).getName() + ")");
                        valueModel
                           .setChoiceQuery(((UserVariable) value).getChoiceQuery());
                     }
                     else {
                        valueModel.setValue(value);
                     }

                     valueModel.setType(ConditionValueModel.VARIABLE);
                  }
                  else if(value instanceof DataRef) {
                     valueModel.setValue(
                        refModelFactoryService.createDataRefModel((DataRef) value));
                     valueModel.setType(ConditionValueModel.FIELD);
                  }
                  else {
                     if(value instanceof Date) {
                        valueModel.setValue(AbstractCondition.getValueString(value));
                     }
                     else {
                        valueModel.setValue(value);
                     }

                     valueModel.setType(ConditionValueModel.VALUE);
                  }

                  valueModels[j] = valueModel;
               }
            }
            else if(condition instanceof DateCondition) {
               valueModels = new ConditionValueModel[1];
               valueModels[0] = new ConditionValueModel();
               valueModels[0].setValue(((DateCondition) condition).getName());
               valueModels[0].setType(ConditionValueModel.VALUE);
            }
            else if(condition instanceof RankingCondition) {
               RankingCondition rankingCondition = (RankingCondition) condition;
               valueModels = new ConditionValueModel[1];
               valueModels[0] = new ConditionValueModel();
               RankingValueModel rankingValueModel =
                  new RankingValueModel();
               rankingValueModel.setDataRef(refModelFactoryService
                  .createDataRefModel(rankingCondition.getDataRef()));

               if(rankingCondition.getN() instanceof UserVariable) {
                  rankingValueModel.setN( "$(" + ((UserVariable) rankingCondition.getN()).getName() + ")");
                  valueModels[0].setType(ConditionValueModel.VARIABLE);
               }
               else if(Condition.isVariable(rankingCondition.getN())) {
                  rankingValueModel.setN(rankingCondition.getN());
                  valueModels[0].setType(ConditionValueModel.VARIABLE);
               }
               else {
                  rankingValueModel.setN(rankingCondition.getN());
                  valueModels[0].setType(ConditionValueModel.VALUE);
               }

               valueModels[0].setValue(rankingValueModel);
            }

            ConditionModel conditionModel = new ConditionModel();
            conditionModel.setField(fieldModel);
            conditionModel.setOperation(condition.getOperation());
            conditionModel.setValues(valueModels);
            conditionModel.setLevel(conditionItem.getLevel());
            conditionModel.setEqual(condition.isEqual());
            conditionModel.setNegated(condition.isNegated());
            conditionListModel[i] = conditionModel;
         }
         else {
            JunctionOperator operator = conditionList.getJunctionOperator(i);
            JunctionOperatorModel operatorModel = new JunctionOperatorModel();
            operatorModel.setType(operator.getJunction());
            operatorModel.setLevel(operator.getLevel());
            conditionListModel[i] = operatorModel;
         }
      }

      return conditionListModel;
   }

   public static DataRef getDataRef(DataRefModel dataRefModel, SourceInfo sourceInfo,
      WorksheetService worksheetService ,Principal principal) throws Exception
   {
      return getDataRef(dataRefModel, sourceInfo, worksheetService, principal, false);
   }

   public static DataRef getDataRef(DataRefModel dataRefModel, SourceInfo sourceInfo,
                                    WorksheetService worksheetService ,Principal principal,
                                    boolean aggUseAggField)
      throws Exception
   {
      if(dataRefModel == null) {
         return null;
      }

      DataRef dataRef =
         dataRefModel instanceof BAggregateRefModel && aggUseAggField ?
            ((BAggregateRefModel) dataRefModel).createAggregateField() :
            dataRefModel.createDataRef();

      if(sourceInfo != null) {
         DataRef dataRef0 = getOriginalDataRef(dataRef, sourceInfo, worksheetService, principal);
         dataRef = dataRef0 != null ? dataRef0 : dataRef;
      }

      return dataRef;
   }

   /**
    * Gets the original DataRef from the DataRefModel
    */
   public static DataRef getOriginalDataRef(
      DataRef dataRef, SourceInfo sourceInfo, WorksheetService worksheetService,
      Principal principal) throws Exception
   {
      ColumnSelection columns = AssetEventUtil.getAttributesBySource(
         worksheetService, principal, sourceInfo);

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         DataRef ref0 = columns.getAttribute(i);

         if(ref0.toView().equals(dataRef.toView()) && ref0.getClass().isInstance(dataRef)) {
            return ref0;
         }
      }

      return null;
   }

   public static DataRefModel[] getVisibleDataRefModelsFromColumnSelection(
      ColumnSelection columns,
      DataRefModelFactoryService dataRefModelFactoryService)
   {
      return getDataRefModelsFromColumnSelection(columns, dataRefModelFactoryService, 0, true, true);
   }

   public static DataRefModel[] getDataRefModelsFromColumnSelection(
      ColumnSelection columns,
      DataRefModelFactoryService dataRefModelFactoryService, int keepOrder)
   {
      return getDataRefModelsFromColumnSelection(columns, dataRefModelFactoryService, keepOrder, true, true);
   }

   public static DataRefModel[] getDataRefModelsFromColumnSelection(
      ColumnSelection columns,
      DataRefModelFactoryService dataRefModelFactoryService,
      int keepOrder, boolean sort, boolean includeHiddenFields)
   {
      List<DataRefModel> visibleRefs = new ArrayList<>();
      List<DataRefModel> hiddenRefs = new ArrayList<>();

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         DataRef ref = columns.getAttribute(i);
         boolean visible = !(ref instanceof ColumnRef) || ((ColumnRef) ref).isVisible();

         if(VSUtil.isPreparedCalcField(ref) || !includeHiddenFields && !visible) {
            continue;
         }

         DataRefModel refModel = dataRefModelFactoryService.createDataRefModel(columns.getAttribute(i));

         if(refModel != null) {
            if(refModel.getName().isEmpty()) {
               refModel.setView("Column[" + i + "]");
            }
            else if(refModel.getAttribute().isEmpty()) {
               refModel.setView(refModel.getView() + "Column[" + i + "]");
            }

            if(visible) {
               visibleRefs.add(refModel);
            }
            else {
               hiddenRefs.add(refModel);
            }
         }
      }

      if(sort) {
         int keepVisibleOrder = keepOrder > 0 ? keepOrder - hiddenRefs.size() : keepOrder;
         visibleRefs.subList(keepVisibleOrder, visibleRefs.size())
            .sort(Comparator.comparing(a -> a.getView().toLowerCase()));
         hiddenRefs.sort(Comparator.comparing(a -> a.getView().toLowerCase()));
      }

      // please visible fields in the front
      // hidden fields should follow immediately after visible fields,
      // so insert hidden fields at the specified index.
      int insertIndex = keepOrder > 0 ? keepOrder - hiddenRefs.size() : visibleRefs.size();
      visibleRefs.addAll(insertIndex, hiddenRefs);

      return visibleRefs.toArray(new DataRefModel[0]);
   }

   /**
    * Remove Duplicate Conditions.
    * @param condition ConditionList
    * @return ConditionList that no duplicate.
    */
   public static ConditionList removeDuplicateConditions(ConditionList condition) {
      if(inetsoft.uql.asset.internal.ConditionUtil.isEmpty(condition)) {
         return condition;
      }

      Set<String> refNames = new HashSet<>();

      for(int i = condition.getConditionSize() - 1; i >= 0; i--) {
         if(condition.isConditionItem(i)) {
            ConditionItem conditionItem = condition.getConditionItem(i);
            DataRef ref = conditionItem.getAttribute();
            String key = ref.getEntity() + ref.getName()
               + conditionItem.getXCondition();

            if(refNames.contains(key)) {
               condition.removeConditionItem(i);
            }
            else {
               refNames.add(key);
            }
         }
      }

      return  condition;
   }

   /**
    * Condition operations are used for vpm conditions or ws query table conditions.
    */
   public static List<Operation> getOperationList() {
      List<Operation> operations = new ArrayList<>();
      String[] names = XFilterNode.getAllOperators();

      for(String name : names) {
         if(name.equals(Catalog.getCatalog().getString("one of")) ||
            name.equals(Catalog.getCatalog().getString("not equal to")))
         {
            continue;
         }

         String symbol = XFilterNode.getOpSymbol(name);

         if(">=".equals(symbol) || "<=".equals(symbol)) {
            continue;
         }

         Operation op = new Operation();
         op.setName(name);
         op.setSymbol(XFilterNode.getOpSymbol(name));
         operations.add(op);
      }

      return operations;
   }
}
