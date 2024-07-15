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
import {
   ChangeDetectorRef,
   Component,
   ElementRef,
   EventEmitter,
   HostListener,
   Input,
   NgZone,
   OnChanges,
   OnDestroy,
   OnInit,
   Output,
   Renderer2,
   SimpleChanges,
   ViewChild
} from "@angular/core";
import { Observable, Subscription } from "rxjs";
import { Tool } from "../../../../../../../shared/util/tool";
import { AssemblyActionGroup } from "../../../../common/action/assembly-action-group";
import { Dimension } from "../../../../common/data/dimension";
import { Rectangle } from "../../../../common/data/rectangle";
import { GuiTool } from "../../../../common/util/gui-tool";
import { CommandProcessor, ViewsheetClientService } from "../../../../common/viewsheet-client";
import { SetCurrentFormatCommand } from "../../../../vsobjects/command/set-current-format-command";
import { UpdateLayoutUndoStateCommand } from "../../../../vsobjects/command/update-layout-undo-state-command";
import { UpdateUndoStateCommand } from "../../../../vsobjects/command/update-unto-state-command";
import { GetVSObjectFormatEvent } from "../../../../vsobjects/event/get-vs-object-format-event";
import { OpenViewsheetEvent } from "../../../../vsobjects/event/open-viewsheet-event";
import { LoadTableDataEvent } from "../../../../vsobjects/event/table/load-table-data-event";
import { GuideBounds } from "../../../../vsobjects/model/layout/guide-bounds";
import { PrintLayoutSection } from "../../../../vsobjects/model/layout/print-layout-section";
import { VSViewsheetModel } from "../../../../vsobjects/model/vs-viewsheet-model";
import { VSUtil } from "../../../../vsobjects/util/vs-util";
import { SelectionBoxEvent } from "../../../../widget/directive/selection-box.directive";
import { DebounceService } from "../../../../widget/services/debounce.service";
import { Viewsheet } from "../../../data/vs/viewsheet";
import { PrintLayoutMeasures, VSLayoutModel } from "../../../data/vs/vs-layout-model";
import { VSLayoutObjectModel } from "../../../data/vs/vs-layout-object-model";
import { TouchAssetEvent } from "../../ws/socket/touch-asset-event";
import { AssemblyType } from "../assembly-type";
import { AddLayoutObjectCommand } from "../command/add-layout-object-command";
import { RemoveLayoutObjectsCommand } from "../command/remove-layout-objects-command";
import { ComposerObjectService } from "../composer-object.service";
import { AddVSLayoutObjectEvent } from "../event/add-vs-layout-object-event";
import { MoveResizeLayoutObjectsEvent } from "../event/move-resize-layout-objects-event";
import { RemoveVSLayoutObjectEvent } from "../event/remove-vs-layout-object-event";
import { RefreshLayoutObjectsCommand } from "../command/refresh-layout-objects-comman";
import { InteractContainerDirective } from "../../../../widget/interact/interact-container.directive";
import { ResizeHandlerService } from "../../resize-handler.service";

@Component({
   selector: "layout-pane",
   templateUrl: "layout-pane.component.html",
   styleUrls: ["layout-pane.component.scss"],
   providers: [ViewsheetClientService]
})
export class LayoutPane extends CommandProcessor implements OnInit, OnChanges, OnDestroy {
   @Input() layoutChange: Observable<any>;
   @Input() runtimeId: string;
   @Input() vs: Viewsheet;
   @Input() linkUri: string;
   @Input() snapOffset: number = 2;
   @Input() snapToGrid: boolean = false;
   @Input() snapToObjects: boolean = false;
   @Input() vsPaneBounds: ClientRect;
   @Input() guideLineColor: string;
   @Output() onLayoutObjectChange: EventEmitter<any> = new EventEmitter<any>();
   @Output() onUpdateLayoutUndoState = new EventEmitter<UpdateLayoutUndoStateCommand>();
   @Output() onLayoutObjectMove = new EventEmitter<any>();
   @ViewChild("layoutPane", {static: true}) layoutPane: ElementRef;
   @ViewChild(InteractContainerDirective) interactContainer: InteractContainerDirective;
   vsLayout: VSLayoutModel;
   _guideSize: Dimension = new Dimension(0, 0);
   _layoutRegion: string = "CONTENT";
   pages: number[];
   draggableSnapGuides: {horizontal: number[], vertical: number[]} = {horizontal: [], vertical: []};
   currentSnapGuides: {x: number, y: number} = null;
   private scrollbarWidth: number = GuiTool.measureScrollbars();
   private inited: boolean = false;
   private subscriptions = new Subscription();
   private snapSubscription: Subscription;
   private focusedObjectSubscription: Subscription;
   layoutScrollTop: number = 0;
   layoutScrollLeft: number = 0;
   private resizeTimeout: any = null;
   private click: boolean = false;

   // Height taken from Flash
   PANE_HEIGHT: number = 5436;
   private draggableRestrictionRects: Map<any, {left: number, top: number, right: number, bottom: number}>;

