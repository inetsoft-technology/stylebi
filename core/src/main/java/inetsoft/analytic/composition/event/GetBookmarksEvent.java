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
package inetsoft.analytic.composition.event;

import inetsoft.analytic.composition.ViewsheetEvent;
import inetsoft.analytic.composition.command.GetBookmarksCommand;
import inetsoft.report.composition.AssetCommand;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.command.MessageCommand;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.VSBookmark;
import inetsoft.uql.viewsheet.VSBookmarkInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Catalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Get bookmarks event.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class GetBookmarksEvent extends ViewsheetEvent {
   /**
    * Constructor.
    */
   public GetBookmarksEvent() {
      super();
   }

   /**
    * Get the name of the asset event.
    * @return the name of the asset event.
    */
   @Override
   public String getName() {
      return Catalog.getCatalog().getString("Get bookmarks");
   }

   /**
    * Check if is undoable/redoable.
    * @return <tt>true</tt> if undoable/redoable.
    */
   @Override
   public boolean isUndoable() {
      return false;
   }

   /**
    * Get the influenced assemblies.
    * @return the influenced assemblies, <tt>null</tt> means all.
    */
   @Override
   public String[] getAssemblies() {
      return new String[0];
   }

   /**
    * Process event.
    */
   @Override
   public void process(RuntimeViewsheet rvs, AssetCommand command)
      throws Exception
   {
      String vsId = (String) get("vsId");
      boolean schedule = "true".equals(get("schedule"));
      boolean mobile = "true".equals(get("mobile"));
      VSBookmark.DefaultBookmark defBookmark = null;
      List<VSBookmarkInfo> bookmarks = new ArrayList<>();

      // get other vs bookmarks, it is not current vs.
      if(vsId != null) {
         AssetEntry entry = AssetEntry.createAssetEntry(vsId);
         AssetRepository rep = getAssetRepository();
         List<IdentityID> users = rep.getBookmarkUsers(entry);
         IdentityID currUser = IdentityID.getIdentityIDFromKey(getUser().getName());

         for(IdentityID user : users) {
            XPrincipal principal = new XPrincipal(user);
            VSBookmark bookmark = rep.getVSBookmark(entry, principal);

            if(bookmark != null) {
               for(String name : bookmark.getBookmarks()) {
                  VSBookmarkInfo bminfo = bookmark.getBookmarkInfo(name);
                  int type = bminfo.getType();

                  if(!user.equals(currUser)) {
                     if(VSBookmark.HOME_BOOKMARK.equals(name)) {
                        continue;
                     }
                     else if(type == VSBookmarkInfo.PRIVATE) {
                        continue;
                     }
                     else if(type == VSBookmarkInfo.GROUPSHARE &&
                        !rvs.isSameGroup(user, currUser))
                     {
                        continue;
                     }
                  }

                  if(bminfo.getOwner() == null) {
                     bminfo.setOwner(user);
                  }

                  bookmarks.add(bminfo);
               }
            }

            // @by davidd bug1370989280980, Add (Home) bookmark, if missing
            if(user.equals(currUser) && (bookmark == null ||
               !bookmark.containsBookmark(VSBookmark.HOME_BOOKMARK)))
            {
               VSBookmarkInfo info = new VSBookmarkInfo(VSBookmark.HOME_BOOKMARK,
                                     VSBookmarkInfo.ALLSHARE, currUser, false,
                                     new java.util.Date().getTime());
               bookmarks.add(info);
            }

            bookmarks = VSUtil.sortBookmark(bookmarks, getUser());
         }

         if(bookmarks.size() == 0) {
            VSBookmarkInfo info = new VSBookmarkInfo(VSBookmark.HOME_BOOKMARK,
                                  VSBookmarkInfo.ALLSHARE, currUser, false,
                                  new java.util.Date().getTime());
            bookmarks.add(info);
         }
      }
      else {
         bookmarks = rvs.getBookmarks();
         defBookmark = rvs.getDefaultBookmark();

         // for BC
         if(defBookmark != null && defBookmark.getOwner() == null) {
            defBookmark.setOwner(IdentityID.getIdentityIDFromKey(getUser().getName()));
         }
      }

      GetBookmarksCommand cmd = new GetBookmarksCommand(bookmarks, defBookmark);

      if(schedule) {
         ScheduleManager manager = ScheduleManager.getScheduleManager();
         AssetEntry entry = rvs.getEntry();
         String taskName = entry.getAlias() != null ?
            entry.getAlias() : entry.getName();
         taskName = isSecurityEnabled() && getUser() != null ?
            getUser().getName() + ":" + taskName : taskName;

         if(manager.getScheduleTask(taskName) != null) {
            String oname = taskName;

            for(int i = 1; i < Integer.MAX_VALUE; i++) {
               if(manager.getScheduleTask(oname + "_" + i) == null) {
                  taskName = oname + "_" + i;
                  break;
               }
            }
         }

         int idx = taskName.indexOf(":");

         if(isSecurityEnabled() && idx > 0) {
            taskName = taskName.substring(idx + 1);
         }

         cmd.put("taskName", taskName);
      }

      command.addCommand(cmd);

      if(!mobile && vsId == null && !schedule) {
         command.addCommand(new MessageCommand("", MessageCommand.OK));
      }
   }

   /**
    * Check if security is enabled.
    */
   private boolean isSecurityEnabled() {
      try {
         return !SecurityEngine.getSecurity().getSecurityProvider().isVirtual();
      }
      catch(Exception ex) {
         LOG.warn("Failed to determine if security is enabled",
            ex);
         return false;
      }
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(GetBookmarksEvent.class);
}
