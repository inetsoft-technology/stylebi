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
import { Observable ,  ReplaySubject ,  Subject } from "rxjs";
import { SelectionContainerChildDragModel } from "../selection-container-child-drag-model";

@Injectable()
export class SelectionContainerChildrenService {
   private childUpdated: Subject<number> = new Subject<number>();
   private subject: ReplaySubject<SelectionContainerChildDragModel> =
      new ReplaySubject<SelectionContainerChildDragModel>(1);
   private dragModel: SelectionContainerChildDragModel =
      <SelectionContainerChildDragModel> { dragging: false };
   private childDraggedOver: number = -1;

   public get onChildUpdate(): Observable<number> {
      return this.childUpdated.asObservable();
   }

   public updateChild(childIndex: number): void {
      this.childUpdated.next(childIndex);
   }

   public get dragModelSubject(): ReplaySubject<SelectionContainerChildDragModel> {
      return this.subject;
   }

   public get childDragModel(): SelectionContainerChildDragModel {
      return this.dragModel;
   }

   /** Push new drag model */
   public pushModel(model: SelectionContainerChildDragModel): void {
      this.dragModel = model;
      this.update();
   }

   public get childWithBorder(): number {
      return this.childDraggedOver;
   }

   public set childWithBorder(childDraggedOver: number) {
      this.childDraggedOver = childDraggedOver;
   }

   /** Notify all subscribers of new dragModel */
   private update(): void {
      this.subject.next(this.dragModel);
   }
}