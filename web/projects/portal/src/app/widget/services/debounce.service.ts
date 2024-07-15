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
import { Injectable, NgZone } from "@angular/core";

export interface DebounceCallback {
   callback: DebounceFn;
   args: any[];
}

export type DebounceFn = (...args: any[]) => any;
export type DebounceReducer =
   (previous: DebounceCallback, next: DebounceCallback) => DebounceCallback;

const defaultReducer = (previous, next) => next;

@Injectable({
   providedIn: "root"
})
export class DebounceService {
   private callbacks = new Map<string, {callback: DebounceCallback, timeoutId: any}>();

   constructor(private zone: NgZone) {
   }

   debounce(key: string, fn: DebounceFn, delay: number, args: any[] = [],
            reducer: DebounceReducer = defaultReducer): void
   {
      let next = {callback: fn, args};

      if(this.callbacks.has(key)) {
         const previous = this.callbacks.get(key);
         next = reducer(previous.callback, next);
         clearTimeout(previous.timeoutId);
         this.callbacks.delete(key);
      }

      // debounce should not cause an extra change detection
      this.zone.runOutsideAngular(() => {
         const timeoutId = setTimeout(() => {
            if(this.callbacks.has(key)) {
               const {callback} = this.callbacks.get(key);
               callback.callback.apply(null, callback.args);
               this.callbacks.delete(key);
            }
         }, delay);

         this.callbacks.set(key, {callback: next, timeoutId: timeoutId});
      });
   }

   cancel(key: string): void {
      if(this.callbacks.has(key)) {
         clearTimeout(this.callbacks.get(key).timeoutId);
         this.callbacks.delete(key);
      }
   }
}
