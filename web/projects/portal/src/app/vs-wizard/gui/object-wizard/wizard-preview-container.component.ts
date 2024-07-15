/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { Component, ElementRef, EventEmitter, Input, Output, ViewChild } from "@angular/core";
import { AssemblyActionEvent } from "../../../common/action/assembly-action-event";
import { VSObjectFormatInfoModel } from "../../../common/data/vs-object-format-info-model";
import { AbstractActionComponent } from "../../../composer/gui/vs/editor/abstract-action-component";
import { AssemblyActionFactory } from "../../../vsobjects/action/assembly-action-factory.service";
import { ViewsheetInfo } from "../../../vsobjects/data/viewsheet-info";
import { VSChartModel } from "../../../vsobjects/model/vs-chart-model";
import { VSObjectModel } from "../../../vsobjects/model/vs-object-model";
import { MiniToolbarService } from "../../../vsobjects/objects/mini-toolbar/mini-toolbar.service";
import { VsWizardEditModes } from "../../model/vs-wizard-edit-modes";
import { ScaleService } from "../../../widget/services/scale/scale-service";
import { ConsoleMessage } from "../../../widget/console-dialog/console-message";
import { NgbModal, NgbModalOptions } from "@ng-bootstrap/ng-bootstrap";
import { Tool } from "../../../../../../shared/util/tool";
import { ConsoleDialogComponent } from "../../../widget/console-dialog/console-dialog.component";
import { ModelService } from "../../../widget/services/model.service";

const GET_MESSAGE_LEVELS_URI = "../api/composer/console-dialog/get-message-levels/";

@Component({
   selector: "wizard-preview-container",
   templateUrl: "./wizard-preview-container.component.html",
   styleUrls: ["./wizard-preview-container.component.scss"]
})
export class WizardPreviewContainer extends AbstractActionComponent {
   vsObject: VSObjectModel;
   @Input() runtimeId: string;
   @Input() linkuri: string;
   @Input() formatMap: Map<string, VSObjectFormatInfoModel>;
   @Input() editMode: VsWizardEditModes = VsWizardEditModes.WIZARD_DASHBOARD;
   @Input() showLegend: boolean;
   @Input() vsInfo: ViewsheetInfo;
   @Input() description: string;
   @Input() consoleMessages: ConsoleMessage[] = [];

   @Output() onDescriptionChange = new EventEmitter<string>();
   @Output() onUpdateFormat: EventEmitter<string> = new EventEmitter<string>();
   @Output() showLegendChange = new EventEmitter<boolean>();
   @Output() onMessageChange = new EventEmitter<ConsoleMessage[]>();

   @ViewChild("assemblyObject") assemblyObject: ElementRef;
   @ViewChild("wizardPreviewContainer") wizardPreviewContainer: ElementRef;
   @ViewChild("consoleDialog") consoleDialog: ConsoleDialogComponent;

   onAssemblyActionEvent = new EventEmitter<AssemblyActionEvent<VSObjectModel>>();
   messageLevels: string[] = [];

   constructor(actionFactory: AssemblyActionFactory,
               private scaleService: ScaleService,
               private miniToolbarService: MiniToolbarService,
               protected modalService: NgbModal,
               private modelService: ModelService)
   {
      super(actionFactory);
   }

   @Input()
   set vsObjectModel(_vsObject: VSObjectModel) {
      this.vsObject = _vsObject;
      // viewsheet for group/ungroup.
      this.updateActions(this.vsObject, null);
   }

   get showTopDescription(): boolean {
      /*
      return this.vsObject &&
         (this.vsObject.objectType == "VSGauge" ||
          this.vsObject.objectType == "VSRangeSlider" );
      */
      // @by larryl, adding external title/label feels unpolished. seems static text
      // should be sufficient. leave the code in-place for now and will reevaluate after
      // more usage (13.2).
      return false;
   }

   get showLeftDescription(): boolean {
      //return !!this.vsObject && this.vsObject.objectType == "VSText";
      return false;
   }

   get previewPaneWidth() {
      if(!this.vsObject) {
         return 0;
      }

      return this.showLeftDescription ? this.vsObject.objectFormat.width + 150 :
         this.vsObject.objectFormat.width;
   }

   public isMiniToolbarVisible(): boolean {
      return this.miniToolbarService.isMiniToolbarVisible(this.vsObject);
   }

   public toolbarForceHidden(): boolean {
      return this.miniToolbarService.isMiniToolbarHidden(this.vsObject?.absoluteName);
   }

   get miniToolbarWidth() {
      return this.miniToolbarService.getToolbarWidth(this.vsObject, null,
                                                     this.scaleService.getCurrentScale(),
                                                     0, true);
   }

   public hasMiniToolbar(): boolean {
      return this.miniToolbarService.hasMiniToolbar(this.vsObject);
   }

   get searchDisplayed(): boolean {
      const obj = this.vsObject as any;
      return !!obj && !!obj.searchDisplayed;
   }

   get showLegendCheckbox(): boolean {
      return this.vsObject.objectType == "VSChart" && (<VSChartModel> this.vsObject).hasLegend;
   }

   changeDescription(): void {
      this.onDescriptionChange.emit(this.description);
   }

   get miniToolbarLeft(): number {
      let objectOffsetLeft = 0;

      if(!!this.assemblyObject && !!this.wizardPreviewContainer) {
         objectOffsetLeft = this.assemblyObject.nativeElement.getBoundingClientRect().left
            - this.wizardPreviewContainer.nativeElement.getBoundingClientRect().left;
      }

      return objectOffsetLeft;
   }

   get miniToolbarTop(): number {
      let objectOffsetTop = 0;

      // don't cover 'Show Legend'
      if(this.showLegendCheckbox) {
         objectOffsetTop += 60;
      }

      return objectOffsetTop;
   }

   resetToolbarVisible(event: any) {
      this.miniToolbarService.handleMouseEnter(this.vsObject?.absoluteName, event);
   }

   openConsoleDialog(): void {
      const options: NgbModalOptions = {
         backdrop: "static",
         windowClass: "console-dialog"
      };

      this.modelService.getModel(GET_MESSAGE_LEVELS_URI + Tool.byteEncodeURLComponent(this.runtimeId))
         .subscribe((res: any) => {
            this.messageLevels = res;
            this.modalService.open(this.consoleDialog, options).result
               .then((messageLevels: string[]) => {
                  this.messageLevels = messageLevels;
               }, () => {});
         });
   }

   changeMessage(messages: ConsoleMessage[]) {
      this.consoleMessages = messages;
      this.onMessageChange.emit(this.consoleMessages);
   }
}
