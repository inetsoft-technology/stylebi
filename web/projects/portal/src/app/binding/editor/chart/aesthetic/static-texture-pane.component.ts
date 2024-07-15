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
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { StaticTextureModel } from "../../../../common/data/visual-frame-model";
import { ChartConfig } from "../../../../common/util/chart-config";
import { ChartAggregateRef } from "../../../data/chart/chart-aggregate-ref";

@Component({
   selector: "static-texture-pane",
   templateUrl: "static-texture-pane.component.html",
   styleUrls: ["static-texture-pane.component.scss"]
})
export class StaticTexturePane {
   @Input() texture: number;
   @Input() frameModel: StaticTextureModel;
   @Input() aggr: ChartAggregateRef;
   @Input() isMixed: boolean;
   @Output() textureChanged: EventEmitter<number> = new EventEmitter<number>();

   get textures(): number[] {
      return ChartConfig.TEXTURE_STYLES;
   }

   isSelectTexture(val: number): boolean {
      if(this.isMixed) {
         return false;
      }

      if(this.frameModel) {
         return this.frameModel.texture == val;
      }

      return this.texture == val;
   }

   selectTexture(ntexture: number) {
      if(this.frameModel) {
         this.frameModel.texture = ntexture;
         // force submit for mixed (allaggregate) since the default is -1 and the
         // first texture (solid) is -1, so clicking on -1 will be ignored if we don't
         // mark it as changed.
         this.frameModel.changed = true;
      }

      this.texture = ntexture;
      this.textureChanged.emit(ntexture);
   }
}