   draggableRestriction = (x: number, y: number, element: any) => {
      if(!this.draggableRestrictionRects) {
         this.draggableRestrictionRects =
            new Map<any, {left: number, top: number, right: number, bottom: number}>();
      }

      let draggableRestrictionRect = this.draggableRestrictionRects.get(element);

      if(!draggableRestrictionRect) {
         const containerElement = this.layoutPane.nativeElement;
         const containerRect = GuiTool.getElementRect(containerElement);
         const elementRect = GuiTool.getElementRect(element);
         let offsetX = 0;
         let offsetY = 0;

         if(this.vsLayout.focusedObjects.length > 1) {
            this.vsLayout.focusedObjects
               .forEach((layoutObject) => {
                  const assemblyElement = containerElement.querySelector(
                     `.object-editor[data-name='${layoutObject.name}']`);

                  if(assemblyElement) {
                     const assemblyRect = GuiTool.getElementRect(assemblyElement);
                     offsetX = Math.max(offsetX, elementRect.left - assemblyRect.left);
                     offsetY = Math.max(offsetY, elementRect.top - assemblyRect.top);
                  }
               });
         }

         // offset for the selection border
         const selBorderSize = 2;

         draggableRestrictionRect = {
            top: containerRect.top + offsetY - selBorderSize,
            left: containerRect.left + offsetX - selBorderSize,
            bottom: containerRect.bottom,
            right: containerRect.right
         };

         this.draggableRestrictionRects.set(element, draggableRestrictionRect);
      }

      return draggableRestrictionRect;
   };

   public menuActions: AssemblyActionGroup[] = [
      new AssemblyActionGroup([
         {
            id: () => "composer viewsheet layout print",
            label: () => "_#(js:Print)...",
            icon: () => "print-icon",
            enabled: () => true,
            visible: () => this.vsLayout.printLayout,
            action: () => this.print()
         }
      ])
   ];

   @Input() set model(value: VSLayoutModel) {
      if(this.inited) {
         this.vsLayout.clearFocusedObjects();
         this.vsLayout = value;
         this.vsLayout.socketConnection = this.viewsheetClientService;
         this.viewsheetClientService.runtimeId = value.runtimeID;
         // no need to scale objects if user is still editing same layout
         this.getLayoutSize();
         this.refreshViewsheet();
      }
      else {
         this.vsLayout = value;
      }

      if(this.vsLayout.printLayout) {
         this.focusedObjectSubscription && this.focusedObjectSubscription.unsubscribe();
         this.focusedObjectSubscription = this.vsLayout.focused.subscribe(
            (objects: VSLayoutObjectModel[]) => {
               if(!objects || objects.length == 0 ||
                  objects.filter(obj => !obj.editable).length > 0)
               {
                  return;
               }


               const layoutObject: VSLayoutObjectModel = objects[0];
               let event: GetVSObjectFormatEvent =
                  new GetVSObjectFormatEvent(layoutObject.name);
               event.layout = true;
               event.layoutRegion = this.vsLayout.currentPrintSection;
               this.viewsheetClientService.sendEvent(
                  "/events/composer/viewsheet/getFormat", event);
               setTimeout(() => this.onLayoutObjectMove.emit());
            });
         this.subscriptions.add(this.focusedObjectSubscription);
      }

      this.snapSubscription && this.snapSubscription.unsubscribe();
      this.snapSubscription = this.vsLayout.focused.subscribe(() => {
         this.updateSnapGuides();
         setTimeout(() => this.onLayoutObjectMove.emit());
      });
      this.subscriptions.add(this.snapSubscription);

      this.viewsheetClientService.focusedLayoutName = this.vsLayout.name;
   }

   get sizeGuidesVisible(): boolean {
      return this.vsLayout.printLayout || !!this.vsLayout.guideType;
   }

   constructor(private viewsheetClientService: ViewsheetClientService,
               private renderer: Renderer2, private debounceService: DebounceService,
               private changeRef: ChangeDetectorRef,
               private zone: NgZone,
               private composerObjectService: ComposerObjectService,
               private resizeHandlerService: ResizeHandlerService)
   {
      super(viewsheetClientService, zone, true);
   }

   ngOnInit() {
      this.viewsheetClientService.connect();
      this.viewsheetClientService.runtimeId = this.vsLayout.runtimeID;
      this.getLayoutSize(true);
      this.refreshViewsheet();
      this.inited = true;
      this.vsLayout.socketConnection = this.viewsheetClientService;

      // Subscribe to heartbeat and touch asset to prevent expiration
      this.subscriptions.add(this.viewsheetClientService.onHeartbeat.subscribe(() => {
         let event = new TouchAssetEvent();
         event.setDesign(true);
         event.setChanged(false);
         event.setUpdate(false);
         this.viewsheetClientService.sendEvent("/events/composer/touch-asset", event);
      }));

      this.subscriptions.add(this.layoutChange.subscribe((isGuideChanged) => {
         this.getLayoutSize(isGuideChanged);
      }));

      this.subscriptions.add(this.resizeHandlerService.anyResizeSubject
         .subscribe(() => this.draggableRestrictionRects = null));
   }

   ngOnChanges(changes: SimpleChanges) {
      if(this.inited && this._layoutRegion !== this.vsLayout.getLayoutSection()) {
         this._layoutRegion = this.vsLayout.getLayoutSection();
         this.refreshViewsheet();
      }
   }

   ngOnDestroy() {
      super.cleanup();
      this.subscriptions.unsubscribe();
   }

