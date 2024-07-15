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
package inetsoft.uql.asset.delete;

import inetsoft.uql.asset.sync.DependencyTool;
import inetsoft.util.Tool;
import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import java.util.List;

public abstract class DependencyChecker {

   @Nullable
   public List<DeleteInfo> hasDependency(List<DeleteInfo> infos, boolean checkAll)
      throws Exception
   {
      return null;
   }

   protected abstract boolean isSameSource(Element elem, DeleteInfo info) throws Exception;

   protected boolean checkDataRef(Element elem, DeleteInfo info) {
      String name = info.getName();
      String isRangeRef = "(@class='inetsoft.uql.asset.NumericRangeRef' " +
         "or @class='inetsoft.uql.asset.DateRangeRef') and attribute='" + name + "'";

      Element node = DependencyTool.getChildNode(xpath, elem, ".//dataRef[(@name='"
         + name + "' and @class='inetsoft.uql.erm.ExpressionRef')" +
         " or @attribute='"+ name + "'" +
         " or ("+ isRangeRef + ")]");

      if(node != null) {
         return true;
      }

      // check chart aggregate binding
      node = DependencyTool.getChildNode(xpath, elem, ".//dataRef[refValue='"
         + name + "']");

      if(node != null) {
         return true;
      }

      return false;
   }

   protected boolean checkTablePaths(Element elem, DeleteInfo info) {
      NodeList list = DependencyTool.getChildNodes(xpath, elem, ".//tableDataPath/path/aPath");

      for(int i = 0; list != null && i < list.getLength(); i++) {
         Element aPath = (Element) list.item(i);

         String val = Tool.getValue(aPath);

         if(Tool.equals(info.getName(), val)) {
            return true;
         }
      }

      return false;
   }

   protected <T> List<T> makeEmptyToNone(List<T> infos) {
      return CollectionUtils.isEmpty(infos) ? null : infos;
   }

   protected boolean checkChildValue(Element elem, String key, String name, boolean isTotal) {
      Element child = Tool.getChildNodeByTagName(elem, key);
      String val = Tool.getValue(child);

      if(isTotal && Tool.equals(val, name)) {
         return true;
      }
      else if(!isTotal && val != null && val.contains(name)) {
         return true;
      }

      return false;
   }

   protected final static XPath xpath = XPathFactory.newInstance().newXPath();
}
