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
import {
   ChangeDetectorRef,
   Component,
   ElementRef,
   EventEmitter,
   Input,
   NgZone,
   OnDestroy,
   OnInit,
   Output,
   TemplateRef,
   ViewChild
} from "@angular/core";
import { NgbModalOptions } from "@ng-bootstrap/ng-bootstrap";
import { Subscription } from "rxjs";
import { AssemblyActionGroup } from "../../../../common/action/assembly-action-group";
import { Tool } from "../../../../../../../shared/util/tool";
import { ViewsheetClientService } from "../../../../common/viewsheet-client";
import { ViewsheetInfo } from "../../../../vsobjects/data/viewsheet-info";
import { VSLineModel } from "../../../../vsobjects/model/vs-line-model";
import { VSObjectModel } from "../../../../vsobjects/model/vs-object-model";
import { VSSelectionContainerModel } from "../../../../vsobjects/model/vs-selection-container-model";
import { DebounceService } from "../../../../widget/services/debounce.service";
import { ModelService } from "../../../../widget/services/model.service";
import { DialogService } from "../../../../widget/slide-out/dialog-service.service";
import { ImagePropertyDialogModel } from "../../../data/vs/image-property-dialog-model";
import { TextPropertyDialogModel } from "../../../data/vs/text-property-dialog-model";
import { Viewsheet } from "../../../data/vs/viewsheet";
import { VSLayoutModel } from "../../../data/vs/vs-layout-model";
import { VSLayoutObjectModel } from "../../../data/vs/vs-layout-object-model";
import { ComposerObjectService, KeyEventAdapter } from "../composer-object.service";
import { MoveResizeLayoutObjectsEvent } from "../event/move-resize-layout-objects-event";
import { Dimension } from "../../../../common/data/dimension";
import { TableLayoutPropertyDialogModel } from "../../../data/vs/table-layout-property-dialog-model";
import { ComposerVsSearchService } from "../composer-vs-search.service";

const TEXT_PROPERTY_URI: string = "composer/vs/layouts/text-property-dialog/";
const IMAGE_PROPERTY_URI: string = "composer/vs/layouts/image-property-dialog/";
const TABLE_LAYOUT_PROPERTY_URI: string = "composer/vs/layouts/table-layout-property-dialog/";

@Component({
   selector: "layout-object",
   templateUrl: "layout-object.component.html",
   styleUrls: ["layout-object.component.scss"],
})
export class LayoutObject implements OnInit, OnDestroy {
   @Input() model: VSLayoutObjectModel;
   @Input() runtimeId: string;
   @Input() viewsheetScale: number;
   @Input() linkUri: string;
   @Input() snapToGrid: boolean;
   @Input() containerRef: HTMLElement;
   @Input() guideSize: Dimension;
   @Output() onRemoveSelected = new EventEmitter<any>();
   @Output() onResize = new EventEmitter<{event: any, model: VSLayoutObjectModel}>();
   @Output() onLayoutObjectMove = new EventEmitter<any>();
   public layoutSection: string = "CONTENT";
   public selected: boolean = false;
   public vsInfo: ViewsheetInfo;
   public dbclick: boolean = false;

   @ViewChild("textPropertyDialog") textPropertyDialog: TemplateRef<any>;
   @ViewChild("imagePropertyDialog") imagePropertyDialog: TemplateRef<any>;
   @ViewChild("tableLayoutPropertyDialog") tableLayoutPropertyDialog: TemplateRef<any>;

   textPropertyModel: TextPropertyDialogModel;
   imagePropertyModel: ImagePropertyDialogModel;
   tableLayoutDialogPropertyModel: TableLayoutPropertyDialogModel;

   private focusedObjectsSubject: Subscription;
   private _layout: VSLayoutModel;
   private keyEventAdapter: KeyEventAdapter;

