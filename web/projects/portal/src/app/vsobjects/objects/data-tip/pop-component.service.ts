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
import { Injectable } from "@angular/core";
import { Observable, Subject } from "rxjs";
import { VSObjectModel } from "../../model/vs-object-model";
import { OpenDataTipEvent } from "../../event/open-datatip-event";
import { ViewsheetClientService } from "../../../common/viewsheet-client/viewsheet-client.service";

export enum PopLocation { CENTER = "CENTER", MOUSE = "MOUSE" }

export interface PopInfo {
   top: number;
   left: number;
   width: number;
   height: number;
   absoluteName: string;
   vsObject: VSObjectModel;
}

/**
 * Manage the pop components.
 */
@Injectable()
export class PopComponentService {
   get componentRegistered(): Observable<{ name: string, parent: string }> {
      return this._componentRegistered.asObservable();
   }

   private _componentRegistered = new Subject<{name: string, parent: string}>();
   private _popX: number;
   private _popY: number;
   private popLocation: PopLocation;
   private _popComponent: string;
   private _popComponentSource: string;
   private _popAlpha: number;
   private _viewerOffsetFunc: () => {x: number, y: number, width: number, height: number, scrollLeft: number, scrollTop: number} = () => {
      return {x: 0, y: 0, width: 0, height: 0, scrollLeft: 0, scrollTop: 0};
   };
   private _getComponentModelFunc: (string) => VSObjectModel;
   // Map of pop component names -> parent names
   private _popComponents: {[name: string]: string} = {};
   private _popInfos: {[name: string]: PopInfo} = {};
   // Feature 19827, only when the pop component is added to layout pane,
   // the pop component will show up.
   // Map of pop component names -> visibility.
   private _popComponentsVisible: Map<string, boolean> = new Map<string, boolean>();
   private _popComponentOffsets: Map<string, any> = new Map<string, any>();
   private popContainers: Map<string, string> = new Map<string, string>();
   private popContainerCallbacks: Map<string, ((event: MouseEvent) => boolean)[]> =
      new Map<string, ((event: MouseEvent) => boolean)[]>();
   private _componentPop = new Subject<string>();

   get componentPop(): Observable<string> {
      return this._componentPop.asObservable();
   }

   constructor(private viewsheetClient: ViewsheetClientService) {}

   /**
    * Register a pop component with the service
    * @param name       the name of the data tip
    * @param parent     the name of its parent
    */
   registerPopComponent(name: string, parent: string, parentTop: number,
                        parentLeft: number, parentWidth: number,
                        parentHeight: number, absoluteName: string,
                        vsObject: VSObjectModel,
                        container: string = null): void
   {
      if(name && parent) {
         this._popComponents[name] = parent;
      }

      if(parent) {
         this._popInfos[parent] = {
            top: parentTop,
            left: parentLeft,
            width: parentWidth,
            height: parentHeight,
            absoluteName: absoluteName,
            vsObject: vsObject
         };
      }

      if(container) {
         this.popContainers.set(absoluteName, container);
      }

      this._componentRegistered.next({name, parent});
   }

   /**
    * leaves a callback so that if a pop component contains multiple elements, it can use
    * its children to see if the click event is inside of the containing element
    * @param {string} container
    * @param {(event: MouseEvent) => boolean} containerNameCallback
    */
   registerPopComponentChild(popcomponent: string, containerNameCallback: (event: MouseEvent) => boolean): void
   {
      let container = this.popContainers.get(popcomponent);

      if(!container || !containerNameCallback) {
         return;
      }

      if(!this.popContainerCallbacks.get(container)) {
         this.popContainerCallbacks.set(container, []);
      }

      this.popContainerCallbacks.get(container).push(containerNameCallback);
   }

   /**
    * tells if mouse event clicks on an element that is inside of a containing element
    * that is the current pop component
    * @param {MouseEvent} event
    * @returns {boolean} inside -> true, outside -> false
    */
   isInPopContainer(event: MouseEvent): boolean {
      let callbacks = this.popContainerCallbacks.get(this._popComponent);

      if(callbacks) {
         return callbacks.reduce((val, fn) => fn(event) || val, false);
      }

      return false;
   }

   /**
    * Register a data tip visibility with the service
    * @param {string} data tip name
    * @param {boolean} visible
    */
   registerPopComponentVisible(name: string, visible: boolean): void {
      if(name && visible) {
         this._popComponentsVisible.set(name, visible);
      }
   }

   getPopComponent(): string {
      return this._popComponent;
   }

