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
package inetsoft.web.composer.vs.objects.event;

import inetsoft.web.viewsheet.event.ViewsheetEvent;

/**
 * Class that encapsulates the parameters for converting a table to freehand table.
 *
 * @since 12.3
 */
public class ConvertToFreehandTableEvent extends VSObjectEvent implements ViewsheetEvent {
   @Override
   public String toString() {
      return "ConvertToFreehandTableEvent{" +
         "name='" + this.getName() + '\'' + '}';
   }

   public void setConfirmed(boolean confirmed) {
      this.confirmed = confirmed;
   }

   public boolean isConfirmed() {
      return confirmed;
   }

   public boolean confirmed() {
      return confirmed;
   }

   public String name() {
      return getName();
   }

   private boolean confirmed;
}
