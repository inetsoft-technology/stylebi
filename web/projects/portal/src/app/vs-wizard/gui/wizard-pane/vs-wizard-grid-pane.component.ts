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
import {
   ChangeDetectionStrategy,
   Component,
   EventEmitter,
   Input, NgZone,
   OnChanges,
   OnDestroy,
   Output,
   SimpleChanges
} from "@angular/core";
import { Rectangle } from "../../../common/data/rectangle";
import { DebounceService } from "../../../widget/services/debounce.service";
import { VSWizardConstants } from "../../model/vs-wizard-constants";
import { WizardNewObjectModel } from "../objects/wizard-new-object-model";

@Component({
   selector: "vs-wizard-grid-pane",
   templateUrl: "./vs-wizard-grid-pane.component.html",
   styleUrls: ["./vs-wizard-grid-pane.component.scss"],
   changeDetection: ChangeDetectionStrategy.OnPush
})
export class VsWizardGridPaneComponent implements OnChanges, OnDestroy {
   @Input() cellWidth: number = 100;
   @Input() cellHeight: number = 40;
   @Input() rowCount = 12;
   @Input() colCount = 9;
   @Output() onChangeNewObject = new EventEmitter<WizardNewObjectModel>();
   rowRange: void[];
   colRange: void[];
   private readonly DEBOUNCE_TIME_MS = 100;

   constructor(private debounceService: DebounceService,
               private zone: NgZone) {
   }

   ngOnChanges(changes: SimpleChanges): void {
      if(changes.hasOwnProperty("rowCount")) {
         this.rowRange = new Array<void>(this.rowCount);
      }

      if(changes.hasOwnProperty("colCount")) {
         this.colRange = new Array<void>(this.colCount);
      }
   }

   ngOnDestroy(): void {
      this.debounceService.cancel("wizard new object");
   }

   onMouseChange(row: number, col: number, enter: boolean): void {
      this.debounceService.debounce("wizard new object", () => {
         this.zone.run(() => {
            this.onChangeNewObject.emit({
               visible: enter,
               bounds: new Rectangle(col * VSWizardConstants.GRID_CELL_WIDTH,
                  row * VSWizardConstants.GRID_CELL_HEIGHT, VSWizardConstants.NEW_OBJECT_WIDTH,
                  VSWizardConstants.NEW_OBJECT_HEIGHT)
            });
         });
      }, this.DEBOUNCE_TIME_MS, []);
   }
}
