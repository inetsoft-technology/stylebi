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
import {
   Component,
   EventEmitter,
   Input,
   OnInit,
   Output,
   TemplateRef,
   ViewChild
} from "@angular/core";
import { NgbModal, NgbModalOptions } from "@ng-bootstrap/ng-bootstrap";
import { Observable } from "rxjs";
import { filter, map, tap } from "rxjs/operators";
import { ColorMap } from "../../../../common/data/color-map";
import {
   CategoricalColorModel,
   VisualFrameModel
} from "../../../../common/data/visual-frame-model";
import { UIContextService } from "../../../../common/services/ui-context.service";
import { Tool } from "../../../../../../../shared/util/tool";
import { ComponentTool } from "../../../../common/util/component-tool";
import { ModelService } from "../../../../widget/services/model.service";
import { AestheticInfo } from "../../../data/chart/aesthetic-info";
import { ChartDimensionRef } from "../../../data/chart/chart-dimension-ref";
import { ColorMappingDialogModel } from "../../../data/chart/color-mapping-dialog-model";
import { GraphUtil } from "../../../util/graph-util";
import { ColorMappingDialog } from "../color-mapping-dialog.component";
import { PaletteDialog } from "../palette-dialog.component";
import { CategoricalFramePane } from "./categorical-frame-pane";
import { ChartEditorService } from "../../../services/chart/chart-editor.service";

const COLOR_PALETTES_URI: string = "../api/composer/chart/colorpalettes";
const COLOR_MAPPING_URI: string = "../api/composer/vs/getColorMappingDialogModel";

@Component({
   selector: "categorical-color-pane",
   templateUrl: "categorical-color-pane.component.html",
   styleUrls: ["categorical-pane.scss", "categorical-color-pane.component.scss"]
})
export class CategoricalColorPane extends CategoricalFramePane implements OnInit {
   @Input() vsId: string;
   @Input() assemblyName: string;
   @Input() frameModel: CategoricalColorModel;
   @Input() field: AestheticInfo;
   @Input() isDC: boolean = false;
   @ViewChild("paletteDialog") paletteDialog: TemplateRef<any>;
   colorMappingDialogModel: ColorMappingDialogModel;
   colorPalettes: CategoricalColorModel[];
   @Output() openDialog: EventEmitter<boolean> = new EventEmitter<boolean>();
   @Output() apply: EventEmitter<any> = new EventEmitter<any>();
   @Output() resetted: EventEmitter<any> = new EventEmitter<any>();
   customChartFrames: string[];

   constructor(private modalService: NgbModal, private modelService: ModelService,
               private uiContextService: UIContextService,
               private editorService: ChartEditorService)
   {
      super();
      editorService.getCustomChartFrames()
         .subscribe((types: string[]) => this.customChartFrames = types);
   }

   get isVS(): boolean {
      return this.uiContextService.isVS();
   }

   ngOnInit() {
      this.getColorPalettes().subscribe((data: CategoricalColorModel[]) => {
         this.colorPalettes = data;
      });
   }

   /**
    * load color mapping dialog method, this is only for composer.
    */
   private getColorMappingDialog(): Observable<any> {
      const params = new HttpParams()
         .set("vsId", this.vsId)
         .set("assemblyName", this.assemblyName)
         .set("dimensionName", this.field.dataInfo.fullName);
      return this.modelService.sendModel(COLOR_MAPPING_URI, this.frameModel, params);
   }

   /**
    * load color palettes.
    */
   private getColorPalettes(): Observable<any> {
      return this.modelService.getModel(COLOR_PALETTES_URI);
   }

   clickPaletteButton() {
      let modalOptions: NgbModalOptions = {
         backdrop: "static",
         size: "sm"
      };

      this.openDialog.emit(true);
      const dialog = ComponentTool.showDialog(this.modalService, PaletteDialog,
         (result: CategoricalColorModel) => {
            this.frameModel.colors = result.colors;
            setTimeout(() => this.openDialog.emit(false), 0);
         }, modalOptions, () => {
            setTimeout(() => this.openDialog.emit(false), 0);
         });

      dialog.colorPalettes = this.colorPalettes;
      dialog.currPalette = this.frameModel;
   }

