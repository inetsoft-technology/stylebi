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

import inetsoft.uql.asset.internal.AssetUtil;

import java.awt.Dimension;

/**
 * The density size rules the form controls share. Their values live in VSDensityDefaults.
 */
final class FormControlDensity {
   private FormControlDensity() {
   }

   /**
    * The single-height rule for combo box, text input, spinner and submit. The legacy branch
    * runs only on a Revert or mode switch, so a restore never resizes an unmarked control.
    */
   static void seedControlHeight(VSAssemblyInfo info, VizContext ctx) {
      if(info.isUserSize()) {
         return;
      }

      Dimension size = info.getPixelSize();

      if(ctx.modern && VSDensityDefaults.isSeededControlHeight(size.height)) {
         info.setPixelSize(new Dimension(size.width, VSDensityDefaults.controlHeight(ctx)));
      }
      else if(!ctx.modern && ctx.transition && VSDensityDefaults.isControlHeight(size.height)) {
         info.setPixelSize(new Dimension(size.width, AssetUtil.defh));
      }
   }

   /**
    * A single control's density size: its own width over the tier height.
    */
   static Dimension controlSize(VSAssemblyInfo info, VizContext ctx) {
      return new Dimension(info.getPixelSize().width, VSDensityDefaults.controlHeight(ctx));
   }
}
