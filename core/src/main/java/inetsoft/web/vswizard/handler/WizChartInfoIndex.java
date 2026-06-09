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
package inetsoft.web.vswizard.handler;

/**
 * Index resolution for a chart recommendation's selected sub-type across the combined
 * {@code chartInfos ++ prefInfos} index space used by the wiz auto-binding path.
 *
 * <p>Kept as a standalone, state-free class (no static initializer) so the pure logic is unit
 * testable without bootstrapping {@link VSWizardBindingHandler}'s app-dependent static context.
 */
final class WizChartInfoIndex {
   private WizChartInfoIndex() {
   }

   /**
    * Resolves the selected index for a chart recommendation across the combined
    * {@code chartInfos ++ prefInfos} index space (see {@code VSWizardBindingHandler.addChartVSAssembly}).
    *
    * <p>Unlike the subType-based clamp in {@code getSelectedSubTypeIdx}, this does NOT clamp against
    * {@code subTypes} — those span only {@code chartInfos}, so a preference-scored chart type stored
    * in {@code prefInfos} (selectedIndex &ge; {@code chartInfosSize}) would otherwise be reset to
    * {@code chartInfos[0]}, silently turning a requested type (e.g. point) into the default bar.
    *
    * @return the selected index in {@code [0, chartInfosSize + prefInfosSize)}, or {@code -1} when
    *         there are no infos at all (preserving the "No valid subtype for chart!" guard).
    */
   static int resolve(int selectedIndex, int chartInfosSize, int prefInfosSize) {
      int total = Math.max(0, chartInfosSize) + Math.max(0, prefInfosSize);

      if(total == 0) {
         return -1;
      }

      return selectedIndex >= 0 && selectedIndex < total ? selectedIndex : 0;
   }
}
