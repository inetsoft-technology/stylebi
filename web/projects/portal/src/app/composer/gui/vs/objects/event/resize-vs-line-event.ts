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
import { VSObjectEvent } from "../../../../../vsobjects/event/vs-object-event";
import { VSLineModel } from "../../../../../vsobjects/model/vs-line-model";

/**
 * Event used to resize a vs line object in the composer.
 */
export class ResizeVSLineEvent extends VSObjectEvent {
   /**
    * The new x position of the starting anchor.
    */
   public startLeft: number;

   /**
    * The new y position of the starting anchor.
    */
   public startTop: number;

   /**
    * The new x position of the ending anchor.
    */
   public endLeft: number;

   /**
    * The new y position of the ending anchor.
    */
   public endTop: number;

   /**
    * The new width of the line image.
    */
   public width: number;

   /**
    * The new width of the line image.
    */
   public height: number;

   /**
    * The new x offset of the line image.
    */
   public offsetX: number;

   /**
    * The new y offset of the line image.
    */
   public offsetY: number;

   public startAnchorId: string;

   public startAnchorPos: number;

   public endAnchorId: string;

   public endAnchorPos: number;

   /**
    * Creates a new instance of <tt>ResizeVSLineEvent</tt>.
    *
    * @param object the vs object.
    */
   constructor(object: VSLineModel) {
      super(object.absoluteName);

      this.startLeft = object.startLeft;
      this.startTop = object.startTop;
      this.endLeft = object.endLeft;
      this.endTop = object.endTop;

      this.width = object.objectFormat.width;
      this.height = object.objectFormat.height;

      this.offsetX = object.objectFormat.left;
      this.offsetY = object.objectFormat.top;
   }
}