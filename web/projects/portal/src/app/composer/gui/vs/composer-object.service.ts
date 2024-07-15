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
import { ElementRef, Injectable, NgZone } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { AssetEntry } from "../../../../../../shared/data/asset-entry";
import { TableTransfer } from "../../../common/data/dnd-transfer";
import { TrapInfo } from "../../../common/data/trap-info";
import { Rectangle } from "../../../common/data/rectangle";
import { BaseFormatModel } from "../../../common/data/base-format-model";
import { Tool } from "../../../../../../shared/util/tool";
import { GuiTool } from "../../../common/util/gui-tool";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { VSLineModel } from "../../../vsobjects/model/vs-line-model";
import { VSObjectModel } from "../../../vsobjects/model/vs-object-model";
import { VSTabModel } from "../../../vsobjects/model/vs-tab-model";
import { VSUtil } from "../../../vsobjects/util/vs-util";
import { ModelService } from "../../../widget/services/model.service";
import { VSTrapService } from "../../../vsobjects/util/vs-trap.service";
import { Viewsheet } from "../../data/vs/viewsheet";
import { AssemblyType } from "./assembly-type";
import { AddNewVSObjectEvent } from "./objects/event/add-new-vs-object-event";
import { ChangeVSObjectBindingEvent } from "./objects/event/change-vs-object-binding-event";
import { ChangeVSObjectLayerEvent } from "./objects/event/change-vs-object-layer-event";
import { CopyVSObjectEvent } from "./event/copy-vs-objects-event";
import { MoveVSObjectEvent } from "./objects/event/move-vs-object-event";
import { ResizeVSLineEvent } from "./objects/event/resize-vs-line-event";
import { ResizeVSObjectEvent } from "./objects/event/resize-vs-object-event";
import { RemoveVSObjectsEvent } from "./objects/event/remove-vs-objects-event";
import { ResizeVsObjectTitleEvent } from "./objects/event/resize-vs-object-title-event";
import { EventQueueService } from "./event-queue.service";
import { LineAnchorService } from "../../services/line-anchor.service";
import { UIContextService } from "../../../common/services/ui-context.service";
import { AssetType } from "../../../../../../shared/data/asset-type";
import { map } from "rxjs/operators";
import { ComponentTool } from "../../../common/util/component-tool";
import { DebounceService } from "../../../widget/services/debounce.service";

const DRAG_NAMES_MAP = new Map<string, AssemblyType>([
   ["dragchart", AssemblyType.CHART_ASSET],
   ["dragcrosstab", AssemblyType.CROSSTAB_ASSET],
   ["dragtable", AssemblyType.TABLE_VIEW_ASSET],
   ["dragfreehandtable", AssemblyType.FORMULA_TABLE_ASSET],
   ["dragselectionlist", AssemblyType.SELECTION_LIST_ASSET],
   ["dragselectiontree", AssemblyType.SELECTION_TREE_ASSET],
   ["dragrangeslider", AssemblyType.TIME_SLIDER_ASSET],
   ["dragcalendar", AssemblyType.CALENDAR_ASSET],
   ["dragselectioncontainer", AssemblyType.CURRENTSELECTION_ASSET],
   ["dragtext", AssemblyType.TEXT_ASSET],
   ["dragimage", AssemblyType.IMAGE_ASSET],
   ["draggauge", AssemblyType.GAUGE_ASSET],
   ["dragslider", AssemblyType.SLIDER_ASSET],
   ["dragspinner", AssemblyType.SPINNER_ASSET],
   ["dragcheckbox", AssemblyType.CHECKBOX_ASSET],
   ["dragradiobutton", AssemblyType.RADIOBUTTON_ASSET],
   ["dragcombobox", AssemblyType.COMBOBOX_ASSET],
   ["dragtextinput", AssemblyType.TEXTINPUT_ASSET],
   ["dragsubmit", AssemblyType.SUBMIT_ASSET],
   ["dragupload", AssemblyType.UPLOAD_ASSET],
   ["dragline", AssemblyType.LINE_ASSET],
   ["dragrectangle", AssemblyType.RECTANGLE_ASSET],
   ["dragoval", AssemblyType.OVAL_ASSET],
   ["viewsheet", AssemblyType.VIEWSHEET_ASSET]
]);

