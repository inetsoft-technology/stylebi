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

import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.script.viewsheet.ViewsheetScope;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.binding.VSScriptableService;
import inetsoft.web.wiz.pairing.PairingException;
import inetsoft.web.wiz.script.model.AssemblyContext;
import inetsoft.web.wiz.script.model.ScriptContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Live introspection of a joined viewsheet's scriptable surface ("Layer B" from the original
 * script-plugin design).
 *
 * <p>Reuses {@code VSScriptableService.getScriptDefinition} — the SAME live function/property
 * tree the Composer's own script editor autocomplete uses. Earlier this was avoided over a
 * concern that re-resolving the runtime via {@code ViewsheetEngine.getViewsheet(vsId, principal)}
 * would fail the session {@code matches()} check our pairing bypass exists to skip. That concern
 * was CONFIRMED correct empirically (an {@code InvalidUserException} — composer sessions use a
 * per-connection {@code Client[...]} principal that never equals the agent's rebuilt identity),
 * so {@code VSScriptableService} now has {@code RuntimeViewsheet}-accepting overloads that skip
 * the re-resolution entirely, callable here because {@code rvs} is already independently
 * authorized via the pairing grant.
 *
 * <p>The raw tree from {@code getScriptDefinition} is enormous (the entire global Excel/JS
 * function library plus every assembly, ~15k+ lines) because the editor lazily renders slices of
 * it as the user navigates; wholesale is unusable for an agent. We extract just
 * {@code root.get(assemblyName)} — the nested, assembly-scoped node StyleBI's own code builds
 * before merging it into the noisy root (see {@code getScriptDefinition}'s "copy assembly
 * properties... to top-level scope" comment) — cutting ~15k lines to ~1k. {@code getColumnTree}
 * was tried too but its per-assembly subtree is entirely redundant with this (same function
 * names, bulkier format) with no information {@code Chart1.table} doesn't already give directly,
 * so it's dropped rather than returned.</p>
 */
@Service
public class ScriptContextService {
   private static final Logger LOG = LoggerFactory.getLogger(ScriptContextService.class);

   // Fallback only — used when the live VSScriptableService call fails for an assembly.
   // Confirmed against ChartVSAScriptable's own addProperty(...) calls at the time this was
   // written; the live apiTree above is authoritative and will include more (e.g. addTarget*
   // functions this list doesn't).
   private static final List<String> CHART_SCRIPTABLE_MEMBERS = List.of(
      "chartStyle", "singleStyle", "xFields", "yFields", "colorField", "shapeField",
      "sizeField", "textField", "geoFields", "xAxis", "yAxis", "y2Axis", "axis",
      "bindingInfo", "title", "titleVisible", "mapType", "drillEnabled", "dataConditions",
      "highlighted", "graph", "dataset", "query", "table", "data"
   );

   private static final List<String> CONTEXT_VARS = List.of(
      "thisViewsheet", "parameter", "_USER_", "_ROLES_", "_GROUPS_", "event"
   );

   @Autowired
   public ScriptContextService(VSScriptableService scriptableService) {
      this.scriptableService = scriptableService;
   }

   /**
    * The scriptable surface for one target.
    *
    * <p>Scoped because an unscoped dump is both enormous and the reason this tool got reached for
    * during non-script work: the full apiTree for every assembly is a viewsheet-wide inventory,
    * which is the pull the GUI-first rule exists to resist. Without a target the assembly LIST
    * still comes back — that is what makes a target choosable — but without the trees.
    *
    * @param target null for the unscoped listing; a viewsheet-level kind keeps every tree, because
    *               an onInit/onLoad script coordinates across assemblies by definition.
    */
   public ScriptContext context(RuntimeViewsheet rvs, ScriptTarget target) throws PairingException {
      List<AssemblyContext> all = describeAssemblies(rvs);

      if(target == null) {
         // No kind stated, so no basis for narrowing the vars: report the union rather than
         // guessing a context the caller has not chosen.
         return new ScriptContext(all.stream().map(ScriptContextService::withoutApiTree).toList(),
                                  CONTEXT_VARS);
      }

      List<String> vars = contextVars(target.kind());

      if(target.assemblyName() == null) {
         return new ScriptContext(all, vars);
      }

      List<AssemblyContext> one = all.stream()
         .filter(a -> target.assemblyName().equals(a.name()))
         .toList();

      if(one.isEmpty()) {
         throw new PairingException("Assembly not found: " + target.assemblyName());
      }

      return new ScriptContext(one, vars);
   }

   /**
    * The variables actually in scope for this kind.
    *
    * <p>The API differs by context, so reporting one flat list makes the tool wrong for every kind
    * but one. {@code event} resolves from {@link ViewsheetScope}'s vmap
    * ({@code ViewsheetScope.java:368}) and is populated only while an onClick is executing —
    * offering it to an onInit script promises a variable that is always null there.
    *
    * <p>This is the seam Tier 2 kinds extend: a {@code calcField} runs per row with
    * {@code field['x']} and no assembly API at all, so it will add a case here rather than a
    * special path elsewhere.
    */
   private static List<String> contextVars(ScriptTarget.Kind kind) {
      return switch(kind) {
         case ASSEMBLY_ON_CLICK -> CONTEXT_VARS;
         default -> CONTEXT_VARS.stream().filter(v -> !"event".equals(v)).toList();
      };
   }

   private static AssemblyContext withoutApiTree(AssemblyContext a) {
      return new AssemblyContext(a.name(), a.type(), a.scriptable(), a.scriptableMembers(), null);
   }

   /**
    * Builds the per-assembly scriptable surface via the live {@code VSScriptableService} lookup,
    * falling back to the curated member list for charts when the live lookup fails.
    */
   List<AssemblyContext> describeAssemblies(RuntimeViewsheet rvs) {
      List<AssemblyContext> assemblies = new ArrayList<>();
      Viewsheet vs = rvs.getViewsheet();

      if(vs != null) {
         for(Assembly a : vs.getAssemblies()) {
            if(!(a instanceof VSAssembly vsAssembly)) {
               continue;
            }

            String type = a.getClass().getSimpleName();
            boolean isChart = vsAssembly instanceof ChartVSAssembly;
            Object apiTree = null;

            try {
               ObjectNode root = scriptableService.getScriptDefinition(rvs, a.getName(), null, false);
               apiTree = root.has(a.getName()) ? root.get(a.getName()) : root;
            }
            catch(Exception e) {
               LOG.warn("Failed to load live script definition for assembly {}; falling back " +
                        "to the curated member list", a.getName(), e);
            }

            assemblies.add(new AssemblyContext(a.getName(), type, true,
               isChart ? CHART_SCRIPTABLE_MEMBERS : List.of(), apiTree));
         }
      }

      return assemblies;
   }

   private final VSScriptableService scriptableService;
}
