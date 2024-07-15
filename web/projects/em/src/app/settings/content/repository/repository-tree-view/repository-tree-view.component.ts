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
import { BreakpointObserver, BreakpointState } from "@angular/cdk/layout";
import { HttpClient } from "@angular/common/http";
import {
   ChangeDetectionStrategy,
   ChangeDetectorRef,
   Component,
   EventEmitter,
   Input,
   OnChanges,
   OnDestroy,
   OnInit,
   Output,
   SimpleChanges
} from "@angular/core";
import { UntypedFormBuilder, UntypedFormGroup } from "@angular/forms";
import { BehaviorSubject, Subject } from "rxjs";
import { debounceTime, distinctUntilChanged, map, takeUntil } from "rxjs/operators";
import { RepositoryEntryType } from "../../../../../../../shared/data/repository-entry-type.enum";
import { FlatTreeNode, FlatTreeNodeMenuItem } from "../../../../common/util/tree/flat-tree-model";
import { FlatTreeSelectNodeEvent } from "../../../../common/util/tree/flat-tree-view.component";
import { RepositoryTreeDataSource } from "../repository-tree-data-source";
import { RepositoryFlatNode } from "../repository-tree-node";
import { LicensedComponents } from "./licensed-components";

@Component({
   selector: "em-repository-tree-view",
   templateUrl: "./repository-tree-view.component.html",
   styleUrls: ["./repository-tree-view.component.scss"],
   changeDetection: ChangeDetectionStrategy.OnPush
})
export class RepositoryTreeViewComponent implements OnInit, OnDestroy, OnChanges {
   @Input() dataSource: RepositoryTreeDataSource;
   @Input() selectedNodes: RepositoryFlatNode[];
   @Input() smallDevice: boolean;
   @Input() wsMVEnabled: boolean;
   @Output() nodeSelected = new EventEmitter<FlatTreeSelectNodeEvent>();
   @Output() filter = new EventEmitter<number>();
   @Output() newDashboard = new EventEmitter<void>();
   @Output() newFolder = new EventEmitter<void>();
   @Output() deleteNodes = new EventEmitter<void>();
   @Output() importAsset = new EventEmitter<void>();
   @Output() exportAsset = new EventEmitter<void>();
   @Output() moveAsset = new EventEmitter<void>();
   @Output() onDrop = new EventEmitter<FlatTreeNode>();
   @Output() dblClicked = new EventEmitter<FlatTreeNode>();
   @Output() editNode = new EventEmitter<void>();
   @Output() openMaterializeDialog = new EventEmitter<void>();

   newDashboardDisabled = true;
   newFolderDisabled = true;
   deleteDisabled = true;
   moveDisabled = true;
   exportDisabled = true;
   searchOpen = false;
   collapseToolbar = false;
   filterForm: UntypedFormGroup;
   licensedComponents: LicensedComponents;
   isSiteAdmin = false;

   get searchQuery(): string {
      return this.searchQuery$.value;
   }

   set searchQuery(value: string) {
      this.searchQuery$.next(value ? value.replace(/^\s+/, "").replace(/\s+$/, "") : "");
   }

   private searchQuery$ = new BehaviorSubject<string>("");
   private destroy$ = new Subject<void>();

   constructor(private breakpointObserver: BreakpointObserver,
      private changeDetector: ChangeDetectorRef, private http: HttpClient, fb: UntypedFormBuilder) {
      this.filterForm = fb.group({
         user: [true],
         viewsheet: [true],
         worksheet: [true],
      });
   }

