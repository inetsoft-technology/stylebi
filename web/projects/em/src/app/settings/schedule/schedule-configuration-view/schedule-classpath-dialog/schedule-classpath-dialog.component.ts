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
import { Component, ElementRef, Inject, OnInit, ViewChild, ViewEncapsulation } from "@angular/core";
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from "@angular/material/dialog";
import { MatSnackBar, MatSnackBarConfig } from "@angular/material/snack-bar";
import { Tool } from "../../../../../../../shared/util/tool";
import { EditClasspathTextDialogComponent } from "./edit-classpath-text-dialog/edit-classpath-text-dialog.component";

@Component({
  selector: "em-schedule-classpath-dialog",
  templateUrl: "./schedule-classpath-dialog.component.html",
  styleUrls: ["./schedule-classpath-dialog.component.scss"],
  encapsulation: ViewEncapsulation.None,
  host: {// eslint-disable-line @angular-eslint/no-host-metadata-property
    "class": "schedule-classpath-dialog"
  }
})
export class ScheduleClasspathDialogComponent implements OnInit {
  @ViewChild("scrollViewport", { static: true }) scrollViewport: ElementRef<any>;
  @ViewChild("pathEditInput") pathInput: ElementRef;
  title: string;
  classpath: string[] = [];
  separator: string = ";";
  selectedIndex: number = -1;
  editing: boolean = false;
  editingPath: string;
  searchText: string;

  constructor(@Inject(MAT_DIALOG_DATA) data: any,
              private dialog: MatDialog,
              private dialogRef: MatDialogRef<ScheduleClasspathDialogComponent>,
              private snackbar: MatSnackBar) {
    this.title = data.title;

    if(!!data.separator) {
      this.separator = data.separator;
    }

    this.classpath = data.classpath ? data.classpath.split(this.separator) : [];
  }

  ngOnInit(): void {
  }

  selectPath(index: number) {
    let oldSelectIndex = this.selectedIndex;
    this.selectedIndex = index;

    if(index != oldSelectIndex && oldSelectIndex != -1) {
      this.clearEdit(oldSelectIndex);
    }
  }

  search(value: string) {
    this.searchText = value;
  }

  matchSearch(path: string): boolean {
    if(this.searchText && this.searchText.trim()) {
      return path && path.toLowerCase().includes(this.searchText.toLowerCase());
    }

    return true;
  }

  editPath() {
    this.editing = true;
    this.editingPath = this.classpath[this.selectedIndex];

    setTimeout(() => {
      if(this.pathInput && this.pathInput.nativeElement) {
        this.pathInput.nativeElement.focus();
      }
    }, 0);
  }

  pathChange(index: number, event: any) {
    if(this.classpath.length < index) {
      return;
    }

    this.editingPath = event.target.value;
  }

  clearEdit(index: number) {
    if(this.editingPath == "") {
      this.classpath.splice(index, 1);
    }
    else if(this.editingPath){
       let errorMsg = "";

       if (/;/.test(this.editingPath)) {
          errorMsg = "_#(js:em.schedule.invalidClasspath)";
       }
       else if(this.classpath.find((path) => path == this.editingPath)) {
          errorMsg = "_#(js:em.schedule.duplicateClasspath)";
       }

       if(errorMsg != "") {
          let config = new MatSnackBarConfig();
          config.duration = Tool.SNACKBAR_DURATION;
          config.panelClass = ["max-width"];
          this.snackbar.open(errorMsg, "_#(js:Close)", config);

          if(this.classpath[index] == "") {
             this.classpath.splice(index, 1);
          }
       }
       else {
          this.classpath[index] = this.editingPath;
       }
    }

    this.editing = false;
    this.editingPath = null;
  }

  isEditingPath(index: number) {
    return this.selectedIndex == index && this.editing;
  }

  isSelectedPath(index: number) {
    return this.selectedIndex == index;
  }

  newPath() {
    if(this.scrollViewport && this.scrollViewport.nativeElement) {
      if(this.selectedIndex > 0 && this.selectedIndex < this.classpath.length) {
        this.clearEdit(this.selectedIndex);
      }

      this.classpath.push("");
      this.selectedIndex = this.classpath.length - 1;
      this.editingPath = "";
      this.editing = true;
      let pathItemHeight = 35;

      setTimeout(() => {
        this.scrollViewport.nativeElement.scrollTop = this.classpath.length * pathItemHeight;
      }, 0);
    }
  }

  deletePath() {
    if(this.selectedIndex == -1  || this.selectedIndex > this.classpath.length - 1) {
      return;
    }

    this.classpath.splice(this.selectedIndex, 1);

    if(this.selectedIndex >= this.classpath.length) {
      if(this.classpath.length == 0) {
        this.resetStates();
      }
      else {
        this.selectedIndex = this.classpath.length - 1;
      }
    }

    this.editingPath = this.classpath[this.selectedIndex];
  }

  moveDownPath() {
    if(this.exchangePath(this.selectedIndex, this.selectedIndex + 1)) {
      this.selectedIndex++;
      this.autoScroll();
    }
  }

  moveUpPath() {
    if(this.exchangePath(this.selectedIndex, this.selectedIndex - 1)) {
      this.selectedIndex--;
      this.autoScroll();
    }
  }

  private autoScroll() {
    if(this.scrollViewport && this.scrollViewport.nativeElement) {
      let pathItemHeight = 41;
      let scrollElement = this.scrollViewport.nativeElement;
      let scrollTop = scrollElement.scrollTop;
      let pathItemOffset = (this.selectedIndex + 1) * pathItemHeight;
      let height = this.scrollViewport.nativeElement.getBoundingClientRect().height;
      let newScrollTop = scrollTop;

      if(pathItemOffset - scrollTop > height) {
        newScrollTop = pathItemOffset - height;
      }
      else if(pathItemOffset - pathItemHeight < scrollTop) {
        newScrollTop = pathItemOffset - pathItemHeight;
      }

      if(scrollTop != newScrollTop) {
        setTimeout(() => scrollElement.scrollTop = newScrollTop, 0);
      }
    }
  }

  editText() {
    const dialogRef = this.dialog.open(EditClasspathTextDialogComponent, {
      role: "dialog",
      width: "750px",
      maxWidth: "100%",
      maxHeight: "100%",
      disableClose: true,
      data: {
        title: "_#(js:Edit Classpath)",
        classpath: this.classpath.join(this.separator)
      }
    });

    dialogRef.afterClosed().subscribe((data) => {
      if(data) {
        this.classpath = data.split(this.separator);
        this.classpath = this.classpath.filter((path) => path && path.trim());
        this.resetStates();
      }
    });
  }

  private exchangePath(index1: number, index2: number): boolean {
    if (index1 > this.classpath.length - 1 || index2 > this.classpath.length - 1 || index1 < 0 ||
       index2 < 0)
    {
      return false;
    }

    let path1: string = this.classpath[index1];
    this.classpath[index1] = this.classpath[index2];
    this.classpath[index2] = path1;

    return true;
  }

  private resetStates(): void {
    this.selectedIndex = -1;
    this.editing = false;
    this.editingPath = null;
  }

  ok(): void {
    this.dialogRef.close(this.classpath.join(this.separator));
  }
}
