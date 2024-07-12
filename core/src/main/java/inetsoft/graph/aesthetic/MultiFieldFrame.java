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
package inetsoft.graph.aesthetic;

/**
 * This interface defines the common api for visual frames with multiple fields. The visual
 * attribute is calculated from the values of multiple fields.
 *
 * @version 13.2
 * @author InetSoft Technology
 */
public interface MultiFieldFrame {
   /**
    * Set the fields for this visual frame.
    */
   public void setFields(String... fields);

   /**
    * Get the fields for this visual frame.
    */
   public String[] getFields();
}
