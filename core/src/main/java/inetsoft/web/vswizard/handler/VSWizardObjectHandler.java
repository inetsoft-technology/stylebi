/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.vswizard.handler;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.StyleConstants;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import inetsoft.web.viewsheet.command.RemoveVSObjectCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.PlaceholderService;
import inetsoft.web.vswizard.model.VSWizardConstants;
import inetsoft.web.vswizard.model.recommender.VSTemporaryInfo;
import inetsoft.web.vswizard.recommender.WizardRecommenderUtil;
import inetsoft.web.vswizard.service.VSWizardTemporaryInfoService;
import inetsoft.web.vswizard.service.WizardViewsheetService;
import org.apache.commons.lang.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.awt.*;
import java.security.Principal;
import java.util.List;
import java.util.*;

@Component
public class VSWizardObjectHandler {
   @Autowired
   public VSWizardObjectHandler(SyncInfoHandler syncInfoHandler,
                                ViewsheetService viewsheetService,
                                PlaceholderService placeholderService,
                                VSWizardBindingHandler bindingHandler,
                                WizardViewsheetService wizardVSService,
                                VSWizardTemporaryInfoService temporaryInfoService)
   {
      this.bindingHandler = bindingHandler;
      this.syncInfoHandler = syncInfoHandler;
      this.wizardVSService = wizardVSService;
      this.viewsheetService = viewsheetService;
      this.placeholderService = placeholderService;
      this.temporaryInfoService = temporaryInfoService;
   }

   /**
    * Reload aggregateInfo.
    * when aggegate of <td>assembly</td> is empty, create it for default.
    * @param temporaryInfo vs temp info.
    * @param assembly edit assembly.
    * @param tempChart temp chart for information of component wizard tree.
    * @throws Exception fixAggregateInfo error.
    */
   public void reloadAggegateInfo(VSTemporaryInfo temporaryInfo, Viewsheet vs,
                                  VSAssembly assembly, ChartVSAssembly tempChart)
      throws Exception
   {

      if(assembly instanceof CubeVSAssembly &&
         ((CubeVSAssembly) assembly).getAggregateInfo() != null &&
         !((CubeVSAssembly) assembly).getAggregateInfo().isEmpty())
      {
         tempChart.getVSChartInfo().setAggregateInfo(
            ((CubeVSAssembly) assembly).getAggregateInfo());
      }
      else if(assembly instanceof GaugeVSAssembly || assembly instanceof TextVSAssembly) {
         bindingHandler.fixAggregateInfo(temporaryInfo, vs, null);
         AggregateInfo aggInfo = tempChart.getAggregateInfo();
         DataRef[] refs = assembly.getAllBindingRefs();
         GroupRef groupRef = refs.length > 0 ? bindingHandler.findGroupRef(aggInfo, refs[0]) : null;

         if(groupRef != null) {
            //it only recommends the gauge and text when the bound column is the measure,
            //so both the gauge and text bound column should be converted to the measure.
            VSEventUtil.fixAggInfoByConvertRef(aggInfo,
               VSEventUtil.CONVERT_TO_MEASURE, groupRef.getName());
            DataRef column = ((OutputVSAssembly) assembly).getScalarBindingInfo().getColumn();

            if(column != null && column instanceof ColumnRef) {
               ColumnRef columnRef = (ColumnRef) column;

               if(columnRef.getDataRef() instanceof AttributeRef) {
                  // mark measure
                  ((AttributeRef) columnRef.getDataRef()).setRefType(DataRef.MEASURE);
               }
            }
         }

         tempChart.getVSChartInfo().setAggregateInfo(aggInfo);
      }
      else {
         bindingHandler.fixAggregateInfo(temporaryInfo, vs, null);
      }
   }

   /**
    * Reload geo cols.
    * @param assembly edit assembly.
    * @param tempChart temp chart for information of component wizard tree.
    */
   public void reloadGeoCols(VSAssembly assembly, ChartVSAssembly tempChart) {
      if(assembly instanceof ChartVSAssembly) {
         ChartVSAssembly chartVSAssembly = (ChartVSAssembly) assembly;

         tempChart.getVSChartInfo().setGeoColumns(chartVSAssembly.getVSChartInfo().getGeoColumns());
      }
   }

