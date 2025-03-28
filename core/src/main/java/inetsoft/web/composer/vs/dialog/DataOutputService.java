/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.web.composer.vs.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.*;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.CalculateRef;
import inetsoft.uql.viewsheet.internal.SelectionVSUtil;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.viewsheet.service.VSOutputService;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;

@Service
@ClusterProxy
public class DataOutputService {

   public DataOutputService(ViewsheetService viewsheetService,
                            VSOutputService vsOutputService)
   {
      this.viewsheetService = viewsheetService;
      this.vsOutputService = vsOutputService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public OutputColumnModel[] getOutputTableColumns(@ClusterProxyKey String runtimeId,
                                                    String table, Principal principal) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      ColumnSelection selection =
         this.vsOutputService.getOutputTableColumns(rvs, table, true, principal);
      List<OutputColumnModel> columnList = new ArrayList<>();

      for(int i = 0; i < selection.getAttributeCount(); i++) {
         OutputColumnModel outputColumn = new OutputColumnModel();
         ColumnRef ref = (ColumnRef) selection.getAttribute(i);
         String name = ref.getName();
         String label = ref.getView() == null ? name : ref.getView();
         label = label.isEmpty() ? "Column [" + i + "]" : label;

         if(name.startsWith("Range@")) {
            continue;
         }

         outputColumn.setName(name);
         outputColumn.setType(ref.getDataType());
         outputColumn.setTooltip(ref.getDescription());
         outputColumn.setLabel(Tool.localize(label));
         outputColumn.setAggregateCalcField(ref instanceof CalculateRef &&
                                               !((CalculateRef) ref).isBaseOnDetail());
         outputColumn.setDefaultFormula(ref.getDefaultFormula());
         columnList.add(outputColumn);
      }

      return columnList.toArray(new OutputColumnModel[0]);
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public OutputCubeModel[] getOutputCubeColumns(@ClusterProxyKey String runtimeId, String table,
                                                 String columnType, Principal principal)
      throws Exception
   {
      OutputCubeModel[] result = new OutputCubeModel[0];
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);

      // Get measures columns and dimensions columns unless columnType parameter is passed in
      String showMeasures = columnType == null ? "true" :
         "measures".equals(columnType) ? "true" : "false";
      String showDimensions = columnType == null ? "true" :
         "dimensions".equals(columnType) ? "true" : "false";

      AssetTreeModel model = vsOutputService.getVSTreeModel(rvs, table, table,
                                                            showMeasures, showDimensions, "true", null, principal);

      AssetTreeModel.Node[] nodes = ((AssetTreeModel.Node) model.getRoot()).getNodes();

      if(nodes == null || nodes.length < 1) {
         return result;
      }

      nodes = nodes[0].getNodes();

      if(nodes == null || nodes.length < 1) {
         return result;
      }

      nodes = nodes[0].getNodes();

      if(nodes == null || nodes.length < 1) {
         return result;
      }

      AssetTreeModel.Node measureRoot = nodes[0];

      if(measureRoot == null) {
         return result;
      }

      nodes = measureRoot.getNodes();

      if(nodes == null || nodes.length < 1) {
         return result;
      }

      List<OutputCubeModel> cubeList = new ArrayList<>();

      for(AssetTreeModel.Node node : nodes) {
         OutputCubeModel cubeModel = new OutputCubeModel();
         AssetEntry entry = node.getEntry();
         String localString = entry.getProperty("localStr");

         if(localString == null || localString.isEmpty()) {
            localString = node.toString();
         }

         cubeModel.setName(localString);
         cubeModel.setType("cubeNode");
         cubeModel.setData(entry.getProperty("attribute"));
         cubeModel.setSqlProvider("true".equals(entry.getProperty("sqlServer")));
         cubeList.add(cubeModel);
      }

      return cubeList.toArray(result);
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public OutputColumnRefModel[] getOutputSelectionColumns(@ClusterProxyKey String runtimeId,
                                                           List<String> tables, Principal principal) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);

      final List<OutputColumnRefModel> columns = new ArrayList<>();

      for(int i = 0; i < tables.size(); i++) {
         final String table = tables.get(i);
         List<OutputColumnRefModel> tableColumns = new ArrayList<>();

         if(!table.contains(Assembly.CUBE_VS)) { // Table
            ColumnSelection selection =
               this.vsOutputService.getOutputSelectionColumns(rvs, table, true, principal);

            for(int j = 0; j < selection.getAttributeCount(); j++) {
               ColumnRef columnRef = (ColumnRef) selection.getAttribute(j);
               OutputColumnRefModel columnModel = new OutputColumnRefModel();
               columnModel.setView(columnRef.toView());
               columnModel.setName(columnRef.getName());
               columnModel.setEntity(columnRef.getEntity());
               columnModel.setAttribute(columnRef.getAttribute());
               columnModel.setDataType(columnRef.getDataType());
               columnModel.setRefType(columnRef.getRefType());
               columnModel.setAlias(columnRef.getAlias());
               columnModel.setDescription(columnRef.getDescription());
               tableColumns.add(columnModel);
            }
         }
         else { // Cube
            // Get all cubes
            List<AssetTreeModel.Node> cubes = AssetEventUtil.getCubes(principal, null, rvs.getViewsheet());

            // Filter out the parent cube for the selected column
            AssetTreeModel.Node cubeTable =
               cubes.stream()
                  .filter(cube -> cube.getEntry().getPath().equals(table.substring(Assembly.CUBE_VS.length())))
                  .findFirst().get();

            // Filter out the measures columns of the parent
            AssetTreeModel.Node measuresCube =
               Arrays.stream(cubeTable.getNodes())
                  .filter(node -> "Measure".equals(node.getEntry().getProperty("localStr")))
                  .findFirst().get();

            // Add an OutputColumnRefModel for each measure
            for(AssetTreeModel.Node measure: measuresCube.getNodes()) {
               OutputColumnRefModel columnModel = new OutputColumnRefModel();
               columnModel.setView(measure.getEntry().getProperty("alias"));
               columnModel.setName(measure.getEntry().getProperty("alias"));
               columnModel.setEntity(null);
               columnModel.setAttribute(measure.getEntry().getProperty("attribute"));
               columnModel.setDataType(measure.getEntry().getProperty("dtype"));
               columnModel.setRefType(Integer.parseInt(measure.getEntry().getProperty("refType")));
               columnModel.setAlias(measure.getEntry().getProperty("alias"));
               tableColumns.add(columnModel);
            }
         }

         if(i == 0) {
            columns.addAll(tableColumns);
         }
         // Find column intersection
         else {
            final Iterator<OutputColumnRefModel> columnsIterator = columns.iterator();

            while(columnsIterator.hasNext()) {
               final OutputColumnRefModel column = columnsIterator.next();

               final boolean hasMatchingColumn = tableColumns.stream()
                  .anyMatch((c) -> SelectionVSUtil.areColumnsCompatible(c, column));

               if(!hasMatchingColumn) {
                  columnsIterator.remove();
               }
            }
         }
      }

      final OutputColumnRefModel noneColumn = new OutputColumnRefModel();
      noneColumn.setView(Catalog.getCatalog().getString("None"));
      noneColumn.setName(null);
      columns.add(0, noneColumn);
      return columns.toArray(new OutputColumnRefModel[0]);
   }

   private final VSOutputService vsOutputService;
   private final ViewsheetService viewsheetService;
}
