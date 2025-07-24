/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.uql.asset;

import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.sree.internal.SUtil;
import inetsoft.uql.erm.vpm.VpmProcessor;
import inetsoft.util.gui.ObjectInfo;
import inetsoft.mv.*;
import inetsoft.report.LibManager;
import inetsoft.report.ReportSheet;
import inetsoft.report.style.XTableStyle;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.sync.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.util.SQLTypes;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.util.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.*;
import inetsoft.util.log.LogLevel;
import inetsoft.web.RecycleUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXParseException;

import java.lang.ref.WeakReference;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;

/**
 * Abstract asset engine, implements most methods defined in AssetRepository.
 *
 * @author InetSoft Technology Corp
 * @version 8.0
 */
public abstract class AbstractAssetEngine implements AssetRepository, AutoCloseable {
   /**
    * Constructor.
    */
   protected AbstractAssetEngine() {
      super();
   }

   /**
    * Constructor.
    */
   public AbstractAssetEngine(int[] scopes, IndexedStorage istore) {
      this();
      this.scopes = scopes;
      Arrays.sort(this.scopes);
      this.istore = istore;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setParent(AssetRepository engine) {
      this.parent = engine == null ? null : new WeakReference<>(engine);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public AssetRepository getParent() {
      return parent == null ? null : parent.get();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean supportsScope(int scope) {
      return scope == QUERY_SCOPE || scope == COMPONENT_SCOPE || Arrays.binarySearch(scopes, scope) >= 0;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public AssetEntry[] getEntries(AssetEntry entry, Principal user,
                                  ResourceAction permission) throws Exception
   {
      return getEntries(entry, user, permission, new AssetEntry.Selector(
         AssetEntry.Type.FOLDER, AssetEntry.Type.WORKSHEET,
         AssetEntry.Type.VIEWSHEET, AssetEntry.Type.DATA,
         AssetEntry.Type.PHYSICAL));
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public AssetEntry[] getEntries(AssetEntry entry, Principal user,
                                  ResourceAction permission,
                                  AssetEntry.Selector selector)
      throws Exception
   {
      if(!entry.isWorksheet() && !entry.isFolder() || !supportsScope(entry.getScope()) ||
         !entry.isValid())
      {
         throw new MessageException(catalog.getString(
            "common.invalidEntry", entry));
      }

      if(entry.getScope() == QUERY_SCOPE) {
         if(selector.matches(AssetEntry.Type.DATA)) {
            AssetEntry[] entries = getQueryEntries(entry, selector, user);
            return entries;
         }
         else {
            return new AssetEntry[0];
         }
      }
      else if(entry.getScope() == COMPONENT_SCOPE) {
         if(entry.isTableStyleFolder()) {
            return getTableStyleEntries(entry, permission, user);
         }
         else if(entry.isScriptFolder()) {
            return getScriptEntries(entry, permission, user);
         }
         else if(entry.isLibraryFolder()) {
            return getLibraryEntries(entry, permission, user);
         }
      }
      else if(entry.getScope() == AssetRepository.REPORT_SCOPE) {
         if(selector.matches(AssetEntry.Type.DATA) ||
            selector.matches(AssetEntry.Type.WORKSHEET))
         {
            return getReportEntries(entry, user, permission, selector);
         }
      }
      else if(entry.getScope() == AssetRepository.GLOBAL_SCOPE ||
         entry.getScope() == AssetRepository.USER_SCOPE) {

         if(entry.isWorksheet()) {
            return getWSColumnEntries(entry, user, permission, selector);
         }

         return getWorksheetEntries(entry, user, permission, selector);
      }

      return new AssetEntry[0];
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public AssetEntry[] getAllEntries(final AssetEntry entry, final Principal user,
                                     final ResourceAction action,
                                     final AssetEntry.Selector selector)
      throws Exception
   {
      final IndexedStorage storage = getStorage(entry);
      final ArrayList<AssetEntry> entryList = new ArrayList<>();
      final boolean userScopeOnly =
         entry.getScope() == AssetRepository.USER_SCOPE;

      long modified = istore.lastModified();

      if(modified != lastMod) {
         foldermap.clear();
      }

      IndexedStorage.Filter filter = key -> {
         AssetEntry entry0 = AssetEntry.createAssetEntry(key);

         try {
            if(entry0 == null || entry0.isRoot()) {
               return false;
            }

            //signifies that the asset has the type being searched for
            boolean sameType = selector.isEqual(entry0.getType());
            // signifies that the asset has the same scope
            boolean sameScope = entry.getScope() == entry0.getScope();
            // signifies that the asset lives directly in this folder
            boolean samePath = entry0.getParentPath().equals(entry.getPath());
            // signifies that the asset lives somewhere under this folder
            boolean sameSubPath =
               entry0.getParentPath().startsWith(entry.getPath() + "/");

            boolean foundInPath = entry.isRoot() ||
                                  (sameSubPath || samePath );

            if(userScopeOnly) {
               if(sameScope && sameType && entry.getUser() != null &&
                  entry.getUser().equals(entry0.getUser()) && foundInPath)
               {
                  copyEntryProperty(entry0, storage);
                  entryList.add(entry0);
                  return true;
               }
            }
            else if(sameType && sameScope && foundInPath &&
               checkAssetPermission0(user, entry0, action))
            {
               copyEntryProperty(entry0, storage);
               entryList.add(entry0);
               return true;
            }
         }
         catch(Exception acceptException) {
            LOG.debug("Error occurred while building " +
               "entry list for: " + entry + " at " + entry0, acceptException);
         }

         return false;
      };

      storage.getKeys(filter, entry.getOrgID());
      return entryList.toArray(new AssetEntry[0]);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void copyEntryProperty(AssetEntry entry, IndexedStorage storage) {
      if(entry == null) {
         return;
      }

      AssetEntry pentry = entry.getParent();

      if(pentry == null) {
         return;
      }

      String pidentifier = getEntryIdentifier(pentry);
      AssetFolder pfolder = foldermap.get(pidentifier);

      if(pfolder == null) {
         pfolder = getParentFolder(entry, storage);

         if(pfolder != null) {
            foldermap.put(pidentifier, pfolder);
         }
      }

      if(pfolder != null && pfolder.containsEntry(entry)) {
         AssetEntry oldEntry = pfolder.getEntry(entry);

         if(oldEntry.getAlias() != null) {
            entry.setAlias(oldEntry.getAlias());
         }

         for(String key : oldEntry.getPropertyKeys()) {
            if(!AssetEntry.isIgnoredProperty(key) && entry.getProperty(key) == null &&
               (Tool.equals(entry.getProperty("viewer"), "true") ||
               !Tool.equals(key, "_device_display_width")))
            {
               entry.setProperty(key, oldEntry.getProperty(key));
            }
         }
      }
   }

   /**
    * Get the report scope object entries of a report.
    *
    * @param entry    the specified folder entry.
    * @param selector the specified selector.
    * @param user     the specified user.
    * @return the sub entries of a folder.
    */
   private AssetEntry[] getReportEntries(AssetEntry entry, Principal user,
                                         ResourceAction permission,
                                         AssetEntry.Selector selector)
   {
      AssetEntry[] entries = new AssetEntry[0];

      // add local queries, parameter sheet, worksheets and worksheet folders
      if(entry.isRoot()) {
         List<AssetEntry> list = new ArrayList<>();
         entries = list.toArray(new AssetEntry[0]);
      }
      // add worksheet to workshet folder
      else if(entry.isFolder()) {
         try {
            if(getStorage(entry) != null) {
               AssetEntry entry2 = entry;
               entry2.copyProperties(entry);
               entries = getWorksheetEntries(entry2, user, permission, selector);
            }
         }
         catch(Exception e) {
            LOG.error("Failed to list contents of worksheet folder: " + entry, e);
         }
      }
      else {
         throw new MessageException(catalog.getString(
            "common.invalidEntry", entry));
      }

      return entries;
   }

   /**
    * Get the worksheet entries of a folder.
    *
    * @param entry    the specified folder entry.
    * @param selector the specified selector.
    * @param user     the specified user.
    * @return the sub entries of a folder.
    */
   public AssetEntry[] getWorksheetEntries(AssetEntry entry, Principal user,
                                           ResourceAction permission,
                                           AssetEntry.Selector selector)
   {
      IndexedStorage storage = null;

      try {
         storage = getReportStorage(entry);
      }
      catch(Exception ex) {
         LOG.error("Failed to get report storage for " + entry, ex);
      }

      AssetEntry[] entries = new AssetEntry[0];

      try {
         entries = getWorksheetEntries(entry, user, permission, selector, storage, entries);
      }
      catch(Exception ex) {
         LOG.error("Failed to list worksheets in folder: " + entry, ex);
      }
      finally {
         closeStorages();
      }

      return entries;
   }

   /**
    * Get the worksheet entries of a folder.
    *
    * @param entry    the specified folder entry.
    * @param selector the specified selector.
    * @param user     the specified user.
    * @param storage     the specified report storage.
    * @return the sub entries of a folder.
    */
   public AssetEntry[] getWorksheetEntries(AssetEntry entry, Principal user,
                                           ResourceAction permission,
                                           AssetEntry.Selector selector,
                                           IndexedStorage storage,
                                           AssetEntry[] entries)
      throws Exception
   {
      String identifier = entry.toIdentifier();
      boolean oneoff = "true".equals(entry.getProperty("__oneoff__"));

      if(storage == null) {
         throw new MessageException(catalog.getString(
            "common.invalidStorage", entry));
      }

      String orgID = OrganizationManager.getInstance().getCurrentOrgID();
      String hostOrgID = Organization.getDefaultOrganizationID();

      //pass default org global repository if inside global visible folder
      if(SUtil.isDefaultVSGloballyVisible(user) &&
         Tool.equals(entry.getOrgID(), hostOrgID) &&
         Tool.equals(entry.getPath(), OrganizationManager.getGlobalDefOrgFolderName()))
      {
         identifier = "1^4097^__NULL__^/^host-org";
         orgID = hostOrgID;
      }

      XMLSerializable obj = storage.getXMLSerializable(identifier, null, orgID);

      //catch default org folder by checking as host org
      if(obj == null && SUtil.isDefaultVSGloballyVisible(user) &&
         Tool.equals(entry.getOrgID(), hostOrgID) &&
         !Tool.equals(identifier, "1^4097^__NULL__^/^"+Organization.getSelfOrganizationID()) &&
         !Tool.equals(identifier, "1^1^__NULL__^/^"+Organization.getSelfOrganizationID()))
      {
         obj = storage.getXMLSerializable(identifier, null, hostOrgID);
      }

      if(obj instanceof AssetFolder) {
         AssetFolder folder = (AssetFolder) obj;
         entries = folder.getEntries();

         List<AssetEntry> list = new ArrayList<>();
         Principal user0 = ThreadContext.getContextPrincipal();
         Catalog ucata = Catalog.getCatalog(user0, Catalog.REPORT);

         for(AssetEntry childEntry : entries) {
            AssetEntry.Type type = childEntry.getType();

            if(selector.matchesExactly(type) && checkAssetPermission0(user, childEntry, permission)) {
               try {
                  if(!oneoff) {
                     if(childEntry.isVSSnapshot()) {
                        VSSnapshot snap = (VSSnapshot) getSheet(
                           childEntry, user, false, AssetContent.CONTEXT);

                        if(snap != null) {
                           childEntry.setProperty(AssetEntry.SHEET_DESCRIPTION,
                              snap.getDescription());
                           String str = childEntry.getAlias() != null ?
                              ucata.getString(childEntry.getAlias()) :
                              ucata.getString(childEntry.getName());
                           childEntry.setProperty("localStr", str);
                           childEntry.setProperty("Tooltip", snap.getSnapshotDescription());
                        }
                     }
                     else if(childEntry.isViewsheet()) {
                        Viewsheet vs = (Viewsheet) getSheet(
                           childEntry, user, false, AssetContent.CONTEXT);

                        if(vs != null) {
                           ViewsheetInfo vinfo = vs.getViewsheetInfo();
                           String onReport = vinfo.isOnReport() + "";
                           childEntry.setProperty("onReport", onReport);
                           String str = childEntry.getAlias() != null ?
                              ucata.getString(childEntry.getAlias()) :
                              ucata.getString(childEntry.getName());
                           childEntry.setProperty("localStr", str);
                           childEntry.setProperty("Tooltip", vs.getDescription());
                        }
                     }
                     else if(childEntry.isFolder()) {
                        String str = childEntry.getAlias() != null ?
                           childEntry.getAlias() : childEntry.getName();

                        // built-in folder should be localizable using main catalog too
                        String localStr = "Built-in Admin Reports".equals(str) ?
                           Catalog.getCatalog(user0).getString(str) : ucata.getString(str);
                        childEntry.setProperty("localStr", localStr);
                     }
                     else if(childEntry.isWorksheet()) {
                        String str = childEntry.getAlias() != null ?
                           ucata.getString(childEntry.getAlias()) :
                           ucata.getString(childEntry.getName());
                        childEntry.setProperty("localStr", str);
                        childEntry.setProperty("mainType", "worksheet");
                        childEntry.setProperty("Tooltip", childEntry.getProperty("description"));
                     }
                  }
               }
               catch(Exception ex) {
                  LOG.error("Failed to load sheet: " + entry);
               }

               // needs to copy properties to sub entries, so that we
               // will not lose very important information like report id
               childEntry.copyProperties(entry);
               list.add(childEntry);
            }
            else if(childEntry.getType() == AssetEntry.Type.FOLDER && checkAssetPermission0(user, childEntry, permission)) {
               return getWorksheetEntries(childEntry, user, permission, selector, storage, entries);
            }
         }

         list.sort(Comparator.naturalOrder());
         entries = list.toArray(new AssetEntry[0]);
      }

      return entries;
   }


   public AssetEntry[] getWSColumnEntries(AssetEntry entry, Principal user,
                                           ResourceAction permission,
                                           AssetEntry.Selector selector) {
      String identifier = entry.toIdentifier();
      IndexedStorage storage = null;

      try {
         storage = getReportStorage(entry);
      }
      catch(Exception ex) {
         LOG.error("Failed to get report storage for " + entry, ex);
      }

      AssetEntry[] entries = new AssetEntry[0];

      try {
         XMLSerializable obj = storage.getXMLSerializable(identifier, null);

         if(obj instanceof Worksheet) {
            Worksheet ws = (Worksheet) obj;

            Assembly ass = ws.getPrimaryAssembly();

            if(!(ass instanceof TableAssembly)) {
               return entries;
            }

            TableAssembly assembly = (TableAssembly) ass;
            ColumnSelection columns = assembly.getColumnSelection(true);
            List<ColumnRef> list = new ArrayList<>();
            entries = new AssetEntry[columns.getAttributeCount()];

            for(int i = 0; i < columns.getAttributeCount(); i++) {
               ColumnRef cref = (ColumnRef) columns.getAttribute(i);

               if(!(cref instanceof CalculateRef)) {
                  list.add(cref);
               }
            }

            list.sort(new VSUtil.DataRefComparator());

            for(int i = 0; i < list.size(); i++) {
               ColumnRef column = list.get(i);
               DataRef ref = column.getDataRef();
               String name = column.getAlias();
               name = name == null || name.length() == 0 ? column.getAttribute() : name;
               AssetEntry tempEntry = new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.COLUMN,
                       "/baseWorksheet/" + assembly.getName() + "/" + name,
                       IdentityID.getIdentityIDFromKey(user.getName()));
               tempEntry.setProperty("dtype", column.getDataType());
               tempEntry.setProperty("assembly", assembly.getName());
               tempEntry.setProperty("attribute", name);
               tempEntry.setProperty("source", VSEventUtil.BASE_WORKSHEET);
               tempEntry.setProperty("ws", identifier);
               tempEntry.setProperty("type", XSourceInfo.ASSET + "");
               tempEntry.setProperty("embedded",
                  VSEventUtil.isEmbeddedDataSource(assembly, true, true) + "");
               tempEntry.setProperty("expression", (ref instanceof ExpressionRef) + "");
               tempEntry.setProperty("Tooltip", column.getDescription());

               if(assembly instanceof CubeTableAssembly && (column instanceof CalculateRef)) {
                  tempEntry.setProperty("wsCubeCalc", "true");
               }

               entries[i] = tempEntry;
            }
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to list worksheets in folder: " + entry, ex);
      }
      finally {
         closeStorages();
      }

      return entries;
   }
   /**
    * {@inheritDoc}
    */
   @Override
   public Object getSession() throws Exception {
      XRepository rep = XFactory.getRepository();
      return rep.bind(System.getProperty("user.name"));
   }

   /**
    * Get sorted children names.
    */
   private String[] getSortedChildren(XNode node) {
      return getSortedChildren(node, false);
   }

   private String[] getSortedChildren(XNode node, boolean sortNameByCase) {
      return getSortedChildren(node, true, sortNameByCase);
   }

   /**
    * Get sorted children names.
    */
   private String[] getSortedChildren(XNode node, boolean sort, boolean sortNameByCase) {
      if(node == null) {
         return new String[0];
      }

      String[] cnames = new String[node.getChildCount()];

      for(int i = 0; i < cnames.length; i++) {
         cnames[i] = node.getChild(i).getName();
      }

      if(sort) {
         Arrays.sort(cnames, new QueryEntriesComparator(sortNameByCase));
      }

      return cnames;
   }

   /**
    * Get the sub physical entries of an asset entry.
    *
    * @param entry    the specified asset entry.
    * @param selector the specified selector.
    * @param user     the specified user.
    * @return the sub entries of the asset entry.
    */
   private AssetEntry[] getPhysicalEntries(AssetEntry entry, AssetEntry.Selector selector,
                                           Principal user)
   {
      if(!selector.matches(AssetEntry.Type.PHYSICAL)) {
         return new AssetEntry[0];
      }

      if(entry.getType() != AssetEntry.Type.DATA_SOURCE &&
         entry.getType() != AssetEntry.Type.PHYSICAL_FOLDER &&
         entry.getType() != AssetEntry.Type.PHYSICAL_TABLE) {
         return new AssetEntry[0];
      }

      boolean portalData = "true".equals(entry.getProperty(XUtil.PORTAL_DATA));

      // check physical table permission
      if(!portalData && entry.getType() == AssetEntry.Type.DATA_SOURCE) {
         if(!checkPermission(
            user, ResourceType.PHYSICAL_TABLE, "*", EnumSet.of(ResourceAction.ACCESS)))
         {
            return new AssetEntry[0];
         }
      }

      try {
         String source = entry.getProperty("prefix");
         XRepository rep = XFactory.getRepository();
         XDataSource xds = rep.getDataSource(source);

         if(!(xds instanceof JDBCDataSource)) {
            return new AssetEntry[0];
         }

         if(((JDBCDataSource) xds).getDatabaseType() == JDBCDataSource.JDBC_HIVE &&
            !Drivers.getInstance().isHiveEnabled())
         {
            return new AssetEntry[0];
         }

         String additional = entry.getProperty("additional");

         // for portal data gui, load additional nodes for additional source.
         if(!StringUtils.isEmpty(additional) &&
            !XUtil.OUTER_MOSE_LAYER_DATABASE.equals(additional) && portalData &&
            !Tool.equals(additional, xds.getName()))
         {
            xds = ((JDBCDataSource) xds).getDataSource(additional);

            if(entry.isDataSource()) {
               String path = xds.getFullName();
               AssetEntry originalEntry = entry;
               entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
                  AssetEntry.Type.DATA_SOURCE, path, null);
               entry.setProperty(XUtil.PORTAL_DATA, "" + portalData);
               entry.setProperty("skipSort", originalEntry.getProperty("skipSort"));
            }
         }

         xds.setFromPortal(portalData);
         SQLTypes stypes = SQLTypes.getSQLTypes((JDBCDataSource) xds);
         Object session = getSession();

         XNode node = findNode(entry, xds, rep, session);

         if(node == null) {
            return new AssetEntry[0];
         }

         List<AssetEntry> list = new ArrayList<>();

         if(entry.getType() == AssetEntry.Type.DATA_SOURCE ||
            entry.getType() == AssetEntry.Type.PHYSICAL_FOLDER)
         {
            boolean skipSort = "true".equals(entry.getProperty("skipSort"));
            boolean sortNameByCase = "true".equals(entry.getProperty("sortNameByCase"));
            String[] cnames = getSortedChildren(node, !skipSort, sortNameByCase);

            for(String cname : cnames) {
               XNode snode = node.getChild(cname);
               AssetEntry sentry = createPhysicalEntry(snode, user, xds);

               if(sentry != null && !list.contains(sentry)) {
                  sentry.setProperty("prefix", source);
                  list.add(sentry);
               }
            }
         }
         else if(entry.getType() == AssetEntry.Type.PHYSICAL_TABLE) {
            XNode meta = new XNode();
            meta.setAttribute("type", "DBPROPERTIES");

            if(!StringUtils.isEmpty(entry.getProperty("additional"))) {
               meta.setAttribute("additional", entry.getProperty("additional"));
            }

            meta = rep.getMetaData(session, xds, meta, true, null);

            String source0 = entry.getProperty("source");
            XNode table = stypes.getQualifiedTableNode(
               source0, "true".equals(meta.getAttribute("hasCatalog")),
               "true".equals(meta.getAttribute("hasSchema")),
               (String) meta.getAttribute("catalogSep"), (JDBCDataSource) xds,
               (String) node.getAttribute("catalog"),
               (String) node.getAttribute("schema"));

            String type = (String) node.getAttribute("type");

            if(type != null) {
               table.setAttribute("type", type);
            }

            boolean catalog = "true".equals(node.getAttribute("supportCatalog"));
            table.setAttribute("supportCatalog", catalog + "");

            if(!StringUtils.isEmpty(entry.getProperty("additional"))) {
               table.setAttribute("additional", entry.getProperty("additional"));
            }

            try {
               node = rep.getMetaData(session, xds, table, true, null);
            }
            catch(Exception ex) {
               LOG.debug("Failed to get meta-data for table: " + entry, ex);
               return new AssetEntry[0];
            }

            if(node == null) {
               return new AssetEntry[0];
            }

            node = node.getChild("Result");
            String ppath = entry.getPath();
            String parr = entry.getProperty(AssetEntry.PATH_ARRAY);

            if(parr == null) {
               throw new RuntimeException("Invalid entry found: " + entry);
            }

            String folderDesc = entry.getProperty("folder_description");
            boolean loadAllCols = "true".equals(entry.getProperty("ignoreVpm"));
            String[] cnames = getSortedChildren(node);
            BiFunction<String, String, Boolean> hiddens = null;

            if(!loadAllCols) {
               String name = xds.getFullName();
               hiddens = VpmProcessor.getInstance().getHiddenColumnsSelector(
                  new String[] { source0}, new String[0], name, null, null, user);
            }

            for(String name : cnames) {
               if(hiddens != null && hiddens.apply(source0, name)) {
                  continue;
               }

               XTypeNode tnode = (XTypeNode) node.getChild(name);
               String tparr = parr + "^_^" + name;
               AssetEntry sentry = new AssetEntry(
                  QUERY_SCOPE, AssetEntry.Type.PHYSICAL_COLUMN, ppath + "/" + name,
                  user == null ? null : IdentityID.getIdentityIDFromKey(user.getName()));
               sentry.setProperty(AssetEntry.PATH_ARRAY, tparr);
               sentry.setProperty("prefix", entry.getProperty("prefix"));
               sentry.setProperty("source", entry.getProperty("source"));
               sentry.setProperty("source_with_no_quote", entry.getProperty("source_with_no_quote"));
               sentry.setProperty("attribute", name);
               sentry.setProperty("qualifiedAttribute",
                       stypes.getQualifiedColumnName(table, name, (JDBCDataSource) xds));
               sentry.setProperty("type", XSourceInfo.PHYSICAL_TABLE + "");
               sentry.setProperty("dtype", tnode.getType());
               sentry.setProperty("sqltype", tnode.getSqlType());
               sentry.setProperty(XSourceInfo.CATALOG, entry.getProperty(XSourceInfo.CATALOG));
               sentry.setProperty(XSourceInfo.SCHEMA, entry.getProperty(XSourceInfo.SCHEMA));
               sentry.setProperty(XSourceInfo.TABLE_TYPE,
                                  entry.getProperty(XSourceInfo.TABLE_TYPE));
               sentry.setProperty("folder_description", folderDesc);
               list.add(sentry);
            }
         }

         return list.toArray(new AssetEntry[0]);
      }
      catch(Exception ex) {
         LOG.warn("Failed to get sub-entries for " + entry, ex);
         return new AssetEntry[0];
      }
   }

   /**
    * Find the associated node of an entry.
    *
    * @param entry   the specified asset entry.
    * @param xds     the data source that contains the node.
    * @param session the current data session.
    *
    * @return the associated node of the asset entry, <tt>null</tt> not found.
    */
   private XNode findNode(AssetEntry entry, XDataSource xds, XRepository rep,
                          Object session) throws Exception
   {
      if(entry.getType() != AssetEntry.Type.DATA_SOURCE &&
         entry.getType() != AssetEntry.Type.PHYSICAL_FOLDER &&
         entry.getType() != AssetEntry.Type.PHYSICAL_TABLE)
      {
         return null;
      }

      AssetEntry oEntry = entry;
      List<String> list = new ArrayList<>();

      while(!entry.isRoot()) {
         list.add(0, entry.getName());
         entry = entry.getParent();
      }

      String[] names = list.toArray(new String[0]);

      if(names.length == 0) {
         LOG.debug("Entry has no path");
         return null;
      }

      if(!(xds instanceof JDBCDataSource)) {
         // sanity check
         LOG.debug("Only JDBC data sources support physical entries");
         return null;
      }

      JDBCDataSource jdbc = (JDBCDataSource) xds;
      String fullName = xds.getFullName();
      String[] xdsPath = fullName.split("/");

      // When the entry represents the data source, names could be [datasource]
      // or [folder, datasource]. For entries that are children of the data
      // source, names could be [datasource, child, ...] or
      // [folder/datasource, child, ...].
      if(xdsPath.length == 1 && !names[0].equalsIgnoreCase(fullName) ||
         xdsPath.length > 1 && !names[0].equalsIgnoreCase(fullName) &&
         !Arrays.equals(names, xdsPath))
      {
         // name of entry could be the name of the child data source, check if
         // that matches
         if((xds = jdbc.getDataSource(names[0])) == null) {
            LOG.debug("Entry does not match data source: " + list + " (" + fullName + ")");
            return null;
         }

         return findNode(oEntry, xds, rep, session);
      }

      XNode root = new XNode();
      root.setAttribute("type", "TABLETYPES");

      if(!StringUtils.isEmpty(oEntry.getProperty("additional"))) {
         root.setAttribute("additional", oEntry.getProperty("additional"));
      }

      root = rep.getMetaData(session, xds, root, true, null);

      XNode node;

      if(Arrays.equals(xdsPath, names)) {
         node = root;
      }
      else {
         XNode parent = root.getChild(names[1]);

         if(parent == null) {
            LOG.debug("No meta data for entry " + list);
            return null;
         }

         XNode children = new XNode();
         children.setAttribute("type", "SCHEMAS");

         if(!StringUtils.isEmpty(oEntry.getProperty("additional"))) {
            children.setAttribute("additional", oEntry.getProperty("additional"));
         }

         children = rep.getMetaData(session, xds, children, true, null);
         copyChildren(children, parent);

         node = parent;
         boolean tablesLoaded = false;

         for(int i = 2; i < names.length; i++) {
            if(parent.getChildCount() == 0) {
               tablesLoaded = true;
               addTables(parent, names[1], rep, session, xds);
            }

            node = parent.getChild(names[i]);

            if(node == null) {
               LOG.debug("No meta data for entry " + list);
               return null;
            }

            parent = node;
         }

         if(node != null && !StringUtils.isEmpty(oEntry.getProperty("additional"))) {
            node.setAttribute("additional", oEntry.getProperty("additional"));
         }

         if(node.getChildCount() == 0) {
            if(tablesLoaded) {
               parent = node.getParent();
               int index = parent.getChildIndex(node);
               node = getColumns(node, rep, session, xds);
               parent.setChild(index, node);
            }
            else {
               addTables(node, names[1], rep, session, xds);
            }
         }
      }

      return node;
   }

   /**
    * Copies the children from one node to another.
    *
    * @param from the source node.
    * @param to   the target node.
    */
   private void copyChildren(XNode from, XNode to) {
      for(int i = 0; i < from.getChildCount(); i++) {
         to.addChild(from.getChild(i), true, false);
      }
   }

   /**
    * Copies an attribute from one node to another, if it exists.
    *
    * @param from the source node.
    * @param to   the target node.
    * @param name the attribute name.
    */
   private void copyAttribute(XNode from, XNode to, String name) {
      Object value = from.getAttribute(name);

      if(value != null) {
         to.setAttribute(name, value);
      }
   }

   /**
    * Adds table meta-data nodes to the specified parent node.
    *
    * @param parent  the parent node.
    * @param type    the table type.
    * @param rep     the data repository.
    * @param session the data session.
    * @param xds     the data source.
    *
    * @throws Exception if an unhandled error occurs.
    */
   private void addTables(XNode parent, String type, XRepository rep,
                          Object session, XDataSource xds)
      throws Exception
   {
      XNode children = new XNode();
      children.setAttribute("type", "SCHEMATABLES_" + type);
      children.setAttribute("tableType", type);

      copyAttribute(parent, children, "supportCatalog");
      copyAttribute(parent, children, "catalog");
      copyAttribute(parent, children, "catalogSep");
      copyAttribute(parent, children, "schema");
      copyAttribute(parent, children, "additional");

      children = rep.getMetaData(session, xds, children, true, null);

      copyChildren(children, parent);
   }

   /**
    * Gets column meta-data for the specified table node.
    *
    * @param parent  the parent node.
    * @param rep     the data repository.
    * @param session the data session.
    * @param xds     the data source.
    *
    * @return the table meta-data node.
    *
    * @throws Exception if an unhandled error occurs.
    */
   private XNode getColumns(XNode parent, XRepository rep, Object session,
                            XDataSource xds) throws Exception
   {
      XNode children = new XNode(parent.getName());

      copyAttribute(parent, children, "supportCatalog");
      copyAttribute(parent, children, "catalog");
      copyAttribute(parent, children, "catalogSep");
      copyAttribute(parent, children, "schema");

      return rep.getMetaData(session, xds, children, true, null);
   }

   /**
    * Create a physical asset entry according to an xnode as metadata.
    *
    * @param node the specified xnode.
    * @param user the specified user.
    * @param xds  the specified jdbc data source.
    * @return the created xnode if any, <tt>null</tt> otherwise.
    */
   @SuppressWarnings("WeakerAccess")
   protected AssetEntry createPhysicalEntry(XNode node, Principal user,
                                            XDataSource xds)
   {
      String name = node.getName();

      // do not support procedure
      if(name.equalsIgnoreCase("PROCEDURE")) {
         return null;
      }

      AssetEntry.Type type = node.getAttribute("type") == null ?
         AssetEntry.Type.PHYSICAL_FOLDER : AssetEntry.Type.PHYSICAL_TABLE;
      XNode pnode = node.getParent();

      if(pnode == null) {
         type = AssetEntry.Type.DATA_SOURCE;
      }

      AssetEntry pentry = pnode == null ? null : createPhysicalEntry(pnode, user, xds);
      String path = pentry == null ? name : pentry.getPath() + "/" + name;
      String parr = pentry == null ? null : pentry.getProperty(AssetEntry.PATH_ARRAY);

      if(pentry != null && parr == null) {
         throw new RuntimeException("Invalid entry found: " + pentry);
      }

      parr = parr == null ? name : parr + "^_^" + name;
      AssetEntry entry = new AssetEntry(QUERY_SCOPE, type, path,
         user == null ? null : IdentityID.getIdentityIDFromKey(user.getName()));
      entry.setProperty(AssetEntry.PATH_ARRAY, parr);
      String catalog = (String) node.getAttribute("catalog");
      String schema = (String) node.getAttribute("schema");
      entry.setProperty(XSourceInfo.CATALOG, catalog);
      entry.setProperty(XSourceInfo.SCHEMA, schema);
      entry.setProperty("type", XSourceInfo.PHYSICAL_TABLE + "");
      entry.setProperty("entity", name);
      entry.setProperty("supportCatalog", node.getAttribute("supportCatalog") + "");

      String folderDesc =
         pentry == null ? null : pentry.getProperty("folder_description");

      if(folderDesc != null) {
         entry.setProperty("folder_description", folderDesc);
      }

      if(type == AssetEntry.Type.PHYSICAL_TABLE) {
         if(xds.getDomainType() == XDataSource.DOMAIN_ORACLE ||
            xds.getDomainType() == XDataSource.DOMAIN_DB2) {
            entry.setProperty("folder_description",
               node.getParent().getParent().getName());
         }

         String qname = SQLTypes.getSQLTypes((JDBCDataSource) xds).
            getQualifiedName(node, (JDBCDataSource) xds);
         entry.setProperty("source", qname);
         Object val = node.getAttribute("fixquote");
         node.setAttribute("fixquote", "false");
         String oname = SQLTypes.getSQLTypes((JDBCDataSource) xds).
            getQualifiedName(node, (JDBCDataSource) xds);
         entry.setProperty("source_with_no_quote", oname);
         node.setAttribute("fixquote", val);
         entry.setProperty(XSourceInfo.TABLE_TYPE,
            (String) node.getAttribute("type"));
         String tip = Catalog.getCatalog().getString(
            entry.getParent().getName()) + ": " + qname + "[" + xds + "]";
         entry.setProperty("Tooltip", tip);
         XNode root = node;

         while(root.getParent() != null) {
            root = root.getParent();
         }

         entry.setProperty("hasSchema", Tool.toString(root.getAttribute("hasSchema")));
         entry.setProperty("defaultSchema", Tool.toString(root.getAttribute("defaultSchema")));
         entry.setProperty("supportCatalog", Tool.toString(root.getAttribute("supportCatalog")));
         entry.setProperty("hasCatalog", Tool.toString(root.getAttribute("hasCatalog")));
      }
      else if(type == AssetEntry.Type.PHYSICAL_FOLDER) {
         entry.setProperty("folder_description", name);
      }

      return entry;
   }

   private AssetEntry[] getLibraryEntries(AssetEntry entry, ResourceAction action, Principal user) {
      AssetEntry[] entries;
      List<AssetEntry> list = new ArrayList<>();

      if(checkPermission(user, ResourceType.TABLE_STYLE_LIBRARY, "*", EnumSet.of(ResourceAction.READ))) {
         AssetEntry entryStyle = new AssetEntry(
            AssetRepository.COMPONENT_SCOPE, AssetEntry.Type.TABLE_STYLE_FOLDER,
            "/" + TABLE_STYLE, user == null ? null : IdentityID.getIdentityIDFromKey(user.getName()));
         list.add(entryStyle);
      }

      if(checkPermission(user, ResourceType.SCRIPT_LIBRARY, "*", EnumSet.of(ResourceAction.READ))) {
         AssetEntry entryScript = new AssetEntry(
            AssetRepository.COMPONENT_SCOPE, AssetEntry.Type.SCRIPT_FOLDER,
            "/" + SCRIPT, user == null ? null : IdentityID.getIdentityIDFromKey(user.getName()));
         list.add(entryScript);
      }

      entries = new AssetEntry[list.size()];
      list.toArray(entries);
      return entries;
   }
   private AssetEntry[] getTableStyleEntries(AssetEntry entry, ResourceAction permission, Principal user) {
      AssetEntry[] entries;
      LibManager libManager = LibManager.getManager();
      List<AssetEntry> list = new ArrayList<>();
      String folder = entry.getProperty("folder");
      folder = Tool.isEmptyString(folder) ? null : folder;
      String[] folders = libManager.getTableStyleFolders(folder);

      for(String styleFolder : folders) {
         if(!checkPermission(user, ResourceType.TABLE_STYLE, styleFolder, EnumSet.of(permission))) {
            continue;
         }

         int idx = styleFolder.lastIndexOf(LibManager.SEPARATOR);
         String name = idx < 0 ? styleFolder : styleFolder.substring(idx + 1);
         String path = folder == null ?
            TABLE_STYLE + "/" + name : entry.getPath() + "/" + name;
         String parr = folder == null ?
            TABLE_STYLE + "^_^" + name :
            entry.getProperty(AssetEntry.PATH_ARRAY) + "^_^" + name;
         AssetEntry entry2 = new AssetEntry(AssetRepository.COMPONENT_SCOPE,
            AssetEntry.Type.TABLE_STYLE_FOLDER, path, null);
         entry2.setProperty(AssetEntry.PATH_ARRAY, parr);
         entry2.setProperty("folder", styleFolder);
         entry2.setProperty("source", name);
         list.add(entry2);
      }

      list.sort(Comparator.naturalOrder());

      XTableStyle[] tstyles = libManager.getTableStyles(folder);
      ArrayList<AssetEntry> styleList = new ArrayList<>();

      for(XTableStyle tstyle : tstyles) {
         String style = tstyle.getName();

         if(!checkPermission(user, ResourceType.TABLE_STYLE, style, EnumSet.of(permission))) {
            continue;
         }

         int idx = style.lastIndexOf(LibManager.SEPARATOR);
         String name = idx < 0 ? style : style.substring(idx + 1);
         String path = folder == null ?
            TABLE_STYLE + "/" + name : entry.getPath() + "/" + name;
         String parr = folder == null ?
            TABLE_STYLE + "^_^" + name :
            entry.getProperty(AssetEntry.PATH_ARRAY) + "^_^" + name;
         AssetEntry entry2 = new AssetEntry(AssetRepository.COMPONENT_SCOPE,
            AssetEntry.Type.TABLE_STYLE, path, null);
         entry2.setProperty(AssetEntry.PATH_ARRAY, parr);
         entry2.setProperty("source", name);
         entry2.setProperty("styleName", style);
         entry2.setProperty("styleID", tstyle.getID());
         entry2.setProperty("needRequestTooltip", "true");
         entry2.setProperty("mainType", ObjectInfo.COMPONENT);
         entry2.setProperty("subType", ObjectInfo.TABLE_STYLE);
         entry2.setProperty("folder", folder);
         styleList.add(entry2);
      }

      styleList.sort(Comparator.naturalOrder());
      list.addAll(styleList);
      entries = new AssetEntry[list.size()];
      list.toArray(entries);

      return entries;
   }

   private AssetEntry[] getScriptEntries(AssetEntry entry, ResourceAction action, Principal user) {
      AssetEntry[] entries;
      LibManager libManager = LibManager.getManager();
      Enumeration<String> e = libManager.getScripts();
      List<String> list = new ArrayList<>();

      while(e.hasMoreElements()) {
         String scriptName = e.nextElement();

         if(!libManager.isAuditScript(scriptName) &&
            checkPermission(user, ResourceType.SCRIPT, scriptName, EnumSet.of(action)))
         {
            list.add(scriptName);
         }
      }

      String[] scripts = new String[list.size()];
      list.toArray(scripts);
      entries = new AssetEntry[scripts.length];

      for(int i = 0; i < scripts.length; i++) {
         String path = SCRIPT + "/" + scripts[i];
         String parr = SCRIPT + "^_^" + scripts[i];
         entries[i] = new AssetEntry(AssetRepository.COMPONENT_SCOPE,
            AssetEntry.Type.SCRIPT, path, null);
         entries[i].setProperty(AssetEntry.PATH_ARRAY, parr);
         entries[i].setProperty("source", scripts[i]);
         entries[i].setProperty("description",
            libManager.getScriptComment(scripts[i]));
         entries[i].setProperty("needRequestTooltip", "true");
         entries[i].setProperty("mainType", ObjectInfo.COMPONENT);
         entries[i].setProperty("subType", ObjectInfo.SCRIPT_FUNCTION);
         entries[i].setProperty("Tooltip", libManager.getScriptComment(scripts[i]));
      }

      entries = Arrays.stream(entries).sorted(Comparator.naturalOrder())
         .toArray(AssetEntry[]::new);

      return entries;
   }

   /**
    * Get the sub query entries of a folder.
    *
    * @param entry    the specified folder entry.
    * @param selector the specified selector.
    * @param user     the specified user.
    * @return the sub entries of a folder.
    */
   @SuppressWarnings("WeakerAccess")
   protected AssetEntry[] getQueryEntries(AssetEntry entry, AssetEntry.Selector selector,
                                          Principal user)
      throws Exception
   {
      XRepository repository = XFactory.getRepository();
      AssetEntry[] entries;
      IdentityID userID = user == null ? null : IdentityID.getIdentityIDFromKey(user.getName());

      // root? return data sources
      if(entry.isRoot() || entry.isDataSourceFolder()) {
         String prefix = entry.getProperty("prefix");
         String[] folders = repository.getSubfolderNames(prefix);
         String[] sources = repository.getSubDataSourceNames(prefix);
         Arrays.sort(folders, new QueryEntriesComparator());
         Arrays.sort(sources, new QueryEntriesComparator());
         List<String> slist = new ArrayList<>();
         List<String> flist = new ArrayList<>();
         Map<String, String> descriptions = new HashMap<>();
         Map<String, String> dtypes = new HashMap<>();

         for(String source : sources) {
            // ignore xmla data source for binding tree
            XDataSource dx = repository.getDataSource(source);

            if(dx == null || Tool.equals(dx.getType(), XDataSource.XMLA)) {
               continue;
            }

            if(checkDataSourcePermission(source, user)) {
               slist.add(source);
               descriptions.put(source, dx.getDescription());
               dtypes.put(source, dx.getType());
            }
         }

         for(String folder : folders) {
            if(checkDataSourceFolderPermission(folder, user)) {
               flist.add(folder);
            }
         }

         int length = slist.size() + flist.size();
         entries = new AssetEntry[length];

         for(int i = 0; i < flist.size(); i++) {
            String parr = flist.get(i);
            entries[i] = new AssetEntry(AssetRepository.QUERY_SCOPE,
               AssetEntry.Type.DATA_SOURCE_FOLDER, parr, userID);
            entries[i].setProperty(AssetEntry.PATH_ARRAY,
               parr.replaceAll("/", "^_^"));
            entries[i].setProperty("prefix", parr);
            entries[i].setProperty("source", parr);
            entries[i].setProperty("mainType", "data source");
            String tip = Catalog.getCatalog().getString("Data Source Folder") + ": " + parr;
            entries[i].setProperty("Tooltip", tip);
         }

         for(int i = 0, j = flist.size(); j < length; j++, i++) {
            String parr = slist.get(i);
            entries[j] = new AssetEntry(QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE, parr, userID);
            entries[j].setProperty(AssetEntry.PATH_ARRAY, parr.replaceAll("/", "^_^"));
            entries[j].setProperty("prefix", parr);
            entries[j].setProperty("source", parr);
            String des = descriptions.get(parr);
            entries[j].setProperty("description", des);

            if(des != null) {
               entries[j].setProperty("Tooltip", des);
            }

            entries[j].setProperty(AssetEntry.DATA_SOURCE_TYPE, dtypes.get(parr));
            entries[j].setProperty("mainType", "data source");
            entries[j].setProperty("subType", dtypes.get(parr));
         }
      }
      // data source? return queries and logic models
      else if(entry.isDataSource()) {
         String source = entry.getProperty("prefix");
         XDataModel model = repository.getDataModel(source);

         // add models and physical entries
         List<AssetEntry> list = new ArrayList<>();
         Collections.addAll(list, getPhysicalEntries(entry, selector, user));

         String[] lmodels = XUtil.getLogicalModels(source);
         Arrays.sort(lmodels, new QueryEntriesComparator());

         for(String lmodel : lmodels) {
            XLogicalModel logicalModel = model.getLogicalModel(lmodel);
            String folder = logicalModel.getFolder();

            if(!checkDataModelFolderPermission(folder, source, user)) {
               continue;
            }

            String resource = folder != null && !folder.equals("")
               ? "__^" + folder + "^" + lmodel + "::" + source : lmodel + "::" + source;

            if(checkQueryPermission(resource, user)) {
               String path = folder != null && !folder.equals("") ?
                  source + "/" + folder + "/" + lmodel : source + "/" + lmodel;
               String parr = folder != null && !folder.equals("") ?
                  source + "^_^" + folder + "^_^" + lmodel : source + "^_^" + lmodel;
               AssetEntry entry2 = new AssetEntry(
                  QUERY_SCOPE, AssetEntry.Type.LOGIC_MODEL, path, userID);
               entry2.setProperty(AssetEntry.PATH_ARRAY, parr);
               entry2.setProperty("prefix", source);
               entry2.setProperty("source", lmodel);
               entry2.setProperty("mainType", "logical model");
               String des =
                  XUtil.getLogicalModelDescription(source, lmodel);
               entry2.setProperty("description", des);
               entry2.setProperty("type", XSourceInfo.MODEL + "");
               String tip = catalog.getString("Logical Model") + ": " +
                  lmodel + "[" + catalog.getString("Data Source") +
                  ": " + source + "; " + catalog.getString("Physical View") + ": " +
                  logicalModel.getPartition() + "]";

               if(des != null) {
                  tip += "\n" + des;
               }

               if(folder != null) {
                  entry2.setProperty("folder", logicalModel.getFolder());
                  entry2.setProperty("folder_description", folder);
               }

               entry2.setProperty("Tooltip", des);
               list.add(entry2);
            }
         }

         entries = list.toArray(new AssetEntry[0]);
      }
      // query or model, return columns or entities
      else if(entry.isLogicModel()) {
         // logical model? return entities
         String source = entry.getProperty("prefix");
         String lmodel = entry.getProperty("source");
         String folderDesc = entry.getProperty("folder_description");
         XDataModel model = repository.getDataModel(source);

         if(model == null) {
            /*
            throw new RuntimeException(Catalog.getCatalog().getString(
               "common.dataModelNotFound") + ": " + source);
            */
            // possible with tabular query in ws
            return new AssetEntry[0];
         }

         XLogicalModel logicmodel = model.getLogicalModel(lmodel, user, true);
         boolean defaultOrder =
            logicmodel == null || logicmodel.getEntityOrder();
         String[] tables = XUtil.getEntities(source, lmodel, user, true);
         List<String> list = new ArrayList<>();
         boolean vpm = user == null ||
            !"true".equals(((XPrincipal) user).getProperty("bypassVPM"));

         for(String entity : tables) {
            if(XUtil.getAttributes(source, lmodel, entity, user, true, vpm).length != 0) {
               list.add(entity);
            }
         }

         tables = list.toArray(new String[0]);
         entries = new AssetEntry[tables.length];

         if(defaultOrder) {
            Arrays.sort(tables, new QueryEntriesComparator());
         }

         for(int i = 0; i < tables.length; i++) {
            String path = source + "/" + lmodel + "/" + tables[i];
            String parr = source + "^_^" + lmodel + "^_^" + tables[i];
            entries[i] = new AssetEntry(QUERY_SCOPE, AssetEntry.Type.TABLE, path, userID);
            entries[i].setProperty(AssetEntry.PATH_ARRAY, parr);
            entries[i].setProperty("prefix", entry.getProperty("prefix"));
            entries[i].setProperty("source", entry.getProperty("source"));
            entries[i].setProperty("entity", tables[i]);

            if(logicmodel != null) {
               entries[i].setProperty("description",
                  logicmodel.getEntity(tables[i]).getDescription());
            }

            entries[i].setProperty("type", XSourceInfo.MODEL + "");
            String desc = XUtil.getEntityDescription(logicmodel,
               entries[i].getName());

            if(desc != null) {
               entries[i].setProperty("Tooltip", desc);
            }

            if(folderDesc != null) {
               entries[i].setProperty("folder_description", folderDesc);
            }
         }
      }
      // entity? return attributes
      else if(entry.isTable()) {
         String source = entry.getProperty("prefix");
         String lmodel = entry.getProperty("source");
         String entity = entry.getProperty("entity");
         String folderDesc = entry.getProperty("folder_description");
         boolean vpm = user == null ||
            !"true".equals(((XPrincipal) user).getProperty("bypassVPM"));
         XAttribute[] attributes = XUtil.getAttributes(source, lmodel, entity, user, true, vpm);
         entries = new AssetEntry[attributes.length];

         for(int i = 0; i < attributes.length; i++) {
            String attr = entity + ":" + attributes[i].getName();
            String path = source + "/" + lmodel + "/" + entity + "/" +
               attributes[i].getName();
            String parr = source + "^_^" + lmodel + "^_^" + entity + "^_^" +
               attributes[i].getName();
            entries[i] = new AssetEntry(QUERY_SCOPE, AssetEntry.Type.COLUMN, path, userID);
            entries[i].setProperty(AssetEntry.PATH_ARRAY, parr);
            entries[i].setProperty("prefix", entry.getProperty("prefix"));
            entries[i].setProperty("source", entry.getProperty("source"));
            entries[i].setProperty("entity", entry.getProperty("entity"));
            entries[i].setProperty("attribute", attr);
            String desc = attributes[i].getDescription();
            entries[i].setProperty("description", desc);
            entries[i].setProperty("type", XSourceInfo.MODEL + "");
            entries[i].setProperty("dtype", attributes[i].getDataType());
            entries[i].setProperty("refType", attributes[i].getRefType() + "");

            if(desc != null) {
               entries[i].setProperty("Tooltip", desc);
            }

            if(attributes[i].getDefaultFormula() != null) {
               entries[i].setProperty("formula",
                  attributes[i].getDefaultFormula());
            }

            if(folderDesc != null) {
               entries[i].setProperty("folder_description", folderDesc);
            }

            boolean aggregate = false;

            if(attributes[i] instanceof ExpressionAttribute) {
               aggregate = ((ExpressionAttribute) attributes[i]).isAggregateExpression();
            }

            entries[i].setProperty("aggformula", aggregate + "");
         }
      }
      // physical folder? return sub physical entries
      else if(entry.isPhysicalFolder()) {
         entries = getPhysicalEntries(entry, selector, user);
      }
      // physical table? return physical columns
      else if(entry.isPhysicalTable()) {
         entries = getPhysicalEntries(entry, selector, user);
      }
      else {
         throw new MessageException(catalog.getString(
            "common.invalidEntry", entry));
      }

      copyQueryEntryProperties(entries);
      return entries;
   }

   /**
    * Copies properties from the original entries in the indexed storage for
    * query-scoped entries.
    *
    * @param entries the entries to process.
    *
    * @throws Exception if the indexed storage could not be obtained.
    *
    * @since 12.2
    */
   private void copyQueryEntryProperties(AssetEntry[] entries) throws Exception {
      AssetFolder dataSourceFolder = null;
      AssetFolder queryFolder = null;

      for(AssetEntry entry : entries) {
         AssetFolder folder;

         if(entry.isQuery() || entry.isQueryFolder()) {
            if(queryFolder == null) {
               // lazily load the root folder
               AssetEntry rootEntry = new AssetEntry(
                  AssetRepository.QUERY_SCOPE, AssetEntry.Type.QUERY_FOLDER,
                  "/", null);
               queryFolder = (AssetFolder) getStorage(rootEntry)
                  .getXMLSerializable(rootEntry.toIdentifier(), null);
            }

            folder = queryFolder;
         }
         else {
            if(dataSourceFolder == null) {
               // lazily load the root folder
               AssetEntry rootEntry = new AssetEntry(
                  AssetRepository.QUERY_SCOPE,
                  AssetEntry.Type.DATA_SOURCE_FOLDER, "/", null);
               dataSourceFolder = (AssetFolder) getStorage(rootEntry)
                  .getXMLSerializable(rootEntry.toIdentifier(), null);
            }

            folder = dataSourceFolder;
         }

         AssetEntry original = folder == null ? null : folder.getEntry(entry);

         if(original != null) {
            entry.setCreatedUsername(original.getCreatedUsername());
            entry.setCreatedDate(original.getCreatedDate());
            entry.setModifiedUsername(original.getModifiedUsername());
            entry.setModifiedDate(original.getModifiedDate());
         }
      }
   }

   /**
    * Sort QueryEntries.
    */
   private static class QueryEntriesComparator implements Comparator<String> {
      public QueryEntriesComparator() {
      }

      public QueryEntriesComparator(boolean sortByCase) {
         this.sortByCase = sortByCase;
      }

      @Override
      public int compare(String v1, String v2) {
         if(v1 == null || v2 == null) {
            return (v1 == null && v2 == null) ? 0 : ((v1 == null) ? -1 : 1);
         }

         String str1 = sortByCase ? v1 : v1.toUpperCase();
         String str2 = sortByCase ? v2 : v2.toUpperCase();

         if(str1.equals(str2)) {
            return v1.compareTo(v2);
         }
         else {
            return str1.compareTo(str2);
         }
      }

      private boolean sortByCase = false;
   }

   /**
    * Sort the queries according the entry path.
    */
   private static class EntryPathComparator implements Comparator {
      @Override
      public int compare(Object v1, Object v2) {
         AssetEntry entry1 = (AssetEntry) v1;
         AssetEntry entry2 = (AssetEntry) v2;
         String[] paths1 = Tool.split(entry1.getPath(), '/');
         String[] paths2 = Tool.split(entry2.getPath(), '/');

         if(paths1.length != paths2.length) {
            return paths2.length - paths1.length;
         }
         else if(paths1.length == 3) {
            return Tool.compare(paths1[1], paths2[1]);
         }

         return 0;
      }
   }

   /**
    * Check the data model folder permission.
    *
    * @param folder the specified folder.
    * @param source the specified source.
    * @param user  the specified user.
    * @return <tt>true</tt> if pass, <tt>false</tt> otherwise.
    */
   protected abstract boolean checkDataModelFolderPermission(String folder,
                                                             String source, Principal user);

   /**
    * Check the query folder permission.
    *
    * @param folder the specified folder.
    * @param source the specified source.
    * @param user  the specified user.
    * @return <tt>true</tt> if pass, <tt>false</tt> otherwise.
    */
   protected abstract boolean checkQueryFolderPermission(String folder,
      String source, Principal user);

   /**
    * Check the query permission.
    *
    * @param query the specified query.
    * @param user  the specified user.
    * @return <tt>true</tt> if pass, <tt>false</tt> otherwise.
    */
   protected abstract boolean checkQueryPermission(String query,
                                                   Principal user);

   /**
    * Check the datasource permission.
    *
    * @param dname the specified datasource.
    * @param user  the specified user.
    * @return <tt>true</tt> if pass, <tt>false</tt> otherwise.
    */
   protected abstract boolean checkDataSourcePermission(String dname,
                                                        Principal user);

      /**
    * Check the datasource folder permission.
    *
    * @param folder the specified datasource folder.
    * @param user  the specified user.
    * @return <tt>true</tt> if pass, <tt>false</tt> otherwise.
    */
   protected abstract boolean checkDataSourceFolderPermission(String folder,
                                                        Principal user);

   /**
    * {@inheritDoc}
    */
   @Override
   public void addFolder(AssetEntry entry, Principal user)
      throws Exception
   {
      writeLock.lock();

      try {
         String identifier = entry.toIdentifier();

         if(!entry.isFolder() || !supportsScope(entry.getScope()) ||
            !entry.isValid() || entry.isRoot()
            || entry.getScope() == QUERY_SCOPE)
         {
            throw new MessageException(catalog.getString(
               "common.invalidEntry", entry));
         }

         IndexedStorage storage = getStorage(entry);

         if(storage == null) {
            throw new MessageException(catalog.getString(
               "common.invalidStorage", entry));
         }

         try {
            AssetEntry pentry = entry.getParent();

            if(pentry == null) {
               throw new IllegalArgumentException("{" + entry + "} " +
                  catalog.getString("common.pfolderNotFound"));
            }

            AssetFolder pfolder = getParentFolder(entry, storage);

            // if we have trouble parsing the folder (e.g. corrupted xml
            // serializable), we should still allow the folder to be
            // usable as an empty folder.
            if(pfolder == null) {
               // create parent if doesn't exist
               addFolder(entry.getParent(), user);
               pfolder = new AssetFolder();
            }

            String pidentifier = pentry.toIdentifier();

            // if folder is recovered in the previous if block, it may be missing in
            // index.properties but still in parent folder's xml entry. there is no
            // throw an exception. a log message should suffice. (46702)
            if(pfolder.containsEntry(entry)) {
               String msg = catalog.getString("common.entryExist", entry.getPath());
               LOG.info(msg);
            }

            if(!RecycleUtils.isInRecycleBin(entry.getPath()) && pentry.getPath().equals("/")) {
               checkAssetPermission(user, pentry, ResourceAction.WRITE);
            }

            // @by jasonshobe, try to create the new folder before adding it to
            // the parent so that if an exception occurs while creating the new
            // folder, the parent is not modified.
            AssetFolder folder = new AssetFolder();
            storage.putXMLSerializable(identifier, folder);

            long time = System.currentTimeMillis();

            AssetUtil.updateMetaData(entry, user, time);

            pfolder.addEntry(entry);
            storage.putXMLSerializable(pidentifier, pfolder);

            fireEvent(entry.getType().id(), AssetChangeEvent.ASSET_RENAMED, entry,
               entry.toIdentifier(), true, null, "addFolder: " + entry);
         }
         finally {
            closeStorages();
         }
      }
      finally {
         writeLock.unlock();
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean containsEntry(AssetEntry entry) {
      return getAssetEntry(entry) != null;
   }

   @Override
   public AssetEntry getAssetEntry(AssetEntry entry) {
      if(entry.getScope() == QUERY_SCOPE ||
         (entry.isRoot() && entry.getScope() != REPORT_SCOPE))
      {
         return entry;
      }

      try {
         IndexedStorage storage = entry.isRoot() ? null : getReportStorage(entry);

         if(storage == null) {
            return null;
         }

         if(isMetadataAware(storage)) {
            return ((MetadataAwareStorage) storage).getAssetEntry(entry.toIdentifier());
         }
         else {
            AssetFolder folder = getParentFolder(entry, storage);

            if(folder != null) {
               final AssetEntry folderEntry = folder.getEntry(entry);

               if(folderEntry != null) {
                  if(storage.contains(entry.toIdentifier(), entry.getOrgID())) {
                     return folderEntry;
                  }
                  else {
                     LOG.warn("Folder and repository are out of sync on entry {}", entry.getPath());
                  }
               }
            }
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to determine if entry exists: " + entry, ex);
      }

      return null;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void changeFolder(AssetEntry oentry, AssetEntry nentry,
                            Principal user, boolean force)
      throws Exception
   {
      changeFolder(oentry, nentry, user, force, true);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void changeFolder(AssetEntry oentry, AssetEntry nentry,
                            Principal user, boolean force, boolean callFireEvent)
   throws Exception
   {
      writeLock.lock();

      try {
         if(!oentry.isFolder() || !supportsScope(oentry.getScope()) ||
            !oentry.isValid() || oentry.isRoot() ||
            oentry.getScope() == QUERY_SCOPE)
         {
            throw new MessageException(catalog.getString(
               "common.invalidEntry", oentry));
         }

         if(!nentry.isFolder() || !supportsScope(nentry.getScope()) ||
            !nentry.isValid() || nentry.isRoot() ||
            nentry.getScope() == QUERY_SCOPE)
         {
            throw new MessageException(catalog.getString(
               "common.invalidEntry", nentry));
         }

         if(nentry.equals(oentry) &&
            Tool.equals(nentry.getAlias(), oentry.getAlias()))
         {
            throw new MessageException(catalog.getString(
               "common.sameFolder", nentry));
         }

         IndexedStorage ostorage = getStorage(oentry);
         IndexedStorage nstorage = getStorage(nentry);

         if(ostorage == null || nstorage == null) {
            throw new MessageException(catalog.getString(
               "common.invalidStorage", oentry, nentry));
         }

         try {
            AssetEntry npentry = nentry.getParent();

            if(!RecycleUtils.isInRecycleBin(nentry.getPath())) {
               checkAssetPermission(user, oentry, ResourceAction.WRITE);

               if(!Tool.equals(npentry, oentry.getParent())) {
                  checkAssetPermission(user, npentry, ResourceAction.WRITE);
               }
            }

            checkAssetPermission(user, oentry, ResourceAction.DELETE);

            try {
               if(oentry.getScope() != nentry.getScope()) {
                  allowsFolderScopeChange0(oentry, nentry.getScope(), ostorage);
               }
            }
            catch(DependencyException dex) {
               if(!force) {
                  throw dex;
               }
            }

            changeFolder0(oentry, ostorage, nentry, nstorage, true, callFireEvent);
         }
         finally {
            closeStorages();
         }
      }
      finally {
         writeLock.unlock();
      }
   }

   /**
    * Change one folder internally.
    *
    * @param oentry the specified old folder entry.
    * @param nentry the specified new folder entry.
    */
   protected Map<AssetEntry, AssetEntry> changeFolder0(AssetEntry oentry, IndexedStorage ostorage,
                                                       AssetEntry nentry, IndexedStorage nstorage,
                                                       boolean root) throws Exception
   {
      return changeFolder0(oentry, ostorage, nentry, nstorage, root, true);
   }

   /**
    * Change one folder internally.
    *
    * @param callFireEvent if send event
    */
   protected Map<AssetEntry, AssetEntry> changeFolder0(AssetEntry oentry, IndexedStorage ostorage,
                                                       AssetEntry nentry, IndexedStorage nstorage,
                                                       boolean root, boolean callFireEvent) throws Exception
   {
      Map<AssetEntry, AssetEntry> changed = new HashMap<>();
      changeFolder0(oentry, ostorage, nentry, nstorage, root, changed, callFireEvent);
      return changed;
   }

   private void changeFolder0(AssetEntry oentry, IndexedStorage ostorage,
                              AssetEntry nentry, IndexedStorage nstorage,
                              boolean root, Map<AssetEntry, AssetEntry> changed,
                              boolean callFireEvent) throws Exception
   {

      List<RenameDependencyInfo> renameDependencyInfos = new ArrayList<>();
      changeFolder0(oentry, ostorage, nentry, nstorage, root, changed, callFireEvent,
         renameDependencyInfos);

      // for relation viewsheets under the same folder and folder changed, in order to avoid
      // transform thread and rename thread information(asset path) is out of sync.
      // so wait renaming finish to do transform.
      renameDependencyInfos.forEach(info -> {
         if(info == null) {
            return;
         }

         AssetObject[] assetObjects = info.getAssetObjects();

         if(assetObjects == null || assetObjects.length == 0) {
            return;
         }

         for(AssetObject assetObject : assetObjects) {
            if(!(assetObject instanceof AssetEntry)) {
               continue;
            }

            AssetEntry newAssetEntry = changed.get(assetObject);

            if(newAssetEntry == null) {
               continue;
            }

            List<RenameInfo> renameInfos = info.getRenameInfo(assetObject);
            info.removeRenameInfo(assetObject);
            info.setRenameInfo(newAssetEntry, renameInfos);
         }

         RenameTransformHandler.getTransformHandler().addTransformTask(info, true);
      });
   }

   private void changeFolder0(AssetEntry oentry, IndexedStorage ostorage,
                              AssetEntry nentry, IndexedStorage nstorage,
                              boolean root, Map<AssetEntry, AssetEntry> changed,
                              boolean callFireEvent,
                              List<RenameDependencyInfo> renameDependencyInfos) throws Exception
   {
      AssetFolder ofolder = getFolder(oentry, ostorage);
      AssetFolder nfolder = new AssetFolder();
      String oidentifier = oentry.toIdentifier();
      String nidentifier = nentry.toIdentifier();
      AssetEntry opentry = null;
      AssetFolder opfolder = null;
      String opidentifier = null;
      AssetEntry npentry;
      AssetFolder npfolder;

      clearCache(oentry);

      // alias changed
      if(Tool.equals(oentry, nentry) &&
         !Tool.equals(oentry.getAlias(), nentry.getAlias())) {
         opentry = oentry.getParent();
         opfolder = getParentFolder(oentry, ostorage);
         opfolder.removeEntry(oentry);
         opfolder.addEntry(nentry);
         ostorage.putXMLSerializable(opentry.toIdentifier(), opfolder);
         fireEvent(oentry.getType().id(), AssetChangeEvent.ASSET_RENAMED, nentry,
            oentry.toIdentifier(), root, null, "changeFolder: " + oentry + ", " + nentry);

         return;
      }

      if(!oentry.isRoot()) {
         opentry = oentry.getParent();
         opfolder = getParentFolder(oentry, ostorage);
         opidentifier = opentry.toIdentifier();

         if(!opfolder.containsEntry(oentry)) {
            MessageException ex = new MessageException(catalog.getString(
               "common.notContainedEntry", opentry, oentry));
            ex.setKeywords("NOT_CONTAINED_ENTRY");
            throw ex;
         }
      }

      if(!nentry.isRoot()) {
         npentry = nentry.getParent();
         npfolder = getParentFolder(nentry, nstorage);
         String npidentifier = npentry.toIdentifier();

         if(root && npfolder.containsEntry(nentry)) {
            throw new MessageException(catalog.getString(
               "common.folderExist", nentry));
         }

         if(npentry.equals(opentry)) {
            opfolder = npfolder;
         }

         npfolder.addEntry(nentry);
         nstorage.putXMLSerializable(npidentifier, npfolder);
      }

      updatePermission(oentry, nentry);
      changed.put(oentry, nentry);

      AssetEntry[] entries = ofolder.getEntries();
      String opath = oentry.getPath();
      String npath = nentry.getPath();
      int scope = nentry.getScope();
      IdentityID user = nentry.getUser();
      AssetEntry[] nentries = new AssetEntry[entries.length];
      ScheduleManager manager = ScheduleManager.getScheduleManager();

      for(int i = 0; i < entries.length; i++) {
         String path = entries[i].getPath();
         path = oentry.isRoot() ? path : path.substring(opath.length() + 1);
         path = nentry.isRoot() ? path : npath + "/" + path;
         AssetEntry.Type type = entries[i].getType();
         AssetEntry entry2 = new AssetEntry(scope, type, path, user);
         entry2.copyProperties(nentry);
         AssetUtil.copyWSInfo(entries[i], entry2);

         entry2.setProperty(AssetEntry.SHEET_DESCRIPTION,
            entries[i].getProperty(AssetEntry.SHEET_DESCRIPTION));

         if(entry2.isWorksheet()) {
            entry2.setProperty(AssetEntry.WORKSHEET_TYPE,
               entries[i].getProperty(AssetEntry.WORKSHEET_TYPE));
         }

         AssetEntry e = entries[i];
         entry2.setCreatedDate(e.getCreatedDate());
         entry2.setCreatedUsername(e.getCreatedUsername());
         entry2.setModifiedDate(e.getModifiedDate());
         entry2.setModifiedUsername(e.getModifiedUsername());
         entry2.addFavoritesUser(e.getFavoritesUser());
         nfolder.addEntry(entry2);
         nentries[i] = entry2;

         if(entry2.isViewsheet() && RecycleUtils.isInRecycleBin(npath)) {
            manager.viewsheetRemoved(entries[i], entries[i].getOrgID());
         }
      }

      // save folder first for the recursive calls require the information
      nstorage.putXMLSerializable(nidentifier, nfolder);

      if(!oentry.isRoot() && opfolder != null) {
         opfolder.removeEntry(oentry);
         ostorage.putXMLSerializable(opidentifier, opfolder);
      }

      for(int i = 0; i < entries.length; i++) {
         if(entries[i].isFolder()) {
            changeFolder0(entries[i], ostorage, nentries[i], nstorage, false, changed,
               callFireEvent, renameDependencyInfos);
         }
         else {
            changed.put(entries[i], nentries[i]);
            changeSheet0(entries[i], ostorage, nentries[i], nstorage, false, callFireEvent,
               renameDependencyInfos);
         }
      }

      if(ostorage.contains(oidentifier)) {
         ostorage.remove(oidentifier);
      }

      fireEvent(oentry.getType().id(), AssetChangeEvent.ASSET_RENAMED, nentry,
         oentry.toIdentifier(), root, null, "changeFolder: " + oentry + ", " + nentry);
   }

   private void updatePermission(AssetEntry oentry, AssetEntry nentry) {
      if(oentry == null || nentry == null) {
         return;
      }

      SecurityEngine securityEngine = SecurityEngine.getSecurity();
      ResourceType type = getAssetResourceType(oentry);
      Permission oldPermission = securityEngine.getPermission(type, oentry.getPath());

      if(oldPermission == null) {
         return;
      }

      securityEngine.removePermission(type, oentry.getPath());
      securityEngine.setPermission(type, nentry.getPath(), oldPermission);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void removeFolder(AssetEntry entry, Principal user, boolean force)
      throws Exception
   {
      writeLock.lock();

      try {
         if(!entry.isFolder() || !supportsScope(entry.getScope()) ||
            !entry.isValid() || entry.isRoot()
            || entry.getScope() == QUERY_SCOPE)
         {
            throw new MessageException(catalog.getString(
               "common.invalidEntry", entry));
         }

         IndexedStorage storage = getStorage(entry);

         if(storage == null) {
            throw new MessageException(catalog.getString(
               "common.invalidStorage", entry));
         }

         try {
            checkAssetPermission(user, entry, ResourceAction.DELETE);

            if(!force) {
               checkFolderRemovable0(entry, storage);
            }

            removeFolder0(entry, storage, true);
         }
         finally {
            closeStorages();
         }
      }
      finally {
         writeLock.unlock();
      }
   }

   /**
    * Remove one folder internally.
    *
    * @param entry   the specified folder entry.
    * @param storage the specified indexed storage.
    * @param root    whether or not this is the root entry that changed
    */
   protected void removeFolder0(AssetEntry entry, IndexedStorage storage, boolean root)
      throws Exception
   {
      try {
         clearCache(entry);
         AssetFolder folder = getFolder(entry, storage);
         AssetEntry[] entries = folder.getEntries();

         for(AssetEntry childEntry : entries) {
            if(childEntry.isFolder()) {
               removeFolder0(childEntry, storage, false);
            }
            else {
               removeSheet0(childEntry, storage, true);
            }
         }
      }
      catch(MessageException exc) {
         // @by jasons, feature1272047865192, may be in an inconsistent state,
         // continue to ensure that the entry is removed from the parent
         if("FOLDER_REQUIRED".equals(exc.getKeywords())) {
            Throwable thrown = LOG.isDebugEnabled() ? exc : null;
            LOG.warn("Failed to remove missing folder: {}", exc.getMessage(), thrown);
         }
         else {
            throw exc;
         }
      }

      if(!entry.isRoot()) {
         try {
            AssetEntry pentry = entry.getParent();
            AssetFolder pfolder = getParentFolder(entry, storage);
            String pidentifier = pentry.toIdentifier();

            if(!pfolder.containsEntry(entry)) {
               MessageException ex = new MessageException(catalog.getString(
                  "common.notContainedEntry", pentry, entry));
               ex.setKeywords("NOT_CONTAINED_ENTRY");
               throw ex;
            }

            pfolder.removeEntry(entry);
            storage.putXMLSerializable(pidentifier, pfolder);
         }
         catch(MessageException exc) {
            // @by jasons, feature1272047865192, may be in an inconsistent
            // state, continue to ensure that the entry is removed
            if("FOLDER_REQUIRED".equals(exc.getKeywords()) ||
               "NOT_CONTAINED_ENTRY".equals(exc.getKeywords()))
            {
               Throwable thrown = LOG.isDebugEnabled() ? exc : null;
               LOG.warn("Failed to remove missing entry: {}", exc.getMessage(), thrown);
            }
            else {
               throw exc;
            }
         }
      }

      storage.remove(entry.toIdentifier());
      fireEvent(entry.getType().id(), AssetChangeEvent.ASSET_DELETED, entry, null, root, null,
                "removeFolder: " + entry);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void checkFolderRemoveable(AssetEntry entry, Principal user)
      throws Exception
   {
      if(!entry.isFolder() || !supportsScope(entry.getScope()) ||
         !entry.isValid() || entry.isRoot() || entry.getScope() == QUERY_SCOPE)
      {
         throw new MessageException(catalog.getString(
            "common.invalidEntry", entry));
      }

      IndexedStorage storage = getStorage(entry);

      if(storage == null) {
         throw new MessageException(catalog.getString(
            "common.invalidStorage", entry));
      }

      try {
         checkAssetPermission(user, entry, ResourceAction.DELETE);
         checkFolderRemovable0(entry, storage);
      }
      finally {
         storage.close();
      }
   }

   /**
    * Check if folder is removeable.
    *
    * @param entry   the specified folder entry.
    * @param storage the specified indexed storage.
    */
   private void checkFolderRemovable0(AssetEntry entry, IndexedStorage storage)
      throws Exception
   {
      AssetFolder folder = getFolder(entry, storage);
      AssetEntry[] entries = folder.getEntries();

      for(AssetEntry childEntry : entries) {
         if(childEntry.isFolder()) {
            checkFolderRemovable0(childEntry, storage);
         }
         else {
            checkSheetRemoveable(childEntry, null);
         }
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void checkSheetRemoveable(AssetEntry entry, Principal user)
     throws Exception
   {
      try {
         List<Object> aentries = new ArrayList<>();

         AssetEntry[] entries = getSheetDependencies(entry, user);
         Collections.addAll(aentries, entries);

         if(aentries.size() > 0) {
            DependencyException ex = new DependencyException(entry);
            ex.addDependencies(aentries.toArray(new Object[0]));
            throw ex;
         }
      }
      catch(SAXParseException | MissingAssetClassNameException ex) {
         // ignore if the sheet can't be read
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void syncFolders(Principal user) throws Exception {
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public String getEntryIdentifier(AssetEntry entry) {
      return entry.toIdentifier();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public AbstractSheet getSheet(AssetEntry entry, Principal user,
                                 boolean permission, AssetContent ctype)
      throws Exception
   {
      return getSheet(entry, user, permission, ctype, true);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public AbstractSheet getSheet(AssetEntry entry, Principal user,
                                 boolean permission, AssetContent ctype, boolean useCache)
      throws Exception
   {
      if(entry == null) {
         return null;
      }

      String identifier = entry.toIdentifier();
      String eidentifier = getEntryIdentifier(entry);
      String orgID = entry.getOrgID();

      if(orgID == null) {
         if(user == null) {
            orgID = OrganizationManager.getInstance().getCurrentOrgID();
         }
         else {
            orgID = ((XPrincipal) user).getOrgId();
         }
      }

      if(!entry.isSheet() || !entry.isValid()) {
         throw new MessageException(catalog.getString(
            "common.invalidEntry", entry));
      }

      IndexedStorage storage = getReportStorage(entry);

      if(storage == null) {
         throw new MessageException(catalog.getString(
            "common.invalidStorage", entry));
      }

      try {
         if(permission && !"true".equals(entry.getProperty("openAutoSaved"))) {
            checkAssetPermission(user, entry, ResourceAction.READ, true);
         }

         long modified = istore.lastModified();

         if(modified != lastMod) {
            lastMod = modified;
            contextmap.clear();
            sheetmap.clear();
            foldermap.clear();
            bookmarkmap.clear();
         }

         copyEntryProperty(entry, storage);
         AbstractSheet sheet = null;
         XMLSerializable obj = null;

         // old logic to fix auto save files on saved vs, after get auto save file, should set
         // "openAutoSaved" to false. But for untitled vs, should not sheet from the user, should
         // get sheet by full name of auto saved file.
         // The untitled sheets are show only for created users and admin. We can't set its
         // permisson.
         if("true".equals(entry.getProperty("openAutoSaved")) &&
            storage instanceof AbstractIndexedStorage)
         {
            obj = ((AbstractIndexedStorage) storage).getAutoSavedSheet(entry, user);

            if(entry.getScope() != AssetRepository.TEMPORARY_SCOPE) {
               entry.setProperty("openAutoSaved", "false");
            }
         }
         // optimization, cache context object since it's requested often and
         // parsing a large xml to dom could be expensive
         else if(ctype == AssetContent.CONTEXT) {
            sheet = contextmap.get(eidentifier);
         }
         //else if(ctype == AssetContent.ALL) {
         // cache no_data and all in sheetmap
         // fix Bug #3748, don't get localworksheet from cache to make sure
         // the loaded localworksheet is always the newest.
         else if(useCache && entry.getScope() != REPORT_SCOPE) {
            sheet = sheetmap.get(eidentifier);
         }

         if(sheet == null) {
            if(obj == null) {
               obj = storage.getXMLSerializable(identifier, ctype, orgID);
            }

            if(obj == null) {
               return null;
            }

            if(!(obj instanceof AbstractSheet)) {
               MessageException ex = new MessageException(catalog.getString(
                  "common.notContainedEntry",
                  entry.getParent(), entry));
               ex.setKeywords("NOT_CONTAINED_ENTRY");
               throw ex;
            }

            sheet = (AbstractSheet) obj;

            if(ctype == AssetContent.CONTEXT) {
               contextmap.put(eidentifier, sheet);
            }
            else if(ctype == AssetContent.ALL) {
               sheetmap.put(eidentifier, sheet);
            }
         }

         if(ctype == AssetContent.ALL) {
            sheet = (AbstractSheet) sheet.clone();
            sheet.update(getParent() == null ? this : getParent(), entry, user);
         }

         entry.setModifiedUsername(sheet.getLastModifiedBy());
         entry.setModifiedDate(new Date(sheet.getLastModified()));
         entry.setCreatedUsername(sheet.getCreatedBy());
         entry.setCreatedDate(new Date(sheet.getCreated()));

         if(ctype == AssetContent.ALL && entry.getScope() != AssetRepository.TEMPORARY_SCOPE) {
            VSEventUtil.deleteAutoSavedFile(entry, user);
         }

         return sheet;
      }
      finally {
         closeStorages();
      }
   }

   /**
    * Get report storage.
    */
   public IndexedStorage getReportStorage(AssetEntry entry) throws Exception {
      try {
         return getStorage(entry);
      }
      catch(Exception ex) {
         throw new MessageException(catalog.getString(
                                       "common.sheetCannotFount", entry));
      }
   }

   public void fireLibraryEvent(AssetEntry entry) {
      fireEvent(entry.getType().id(), AssetChangeEvent.ASSET_MODIFIED, entry, null, true, null,
         "library: " + entry);
   }

   /**
    * Check if the sheet is contained in this engine.
    */
   private boolean containsSheet(AssetEntry entry) throws Exception {
      String identifier = entry.toIdentifier();
      IndexedStorage storage = getReportStorage(entry);

      return storage != null && storage.contains(identifier);
   }

   /**
    * Get original hash code.
    */
   public int addr() {
      return super.hashCode();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setSheet(AssetEntry entry, AbstractSheet sheet, Principal user,
                        boolean force) throws Exception
   {
      setSheet(entry, sheet, user, force, true, false);
   }

   /**
    * Set one sheet.
    *
    * @param entry the specified sheet entry.
    * @param sheet the specified sheet.
    * @param user  the specified user.
    * @param force <tt>true</tt> to set sheet forcely without
    *              checking.
    */
   public void setSheet(AssetEntry entry, AbstractSheet sheet, Principal user,
                        boolean force, boolean checkDependency)
      throws Exception
   {
      setSheet(entry, sheet, user, force, checkDependency, true);
   }

   public void setSheet(AssetEntry entry, AbstractSheet sheet, Principal user,
                        boolean force, boolean checkDependency, boolean updateDependency)
           throws Exception
   {
      setSheet(entry, sheet, user, force, checkDependency, updateDependency, true);
   }

   public void setSheet(AssetEntry entry, AbstractSheet sheet, Principal user, boolean force,
                        boolean checkDependency, boolean updateDependency, boolean checkCrossJoins)
      throws Exception
   {
      writeLock.lock();

      try {
         String identifier = entry.toIdentifier();
         sheet.checkValidity(checkCrossJoins);

         if(!entry.isSheet() || !(supportsScope(entry.getScope()) ||
                 entry.getScope() == AssetRepository.REPORT_SCOPE) ||
                 !entry.isValid())
         {
            throw new MessageException(catalog.getString(
                    "common.invalidEntry", entry));
         }

         IndexedStorage storage = getReportStorage(entry);

         if(storage == null) {
            throw new MessageException(catalog.getString(
                    "common.invalidStorage", entry));
         }

         boolean update = lastMod == istore.lastModified();
         AssetEntry[] outerDependencies = sheet.getOuterDependencies();

         try {
            AssetEntry pentry = entry.getParent();
            AssetFolder pfolder = getParentFolder(entry, storage);
            String pidentifier = pentry.toIdentifier();

            if(pfolder == null && entry.isViewsheet()) {
               throw new Exception(Catalog.getCatalog().getString(
                       "common.move.notallowed"));
            }

            if(pfolder == null) {
               IndexedStorage dstorage = getStorage(pentry);
               pfolder = new AssetFolder();
               dstorage.putXMLSerializable(pidentifier, pfolder);
            }

            boolean contained = pfolder.containsEntry(entry);
            AbstractSheet osheet = null;

            if(contained) {
               checkAssetPermission(user, entry, ResourceAction.WRITE);

               try {
                  osheet = getSheet((AssetEntry) entry.clone(), user, false, AssetContent.NO_DATA);
               }
               catch(Exception ex) {
                  LOG.info("Failed to parse sheet: " + entry, ex);
               }
            }
            else {
               checkAssetPermission(user, pentry, ResourceAction.WRITE);
               // remember dependency and restore later
               // don't clone the sheet. otherwise changes (e.g. SnapshotEmbeddedTableAssembly)
               // made in write will not be reflected in in-memory object, causing problems
               // in subsequent logic
               outerDependencies = sheet.getOuterDependencies();
               sheet.removeOuterDependencies();
            }

            // dependent sheets
            AssetEntry[] dentries = sheet.getOuterDependents();

            // check 1 ring dependency cycle, which is not complete,
            // but considering expending, it seems relatively enough
            AssetEntry[] dentries2 = sheet.getOuterDependencies();

            // the dependencies might be changed already, which is done by
            // this asset repository, so we should use the dependencies
            // come from the old sheet if the old sheet exists
            if(osheet != null) {
               dentries2 = osheet.getOuterDependencies();
               sheet.removeOuterDependencies();
               dentries2 = getExistingEntries(dentries2, entry);

               for(AssetEntry dentry2 : dentries2) {
                  sheet.addOuterDependency(dentry2);
               }
            }

            if(!force && osheet != null && osheet.getType() != sheet.getType() &&
                    dentries2.length > 0)
            {
               DependencyException ex = new DependencyException(entry) {
                  @Override
                  public String getMessage() {
                     String msg = super.getMessage();
                     msg += ". " + Catalog.getCatalog().getString(
                             "common.changePrimary");
                     return msg;
                  }
               };

               for(AssetEntry dentry2 : dentries2) {
                  ex.addDependency(dentry2);
               }

               throw ex;
            }

            // check sheet dependency validity
            for(AssetEntry dentry : dentries) {
               if(checkDependency) {
                  checkDependencyValidity(entry, dentry);
               }

               try {
                  if(!containsSheet(dentry)) {
                     continue;
                  }
               }
               catch(Exception ex) {
                  LOG.error("Failed to determine if entry exists: " + dentry, ex);
               }

               for(AssetEntry dentry2 : dentries2) {
                  if(dentry2.equals(dentry)) {
                     InvalidDependencyException ex =
                             new InvalidDependencyException(catalog.getString(
                                     "common.dependencyCycle"));
                     ex.setLogLevel(LogLevel.DEBUG);
                     throw ex;
                  }
               }
            }

            // maintain sheet dependency
            if(entry.getScope() != REPORT_SCOPE) {
               nextd:
               for(AssetEntry dentry : dentries) {
                  AbstractSheet dsheet = null;

                  try {
                     dsheet = getSheet(dentry, user, false, AssetContent.CONTEXT);
                  }
                  catch(Exception ex) {
                     LOG.error("Failed to get sheet for entry: " + dentry, ex);
                  }

                  if(dsheet == null) {
                     continue;
                  }

                  AssetEntry[] outers = dsheet.getOuterDependencies();

                  // optimization, avoid reading/writing full sheet
                  for(AssetEntry outer : outers) {
                     if(entry.equals(outer)) {
                        continue nextd;
                     }
                  }

                  // get full sheet
                  dsheet = getSheet(dentry, user, false, AssetContent.ALL);
                  IndexedStorage dstorage = getStorage(dentry);
                  String didentifier = dentry.toIdentifier();
                  dsheet.addOuterDependency(entry);
                  dstorage.putXMLSerializable(didentifier, dsheet);
                  sheetmap.put(didentifier, dsheet);
               }
            }

            entry.setProperty(AssetEntry.SHEET_DESCRIPTION, sheet.getDescription());
            entry.setProperty("isEdit", "false");

            if(entry.isWorksheet()) {
               Worksheet ws = (Worksheet) sheet;
               entry.setProperty(AssetEntry.WORKSHEET_TYPE, ws.getType() + "");

               ///set all the metadata here based on ws, containing imported data
              entry.setModifiedDate(new Date(ws.getLastModified()));
              entry.setModifiedUsername(ws.getLastModifiedBy());
              entry.setCreatedDate(new Date(ws.getCreated()));
              entry.setCreatedUsername(ws.getCreatedBy());
            }

            long time = System.currentTimeMillis();

            AssetUtil.updateMetaData(entry, user, time);
            clearCache(entry);

            if(sheet.getCreatedBy() == null) {
               Date date = entry.getCreatedDate();

               if(date != null) {
                  sheet.setCreated(date.getTime());
               }

               sheet.setCreatedBy(entry.getCreatedUsername());
            }

            // @by stephenwebster, upon import do not allow viewsheet meta data to be overridden
            // if the modified username is not set on the entry
            if(entry.getModifiedUsername() != null) {
               sheet.setLastModified(entry.getModifiedDate().getTime());
               sheet.setLastModifiedBy(entry.getModifiedUsername());
            }

            Principal bookmarkUser = user;

            if(sheet instanceof Viewsheet) {
               Viewsheet vs = (Viewsheet) sheet;
               ViewsheetInfo vsInfo = vs.getViewsheetInfo();

               // no security, logged in as admin and saving a composed dashboard
               if(vsInfo.isComposedDashboard() && !SecurityEngine.getSecurity().isSecurityEnabled()
                       && user != null && Tool.equals(XPrincipal.ANONYMOUS, entry.getUser()))
               {
                  bookmarkUser = new XPrincipal(new IdentityID(XPrincipal.ANONYMOUS, Organization.getDefaultOrganizationID()));
               }
            }

            // fixed bug1219747176468
            overwriteBookmarks(sheet, entry, bookmarkUser);
            // for feature #9005, update dependencies of the binding sources.

            if(updateDependency) {
               DependencyHandler.getInstance().updateSheetDependencies(sheet, osheet, entry);
            }

            storage.putXMLSerializable(identifier, sheet);
            EmbeddedDataCacheHandler.clearWSCache(identifier);

            // @by jasonshobe, add the entry to the parent after saving the
            // sheet so that if an exception occurs while saving the sheet, the
            // folder entry remains unmodified
            AssetEntry oentry = pfolder.getEntry(entry);
            pfolder.removeEntry(entry);

            if(oentry != null) {
               entry.addFavoritesUser(oentry.getFavoritesUser());
            }

            pfolder.addEntry(entry);

            // also need clear parent cache, or the "foldermap" info is wrong,
            // see bug1365582740490
            clearCache(pentry);
            storage.putXMLSerializable(pidentifier, pfolder);
            fireEvent(sheet.getType(), AssetChangeEvent.ASSET_MODIFIED, entry, null, true, null,
                      "setSheet: " + entry);

            String oid = LOCAL.get();

            if(oid != null) {
               AssetEntry oldEntry = AssetEntry.createAssetEntry(oid);
               MVManager mvManager = MVManager.getManager();
               boolean wsMV = entry.getType() == AssetEntry.Type.WORKSHEET;
               MVDef[] defs = mvManager.list(true, def -> def.isWSMV() == wsMV);

               for(MVDef def : defs) {
                  MVMetaData data = def.getMetaData();
                  boolean changed = false;

                  if(def.matches(oldEntry)) {
                     def.setEntry(entry);
                     def.setChanged(true);
                     changed = true;
                  }
                  else if(data.isRegistered(oid)) {
                     data.renameRegistered(oid, entry.toIdentifier());
                     def.setChanged(true);
                     changed = true;
                  }

                  if(def.renameParentVsId(oid, entry.toIdentifier())) {
                     changed = true;
                  }

                  if(changed) {
                     mvManager.add(def);
                  }
               }

               LOCAL.remove();
            }
         }
         finally {
            closeStorages();

            if(update) {
               lastMod = istore.lastModified();
            }

            // restore outer dependency cleared earlier
            if(outerDependencies != null) {
               Arrays.stream(outerDependencies).forEach(d -> sheet.addOuterDependency(d));
            }
         }
      }
      finally {
         writeLock.unlock();
      }
   }

   /**
    * Check the validity of a dependency from one asset entry to another aset
    * entry.
    *
    * @param from the specified dependecy asset entry.
    * @param to   the specified dependent asset entry.
    */
   @SuppressWarnings("WeakerAccess")
   protected void checkDependencyValidity(AssetEntry from, AssetEntry to) throws Exception
   {
      checkDependencyValidity(from, to, false);
   }

   /**
    * Check the validity of a dependency from one asset entry to another aset
    * entry.
    *
    * @param from the specified dependency asset entry.
    * @param to   the specified dependent asset entry.
    * @param ignoreUserName if true don't need to check uer name else not.
    */
   @SuppressWarnings("WeakerAccess")
   protected void checkDependencyValidity(AssetEntry from, AssetEntry to,
                                          boolean ignoreUserName) throws Exception
   {
      int fscope = from.getScope();
      int tscope = to.getScope();

      // global scope? all are allowed
      if(tscope != GLOBAL_SCOPE) {
         // report scope? only report scope is allowed
         if(tscope == REPORT_SCOPE) {
            if(fscope != REPORT_SCOPE) {
               throw new MessageException(catalog.getString(
                  "common.reportScopeReferenced"));
            }
         }
         else if(tscope == USER_SCOPE) {
            //global to user, and user to user, not handler reference.
            if(fscope != USER_SCOPE ||
               !ignoreUserName && !Tool.equals(from.getUser(), to.getUser())) {
               throw new MessageException(catalog.getString(
                  "common.userScopeReferenced"));
            }
         }
         else {
            throw new RuntimeException("Unsupported scope found: " + tscope);
         }
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void changeSheet(AssetEntry oentry, AssetEntry nentry,
                           Principal user, boolean force)
      throws Exception
   {
      changeSheet(oentry, nentry, user, force, true);
   }

   @Override
   public void changeSheet(AssetEntry oentry, AssetEntry nentry,
                           Principal user, boolean force, boolean callFireEvent)
      throws Exception
   {
      changeSheet(oentry, nentry, user, force, callFireEvent, false);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void changeSheet(AssetEntry oentry, AssetEntry nentry,
                           Principal user, boolean force, boolean callFireEvent,
                           boolean ignorePermissions)
      throws Exception
   {
      writeLock.lock();

      try {
         if(!oentry.isSheet() || !supportsScope(oentry.getScope()) ||
            !oentry.isValid())
         {
            throw new MessageException(catalog.getString(
               "common.invalidEntry", oentry));
         }

         if(!nentry.isSheet() || nentry.getType() != oentry.getType() ||
            !supportsScope(nentry.getScope()) || !nentry.isValid())
         {
            throw new MessageException(catalog.getString(
               "common.invalidEntry", nentry));
         }

         IndexedStorage ostorage = getStorage(oentry);
         IndexedStorage nstorage = getStorage(nentry);

         if(ostorage == null) {
            throw new MessageException(catalog.getString(
               "common.invalidStorage", oentry));
         }

         if(nstorage == null) {
            throw new MessageException(catalog.getString(
               "common.invalidStorage", nentry));
         }

         try {
            if(!ignorePermissions) {
               //bug1374830716367,
               //change folder and rename need to check delete permission.
               if(!oentry.getPath().equals(nentry.getPath()) ||
                  RecycleUtils.isInRecycleBin(nentry.getPath()))
               {
                  checkAssetPermission(user, oentry, ResourceAction.DELETE);
               }

               AssetEntry npentry = nentry.getParent();

               //by Sabolei, if move sheet to recycle, it should not check write permission,
               //should check delete permission.
               if(!RecycleUtils.isInRecycleBin(nentry.getPath())) {
                  checkAssetPermission(user, oentry, ResourceAction.WRITE);

                  if(!Tool.equals(npentry, oentry.getParent())) {
                     checkAssetPermission(user, npentry, ResourceAction.WRITE);
                  }
               }
            }

            try {
               allowsSheetScopeChange0(oentry, nentry.getScope());
            }
            catch(DependencyException dex) {
               if(!force) {
                  throw dex;
               }
            }

            changeSheet0(oentry, ostorage, nentry, nstorage, true, callFireEvent);
         }
         finally {
            closeStorages();
         }
      }
      finally {
         writeLock.unlock();
      }
   }

   /**
    * Change one sheet internally.
    *
    * @param oentry the specified old sheet entry.
    * @param nentry the specified new sheet entry.
    * @param root   whether or not this is the root entry that changed
    * @param callFireEvent if send event
    */
   protected void changeSheet0(AssetEntry oentry, IndexedStorage ostorage,
                               AssetEntry nentry, IndexedStorage nstorage,
                               boolean root, boolean callFireEvent)
      throws Exception
   {
      changeSheet0(oentry, ostorage, nentry, nstorage, root, callFireEvent, null);
   }

   /**
    * Change one sheet internally.
    *
    * @param oentry the specified old sheet entry.
    * @param nentry the specified new sheet entry.
    * @param root   whether or not this is the root entry that changed
    * @param callFireEvent if send event
    */
   protected void changeSheet0(AssetEntry oentry, IndexedStorage ostorage,
                               AssetEntry nentry, IndexedStorage nstorage,
                               boolean root, boolean callFireEvent,
                               List<RenameDependencyInfo> renameDependencyInfos)
      throws Exception
   {
      String oidentifier = oentry.toIdentifier();
      String nidentifier = nentry.toIdentifier();
      AssetEntry opentry = oentry.getParent();
      AssetFolder opfolder = getParentFolder(oentry, ostorage);
      String opidentifier = opentry.toIdentifier();

      if(!opfolder.containsEntry(oentry)) {
         MessageException ex = new MessageException(catalog.getString(
            "common.notContainedEntry", opentry, oentry));
         ex.setKeywords("NOT_CONTAINED_ENTRY");
         throw ex;
      }

      AbstractSheet sheet = (AbstractSheet)
         ostorage.getXMLSerializable(oidentifier, null, oentry.getOrgID());
      Principal principal = ThreadContext.getContextPrincipal();

      // @by stephenwebster, related to bug1408723303556
      // Handle an exception case where a folder points to an asset that does
      // not exist. Code which loops through folder entries may fail to process
      // all entries if not handled.
      if(sheet == null) {
         LOG.warn("Unable to obtain entry by identifier: " +
         oentry.getPath());
         // if the old sheet is missing, we should remove the entry in folder
         // otherwise the agile recycle bin won't work
         opfolder.removeEntry(oentry);
         ostorage.putXMLSerializable(opidentifier, opfolder);
         ostorage.remove(oidentifier);
         return;
      }

      DependencyHandler dependencyHandler = DependencyHandler.getInstance();

      if(oentry.isWorksheet()) {
         RenameInfo rinfo = new RenameInfo(oidentifier, nidentifier,
                 (RenameInfo.ASSET | RenameInfo.SOURCE));
         Worksheet ws = (Worksheet) sheet;
         WSAssembly wsAssembly = ws.getPrimaryAssembly();

         if(wsAssembly instanceof BoundTableAssembly) {
            String opath = oentry.getPath() + "/" + wsAssembly.getAbsoluteName();
            String npath = nentry.getPath() + "/" + wsAssembly.getAbsoluteName();
            rinfo.setOldPath(opath);
            rinfo.setNewPath(npath);
         }

         if(renameDependencyInfos != null) {
            if(dependencyHandler.updateDependencies(oentry, nentry)) {
               renameDependencyInfos.add(dependencyHandler.getRenameDependencyInfo(oentry, nentry));
            }
         }
         else if(!Tool.equals(oentry, nentry)) {
            dependencyHandler.updateDependencies(oentry, nentry);
         }

         XFactory.getRepository().renameTransform(rinfo);
         RenameSheetEvent renameSheetEvent = new RenameSheetEvent(oentry);
         renameSheetEvent.setRenameInfo(rinfo);
         Cluster.getInstance().sendMessage(renameSheetEvent);
      }
      else {
         if(oidentifier != nidentifier) {
            nentry.setProperty("__bookmark_id__", null);
         }

         if(renameDependencyInfos != null) {
            if(dependencyHandler.updateDependencies(oentry, nentry)) {
               renameDependencyInfos.add(dependencyHandler.getRenameDependencyInfo(oentry, nentry));
            }
         }
         else if(!Tool.equals(oentry, nentry)) {
            dependencyHandler.renameDependencies(oentry, nentry);
         }
      }

      AssetEntry npentry = nentry.getParent();
      AssetFolder npfolder = getParentFolder(nentry, nstorage);
      String npidentifier = npentry.toIdentifier();
      opfolder = getParentFolder(oentry, ostorage);

      if(npentry.equals(opentry)) {
         opfolder = npfolder;
      }

      oentry = opfolder.getEntry(oentry);
      nentry.setProperty(AssetEntry.SHEET_DESCRIPTION,
                         oentry.getProperty(AssetEntry.SHEET_DESCRIPTION));

      if(nentry.isWorksheet()) {
         nentry.setProperty(AssetEntry.WORKSHEET_TYPE,
                            oentry.getProperty(AssetEntry.WORKSHEET_TYPE));
      }

      opfolder.removeEntry(oentry);

      if(!Objects.equals(opidentifier, npidentifier) || !Objects.equals(opfolder, npfolder)) {
         ostorage.putXMLSerializable(opidentifier, opfolder);
      }

      if(!Objects.equals(oidentifier, nidentifier)) {
         ostorage.remove(oidentifier);
         EmbeddedDataCacheHandler.clearWSCache(oidentifier);
      }

      npfolder.addEntry(nentry);

      if(nentry.getAlias() != null && oentry.getAlias() != null &&
         !oentry.getAlias().equals(nentry.getAlias()))
      {
         foldermap.remove(opidentifier);
      }

      nstorage.putXMLSerializable(npidentifier, npfolder);
      nstorage.putXMLSerializable(nidentifier, sheet);

      changeSheetDependents(sheet, oentry, nentry);

      if(oentry.getType() == AssetEntry.Type.VIEWSHEET) {
         renameVSBookmark(oentry, nentry);
      }

      //remove oidentifier last, otherwise dependencies might rewrite it
      if(!Objects.equals(oidentifier, nidentifier)) {
         ostorage.remove(oidentifier);
      }

      clearCache(oentry);
      clearCache(nentry);

      if(callFireEvent) {
         fireEvent(sheet.getType(), AssetChangeEvent.ASSET_RENAMED, nentry,
            oidentifier, root, sheet, "changeSheet: " + oentry + ", " + nentry);
      }
   }

   private boolean isRenameUser(AssetEntry oentry, AssetEntry nentry) {
      return oentry != null && nentry != null && !Tool.equals(oentry.getUser(), nentry.getUser());
   }

   /**
    * Change sheet dependents.
    *
    * @param sheet  the specified sheet.
    * @param oentry the specified old entry.
    * @param nentry the specified new entry.
    */
   private void changeSheetDependents(AbstractSheet sheet, AssetEntry oentry,
                                      AssetEntry nentry)
      throws Exception
   {
      if(Tool.equals(oentry, nentry)) {
         return;
      }

      AssetEntry[] entries = sheet.getOuterDependents();

      for(AssetEntry entry : entries) {
         String identifier = entry.toIdentifier();
         IndexedStorage storage = getStorage(entry);
         AbstractSheet dsheet = (AbstractSheet)
            storage.getXMLSerializable(identifier, null);

         if(dsheet != null) {
            dsheet.removeOuterDependency(oentry);
            dsheet.addOuterDependency(nentry);
            clearCache(entry);
            storage.putXMLSerializable(identifier, dsheet);
         }
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void removeSheet(AssetEntry entry, Principal user, boolean force) throws Exception {
      writeLock.lock();

      try {
         if(!entry.isSheet() || !supportsScope(entry.getScope()) ||
            !entry.isValid())
         {
            throw new MessageException(catalog.getString(
               "common.invalidEntry", entry));
         }

         IndexedStorage storage = entry.isRoot() ? null : getReportStorage(entry);

         if(storage == null) {
            throw new MessageException(catalog.getString(
               "common.invalidStorage", entry));
         }

         try {
            checkAssetPermission(user, entry, ResourceAction.DELETE);

            if(!force) {
               try {
                  checkSheetRemoveable(entry, user);
               }
               catch(DependencyException ex) {
                  throw ex;
               }
               catch(Throwable ex) {
                  LOG.warn("Error checking dependency: " + entry, ex);
               }
            }

            removeSheet0(entry, storage, false);
         }
         finally {
            closeStorages();
         }
      }
      finally {
         writeLock.unlock();
      }
   }

   /**
    * Remove one sheet internally.
    *
    * @param entry   the specified sheet entry.
    * @param storage the storage that contains the entry.
    * @param isFolder is whether or not folder delete.
    */
   protected void removeSheet0(AssetEntry entry, IndexedStorage storage, boolean isFolder)
      throws Exception
   {
      String identifier = entry.toIdentifier();
      AssetEntry pentry = entry.getParent();
      AssetFolder pfolder = getParentFolder(entry, storage);
      String pidentifier = pentry.toIdentifier();

      /* should allow folder to save in case things get out of sync
      if(!pfolder.containsEntry(entry)) {
         MessageException ex = new MessageException(catalog.getString(
            "common.notContainedEntry", pentry, entry));
         ex.setKeywords("NOT_CONTAINED_ENTRY");
         throw ex;
      }
      */

      fireEvent(-1, AssetChangeEvent.ASSET_TO_BE_DELETED, entry, null, true, null,
                "removeSheet: " + entry + (isFolder ? " in folder" : ""));

      if(pfolder != null) {
         pfolder.removeEntry(entry);
         storage.putXMLSerializable(pidentifier, pfolder);
      }

      AbstractSheet sheet = null;

      try {
         sheet = (AbstractSheet) storage.getXMLSerializable(identifier, null);
      }
      catch(Exception ex) {
         LOG.debug("Failed to parse: " + identifier, ex);
      }

      clearCache(entry);
      storage.remove(identifier);
      clearCache(entry);
      storage.remove(identifier);

      if(entry.getType() == AssetEntry.Type.VIEWSHEET) {
         clearVSBookmark(entry);
      }

      if(sheet != null) {
         fireEvent(sheet.getType(), AssetChangeEvent.ASSET_DELETED, entry, null, true, sheet,
                   "removeSheet: " + entry + (isFolder ? " in folder" : ""));
      }

      if(sheet instanceof Worksheet) {
         ((Worksheet) sheet).clearSnapshot();
      }

      DependencyHandler.getInstance().deleteDependencies(entry);
   }

   /**
    * Get the folder content of a folder.
    *
    * @param entry   the specified folder entry.
    * @param storage the specified indexed storage.
    * @return the folder content of the folder.
    */
   private AssetFolder getFolder(AssetEntry entry, IndexedStorage storage)
      throws Exception
   {
      String identifier = entry.toIdentifier();

      if(!entry.isFolder()) {
         MessageException ex = new MessageException(catalog.getString(
            "common.folderRequired", entry));
         ex.setKeywords("FOLDER_REQUIRED");
         throw ex;
      }

      AssetFolder folder;

      if(isMetadataAware(storage)) {
         folder = ((MetadataAwareStorage) storage).getAssetFolder(identifier);
      }
      else {
         folder = (AssetFolder) storage.getXMLSerializable(identifier, null);
      }

      if(folder == null) {
         if(entry.isRoot()) {
            folder = new AssetFolder();
         }
         else {
            if(storage.contains(identifier)) {
               storage.remove(identifier);
            }

            MessageException ex = new MessageException(catalog.getString(
               "common.folderNotFound", entry));
            ex.setKeywords("FOLDER_REQUIRED");
            throw ex;
         }
      }

      return folder;
   }

   /**
    * Get the parent folder content of an asset(sheet/folder).
    *
    * @param entry   the specified asset entry.
    * @param storage the specified indexed storage.
    * @return the parent folder content of the asset.
    */
   @SuppressWarnings("WeakerAccess")
   protected AssetFolder getParentFolder(AssetEntry entry, IndexedStorage storage) {
      if(entry.isRoot()) {
         MessageException ex = new MessageException(catalog.getString(
            "common.rootNoParent", entry));
         ex.setKeywords("FOLDER_REQUIRED");
         throw ex;
      }

      AssetEntry parent = entry.getParent();
      String pidentifier = parent.toIdentifier();
      String orgID = entry.getOrgID();
      Object folder = null;

      if(isMetadataAware(storage)) {
         folder = ((MetadataAwareStorage) storage).getAssetFolder(pidentifier);
      }
      else {
         try {
            folder = storage.getXMLSerializable(pidentifier, null, orgID);
         }
         catch(Throwable ex) {
            LOG.error("Unable to parse folder " + parent, ex);
         }
      }

      if(folder == null) {
         if(parent.isRoot()) {
            return new AssetFolder();
         }
         else {
            return null;
         }
      }
      else if(folder instanceof AssetFolder) {
         return (AssetFolder) folder;
      }

      return new AssetFolder();
   }

   @Override
   public void checkAssetPermission(Principal principal, AssetEntry entry, ResourceAction action)
      throws Exception
   {
      checkAssetPermission(principal, entry, action, false);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void checkAssetPermission(Principal principal,
                                    AssetEntry entry, ResourceAction permission,
                                    boolean checkUserAsset)
      throws Exception
   {
      if(IGNORE_PERM.get() != null && IGNORE_PERM.get()) {
         return;
      }

      if(!checkAssetPermission0(principal, entry, permission, checkUserAsset)) {
         switch(permission) {
         case READ:
            throw new MessageException(catalog.getString(
               "common.readAuthority", entry), LogLevel.INFO, false);
         case WRITE:
            Object message = entry.getProperty("_sheetType_");
            String msgPrefix = "security.nopermission.create";

            if(message == null) {
               message = entry;
               msgPrefix = "common.writeAuthority";
            }
            else {
               entry.setProperty("_sheetType_", null);
            }

            throw new MessageException(catalog.getString(msgPrefix, message), LogLevel.INFO, false);
         default:
            throw new MessageException(catalog.getString(
               "common.deleteAuthority", entry), LogLevel.INFO, false);
         }
      }
   }

   /**
    * Get the asset permission prefix.
    *
    * @param entry the specified asset entry.
    * @return the asset premission prefix of the asset entry.
    */
   protected final ResourceType getAssetResourceType(AssetEntry entry) {
      if(entry.isViewsheet() || entry.isRepositoryFolder()) {
         return ResourceType.REPORT;
      }
      else if(AssetUtil.isLibraryType(entry)) {
         return AssetUtil.getLibraryAssetType(entry);
      }
      else {
         return ResourceType.ASSET;
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean checkPermission(Principal principal, ResourceType type,
                                  String resource, EnumSet<ResourceAction> action)
   {
      return true;
   }

   /**
    * {@inheritDoc}
    */
   public boolean checkPermission(Principal principal, ResourceType type,
                                  IdentityID resource, EnumSet<ResourceAction> action)
   {
      return true;
   }

   /**
    * Check asset permission internally.
    *
    * @param user       the specified user.
    * @param entry      the specified asset entry.
    * @param permission the specified permission.
    * @return <tt>true</tt> if passed, <tt>false</tt> otherwise.
    */
   private boolean checkAssetPermission0(Principal user, AssetEntry entry,
                                         ResourceAction permission) throws Exception
   {
      return checkAssetPermission0(user, entry, permission, false);
   }

   /**
    * Check asset permission internally.
    *
    * @param user       the specified user.
    * @param entry      the specified asset entry.
    * @param permission the specified permission.
    * @param checkUserAsset if check user private asset permission.
    * @return <tt>true</tt> if passed, <tt>false</tt> otherwise.
    */
   private boolean checkAssetPermission0(Principal user, AssetEntry entry,
                                         ResourceAction permission, boolean checkUserAsset)
      throws Exception
   {
      if(IGNORE_PERM.get() != null && IGNORE_PERM.get()) {
         return true;
      }

      //give permission if default org globally visible
      if(Tool.equals(permission, ResourceAction.READ) && SUtil.isDefaultVSGloballyVisible(user) &&
                           Organization.getDefaultOrganizationID().equals(entry.getOrgID()) &&
                           user != null && !((XPrincipal)user).getOrgId().equals(Organization.getDefaultOrganizationID())) {
         return true;
      }

      //reject if attempting to get another organization's assets
      if(!OrganizationManager.getInstance().isSiteAdmin(user) && user != null &&
         !((XPrincipal)user).getOrgId().equalsIgnoreCase(entry.getOrgID())) {
         return false;
      }

      //reject if non site admin accessing another's private repo
      if(checkUserAsset && !OrganizationManager.getInstance().isSiteAdmin(user) && user != null &&
         entry.getScope() == AssetRepository.USER_SCOPE &&
         !(user.getName().equals(entry.getUser().convertToKey())) )
      {
         return false;
      }

      ResourceType type = getAssetResourceType(entry);
      String path = entry.getPath();

      if(user == null || permission == null) {
         return true;
      }
      else if(entry.getScope() == REPORT_SCOPE) {
         return true;
      }
      else if(entry.getScope() == USER_SCOPE) {
         return (user.getName().equals(entry.getUser().convertToKey()) ||
            checkPermission(user, ResourceType.SECURITY_USER, entry.getUser(), EnumSet.of(ResourceAction.ADMIN))) &&
            checkPermission(user, ResourceType.MY_DASHBOARDS, "*", EnumSet.of(ResourceAction.READ));
      }
      else if(entry.getScope() == QUERY_SCOPE) {
         if(entry.isPartition()) {
            return checkPartitionPermission(user, entry);
         }
         else if(entry.isLogicModel()) {
            return checkLogicalModelPermission(user, entry, permission);
         }
         else if(entry.isExtendedLogicModel() || entry.isExtendedPartition()) {
            return checkExtendedModelPermission(user, entry, permission);
         }

         type = ResourceType.QUERY;

         if(entry.isQuery()) {
            int idx = path.lastIndexOf('/');

            if(idx != -1) {
               path = path.substring(idx + 1);
            }
         }
      }

      // @by yanie: bug1426103908289
      // Check physical table permission properly
      if(entry.getType() == AssetEntry.Type.PHYSICAL_TABLE) {
         return checkPermission(
            user, ResourceType.PHYSICAL_TABLE, "*", EnumSet.of(ResourceAction.ACCESS));
      }

      if(AssetUtil.isLibraryType(entry)) {
         path = AssetUtil.getLibraryPermissionPath(entry, type);
      }

      return checkPermission(user, type, path, EnumSet.of(permission));
   }

   /**
    * Check permission for logical model.
    * @param user       the specified user.
    * @param entry      the specified asset entry.
    * @param permission the specified permission.
    * @return <tt>true</tt> if passed, <tt>false</tt> otherwise.
    */
   private boolean checkLogicalModelPermission(Principal user, AssetEntry entry,
                                               ResourceAction permission)
   {
      if(entry == null) {
         return false;
      }

      String path = entry.getPath();

      String dsname = entry.getProperty("prefix");
      String folder = entry.getProperty("folder");
      folder = "/".equals(folder) ? null : folder;

      if(dsname == null || folder == null) {
         int idx = path.lastIndexOf("::");

         if(idx > 0) {
            dsname = path.substring(idx + 2);

            idx = dsname.lastIndexOf(XUtil.DATAMODEL_FOLDER_SPLITER);

            if(idx > 0) {
               folder = dsname.substring(idx + XUtil.DATAMODEL_FOLDER_SPLITER.length());
               dsname = dsname.substring(0, idx);
            }
         }
      }

      path = entry.getName() + "::" + dsname;
      path += (folder == null ? "" : XUtil.DATAMODEL_FOLDER_SPLITER + folder);
      return checkPermission(user, ResourceType.QUERY, path, EnumSet.of(permission));
   }

   /**
    * Check permission for partition.
    *
    * Just display a view but not edit it is meaningless, so display or edit view always
    * need write permission of its parent.
    *
    * @param user       the specified user.
    * @param entry      the specified asset entry.
    * @return <tt>true</tt> if passed, <tt>false</tt> otherwise.
    */
   private boolean checkPartitionPermission(Principal user, AssetEntry entry) {
      if(entry == null) {
         return false;
      }

      ResourceType type = ResourceType.DATA_SOURCE;
      String path = entry.getPath();
      String folder = entry.getProperty("folder");
      String dsname = entry.getProperty("prefix");

      if(dsname == null) {
         int idx = path.lastIndexOf(XUtil.DATAMODEL_FOLDER_SPLITER);

         if(idx > 0) {
            folder = path.substring(idx + 2);
            dsname = dsname.substring(0, idx);
         }
         else {
            dsname = path;
         }
      }

      if(folder != null) {
         type = ResourceType.DATA_MODEL_FOLDER;
         path = dsname + (folder == null ? "" : "/" + folder);
      }
      else {
         path = dsname;
      }

      return checkPermission(user, type, path, EnumSet.of(ResourceAction.WRITE));
   }

   /**
    * Check permission for extended logical model/partition.
    * @param user       the specified user.
    * @param entry      the specified asset entry.
    * @param permission the specified permission.
    * @return <tt>true</tt> if passed, <tt>false</tt> otherwise.
    */
   private boolean checkExtendedModelPermission(Principal user, AssetEntry entry,
                                                ResourceAction permission)
   {
      if(entry == null) {
         return false;
      }

      String prefix = entry.getProperty("prefix");
      String extended = entry.getProperty(XUtil.DATASOURCE_ADDITIONAL);
      extended = StringUtils.isEmpty(extended) ? entry.getName() : extended;

      // extended requires read permission of the additional source.
      if(!StringUtils.isEmpty(extended) && !"(Default Connection)".equals(extended) &&
         !checkPermission(user, ResourceType.DATA_SOURCE, prefix + "::" + extended,
         EnumSet.of(ResourceAction.READ)))
      {
         return false;
      }

      // Just display a view but not edit it is meaningless, so display or edit view
      // always need write permission of its parent.
      if(entry.isExtendedPartition()) {
         permission = ResourceAction.WRITE;
      }

      String folder = entry.getProperty("folder");
      ResourceType type = folder != null ?
         ResourceType.DATA_MODEL_FOLDER : ResourceType.DATA_SOURCE;
      String path = folder != null ? prefix + "/" + folder : prefix;

      if(permission == ResourceAction.WRITE) {
         return checkPermission(user, type, path, EnumSet.of(permission));
      }

      // if parent has write permission, then extended has r/d/w permission.
      return checkPermission(user, type, path, EnumSet.of(permission)) ||
         checkPermission(user, type, path, EnumSet.of(ResourceAction.WRITE));
   }

   /**
    * Close all the storages.
    */
   @SuppressWarnings("WeakerAccess")
   protected void closeStorages() {
      IndexedStorage[] storages = getStorages();

      for(IndexedStorage storage : storages) {
         storage.close();
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public IndexedStorage getStorage(AssetEntry entry) throws Exception {
      return istore;
   }

   /**
    * Get all the indexed storages require close.
    *
    * @return all the indexed storages.
    */
   @SuppressWarnings("WeakerAccess")
   protected IndexedStorage[] getStorages() {
      return new IndexedStorage[]{istore};
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public AssetEntry[] getSheetDependencies(AssetEntry entry, Principal user)
      throws Exception
   {
      String identifier = entry.toIdentifier();

      if(!entry.isSheet() || !supportsScope(entry.getScope()) ||
         !entry.isValid() || entry.getScope() == QUERY_SCOPE)
      {
         throw new MessageException(catalog.getString(
            "common.invalidEntry", entry));
      }

      IndexedStorage storage = getStorage(entry);

      if(storage == null) {
         throw new MessageException(catalog.getString(
            "common.invalidStorage", entry));
      }

      try {
         checkAssetPermission(user, entry, ResourceAction.READ);

         AssetEntry pentry = entry.getParent();
         AssetFolder pfolder = getParentFolder(entry, storage);

         if(!pfolder.containsEntry(entry)) {
            MessageException ex = new MessageException(catalog.getString(
               "common.notContainedEntry", pentry, entry));
            ex.setKeywords("NOT_CONTAINED_ENTRY");
            throw ex;
         }

         XMLSerializable obj = storage.getXMLSerializable(identifier, null);

         if(!(obj instanceof AbstractSheet)) {
            return new AssetEntry[0];
         }

         AbstractSheet sheet = (AbstractSheet) obj;
         AssetEntry[] entries = sheet.getOuterDependencies();
         int olength = entries.length;
         entries = getExistingEntries(entries, entry);
         boolean shrinked = olength != entries.length;

         // shrinked? save the change
         if(shrinked) {
            sheet.removeOuterDependencies();

            for(AssetEntry existing : entries) {
               sheet.addOuterDependency(existing);
            }

            storage.putXMLSerializable(identifier, sheet);
         }

         return Arrays.stream(entries)
            .filter(e -> !RecycleUtils.isInRecycleBin(e.getPath()))
            .toArray(AssetEntry[]::new);
      }
      finally {
         closeStorages();
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void allowsFolderScopeChange(AssetEntry entry, int nscope,
                                       Principal user) throws Exception
   {
      if(!entry.isFolder() || !supportsScope(entry.getScope()) ||
         !entry.isValid() || entry.isRoot() || entry.getScope() == QUERY_SCOPE)
      {
         throw new MessageException(catalog.getString(
            "common.invalidEntry", entry));
      }

      IndexedStorage storage = getStorage(entry);

      if(storage == null) {
         throw new MessageException(catalog.getString(
            "common.invalidStorage", entry));
      }

      try {
         checkAssetPermission(user, entry, ResourceAction.READ);
         allowsFolderScopeChange0(entry, nscope, storage);
      }
      finally {
         storage.close();
      }
   }

   /**
    * Check if to change a folder scope is allowed.
    *
    * @param entry   the specified folder entry.
    * @param nscope  the specified new scope to change to.
    * @param storage the specified index storage.
    */
   private void allowsFolderScopeChange0(AssetEntry entry, int nscope,
                                         IndexedStorage storage)
      throws Exception
   {
      AssetFolder folder = getFolder(entry, storage);
      AssetEntry[] entries = folder.getEntries();
      DependencyException ex = new DependencyException(null);

      for(AssetEntry childEntry : entries) {
         try {
            if(childEntry.isFolder()) {
               allowsFolderScopeChange0(childEntry, nscope, storage);
            }
            else {
               allowsSheetScopeChange0(childEntry, nscope);
            }
         }
         catch(DependencyException dex) {
            ex.addDependency(dex);
         }
      }

      if(!ex.isEmpty()) {
         throw ex;
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void allowsSheetScopeChange(AssetEntry entry, int nscope,
                                      Principal user) throws Exception
   {
      if(!entry.isSheet() || !supportsScope(entry.getScope()) ||
         !entry.isValid() || entry.getScope() == QUERY_SCOPE)
      {
         throw new MessageException(catalog.getString(
            "common.invalidEntry", entry));
      }

      if(!supportsScope(nscope) || nscope == QUERY_SCOPE) {
         throw new MessageException(catalog.getString(
            "common.invalidStorage", nscope + ""));
      }

      checkAssetPermission(user, entry, ResourceAction.READ);

      try {
         allowsSheetScopeChange0(entry, nscope);
      }
      finally {
         closeStorages();
      }
   }

   /**
    * Check if to change a sheet scope is allowed.
    *
    * @param entry  the specified sheet entry.
    * @param nscope the specified new scope to change to.
    */
   private void allowsSheetScopeChange0(AssetEntry entry, int nscope)
      throws Exception
   {
      IndexedStorage storage = getStorage(entry);

      if(storage == null) {
         throw new MessageException(catalog.getString(
            "common.invalidStorage", entry));
      }

      AssetEntry pentry = entry.getParent();
      AssetFolder pfolder = getParentFolder(entry, storage);

      if(!pfolder.containsEntry(entry)) {
         MessageException ex = new MessageException(catalog.getString(
            "common.notContainedEntry", pentry, entry));
         ex.setKeywords("NOT_CONTAINED_ENTRY");
         throw ex;
      }

      int oscope = entry.getScope();
      String identifier = entry.toIdentifier();
      AbstractSheet sheet = (AbstractSheet)
         storage.getXMLSerializable(identifier, AssetContent.NO_DATA, entry.getOrgID());

      // @by stephenwebster, related to bug1408723303556
      // Handle an exception case where a folder points to an asset that does
      // not exist. Code which loops through folder entries may fail to process
      // all entries if not handled.
      if(sheet == null) {
         LOG.warn("Unable to obtain entry by identifier: " +
                 entry.getPath());
         return;
      }

      AssetEntry[] entries = sheet.getOuterDependencies();
      getExistingEntries(entries, entry);
      DependencyException ex = new DependencyException(entry);

      entries = sheet.getOuterDependents();
      StringBuilder sb = new StringBuilder();

      for(int i = 0; i < entries.length; i++) {
         if(i > 0) {
            sb.append(",");
         }

         sb.append(entries[i]);
      }

      if(oscope == USER_SCOPE) {
         // 1. user scope --> global scope
         // 2. user scope --> report scope
         // do not depend on user_scope sheet
         if(nscope == GLOBAL_SCOPE || nscope == REPORT_SCOPE) {
            for(AssetEntry dependent : entries) {
               if(dependent.getScope() == USER_SCOPE) {
                  throw new MessageException(catalog.getString(
                     "common.userScopeForbidden", entry, sb));
               }
            }
         }
         // 3. user scope --> user scope
         // allowed
      }
      else if(oscope == REPORT_SCOPE) {
         // 4. report scope --> user scope
         // 5. report scope --> global scope
         // do not depend on report_scope sheet
         if(nscope == GLOBAL_SCOPE || nscope == USER_SCOPE) {
            for(AssetEntry dependent : entries) {
               if(dependent.getScope() == REPORT_SCOPE) {
                  throw new MessageException(catalog.getString(
                     "common.reportScopeForbidden", dependent));
               }
            }
         }
         // 6. report scope --> report scope
         // allowed
      }
      // 7. global scope --> report scope
      // 8. global scope --> user scope
      // 9. global scope --> global scope
      // allowed

      if(!ex.isEmpty()) {
         throw ex;
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public synchronized void dispose() {
      if(disposed.compareAndSet(false, true)) {
         if(istore != null) {
            istore.close();
            istore.removeStorageRefreshListener(refreshListener);
         }
      }
   }

   @Override
   public final void close() {
      dispose();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void addAssetChangeListener(AssetChangeListener listener) {
      synchronized(listeners) {
         listeners.put(listener, "");
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void removeAssetChangeListener(AssetChangeListener listener) {
      synchronized(listeners) {
         listeners.remove(listener);
      }
   }

   /**
    * Notify all registered listeners that an asset entry has been modified.
    *
    * @param entryType  the type of entry to which the change was made.
    * @param changeType the type of change that was made to the entry.
    * @param assetEntry the modified asset entry.
    * @param oldName    the old name of the entry, of <code>null</code> if the
    *                   entry was not renamed.
    * @param root       whether or not this is the root entry that changed
    */
   protected void fireEvent(int entryType, int changeType, AssetEntry assetEntry,
                            String oldName, boolean root, AbstractSheet sheet, String reason)
   {
      AssetChangeEvent event = null;
      List<AssetChangeListener> listeners;

      synchronized(this.listeners) {
         listeners = new ArrayList<>(this.listeners.keySet());
      }

      for(int i = listeners.size() - 1; i >= 0; i--) {
         AssetChangeListener listener = listeners.get(i);

         if(listener == null) {
            continue;
         }

         if(event == null) {
            event = new AssetChangeEvent(this, entryType, changeType,
                                         assetEntry, oldName, root, sheet, reason);
         }

         try {
            listener.assetChanged(event);
         }
         catch(Exception exc) {
            LOG.error("Failed to handle asset changed event", exc);
         }
      }
   }

   public void fireAutoSaveEvent(AssetEntry entry) {
      if(entry == null) {
         fireEvent(Viewsheet.VIEWSHEET_ASSET, AssetChangeEvent.AUTO_SAVE_ADD,
            null, null, true, null, "autoSave: " + entry);
         return;
      }

      if(entry.getScope() ==  AssetRepository.TEMPORARY_SCOPE) {
         fireEvent(Viewsheet.VIEWSHEET_ASSET, AssetChangeEvent.AUTO_SAVE_ADD,
                 entry, null, true, null, "autoSave: " + entry);
      }

   }

   @Override
   protected void finalize() throws Throwable {
      dispose();
      super.finalize();
   }

   /**
    * Get the existing asset entries.
    *
    * @param entries the specified asset entries.
    * @param dentry  the specified dependent asset entry.
    * @return the existing asset entries.
    */
   private AssetEntry[] getExistingEntries(AssetEntry[] entries, AssetEntry dentry)
      throws Exception
   {
      List<AssetEntry> list = new ArrayList<>();

      // filter out nonexistent sheet
      for(AssetEntry entry : entries) {
         try {
            IndexedStorage storage = getStorage(entry);

            if(storage == null || !storage.contains(entry.toIdentifier())) {
               continue;
            }
         }
         catch(Exception e) {
            LOG.warn(e.getMessage());
            continue;
         }


         AbstractSheet sheet = getSheet(entry, null, false, AssetContent.NO_DATA);

         if(sheet == null) {
            LOG.warn("Sheet corresponding to entry {} could not be read.", entry.toIdentifier());
            continue;
         }

         AssetEntry[] dentries = sheet.getOuterDependents();

         for(AssetEntry dentry1 : dentries) {
            if(dentry1.equals(dentry)) {
               list.add(entry);
               break;
            }
         }
      }

      return list.toArray(new AssetEntry[0]);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void renameUser(IdentityID oname, IdentityID nname) throws Exception {
      if(oname == null || nname == null || Tool.equals(oname, nname)) {
         return;
      }

      AssetEntry oentry = new AssetEntry(AssetRepository.USER_SCOPE,
         AssetEntry.Type.FOLDER, "/", oname);
      AssetEntry nentry = new AssetEntry(AssetRepository.USER_SCOPE,
         AssetEntry.Type.FOLDER, "/", nname);
      IndexedStorage ostorage = getStorage(oentry);
      IndexedStorage nstorage = getStorage(nentry);
      changeFolder0(oentry, ostorage, nentry, nstorage, true);

      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.REPOSITORY_FOLDER, "/", oname);
      IndexedStorage storage = getStorage(entry);
      renameUserBookmarks(entry, storage, oname, nname);
   }

   /**
    * Rename the user bookmarks for global viewsheets.
    * @param  entry     the global asset entry.
    * @param  ouser     the old user name.
    * @param  nuser     the new user name.
    */
   private void renameUserBookmarks(AssetEntry entry, IndexedStorage storage,
                                    IdentityID ouser, IdentityID nuser)
      throws Exception
   {
      AssetFolder folder;

      try {
         folder = getFolder(entry, storage);
      }
      catch(Exception ignore) {
         // folder does nto exist;
         return;
      }

      AssetEntry[] entries = folder.getEntries();

      for(int i = 0; entries != null && i < entries.length; i++) {
         if(entries[i].isViewsheet()) {
            AssetEntry obentry = getVSBookmarkEntry(entries[i], ouser, true);
            AssetEntry nbentry = getVSBookmarkEntry(entries[i], nuser, true);
            renameVSBookmark0(obentry, nbentry);
         }
         else if(entries[i].isFolder()) {
            renameUserBookmarks(entries[i], storage, ouser, nuser);
         }
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void removeUser(IdentityID identityID) throws Exception {
      AssetEntry entry = new AssetEntry(AssetRepository.USER_SCOPE,
         AssetEntry.Type.FOLDER, "/", identityID);
      IndexedStorage storage = getStorage(entry);

      removeFolder0(entry, storage, true);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public List<IdentityID> getBookmarkUsers(AssetEntry entry) throws Exception {
      AssetEntry bookmarkEntry = getVSBookmarkEntry(entry, null);
      IndexedStorage storage = getStorage(entry);
      final ArrayList<IdentityID> userList = new ArrayList<>();
      SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();

      if(bookmarkEntry == null) {
         // A User Scoped VS, should only have bookmarks from the user who
         // owns the VS.
         if(entry.getScope() == USER_SCOPE && entry.getUser() != null) {
            userList.add(entry.getUser());
         }

         return userList;
      }

      final String viewsheetPath = bookmarkEntry.getPath();

      IndexedStorage.Filter filter = key -> {
         AssetEntry entry0 = AssetEntry.createAssetEntry(key);

         if(entry0 != null &&
            entry0.getType() == AssetEntry.Type.VIEWSHEET_BOOKMARK &&
            entry0.getUser() != null && entry0.getPath().equals(viewsheetPath))
         {
            AssetEntry bEntry = AssetEntry.createAssetEntry(key);

            if(bEntry != null) {
               userList.add(bEntry.getUser());
            }

            return true;
         }

         return false;
      };

      storage.getKeys(filter, entry.getOrgID());

      return userList.stream().filter(id ->  provider.getOrganization(id.orgID) != null).sorted().toList();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public VSBookmark getVSBookmark(AssetEntry entry, Principal user)
      throws Exception
   {
      return getVSBookmark(entry, user, false);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public VSBookmark getVSBookmark(AssetEntry entry, Principal user, boolean ignoreCache)
      throws Exception
   {
      IdentityID pId = IdentityID.getIdentityIDFromKey(user.getName());
      AssetEntry bentry = getVSBookmarkEntry(entry, user == null ? null : IdentityID.getIdentityIDFromKey(user.getName()));

      if(entry == null) {
         throw new RuntimeException("Invalid entry found: null");
      }

      if(bentry == null) {
         return null;
      }

      String bidentifier = bentry.toIdentifier();
      VSBookmark bookmark = ignoreCache ? null : bookmarkmap.get(bidentifier);

      if(bookmark == null) {
         try {
            IndexedStorage storage = getStorage(bentry);

            if(storage == null) {
               throw new MessageException(catalog.getString(
                        "common.invalidStorage", bentry));
            }

            Object obj = storage.getXMLSerializable(bidentifier, null, bentry.getOrgID());

            // fix bug1368731629994
            if(obj instanceof VSBookmark) {
               bookmark = (VSBookmark) obj;
            }
            else if(obj != null) {
               LOG.debug("Failed to read bookmark: " + bidentifier + " [" +
                  obj.getClass().getName() + "]", new Exception("Stack trace"));
            }

            if(bookmark == null) {
               bookmark = new VSBookmark();
            }

            bookmark.setIdentifier(entry.toIdentifier());
            bookmark.setUser(user == null ? null : pId);
            bookmarkmap.put(bidentifier, bookmark);
         }
         finally {
            closeStorages();
         }
      }

      return bookmark;
   }

   /**
    * Get the corresponding viewsheet bookmark entry of a viewsheet entry.
    *
    * @param entry the entry of the specified viewsheet.
    * @param user  the specified user.
    * @return the corresponding viewsheet bookmark entry of the viewsheet entry.
    */
   private AssetEntry getVSBookmarkEntry(AssetEntry entry, IdentityID user) {
      return getVSBookmarkEntry(entry, user, false);
   }

   /**
    * Get the corresponding viewsheet bookmark entry of a viewsheet entry.
    *
    * @param entry the entry of the specified viewsheet.
    * @param user  the specified user.
    * @param ignoreUserName if true don't need to check uer name else not.
    * @return the corresponding viewsheet bookmark entry of the viewsheet entry.
    */
   protected AssetEntry getVSBookmarkEntry(AssetEntry entry, IdentityID user, boolean ignoreUserName) {
      if(entry == null || entry.getType() == AssetEntry.Type.VIEWSHEET_SNAPSHOT) {
         return null;
      }

      if(entry.getType() != AssetEntry.Type.VIEWSHEET) {
         throw new RuntimeException("invalid asset entry found: " + entry);
      }

      if(entry.getScope() == USER_SCOPE && !ignoreUserName &&
         !Tool.equals(entry.getUser(), user))
      {
         return null;
      }

      String bookmarkId = entry.getProperty("__bookmark_id__");
      String orgID = entry.getOrgID();

      // optimization
      if(bookmarkId == null) {
         bookmarkId = VSUtil.createBookmarkIdentifier(entry);
         entry.setProperty("__bookmark_id__", bookmarkId);
      }

      return new AssetEntry(USER_SCOPE, AssetEntry.Type.VIEWSHEET_BOOKMARK, bookmarkId, user, orgID);
   }

   /**
    * Get all bookmark entries corresponding to the specified viewsheet entry.
    */
   protected List<AssetEntry> getVSBookmarkEntries(AssetEntry entry)
      throws Exception
   {
      if(getStorage(entry) == null) {
         return new ArrayList<>();
      }

      AssetEntry bookmarkEntry = getVSBookmarkEntry(entry, entry.getUser());
      String viewsheetPath = bookmarkEntry.getPath();
      List<AssetEntry> entries = new ArrayList<>();

      IndexedStorage.Filter filter = key -> {
         AssetEntry entry0 = AssetEntry.createAssetEntry(key);

         if(entry0 != null && entry0.getType() == AssetEntry.Type.VIEWSHEET_BOOKMARK &&
            entry0.getPath().equals(viewsheetPath))
         {
            entries.add(entry0);
            return true;
         }

         return false;
      };

      getStorage(entry).getKeys(filter, entry.getOrgID());

      return entries;
   }

   /**
    * Rename the viewsheet bookmark.
    *
    * @param oentry the entry of the original viewsheet.
    * @param nentry the entry of the new viewsheet.
    */
   private void renameVSBookmark(AssetEntry oentry, AssetEntry nentry) throws Exception {
      // @by stephenwebster, Bug #35814
      // Renaming bookmarks on user scoped assets is done as part of the renameUser process
      if(isRenameUser(oentry, nentry)) {
         return;
      }

      Principal principal = ThreadContext.getContextPrincipal();

      if(SUtil.isInternalUser(principal)) {
         IdentityID[] users = XUtil.getUsers();

         for(IdentityID user : users) {
            AssetEntry obentry = getVSBookmarkEntry(oentry, user);
            AssetEntry nbentry = getVSBookmarkEntry(nentry, user);
            renameVSBookmark0(obentry, nbentry);
         }
      }
      else {
         List<AssetEntry> bookmarkEntries = getVSBookmarkEntries(oentry);

         for(AssetEntry bookmarkEntry : bookmarkEntries) {
            AssetEntry nbentry = getVSBookmarkEntry(nentry, bookmarkEntry.getUser());
            renameVSBookmark0(bookmarkEntry, nbentry);
         }
      }
   }

   /**
    * Rename the viewsheet bookmark.
    * @param obentry the original bookmark entry.
    * @param nbentry the new bookmark entry.
    */
   protected void renameVSBookmark0(AssetEntry obentry, AssetEntry nbentry) throws Exception {
      if(obentry == null || nbentry == null) {
         return;
      }

      String obidentifier = obentry.toIdentifier();
      String nbidentifier = nbentry.toIdentifier();
      IndexedStorage ostorage = getStorage(obentry);

      if(ostorage == null) {
         throw new MessageException(catalog.getString(
            "common.invalidStorage", obentry));
      }

      VSBookmark bookmark = (VSBookmark)
         ostorage.getXMLSerializable(obidentifier, null);

      if(bookmark == null) {
         return;
      }

      clearCache(obentry);
      clearCache(nbentry);
      ostorage.remove(obidentifier);

      IndexedStorage nstorage = getStorage(nbentry);

      if(nstorage == null) {
         throw new MessageException(catalog.getString(
            "common.invalidStorage", nbentry));
      }

      nstorage.putXMLSerializable(nbidentifier, bookmark);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void clearVSBookmark(AssetEntry entry) throws Exception {
      List<IdentityID> userList = getBookmarkUsers(entry);

      for(IdentityID user : userList) {
         AssetEntry bentry = getVSBookmarkEntry(entry, user);

         if(bentry == null) {
            continue;
         }

         String bidentifier = bentry.toIdentifier();
         IndexedStorage storage = getStorage(bentry);

         if(storage == null) {
            throw new MessageException(catalog.getString(
               "common.invalidStorage", bentry));
         }

         clearCache(bentry);
         storage.remove(bidentifier);
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setVSBookmark(AssetEntry entry, VSBookmark bookmark,
                             Principal user)
      throws Exception
   {
      AssetEntry bentry = getVSBookmarkEntry(entry,
         user == null ? null : IdentityID.getIdentityIDFromKey(user.getName()));

      if(bentry == null) {
         throw new RuntimeException("Invalid entry found: " + entry);
      }

      String bidentifier = bentry.toIdentifier();

      try {
         IndexedStorage storage = getStorage(bentry);

         if(storage == null) {
            throw new MessageException(catalog.getString(
               "common.invalidStorage", bentry));
         }

         clearCache(bentry);
         Cluster.getInstance().sendMessage(new ClearAssetCacheEvent(bentry));
         storage.putXMLSerializable(bidentifier, bookmark);
      }
      finally {
         closeStorages();
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public ReportSheet getCurrentReport() {
      return null;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public String getFileName() {
      return null;
   }

   /**
    *  Get asset entry of the current report sheet.
    */
   @Override
   public AssetEntry getAssetEntry() {
      return null;
   }

   /**
    * For view sheet, overwrite bookmarks as well.
    */
   private void overwriteBookmarks(AbstractSheet sheet, AssetEntry entry2,
                                   Principal user)
      throws Exception
   {
      if(!(sheet instanceof Viewsheet)) {
         return;
      }

      Viewsheet vsheet = (Viewsheet) sheet;
      AssetEntry entry1 = vsheet.getRuntimeEntry();

      if(entry1 == null || entry2 == null) {
         return;
      }

      if(entry1.getType() != AssetEntry.Type.VIEWSHEET ||
         entry2.getType() != AssetEntry.Type.VIEWSHEET) {
         return;
      }

      if(entry1.getScope()  == AssetRepository.TEMPORARY_SCOPE &&
         getVSBookmark(entry2, user).getBookmarks().length != 0)
      {
         return;
      }

      String userName = user == null ? null : user.getName();
      String lockKey = VSBookmark.getLockKey(entry2.toIdentifier(), userName);
      Cluster cluster = Cluster.getInstance();
      cluster.lockKey(lockKey);

      try {
         VSBookmark book1 = getVSBookmark(entry1, user, true);
         setVSBookmark(entry2, book1, user);
      }
      finally {
         cluster.unlockKey(lockKey);
      }
   }

   /**
    * Clear the cache for the entry.
    */
   @Override
   public void clearCache(AssetEntry entry) {
      String eidentifier = getEntryIdentifier(entry);
      contextmap.remove(eidentifier);
      sheetmap.remove(eidentifier);
      foldermap.remove(eidentifier);
      // key for bookmark is not on sheet entry so we just clear it out
      bookmarkmap.clear();
   }

   protected void initStorageRefreshListener() {
      if(istore == null) {
         LOG.debug("Indexed storage was null when initializing storage refresh listener.");
         return;
      }

      istore.addStorageRefreshListener(refreshListener);
   }

   private int convertTimestampIndexChangeType(TimestampIndexChangeType change) {
      final int changeType;

      switch(change) {
         case ADD:
            changeType = AssetChangeEvent.ASSET_RENAMED;
            break;
         case REMOVE:
            changeType = AssetChangeEvent.ASSET_DELETED;
            break;
         case MODIFY:
            changeType = AssetChangeEvent.ASSET_MODIFIED;
            break;
         default:
            throw new IllegalStateException("Unexpected value: " + change);
      }

      return changeType;
   }

   private static boolean isMetadataAware(IndexedStorage storage) {
      return (storage instanceof MetadataAwareStorage) &&
         ((MetadataAwareStorage) storage).isMetadataEnabled();
   }

   @Override
   public void fireExposeDefaultOrgPropertyChange() {
      AssetEntry root = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.REPOSITORY_FOLDER, "/", null);

      fireEvent(Viewsheet.VIEWSHEET_ASSET, AssetChangeEvent.ASSET_MODIFIED, root, null, true, null, "");
   }

   public static final ThreadLocal<String> LOCAL = new ThreadLocal<>();
   protected int[] scopes; // supported scopes
   protected IndexedStorage istore; // default indexed storage
   protected WeakReference<AssetRepository> parent; // parent asset engine
   protected Catalog catalog = Catalog.getCatalog();
   private final Map<AssetChangeListener,Object> listeners = new WeakHashMap<>();
   // context cache
   private DataCache<String, AbstractSheet> contextmap = new DataCache<>(5000, 3600000);
   // sheet cache
   private DataCache<String, AbstractSheet> sheetmap = new DataCache<>(100, 3600000);
   // folder cache
   private DataCache<String, AssetFolder> foldermap = new DataCache<>(5000, 3600000);
   private DataCache<String, VSBookmark> bookmarkmap = new DataCache<>(200, 3600000);
   private long lastMod = 0;
   private static final String TABLE_STYLE = "Table Styles";
   private static final String SCRIPT = "Scripts";

   private final ReentrantLock writeLock = new ReentrantLock();
   private final AtomicBoolean disposed = new AtomicBoolean(false);

   private final StorageRefreshListener refreshListener = event -> {
      final List<TimestampIndexChange> changes = event.getChanges();

      if(changes != null) {
         for(final TimestampIndexChange change : changes) {
            final String key = change.getKey();
            final AssetEntry entry = AssetEntry.createAssetEntry(key);

            if(entry != null) {
               final int changeType = convertTimestampIndexChangeType(change.getChange());
               fireEvent(entry.getType().id(), changeType, entry, entry.toIdentifier(), true, null,
                         "StorageRefresh: " + entry);
            }
         }
      }
   };

   private static final Logger LOG =
      LoggerFactory.getLogger(AbstractAssetEngine.class);
}
