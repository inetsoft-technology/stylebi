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
import { Injectable } from "@angular/core";
import { from as observableFrom, Observable, ReplaySubject, Subject } from "rxjs";

@Injectable({
   providedIn: "root"
})
export class DragService {
   private dragData: {[key: string]: any} = {};
   readonly dragDataListeners: {[key: string]: {subject: ReplaySubject<any>, numSubs: number}} = {};
   readonly dragBeginSubject: Subject<null> = new Subject<null>();
   readonly dragEndSubject: Subject<null> = new Subject<null>();
   private _currentlyDragging: boolean = false;
   private readonly _currentlyDraggingSubject: ReplaySubject<boolean> = new ReplaySubject<boolean>(1);
   private readonly _currentlyDraggingObservable: Observable<boolean> =
      observableFrom(this._currentlyDraggingSubject);

   private readonly dragstartCallback = this.beginDrag.bind(this);
   private readonly dragendCallback = this.endDragAfterTick.bind(this);

   public get currentlyDragging(): boolean {
      return this._currentlyDragging;
   }

   private setCurrentlyDragging(currentlyDragging: boolean): void {
      this._currentlyDragging = currentlyDragging;
      this._currentlyDraggingSubject.next(currentlyDragging);
   }

   public get currentDraggingObservable(): Observable<boolean> {
      return this._currentlyDraggingObservable;
   }

   public initListeners(ref: EventTarget): void {
      ref.addEventListener("dragstart", this.dragstartCallback, true);
      ref.addEventListener("drop", this.dragendCallback, true);
      ref.addEventListener("dragend", this.dragendCallback, true);
   }

   public removeListeners(ref: EventTarget): void {
      ref.removeEventListener("dragstart", this.dragstartCallback, true);
      ref.removeEventListener("drop", this.dragendCallback, true);
      ref.removeEventListener("dragend", this.dragendCallback, true);
   }

   put(key: string, data: any): void {
      this.dragData[key] = data;

      if(this.currentlyDragging && this.dragDataListeners[key]) {
         this.dragDataListeners[key].subject.next(data);
      }
   }

   get(key: string): any {
      return this.dragData[key];
   }

   getDragData(): {[key: string]: any} {
      return this.dragData;
   }

   reset(): void {
      const keys = Object.keys(this.dragData);

      for(const key of keys) {
         if(this.dragDataListeners[key]) {
            this.dragDataListeners[key].subject.next(null);
         }
      }

      this.dragData = {};
   }

   private beginDrag(): void {
      this.dragBeginSubject.next(null);
      this.setCurrentlyDragging(true);
      const keys = Object.keys(this.dragData);

      for(const key of keys) {
         if(this.dragDataListeners[key]) {
            this.dragDataListeners[key].subject.next(this.dragData[key]);
         }
      }
   }

   private endDrag(): void {
      if(this.currentlyDragging) {
         this.dragEndSubject.next(null);
         this.reset();
         this.setCurrentlyDragging(false);
      }
   }

   /** Wait a tick so that the actual event handlers are processed before this resolves. */
   private endDragAfterTick(): void {
      setTimeout(() => {
         this.endDrag();
      }, 0);
   }

   /**
    * Register a listener that listens to the keys of dragData and emits the value if it
    * has one when drag begins
    */
   registerDragDataListener(key: string): ReplaySubject<any> {
      if(!this.dragDataListeners[key]) {
         this.dragDataListeners[key] = {subject: new ReplaySubject<any>(1), numSubs: 1};
      }
      else {
         this.dragDataListeners[key].numSubs++;
      }

      return this.dragDataListeners[key].subject;
   }

   disposeDragDataListener(key: string): void {
      if(this.dragDataListeners[key]) {
         if(this.dragDataListeners[key].numSubs <= 1) {
            this.dragDataListeners[key].subject.complete();
            delete this.dragDataListeners[key];
         }
         else {
            this.dragDataListeners[key].numSubs--;
         }
      }
   }

   getDragDataValues<T>(event: DragEvent): T[] {
      if(!event.dataTransfer.getData("text")) {
         return null;
      }

      let result: T[] = [];
      let data = JSON.parse(event.dataTransfer.getData("text"));

      if(!!data.dragName) {
         for(let dragKey of data.dragName) {
            let dragValue: T = data[dragKey];

            if(!!dragValue) {
               result.push(dragValue);
            }
         }
      }

      return result;
   }
}
