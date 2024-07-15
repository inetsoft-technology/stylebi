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
import { NgZone, OnChanges, OnDestroy, Input, Directive } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Subscription } from "rxjs";
import { GuiTool } from "../../../common/util/gui-tool";
import { StyleConstants } from "../../../common/util/style-constants";
import { ViewsheetClientService } from "../../../common/viewsheet-client/viewsheet-client.service";
import { ContextProvider } from "../../context-provider.service";
import { VSShapeModel } from "../../model/vs-shape-model";
import { AbstractVSObject } from "../abstract-vsobject.component";
import { AbstractVSActions } from "../../action/abstract-vs-actions";
import { isNumber } from "lodash";
import { DataTipService } from "../data-tip/data-tip.service";

@Directive()
export abstract class VSShape<T extends VSShapeModel> extends AbstractVSObject<T> implements OnChanges, OnDestroy
{
   public lineWidth: number;
   public lineDash: string;
   public doubleLine: boolean;
   private _actions: AbstractVSActions<VSShapeModel>;
   private actionSubscription: Subscription;
   private ts = (new Date()).getTime();

   public static readonly GRADIENTCOLOR_DEFAULT_ANGLE = 45;

   constructor(protected viewsheetClient: ViewsheetClientService,
               protected modalService: NgbModal,
               zone: NgZone,
               protected contextProvider: ContextProvider,
               protected dataTipService: DataTipService)
   {
      super(viewsheetClient, zone, contextProvider, dataTipService);
   }

   @Input()
   set actions(actions: AbstractVSActions<VSShapeModel>) {
      this._actions = actions;
      this.unsubscribe();
   }

   get actions(): AbstractVSActions<VSShapeModel> {
      return this._actions;
   }

   getGradientId(type: string) {
      return this.model.absoluteName + type + "_" + this.ts;
   }

   get gradientDirection(): string {
      if(!this.model.objectFormat.gradientColor) {
         return null;
      }

      return this.model.objectFormat.gradientColor.direction;
   }

   abstract ngOnChanges(SimpleChanges);

   ngOnDestroy(): void {
      super.ngOnDestroy();
      this.unsubscribe();
   }

   private unsubscribe(): void {
      if(this.actionSubscription) {
         this.actionSubscription.unsubscribe();
         this.actionSubscription = null;
      }
   }

   /**
    * When the model changes call this method to update the line style
    */
   protected updateLineStyle() {
      this.doubleLine = this.model.lineStyle === StyleConstants.DOUBLE_LINE;

      if(!this.doubleLine) {
         this.lineWidth = VSShape.getThickness(this.model);
         this.lineDash = VSShape.getDash(this.model);
      }
   }

   /**
    * Get the line style 's width.
    */
   private static getThickness(model: VSShapeModel): number {
      const style = model.lineStyle;

      return (style & GuiTool.WIDTH_MASK) + ((style & GuiTool.FRACTION_WIDTH_MASK) != 0 ? 1 : 0);
   }

   /**
    * Get the line style 's dash length and return the correspond width/gap combination
    */
   private static getDash(model: VSShapeModel): string {
      const style = model.lineStyle;

      if((style & GuiTool.DASH_MASK) != 0) {
         const dashLength: number = (style & GuiTool.DASH_MASK) >> 4;
         return dashLength.toString() + ", " + (dashLength + 1).toString();
      }

      return null;
   }

    /**
     * get gradient color angle. by default is GRADIENTCOLOR_DEFAULT_ANGLE.
     */
   get gradientAngle(): number {
       return !!this.model.objectFormat.gradientColor ? this.model.objectFormat.gradientColor.angle
           : VSShape.GRADIENTCOLOR_DEFAULT_ANGLE;
   }

   get linearGradientStartX(): string {
      let tan: number;

      try {
         tan = Math.abs(Math.round(Math.tan(this.gradientAngle * Math.PI / 180)));
         tan = this.gradientAngle > 90 && this.gradientAngle < 270 ? tan : -tan;
      }
      catch (ignore) {
          return "50%";
      }

      if(this.gradientAngle == 0) {
          return "0%";
      }
      else if(this.gradientAngle == 90 || this.gradientAngle == 270) {
          return "50%";
      }
      else if(this.gradientAngle == 180) {
          return "100%";
      }
      else if(isNumber(tan)) {
         return `${100 * (1 + tan) / 2}%`;
      }

      return "0%";
   }

   get linearGradientStartY(): string {
      if(this.gradientAngle == 0 || this.gradientAngle == 180) {
          return "50%";
      }
      else if(this.gradientAngle > 0 && this.gradientAngle < 180
         || this.gradientAngle == 90) {
         return "100%";
      }
      else if(this.gradientAngle > 180 && this.gradientAngle < 360
         || this.gradientAngle == 270) {
         return "0%";
      }


      return "100%";
   }

   get linearGradientEndX(): string {
      if(this.gradientAngle == 90 || this.gradientAngle == 270) {
         return "50%";
      }
      else if(this.gradientAngle == 0
         || this.gradientAngle > 0 && this.gradientAngle < 90
         || this.gradientAngle > 270 && this.gradientAngle < 360) {
            return "100%";
      }
      else if(this.gradientAngle == 180
         || this.gradientAngle > 90 && this.gradientAngle < 270) {
         return "0%";
      }

      return "100%";
   }

   get linearGradientEndY(): string {
      let tan: number;

      try {
         tan = Math.abs(Math.round(Math.tan(this.gradientAngle * Math.PI / 180)));
         tan = this.gradientAngle > 0 && this.gradientAngle < 180 ? -tan : tan;
      }
      catch (ignore) {
      }

      if(this.gradientAngle == 90) {
         return "0%";
      }
      else if(this.gradientAngle == 270) {
         return "100%";
      }
      else if(isNumber(tan)) {
         return `${100 * (1 + tan) / 2}%`;
      }

      return "0%";
   }
}
