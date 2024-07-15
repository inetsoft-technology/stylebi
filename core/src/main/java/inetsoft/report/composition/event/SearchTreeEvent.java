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
package inetsoft.report.composition.event;

import inetsoft.report.composition.*;
import inetsoft.report.composition.AssetTreeModel.Node;
import inetsoft.report.composition.command.SearchTreeCommand;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Search tree event.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class SearchTreeEvent extends AssetRepositoryEvent {
   /**
    * Constructor.
    */
   public SearchTreeEvent() {
      super();
   }

   /**
    * Construct a tree with all nodes that contains the search string.
    * @param searchString search string (ignore case).
    */
   public SearchTreeEvent(String searchString) {
      this();
      put("searchString", searchString);
   }

   /**
    * Construct a tree with all nodes that contains the search string.
    * @param searchString search string (ignore case).
    * @param entries the opend Asset entires.
    */
   public SearchTreeEvent(String searchString, AssetEntry[] entries) {
      this();
      this.entries = entries;
      put("searchString", searchString);
   }

   /**
    * Get the name of the asset event.
    * @return the name of the asset event.
    */
   @Override
   public String getName() {
      return Catalog.getCatalog().getString("Search Tree");
   }

   /**
    * Return true if the event will access storage heavily.
    */
   @Override
   public boolean isStorageEvent() {
      return true;
   }

   /**
    * Process this event.
    */
   @Override
   public void process(AssetRepository engine, AssetCommand command)
      throws Exception
   {
      AssetEntry.Selector selector = new AssetEntry.Selector(
         AssetEntry.Type.FOLDER, AssetEntry.Type.WORKSHEET,
         AssetEntry.Type.DATA, AssetEntry.Type.PHYSICAL);

      if(isServer()) {
         boolean queryDisable = "true".equals(get("queryDisable"));
         boolean wsDisable = "true".equals(get("wsDisable"));
         selector = new AssetEntry.Selector(AssetEntry.Type.FOLDER,
            AssetEntry.Type.VIEWSHEET, AssetEntry.Type.VIEWSHEET_SNAPSHOT,
            AssetEntry.Type.REPOSITORY_FOLDER, AssetEntry.Type.PHYSICAL);

         //bug1358153533483, on server sometime need not select query and ws.
         if(!queryDisable) {
            selector.add(AssetEntry.Type.QUERY, AssetEntry.Type.DATA_SOURCE);
         }

         if(!wsDisable) {
            selector.add(AssetEntry.Type.WORKSHEET);
         }
      }

      AssetTreeModel model = AssetEventUtil.refreshTree(engine, getUser(), this,
         selector);
      removeEmptyFolders((Node) model.getRoot());
      command.addCommand(new SearchTreeCommand(model));
   }

   /**
    * Remove all empty tree models.
    * @return true if this node is empty and should be removed.
    */
   private boolean removeEmptyFolders(Node root) {
      Node[] nodes = root.getNodes();

      for(int i = nodes.length - 1; i >= 0; i--) {
         boolean empty = removeEmptyFolders(nodes[i]);

         if(empty) {
            root.removeNode(i);
         }
      }

      //bug1358328390708, the empty folder contains search string should not be
      //remove.
      return root.getNodes().length == 0 && root.getEntry().isFolder() &&
         !root.getEntry().getName().contains(getSearchString());
   }

   /**
    * Get the search string for constructing an asset tree.
    */
   public String getSearchString() {
      return (String) get("searchString");
   }

   /**
    * Get the opened entries.
    */
   public AssetEntry[] getEntries() {
      return entries;
   }

   /**
    * Write the contents of this object.
    * @param writer the output stream to which to write the XML data.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(entries != null) {
         writer.print("<entries>");

         for(int i = 0; i < entries.length; i++) {
            entries[i].writeXML(writer);
         }

         writer.print("</entries>");
      }
   }

   /**
    * Read in the contents of this object from an xml tag.
    * @param tag the specified xml element.
    */
   @Override
   protected void parseContents(Element tag) throws Exception {
      super.parseContents(tag);
      Element node = Tool.getChildNodeByTagName(tag, "entries");

      if(node != null) {
         List list = new ArrayList();
         NodeList nodes =  node.getChildNodes();

         for(int i = 0; i < nodes.getLength(); i++) {
            list.add(AssetEntry.createAssetEntry((Element) nodes.item(i)));
         }

         entries = new AssetEntry[list.size()];
         list.toArray(entries);
      }
   }

   protected AssetEntry[] entries;
}
