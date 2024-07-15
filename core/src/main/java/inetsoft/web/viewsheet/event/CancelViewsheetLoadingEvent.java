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
package inetsoft.web.viewsheet.event;

public class CancelViewsheetLoadingEvent {
   public String getRuntimeViewsheetId() {
      return this.runtimeViewsheetId;
   }

   public boolean isPreview() {
      return preview;
   }

   public boolean isIniting() {
      return initing;
   }

   public boolean isMeta() {
      return meta;
   }

   @Override
   public String toString() {
      return "CancelViewsheetLoadingEvent{" +
         "runtimeViewsheetId='" + runtimeViewsheetId + '\'' +
         ", preview='" + preview + '\'' +
         ", initing='" + initing + '\'' +
         '}';
   }

   private String runtimeViewsheetId;
   private boolean preview;
   private boolean initing;
   private boolean meta;
}
