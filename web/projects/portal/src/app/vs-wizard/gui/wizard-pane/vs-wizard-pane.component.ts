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
import { HttpClient } from "@angular/common/http";
import {
   AfterViewInit,
   Component,
   ElementRef,
   EventEmitter,
   HostListener,
   Input,
   NgZone,
   OnDestroy,
   OnInit,
   Output,
   Renderer2,
   TemplateRef,
   ViewChild,
   ChangeDetectorRef
} from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Subscription } from "rxjs";
import { KeyCodeValue } from "../../../../../../shared/util/key-code-value";
import { Tool } from "../../../../../../shared/util/tool";
import { Point } from "../../../common/data/point";
import { Rectangle } from "../../../common/data/rectangle";
import { ComponentTool } from "../../../common/util/component-tool";
import { GuiTool } from "../../../common/util/gui-tool";
import { CommandProcessor, ViewsheetClientService } from "../../../common/viewsheet-client";
import { Viewsheet } from "../../../composer/data/vs/viewsheet";
import { AssemblyType } from "../../../composer/gui/vs/assembly-type";
import { EventQueueService } from "../../../composer/gui/vs/event-queue.service";
import { AddNewVSObjectEvent } from "../../../composer/gui/vs/objects/event/add-new-vs-object-event";
import { MoveVSObjectEvent } from "../../../composer/gui/vs/objects/event/move-vs-object-event";
import { RemoveVSObjectsEvent } from "../../../composer/gui/vs/objects/event/remove-vs-objects-event";
import { ResizeVSObjectEvent } from "../../../composer/gui/vs/objects/event/resize-vs-object-event";
import { AddVSObjectCommand } from "../../../vsobjects/command/add-vs-object-command";
import { RefreshVSObjectCommand } from "../../../vsobjects/command/refresh-vs-object-command";
import { RemoveVSObjectCommand } from "../../../vsobjects/command/remove-vs-object-command";
import { UpdateUndoStateCommand } from "../../../vsobjects/command/update-unto-state-command";
import {
   ContextProvider,
   VSWizardContextProviderFactory
} from "../../../vsobjects/context-provider.service";
import { VSImageModel } from "../../../vsobjects/model/output/vs-image-model";
import { VSObjectModel } from "../../../vsobjects/model/vs-object-model";
import { PopComponentService } from "../../../vsobjects/objects/data-tip/pop-component.service";
import { VSUtil } from "../../../vsobjects/util/vs-util";
import { SelectionBoxEvent } from "../../../widget/directive/selection-box.directive";
import { ModelService } from "../../../widget/services/model.service";
import { SetWizardGridCommand } from "../../model/command/set-wizard-grid-command";
import { UploadImageCommand } from "../../model/command/upload-image-command";
import { VSWizardConstants } from "../../model/vs-wizard-constants";
import { VsWizardEditModes } from "../../model/vs-wizard-edit-modes";
import { WizardNewObjectModel } from "../objects/wizard-new-object-model";
import { DragMoveStartOptions } from "./drag-move-start-options";
import { FollowDirection } from "./follow-direction";

const UPLOAD_IMAGE_URI = "../composer/vswizard/update-image";
const REMOVE_VS_WIZARD_OBJECT_URI = "/events/composer/vswizard/object/remove";
const INSERT_OBJECT_URI = "/events/composer/vswizard/insert-object";
const VIEWSHEET_WIZARD_OBJECTS_RESIZE_URI = "/events/composer/vswizard/object/resize";

const PADDING_H = 15; // Bug 39329
const PADDING_V = 30; // mini-toolbar should display

@Component({
   selector: "vs-wizard-pane",
   templateUrl: "vs-wizard-pane.component.html",
   styleUrls: ["vs-wizard-pane.component.scss"],
   providers: [
      PopComponentService,
      {
         provide: ContextProvider,
         useFactory: VSWizardContextProviderFactory
      }
   ]
})
export class VsWizardPane extends CommandProcessor implements OnInit, AfterViewInit, OnDestroy {
   @Input() currentVSObject: VSObjectModel = null;
   @Input() editMode: VsWizardEditModes = VsWizardEditModes.WIZARD_DASHBOARD;
   @Input() componentWizardEnable: boolean;
   @Input() hiddenNewBlock: boolean = false;
   @Output() toComponentWizard: EventEmitter<{objectName?, objectType?, point?}> = new EventEmitter<{objectName?, objectType?, point?}>();
   @Output() onChangeCurrentObject = new EventEmitter<VSObjectModel>();
   @Output() onCancel: EventEmitter<any> = new EventEmitter();
   @Output() onFinish = new EventEmitter<number>();
   @Output() onUpdateObjectModel = new EventEmitter<VSObjectModel>();
   @Output() onHiddenNewBlockChanged = new EventEmitter<boolean>();
   @ViewChild("uploadInput") uploadInput: ElementRef;
   @ViewChild("paneContainer") paneContainer: ElementRef;
   @ViewChild("scrollContainer") scrollContainer: ElementRef;
   viewsheet: Viewsheet = new Viewsheet();
   autoLayoutHorizontal: boolean = true;
   gridCellWidth: number = VSWizardConstants.GRID_CELL_WIDTH;
   gridCellHeight: number = VSWizardConstants.GRID_CELL_HEIGHT;
   _gridRowCount: number = VSWizardConstants.GRID_ROW;
   gridColCount: number = VSWizardConstants.GRID_COLUMN;
   originalRows: number = this.gridRowCount;
   originalCols: number = this.gridColCount;
   drageWithShift: boolean = false;
   maxYSelectedAssembly: VSObjectModel;
   maxXSelectedAssembly: VSObjectModel;
   followDirection: FollowDirection = new FollowDirection();
   private moveTimeout: any = null;
   followAssemblies: Map<string, boolean> = new Map<string, boolean>();
   rightFollowAssemblies: Map<string, boolean> = new Map<string, boolean>();
   bottomFollowAssemblies: Map<string, boolean> = new Map<string, boolean>();
   private draggableRestrictionRects: Map<any, {left: number, top: number, right: number, bottom: number}>;
   private subscriptions: Subscription = new Subscription();
   private keydownListener: () => void;
   bottomFollowRestriction: {top, left, right, bottom};
   rightFollowRestriction: {top, left, right, bottom};
   private defaultObjectModel: WizardNewObjectModel = {
      visible: true,
      bounds: new Rectangle(520, 80 , VSWizardConstants.NEW_OBJECT_WIDTH,
                            VSWizardConstants.NEW_OBJECT_HEIGHT)
   };
   newObjectModel = this.defaultObjectModel;
   private moving = false;

