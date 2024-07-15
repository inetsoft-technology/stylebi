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
package inetsoft.web.binding.dnd.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.DataMap;
import inetsoft.report.composition.execution.VSAQuery;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.graph.ChangeChartProcessor;
import inetsoft.uql.asset.AggregateInfo;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.CategoricalColorFrameContext;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.web.binding.controller.ChangeChartAestheticController;
import inetsoft.web.binding.dnd.ChartAestheticDropTarget;
import inetsoft.web.binding.event.VSDndEvent;
import inetsoft.web.binding.handler.*;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.List;
import java.util.Set;

/**
 * This class handles get vsobjectmodel from the server.
 */
@Controller
public class VSChartDndController extends VSAssemblyDndController {
   /**
    * Creates a new instance of <tt>VSViewController</tt>.
    *
    * @param runtimeViewsheetRef reference to the runtime viewsheet associated with the
    *                            WebSocket session.
    */
   @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
   @Autowired
   public VSChartDndController(
      RuntimeViewsheetRef runtimeViewsheetRef,
      VSBindingService bfactory,
      VSAssemblyInfoHandler assemblyInfoHandler,
      VSChartDataHandler dataHandler,
      VSChartBindingHandler chartHandler,
      VSObjectModelFactoryService objectModelService,
      ViewsheetService viewsheetService,
      PlaceholderService placeholderService)
   {
      super(runtimeViewsheetRef, bfactory, assemblyInfoHandler, objectModelService,
            viewsheetService, placeholderService);
      this.chartHandler = chartHandler;
      this.dataHandler = dataHandler;
   }

   /**
    * This method is to get vsobjectmodel for edit binding pane.
    *
    */
   @Undoable
   @LoadingMask
   @MessageMapping(value="/vschart/dnd/addRemoveColumns")
   public void addRemoveColumns(@Payload VSDndEvent event, Principal principal,
      @LinkUri String linkUri, CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs = getRuntimeVS(principal);

      if(rvs == null) {
         return;
      }

      ChartVSAssembly assembly = (ChartVSAssembly)getVSAssembly(rvs, event.name());
      ChartVSAssembly clone = (ChartVSAssembly) assembly.clone();
      chartHandler.addRemoveColumns(rvs, clone, event.getTransfer(), event.getDropTarget());
      applyChartInfo(rvs, assembly, (ChartVSAssemblyInfo) clone.getInfo(), dispatcher,
         event, linkUri, true);
   }

