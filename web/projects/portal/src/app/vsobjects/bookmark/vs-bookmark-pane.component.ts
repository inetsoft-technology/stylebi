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
import { Component, EventEmitter, Input, OnInit, Output, TemplateRef, ViewChild } from "@angular/core";
import { convertToKey } from "../../../../../em/src/app/settings/security/users/identity-id";
import { GuiTool } from "../../common/util/gui-tool";
import { VSBookmarkInfoModel } from "../model/vs-bookmark-info-model";
import { FixedDropdownDirective } from "../../widget/fixed-dropdown/fixed-dropdown.directive";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import {FeatureFlagValue} from "../../../../../shared/feature-flags/feature-flags.service";
import { RemoveAnnotationsCondition } from "../model/remove-annotations-condition";
import { NgbModalOptions } from "@ng-bootstrap/ng-bootstrap/modal/modal-config";

enum BookmarkFilter {
   ALL,
   SHARE_BY_OTHERS,
   OWNED_BY_ME
}

@Component({
   selector: "vs-bookmark-pane",
   templateUrl: "./vs-bookmark-pane.component.html",
   styleUrls: ["./vs-bookmark-pane.component.scss"]
})
export class VsBookmarkPaneComponent implements OnInit {
   @Input() preview: boolean;
   @Input() vsBookmarkList: VSBookmarkInfoModel[] = [];
   @Input() securityEnabled: boolean;
   @Input() addBookmarkDisabled;
   @Input() saveCurrentBookmarkDisabled;
   @Input() setDefaultBookmarkDisabled;
   @Input() principal: string;
   @Output() onSetDefaultBookmark: EventEmitter<VSBookmarkInfoModel> = new EventEmitter<VSBookmarkInfoModel>();
   @Output() onEditBookmark: EventEmitter<VSBookmarkInfoModel> = new EventEmitter<VSBookmarkInfoModel>();
   @Output() onDeleteBookmark: EventEmitter<VSBookmarkInfoModel> = new EventEmitter<VSBookmarkInfoModel>();
   @Output() onDeleteBookmarks = new EventEmitter<any>();
   @Output() onGoToBookmark: EventEmitter<VSBookmarkInfoModel> = new EventEmitter<VSBookmarkInfoModel>();
   @Output() onAddBookmark: EventEmitter<any> = new EventEmitter<any>();
   @Output() onSaveBookmark: EventEmitter<any> = new EventEmitter<any>();
   @ViewChild(FixedDropdownDirective) dropdown: FixedDropdownDirective;
   mobileDevice: boolean = GuiTool.isMobileDevice();
   searchString: string;
   filterByValue: BookmarkFilter = BookmarkFilter.ALL;
   BookmarkFilter = BookmarkFilter;
   FeatureFlagValue = FeatureFlagValue;

   constructor(private modalService: NgbModal,) {
   }

   ngOnInit(): void {
   }

   isSetDefaultBookmarkVisible(bookmark: VSBookmarkInfoModel): boolean {
      return !bookmark.defaultBookmark;
   }

   isEditBookmarkDisabled(bookmark: VSBookmarkInfoModel): boolean {
      return convertToKey(bookmark.owner) !== this.principal;
   }

   isEditBookmarkVisible(bookmark: VSBookmarkInfoModel): boolean {
      return !this.isBookmarkHome(bookmark.name);
   }

   isBookmarkHome(name: string): boolean {
      return name == VSBookmarkInfoModel.HOME;
   }

   setDefaultBookmark(bookmark: VSBookmarkInfoModel): void {
      this.onSetDefaultBookmark.emit(bookmark);
   }

   editBookmark(bookmark: VSBookmarkInfoModel): void {
      this.onEditBookmark.emit(bookmark);
   }

   deleteBookmark(bookmark: VSBookmarkInfoModel): void {
      this.onDeleteBookmark.emit(bookmark);
   }

   addBookmark(): void {
      this.onAddBookmark.emit();
   }

   saveBookmark(): void {
      this.onSaveBookmark.emit();
   }

   gotoBookmark(bookmark: VSBookmarkInfoModel) {
      this.onGoToBookmark.emit(bookmark);
   }

   bookmarkVisible(bookmark: VSBookmarkInfoModel): boolean {
      if(this.filterByValue == BookmarkFilter.OWNED_BY_ME && convertToKey(bookmark.owner) != this.principal &&
         !this.isBookmarkHome(bookmark?.name))
      {
         return false;
      }

      if(this.filterByValue == BookmarkFilter.SHARE_BY_OTHERS && convertToKey(bookmark.owner) == this.principal &&
         !this.isBookmarkHome(bookmark?.name))
      {
         return false;
      }

      if(!!!this.searchString || !!!this.searchString.trim()) {
         return true;
      }

      return bookmark.label?.toLowerCase().includes(this.searchString.trim().toLowerCase());
   }

   resetSearchMode() {
      this.searchString = null;
   }

   closeFilterDropDown() {
      if(this.dropdown) {
         this.dropdown.close();
      }
   }
}
