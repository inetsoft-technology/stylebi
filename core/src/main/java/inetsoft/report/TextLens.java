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
package inetsoft.report;

/**
 * TextLens is a projected view into a text content. It can be used to
 * wrap around text source, such as TextField or URL. Refer to 
 * inetsoft.report.lens package for predefined TextLens classes.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public interface TextLens extends java.io.Serializable, Cloneable {
   /**
    * Get the text content.
    * @return text string.
    */
   public String getText();

   /**
    * Make a copy of this text lens.
    */
   public Object clone();
}

