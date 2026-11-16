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

import { HyperlinkDialogModel } from "../../model/hyperlink-dialog-model";

/**
 * The region key of the hyperlink a Hyperlink dialog is editing, in `set_hyperlink`'s own
 * vocabulary (the server's HyperlinkRegionName grammar): "link" for the one link of a text,
 * image or gauge, "title" / "emptyPlot" for the chart's own links, "cell[row,col]" for a table
 * cell, and "field|axis|text[<column>]" for a chart data region. Null when the model is missing.
 *
 * Whether the model names a region the server accepts for the assembly type is the server's
 * call at mint; the browser only reports what the dialog is showing.
 */
export function hyperlinkRegionName(model: HyperlinkDialogModel | null | undefined): string | null {
   if(!model) {
      return null;
   }

   if(model.titleLink) {
      return "title";
   }

   if(model.emptyPlotLink) {
      return "emptyPlot";
   }

   if(model.table) {
      return Number.isInteger(model.row) && Number.isInteger(model.col)
         ? `cell[${model.row},${model.col}]` : null;
   }

   if(model.colName) {
      const form = model.text ? "text" : model.axis ? "axis" : "field";
      return `${form}[${model.colName}]`;
   }

   return "link";
}
