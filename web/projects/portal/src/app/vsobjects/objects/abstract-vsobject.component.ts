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
import { ElementRef, EventEmitter, Input, NgZone, OnDestroy, Output, ViewChild, Directive } from "@angular/core";
import { GuiTool } from "../../common/util/gui-tool";
import { CommandProcessor, ViewsheetClientService } from "../../common/viewsheet-client";
import { AbstractVSActions } from "../action/abstract-vs-actions";
import { ContextProvider } from "../context-provider.service";
import { ViewsheetInfo } from "../data/viewsheet-info";
import { AddAnnotationEvent } from "../event/annotation/add-annotation-event";
import { VSAnnotationModel } from "../model/annotation/vs-annotation-model";
import { VSObjectModel } from "../model/vs-object-model";
import { DataTipService } from "./data-tip/data-tip.service";
import { VsWizardEditModes } from "../../vs-wizard/model/vs-wizard-edit-modes";
import { VsWizardModel } from "../../vs-wizard/model/vs-wizard-model";
import { AssemblyLoadingCommand } from "../command/assembly-loading-command";
import { ClearAssemblyLoadingCommand } from "../command/clear-assembly-loading-command";
import { VSChartModel } from "../model/vs-chart-model";
import { BaseTableModel } from "../model/base-table-model";
import { Rectangular } from "../../common/data/rectangle";
import { DateTipHelper } from "./data-tip/date-tip-helper";

/**
 * Abstract base class for all vsobject components.
 */