   public menuActions: AssemblyActionGroup[] = [
      new AssemblyActionGroup([
         {
            id: () => "composer viewsheet layout properties",
            label: () => "_#(js:Properties)...",
            icon: () => "fa fa-slider",
            enabled: () => true,
            visible: () => this.model.editable,
            action: () => this.openPropertiesDialog()
         },
         {
            id: () => "composer viewsheet layout properties",
            label: () => "_#(js:Remove)",
            icon: () => "close-icon",
            enabled: () => true,
            visible: () => true,
            action: () => this.removeObject()
         },
         {
            id: () => "composer viewsheet table flow control",
            label: () => "_#(js:Table Flow Control)...",
            icon: () => "fa fa-slider",
            enabled: () => true,
            visible: () => this.model.supportTableLayout && this._layout.printLayout,
            action: () => this.openTableLayoutPropertyDialog()
         }
      ])
   ];

   get lineModel(): VSLineModel {
      return !!this.model ? (this.model.objectModel as VSLineModel) : null;
   }

   constructor(private element: ElementRef,
               private socket: ViewsheetClientService,
               private changeDetectorRef: ChangeDetectorRef,
               private modelService: ModelService,
               private zone: NgZone,
               private modalService: DialogService,
               private debounceService: DebounceService,
               private composerObjectService: ComposerObjectService,
               private composerVsSearchService: ComposerVsSearchService)
   {
      this.keyEventAdapter = {
         getElement: () => this.element,
         getViewsheet: () => <Viewsheet> {
            get isFocused(): boolean { return true; }},
         getVsObject: () => this.model.objectModel,
         isSelected: () => this.selected,
      };
      this.composerObjectService.addLayoutObjectKeyEventAdapter(this.keyEventAdapter);
   }

   ngOnInit() {
      this.vsInfo = new ViewsheetInfo([], this.linkUri, false, this.runtimeId);
   }

   ngOnDestroy() {
      this.changeDetectorRef.detach();
      this.focusedObjectsSubject.unsubscribe();
   }

   test() {
      this.changeDetectorRef.detectChanges();
   }

   @Input()
   set layout(value: VSLayoutModel) {
      this._layout = value;
      this.focusedObjectsSubject = this.layout.focused.subscribe(() => {
         this.selected = this.layout.isObjectFocused(this.model);
         this.dbclick = false;
      });

      this.layoutSection = value.getLayoutSection();
   }

   get layout(): VSLayoutModel {
      return this._layout;
   }

   get searchVisible(): boolean {
      return this.isSearchMatched(this.model);
   }

   public onDragMove(event: any) {
      let pageBreak = this.model.objectModel.objectType == "VSPageBreak";
      const scale: number = this.viewsheetScale ? 1 / this.viewsheetScale : 1;
      let dy: number = event.dy * scale;
      let dx: number = event.dx * scale;
      const newLeft = pageBreak ? this.model.left : this.model.left + dx;
      const newTop = this.model.top + dy;

      this.model.left = Math.max(0, newLeft);
      this.model.top = Math.max(0, newTop);

      if(this.model.objectModel) {
         this.model.objectModel.objectFormat.left = this.model.left;
         this.model.objectModel.objectFormat.top = this.model.top;

         for(let childModel of this.model.childModels) {
            childModel.objectFormat.left += newLeft >= 0 ? dx : 0;
            childModel.objectFormat.top += newTop >= 0 ? dy : 0;
         }
      }

      this.changeDetectorRef.detectChanges();
      this.onLayoutObjectMove.emit(this.model);
   }

   public onDragEnd() {
      this.updateDimensions();
   }

   public onResizeStart() {
      //When resizing, deselect all other items.
      this.layout.clearFocusedObjects();
      this.layout.selectObject(this.model);
   }