   /**
    * Reload source info
    * @param assembly edit assembly.
    */
   public SourceInfo reloadSourceInfo(VSAssembly assembly, ChartVSAssembly tempChart) {
      SourceInfo source = null;

      if(assembly instanceof DataVSAssembly) {
         DataVSAssembly dataVSAssembly = (DataVSAssembly) assembly;
         source = dataVSAssembly.getSourceInfo();
      }
      else if(assembly instanceof OutputVSAssembly) {
         OutputVSAssembly outputAssembly = (OutputVSAssembly) assembly;
         String tableName = outputAssembly.getTableName();
         int sourceType = outputAssembly.getSourceType();
         source = buildSourceInfo(tableName, sourceType);
      }
      else if(assembly instanceof AbstractSelectionVSAssembly) {
         AbstractSelectionVSAssembly sAssembly = (AbstractSelectionVSAssembly) assembly;
         String tableName = sAssembly.getTableName();
         int sourceType = sAssembly.getSourceType();
         source = buildSourceInfo(tableName, sourceType);
      }

      tempChart.setSourceInfo(source);

      return source;
   }

   private SourceInfo buildSourceInfo(String tableName, int sourceType) {
      if(!StringUtils.isEmpty(tableName)) {
         return new SourceInfo(sourceType, null, tableName);
      }

      return null;
   }

   public RuntimeViewsheet getOriginalRViewsheet(RuntimeViewsheet rvs, Principal principal)
      throws Exception
   {
      RuntimeViewsheet orvs = rvs;

      // update the runtime viewsheet of viewsheet pane directly.
      if(rvs.getOriginalID() != null) {
         orvs = viewsheetService.getViewsheet(
            getOriginalRuntimeId(rvs.getID(), principal), principal);
      }

      return orvs;
   }

   /**
    * Update assembly by temporary assembly
    * @param rvs              the original rvs.
    * @param currentRvs       the current rvs.
    * @param assembly         the temp assembly.
    * @param originalAssembly the original assembly which want to be edited.
    */
   public VSAssembly updateAssemblyByTemporary(RuntimeViewsheet rvs, RuntimeViewsheet currentRvs,
                                               VSAssembly assembly, VSAssembly originalAssembly,
                                               CommandDispatcher dispatcher, String linkUri)
      throws Exception
   {
      VSTemporaryInfo tempInfo = temporaryInfoService.getVSTemporaryInfo(currentRvs);
      return updateAssemblyByTemporary0(rvs, tempInfo, assembly, originalAssembly, dispatcher, linkUri);
   }

   /**
    * Update assembly by temporary assembly
    * @param rvs               the real runtime viewsheet which should be updated.
    * @param tempInfo          the VSTemporaryInfo which contains the temporary info of wizard.
    * @param temp      the temp assembly which created in wizard.
    * @param original  the original assembly which want to be edited.
    */
   private VSAssembly updateAssemblyByTemporary0(RuntimeViewsheet rvs, VSTemporaryInfo tempInfo,
                                           VSAssembly temp, VSAssembly original,
                                           CommandDispatcher dispatcher, String linkUri)
      throws Exception
   {
      Point pos = null;
      Dimension size = null;
      String oDescName = null;
      Viewsheet vs = rvs.getViewsheet();

      if(original == null) {
         pos = tempInfo.getPosition();
         size = wizardVSService.getDefaultAssemblyPixelSize(temp);
      }
      else {
         if(temp.getAssemblyType() != original.getAssemblyType()) {
            size = wizardVSService.getDefaultAssemblyPixelSize(temp);
         }
         else {
            size = original.getPixelSize();
         }

         pos = original.getPixelOffset();
         // sync zIndex
         temp.setZIndex(original.getZIndex());

         VSAssemblyInfo oinfo = original.getVSAssemblyInfo();

         if(oinfo instanceof DescriptionableAssemblyInfo) {
            oDescName = ((DescriptionableAssemblyInfo) oinfo).getDescriptionName();
         }

         Assembly container = original.getContainer();
         container = container != null ? (Assembly) container.clone() : null;
         syncContainer(container, vs);
      }

      String newName = getNewAssemblyName(vs, temp, original);

      if(original != null && !Tool.equals(newName, original.getAbsoluteName())) {
         if(vs.containsAssembly(original)) {
            placeholderService.removeVSAssembly(rvs, linkUri, original, dispatcher, true, true);
         }

         RemoveVSObjectCommand command = new RemoveVSObjectCommand();
         command.setName(original.getAbsoluteName());
         dispatcher.sendCommand(command);
      }

      syncAssemblyInfo(temp, original, newName);
      syncChartDescriptor(temp, original, rvs, tempInfo);
      syncDrillCondition(temp, original);
      // avoid duplicate assembly
      vs.removeAssembly(newName, false, true);
      temp = temp.copyAssembly(newName);
      temp.setWizardTemporary(false);
      temp.setPixelOffset(pos);
      temp.setPixelSize(size);
      updateDescription(vs, tempInfo, temp, oDescName);
      return temp;
   }

