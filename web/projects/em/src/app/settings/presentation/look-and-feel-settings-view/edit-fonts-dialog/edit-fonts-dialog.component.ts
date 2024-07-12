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
import { AfterViewInit, Component, HostListener, Inject, OnInit, ViewChild } from "@angular/core";
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from "@angular/material/dialog";
import { MatSort } from "@angular/material/sort";
import { MatTableDataSource } from "@angular/material/table";
import { AddFontFaceDialogComponent } from "../add-font-dialog/add-font-face-dialog.component";
import { UserFontModel } from "../user-font-model";
import { animate, state, style, transition, trigger } from "@angular/animations";
import { FontFaceModel } from "../font-face-model";
import { AddFontFaceDialogData } from "../add-font-face-dialog-data";

interface FontFamilyElement {
   select: boolean;
   font: string;
   fontFaces: FontFaceModel[];
}

@Component({
   selector: "em-add-user-font-dialog",
   templateUrl: "./edit-fonts-dialog.component.html",
   styleUrls: ["./edit-fonts-dialog.component.scss"],
   animations: [
      trigger("detailExpand", [
         state("collapsed", style({height: "0px", minHeight: "0"})),
         state("expanded", style({height: "*"})),
         transition("expanded <=> collapsed", animate("225ms cubic-bezier(0.4, 0.0, 0.2, 1)")),
      ]),
   ],
})
export class EditFontsDialogComponent implements OnInit, AfterViewInit {
   @ViewChild(MatSort, { static: true }) sort: MatSort;

   title: string;
   userFonts: string[];
   fontFaces: FontFaceModel[];
   displayedColumns = ["select", "font", "font face count"];
   readonly fontFaceColumns = ["font faces"];
   selectedValues: string[] = [];
   deleteFontFaces: FontFaceModel[] = [];
   newFontFaces: UserFontModel[] = [];
   matTableDataSource: MatTableDataSource<FontFamilyElement>;
   expandedElement: FontFamilyElement | null = null;

   constructor(private dialogRef: MatDialogRef<EditFontsDialogComponent>,
               @Inject(MAT_DIALOG_DATA) public data: any, private dialog: MatDialog)
   {
      this.userFonts = data.userFonts.slice();
      this.fontFaces = data.fontFaces.slice();
      this.deleteFontFaces = data.deleteFontFaces.slice();
      this.newFontFaces = data.newFontFaces.slice();
      this.initDataSource();
   }

   ngAfterViewInit() {
      if(this.matTableDataSource && !this.matTableDataSource.sort) {
         this.matTableDataSource.sort = this.sort;
      }
   }

   ngOnInit() {
      this.title = "_#(js:User Fonts)";
   }

   toggleHeader(event: any) {
      if(event.checked) {
         this.matTableDataSource.data.forEach((element) => {
            element.select = true;
            this.selectedValues.push(element.font);
         });

         return;
      }

      this.initDataSource();
      this.selectedValues = [];
   }

   selectToggle(select: any, element: any): void {
      if(select.checked) {
         element.select = true;
         this.selectedValues.push(element.font);
      }
      else {
         element.select = false;
         this.selectedValues = this.selectedValues.filter(v => v != element.font);
      }
   }

   isAllSelected(): boolean {
      return this.selectedValues.length == this.userFonts.length;
   }

   add(): void {
      const ref = this.dialog.open<AddFontFaceDialogComponent, AddFontFaceDialogData, UserFontModel>(
         AddFontFaceDialogComponent, {
            role: "dialog",
            disableClose: false,
            width: "400px",
            maxWidth: "70vw",
            maxHeight: "75vh",
            data: {
               existingFontNames: [...this.userFonts],
               existingFontFaces: []
            }
         });

      ref.afterClosed().subscribe(result => {
         if(!!result) {
            this.userFonts.push(result.name);
            this.newFontFaces.push(result);

            this.fontFaces.push({
               fontName: result.name,
               identifier: result.identifier,
               fontWeight: result.fontWeight,
               fontStyle: result.fontStyle
            });
         }

         this.initDataSource();

         if(!!result) {
            this.expandedElement = this.matTableDataSource.data.find(el => el.font === result.name);
         }
      });
   }

   delete(): void {
      this.selectedValues.forEach(value => this.deleteFont(value));
      this.initDataSource();
      this.selectedValues = [];
   }

