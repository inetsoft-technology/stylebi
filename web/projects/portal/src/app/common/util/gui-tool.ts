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
import { HttpParams } from "@angular/common/http";
import { NgZone } from "@angular/core";
import { TinyColor } from "@ctrl/tinycolor";
import { isBoolean, isNumber, isString } from "lodash";
import { of as observableOf, Subject } from "rxjs";
import { AssetEntry } from "../../../../../shared/data/asset-entry";
import { AssetType } from "../../../../../shared/data/asset-type";
import { Tool } from "../../../../../shared/util/tool";
import { DomService } from "../../widget/dom-service/dom.service";
import { TreeNodeModel } from "../../widget/tree/tree-node-model";
import { AssetConstants } from "../data/asset-constants";
import { AssetEntryHelper } from "../data/asset-entry-helper";
import { DataRef } from "../data/data-ref";
import { DataRefType } from "../data/data-ref-type";
import { Dimension } from "../data/dimension";
import { DragEvent } from "../data/drag-event";
import { XSchema } from "../data/xschema";
import { BrowserMaxHeight } from "./browser-max-height";
import { NetTool } from "./net-tool";

declare const window: any;

export class GuiTool {
   /**
    * Fraction width, 1/16 point.
    */
   static readonly FRACTION_WIDTH_MASK: number = 0xF0000;

   /**
    * This mask is used to extract the encoded line width from line styles.
    */
   static readonly WIDTH_MASK: number = 0x0F;

   /**
    * This mask is used to extract the dash length.
    */
   static readonly DASH_MASK: number = 0x0F0;

   static readonly DATA_TIP_OFFSET = GuiTool.measureScrollbars();

   static readonly MINI_TOOLBAR_HEIGHT = 28;

   static readonly MINIMUM_TITLE_HEIGHT = 18;

   private static scrollbarWidth: number;
   private static scrollIdentifier = 0;

   /**
    * Return the text width in pixels.
    */
   static measureText(str: string, font: string): number {
      const id = "measureText_canvas";
      let canvas: HTMLCanvasElement =
         <HTMLCanvasElement> window.document.querySelector("#" + id);

      if(!canvas) {
         canvas = window.document.createElement("canvas");
         canvas.style.display = "flex";
         canvas.id = id;
         canvas.width = 1;
         canvas.height = 1;
         window.document.body.appendChild(canvas);
      }

      const context: CanvasRenderingContext2D = canvas.getContext("2d");
      context.font = font;
      return Math.ceil(context.measureText(str).width);
   }

   /**
    * Get width of the current browsers scrollbars by temporarily creating a div
    * with an overflow and measuring the width.
    *
    * Copied from:
    * http://stackoverflow.com/questions/13382516/getting-scroll-bar-width-using-javascript
    *
    * @returns {number} the width of the scrollbar in pixels
    */
   static measureScrollbars(): number {
      if(GuiTool.scrollbarWidth === undefined) {
         let outer = window.document.createElement("div");
         outer.style.visibility = "hidden";
         outer.style.width = "100px";
         outer.style.msOverflowStyle = "scrollbar"; // needed for WinJS apps

         window.document.body.appendChild(outer);

         let widthNoScroll = outer.offsetWidth;
         // force scrollbars
         outer.style.overflow = "scroll";

         // add innerdiv
         let inner = window.document.createElement("div");
         inner.style.width = "100%";
         outer.appendChild(inner);

         let widthWithScroll = inner.offsetWidth;

         // remove divs
         outer.parentNode.removeChild(outer);

         GuiTool.scrollbarWidth = widthNoScroll - widthWithScroll;
         // Some browsers (e.g. safari) don't show scrollbars unless scrolling so it does not
         // find the scroll bar width. Default to 10px
         GuiTool.scrollbarWidth =  GuiTool.scrollbarWidth == 0 ? 10 : GuiTool.scrollbarWidth;
      }

      return GuiTool.scrollbarWidth;
   }

   public static resetScrollbarWidth(): void {
      GuiTool.scrollbarWidth = undefined;
   }

   /**
    * Checks whether the element matches the selector.
    *
    * @param el the element to check
    * @param selector the selector to check
    *
    * @returns true if the element matches the selector, false otherwise
    */
   static matches(el: Element, selector: string): boolean {
      let matchesFn: string;

      // find vendor prefix
      ["matches", "webkitMatchesSelector", "mozMatchesSelector",
       "msMatchesSelector", "oMatchesSelector"].some(fn => {
         if(typeof document.body[fn] == "function") {
            matchesFn = fn;
            return true;
         }

         return false;
      });

      return el && el[matchesFn](selector);
   }

   // Find the closest parent
   static closest(el: Element, selector: string): Element {
      let parent: Element;

      // traverse parents
      while(el) {
         parent = el.parentElement;

         if(GuiTool.matches(parent, selector)) {
            return parent;
         }

         el = parent;
      }

      return null;
   }

