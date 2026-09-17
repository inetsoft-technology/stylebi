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
import { HttpParams } from "@angular/common/http";
import { Component, Input, OnInit, Output, EventEmitter } from "@angular/core";
import { Observable } from "rxjs";
import { Tool } from "../../../../../../../shared/util/tool";
import * as V from "../../../../common/data/visual-frame-model";
import { ModelService } from "../../../../widget/services/model.service";
import { LinearColorDropdown } from "./linear-color-dropdown.component";
import { GradientColorEditor } from "./gradient-color-editor.component";
import { FormsModule } from "@angular/forms";

const HIDDEN_LINEAR_FRAMES_URI: string = "../api/composer/chart/hiddenlinearframes";

@Component({
    selector: "linear-color-pane",
    templateUrl: "linear-color-pane.component.html",
    styleUrls: ["linear-color-pane.component.scss"],
    imports: [FormsModule, GradientColorEditor, LinearColorDropdown]
})
export class LinearColorPane implements OnInit {
   @Input() frame: V.ColorFrameModel = new V.GradientColorModel();
   @Input() vsId: string;
   @Input() assemblyName: string;
   @Output() onChangeColorFrame: EventEmitter<any> = new EventEmitter<any>();
   @Output() apply: EventEmitter<boolean> = new EventEmitter<boolean>();
   originalFrame: V.ColorFrameModel;
   gradientModel: V.GradientColorModel = new V.GradientColorModel();
   heatModel: V.HeatColorModel = new V.HeatColorModel();
   singleHueModel: V.BluesColorModel = new V.BluesColorModel();
   multiHueModel: V.BuGnColorModel = new V.BuGnColorModel();
   divergingModel: V.BrBGColorModel = new V.BrBGColorModel();
   hiddenFrames: string[] = [];
   visibleSingleHue: string[] = [];
   visibleMultiHue: string[] = [];
   visibleDiverging: string[] = [];

   AmberColorModel: V.AmberColorModel = new V.AmberColorModel();
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
   TealColorModel: V.TealColorModel = new V.TealColorModel();
   VarianceColorModel: V.VarianceColorModel = new V.VarianceColorModel();
   YlGnBuColorModel: V.YlGnBuColorModel = new V.YlGnBuColorModel();
   YlGnColorModel: V.YlGnColorModel = new V.YlGnColorModel();
   YlOrBrColorModel: V.YlOrBrColorModel = new V.YlOrBrColorModel();
   YlOrRdColorModel: V.YlOrRdColorModel = new V.YlOrRdColorModel();

   singleHueModels: string[] = [
      "AmberColorModel",
      "BluesColorModel",
      "GreensColorModel",
      "GreysColorModel",
      "OrangesColorModel",
      "PurplesColorModel",
      "RedsColorModel",
      "TealColorModel",
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
      "VarianceColorModel",
   ];

   constructor(private modelService: ModelService) {
   }

   ngOnInit() {
      this.resetEditors(true);
      this.syncColors(false);
      this.setBrewerColor();
      this.getHiddenFrames().subscribe((data: string[]) => {
         this.hiddenFrames = data || [];
         this.recomputeVisible();
      });
      this.recomputeVisible();
   }

   /**
    * load which linear frames are hidden under the current chart's mark mode.
    */
   private getHiddenFrames(): Observable<string[]> {
      let params = new HttpParams();

      if(this.vsId) {
         params = params.set("vsId", this.vsId);
      }

      if(this.assemblyName) {
         params = params.set("assemblyName", this.assemblyName);
      }

      return this.modelService.getModel(HIDDEN_LINEAR_FRAMES_URI, params);
   }

   /**
    * Filter a family's model names to the ones that should be offered, keeping the
    * currently selected frame even when it is hidden so a chart already on a hidden
    * ramp doesn't lose it out from under itself.
    */
   private visible(models: string[]): string[] {
      return models.filter(m => !this.hiddenFrames.includes(m) || this.frame?.clazz?.endsWith("." + m));
   }

   private recomputeVisible(): void {
      this.visibleSingleHue = this.visible(this.singleHueModels);
      this.visibleMultiHue = this.visible(this.multiHueModels);
      this.visibleDiverging = this.visible(this.divergingModels);
      this.singleHueModel = this.offeredFamilyModel(this.singleHueModel, this.visibleSingleHue);
      this.multiHueModel = this.offeredFamilyModel(this.multiHueModel, this.visibleMultiHue);
      this.divergingModel = this.offeredFamilyModel(this.divergingModel, this.visibleDiverging);
   }

   /**
    * A family's remembered model, moved onto the family's first offered ramp when the one it holds
    * is hidden. The family radio and the collapsed dropdown face both read this model, so leaving a
    * hidden ramp in it would show a retired ramp and put the chart on it in one click. The current
    * frame is never moved: visible() keeps it in its own family's list.
    */
   private offeredFamilyModel(model: V.ColorFrameModel, offered: string[]): V.ColorFrameModel {
      const name = model?.clazz?.substring(model.clazz.lastIndexOf(".") + 1);

      if(name && offered.length > 0 && !offered.includes(name) && this[offered[0]]) {
         return this[offered[0]];
      }

      return model;
   }

   /**
    * Whether the Heat row is rendered. Hidden under a modern mark, unless the chart is already on
    * Heat - hide never removes, and the four radios in this pane bind against their own class, so
    * dropping the row on a chart that holds Heat would leave none of them checked and no way back.
    */
   get heatVisible(): boolean {
      return !this.hiddenFrames.includes("HeatColorModel")
         || this.frame?.clazz?.endsWith(".HeatColorModel");
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
      this.recomputeVisible();
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