   onLayoutResize(): void {
      this.debounceService.debounce(this.runtimeId + "_layout_resize", () => {
         this.getLayoutSize(true);
         this.changeRef.detectChanges();
      }, 300, []);
   }

   get guideSize(): Dimension {
      return this._guideSize;
   }

   set guideSize(guideSize: Dimension) {
      this._guideSize = guideSize;
   }

   getAssemblyName(): string {
      return null;
   }

   refreshViewsheet(): void {
      let size: [number, number] = GuiTool.getViewportSize();
      let mobile: boolean = GuiTool.isMobileDevice();
      let event: OpenViewsheetEvent = new OpenViewsheetEvent(
         null, size[0], size[1], mobile, window.navigator.userAgent);
      event.layoutName = this.vsLayout.name;
      this.viewsheetClientService.sendEvent("/events/composer/viewsheet/refresh", event);
   }

   private getLayoutSize(scaleAssemblies: boolean = false): void {
      this.pages = [0];

      if(this.vsLayout.printLayout) {
         this.getPrintBounds();
         return;
      }

      this.guideSize = this.getGuideSize();
   }

   /**
    * Paint print layout bounds.
    */
   private getPrintBounds(): void {
      let region: PrintLayoutSection = this.vsLayout.currentPrintSection;
      let horizontalScreen: boolean = this.vsLayout.horizontal;
      let unit: string = this.vsLayout.unit;
      let pwidth: number = this.getPLayoutSize(this.vsLayout.width, unit);
      let pheight: number = this.getPLayoutSize(this.vsLayout.height, unit);
      let mtop: number = this.getPLayoutSize(this.vsLayout.marginTop, "inches");
      let mleft: number = this.getPLayoutSize(this.vsLayout.marginLeft, "inches");
      let mright: number = this.getPLayoutSize(this.vsLayout.marginRight, "inches");
      let mbottom: number = this.getPLayoutSize(this.vsLayout.marginBottom, "inches");

      pwidth -= !horizontalScreen ? mleft + mright : mtop + mbottom;
      pheight -= !horizontalScreen ? mtop + mbottom : mleft + mright;

      if(region == PrintLayoutSection.CONTENT) {
         this.pages = [];
         this.guideSize.height = horizontalScreen ? pwidth : pheight;
         const objectSize = this.getLayoutObjectSize();
         const paneHeight = Math.max(objectSize.height, this.PANE_HEIGHT);

         for(let i: number = 0; (i + 1) * this.guideSize.height < paneHeight; i++) {
            this.pages.push(i);
         }
      }
      else {
         let nheight: number;

         if(region == PrintLayoutSection.HEADER) {
            nheight = mtop - this.getPLayoutSize(this.vsLayout.headerFromEdge, unit);
         }
         else {
            nheight = this.getPLayoutSize(this.vsLayout.footerFromEdge, unit);
         }

         this.guideSize.height = nheight;
      }

      this.guideSize.width = horizontalScreen ? pheight : pwidth;
   }

   /**
    * Get the pixel size of the print layout dimesion.
    */
   private getPLayoutSize(asize: number, unit: string): number {
      let psize: number = asize;

      switch(unit) {
         case "inches":
            psize = asize * PrintLayoutMeasures.INCH_POINT;
            break;
         case "mm":
            psize = asize / PrintLayoutMeasures.INCH_MM * PrintLayoutMeasures.INCH_POINT;
            break;
         default:
            break;
      }

      return psize;
   }

   get showContent(): boolean {
      return !this.vsLayout.printLayout
         || this.vsLayout.currentPrintSection == PrintLayoutSection.CONTENT;
   }

   getSnapGridStyle() {
      let style = {};

      if(this.snapToGrid) {
         style = {
            "background-image": this.getBackgroundImage(this.vs.snapGrid),
         };
      }

      return style;
   }

   getBackgroundImage(snapGrid: number) {
      let url = "url" + "(data:image/svg+xml;charset=UTF-8,%3Csvg%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%20width%3D%22"
          +snapGrid+"%22%20height%3D%22"+snapGrid+"%22%3E%3Ccircle%20cx%3D%220.5%22%20cy%3D%220.5%22%20r%3D%220.5%22%20stroke-width%3D%220%22%20fill%3D%22%23999%22%2F%3E%3C%2Fsvg%3E)";

      return url;
   }

   get showHeader(): boolean {
      return this.vsLayout.printLayout
         && this.vsLayout.currentPrintSection == PrintLayoutSection.HEADER;
   }

   get showFooter(): boolean {
      return this.vsLayout.printLayout
         && this.vsLayout.currentPrintSection == PrintLayoutSection.FOOTER;
   }

