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
   HostBinding,
   HostListener,
   Input,
   NgZone,
   OnInit,
   OnChanges,
   Output,
   SimpleChanges
} from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { GuiTool } from "../../../common/util/gui-tool";
import { PrintLayoutSection } from "../../../vsobjects/model/layout/print-layout-section";
import { VSObjectModel } from "../../../vsobjects/model/vs-object-model";
import { DomService } from "../../../widget/dom-service/dom.service";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { Sheet } from "../../data/sheet";
import { VSObjectTreeNode } from "../../data/vs-object-tree-node";
import { VSLayoutModel } from "../../data/vs/vs-layout-model";
import { VSLayoutObjectModel } from "../../data/vs/vs-layout-object-model";
import { AssemblyType } from "../vs/assembly-type";
import { ComponentTool } from "../../../common/util/component-tool";

@Component({
   selector: "components-pane",
   templateUrl: "components-pane.component.html",
   styleUrls: ["components-pane.component.scss"]
})
export class ComponentsPane implements OnChanges, OnInit{
   @HostBinding("hidden")
   @Input() inactive: boolean;
   @Input() set currentLayout(value: VSLayoutModel) {
      this.layout = value;
      this.updateRoots();
   }
   @Input() sheet: Sheet;
   @Input() set tree(value: VSObjectTreeNode) {
      this.objectTree = value;
      this.updateRoots();
   }
   @Output() onCopy: EventEmitter<VSObjectModel> = new EventEmitter<VSObjectModel>();
   @Output() onCut: EventEmitter<VSObjectModel> = new EventEmitter<VSObjectModel>();
   @Output() onRemove: EventEmitter<VSObjectModel> = new EventEmitter<VSObjectModel>();
   @Output() onBringToFront: EventEmitter<VSObjectModel> = new EventEmitter<VSObjectModel>();
   @Output() onSendToBack: EventEmitter<VSObjectModel> = new EventEmitter<VSObjectModel>();
   @Output() onClearFocusedObjects: EventEmitter<any> = new EventEmitter<any>();
   componentRoot: TreeNodeModel;
   toolBox: TreeNodeModel;
   layout: VSLayoutModel;
   objectTree: VSObjectTreeNode;
   contentToolBox: TreeNodeModel;

   constructor(private changeDetector: ChangeDetectorRef,
               private modalService: NgbModal,
               private zone: NgZone,
               private domService: DomService) {
   }

   ngOnInit() {
      this.contentToolBox = {
         label: "_#(js:Toolbox)",
         children: [
            {
               label: "_#(js:PageBreak)",
               icon: this.getIcon("VSPageBreak"),
               dragName: "newObject",
               dragData: AssemblyType.PAGEBREAK_ASSET + ""
            }
         ]
      };
   }

   ngOnChanges(changes: SimpleChanges) {
      if(this.inactive) {
         this.changeDetector.detach();
      }
      else {
         this.changeDetector.reattach();
      }
   }

   updateRoots(): void {
      if(!this.objectTree || !this.layout) {
         this.componentRoot = null;
         this.toolBox = null;
         return;
      }

      let objects: VSObjectTreeNode[] = this.objectTree.children;

      let components: TreeNodeModel = {
         label: "_#(js:Components)",
         children: []
      };

      for(let object of objects) {
         if(!object.model || object.model.objectType == "VSAnnotation") {
            continue;
         }

         // check if the object is on the layout-pane already
         const isInLayoutPane: boolean = !!this.findLayoutObject(object.model.absoluteName);

         let node: TreeNodeModel = {
            label: object.model.absoluteName,
            children: [],
            disabled: false,
            dragName: isInLayoutPane && this.layoutMode ? "object-exist" : "object",
            dragData: !!this.findLayoutObject(object.model.absoluteName) ?
               null : object.model.absoluteName,
            icon: "binding-tree-image " + this.getIcon(object.model.objectType),
            cssClass: !!this.findLayoutObject(object.model.absoluteName) ? "text-muted" : null,
            tooltip: this.isDatatipOrPopComp(object.model.absoluteName) && this.layoutMode && !this.layout.printLayout ?
               this.getDatatipAndPopCompHint(object.model.absoluteName) : null
         };

         components.children.push(node);
      }

      components.children.sort((a, b) => a.label.localeCompare(b.label));
      this.componentRoot = components;

      this.toolBox = {
         label: "_#(js:Toolbox)",
         children: [
            {
               label: "_#(js:Text)",
               icon: this.getIcon("VSText"),
               dragName: "newObject",
               dragData: AssemblyType.TEXT_ASSET + ""
            },
            {
               label: "_#(js:Image)",
               icon: this.getIcon("VSImage"),
               dragName: "newObject",
               dragData: AssemblyType.IMAGE_ASSET + ""
            }
         ]
      };
   }