   public void syncDrillCondition(VSAssembly nassembly, VSAssembly oassembly) {
      if(!(oassembly instanceof DrillFilterVSAssembly) || !(nassembly instanceof DrillFilterVSAssembly)) {
         return;
      }

      if(isCrosstab(oassembly) && isCrosstab(nassembly)) {
         CrosstabVSAssembly temp = (CrosstabVSAssembly) nassembly;
         DrillFilterInfo drillFilterInfo = temp.getDrillFilterInfo();
         VSCrosstabInfo crosstabInfo = temp.getVSCrosstabInfo();
         Object[] refs = ArrayUtils.addAll(crosstabInfo.getRowHeaders(), crosstabInfo.getColHeaders());
         syncDrillCondition(temp, drillFilterInfo, refs);
      }
      else if(isChart(oassembly) && isChart(nassembly)) {
         ChartVSAssembly temp = (ChartVSAssembly) nassembly;
         DrillFilterInfo drillFilterInfo = temp.getDrillFilterInfo();
         VSChartInfo chartInfo = temp.getVSChartInfo();
         Object[] refs = GraphUtil.getAllDimensions(chartInfo, false).toArray();
         syncDrillCondition(temp, drillFilterInfo, refs);
      }
   }

   private void syncDrillCondition(CubeVSAssembly assembly, DrillFilterInfo filterInfo, Object[] refs) {
      DrillFilterInfo ofilterInfo = filterInfo.clone();
      Set<String> fields = ofilterInfo.getFields();

      if(fields.size() == 0) {
         return;
      }

      for(String field : fields) {
         boolean find = false;

         for(Object ref : refs) {
            if(!(ref instanceof VSDimensionRef)) {
               continue;
            }

            VSDimensionRef parentRef = VSUtil.getLastDrillLevelRef((VSDimensionRef) ref, assembly.getXCube());

            if(Objects.nonNull(parentRef) && Objects.equals(parentRef.getFullName(), field)) {
               find = true;
            }
         }

         if(!find) {
            filterInfo.setDrillFilterConditionList(field, null);
         }
      }
   }

   private boolean isChart(VSAssembly assembly) {
      return assembly instanceof ChartVSAssembly;
   }

   private boolean isCrosstab(VSAssembly assembly) {
      return assembly instanceof CrosstabVSAssembly;
   }

   /**
    * Sync container information, else will be remove from container.
    * @param ocontainer the container of the original assembly.
    * @param vs the current container need to sync information.
    */
   private void syncContainer(Assembly ocontainer, Viewsheet vs) {
      if(ocontainer == null) {
         return;
      }

      Assembly ncontainer = vs.getAssembly(ocontainer.getAbsoluteName());

      if(ncontainer == null) {
         return;
      }

      String[] children  = ((ContainerVSAssembly) ocontainer).getAssemblies();
      ((ContainerVSAssembly) ncontainer).setAssemblies(children);

      if(ocontainer instanceof TabVSAssembly) {
         String selected = ((TabVSAssembly) ocontainer).getSelectedValue();
         ((TabVSAssembly) ncontainer).setSelectedValue(selected);
      }
   }

