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
import inetsoft.report.composition.graph.VGraphPair;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.script.ScriptException;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

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
 * <p>The Composer's own render between two writes uses the re-armed failure up again, so each
 * chart that fails is remembered per sandbox; a later build that returns a finished pair with no
 * graph for such a chart is rebuilt once to recapture the real exception. A chart never seen
 * failing here is not rebuilt (that state is also plain empty data). A chart already consumed by a
 * render before its first check, with no failure recorded, cannot be told from an empty chart and
 * is not reported until a data-changing write makes it throw again.
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

   /** One step of a chart write that may throw. */
   @FunctionalInterface
   public interface Body {
      void run() throws Exception;
   }

   /**
    * Wraps a chart write so a chart script error can never escape it as an opaque 500, then runs
    * {@link #warnIfScriptFails} after it.
    *
    * <p>The Composer services a chart write calls ({@code editObjectProperty}, the legend
    * visibility service, ...) read the chart's data themselves, after they have stored the edit
    * but before they refresh. When the chart's one-shot script failure is armed (right after a
    * condition write, or after this check re-armed it) that read throws the
    * {@code ScriptException}, which used to leave the write as an unexpected server error although
    * the edit was kept. Here that exception is reported as the same named warning, and the chart
    * is re-armed so the native render still shows its own error. Any other exception propagates
    * unchanged.
    */
   public static void guard(RuntimeViewsheet rvs, String chartName, CommandDispatcher dispatcher,
                            Body body) throws Exception
   {
      try {
         body.run();
      }
      catch(Exception e) {
         ScriptException script = scriptCause(e);

         if(script == null) {
            throw e;
         }

         ViewsheetSandbox box = rvs == null ? null : rvs.getViewsheetSandbox().orElse(null);

         if(box != null) {
            remember(box, chartName);
            rearm(box, chartName);
         }

         warn(chartName, script.getMessage(), dispatcher);
         return;
      }

      warnIfScriptFails(rvs, chartName, dispatcher);
   }

   /** {@link #guard} for a whole mutation, so a service needs no check of its own at the end. */
   public static ViewsheetSessionService.Mutation guarded(
      String chartName, ViewsheetSessionService.Mutation mutation)
   {
      return (rvs, runtimeId, dispatcher) ->
         guard(rvs, chartName, dispatcher, () -> mutation.run(rvs, runtimeId, dispatcher));
   }

   private static ScriptException scriptCause(Throwable e) {
      for(int depth = 0; e != null && depth < 10; depth++, e = e.getCause()) {
         if(e instanceof ScriptException script) {
            return script;
         }
      }

      return null;
   }

   private static void warn(String chartName, String failure, CommandDispatcher dispatcher) {
      MessageCommand command = new MessageCommand();
      command.setType(MessageCommand.Type.WARNING);
      command.setMessage(prefix(chartName) + failure +
                         "\nThe edit was applied, but the chart will show no data until the " +
                         "script is fixed.");

      if(dispatcher != null) {
         dispatcher.sendCommand(chartName, command);
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
      String failure = scriptFailure(rvs, chartName);

      if(failure == null) {
         return;
      }

      warn(chartName, failure, dispatcher);
   }

   /**
    * The script error text when {@code chartName} cannot be built right now, else null. Leaves the
    * chart re-armed after a failure (see the class comment), so this can be asked of a chart
    * without costing the Composer's own render its error.
    */
   public static String scriptFailure(RuntimeViewsheet rvs, String chartName) {
      ViewsheetSandbox box = rvs == null ? null : rvs.getViewsheetSandbox().orElse(null);

      if(box == null || chartName == null) {
         return null;
      }

      Build build = build(box, chartName);

      // A render between two writes (the Composer's chart-areas request after every broadcast)
      // uses up the re-armed failure, leaving a completed pair with no graph and no exception,
      // which looks exactly like a chart with no data. Only a chart this check already saw fail
      // is rebuilt for it, so a chart that is merely empty never pays for a second build.
      if(build.failure == null && build.consumedFailure() && hasFailed(box, chartName)) {
         rearm(box, chartName);
         build = build(box, chartName);
      }

      if(build.failure == null) {
         // A build that no longer throws means the earlier failure is not current any more.
         forget(box, chartName);
         return null;
      }

      remember(box, chartName);
      rearm(box, chartName);
      return build.failure.getMessage();
   }

   /** The refusal text for a read or write that needs the chart's rendered areas. */
   public static String unavailableMessage(String chartName, String failure, String what) {
      return prefix(chartName) + failure + "; " + what + " are unavailable until the script is fixed.";
   }

   /** Forces the next consumer of the chart to rebuild, and so to see the failure again. */
   private static void rearm(ViewsheetSandbox box, String chartName) {
      box.clearGraph(chartName);
      box.resetDataMap(chartName);
   }

   private static Build build(ViewsheetSandbox box, String chartName) {
      box.lockRead();

      try {
         return new Build(box.getVGraphPair(chartName, true), null);
      }
      catch(Exception e) {
         Throwable cause = e;

         while(cause instanceof RuntimeException && cause.getCause() != null) {
            cause = cause.getCause();
         }

         if(cause instanceof ScriptException script) {
            return new Build(null, script);
         }

         LOG.debug("Chart '{}' could not be built after a write: {}", chartName, e.toString());
         return new Build(null, null);
      }
      finally {
         box.unlockRead();
      }
   }

   /** What one build produced: a pair (maybe without a graph), or a script failure, or neither. */
   private record Build(VGraphPair pair, ScriptException failure) {
      /** A finished pair that has no graph, did not fail and was not cancelled. */
      boolean consumedFailure() {
         return pair != null && pair.isCompleted() && !pair.isFailed() && !pair.isCancelled() &&
            pair.getRealSizeVGraph() == null;
      }
   }

   static boolean hasFailed(ViewsheetSandbox box, String chartName) {
      synchronized(FAILED) {
         Set<String> names = FAILED.get(box);
         return names != null && names.contains(chartName);
      }
   }

   private static void remember(ViewsheetSandbox box, String chartName) {
      synchronized(FAILED) {
         FAILED.computeIfAbsent(box, k -> new HashSet<>()).add(chartName);
      }
   }

   private static void forget(ViewsheetSandbox box, String chartName) {
      synchronized(FAILED) {
         Set<String> names = FAILED.get(box);

         if(names != null) {
            names.remove(chartName);
         }
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(ChartRenderCheck.class);

   /** Charts whose last build threw a script error, per sandbox, for as long as it lives. */
   private static final Map<ViewsheetSandbox, Set<String>> FAILED = new WeakHashMap<>();
}
