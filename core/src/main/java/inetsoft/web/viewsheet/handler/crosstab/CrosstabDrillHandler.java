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
package inetsoft.web.viewsheet.handler.crosstab;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.TableLens;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.composition.graph.calc.RunningTotalCalc;
import inetsoft.report.composition.graph.calc.ValueOfCalc;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XNamedGroupInfo;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.Calculator;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import inetsoft.web.viewsheet.controller.table.BaseTableDrillController;
import inetsoft.web.viewsheet.handler.BaseDrillHandler;
import inetsoft.web.viewsheet.model.*;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.Principal;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

@Component
public class CrosstabDrillHandler
   extends BaseDrillHandler<CrosstabVSAssembly, CrosstabDrillFilterAction>
{
   @Autowired
   public CrosstabDrillHandler(ViewsheetService viewsheetService,
                               CoreLifecycleService coreLifecycleService,
                               RuntimeViewsheetRef runtimeViewsheetRef)
   {
      this.viewsheetService = viewsheetService;
      this.coreLifecycleService = coreLifecycleService;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
   }

   @Override
   public void processDrillFilter(CrosstabVSAssembly assembly,
                                  CrosstabDrillFilterAction drillFilterInfo,
                                  CommandDispatcher dispatcher, String linkUri,
                                  Principal principal)
      throws Exception
   {
      if(drillFilterInfo.isInvalid()) {
         return;
      }

      RuntimeViewsheet rvs = viewsheetService.getViewsheet(
         runtimeViewsheetRef.getRuntimeId(), principal);

      processDrillFilter0(rvs, assembly, drillFilterInfo);
   }

   private void processDrillFilter0(RuntimeViewsheet rvs, CrosstabVSAssembly assembly,
                                    CrosstabDrillFilterAction drillAction) throws Exception
   {
      if(drillAction.isDrillUp()) {
         processDrillUpFilter(assembly, drillAction);
      }
      else {
         processDrillDownFilter(rvs, assembly, drillAction);
      }
   }

   private void processDrillDownFilter(RuntimeViewsheet rvs, CrosstabVSAssembly assembly,
                                       CrosstabDrillFilterAction drillAction)
      throws Exception
   {
      Map<String, ConditionList> ndrillConds = new LinkedHashMap<>();

      for(CrosstabDrillFilterAction.DrillCellInfo cellInfo : drillAction.getCellInfos()) {
         ConditionList conditionList = createDrillCondition(rvs, assembly, cellInfo);
         DataRef[] rows = assembly.getVSCrosstabInfo().getRuntimeRowHeaders();
         DataRef[] cols = assembly.getVSCrosstabInfo().getRuntimeColHeaders();
         CrosstabTree ctree = assembly.getCrosstabTree();
         VSDimensionRef ref;

         String field = cellInfo.getField();

         ref = getDataRefByField(ctree, Arrays.asList(rows), field);

         if(ref == null) {
            ref = getDataRefByField(ctree, Arrays.asList(cols), field);
         }

         String name = CrosstabTree.getDrillFilterName(ref, assembly.getXCube(), true);
         conditionList = replaceNamedGroup(conditionList, assembly);
         name = NamedRangeRef.getBaseName(name);
         mergeDrillFilterConditionList(ndrillConds, name, conditionList);
      }

      updateDrillFilterAssemblyCondition(ndrillConds, assembly);
   }

   private void processDrillUpFilter(CrosstabVSAssembly assembly,
                                     CrosstabDrillFilterAction drillAction)
   {
      drillAction.getFields().stream().distinct().forEach(field -> {
         VSDimensionRef fieldRef = getDataRefByField(assembly.getCrosstabTree(),
            Arrays.asList(assembly.getBindingRefs()), field);
         // using assembly.getViewsheet() for get embedded assemblies
         removeDrillFilter(fieldRef, assembly.getViewsheet());
      });
   }

   private ConditionList createDrillCondition(RuntimeViewsheet rvs, CrosstabVSAssembly assembly,
                                              CrosstabDrillFilterAction.DrillCellInfo cellInfo)
      throws Exception
   {
      return VSUtil.getConditionList(rvs, assembly, cellInfo.getSelectionString(), true);
   }

   @Override
   public void processDrillAction(CrosstabVSAssembly table, DrillFilterAction drillFilterAction,
                                  DrillFilterVSAssembly targetAssembly,
                                  CommandDispatcher dispatcher, String linkUri,
                                  Principal principal)
      throws Exception
   {
      SourceInfo src = table.getSourceInfo();

      if(drillFilterAction.isInvalid() || src == null) {
         return;
      }

      RuntimeViewsheet rvs = viewsheetService.getViewsheet(
         runtimeViewsheetRef.getRuntimeId(), principal);
      final ViewsheetSandbox box = rvs.getViewsheetSandbox();
      VSTableLens lens = box.getVSTableLens(table.getName(), false);

      for(String field : drillFilterAction.getFields()) { // for chart
         drillAllField(true, drillFilterAction.isDrillUp(), field,
                       table, this::drillChildEnabled, false, lens);
      }
   }

   public void drillAllField(boolean replace, boolean drillUp, String field,
                             CrosstabVSAssembly assembly,
                             BiFunction<XCube, VSDimensionRef, Boolean> allowExpandLevel,
                             boolean recursive, VSTableLens lens)
   {
      drillAllField(replace, drillUp, field, assembly, allowExpandLevel, recursive, false, lens);
   }

   public XCube getCube(CrosstabVSAssembly table) {
      XCube cube = table.getXCube();

      if(cube == null) {
         SourceInfo src = table.getSourceInfo();
         cube = AssetUtil.getCube(src.getPrefix(), src.getSource());
      }

      return cube;
   }

   /**
    * drill field
    * @param replace whether to replace data ref
    * @param field drill field name
    * @param assembly drill target assembly
    * @param allowExpandLevel allows expansion to the specified level
    * @param recursive whether to recurse drill field
    */
   public void drillAllField(boolean replace, boolean drillUp, String field,
                             CrosstabVSAssembly assembly,
                             BiFunction<XCube, VSDimensionRef, Boolean> allowExpandLevel,
                             boolean recursive, boolean drillAllCell, VSTableLens lens)
   {
      VSCrosstabInfo cinfo = assembly.getVSCrosstabInfo();
      DataRef[] rows = cinfo.getRuntimeRowHeaders();
      DataRef[] cols = cinfo.getRuntimeColHeaders();
      List<DataRef> rowsList = new ArrayList<>(Arrays.asList(rows));
      List<DataRef> colsList = new ArrayList<>(Arrays.asList(cols));
      CrosstabTree crosstabTree = assembly.getCrosstabTree();
      XCube cube = getCube(assembly);
      DataRef[] newRows;
      DataRef[] newCols;

      if(drillUp) {
         if(replace) {
            drillUpAction(cinfo, field, cube, crosstabTree, rowsList);
            drillUpAction(cinfo, field, cube, crosstabTree, colsList);
         }
         else {
            // clear drill status of current filed.
            crosstabTree.removeDrill(field);
            // remove child
            field = crosstabTree.getNextField(field);
            removeFieldRefs(crosstabTree, rowsList, field);
            removeFieldRefs(crosstabTree, colsList, field);
         }

         newRows = rowsList.toArray(new DataRef[0]);
         newCols = colsList.toArray(new DataRef[0]);
      }
      else {
         Set<String> childFields = getRefFields(rowsList, crosstabTree);
         newRows = drillDownField(crosstabTree, assembly, cube, rowsList, childFields, field,
                                  replace, allowExpandLevel, recursive, drillAllCell);

         childFields = getRefFields(colsList, crosstabTree);
         newCols = drillDownField(crosstabTree, assembly, cube, colsList, childFields, field,
                                  replace, allowExpandLevel, recursive, drillAllCell);
      }

      if(newRows != null && dataRefChanged(newRows, cinfo.getDesignRowHeaders())) {
         newRows = fixDesignHeaders(newRows);
         cinfo.setDesignRowHeaders(newRows);
         cinfo.setRuntimeRowHeaders(newRows);
      }

      if(newCols != null && dataRefChanged(newCols, cinfo.getDesignColHeaders())) {
         newCols = fixDesignHeaders(newCols);
         cinfo.setDesignColHeaders(newCols);
         cinfo.setRuntimeColHeaders(newCols);
      }

      BaseTableDrillController.saveColumnInfo(assembly.getCrosstabInfo(), lens);
   }

   private void drillUpAction(VSCrosstabInfo cinfo, String field, XCube cube,
                              CrosstabTree crosstabTree, List<DataRef> refs)
   {
      VSDimensionRef ref = getDataRefByField(crosstabTree, refs, field);

      if(ref == null) {
         return;
      }

      int index = VSUtil.findIndex(refs.toArray(new DataRef[0]), ref);
      VSDimensionRef parentRef = VSUtil.getLastDrillLevelRef(ref, cube);

      if(parentRef == null) {
         return;
      }

      VSDimensionRef grandpaRef = VSUtil.getLastDrillLevelRef(parentRef, cube);
      String upKey;

      if(grandpaRef == null) {
         upKey = CrosstabTree.getHierarchyRootKey(parentRef);

         //If parent is root, then set ranking values
         parentRef.setRankingOptionValue(parentRef.getRootRankingOption());
         parentRef.setRootRankingOption(null);

         if(parentRef.getDrillRootOrder() != -1) {
            parentRef.setOrder(parentRef.getDrillRootOrder());
            parentRef.setDrillRootOrder(-1);
         }
      }
      else {
         upKey = grandpaRef.getFullName();
      }

      VSDimensionRef dimRef = crosstabTree.getChildRef(upKey);

      if(dimRef == null) {
         dimRef = parentRef;

         if(dimRef != null && VSUtil.isVariableValue(dimRef.getDateLevelValue())) {
            int level = dimRef.getDateLevel();
            dimRef.setDateLevelValue(ref.getDateLevelValue());
            dimRef.setDateLevel(level);
         }
      }

      if(index < 0) {
         return;
      }

      replaceDimension(refs, index, dimRef);
      updateCalcuators(cinfo, ref, dimRef);
      removeDuplicates(refs);
   }

   public void replaceDimension(List<DataRef> refs, int index, VSDimensionRef targetRef) {
      String targetName = targetRef.getFullName();

      if(index >= 0 && index < refs.size()) {
         for(int i = 0; i < refs.size(); i++) {
            DataRef dataRef = refs.get(i);

            if(!(dataRef instanceof VSDimensionRef)) {
               continue;
            }

            // if col is exists.
            if(((VSDimensionRef) dataRef).getFullName().equals(targetName)) {
               // remove child.
               refs.remove(index);
               return;
            }
         }
      }

      refs.set(index, targetRef);
   }

   private void updateCalcuators(VSCrosstabInfo cinfo, VSDimensionRef oldRef,
                                 VSDimensionRef newRef)
   {
      if(cinfo == null || cinfo.getAggregates() == null || oldRef == null || newRef == null) {
         return;
      }

      String oldName = oldRef.getFullName();
      String newName = newRef.getFullName();

      for(DataRef ref : cinfo.getAggregates()) {
         if(!(ref instanceof VSAggregateRef)) {
            continue;
         }

         VSAggregateRef aggr = (VSAggregateRef) ref;
         Calculator calc = aggr.getCalculator();

         if(calc instanceof ValueOfCalc) {
            ValueOfCalc vcalc = (ValueOfCalc) calc;

            if(Objects.equals(vcalc.getColumnName(), oldName)) {
               vcalc.setColumnName(newName);
            }
         }

         if(calc instanceof RunningTotalCalc){
            RunningTotalCalc rcalc = (RunningTotalCalc) calc;

            if(Objects.equals(rcalc.getBreakBy(), oldName)) {
               rcalc.setBreakBy(newName);
            }
         }
      }
   }

   public static ConditionList getGroupCondition(XNamedGroupInfo groupInfo, ColumnRef columnRef,
                                                 String name)
   {
      ConditionList conditionList = new ConditionList();

      if(!(groupInfo instanceof SNamedGroupInfo)) {
         return conditionList;
      }

      SNamedGroupInfo info = (SNamedGroupInfo) groupInfo;
      List<?> value = info.getGroupValue(name);

      if(value == null || value.size() == 0) {
         return conditionList;
      }

      for(int i = 0; i < value.size(); i++) {
         Condition cond = new Condition(columnRef.getDataType());
         Object ovalue = value.get(i);

         if(ovalue instanceof String) {
            Object val = Tool.getData(columnRef.getDataType(), (String) ovalue);
            int operation = val == null ? XCondition.NULL : XCondition.EQUAL_TO;
            cond.setOperation(operation);

            if(val != null) {
               cond.addValue(val);
            }
         }
         else {
            cond.addValue(ovalue);
         }

         ConditionItem item = new ConditionItem(columnRef, cond, 0);

         if(i != 0) {
            conditionList.append(new JunctionOperator(JunctionOperator.OR, 0));
         }

         conditionList.append(item);
      }

      return conditionList;
   }

   /**
    * Remove the duplicate dim (with variable) from the list.
    */
   private void removeDuplicates(List<DataRef> reflist) {
      Set<String> dynamicGroups = new HashSet<>();
      Set<String> dateDGroups = new HashSet<>();
      Set<String> dateRTGroups = new HashSet<>();

      // dynamic field can generate multiple runtime fields. the following logic removes
      // the duplicates: (49751)
      // 1. if a dynamic value generated more than one field, only one field is kept.
      // 2. for dates, multiple dynamic values (generating a single value) with different
      // date groups can exist.
      // 3. if the same dynamic value generated a date and a non-date, then it must be
      // multi-value variable and we should only keep one.

      for(int i = 0; i < reflist.size(); i++) {
         VSDimensionRef ref = (VSDimensionRef) reflist.get(i);

         if(ref.isDynamic()) {
            String dynamicGroup = ref.getGroupColumnValue();

            if(ref.isDateTime()) {
               String fullname = ref.getFullName();

               if(dateRTGroups.contains(fullname) || dynamicGroups.contains(dynamicGroup)) {
                  reflist.remove(i--);
               }
               else {
                  dateDGroups.add(dynamicGroup);
                  dateRTGroups.add(fullname);
               }
            }
            else {
               if(dynamicGroups.contains(dynamicGroup) || dateDGroups.contains(dynamicGroup)) {
                  reflist.remove(i--);
               }
               else {
                  dynamicGroups.add(dynamicGroup);
               }
            }
         }
      }
   }

   public Set<String> getRefFields(List<DataRef> refs, CrosstabTree crosstabTree) {
      return refs.stream()
         .filter(ref -> ref instanceof VSDimensionRef)
         .map(crosstabTree::getFieldName)
         .collect(Collectors.toSet());
   }

   public DataRef[] drillDownField(CrosstabTree crosstabTree, CrosstabVSAssembly assembly,
                                   XCube cube, List<DataRef> refs, Set<String> childFields,
                                   String field, boolean replace,
                                   BiFunction<XCube, VSDimensionRef, Boolean> allowExpandLevel,
                                   boolean recursive, boolean drillAllCell)
   {
      VSDimensionRef fieldRef = getDataRefByField(crosstabTree, refs, field);
      String nextField = crosstabTree.getNextField(field);

      if(childFields.contains(nextField)) {
         VSDimensionRef nextRef = getDataRefByField(crosstabTree, refs, nextField);

         //there is no need to remove the expanded path when drillAllCell is true.
         if(nextRef != null && !drillAllCell) {
            crosstabTree.removeDrill(field);
            return refs.toArray(new DataRef[0]);
         }
      }

      if(fieldRef == null) {
         return null;
      }

      drillDownChild(crosstabTree, assembly, cube, refs, childFields, fieldRef,
         recursive, replace, allowExpandLevel, drillAllCell);

      removeDuplicates(refs);

      return refs.toArray(new DataRef[0]);
   }

   public void drillDownChild(CrosstabTree crosstabTree, CrosstabVSAssembly assembly,
                              XCube cube, List<DataRef> refs, Set<String> childFields,
                              VSDimensionRef ref, boolean recursive, boolean replace,
                              BiFunction<XCube, VSDimensionRef, Boolean> allowExpandLevel,
                              boolean drillAllCell)
   {
      if(ref == null) {
         return;
      }

      String field = crosstabTree.getFieldName(ref);
      String nextField = crosstabTree.getNextField(field);

      if(cube != null && cube.getDimension(ref.getName()) != null &&
         getCubeNextLevelRef(ref, cube) == null)
      {
         return;
      }

      if(childFields.contains(nextField)) {
         VSDimensionRef nextRef = getDataRefByField(crosstabTree, refs, nextField);

         if(nextRef != null && !drillAllCell) {
            crosstabTree.removeDrill(field);
            return;
         }
      }

      VSDimensionRef childRef = crosstabTree.getChildRef(ref.getFullName());

      if(childRef == null || childRef == ref) {
         VSDimensionRef nref = VSUtil.getNextLevelRef(ref, cube, true);

         if(nref != null && VSUtil.isVariableValue(ref.getDateLevelValue())) {
            int level = nref.getDateLevel();
            nref.setDateLevel(level);
         }

         childRef = nref;
      }

      if(childRef == ref) {
         return;
      }

      int drillDownDateLevel = GraphUtil.getDrillDownDateLevel(ref.getDateLevel());

      if(childRef == null || recursive && (!allowExpandLevel.apply(cube, childRef) ||
         XSchema.DATE.equals(ref.getDataType()) && drillDownDateLevel == DateRangeRef.HOUR_INTERVAL))
      {
         return;
      }

      String childField = crosstabTree.getFieldName(childRef);
      boolean includeChild = childFields.contains(childField);

      if(!recursive && includeChild) {
         return;
      }

      int currentColIndex = VSUtil.findIndex(refs.toArray(new DataRef[0]), ref);
      crosstabTree.updateChildRef(ref.getFullName(), childRef);

      if(currentColIndex >= 0) {
         if(replace) {
            refs.set(currentColIndex, childRef);
            // in-place drill, port the format to new data path.
            // the same for regular drill is handled in BaseTableDrillController.syncPath().
            syncPath(assembly.getFormatInfo().getFormatMap(), ref, childRef);
            updateCalcuators(assembly.getVSCrosstabInfo(), ref, childRef);
         }
         else if(!includeChild) {
            refs.add(currentColIndex + 1, childRef);
         }
      }
      else {
         refs.add(childRef);
      }

      if(!includeChild) {
         childFields.add(childField);
      }

      if(recursive) {
         drillDownChild(crosstabTree, assembly, cube, refs, childFields, childRef,
            true, replace, allowExpandLevel, drillAllCell);
      }
   }

   public boolean drillChildEnabled(XCube cube, VSDimensionRef childRef) {
      if(childRef == null) {
         return false;
      }

      if(cube == null) {
         return true;
      }

      VSDimensionRef cubeNextLevelRef = getCubeNextLevelRef(childRef, cube);

      return cubeNextLevelRef != null
         || childRef.isDateTime() && !VSUtil.isCubeContainRef(childRef, cube);
   }

   public void processChange(RuntimeViewsheet rvs, String name,
                             VSAssemblyInfo oinfo,
                             CommandDispatcher dispatcher, String linkUri,
                             boolean refreshData)
      throws Exception
   {
      final ViewsheetSandbox box = rvs.getViewsheetSandbox();

      rvs.resetMVOptions();
      box.updateAssembly(name);

      Viewsheet vs = rvs.getViewsheet();
      VSAssembly assembly = vs != null ? vs.getAssembly(name) : null;

      if(assembly instanceof CrosstabVSAssembly && oinfo instanceof CrosstabVSAssemblyInfo) {
         CrosstabVSAssembly table = (CrosstabVSAssembly) assembly;
         // sync alias
         VSUtil.syncCrosstabPath(table, ((CrosstabVSAssemblyInfo) oinfo), false,
                                 table.getFormatInfo().getFormatMap(), false, true, null);
                                 // should not lose header alias when drill down then up
                                 // ClearTableHeaderAliasHandler::processClearAliasFormat);
      }

      int hint = VSAssembly.INPUT_DATA_CHANGED;
      ChangedAssemblyList clist = coreLifecycleService.createList(false, dispatcher, rvs, linkUri);
      box.processChange(name, hint, clist);

      if(assembly instanceof CrosstabVSAssembly) {
         TableLens nlens = box.getVSTableLens(name, false);
         // restore the saved (in syncCrosstabPath) header info with the new table.
         BaseTableDrillController.restoreColumnInfo(
            ((CrosstabVSAssembly) assembly).getCrosstabInfo(), nlens);
      }

      coreLifecycleService.execute(rvs, name, linkUri, clist, dispatcher, true, refreshData);
   }

   public VSDimensionRef getDataRefByField(CrosstabTree crosstabTree,
                                           List<DataRef> refs,
                                           String field)
   {
      return refs.stream().filter(ref0 -> ref0 instanceof VSDimensionRef)
         .map(ref0 -> (VSDimensionRef) ref0)
         .filter(ref0 -> isSameField(crosstabTree, field, ref0))
         .findFirst().orElse(null);
   }

   /**
    * Check if the target fieldname match the target dataref.
    *
    * @param crosstabTree
    * @param field  the target field name.
    * @param ref    the target data ref.
    * @return
    */
   public boolean isSameField(CrosstabTree crosstabTree, String field, DataRef ref) {
      String fieldName = crosstabTree.getFieldName(ref);

      if(Tool.equals(field, fieldName)) {
         return true;
      }

      if(!(ref instanceof VSDimensionRef) || fieldName == null) {
         return false;
      }

      VSDimensionRef dim = (VSDimensionRef) ref;

      if(fieldName != null && dim.isNameGroup() && dim.getGroupType() != null) {
         fieldName = NamedRangeRef.getName(fieldName, Integer.parseInt(dim.getGroupType()));
      }

      return Tool.equals(field, fieldName);
   }

   public VSDimensionRef getCubeNextLevelRef(VSDimensionRef ref, XCube cube) {
      if(ref == null) {
         return null;
      }

      VSDimensionRef nref = null;

      if(cube != null) {
         nref = VSUtil.getCubeNextLevelRef(ref, cube);
      }

      return nref;
   }

   /**
    * Remove duplicate variable headers.
    */
   public DataRef[] fixDesignHeaders(DataRef[] rows) {
      if(rows == null || rows.length == 0) {
         return rows;
      }

      List<DataRef> list = new ArrayList<>();

      for(int i = 0; i < rows.length; i++) {
         if(!containsInList(list, rows[i])) {
            list.add(rows[i]);
         }
      }

      return list.toArray(new DataRef[list.size()]);
   }

   private static boolean containsInList(List<DataRef> list, DataRef ref) {
      if(ref == null || list == null || list.size() == 0) {
         return false;
      }

      for(int i = 0; i < list.size(); i++) {
         if(((VSDimensionRef) list.get(i)).isDynamic() && ((VSDimensionRef) ref).isDynamic() &&
            Tool.equals(((VSDimensionRef) list.get(i)).getGroupColumnValue(),
            ((VSDimensionRef) ref).getGroupColumnValue()))
         {
            return true;
         }

         if(Tool.equals(((VSDimensionRef) list.get(i)).getFullName(),
            ((VSDimensionRef) ref).getFullName()))
         {
            return true;
         }
      }

      return false;
   }

   private boolean dataRefChanged(DataRef[] refs1, DataRef[] refs2) {
      boolean changed = !Tool.equalsContent(refs1, refs2);

      if(!changed) {
         for(int i = 0; i < refs1.length; i++) {
            if(refs1[i] instanceof VSDimensionRef && refs2[i] instanceof VSDimensionRef) {
               VSDimensionRef ref1 = (VSDimensionRef) refs1[i];
               VSDimensionRef ref2 = (VSDimensionRef) refs2[i];

               if(ref1.isDate() && ref2.isDate() && ref1.getDateLevel() != ref2.getDateLevel()) {
                  changed = true;
               }
            }
         }
      }

      return changed;
   }

   private void removeFieldRefs(CrosstabTree crosstabTree,
                                List<DataRef> refs, String field)
   {
      if(field == null) {
         return;
      }

      DataRef ref;
      String nextField = crosstabTree.getNextField(field);
      removeFieldRefs(crosstabTree, refs, nextField);

      for(int i = refs.size() - 1; i >= 0; i--) {
         ref = refs.get(i);

         if(field.equals(crosstabTree.getFieldName(ref))) {
            refs.remove(i);
            break;
         }
      }
   }

   /**
    * Refresh assembliese that binding assembly--${assemblyName };
    */
   public void refreshDependAssemblies(CommandDispatcher dispatcher, String linkUri,
                                       RuntimeViewsheet rvs, Viewsheet vs, String assemblyName,
                                       VSAssembly drillAssembly, CrosstabVSAssemblyInfo oinfo)
      throws Exception
   {
      Assembly[] assemblies = vs.getAssemblies();

      for (Assembly assembly: assemblies) {
         if(assembly == drillAssembly) {
            continue;
         }

         VSAssembly vsAssembly = (VSAssembly) assembly;
         String tableName = VSUtil.getVSAssemblyBinding(vsAssembly.getTableName());

         // binding current assembly
         if(!StringUtils.isEmpty(tableName) && tableName.equals(assemblyName)) {
            processChange(rvs, assembly.getAbsoluteName(), null, dispatcher, linkUri, true);
         }
      }
   }

   @Override
   public boolean isHandler(VSAssembly vsobj) {
      return vsobj instanceof CrosstabVSAssembly;
   }

   @Override
   public DataRef getFieldByName(CrosstabVSAssembly assembly, String field) {
      return getDataRefByField(assembly.getCrosstabTree(),
                               Arrays.asList(assembly.getBindingRefs()), field);
   }

   private final ViewsheetService viewsheetService;
   private final CoreLifecycleService coreLifecycleService;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
}