   readonly PADDING_H = PADDING_H;
   readonly PADDING_V = PADDING_V;

   get gridRowCount(): number {
      return this._gridRowCount;
   }

   set gridRowCount(gridRowCount: number) {
      if(gridRowCount % 2 != 0) {
         gridRowCount++;
      }

      this._gridRowCount = gridRowCount;
   }

   draggableRestriction = (x: number, y: number, element: any) => {
      if(!this.draggableRestrictionRects) {
         this.draggableRestrictionRects = new Map<any, {left: number, top: number, right: number,
            bottom: number}>();
      }

      let draggableRestrictionRect = this.draggableRestrictionRects.get(element);

      if(!draggableRestrictionRect) {
         const containerElement = this.scrollContainer.nativeElement;
         const containerRect = GuiTool.getElementRect(containerElement);
         const elementRect = GuiTool.getElementRect(element);
         let offsetX = 0;
         let offsetY = 0;
         let offsetRight = 0;

         if(this.viewsheet.currentFocusedAssemblies.length > 1) {
            this.viewsheet.currentFocusedAssemblies
               .filter(a => !a.interactionDisabled)
               .forEach((assembly) => {
                  const assemblyElement = containerElement.querySelector(
                     `.wizard-object-editor[data-name='${assembly.absoluteName}']`);

                  if(assemblyElement) {
                     const assemblyRect = GuiTool.getElementRect(assemblyElement);
                     offsetX = Math.max(offsetX, elementRect.left - assemblyRect.left);
                     offsetY = Math.max(offsetY, elementRect.top - assemblyRect.top);
                     offsetRight = Math.max(offsetRight, assemblyRect.right - elementRect.right);
                  }
               });
         }

         let scrollVWidth = this.getVScrollWidth(this.scrollContainer);

         draggableRestrictionRect = {
            top: containerRect.top + PADDING_V + offsetY,
            left: containerRect.left + PADDING_H + offsetX,
            bottom: Number.MAX_VALUE,
            right: Number.MAX_VALUE
         };

         this.draggableRestrictionRects.set(element, draggableRestrictionRect);
      }

      return draggableRestrictionRect;
   };

   constructor(protected zone: NgZone,
               private http: HttpClient,
               private modalService: NgbModal,
               private modelService: ModelService,
               private renderer: Renderer2,
               private eventQueueService: EventQueueService,
               private changeRef: ChangeDetectorRef,
               public viewsheetClient: ViewsheetClientService)
   {
      super(viewsheetClient, zone, true);
      this.viewsheet.socketConnection = viewsheetClient;
   }

   ngOnInit(): void {
      this.subscriptions.add(this.viewsheet.focusedAssemblies
         .subscribe(() => {
            this.draggableRestrictionRects = null;

            this.viewsheet.currentFocusedAssemblies.forEach((obj: VSObjectModel) => {
               if(!this.maxYSelectedAssembly) {
                  this.maxYSelectedAssembly = obj;
               }

               if(!this.maxXSelectedAssembly) {
                  this.maxXSelectedAssembly = obj;
               }

               let objectMaxY = obj.objectFormat.top + obj.objectFormat.height;
               let objectMaxX = obj.objectFormat.left + obj.objectFormat.width;
               let currentMaxY = this.maxYSelectedAssembly.objectFormat.top +
                  this.maxYSelectedAssembly.objectFormat.height;
               let currentMaxX = this.maxYSelectedAssembly.objectFormat.left +
                  this.maxYSelectedAssembly.objectFormat.width;

               if(objectMaxY > currentMaxY) {
                  this.maxYSelectedAssembly = obj;
               }

               if(objectMaxX > currentMaxX) {
                  this.maxXSelectedAssembly = obj;
               }
            });
         }));

      this.zone.runOutsideAngular(() => {
         this.renderer.listen(
            "document", "mouseup", (evt: MouseEvent) => {
               const html: any = window.document.getElementsByTagName("html")[0];
               html.style.cursor = "";
               this.clearFocused(evt);
            });
      });

      this.setKeydownListener();
      this.viewsheet.scale = 1;
   }

