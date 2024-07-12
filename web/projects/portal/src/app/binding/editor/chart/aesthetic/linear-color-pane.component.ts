/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { Component, Input, OnInit, Output, EventEmitter } from "@angular/core";
import { Tool } from "../../../../../../../shared/util/tool";
import * as V from "../../../../common/data/visual-frame-model";

@Component({
   selector: "linear-color-pane",
   templateUrl: "linear-color-pane.component.html",
   styleUrls: ["linear-color-pane.component.scss"]
})
export class LinearColorPane implements OnInit {
   @Input() frame: V.ColorFrameModel = new V.GradientColorModel();
   @Output() onChangeColorFrame: EventEmitter<any> = new EventEmitter<any>();
   @Output() apply: EventEmitter<boolean> = new EventEmitter<boolean>();
   originalFrame: V.ColorFrameModel;
   gradientModel: V.GradientColorModel = new V.GradientColorModel();
   heatModel: V.HeatColorModel = new V.HeatColorModel();
   singleHueModel: V.BluesColorModel = new V.BluesColorModel();
   multiHueModel: V.BuGnColorModel = new V.BuGnColorModel();
   divergingModel: V.BrBGColorModel = new V.BrBGColorModel();

   BluesColorModel: V.BluesColorModel = new V.BluesColorModel();
   BrBGColorModel: V.BrBGColorModel = new V.BrBGColorModel();
   BuGnColorModel: V.BuGnColorModel = new V.BuGnColorModel();
   BuPuColorModel: V.BuPuColorModel = new V.BuPuColorModel();
   GnBuColorModel: V.GnBuColorModel = new V.GnBuColorModel();
   GreensColorModel: V.GreensColorModel = new V.GreensColorModel();
   GreysColorModel: V.GreysColorModel = new V.GreysColorModel();
   OrangesColorModel: V.OrangesColorModel = new V.OrangesColorModel();
   OrRdColorModel: V.OrRdColorModel = new V.OrRdColorModel();
   PiYGColorModel: V.PiYGColorModel = new V.PiYGColorModel();
   PRGnColorModel: V.PRGnColorModel = new V.PRGnColorModel();
   PuBuColorModel: V.PuBuColorModel = new V.PuBuColorModel();
   PuBuGnColorModel: V.PuBuGnColorModel = new V.PuBuGnColorModel();
   PuOrColorModel: V.PuOrColorModel = new V.PuOrColorModel();
   PuRdColorModel: V.PuRdColorModel = new V.PuRdColorModel();
   PurplesColorModel: V.PurplesColorModel = new V.PurplesColorModel();
   RdBuColorModel: V.RdBuColorModel = new V.RdBuColorModel();
   RdGyColorModel: V.RdGyColorModel = new V.RdGyColorModel();
   RdPuColorModel: V.RdPuColorModel = new V.RdPuColorModel();
   RdYlGnColorModel: V.RdYlGnColorModel = new V.RdYlGnColorModel();
   RedsColorModel: V.RedsColorModel = new V.RedsColorModel();
   SpectralColorModel: V.SpectralColorModel = new V.SpectralColorModel();
   RdYlBuColorModel: V.RdYlBuColorModel = new V.RdYlBuColorModel();
   YlGnBuColorModel: V.YlGnBuColorModel = new V.YlGnBuColorModel();
   YlGnColorModel: V.YlGnColorModel = new V.YlGnColorModel();
   YlOrBrColorModel: V.YlOrBrColorModel = new V.YlOrBrColorModel();
   YlOrRdColorModel: V.YlOrRdColorModel = new V.YlOrRdColorModel();

   singleHueModels: string[] = [
      "BluesColorModel",
      "GreensColorModel",
      "GreysColorModel",
      "OrangesColorModel",
      "PurplesColorModel",
      "RedsColorModel",
   ];

   multiHueModels: string[] = [
      "BuGnColorModel",
      "BuPuColorModel",
      "GnBuColorModel",
      "OrRdColorModel",
      "PuBuColorModel",
      "PuBuGnColorModel",
      "PuRdColorModel",
      "RdPuColorModel",
      "YlGnBuColorModel",
      "YlGnColorModel",
      "YlOrBrColorModel",
      "YlOrRdColorModel",
   ];

