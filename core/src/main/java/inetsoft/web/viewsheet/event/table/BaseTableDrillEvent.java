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
package inetsoft.web.viewsheet.event.table;

public class BaseTableDrillEvent implements BaseTableEvent {

   public DrillEvent[] getDrillEvents() {
      return drillEvents;
   }

   public void setDrillEvents(DrillEvent[] drillEvents) {
      this.drillEvents = drillEvents;
   }

   public boolean isDrillUp() {
      return drillUp;
   }

   public void setDrillUp(boolean drillUp) {
      this.drillUp = drillUp;
   }

   @Override
   public String getAssemblyName() {
      return assemblyName;
   }

   public void setAssemblyName(String assemblyName) {
      this.assemblyName = assemblyName;
   }

   public boolean isReplace() {
      return replace;
   }

   public void setReplace(boolean replace) {
      this.replace = replace;
   }

   private DrillEvent[] drillEvents;
   private boolean drillUp;
   private String assemblyName;
   private boolean replace;
}