   ok(): void {
      this.dialogRef.close({
         userFonts: this.userFonts,
         fontFaces: this.fontFaces,
         deleteFontFaces: this.deleteFontFaces,
         newFontFaces: this.newFontFaces
      });
   }

   @HostListener("window:keyup.esc", [])
   onKeyUp() {
      this.dialogRef.close(null);
   }

   clickRow(row: FontFamilyElement): void {
      this.expandedElement = this.expandedElement === row ? null : row;
   }

   addFontFace(font: FontFamilyElement): void {
      const ref = this.dialog.open<AddFontFaceDialogComponent, AddFontFaceDialogData, UserFontModel>(
         AddFontFaceDialogComponent, {
            role: "dialog",
            disableClose: false,
            width: "400px",
            maxWidth: "70vw",
            maxHeight: "75vh",
            data: {
               existingFontNames: [...this.userFonts],
               existingFontFaces: font.fontFaces,
               fontName: font.font
            }
         });

      ref.afterClosed().subscribe(result => {
         if(!!result) {
            this.newFontFaces.push(result);

            this.fontFaces.push({
               fontName: result.name,
               identifier: result.identifier,
               fontWeight: result.fontWeight,
               fontStyle: result.fontStyle
            });
         }

         this.initDataSource();
      });
   }

   removeFontFace(font: FontFamilyElement, index: number): void {
      if(font.fontFaces.length === 1) {
         this.deleteFont(font.font);
      }
      else {
         const fontFace = font.fontFaces[index];
         const ffIndex = this.fontFaces.findIndex(f => f.fontName === fontFace.fontName &&
            f.identifier === fontFace.identifier);
         this.fontFaces.splice(ffIndex, 1);
      }

      this.initDataSource();
   }

   editFontFace(font: FontFamilyElement, index: number): void {
      const fontFace = font.fontFaces[index];

      const ref = this.dialog.open<AddFontFaceDialogComponent, AddFontFaceDialogData, UserFontModel>(
         AddFontFaceDialogComponent, {
            role: "dialog",
            disableClose: false,
            width: "400px",
            maxWidth: "70vw",
            maxHeight: "75vh",
            data: {
               existingFontNames: [...this.userFonts],
               existingFontFaces: font.fontFaces,
               fontName: font.font,
               identifier: fontFace.identifier,
               fontWeight: fontFace.fontWeight,
               fontStyle: fontFace.fontStyle
            }
         });

      ref.afterClosed().subscribe(result => {
         if(!!result) {
            const editFontFace: FontFaceModel = {
               fontName: result.name,
               identifier: result.identifier,
               fontWeight: result.fontWeight,
               fontStyle: result.fontStyle
            };

            const idx = this.fontFaces
               .findIndex(f => f.fontName === fontFace.fontName && f.identifier === fontFace.identifier);

            if(idx >= 0) {
               this.fontFaces[idx] = editFontFace;
            }
         }

         this.initDataSource();
      });
   }

   private initDataSource(): void {
      const dataSource = this.userFonts.map((font, i) => (
         {select: false, font, fontFaces: this.fontFaces.filter(f => f.fontName === font)}
      ));

      this.matTableDataSource = new MatTableDataSource(dataSource);
      this.matTableDataSource.sort = this.sort;

      if(this.expandedElement != null) {
         this.expandedElement = this.matTableDataSource.data.find(el => el.font === this.expandedElement.font);
      }
   }

   private deleteFont(name: string): void {
      this.userFonts = this.userFonts.filter(value => value !== name);

      if(this.newFontFaces.some(f => f.name === name)) {
         this.newFontFaces = this.newFontFaces.filter(f => f.name !== name);
      }

      const fontFacesToRemove = this.fontFaces.filter(f => f.fontName === name);

      for(let fontFace of fontFacesToRemove) {
         if(!this.deleteFontFaces.some(f => f.identifier === fontFace.identifier)) {
            this.deleteFontFaces.push({
               fontName: name,
               identifier: fontFace.identifier
            });
         }
      }

      this.fontFaces = this.fontFaces.filter(f => f.fontName !== name);
   }

   getFontWeightString(fontFace: FontFaceModel): string {
      return !!fontFace.fontWeight ? fontFace.fontWeight : "<default>";
   }

   getFontStyleString(fontFace: FontFaceModel): string {
      return !!fontFace.fontStyle ? fontFace.fontStyle : "<default>";
   }

   getFontFaceIdentifier(fontFace: FontFaceModel): string {
      return fontFace.identifier ? fontFace.identifier : "<legacy font face>";
   }
}