   // Check if mouse is inside element
   // @param moe margin of error
   static isMouseIn(elem: HTMLElement, event: MouseEvent, moe: number = 2): boolean {
      const rect = elem.getBoundingClientRect();
      return event.pageX >= rect.left - moe &&
         event.pageX <= rect.left + rect.width + moe &&
         event.pageY >= rect.top - moe &&
         event.pageY <= rect.top + rect.height + moe;
   }

   /**
    * Gets the total viewport size of the hosting browser window.
    *
    * @returns {any} a tuple containing the width and height.
    */
   static getViewportSize(): [number, number] {
      let size: [number, number] = [-1, -1];
      let w: number;
      let h: number;

      if(typeof window.innerWidth !== "undefined") {
         if(window.innerWidth > 0) {
            size[0] = window.innerWidth;
         }

         if(window.innerHeight > 0) {
            size[1] = window.innerHeight;
         }
      }

      if(typeof window.document.documentElement !== "undefined" &&
         typeof window.document.documentElement.clientWidth !== "undefined" &&
         window.document.documentElement.clientWidth != 0) {
         w = window.document.documentElement.clientWidth;
         h = window.document.documentElement.clientHeight;

         if(size[0] == -1) {
            size[0] = w;
            size[1] = h;
         }
         else {
            if(w > 0) {
               size[0] = Math.min(size[0], w);
            }

            if(h > 0) {
               size[1] = Math.min(size[1], h);
            }
         }
      }

      w = window.document.getElementsByTagName("body")[0].clientWidth;
      h = window.document.getElementsByTagName("body")[0].clientHeight;

      if(size[0] == -1) {
         size[0] = w;
         size[1] = h;
      }

      return size;
   }

   static getMaxModeSize(container: any = null, touch: boolean = false): Dimension {
      if(!!container && typeof(container) === "string") {
         container = window.document.querySelector(container);
      }

      if(container) {
         let {width, height} = GuiTool.getElementClientRect(container);

         if(!touch) {
            if(width < container.offsetWidth) {
               height -= GuiTool.measureScrollbars();
            }

            if(height < container.offsetHeight) {
               width -= GuiTool.measureScrollbars();
            }
         }

         width = Math.min(width, window.document.body.clientWidth);
         height = Math.min(height, window.document.body.clientHeight);
         return new Dimension(width, height);
      }

      return new Dimension(
         Math.max(window.innerWidth, document.documentElement.clientWidth || 0) - 20,
         Math.max(window.innerHeight, document.documentElement.clientHeight || 0) - 75,
      );
   }

   static getChartMaxModeSize(container: any = null, touch: boolean = false): Dimension {
      let size = GuiTool.getMaxModeSize(container, touch);

      if(container) {
         // subtract size of scroll area divs
         size.width -= 19;
         size.height -= 19;
      }
      else {
         const sbSize = GuiTool.measureScrollbars();
         container = window.document.querySelector(".right-container.split");
         size = GuiTool.getMaxModeSize(container, touch);

         size.width -= 2 * sbSize;
         size.height -= 2 * sbSize;

         const topContainer = window.document.querySelector(".right-top-container");

         if(topContainer) {
            const topSize = GuiTool.getMaxModeSize(topContainer, touch);
            size.height -= topSize.height;
         }
      }

      return size;
   }

   static isTouchDevice(): Promise<boolean> {
      let result: Promise<boolean>;

      if(!!window.DocumentTouch && (window.document instanceof DocumentTouch)) {
         result = observableOf(true).toPromise();
      }
      else {
         let div = window.document.querySelector("#inetsoft_touchTest_div");

         if(!div) {
            div = window.document.createElement("div");
            div.id = "inetsoft_touchTest_div";
            window.document.body.appendChild(div);

            const style = window.document.createElement("style");
            style.innerHTML = `#inetsoft_touchTest_div {
               position: absolute;
               z-index: -1;
               display: none;
            }
            @media ('-moz-touch-enabled'),
            ('-ms-touch-enabled'),
            ('-o-touch-enabled'),
            (pointer:coarse),
            not all (pointer:fine) {
              #inetsoft_touchTest_div {
                display: block;
              }
            }`;
            window.document.body.appendChild(style);
         }

         const subject: Subject<boolean> =  new Subject<boolean>();
         result = subject.toPromise();
         window.setTimeout(() => {
            subject.next(!!div.offsetParent);
            subject.complete();
         }, 0);
      }

      return result;
   }

   private static getElementClientRect(element: any): {top: number, left: number, bottom: number, right: number, width: number, height: number} {
      let clientRect;

      if(element instanceof window.SVGElement) {
         clientRect = element.getBoundingClientRect();
      }
      else {
         const clientRects = element.getClientRects();

         if(clientRects && clientRects.length) {
            clientRect = clientRects[0];
         }
         else {
            clientRect = element.getBoundingClientRect();
         }
      }

      // account for the border on editable-object
      const adj = element.classList.contains("object-editor")  &&
         !element.classList.contains("wizard-object-editor") ? 2 : 0;

      return clientRect && {
         left  : clientRect.left + adj,
         right : clientRect.right - adj,
         top   : clientRect.top + adj,
         bottom: clientRect.bottom - adj,
         width : (clientRect.width || clientRect.right  - clientRect.left) - adj * 2,
         height: (clientRect.height || clientRect.bottom - clientRect.top) - adj * 2,
      };
   }

