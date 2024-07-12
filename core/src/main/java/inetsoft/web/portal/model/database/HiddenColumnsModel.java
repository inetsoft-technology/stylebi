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
package inetsoft.web.portal.model.database;

import inetsoft.sree.security.IdentityID;
import inetsoft.web.binding.drm.DataRefModel;

import java.util.List;

public class HiddenColumnsModel {
   public List<String> getRoles() {
      return roles;
   }

   public void setRoles(List<String> roles) {
      this.roles = roles;
   }

   public List<DataRefModel> getHiddens() {
      return hiddens;
   }

   public void setHiddens(List<DataRefModel> hiddens) {
      this.hiddens = hiddens;
   }

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getScript() {
      return script;
   }

   public void setScript(String script) {
      this.script = script;
   }

   private List<String> roles;
   private List<DataRefModel> hiddens;
   private String name;
   private String script;
}
