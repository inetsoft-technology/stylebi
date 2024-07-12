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

import inetsoft.report.composition.*;
import inetsoft.report.composition.command.CollectConnectionVariablesCommand;
import inetsoft.report.composition.command.RefreshTreeCommand;
import inetsoft.uql.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.xmla.XMLADataSource;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

/**
 * Refresh tree event.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class RefreshTreeEvent extends AssetRepositoryEvent
   implements BinaryEvent
{
   /**
    * Constructor.
    */
   public RefreshTreeEvent() {
      super();
   }

   /**
    * Constructor.
    * @param entries the opend Asset entires.
    */
   public RefreshTreeEvent(AssetEntry[] entries) {
      this();
      this.entries = entries;
   }

   /**
    * Constructor.
    * @param entries the opend Asset entires.
    */
   public RefreshTreeEvent(AssetEntry[] entries, boolean isVSIncluded) {
      this(entries);
      put("isVSIncluded", isVSIncluded + "");
   }

   /**
    * Return true if the event will access storage heavily.
    */
   @Override
   public boolean isStorageEvent() {
      return true;
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
      List list = new ArrayList();
      XPrincipal user = (XPrincipal) getUser();
      VariableTable vtbl = new VariableTable();
      fixEntries();
      XUtil.copyDBCredentials(user, vtbl);

      for(int i = 0; entries != null && i < entries.length; i++) {
         if(entries[i].isDataSource() ||
            "true".equals(entries[i].getProperty("CUBE_TABLE")))
         {
            XRepository rep = XFactory.getRepository();
            Object session = engine.getSession();
            String source = entries[i].getName();
            XDataSource ds = rep.getDataSource(source);

            if(ds == null || !(ds instanceof JDBCDataSource) &&
               !(ds instanceof XMLADataSource))
            {
               continue;
            }

            if(vtbl.contains(XUtil.DB_USER_PREFIX + source)) {
               rep.connect(session, ":" + source, vtbl);
               continue;
            }

            UserVariable[] vars = rep.getConnectionParameters(
               engine.getSession(), ":" + source);

            for(int j = 0; vars != null && j < vars.length; j++) {
               if(!AssetUtil.containsVariable(list, vars[j])) {
                  list.add(vars[j]);
               }
            }
         }
      }

      String str = (String) get("isVSIncluded");
      str = str == null ? "false" : str;
      boolean isVSIncluded = Boolean.valueOf(str).booleanValue();

      if(list.size() > 0) {
         UserVariable[] vars = new UserVariable[list.size()];
         list.toArray(vars);
         AssetUtil.validateAlias(vars);
         command.addCommand(new CollectConnectionVariablesCommand(vars, this));
      }
      else {
         AssetTreeModel model;
         Principal principal = getUser();

         if(!isVSIncluded) {
            AssetEntry.Selector selector = new AssetEntry.Selector(
               AssetEntry.Type.FOLDER, AssetEntry.Type.WORKSHEET,
               AssetEntry.Type.DATA, AssetEntry.Type.PHYSICAL);

            model = AssetEventUtil.refreshTree(engine, principal, this,
                                               selector);
         }
         else {
            model = AssetEventUtil.refreshTree(engine, principal, this,
                                               isServer());
         }

         command.addCommand(new RefreshTreeCommand(model));
      }
   }

   /**
    * It's parent entry may be collapsed.
    */
   public void fixEntries() {
      List<AssetEntry> entriesList = new ArrayList<>();

      for(int i = 0; entries != null && i < entries.length; i++) {
         fixEntries(entries[i], entriesList);
      }

      entries = new AssetEntry[entriesList.size()];
      entriesList.toArray(entries);
   }

   /**
    * It's parent entry may be collapsed.
    */
   private void fixEntries(AssetEntry entry, List entries) {
      if(!entries.contains(entry)) {
         entries.add(entry);
      }

      AssetEntry parent = entry.getParent();

      if(parent != null) {
         fixEntries(parent, entries);
      }
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
