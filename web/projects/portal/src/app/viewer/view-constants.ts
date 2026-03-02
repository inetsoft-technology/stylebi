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
/**
 * Constant strings used multiple times should be defined only once.
 */
export class ViewConstants {
   /**
    * Use to save previous sheet data stack when hyperlink to other sheet.
    */
   public static readonly PRE_SNAPSHOT_PARAM_NAME = "previousSnapshots";
   /** Height of the viewer toolbar in pixels on desktop. */
   public static readonly TOOLBAR_HEIGHT_PX = 33;
   /** Height of the viewer toolbar in pixels on mobile (taller to accommodate touch targets). */
   public static readonly TOOLBAR_HEIGHT_MOBILE_PX = 66;
}