   nodesSelected(nodes: TreeNodeModel[]) {
      this.layout.clearFocusedObjects();

      for(let node of nodes) {
         const layoutObject: VSLayoutObjectModel = this.findLayoutObject(node.label);

         if(!!layoutObject) {
            this.layout.selectObject(layoutObject);
         }
      }
   }

   @HostListener("click", ["$event"])
   click(event: MouseEvent): void {
      this.onClearFocusedObjects.emit(true);
   }

   copyAssembly(model: VSObjectModel): void {
      this.onCopy.emit(model);
   }

   cutAssembly(model: VSObjectModel): void {
      this.onCut.emit(model);
   }

   removeAssembly(model: VSObjectModel): void {
      this.onRemove.emit(model);
   }

   bringAssemblyToFront(model: VSObjectModel): void {
      this.onBringToFront.emit(model);
   }

   sendAssemblyToBack(model: VSObjectModel): void {
      this.onSendToBack.emit(model);
   }

   get layoutMode(): boolean {
      return !!this.layout;
   }

   get showToolBox(): boolean {
      return this.layout != null &&
         this.layout.currentPrintSection != PrintLayoutSection.CONTENT;
   }

   get showContentToolBox(): boolean {
      return this.layout != null && this.layout.printLayout &&
         this.layout.currentPrintSection == PrintLayoutSection.CONTENT;
   }

   private findLayoutObject(name: string): VSLayoutObjectModel {
      return this.layout.objects.find(
         obj => obj.objectModel && obj.objectModel.absoluteName == name)
         || (this.layout.headerObjects && this.layout.headerObjects.find(
            obj => obj.objectModel && obj.objectModel.absoluteName == name))
         || (this.layout.footerObjects && this.layout.footerObjects.find(
            obj => obj.objectModel && obj.objectModel.absoluteName == name));
   }

   /**
    * When component is used as datatip of charts and is not added to layout pane,
    * we should show obvious hint that it is required to add the flyover component
    * Feature 3956
    *
    * @param {string} dataTipName
    * @returns {VSObjectTreeNode[]}
    */
   private getDatatipSource(dataTipName: string): VSObjectTreeNode[] {
      const objects: VSObjectTreeNode[] = this.objectTree.children;
      return objects.filter((object) =>
         object.model && object.model.dataTip == dataTipName &&
         !this.findLayoutObject(dataTipName));
   }

   /**
    * When component is used as pop component of text or image and is not added to layout pane,
    * we should show obvious hint that it is required to add the flyover component
    * Feature 19827
    *
    * @param {string} popName
    * @returns {VSObjectTreeNode[]}
    */
   private getPopComponentSource(popName: string): VSObjectTreeNode[] {
      const objects: VSObjectTreeNode[] = this.objectTree.children;
      return objects.filter((object) => object.model && object.model.popComponent == popName);
   }

   private isCurrentUsedDatatip(name: string): boolean {
      const charts: VSObjectTreeNode[] = this.getDatatipSource(name);
      return !!charts && charts.length > 0;
   }

   private isCurrentUsedPopComp(name: string): boolean {
      const parentArray: VSObjectTreeNode[] = this.getPopComponentSource(name);
      return !!parentArray && parentArray.length > 0;
   }

