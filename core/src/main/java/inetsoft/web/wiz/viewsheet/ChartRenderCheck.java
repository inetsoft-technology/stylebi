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
package inetsoft.web.wiz.viewsheet;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.script.ScriptException;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Surfaces a chart whose data fetch throws a {@link ScriptException} (e.g. a pre-condition or
 * expression condition calling an undefined function) from a wiz chart write.
 *
 * <p>No wiz chart write builds the chart, so the failure appears only when something later
 * renders it, and the write reads as a clean success. The Composer discovers it in its own
 * chart-areas request ({@code VSChartAreasService}); this does the same build right after the
 * write and reports it as a {@code Type.WARNING} {@code MessageCommand} on the capturing
 * dispatcher, which {@code ViewsheetSessionService.mutate} returns to the caller.
 *
 * <p>A graph pair reports its failure once: the failed pair is dropped and the sandbox keeps a
 * completed pair with no graph, so the next consumer renders silently as "No data". After warning,
 * the chart is therefore re-armed so the Composer's own render that follows the write still throws
 * and shows its native error, and so a repeated write on a still-broken chart warns again.
 *
 * <p>Failures the engine swallows (a viewsheet calc field, a chart's own assembly script) leave
 * the chart rendering and are deliberately not reported here.
 */
public final class ChartRenderCheck {
   private ChartRenderCheck() {
   }

   /**
    * {@link #warnIfScriptFails} for a write that may or may not have touched a chart, such as the
    * condition endpoint.
    */
   public static void warnIfChartScriptFails(RuntimeViewsheet rvs, String name,
                                             CommandDispatcher dispatcher)
   {
      Viewsheet vs = rvs == null ? null : rvs.getViewsheet();

      if(vs != null && vs.getAssembly(name) instanceof ChartVSAssembly) {
         warnIfScriptFails(rvs, name, dispatcher);
      }
   }

   /** Whether {@code warning} is the one {@link #warnIfScriptFails} raises for the chart. */
   public static boolean isWarningFor(String warning, String chartName) {
      return warning != null && warning.startsWith(prefix(chartName));
   }

   private static String prefix(String chartName) {
      return "Chart '" + chartName + "' cannot be generated: ";
   }

   /**
    * Builds {@code chartName}'s graph pair and warns when that throws a script error. Never
    * throws: a failure of any other kind is left for the native render to report.
    */
   public static void warnIfScriptFails(RuntimeViewsheet rvs, String chartName,
                                        CommandDispatcher dispatcher)
   {
      ViewsheetSandbox box = rvs == null ? null : rvs.getViewsheetSandbox().orElse(null);

      if(box == null || chartName == null) {
         return;
      }

      ScriptException failure = null;

      box.lockRead();

      try {
         box.getVGraphPair(chartName, true);
      }
      catch(Throwable e) {
         Throwable cause = e;

         while(cause instanceof RuntimeException && cause.getCause() != null) {
            cause = cause.getCause();
         }

         if(cause instanceof ScriptException script) {
            failure = script;
         }
         else {
            LOG.debug("Chart '{}' could not be built after a write: {}", chartName, e.toString());
         }
      }
      finally {
         box.unlockRead();
      }

      if(failure == null) {
         return;
      }

      MessageCommand command = new MessageCommand();
      command.setType(MessageCommand.Type.WARNING);
      command.setMessage(prefix(chartName) + failure.getMessage() +
                         "\nThe edit was applied, but the chart will show no data until the " +
                         "script is fixed.");
      if(dispatcher != null) {
         dispatcher.sendCommand(chartName, command);
      }

      box.clearGraph(chartName);
      box.resetDataMap(chartName);
   }

   private static final Logger LOG = LoggerFactory.getLogger(ChartRenderCheck.class);
}
