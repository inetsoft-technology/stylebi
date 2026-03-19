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
   AfterViewInit,
   Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, ViewChild
} from "@angular/core";
import { SelectionModel } from "@angular/cdk/collections";
import { Tool } from "../../../../../../shared/util/tool";
import { IdentityModel } from "./identity-model";
import { MatDialog } from "@angular/material/dialog";
import { MatTableDataSource } from "@angular/material/table";
import { IdentityType } from "../../../../../../shared/data/identity-type";
import { SecurityTreeDialogComponent } from "../security-tree-dialog/security-tree-dialog.component";
import { SecurityTreeDialogData } from "../security-tree-dialog/security-tree-dialog-data";
import { MatPaginator } from "@angular/material/paginator";
import { MatSnackBar } from "@angular/material/snack-bar";
import { take } from "rxjs/operators";
import { IdentityClipboardService, IdentityCopyPasteContext } from "./identity-clipboard.service";
import { MessageDialog, MessageDialogType } from "../../../common/util/message-dialog";

@Component({
   selector: "em-security-table-view",
   templateUrl: "./security-table-view.component.html",
   styleUrls: ["./security-table-view.component.scss"]
})
export class SecurityTableViewComponent implements OnChanges, AfterViewInit {
   @Input() name: string;
   @Input() globalRole: boolean = false;
   @Input() type: number;
   @Input() label: string;
   @Input() dialogData: SecurityTreeDialogData;
   @Input() dataSource: IdentityModel[] = [];
   @Input() editable: boolean = true;
   @Input() showCopyPaste = false;
   @Input() copyPasteContext: IdentityCopyPasteContext | null = null;
   @Input() pasteContexts: IdentityCopyPasteContext | IdentityCopyPasteContext[] | null = null;
   @Input() pasteTypeFilter: IdentityType[] | null = null;
   @Input() pasteExcludeIdentities: IdentityModel[] | null = null;
   @Output() addIdentities = new EventEmitter<IdentityModel[]>();
   @Output() removeSelection = new EventEmitter<IdentityModel[]>();
   @Output() dropOnTable = new EventEmitter<IdentityModel>();
   @Output() pasteReplaceIdentities = new EventEmitter<IdentityModel[]>();
   @ViewChild(MatPaginator, { static: true }) paginator: MatPaginator;
   matTableDataSource: MatTableDataSource<IdentityModel>;
   displayColumns: string[] = ["selected", "type", "name"];
   selection = new SelectionModel<IdentityModel>(true, []);

   constructor(private dialog: MatDialog,
               private clipboardService: IdentityClipboardService,
               private snackBar: MatSnackBar) { }

   ngOnChanges(changes: SimpleChanges) {
      if(changes.dataSource) {
         if(this.dataSource != null) {
            this.matTableDataSource = new MatTableDataSource(this.dataSource);
            this.matTableDataSource.paginator = this.paginator;
            this.selection.clear();
         }

         if(this.editable) {
            this.displayColumns = ["selected", "type", "name"];
         }
         else {
            this.displayColumns = ["type", "name"];
         }
      }
   }

   ngAfterViewInit() {
      if(this.matTableDataSource) {
         this.matTableDataSource.paginator = this.paginator;
      }
   }

   isAddButtonVisible(): boolean {
      return !(this.organization && this.label == "_#(js:Members)");
   }

   get organization(): boolean {
      return this.type === IdentityType.ORGANIZATION;
   }

   onDrop(event: DragEvent): void {
      event.preventDefault();

      if(!this.editable) {
         return;
      }

      const dragNodes: IdentityModel[] = JSON.parse(event.dataTransfer.getData("text"));
      dragNodes.filter((model) => !this.dataSource.some((data) => data.identityID === model.identityID &&
         data.type === model.type))
         .forEach((model) => this.dropOnTable.emit(model));
   }

   openAddDialog() {
      this.dialog.open(SecurityTreeDialogComponent, {
         role: "dialog",
         width: "500px",
         maxWidth: "100%",
         height: "75vh",
         maxHeight: "100%",
         data: this.dialogData,
      })
         .afterClosed().subscribe(result => {
         if(result) {
            this.addIdentities.emit(result
               .map(node => <IdentityModel>{identityID: node.identityID,
                  identityIDLabel: node.organization, type: node.type})
               .filter((model) => !this.dataSource.some(
                  (data) => data.identityID === model.name && data.type === model.type)
            ));
         }
      });
   }

   getIcon(type: number): string {
      let icon: string;

      switch(type) {
      case IdentityType.USER:
         icon = "account-icon";
         break;
      case IdentityType.GROUP:
         icon = "user-group-icon";
         break;
      case IdentityType.ORGANIZATION:
         icon = "user-organizations-icon";
         break;
      default:
         icon = "user-roles-icon";
      }

      return icon;
   }

   isAllSelected(): boolean {
      return this.selection.selected.length === this.matTableDataSource.data.length;
   }

   toggleRow(row) {
      this.selection.toggle(row);
   }

   /** Selects all rows if they are not all selected; otherwise clear selection. */
   masterToggle() {
      this.isAllSelected() ?
         this.selection.clear() :
         this.matTableDataSource.data.forEach(row => this.selection.select(row));
   }

   private get effectivePasteContext(): IdentityCopyPasteContext | IdentityCopyPasteContext[] | null {
      return this.pasteContexts ?? this.copyPasteContext;
   }

   get canPaste(): boolean {
      return this.clipboardService.canPaste(this.effectivePasteContext);
   }

   get pasteCount(): number {
      return this.clipboardService.copiedCount(this.effectivePasteContext, this.pasteTypeFilter, this.pasteExcludeIdentities);
   }

   get pasteTotal(): number {
      return this.clipboardService.copiedTotal(this.effectivePasteContext);
   }

   get pasteTooltip(): string {
      if(!this.canPaste) {
         return this.clipboardService.hasContent()
            ? "_#(js:em.security.clipboard.cannotPasteHere)"
            : "_#(js:em.security.clipboard.empty)";
      }

      if(this.pasteCount === 0) {
         return "_#(js:em.security.clipboard.noMatchingIdentities)";
      }

      return "";
   }

   get pasteBadgeLabel(): string {
      const count = this.pasteCount;

      if(count === 0) {
         return "";
      }

      const total = this.pasteTotal;
      return ` (${count}${total > count ? ` of ${total}` : ""})`;
   }

   copyIdentities(): void {
      if(!this.copyPasteContext) {
         return;
      }

      const selected = this.selection.selected;
      const toCopy = selected.length > 0 ? selected : this.dataSource;

      if(!toCopy.length) {
         return;
      }

      this.clipboardService.copy(toCopy, this.copyPasteContext);
      this.snackBar.open("_#(js:em.security.identitiesCopied)", null, { duration: Tool.SNACKBAR_DURATION });
   }

   pasteIdentities(): void {
      const pasted = this.clipboardService.paste(this.effectivePasteContext, this.pasteTypeFilter, this.pasteExcludeIdentities);

      if(!pasted || !pasted.length) {
         return;
      }

      this.dialog.open(MessageDialog, {
         width: "350px",
         data: {
            title: "_#(js:Paste)",
            content: "_#(js:em.security.pasteIdentities.confirm)",
            type: MessageDialogType.CONFIRMATION
         }
      }).afterClosed().pipe(take(1)).subscribe(confirmed => {
         if(confirmed) {
            this.pasteReplaceIdentities.emit(pasted);
         }
      });
   }
}