   private getDatatipAndPopCompHint(name: string): string {
      let currentUsedDatatipString: string = `If ${
         name} is not added to current layout, the datatip view of `;
      const charts: VSObjectTreeNode[] = this.getDatatipSource(name);
      currentUsedDatatipString += charts.map((object) => {
         return object.model.absoluteName;
      }).join(" ");
      const dataTipHint: string =  `${currentUsedDatatipString} will not be enabled.`;
      currentUsedDatatipString.replace(/\s+/g, " ");

      let currentUsedPopCompString: string = `If ${
         name} is not added to current layout, the pop component of `;
      const parent: VSObjectTreeNode[] = this.getPopComponentSource(name);
      currentUsedPopCompString += parent.map((object) => {
         return object.model.absoluteName;
      }).join(" ");
      const popCompHint: string =  `${currentUsedPopCompString} will not be enabled.`;
      popCompHint.replace(/\s+/g, " ");

      return this.isCurrentUsedDatatip(name) && this.isCurrentUsedPopComp(name) ?
         dataTipHint + "\n" + popCompHint : this.isCurrentUsedDatatip(name) ?
         dataTipHint : this.isCurrentUsedPopComp(name) ? popCompHint : null;
   }

   private getDisableDatatipClass(name: string): string {
      if(this.isDatatipOrPopComp(name)) {
         return "eye-off-icon";
      }

      return "";
   }

   private getIcon(objectType: string): string {
      switch(objectType) {
      case "VSCheckBox":
         return "checkbox-icon";
      case "VSComboBox":
         return "dropdown-box-icon";
      case "VSCrosstab":
         return "crosstab-icon";
      case "VSGauge":
         return "gauge-icon";
      case "VSImage":
         return "image-icon";
      case "VSRadioButton":
         return "radio-button-icon";
      case "VSSelectionList":
         return "selection-list-icon";
      case "VSSelectionTree":
         return "selection-tree-icon";
      case "VSSlider":
         return "slider-icon";
      case "VSSpinner":
         return "spinner-icon";
      case "VSTab":
         return "tab-icon";
      case "VSTable":
         return "table-icon";
      case "VSTextInput":
         return "text-input-icon";
      case "VSRangeSlider":
         return "range-slider-icon";
      case "Viewsheet":
         return "viewsheet-icon";
      case "VSChart":
         return "chart-icon";
      case "VSCalendar":
         return "calendar-icon";
      case "VSUpload":
         return "upload-icon";
      case "VSSubmit":
         return "submit-icon";
      case "VSText":
         return "text-box-icon";
      case "VSCalcTable":
         return "formula-table-icon";
      case "VSLine":
         return "line-icon";
      case "VSRectangle":
         return "rectangle-icon";
      case "VSOval":
         return "oval-icon";
      case "VSPageBreak":
         return "format-page-break-icon";
      case "VSSelectionContainer":
         return "selection-container-icon";
      case "VSGroupContainer":
         return "grouping-icon";
      case "VSViewsheet":
         return "viewsheet-icon";
      default:
         console.log("Unknown object type: " + objectType);
      }

      return "";
   }

   public dragNode(event: any) {
      const srcData = JSON.parse(event.dataTransfer.getData("text"));
      let labels: string[] = srcData.object;

      if(srcData.dragName == "object-exist") {
         ComponentTool.showMessageDialog(this.modalService, "_#(js:Warning)",
                                "_#(js:viewer.viewsheet.layout.objectExists)");
         return;
      }

      if(!labels) {
         let newObject = srcData.newObject;

         if(Array.isArray(newObject) && newObject.length > 0) {
            newObject = newObject[0];
         }

         switch(newObject) {
            case AssemblyType.IMAGE_ASSET + "":
               labels = ["_#(js:Image)"];
               break;
            case AssemblyType.TEXT_ASSET + "":
               labels = ["_#(js:Text)"];
               break;
            case AssemblyType.PAGEBREAK_ASSET + "":
               labels = ["_#(js:PageBreak)"];
               break;
         }
      }

      const elem = GuiTool.createDragImage(labels, srcData.dragName);
      GuiTool.setDragImage(event, elem, this.zone, this.domService);
   }

   private isDatatipOrPopComp(absoluteName: string) {
      return this.isCurrentUsedDatatip(absoluteName) || this.isCurrentUsedPopComp(absoluteName);
   }
}
