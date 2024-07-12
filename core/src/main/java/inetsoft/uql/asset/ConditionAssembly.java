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
package inetsoft.uql.asset;

import inetsoft.uql.ConditionList;
import inetsoft.uql.ConditionListWrapper;

/**
 * Condition assembly contains a <tt>ConditionList</tt> for reuse.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public interface ConditionAssembly extends AttachedAssembly, WSAssembly,
   ConditionListWrapper
{
   /**
    * Get the condition list.
    * @return the condition list of the condition assembly.
    */
   @Override
   public ConditionList getConditionList();

   /**
    * Set the condition list.
    * @param conditions the specified condition list.
    */
   public void setConditionList(ConditionList conditions);
}
