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
import { ResizeVSObjectEvent } from "./resize-vs-object-event";

/**
 * Event used to resize title of an object in the composer.
 */
export class ResizeVsObjectTitleEvent extends ResizeVSObjectEvent {
   /**
    * The title height.
    */
   public titleHeight: number;

   /**
    * Creates a new instance of <tt>ResizeVsObjectTitleEvent</tt>.
    *
    * @param objectName the name of the object.
    * @param width      the new width.
    * @param height     the new height.
    */
   constructor(objectName: string, left: number, top: number, width: number, height: number,
               titleHeight: number)
   {
      super(objectName, left, top, width, height);
      this.titleHeight = titleHeight;
   }
 }