   ngAfterViewInit(): void {
      Promise.resolve(null).then(() => this.refreshGridRows()); // Wait a tick for cd.
   }

   getVScrollWidth(ele: ElementRef): number {
      if(!ele || !ele.nativeElement) {
         return 0;
      }

      let vScrollWidth: number = 0;

      let scrollHeight = ele.nativeElement.scrollHeight;
      let offsetHeight = ele.nativeElement.offsetHeight;

      if(scrollHeight > offsetHeight) {
         let offsetWidth = ele.nativeElement.offsetWidth;
         let clientWidth = ele.nativeElement.clientWidth;
         vScrollWidth = offsetWidth - clientWidth;
      }

      return vScrollWidth;
   }

   ngOnDestroy(): void {
      this.removeKeydownListener();
      this.cleanup();
      this.subscriptions.unsubscribe();
   }

   @Input() set runtimeId(runtimeId: string) {
      this.viewsheet.runtimeId = runtimeId;
   }

   @Input() set linkUri(linkUri: string) {
      this.viewsheet.linkUri = linkUri;
   }

   @HostListener("window:resize")
   resizeListener(): void {
      this.refreshGridRows();
   }

   refreshGridRows() {
      if(!this.paneContainer) {
         return;
      }

      let bottom = 0;

      this.viewsheet.vsObjects.forEach((vsobject) => {
         let format = vsobject.objectFormat;
         bottom = Math.max(format.height + format.top, bottom);
      });

      let minRowCount = Math.ceil(bottom / VSWizardConstants.GRID_CELL_HEIGHT);
      let h = this.paneContainer.nativeElement.offsetHeight;
      let rowCount = Math.floor(h /  VSWizardConstants.GRID_CELL_HEIGHT) - 2;
      this.gridRowCount = Math.max(minRowCount, rowCount);
      this.newObjectModel = this.getBottomRight();
   }

   private setKeydownListener() {
      if(this.keydownListener == null) {
         this.zone.runOutsideAngular(() => {
            this.keydownListener = this.renderer.listen(
               "document", "keydown", (e) => this.onKeydown(e));
         });
      }
   }

   private removeKeydownListener() {
      if(!!this.keydownListener) {
         this.keydownListener();
         this.keydownListener = null;
      }
   }

   get undoEnabled(): boolean {
      return this.viewsheet && this.viewsheet.current > 0 && !this.viewsheet.loading;
   }

   get redoEnabled(): boolean {
      return this.viewsheet && this.viewsheet.current < this.viewsheet.points - 1 &&
         !this.viewsheet.loading;
   }

   private onKeydown(event: KeyboardEvent) {
      if(event.target instanceof HTMLInputElement || event.target instanceof HTMLTextAreaElement) {
         return;
      }

      if(event.ctrlKey || event.metaKey) {
         // ctrl-z
         if(this.undoEnabled && event.keyCode == KeyCodeValue.Z) {
            this.viewsheetClient.sendEvent("/events/undo");
         }
         // ctrl-y
         else if(this.redoEnabled && event.keyCode == KeyCodeValue.Y) {
            this.viewsheetClient.sendEvent("/events/redo");
         }
      }
      else if(event.keyCode == KeyCodeValue.DELETE) {
         let objs: string[] = this.viewsheet.currentFocusedAssemblies.map(obj => obj.absoluteName);

         if(objs.length > 0) {
            this.remove(objs);
         }
      }
      //up
      else if(event.keyCode == KeyCodeValue.UP && this.moveable(event.keyCode)) {
         let offset: number = VSWizardConstants.GRID_CELL_HEIGHT;

         this.viewsheet.currentFocusedAssemblies.forEach((assembly: VSObjectModel) => {
            const format = assembly.objectFormat;
            format.top = Math.max(format.top - offset, 0);
            this.moveWizardObject({model: assembly}, true);
         });
      }
      //down
      else if(event.keyCode == KeyCodeValue.DOWN && this.moveable(event.keyCode)) {
         let offset: number = VSWizardConstants.GRID_CELL_HEIGHT;

         this.viewsheet.currentFocusedAssemblies.forEach((assembly: VSObjectModel) => {
            assembly.objectFormat.top += offset;
            this.moveWizardObject({model: assembly}, true);
         });
      }
      //left
      else if(event.keyCode == KeyCodeValue.LEFT && this.moveable(event.keyCode)) {
         let offset: number = VSWizardConstants.GRID_CELL_WIDTH;

         this.viewsheet.currentFocusedAssemblies.forEach((assembly: VSObjectModel) => {
            const format = assembly.objectFormat;

            if(format.left > 0) {
               format.left = Math.max(format.left - offset, 0);
               this.moveWizardObject({model: assembly}, true);
            }
         });
      }
      //right
      else if(event.keyCode == KeyCodeValue.RIGHT && this.moveable(event.keyCode)) {
         let offset: number = VSWizardConstants.GRID_CELL_WIDTH;

         this.viewsheet.currentFocusedAssemblies.forEach((assembly: VSObjectModel) => {
            assembly.objectFormat.left += offset;
            this.moveWizardObject({model: assembly}, true);
            this.changeCols(assembly);
         });
      }
   }