   private static getScrollXY(relevantWindow: any): {x: number, y: number} {
      relevantWindow = relevantWindow || window;
      return {
         x: relevantWindow.scrollX || relevantWindow.document.documentElement.scrollLeft,
         y: relevantWindow.scrollY || relevantWindow.document.documentElement.scrollTop,
      };
   }

   private static isWindow(obj: any): boolean {
      return !!(obj && obj.Window) && (obj instanceof obj.Window);
   }

   private static getWindow(node: any): any {
      if(GuiTool.isWindow(node)) {
         return node;
      }

      const rootNode = (node.ownerDocument || node);
      return rootNode.defaultView || rootNode.parentWindow || window;
   }

   static getElementRect(element: any): {top: number, left: number, bottom: number, right: number, width: number, height: number} {
      const clientRect = GuiTool.getElementClientRect(element);

      if(clientRect) {
         const scroll = GuiTool.getScrollXY(GuiTool.getWindow(element));

         clientRect.left   += scroll.x;
         clientRect.right  += scroll.x;
         clientRect.top    += scroll.y;
         clientRect.bottom += scroll.y;
      }

      return clientRect;
   }

   static resolveUrl(relativeUrl: string): string {
      const resolver = window.document.createElement("a");
      resolver.href = relativeUrl;
      return <string> resolver.href;
   }

   /**
    * This method needs to be called to append parameters when the type of value is unknown.
    **/
   static appendHttpParams(params: HttpParams, key: string,
                           value: any, allowRepeat: boolean = false): HttpParams
   {
      if(!key || !value) {
         return params;
      }

      if(isString(value) || isBoolean(value) || isNumber(value)) {
         if(!allowRepeat) {
            return params.set(key, value + "");
         }

         return params.has(key) ? params.append(key, value + "") : params.set(key, value + "");
      }

      if(Tool.isArray(value)) {
         value.forEach((val) => {
            params = GuiTool.appendHttpParams(params, key, val, allowRepeat);
         });
      }

      return params;
   }

   static appendParams(url: string, params?: HttpParams): string {
      if(!!params) {
         params.keys().forEach((paramName) => {
            if(!!paramName && !!params.getAll(paramName)) {
               params.getAll(paramName)
                  .filter((paramValue) => !!paramValue)
                  .forEach((paramValue) => {
                     url += url.indexOf("?") < 0 ? "?" : "&";
                     url += paramName + "=" + encodeURIComponent(paramValue);
                  });
            }
         });
      }

      return url;
   }

   static openBrowserTab(url: string,
                                  params?: HttpParams,
                                  target: string = "_blank",
                                  appendToken: boolean = true): void {
      url = GuiTool.appendParams(url, params);

      if(appendToken && url.indexOf(NetTool.PARAMETER_NAME) < 0) {
         url += url.indexOf("?") < 0 ? "?" : "&";
         url += NetTool.xsrfToken();
      }

      window.open(url, target);
   }

   static isHostGlobalNode(nodeData: any): boolean {
      return nodeData?.path == "Host Organization Global Repository" && nodeData?.defaultOrgAsset;
   }

