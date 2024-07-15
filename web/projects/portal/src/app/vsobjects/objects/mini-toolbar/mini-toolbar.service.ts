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
import { ResizeSensor } from "css-element-queries";
import { fromEvent, Observable, Subscription, TeardownLogic } from "rxjs";
import { AssemblyActionGroup } from "../../../common/action/assembly-action-group";
import { Rectangular } from "../../../common/data/rectangle";
import { GuiTool } from "../../../common/util/gui-tool";
import { VSCalendarModel } from "../../model/calendar/vs-calendar-model";
import { VSObjectModel } from "../../model/vs-object-model";
import { VSSelectionBaseModel } from "../../model/vs-selection-base-model";
import { FeatureFlagsService, FeatureFlagValue } from "../../../../../../shared/feature-flags/feature-flags.service";

@Injectable()
export class MiniToolbarService {
   private listeners = new Map<HTMLElement, Observable<any>>();
   private scrollbarWidth = GuiTool.measureScrollbars();
   private hideMiniToolbarAssembly: string;
   private hideMiniToolbarFreeze: boolean = false;
   private mouseVisited: boolean = false;
   private unfreezeTimer: any;

   constructor(private zone: NgZone) {
   }

   hideMiniToolbar(assemblyName: string, visited: boolean = false) {
      this.hideMiniToolbarAssembly = assemblyName;
      this.hideMiniToolbarFreeze = false;
      this.mouseVisited = visited;
   }

   isMiniToolbarHidden(assemblyName: string): boolean {
      return this.hideMiniToolbarAssembly == assemblyName;
   }

   hiddenFreeze(assembly: string) {
      if(!this.isMiniToolbarHidden(assembly)) {
         return;
      }

      clearTimeout(this.unfreezeTimer);
      this.hideMiniToolbarFreeze = true;
   }

   hiddenUnfreeze(assembly: string, waitUI: boolean = true) {
      if(!this.isMiniToolbarHidden(assembly)) {
         return;
      }

      clearTimeout(this.unfreezeTimer);

      if(waitUI) {
         this.unfreezeTimer = setTimeout(() => this.hideMiniToolbarFreeze = false, 300);
      }
      else {
         this.hideMiniToolbarFreeze = false;
      }
   }

   isMouseVisited(): boolean {
      return this.mouseVisited;
   }

   mouseVisit() {
      this.mouseVisited = true;
   }

   handleMouseEnter(assembly: string, event: any) {
      if(this.isNonExistElement(event.fromElement)) {
         return;
      }

      if(this.isMiniToolbarHidden(assembly)) {
         if(this.isMouseVisited()) {
            this.resetMiniToolbarHidden(assembly);
         }

         this.mouseVisit();
      }
   }

   isNonExistElement(ele: any): boolean {
      if(!ele) {
         return false;
      }

      let {top, left, bottom, right, width, height} = GuiTool.getElementRect(ele);

      return top == 0 && left == 0 && bottom == 0 && right == 0 && width == 0 && height == 0;
   }

   private resetMiniToolbarHidden(assemblyName: string) {
      if(assemblyName == this.hideMiniToolbarAssembly && !this.hideMiniToolbarFreeze) {
         this.hideMiniToolbarAssembly = null;
         this.mouseVisited = false;
      }
   }

   /**
    * Add the scroll and resize listeners to the target
    * @param {HTMLElement} target the target that gets the event listeners
    * @param {(e) => void} scrollCallback triggered with UIEvent on scroll
    * @param {() => void} resizeCallback triggered on resize
    * @return {Subscription} used to clean up the listeners when unsubscribe is called
    */
   public addContainerEvents(target: HTMLElement,
                             scrollCallback: (e) => void,
                             resizeCallback: () => void): Subscription
   {
      if(target == null) {
         throw new TypeError("Toolbar container target shouldn't be null");
      }

      // add returns the teardown parameter instead of 'this' so we shouldn't chain
      const subscription = new Subscription();
      subscription.add(this.addScrollListener(target, scrollCallback));
      subscription.add(() => this.listeners.delete(target));
      subscription.add(this.addResizeListener(target, resizeCallback));
      return subscription;
   }

