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
   OnInit,
   Output,
   SimpleChanges
} from "@angular/core";
import { BehaviorSubject } from "rxjs";
import { HighlightModel } from "../../model/highlight-list-model";

export interface SelectedHighlight {
   element: string;
   highlight: string;
}

export interface ScheduleAlerts {
   enabled: boolean;
   highlights: SelectedHighlight[];
}

export class AlertHighlightRow {
   selected = false;

   get label(): string {
      let hightlight = this.highlight;

      if(this.highlight.match(/^RangeOutput_Range_\d+$/)) {
         hightlight = this.highlight.replace(/^RangeOutput_Range_(\d+)$/, "_#(js:Range) $1");
      }

      return this.count > 1 ? hightlight + `(${this.count})` : hightlight;
   }

   constructor(public element: string, public highlight: string, public condition: string,
               public count: number)
   {
   }
}

@Component({
   selector: "em-schedule-alerts",
   templateUrl: "./schedule-alerts.component.html",
   styleUrls: ["./schedule-alerts.component.scss"]
})
export class ScheduleAlertsComponent implements OnInit, OnChanges {
   @Input() enabled = false;
   @Output() alertsChanged = new EventEmitter<ScheduleAlerts>();

   @Input()
   get highlights(): HighlightModel[] {
      return this._highlights;
   }

   set highlights(val: HighlightModel[]) {
      this._highlights = val || [];
   }

   @Input()
   get selectedElements(): string[] {
      return this._selectedElements;
   }

   set selectedElements(val: string[]) {
      this._selectedElements = val || [];
   }

   @Input()
   get selectedHighlights(): string[] {
      return this._selectedHighlights;
   }

   set selectedHighlights(val: string[]) {
      this._selectedHighlights = val || [];
   }

   get disabled(): boolean {
      return !this.highlights || this.highlights.length == 0;
   }

   dataSource = new BehaviorSubject<AlertHighlightRow[]>([]);
   columnsToDisplay = ["select", "element", "highlight", "condition"];

   private _rows: AlertHighlightRow[] = [];
   private _highlights: HighlightModel[] = [];
   private _selectedElements: string[] = [];
   private _selectedHighlights: string[] = [];

   constructor() {
   }

   ngOnInit() {
   }

   ngOnChanges(changes: SimpleChanges): void {
      this.updateDataSource();
   }

   fireAlertsChanged(): void {
      this.alertsChanged.emit({
         enabled: this.enabled,
         highlights: this._rows
            .filter(hl => hl.selected)
            .map(hl => ({element: hl.element, highlight: hl.highlight}))
      });
   }

   isElementDisabled(row: AlertHighlightRow): boolean {
      return !this.highlights.some((h) =>
         h.element == row.element && h.highlight == row.highlight);
   }

   private updateDataSource(): void {
      this._rows = this.highlights
         .map(hl => new AlertHighlightRow(hl.element, hl.highlight, hl.condition, hl.count));

      for(let i = 0; i < this.selectedElements.length; i++) {
         const row = this._rows.find(
            r => r.element === this.selectedElements[i] && r.highlight === this.selectedHighlights[i]);

         if(row) {
            row.selected = true;
         }
         else {
            this._rows.push(new AlertHighlightRow(this.selectedElements[i],
               this.selectedHighlights[i], "-", 1));
         }
      }

      this.dataSource.next(this._rows);
   }
}
