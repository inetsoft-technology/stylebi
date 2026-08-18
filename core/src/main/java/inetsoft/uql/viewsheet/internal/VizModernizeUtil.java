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
package inetsoft.uql.viewsheet.internal;

import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;

import java.util.ArrayList;
import java.util.List;

/**
 * Modernize: give an existing dashboard's unmarked content the chrome a freshly created dashboard
 * would have. It lives in this package because seedChromeDefaults is protected.
 *
 * Unmarked content is never touched automatically - this is the one route in, and something has to
 * ask for it. Marked content is left exactly as it is, so a mixed dashboard stays mixed.
 */
public final class VizModernizeUtil {
   private VizModernizeUtil() {
   }

   /**
    * Whether this sheet holds anything Modernize would act on: the sheet's own info, or any
    * assembly of its own, carrying no mark.
    */
   public static boolean hasUnmarked(Viewsheet vs) {
      return !unmarked(vs).isEmpty();
   }

   /**
    * Stamp and seed every unmarked assembly, and the sheet itself. Returns how many were touched.
    *
    * A no-op returning 0 when the gate is off: VizMark.fromGate() has no mark to give, and
    * modernizing into a legacy gate would produce content that reverts the moment the gate turns
    * on. Callers still gate explicitly - this is a floor, not the policy.
    */
   public static int modernize(Viewsheet vs) {
      VizMark mark = VizMark.fromGate();

      if(mark == null) {
         return 0;
      }

      VizContext ctx = VizContext.of(mark);
      List<VSAssemblyInfo> targets = unmarked(vs);

      for(VSAssemblyInfo info : targets) {
         info.setVizMark(mark);
         info.seedChromeDefaults(ctx);
      }

      return targets.size();
   }

   /**
    * The sheet's own info, every unmarked assembly of its own, and every unmarked embedded-viewsheet
    * container of its own. Assemblies belonging to an embedded viewsheet are skipped: they are
    * another asset's content, and this sheet has no business writing to them.
    */
   private static List<VSAssemblyInfo> unmarked(Viewsheet vs) {
      List<VSAssemblyInfo> targets = new ArrayList<>();

      addIfUnmarked(targets, vs.getVSAssemblyInfo());

      for(Assembly assembly : vs.getAssemblies(true)) {
         if(!(assembly instanceof VSAssembly)) {
            continue;
         }

         VSAssemblyInfo info = ((VSAssembly) assembly).getVSAssemblyInfo();

         if(info != null && !info.isEmbedded()) {
            addIfUnmarked(targets, info);
         }
      }

      // getAssemblies(true) flattens embedded-viewsheet containers away: the collector recurses into
      // a Viewsheet-typed child and never adds it (Viewsheet.java:3246-3263). The containers are this
      // sheet's own content, so collect them from the direct children; their children belong to the
      // embedded asset and stay excluded by the isEmbedded() test above.
      for(Assembly assembly : vs.getAssemblies(false)) {
         if(assembly instanceof Viewsheet) {
            addIfUnmarked(targets, ((Viewsheet) assembly).getVSAssemblyInfo());
         }
      }

      return targets;
   }

   private static void addIfUnmarked(List<VSAssemblyInfo> targets, VSAssemblyInfo info) {
      if(info != null && info.getVizMark() == null) {
         targets.add(info);
      }
   }
}