   /**
    * Method for determining the css class of an entry by its AssetType
    */
   static getTreeNodeIconClass(node: TreeNodeModel, prefix: string): string {
      let css: string = prefix;
      const entry: AssetEntry = node.data;
      let entryPath = "";
      let entryParent = "";
      let ntype = "";
      let ctype = null;
      let properties: any = {};

      if(entry != null) {
         ntype = typeof entry.type === "number" && !isNaN(entry.type) ? "" : entry.type;
         entryPath = entry.path;
         entryParent = AssetEntryHelper.getParentName(entry);
         properties = entry.properties || {};
         ctype = properties ? properties["classtype"] : null;
      }

      if(!ntype) {
         ntype = node.type;
      }

      switch(ntype ? ntype.toUpperCase() : "") {
         case AssetType.DATA_SOURCE:
            if(properties["datasource.type"] === "jdbc") {
               css += " database-icon";
            }
            else if(properties["datasource.type"] === "object") {
               css += " OBJ-icon";
            }
            else if(properties["datasource.type"] === "sap") {
               css += " SAP-icon";
            }
            else if(properties["datasource.type"] === "soap") {
               css += " SOAP-icon";
            }
            else if(properties["datasource.type"] === "tabular") {
               css += " tabular-data-icon";
            }
            else if(properties["datasource.type"] === "text") {
               css += " TXT-icon";
            }
            else if(properties["datasource.type"] === "xml") {
               css += " XML-icon";
            }
            else if(properties["datasource.type"] === "xmla") {
               css += " cube-icon";
            }
            else {
               css += " tabular-data-icon";
            }

            break;
         case AssetType.DATA_SOURCE_FOLDER:
            css += " data-source-folder-icon";
            break;
         case AssetType.VIEWSHEET:
            if(node.materialized) {
               css += " materialized-viewsheet-icon";
            }
            else {
               css += " viewsheet-icon";
            }

            break;
         case AssetType.WORKSHEET:
            const type = parseInt(properties["worksheet.type"], 10);

            if(type === AssetConstants.NAMED_GROUP_ASSET) {
               css += " grouping-icon";
            }
            else if(type === AssetConstants.VARIABLE_ASSET) {
               css += " variable-icon";
            }
            else if(type === AssetConstants.CONDITION_ASSET) {
               css += " condition-icon";
            }
            else if(type === AssetConstants.DATE_RANGE_ASSET) {
               css += " date-range-icon";
            }
            else {
               if(node.materialized) {
                  css += " materialized-worksheet-icon";
               }
               else {
                  css += " worksheet-icon";
               }
            }

            break;
         case AssetType.SCRIPT:
            css += " javascript-icon";
            break;
         case AssetType.TABLE_STYLE:
            css += "style-icon";
            break;
         case AssetType.QUERY:
            css += " db-table-icon";
            break;
         // bug 10852 refer logic model icon to folder icon -> reversed, change SCSS file for right icon
         case AssetType.LOGIC_MODEL:
            css += " db-model-icon";
            break;
         case AssetType.VARIABLE:
            css += " variable-icon";
            break;
         case AssetType.PHYSICAL_FOLDER:
            css += " folder-icon";
            break;
         case AssetType.REPOSITORY_FOLDER:
            if(node.label == "_#(js:Global Viewsheet)") {
               css += " shared-viewsheet-icon";
            }
            else if(node.label == "_#(js:User Viewsheet)") {
               css += " private-viewsheet-icon";
            }
            else {
               css += " folder-icon";
            }

            break;
         case AssetType.LOCAL_QUERY_FOLDER:
         case AssetType.REPORT_WORKSHEET_FOLDER:
         case AssetType.FOLDER:
         case AssetType.QUERY_FOLDER:
            const showAsReplet = properties ? properties["showAsReplet"] : null;

            if(showAsReplet) {
               css += " report-icon";
               break;
            }

            if(ctype != null) {
               if(ctype == "EMBEDED_DATA") {
                  css += " folder-icon";
               }
               else if(ctype == "ReportFolder") {
                  css += " report-icon";
               }
               else {
                  css += " folder-icon";
               }
            }
            else {
               if(entryParent == "Dimension") {
                  css += " dimension-icon";
               }
               else if(node.label == "_#(js:Global Worksheet)") {
                  css += " shared-worksheet-icon";
               }
               else if(node.label == "_#(js:User Worksheet)") {
                  css += " private-worksheet-icon";
               }
               else if(node.expanded) {
                  css += " folder-open-icon";
               }
               else {
                  css += " folder-icon";
               }
            }

            break;
         case "COLUMNNODE":
         case AssetType.PHYSICAL_COLUMN:
         case AssetType.COLUMN:
            const rtype: string = properties["refType"];
            const refType: number = rtype ? parseInt(rtype, 10) : DataRefType.NONE;
            const status: string = properties["mappingStatus"];
            const isGeo: string = properties["isGeo"];
            const fromWS: boolean = entryPath && entryPath.indexOf("/baseWorksheet/") == 0;
            const isFormula: boolean = properties["formula"] == "true";
            const dataType: string = properties["dtype"];

            if(status == "true" && isGeo == "true") {
               css += " geo-mapped-icon";
            }
            else if(status == "false" && isGeo == "true") {
               css += " geo-not-mapped-icon";
            }
            else if(properties["basedOnDetail"] == "false") {
               css += " calculated-sum-field-icon";
            }
            else if(properties["basedOnDetail"] == "true") {
               css += " calculated-detail-field-icon";
            }
            else if((refType & DataRefType.CUBE_DIMENSION) ==
               DataRefType.CUBE_DIMENSION && !fromWS)
            {
               if(status == "true") {
                  css += " cube-icon";
               }
               else if(status == "false") {
                  css += " cube-icon";
               }
               else {
                  css += " dimension-icon";
               }
            }
            else if(isFormula) {
               css += " formula-icon";
            }
            else if(dataType === XSchema.DATE) {
               css += " date-field-icon";
            }
            else if(dataType === XSchema.TIME_INSTANT ||
                    dataType === XSchema.TIME)
            {
               css += " datetime-field-icon";
            }
            else if(dataType === XSchema.STRING ||
                    dataType === XSchema.CHARACTER ||
                    dataType === XSchema.CHAR)
            {
               css += " text-field-icon";
            }
            else if(dataType === XSchema.DOUBLE ||
                    dataType === XSchema.INTEGER ||
                    dataType === XSchema.FLOAT ||
                    dataType === XSchema.SHORT ||
                    dataType === XSchema.LONG  ||
                    dataType === XSchema.BYTE)
            {
               css += " number-field-icon";
            }
            else if(dataType === XSchema.BOOLEAN) {
               css += " boolean-field-icon";
            }
            else {
               css += " column-icon";
            }

            break;
         case AssetType.PHYSICAL_TABLE:
            css += " db-table-icon";
            break;
         case AssetType.TABLE:
            if(properties["CUBE_TABLE"] == "true") {
               css += " db-table-icon";
            }
            else {
               css += " data-table-icon";
            }

            break;
         default:
         // console.log(`No image set for ${entry.identifier}`);
      }

      return css === prefix ? "" : css;
   }