@Directive()
export abstract class AbstractVSObject<T extends VSObjectModel> extends CommandProcessor
   implements OnDestroy
{
   @Input() vsInfo: ViewsheetInfo;
   @Input() printLayout: boolean = false;
   @Input() embeddedVS: boolean = false;
   @Input() embeddedVSBounds: Rectangular;
   @Input() popupShowing: boolean = false;
   @Output() removeAnnotations = new EventEmitter<void>();
   @Output() public onOpenWizardPane = new EventEmitter<VsWizardModel>();
   protected _model: T;
   mobileDevice: boolean = GuiTool.isMobileDevice();
   @ViewChild("objectContainer") objectContainer: ElementRef;
   loadingCount: number = 0;

   constructor(protected viewsheetClient: ViewsheetClientService,
               zone: NgZone,
               protected context: ContextProvider,
               protected dataTipService: DataTipService,
               handleGlobal: boolean = false)
   {
      super(viewsheetClient, zone, handleGlobal);
   }

   ngOnDestroy(): void {
      this.cleanup();
   }

   get viewer(): boolean {
      return this.context.viewer || this.context.preview;
   }

   get vsWizard(): boolean {
      return this.context.vsWizard;
   }

   get vsWizardPreview(): boolean {
      return this.context.vsWizardPreview;
   }

   get model(): T {
      return this._model;
   }

   @Input() set model(m: T) {
      this._model = m;
   }

   get sourceType(): number {
      return this.model?.sourceType ?? -1;
   }

   getVisible(): boolean {
      if(this.context.embed) {
         return true;
      }

      if(this.viewer) {
         if(this.dataTipService.isDataTip(this.getAssemblyName())) {
            return false;
         }

         if(this.model.container && this.dataTipService.isDataTip(this.model.container)) {
            return false;
         }

         let maxObj = this.vsInfo?.vsObjects.find(v => v["maxMode"]);

         if(maxObj && this.model.container && this.model.container == maxObj.absoluteName) {
            return true;
         }
      }

      return !this.viewer && !this.embeddedVS || this.model.visible &&
         (!!this.model.container && this.model.active || !this.model.container);
   }

   get enableAnnotation(): boolean {
      return this.viewer && !this.context.binding && !this.model["maxMode"];
   }

   getAssemblyName(): string {
      return this._model ? this._model.absoluteName : null;
   }

   /**
    * Retrieve all the annotations for a component
    */
   public get allAnnotations(): VSAnnotationModel[] {
      return [].concat(this.model.dataAnnotationModels, this.model.assemblyAnnotationModels);
   }

   public selectAnnotation(payload: [VSAnnotationModel, MouseEvent]): void {
      const [model, event] = payload;
      if((event.ctrlKey || event.button === 2) && this.model.selectedAnnotations) {
         const currentIndex = this.model.selectedAnnotations.indexOf(model.absoluteName);

         if(currentIndex === -1) {
            this.model.selectedAnnotations.push(model.absoluteName);
         }
      }
      else {
         this.model.selectedAnnotations = [model.absoluteName];
      }
   }

   public isAnnotationSelected(model: VSAnnotationModel): boolean {
      return model && this.model.selectedAnnotations &&
         this.model.selectedAnnotations.indexOf(model.absoluteName) > -1;
   }

   public removeAnnotation(model: VSAnnotationModel): void {
      this.removeAnnotations.emit();
      this.model.selectedAnnotations =
         this.model.selectedAnnotations.filter((name) => name !== model.absoluteName);
   }

   isDataTip(): boolean {
      return this.dataTipService.isDataTip(this.getAssemblyName());
   }

   get inContainer(): boolean {
      return this.model.containerType == "VSSelectionContainer" ||
         this.model.containerType == "VSTab";
   }

   get inSelectedGroupContainer(): boolean {
      return this.model.containerType == "VSGroupContainer" && this.model.container &&
         this.vsInfo.isAssemblyFocused(this.model.container);
   }

   /**
    * Check if an action is visible
    */
   public isActionVisible(name: string): boolean {
      return AbstractVSActions.isActionVisible(this.model.actionNames, name);
   }

   public isActionVisibleInViewer(name: string): boolean {
      return !this.viewer || this.isActionVisible(name);
   }

   public trackByIdx(index, item) {
      return index;
   }

   // For use in ngFor to prevent excessive DOM manipulation due to component creation/destruction.
   // The child views will still be checked for changes.
   public trackByConstant(index, item) {
      return 0;
   }

   /**
    * Add an assembly level annotation to this component
    */
   protected addAnnotation(content: string, event: MouseEvent): void {
      const container = this.objectContainer.nativeElement.getBoundingClientRect();
      const x = event.clientX - container.left;
      const y = event.clientY - container.top;
      const annotateEvent = new AddAnnotationEvent(content, x, y, this.model.absoluteName);
      this.viewsheetClient.sendEvent("/events/annotation/add-assembly-annotation", annotateEvent);
   }

   protected getAnnotationMarkup(str: string): string {
      return str.replace(/[\r]/g, "")
         .split("\n")
         .map(line => `<p>${line}</p>`)
         .join("");
   }

   // called when the obj is being resized
   public resized(): void {
   }

   protected openWizardPane(editMode: VsWizardEditModes): void {
      this.onOpenWizardPane.emit({
         runtimeId: this.viewsheetClient.runtimeId,
         linkUri: this.vsInfo.linkUri,
         editMode: editMode,
         oinfo: {
            runtimeId: this.viewsheetClient.runtimeId,
            editMode: editMode,
            objectType: this.model.objectType,
            absoluteName: this.model.absoluteName
         },
         objectModel: this.model,
         viewer: this.viewer
      });
   }

   get isLoading(): boolean {
      return this.loadingCount > 0;
   }

   protected processAssemblyLoadingCommand(command: AssemblyLoadingCommand) {
      this.loadingCount += (command ? command.count : 1);
      this.detectChanges();
   }

   protected processClearAssemblyLoadingCommand(command: ClearAssemblyLoadingCommand) {
      this.loadingCount = Math.max(0, this.loadingCount - (command ? command.count : 1));
      this.detectChanges();
   }

   protected detectChanges(): void {
   }

   // clear selections on other table/chart
   protected clearOtherSelections() {
      this.vsInfo.vsObjects.filter(v => v.absoluteName != this.model.absoluteName)
         .forEach(v => {
            if("clearCanvasSubject" in v) {
               if(!(v as VSChartModel).sendingFlyover) {
                  (<VSChartModel>v).chartSelection.regions = [];
                  (<VSChartModel>v).clearCanvasSubject.next(true);
               }

               (<VSChartModel> v).lastFlyover = null;
            }
            else if("selectedData" in v) {
               (<BaseTableModel> v).selectedRegions = [];
               (<BaseTableModel> v).selectedHeaders = null;
               (<BaseTableModel> v).selectedData = null;
            }
         });
   }

   protected isPopupOrDataTipSource(): boolean {
      return false;
   }

   protected getZIndex(): number {
      if(this.isPopupOrDataTipSource()) {
         return DateTipHelper.getPopUpSourceZIndex();
      }

      return this.viewer ? this.model.objectFormat.zIndex : null;
   }
}