const VIEWSHEET_OBJECTS_URI = "/events/composer/viewsheet/objects/";
const RESIZE_VSLINE_URI = "/events/composer/viewsheet/vsLine/resize";
const CHECK_TRAP_URI = "../api/composer/viewsheet/objects/checkTrap";
const CHECK_ASSEMBLY_IN_USE_URI = "../api/composer/viewsheet/objects/checkAssemblyInUse/";
const ASSEMBLY_IN_USE = "_#(js:fl.action.alertAssemblyInUse)";

export interface KeyEventAdapter {
   getElement: () => ElementRef;
   getViewsheet: () => Viewsheet;
   getVsObject: () => VSObjectModel;
   isSelected: () => boolean;
}

export interface DependentAssemblies {
   assemblies: Map<string, string>;
}

@Injectable()
export class ComposerObjectService {
   private keyEventAdapters: KeyEventAdapter[] = [];
   private layoutObjectsKeyEventAdapters: KeyEventAdapter[] = [];

   constructor(private modelService: ModelService,
               private modalService: NgbModal,
               private trapService: VSTrapService,
               private eventQueueService: EventQueueService,
               private uiContextService: UIContextService,
               private lineAnchorService: LineAnchorService,
               private debounceService: DebounceService,
               private zone: NgZone) {
   }

   public addKeyEventAdapter(adapter: KeyEventAdapter): void {
      this.keyEventAdapters.push(adapter);
   }

   public addLayoutObjectKeyEventAdapter(adapter: KeyEventAdapter): void {
      this.layoutObjectsKeyEventAdapters.push(adapter);
   }

   public removeKeyEventAdapter(adapter: KeyEventAdapter): void {
      const index = this.keyEventAdapters.indexOf(adapter);

      if(index >= 0) {
         this.keyEventAdapters.splice(index, 1);
      }
   }