   moveable(keyCode: number): boolean {
      let assemblies: VSObjectModel[] = this.viewsheet.currentFocusedAssemblies;
      let move: VSObjectModel = null;

      if(keyCode == KeyCodeValue.LEFT) {
         move = assemblies.find((assembly: VSObjectModel) => {
            return assembly.objectFormat.left == 0;
         });
      }
      else if(keyCode == KeyCodeValue.UP) {
         move = assemblies.find((assembly: VSObjectModel) => {
            return assembly.objectFormat.top == 0;
         });
      }

      return move == null;
   }

   getAssemblyName() {
      return null;
   }

   trackByFn(index: number, object: VSObjectModel) {
      return object.absoluteName;
   }

   close(save: boolean): void {
      if(save) {
         this.onFinish.emit(this.gridRowCount);
      }
      else {
         this.onCancel.emit();
      }
   }

   editWizardObject(objectName: string): void {
      this.viewsheet.vsObjects.forEach((vsobject) => {
         if(vsobject.absoluteName === objectName) {
            this.onChangeCurrentObject.emit(vsobject);
            const imageExplore: boolean = this.openImageExplore(vsobject.objectType);

            if(!imageExplore) {
               this.toComponentWizard.emit({
                  objectName: objectName,
                  objectType: vsobject.objectType
               });
            }

            return;
         }
      });
   }

   changeFollowDirection(followDirection: FollowDirection): void {
      if(!this.drageWithShift) {
         return;
      }

      if(followDirection.bounds) {
         this.followDirection.bounds = followDirection.bounds;
      }

      if(followDirection.direction) {
         this.followDirection.direction = followDirection.direction;

         if(followDirection.direction == "right") {
            this.followAssemblies = this.rightFollowAssemblies;
         }
         else if(followDirection.direction == "bottom") {
            this.followAssemblies = this.bottomFollowAssemblies;
         }
      }
   }

   isFollow(name: string) {
      return this.followDirection.direction != "none" && this.followAssemblies.get(name);
   }

   openImageExplore(objectType: string): boolean {
      if(objectType != "VSImage") {
         return false;
      }

      this.uploadInput.nativeElement.value = "";
      this.uploadInput.nativeElement.click();

      return true;
   }

   removeWizardObject(objectName: string) {
      this.remove([objectName]);
   }

   remove(objects: string[]) {
      const vsRemoveEvent: RemoveVSObjectsEvent = new RemoveVSObjectsEvent(objects);
      vsRemoveEvent.wizardGridRows = this.gridRowCount;
      vsRemoveEvent.wizardGridCols = this.gridColCount;
      this.viewsheetClient.sendEvent(REMOVE_VS_WIZARD_OBJECT_URI, vsRemoveEvent);
   }

   private processSetWizardGridCommand(command: SetWizardGridCommand): void {
      this.gridRowCount = command.gridRowCount;
      this.gridColCount = command.gridColCount;
      this.originalCols = this.gridColCount;

      this.refreshGridRows();
   }

