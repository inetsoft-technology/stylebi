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
import {
   ChangeDetectorRef,
   Component,
   EventEmitter,
   HostListener,
   ViewChild,
   ElementRef,
   Input,
   NgZone,
   AfterViewInit,
   OnDestroy,
   OnInit,
   Output
} from "@angular/core";
import { Dimension } from "../../common/data/dimension";
import { CalcTableLayout } from "../../common/data/tablelayout/calc-table-layout";
import { GuiTool } from "../../common/util/gui-tool";
import { LocalStorage } from "../../common/util/local-storage.util";
import { ViewsheetClientService, CommandProcessor } from "../../common/viewsheet-client";
import { AbstractVSActions } from "../../vsobjects/action/abstract-vs-actions";
import { AssemblyActionFactory } from "../../vsobjects/action/assembly-action-factory.service";
import { ViewsheetInfo } from "../../vsobjects/data/viewsheet-info";
import { BaseTableModel } from "../../vsobjects/model/base-table-model";
import { VSObjectModel } from "../../vsobjects/model/vs-object-model";
import { VSChartModel } from "../../vsobjects/model/vs-chart-model";
import { AddVSObjectCommand } from "../../vsobjects/command/add-vs-object-command";
import { RefreshVSObjectCommand } from "../../vsobjects/command/refresh-vs-object-command";
import { NgbModal, NgbModalOptions } from "@ng-bootstrap/ng-bootstrap";
import { ComponentTool } from "../../common/util/component-tool";
import { VsWizardEditModes } from "../../vs-wizard/model/vs-wizard-edit-modes";
import { VsWizardModel } from "../../vs-wizard/model/vs-wizard-model";
import { VSChart } from "../../vsobjects/objects/chart/vs-chart.component";
import { AbstractVSObject } from "../../vsobjects/objects/abstract-vsobject.component";
import { CalcTableLayoutPane } from "./vs-calc-table-layout.component";
import { MiniToolbarService } from "../../vsobjects/objects/mini-toolbar/mini-toolbar.service";

@Component({
   selector: "vs-object-view",
   templateUrl: "vs-object-view.component.html",
   styleUrls: ["vs-object-view.component.scss"]
})
export class VSObjectView extends CommandProcessor implements OnDestroy, OnInit, AfterViewInit {
   @Input() linkUri: string;
   @Input() variableValues: string[];
   actions: AbstractVSActions<any>;
   layoutModel: CalcTableLayout;
   variableValuesFunction: (objName: string) => string[] = (objName: string) => this.variableValues;
   @Output() onUpdateData = new EventEmitter<string>();
   @Output() chartMaxModeChange = new EventEmitter<{assembly: string, maxMode: boolean}>();
   @Output() onRefreshVSObjectCommand = new EventEmitter<RefreshVSObjectCommand>();
   @Output() onAddVSObjectCommand = new EventEmitter<AddVSObjectCommand>();
   @Output() onOpenWizardPane = new EventEmitter<VsWizardModel>();
   @Output() onOpenFormatPane = new EventEmitter<string>();
   @Output() onPopupNotifications: EventEmitter<any> = new EventEmitter<any>();

   _model: VSObjectModel;
   vsInfo: ViewsheetInfo;
   chartMaxMode: boolean = false;
   modelTS: number = 0;
   @ViewChild("objectView") objectView: ElementRef;
   @ViewChild("object") object: AbstractVSObject<VSObjectModel>;
   @ViewChild("calcObject") calcObject: CalcTableLayoutPane;

   constructor(private clientService: ViewsheetClientService,
               private actionFactory: AssemblyActionFactory,
               private changeDetectorRef: ChangeDetectorRef,
               private modalService: NgbModal,
               private miniToolbarService: MiniToolbarService,
               zone: NgZone)
   {
      super(clientService, zone, false);
   }

   ngOnInit() {
      this.vsInfo = new ViewsheetInfo([], this.linkUri, null);

      if(!!LocalStorage.getItem("chart-edit-max-mode")) {
         this.onChartMaxModeChange({assembly: this._model.absoluteName, maxMode: true});

         if(this._model.objectType == "VSChart") {
            (this._model as VSChartModel).maxMode = true;
         }
      }
   }

   ngOnDestroy(): void {
      super.cleanup();
   }

   get model(): VSObjectModel {
      return this._model;
   }

   @Input()
   set model(_vsObject: VSObjectModel) {
      if(!_vsObject) {
         return;
      }

      this._model = _vsObject;
      this.actions = this.actionFactory.createActions(this.model);

      if(this.objectView && this.objectView.nativeElement) {
         this.resizeModelView();
      }
   }