   static getDataTypeIconClass(dataType: string): string {
      switch(dataType) {
         case XSchema.DATE:
            return "date-field-icon";
         case XSchema.TIME_INSTANT:
         case XSchema.TIME:
            return "datetime-field-icon";
         case XSchema.STRING:
         case XSchema.CHARACTER:
         case XSchema.CHAR:
            return "text-field-icon";
         case XSchema.DOUBLE:
         case XSchema.INTEGER:
         case XSchema.FLOAT:
         case XSchema.SHORT:
         case XSchema.LONG:
         case XSchema.BYTE:
            return "number-field-icon";
         case XSchema.BOOLEAN:
            return "boolean-field-icon";
         default:
            return "";
      }
   }

   static isGrayedOutField(name: string, grayedOutFields: DataRef[]) {
      if(name == null) {
         return false;
      }
      let field = Tool.replaceStr(name, "\\^", ".");
      field = Tool.replaceStr(field, ":", ".");

      for(let i = 0; grayedOutFields && i < grayedOutFields.length; i++) {
         if(field === grayedOutFields[i].name) {
            return true;
         }
      }

      return false;
   }

   private static readonly defsizes: {dragName: string, width: number, height: number}[] = [
      { dragName: "dragchart", width: 400, height: 240 },
      { dragName: "dragcrosstab", width: 400, height: 240 },
      { dragName: "dragtable", width: 400, height: 240 },
      { dragName: "dragfreehandtable", width: 400, height: 240 },
      { dragName: "dragselectionlist", width: 100, height: 120 },
      { dragName: "dragselectiontree", width: 100, height: 120 },
      { dragName: "dragrangeslider", width: 200, height: 40 },
      { dragName: "dragcalendar", width: 300, height: 200 },
      { dragName: "dragselectioncontainer", width: 300, height: 240 },
      { dragName: "dragtext", width: 100, height: 20 },
      { dragName: "dragimage", width: 100, height: 40 },
      { dragName: "draggauge", width: 140, height: 140 },
      { dragName: "dragslider", width: 200, height: 40 },
      { dragName: "dragspinner", width: 100, height: 20 },
      { dragName: "dragcheckbox", width: 200, height: 40 },
      { dragName: "dragradiobutton", width: 200, height: 40 },
      { dragName: "dragcombobox", width: 100, height: 20 },
      { dragName: "dragtextinput", width: 100, height: 20 },
      { dragName: "dragsubmit", width: 100, height: 20 },
      { dragName: "dragline", width: 50, height: 50 },
      { dragName: "dragrectangle", width: 100, height: 75 },
      { dragName: "dragoval", width: 100, height: 75 },
      { dragName: "table", width: 100, height: 120 },
   ];

   /**
    * Find the default object size.
    */
   static getDefaultObjectSize(dragName: string): any {
      return GuiTool.defsizes.find(d => d.dragName == dragName);
   }

   /**
    * Create a div as a drag image, one line per label.
    */
   static createDragImage(labels: string[], dragNames: string[] = [],
                          cols: number = 1, alignLeft: boolean = false): Element
   {
      if(GuiTool.isSafari()) {
         return null;
      }

      let elem: Element = document.querySelector(".show-drag-field");

      if(!elem) {
         elem = document.createElement("div");
         document.body.appendChild(elem);
         elem.setAttribute("class", "show-drag-field txt-primary");
         // Dragging can be done in a modal, make sure the image is visible.
         (<HTMLElement> elem).style.zIndex = "20000";
      }

      (<HTMLElement> elem).style.width = null;
      (<HTMLElement> elem).style.height = null;

      if(dragNames) {
         let w: number = 0;
         let h: number = 0;

         dragNames.forEach(dragName => {
            const size = GuiTool.getDefaultObjectSize(dragName);

            if(size) {
               w = Math.max(size.width * cols, w);
               h += size.height;
            }

            if(dragName == "column") {
               alignLeft = true;
            }
         });

         if(w > 0 && h > 0) {
            (<HTMLElement> elem).style.width = w + "px";
            (<HTMLElement> elem).style.height = h + "px";
            const fsize = Math.max(12, Math.min(24, h / 10));
            (<HTMLElement> elem).style["font-size"] = fsize.toFixed(0) + "px";

            if(labels.length > 1) {
               (<HTMLElement> elem).style.height = labels.length * h + "px";
            }
         }

         if(alignLeft) {
            (<HTMLElement> elem).classList.add("drag-field-left");
         }
      }

      elem.innerHTML = labels.join("<br>");
      return elem;
   }