   ngOnInit(): void {
      this.breakpointObserver
         .observe("(min-width: 0) and (max-width: 1025px)")
         .pipe(takeUntil(this.destroy$))
         .subscribe((state: BreakpointState) => {
            this.collapseToolbar = state.matches;
            this.changeDetector.markForCheck();
         });

      this.filterForm.valueChanges
         .pipe(map(value => {
            return (value.user && RepositoryEntryType.USER_FOLDERS) |
               (value.viewsheet && RepositoryEntryType.VIEWSHEETS) |
               (value.worksheet && RepositoryEntryType.WORKSHEETS);
         }))
         .subscribe(value => this.filter.emit(value));

      this.searchQuery$
         .pipe(
            takeUntil(this.destroy$),
            distinctUntilChanged(),
            debounceTime(1000)
         )
         .subscribe(value => this.dataSource.search(value));

      this.http.get<LicensedComponents>("../api/em/content/repository/tree/licensed")
         .subscribe(components => {
            this.licensedComponents = components;
            this.dataSource.licensedComponents = components;
         });

      this.http.get<boolean>("../api/em/content/is-site-admin")
         .subscribe(res => {
            this.isSiteAdmin = res;
            this.dataSource.isSiteAdmin = this.isSiteAdmin;
         });

      this.dataSource.contextMenusEnabled = true;
      this.dataSource.smallDevice = this.smallDevice;
      this.dataSource.wsMVEnabled = this.wsMVEnabled;
   }

   ngOnDestroy(): void {
      this.destroy$.next();
      this.destroy$.unsubscribe();
   }

   ngOnChanges(changes: SimpleChanges) {
      if(changes.selectedNodes) {
         this.newDashboardDisabled = true;
         this.newFolderDisabled = true;

         if(this.selectedNodes) {
            this.deleteDisabled = this.selectedNodes.length === 0;
            this.moveDisabled = this.selectedNodes.length === 0;
            this.exportDisabled = false;

            for(let i = 0; i < this.selectedNodes.length; i++) {
               const states = this.dataSource.getActionStates(
                  this.selectedNodes[i].data, this.selectedNodes[i].level);
               this.deleteDisabled = this.deleteDisabled || states.deleteDisabled;
               this.moveDisabled = this.moveDisabled || states.moveDisabled;
               this.exportDisabled = this.exportDisabled || states.exportDisabled;
            }

            if(this.selectedNodes.length === 1) {
               const state = this.dataSource.getActionStates(
                  this.selectedNodes[0].data, this.selectedNodes[0].level);
               this.newDashboardDisabled = state.newDashboardDisabled;
               this.newFolderDisabled = state.newFolderDisabled;
            }
         }
      }
   }

   openMaterialize() {
      if(this.enableMaterialize) {
         this.openMaterializeDialog.emit();
      }
   }

   deleteNode() {
      if(!this.deleteDisabled) {
         this.deleteNodes.emit();
      }
   }

   editNodeAction() {
      if(this.selectedNodes.length == 1) {
         this.editNode.emit();
      }
   }

   onMoveAsset() {
      if(!this.moveDisabled) {
         this.moveAsset.emit();
      }
   }

   onExportAsset() {
      if(!this.exportDisabled) {
         this.exportAsset.emit();
      }
   }

   createDashboard() {
      if(!this.newDashboardDisabled) {
         this.newDashboard.emit();
      }
   }

   createFolder() {
      if(!this.newFolderDisabled) {
         this.newFolder.emit();
      }
   }


   // Toggle search input visibility
   toggleSearch() {
      this.searchOpen = !this.searchOpen;
   }

   get enableMaterialize(): boolean {
      return !!this.selectedNodes && this.selectedNodes.length > 0 &&
         this.selectedNodes.some(node => {
            if(!node?.data || node.label == "_#(js:Built-in Admin Reports)") {
               return false;
            }

            const states = this.dataSource.getActionStates(node.data, node.level);
            return !states.materializeDisabled;
         });
   }

   onContextMenu(node: FlatTreeNode<any>, menu: FlatTreeNodeMenuItem): void {
      switch(menu.name) {
         case "new-dashboard":
            this.newDashboard.emit();
            break;
         case "new-folder":
            this.newFolder.emit();
            break;
         case "delete":
            this.deleteNodes.emit();
            break;
         case "move":
            this.moveAsset.emit();
            break;
         case "edit":
            this.editNode.emit();
            break;
         case "materialize":
            this.openMaterializeDialog.emit();
            break;
         case "export":
            this.exportAsset.emit();
            break;
         default:
            console.warn("Unsupported context action: " + menu.name);
      }
   }
}
