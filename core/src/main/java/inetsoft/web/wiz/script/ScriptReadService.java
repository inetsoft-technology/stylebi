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
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.wiz.script.model.ScriptInfo;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads script locations from a live {@link RuntimeViewsheet}.
 *
 * <p>Enumerates every scriptable location (viewsheet init/load, per-assembly
 * script, and per-assembly onClick handlers) and can read the current text for
 * any one of them via {@link #read(RuntimeViewsheet, ScriptTarget)}.</p>
 */
@Service
public class ScriptReadService {

   /**
    * List every script location in the viewsheet together with its current text
    * and enabled state.
    *
    * @param rvs the live runtime viewsheet; must not be {@code null}
    * @return all script locations with their current state
    */
   public List<ScriptInfo> list(RuntimeViewsheet rvs) {
      Viewsheet vs = rvs.getViewsheet();
      List<ScriptInfo> result = new ArrayList<>();

      // viewsheet-level scripts (always present; enabled is implicitly true)
      ViewsheetInfo vsi = vs.getViewsheetInfo();
      result.add(new ScriptInfo("vs-init", vsi.getOnInit(), true));
      result.add(new ScriptInfo("vs-load", vsi.getOnLoad(), true));

      // per-assembly scripts
      Assembly[] assemblies = vs.getAssemblies();

      for(Assembly a : assemblies) {
         if(!(a instanceof VSAssembly vsa)) {
            continue;
         }

         VSAssemblyInfo info = vsa.getVSAssemblyInfo();

         if(info == null) {
            continue;
         }

         result.add(new ScriptInfo(
            "assembly:" + vsa.getName(),
            info.getScript(),
            info.isScriptEnabled()
         ));

         // onClick handler (only for clickable assembly types)
         String onClick = extractOnClick(info);

         if(onClick != null) {
            result.add(new ScriptInfo(
               "assembly:" + vsa.getName() + ":onClick",
               onClick,
               true
            ));
         }
      }

      return result;
   }

   /**
    * Read the current script text for a single location.
    *
    * @param rvs    the live runtime viewsheet; must not be {@code null}
    * @param target the location to read
    * @return a {@link ScriptInfo} carrying the current text and enabled state
    * @throws IllegalArgumentException if the assembly named by {@code target}
    *                                  does not exist in the viewsheet
    */
   public ScriptInfo read(RuntimeViewsheet rvs, ScriptTarget target) {
      Viewsheet vs = rvs.getViewsheet();

      return switch(target.location()) {
         case VS_INIT -> {
            ViewsheetInfo vsi = vs.getViewsheetInfo();
            yield new ScriptInfo("vs-init", vsi.getOnInit(), true);
         }
         case VS_LOAD -> {
            ViewsheetInfo vsi = vs.getViewsheetInfo();
            yield new ScriptInfo("vs-load", vsi.getOnLoad(), true);
         }
         case ASSEMBLY -> {
            VSAssembly vsa = requireAssembly(vs, target.assemblyName());
            VSAssemblyInfo info = vsa.getVSAssemblyInfo();
            yield new ScriptInfo(target.toString(), info.getScript(), info.isScriptEnabled());
         }
         case ASSEMBLY_ONCLICK -> {
            VSAssembly vsa = requireAssembly(vs, target.assemblyName());
            VSAssemblyInfo info = vsa.getVSAssemblyInfo();
            yield new ScriptInfo(target.toString(), extractOnClick(info), true);
         }
      };
   }

   // -------------------------------------------------------------------------
   // Helpers
   // -------------------------------------------------------------------------

   private VSAssembly requireAssembly(Viewsheet vs, String name) {
      Assembly a = vs.getAssembly(name);

      if(!(a instanceof VSAssembly vsa)) {
         throw new IllegalArgumentException("Assembly not found in viewsheet: " + name);
      }

      return vsa;
   }

   /**
    * Returns the onClick text if the assembly info type supports it; {@code null} otherwise.
    * This distinguishes clickable assemblies (Text, Image, Submit, TextInput) from
    * non-clickable ones (Chart, Gauge, etc.) without hard-coding a class list.
    */
   private String extractOnClick(VSAssemblyInfo info) {
      if(info instanceof ClickableOutputVSAssemblyInfo co) {
         return co.getOnClick();
      }

      if(info instanceof ClickableInputVSAssemblyInfo ci) {
         return ci.getOnClick();
      }

      return null;
   }
}
