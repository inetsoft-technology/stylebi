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
package inetsoft.web.composer.model.ws;

import java.util.HashMap;
import java.util.Map;

public class AddColumnInfoResult {

   public AddColumnInfoResult() {
   }

   public String getLimitMessage() {
      return limit;
   }

   public void setLimitMessage(String limitMessage) {
      this.limit = limitMessage;
   }

   public Map<String, String> getColumnMap() {
      return map;
   }

   public void setColumnMap(Map<String, String> map) {
      this.map = map;
   }

   private String limit = null;
   private Map<String, String> map = new HashMap<>();
}
