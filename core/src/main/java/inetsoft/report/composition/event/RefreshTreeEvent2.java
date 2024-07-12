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
package inetsoft.report.composition.event;

import inetsoft.report.composition.AssetCommand;
import inetsoft.report.composition.AssetTreeModel;
import inetsoft.report.composition.command.RefreshTreeCommand2;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.Catalog;
import inetsoft.util.ItemList;

/**
 * Refresh tree event.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class RefreshTreeEvent2 extends RefreshTreeEvent {
   /**
    * Constructor.
    */
   public RefreshTreeEvent2() {
      super();
   }

   /**
    * Constructor.
    */
   public RefreshTreeEvent2(AssetEntry[] entries) {
      super(entries);
   }

   /**
    * Constructor.
    */
   public RefreshTreeEvent2(AssetEntry[] entries, int selector,
                            boolean folderInclude, boolean showReportScope)
   {
      this(entries);
      put("selector", selector + "");
      put("isFolderIncluded", folderInclude + "");
      put("showReportScope", showReportScope + "");
   }

   /**
    * Swap Worksheet and Query nodes.
    * @return isWSandQuerySwapped if swap ws and query nodes, return true.
    */
   public boolean isWSandQuerySwapped() {
      String str = (String) get("swapWSandQuery");
      str = str == null ? "false" : str;
      return Boolean.valueOf(str).booleanValue();
   }

   /**
    * Is folders included.
    * @return isFolderIncluded if folders included, return true.
    */
   public boolean isFolderIncluded() {
      String str = (String) get("folderInclude");
      str = str == null ? "true" : str;
      return Boolean.valueOf(str).booleanValue();
   }

   /**
    * Is folders included.
    * @return isFolderIncluded if folders included, return true.
    */
   public boolean showReportScope() {
      String str = (String) get("showReportScope");
      str = str == null ? "true" : str;
      return Boolean.valueOf(str).booleanValue();
   }

   /**
    * Get the name of the asset event.
    * @return the name of the asset event.
    */
   @Override
   public String getName() {
      return Catalog.getCatalog().getString("Refresh Tree");
   }

   /**
    * Process this event.
    */
   @Override
   public void process(AssetRepository engine, AssetCommand command)
      throws Exception
   {
      ItemList selectorList = (ItemList) get("selector");
      AssetEntry.Selector selector = new AssetEntry.Selector();

      if(selectorList == null) {
         selector.add(AssetEntry.Type.FOLDER);
      }
      else {
         for(Object item : selectorList.toArray()) {
            int id = Integer.parseInt((String) item);
            AssetEntry.Type type = AssetEntry.Type.forId(id);
            selector.add(type);
         }
      }

      if(!isEntriesExisted(entries, engine)) {
         entries = null;
      }

      AssetTreeModel model = AssetEventUtil.refreshTree(engine, getUser(), this,
         selector);

      AssetTreeModel.Node root = (AssetTreeModel.Node) model.getRoot();
      AssetTreeModel.Node[] nodes = root.getNodes();

      if(nodes.length > 0 && nodes[0].getNodeCount() != 0) {
         AssetTreeModel.Node[] snodes = nodes[0].getNodes();

         for(int i = snodes.length - 1; i >= 0; i--) {
            AssetEntry entry = snodes[i].getEntry();

            if(entry == null) {
               continue;
            }

            if(AssetRepository.LOCAL_QUERY.equals(entry.getPath())) {
               nodes[0].removeNode(i);
            }
         }
      }

      command.addCommand(new RefreshTreeCommand2(model));
   }

   /**
    * If any one of the entries is not existing, false will be returned.
    * @param entries asset entries.
    */
   private boolean isEntriesExisted(AssetEntry[] entries,
                                    AssetRepository engine) throws Exception
   {
      if(entries == null) {
         return false;
      }

      for(int i = 0; i < entries.length; i++) {
         if(!engine.containsEntry(entries[i])) {
            return false;
         }
      }

      return true;
   }
}
