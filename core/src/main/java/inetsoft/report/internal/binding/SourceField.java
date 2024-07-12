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
package inetsoft.report.internal.binding;

/**
 * Source field.
 *
 * @version 11.1, 5/3/2011
 */
public interface SourceField {
   /**
    * Set the source of this field. It could be the name of the query or data
    * model.
    */
   public void setSource(String source);

   /**
    * Get the source of this field.
    */
   public String getSource();

   /**
    * Get the source of this field.
    * @param force, to get the really source any way, because from 11.0, we
    * not support join source, so the source is really useless except for cal
    * table.
    */
   public String getSource(boolean force);

   /**
    * Set the prefix of source.
    */
   public void setSourcePrefix(String prefix);

   /**
    * Get the prefix of the source.
    */
   public String getSourcePrefix();

   /**
    * Set the type of this source.
    */
   public void setSourceType(int type);

   /**
    * Get the type of the source.
    */
   public int getSourceType();
}