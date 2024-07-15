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
   Component,
   EventEmitter,
   Input,
   NgZone,
   OnDestroy,
   Output,
   TemplateRef,
   ViewChild, ViewEncapsulation,
} from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { CommandProcessor, ViewsheetClientService } from "../../../common/viewsheet-client";
import { DownloadService } from "../../../../../../shared/download/download.service";
import { DialogService } from "../../../widget/slide-out/dialog-service.service";
import { LoadPreviewTableCommand } from "../../command/load-preview-table-command";
import { BaseTableCellModel } from "../../model/base-table-cell-model";
import { SortInfo } from "./sort-info";
import { DetailDndInfo } from "./detail-dnd-info";
import { SlideOutOptions } from "../../../widget/slide-out/slide-out-options";
import { Tool } from "../../../../../../shared/util/tool";
import { FormatInfoModel } from "../../../common/data/format-info-model";
import { Observable } from "rxjs";
import { ComponentTool } from "../../../common/util/component-tool";
import { SlideOutRef } from "../../../widget/slide-out/slide-out-ref";
import { TableStylePaneModel } from "../../../widget/table-style/table-style-pane-model";
import { ContextProvider } from "../../context-provider.service";
import { BaseHrefService } from "../../../common/services/base-href.service";

@Component({
   selector: "vs-preview-table",
   templateUrl: "./vs-preview-table.component.html",
   styleUrls: ["./vs-preview-table.component.scss"],
   encapsulation: ViewEncapsulation.None
})
export class VSPreviewTable extends CommandProcessor implements OnDestroy {
   @Input() assemblyName: string;
   @Input() linkUri: string;
   @Input() isDetails: boolean = true;
   @Input() isDataTip: boolean = false;
   @Input() formatGetter: (wsId: string, column: number) => Observable<FormatInfoModel>;
   @Output() onPreviewClose = new EventEmitter<void>();
   @Output() onStyleChange = new EventEmitter<string>();
   @Output() onChange = new EventEmitter<{
      sortInfo: SortInfo,
      format: FormatInfoModel,
      column: number[],
      str: string,
      detailStyle: string,
      dndInfo: DetailDndInfo,
      newColName?: string,
      toggleHide?: boolean
   }>();
   @ViewChild("content") modalContent: TemplateRef<any>;
   @ViewChild("styleDialog") styleDialog: TemplateRef<any>;
   tableData: BaseTableCellModel[][];
   worksheetId: string;
   sortInfo: SortInfo;
   formatModel: FormatInfoModel;
   tableStylePaneModel: TableStylePaneModel;
   slideOut: SlideOutRef;
   colWidths: number[];

   constructor(public viewsheetClient: ViewsheetClientService,
               private downloadService: DownloadService,
               private modalService: DialogService,
               private ngbModalService: NgbModal,
               zone: NgZone,
               protected context: ContextProvider,
               private baseHrefService: BaseHrefService)
   {
      super(viewsheetClient, zone);
   }

   ngOnDestroy() {
      super.cleanup();
   }

   /**
    * Return the name of the containing component so we can use that to process load
    * table data commands
    */
   getAssemblyName(): string {
      return this.assemblyName;
   }

   // noinspection JSUnusedGlobalSymbols
   processLoadPreviewTableCommand(command: LoadPreviewTableCommand): void {
      if(command.tableData == null) {
         return;
      }

      this.tableData = command.tableData;
      this.worksheetId = command.worksheetId;
      this.sortInfo = command.sortInfo;
      this.colWidths = command.colWidths;
      this.tableStylePaneModel = command.styleModel;
      let popup = this.context.viewer || this.context.preview;

      this.tableData.forEach(cells => {
         cells.filter(cell => !!cell && cell.protoIdx > -1)
            .forEach((cell) => {
               const prototype = command.prototypeCache[cell.protoIdx];
               delete cell.protoIdx;

               if(prototype != null) {
                  Object.assign(cell, prototype);
               }
               else {
                  throw new Error("Cell prototype expected but not found");
               }
            });
      });

      if(!this.slideOut || (!popup && (!this.slideOut.isOnTop || !this.slideOut.isOnTop()))) {
         this.openModal().then(() => {}, () => {});
      }
      else if(!popup && !this.slideOut.isExpanded()) {
         this.slideOut.setExpanded(true);
      }
   }

   /**
    * Exports the table
    */
   exportTable(): void {
      const fileName = this.getAssemblyName() + "_Data_ExportedData";
      const uri = this.baseHrefService.getBaseHref() +
         "/../export/worksheet/" + Tool.encodeURIPath(this.worksheetId) +
         "/Data?fileName=" + fileName + "&viewsheetId=" + this.viewsheetClient.runtimeId;
      this.downloadService.download(uri);
   }

   async openModal(): Promise<any> {
      try {
         const options: SlideOutOptions = {
            title: ComponentTool.getAssemblySlideOutTitle(this.getAssemblyName(), "_#(js:Data)"),
            size: "lg",
            objectId: this.getAssemblyName() + "_showDetails",
            windowClass: "preview-table-dialog",
            popup: this.context.viewer || this.context.preview
         };
         this.slideOut = this.modalService.open(this.modalContent, options);
         await this.slideOut.result;
      }
      catch(e) {
         // Ignore any exceptions coming from ng-bootstrap modal since the promise rejects
         // on dismiss which happens when clicking outside the modal or pressing 'esc'
      }
      finally {
         this.slideOut = null;
         this.onPreviewClose.emit();
      }
   }

   showStyle() {
      this.ngbModalService.open(this.styleDialog, {backdrop: false, keyboard: false, size: "lg"}).result.then(
         (result: string) => {
            this.onChange.emit({sortInfo: null, format: null, column: [], str: this.worksheetId,
               detailStyle: result + "", dndInfo: null});
         },
         (reason: string) => {
         }
      );
   }
}
