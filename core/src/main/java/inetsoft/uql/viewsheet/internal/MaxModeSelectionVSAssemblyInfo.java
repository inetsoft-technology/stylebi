/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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

import inetsoft.uql.viewsheet.BorderColors;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.VSFormat;

import java.awt.*;

public abstract class MaxModeSelectionVSAssemblyInfo extends SelectionVSAssemblyInfo
   implements MaxModeSupportAssemblyInfo
{

   @Override
   public Dimension getMaxSize() {
      return maxSize;
   }

   @Override
   public void setMaxSize(Dimension maxSize) {
      this.maxSize = maxSize;
   }

   /**
    * @return the z-index value when in max mode
    */
   @Override
   public int getMaxModeZIndex() {
      return maxModeZIndex > 0 ? maxModeZIndex : getZIndex();
   }

   /**
    * Set the z-index value when in max mode
    */
   @Override
   public void setMaxModeZIndex(int maxModeZIndex) {
      this.maxModeZIndex = maxModeZIndex;
   }

   @Override
   protected void seedChromeDefaults(VizContext ctx) {
      super.seedChromeDefaults(ctx);

      // the title lane, shared by the selection family and the range slider: both install or
      // overwrite their own title composite, and neither has ever carried a fill, so only the
      // rule's colour and the text colour move. Both branches write, because the legacy one is
      // what Revert relies on to restore a never-modernized assembly
      VSCompositeFormat titleFormat = getFormatInfo().getFormat(TITLEPATH);

      if(titleFormat != null) {
         VSFormat def = titleFormat.getDefaultFormat();
         def.setBordersValue(VSTitleChromeDefaults.titleRuleBorders());
         def.setBorderColorsValue(ctx.modern
            ? VSTitleChromeDefaults.titleRuleColors(ctx) : legacyTitleRuleColors());
         def.setForegroundValue(
            ctx.modern ? VSTitleChromeDefaults.titleForegroundValue(ctx) : null);
         // getForeground() falls back to the fg field when fgval yields nothing, so the legacy
         // branch has to null both or a runtime foreground survives the clear
         def.setForeground(null);
      }
   }

   // the title rule this family has always drawn; the seed's legacy branch restores it, and
   // setDefaultFormat in both subclasses paints it in fresh. Fresh every call - BorderColors is
   // mutable and the caller installs it on a format.
   static BorderColors legacyTitleRuleColors() {
      return new BorderColors(new Color(0xc0c0c0), new Color(0xc0c0c0),
                              new Color(0xc0c0c0), new Color(0xc0c0c0));
   }

   // max mode
   private Dimension maxSize = null;
   private int maxModeZIndex = -1;
}