   private processAssemblyMoved(model: VSObjectModel, moveRight: boolean, moveDown: boolean,
                                moveLeft: boolean, moveUp: boolean,
                                offsetX: number, offsetY: number): void
   {
      if(this.moveTimeout) {
         clearTimeout(this.moveTimeout);
         this.moveTimeout = null;
      }

      if(model) {
         let right = model.objectFormat.left + model.objectFormat.width;
         let bottom = model.objectFormat.top + model.objectFormat.height;
         let left = model.objectFormat.left;
         let top = model.objectFormat.top;

         // calculate right, bottom, left, top based on all selected assemblies
         this.viewsheet.currentFocusedAssemblies
            .filter(a => !a.interactionDisabled && a !== model)
            .forEach((assembly) => {
               const right2 = assembly.objectFormat.left + assembly.objectFormat.widht;
               const bottom2 = assembly.objectFormat.top + assembly.objectFormat.height;
               const left2 = assembly.objectFormat.left;
               const top2 = assembly.objectFormat.top;
               right = right2 > right ? right2 : right;
               bottom = bottom2 > bottom ? bottom2 : bottom;
               left = left2 < left ? left2 : left;
               top = top2 < top ? top2 : top;
            });

         let scrollPane = !!this.scrollContainer ? this.scrollContainer.nativeElement : null;

         if(!scrollPane) {
            return;
         }

         const paddingRight = 30;
         const paddingLeft = 30;

         const {clientHeight, scrollLeft, scrollTop, clientWidth} = scrollPane;
         const autoDown = moveDown && clientHeight + scrollTop < bottom + 15;
         const autoRight = moveRight && clientWidth - paddingRight - paddingLeft + scrollLeft <
            right + 15;
         const autoLeft = moveLeft && scrollLeft >= left - 15 && scrollLeft > 0;
         const autoUp = moveUp && scrollTop >= top - 15 && scrollTop > 0;

         if(autoDown || autoLeft || autoUp || autoRight) {
            this.moveTimeout = setTimeout(() => {
               this.moveTimeout = null;

               if(autoLeft) {
                  scrollPane.scrollLeft =
                     Math.max(0, scrollPane.scrollLeft - 10);
               }
               else if(autoRight) {
                  scrollPane.scrollLeft =
                     Math.max(0, scrollPane.scrollLeft + 10);
               }

               if(autoDown) {
                  scrollPane.scrollTop =
                     Math.max(0, scrollPane.scrollTop + 10);
               }
               else if(autoUp) {
                  scrollPane.scrollTop =
                     Math.max(0, scrollPane.scrollTop - 10);
               }

               // move each selected assembly in the same direction
               this.viewsheet.currentFocusedAssemblies
                  .filter(a => !a.interactionDisabled)
                  .map(a => a.dragObj ? a.dragObj : a)
                  .forEach((assembly) => {
                     if(autoLeft) {
                        assembly.objectFormat.left -= 10;
                     }
                     else if(autoRight) {
                        assembly.objectFormat.left += 10;
                     }

                     if(autoDown) {
                        assembly.objectFormat.top += 10;
                     }
                     else if(autoUp) {
                        assembly.objectFormat.top -= 10;
                     }

                     this.processAssemblyMoved(assembly, autoRight, autoDown, autoLeft,
                       autoUp, offsetX, offsetY);
                  });
            }, 50);
         }

         this.changeRows(model);
         this.changeCols(model);
      }
   }

