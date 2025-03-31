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
import { HttpClient } from "@angular/common/http";
import {
   Component,
   OnInit,
   Input,
   Output,
   EventEmitter,
   TemplateRef,
   ViewChild
} from "@angular/core";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { FormValidators } from "../../../../../../shared/util/form-validators";
import { ScreenSizeDialogModel } from "../../data/vs/screen-size-dialog-model";
import { ViewsheetDeviceLayoutDialogModel } from "../../data/vs/viewsheet-device-layout-dialog-model";
import { Tool } from "../../../../../../shared/util/tool";
import { Point } from "../../../common/data/point";
import { ComponentTool } from "../../../common/util/component-tool";

const URI_NEW_DEViCE = "../api/composer/device/new";
const URI_EDIT_DEViCE = "../api/composer/device/edit";
const URI_GET_DELETE_DEViCE = "../api/composer/device/delete";

@Component({
   selector: "viewsheet-device-layout-dialog",
   templateUrl: "viewsheet-device-layout-dialog.component.html",
   styleUrls: ["viewsheet-device-layout-dialog.component.scss"],
})
export class ViewsheetDeviceLayoutDialog implements OnInit {
   @Input() index: number = -1;
   @Input() add: boolean;
   @Input() devices: ScreenSizeDialogModel[];
   @Input() isEditAllowed: boolean;
   @Output() onCommit: EventEmitter<any> = new EventEmitter<any>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   @ViewChild("screenSizeDialog") screenSizeDialog: TemplateRef<any>;
   _layouts: ViewsheetDeviceLayoutDialogModel[];
   model: ViewsheetDeviceLayoutDialogModel;
   formDevice: UntypedFormGroup;
   scaleOptions: number[] = [
      0.5, 1, 1.5, 2, 2.5, 3
   ];
   selected: boolean[] = [];
   editDevice: number = -1;
   formValid = () => this.model && this.formDevice && this.formDevice.valid &&
      this.deviceSelected() && !this.duplicateName() && !this.reservedName();

   constructor(private modalService: NgbModal, private http: HttpClient) {
   }

   ngOnInit() {
      if(this.add) {
         this.model = {
            name: "",
            mobileOnly: true,
            // scaleFont: 1,
            selectedDevices: [],
            id: "ViewsheetLayout" + "-" + Date.now()
         };
      }
      else {
         this.model = this.layouts[this.index];
         this.layouts.splice(this.index, 1);
      }

      for(let i = 0; i < this.devices.length; i++) {
         let device: ScreenSizeDialogModel = this.devices[i];
         this.selected[i] = !!device.id ?
            this.model.selectedDevices.indexOf(device.id) != -1 :
            this.model.selectedDevices.indexOf(device.tempId) != -1;
      }

      this.initForm();
   }

   @Input()
   set layouts(value: ViewsheetDeviceLayoutDialogModel[]) {
      this._layouts = Tool.clone(value);
   }

   get layouts(): ViewsheetDeviceLayoutDialogModel[] {
      return this._layouts;
   }

   initForm(): void {
      this.formDevice = new UntypedFormGroup({
         name: new UntypedFormControl(this.model.name, [
            Validators.required,
            FormValidators.validLayoutName,
         ])
      });
   }

   deviceSelected(): boolean {
      return this.selected.indexOf(true) != -1;
   }

