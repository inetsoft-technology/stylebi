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
import { Component, Input } from "@angular/core";
import { StaticTextureModel } from "../../../../common/data/visual-frame-model";
import * as Visual from "../../../../common/data/visual-frame-model";
@Component({
   selector: "linear-texture-pane",
   templateUrl: "linear-texture-pane.component.html",
})

export class LinearTexturePane {
   @Input() frameModel: Visual.TextureFrameModel;
   leftTiltModel: Visual.LeftTiltTextureModel;
   rightTiltModel: Visual.RightTiltTextureModel;
   gridModel: Visual.GridTextureModel;
   orientationModel: Visual.OrientationTextureModel;

   constructor() {
      this.initOption();
   }

   initOption() {
      this.leftTiltModel =  new Visual.LeftTiltTextureModel();
      this.rightTiltModel =  new Visual.RightTiltTextureModel();
      this.gridModel =  new Visual.GridTextureModel();
      this.orientationModel =  new Visual.OrientationTextureModel();
   }

}