   public handleKeyEvent(event: KeyboardEvent, snap: boolean, gridSize: number) {
      let adapters = this.keyEventAdapters.filter(
         (adapter) => this.isKeyEventApplied(event.target, adapter));

      // no key events applied - nothing to do
      if(adapters.length === 0) {
         return;
      }

      if(event.keyCode == 46) { // delete
         const adapterMap = new Map<string, KeyEventAdapter>();
         adapters.forEach(adapter => adapterMap.set(adapter.getVsObject().absoluteName, adapter));
         const toRemove: string[] = [];

         adapters.forEach((adapter) => {
            const model = adapter.getVsObject();

            // the behavior for tab removal seems to be this:
            //
            // 1. if an inactive child of the tab is selected, ignore it
            // 2. if the tab and the active child are selected, only remove the selected child
            // 3. if the only the tab is selected (no children), only remove the tab
            // 4. if all(active/inactive) children of the tab are selected, remove the tab and all the children
            //
            // so the logic is this:
            //
            // 1. remove all inactive tab children from the selection
            // 2. if the selection contains both the tab and the active child,
            // remove the tab from the selection
            if(model.container && adapterMap.has(model.container)) {
               const container = adapterMap.get(model.container).getVsObject();

               if(container.objectType === "VSTab") {
                  const tab = <VSTabModel> container;
                  let allChildrenSelected: boolean = true;

                  for(let name of tab.childrenNames) {
                     if(!adapterMap.has(name)) {
                        allChildrenSelected = false;
                        break;
                     }
                  }

                  if(!allChildrenSelected) {
                     if(tab.selected === model.absoluteName) {
                        // tab and active component are selected, only delete the active
                        // component - defer deleting tab until all selected assemblies are
                        // checked
                        toRemove.push(tab.absoluteName);
                     }
                     else {
                        // selection contains tab and inactive component, don't delete
                        // inactive component
                        adapterMap.delete(model.absoluteName);
                     }
                  }
                  else {
                     toRemove.push(tab.absoluteName);
                  }
               }
               else if(container.objectType === "VSSelectionContainer") {
                  toRemove.push(container.absoluteName);
               }
            }
         });

         toRemove.forEach((name) => adapterMap.delete(name));

         const objectNames: string[] = [];
         let viewsheet: Viewsheet = null;

         adapterMap.forEach((adapter) => {
            adapter.getViewsheet().deselectAssembly(adapter.getVsObject());
            objectNames.push(adapter.getVsObject().absoluteName);
            // the filter above only selects adapters for the focused viewsheet, so all
            // the items in the adapters array are guaranteed to have the same viewsheet
            // and it doesn't matter which one we use
            viewsheet = adapter.getViewsheet();
         });

         this.removeObjects(viewsheet, objectNames);
      }
      else {
         let moved = false;
         // Set the movement increment of unmodified arrow key presses to the gridSize
         // if the snap grid is present
         let offset = event.ctrlKey || event.shiftKey ? 1 : snap ? gridSize : 10;
         let objNames = [];

         adapters.forEach((adapter) => {
            let obj = adapter.getVsObject();

            if(obj.objectType != "VSGroupContainer") {
               objNames.push(obj.absoluteName);
            }
         });

         adapters.forEach((adapter) => {
            if((<any>adapter.getVsObject()).locked) {
               return;
            }

            let obj = adapter.getVsObject();
            let vs = adapters[0].getViewsheet();
            let objs = vs.vsObjects;

            // 1. For group container, when move object by key(up/down...), should only move its
            // children in group, and group will be changed according to its children. Should not
            // move container and child together, it will cause some error to position wrong.
            // 2. if select child node and group container, should not move child twitch. if select
            // the child, it will in adapters should not add here.
            if(obj.objectType == "VSGroupContainer") {
               for(let i = 0; i < objs.length; i++) {
                  let child = objs[i];

                  if(child.container != null && child.container == obj.absoluteName &&
                     objNames.indexOf(child.absoluteName) < 0)
                  {
                     this.moveObj(event, vs, child, offset);
                  }
               }
            }
            else {
               moved = this.moveObj(event, vs, obj, offset);
            }
         });

         if(moved) {
            // Bug #21552, if the VSPane has scrollbars, the pane will be scrolled instead
            // of/in addition to moving the objects.
            event.preventDefault();
            event.stopPropagation();
         }
      }
   }

   moveObj(event: KeyboardEvent, vs: Viewsheet, obj: VSObjectModel, offset): boolean {
      let moved = false;

      if(event.keyCode === 38) { // up
         if(obj.objectFormat.top > 0) {
            obj.objectFormat.top = Math.max(obj.objectFormat.top - offset, 0);
            this.moveObject(vs, obj);
            moved = true;
         }
      }
      else if(event.keyCode === 40) { // down
         obj.objectFormat.top += offset;
         this.moveObject(vs, obj);
         moved = true;
      }
      else if(event.keyCode === 37) { // left
         if(obj.objectFormat.left > 0) {
            obj.objectFormat.left = Math.max(obj.objectFormat.left - offset, 0);
            this.moveObject(vs, obj);
            moved = true;
         }
      }
      else if(event.keyCode === 39) { // right
         obj.objectFormat.left += offset;
         this.moveObject(vs, obj);
         moved = true;
      }

      return moved;
   }

   getObjectType(dragName: string): number {
      return DRAG_NAMES_MAP.get(dragName);
   }

   getObjectDefaultSize(dragName: string): {width: number, height: number} {
      return GuiTool.getDefaultObjectSize(dragName);
   }

