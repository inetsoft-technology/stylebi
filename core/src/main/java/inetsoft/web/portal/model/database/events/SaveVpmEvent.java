/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.portal.model.database.events;

import inetsoft.web.portal.model.database.VPMDefinition;

public class SaveVpmEvent {
   public String getDatabase() {
      return database;
   }

   public void setDatabase(String database) {
      this.database = database;
   }

   public String getVpmName() {
      return vpmName;
   }

   public void setVpmName(String vpmName) {
      this.vpmName = vpmName;
   }

   public VPMDefinition getModel() {
      return model;
   }

   public void setModel(VPMDefinition model) {
      this.model = model;
   }

   private String database;
   private String vpmName;
   private VPMDefinition model;
}