   /**
    * Drop event handler. Place objects into layout.
    */
   drop(event: any): void {
      event.preventDefault();
      GuiTool.clearDragImage();

      const scale: number = this.vs.scale ? 1 / this.vs.scale : 1;
      let box: ClientRect = this.layoutPane.nativeElement.getBoundingClientRect();
      let left: number = (event.pageX - box.left + this.layoutPane.nativeElement.scrollLeft) * scale;
      let top: number = (event.pageY - box.top + this.layoutPane.nativeElement.scrollTop) * scale;
      left = Math.round(left / this.vs.snapGrid) * this.vs.snapGrid;
      top = Math.round(top / this.vs.snapGrid) * this.vs.snapGrid;
      const data: any = JSON.parse(event.dataTransfer.getData("text"));
      let objects: string[] = !!data.object ? data.object.filter(obj => obj != null) : null;
      let newObject: string = data.newObject;

      if(data["object-exist"]) {
         return;
      }

      if(this.vsLayout.printLayout && !!newObject && !objects) {
         let vsevent: AddVSLayoutObjectEvent =
            new AddVSLayoutObjectEvent(Number(newObject), left, top);
         vsevent.names = [this.getNextObjectName(Number(newObject))];
         vsevent.layoutName = this.vsLayout.name;
         vsevent.region = this.vsLayout.currentPrintSection;

         if(vsevent.type == AssemblyType.PAGEBREAK_ASSET) {
            vsevent.xOffset = 0;
         }

         this.viewsheetClientService.sendEvent("/events/composer/vs/layouts/addObject", vsevent);
      }
      else if(!newObject && objects && objects.length > 0) {
         let vsevent: AddVSLayoutObjectEvent = new AddVSLayoutObjectEvent(null, left, top);
         vsevent.names = objects;
         vsevent.layoutName = this.vsLayout.name;
         vsevent.region = this.vsLayout.currentPrintSection;
         this.viewsheetClientService.sendEvent("/events/composer/vs/layouts/addObject", vsevent);
      }
   }

   onSelectionBox(event: SelectionBoxEvent) {
      this.vsLayout.clearFocusedObjects();
      const scale = this.vs.scale;
      let objects: VSLayoutObjectModel[] = [];

      if(this.vsLayout.currentPrintSection == PrintLayoutSection.HEADER) {
         objects = this.vsLayout.headerObjects;
      }
      else if(this.vsLayout.currentPrintSection == PrintLayoutSection.FOOTER) {
         objects = this.vsLayout.footerObjects;
      }
      else {
         objects = this.vsLayout.objects;
      }

      for(let object of objects) {
         const objectRect = new Rectangle(object.left * scale,
            object.top * scale,
            object.width * scale,
            object.height * scale);

         if(objectRect.intersects(event.box)) {
            this.vsLayout.selectObject(object);
         }
      }

      this.changeRef.detectChanges();
   }

   public getLayoutObjects(): VSLayoutObjectModel[] {
      let objects: VSLayoutObjectModel[] = [];

      if(this.vsLayout.currentPrintSection == PrintLayoutSection.HEADER) {
         objects = this.vsLayout.headerObjects;
      }
      else if(this.vsLayout.currentPrintSection == PrintLayoutSection.FOOTER) {
         objects = this.vsLayout.footerObjects;
      }
      else {
         objects = this.vsLayout.objects;
      }

      return objects;
   }

   public onSnap(snap: {x, y}): void {
      this.currentSnapGuides = snap;
   }

   private getNextObjectName(type: number): string {
      let prefixStr: string = "";

      switch(this.vsLayout.currentPrintSection) {
      case PrintLayoutSection.CONTENT:
         prefixStr = "Content_";
         break;
      case PrintLayoutSection.HEADER:
         prefixStr = "Header_";
         break;
      case PrintLayoutSection.FOOTER:
         prefixStr = "Footer_";
         break;
      default:
         break;
      }

      if(type == AssemblyType.TEXT_ASSET) {
         prefixStr += "Text";
      }
      else if(type == AssemblyType.IMAGE_ASSET) {
         prefixStr += "Image";
      }
      else if(type == AssemblyType.PAGEBREAK_ASSET) {
         prefixStr += "PageBreak";
      }

      let name: string = prefixStr;

      // eslint-disable-next-line no-constant-condition
      for(let n = 1; true; n++) {
         name = prefixStr + n;

         if(!this.vsLayout.objects.find((obj) => name == obj.name)
            && !this.vsLayout.headerObjects.find((obj) => name == obj.name)
            && !this.vsLayout.footerObjects.find((obj) => name == obj.name))
         {
            return name;
         }
      }
   }

   private print(): void {
      const url = "../export/viewsheet/" + Tool.byteEncode(this.runtimeId) +
         "?match=false&current=true&previewPrintLayout=true";
      GuiTool.openBrowserTab(url);
   }

   trackByFn(index: number, object: VSLayoutObjectModel) {
      return object.name;
   }

   /**
    * Add a new object to vs layout/ or update a current one.
    * @param {ChangeCurrentLayoutCommand} command
    */
   private processAddLayoutObjectCommand(command: AddLayoutObjectCommand): void {
      if(command.region == PrintLayoutSection.HEADER) {
         this.vsLayout.headerObjects = this.updateObjectList(this.vsLayout.headerObjects,
                                                             command.object);
      }
      else if(command.region == PrintLayoutSection.FOOTER) {
         this.vsLayout.footerObjects = this.updateObjectList(this.vsLayout.footerObjects,
                                                             command.object);
      }
      else {
         const oldSize = this.vsLayout.objects.length;
         this.vsLayout.objects = this.updateObjectList(this.vsLayout.objects, command.object);
         const newSize = this.vsLayout.objects.length;

         if(!this.vsLayout.printLayout) {
            /*
            if(oldSize === 0 && newSize === 1) {
               // Scale the top-left coordinate to the approximate drop point in the original, full-
               // screen guides.
               const object = this.vsLayout.objects[0];
               const scale = object.width / this.guideSize.width;
               object.top = Math.round(object.top * scale);
               object.left = Math.round(object.left * scale);
               this.guideSize = this.getGuideSize();
               this.updateDimensions(this.vsLayout.objects);
            }
            */
            // This is modifying a layout with existing assemblies, just handle it normally.
            this.guideSize = this.getGuideSize();
         }
      }

      this.vsLayout.updateFocusedObjects(command.object);
      this.onLayoutObjectChange.emit(null);
   }

