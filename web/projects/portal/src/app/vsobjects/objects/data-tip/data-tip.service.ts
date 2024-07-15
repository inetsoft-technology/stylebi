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
import { Injectable, NgZone } from "@angular/core";
import { GuiTool } from "../../../common/util/gui-tool";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { OpenDataTipEvent } from "../../event/open-datatip-event";

export interface DataTipInfo {
   dataTipX: number;
   dataTipY: number;
   dataTipName: string;
   dataTipAlpha: number;
   parentName: string;
   condition: string;
}

/**
 * Data tip service stores the current data tip and serves as a middle-man between
 * components that have a data tip and the data tip itself
 */
@Injectable()
export class DataTipService {
   static readonly DEBOUNCE_KEY = "DEBOUNCE_DATA_TIP_EVENT";
   static readonly DATA_TIP_OFFSET = GuiTool.DATA_TIP_OFFSET;

   private _dataTipX: number;
   private _dataTipY: number;
   private _dataTipName: string;
   private _dataTipAlpha: number;
   private _parentName: string;
   private _freeze: boolean = false;
   private _lastDataTip: DataTipInfo;
   private _condition: string;
   private _viewerOffsetFunc: () => {x: number, y: number, width: number, height: number, scrollLeft: number, scrollTop: number} = () => {
      return {x: 0, y: 0, width: 0, height: 0, scrollLeft: 0, scrollTop: 0};
   };
   // Map of parent names -> data tip names.
   private _dataTips: Map<string, string> = new Map<string, string>();
   // Feature 3956, only when the data tip component is added to layout pane,
   // the data tip will show up.
   // Map of data tip names -> visibility.
   private _dataTipsVisible: Map<string, boolean> = new Map<string, boolean>();
   private inited = {}; // track if data tip has been initialized

   constructor(private viewsheetClient: ViewsheetClientService, private zone: NgZone)
   {
   }

   /**
    * Register a data tip with the service
    * @param name       the name of the data tip
    * @param parent     the name of its parent
    */
   registerDataTip(name: string, parent: string): void {
      if(name && parent) {
         this._dataTips.set(parent, name);
      }
   }

   /**
    * Register a data tip visibility with the service
    * @param {string} name data tip (popup) assembly name
    * @param {boolean} visible
    */
   registerDataTipVisible(name: string, visible: boolean): void {
      if(name && visible) {
         this._dataTipsVisible.set(name, visible);
      }
   }

   // remove data tip mapping, name is the parent
   clearDataTips(name: string): void {
      this._dataTips.forEach((val, key, map) => {
         if(key === name) {
            map.delete(key);
         }
      });
   }

   // name is the child (data tip view)
   clearDataTipChild(name: string): void {
      this._dataTips.forEach((val, key, map) => {
         if(val === name) {
            map.delete(key);
         }
      });
   }

   /**
    * Check if a data tip has been registered
    * @param name          the name of the data tip
    * @returns {boolean}   whether the data tip exists
    */
   isDataTip(name: string): boolean {
      let res: boolean = false;
      this._dataTips.forEach((dataTipName, key, map) => {
         if(dataTipName === name) {
            res = true;
         }
      });
      return res;
   }

   // check if the data tip (popup) component is currently shown
   isDataTipVisible(name: string): boolean {
      return this._dataTipsVisible.has(name) && this._dataTipsVisible.get(name);
   }

   isCurrentDataTip(dataTipName: string, popContainerName: string): boolean {
      return this.dataTipName &&
         (this.dataTipName === dataTipName || this.dataTipName === popContainerName);
   }

   isFrozen(): boolean {
      return this._freeze;
   }

   get viewerOffset(): {x: number, y: number, width: number, height: number, scrollLeft: number, scrollTop: number} {
      return this._viewerOffsetFunc();
   }

   set viewerOffsetFunc(f: () => {x: number, y: number, width: number, height: number, scrollLeft: number, scrollTop: number}) {
      this._viewerOffsetFunc = f;
   }