   removeDevice(index: number): void {
      const message = "_#(js:layout.vsLayout.deleteDevice)";

      ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)", message,
         {"yes": "_#(js:Yes)", "no": "_#(js:No)"})
         .then((buttonClicked) => {
            if(buttonClicked === "yes") {
               let device: ScreenSizeDialogModel = this.devices[index];

               this.http.post(URI_GET_DELETE_DEViCE, device).subscribe();
               this.selected.splice(index, 1);
               this.devices.splice(index, 1);
            }
         });
   }

   duplicateName() {
      for(let layout of this.layouts) {
         if(layout.name === this.model.name || "Master" === this.model.name) {
            return true;
         }
      }

      return false;
   }

   reservedName() {
      return this.model.name === "_#(js:Print Layout)";
   }

   showScreenSizeDialog(index?: number): void {
      this.editDevice = index != null ? index : -1;

      this.modalService.open(this.screenSizeDialog, { backdrop: "static"}).result.then(
         (result: ScreenSizeDialogModel) => {
            if(this.editDevice == -1) {
               result.id = Tool.generateRandomUUID();
               this.devices.push(result);
               this.http.post(URI_NEW_DEViCE, result).subscribe();
            }
            else {
               this.devices[this.editDevice] = result;
               this.http.post(URI_EDIT_DEViCE, result).subscribe();
            }
         },
         () => {
            // cancel
         }
      );
   }

   cancelChanges(): void {
      this.onCancel.emit("cancel");
   }

   saveChanges(): void {
      this.model.selectedDevices = [];

      for(let i = 0; i < this.devices.length; i++) {
         if(this.selected[i]) {
            let device: ScreenSizeDialogModel = this.devices[i];
            this.model.selectedDevices.push(device.id ? device.id : device.tempId);
         }
      }

      const ambiguousDevices: ViewsheetDeviceLayoutDialogModel[] = this.getAmbiguousLayouts();

      if(ambiguousDevices.length > 0) {
         let message = "_#(js:layout.vsLayout.ambiguousSelection)\n";

         ambiguousDevices.forEach((ambiguous) => {
            message += `\n${ambiguous.name}: `;
            ambiguous.selectedDevices.forEach((deviceName, i) => {
               if(i > 0) {
                  message += ", ";
               }

               message += deviceName;
            });
         });

         ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)", message,
            {"yes": "_#(js:Yes)", "no": "_#(js:No)"})
            .then((buttonClicked) => {
               if(buttonClicked === "yes") {
                  this.onCommit.emit(this.model);
               }
            });
      }
      else {
         this.onCommit.emit(this.model);
      }
   }

   deviceTooltip(device: ScreenSizeDialogModel) {
      if(device.description) {
         return device.description;
      }

      return device.label + " " + device.minWidth + " - " + device.maxWidth;
   }

   /**
    * Check whether other existing layouts match the same devices with current one.
    */
   private getAmbiguousLayouts(): ViewsheetDeviceLayoutDialogModel[] {
      let otherLayouts: { size: Point, name: string, mobileOnly: boolean }[] = [];
      let duplicates: ViewsheetDeviceLayoutDialogModel[] = [];

      this.layouts.forEach((layout) => {
         if(this.model.name != layout.name) {
            otherLayouts.push({
               size: this.createLayoutBounds(layout),
               name: layout.name,
               mobileOnly: layout.mobileOnly
            });
         }
      });

      const size: Point = this.createLayoutBounds(this.model);

      otherLayouts.forEach((layout, i) => {
         let size2: Point = layout.size;
         let layoutDuplicates: ViewsheetDeviceLayoutDialogModel = {
            name: layout.name,
            mobileOnly: layout.mobileOnly,
            selectedDevices: [],
            id: null
         };

         layoutDuplicates.selectedDevices = this.devices
            .filter((device) =>
               this.isInLayoutBounds(new Point(device.minWidth, device.maxWidth), size) &&
               this.isInLayoutBounds(new Point(device.minWidth, device.maxWidth), size2))
            .map(device => device.label);

         if(layoutDuplicates.selectedDevices.length > 0) {
            duplicates.push(layoutDuplicates);
         }
      });

      return duplicates.filter((dup: ViewsheetDeviceLayoutDialogModel) => dup.mobileOnly === this.model.mobileOnly);
   }

   /**
    * Check whether two bounds have intersection
    */
   private isInLayoutBounds(deviceBounds: Point, layoutBounds: Point) {
      let result: boolean = true;

      if(deviceBounds.x > 0 && layoutBounds.y > 0) {
         result = (deviceBounds.x >= layoutBounds.x &&
            deviceBounds.x <= layoutBounds.y ||
            deviceBounds.y >= layoutBounds.x &&
            deviceBounds.y <= layoutBounds.y ||
            deviceBounds.x < layoutBounds.x &&
            deviceBounds.y > layoutBounds.y);
      }

      return result;
   }

   private createLayoutBounds(layout: ViewsheetDeviceLayoutDialogModel): Point {
      let layoutBounds: Point = new Point(Number.MAX_VALUE, Number.MIN_VALUE);

      layout.selectedDevices.forEach((selectedDevice) => {
         let device = this.devices.filter(
            (d) => d.id ? d.id == selectedDevice : d.tempId == selectedDevice)[0];
         layoutBounds.x = Math.min(layoutBounds.x, device.minWidth);
         layoutBounds.y = Math.max(layoutBounds.y, device.maxWidth);
      });

      return layoutBounds;
   }
}