   addNewObject(vs: Viewsheet, dragName: string, left: number, top: number,
                entry: AssetEntry, forceEditMode: boolean = false): void
   {
      if(!DRAG_NAMES_MAP.has(dragName)) {
         return;
      }

      let vsEvent: AddNewVSObjectEvent = new AddNewVSObjectEvent(
         DRAG_NAMES_MAP.get(dragName), left, top, forceEditMode);
      vsEvent.entry = entry;
      vsEvent.scale = vs.scale;

      this.uiContextService.objectAdded(dragName);
      vs.socketConnection.sendEvent(VIEWSHEET_OBJECTS_URI + "addNew", vsEvent);
   }

   copyObject(vs: Viewsheet, vsObject: VSObjectModel): void {
      let object: string[] = [vsObject.absoluteName];

      let vsEvent: CopyVSObjectEvent = new CopyVSObjectEvent(
         object, false, vsObject.objectFormat.left, vsObject.objectFormat.top, true);

      // combine to avoid race condition. (60835)
      this.debounceService.debounce(
         `copyObject.${vs.id}`,
         (evt) => {
            vs.socketConnection.sendEvent(VIEWSHEET_OBJECTS_URI + "copymove", evt);
         },
         500, [vsEvent],
         (prev, next) => {
            const evt1 = <CopyVSObjectEvent> prev.args[0];
            const evt2 = <CopyVSObjectEvent> next.args[0];
            const newEvent = new CopyVSObjectEvent(evt1.objects.concat(evt2.objects),
                                                   false, evt1.xOffset, evt1.yOffset, true);
            return {
               callback: prev.callback,
               args: [newEvent]
            };
         });
   }

   moveObject(vs: Viewsheet, vsObject: VSObjectModel): void {
      let vsEvent: MoveVSObjectEvent = new MoveVSObjectEvent(vsObject.absoluteName,
         vsObject.objectFormat.left, vsObject.objectFormat.top);
      vsEvent.scale = vs.scale;

      this.updateLayerMovement(vs, vsObject);
      this.eventQueueService.addMoveEvent(vs.socketConnection, vsEvent);
   }

   resizeObject(vs: Viewsheet, vsObject: VSObjectModel): void {
      let vsEvent: ResizeVSObjectEvent = new ResizeVSObjectEvent(vsObject.absoluteName,
         vsObject.objectFormat.left, vsObject.objectFormat.top, vsObject.objectFormat.width,
         vsObject.objectFormat.height);
      vsEvent.scale = vs.scale;

      vs.socketConnection.sendEvent(VIEWSHEET_OBJECTS_URI + "resize", vsEvent);
   }

   resizeObjectTitle(vs: Viewsheet, vsObject: VSObjectModel) {
      let titleFormat = (vsObject as any).titleFormat;

      if(!titleFormat) {
         return;
      }

      const titleHeight = titleFormat.height;
      let vsEvent: ResizeVsObjectTitleEvent = new ResizeVsObjectTitleEvent(vsObject.absoluteName,
         vsObject.objectFormat.left, vsObject.objectFormat.top, vsObject.objectFormat.width,
         vsObject.objectFormat.height, titleHeight);
      vsEvent.scale = vs.scale;

      vs.socketConnection.sendEvent(VIEWSHEET_OBJECTS_URI + "resizeTitle", vsEvent);
   }

   adjustTitleHeight(vsObject: VSObjectModel, newHeight: number) {
      let titleFormat = (vsObject as any).titleFormat;

      if(!titleFormat) {
         return;
      }

      const objectFormat = vsObject.objectFormat;
      const oldHeight = titleFormat.height;

      titleFormat.height = newHeight < GuiTool.MINIMUM_TITLE_HEIGHT
         ? GuiTool.MINIMUM_TITLE_HEIGHT : newHeight;
      objectFormat.height += newHeight < GuiTool.MINIMUM_TITLE_HEIGHT ? 0 : newHeight - oldHeight;
   }

