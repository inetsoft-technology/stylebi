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
package inetsoft.util.swap;

import java.util.EventObject;

/**
 * DataSwapEvent is used to notify a swappable object data accessed or swapped
 * occured.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class DataSwapEvent extends EventObject {
   /**
    * Constructor.
    * @param source the event target.
    * @param swapped identify the data is swapped out or not.
    */
   public DataSwapEvent(Object source, boolean swapped) {
      super(source);
      this.swapped = swapped;
   }

   /**
    * Check if data swapped out or acessed in.
    */
   public boolean isDataSwapped() {
      return swapped;
   }

   private boolean swapped;
}