   public onResizeMove(event: any) {
      const deltaWidth: number = event.deltaRect.left == 0 ?
         event.deltaRect.right : -event.deltaRect.left;
      const deltaHeight: number = event.deltaRect.top == 0 ?
         event.deltaRect.bottom : -event.deltaRect.top;
      this.model.width += deltaWidth;
      this.model.height += deltaHeight;

      // Move when resizing from top or left edges
      const newLeft = this.model.left + event.deltaRect.left;
      const newTop = this.model.top + event.deltaRect.top;
      this.model.left = Math.max(0, newLeft);
      this.model.top = Math.max(0, newTop);

      if(this.model.objectModel) {
         this.model.objectModel.objectFormat.width = this.model.width;
         this.model.objectModel.objectFormat.height = this.model.height;
         this.model.objectModel.objectFormat.left = this.model.left;
         this.model.objectModel.objectFormat.top = this.model.top;
      }

      this.changeDetectorRef.detectChanges();
      this.onResize.emit({event: event, model: this.model});
   }

   public onResizeEnd() {
      this.updateDimensions();
      this.onResize.emit(null);
   }

   select(event: MouseEvent): void {
      if(!this.selected) {
         if(!event.ctrlKey && !event.metaKey && !event.shiftKey) {
            this.layout.clearFocusedObjects();
         }

         this.layout.selectObject(this.model);
      }
      else if(this.selected && (event.ctrlKey || event.metaKey)) {
         this.layout.deselectAssembly(this.model);
      }
   }

   transformChildLineModel(model: VSObjectModel): VSObjectModel {
      if(model.objectType == "VSLine") {
         return this.transformLineModel(<VSLineModel> model);
      }
      else {
         return model;
      }
   }

   transformLineModel(model: VSLineModel): VSLineModel {
      let _model = Tool.clone(model);
      _model.objectFormat.top = 0;
      _model.objectFormat.left = 0;
      return _model;
   }

   removeSpaces(str: string): string {
      return str ? (str.replace(/ /g, "-")) : "";
   }

   private updateDimensions(): void {
      const key = `layout-object-updateDimensions::${this.layout.name}:${this.model.name}`;
      this.debounceService.debounce(key, () => this.updateDimensions0(), 200, []);
   }

   private updateDimensions0(): void {
      let event: MoveResizeLayoutObjectsEvent = new MoveResizeLayoutObjectsEvent(
         this.layout.name, [this.model.name], [this.model.left], [this.model.top],
         [this.model.width], [this.model.height]);
      event.region = this.layout.currentPrintSection;

      this.layout.socketConnection.sendEvent("/events/composer/vs/layouts/moveResizeObjects",
         event);
   }

   private removeObject(): void {
      this.layout.selectObject(this.model);
      this.onRemoveSelected.emit();
   }

   private openTableLayoutPropertyDialog(): void {
      this.tableLayoutDialogPropertyModel = {
         tableLayout: this.model.tableLayout
      };

      this.modalService.open(this.tableLayoutPropertyDialog, {windowClass: "property-dialog-window"}).result.then(
          (result: TableLayoutPropertyDialogModel) => {
             this.model.tableLayout = result.tableLayout;
             this.tableLayoutDialogPropertyModel = null;
             const eventUri: string = "/events/" + TABLE_LAYOUT_PROPERTY_URI + "/"
                 + this.layout.currentPrintSection + "/" + this.model.objectModel.absoluteName;

             this.socket.sendEvent(eventUri, result);
          },
          (reason: string) => {
             this.tableLayoutDialogPropertyModel = null;
          }
      );
   }

   private openPropertiesDialog(): void {
      if(this.model.objectModel.objectType == "VSText") {
         this.openTextPropertiesDialog();
      }
      else {
         this.openImagePropertiesDialog();
      }
   }

