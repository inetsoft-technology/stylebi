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
package inetsoft.mv;

import inetsoft.mv.data.SubTableBlock;
import inetsoft.mv.mr.AbstractMapResult;

/**
 * SubMVResult, the map result passed on from data node to server node.
 *
 * @author InetSoft Technology
 * @version 10.2
 */
public final class SubMVResult extends AbstractMapResult {
   /**
    * Create an instance of SubMVResult.
    */
   public SubMVResult() {
      super();
   }

   /**
    * Create an instance of SubMVResult.
    */
   public SubMVResult(SubMVTask task) {
      super(task);
   }

   /**
    * Create an instance of SubMVResult.
    */
   public SubMVResult(SubMVTask task, SubTableBlock node) {
      super(task);

      setData(node);
   }

   /**
    * Get the data.
    */
   public SubTableBlock getData() {
      return (SubTableBlock) get("data");
   }

   /**
    * Set the data.
    */
   public void setData(SubTableBlock data) {
      set("data", data);
   }
}