   getPopComponentModel(): VSObjectModel {
      if(this.getComponentModelFunc) {
         return this.getComponentModelFunc(this._popComponent);
      }

      return null;
   }

   getPopInfo(name): PopInfo {
      return this._popInfos[name];
   }

   getTriggerPopInfo(name): PopInfo {
      return this._popInfos[this._popComponents[name]];
   }

   putPopViewerOffset(name, offset: any) {
      this._popComponentOffsets.set(name, offset);
   }

   getPopViewerOffset(name) {
      return this._popComponentOffsets.get(name);
   }

   clearPopViewerOffset() {
      this._popComponentOffsets.clear();
   }

   get viewerOffset(): {x: number, y: number, width: number, height: number, scrollLeft: number, scrollTop: number} {
      return this._viewerOffsetFunc();
   }

   set viewerOffsetFunc(f: () => {x: number, y: number, width: number, height: number, scrollLeft: number, scrollTop: number}) {
      this._viewerOffsetFunc = f;
   }

   set getComponentModelFunc(f: (string) => VSObjectModel) {
      this._getComponentModelFunc = f;
   }

   get getComponentModelFunc(): (string) => VSObjectModel {
      return this._getComponentModelFunc;
   }

   /**
    * Check if a pop component has been registered
    * @param name          the name of the data tip
    * @returns {boolean}   whether the data tip exists
    */
   isPopComponent(name: string): boolean {
      return this._popComponents.hasOwnProperty(name) && this._popComponents[name] !== null;
   }

   // check if the pop component is currently shown
   isPopComponentVisible(name: string): boolean {
      let container = this.popContainers.get(name);
      name = !!container ? container : name;

      return this._popComponentsVisible.has(name) && this._popComponentsVisible.get(name);
   }

   showPopComponent(parentName: string, popName) {
      const tipEvent = new OpenDataTipEvent();
      tipEvent.setName(popName);
      tipEvent.setParent(parentName);
      this.viewsheetClient.sendEvent("/events/datatip/open", tipEvent);
   }

   /**
    * Toggle visibility of pop component at the specified location.
    *
    * @param name          the name of the data tip to show
    * @param x             the x-pos in pixels
    * @param y             the y-pos in pixels
    */
   toggle(name: string, x: number, y: number, alpha: number, source: string): void {
      let offset = this.viewerOffset;
      this._popX = x - offset.x;
      this._popY = y - offset.y;
      this._popAlpha = alpha;

      if(name && this._popComponent && this._popComponent != name) {
         // hide current, and then show next
         this._popComponent = null;
         this._popComponentSource = null;
         this.showPopComponent(source, name);

         setTimeout(() => {
            this._popComponent = name;
            this._popComponentSource = source;
            this._componentPop.next(this._popComponent);
         }, 0);
      }
      else {
         this.showPopComponent(source, name);

         // make sure the code is executed after all outside listeners have been fired
         setTimeout(() => {
            this._popComponent = (this._popComponent == name) ? null : name;
            this._popComponentSource = !this._popComponent ? null : source;
            this._componentPop.next(this._popComponent);
         });
      }
   }

   hidePopComponent() {
      this.clearPopViewerOffset();
      this._popComponent = null;
      this._popComponentSource = null;
   }

   // Check if this component should be visible. Either it's not a popcomponent or its popped up.
   isVisible(name: string): boolean {
      return this._popComponents[name] == null || name == this._popComponent &&
         this.isPopComponentVisible(name);
   }

   isCurrentPopComponent(popComponentName: string, popContainerName: string): boolean {
      return !popContainerName && this.isVisible(popComponentName) ||
         popContainerName && this.isVisible(popContainerName);
   }

   isPopSource(name: string): boolean {
      return this.hasPopUpComponentShowing() && name && name == this._popComponentSource;
   }

   get popX(): number {
      return this._popX;
   }

   get popY(): number {
      return this._popY;
   }

   get popAlpha(): number {
      return this._popAlpha;
   }

   getPopLocation(): PopLocation {
      return this.popLocation;
   }

   setPopLocation(popLocation: PopLocation): void {
      this.popLocation = popLocation;
   }

   hasPopUpComponentShowing(): boolean {
      return this._popComponent && this._popComponentsVisible.get(this._popComponent);
   }

   getPopLocationLabel(component: string): string {
      switch(component) {
      case PopLocation.MOUSE:
         return "_#(js:Mouse)";
      case PopLocation.CENTER:
         return "_#(js:Center)";
      default:
         return component;
      }
   }
}