   private void syncAssemblyInfo(VSAssembly nassembly, VSAssembly oassembly, String newName) {
      if(!syncInfoHandler.shouldSyncInfo(nassembly, oassembly)) {
         return;
      }

      String script = ((AbstractVSAssembly) nassembly).getScript();

      // sync script, use the new created name to replace the temp name.
      if(!StringUtils.isEmpty(script)) {
         ((AbstractVSAssembly) nassembly).setScript(
            SyncAssemblyHandler.updateScript(script, nassembly.getName(), newName));
      }

      VSAssemblyInfo oinfo = oassembly.getVSAssemblyInfo();
      VSAssemblyInfo ninfo = nassembly.getVSAssemblyInfo();

      // sync annotation
      if(oinfo instanceof DataVSAssemblyInfo && ninfo instanceof DataVSAssemblyInfo) {
         DataVSAssemblyInfo odinfo = (DataVSAssemblyInfo) oinfo;
         DataVSAssemblyInfo ndinfo = (DataVSAssemblyInfo) ninfo;
         List<String> annotations = odinfo.getAnnotations();

         if(annotations != null) {
            annotations.stream().forEach(annotation -> {
               ndinfo.addAnnotation(annotation);
            });
         }
      }
   }

   private void syncChartDescriptor(VSAssembly nassembly, VSAssembly oassembly,
                                    RuntimeViewsheet rvs, VSTemporaryInfo tempInfo)
      throws Exception
   {
      if(!(nassembly instanceof ChartVSAssembly)) {
         return;
      }

      ChartVSAssembly nchart = (ChartVSAssembly) nassembly;
      WizardRecommenderUtil.prepareCalculateRefs(
         rvs, (ChartVSAssemblyInfo) nchart.getVSAssemblyInfo(), tempInfo);

      if(!(oassembly instanceof ChartVSAssembly)) {
         nchart.getChartDescriptor().getPlotDescriptor().setInPlot(true);
         return;
      }

      ChartVSAssembly ochart = (ChartVSAssembly) oassembly;
      ChartDescriptor odesc = ochart.getChartDescriptor();
      PlotDescriptor oplot = odesc == null ? null : odesc.getPlotDescriptor();
      ChartDescriptor ndesc = nchart.getChartDescriptor();
      PlotDescriptor nplot = ndesc == null ? null : ndesc.getPlotDescriptor();

      if(oplot != null && nplot != null) {
         nplot.setInPlot(oplot.isInPlot());
      }
   }