   /**
    * Refresh layout objects position after adding/moving/resizing print layout objects.
    *
    * for page break.
    */
   private processRefreshLayoutObjectsCommand(command: RefreshLayoutObjectsCommand): void {
      if(!command || !command.layoutObjects || !this.vsLayout.objects) {
         return;
      }

      let nlayouts = command.layoutObjects;
      let currLayouts = this.vsLayout.objects;

      for(let i = 0; i < nlayouts.length; i++) {
         for(let j = 0; j < currLayouts.length; j++) {
            if(nlayouts[i].name != currLayouts[j].name) {
               continue;
            }

            currLayouts[j].left = nlayouts[i].left;
            currLayouts[j].top = nlayouts[i].top;
            currLayouts[j].width = nlayouts[i].width;
            currLayouts[j].height = nlayouts[i].height;
         }
      }
   }

   private updateObjectList(objects: VSLayoutObjectModel[], object: VSLayoutObjectModel) {
      if(objects) {
         let index: number = objects
            .findIndex((obj) => obj.name == object.name);

         if(index == -1) {
            objects.push(object);
         }
         else {
            const oldModel = objects[index].objectModel;
            const newModel = object.objectModel;

            if(oldModel && newModel) {
               object.objectModel = VSUtil.replaceObject(Tool.clone(oldModel), newModel);
            }

            if(object.childModels && objects[index].childModels) {
               object.childModels = object.childModels.map((childModel) => {
                  const oldIndex = objects[index].childModels.findIndex(
                     (model) => model.absoluteName === childModel.absoluteName);

                  if(oldIndex >= 0) {
                     return VSUtil.replaceObject(
                        Tool.clone(objects[index].childModels[oldIndex]), childModel);
                  }

                  return childModel;
               });
            }

            objects[index] = object;
         }
      }
      else {
         objects = [object];
      }

      let hasChildVSViewsheet = false;

      object.childModels.forEach((child) => {
         switch(child.objectType) {
            case "VSViewsheet":
               hasChildVSViewsheet = true;
               break;
            case "VSTable":
            case "VSCrosstab":
            case "VSCalcTable":
               const block_size = Math.max(100, child.objectFormat.height / 16);
               this.viewsheetClientService.sendEvent(
                  "/events/table/reload-table-data",
                  new LoadTableDataEvent(child.absoluteName, 0, block_size));
               break;
         }
      });

      if(object.objectModel.objectType == "VSViewsheet" || hasChildVSViewsheet) {
         this.refreshViewsheet();
      }

      return objects;
   }

   private getGuideSize(): Dimension {
      const screenSize: Dimension  = VSUtil.getLayoutPreviewSize(
         this.vsLayout.objects && this.vsLayout.objects.length > 0
            ? this.layoutPane.nativeElement.clientWidth : this.vsPaneBounds.width,
         this.vsLayout.objects && this.vsLayout.objects.length > 0
            ? this.layoutPane.nativeElement.clientHeight : this.vsPaneBounds.height - 56,
         this.vsLayout.guideType);

      const objectSize = this.getLayoutObjectSize();
      const objectMaxWidth = objectSize.width;
      const guideWidth = objectMaxWidth > 0 ? objectMaxWidth : screenSize.width;
      let guideHeight = this.layoutPane.nativeElement.clientHeight;
      switch(this.vsLayout.guideType) {
      case GuideBounds.GUIDES_16_9_PORTRAIT:
         guideHeight = guideWidth * 16 / 9;
         break;
      case GuideBounds.GUIDES_16_9_LANDSCAPE:
         guideHeight = guideWidth * 9 / 16;
         break;
      case GuideBounds.GUIDES_4_3_PORTRAIT:
         guideHeight = guideWidth * 4 / 3;
         break;
      case GuideBounds.GUIDES_4_3_LANDSCAPE:
         guideHeight = guideWidth * 3 / 4;
         break;
      }

      this.pages = [];

      if(!!this.vsLayout.guideType) {
         let page = 0;

         do {
            this.pages.push(page);
            page += 1;
         }
         while(page * guideHeight < objectSize.height);
      }

      return new Dimension(guideWidth, guideHeight);
   }

   private getLayoutObjectSize(): Dimension {
      return this.vsLayout.objects.reduce((size, object) => {
         const width = object.objectModel.objectType === "VSViewsheet" ?
            object.left + (<VSViewsheetModel>(object.objectModel)).bounds.width :
            object.left + object.width;
         const height = object.objectModel.objectType === "VSViewsheet" ?
            object.top + (<VSViewsheetModel>(object.objectModel)).bounds.height :
            object.top + object.height;
         size.width = Math.max(size.width, width);
         size.height = Math.max(size.height, height);
         return size;
      }, new Dimension(0, 0));
   }