   /**
    * Set the current data tip and display at a given position. If the data tip is valid
    * and distinct from the last then make a request to the server to refresh related
    * assemblies with the given condition applied
    *
    * @param name          the name of the parent assembly of data tip
    * @param dataTipName   the name of the data tip
    * @param x             the x-pos in pixels
    * @param y             the y-pos in pixels
    * @param condition     the condition string to send to the server
    */
   showDataTip(name: string, dataTipName: string, x0: number, y0: number,
               condition: string = null, alpha: number = 1): void
   {
      let offset = this.viewerOffset;
      const x = x0 - offset.x;
      const y = y0 - offset.y;
      const inited = dataTipName && !!this.inited[dataTipName];

      if(!this._freeze || this._dataTipName != dataTipName && dataTipName) {
         if((dataTipName && dataTipName !== this._dataTipName) ||
            (condition !== this._condition) && this.isDataTipVisible(dataTipName))
         {
            const tipEvent = new OpenDataTipEvent();
            tipEvent.setName(dataTipName);
            tipEvent.setParent(name);
            tipEvent.setConds(condition);
            this.viewsheetClient.sendEvent("/events/datatip/open", tipEvent);

            if(!condition) {
               return;
            }

            // delay the display of datatip for a little so the data can be refreshed.
            // otherwise it will show the old data from previous data point before the
            // new condition is applied. we don't have the precise timing for this unless
            // we have each data tip component send a notification after data is refreshed.
            // this is only necessary for the first time the tip is shown.
            // if we do this all the time, the scrolling will flicker when moving between cells.
            if(!inited) {
               setTimeout(() => this.zone.run(() => this._dataTipName = dataTipName), 200);
            }
         }
         else if(this._dataTipName != dataTipName) {
            this._dataTipName = dataTipName;
            this.zone.run(() => this._dataTipName = dataTipName);
         }

         if(inited) {
            this._dataTipName = dataTipName;
         }
         else if(dataTipName) {
            this.inited[dataTipName] = true;
         }

         this._parentName = name;
         this._dataTipX = x;
         this._dataTipY = y;
         this._dataTipAlpha = alpha;
         this._condition = condition;
      }

      this._lastDataTip = {
         dataTipX: x0,
         dataTipY: y0,
         dataTipName: dataTipName,
         parentName: name,
         condition: condition,
         dataTipAlpha: alpha
      };
   }

   /**
    * Unfreeze and hide the current data tip
    */
   hideDataTip(clear?: boolean): void {
      if(this.isFrozen()) {
         this.unfreeze();
      }

      if(this._lastDataTip && clear) {
         this.clearDataTip();
      }

      this._dataTipName = null;
      this._parentName = null;
      this._condition = null;
      this._lastDataTip = null;
   }

   public clearDataTip() {
      this.showDataTip(this._parentName, this._dataTipName, this._lastDataTip.dataTipX, this._lastDataTip.dataTipY,
         null, this._lastDataTip.dataTipAlpha);
   }

   freeze(): void {
      if(this._lastDataTip) {
         this._freeze = false;
         this._dataTipName = null;
         this._condition = null;
         this.showDataTip(this._lastDataTip.parentName, this._lastDataTip.dataTipName,
                          this._lastDataTip.dataTipX, this._lastDataTip.dataTipY,
                          this._lastDataTip.condition, this._lastDataTip.dataTipAlpha);
         this._lastDataTip = null;
      }

      this._freeze = true;
   }

   unfreeze(): void {
      if(this._freeze) {
         this._freeze = false;
         this._lastDataTip = null;
         this.hideDataTip();
      }
   }

   get dataTipX(): number {
      return this._dataTipX;
   }

   get dataTipY(): number {
      return this._dataTipY;
   }

   get dataTipName(): string {
      return this._dataTipName;
   }

   get dataTipAlpha(): number {
      return this._dataTipAlpha;
   }

   // get the html id of the vsobject div
   public getVSObjectId(name: string) {
      return this.viewsheetClient.runtimeId + "_" + name.replace(/ /g, "-") + "_main";
   }

   public isDataTipSource(name: string): boolean {
      return this.hasDataTipShowing() && name && name == this._parentName;
   }

   public hasDataTipShowing(): boolean {
      return !!this._dataTipName && this.isDataTipVisible(this._dataTipName);
   }
}
