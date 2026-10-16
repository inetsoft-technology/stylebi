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

   /**
    * The lane a checkbox or radio button takes under ctx: the stored height when its author set
    * it or moved it off the legacy height, the tier lane otherwise.
    */
   static <T extends ListInputVSAssemblyInfo & TitledVSAssemblyInfo> int lane(T info,
                                                                             VizContext ctx)
   {
      int stored = info.getTitleHeightValue();
      return info.isUserTitleHeight() || stored != info.getLegacyTitleHeight() ?
         stored : VSDensityDefaults.titleHeight(ctx);
   }

   /**
    * The option row under ctx, by the same tests as the lane.
    */
   static <T extends ListInputVSAssemblyInfo & TitledVSAssemblyInfo> int row(T info,
                                                                            VizContext ctx)
   {
      int stored = info.getCellHeightValue();
      return info.isUserCellHeight() || stored != AssetUtil.defh ?
         stored : VSDensityDefaults.cellHeight(ctx);
   }

   /**
    * An option list's density size: its own width over its lane and one row.
    */
   static <T extends ListInputVSAssemblyInfo & TitledVSAssemblyInfo> Dimension optionBoxSize(
      T info, VizContext ctx)
   {
      return new Dimension(info.getPixelSize().width, lane(info, ctx) + row(info, ctx));
   }

   /**
    * The checkbox and radio rule: clear a row the earlier seed stored, then size the box. The
    * legacy branches run only on a Revert or mode switch.
    */
   static <T extends ListInputVSAssemblyInfo & TitledVSAssemblyInfo> void seedOptionBox(
      T info, VizContext ctx)
   {
      int box = info.getPixelSize().height;
      int storedRow = info.getCellHeightValue();

      if(!info.isUserCellHeight() && VSDensityDefaults.isFormerOptionRow(storedRow) &&
         (ctx.modern || ctx.transition && VSDensityDefaults.isFormerOptionBoxHeight(box) &&
            storedRow == box - info.getTitleHeightValue()))
      {
         info.setCellHeight(AssetUtil.defh);
      }

      if(info.isUserSize() || !VSDensityDefaults.isSeededOptionBoxHeight(box)) {
         return;
      }

      if(ctx.modern) {
         info.setPixelSize(optionBoxSize(info, ctx));
      }
      else if(ctx.transition) {
         info.setPixelSize(new Dimension(info.getPixelSize().width,
                                         VSDensityDefaults.optionBoxHeight(ctx)));
      }
   }
}
