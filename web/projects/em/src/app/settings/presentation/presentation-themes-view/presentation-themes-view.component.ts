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
import { BreakpointObserver } from "@angular/cdk/layout";
import { HttpClient } from "@angular/common/http";
import { Component, OnInit } from "@angular/core";
import { MatDialog } from "@angular/material/dialog";
import { Observable, of } from "rxjs";
import { map } from "rxjs/operators";
import { DownloadService } from "../../../../../../shared/download/download.service";
import { Tool } from "../../../../../../shared/util/tool";
import { MessageDialog, MessageDialogType } from "../../../common/util/message-dialog";
import { ContextHelp } from "../../../context-help";
import { PageHeaderService } from "../../../page-header/page-header.service";
import { Searchable } from "../../../searchable";
import { Secured } from "../../../secured";
import { AddThemeDialogComponent } from "./add-theme-dialog/add-theme-dialog.component";
import { CustomThemeModel } from "./custom-theme-model";

const SMALL_WIDTH_BREAKPOINT = 720;

interface CustomThemeList {
   themes: CustomThemeModel[];
}

@Secured({
   route: "/settings/presentation/themes",
   label: "Themes"
})
@Searchable({
   route: "/settings/presentation/themes",
   title: "Themes",
   keywords: ["Theme"]
})
@ContextHelp({
   route: "/settings/presentation/themes",
   link: "EMThemes"
})
@Component({
   selector: "em-presentation-themes-view",
   templateUrl: "./presentation-themes-view.component.html",
   styleUrls: ["./presentation-themes-view.component.scss"]
})
export class PresentationThemesViewComponent implements OnInit {
   themes: CustomThemeModel[] = [];
   selectedTheme: CustomThemeModel;
   unselectedThemeNames: string[] = [];
   //For small device use only
   editing = false;
   isSiteAdmin = false;

   get smallScreen(): boolean {
      return this.breakpointObserver.isMatched(`(max-width: ${SMALL_WIDTH_BREAKPOINT}px)`);
   }

   get themeModified(): boolean {
      if(!!this.selectedTheme) {
         const original = this.themes.find(t => t.id === this.selectedTheme.id);

         if(!!original) {
            return !Tool.isEquals(this.selectedTheme, original);
         }
      }

      return false;
   }

   constructor(private pageTitle: PageHeaderService, private breakpointObserver: BreakpointObserver,
               private http: HttpClient, private dialog: MatDialog,
               private downloadService: DownloadService)
   {
   }

   ngOnInit(): void {
      this.pageTitle.title = "_#(js:Themes)";
      this.http.get<CustomThemeList>("../api/em/settings/presentation/themes")
         .subscribe(list => {
            this.setThemes(list.themes || []);
            this.unselectedThemeNames = this.themes.map(t => t.name);
         });

      this.http.get<boolean>("../api/em/navbar/isSiteAdmin")
         .subscribe(isSiteAdmin => this.isSiteAdmin = isSiteAdmin);
   }

   onThemeSelected(id: string) {
      if(!!id) {
         if(id !== this.selectedTheme?.id) {
            this.confirmChange().subscribe(confirmed => {
               if(confirmed) {
                  const index = this.themes.findIndex(t => t.id === id);

                  if(index < 0) {
                     this.clearSelection();
                  }
                  else {
                     const uri = `../api/em/settings/presentation/themes/${Tool.byteEncode(id)}`;
                     this.http.get<CustomThemeModel>(uri).subscribe(theme => {
                        if(!!theme) {
                           const newThemes = this.themes.slice();
                           newThemes[index] = theme;
                           this.setThemes(newThemes);
                           this.setSelection(theme);
                        }
                        else {
                           this.clearSelection();
                        }
                     });
                  }
               }
            });
         }
      }
      else {
         this.clearSelection();
      }
   }

   deleteTheme(id: string) {
      const ref = this.dialog.open(MessageDialog, {
         width: "500px",
         data: {
            title: "_#(js:Confirm)",
            content: "_#(js:em.common.items.deleteSelectedItems)",
            type: MessageDialogType.CONFIRMATION
         }
      });

      ref.afterClosed().subscribe(result => {
         if(result) {
            const uri = `../api/em/settings/presentation/themes/${Tool.byteEncode(id)}`;
            this.http.delete(uri).subscribe(() => {
               this.themes = this.themes.filter(t => t.id !== id);
               this.clearSelection();
            });
         }
      });
   }

