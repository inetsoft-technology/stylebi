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

/**
 * LockableVSAssembly represents one lockable assembly contained in a
 * <tt>Viewsheet</tt>.
 *
 * @version 12.1
 * @author InetSoft Technology Corp
 */
public interface LockableVSAssembly {
   /**
    * if the object is locked ,then it can not be drag.
    */
   public Boolean islocked();

   /*
    * if the object is locked ,then it can not be drag.
    * this function is to set the value of whether locked.
    * @param locked,lock the object when the value is true.
    */
   public void setLocked(Boolean locked);
}
