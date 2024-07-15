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
package inetsoft.web.viewsheet.handler.chart;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.ChartEvent;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.graph.VGraph;
import inetsoft.graph.data.DataSet;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.DataMap;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.*;
import inetsoft.report.composition.region.ChartConstants;
import inetsoft.uql.*;
import inetsoft.uql.asset.NamedRangeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.CategoricalColorFrameContext;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.binding.handler.VSChartDataHandler;
import inetsoft.web.graph.GraphBuilder;
import inetsoft.web.viewsheet.handler.BaseDrillHandler;
import inetsoft.web.viewsheet.model.*;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.awt.*;
import java.security.Principal;
import java.util.List;
import java.util.*;
import java.util.stream.Stream;

@Component
public class VSChartDrillHandler extends BaseDrillHandler<ChartVSAssembly, ChartDrillFilterAction> {
   public VSChartDrillHandler(VSChartDataHandler dataHandler,
                              ViewsheetService viewsheetService,
                              RuntimeViewsheetRef runtimeViewsheetRef)
   {
      this.dataHandler = dataHandler;
      this.viewsheetService = viewsheetService;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
   }

   @Override
   public void processDrillFilter(ChartVSAssembly assembly, ChartDrillFilterAction drillAction,
                                  CommandDispatcher dispatcher, String linkUri,
                                  Principal principal)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(
         runtimeViewsheetRef.getRuntimeId(), principal);
      final ViewsheetSandbox box = rvs.getViewsheetSandbox();
      VGraphPair pair = box.getVGraphPair(drillAction.getAssemblyName());
      VGraph vgraph = pair.getRealSizeVGraph();
      VSSelection selection = getVSSelection(rvs, assembly, vgraph, drillAction.getSelected(),
            drillAction.isRangeSelection());

      ChartVSAssemblyInfo assemblyInfo = (ChartVSAssemblyInfo) assembly.getVSAssemblyInfo();
      VSChartInfo info = assemblyInfo.getVSChartInfo();
      List<String> fields = drillAction.getFields();

