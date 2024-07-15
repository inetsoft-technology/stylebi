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
import { NgbModalOptions } from "@ng-bootstrap/ng-bootstrap";

/**
 * Additional options for the slide out component.
 */
export interface SlideOutOptions extends NgbModalOptions {
   /**
    * The panel title is automatically set via ".modal-title" selector content,
    * however in the case of binded titles, this will not work.
    * It is best to set it in that case.
    */
   title?: string;
   /**
    * Which side of the container to attach to.
    */
   side?: "left" | "right";
   // assembly id associated with this pane
   objectId?: string;
   // true to show popup dialog
   popup?: boolean;

   /**
    *  Whether to limit resize.
    */
   limitResize?: boolean;
}