   downloadTheme(id: string) {
      const uri = `../em/settings/presentation/themes/download/${Tool.byteEncode(id)}`;
      this.downloadService.download(uri);
   }

   createTheme() {
      this.confirmChange().subscribe(confirmed => {
         if(!confirmed) {
            return;
         }

         const ids = this.themes.map(t => t.id);
         const names = this.themes.map(t => t.name);
         const ref = this.dialog.open(AddThemeDialogComponent, {
            role: "dialog",
            disableClose: false,
            width: "400px",
            maxWidth: "70vw",
            maxHeight: "75vh",
            data: { ids, names }
         });

         ref.afterClosed().subscribe(result => {
            result.global = this.isSiteAdmin;

            if(!!result) {
               this.http.post<CustomThemeModel>("../api/em/settings/presentation/themes", result).subscribe(model => {
                  const newThemes = this.themes.slice();
                  newThemes.push(model);
                  this.setThemes(newThemes);
                  this.onThemeSelected(model.id);
               });
            }
         });
      });
   }

   saveTheme(current: CustomThemeModel): void {
      if(!!current) {
         const uri = `../api/em/settings/presentation/themes/${Tool.byteEncode(current.id)}`;
         this.http.put(uri, current).subscribe(() => {
            const newThemes = this.themes.slice();
            const index = newThemes.findIndex(t => t.id === current.id);

            if(index < 0) {
               this.clearSelection();
            }
            else {
               newThemes[index] = Tool.clone(current);

               // if current theme is the new default theme then reset the defaultTheme property of
               // other themes
               if(current.defaultTheme) {
                  for(let i = 0; i < newThemes.length; i++) {
                     if(i != index) {
                        newThemes[i].defaultTheme = false;
                     }
                  }
               }

               this.setThemes(newThemes);
               this.setSelection(current);
            }
         });
      }
   }

   resetTheme(): void {
      if(!!this.selectedTheme) {
         const original = this.themes.find(t => t.id === this.selectedTheme.id);

         if(original) {
            this.selectedTheme = Tool.clone(original);
         }
         else {
            this.clearSelection();
         }
      }
   }

   cancel() {
      this.editing = false;
   }

   onThemeChanged(current: CustomThemeModel): void {
      // if jar has changed then get the css variables from the jar
      if(!!this.selectedTheme && !!current &&
         this.selectedTheme?.jar?.name != current?.jar?.name)
      {
         this.http.post<CustomThemeModel>("../api/em/settings/presentation/themes/css", current)
            .subscribe(model => {
               this.selectedTheme = current;
               this.selectedTheme.portalCss = model.portalCss;
               this.selectedTheme.emCss = model.emCss;
            });
      }
      else {
         if(this.selectedTheme && current) {
            Object.assign(this.selectedTheme, current);
         }
         else {
            this.selectedTheme = current;
         }
      }
   }

   private confirmChange(): Observable<boolean> {
      if(this.themeModified) {
         const ref = this.dialog.open(MessageDialog, {
            width: "500px",
            data: {
               title: "_#(js:Confirm)",
               content: "_#(js:em.presentation.theme.confirm)",
               type: MessageDialogType.CONFIRMATION
            }
         });

         return ref.afterClosed().pipe(map(result => !!result));
      }

      return of(true);
   }

   private clearSelection(): void {
      this.selectedTheme = null;
      this.unselectedThemeNames = this.themes.map(t => t.name);
      this.editing = false;
   }

   private setSelection(theme: CustomThemeModel): void {
      this.selectedTheme = Tool.clone(theme);
      this.unselectedThemeNames = this.themes.filter(t => t.id !== theme.id).map(t => t.name);
      this.editing = true;
   }

   private setThemes(newThemes: CustomThemeModel[]): void {
      this.themes = newThemes.sort((a, b) => {
         return a.name.localeCompare(b.name);
      });
   }
}
