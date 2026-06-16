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
import org.springframework.stereotype.Service;

/**
 * Writes scripts to a live {@link RuntimeViewsheet}.
 *
 * <p>Methods mutate the in-memory viewsheet model directly. Callers are responsible
 * for broadcasting any required UI refresh to the browser after the mutation.</p>
 */
@Service
public class ScriptWriteService {

   /**
    * Set (or clear) the script text at the given target location.
    *
    * @param rvs    the live runtime viewsheet; must not be {@code null}
    * @param target the script location to update
    * @param text   the new script text; {@code null} or empty clears the script
    * @throws IllegalArgumentException if the assembly named by {@code target}
    *                                  does not exist or does not support onClick
    */
   public void write(RuntimeViewsheet rvs, ScriptTarget target, String text) {
      Viewsheet vs = rvs.getViewsheet();

      switch(target.location()) {
         case VS_INIT -> vs.getViewsheetInfo().setOnInit(text != null ? text : "");
         case VS_LOAD -> vs.getViewsheetInfo().setOnLoad(text != null ? text : "");
         case ASSEMBLY -> {
            VSAssemblyInfo info = requireAssemblyInfo(vs, target.assemblyName());
            info.setScript(text != null ? text : "");
         }
         case ASSEMBLY_ONCLICK -> {
            VSAssemblyInfo info = requireAssemblyInfo(vs, target.assemblyName());
            setOnClick(info, target.assemblyName(), text != null ? text : "");
         }
      }
   }

   /**
    * Enable or disable the script for a per-assembly script target.
    * Only applicable to {@link ScriptLocation#ASSEMBLY} targets; viewsheet-level
    * and onClick scripts do not have a separate enabled flag.
    *
    * @param rvs     the live runtime viewsheet; must not be {@code null}
    * @param target  the assembly script location to toggle
    * @param enabled {@code true} to enable the script, {@code false} to disable
    * @throws IllegalArgumentException if {@code target} is not an
    *                                  {@link ScriptLocation#ASSEMBLY} target or
    *                                  the assembly does not exist
    */
   public void setEnabled(RuntimeViewsheet rvs, ScriptTarget target, boolean enabled) {
      if(target.location() != ScriptLocation.ASSEMBLY) {
         throw new IllegalArgumentException(
            "setEnabled is only supported for assembly scripts; target was: " + target);
      }

      Viewsheet vs = rvs.getViewsheet();
      VSAssemblyInfo info = requireAssemblyInfo(vs, target.assemblyName());
      info.setScriptEnabled(enabled);
   }

   // -------------------------------------------------------------------------
   // Helpers
   // -------------------------------------------------------------------------

   private VSAssemblyInfo requireAssemblyInfo(Viewsheet vs, String name) {
      Assembly a = vs.getAssembly(name);

      if(!(a instanceof VSAssembly vsa)) {
         throw new IllegalArgumentException("Assembly not found in viewsheet: " + name);
      }

      VSAssemblyInfo info = vsa.getVSAssemblyInfo();

      if(info == null) {
         throw new IllegalArgumentException("Assembly has no info object: " + name);
      }

      return info;
   }

   private void setOnClick(VSAssemblyInfo info, String assemblyName, String text) {
      if(info instanceof ClickableOutputVSAssemblyInfo co) {
         co.setOnClick(text);
      }
      else if(info instanceof ClickableInputVSAssemblyInfo ci) {
         ci.setOnClick(text);
      }
      else {
         throw new IllegalArgumentException(
            "Assembly does not support onClick: " + assemblyName);
      }
   }
}