   getCalcTableLayout(calcTableLayout: CalcTableLayout): void {
      this.layoutModel = calcTableLayout;
   }

   onChartMaxModeChange($event: {assembly: string, maxMode: boolean}) {
      this.chartMaxMode = $event.maxMode;
      this.chartMaxModeChange.emit($event);
      LocalStorage.setItem("chart-edit-max-mode", this.chartMaxMode ? "true" : "");
   }

   onResize(event: any) {
      if(this.chartMaxMode && !!this.model && this.model.objectType == "VSChart" && !!this.object) {
         (<VSChart> this.object).openMaxMode();
      }
   }

   ngAfterViewInit() {
      this.resizeModelView();
   }

   //only resize vstable and vscrosstable
   public resizeModelView(): void {
      if(this.model.objectType == "VSTable" || this.model.objectType == "VSCrosstab" ||
         this.model.objectType == "VSCalcTable")
      {
         if(this.model.originalObjectFormat?.width != null && this.model.originalObjectFormat?.height != null) {
            this.model.objectFormat.width = this.model.originalObjectFormat.width;
            this.model.objectFormat.height = this.model.originalObjectFormat.height;
         }

         const viewSize = this.getViewSize(this.model.objectType);
         const objWidth = this.model.objectFormat.width;
         const objHeight = this.model.objectFormat.height;
         const model = <BaseTableModel> this.model;

         const totalHeight = (model.dataRowHeight + 0.4) * (model.rowCount + model.headerRowCount);
         const totalWidth = this.getTotalWidth(model.colWidths);

         if(this.model.originalObjectFormat?.width == null && this.model.originalObjectFormat?.height == null) {
            if(this.model.originalObjectFormat == null) {
               this.model.originalObjectFormat = {};
            }

            this.model.originalObjectFormat.width = this.model.objectFormat.width;
            this.model.originalObjectFormat.height = this.model.objectFormat.height;
         }

         if(!!viewSize) {
            if(this.model.objectType == "VSCalcTable") {
               this.model.objectFormat.width = viewSize.width;
               this.model.objectFormat.height = viewSize.height;
            }
            else {
               this.model.objectFormat.width = Math.min(Math.max(objWidth, totalWidth),
                  viewSize.width);
               this.model.objectFormat.height = Math.min(Math.max(objHeight, totalHeight),
                  viewSize.height);
            }
         }

         // force table change detection
         this.modelTS = new Date().getTime();
         this.changeDetectorRef.detectChanges();

         if(this.calcObject) {
            this.calcObject.detectChanges();
         }
      }
   }

   private getViewSize(objectType: String): Dimension {
      let scrollBarWidth = 50;

      if("VSCalcTable" == objectType) {
         scrollBarWidth = 25;
      }

      const rightContainer = <HTMLElement>
         GuiTool.closest(this.objectView.nativeElement, ".right-container");

      if(rightContainer == null) {
         return null;
      }

      const rightTopContainer = <HTMLElement> rightContainer.querySelector(".right-top-container");

      return new Dimension(rightContainer.offsetWidth - scrollBarWidth,
                           rightContainer.offsetHeight - rightTopContainer.offsetHeight - scrollBarWidth);
   }

   private getTotalWidth(colWidths: Array<number>): number {
      let width: number = 0;

      for(let i = 0; i < colWidths.length; i++) {
         width += colWidths[i];
      }

      return width;
   }

   @HostListener("window:resize")
   resizeListener(): void {
      this.resizeModelView();
   }

   @HostListener("click", ["$event"])
   getFormats(event: MouseEvent): void {
      setTimeout(() => {
         this.onUpdateData.emit("getCurrentFormat");
      }, 250);
   }

   public getAssemblyName(): string {
      if(!!this._model) {
         let idx: number = this._model.absoluteName.lastIndexOf(".");
         return idx == -1 ? null : this._model.absoluteName.substring(0, idx);
      }

      return null;
   }

   showMiniToolbar(): boolean {
      return !this.miniToolbarService?.isMiniToolbarHidden(this.model.absoluteName);
   }

   onMouseEnter(event: any): void {
      this.miniToolbarService.handleMouseEnter(this.model?.absoluteName, event);
   }

   contextmenuOpened() {
      this.miniToolbarService.hiddenFreeze(this.model?.absoluteName);
   }

   contextmenuClosed() {
      this.miniToolbarService.hiddenUnfreeze(this.model?.absoluteName);
   }

   private processRefreshVSObjectCommand(command: RefreshVSObjectCommand): void {
      this.onRefreshVSObjectCommand.emit(command);
   }

   private processAddVSObjectCommand(command: AddVSObjectCommand): void {
      this.onAddVSObjectCommand.emit(command);
   }
}