   private openTextPropertiesDialog(): void {
      const modelUri: string = "../api/" + TEXT_PROPERTY_URI + this.layout.currentPrintSection
         + "/" + this.model.objectModel.absoluteName + "/" + Tool.encodeURIPath(this.runtimeId);

      this.modelService.getModel(modelUri).toPromise().then(
         (data: any) => {
            this.textPropertyModel = <TextPropertyDialogModel> data;
            const options: NgbModalOptions = {
               windowClass: "property-dialog-window"
            };

            this.modalService.open(this.textPropertyDialog, options).result.then(
               (result: TextPropertyDialogModel) => {
                  this.textPropertyModel = null;
                  const eventUri: string = "/events/" + TEXT_PROPERTY_URI + "/"
                     + this.layout.currentPrintSection + "/" + this.model.objectModel.absoluteName;

                  this.socket.sendEvent(eventUri, result);
               },
               (reason: string) => {
                  this.textPropertyModel = null;
               }
            );
         },
         (error: any) => {
            //TODO handle error
            console.error("Failed to load text property model: ", error);
         }
      );
   }

   private openImagePropertiesDialog(): void {
      const modelUri: string = "../api/" + IMAGE_PROPERTY_URI + this.layout.currentPrintSection
         + "/"  + this.model.objectModel.absoluteName + "/" + Tool.encodeURIPath(this.runtimeId);

      this.modelService.getModel(modelUri).toPromise().then(
         (data: any) => {
            this.imagePropertyModel = <ImagePropertyDialogModel> data;
            const options: NgbModalOptions = {
               windowClass: "property-dialog-window"
            };

            this.modalService.open(this.imagePropertyDialog, options).result.then(
               (result: ImagePropertyDialogModel) => {
                  this.imagePropertyModel = null;
                  const eventUri: string = "/events/" + IMAGE_PROPERTY_URI + "/"
                     + this.layout.currentPrintSection + "/" + this.model.objectModel.absoluteName;

                  this.socket.sendEvent(eventUri, result);
               },
               (reason: string) => {
                  this.imagePropertyModel = null;
               }
            );
         },
         (error: any) => {
            //TODO handle error
            console.error("Failed to load image property model: ", error);
         }
      );
   }

   public get showSelectionChildren(): boolean {
      return !!this.model.objectModel &&
         this.model.objectModel.objectType == "VSSelectionContainer" &&
         (!!(<VSSelectionContainerModel> this.model.objectModel).childObjects ||
         !!(<VSSelectionContainerModel> this.model.objectModel).outerSelections);
   }

   isHLine(): boolean {
      return this.model.objectModel &&
         this.model.objectModel.objectType == "VSLine" &&
         this.lineModel.startTop == this.lineModel.endTop;
   }

   isVLine(): boolean {
      return this.model.objectModel &&
         this.model.objectModel.objectType == "VSLine" &&
         this.lineModel.startLeft == this.lineModel.endLeft;
   }

   get resizeable(): boolean {
      return this.selected && !this.dbclick && this.model?.objectModel?.objectType != "VSPageBreak";
   }

   get isSearchResults(): boolean {
      return this.composerVsSearchService.isSearchMode() && !!this.composerVsSearchService.searchString &&
         this.composerVsSearchService.matchName(this.model.name);
   }

   get searchMode(): boolean {
      return this.composerVsSearchService.isSearchMode() && !!this.composerVsSearchService.searchString;
   }

   get isSearchFocus(): boolean {
      return this.composerVsSearchService.isFocusAssembly(this.model?.name);
   }

   private isSearchMatched(obj: VSLayoutObjectModel): boolean {
      if(!obj) {
         return true;
      }

      if(this.composerVsSearchService.isSearchMode() && this.composerVsSearchService.searchString) {
         if(this.composerVsSearchService.matchName(obj.name)) {
            return true;
         }

         return false;
      }

      return true;
   }

   isTabLineOrCalendar(): boolean {
      return this.model?.objectModel?.objectType == "VSTab" || this.model?.objectModel?.objectType == "VSCalendar" ||
         this.model?.objectModel.objectType == "VSLine";
   }
}
