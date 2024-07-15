/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.viewsheet;

import java.util.EventObject;

public class ViewsheetLifecycleMessage extends EventObject {
   public ViewsheetLifecycleMessage(Object source, String runtimeId,
                                    ViewsheetLifecycleState state)
   {
      super(source);
      this.runtimeId = runtimeId;
      this.state = state;
   }

   public String getRuntimeId() {
      return runtimeId;
   }

   public ViewsheetLifecycleState getState() {
      return state;
   }

   @Override
   public String toString() {
      return "ViewsheetLifecycleMessage{" +
         "runtimeId='" + runtimeId + '\'' +
         ", state=" + state +
         '}';
   }

   private final String runtimeId;
   private final ViewsheetLifecycleState state;
}