   /**
    * Implement our own drag image because:
    * 1. IE/Edge doesn't support Dom setDragimage
    * 2. DragImage addes a gradient transparency to the image that makes the image
    * look terrible in many cases.
    * @param event    the drag event.
    * @param image    the image or element to use.
    */
   static setDragImage(event: any, image: Element, zone: NgZone,
                                domService: DomService): Promise<void>
   {
      if(GuiTool.isSafari()) {
         return new Promise(() => {});
      }

      if(event && event.dataTransfer && event.dataTransfer.setDragImage) {
         if(image instanceof Image) {
            event.dataTransfer.setDragImage(image, 0, 0);
         }
         else {
            event.dataTransfer.setDragImage(new Image(), 0, 0);
         }
      }

      // clear default dragging image
      if(GuiTool.isEdge() || GuiTool.isIE()) {
         const odisplay = event.srcElement.style.display;
         event.srcElement.style.display = "none";
         setTimeout(() => event.srcElement.style.display = odisplay, 5);
      }

      if(image instanceof HTMLElement) {
         let ox: number = null;
         let oy: number = null;

         const dragOverFn = (e: DragEvent) => {
            domService.requestRead(() => {
               const pY = e.pageY;
               const pX = e.pageX;

               if(ox != pX || oy != pY) {
                  domService.requestWrite(() => {
                     ox = pX;
                     oy = pY;
                     (<HTMLElement> image).style.top = (pY + 1) + "px";
                     (<HTMLElement> image).style.left = (pX + 1) + "px";
                  });
               }
            });
         };

         return new Promise((resolve) => {
            zone.runOutsideAngular(() => {
               document.addEventListener("dragover", dragOverFn);
               const dragendFn = () => {
                  document.removeEventListener("dragover", dragOverFn);
                  document.removeEventListener("dragend", dragendFn);

                  if(image.parentElement) {
                     document.body.removeChild(image);
                  }

                  resolve();
               };
               document.addEventListener("dragend", dragendFn);
            });
         });
      }

      return null;
   }

   // remove drag image (for dnd)
   static clearDragImage() {
      let elem: Element = document.querySelector(".show-drag-field");

      if(elem) {
         document.body.removeChild(elem);
      }
   }

   // convert valign to flex align-items
   static getFlexVAlign(vAlign: string): string {
      switch(vAlign?.toLowerCase()) {
      case "top":
         return "flex-start";
      case "center":
      case "middle":
         return "center";
      case "bottom":
         return "flex-end";
      default:
         return "center";
      }
   }

   // convert valign to flex justify-content
   static getFlexHAlign(hAlign: string): string {
      switch(hAlign?.toLowerCase()) {
      case "left":
         return "flex-start";
      case "center":
         return "center";
      case "right":
         return "flex-end";
      default:
         return "flex-start";
      }
   }

   // check if any parent contains clsas
   static parentContainsClass(element: Element, cls: string): boolean {
      if(!element) {
         return false;
      }

      if(element.classList.contains(cls)) {
         return true;
      }

      const parent = element.parentElement;
      return parent && GuiTool.parentContainsClass(parent, cls);
   }

   /**
    * @return {boolean} whether we're in an iframe
    */
   static isIFrame(): boolean {
      try {
         return window.frameElement != null || window.self !== window.top;
      }
      catch(e) {
         return window.self !== window.top;
      }
   }

   /**
    * Find node form the tree by the path.
    * @param {string} path node path
    * @param {TreeNodeModel} node treeNode
    * @returns {TreeNodeModel} If found, return the node, otherwise return null
    */
   static getNodeByPath(path: string, node: TreeNodeModel, defaultOrgAsset: boolean = false): TreeNodeModel {
      if(path) {
         return GuiTool.findNode(node, (n) => !!n.data &&
            n.data.path === path && !!n.data.defaultOrgAsset == defaultOrgAsset);
      }

      return null;
   }

   static findNode(node: TreeNodeModel, match: (n: TreeNodeModel) => boolean): TreeNodeModel {
      if(node) {
         const queue: TreeNodeModel[] = [node];

         while(queue.length > 0) {
            const n = queue.shift();

            if(match(n)) {
               return n;
            }

            if(n.children && n.children.length > 0) {
               queue.push(...n.children);
            }
         }
      }

      return null;
   }

   static isTouch(event: any) {
      return event.pageX == null;
   }

   static isButton1(event: any) {
      return event.button == null || event.button == 0;
   }

   static pageX(event: any) {
      return (event.pageX != null) ? event.pageX : (<TouchEvent>event).touches[0].pageX;
   }

   static pageY(event: any) {
      return (event.pageY != null) ? event.pageY : (<TouchEvent>event).touches[0].pageY;
   }

   static clientX(event: any) {
      return (event.clientX != null) ? event.clientX : (<TouchEvent>event).touches[0].clientX;
   }

   static clientY(event: any) {
      return (event.clientY != null) ? event.clientY : (<TouchEvent>event).touches[0].clientY;
   }

   static isIE(): boolean {
      const uA = window.navigator.userAgent;
      return /msie\s|trident\//i.test(uA);
   }

   static isEdge(): boolean {
      const uA = window.navigator.userAgent;
      return /\sEdge\//i.test(uA);
   }

   static isFF(): boolean {
      const uA = window.navigator.userAgent;
      return uA.toLowerCase().indexOf("firefox") >= 0;
   }

