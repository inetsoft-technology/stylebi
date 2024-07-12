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
import { merge as observableMerge, Subject } from "rxjs";

declare var window: Window;

@Injectable()
export class ResizeHandlerService {
   private _windowResizeSubject: Subject<any> = new Subject<any>();
   private _verticalResizeSubject: Subject<any> = new Subject<any>();
   private _horizontalResizeSubject: Subject<any> = new Subject<any>();
   private windowListener: EventListener;

   private _anyResizeSubject: Subject<any> = <Subject<any>> observableMerge(
      this.windowResizeSubject.asObservable(),
      this.verticalResizeSubject.asObservable(),
      this.horizontalResizeSubject.asObservable()
   );

   public get windowResizeSubject(): Subject<any> {
      return this._windowResizeSubject;
   }

   public get verticalResizeSubject(): Subject<any> {
      return this._verticalResizeSubject;
   }

   public get horizontalResizeSubject(): Subject<any> {
      return this._horizontalResizeSubject;
   }

   public get anyResizeSubject(): Subject<any> {
      return this._anyResizeSubject;
   }

   public initListeners() {
      if(!!this.windowListener) {
         return;
      }

      let timeout: any;

      this.windowListener = (e: UIEvent) => {
         clearTimeout(timeout);
         timeout = setTimeout(() => {
            this._windowResizeSubject.next(e);
         }, 100);
      };

      window.addEventListener("resize", this.windowListener);
   }

   public removeListeners() {
      window.removeEventListener("resize", this.windowListener);
   }

   public onVerticalResizeEnd() {
      this._verticalResizeSubject.next(null);
   }

   public onHorizontalDrag() {
      this._horizontalResizeSubject.next(null);
   }
}
