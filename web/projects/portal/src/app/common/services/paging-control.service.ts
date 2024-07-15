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
import { Observable, Subject } from "rxjs";
import { PagingControlModel } from "../../vsobjects/model/paging-control-model";

@Injectable({
   providedIn: "root"
})
export class PagingControlService {
   hasDropdownOrTooltip: boolean = false;
   private changedScrollTop: Subject<number> = new Subject<number>();
   private changedScrollLeft: Subject<number> = new Subject<number>();
   private model: PagingControlModel;
   private buttonSize: number = 80;

   setPagingControlModel(model: PagingControlModel): void {
      this.model = model;
   }

   getPagingControlModel(): PagingControlModel {
      return this.model;
   }

   getCurrentAssembly(): string {
      return this.model.assemblyName;
   }

   scrollTop(): Observable<number> {
      return this.changedScrollTop.asObservable();
   }

   scrollTopChange(changed: number): void {
      this.changedScrollTop.next(changed);
   }

   scrollLeft(): Observable<number> {
      return this.changedScrollLeft.asObservable();
   }

   scrollLeftChange(changed: number): void {
      this.changedScrollLeft.next(changed);
   }

   inViewport(event: any, moving: boolean = false): boolean {
      return this.xDirectionInView(event, moving) && this.yDirectionInView(event, moving);
   }

   xDirectionInView(event: any, moving: boolean = false): boolean {
      const viewWidth = window.innerWidth;
      const touchX = event.clientX;
      const buttonSize = moving ? this.buttonSize / 2 : this.buttonSize;
      return !moving ? touchX <= viewWidth - buttonSize :
         touchX - buttonSize >= 0 && touchX <= viewWidth - buttonSize;
   }

   yDirectionInView(event: any, moving: boolean = false): boolean {
      const viewHeight = window.innerHeight;
      const touchY = event.clientY;
      const buttonSize = moving ? this.buttonSize / 2 : this.buttonSize;
      return !moving ? touchY <= viewHeight - buttonSize :
         touchY - buttonSize >= 0 && touchY <= viewHeight - buttonSize;
   }
}