   /**
    * Remove an object from vs layout.
    * @param {RemoveLayoutObjectsCommand} command
    */
   private processRemoveLayoutObjectsCommand(command: RemoveLayoutObjectsCommand): void {
      if(this.vsLayout.name != command.layoutName) {
         return;
      }

      command.assemblies.forEach((assemblyName) => {
         if(this.vsLayout.currentPrintSection == PrintLayoutSection.HEADER) {
            this.vsLayout.headerObjects =
               this.removeLayoutObject(this.vsLayout.headerObjects, assemblyName);
         }
         else if(this.vsLayout.currentPrintSection == PrintLayoutSection.FOOTER) {
            this.vsLayout.footerObjects =
               this.removeLayoutObject(this.vsLayout.footerObjects, assemblyName);
         }
         else {
            this.vsLayout.objects =
               this.removeLayoutObject(this.vsLayout.objects, assemblyName);

            if(!this.vsLayout.printLayout) {
               this.guideSize = this.getGuideSize();
            }
         }
      });

      this.onLayoutObjectChange.emit(null);
   }

   public removeSelectedAssemblies(): void {
      let event: RemoveVSLayoutObjectEvent = new RemoveVSLayoutObjectEvent();
      event.layoutName = this.vsLayout.name;
      event.region = this.vsLayout.currentPrintSection;
      event.names = this.vsLayout.focusedObjects.map((focusedObject) => focusedObject.name);
      this.viewsheetClientService.sendEvent("/events/composer/vs/layouts/removeObjects", event);
   }

   private removeLayoutObject(objects: VSLayoutObjectModel[], name: string) {
      let index: number = objects.findIndex((object) => object.name == name);

      if(index > -1) {
         objects.splice(index, 1);
      }

      return objects;
   }

   private updateDimensions(layoutObjects: VSLayoutObjectModel[] = null): void {
      if(!layoutObjects) {
         layoutObjects = this.vsLayout.focusedObjects;
      }

      let event: MoveResizeLayoutObjectsEvent = new MoveResizeLayoutObjectsEvent(
         this.vsLayout.name,
         layoutObjects.map(object => object.name),
         layoutObjects.map(object => object.left),
         layoutObjects.map(object => object.top),
         layoutObjects.map(object => object.width),
         layoutObjects.map(object => object.height)
      );

      event.region = this.vsLayout.currentPrintSection;
      this.vsLayout.socketConnection.sendEvent("/events/composer/vs/layouts/moveResizeObjects",
                                               event);
   }

   /**
    * Used to update undo/redo state of vs.
    * @param {UpdateUndoStateCommand} command
    */
   private processUpdateLayoutUndoStateCommand(command: UpdateLayoutUndoStateCommand): void {
      if(command.id == this.runtimeId) {
         this.onUpdateLayoutUndoState.emit(command);
      }
   }

   /**
    * Retrieve current format to display in format pane.
    * @param {SetCurrentFormatCommand} command
    */
   private processSetCurrentFormatCommand(command: SetCurrentFormatCommand): void {
      this.vsLayout.currentFormat = command.model;
      this.vsLayout.origFormat = Tool.clone(command.model);
   }

   @HostListener("mousedown", ["$event"])
   mousedown(event: MouseEvent) {
      this.click = true;

      if(this.layoutPane) {
         const layoutPaneElement: any = this.layoutPane.nativeElement;

         //mousedown gets triggered when clicking scroll bar, check if mousedown was called
         // from scrollbar and return if yes
         if((layoutPaneElement.scrollHeight > layoutPaneElement.clientHeight &&
             event.offsetX >= layoutPaneElement.offsetWidth - this.scrollbarWidth) ||
            (layoutPaneElement.scrollWidth > layoutPaneElement.clientWidth &&
             event.offsetY >= layoutPaneElement.offsetHeight - this.scrollbarWidth))
         {
            return;
         }
      }

      // listen for mouse up when mousedown event begins on host, then remove after mouseup
      let mouseUpListener = this.renderer.listen("document", "mouseup",
         (evt: MouseEvent) => {
            if(this.layoutPane && evt.target == this.layoutPane.nativeElement) {
               this.vsLayout.clearFocusedObjects();
            }

            this.click = false;
            mouseUpListener();
         });
   }

