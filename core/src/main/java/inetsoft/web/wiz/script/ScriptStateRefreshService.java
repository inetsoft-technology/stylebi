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
package inetsoft.web.wiz.script;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.SelectionListVSAssembly;
import inetsoft.uql.viewsheet.SelectionTreeVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.viewsheet.controller.VSRefreshService;
import inetsoft.web.viewsheet.event.VSRefreshEvent;
import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import inetsoft.web.wiz.script.model.ScriptExecResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

/**
 * Makes the runtime state a wiz script run wrote visible to everything that depends on it.
 *
 * <p>A script that assigns {@code Selection.selectedObjects} in a design-mode runtime only stores
 * a deferred value on the assembly (a "pin"); nothing turns it into the selection list until the
 * list is read, and nothing recomputes the assemblies that depend on the selection. The same
 * holds for other runtime-only effects the viewsheet XML does not carry. After every wiz script
 * run, whatever its target ({@code assemblyOnClick}, {@code assemblyMain}, {@code viewsheetOnInit}
 * or {@code viewsheetOnLoad}), this service therefore resolves each selection pin and performs
 * the same whole-sheet refresh as {@code edit(op:"refresh_viewsheet")}, then clears the pins so a
 * later click in the browser is not reverted by a stale pin.</p>
 *
 * <p>Both {@code execute} and {@code execute-live} are refreshed: neither runs against a clone,
 * so both have already changed the live runtime. The refresh re-runs the viewsheet's onLoad
 * script, so an onLoad that has side effects of its own runs again after the script.</p>
 *
 * <p>The refresh adds no undo checkpoint, so a run that only wrote selection state still adds
 * none (Bug #79051). It runs on a capturing dispatcher, which transmits nothing; the controller's
 * own broadcast after the run is what repaints the browser, so the browser is sent one refresh.</p>
 */
@Service
public class ScriptStateRefreshService {
   @Autowired
   public ScriptStateRefreshService(VSRefreshService refreshService) {
      this.refreshService = refreshService;
   }

   /** A script run whose result is reported by the caller. */
   @FunctionalInterface
   public interface ScriptRun {
      ScriptExecResult run() throws Exception;
   }

   /**
    * Runs {@code run}, then refreshes the runtime unless the run was refused for confirmation
    * (nothing executed). The refresh also happens when the run threw part-way, because the
    * writes before the failure stay applied; a script failure is still reported as before, and
    * a refresh failure never masks it.
    *
    * @param linkUri the link URI the refresh renders against; empty when the caller has none.
    */
   public ScriptExecResult runAndRefresh(RuntimeViewsheet rvs, Principal user, String linkUri,
                                         ScriptRun run) throws Exception
   {
      ScriptExecResult result;

      try {
         result = run.run();
      }
      catch(Exception ex) {
         try {
            refresh(rvs, user, linkUri);
         }
         catch(Exception refreshFailure) {
            ex.addSuppressed(refreshFailure);
         }

         throw ex;
      }

      if(result == null || result.requiresConfirmation()) {
         return result;
      }

      try {
         refresh(rvs, user, linkUri);
      }
      catch(Exception ex) {
         // an erroring script reports its own error; only a clean run has nothing else to say
         if(result.ok()) {
            throw ex;
         }

         LOG.warn("Could not refresh the viewsheet after a failed script run", ex);
      }

      return result;
   }

   private void refresh(RuntimeViewsheet rvs, Principal user, String linkUri) throws Exception {
      List<Assembly> pinned = pinnedSelections(rvs.getViewsheet());

      // Resolve each pin into the assembly's selection list before anything reads it.
      for(Assembly assembly : pinned) {
         if(assembly instanceof SelectionListVSAssembly list) {
            list.getSelectedObjects();
         }
         else if(assembly instanceof SelectionTreeVSAssembly tree) {
            tree.getSelectedObjects();
         }
      }

      try {
         CapturingCommandDispatcher.withCapturingDispatcher(user, dispatcher -> {
            refreshService.refreshViewsheet(
               rvs.getID(), VSRefreshEvent.builder().confirmed(false).build(), user, dispatcher,
               linkUri);
            return null;
         });
      }
      finally {
         for(Assembly assembly : pinned) {
            if(assembly instanceof SelectionListVSAssembly list) {
               list.setScriptSelectedValues(null);
            }
            else if(assembly instanceof SelectionTreeVSAssembly tree) {
               tree.setScriptSelectedValues(null);
            }
         }
      }
   }

   /** The selection list and tree assemblies that hold a script-written, unresolved value. */
   private static List<Assembly> pinnedSelections(Viewsheet vs) {
      List<Assembly> pinned = new ArrayList<>();

      if(vs == null) {
         return pinned;
      }

      for(Assembly assembly : vs.getAssemblies(true)) {
         if(assembly instanceof SelectionListVSAssembly list &&
            list.getScriptSelectedValues() != null ||
            assembly instanceof SelectionTreeVSAssembly tree &&
            tree.getScriptSelectedValues() != null)
         {
            pinned.add(assembly);
         }
      }

      return pinned;
   }

   private final VSRefreshService refreshService;
   private static final Logger LOG = LoggerFactory.getLogger(ScriptStateRefreshService.class);
}
