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
  ChangeDetectorRef,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  Output,
  Renderer2,
  ViewChild
} from "@angular/core";
import { GuiTool } from "../../common/util/gui-tool";
import { SortInfo } from "../../vsobjects/objects/table/sort-info";
import { BaseTableCellModel } from "../../vsobjects/model/base-table-cell-model";
import { NgbTooltip } from "@ng-bootstrap/ng-bootstrap";
import { ModelService } from "../../widget/services/model.service";
import { UIContextService } from "../../common/services/ui-context.service";
import { XConstants } from "../../common/util/xconstants";
import { ProfileTableDataEvent } from "../model/profile-table-data-event";
import { TableDataEvent } from "../../widget/simple-table/table-data-event";

@Component({
  selector: "profiling-data-pane",
  templateUrl: "./profiling-data-pane.component.html",
  styleUrls: ["./profiling-data-pane.component.scss"]
})
export class ProfilingDataPaneComponent {
  @Input() chartUrl: string;
  @Output() onCommit: EventEmitter<any> = new EventEmitter<any>();
  @Output() onCancel: EventEmitter<any> = new EventEmitter<any>();
  @Output() onReloadTable: EventEmitter<ProfileTableDataEvent> = new EventEmitter<ProfileTableDataEvent>();
  @Output() onShowValue: EventEmitter<boolean> = new EventEmitter<boolean>();

  showValue: boolean = true;
  _showDetails: boolean = false;
  sortInfo: SortInfo;

  scrollbarWidth: number;
  tableHeight: number;
  scrollY: number = 0;
  _tableData: BaseTableCellModel[][] = [];
  //Currently depends on font-size and line height.
  readonly cellHeight: number = 28;

  constructor() {
  }

  @Input()
  set tableData(tableData: BaseTableCellModel[][]) {
    if(!tableData) {
      tableData = [];
    }

    this._tableData = tableData;

    if(this.scrollbarWidth == undefined) {
      this.scrollbarWidth = GuiTool.measureScrollbars();
    }

    if(tableData && tableData.length > 0) {
      this.tableHeight = tableData.length * this.cellHeight;
    }
  }

  get tableData(): BaseTableCellModel[][] {
    return this._tableData;
  }

  get showDetails(): boolean {
    return this._showDetails;
  }

  set showDetails(show: boolean) {
    this._showDetails = show;

    let event = <ProfileTableDataEvent> {
      sortValue: 0,
      sortCol: 0
    };

    this.onReloadTable.emit(event);
  }

  sortClicked(sortEvent: TableDataEvent): void {
    if(!this.sortInfo) {
      this.sortInfo = <SortInfo> {
        sortValue: XConstants.SORT_ASC
      };
    }

    this.sortInfo.col = sortEvent.sortCol;
    this.sortInfo.sortValue = sortEvent.sortValue;

    let event = <ProfileTableDataEvent> {
      sortValue: this.sortInfo.sortValue,
      sortCol: this.sortInfo.col
    };

    this.onReloadTable.emit(event);
  }
}
