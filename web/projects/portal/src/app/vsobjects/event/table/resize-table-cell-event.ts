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
import {ResizeTableRowEvent} from "../resize-table-row-event";
import {ResizeTableColumnEvent} from "../resize-table-column-event";

/**
 * Resize both width/height of table cell in 1 event
 */
export class ResizeTableCellEvent {
  constructor(public colEvent: ResizeTableColumnEvent, public rowEvent: ResizeTableRowEvent) {
  }
}
