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
package inetsoft.util;

/**
 * Define an object which listens for DataChangeEvents.
 *
 * @version 6.1
 * @author InetSoft Technology Corp
 */
public interface DataChangeListener {
   /**
    * Invoked when the target of the listener has changed its data.
    *
    * @param event a DataChangeEvent Object.
    */
   public void dataChanged(DataChangeEvent event);
}