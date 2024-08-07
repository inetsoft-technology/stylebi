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
import { Component, EventEmitter, OnInit, Output } from "@angular/core";

@Component({
  selector: "c-image-format-select",
  templateUrl: "./image-format-select.component.html"
})
export class ImageFormatSelectComponent implements OnInit {

  @Output() onCommit: EventEmitter<boolean> = new EventEmitter<boolean>();
  @Output() onCancel: EventEmitter<void> = new EventEmitter<void>();
  constructor() { }

  ngOnInit() {
  }

  png(): void {
    this.onCommit.emit(false);
  }

  svg(): void {
    this.onCommit.emit(true);
  }

  cancel(): void {
    this.onCancel.emit();
  }
}