   divergingModels: string[] = [
      "BrBGColorModel",
      "PiYGColorModel",
      "PRGnColorModel",
      "PuOrColorModel",
      "RdBuColorModel",
      "RdGyColorModel",
      "RdYlGnColorModel",
      "SpectralColorModel",
      "RdYlBuColorModel",
   ];

   constructor() {
   }

   ngOnInit() {
      this.resetEditors(true);
      this.syncColors(false);
      this.setBrewerColor();
   }

   // set single/multi/diverging from frame
   setBrewerColor() {
      if(this.singleHueModels.find(m => this.frame.clazz.endsWith("." + m))) {
         this.singleHueModel = this.frame;
      }
      else if(this.multiHueModels.find(m => this.frame.clazz.endsWith("." + m))) {
         this.multiHueModel = this.frame;
      }
      else if(this.divergingModels.find(m => this.frame.clazz.endsWith("." + m))) {
         this.divergingModel = this.frame;
      }
   }

   getSingleHueModel(): string {
      return this.singleHueModel.clazz.substring(this.singleHueModel.clazz.lastIndexOf(".") + 1);
   }

   getMultiHueModel(): string {
      return this.multiHueModel.clazz.substring(this.multiHueModel.clazz.lastIndexOf(".") + 1);
   }

   getDivergingHueModel(): string {
      return this.divergingModel.clazz.substring(this.divergingModel.clazz.lastIndexOf(".") + 1);
   }

   get gmodel() {
      return this.isSelectedFrame(this.gradientModel) ?
         this.frame : this.gradientModel;
   }

   resetEditors(init?: boolean) {
      if(!init) {
         if(this.isSelectedFrame(this.gradientModel)) {
            this.gradientModel = <V.GradientColorModel>(this.gmodel);
         }
      }

      if(init) {
         this.originalFrame = this.frame;
      }

      this.gradientModel.fromColor = null;
      this.gradientModel.defaultFromColor = "#ff99cc";
      this.gradientModel.toColor = null;
      this.gradientModel.defaultToColor = "#008000";
      this.frame = this.originalFrame;
      this.setBrewerColor();
      this.onChangeColorFrame.emit(this.frame);
   }

   switchColorModel(val: string) {
      if(val == this.gradientModel.clazz) {
         let cssFromColor = null;
         let cssToColor = null;

         if(this.frame && this.frame.clazz == this.gradientModel.clazz) {
            cssFromColor = (<V.GradientColorModel> this.frame).cssFromColor;
            cssToColor = (<V.GradientColorModel> this.frame).cssToColor;
         }
         else if(this.originalFrame && this.originalFrame.clazz == this.gradientModel.clazz) {
            cssFromColor = (<V.GradientColorModel> this.originalFrame).cssFromColor;
            cssToColor = (<V.GradientColorModel> this.originalFrame).cssToColor;
         }

         this.frame = <V.GradientColorModel> Tool.clone(this.gradientModel);
         (<V.GradientColorModel> this.frame).cssFromColor = cssFromColor;
         (<V.GradientColorModel> this.frame).cssToColor = cssToColor;
      }
      else if(val == this.heatModel.clazz) {
         this.frame = new V.HeatColorModel();
      }
      else if(this[val]) {
         this.frame = <any> Tool.clone(this[val]);
      }
      else if(this[val.substring(val.lastIndexOf(".") + 1)]) {
         this.frame = <any> Tool.clone(this[val.substring(val.lastIndexOf(".") + 1)]);
      }

      this.setBrewerColor();
      this.onChangeColorFrame.emit(this.frame);
   }

   /**
    * Sync the colors from this.frame to gradient/saturation/brightness model
    * to keep the colors after switch the color model.
    */
   syncColors(submit: boolean = true) {
      if(this.isSelectedFrame(this.gradientModel)) {
         this.gradientModel.fromColor =
            (<V.GradientColorModel> this.frame).fromColor;
         this.gradientModel.toColor = (<V.GradientColorModel> this.frame).toColor;

         if(submit) {
            this.onChangeColorFrame.emit(this.frame);
         }
      }
   }

   isSelectedFrame(colorModel: V.ColorFrameModel): boolean {
      return this.frame && (this.frame.clazz == colorModel.clazz);
   }
}