   clickColorMappingButton() {
      if(this.colorMappingDialogModel) {
         this.openColorMappingDialog();
      }
      else {
         this.getColorMappingDialogModel().subscribe(() => this.openColorMappingDialog());
      }
   }

   private getColorMappingDialogModel(): Observable<ColorMappingDialogModel> {
      return this.getColorMappingDialog().pipe(
         filter(data => data.body != null),
         map(data => data.body),
         tap((model) => this.colorMappingDialogModel = model),
      );
   }

   private openColorMappingDialog() {
      let modalOptions: NgbModalOptions = {
         backdrop: "static",
      };

      const currentColorMaps = this.colorMappingDialogModel.colorMaps;
      this.openDialog.emit(true);
      const dialog = ComponentTool.showDialog(
         this.modalService, ColorMappingDialog, (maps: ColorMap[]) => {
            const useGlobal = this.colorMappingDialogModel.useGlobal;

            // color mapping changed, clear out oframes to force submit. (57425)
            if(!Tool.isEquals(currentColorMaps, maps)) {
               this.resetted.emit();
            }

            if(useGlobal) {
               this.frameModel.globalColorMaps = maps;
            }
            else {
               this.frameModel.colorMaps = maps;
            }

            this.frameModel.useGlobal = this.colorMappingDialogModel.useGlobal;
            const dataRef = this.field.dataInfo;

            if(GraphUtil.isDimensionRef(dataRef)) {
               const dim = <ChartDimensionRef> dataRef;
               this.frameModel.dateFormat = !!maps && maps.length > 0 ?
                  parseInt(dim.dateLevel, 10) : null;
            }

            setTimeout(() => this.openDialog.emit(false), 0);
         }, modalOptions, () => {
            setTimeout(() => this.openDialog.emit(false), 0);
         });

      dialog.model = this.colorMappingDialogModel;
      dialog.field = this.field;
   }

   reset() {
      if(!this.frameModel || !this.frameModel.colors) {
         return;
      }

      for(let i = 0; i < this.frameModel.colors.length; i++) {
         this.frameModel.colors[i] = this.frameModel.cssColors[i] ?
            this.frameModel.cssColors[i] : this.frameModel.defaultColors[i];
      }
   }

   isResetted() {
      if(!this.frameModel || !this.frameModel.colors) {
         return false;
      }

      for(let i = 0; i < this.frameModel.colors.length; i++) {
         let ocolor = this.frameModel.cssColors[i] ? this.frameModel.cssColors[i] :
            this.frameModel.defaultColors[i];

         if(ocolor != this.frameModel.colors[i]) {
            return false;
         }
      }

      return true;
   }

   getNumItems(): number {
      if(!this.frameModel || !this.frameModel.colors) {
         return 0;
      }

      return this.frameModel.colors.length;
   }

   changeColor(ncolor: string, idx: number) {
      if(this.frameModel) {
         this.frameModel.colors[idx] = ncolor;
      }
   }

   getFrame(): VisualFrameModel {
      return this.frameModel;
   }

   isDimension(): boolean {
      return GraphUtil.isDimensionRef(this.field.dataInfo) ||
         GraphUtil.isGeoRef(this.field.dataInfo);
   }

   applyClick() {
      this.apply.emit(false);
   }

   shareColorsChange(share: boolean) {
      this.frameModel.useGlobal = share;
      this.frameModel.shareColors = share;

      if(share) {
         this.frameModel.globalColorMaps = this.frameModel.colorMaps;
      }
      else {
         this.frameModel.colorMaps = this.frameModel.globalColorMaps;
      }

      this.getColorMappingDialogModel().subscribe(() => {
         this.colorMappingDialogModel.shareColors = share;
         this.colorMappingDialogModel.useGlobal = share;
         this.frameModel.globalColorMaps = this.colorMappingDialogModel.globalModel.colorMaps;
         this.frameModel.colorMaps = this.colorMappingDialogModel.colorMaps;
      });
   }

   showColorValueFrame(): boolean {
      return this.customChartFrames && this.customChartFrames.includes("ColorValueColorFrame");
   }
}
