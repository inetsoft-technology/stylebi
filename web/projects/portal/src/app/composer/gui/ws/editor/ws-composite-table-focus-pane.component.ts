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
import {
   Component,
   EventEmitter,
   Input,
   OnChanges,
   Output,
   SimpleChanges,
   ViewChild
} from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { tap } from "rxjs/operators";
import { Notification } from "../../../../common/data/notification";
import { ComponentTool } from "../../../../common/util/component-tool";
import { AbstractTableAssembly } from "../../../data/ws/abstract-table-assembly";
import { CompositeTableAssembly } from "../../../data/ws/composite-table-assembly";
import { Worksheet } from "../../../data/ws/worksheet";
import { WSCompositeBreadcrumb } from "../../../data/ws/ws-composite-breadcrumb";
import { Observable, of, Subject } from "rxjs";
import { WSMergeJoinEditorPaneComponent } from "./merge/ws-merge-join-editor-pane.component";

@Component({
   selector: "ws-composite-table-focus-pane",
   templateUrl: "ws-composite-table-focus-pane.component.html",
   styleUrls: ["ws-composite-table-focus-pane.component.scss"]
})
export class WSCompositeTableFocusPaneComponent {
   @Input() worksheet: Worksheet;
   @Input() crossJoinEnabled = true;
   @Output() onWorksheetCompositionChanged: EventEmitter<null> = new EventEmitter<null>();
   @Output() onWorksheetCancel: EventEmitter<null> = new EventEmitter<null>();
   @Output() onNotification = new EventEmitter<Notification>();
   @ViewChild("mergeJoinPane") mergeJoinPane: WSMergeJoinEditorPaneComponent;
   crossJoins: [string, string][] = [];

   constructor(private modal: NgbModal) {
   }

   unfocusCompositeTable(cancel: boolean = false) {
      let canContinue: Observable<boolean>;

      if(this.worksheet.selectedCompositeTable) {
         canContinue = this.checkCrossJoins(cancel).pipe(tap(ok => {
            if(ok) {
               this.worksheet.selectedSubtables = [];
               this.crossJoins = [];
            }
         }));
      }
      else {
         canContinue = of(true);
      }

      canContinue.subscribe(ok => {
         if(ok) {
            this.worksheet.exitCompositeView();
            this.onWorksheetCompositionChanged.emit();
         }
      });
   }

   cancelCompositeTable() {
      this.onWorksheetCancel.emit();
      this.unfocusCompositeTable(true);
   }

   focusCompositeTable(table: CompositeTableAssembly) {
      this.worksheet.selectedCompositeTable = table;
      this.crossJoins = [];
      this.onWorksheetCompositionChanged.emit();
   }

   selectSubtables(subtables: AbstractTableAssembly[]) {
      this.worksheet.selectedSubtables = subtables;
   }

   selectBreadcrumb(breadcrumb: WSCompositeBreadcrumb) {
      this.worksheet.compositeViewInfo.selectedBreadcrumb = breadcrumb;
      this.onWorksheetCompositionChanged.emit();
   }

   notify(notification: Notification) {
      this.onNotification.emit(notification);
   }

   private checkCrossJoins(cancel: boolean): Observable<boolean> {
      if(this.worksheet.selectedCompositeTable.tableClassType === "RelationalJoinTableAssembly" &&
         !!this.crossJoins && !!this.crossJoins.length && !cancel)
      {
         const subject = new Subject<boolean>();

         if(this.crossJoinEnabled) {
            ComponentTool
               .showConfirmDialog(this.modal, "_#(js:Cross Join)", "_#(js:cross.join.prompt)")
               .then(ok => {
                  subject.next(ok === "ok");
                  subject.complete();
               });
         }
         else {
            ComponentTool
               .showMessageDialog(this.modal, "_#(js:Cross Join)", "_#(js:cross.join.forbidden)")
               .then(() => {
                  subject.next(false);
                  subject.complete();
               });
         }

         return subject.asObservable();
      }

      return of(true);
   }
}
