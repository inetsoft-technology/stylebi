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
import inetsoft.report.composition.command.MessageCommand;
import inetsoft.report.composition.command.RefreshTreeCommand;
import inetsoft.sree.RepletRepository;
import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.util.XSessionService;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.sql.Timestamp;
import java.util.*;

/**
 * Change asset event.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class ChangeAssetEvent extends AssetRepositoryEvent {
   /**
    * Constructor.
    */
   public ChangeAssetEvent() {
      super();
   }

   /**
    * Constructor.
    * @param entry the specified asset entry.
    * @param parent parent asset entry.
    * @param event  refresh asset tree event.
    */
   public ChangeAssetEvent(AssetEntry entry, AssetEntry parent,
                           AssetEvent event) {
      put("entry", entry);
      put("parent", parent);
      put("event", event);
   }

   /**
    * Get the name of the asset event.
    * @return the name of the asset event.
    */
   @Override
   public String getName() {
      return Catalog.getCatalog().getString("Change Asset");
   }

   /**
    * Return true if the event will access storage heavily.
    */
   @Override
   public boolean isStorageEvent() {
      return true;
   }

   /**
    * Process change asset event.
    */
   @Override
   public void process(AssetRepository engine, AssetCommand command)
      throws Exception
   {
      AssetEntry parent = (AssetEntry) get("parent");
      Catalog catalog = Catalog.getCatalog();

      if(!parent.isFolder()) {
         command.addCommand(
            new MessageCommand(catalog.getString(
               "common.invalidFolder")));
         return;
      }

      AssetEntry entry = (AssetEntry) get("entry");

      if(entry != null && entries == null) {
         entries = new ArrayList();
         entries.add(entry);
      }

      // @by jasonguo, fix bug1255421825412, sort entries for deleting
      // viewsheet before snapshot.
      Collections.sort(entries, new EntriesComparator());
      Iterator it = entries.iterator();

      while(it.hasNext()) {
         entry = (AssetEntry) it.next();
         String path = parent.isRoot() ?
            entry.getName() : parent.getPath() + "/" + entry.getName();
         AssetEntry nentry = new AssetEntry(parent.getScope(), entry.getType(),
                                            path, parent.getUser());
         nentry.copyProperties(parent);
         nentry.setReportDataSource(entry.isReportDataSource());

         if(entry.getAlias() != null && !entry.getAlias().equals("")) {
            nentry.setAlias(entry.getAlias());
         }

         if(entry.getProperty("description") != null &&
            !entry.getProperty("description").equals(""))
         {
            nentry.setProperty("description", entry.getProperty("description"));
         }

         if(nentry.isAncestor(entry) || entry.isAncestor(nentry)) {
            MessageCommand mcmd =
               new MessageCommand(catalog.getString(
                  "common.invalidFolder"));
            command.addCommand(mcmd);
            return;
         }
         else if(nentry.equals(entry)) {
            MessageCommand mcmd =
               new MessageCommand(catalog.getString(
                  "common.sameFolder"));
            command.addCommand(mcmd);
            return;
         }

         if(!nentry.equals(entry) && getWorksheetEngine().isDuplicatedEntry(
            getAssetRepository(), nentry))
         {
            MessageCommand mcmd = new MessageCommand(
               Catalog.getCatalog().getString("common.duplicateName"),
               MessageCommand.ERROR);
            command.addCommand(mcmd);
            return;
         }

         // Moving between different scopes is not allowed for repository
         // folders. The same is true in the portal.
         if(entry.isRepositoryFolder() && entry.getScope() != parent.getScope()) {
            MessageCommand mcmd = new MessageCommand(
               Catalog.getCatalog().getString(
               "common.moveFoldersBetweenScopes"),
               MessageCommand.ERROR);
            command.addCommand(mcmd);
            return;
         }

         // log action
         String userName = SUtil.getUserName(getUser());
         String actionName = ActionRecord.ACTION_NAME_MOVE;
         String objectName = entry.getDescription();
         String objectType = AssetEventUtil.getObjectType(entry);
         Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
         ActionRecord actionRecord = new ActionRecord(userName, actionName, objectName,
               objectType, actionTimestamp, ActionRecord.ACTION_STATUS_FAILURE,
               null);

         try {
            if(entry.isRepositoryFolder() && engine instanceof RepletRepository)
            {
               String rpath = entry.getPath();

               if(entry.getScope() == AssetRepository.USER_SCOPE) {
                  rpath = Tool.MY_DASHBOARD + "/" + rpath;
               }

               path = parent.getPath();

               if(parent.getScope() == AssetRepository.USER_SCOPE) {
                  path = parent.isRoot() ? Tool.MY_DASHBOARD :
                     Tool.MY_DASHBOARD + "/" + path;
               }

               RepositoryEntry rentry =  new RepositoryEntry(rpath,
                  RepositoryEntry.FOLDER, entry.getUser());
               ((RepletRepository) engine).changeFolder(rentry, path, getUser());
            }
            else if(entry.isFolder()) {
               engine.changeFolder(entry, nentry, getUser(), isConfirmed());
            }
            else {
               engine.changeSheet(entry, nentry, getUser(), isConfirmed());
            }

            if(actionRecord != null) {
               actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_SUCCESS);
               actionRecord.setActionError(
                  "Target Entry: " + nentry.getDescription());
            }
         }
         catch(Exception ex) {
            if(ex instanceof ConfirmException) {
               actionRecord = null;
            }

            if(actionRecord != null) {
               actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
               actionRecord.setActionError(ex.getMessage() + ", Target Entry: " +
                  nentry.getDescription());
            }

            throw ex;
         }
         finally {
            if(actionRecord != null) {
               Audit.getInstance().auditAction(actionRecord, getUser());
            }
         }

         setConfirmed(false);
         it.remove();
      }

      RefreshTreeEvent event = (RefreshTreeEvent) get("event");
      event.fixEntries();
      AssetTreeModel model =
         AssetEventUtil.refreshTree(engine, getUser(), event, isServer());
      command.addCommand(new RefreshTreeCommand(model));
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
         entries = new ArrayList();
         NodeList nodes =  node.getChildNodes();

         for(int i = 0; i < nodes.getLength(); i++) {
            entries.add(AssetEntry.createAssetEntry((Element) nodes.item(i)));
         }
      }
      else {
         AssetEntry entry = (AssetEntry) get("entry");

         if(entry != null) {
            entries = new ArrayList();
            entries.add(entry);
         }
      }
   }

   /**
    * Write the contents of this object.
    * @param writer the output stream to which to write the XML data.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(entries != null) {
         writer.println("<entries>");

         for(AssetEntry entry : entries) {
            entry.writeXML(writer);
         }

         writer.println("</entries>");
      }
   }

   private List<AssetEntry> entries;

   /**
    * Sort entries.
    */
   private static class EntriesComparator implements Comparator {
      @Override
      public int compare(Object v1, Object v2) {
         int type1 = ((AssetEntry) v1).getType().id();
         int type2 = ((AssetEntry) v2).getType().id();

         return type1 - type2;
      }
   }
}
