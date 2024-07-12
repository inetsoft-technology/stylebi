/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.composer.vs.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.AssetTreeModel;
import inetsoft.report.composition.AssetTreeModel.Node;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.CalculateRef;
import inetsoft.uql.viewsheet.internal.SelectionVSUtil;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.viewsheet.service.VSOutputService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

@Controller
class DataOutputController {
   /**
    * @param vsOutputService VSInputService instance
    * @param viewsheetService
    */
   @Autowired
   public DataOutputController(VSOutputService vsOutputService, ViewsheetService viewsheetService) {
      this.vsOutputService = vsOutputService;
      this.viewsheetService = viewsheetService;
   }

   /**
    *  Gets the Columns of the specified table for data output pane
    * @param table      Table name
    * @param runtimeId  Runtime viewsheet id
    * @param principal  the principal user
    * @return the columns of the table
    * @throws Exception if could not get the columns
    */
   @RequestMapping(
      value = "/vs/dataOutput/table/columns",
      method = RequestMethod.GET
   )
   @ResponseBody
   public OutputColumnModel[] getOutputTableColumns(@RequestParam("table") String table,
                                                    @RequestParam("runtimeId") String runtimeId,
                                                    Principal principal)
      throws Exception
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

   /**
    *  Gets the columns of the specified cube
    * @param table      The cube name
    * @param runtimeId  the runtime viewsheet id
    * @param principal  the principal user
    * @return the columns of the cube
    * @throws Exception if could not get the columns
    */
   @RequestMapping(
      value = "/vs/dataOutput/cube/columns",
      method = RequestMethod.GET
   )
   @ResponseBody
   public OutputCubeModel[] getOutputCubeColumns(@RequestParam("table") String table,
                                                 @RequestParam("runtimeId") String runtimeId,
                                                 @RequestParam("columnType") String columnType,
                                                 Principal principal)
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

      Node[] nodes = ((Node) model.getRoot()).getNodes();

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

      Node measureRoot = nodes[0];

      if(measureRoot == null) {
         return result;
      }

      nodes = measureRoot.getNodes();

      if(nodes == null || nodes.length < 1) {
         return result;
      }

      List<OutputCubeModel> cubeList = new ArrayList<>();

      for(Node node : nodes) {
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

   /**
    * Gets the measures columns of the specified table for selection data pane
    *
    * @param tables    table names
    * @param runtimeId Runtime viewsheet id
    * @param principal the principal user
    * @return the columns of the table
    * @throws Exception if could not get the columns
    */
   @RequestMapping(
      value = "/vs/dataOutput/selection/columns",
      method = RequestMethod.GET
   )
   @ResponseBody
   public OutputColumnRefModel[] getOutputSelectionColumns(
      @RequestParam("table") List<String> tables,
      @RequestParam("runtimeId") String runtimeId,
      Principal principal) throws Exception
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
            List<Node> cubes = AssetEventUtil.getCubes(principal, null, rvs.getViewsheet());

            // Filter out the parent cube for the selected column
            Node cubeTable =
               cubes.stream()
                  .filter(cube -> cube.getEntry().getPath().equals(table.substring(Assembly.CUBE_VS.length())))
                  .findFirst().get();

            // Filter out the measures columns of the parent
            Node measuresCube =
               Arrays.stream(cubeTable.getNodes())
                  .filter(node -> "Measure".equals(node.getEntry().getProperty("localStr")))
                  .findFirst().get();

            // Add an OutputColumnRefModel for each measure
            for(Node measure: measuresCube.getNodes()) {
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