   static isSafari(): boolean {
      const uA = window.navigator.userAgent;
      return uA.toLowerCase().indexOf("safari") >= 0 && uA.toLowerCase().indexOf("chrome") < 0;
   }

   static isChrome(): boolean {
      const uA = window.navigator.userAgent;
      return uA.toLowerCase().indexOf("chrome") >= 0 && uA.toLowerCase().indexOf("edge") < 0;
   }

   static isIOS(): boolean {
      const uA = window.navigator.userAgent;
      return /Safari/i.test(uA) && (/iPad;/i.test(uA) ||
         /iPod;/i.test(uA) || /iPhone;/i.test(uA));
   }

   static getIOSVersion(): number {
      if(!GuiTool.isIOS()) {
         return -1;
      }

      let result = (window.navigator.appVersion).match(/OS (\d+)_(\d+)_?(\d+)?/);

      if(!!result && result.length > 1) {
         return parseInt(result[1], 10);
      }

      return -1;
   }

   /**
    * Determines if the browser is hosted on a mobile device. The implementation of this
    * function is taken from http://detectmobilebrowsers.com (public domain).
    *
    * @returns {boolean} <tt>true</tt> if a mobile device; <tt>false</tt> otherwise.
    */
   static isMobileDevice(): boolean {
      let a: string = (window.navigator.userAgent || window.navigator.vendor || window["opera"]);
      return (/(android|bb\d+|meego).+mobile|android|ipad|playbook|silk|avantgo|bada\/|blackberry|blazer|compal|elaine|fennec|hiptop|iemobile|ip(hone|od)|iris|kindle|lge |maemo|midp|mmp|mobile.+firefox|netfront|opera m(ob|in)i|palm( os)?|phone|p(ixi|re)\/|plucker|pocket|psp|series(4|6)0|symbian|treo|up\.(browser|link)|vodafone|wap|windows ce|xda|xiino/i.test(a) || /1207|6310|6590|3gso|4thp|50[1-6]i|770s|802s|a wa|abac|ac(er|oo|s-)|ai(ko|rn)|al(av|ca|co)|amoi|an(ex|ny|yw)|aptu|ar(ch|go)|as(te|us)|attw|au(di|-m|r |s )|avan|be(ck|ll|nq)|bi(lb|rd)|bl(ac|az)|br(e|v)w|bumb|bw-(n|u)|c55\/|capi|ccwa|cdm-|cell|chtm|cldc|cmd-|co(mp|nd)|craw|da(it|ll|ng)|dbte|dc-s|devi|dica|dmob|do(c|p)o|ds(12|-d)|el(49|ai)|em(l2|ul)|er(ic|k0)|esl8|ez([4-7]0|os|wa|ze)|fetc|fly(-|_)|g1 u|g560|gene|gf-5|g-mo|go(\.w|od)|gr(ad|un)|haie|hcit|hd-(m|p|t)|hei-|hi(pt|ta)|hp( i|ip)|hs-c|ht(c(-| |_|a|g|p|s|t)|tp)|hu(aw|tc)|i-(20|go|ma)|i230|iac( |-|\/)|ibro|idea|ig01|ikom|im1k|inno|ipaq|iris|ja(t|v)a|jbro|jemu|jigs|kddi|keji|kgt( |\/)|klon|kpt |kwc-|kyo(c|k)|le(no|xi)|lg( g|\/(k|l|u)|50|54|-[a-w])|libw|lynx|m1-w|m3ga|m50\/|ma(te|ui|xo)|mc(01|21|ca)|m-cr|me(rc|ri)|mi(o8|oa|ts)|mmef|mo(01|02|bi|de|do|t(-| |o|v)|zz)|mt(50|p1|v )|mwbp|mywa|n10[0-2]|n20[2-3]|n30(0|2)|n50(0|2|5)|n7(0(0|1)|10)|ne((c|m)3-|on|tf|wf|wg|wt)|nok(6|i)|nzph|o2im|op(ti|wv)|oran|owg1|p800|pan(a|d|t)|pdxg|pg(13|-([1-8]|c))|phil|pire|pl(ay|uc)|pn-2|po(ck|rt|se)|prox|psio|pt-g|qa-a|qc(07|12|21|32|60|-[2-7]|i-)|qtek|r380|r600|raks|rim9|ro(ve|zo)|s55\/|sa(ge|ma|mm|ms|ny|va)|sc(01|h-|oo|p-)|sdk\/|se(c(-|0|1)|47|mc|nd|ri)|sgh-|shar|sie(-|m)|sk-0|sl(45|id)|sm(al|ar|b3|it|t5)|so(ft|ny)|sp(01|h-|v-|v )|sy(01|mb)|t2(18|50)|t6(00|10|18)|ta(gt|lk)|tcl-|tdg-|tel(i|m)|tim-|t-mo|to(pl|sh)|ts(70|m-|m3|m5)|tx-9|up(\.b|g1|si)|utst|v400|v750|veri|vi(rg|te)|vk(40|5[0-3]|-v)|vm40|voda|vulc|vx(52|53|60|61|70|80|81|83|85|98)|w3c(-| )|webc|whit|wi(g |nc|nw)|wmlb|wonu|x700|yas-|your|zeto|zte-/i.test(a.substr(0, 4)));
   }

