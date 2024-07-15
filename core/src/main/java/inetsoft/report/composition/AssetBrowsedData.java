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
package inetsoft.report.composition;

import inetsoft.uql.util.BrowsedData;
import inetsoft.util.ItemList;

/**
 * AssetBrowseData encapsulates the logic that retrieves data by
 * BrowseDataEvent. We set the BrowseData object into editors instead of the
 * actual data. When the editor needs to open the browse data window,
 * it can retrieve the data on demand.
 *
 * @version 8.0, 9/11/2005
 * @author InetSoft Technology Corp
 */
public class AssetBrowsedData extends BrowsedData {
   /**
    * Constructor.
    * @param list the data retrieved from BrowseDataEvent.
    */
   public AssetBrowsedData(ItemList list) {
      this.list = list;
   }

   /**
    * Get the browsed data.
    * @return the browsed data.
    */
   @Override
   public String[][] getBrowsedData() {
      String[][] data = null;

      if(list != null && list.size() > 0) {
         ItemList dataList = list;
         ItemList labelList = list;

         if(list.getItem(0) instanceof ItemList) {
            // never get here
            dataList = (ItemList) list.getItem(0);
            labelList = (ItemList) (list.size() > 1 ? list.getItem(1) :
                                                      dataList);
         }

         data = new String[2][dataList.size()];

         for(int i = 0; i < dataList.size(); i++) {
            Object dobj = dataList.getItem(i);
            Object lobj = labelList.getItem(i);
            data[0][i] = dobj != null ? dobj.toString() : "";
            data[1][i] = lobj != null ? lobj.toString() : "";
         }
      }

      return data != null ? data : new String[2][0];
   }

   private ItemList list;
}
