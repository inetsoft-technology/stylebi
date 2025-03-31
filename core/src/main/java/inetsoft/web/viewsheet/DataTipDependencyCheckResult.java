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
package inetsoft.web.viewsheet;

import java.io.Serializable;

/**
 * The result of the datatip cycle dependency check
 */
public class DataTipDependencyCheckResult implements Serializable {

   public void setMessage(String message) {
      this.message = message;
   }

   public String getMessage() {
      return message;
   }

   public void setCycle(boolean cycle) {
      this.cycle = cycle;
   }

   public boolean getCycle() {
      return cycle;
   }

   private String message = "";
   private boolean cycle;
}
