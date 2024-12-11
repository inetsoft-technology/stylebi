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
import {
   ChangeDetectorRef,
   Component,
   EventEmitter,
   Input,
   OnDestroy,
   OnInit,
   Output
} from "@angular/core";
import { Subject } from "rxjs";
import { takeUntil } from "rxjs/operators";
import { CustomThemeModel } from "../custom-theme-model";

@Component({
   selector: "em-theme-list-view",
   templateUrl: "./theme-list-view.component.html",
   styleUrls: ["./theme-list-view.component.scss"]
})
export class ThemeListViewComponent implements OnInit, OnDestroy {
   @Input() get themes(): CustomThemeModel[] {
      return this._themes;
   }

   set themes(value: CustomThemeModel[]) {
      this._themes = value;
      this.defaultTheme = this.themes.find(theme => theme.defaultThemeOrg);

      if(this.defaultTheme == null) {
         this.defaultTheme = this.themes.find(theme => theme.defaultThemeGlobal);
      }
   }

   @Input() selectedTheme: CustomThemeModel;
   @Input() isSiteAdmin = false;
   @Input() orgId: string;
   @Output() themeSelected = new EventEmitter<string>();
   @Output() themeDeleted = new EventEmitter<string>();
   @Output() themeCreated = new EventEmitter<string>();
   @Output() themeDownloaded = new EventEmitter<string>();

   _themes: CustomThemeModel[];
   defaultTheme: CustomThemeModel;
   collapseToolbar = false;
   private destroy$ = new Subject<void>();

   constructor(private breakpointObserver: BreakpointObserver,
               private changeDetector: ChangeDetectorRef)
   {
   }

   ngOnInit(): void {
      this.breakpointObserver
         .observe("(min-width: 0) and (max-width: 1025px)")
         .pipe(takeUntil(this.destroy$))
         .subscribe((state: BreakpointState) => {
            this.collapseToolbar = state.matches;
            this.changeDetector.markForCheck();
         });
   }

   ngOnDestroy(): void {
      this.destroy$.next();
      this.destroy$.unsubscribe();
   }

   createTheme(): void {
      this.themeCreated.emit();
   }

   deleteTheme(): void {
      this.themeDeleted.emit(this.selectedTheme?.id);
   }

   downloadTheme(): void {
      this.themeDownloaded.emit(this.selectedTheme?.id);
   }

   onThemeSelected(id: string): void {
      this.themeSelected.emit(id);
   }

   cannotDelete(): boolean {
      return !this.selectedTheme?.id || this.selectedTheme.global && !this.isSiteAdmin;
   }

   isDefaultTheme(theme: CustomThemeModel): boolean {
      return this.defaultTheme != null && this.defaultTheme.id == theme.id;
   }
}
