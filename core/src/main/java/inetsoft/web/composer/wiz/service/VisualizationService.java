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

package inetsoft.web.composer.wiz.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.OutputVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.WizUtil;
import inetsoft.util.Tool;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.composer.wiz.command.SetWizDetailsCommand;
import inetsoft.web.composer.wiz.model.VisualizationDetailModel;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@ClusterProxy
public class VisualizationService {
   public VisualizationService(ViewsheetService viewsheetService, AssetRepository assetRepository) {
      this.viewsheetService = viewsheetService;
      this.assetRepository = assetRepository;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public TreeNodeModel getComponents(@ClusterProxyKey String runtimeId, Principal principal) throws Exception {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);

      if(rvs == null || rvs.getViewsheet() == null || rvs.getViewsheet().getWizInfo() == null ||
         !rvs.getViewsheet().getWizInfo().isWizSheet() ||
         rvs.getViewsheet().getWizInfo().getVisualizations() == null ||
         rvs.getViewsheet().getWizInfo().getVisualizations().isEmpty())
      {
         return TreeNodeModel.builder().build();
      }

      List<TreeNodeModel> children = new ArrayList<>();

      for(String visualization : rvs.getViewsheet().getWizInfo().getVisualizations()) {
         try {
            AssetEntry assetEntry = AssetEntry.createAssetEntry(visualization);

            if(assetEntry == null) {
               LOG.warn("Invalid visualization entry for " + visualization);
               continue;
            }

            AssetEntry vEntry = assetRepository.getAssetEntry(assetEntry);

            if(vEntry == null) {
               LOG.warn("Asset entry could not be found for " + visualization);
               continue;
            }

            if(!Tool.equals(vEntry.getProperty("visualizationScope"), WizUtil.VisualizationScope.PRIVATE.getValue())) {
               continue;
            }

            TreeNodeModel.Builder builder = TreeNodeModel.builder()
               .icon("new-viewsheet-icon")
               .dragName("dragVisualization")
               .data(vEntry)
               .leaf(true);

            if(WizUtil.isWizCopyEntry(vEntry, true)) {
               AssetEntry wizOriginalVisualization = WizUtil.createWizOriginalVisualization(vEntry);
               builder.label(wizOriginalVisualization.getName());
            }
            else {
               builder.label(vEntry.getName());
            }

            children.add(builder.build());
         }
         catch(Exception e) {
            LOG.error("Failed to load visualization entry: {}", visualization, e);
         }
      }

      return TreeNodeModel.builder()
         .children(children)
         .build();
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public TreeNodeModel getVisualizations(@ClusterProxyKey String runtimeId, Principal principal) throws Exception {
      IdentityID user = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());
      AssetEntry visualizationsEntry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.REPOSITORY_FOLDER,
         VISUALIZATION_ROOT_FOLDER_PATH, user);
      AssetEntry.Selector assetSelector = new AssetEntry.Selector(
         AssetEntry.Type.FOLDER, AssetEntry.Type.VIEWSHEET, AssetEntry.Type.REPOSITORY_FOLDER);
      AssetEntry[] entries = assetRepository.getEntries(
         visualizationsEntry, principal, ResourceAction.READ, assetSelector);
      List<TreeNodeModel> children = new ArrayList<>();

      if(entries != null) {
         for(AssetEntry entry : entries) {
            if(entry.isFolder()) {
               children.add(TreeNodeModel.builder()
                  .label(entry.getName())
                  .icon("folder-toolbox-icon")
                  .data(entry)
                  .leaf(false)
                  .build());
            }
            else if(WizUtil.VisualizationScope.SHARED.getValue().equals(entry.getProperty("visualizationScope"))) {
               TreeNodeModel.Builder builder = TreeNodeModel.builder()
                  .icon("new-viewsheet-icon")
                  .dragName("dragVisualization")
                  .data(entry)
                  .leaf(true);

               if(WizUtil.isWizCopyEntry(entry, true)) {
                  AssetEntry wizOriginalVisualization = WizUtil.createWizOriginalVisualization(entry);
                  builder.label(wizOriginalVisualization.getName());
               }
               else {
                  builder.label(entry.getName());
               }

               children.add(builder.build());
            }
         }
      }