   /**
    * Add or update the description text assembly for the target assembly.
    */
   private void updateDescription(Viewsheet vs, VSTemporaryInfo tempInfo, VSAssembly tempAssembly,
                                  String originalDescriptionName)
   {
      VSAssemblyInfo info = tempAssembly.getVSAssemblyInfo();

      if(info == null) {
         return;
      }

      Point pos = tempAssembly.getPixelOffset();
      String desc = tempInfo.getDescription();

      if(tempAssembly instanceof ChartVSAssembly && !StringUtils.isEmpty(desc)) {
         ((ChartVSAssembly) tempAssembly).setTitleValue(desc);
         ((ChartVSAssemblyInfo) tempAssembly.getVSAssemblyInfo()).setTitleVisibleValue(true);
      }
      else if(tempAssembly instanceof TableDataVSAssembly && !StringUtils.isEmpty(desc)) {
         ((TableDataVSAssembly) tempAssembly).setTitleValue(desc);
         ((TableDataVSAssemblyInfo) tempAssembly.getVSAssemblyInfo()).setTitleVisible(true);
      }

      if(!(info instanceof DescriptionableAssemblyInfo)) {
         if(!StringUtils.isEmpty(originalDescriptionName)) {
            vs.removeAssembly(originalDescriptionName);
         }

         return;
      }

      DescriptionableAssemblyInfo dinfo = (DescriptionableAssemblyInfo) info;
      String descName = dinfo.getDescriptionName();

      if(descName != null && StringUtils.isEmpty(desc)) {
         tempAssembly.setPixelOffset(new Point(pos.x, pos.y - VSWizardConstants.GRID_CELL_HEIGHT));
         dinfo.setDescriptionName(null);
         vs.removeAssembly(descName);
      }
      else if(descName != null) {
         TextVSAssembly textVSAssembly = (TextVSAssembly) vs.getAssembly(descName);
         textVSAssembly.setTextValue(desc);
      }
      else if(originalDescriptionName != null && !StringUtils.isEmpty(desc)) {
         TextVSAssembly textVSAssembly = (TextVSAssembly) vs.getAssembly(originalDescriptionName);
         textVSAssembly.setTextValue(desc);
         dinfo.setDescriptionName(originalDescriptionName);
      }
      else if(!StringUtils.isEmpty(desc)) {
         TextVSAssembly descAssembly =
            new TextVSAssembly(vs, AssetUtil.getNextName(vs, Viewsheet.TEXT_ASSET));
         TextVSAssemblyInfo descInfo = (TextVSAssemblyInfo) descAssembly.getInfo();
         descAssembly.initDefaultFormat();
         descInfo.setTextValue(desc);

         descInfo.setPixelOffset(pos);
         descInfo.setPadding(new Insets(0, 0, 0, 5));
         // match the default font for text in insertVsObject
         descInfo.getFormat().getDefaultFormat().setFontValue(
            VSAssemblyInfo.getDefaultFont(Font.BOLD, 12));

         if(tempAssembly instanceof TextVSAssembly) {
            tempAssembly.setPixelOffset(
               new Point(pos.x + descAssembly.getBounds().width, pos.y));
            descInfo.getFormat().getUserDefinedFormat()
               .setAlignmentValue(StyleConstants.H_RIGHT | StyleConstants.V_CENTER);
            descInfo.getPixelSize().height = tempAssembly.getBounds().height;
         }
         else {
            tempAssembly.setPixelOffset(
               new Point(pos.x, pos.y + VSWizardConstants.GRID_CELL_HEIGHT));
            descInfo.getPixelSize().width = tempAssembly.getBounds().width;
            descInfo.getPixelSize().height = VSWizardConstants.GRID_CELL_HEIGHT;
         }

         vs.addAssembly(descAssembly);
         dinfo.setDescriptionName(descAssembly.getAbsoluteName());
      }
   }

   /**
    * When switch between chart wizard and binding pane, the directly original runtimeid
    * may not be the real original runtimeid, this funciton is used to get the real
    * original runtime viewsheet id.
    */
   public String getOriginalRuntimeId(String rid, Principal principal)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(rid, principal);

      while(rvs != null && rvs.getOriginalID() != null) {
         return getOriginalRuntimeId(rvs.getOriginalID(), principal);
      }

      return rid;
   }

   public String getNewAssemblyName(Viewsheet vs, VSAssembly tempAssembly,
                                    VSAssembly originalAssembly)
   {
      boolean isCreate = originalAssembly == null;
      int newType = tempAssembly.getAssemblyType();
      String newName;

      if(isCreate) {
         newName = AssetUtil.getNextName(vs, newType);
      }
      else {
         boolean isTypeChanged = newType != originalAssembly.getAssemblyType();
         newName = isTypeChanged ? AssetUtil.getNextName(vs, newType)
            : originalAssembly.getAbsoluteName();
      }

      return newName;
   }

   private boolean isSupportBindingEditor(VSAssembly assembly) {
      return assembly instanceof ChartVSAssembly ||
             assembly instanceof TableVSAssembly ||
             assembly instanceof CrosstabVSAssembly;
   }

   private final SyncInfoHandler syncInfoHandler;
   private final ViewsheetService viewsheetService;
   private final PlaceholderService placeholderService;
   private final VSWizardBindingHandler bindingHandler;
   private final WizardViewsheetService wizardVSService;
   private final VSWizardTemporaryInfoService temporaryInfoService;
}