   moveObject(event: KeyboardEvent) {
      if(event.keyCode == 65 && (event.ctrlKey || event.metaKey)) { // Ctrl + a
         this.zone.run(() => {
            if(!(event.target instanceof HTMLInputElement)) {
               event.preventDefault();
               this.vsLayout.selectAllObjects(this.vsLayout.getLayoutSection());
            }
         });

         return;
      }

      if(!this.composerObjectService.isLayoutKeyEventApplied(event)) {
         return;
      }

      if(this.vsLayout.focusedObjects.length > 0) {
         let offset = this.snapToGrid ? this.interactContainer.snapGridSize : 1;

         if(event.keyCode === 46 || event.keyCode === 8) { //Delete || Backspace
            event.preventDefault();
            this.removeSelectedAssemblies();
            return;
         }
         else if(event.keyCode === 38) { //Up
            event.preventDefault();
            this.vsLayout.focusedObjects.forEach((object, i) => {

               if(this.vsLayout.focusedObjects[i].objectModel.objectType != "VSPageBreak") {
                  if(this.vsLayout.focusedObjects[i].top > 1) {
                     this.vsLayout.focusedObjects[i].top -= offset;

                     if(!!this.vsLayout.focusedObjects[i].objectModel &&
                        this.vsLayout.focusedObjects[i].objectModel.objectType == "VSSelectionContainer") {
                        this.vsLayout.focusedObjects[i].objectModel.objectFormat.top -= offset;
                     }
                  }
                  else {
                     this.vsLayout.focusedObjects[i].top = 0;
                  }
               }
            });

            this.updateDimensions0();
         }
         else if(event.keyCode === 40) { //Down
            event.preventDefault();
            this.vsLayout.focusedObjects.forEach((object, i) => {
               if(this.vsLayout.focusedObjects[i].objectModel.objectType != "VSPageBreak") {
                  this.vsLayout.focusedObjects[i].top += offset;

                  if(!!this.vsLayout.focusedObjects[i].objectModel &&
                     this.vsLayout.focusedObjects[i].objectModel.objectType == "VSSelectionContainer") {
                     this.vsLayout.focusedObjects[i].objectModel.objectFormat.top += offset;
                  }
               }
            });

            this.updateDimensions0();
         }
         else if(event.keyCode === 37) { //Left
            event.preventDefault();
            this.vsLayout.focusedObjects.forEach((object, i) => {
               if(this.vsLayout.focusedObjects[i].objectModel.objectType != "VSPageBreak") {
                  if(this.vsLayout.focusedObjects[i].left > 1) {
                     this.vsLayout.focusedObjects[i].left -= offset;

                     if(!!this.vsLayout.focusedObjects[i].objectModel &&
                        this.vsLayout.focusedObjects[i].objectModel.objectType == "VSSelectionContainer") {
                        this.vsLayout.focusedObjects[i].objectModel.objectFormat.left -= offset;
                     }
                  }
                  else {
                     this.vsLayout.focusedObjects[i].left = 0;
                  }
               }
            });

            this.updateDimensions0();
         }
         else if(event.keyCode === 39) { //Right
            event.preventDefault();
            this.vsLayout.focusedObjects.forEach((object, i) => {
               if(this.vsLayout.focusedObjects[i].objectModel.objectType != "VSPageBreak") {
                  this.vsLayout.focusedObjects[i].left += offset;

                  if(!!this.vsLayout.focusedObjects[i].objectModel &&
                     this.vsLayout.focusedObjects[i].objectModel.objectType == "VSSelectionContainer") {
                     this.vsLayout.focusedObjects[i].objectModel.objectFormat.left += offset;
                  }
               }
            });

            this.updateDimensions0();
         }
      }
   }

   private updateDimensions0(): void {
       this.debounceService.debounce("updateDimensions", () => {
             this.changeRef.detectChanges();
             this.updateDimensions();
          }, 300, []);
      this.onLayoutObjectMove.emit();
   }

   private updateSnapGuides(): void {
      this.draggableSnapGuides.horizontal = [];
      this.draggableSnapGuides.vertical = [];
      const {horizontal, vertical} = this.draggableSnapGuides;
      const layoutObjects = this.getLayoutObjects();

      layoutObjects.forEach((layoutObject) => {
         const assembly = layoutObject.objectModel;

         if(!this.vsLayout.focusedObjects.some(
               (focused) => focused.objectModel.absoluteName === assembly.absoluteName ||
                  focused.objectModel.absoluteName === assembly.container))
         {
            let {top, left, width, height} = assembly.objectFormat;

            if(assembly.objectType === "VSViewsheet") {
               const vs = <VSViewsheetModel> assembly;
               width = vs.bounds.width;
               height = vs.bounds.height;
            }

            vertical.push(left);
            vertical.push(left + width);
            horizontal.push(top);
            horizontal.push(top + height);

            // add mid points
            if(width >= 20) {
               vertical.push(-Math.round(left + width / 2));
            }

            if(height >= 10) {
               horizontal.push(-Math.round(top + height / 2));
            }
         }
      });

      horizontal.sort((a, b) => a - b);

      for(let i = 1; i < horizontal.length; i++) {
         if(horizontal[i] === horizontal[i - 1]) {
            horizontal.splice(i, 1);
            i -= 1;
         }
      }

      vertical.sort((a, b) => a - b);

      for(let i = 1; i < vertical.length; i++) {
         if(vertical[i] === vertical[i - 1]) {
            vertical.splice(i, 1);
            i -= 1;
         }
      }
   }

   scrolled(event: any) {
      this.layoutScrollLeft = this.layoutPane.nativeElement.scrollLeft;
      this.layoutScrollTop = this.layoutPane.nativeElement.scrollTop;
   }

