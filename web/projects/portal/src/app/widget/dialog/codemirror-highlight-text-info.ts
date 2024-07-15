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
import { Rectangle } from "../../common/data/rectangle";
import { AnalysisResult, AnalysisResultLevel } from "./script-pane/analysis-result";

export class CodemirrorHighlightTextInfo extends AnalysisResult {

   constructor(public from: {line, ch},
               public to: {line, ch},
               public label: string,
               public msg: string,
               public textMarker?: any)
   {
      super(AnalysisResultLevel.Error);
   }

   public intersects(from: {line, ch}, to: {line, ch}): boolean {
      const thisRec = this.buildRectangle(this.from, this.to);
      const otherRec = this.buildRectangle(from, to);

      return thisRec.intersects(otherRec);
   }

   private buildRectangle(from: {line, ch}, to: {line, ch}): Rectangle {
      return new Rectangle(from.ch, from.line,
         Math.max(1, Math.abs(to.ch - from.ch)),
         Math.max(1, Math.abs(to.line - from.line)));
   }
}
