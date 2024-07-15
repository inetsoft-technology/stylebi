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
package inetsoft.uql.xmla;

import inetsoft.uql.ConditionItem;
import inetsoft.uql.XNode;
import inetsoft.util.Tool;

/**
 * The XMLANode is a class extends XNode. XMLANode is extended
 * by XMLASet which is used to build condition.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class XMLANode extends XNode {
   /**
    * Compare if two nodes are equal. Two nodes are considered equal if they
    * have same name.
    */
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      if(!(obj instanceof XMLANode)) {
         return false;
      }

      XMLANode node = (XMLANode) obj;

      return Tool.equals(node.getValue(), getValue());
   }

   /**
    * Calculate hash code of a node.
    */
   public int hashCode() {
      if(getValue() == null) {
         return super.hashCode();
      }

      return getValue().hashCode();
   }
   
   /**
    * Clone node value when necessary, do nothing by default.
    */
   @Override
   protected void cloneValue(XNode node) {
      Object val = node.getValue();
      
      if(val instanceof ConditionItem) {
         node.setValue(((ConditionItem) val).clone());
      }
   }   
}
