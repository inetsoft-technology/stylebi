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

package inetsoft.web.viewsheet.controller.chart;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.VGraphPair;
import inetsoft.report.composition.graph.VSDataSet;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.event.chart.VSChartEvent;
import inetsoft.web.viewsheet.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.Payload;

import java.security.Principal;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

/**
 * Abstract chart service base class to reduce boiler plate code necessary for
 * initializing chart state and executing the runtime viewsheet after certain changes
 *
 * @param <T> A subclass of VSChartEvent
 */
public abstract class VSChartControllerService<T extends VSChartEvent>  {
   protected VSChartControllerService(
      CoreLifecycleService coreLifecycleService,
      ViewsheetService viewsheetService,
      VSChartAreasServiceProxy vsChartAreasService)
   {
      this.coreLifecycleService = coreLifecycleService;
      this.viewsheetService = viewsheetService;
      this.vsChartAreasService = vsChartAreasService;
   }

   /**
    * Message Mapping endpoint. This method should supply a method to
    * {@link #processEvent} so the chart state can be injected and we can optionally
    * call complete after the event handling
    *
    * @param event      A chart events that extends VSChartEvent
    * @param principal  The injected principal object
    * @param dispatcher The injected command dispatcher
    */
   public abstract void eventHandler(@Payload T event,
                                     @LinkUri String linkUri,
                                     Principal principal,
                                     CommandDispatcher dispatcher) throws Exception;

   /**
    * Process an event that handles its own completion (usually by dispatching a command)
    *
    * @param event      A subclass of VSChartEvent
    * @param principal  The principal object
    * @param handler    A lambda function that takes a VSChartStateInfo and returns void
    */
   protected void processEvent(String runtimeId,
                               T event,
                               Principal principal,
                               Consumer<VSChartStateInfo> handler) throws Exception
   {
      VSChartStateInfo chartState = getChartState(runtimeId, event, viewsheetService, principal);

      if(chartState != null) {
         handler.accept(chartState);
      }
   }

   /**
    * Process an event that needs to be completed. Runs the given lambda injected with
    * the chart state, receives its completion hint, then executes the current runtime
    * viewsheet
    *
    * @param event      A subclass of VSChartEvent
    * @param principal  The principal object
    * @param dispatcher The command dispatcher
    * @param handler    A lambda function that takes a VSChartStateInfo and returns a
    *                   hint for completion
    */
   protected void processEvent(String runtimeId,
                               T event,
                               Principal principal,
                               String linkUri,
                               CommandDispatcher dispatcher,
                               ToIntFunction<VSChartStateInfo> handler) throws Exception
   {
      VSChartStateInfo chartState = getChartState(runtimeId, event, viewsheetService, principal);

      if(chartState != null) {
         int hint = handler.applyAsInt(chartState);
         complete(chartState, hint, linkUri, dispatcher, event, principal);
      }
   }

   /**
    * Calls the VSChartStateInfo factory method and returns the newly created state given
    * the event parameters and spring injected runtime ID
    */
   private VSChartStateInfo getChartState(
      String runtimeId, T event, ViewsheetService viewsheetService, Principal principal)
      throws Exception
   {

      return VSChartStateInfo.createChartState(event, viewsheetService, principal, runtimeId);
   }

   // from LayoutLegendEvent.process()
   protected void complete(VSChartStateInfo chartState, int hint, String linkUri,
                           CommandDispatcher dispatcher, T event, Principal principal)
   {
      final ChartVSAssembly assembly = chartState.getAssembly();

      if(hint < 0) {
         hint = assembly.setVSAssemblyInfo(chartState.getChartAssemblyInfo());
      }

      RuntimeViewsheet rvs = chartState.getRuntimeViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      box.lockRead();

      try {
         String name = event.getChartName();
         coreLifecycleService.execute(rvs, name, linkUri, hint, dispatcher);

         if((hint & VSAssembly.INPUT_DATA_CHANGED) == VSAssembly.INPUT_DATA_CHANGED) {
            final ChangedAssemblyList clist =
               coreLifecycleService.createList(true, dispatcher, rvs, linkUri);
            coreLifecycleService.refreshViewsheet(rvs, rvs.getID(), linkUri, event.getViewportWidth(),
                                                  event.getViewportHeight(), false, null, dispatcher, false, false, true, clist);
         }
      }
      catch(Exception e) {
         throw new RuntimeException(e);
      }
      finally {
         box.unlockRead();
      }
   }

