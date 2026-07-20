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
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.ViewsheetInfo;
import inetsoft.uql.viewsheet.internal.ClickableInputVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.ClickableOutputVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.web.wiz.pairing.PairingException;
import inetsoft.web.wiz.script.model.ScriptInfo;
import inetsoft.web.wiz.script.model.ScriptTargetInfo;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads script text/enabled-state from a joined viewsheet's onInit/onLoad, per-assembly
 * script, and onClick locations (see {@link ScriptTarget}).
 */
@Service
public class ScriptReadService {

   /** Enumerates every scriptable target on the viewsheet with its has-script/enabled state. */
   public List<ScriptTargetInfo> list(RuntimeViewsheet rvs) {
      List<ScriptTargetInfo> targets = new ArrayList<>();
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         return targets;
      }

      ViewsheetInfo vsInfo = vs.getViewsheetInfo();
      boolean vsScriptEnabled = vsInfo.isScriptEnabled();
      targets.add(new ScriptTargetInfo("vs-init", !isBlank(vsInfo.getOnInit()), vsScriptEnabled));
      targets.add(new ScriptTargetInfo("vs-load", !isBlank(vsInfo.getOnLoad()), vsScriptEnabled));

      for(Assembly a : vs.getAssemblies()) {
         if(!(a instanceof VSAssembly vsAssembly)) {
            continue;
         }

         VSAssemblyInfo info = vsAssembly.getVSAssemblyInfo();
         targets.add(new ScriptTargetInfo(
            "assembly:" + a.getName(), !isBlank(info.getScript()), info.isScriptEnabled()));

         if(supportsOnClick(info)) {
            targets.add(new ScriptTargetInfo(
               "assembly:" + a.getName() + ":onClick", !isBlank(getOnClick(info)),
               info.isScriptEnabled()));
         }
      }

      return targets;
   }

   /** Reads the current text + enabled-state for {@code target}. */
   public ScriptInfo read(RuntimeViewsheet rvs, ScriptTarget target) throws PairingException {
      String text;
      boolean enabled;

      switch(target.location()) {
         case VS_INIT -> {
            ViewsheetInfo vsInfo = requireViewsheetInfo(rvs);
            text = vsInfo.getOnInit();
            enabled = vsInfo.isScriptEnabled();
         }
         case VS_LOAD -> {
            ViewsheetInfo vsInfo = requireViewsheetInfo(rvs);
            text = vsInfo.getOnLoad();
            enabled = vsInfo.isScriptEnabled();
         }
         case ASSEMBLY -> {
            VSAssemblyInfo info = requireAssemblyInfo(rvs, target.assemblyName());
            text = info.getScript();
            enabled = info.isScriptEnabled();
         }
         case ASSEMBLY_ONCLICK -> {
            VSAssemblyInfo info = requireAssemblyInfo(rvs, target.assemblyName());

            if(!supportsOnClick(info)) {
               throw new PairingException("Assembly does not support onClick: " + target.assemblyName());
            }

            text = getOnClick(info);
            enabled = info.isScriptEnabled();
         }
         default -> throw new PairingException("Unsupported target: " + target);
      }

      return new ScriptInfo(target.toString(), text == null ? "" : text, enabled);
   }

   ViewsheetInfo requireViewsheetInfo(RuntimeViewsheet rvs) throws PairingException {
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         throw new PairingException("Viewsheet not found in runtime");
      }

      return vs.getViewsheetInfo();
   }

   VSAssemblyInfo requireAssemblyInfo(RuntimeViewsheet rvs, String name) throws PairingException {
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         throw new PairingException("Viewsheet not found in runtime");
      }

      return requireAssemblyInfo(vs, name);
   }

   static VSAssemblyInfo requireAssemblyInfo(Viewsheet vs, String name) throws PairingException {
      Assembly a = vs.getAssembly(name);

      if(!(a instanceof VSAssembly vsAssembly)) {
         throw new PairingException("Assembly not found: " + name);
      }

      return vsAssembly.getVSAssemblyInfo();
   }

   static boolean supportsOnClick(VSAssemblyInfo info) {
      return info instanceof ClickableOutputVSAssemblyInfo || info instanceof ClickableInputVSAssemblyInfo;
   }

   static String getOnClick(VSAssemblyInfo info) {
      if(info instanceof ClickableOutputVSAssemblyInfo c) {
         return c.getOnClick();
      }

      if(info instanceof ClickableInputVSAssemblyInfo c) {
         return c.getOnClick();
      }

      return null;
   }

   static void setOnClick(VSAssemblyInfo info, String text) throws PairingException {
      if(info instanceof ClickableOutputVSAssemblyInfo c) {
         c.setOnClick(text);
      }
      else if(info instanceof ClickableInputVSAssemblyInfo c) {
         c.setOnClick(text);
      }
      else {
         throw new PairingException("Assembly does not support onClick");
      }
   }

   private static boolean isBlank(String s) {
      return s == null || s.isEmpty();
   }
}
