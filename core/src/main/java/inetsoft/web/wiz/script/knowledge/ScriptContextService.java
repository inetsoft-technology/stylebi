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
package inetsoft.web.wiz.script.knowledge;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.asset.AbstractSheet;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.*;
import inetsoft.web.wiz.script.model.ScriptContext;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Produces a live {@link ScriptContext} for the joined viewsheet (Layer B of the
 * scripting knowledge architecture).
 *
 * <p>The context answers the agent's question "what exists in this viewsheet and what
 * can I reference in a script?" without requiring the agent to know the assembly
 * names or types in advance.</p>
 *
 * <p>Globals and context-variable names are the static set registered by
 * {@link inetsoft.report.script.viewsheet.ViewsheetScope}; they do not change between
 * viewsheets.</p>
 */
@Service
public class ScriptContextService {

   /**
    * Globally-available functions registered by {@code ViewsheetScope}.
    *
    * <p>Note: {@code saveWorksheet}, {@code runQuery}, {@code setCellValue}, and
    * {@code refreshData} have real side-effects and trigger the destructive-global
    * guardrail in
    * {@link inetsoft.web.wiz.script.ScriptExecuteService#runLive}.</p>
    */
   static final List<String> GLOBALS = List.of(
      "runQuery",
      "setCellValue",
      "saveWorksheet",
      "refreshData",
      "toList",
      "addImage",
      "createConnection",
      "appendRow",
      "isCancelled",
      "delayVisibility",
      "addVariable",
      "removeVariable",
      "addAction"
   );

   /** Predefined context variables always in scope during viewsheet script execution. */
   static final List<String> CONTEXT_VARS = List.of(
      "thisViewsheet",
      "parameter",
      "_USER_",
      "_ROLES_",
      "_GROUPS_",
      "__principal__",
      "event",
      "OLD",
      "CHANGED",
      "ADDED",
      "DELETED"
   );

   /**
    * Build the live scripting context for the given viewsheet session.
    *
    * @param rvs the joined runtime viewsheet; must not be {@code null}
    * @return a {@link ScriptContext} with this viewsheet's assemblies,
    *         the known globals, and the standard context variables
    */
   public ScriptContext context(RuntimeViewsheet rvs) {
      Viewsheet vs = rvs.getViewsheet();
      List<ScriptContext.AssemblyEntry> assemblies = new ArrayList<>();

      for(Assembly a : vs.getAssemblies()) {
         if(!(a instanceof VSAssembly vsa)) {
            continue;
         }

         String name = vsa.getName();
         String type = assemblyTypeName(vsa.getAssemblyType());
         String scriptableType = scriptableTypeName(vsa.getAssemblyType());
         assemblies.add(new ScriptContext.AssemblyEntry(name, type, scriptableType));
      }

      return new ScriptContext(assemblies, GLOBALS, CONTEXT_VARS);
   }

   // -------------------------------------------------------------------------
   // Assembly type → human-readable string
   // -------------------------------------------------------------------------

   private static String assemblyTypeName(int assemblyType) {
      return switch(assemblyType) {
         case AbstractSheet.CHART_ASSET -> "chart";
         case AbstractSheet.TABLE_VIEW_ASSET -> "table";
         case AbstractSheet.CROSSTAB_ASSET -> "crosstab";
         case AbstractSheet.TEXT_ASSET -> "text";
         case AbstractSheet.IMAGE_ASSET -> "image";
         case AbstractSheet.EMBEDDEDTABLE_VIEW_ASSET -> "embeddedTable";
         default -> "assembly";
      };
   }

   /**
    * Maps an assembly type to the Tern API tree key for that type's scriptable.
    * Returns {@code null} when no prototype API is indexed for this assembly type.
    *
    * <p>Currently the {@code js-functions.json} only contains
    * {@code "CALC"} as a named scriptable prototype. This method will be
    * extended as the metadata is regenerated against the modernized runtime.</p>
    */
   private static String scriptableTypeName(int assemblyType) {
      // CALC is the main formula/calculation type exposed in the Tern tree.
      // Assembly-specific scriptable prototypes (ChartVSAScriptable, etc.) are
      // not yet indexed in js-functions.json — this will be populated once the
      // metadata is regenerated against the modernized runtime (Phase 0).
      return null;
   }
}