   /**
    * Call coreLifecycleService.execute for now
    */
   protected void execute(RuntimeViewsheet rvs, String name, String uri, int hint,
                          CommandDispatcher dispatcher) throws Exception
   {
      coreLifecycleService.execute(rvs, name, uri, hint, dispatcher);
   }

   /**
    * Call coreLifecycleService.refreshVSAssembly for now
    */
   protected void refreshVSAssembly(RuntimeViewsheet rvs, String name,
                                    CommandDispatcher dispatcher) throws Exception
   {
      coreLifecycleService.refreshVSAssembly(rvs, name, dispatcher);
   }

   /**
    * Call coreLifecycleService.createList for now
    */
   protected ChangedAssemblyList createList(boolean breakable,
                                            CommandDispatcher dispatcher,
                                            RuntimeViewsheet rvs, String uri)
   {
      return coreLifecycleService.createList(breakable, dispatcher, rvs, uri);
   }

   /**
    *  Check if zoom/detail/brush should be supported.
    */
   protected boolean isSelectionActionSupported(VGraphPair pair, CommandDispatcher dispatcher) {
      if(pair != null && pair.isChangedByScript() && !(pair.getData() instanceof VSDataSet)) {
         MessageCommand cmd = new MessageCommand();
         cmd.setMessage(Catalog.getCatalog().getString("action.script.graph"));
         cmd.setType(MessageCommand.Type.INFO);
         dispatcher.sendCommand(cmd);

         return false;
      }

      return true;
   }

   protected ViewsheetService getViewsheetEngine() {
      return viewsheetService;
   }

   // reload assemblies (view only without re-execution).
   protected void reloadVSAssemblies(RuntimeViewsheet rvs, String priorAssembly, String uri,
                                     CommandDispatcher dispatcher, Principal principal,
                                     boolean refreshOthersData)
   {
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      box.lockRead();

      try {
         VSAssembly vsAssembly = rvs.getViewsheet().getAssembly(priorAssembly);

         if(vsAssembly != null) {
            reloadVSAssembly(vsAssembly, rvs, uri, dispatcher, true);

            if(vsAssembly instanceof ChartVSAssembly) {
               VSChartEvent event = new VSChartEvent();
               event.setChartName(priorAssembly);

               try {
                  vsChartAreasService.refreshChartAreasModel(rvs.getID(), event,
                                                             dispatcher, principal);
               }
               catch(Exception e) {
                  LOG.warn("Failed to refresh chart area: " + priorAssembly, e);
               }
            }
         }

         Arrays.stream(rvs.getViewsheet().getAssemblies())
            .filter(a -> a != null && !Tool.equals(a.getAbsoluteName(), priorAssembly))
            .forEach(a -> reloadVSAssembly((VSAssembly) a, rvs, uri, dispatcher, refreshOthersData));
      }
      finally {
         box.unlockRead();
      }
   }

   private void reloadVSAssembly(VSAssembly assembly, RuntimeViewsheet rvs, String uri,
                                 CommandDispatcher dispatcher, boolean refreshData)
   {
      try {
         coreLifecycleService.addDeleteVSObject(rvs, assembly, dispatcher);

         if(refreshData) {
            coreLifecycleService.loadTableLens(rvs, assembly.getAbsoluteName(), uri, dispatcher);
         }
      }
      catch(Exception e) {
         LOG.warn("Failed to refresh assembly: " + assembly.getAbsoluteName(), e);
      }
   }

   private final CoreLifecycleService coreLifecycleService;
   private final ViewsheetService viewsheetService;
   private final VSChartAreasServiceProxy vsChartAreasService;
   private static final Logger LOG = LoggerFactory.getLogger(VSChartControllerService.class);