   /**
    * This method is to get vsobjectmodel for edit binding pane.
    *
    */
   @Undoable
   @LoadingMask
   @MessageMapping(value="/vschart/dnd/addColumns")
   public void addColumns(@Payload VSDndEvent event, Principal principal,
      @LinkUri String linkUri, CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs = getRuntimeVS(principal);

      if(rvs == null) {
         return;
      }

      ChartVSAssembly assembly = (ChartVSAssembly) getVSAssembly(rvs, event.name());
      ChartVSAssembly oassembly = assembly.clone();
      ChartVSAssemblyInfo oinfo = (ChartVSAssemblyInfo) oassembly.getInfo();
      VSChartInfo chartInfo = oinfo.getVSChartInfo();
      int geoSize = chartInfo instanceof VSMapInfo ?
         ((VSMapInfo) chartInfo).getGeoFieldCount() : 0;

      if(oinfo.getVSChartInfo().getFields().length + geoSize + event.getEntries().length >
         Util.getOrganizationMaxColumn())
      {
         MessageCommand command = new MessageCommand();
         command.setMessage(Util.getColumnLimitMessage());
         command.setType(MessageCommand.Type.ERROR);
         dispatcher.sendCommand(command);

         return;
      }

      ChartVSAssembly nassembly = assembly.clone();
      ChartVSAssemblyInfo ninfo = (ChartVSAssemblyInfo) nassembly.getInfo();
      boolean check = false;

      // Handle source changed.
      if(sourceChanged(assembly, event.getTable())) {
         check = true;
         changeSource(nassembly, event.getTable(), event.getSourceType());
         VSChartInfo vsChartInfo = ninfo.getVSChartInfo();
         VSUtil.setDefaultGeoColumns(vsChartInfo, rvs, event.getTable());
         AggregateInfo ainfo = vsChartInfo.getAggregateInfo();

         if(ainfo != null) {
            List<DataRef> calcFields = ainfo.getFormulaFields();
            Set<String> calcFieldsRefs = ainfo.removeFormulaFields(calcFields);
            vsChartInfo.removeFormulaField(calcFieldsRefs);
         }
      }

      if(ninfo.getSourceInfo() == null) {
         ninfo.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, event.getTable()));
      }

      if(check) {
         SourceInfo source = nassembly.getSourceInfo();

         if(source.getType() == SourceInfo.VS_ASSEMBLY) {
            VSAQuery query = VSAQuery.createVSAQuery(rvs.getViewsheetSandbox(),
                                                     nassembly, DataMap.DETAIL);
            query.createAssemblyTable(nassembly.getTableName());
         }
      }

      if(check || VSUtil.isVSAssemblyBinding(event.getTable())) {
         validateBinding(nassembly);
      }

      Viewsheet vs = rvs.getViewsheet();

      if(vs != null) {
         // when drop field to color, should clear old static colors and reassign color for chart.
         if(event.getDropTarget() instanceof ChartAestheticDropTarget &&
            "6".equals(((ChartAestheticDropTarget) event.getDropTarget()).getDropType()))
         {
            ChartAestheticDropTarget target = (ChartAestheticDropTarget) event.getDropTarget();
            vs.clearSharedFrames();
            VSChartHandler.clearColorFrame(nassembly.getVSChartInfo(), false,
                // only clear the target aggr color frame. (60178)
                (target.getAggr() != null ? target.getAggr().createDataRef() : null));
         }

         CategoricalColorFrameContext.getContext().setSharedFrames(vs.getSharedFrames());
      }

      chartHandler.addColumns(rvs, nassembly, event.getEntries(), event.getDropTarget(),
         dispatcher, linkUri);

      applyChartInfo(rvs, assembly, oinfo, ninfo,
         dispatcher, event, "/events/vschart/dnd/addColumns", linkUri, true);
   }

   /**
    * This method is to get vsobjectmodel for edit binding pane.
    *
    */
   @Undoable
   @LoadingMask
   @MessageMapping(value="/vschart/dnd/removeColumns")
   public void removeColumns(@Payload VSDndEvent event, Principal principal,
      @LinkUri String linkUri, CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs = getRuntimeVS(principal);

      if(rvs == null) {
         return;
      }

      ChartVSAssembly assembly = (ChartVSAssembly) getVSAssembly(rvs, event.name());
      ChartVSAssembly clone = assembly.clone();
      chartHandler.removeColumns(rvs, clone, event.getTransfer(), dispatcher, linkUri);
      GraphUtil.syncWorldCloudColor(clone.getVSChartInfo());
      applyChartInfo(rvs, assembly, (ChartVSAssemblyInfo)clone.getInfo(), dispatcher, event, linkUri, false);
   }

   private void applyChartInfo(RuntimeViewsheet rvs, VSAssembly assembly,
      ChartVSAssemblyInfo clone, CommandDispatcher dispatcher, VSDndEvent event,
      String linkUri, boolean addedColumns)
      throws Exception
   {
      applyChartInfo(rvs, assembly, null, clone, dispatcher, event, null, linkUri, addedColumns);
   }

   private void applyChartInfo(RuntimeViewsheet rvs, VSAssembly assembly,
      ChartVSAssemblyInfo oinfo, ChartVSAssemblyInfo clone,
      CommandDispatcher dispatcher, VSDndEvent event,
      String url, String linkUri, boolean addedColumns)
      throws Exception
   {
      (new ChangeChartProcessor()).fixSizeFrame(clone.getVSChartInfo());
      dataHandler.changeChartData(rvs, oinfo, clone, url, event, dispatcher);

      if(addedColumns) {
         ChangeChartAestheticController.syncWorldCloudColor(
            clone.getVSChartInfo(), event.getDropTarget());
         CSSChartStyles.apply(clone.getChartDescriptor(), clone.getVSChartInfo(),
                              null, clone.getCssParentParameters());
      }

      createDndCommands(rvs, assembly, dispatcher, null, linkUri);
   }

   private final VSChartBindingHandler chartHandler;
   private final VSChartDataHandler dataHandler;
   private static final Logger LOG = LoggerFactory.getLogger(VSChartDndController.class);
}
