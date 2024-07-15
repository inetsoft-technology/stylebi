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
export class MultiObjectSelectList<T> {
   private objects: T[] = [];
   private _selectedObjects: T[] = [];
   private selectCursor: T | null = null;
   private _objectEqualsFun: (o1: T, o2: T) => boolean;

   get objectEqualsFun(): (o1: T, o2: T) => boolean {
      return this._objectEqualsFun;
   }

   set objectEqualsFun(fun: (o1: T, o2: T) => boolean) {
      this._objectEqualsFun = fun;
   }

   private get selectedObjects() {
      if(!this._selectedObjects) {
         this._selectedObjects = [];
      }

      return this._selectedObjects;
   }

   private set selectedObjects(selectedObjects: T[]) {
      this._selectedObjects = selectedObjects;
   }

   constructor(objects?: T[]) {
      if(objects) {
         this.objects = objects;
      }
   }

   private reset(objects: T[]) {
      this.objects = objects;
      this.selectedObjects = new Array<T>();
      this.selectCursor = null;
   }

   size(): number {
      return this.objects ? this.objects.length : 0;
   }

   clear(): void {
      this.reset(this.objects);
   }

   isSelected(item: T): boolean {
      return this.selectedObjects.includes(item);
   }

   hasSelection(): boolean {
      return this.selectedObjects.length > 0;
   }

   getSelectedObjects(): T[] {
      return [...this.selectedObjects];
   }

   select(item: T): void {
      this.checkBounds(item);

      this.clear();
      this.selectedObjects.push(item);
      this.selectCursor = item;
   }

   ctrlSelect(item: T): void {
      this.checkBounds(item);

      if(this.isSelected(item)) {
         this.deselect(item);
      }
      else {
         this.selectedObjects.push(item);
         this.selectCursor = item;
      }
   }

   shiftSelect(item: T): void {
      this.checkBounds(item);

      if(this.selectCursor == null || this.objectsEquals(this.selectCursor, item)) {
         this.select(item);
      }
      else {
         let newSelectedObjects = new Array<T>();
         let cursorIndex = this.objects
            .findIndex(obj => this.objectsEquals(obj, this.selectCursor));
         let index = this.objects
            .findIndex(obj => this.objectsEquals(obj, item));
         const start = Math.min(index, cursorIndex);
         const end = Math.max(index, cursorIndex);

         for(let i = start; i < end + 1; i++) {
            newSelectedObjects.push(this.objects[i]);
         }

         this.selectedObjects = newSelectedObjects;
      }
   }

   selectWithEvent(item: T, event: MouseEvent): void {
      if(event.ctrlKey) {
         this.ctrlSelect(item);
      }
      else if(event.shiftKey) {
         this.shiftSelect(item);
      }
      else {
         this.select(item);
      }
   }

   deselect(item: T): void {
      this.checkBounds(item);
      let index = this.selectedObjects.findIndex(obj => this.objectsEquals(obj, item));

      if(index < 0) {
         return;
      }

      this.selectedObjects.splice(index, 1);
      this.selectCursor = null;
   }

   setObjects(objects: T[]) {
      this.reset(objects);
   }

   setObjectsKeepSelection(objects: T[]) {
      let oldSelectedItems = this.selectedObjects;
      let oldSelectCursor = this.selectCursor;
      this.reset(objects);

      if(oldSelectedItems) {
         this.selectedObjects =
            oldSelectedItems.filter(ojb => objects.find(o => this.objectsEquals(o, ojb)));
      }

      if(oldSelectCursor) {
         this.selectCursor = objects.find(obj => this.objectsEquals(obj, oldSelectCursor));
      }
   }

   private checkBounds(item: T): void {
      if(!this.objects.includes(item)) {
         throw new Error(`object ${item} do not in the list`);
      }
   }

   private objectsEquals(obj1: T, obj2: T) {
      return this.objectEqualsFun ? this.objectEqualsFun(obj1, obj2) : obj1 == obj2;
   }
}