      return TreeNodeModel.builder()
         .children(children)
         .build();
   }

   public void sendDetailsCommandIfWiz(Viewsheet vs, CommandDispatcher dispatcher) {
      if(vs != null && vs.getWizInfo() != null && vs.getWizInfo().isWizVisualization()) {
         dispatcher.sendCommand(buildDetailsCommand(vs));
      }
   }

   public SetWizDetailsCommand buildDetailsCommand(Viewsheet vs) {
      if(vs == null) {
         return new SetWizDetailsCommand(Collections.emptyList(), Collections.emptyList());
      }

      List<VisualizationDetailModel> bindingDetails = Collections.emptyList();

      for(Assembly assembly : vs.getAssemblies()) {
         if(assembly instanceof ChartVSAssembly chart) {
            bindingDetails = buildChartDetails(chart);
            break;
         }
         else if(assembly instanceof CrosstabVSAssembly ctab) {
            bindingDetails = buildCrosstabDetails(ctab);
            break;
         }
         else if(assembly instanceof TableVSAssembly table) {
            bindingDetails = buildTableDetails(table);
            break;
         }
         else if(assembly instanceof OutputVSAssembly output) {
            bindingDetails = buildOutputDetails(output);
            break;
         }
      }

      List<VisualizationDetailModel> worksheetDetails = buildWorksheetDetails(vs.getBaseWorksheet());
      return new SetWizDetailsCommand(bindingDetails, worksheetDetails);
   }

   private List<VisualizationDetailModel> buildWorksheetDetails(Worksheet ws) {
      if(ws == null) {
         return Collections.emptyList();
      }

      List<VisualizationDetailModel> result = new ArrayList<>();
      List<String> joinLines = new ArrayList<>();

      for(Assembly assembly : ws.getAssemblies()) {
         if(assembly instanceof MirrorAssembly) {
            continue;
         }

         if(assembly instanceof AbstractJoinTableAssembly joinTable) {
            Enumeration<?> opTables = joinTable.getOperatorTables();

            while(opTables.hasMoreElements()) {
               String[] pair = (String[]) opTables.nextElement();
               String ltable = pair[0];
               String rtable = pair[1];
               TableAssemblyOperator op = joinTable.getOperator(ltable, rtable);

               if(op != null) {
                  for(TableAssemblyOperator.Operator oper : op.getOperators()) {
                     DataRef left = oper.getLeftAttribute();
                     DataRef right = oper.getRightAttribute();

                     if(left != null && right != null) {
                        joinLines.add(ltable + "." + left.getAttribute()
                                         + " = " + rtable + "." + right.getAttribute());
                     }
                  }
               }
            }
         }
         else if(assembly instanceof TableAssembly tableAssembly) {
            ColumnSelection cols = tableAssembly.getColumnSelection(true);

            if(cols != null && cols.getAttributeCount() > 0) {
               String fields = IntStream.range(0, cols.getAttributeCount())
                  .mapToObj(i -> cols.getAttribute(i).getAttribute())
                  .filter(name -> name != null && !name.isEmpty())
                  .collect(Collectors.joining(", "));
               result.add(new VisualizationDetailModel(assembly.getName(), fields));
            }
         }
      }

      if(!joinLines.isEmpty()) {
         result.add(new VisualizationDetailModel("Joins", String.join("; ", joinLines)));
      }

      return result;
   }

   private List<VisualizationDetailModel> buildChartDetails(ChartVSAssembly chart) {
      VSChartInfo chartInfo = chart.getVSChartInfo();
      List<VisualizationDetailModel> details = new ArrayList<>();

      if(chartInfo == null) {
         return details;
      }

      addRefsDetail(details, "X Axis", chartInfo.getXFields());
      addRefsDetail(details, "Y Axis", chartInfo.getYFields());
      addRefsDetail(details, "Group", chartInfo.getGroupFields());
      addAestheticDetail(details, "Color", chartInfo.getColorField());
      addAestheticDetail(details, "Shape", chartInfo.getShapeField());
      addAestheticDetail(details, "Size", chartInfo.getSizeField());
      addAestheticDetail(details, "Text", chartInfo.getTextField());
      addRefDetail(details, "Path", chartInfo.getPathField());

      if(chartInfo instanceof CandleChartInfo candle) {
         addRefDetail(details, "High", candle.getHighField());
         addRefDetail(details, "Low", candle.getLowField());
         addRefDetail(details, "Close", candle.getCloseField());
         addRefDetail(details, "Open", candle.getOpenField());
      }
      else if(chartInfo instanceof GanttChartInfo gantt) {
         addRefDetail(details, "Start", gantt.getStartField());
         addRefDetail(details, "End", gantt.getEndField());
         addRefDetail(details, "Milestone", gantt.getMilestoneField());
      }
      else if(chartInfo instanceof RelationChartInfo relation) {
         addRefDetail(details, "Source", relation.getSourceField());
         addRefDetail(details, "Target", relation.getTargetField());
      }

      return details;
   }

   private List<VisualizationDetailModel> buildCrosstabDetails(CrosstabVSAssembly ctab) {
      VSCrosstabInfo crosstabInfo = ctab.getVSCrosstabInfo();
      List<VisualizationDetailModel> details = new ArrayList<>();

      if(crosstabInfo == null) {
         return details;
      }

      addRefsDetail(details, "Row Headers", (VSDataRef[]) crosstabInfo.getDesignRowHeaders());
      addRefsDetail(details, "Col Headers", (VSDataRef[]) crosstabInfo.getDesignColHeaders());
      addRefsDetail(details, "Aggregates", (VSDataRef[]) crosstabInfo.getDesignAggregates());

      return details;
   }

   private List<VisualizationDetailModel> buildTableDetails(TableVSAssembly table) {
      ColumnSelection cols = table.getColumnSelection();
      List<VisualizationDetailModel> details = new ArrayList<>();

      if(cols != null && cols.getAttributeCount() > 0) {
         String columns = IntStream.range(0, cols.getAttributeCount())
            .mapToObj(i -> cols.getAttribute(i).getAttribute())
            .filter(name -> name != null && !name.isEmpty())
            .collect(Collectors.joining(", "));
         details.add(new VisualizationDetailModel("Columns", columns));
      }

      return details;
   }

   private List<VisualizationDetailModel> buildOutputDetails(OutputVSAssembly output) {
      OutputVSAssemblyInfo outputInfo = (OutputVSAssemblyInfo) output.getInfo();
      ScalarBindingInfo binding = outputInfo.getScalarBindingInfo();
      List<VisualizationDetailModel> details = new ArrayList<>();

      if(binding != null && binding.getColumn() != null) {
         AggregateFormula formula = binding.getAggregateFormula();
         String col1 = binding.getColumn().getAttribute();

         if(formula == null || AggregateFormula.NONE.equals(formula)) {
            details.add(new VisualizationDetailModel("Column", col1));
         }
         else {
            StringBuilder value = new StringBuilder(formula.getFormulaName())
               .append("(").append(col1);

            DataRef col2 = binding.getSecondaryColumn();

            if(col2 != null && formula.isTwoColumns()) {
               value.append(", ").append(col2.getAttribute());
            }

            value.append(")");
            details.add(new VisualizationDetailModel("Column", value.toString()));
         }
      }

      return details;
   }

   private void addRefsDetail(List<VisualizationDetailModel> details, String label, VSDataRef[] refs) {
      if(refs != null && refs.length > 0) {
         details.add(new VisualizationDetailModel(label, joinRefs(refs)));
      }
   }

   private void addRefDetail(List<VisualizationDetailModel> details, String label, ChartRef ref) {
      if(ref != null) {
         details.add(new VisualizationDetailModel(label, ref.getFullName()));
      }
   }

   private void addAestheticDetail(List<VisualizationDetailModel> details, String label, AestheticRef ref) {
      if(ref != null && ref.getDataRef() != null) {
         details.add(new VisualizationDetailModel(label, ref.getFullName()));
      }
   }

   private String joinRefs(VSDataRef[] refs) {
      return Arrays.stream(refs)
         .map(VSDataRef::getFullName)
         .collect(Collectors.joining(", "));
   }

   public static final String VISUALIZATION_ROOT_FOLDER_PATH = "visualizations-593bb4a4-fd6d-4178-b3f0-c89dad407f02";
   private final ViewsheetService viewsheetService;
   private final AssetRepository assetRepository;
   private static final Logger LOG = LoggerFactory.getLogger(VisualizationService.class);
}
