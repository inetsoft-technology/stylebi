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

/**
 * The resolved modern-visualization state a value resolver should answer against. Immutable, and
 * built in one of four ways so that the SreeEnv reads live here rather than in every resolver.
 *
 * During the forward half every caller passes ofGate(), which reads exactly what the resolvers used
 * to read themselves - so threading it changes nothing. of(VSAssemblyInfo) and of(VizMark) exist for
 * the phase that makes reads follow the assembly's mark; nothing calls them yet.
 *
 * Never null: callers must pass one of the factories' results, never a null reference.
 */
public final class VizContext {
   private static final String DENSE = "dense";
   /**
    * Legacy on every axis. For report charts, which have no viewsheet and no mark. No factory may
    * return this instance for a viewsheet chart: descriptor font lines compare identity against it
    * to mean "is a viewsheet chart."
    */
   public static final VizContext LEGACY = new VizContext(false, false, DENSE);

   private VizContext(boolean modern, boolean dark, String density) {
      this.modern = modern;
      this.dark = dark;
      this.density = density;
   }

   /**
    * The org gate as it stands. Equivalent to the predicates each resolver used to evaluate itself.
    */
   public static VizContext ofGate() {
      boolean modern = VSDensityDefaults.isModern();
      return new VizContext(modern, VSDensityDefaults.isDark(modern), VSDensityDefaults.mode());
   }

   /**
    * The context an assembly's own provenance implies. Null, or an unmarked assembly, reads legacy.
    */
   public static VizContext of(VSAssemblyInfo info) {
      return of(info == null ? null : info.getVizMark());
   }

   /**
    * The context a mark implies, gated: modern only while the org gate is also on, so turning the
    * gate off restores legacy read-time chrome even on a marked assembly. Density still comes from
    * the org: the mark decides whether an assembly honours density, not which density is in force.
    */
   public static VizContext of(VizMark mark) {
      boolean modern = VSDensityDefaults.isModern() && mark != null;
      return new VizContext(modern, modern && mark == VizMark.MODERN_DARK, VSDensityDefaults.mode());
   }

   /** Whether modern chrome applies. */
   public final boolean modern;
   /** Whether the dark palette applies. Never true without modern. */
   public final boolean dark;
   /** The active density mode: dense, compact or comfortable. Meaningful only when modern. */
   public final String density;
}