   public fileChanged(event: any) {
      let fileList: FileList = event.target.files;

      if (fileList.length > 0) {
         let file: File = fileList[0];
         let formData: FormData = new FormData();
         formData.append("file", file);
         this.http.post<VSObjectModel>(UPLOAD_IMAGE_URI + "/"
            + this.currentVSObject.absoluteName +
            "/" + this.viewsheet.runtimeId, formData)
            .subscribe(
               (vsModel: VSObjectModel) => {
                  if (vsModel) {
                     this.refreshVSObject(vsModel);
                  }
                  else {
                     ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
                        "_#(js:composer.uploadImageFailed)");
                  }
               },
               (err: any) => {
                  ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
                     "_#(js:composer.uploadImageFailed)");
               }
            );
      }
   }

   private replaceObject(newModel: VSObjectModel, index: number): void {
      this.viewsheet.vsObjects[index] = VSUtil.replaceObject(this.viewsheet.vsObjects[index],
         newModel);
      this.viewsheet.updateSelectedAssembly(this.viewsheet.vsObjects[index]);
   }

   changeRows(model: VSObjectModel | number): void {
      if(typeof model == "number") {
         let gridRow = Math.ceil(model / this.gridCellHeight);

         if(gridRow > this.gridRowCount) {
            this.gridRowCount = gridRow;
         }
      }
      else {
         if(model.absoluteName == this.maxYSelectedAssembly.absoluteName) {
            let maxY = model.objectFormat.top + model.objectFormat.height;
            let row = Math.ceil(maxY / this.gridCellHeight);

            if(row >= this.gridRowCount) {
               this.gridRowCount = row;
            }
         }
      }
   }

   changeCols(model: VSObjectModel | number, autoScroll?: boolean): void {
      let oldCol = this.gridColCount;

      if(typeof model == "number") {
         let gridCol = Math.ceil(model / this.gridCellWidth);

         if(gridCol >= this.gridColCount) {
            this.gridColCount = gridCol;
         }
      }
      else {
         if(model.absoluteName == this.maxYSelectedAssembly.absoluteName) {
            let maxX = model.objectFormat.left + model.objectFormat.width;
            let col = Math.ceil(maxX / this.gridCellWidth);

            if(col >= this.gridColCount) {
               this.gridColCount = col;
            }
         }
      }

      if(this.gridColCount % 2 != 0) {
         this.gridColCount++;
      }

      if(autoScroll && this.gridColCount != oldCol && !!this.scrollContainer) {
         setTimeout(() => {
            let scrollWidth = this.scrollContainer.nativeElement.scrollWidth;
            this.scrollContainer.nativeElement.scrollLeft = scrollWidth;
         }, 0);

      }
   }

   dragResizeStart(options: DragMoveStartOptions): void {
      this.originalRows = this.gridRowCount;
      this.originalCols = this.gridColCount;
      this.newObjectModel = {...this.newObjectModel, visible: false};
      this.moving = true;
      this.changeRef.detectChanges();

      if(this.viewsheet.currentFocusedAssemblies.length > 1) {
         return;
      }

      this.drageWithShift = options.withShift;

      if(this.viewsheet.currentFocusedAssemblies.length == 1 && options.isDrag) {
         this.prepareBottomFollowAssemblies(options.objectModel);
         this.prepareRightFollowAssemblies(options.objectModel);
      }
   }

   dragResizeEnd() {
      this.newObjectModel = this.getBottomRight();
      this.changeRef.detectChanges();
      setTimeout(() => this.moving = false, 500);
   }

   mergeDimension(first: Rectangle, second: Rectangle): Rectangle {
      let x = Math.min(first.x, second.x);
      let y = Math.min(first.y, second.y);
      let width = Math.max(first.x + first.width, second.x + second.width) - x;
      let height = Math.max(first.y + first.height, second.y + second.height) - y;

      return new Rectangle(x, y, width, height);
   }

   prepareBottomFollowAssemblies(assembly: VSObjectModel) {
      this.bottomFollowAssemblies.clear();
      let vsObjects: VSObjectModel[] = this.viewsheet.vsObjects;
      vsObjects.sort(this.sortByYPosition);
      let format = assembly.objectFormat;
      let influencingArea: Rectangle = new Rectangle(format.left, format.top, format.width,
         this.gridRowCount * this.gridCellHeight - format.top);

      vsObjects.forEach(obj => {
         let rectangle = new Rectangle(obj.objectFormat.left, obj.objectFormat.top,
            obj.objectFormat.width, obj.objectFormat.height);

         if(influencingArea.intersects(rectangle)) {
            this.bottomFollowAssemblies.set(obj.absoluteName, true);
            influencingArea = this.mergeDimension(influencingArea, rectangle);
         }
      });

      //this.prepareFollowRestriction(assembly, false);
   }

   prepareFollowRestriction(assembly: VSObjectModel, right: boolean) {
      const containerElement = this.scrollContainer.nativeElement;
      const containerRect = GuiTool.getElementRect(containerElement);
      const element = containerElement.querySelector(
         `.wizard-object-editor[data-name='${assembly.absoluteName}']`);

      if(!element) {
         return;
      }

      const elementRect = GuiTool.getElementRect(element);
      let padding = 30;
      let offsetX = 0;
      let offsetY = 0;
      let offsetRight = 0;
      let followAssemblies: Map<string, boolean>;

      if(right) {
         followAssemblies = this.rightFollowAssemblies;
      }
      else {
         followAssemblies = this.bottomFollowAssemblies;
      }

      if(!followAssemblies) {
         return;
      }

      let names: IterableIterator<string> = followAssemblies.keys();

      for(let  i = 0; i < followAssemblies.size; i++) {
         let iteratorResult = names.next();
         let name = !iteratorResult ? "" : iteratorResult.value;

         const assemblyElement = containerElement.querySelector(
            `.wizard-object-editor[data-name='${name}']`);

         if(assemblyElement) {
            const assemblyRect = GuiTool.getElementRect(assemblyElement);
            offsetX = Math.max(offsetX, elementRect.left - assemblyRect.left);
            offsetY = Math.max(offsetY, elementRect.top - assemblyRect.top);
            offsetRight = Math.max(offsetRight, assemblyRect.right - elementRect.right);
         }
      }

      let scrollVWidth = this.getVScrollWidth(this.scrollContainer);

      let followRestriction = {
         top: containerRect.top + offsetY,
         left: containerRect.left + padding + offsetX,
         bottom: Number.MAX_VALUE,
         right: containerRect.right - offsetRight - padding - scrollVWidth - elementRect.width
      };

      if(right) {
         this.rightFollowRestriction = followRestriction;
      }
      else {
         this.bottomFollowRestriction = followRestriction;
      }
   }

   prepareRightFollowAssemblies(assembly: VSObjectModel) {
      this.rightFollowAssemblies.clear();
      let vsObjects: VSObjectModel[] = this.viewsheet.vsObjects;
      vsObjects.sort(this.sortByXPosition);
      let format = assembly.objectFormat;
      let influencingArea: Rectangle = new Rectangle(format.left, format.top,
         this.gridColCount * this.gridCellWidth - format.left, format.height);

      vsObjects.forEach(obj => {
         let rectangle = new Rectangle(obj.objectFormat.left, obj.objectFormat.top,
            obj.objectFormat.width, obj.objectFormat.height);

         if(influencingArea.intersects(rectangle)) {
            this.rightFollowAssemblies.set(obj.absoluteName, true);
            influencingArea = this.mergeDimension(influencingArea, rectangle);
         }
      });

      //this.prepareFollowRestriction(assembly, true);
   }

   sortByXPosition(obj1: VSObjectModel, obj2: VSObjectModel): number {
      let format1 = obj1.objectFormat;
      let format2 = obj2.objectFormat;

      if(format1.left > format2.left) {
         return 1;
      }
      else if(format1.left < format2.left) {
         return -1;
      }
      else {
         if(format1.top == format2.top) {
            return 0;
         }

         return format1.top > format2.top ? 1 : -1;
      }

      return 0;
   }

   sortByYPosition(obj1: VSObjectModel, obj2: VSObjectModel): number {
      let format1 = obj1.objectFormat;
      let format2 = obj2.objectFormat;

      if(format1.top > format2.top) {
         return 1;
      }
      else if(format1.top < format2.top) {
         return -1;
      }
      else {
         if(format1.left == format2.left) {
            return 0;
         }

         return format1.left > format2.left ? 1 : -1;
      }

      return 0;
   }

   insertObject(type: AssemblyType, point: Point) {
      this.newObjectModel = {...this.newObjectModel, visible: false};
      let event: AddNewVSObjectEvent =  new AddNewVSObjectEvent(
         type, point.x, point.y, true);
      event.wizardCurrentGridRow = this.gridRowCount;
      event.wizardCurrentGridCol = this.gridColCount;
      this.viewsheetClient.sendEvent(INSERT_OBJECT_URI, event);
   }

   changeNewObject(model: WizardNewObjectModel) {
      if(!this.moving) {
         this.newObjectModel = model;

         // if the grid is empty, show the newObject icon in the middle
         if(!model.visible) {
            this.newObjectModel = this.getBottomRight();
         }
      }
      // don't increase grid on mouse move. should only increase on dragging/resizing
      else if(model.bounds) {
         this.changeCols(model.bounds.x + model.bounds.width, true);
      }
   }

   // move new-object out of wizard object
   mouseOnWizardObject() {
      if(this.newObjectModel.visible) {
         this.newObjectModel = this.getBottomRight();
         this.changeRef.detectChanges();
      }
   }

   private getBottomRight(): WizardNewObjectModel {
      let bottom: number = 0;

      this.viewsheet.vsObjects
         .map(vsobj => vsobj.objectFormat)
         .forEach(fmt => bottom = Math.max(fmt.top + fmt.height, bottom));

      // top of all the components at the bottom
      let bottomTop: number = bottom;
      this.viewsheet.vsObjects
         .map(vsobj => vsobj.objectFormat)
         .filter(fmt => fmt.top + fmt.height == bottom)
         .forEach(fmt => bottomTop = Math.min(fmt.top, bottomTop));

      // right edge of the bottom row of components
      let bottomRight: number = 0;
      this.viewsheet.vsObjects
         .map(vsobj => vsobj.objectFormat)
         .filter(fmt => fmt.top >= bottomTop)
         .forEach(fmt => bottomRight = Math.max(fmt.left + fmt.width, bottomRight));

      const newObj = {... this.defaultObjectModel, visible: true};
      const colW = VSWizardConstants.GRID_CELL_WIDTH;

      if(bottomRight + this.defaultObjectModel.bounds.width > this.gridColCount * colW) {
         newObj.bounds.y = bottom + colW;
         newObj.bounds.x = colW;
      }
      else {
         newObj.bounds.x = bottomRight + colW;
         newObj.bounds.y = Math.max(colW, bottomTop);
      }

      return newObj;
   }

   resizeObject(vsObject: VSObjectModel) {
      let vsEvent: ResizeVSObjectEvent = new ResizeVSObjectEvent(vsObject.absoluteName,
         vsObject.objectFormat.left, vsObject.objectFormat.top, vsObject.objectFormat.width,
         vsObject.objectFormat.height);
      vsEvent.wizardGridRows = this.gridRowCount;
      vsEvent.wizardGridCols = this.gridColCount;
      vsEvent.autoLayoutHorizontal = this.autoLayoutHorizontal;

      this.viewsheetClient.sendEvent(VIEWSHEET_WIZARD_OBJECTS_RESIZE_URI, vsEvent);
   }

   moveWizardObject(data: any, keyMove?: boolean) {
      let vsObject = data.model;
      let event = data.event;

      if(!!vsObject && !!event && event.type == "dragmove") {
         this.moveAssembly(event, vsObject);
      }
      else if(!!vsObject) {
         this.moveAssembly(event, null);
         this.draggableRestrictionRects = null;

         if(keyMove) {
            this.refreshGridRows();
         }

         let vsEvent: MoveVSObjectEvent = new MoveVSObjectEvent(vsObject.absoluteName,
            vsObject.objectFormat.left, vsObject.objectFormat.top);
         vsEvent.wizardGridRows = this.gridRowCount;
         vsEvent.wizardGridCols = this.gridColCount;
         vsEvent.autoLayoutHorizontal = this.autoLayoutHorizontal;
         vsEvent.moveRowOrCol = this.drageWithShift;
         this.eventQueueService.addWizardMoveEvent(this.viewsheetClient, vsEvent);
      }
   }

   moveAssembly(event: any, model: VSObjectModel): void {
      let down = false;
      let right = false;
      let offsetX = 0;
      let offsetY = 0;

      if(event) {
         let pointerDeltaX = 0;
         let pointerDeltaY = 0;

         if(event.interaction && event.interaction.pointerDelta) {
            pointerDeltaX = event.interaction.pointerDelta.client.x;
            pointerDeltaY = event.interaction.pointerDelta.client.y;
         }

         right = event.dx > 0 || (event.dx === 0 && pointerDeltaX > 0);
         down = event.dy > 0 || (event.dy === 0 && pointerDeltaY > 0);

         if(event.interaction && event.interaction.downEvent) {
            offsetX = event.interaction.downEvent.offsetX || 0;
            offsetY = event.interaction.downEvent.offsetY || 0;
         }
      }

      this.processAssemblyMoved(model, right, down, !right, !down, offsetX, offsetY);
   }

   clearFocused(event: any): void {
      if(this.shouldClearFocused(event)) {
         this.viewsheet.clearFocusedAssemblies();
      }
   }

   private shouldClearFocused(event: any): boolean {
      return !!event && !!event.target && !!event.target.classList &&
         event.target.classList.contains("vs-grid-table-cell") ||
         event.target.classList.contains("wizard-add");
   }

   onSelectionBox(event: SelectionBoxEvent): void {
      let scaledBox = event.box;

      const selectedAssemblies = this.viewsheet.vsObjects.filter((vsObject) => {
         const format = vsObject.objectFormat;
         let vsObjectRect = new Rectangle(format.left, format.top, format.width, format.height);
         return vsObjectRect.intersects(scaledBox);
      });

      this.viewsheet.currentFocusedAssemblies = selectedAssemblies;
   }

   private isTempAssembly(model: VSObjectModel): boolean {
      return !!model && (model.absoluteName.startsWith(VSWizardConstants.TEMP_ASSEMBLY_PREFIX)
         || model.absoluteName.startsWith(VSWizardConstants.TEMP_CROSSTAB_NAME)
         || model.absoluteName == VSWizardConstants.TEMP_CHART_NAME);
   }

   private processUploadImageCommand(command: UploadImageCommand): void {
      let imgModel: VSImageModel = <VSImageModel> command.uploadObject;
      this.onChangeCurrentObject.emit(command.uploadObject);

      this.viewsheet.vsObjects.forEach((vsobject, index) => {
         if(imgModel && vsobject.absoluteName == imgModel.absoluteName) {
            this.replaceObject(imgModel, index);
         }
      });

      if((<VSImageModel> command.uploadObject).noImageFlag) {
         // Bug #37104. Cannot convert immediately for the edge browser.
         this.currentVSObject = imgModel;
         this.openImageExplore(command.uploadObject.objectType);
      }
   }

   private processAddVSObjectCommand(command: AddVSObjectCommand): void {
      if(this.isTempAssembly(command.model)) {
         return;
      }

      let update = false;

      for(let i = 0; i < this.viewsheet.vsObjects.length; i++) {
         if(this.viewsheet.vsObjects[i].absoluteName === command.name) {
            this.replaceObject(command.model, i);
            return;
         }
      }

      this.viewsheet.vsObjects.push(command.model);
      this.viewsheet.variableNames = VSUtil.getVariableList(this.viewsheet.vsObjects, null);
      this.viewsheet.vsObjects.forEach((vsObject, idx) => {
         if(this.viewsheet.currentFocusedAssemblies.length > 0 && vsObject.absoluteName ===
            this.viewsheet.currentFocusedAssemblies[0].absoluteName)
         {
            this.viewsheet.clearFocusedAssemblies();
            this.viewsheet.selectAssembly(vsObject);
         }
      });
   }

   private processRemoveVSObjectCommand(command: RemoveVSObjectCommand): void {
      this.viewsheet.vsObjects.forEach((vsObject, index) => {
         if(vsObject.absoluteName === command.name) {
            this.viewsheet.vsObjects.splice(index, 1);
         }
      });
   }

   private processRefreshVSObjectCommand(command: RefreshVSObjectCommand): void {
      this.refreshVSObject(command.info);
   }

   refreshVSObject(obj: VSObjectModel) {
      let updated: boolean = false;

      for(let i = 0; i < this.viewsheet.vsObjects.length; i++) {
         if(this.viewsheet.vsObjects[i].absoluteName === obj.absoluteName) {
            this.replaceObject(obj, i);
            updated = true;
            break;
         }
      }

      for(let i = 0; i < this.viewsheet.currentFocusedAssemblies.length; i++) {
         if(this.viewsheet.currentFocusedAssemblies[i].absoluteName == obj.absoluteName) {
            this.viewsheet.currentFocusedAssemblies[i] = obj;
            this.viewsheet.focusedAssembliesChanged();
         }
      }

      if(!updated) {
         if(!this.isTempAssembly(obj)) {
            this.viewsheet.vsObjects.push(obj);
         }

         this.viewsheet.variableNames = VSUtil.getVariableList(this.viewsheet.vsObjects, null);
      }
   }

   /**
    * Used to update undo/redo state of vs.
    * @param {UpdateUndoStateCommand} command
    */
   private processUpdateUndoStateCommand(command: UpdateUndoStateCommand): void {
      this.viewsheet.points = command.points;
      this.viewsheet.current = command.current;
      this.viewsheet.currentTS = (new Date()).getTime();
      this.viewsheet.savePoint = command.savePoint;
   }

   getFollowDirSrc(): string {
      if(this.followDirection.direction == "bottom") {
         return "assets/vs-wizard/arrow-down.svg";
      }
      else if(this.followDirection.direction == "right"){
         return "assets/vs-wizard/arrow-right.svg";
      }

      return "";
   }

   hiddenNewBlockChanged(): void {
      this.onHiddenNewBlockChanged.emit(!this.hiddenNewBlock);
   }

   keyDown(event: KeyboardEvent) {
      // clear selection on esc
      if(event.keyCode == 27) {
         this.viewsheet.clearFocusedAssemblies();
      }
   }
}
