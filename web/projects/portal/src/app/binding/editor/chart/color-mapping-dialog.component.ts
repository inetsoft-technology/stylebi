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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { UntypedFormGroup } from "@angular/forms";
import { ColorMap } from "../../../common/data/color-map";
import { CategoricalColorModel } from "../../../common/data/visual-frame-model";
import { AestheticInfo } from "../../data/chart/aesthetic-info";
import { ColorMappingDialogModel } from "../../data/chart/color-mapping-dialog-model";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ComponentTool } from "../../../common/util/component-tool";
import { ValueLabelModel } from "../../data/value-label-model";

@Component({
   selector: "color-mapping-dialog",
   templateUrl: "color-mapping-dialog.component.html",
   styleUrls: ["color-mapping-dialog.component.scss"]
})
export class ColorMappingDialog implements OnInit {
   @Input() model: ColorMappingDialogModel;
   @Input() field: AestheticInfo;
   controller: string = "../api/composer/vs/color-mapping-dialog-model";
   form: UntypedFormGroup;
   @Output() onCommit: EventEmitter<ColorMap[]> = new EventEmitter<ColorMap[]>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   dimensionData: ValueLabelModel[] = [];

   constructor(private modalService: NgbModal) {
   }

   get currentColorMaps(): ColorMap[] {
      return (this.model.useGlobal ? this.model.globalModel : this.model).colorMaps;
   }

   set currentColorMaps(colorMaps: ColorMap[]) {
      (this.model.useGlobal ? this.model.globalModel : this.model).colorMaps = colorMaps;
   }

   ngOnInit(): void {
      this.updateDimensionLabels();

      if(!!this.model.browseDataErrorMsg) {
         ComponentTool.showMessageDialog(this.modalService, "Info", this.model.browseDataErrorMsg,
            {"ok": "_#(js:OK)"}, {backdrop: false })
          .then(() => {}, () => {});
      }
   }

   close(): void {
      this.onCancel.emit("cancel");
   }

   ok() {
      let npalette: CategoricalColorModel = new CategoricalColorModel();
      npalette.dateFormat = this.field.dataInfo["dateLevel"];
      npalette.colorMaps = this.model.colorMaps;
      let maps: ColorMap[] = new Array<ColorMap>();
      let optionIndexMap = {};

      this.currentColorMaps.forEach((map0: ColorMap) => {
         if(!!map0.color) {
            if(optionIndexMap[map0.option] >= 0) {
               maps[optionIndexMap[map0.option]] = map0;
            }
            else {
               maps.push(map0);
               optionIndexMap[map0.option] = maps.length - 1;
            }
         }
      });

      this.onCommit.emit(maps);
   }

   addRow(): void {
      let option =  this.model.dimensionData && this.model.dimensionData.length > 0 ?
         this.model.dimensionData[0].value : null;
      let colormap: ColorMap = {option: option, color: null};
      this.currentColorMaps.push(colormap);
   }

   deleteRow(index: number): void {
      this.currentColorMaps.splice(index, 1);
   }

   reset(): void {
      this.currentColorMaps = [];
      this.addRow();
   }

   toggleGlobal(): void {
      this.model.useGlobal = !this.model.useGlobal;
      // this.currentColorMaps = this.currentColorMaps;
      this.updateDimensionLabels();
   }

   isValid(): boolean {
      return this.model.colorMaps.some(f => !f.option);
   }

   /**
    * Show labels for dimension values that may not exist in the data set anymore but have already
    * had a color assigned.
    */
   private updateDimensionLabels() {
      let dateLevel = this.field.dataInfo["dateLevel"];

      // if it's a date range then check against the label in case it's from an older version
      // where Sun would be set as option instead of 1
      if(dateLevel != 0) {
         this.currentColorMaps = this.currentColorMaps.map((map) => {
            let found = this.model.dimensionData.find((valLabel) => valLabel.label == map.option);

            if(found) {
               map.option = found.value;
            }

            return map;
         });
      }

      // use text input to enter value if value list is not available
      if(this.model.dimensionData.length > 0) {
         let colorOptions = this.currentColorMaps.map(map => map.option)
            .filter(dim => this.model.dimensionData.find((valLabel) => valLabel.value == dim) == null);
         this.dimensionData = [].concat(this.model.dimensionData)
            .concat(colorOptions.map((option) => <ValueLabelModel>{value: option, label: option}));
      }
      else {
         this.dimensionData = [];
      }
   }
}