   static getQueryParameters(): Map<string, string[]> {
      let query = window.location.search.substring(1);

      const params = query.split("&")
         .map((pair: string) => pair.split("="))
         .reduce((parameterMap: Map<string, string[]>, pair: string[]) => {
            if(pair[0] && pair[1]) {
               let key = decodeURIComponent(pair[0]);
               let value = decodeURIComponent(pair[1]);
               let paramsTemp: string[] = parameterMap.get(key);

               if(!paramsTemp) {
                  paramsTemp = [];
                  parameterMap.set(key, paramsTemp);
               }

               if(value.indexOf("~_") >= 0 && value.indexOf("_~") >= 0) {
                  paramsTemp.push(Tool.byteDecode(value));
               }
               else {
                  paramsTemp.push(value);
               }
            }

            return parameterMap;
         }, new Map<string, string[]>());

      const drillId: string[] = params.get("drillId");

      if(drillId && drillId.length) {
         const storage = window.sessionStorage;
         let drillParamStr = storage.getItem("__drillParameters__" + drillId[0]);

         if(drillParamStr) {
            const drillParams = JSON.parse(drillParamStr);
            setTimeout(() => storage.removeItem("__drillParameters__" + drillId[0]), 2000);

            for(const k of Object.keys(drillParams)) {
               let v = drillParams[k];

               // array will be changed at ie11 (make Array to json failure),
               // so we should create new array
               if(Array.isArray(v)) {
                  let clone = [];

                  for(let value of v) {
                     clone.push(value);
                  }

                  v = clone;
               }

               params.set(k, Array.isArray(v) ? v : [v]);
            }
         }
      }

      return params;
   }

   /**
    * Returns true if the element has an associated CSS layout box, false otherwise.
    * This is useful for detecting whether an element is being displayed in the document.
    *
    * @param element the element to check
    * @returns true if the element has a layout box, false otherwise.
    */
   static hasLayoutBox(element: Element): boolean {
      // Per the spec, clientRects will be empty if the element has no associated layout box.
      // https://www.w3.org/TR/cssom-view-1/#dom-element-getclientrects
      return element.getClientRects().length !== 0;
   }

   /**
    * Returns the maximum height of an element that the current browser can correctly render.
    */
   static measureBrowserElementMaxHeight(): number {
      return BrowserMaxHeight.getBrowserMaxHeight();
   }

   static supportPointEvent() {
      return !!window.PointerEvent || !!window.MSPointerEvent;
   }

   /**
    * If a viewer is embedded in an iframe, and the iframe is taller than viewport so the
    * bottom of the iframe is not visible, when the document is changed (appendChild on document),
    * browser will automatically scroll to bring the bottom of the iframe into view.
    * This seems a 'feature' of browsers and can't be disabled. This function cancels
    * the automatic scroll. It should be called before document.appendChild().
    * @param later true if the scroll position should be restored immediately.
    * @return a function to restore the position if later is true.
    */
   static preventAutoScroll(later: boolean = false): () => {} {
      if(window.parent && window.parent != window) {
         let scrollId: number;
         let windowX: number;
         let windowY: number;

         try {
            windowX = window.parent.scrollX;
            windowY = window.parent.scrollY;
         }
         catch(ex) {
            // Cross-origin context, must use postMessage.
            scrollId = GuiTool.scrollIdentifier++;
            window.parent.postMessage({message: "saveScroll", data: scrollId}, "*");
         }

         let restore: () => {};

         if(windowY != null) {
            restore = () => window.parent.scrollTo(windowX, windowY);
         }
         else {
            restore = () => window.parent.postMessage({message: "restoreScroll", data: scrollId}, "*");
         }

         if(later) {
            return restore;
         }

         setTimeout(restore, 100);
      }

      return null;
   }

   static getContrastColor(colorStr: string, defaultColor: string = "inherit"): string {
      let color = new TinyColor(colorStr);

      if(color.isValid) {
         return color.isDark() ? "#ffffff" : "#000000";
      }

      return defaultColor;
   }

   static setColorAlpha(colorStr: string, alpha: number): string {
      let color = new TinyColor(colorStr);

      if(color.isValid) {
         color.setAlpha(alpha);

         return color.toRgbString();
      }

      return colorStr;
   }

   static getSelectedColor(colorStr: string, defaultColor: string = "inherit"): string {
      let color = new TinyColor(colorStr);

      if(color.isValid) {
         if(color.isDark()) {
            color = color.tint(20);
         }
         else {
            color = color.shade(20);
         }

         return color.toRgbString();
      }

      return defaultColor;
   }

   static getHTMLText(text: string): string {
      if(text) {
         if(text.startsWith("<html>") && text.endsWith("</html>")) {
            return text.substring(6, text.length - 7);
         }

         return text.trimEnd().replace(/\n/g, "<br>")
            .replace(/^ +/gm, (match) => match.replace(/ /g, "&nbsp;"))
            .replace(/ {2}/g, " &nbsp;");
      }

      return "";
   }
}