   /**
    * Chart state object. This should be used by all event handlers to get the
    * state of the current chart. Built using an included static factory method.
    */
   public static final class VSChartStateInfo {
      private VSChartStateInfo(ViewsheetService engine,
                               RuntimeViewsheet rvs, Viewsheet vs,
                               ViewsheetSandbox box,
                               ChartVSAssembly chartAssembly,
                               ChartVSAssemblyInfo chartAssemblyInfo,
                               ChartDescriptor chartDescriptor,
                               VSChartInfo chartInfo,
                               LegendsDescriptor legendsDes)
      {
         this.engine = engine;
         this.rvs = rvs;
         this.vs = vs;
         this.box = box;
         this.chartAssembly = chartAssembly;
         this.chartAssemblyInfo = chartAssemblyInfo;
         this.chartDescriptor = chartDescriptor;
         this.chartInfo = chartInfo;
         this.legendsDes = legendsDes;
      }

      static VSChartStateInfo createChartState(VSChartEvent event, ViewsheetService engine,
                                               Principal principal, String runtimeId)
         throws Exception
      {
         return createChartState(event, engine, principal, runtimeId, false);
      }

      static VSChartStateInfo createChartState(VSChartEvent event, ViewsheetService engine,
                                               Principal principal, String runtimeId,
                                               boolean runtime)
         throws Exception
      {
         if(event == null || event.getChartName() == null) {
            return null;
         }

         String name = event.getChartName();

         RuntimeViewsheet rvs = engine.getViewsheet(runtimeId, principal);
         Viewsheet vs = rvs.getViewsheet();
         ViewsheetSandbox box = rvs.getViewsheetSandbox();

         if(vs == null || box == null) {
            return null;
         }

         box.lockRead();

         try {
            int index = name.lastIndexOf(".");
            String sname = name;

            if(index >= 0) {
               vs = (Viewsheet) vs.getAssembly(name.substring(0, index));
               sname = name.substring(index + 1);
            }

            if(vs == null) {
               return null;
            }

            ChartVSAssembly chartAssembly = (ChartVSAssembly) vs.getAssembly(sname);

            // could be refreshing when chart is deleted
            if(chartAssembly == null) {
               return null;
            }

            ChartVSAssemblyInfo chartAssemblyInfo = chartAssembly.getChartInfo();
            ChartDescriptor chartDescriptor = null;

            if(runtime) {
               chartDescriptor = chartAssemblyInfo.getRTChartDescriptor();
            }

            if(chartDescriptor == null) {
               chartDescriptor = chartAssemblyInfo.getChartDescriptor();
            }

            VSChartInfo chartInfo = chartAssemblyInfo.getVSChartInfo();

            LegendsDescriptor legendsDes = chartDescriptor.getLegendsDescriptor();

            return new VSChartStateInfo(engine, rvs, vs, box,
                                        chartAssembly, chartAssemblyInfo,
                                        chartDescriptor, chartInfo, legendsDes);
         }
         finally {
            box.unlockRead();
         }
      }

      public ViewsheetService getEngine() {
         return engine;
      }

      public RuntimeViewsheet getRuntimeViewsheet() {
         return rvs;
      }

      public Viewsheet getViewsheet() {
         return vs;
      }

      public ViewsheetSandbox getViewsheetSandbox() {
         return box;
      }

      public ChartVSAssembly getAssembly() {
         return chartAssembly;
      }

      public ChartVSAssemblyInfo getChartAssemblyInfo() {
         return chartAssemblyInfo;
      }

      public ChartDescriptor getChartDescriptor() {
         return chartDescriptor;
      }

      public VSChartInfo getChartInfo() {
         return chartInfo;
      }

      public LegendsDescriptor getLegendsDes() {
         return legendsDes;
      }

      private ViewsheetService engine;
      private RuntimeViewsheet rvs;
      private Viewsheet vs;
      private ViewsheetSandbox box;
      private ChartVSAssembly chartAssembly;
      private ChartVSAssemblyInfo chartAssemblyInfo;
      private ChartDescriptor chartDescriptor;
      private VSChartInfo chartInfo;
      private LegendsDescriptor legendsDes;
   }
}
