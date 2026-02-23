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
   Output,
   OnInit,
   OnChanges,
   OnDestroy,
   SimpleChanges
} from "@angular/core";
import { Subject } from "rxjs";
import { takeUntil } from "rxjs/operators";
import { HttpClient } from "@angular/common/http";
import { MatSnackBar } from "@angular/material/snack-bar";
import { Tool } from "../../../../../../shared/util/tool";
import { ResourcePermissionModel } from "./resource-permission-model";
import { PermissionsTableComponent } from "../permissions-table/permissions-table.component";
import { ResourcePermissionTableModel } from "./resource-permission-table-model";
import { MatDialog } from "@angular/material/dialog";
import { SecurityTreeDialogComponent } from "../security-tree-dialog/security-tree-dialog.component";
import { SecurityTreeDialogData } from "../security-tree-dialog/security-tree-dialog-data";
import { PermissionClipboardService } from "./permission-clipboard.service";
import { MessageDialog, MessageDialogType } from "../../../common/util/message-dialog";
import { OrganizationDropdownService } from "../../../navbar/organization-dropdown.service";

@Component({
   selector: "em-resource-permission",
   templateUrl: "./resource-permission.component.html",
   styleUrls: ["./resource-permission.component.scss"]
})
export class ResourcePermissionComponent implements OnInit, OnChanges, OnDestroy {
   @Input() model: ResourcePermissionModel;
   @Input() showRadioButtons = true;
   @Input() showCopyPaste = false;
   @Input() copyPasteContext: string | null = null;
   @Input() ignorePadding: boolean;
   @Input() isTimeRange: boolean = false;
   @Output() permissionChanged = new EventEmitter<ResourcePermissionTableModel[]>();
   private destroy$ = new Subject<void>();
   tableSelected: boolean = false;
   isOrgAdminOnly = true;
   siteAdmin: boolean = true;

   dialogData = <SecurityTreeDialogData> {
      dialogTitle: "_#(js:Add Permission)",
      usersEnabled: true,
      groupsEnabled: true,
      rolesEnabled: true,
      organizationsEnabled: true,
      hideOrgAdminRole: true,
      isTimeRange: false
   };

   constructor(private dialog: MatDialog, private http: HttpClient,
               private snackBar: MatSnackBar,
               private clipboardService: PermissionClipboardService,
               private orgDropdownService: OrganizationDropdownService) {
   }

   get canPaste(): boolean {
      return this.clipboardService.canPaste(this.copyPasteContext);
   }

   get pasteCount(): number {
      return this.clipboardService.copiedCount(this.copyPasteContext, this.model?.displayActions);
   }

   ngOnDestroy(): void {
      this.destroy$.next();
      this.destroy$.complete();
   }

   ngOnInit() {
      this.http.get<boolean>("../api/em/navbar/isOrgAdminOnly")
         .subscribe(isOrgAdminOnly => this.isOrgAdminOnly = isOrgAdminOnly);
      this.http.get<boolean>("../api/em/navbar/isSiteAdmin").subscribe(
         (isAdmin => {this.siteAdmin = isAdmin;
         })
      );
   }

   ngOnChanges(changes: SimpleChanges) {
      if(changes.isTimeRange) {
         this.dialogData.isTimeRange = changes.isTimeRange.currentValue;
      }
   }

   addPermission(table: PermissionsTableComponent): void {
      this.dialog.open(SecurityTreeDialogComponent, {
         role: "dialog",
         width: "500px",
         maxWidth: "100%",
         height: "75vh",
         maxHeight: "100%",
         data: this.dialogData,
      })
         .afterClosed().pipe(takeUntil(this.destroy$)).subscribe(result => {
         if(result) {
            table.receiveSelection(result.map(s =>
               <ResourcePermissionTableModel> {
                  identityID: s.identityID,
                  type: s.type,
               }));
         }
      });
   }

   removePermission(table: PermissionsTableComponent): void {
      if(!this.siteAdmin) {
         let selected = table.selection.selected;
         let hasGlobalRole = selected.find(r => r.identityID != null && r.identityID.orgID == null) != null;

         if(hasGlobalRole) {
            const message = "_#(js:em.security.orgAdmin.removeGlobalRolePermissionDenied)";
            this.snackBar.open(message, null, {duration: Tool.SNACKBAR_DURATION});

            return;
         }
      }

      table.sendSelection();
   }

   onTableDataChange(tableData: ResourcePermissionTableModel[]): void {
      this.model.permissions = tableData.slice(0);
      this.model.hasOrgEdited = this.model.permissions != null;
      this.model.changed = true;
      this.permissionChanged.emit(tableData);
   }

   derivePermissionChange(setToNull: boolean): void {
      this.model.permissions = setToNull ? null : [];
      this.model.hasOrgEdited = !setToNull;
      this.model.changed = true;
      this.permissionChanged.emit(null);
   }

   onTableSelectionChange(selection: ResourcePermissionTableModel[]): void {
      this.tableSelected = selection.length > 0 && this.model.permissions.length > 0;
   }

   copyPermissions(): void {
      this.clipboardService.copy(this.model.permissions, this.model.requiresBoth,
         this.orgDropdownService.getProvider(), this.copyPasteContext);
      this.snackBar.open("_#(js:em.security.permissionsCopied)", null, {
         duration: Tool.SNACKBAR_DURATION
      });
   }

   pastePermissions(): void {
      this.dialog.open(MessageDialog, {
         width: "350px",
         data: {
            title: "_#(js:Paste Permissions)",
            content: "_#(js:em.security.pastePermissions.confirm)",
            type: MessageDialogType.CONFIRMATION
         }
      }).afterClosed().pipe(takeUntil(this.destroy$)).subscribe(confirmed => {
         if(!confirmed) {
            return;
         }

         const result = this.clipboardService.paste(this.model.displayActions, this.copyPasteContext);

         if(result) {
            this.model.permissions = result.permissions;
            this.model.requiresBoth = result.requiresBoth;
            this.model.hasOrgEdited = true;
            this.model.changed = true;
            this.permissionChanged.emit(this.model.permissions);
         }
      });
   }
}
