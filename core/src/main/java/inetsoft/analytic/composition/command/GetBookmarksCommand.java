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
package inetsoft.analytic.composition.command;

import inetsoft.report.composition.AssetCommand;
import inetsoft.report.composition.command.DataCommand;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.viewsheet.VSBookmark;
import inetsoft.uql.viewsheet.VSBookmarkInfo;
import inetsoft.util.ItemList;
import inetsoft.util.Tool;

import java.util.ArrayList;
import java.util.List;

/**
 * Get all bookmarks command.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class GetBookmarksCommand extends AssetCommand
   implements DataCommand
{
   /**
    * Constructor.
    */
   public GetBookmarksCommand() {
      super();
   }

   /**
    * Constructor.
    * @param defBookmark the user defined bookmark's info, defBookmark[0] is the
    *        bookmark's name, defBookmark[1] is the bookmark's owner.
    * @param bookmarks the bookmark names.
    */
   public GetBookmarksCommand(List<VSBookmarkInfo> bookmarks,
                              VSBookmark.DefaultBookmark defBookmark)
   {
      this();

      ItemList list = new ItemList("bookmarks");
      list.addAllItems(bookmarks);
      put("bookmarks", list);

      if(defBookmark != null) {
         ItemList defList = new ItemList("defaultBookmark");
         defList.addItem(defBookmark.getName());
         defList.addItem(defBookmark.getOwner());
         put("defaultBookmark", defList);
      }

      put("timeProp", getTimeFormat());
   }

   /**
    * Get the data contained in the data command.
    * @return the data contained in the data command.
    */
   @Override
   public Object getData() {
      List<Object> list = new ArrayList<>();
      list.add(get("bookmarks"));
      list.add(get("defaultBookmark"));
      list.add(get("timeProp"));

      return list;
   }

   /**
    * Get time format .
    */
   public String getTimeFormat() {
      String timeProp = SreeEnv.getProperty("format.time");

      if(timeProp == null || timeProp.equals("")) {
         timeProp = Tool.DEFAULT_TIME_PATTERN;
      }

      return timeProp.trim();
   }
}