      if(drillAction.isDrillUp()) {
         for(String field : fields) {
            VSDimensionRef ref = (VSDimensionRef) getFieldByName(info, field);
            removeDrillFilter(ref, assembly.getViewsheet());
         }
      }
      else {
         processDrillDownFilter(selection, fields, assembly);
      }
   }

   private void processDrillDownFilter(VSSelection selection, List<String> fields,
                                       ChartVSAssembly assembly)
   {
      ChartVSAssemblyInfo assemblyInfo = (ChartVSAssemblyInfo) assembly.getVSAssemblyInfo();
      VSChartInfo chartInfo = assemblyInfo.getVSChartInfo();

      if(selection == null || selection.isEmpty() || chartInfo == null) {
         return;
      }

      Map<String, ConditionList> ndrillConds = new LinkedHashMap<>();

      for(int i = 0; i < selection.getPointCount(); i++) {
         VSPoint point = selection.getPoint(i);

         for(int j = 0; j < point.getValueCount(); j++) {
            VSSelection child = new VSSelection();
            child.setRange(selection.getRange());
            VSPoint vsPoint = new VSPoint();
            vsPoint.addValue(point.getValue(j));
            child.addPoint(vsPoint);
            String field = point.getValue(j).getFieldName();

            if(!fields.contains(field)) {
               continue;
            }

            ConditionList conditionList = assemblyInfo.getConditionList(child, null);
            VSDimensionRef ref = (VSDimensionRef) getFieldByName(chartInfo, field);
            String name = CrosstabTree.getDrillFilterName(ref, assemblyInfo.getXCube(), true);
            conditionList = replaceNamedGroup(conditionList, assembly);
            name = NamedRangeRef.getBaseName(name);
            mergeDrillFilterConditionList(ndrillConds, name, conditionList);
         }
      }

      updateDrillFilterAssemblyCondition(ndrillConds, assembly);
   }

   @Override
   public void processDrillAction(ChartVSAssembly assembly, DrillFilterAction drillFilterAction,
                                  DrillFilterVSAssembly targetAssembly,
                                  CommandDispatcher dispatcher, String linkUri,
                                  Principal principal)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(
         runtimeViewsheetRef.getRuntimeId(), principal);
      List<String> fields = drillFilterAction.getFields();
      VSChartInfo info = assembly.getVSChartInfo();
      boolean drillUp = drillFilterAction.isDrillUp();

      fields.stream().forEach((field) -> {
         ChartRef ref = getFieldByName(info, field);

         if(ref instanceof VSDimensionRef && !((VSDimensionRef) ref).isDynamic() &&
            !info.isPeriodRef(ref.getFullName()))
         {
            drill(ref, assembly, drillUp, rvs.isViewer(), principal, true);
         }
      });
   }

   public void drill(ChartRef ref, ChartVSAssembly chartAssembly, boolean drillUp, boolean viewer,
                     Principal principal, boolean drillFilterAction)
   {
      if(ref == null || DateComparisonUtil.appliedDateComparison(chartAssembly.getChartInfo())) {
         return;
      }

      VSChartInfo chartInfo = chartAssembly.getVSChartInfo();
      XCube cube = GraphBuilder.getCube(chartAssembly, chartAssembly.getXCube());
      String axisType = getDrillDirectionType(chartInfo, ref);
      Object newValue = null; // newLevel or new DataRef, for drill aestheticRefs
      int currLevel = ((VSDimensionRef) ref).getDateLevel();
      int newLevel = !drillUp ? GraphUtil.getDrillDownDateLevel(currLevel)
         : GraphUtil.getDrillUpDateLevel(currLevel);
      // drill in continuous date level, just change the level without
      // adding/removing dim (see 13.2 VSChartDrillController.doDrill).
      boolean replaceLevel = !drillFilterAction && ((VSDimensionRef) ref).isDateTime() &&
         cube == null && (currLevel & XConstants.PART_DATE_GROUP) == 0 &&
         (newLevel & XConstants.PART_DATE_GROUP) == 0;

      if(replaceLevel) {
         ChartRef ref2 = chartInfo.getFieldByName(ref.getFullName(), false);

         if(ref2 == null) {
            ref2 = (ChartRef) Arrays.stream(chartInfo.getAestheticRefs(false))
               .filter(aref -> Objects.equals(aref.getFullName(), ref.getFullName()))
               .map(aref -> aref.getDataRef())
               .findAny().orElse(null);
         }

         ChartRef oldRef2 = ref2;
         ref2 = oldRef2 == null ? null : (ChartRef) oldRef2.clone();

         if(ref2 instanceof VSChartDimensionRef) {
            VSChartDimensionRef chartRef = (VSChartDimensionRef) ref2;
            boolean dynamic = VSUtil.isDynamicValue(chartRef.getDateLevelValue());

            if(drillFilterAction) {
               // ignore top-n if drilled from original date level
               if(!dynamic && newLevel == Integer.parseInt(chartRef.getDateLevelValue())) {
                  chartRef.setRankingN(null);
               }
               else {
                  chartRef.setRankingN(0);
               }
            }

            if(dynamic) {
               chartRef.setDateLevel(newLevel);
            }
            else {
               chartRef.setDateLevelValue(newLevel + "");
            }

            newValue = chartRef.getDateLevel() + "";
            chartInfo.replaceField(oldRef2, ref2);
         }
      }
      else if(!drillUp) {
         newValue = drillDown(chartAssembly, ref, cube, axisType, viewer, drillFilterAction);
      }
      else if(drillFilterAction) {
         newValue = drillUpSelection(chartAssembly, ref, cube, axisType);
      }
      else {
         drillUp(chartInfo, ref, cube, axisType, viewer);
      }

      if(drillFilterAction) {
         drillAestheticRefs(ref.getFullName(), false /*handleLevel*/, newValue,
            chartAssembly.getChartInfo(), principal);
      }
   }

   private void drillAestheticRefs(String field, boolean handleLevel,
                                   Object newValue, ChartVSAssemblyInfo chartAssemblyInfo,
                                   Principal principal)
   {
      if(StringUtils.isEmpty(field)) {
         return;
      }

      VSChartInfo chartInfo = chartAssemblyInfo.getVSChartInfo();
      AestheticRef[] aestheticRefs = chartInfo.getAestheticRefs(false);
      AestheticRef[] runtimeAestheticRefs = chartInfo.getAestheticRefs(true);

      try {
         RuntimeViewsheet rvs = viewsheetService.getViewsheet(
            runtimeViewsheetRef.getRuntimeId(), principal);
         Viewsheet vs = rvs.getViewsheet();

         if(vs != null) {
            CategoricalColorFrameContext.getContext().setSharedFrames(vs.getSharedFrames());
         }

         // fix Bug #44955, should also update the runtime aesthetic refs,
         // because the new runtime ref will keep the old aestheticRef data ref.
         // see update method of VSChartInfo.
         Stream.concat(Arrays.stream(aestheticRefs), Arrays.stream(runtimeAestheticRefs))
            .filter(ref -> ref.getDataRef() instanceof VSDimensionRef
                  && field.equals(((VSDimensionRef) ref.getDataRef()).getFullName()))
            .forEach(ref -> {
               if(handleLevel && newValue instanceof String) {
                  VSDimensionRef dimensionRef = (VSDimensionRef) ref.getDataRef();
                  boolean variable = VSUtil.isVariableValue(dimensionRef.getDateLevelValue());

                  if(variable) {
                     dimensionRef.setDateLevel(Integer.parseInt((String) newValue) );
                  }
                  else {
                     dimensionRef.setDateLevelValue((String) newValue);
                  }
               }
               else if(newValue instanceof VSDimensionRef) {
                  VSDimensionRef newDim = (VSDimensionRef) ((VSDimensionRef) newValue).clone();

                  if(!Tool.equals(newDim.getFullName(), ref.getFullName())) {
                     ref.setDataRef(newDim);
                     keepDimensionSort((VSDimensionRef) ref.getRTDataRef(), newDim);
                     ((VSAestheticRef) ref).setRTDataRef(newDim);
                  }
               }
            });
      }
      catch(Exception ignore) {
         LOGGER.warn("Drill Aesthetic Failed.");
      }
   }

   private void keepDimensionSort(VSDimensionRef oldDimension, VSDimensionRef newDimension) {
      if(oldDimension == null || newDimension == null) {
         return;
      }

      newDimension.setOrder(oldDimension.getOrder());
      newDimension.setSortByCol(oldDimension.getSortByCol());
   }

   private String getDrillDirectionType(VSChartInfo chartInfo, ChartRef ref) {
      ChartRef[] xfields = chartInfo.getRTXFields();
      ChartRef[] yfields = chartInfo.getRTYFields();
      ChartRef[] gfields = chartInfo instanceof VSMapInfo ? ((VSMapInfo) chartInfo).getGeoFields() : null;

      String axisType = VSUtil.findIndex(xfields, ref) != -1 ? ChartConstants.DRILL_DIRECTION_X
         : VSUtil.findIndex(yfields, ref) != -1 ? ChartConstants.DRILL_DIRECTION_Y
         : VSUtil.findIndex(gfields, ref) != -1 ? ChartConstants.DRILL_DIRECTION_G
         : null;

      if(axisType != null) {
         // found in x/y/g
      }
      else if(GraphTypes.isTreemap(chartInfo.getChartType()) || GraphTypes.isRadarOne(chartInfo)) {
         ChartRef[] groupFields = chartInfo.getGroupFields();

         if(VSUtil.findIndex(groupFields, ref) > -1) {
            axisType = ChartConstants.DRILL_DIRECTION_T;
         }
      }
      else if(GraphTypes.isRelation(chartInfo.getChartType())) {
         RelationVSChartInfo relation = (RelationVSChartInfo) chartInfo;
         ChartRef sourceField = relation.getSourceField();
         ChartRef targetField = relation.getTargetField();

         if(sourceField != null && VSUtil.findIndex(new DataRef[]{sourceField}, ref) != -1) {
            axisType = ChartConstants.DRILL_DIRECTION_SOURCE;
         }
         else if(targetField != null && VSUtil.findIndex(new DataRef[]{targetField}, ref) != -1) {
            axisType = ChartConstants.DRILL_DIRECTION_TARGET;
         }
      }

      if(axisType == null) {
         axisType = ChartConstants.DRILL_DIRECTION_AESTHETIC;
      }

      return axisType;
   }

   private VSDimensionRef getDrillChildRef(String direction, ChartTree chartTree, String key) {
      VSDimensionRef childRef;

      switch (direction) {
         case ChartConstants.DRILL_DIRECTION_X:
            childRef = chartTree.getXChildRef(key);
            break;
         case ChartConstants.DRILL_DIRECTION_Y:
            childRef = chartTree.getYChildRef(key);
            break;
         case ChartConstants.DRILL_DIRECTION_T:
            childRef = chartTree.getTChildRef(key);
            break;
         case ChartConstants.DRILL_DIRECTION_G:
            childRef = chartTree.getGChildRef(key);
            break;
         case ChartConstants.DRILL_DIRECTION_TARGET:
            childRef = chartTree.getTargetChildRef(key);
            break;
         case ChartConstants.DRILL_DIRECTION_SOURCE:
            childRef = chartTree.getSourceChildRef(key);
            break;
         case ChartConstants.DRILL_DIRECTION_AESTHETIC:
            childRef = chartTree.getAestheticChildRef(key);
            break;
         default:
            childRef = null;
      }

      return childRef;
   }

   private VSDimensionRef drillDown(ChartVSAssembly assembly, ChartRef ref, XCube cube,
                                   String direction, boolean viewer, boolean drillFilterAction)
   {
      // This a dimension that is derived from the ref drilled down on.
      // It should only be used if the next level down ref doesn't exist yet.
      VSChartInfo info = assembly.getVSChartInfo();
      boolean isX = isX(direction);
      boolean isY = isY(direction);
      boolean isT = isT(direction);
      boolean isG = isG(direction);
      boolean isSource = isSource(direction);
      boolean isTarget = isTarget(direction);
      ChartTree chartTree = assembly.getChartTree();
      VSDimensionRef childRef = getDrillChildRef(direction, chartTree, ref.getFullName());
      VSDimensionRef freshRef = childRef != null
         ? childRef : VSUtil.getNextLevelRef((VSDimensionRef) ref, cube, true);

      ChartRef[] refs = getFields(info, direction);

      if(freshRef == null) {
         if(drillFilterAction) {
            freshRef = (VSDimensionRef) ref;
         }
         else {
            return null;
         }
      }

      // This a dimension that currently exists in the info.  Prefer this
      // one unless it doesn't exist for some reason.
      VSDimensionRef nref = (VSDimensionRef) info.getFieldByName(freshRef.getFullName(), false);

      // If the next level ref cannot be found, use derived ref.
      if(nref == null) {
         nref = freshRef;
      }

      nref = (VSDimensionRef) nref.clone();

      // only add if not already added (e.g. double click on drill icon)
      if(nref != null && (VSUtil.findIndex(refs, nref) < 0 || !nref.isDrillVisible())) {
         // @by ChrisSpagnoli bug1429507986738 bug1429496186187 2015-4-24
         // incrementing is "good enough", until corrected by next drill down
         info.incrementDrillLevel();

         // reset format if new ref, see bug1332539939927
         copyObjectFormat((ChartVSAssemblyInfo) assembly.getVSAssemblyInfo(), nref);

         int idx = drillFilterAction ? VSUtil.findIndex(refs, ref)
            : VSUtil.findNextIndex(refs, ref);

         if(idx < 0) {
            return nref;
         }

         ChartRef[] refs2 = isT ? null : isX ? info.getYFields() : info.getXFields();
         int idx2 = VSUtil.findIndex(refs2, nref);

         // remove same dim from the other direction
         if(idx2 >= 0 && !drillFilterAction) {
            if(isX) {
               if(viewer) {
                  info.setYFieldVisibility(idx2, false);
               }
               else {
                  info.removeYField(idx2);
               }
            }
            else {
               if(viewer) {
                  info.setXFieldVisibility(idx2, false);
               }
               else {
                  info.removeXField(idx2);
               }
            }
         }

         boolean variable = VSUtil.isVariableValue(((VSDimensionRef) ref).getDateLevelValue());

         if(variable) {
            int level = nref.getDateLevel();
            nref.setDateLevelValue(((VSDimensionRef) ref).getDateLevelValue());
            nref.setDateLevel(level);
            nref.copyOldVariableDateLevel((VSDimensionRef) ref);
         }

         if(isT) { // treemap
            ChartRef groupField = info.getGroupField(idx);

            if(groupField == null || !groupField.getFullName().equals(nref.getFullName())) {
               if(drillFilterAction) {
                  info.setGroupField(idx, (ChartRef) nref);
               }
               else {
                  info.setGroupField(idx, (ChartRef) nref);
               }

               adjustToolTip(info, idx, drillFilterAction ? 0 : 1);
            }
         }
         else if(isX) {
            ChartRef xRef = info.getXField(idx);

            if(xRef == null || !xRef.getFullName().equals(nref.getFullName())) {
               if(drillFilterAction) {
                  info.setXField(idx, (ChartRef) nref);
               }
               else {
                  info.addXField(idx, (ChartRef) nref);
               }
            }

            info.setXFieldVisibility(idx, true);
            adjustToolTip(info, idx, drillFilterAction ? 0 : 1);
         }
         else if(isY) {
            ChartRef yRef = info.getYField(idx);

            if(yRef == null || !yRef.getFullName().equals(nref.getFullName())) {
               if(drillFilterAction) {
                  info.setYField(idx, (ChartRef) nref);
               }
               else {
                  info.addYField(idx, (ChartRef) nref);
               }
            }

            info.setYFieldVisibility(idx, true);
            adjustToolTip(info, idx, drillFilterAction ? 0 : 1);
         }
         else if(isG && ref instanceof GeoRef) {
            VSMapInfo mapInfo = (VSMapInfo) info;
            ChartRef gRef = mapInfo.getGeoFieldByName(idx);

            if(gRef == null || !gRef.getFullName().equals(nref.getFullName())) {
               if(!(nref instanceof VSChartGeoRef)) {
                  ColumnSelection cols = mapInfo.getGeoColumns();
                  DataRef gref = cols == null ? null : cols.getAttribute(nref.getName());
                  nref = gref != null ? (VSChartGeoRef) gref : nref;
               }

               if(drillFilterAction) {
                  mapInfo.setGeoField(idx, (ChartRef) nref);
               }
               else {
                  mapInfo.addGeoField(idx, (ChartRef) nref);
               }

               adjustToolTip(info, idx, drillFilterAction ? 0 : 1);
            }
         }
         else if((isSource || isTarget) && drillFilterAction) {
            RelationVSChartInfo relationInfo = (RelationVSChartInfo) info;
            ChartRef chartRef = isSource ? relationInfo.getSourceField() : relationInfo.getTargetField();

            if(!chartRef.getFullName().equals(nref.getFullName()) && isSource) {
               relationInfo.setSourceField((VSChartRef) nref);
            }
            else if(!chartRef.getFullName().equals(nref.getFullName()) && isTarget) {
               relationInfo.setTargetField((VSChartRef) nref);
            }
         }
      }

      return nref;
   }

   private VSDimensionRef drillUpSelection(ChartVSAssembly assembly, ChartRef ref, XCube cube,
                                          String direction)
   {
      VSChartInfo info = assembly.getVSChartInfo();
      VSDimensionRef lastRef = VSUtil.getLastDrillLevelRef((VSDimensionRef) ref, cube);

      if(lastRef == null) {
         return null;
      }

      VSDimensionRef grandpaRef = VSUtil.getLastDrillLevelRef(lastRef, cube);
      String upKey = "";

      if(grandpaRef == null) {
         upKey = CrosstabTree.getHierarchyRootKey(lastRef);
      }
      else {
         upKey = grandpaRef.getFullName();
      }

      boolean isX = isX(direction);
      boolean isY = isY(direction);
      boolean isT = isT(direction);
      boolean isG = isG(direction);
      boolean isSource = isSource(direction);
      boolean isTarget = isTarget(direction);

      ChartTree chartTree = assembly.getChartTree();
      VSDimensionRef lref = getDrillChildRef(direction, chartTree, upKey);
      lastRef = lref == null ? lastRef : lref;

      ChartRef[] refs = getFields(info, direction);
      VSDimensionRef nref = (VSDimensionRef) info.getFieldByName(lastRef.getFullName(), false);
      boolean exist = nref != null;

      if(nref == null) {
         nref = lastRef;
      }

      if(nref != null) {
         nref = (VSDimensionRef) nref.clone();

         if(!exist) {
            nref.setTimeSeries(((VSDimensionRef) ref).isTimeSeries());
         }

         info.decrementDrillLevel();

         // reset format if new ref, see bug1332539939927
         //copyObjectFormat((ChartVSAssemblyInfo) assembly.getVSAssemblyInfo(), nref);

         int nidx = VSUtil.findIndex(refs, nref);
         int idx = VSUtil.findIndex(refs, ref);

         if(isT) {
            ChartRef groupField = info.getGroupField(idx);

            if(nidx != -1) {
               info.removeGroupField(idx);
               adjustToolTip(info, idx, -1);
            }
            else if(groupField == null || !groupField.getFullName().equals(nref.getFullName())) {
               info.setGroupField(idx, (ChartRef) nref);
               adjustToolTip(info, idx, 0);
            }
         }
         else if(isX) {
            ChartRef xRef = info.getXField(idx);

            if(nidx != -1) {
               info.removeXField(idx);
               adjustToolTip(info, idx, -1);
            }
            else if(xRef == null || !xRef.getFullName().equals(nref.getFullName())) {
               info.setXField(idx, (ChartRef) nref);
               info.setXFieldVisibility(idx, true);
               adjustToolTip(info, idx, 0);
            }
         }
         else if(isY) {
            ChartRef yRef = info.getYField(idx);

            if(nidx != -1) {
               info.removeYField(idx);
               adjustToolTip(info, idx, -1);
            }
            else if(yRef == null || !yRef.getFullName().equals(nref.getFullName())) {
               info.setYField(idx, (ChartRef) nref);
               info.setYFieldVisibility(idx, true);
               adjustToolTip(info, idx, 0);
            }
         }
         else if(isG) {
            VSMapInfo mapInfo = (VSMapInfo) info;
            ChartRef geoField = mapInfo.getGroupField(idx);

            if(nidx != -1) {
               mapInfo.removeGeoField(idx);
               adjustToolTip(mapInfo, idx, -1);
            }
            else if(geoField == null || !geoField.getFullName().equals(nref.getFullName())) {
               mapInfo.setGeoField(idx, (ChartRef) nref);
               adjustToolTip(mapInfo, idx, 0);
            }
         }
         else if(isSource || isTarget) {
            RelationVSChartInfo relationInfo = (RelationVSChartInfo) info;
            ChartRef field = isTarget ? relationInfo.getTargetField() : relationInfo.getSourceField();

            if(!field.getFullName().equals(nref.getFullName()) && isSource) {
               relationInfo.setSourceField((VSChartRef) nref);
            }
            else if(!field.getFullName().equals(nref.getFullName()) && isTarget) {
               relationInfo.setTargetField((VSChartRef) nref);
            }
         }
      }

      return nref;
   }

   private void drillUp(VSChartInfo info, ChartRef ref, XCube cube, String axisType,
                        boolean viewer)
   {
      boolean isX = ChartConstants.DRILL_DIRECTION_X.equals(axisType);
      ChartRef[] refs = isX ? info.getXFields() : info.getYFields();
      int rank = VSUtil.getDimRanking((VSDimensionRef) ref, cube, null);
      int dLevel = -1;

      for(int i = refs.length - 1; i >= 0; i--) {
         if(refs[i] instanceof VSDimensionRef) {
            int rank2 = VSUtil.getDimRanking((VSDimensionRef) refs[i], cube, (VSDimensionRef) ref);

            if(rank2 > rank) {
               if(isX) {
                  if(viewer) {
                     // @by stephenwebster, For Bug #17247
                     // Adjusting the tooltip when the field is already not visible
                     // caused the indexing of the tooltip to be out of sync.
                     if(info.getXFieldVisibility(i)) {
                        info.setXFieldVisibility(i, false);
                        adjustToolTip(info, i, -1);
                     }
                  }
                  else {
                     info.removeXField(i);
                     adjustToolTip(info, i, -1);
                  }
               }
               else {
                  if(viewer) {
                     if(info.getYFieldVisibility(i)) {
                        info.setYFieldVisibility(i, false);
                        adjustToolTip(info, info.getXFieldCount() + i, -1);
                     }
                  }
                  else {
                     info.removeYField(i);
                     adjustToolTip(info, info.getXFieldCount() + i, -1);
                  }
               }
            }
            else {
               dLevel++;
            }
         }
      }

      // @by ChrisSpagnoli bug1429507986738 bug1429496186187 2015-4-24
      // set to the correct value, don't just decrement
      info.setDrillLevel(dLevel);
   }

   public VSSelection getVSSelection(RuntimeViewsheet rvs, ChartVSAssembly assembly,
                                     VGraph vgraph, String selected, boolean rangeSelection)
      throws Exception
   {
      final ViewsheetSandbox box = rvs.getViewsheetSandbox();
      VSChartInfo chartInfo = assembly.getVSChartInfo();
      ChartVSAssemblyInfo assemblyInfo = assembly.getChartInfo();
      VSDataSet lens = (VSDataSet) box.getData(assembly.getAbsoluteName());
      VSDataSet alens = (VSDataSet) box.getData(
         assembly.getAbsoluteName(), true, DataMap.DRILL_FILTER);
      String ctype = ((ChartVSAssemblyInfo)
         VSEventUtil.getAssemblyInfo(rvs, assembly)).getCubeType();
      DataSet vdset = vgraph.getCoordinate().getDataSet();
      VSSelection selection = ChartEvent.getVSSelection(selected, lens, alens, vdset,
                                                        rangeSelection, chartInfo, false, ctype,
                                                        true, true, true);

      if(selection == null) {
         return null;
      }

      VSSelection nselection = selection.clone();
      boolean added = false;

      for(int i = 0; i < selection.getPointCount(); i++) {
         VSPoint point = (VSPoint) selection.getPoint(i).clone();
         VSPoint npoint = nselection.getPoint(i);
         npoint.clearValues();

         for(int j = 0; j < point.getValueCount(); j++) {
            VSFieldValue pair = point.getValue(j);
            DataRef ref = assemblyInfo.getDataRef(pair);

            if(ref instanceof VSDimensionRef && !((VSDimensionRef) ref).isDynamic()) {
               npoint.addValue(pair);
               added = true;
            }
         }
      }

      if(!added && GraphTypes.isRelation(chartInfo.getChartType())) {
         Tool.addUserMessage(
            Catalog.getCatalog().getString("viewer.wizard.relationDrill"));
      }

      // range selection may produce an empty condition, in that case, we should just
      // treat it as a regular selection. otherwise the drill just silently fail. (54617)
      if(!added && rangeSelection) {
         return getVSSelection(rvs, assembly, vgraph, selected, false);
      }

      return nselection;
   }

   // from analytic.composition.event.DrillEvent.adjustToolTip()

   /**
    * Change the field index in custom tooltip. For example, if the custom
    * tooltip is: {0} year, {1} total, and drill down on year, the tip needs
    * to be changed to: {0} year, {2} total.
    *
    * @param idx the field index that is added or removed.
    * @param adj +1 if the a field is inserted, -1 if a field is removed.
    */
   protected void adjustToolTip(VSChartInfo cinfo, int idx, int adj) {
      String tooltip = cinfo.getToolTip();

      if(tooltip == null || tooltip.length() == 0) {
         return;
      }

      boolean inBrace = false;
      StringBuilder ntooltip = new StringBuilder();
      StringBuilder numstr = new StringBuilder();

      for(int i = 0; i < tooltip.length(); i++) {
         char c = tooltip.charAt(i);

         if(inBrace) {
            if(c == '}') {
               try {
                  String str = numstr.toString();
                  int comma = str.indexOf(',');
                  String str2 = (comma < 0) ? str : str.substring(0, comma);
                  int num = Integer.parseInt(str2);

                  if(num >= idx) {
                     num += adj;
                  }

                  ntooltip.append(num);

                  if(comma > 0) {
                     ntooltip.append(str.substring(comma));
                  }

                  ntooltip.append("}");
               }
               catch(Exception ex) {
                  ntooltip.append(numstr);
                  ntooltip.append("}");
               }

               numstr = new StringBuilder();
               inBrace = false;
            }
            else {
               numstr.append(c);
            }

            continue;
         }
         else if(c == '{') {
            inBrace = true;
         }

         ntooltip.append(c);
      }

      ntooltip.append(numstr);
      cinfo.setToolTipValue(ntooltip.toString());
   }

   // from analytic.composition.event.DrillEvent.copyObjectFormat()

   /**
    * Set the default format for the axis from chart format.
    */
   protected void copyObjectFormat(ChartVSAssemblyInfo info, VSDimensionRef ref) {
      if(!(ref instanceof VSChartDimensionRef)) {
         return;
      }

      VSChartDimensionRef cref = (VSChartDimensionRef) ref;
      VSCompositeFormat objfmt = info.getFormat();
      VSFormat objuser = objfmt.getUserDefinedFormat();
      Font font = objfmt.getFont();
      Color clr = objfmt.getForeground();
      AxisDescriptor adesc = cref.getAxisDescriptor();
      CompositeTextFormat tfmt = cref.getTextFormat();
      CompositeTextFormat afmt = adesc == null ?
         null : adesc.getAxisLabelTextFormat();

      if(font != null &&
         (objuser.isFontDefined() || objuser.isFontValueDefined())) {
         if(tfmt != null) {
            tfmt.getUserDefinedFormat().setFont(font);
         }

         if(afmt != null) {
            afmt.getUserDefinedFormat().setFont(font);
         }
      }

      if(clr != null &&
         (objuser.isForegroundDefined() || objuser.isForegroundValueDefined())) {
         if(tfmt != null) {
            tfmt.getUserDefinedFormat().setColor(clr);
         }

         if(afmt != null) {
            afmt.getUserDefinedFormat().setColor(clr);
         }
      }
   }

   public ChartRef getFieldByName(VSChartInfo chartInfo, String fieldName) {
      ChartRef ref = chartInfo.getFieldByName(fieldName, true, true);

      if(ref == null && chartInfo instanceof VSMapInfo) {
         ref = getGeoRef((VSMapInfo) chartInfo, fieldName);
      }

      if(ref == null) {
         AestheticRef aestheticRef = getAestheticRef(chartInfo, fieldName);

         if(aestheticRef != null) {
            ref = (ChartRef) aestheticRef.getDataRef();
         }
      }

      return ref;
   }

   private ChartRef getGeoRef(VSMapInfo mapInfo, String fieldName) {
      ChartRef[] refs = mapInfo.getRTGeoFields();

      for(ChartRef ref : refs) {
         if(fieldName.equals(ref.toView())) {
            return ref;
         }
      }

      return null;
   }

   private AestheticRef getAestheticRef(VSChartInfo chartInfo, String fieldName) {
      AestheticRef[] refs = chartInfo.getAestheticRefs(true);

      for(AestheticRef ref : refs) {
         if(fieldName.equals(ref.getFullName())) {
            return ref;
         }
      }

      return null;
   }

   /**
    * Get drill direction fields
    */
   private ChartRef[] getFields(VSChartInfo info, String direction) {
      ChartRef[] refs = null;

      if(isT(direction)) {
         refs = info.getGroupFields();
      }
      else if(isX(direction)) {
         refs = info.getXFields();
      }
      else if(isY(direction)) {
         refs = info.getYFields();
      }
      else if(isG(direction)) {
         refs = ((VSMapInfo) info).getGeoFields();
      }
      else if(isSource(direction)) {
         refs = new ChartRef[]{((RelationVSChartInfo) info).getSourceField()};
      }
      else if(isTarget(direction)) {
         refs = new ChartRef[]{((RelationVSChartInfo) info).getTargetField()};
      }

      return refs;
   }

   /**
    * Drill x direction.
    */
   private boolean isX(String direction) {
      return direction != null && ChartConstants.DRILL_DIRECTION_X.equals(direction);
   }

   /**
    * Drill y direction.
    */
   private boolean isY(String direction) {
      return direction != null && ChartConstants.DRILL_DIRECTION_Y.equals(direction);
   }

   /**
    * Drill t direction.
    */
   private boolean isT(String direction) {
      return direction != null && ChartConstants.DRILL_DIRECTION_T.equals(direction);
   }

   /**
    * Drill g direction.
    */
   private boolean isG(String direction) {
      return direction != null && ChartConstants.DRILL_DIRECTION_G.equals(direction);
   }

   /**
    * Drill source direction.
    */
   private boolean isSource(String direction) {
      return direction != null && ChartConstants.DRILL_DIRECTION_SOURCE.equals(direction);
   }

   /**
    * Drill target direction.
    */
   private boolean isTarget(String direction) {
      return direction != null && ChartConstants.DRILL_DIRECTION_TARGET.equals(direction);
   }

   @Override
   public boolean isHandler(VSAssembly vsobj) {
      return vsobj instanceof ChartVSAssembly;
   }

   @Override
   public DataRef getFieldByName(ChartVSAssembly assembly, String field) {
      return getFieldByName(assembly.getVSChartInfo(), field);
   }

   private final VSChartDataHandler dataHandler;
   private final ViewsheetService viewsheetService;
   private final RuntimeViewsheetRef runtimeViewsheetRef;

   private static final Logger LOGGER = LoggerFactory.getLogger(VSChartDrillHandler.class);
}