   assemblyResized(event: any, model: VSLayoutObjectModel) {
      let bottom = false;
      let right = false;
      let top = false;
      let left = false;
      let deltaX = 0;
      let deltaY = 0;
      let deltaWidth = 0;
      let deltaHeight = 0;

      if(event && event.edges) {
         bottom = event.edges.bottom;
         right = event.edges.right;
         top = event.edges.top;
         left = event.edges.left;
      }

      if(event && event.deltaRect) {
         deltaX = event.deltaRect.left || 0;
         deltaY = event.deltaRect.top || 0;
         deltaWidth = event.deltaRect.width || 0;
         deltaHeight = event.deltaRect.height || 0;
      }

      this.processAssemblyResize(
         model, bottom, right, top, left, deltaX, deltaY, deltaWidth, deltaHeight);
   }

   private processAssemblyResize(model: VSLayoutObjectModel, atBottom: boolean, atRight: boolean,
                                 atTop: boolean, atLeft: boolean, deltaX: number, deltaY: number,
                                 deltaWidth: number, deltaHeight: number)
   {
      if(this.resizeTimeout) {
         clearTimeout(this.resizeTimeout);
         this.resizeTimeout = null;
      }

      if(model) {
         const left = model.left;
         const top = model.top;
         const right = model.left + model.width;
         const bottom = model.top + model.height;
         const {clientWidth, clientHeight, scrollLeft, scrollTop} = this.layoutPane.nativeElement;

         // Bug #31065. Resize should only occur when the mouse is pressed(dragging).
         if(atRight && this.click && clientWidth + scrollLeft <= right + 10 && deltaWidth > 0) {
            this.layoutPane.nativeElement.scrollLeft = Math.max(0, right - clientWidth);
            this.resizeTimeout = setTimeout(() => {
               this.resizeTimeout = null;
               model.width += 10;
               this.processAssemblyResize(model, atBottom, atRight, atTop, atLeft, 0, 0, 1, 0);
            }, 100);
         }

         if(atRight && this.click && scrollLeft >= right - 10 && deltaWidth < 0) {
            this.layoutPane.nativeElement.scrollLeft = Math.max(0, right - 10);
            this.resizeTimeout = setTimeout(() => {
               this.resizeTimeout = null;
               model.width = Math.max(1, model.width - 10);
               this.processAssemblyResize(model, atBottom, atRight, atTop, atLeft, 0, 0, -1, 0);
            }, 100);
         }

         if(atLeft && this.click && scrollLeft >= left - 10 && deltaX < 0) {
            this.layoutPane.nativeElement.scrollLeft = Math.max(0, left - 10);
            this.resizeTimeout = setTimeout(() => {
               this.resizeTimeout = null;
               const delta = model.left <= 10 ? model.left - 1 : 10;
               model.left -= delta;
               model.width += delta;
               this.processAssemblyResize(model, atBottom, atRight, atTop, atLeft, -1, 0, 1, 0);
            }, 100);
         }

         if(atLeft && this.click && clientWidth + scrollLeft <= left + 10 && deltaX > 0) {
            this.layoutPane.nativeElement.scrollLeft = this.layoutPane.nativeElement.scrollLeft + 10;
            this.resizeTimeout = setTimeout(() => {
               this.resizeTimeout = null;
               const delta = model.width <= 10 ? model.width - 1 : 10;
               model.left += delta;
               model.width -= delta;
               this.processAssemblyResize(model, atBottom, atRight, atTop, atLeft, 1, 0, -1, 0);
            }, 100);
         }

         if(atBottom && this.click && clientHeight + scrollTop <= bottom + 10 && deltaHeight > 0) {
            this.layoutPane.nativeElement.scrollTop = Math.max(0, bottom - clientHeight);
            this.resizeTimeout = setTimeout(() => {
               this.resizeTimeout = null;
               model.height += 10;
               this.processAssemblyResize(model, atBottom, atRight, atTop, atLeft, 0, 0, 0, 1);
            }, 100);
         }

         if(atBottom && this.click && scrollTop >= bottom - 10 && deltaHeight < 0) {
            this.layoutPane.nativeElement.scrollTop = Math.max(0, bottom - 10);
            this.resizeTimeout = setTimeout(() => {
               this.resizeTimeout = null;
               model.height = Math.max(1, model.height - 10);
               this.processAssemblyResize(model, atBottom, atRight, atTop, atLeft, 0, 0, 0, -1);
            }, 100);
         }

         if(atTop && this.click && scrollTop >= top - 10 && deltaY < 0) {
            this.layoutPane.nativeElement.scrollTop = Math.max(0, top - 10);
            this.resizeTimeout = setTimeout(() => {
               this.resizeTimeout = null;
               const delta = model.top <= 10 ? model.top - 1 : 10;
               model.top -= delta;
               model.height += delta;
               this.processAssemblyResize(model, atBottom, atRight, atTop, atLeft, 0, -1, 0, 1);
            }, 100);
         }

         if(atTop && this.click && clientHeight + scrollTop <= top + 10 && deltaY > 0) {
            this.layoutPane.nativeElement.scrollTop = this.layoutPane.nativeElement.scrollTop + 10;
            this.resizeTimeout = setTimeout(() => {
               this.resizeTimeout = null;
               const delta = model.height <= 10 ? model.height - 1 : 10;
               model.top += delta;
               model.height -= delta;
               this.processAssemblyResize(model, atBottom, atRight, atTop, atLeft, 0, 1, 0, -1);
            }, 100);
         }
      }
   }
}