   public getToolbarWidth(vsObject: VSObjectModel, bounds: DOMRectInit, scale: number,
                          scrollLeft: number, includeScrollbar: boolean,
                          embeddedVSBounds?: Rectangular): number
   {
      const width = vsObject.realWidth ? vsObject.realWidth : vsObject.objectFormat.width;

      if(!bounds) {
         return width;
      }

      // now chart left and top do not be changed, when it is max mode and in embedded vs,
      // so need not fix the left with embeddedVSBounds. see vs-viewsheet.applyRefreshObject.
      //const embeddedVSLeftBound = embeddedVSBounds ? embeddedVSBounds.x : 0;
      const left = vsObject.objectFormat.left;
      const containerRightEdge = (bounds.width + scrollLeft) / scale - left;
      const scrollbarOffset = includeScrollbar ? this.scrollbarWidth / scale : 0;
      return Math.min(width, containerRightEdge - scrollbarOffset);
   }

   public getToolbarLeft(left: number, bounds: DOMRectInit, scale: number, boundsScrollLeft: number,
                         includeScrollbar: boolean, actions: AssemblyActionGroup[],
                         embeddedVSBounds?: Rectangular): number
   {
      const actionsWidth: number = this.getActionsWidth(actions);
      const embeddedVSLeftBound = embeddedVSBounds ? embeddedVSBounds.x : 0;
      const scrollbarOffset = includeScrollbar ? this.scrollbarWidth / scale : 0;
      const containerRightBound = (bounds.width + boundsScrollLeft - scrollbarOffset) / scale;

      if(left + embeddedVSLeftBound + actionsWidth > containerRightBound) {
         return containerRightBound - actionsWidth - embeddedVSLeftBound;
      }

      return left;
   }

   public getActionsWidth(actions: AssemblyActionGroup[]) {
      let count = 0;
      let groupCount = 0;
      let actionIconSize = 18; // see .icon-size-small.
      let actionsIconPadding = 2; // see .mini-toolbar .mini-toolbar-container button i.
      let buttonPaddingRem = 0.5; // see .btn-group-sm>.btn, .btn-sm
      let buttonBorder = 1; // see .bd-gray
      let actionWidth = 40;
      let rootFontSize = parseInt(getComputedStyle(window.document.documentElement)["font-size"], 10);

      if(rootFontSize) {
         actionWidth = actionIconSize + 2 * actionsIconPadding +
            2 * buttonPaddingRem * rootFontSize + buttonBorder * 2;
      }

      actions.filter(g => g.visible)
         .forEach(g => {
            let visibleCount = g.actions.filter(a => a.visible()).length;
            count += visibleCount;

            if(visibleCount != 0) {
               ++groupCount;
            }
         });

      // Currently, subsequent buttons overlap the previous button by 1px.
      const overlap = Math.max(0, count - 1);
      return count * actionWidth - overlap + groupCount - 1;
   }

   public getActionCount(actions: AssemblyActionGroup[]): number {
      let count: number = 0;
      actions.filter(g => g.visible)
         .forEach(g => {
            let visibleCount = g.actions.filter(a => a.visible()).length;
            count += visibleCount;
         });

      return count;
   }

   /**
    * Observe the scroll events on a target Element
    */
   private addScrollListener(target: HTMLElement, callback: (e) => void): Subscription {
      if(!this.listeners.has(target)) {
         this.listeners.set(target, fromEvent(target, "scroll"));
      }

      return this.zone.runOutsideAngular(() => this.listeners.get(target).subscribe(callback));
   }

   /**
    * Observe the resize events on a target Element. Uses the ResizeSensor.
    */
   private addResizeListener(target: HTMLElement, callback: () => void): TeardownLogic {
      const ev = this.zone.runOutsideAngular(() => callback);
      new ResizeSensor(target, ev);
      return () => ResizeSensor.detach(target, ev);
   }

   public isMiniToolbarVisible(vsObject: VSObjectModel): boolean {
      return !!vsObject && vsObject.enabled &&
         !(vsObject as VSSelectionBaseModel).dropdown &&
         !(vsObject as VSCalendarModel).dropdownCalendar &&
         vsObject.containerType !== "VSSelectionContainer";
   }

   public hasMiniToolbar(vsObject: VSObjectModel): boolean {
      return !!vsObject && (vsObject.objectType == "VSCalcTable" ||
         vsObject.objectType == "VSCalendar" ||
         vsObject.objectType == "VSChart" ||
         vsObject.objectType == "VSCrosstab" ||
         vsObject.objectType == "VSSelectionList" ||
         vsObject.objectType == "VSSelectionTree" ||
         vsObject.objectType == "VSTable" ||
         vsObject.objectType == "VSSelectionContainer" ||
         vsObject.objectType == "VSRangeSlider" &&
         (<any> vsObject).adhocFilter);
   }
}