   removeObjectFromList(vs: Viewsheet, objectName: string): void {
      for(let i in vs.vsObjects) {
         if(vs.vsObjects[i].absoluteName == objectName) {
            vs.deselectAssembly(vs.vsObjects[i]);
            vs.vsObjects.splice(parseInt(i, 10), 1);
            vs.variableNames = VSUtil.getVariableList(vs.vsObjects, null);
            break;
         }
      }
   }

   removeObjects(vs: Viewsheet, objectNames: string[]): void {
      const vsRemoveEvent: RemoveVSObjectsEvent = new RemoveVSObjectsEvent(objectNames);

      this.modelService.sendModel(CHECK_ASSEMBLY_IN_USE_URI + Tool.encodeURIPath(vs.runtimeId), objectNames).pipe(
         map(response => response.body))
         .subscribe((dependentAssemblies: DependentAssemblies) => {
               if(!!dependentAssemblies && !Tool.isEmpty(dependentAssemblies.assemblies)) {
                  let message: string = "";
                  const dependentMap: Map<string, string> = dependentAssemblies.assemblies;

                  for(const key in dependentMap) {
                     if(dependentMap.hasOwnProperty(key)) {
                        message += Tool.formatCatalogString(ASSEMBLY_IN_USE, [key, dependentMap[key]]);
                     }
                  }

                  this.zone.run(() => {
                     ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)", message,
                        {"yes": "_#(js:Yes)", "no": "_#(js:No)"})
                        .then((result: string) => {
                           if(result == "yes") {
                              objectNames.forEach(n => this.uiContextService.objectDeleted(n));
                              vs.socketConnection.sendEvent(
                                 VIEWSHEET_OBJECTS_URI + "removeAll", vsRemoveEvent);
                           }
                        });
                  });
               }
               else {
                  objectNames.forEach(n => this.uiContextService.objectDeleted(n));
                  vs.socketConnection.sendEvent(
                     VIEWSHEET_OBJECTS_URI + "removeAll", vsRemoveEvent);
               }
            },
            (err: any) => {
               console.error("Failed to delete object: ", err);
            });
   }

   moveFromContainer(vs: Viewsheet, vsObject: VSObjectModel): void {
      let vsEvent: MoveVSObjectEvent = new MoveVSObjectEvent(vsObject.absoluteName,
         vsObject.objectFormat.left, vsObject.objectFormat.top);
      vsEvent.scale = vs.scale;

      vs.socketConnection.sendEvent(VIEWSHEET_OBJECTS_URI + "moveFromContainer", vsEvent);
   }

   sendToFarthestIndex(vs: Viewsheet, vsObject: VSObjectModel, moveUp: boolean): void {
      let closestIndex: number = this.getFarthestIndex(vs, vsObject, moveUp);

      if(closestIndex == vsObject.objectFormat.zIndex) {
         return;
      }

      vsObject.objectFormat.zIndex = moveUp ? closestIndex + 1 : closestIndex - 1;

      let vsEvent: ChangeVSObjectLayerEvent = new ChangeVSObjectLayerEvent(vsObject.absoluteName,
         vsObject.objectFormat.zIndex);

      vs.socketConnection.sendEvent(VIEWSHEET_OBJECTS_URI + "changeZIndex", vsEvent);
   }

   shiftLayerIndex(vs: Viewsheet, vsObject: VSObjectModel, moveUp: boolean): void {
      let closestObj: VSObjectModel = this.getClosestObject(vs, vsObject, moveUp);

      if(!closestObj) {
         return;
      }

      const tempIndex: number = vsObject.objectFormat.zIndex;
      vsObject.objectFormat.zIndex = closestObj.objectFormat.zIndex;
      closestObj.objectFormat.zIndex = tempIndex;

      let vsEvent: ChangeVSObjectLayerEvent = new ChangeVSObjectLayerEvent(vsObject.absoluteName,
         vsObject.objectFormat.zIndex, closestObj.absoluteName);

      vs.socketConnection.sendEvent(VIEWSHEET_OBJECTS_URI + "changeZIndex", vsEvent);
   }

   updateLayerMovement(vs: Viewsheet, object: VSObjectModel): void {
      let zIndex: number = object.objectFormat.zIndex;
      let highest: number = -1;
      let lowest: number = 0xffff;

      object.covered = false;

      if(object.containerType === "VSTab") {
         return;
      }

      vs.vsObjects
         .filter(v => v.container == object.container)
         .forEach(v => {
            let z2: number = v.objectFormat.zIndex;
            highest = Math.max(highest, z2);
            lowest = Math.min(lowest, z2);

            if(!object.covered && z2 > zIndex &&
               this.isCovered(object.objectFormat, v.objectFormat))
            {
               object.covered = true;
            }
         });

      object.objectFormat.bringToFrontEnabled = zIndex < highest;
      object.objectFormat.sendToBackEnabled = zIndex > lowest;
   }

   // an object is covered by other. it's true if any corner is covered
   private isCovered(self: BaseFormatModel, other: BaseFormatModel): boolean {
      const left = self.left;
      const right = self.left + self.width;
      const top = self.top;
      const bot = self.top + self.height;
      const rect = new Rectangle(other.left, other.top, other.width, other.height);

      return rect.contains(left, top) || rect.contains(left, bot) ||
         rect.contains(right, top) || rect.contains(right, bot);
   }

   private getFarthestIndex(vs: Viewsheet, model: VSObjectModel, moveUp: boolean): number {
      let closestIndex: number = model.objectFormat.zIndex;

      for(let object of vs.vsObjects) {
         // only compare objects with same container
         if(model.container != object.container) {
            continue;
         }

         let zIndex: number = object.objectFormat.zIndex;

         if(moveUp && zIndex > closestIndex) {
            closestIndex = zIndex;
         }
         else if(!moveUp && zIndex < closestIndex) {
            closestIndex = zIndex;
         }
      }

      return closestIndex;
   }

   private getClosestObject(vs: Viewsheet, model: VSObjectModel, moveUp: boolean): VSObjectModel {
      const currentIndex: number = model.objectFormat.zIndex;
      let closestIndex: number = moveUp ? 0xffff : 0;
      let obj: VSObjectModel = null;
      const bounds: Rectangle = new Rectangle(model.objectFormat.left,
                                              model.objectFormat.top,
                                              model.objectFormat.width,
                                              model.objectFormat.height);

      for(let object of vs.vsObjects) {
         // only compare objects with same container
         if(model.container != object.container) {
            continue;
         }

         const bounds2: Rectangle = new Rectangle(object.objectFormat.left,
                                                  object.objectFormat.top,
                                                  object.objectFormat.width,
                                                  object.objectFormat.height);

         if(!bounds.intersects(bounds2)) {
            continue;
         }

         const zIndex: number = object.objectFormat.zIndex;

         if(!moveUp && zIndex == currentIndex - 1) {
            obj = object;
            break;
         }
         else if(moveUp && zIndex == currentIndex + 1) {
            obj = object;
            break;
         }
         else if(moveUp && zIndex > currentIndex &&
                 zIndex - currentIndex < closestIndex - currentIndex)
         {
            obj = object;
            closestIndex = object.objectFormat.zIndex;
         }
         else if(!moveUp && zIndex < currentIndex &&
                 currentIndex - zIndex < currentIndex - closestIndex)
         {
            obj = object;
            closestIndex = object.objectFormat.zIndex;
         }
      }

      return obj;
   }

   updateLine(vs: Viewsheet, vsObject: VSObjectModel): void {
      let line: VSLineModel = <VSLineModel>vsObject;
      let vsEvent: ResizeVSLineEvent = new ResizeVSLineEvent(line);
      const anchorInfo = this.lineAnchorService.getLineInfo(line.absoluteName);

      if(!!anchorInfo) {
         vsEvent.endAnchorId = anchorInfo.endId;
         vsEvent.endAnchorPos = anchorInfo.endPos;
         vsEvent.startAnchorId = anchorInfo.startId;
         vsEvent.startAnchorPos = anchorInfo.startPos;
      }

      vs.socketConnection.sendEvent(RESIZE_VSLINE_URI, vsEvent);
   }

   public isLayoutKeyEventApplied(event: KeyboardEvent) {
      let adapters = this.layoutObjectsKeyEventAdapters.filter(
         (adapter) => this.isKeyEventApplied(event.target, adapter)
         );

      return adapters.length != 0;
   }

   private isKeyEventApplied(eventSource: any, adapter: KeyEventAdapter) {
      const target: any = adapter.getElement().nativeElement;
      const vs: Viewsheet = adapter.getViewsheet();

      if(!vs.isFocused || !adapter.isSelected()) {
         return false;
      }

      if(eventSource.tagName != "svg" && eventSource.contains(target)) {
         return true;
      }

      let element: any = eventSource;

      while(element) {
         //TODO check for other allowed components; the trees should probably not be included, as they should handle the key events
         if(element.tagName === "COMPOSER-TOOLBAR" ||
            element.tagName == "EDITABLE-OBJECT-CONTAINER" ||
            element.tagName == "COMPOSER-SELECTION-CONTAINER-CHILDREN" ||
            element.tagName == "LAYOUT-OBJECT")
         {
            return true;
         }

         element = element.parentElement ? element.parentElement : element.parentNode;
      }

      return false;
   }

   getDataSource(data: any): AssetEntry[] {
      if(data.column) {
         return data.column;
      }
      else if(data.dragSource) {
         return data.dragSource;
      }
      else if(data.folder) {
         return data.folder;
      }
      else if(data.table) {
         return data.table;
      }
      else if(data.variable) {
         return data.variable;
      }
      else if(data.physical_table) {
         return data.physical_table;
      }

      return null;
   }

   checkTableTransferDataType(viewsheetService: ViewsheetClientService, tableData: TableTransfer) {
      return this.modelService.sendModel<any>("../api/composer/viewsheet/objects/getTableTransferDataType/" +
         encodeURIComponent(viewsheetService.runtimeId), tableData);
   }

   applyChangeBinding(viewsheetService: ViewsheetClientService, objectName: string,
                      binding: AssetEntry[], options?: any, componentBinding?: TableTransfer)
   {
      let vsevent: ChangeVSObjectBindingEvent = new ChangeVSObjectBindingEvent(objectName);

      if(!binding && !componentBinding) {
         throw new Error("Change Binding Event Data Source must be defined. Event not sent");
      }

      vsevent.binding = binding;
      vsevent.componentBinding = componentBinding;

      if(options) {
         vsevent.x = options.x;
         vsevent.y = options.y;
         vsevent.tab = options.tab;
      }

      this.uiContextService.objectPropertyChanged(objectName, null);
      const trapInfo = new TrapInfo(CHECK_TRAP_URI, "", viewsheetService.runtimeId, vsevent);

      if(binding && binding[0].type == AssetType.VARIABLE) {
         viewsheetService.sendEvent(VIEWSHEET_OBJECTS_URI + "changeBinding", vsevent);
      }
      else {
         this.trapService.checkTrap(trapInfo,
            () => viewsheetService.sendEvent(VIEWSHEET_OBJECTS_URI + "changeBinding", vsevent),
            () => {
            },
            () => viewsheetService.sendEvent(VIEWSHEET_OBJECTS_URI + "changeBinding", vsevent)
         );
      }
   }
}
