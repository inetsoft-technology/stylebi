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
package inetsoft.report.internal.binding;

import inetsoft.util.gui.ObjectInfo;
import inetsoft.util.gui.SVGIcon;
import inetsoft.report.*;
import inetsoft.report.internal.*;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.internal.TableAssemblyInfo;
import inetsoft.uql.erm.*;
import inetsoft.uql.schema.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.tabular.TabularDataSource;
import inetsoft.uql.util.Config;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.util.*;
import inetsoft.web.RecycleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Binding tree model.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class BindingTreeModel implements TreeModel, Serializable {
   /**
    * Create a binding tree model.
    */
   public BindingTreeModel(Context context) {
      super();
      this.context = context;
   }

   /**
    * Set the principal.
    * @param user the specified principal.
    */
   public void setPrincipal(Principal user) {
      this.user = user;
   }

   /**
    * Get the principal.
    * @return the principal of the binding tree model.
    */
   public Principal getPrincipal() {
      return user;
   }

   /**
    * Check the query read permission.
    * @param qname the specified query name.
    * @return <tt>true</tt> if allowed, <tt>false</tt> otherwise.
    */
   protected boolean checkQueryPermission(String qname) {
      return true;
   }

   /**
    * Check the data model folder read permission.
    * @param folder the specified folder name.
    * @param datasource the specified datasource name.
    * @return <tt>true</tt> if allowed, <tt>false</tt> otherwise.
    */
   protected boolean checkDataModelFolderPermission(String folder, String datasource) {
      //check data model folder permission by data model folder registry.
      return doCheckDataModelFolderPermission(folder, datasource);
   }

   protected final boolean doCheckDataModelFolderPermission(String folder, String datasource) {
      return SUtil.checkDataModelFolderPermission(folder, datasource, getPrincipal());
   }

   /**
    * Check the data source read permission.
    * @param dname the specified data source name.
    * @return <tt>true</tt> if allowed, <tt>false</tt> otherwise.
    */
   protected boolean checkDataSourcePermission(String dname) {
      //check datasource permission by datasource registry.
      return true;
   }

   /**
    * Check the data source folder read permission.
    * @param folder the specified data source folder name.
    * @return <tt>true</tt> if allowed, <tt>false</tt> otherwise.
    */
   protected boolean checkDataSourceFolderPermission(String folder) {
      //check datasource folder permission by datasource registry.
      return true;
   }

   /**
    * Get the root node of the tree.
    * @return the root node of the tree.
    */
   @Override
   public RootFolder getRoot() {
      if(root != null) {
         return root;
      }

      synchronized(this) {
         if(root != null) {
            return root;
         }

         root = new RootFolder();
      }

      return root;
   }

   /**
    * Get the child node of a parent node at an index.
    * @param parent the specified parent node.
    * @param index the specified index.
    * @return the child node of the parent node at the index.
    */
   @Override
   public Object getChild(Object parent, int index) {
      Entry entry = (Entry) parent;
      boolean sort = !(entry instanceof LogicalModelEntry || entry instanceof EntityEntry || entry instanceof AssetData);
      Entry[] entries = getEntries(entry, sort);

      return entries[index];
   }

   /**
    * Get the child node count of a parent node.
    * @param parent the specified parent node.
    * @return the child node count of the parent node.
    */
   @Override
   public int getChildCount(Object parent) {
      Entry entry = (Entry) parent;
      Entry[] entries = getEntries(entry, false);
      return entries.length;
   }

   /**
    * Check a node if is a leaf node.
    * @param node the specified node.
    * @return <tt>true</tt> if is a leaf node, false otherwise.
    */
   @Override
   public boolean isLeaf(Object node) {
      Entry entry = (Entry) node;
      return !(entry instanceof Folder) || entry.isLeaf();
   }

   /**
    * Notified when a node is changed.
    * @param path the specified tree path identifies the node.
    * @param val the specified new value.
    */
   @Override
   public void valueForPathChanged(TreePath path, Object val) {
      throw new RuntimeException("Unsupported method is called!");
   }

   /**
    * Get the index of a child node belongs to a parent node.
    * @param parent the specified parent node.
    * @param child the specified child node.
    * @return the index of the child node.
    */
   @Override
   public int getIndexOfChild(Object parent, Object child) {
      Entry entry = (Entry) parent;
      boolean sort = !(entry instanceof LogicalModelEntry || entry instanceof EntityEntry);
      Entry[] entries = getEntries(entry, sort);

      for(int i = 0; i < entries.length; i++) {
         if(entries[i].equals(child)) {
            return i;
         }
      }

      return -1;
   }

   /**
    * Add a tree model listener.
    * @param listener the specified tree model listenr.
    */
   @Override
   public void addTreeModelListener(TreeModelListener listener) {
      if(!listeners.contains(listener)) {
         listeners.add(listener);
      }
   }

   /**
    * Remove a tree model listener.
    * @param listener the specified tree model listenr.
    */
   @Override
   public void removeTreeModelListener(TreeModelListener listener) {
      listeners.remove(listener);
   }

   /**
    * Refresh the tree model.
    */
   public void refresh() {
      rentries = null;
      rentries_s = null;
      RootFolder root = getRoot();
      root.clear();
      TreePath path = getTreePath(root);
      TreeModelEvent event = new TreeModelEvent(this, path);

      for(TreeModelListener listener : listeners) {
         try {
            listener.treeStructureChanged(event);
         }
         catch(Exception ex) {
            LOG.warn("Failed to process tree structure changed event on refresh", ex);
         }
      }
   }

   /**
    * Get the tree path of an entry.
    * @param entry the specified entry.
    * @return the tree path of the entry.
    */
   public TreePath getTreePath(Entry entry) {
      List<Entry> paths = new ArrayList<>();

      for(;;) {
         paths.add(0, entry);
         entry = entry.getParent();

         if(entry == null) {
            break;
         }
      }

      Entry[] entries = new Entry[paths.size()];
      paths.toArray(entries);
      return new TreePath(entries);
   }

   /**
    * Get the tree path.
    */
   public TreePath getTreePath(SourceAttr attr) {
      Entry entry = getEntry(attr);
      return entry == null ? null : getTreePath(entry);
   }

   /**
    * Get the entry.
    */
   public Entry getEntry(SourceAttr source) {
      int type = source.getType();
      String sname = source.getSource();
      String sprefix = source.getPrefix();

      switch(type) {
      case XSourceInfo.NONE:
         return null;
      case XSourceInfo.MODEL:
         return new LogicalModelEntry(sname, sprefix);
      case XSourceInfo.ASSET:
         try {
            return dataCache.get(sname);
         }
         catch(Exception ex) {
            LOG.error("Failed to get asset for entry: " + sname,
               ex);
         }

         return null;
      default:
         throw new RuntimeException("Unsupported type found: " + type);
      }
   }

   /**
    * Check whether the source has cache data.
    * @param source SourceAttr.
    * @return true if source has cache else false.
    */
   public boolean hasCache(SourceAttr source) {
      if(source == null) {
         return false;
      }

      int type = source.getType();
      String sname = source.getSource();

      return type == XSourceInfo.ASSET && dataCache.contains(sname);
   }

   /**
    * Get the sub entries of a folder entry.
    * @param entry the specified folder entry.
    * @return the sub entries of the folder entry.
    */
   private Entry[] getEntries(Entry entry, boolean sort) {
      if(!(entry instanceof Folder)) {
         return new Entry[0];
      }

      // @by billh, performance optimization
      if(entry instanceof RootFolder) {
         return ((RootFolder) entry).getEntries(sort);
      }

      Entry[] entries = ((Folder) entry).getEntries();

      if(sort) {
         Arrays.sort(entries);
      }

      return entries;
   }

   private final class AssetCache extends ResourceCache<String, AssetData> {
      AssetCache() {
         super();
      }

      @Override
      public AssetData create(String key) throws Exception {
         AssetEntry entry = AssetEntry.createAssetEntry(key);

         if(entry == null) {
            return null;
         }

         AssetRepository engine = context.getAssetRepository();

         if(!listening.getAndSet(true)) {
            engine.addAssetChangeListener(assetChangeListener);
         }

         engine = new RuntimeAssetEngine(engine, context.getReport());

         if(!engine.containsEntry(entry)) {
            return null;
         }

         return new AssetData(entry);
      }

      private AtomicBoolean listening = new AtomicBoolean(false);
      private final AssetChangeListener assetChangeListener =
         new AssetChangeListener() {
            @Override
            public void assetChanged(AssetChangeEvent event) {
               dataCache.clear();
            }
         };
   }

   /**
    * Entry cache.
    */
   protected class EntryCache extends ResourceCache {
      public EntryCache() {
         super();
      }

      /**
       * create typenode from a table and a column.
       */
      private static XTypeNode createTypeNode(TableLens table, String colname) {
         int col = Util.findColumn(table, colname);
         String type = Util.getDataType(table, col);
         XTypeNode node = XSchema.createPrimitiveType(type);
         node = node == null ? new StringType() : node;
         node.setName(colname);
         return node;
      }

      @Override
      public Object create(Object key) throws Exception {
         List<Entry> list = new ArrayList<>();

         if(key instanceof AssetFolder) {
            AssetFolder folder = (AssetFolder) key;
            AssetEntry entry = folder.getAssetEntry();
            AssetRepository engine = context.getAssetRepository();

            if(!listening.getAndSet(true)) {
               engine.addAssetChangeListener(assetChangeListener);

               DataSourceRegistry.getRegistry().addModifiedListener(evt -> EntryCache.this.clear());
            }

            engine = new RuntimeAssetEngine(engine, context.getReport());
            AssetEntry.Selector selector = new AssetEntry.Selector(
               AssetEntry.Type.FOLDER, AssetEntry.Type.WORKSHEET);
            AssetEntry[] entries = engine.getEntries(entry, user, ResourceAction.READ, selector);

            for(AssetEntry assetEntry : entries) {
               if(assetEntry.isFolder()) {
                  if(context.isAll() || !isEmpty(assetEntry, selector, engine)) {
                     list.add(new AssetFolder(assetEntry));
                  }

                  continue;
               }

               String val = assetEntry.getProperty(AssetEntry.WORKSHEET_TYPE);
               val = val == null ? Worksheet.TABLE_ASSET + "" : val;
               int type = Integer.parseInt(val);

               if(context.isAll() ||
                  (type == Worksheet.TABLE_ASSET &&
                     assetEntry.isReportDataSource())) {
                  list.add(new AssetData(assetEntry));
               }
            }
         }

         // only show column should to get query column entries
         if(context.isShowColumn()) {
            if(key instanceof AssetData) {
               Entry entry = (Entry) key;
               DesignSession session = context.getDesignSession();

               try {
                  TableLens lens = session.getQueryMetaData(entry.getName(),
                     context.getReport(), entry.getType(), null);
                  AssetEntry aentry = null;
                  boolean iscross = false;

                  if(key instanceof AssetData) {
                     aentry = ((AssetData) key).getAssetEntry();
                  }
                  else if(key instanceof ReportDataEntry) {
                     iscross = ((ReportDataEntry) key).isCrosstab();
                  }

                  // if the report element is not yet in report sheet,
                  // the returned table might be null, in this case
                  // get base table from the report element directly
                  Groupable elem = context.getElement() instanceof Groupable ?
                     (Groupable) context.getElement() : null;

                  if(lens != null) {
                     lens.moreRows(1);
                     Set<String> used = new HashSet<>();
                     TableDataDescriptor des = lens.getDescriptor();

                     for(int i = 0; i < lens.getColCount() && lens.moreRows(0); i++) {
                        String cname = Util.getHeader(lens, i).toString();

                        if(used.contains(cname)) {
                           continue;
                        }

                        Object obj = lens.getObject(0, i);
                        used.add(cname);

                        // fix bug1185344010562, if found column header is null
                        // in calctable, use the first row cell to replace it
                        if(cname.equals("Column [" + i + "]") &&
                           obj != null && obj.toString().length() > 0)
                        {
                           cname = lens.getObject(0, i) + "";
                        }

                        ColumnEntry col;
                        int refType = -1;
                        String defFormula = null;

                        if(des instanceof XNodeMetaTable.TableDataDescriptor2) {
                           XNodeMetaTable.TableDataDescriptor2 desc2 =
                              (XNodeMetaTable.TableDataDescriptor2) des;
                           refType = desc2.getRefType(cname);
                           defFormula = desc2.getDefaultFormula(cname);
                        }

                        XTypeNode type = createTypeNode(lens, cname);

                        if(aentry == null) {
                           String ds = entry.getDataSource();

                           // query columns
                           if(key instanceof ReportDataEntry) {
                              ds = entry.getName();
                           }

                           col = new ColumnEntry(cname, ds,
                              entry.getName(), entry.getType(), type.getType());
                        }
                        else {
                           // worksheet columns
                           col = new ColumnEntry(cname, aentry, entry.getType(),
                              refType, defFormula, type.getType());
                        }

                        col.setCross(iscross);

                        if(!list.contains(col)) {
                           list.add(col);
                        }
                     }
                  }
               }
               catch(Exception ex) {
                  LOG.error("Failed to get query columns when " +
                     "creating entry cache element: " + entry, ex);
               }
            }
         }

         Entry[] result = new Entry[list.size()];
         list.toArray(result);
         return result;
      }

      private boolean isEmpty(AssetEntry entry, AssetEntry.Selector selector,
                              AssetRepository engine)
         throws Exception
      {
         if(!entry.isFolder()) {
            String val = entry.getProperty(AssetEntry.WORKSHEET_TYPE);
            val = val == null ? Worksheet.TABLE_ASSET + "" : val;
            int type = Integer.parseInt(val);

            return !(context.isAll() ||
               (type == Worksheet.TABLE_ASSET && entry.isReportDataSource()));
         }

         AssetEntry[] entries = engine.getEntries(entry, user, ResourceAction.READ, selector);

         for(AssetEntry assetEntry : entries) {
            if(!isEmpty(assetEntry, selector, engine)) {
               return false;
            }
         }

         return true;
      }

      private AtomicBoolean listening = new AtomicBoolean(false);
      private final AssetChangeListener assetChangeListener = event -> EntryCache.this.clear();
   }

   /**
    * Entry.
    */
   public abstract class Entry implements Serializable, Comparable<Entry> {
      public abstract Entry getParent();
      public abstract String getName();
      public abstract int getType();
      public abstract Icon getIcon();
      public abstract String getIconPath();
      public abstract int score();
      //Add a temp method to separate the new flat icon and existing old
      //style icon. If all icons are update the flat style, we'd better
      //remove this method and change all getIconPath() method accordingly.
      protected boolean isFlatIcon = false;

      //Add a temp method to separate the new flat icon and existing old
      //style icon. If all icons are update the flat style, we'd better
      //remove this method and change all getIconPath() method accordingly.
      public void setFlat(boolean isFlatIcon) {
         this.isFlatIcon = isFlatIcon;
      }

      public Icon getIcon(boolean expanded) {
         return getIcon();
      }

      public String getIconClass() {
         return null;
      }

      public Icon getCommonIcon(boolean expanded) {
         return getIcon(expanded);
      }

      public Icon getCommonIcon() {
         return getIcon();
      }

      public boolean isEditable() {
         return false;
      }

      public String getDataSource() {
         return "";
      }

      public String getDescription() {
         return "";
      }

      public String getLabel() {
         Catalog catalog = Catalog.getCatalog();
         return catalog.getString(getName());
      }

      public String getFullName() {
         return getName();
      }

      public String getTooltip() {
         return null;
      }

      public AssetEntry createAssetEntry() {
         return null;
      }

      @Override
      public String toString() {
         return getLabel();
      }

      public boolean isLeaf() {
         return true;
      }

      public String toView() {
         StringBuilder buf = new StringBuilder();

         Principal user = ThreadContext.getContextPrincipal();
         Catalog catalog = Catalog.getCatalog(user, Catalog.REPORT);
         buf.append(catalog.getString(getLabel()));
         return buf.toString();
      }

      @Override
      public int compareTo(Entry entryb) {
         Entry entrya = this;
         int scorea = entrya.score();
         int scoreb = entryb.score();

         if(scorea != scoreb) {
            return scorea - scoreb;
         }

         String namea = entrya.getLabel();
         String nameb = entryb.getLabel();
         return namea.compareToIgnoreCase(nameb);
      }
   }

   // This code prevents a memory leak caused by adding listeners to the data
   // source registry that reference every binding tree model instance ever
   // created. The listener now only holds a weak reference to the binding tree
   // model and when it is only weakly reachable, removes the listener from the
   // data source registry. Phantom references are used instead of a finalize
   // method to prevent the well-documented problem of resurrecting garbage
   // collected objects. We can't just check the reference for null in the
   // listener method and remove the listener there because it will mess up the
   // iteration in the event firing loop.
   private static final class BindingTreeModelReference extends Cleaner.Reference<BindingTreeModel> {
      BindingTreeModelReference(BindingTreeModel model, DataSourceRegistry registry,
                                RegistryListener listener)
      {
         super(model);
         this.registry = new WeakReference<>(registry);
         this.listener = listener;
      }

      @Override
      public void close()  {
         DataSourceRegistry dsr = registry.get();

         if(dsr != null) {
            dsr.removeRefreshedListener(listener);
         }
      }

      private final WeakReference<DataSourceRegistry> registry;
      private final RegistryListener listener;
   }

   private static final class RegistryListener implements PropertyChangeListener {
      RegistryListener(BindingTreeModel model) {
         this.model = new WeakReference<>(model);
      }

      @Override
      public void propertyChange(PropertyChangeEvent evt) {
         BindingTreeModel model = this.model.get();
         if(evt != null &&
            "QueryRegistry".equals(evt.getPropertyName()) &&
            model != null)
         {
            model.rentries = null;
            model.rentries_s = null;
         }
      }

      private final WeakReference<BindingTreeModel> model;
   }

   /**
    * Folder.
    */
   public abstract class Folder extends Entry {
      public abstract Entry[] getEntries();

      public Entry[] getEntries(Principal principal) {
         // no-op. for logical model apply vpm implements.
         return getEntries();
      }

      @Override
      public boolean isLeaf() {
         return false;
      }

      @Override
      public String getIconClass() {
         return "folder";
      }

      @Override
      public int getType() {
         return XSourceInfo.NONE;
      }
   }

   /**
    * Root folder.
    */
   public class RootFolder extends Folder {
      public RootFolder() {
         DataSourceRegistry registry = DataSourceRegistry.getRegistry();
         RegistryListener listener = new RegistryListener(BindingTreeModel.this);
         registry.addRefreshedListener(listener);
         Cleaner.add(new BindingTreeModelReference(BindingTreeModel.this, registry, listener));
      }

      public void clear() {
         rentries = null;
         rentries_s = null;
      }

      @Override
      public Entry[] getEntries() {
         return getEntries(false);
      }

      public Entry[] getEntries(boolean sort) {
         if(rentries == null) {
            List<Entry> list = new ArrayList<>();
            ReportFolder rfolder = new ReportFolder();

            if(context.isDisplayEmptyReportScope() ||
               rfolder.getEntries().length > 0)
            {
               list.add(rfolder);
            }

            try {
               if(context.isDisplayAsset()) {
                  AssetEntry gentry = AssetEntry.createGlobalRoot();
                  AssetRepository asset = context.getAssetRepository();

                  if(asset.supportsScope(AssetRepository.GLOBAL_SCOPE)) {
                     AssetFolder gfolder = new AssetFolder(gentry);
                     list.add(gfolder);
                  }
               }

               list.add(new DataSourceFolderEntry(null));
            }
            catch(Exception ex) {
               LOG.error("Failed to get child entries of root entry", ex);
            }

            Entry[] entries = new Entry[list.size()];
            list.toArray(entries);
            rentries = entries;
         }

         if(!sort) {
            return filter(rentries);
         }
         else {
            if(rentries_s == null) {
               rentries_s = rentries.clone();
               Arrays.sort(rentries_s);
            }

            return filter(rentries_s);
         }
      }

      @Override
      public Entry getParent() {
         return null;
      }

      @Override
      public String getName() {
         Catalog catalog = Catalog.getCatalog();
         return catalog.getString("Root");
      }

      @Override
      public Icon getIcon() {
         return null;
      }

      @Override
      public String getIconPath() {
         return null;
      }

      @Override
      public int score() {
         return 0;
      }

      public boolean equals(Object obj) {
         return obj.getClass() == RootFolder.class;
      }

      public int hashCode() {
         return RootFolder.class.hashCode();
      }
   }

   /**
    * Report folder.
    */
   public class ReportFolder extends Folder {
      @Override
      public Entry getParent() {
         return getRoot();
      }

      @Override
      public Entry[] getEntries() {
         List<Entry> list = new ArrayList<>();
         Entry[] entries = new Entry[list.size()];
         list.toArray(entries);

         return filter(entries);
      }

      @Override
      public String getName() {
         Catalog catalog = Catalog.getCatalog();
         return catalog.getString("Report");
      }

      @Override
      public Icon getIcon() {
         return new SVGIcon(Config.getReportIcon());
      }

      @Override
      public String getIconPath() {
         return null;
      }

      @Override
      public int score() {
         return 0;
      }

      public boolean equals(Object obj) {
         return obj.getClass() == ReportFolder.class;
      }

      public int hashCode() {
         return ReportFolder.class.hashCode();
      }

      @Override
      public String getTooltip() {
         AssetRepository assetRep = context.getAssetRepository();
         ReportSheet sheet = assetRep.getCurrentReport();
         String filename = assetRep.getFileName();
         String reportname = "";
         Catalog catalog = Catalog.getCatalog();

         if(filename != null) {
            reportname = filename.lastIndexOf("\\") > -1 ?
               filename.substring(filename.lastIndexOf("\\") + 1) : filename;
            reportname = reportname.lastIndexOf(".") > -1 ?
               reportname.substring(0, reportname.lastIndexOf(".")) :
               reportname;
         }

         String name = catalog.getString("Report") + ": " + reportname;
         String title = null;
         String author = null;

         if(sheet != null) {
            title = sheet.getProperty("report.title");
            author = sheet.getProperty("report.author");
         }

         return getReportTooltip(name, title, author);
      }
   }

   public class ReportDataFolder extends Folder {
      @Override
      public Entry getParent() {
         return new ReportFolder();
      }

      @Override
      public Entry[] getEntries() {
         List<Entry> list = new ArrayList<>();
         Enumeration<?> elems = ElementIterator.elements(context.getReport());
         String[] ts = {"", SourceAttr.ROTATED};

         while(elems.hasMoreElements()) {
            ReportElement temp = (ReportElement) elems.nextElement();

            if(temp instanceof NonScalar) {
               // if the element is null, will put all the ReportElement(s) to
               // the report folder
               if(context.getElement() != null &&
                  temp.getID().equals(context.getElement().getID()))
               {
                  continue;
               }

               boolean isCalc = temp instanceof TableElementDef && ((TableElementDef) temp).isCalc();

               if(isCalc) {
                  continue;
               }

               boolean rotated = context.getElement() instanceof SectionElement;
               int count = (rotated ? ts.length - 1 : ts.length);

               for(int i = 0; i < count; i++) {
                  String name = temp.getID() + ts[i];
                  list.add(new ReportDataEntry(name, false));
               }
            }
         }

         Entry[] entries = new Entry[list.size()];
         list.toArray(entries);
         return filter(entries);
      }

      @Override
      public String getName() {
         Catalog catalog = Catalog.getCatalog();
         return catalog.getString("Element");
      }

      @Override
      public Icon getIcon() {
         return new SVGIcon(Config.getReportIcon());
      }

      @Override
      public String getIconPath() {
         return null;
      }

      @Override
      public int score() {
         return 1;
      }

      public boolean equals(Object obj) {
         return obj.getClass() == ReportDataFolder.class;
      }

      public int hashCode() {
         return ReportDataFolder.class.hashCode();
      }
   }

   /**
    * Report data entry.
    */
   public class ReportDataEntry extends Folder implements Query {
      public ReportDataEntry(String name, boolean crosstab) {
         this.name = name;
         this.crosstab = crosstab;
      }

      @Override
      public Entry getParent() {
         return new ReportDataFolder();
      }

      @Override
      public String getName() {
         return name;
      }

      @Override
      public int getType() {
         return XSourceInfo.REPORT;
      }

      public boolean isCrosstab() {
         return this.crosstab;
      }

      @Override
      public Icon getIcon() {
         return new SVGIcon(getIconPath());
      }

      @Override
      public String getIconPath() {
         getType();
         return null != null ? "formula.svg" : "report.svg";
      }

      @Override
      public Icon getCommonIcon() {
         return new SVGIcon("report.svg");
      }

      @Override
      public int score() {
         return 0;
      }

      public boolean equals(Object obj) {
         if(!(obj instanceof ReportDataEntry)) {
            return false;
         }

         ReportDataEntry entry2 = (ReportDataEntry) obj;
         return Tool.equals(name, entry2.name);
      }

      public int hashCode() {
         return name.hashCode();
      }

      @Override
      public boolean isLeaf() {
         return !context.isShowColumn();
      }

      @Override
      public Entry[] getEntries() {
         try {
            Entry[] centries = (Entry[]) cache.get(this);
            getType();
            Entry[] formulas = null;

            if(formulas != null) {
               return (Entry[]) Tool.mergeArray(formulas, centries);
            }

            return centries;
         }
         catch(Exception ex) {
            LOG.error("Failed to get child entries of report data entry: " + this, ex);
         }

         return new Entry[0];
      }

      private String getMainType() {
         return ObjectInfo.COMPONENT;
      }

      @Override
      public AssetEntry createAssetEntry() {
         String path = LOCAL_QUERY + "/" + name;
         AssetEntry aentry = new AssetEntry(AssetRepository.REPORT_SCOPE,
            AssetEntry.Type.TABLE, path, null);
         aentry.setProperty("mainType", getMainType());
         aentry.setProperty("prefix", "");
         aentry.setProperty("source", getName());

         return aentry;
      }

      private String name;
      private boolean crosstab;
   }

   /**
    * Data source folder.
    */
   public class DataSourceFolder extends Folder {
      public DataSourceFolder(String name) {
         this.name = name;
      }

      @Override
      public Entry getParent() {
         if(name == null || !name.contains("/")) {
            return new DataSourceFolderEntry(null);
         }

         String entry = name.substring(0, name.lastIndexOf('/'));

         return new DataSourceFolderEntry(entry);
      }

      private String getMainType() {
         return ObjectInfo.DATA_SOURCE;
      }

      public String getSubType() {
         try {
            XDataSource dx = context.getRepository().getDataSource(name);

            if(dx != null) {
               return dx.getType();
            }
         }
         catch(Exception ex) {
            LOG.error("Failed to get sub-type of data source folder: " + this, ex);
         }

         return "";
      }

      @Override
      public AssetEntry createAssetEntry() {
         AssetEntry aentry = new AssetEntry(AssetRepository.QUERY_SCOPE,
            AssetEntry.Type.DATA_SOURCE, getFullName(), null);
         aentry.setProperty("mainType", getMainType());
         aentry.setProperty("subType", getSubType());
         aentry.setProperty("prefix", getFullName());
         aentry.setProperty("source", getFullName());
         //@temp by sunnyhe add datasource.type to entry.
         aentry.setProperty(AssetEntry.DATA_SOURCE_TYPE, getSubType());

         return aentry;
      }

      @Override
      public Entry[] getEntries() {
         try {
            List<Entry> list = new ArrayList<>();
            processModel(list);
            Entry[] entries = new Entry[list.size()];
            list.toArray(entries);

            return filter(entries);
         }
         catch(Exception ex) {
            LOG.error("Failed to get child entries of data source folder: " + this,
               ex);
         }

         return new Entry[0];
      }

      @Override
      public String getName() {
         Catalog catalog = Catalog.getCatalog();

         if(name == null) {
            return catalog.getString("Data Source");
         }

         return inetsoft.uql.DataSourceFolder.getDisplayName(name);
      }

      @Override
      public String getFullName() {
         if(name == null) {
            return getName();
         }

         return name;
      }

      @Override
      public Icon getIcon() {
         try {
            XDataSource dx = context.getRepository().getDataSource(name, false);

            if(dx != null) {
               return new SVGIcon(Objects.requireNonNull(Config.getIconResource(dx.getType())));
            }
         }
         catch(Exception ex) {
            LOG.error("Failed to get icon for data source folder: " + this, ex);
         }

         return null;
      }

      @Override
      public String getIconPath() {
         try {
            XDataSource dx = context.getRepository().getDataSource(name, false);
            String type = (dx instanceof TabularDataSource) ? "tabular" : dx.getType();
            return isFlatIcon ?
                "/inetsoft/sree/portal/images/modern/" + type + ".gif" :
                "/inetsoft/uql/gui/images/" + type + ".gif";
         }
         catch(Exception ex) {
            LOG.error("Failed to get icon path for data source folder: " + this,
               ex);
         }

         return null;
      }

      @Override
      public String getIconClass() {
         try {
            XDataSource dx = context.getRepository().getDataSource(name, false);

            if(dx instanceof TabularDataSource) {
               return "tabular";
            }
            else {
               return dx.getType();
            }
         }
         catch(Exception ex) {
            LOG.error("Failed to get icon class for data source folder: " + this,
               ex);
         }

         return null;
      }

      @Override
      public String getDescription() {
         try {
            XDataSource dx = context.getRepository().getDataSource(name);

            if(dx != null) {
               return dx.getDescription() == null ? "" : dx.getDescription();
            }
         }
         catch(Exception ex) {
            LOG.error("Failed to get description of data source folder: " + this,
               ex);
         }

         return "";
      }

      @Override
      public int score() {
         return 4;
      }

      public boolean equals(Object obj) {
         if(!(obj instanceof DataSourceFolder)) {
            return false;
         }

         DataSourceFolder entry2 = (DataSourceFolder) obj;
         return Tool.equals(getFullName(), entry2.getFullName());
      }

      public int hashCode() {
         return name.hashCode();
      }

      protected void processModel(List<Entry> list) throws Exception {
         ReportElement elem = context.getElement();
         if(context.getElement() == null ||
            !((BaseElement) context.getElement()).isInSection() ||
            !(elem instanceof TextBased) ||
            (context.getElement() instanceof TextBased) && context.portal())
         {
            XRepository repository = context.getRepository();
            XDataModel model = repository.getDataModel(name);

            if(model != null) {
               for(String lname : model.getLogicalModelNames()) {
                  XLogicalModel logicalModel = model.getLogicalModel(lname);
                  String folder = logicalModel != null && logicalModel.getFolder() != null ?
                     logicalModel.getFolder() : null;

                  if(!checkDataSourcePermission(model.getDataSource())) {
                     continue;
                  }

                  if(folder != null && !checkDataModelFolderPermission(folder, model.getDataSource())) {
                     continue;
                  }

                  String res = folder != null ? "__^" + folder + "^" + lname + "::" +
                     model.getDataSource() : lname + "::" + model.getDataSource();

                  if(!checkQueryPermission(res)) {
                     continue;
                  }

                  if(folder != null) {
                     Entry folderEntry = new DataModelFolder(logicalModel.getFolder(), name);

                     if(!list.contains(folderEntry)) {
                        list.add(folderEntry);
                     }
                  }
                  else {
                     list.add(new LogicalModelEntry(lname, name));
                  }
               }
            }
         }
      }

      @Override
      public String getTooltip() {
         Catalog catalog = Catalog.getCatalog();
         String desc = getDescription();
         return (desc != null && !desc.isEmpty()) ? desc
            : catalog.getString("Data Source") + ": " + getName();
      }

      private String name;
   }

   /**
    * Data source folder entry.
    */
   public class DataSourceFolderEntry extends Folder {
      public DataSourceFolderEntry(String name) {
         this.name = name;
      }

      @Override
      public Entry getParent() {
         if(name == null) {
            return getRoot();
         }
         else if(!name.contains("/")) {
            return new DataSourceFolderEntry(null);
         }

         String entry = name.substring(0, name.lastIndexOf('/'));

         return new DataSourceFolderEntry(entry);
      }

      @Override
      public Entry[] getEntries() {
         List<Entry> list = new ArrayList<>();
         XRepository repository = context.getRepository();
         String[] children;

         try {
            children = repository.getSubfolderNames(name);

            for(String child : children) {
               if(checkDataSourceFolderPermission(child)) {
                  list.add(new DataSourceFolderEntry(child));
               }
            }

            children = repository.getSubDataSourceNames(name);

            for(String child : children) {
               XDataSource dx = repository.getDataSource(child);

               if(!context.isDisplayXMLADataSource() && dx != null &&
                  Tool.equals(dx.getType(), XDataSource.XMLA))
               {
                  continue;
               }

               DataSourceFolder dxFolder = new DataSourceFolder(child);

               // @by billh, performance - please do not check whether sub
               // entries exist, for there might be many many queries under a
               // data source. When collecting folders, every query needs to
               // be loaded, the process is too heavy and very slow...
               if(context.isDisplayEmptyDataSource() ||
                  checkDataSourcePermission(dxFolder.getFullName()))
               {
                  list.add(dxFolder);
               }
            }
         }
         catch(Exception ex) {
            LOG.error("Failed to get child entries of data source folder entry: " +
               this, ex);
         }

         return filter(list.toArray(new Entry[list.size()]));
      }

      @Override
      public int score() {
         return 3;
      }

      @Override
      public String getName() {
         Catalog catalog = Catalog.getCatalog();

         if(name == null) {
            return catalog.getString("Data Source");
         }

         return inetsoft.uql.DataSourceFolder.getDisplayName(name);
      }

      @Override
      public String getFullName() {
         if(name == null) {
            return getName();
         }

         return name;
      }

      @Override
      public String getIconPath() {
         try {
            return name == null ? "data-source-folder.svg" : "folder.svg";
         }
         catch(Exception ex) {
            LOG.error("Failed to get icon path for data source folder entry: " + this, ex);
         }

         return null;
      }

      @Override
      public Icon getIcon() {
         try {
            return new SVGIcon(getIconPath());
         }
         catch(Exception ex) {
            LOG.error("Failed to get icon for data source folder entry: " + this, ex);
         }

         return null;
      }

      public boolean equals(Object obj) {
         if(!(obj instanceof DataSourceFolderEntry)) {
            return false;
         }

         DataSourceFolderEntry entry2 = (DataSourceFolderEntry) obj;

         return Tool.equals(getFullName(), entry2.getFullName());
      }

      private String name;
   }

   /**
    * Data model folder.
    */
   public class DataModelFolder extends Folder {
      public DataModelFolder(String name, String dx) {
         this.name = name;
         this.dx = dx;
      }

      @Override
      public Entry[] getEntries() {
         try {
            List<Entry> list = new ArrayList<>();
            processModel(list);
            Entry[] entries = new Entry[list.size()];
            list.toArray(entries);
            return filter(entries);
         }
         catch(Exception ex) {
            LOG.error("Failed to get child entries of query entry: " + this, ex);
         }

         return new Entry[0];
      }

      protected void processModel(List<Entry> list) throws Exception {
         ReportElement elem = context.getElement();
         if(context.getElement() == null ||
            !((BaseElement) context.getElement()).isInSection() ||
            !(elem instanceof TextBased))
         {
            XRepository repository = context.getRepository();
            XDataModel model = repository.getDataModel(dx);

            if(model != null) {
               for(String lname: model.getLogicalModelNames()) {
                  if(!checkDataSourcePermission(model.getDataSource())) {
                     continue;
                  }

                  if(!checkQueryPermission(lname + "::" +
                     model.getDataSource()))
                  {
                     continue;
                  }

                  XLogicalModel logicalModel = model.getLogicalModel(lname);

                  if(logicalModel != null && name != null && name.equals(logicalModel.getFolder())) {
                     list.add(new LogicalModelEntry(lname, dx));
                  }
               }
            }
         }
      }

      @Override
      public Entry getParent() {
         return new DataSourceFolder(dx);
      }

      @Override
      public String getDataSource() {
         return dx;
      }

      @Override
      public String getName() {
         return name;
      }

      @Override
      public Icon getIcon(boolean expanded) {
         String imagePath = getIconPath();

         if(expanded) {
            imagePath = imagePath.replaceAll("folder.svg", "folder-open.svg");
         }

         return new SVGIcon(imagePath);
      }

      @Override
      public Icon getIcon() {
         return new SVGIcon(getIconPath());
      }

      @Override
      public String getIconPath() {
         return "folder.svg";
      }

      @Override
      public String getIconClass() {
         return "folder";
      }

      @Override
      public int score() {
         return 0;
      }

      @Override
      public AssetEntry createAssetEntry() {
         String path = dx + "/" + name;
         AssetEntry aentry = new AssetEntry(AssetRepository.QUERY_SCOPE,
            AssetEntry.Type.DATA_MODEL_FOLDER, path, null);
         aentry.setProperty("prefix", dx);
         aentry.setProperty("source", dx);

         return aentry;
      }

      public boolean equals(Object obj) {
         if(!(obj instanceof DataModelFolder)) {
            return false;
         }

         DataModelFolder entry2 = (DataModelFolder) obj;
         return Tool.equals(name, entry2.name) && Tool.equals(dx, entry2.dx);
      }

      public int hashCode() {
         return name.hashCode() ^ dx.hashCode();
      }

      @Override
      public String getTooltip() {
         Catalog catalog = Catalog.getCatalog();
         return catalog.getString("Folder") + ": " + getName();
      }

      private String name;
      private String dx;
   }

   /**
    * Logical model entry.
    */
   public class LogicalModelEntry extends Folder implements Query {
      public LogicalModelEntry(String name, String dx) {
         this(name, dx, null);
      }

      public LogicalModelEntry(String name, String dx, String label) {
         this.name = name;
         this.dx = dx;
         this.label = label;
      }

      @Override
      public Entry getParent() {
         try {
            XDataModel dataModel = context.getRepository().getDataModel(dx);
            XLogicalModel logicalModel = dataModel.getLogicalModel(name);

            if(logicalModel != null && logicalModel.getFolder() != null &&
               logicalModel.getFolder().length() > 0)
            {
               return new DataModelFolder(logicalModel.getFolder(), dx);
            }

            return new DataSourceFolder(dx);
         }
         catch(Exception ex) {
            LOG.error("Failed to get parent entry of query entry: " + this, ex);
         }

         return new DataSourceFolder(dx);
      }

      @Override
      public String getLabel() {
         return label == null ? name : label;
      }

      @Override
      public String getName() {
         return this.name;
      }

      @Override
      public int getType() {
         return XSourceInfo.MODEL;
      }

      @Override
      public String getDataSource() {
         return dx;
      }

      @Override
      public String getDescription() {
         try {
            XDataModel mdl = context.getRepository().getDataModel(dx);

            if(mdl != null) {
               XLogicalModel lml = mdl.getLogicalModel(name);

               if(lml != null) {
                  return lml.getDescription();
               }
            }
         }
         catch(Exception ex) {
            LOG.error("Failed to get description of logical model entry: " + this, ex);
         }

         return "";
      }

      @Override
      public Icon getIcon() {
         return new SVGIcon(getIconPath());
      }

      @Override
      public String getIconPath() {
         return getIconClass() + ".svg";

      }

      @Override
      public String getIconClass() {
         getType();
         return null != null ?
            "logical-formula" : "logical-model";
      }

      @Override
      public Icon getCommonIcon() {
         return new SVGIcon(getIconPath());
      }

      @Override
      public int score() {
         return 1;
      }

      public boolean equals(Object obj) {
         if(!(obj instanceof LogicalModelEntry)) {
            return false;
         }

         LogicalModelEntry entry2 = (LogicalModelEntry) obj;
         return Tool.equals(name, entry2.name) && Tool.equals(dx, entry2.dx);
      }

      public int hashCode() {
         return name.hashCode() ^ dx.hashCode();
      }

      @Override
      public boolean isLeaf() {
         return !context.isShowColumn();
      }

      private String getMainType() {
         return ObjectInfo.LOGICAL_MODEL;
      }

      @Override
      public AssetEntry createAssetEntry() {
         String path = getDataSource() + "/" + DATA_MODEL + "/" + getName();
         AssetEntry aentry = new AssetEntry(AssetRepository.QUERY_SCOPE,
            AssetEntry.Type.LOGIC_MODEL, path, null);
         aentry.setProperty("mainType", getMainType());
         aentry.setProperty("prefix", getDataSource());
         aentry.setProperty("source", getName());
         aentry.setProperty("description", getDescription());

         return aentry;
      }

      @Override
      public Entry[] getEntries() {
         return getEntries(null);
      }

      @Override
      public Entry[] getEntries(Principal user) {
         try {
            EntityKey key = new EntityKey(createAssetEntry(), user);
            XDataModel mdl = modelCache.get(key);

            if(mdl == null) {
               mdl = context.getRepository().getDataModel(dx);
               modelCache.put(key, mdl);
            }

            List<Entry> list = new ArrayList<>();

            if(mdl != null) {
               XLogicalModel lmdl = mdl.getLogicalModel(name, user);

               if(lmdl != null) {
                  Enumeration<XEntity> entities = lmdl.getEntities();
                  List<XEntity> elist = new ArrayList<>();

                  while(entities.hasMoreElements()) {
                     XEntity entity = entities.nextElement();

                     if(entity.isVisible()) {
                        elist.add(entity);
                     }
                  }

                  if(lmdl.getEntityOrder()) {
                     elist.sort(new QueryEntriesComparator());
                  }

                  for(XEntity ent : elist) {
                     list.add(new EntityEntry(name, dx, label, ent));
                  }

                  getType();
                  Entry[] formulas = null;

                  for(int i = 0; formulas != null && i < formulas.length; i++) {
                     FormulaEntry fentry = (FormulaEntry) formulas[i];
                     list.add(fentry);
                  }
               }
            }

            Entry[] entries = new Entry[list.size()];
            list.toArray(entries);
            return entries;
         }
         catch(Exception ex) {
            LOG.error("Failed to get child entries of logical model entry: " + this,
               ex);
         }

         return new Entry[0];
      }

      @Override
      public String getTooltip() {
         return getDescription();
      }

      private String name;
      private String dx;
      private String label;
   }

   private static final class EntityKey {
      EntityKey(AssetEntry entry, Principal user) {
         this.entry = entry;
         this.user = user;
      }

      @Override
      public boolean equals(Object o) {
         if(this == o) {
            return true;
         }

         if(o == null || getClass() != o.getClass()) {
            return false;
         }

         EntityKey entityKey = (EntityKey) o;

         if(entry != null ? !entry.equals(entityKey.entry) : entityKey.entry != null) {
            return false;
         }

         return user != null ? user.equals(entityKey.user) : entityKey.user == null;
      }

      @Override
      public int hashCode() {
         int result = entry != null ? entry.hashCode() : 0;
         result = 31 * result + (user != null ? user.hashCode() : 0);
         return result;
      }

      private final AssetEntry entry;
      private final Principal user;
   }

   /**
    * Model entity entry.
    */
   public class EntityEntry extends Folder {
      public EntityEntry(String pname, String dx, String plabel, XEntity entity)
      {
         this.pname = pname;
         this.dx = dx;
         this.plabel = plabel;
         this.entity = entity;
      }

      @Override
      public boolean isEditable() {
         return false;
      }

      @Override
      public Entry getParent() {
         try {
            return new LogicalModelEntry(pname, dx, plabel);
         }
         catch(Exception ex) {
            LOG.error("Failed to get parent entry of entity entry: " + this, ex);
         }

         return null;
      }

      @Override
      public String getName() {
         return entity.getName();
      }

      @Override
      public String getDataSource() {
         return dx;
      }

      public XEntity getEntity() {
         return entity;
      }

      @Override
      public Icon getIcon() {
         return new SVGIcon(Config.getQueryIcon());
      }

      @Override
      public String getIconPath() {
         return "db-table.svg";
      }

      @Override
      public int score() {
         return 0;
      }

      @Override
      public int getType() {
         return XSourceInfo.NONE;
      }

      public boolean equals(Object obj) {
         if(!(obj instanceof EntityEntry)) {
            return false;
         }

         EntityEntry entry2 = (EntityEntry) obj;
         return Tool.equals(pname, entry2.pname) &&
            Tool.equals(dx, entry2.dx) && Tool.equals(plabel, entry2.plabel) &&
            Tool.equals(entity, entry2.entity);
      }

      public int hashCode() {
         int val = pname.hashCode() ^ dx.hashCode();

         if(plabel != null) {
            val ^= plabel.hashCode();
         }

         if(entity != null) {
            val ^= entity.hashCode();
         }

         return val;
      }

      @Override
      public boolean isLeaf() {
         return !context.isShowColumn();
      }

      @Override
      public AssetEntry createAssetEntry() {
         String path = getDataSource() + "/" + DATA_MODEL + "/" +
            getParent().getName() + "/" + getName();
         AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
            AssetEntry.Type.TABLE, path, null);
         entry.setProperty("entity", getName());
         entry.setProperty("type", XSourceInfo.MODEL + "");
         entry.setProperty("prefix", getDataSource());
         entry.setProperty("source", getParent().getName());

         return entry;
      }

      @Override
      public Entry[] getEntries() {
         return getEntries(null);
      }

      @Override
      public Entry[] getEntries(Principal user) {
         try {
            List<String> entities = new ArrayList<>();

            if(user != null) {
               EntityKey key = new EntityKey(createAssetEntry(), user);
               AssetEntry[] entries = entityCache.get(key);

               if(entries == null) {
                  entries = context.getAssetRepository().getEntries(
                     createAssetEntry(), user, ResourceAction.READ,
                     new AssetEntry.Selector(AssetEntry.Type.LOGIC_MODEL));
                  entityCache.put(key, entries);
               }

               for(AssetEntry nentry : entries) {
                  entities.add(nentry.getName());
               }
            }

            List<Entry> list = new ArrayList<>();
            final Enumeration<XAttribute> attrs = entity.getAttributes();

            while(attrs.hasMoreElements()) {
               final XAttribute attr = attrs.nextElement();

               // apply vpm
               if(user != null && !entities.contains(attr.getName())) {
                  continue;
               }

               XTypeNode type = XSchema.createPrimitiveType(attr.getDataType());
               BaseField fld = new BaseField(entity.getName(), attr.getName());
               fld.setRefType(attr.getRefType());
               fld.setDefaultFormula(attr.getDefaultFormula());
               fld.setDataType(Objects.requireNonNull(type).getType());
               fld.setDescription(attr.getDescription());
               list.add(new AttributeEntry(pname, dx, plabel, entity, fld));
            }

            Entry[] entries = new Entry[list.size()];
            list.toArray(entries);
            return entries;
         }
         catch(Exception ex) {
            LOG.error("Failed to get child entries of entity entry: " + this, ex);
         }

         return new Entry[0];
      }

      @Override
      public String getDescription() {
         return entity.getDescription() == null ? "" : entity.getDescription();
      }

      @Override
      public String getTooltip() {
         return getDescription();
      }

      private String pname;
      private String dx;
      private String plabel;
      private XEntity entity;
   }

   /**
    * Model entity entry.
    */
   public class AttributeEntry extends ColumnEntry {
      public AttributeEntry(String pname, String dx, String plabel,
                            XEntity pentity, BaseField field) {
         super(field.getName(), dx, pname, XSourceInfo.MODEL,
               field.getDataType());
         this.pname = pname;
         this.dx = dx;
         this.plabel = plabel;
         this.pentity = pentity;
         this.field = field;
      }

      @Override
      public boolean isEditable() {
         return false;
      }

      @Override
      public Entry getParent() {
         try {
            return new EntityEntry(pname, dx, plabel, pentity);
         }
         catch(Exception ex) {
            LOG.error("Failed to get parent entry of attribute entry: " + this, ex);
         }

         return null;
      }

      @Override
      public String getName() {
         return field.getName();
      }

      @Override
      public String getLabel() {
         return field.getAttribute();
      }

      @Override
      public String getDataSource() {
         return dx;
      }

      public String getPartition() {
         return pname;
      }

      @Override
      public BaseField getField() {
         return field;
      }

      @Override
      public Icon getIcon() {
         return new SVGIcon(Config.getColumnIcon());
      }

      @Override
      public String getIconPath() {
         return "column.svg";
      }

      @Override
      public int score() {
         return 0;
      }

      @Override
      public int getType() {
         return XSourceInfo.NONE;
      }

      @Override
      public String getDescription() {
         return field.getDescription();
      }

      @Override
      public boolean equals(Object obj) {
         if(!(obj instanceof AttributeEntry)) {
            return false;
         }

         AttributeEntry entry2 = (AttributeEntry) obj;
         return Tool.equals(pname, entry2.pname) &&
            Tool.equals(dx, entry2.dx) && Tool.equals(plabel, entry2.plabel) &&
            Tool.equals(pentity, entry2.pentity) &&
            Tool.equals(field, entry2.field);
      }

      @Override
      public int hashCode() {
         int val = pname.hashCode() ^ dx.hashCode();

         if(plabel != null) {
            val ^= plabel.hashCode();
         }

         val ^= pentity.hashCode();
         val ^= field.hashCode();
         return val;
      }

      private String pname;
      private String dx;
      private String plabel;
      private XEntity pentity;
      private BaseField field;
   }

   /**
    * Query column entry.
    */
   public class ColumnEntry extends Entry {
      public ColumnEntry(FormulaInfo info, String dx) {
         this.name = info.getFormulaField().getName();
         this.dx = dx;
         this.query = info.getSource().getSource();
         this.ptype = info.getSource().getType();
      }

      public ColumnEntry(FormulaInfo info, AssetEntry pentry, int ptype) {
         this.name = info.getFormulaField().getName();
         this.pentry = pentry;
         this.ptype = ptype;
         this.dx = "";
      }

      public ColumnEntry(String name, String dx, String query,
                         int ptype, String dtype) {
         this.name = name;
         this.dx = dx;
         this.query = query;
         this.ptype = ptype;
         this.dtype = dtype;
      }

      public ColumnEntry(String name, AssetEntry pentry, int ptype, int refType,
         String defFormula, String dtype)
      {
         this.name = name;
         this.dx = "";
         this.pentry = pentry;
         this.ptype = ptype;
         this.refType = refType;
         this.defFormula = defFormula;
         this.dtype = dtype;
      }

      @Override
      public boolean isEditable() {
         return false;
      }

      @Override
      public Entry getParent() {
         try {
            switch(ptype) {
            case XSourceInfo.ASSET:
               return new AssetData(pentry);
            case XSourceInfo.REPORT:
              return new ReportDataEntry(dx, cross);
            }

            return null;
         }
         catch(Exception ex) {
            LOG.error("Failed to get parent entry of column entry: " + this, ex);
         }

         return null;
      }

      @Override
      public String getName() {
         return name;
      }

      @Override
      public String getDataSource() {
         return dx;
      }

      public int getRefType() {
         return this.refType;
      }

      public String getQuery() {
         return this.query;
      }

      public void setCross(boolean iscross) {
         this.cross = iscross;
      }

      public boolean getCross() {
         return this.cross;
      }

      public AssetEntry getParentEntry() {
         return pentry;
      }

      @Override
      public Icon getIcon() {
         return new SVGIcon(Config.getColumnIcon());
      }

      @Override
      public String getIconPath() {
         return "column.svg";
      }

      @Override
      public int score() {
         return 0;
      }

      @Override
      public int getType() {
         return XSourceInfo.NONE;
      }

      public Field getField() {
         BaseField ref = new BaseField(null, getName());

         if(refType >= 0) {
            ref.setRefType(refType);
         }

         ref.setDefaultFormula(defFormula);
         ref.setDataType(dtype);
         ref.setDescription(getDescription());

         return ref;
      }

      public boolean equals(Object obj) {
         if(!(obj instanceof ColumnEntry)) {
            return false;
         }

         ColumnEntry entry2 = (ColumnEntry) obj;
         return Tool.equals(name, entry2.name) && Tool.equals(dx, entry2.dx) &&
            Tool.equals(query, entry2.query) &&
            Tool.equals(pentry, entry2.pentry);
      }

      public int hashCode() {
         int val = name.hashCode() ^ dx.hashCode();

         if(query != null) {
            val ^= query.hashCode();
         }

         if(pentry != null) {
            val ^= pentry.hashCode();
         }

         return val;
      }

      public void setDescription(String desc) {
         this.description = desc;
      }

      @Override
      public String getDescription() {
         return description == null ? "" : description;
      }

      @Override
      public String getTooltip() {
         return getDescription();
      }

      private String name;
      private String dx;
      private String query;
      private String description;
      private int ptype;
      private AssetEntry pentry;
      private boolean cross;
      private int refType = -1;
      private String defFormula;
      private String dtype = XSchema.STRING;
   }

   /**
    * Formula column entry.
    */
   public class FormulaEntry extends ColumnEntry {
      public FormulaEntry(FormulaInfo info, String dx) {
         super(info, dx);
         this.field = info.getFormulaField();
         this.info = info;
      }

      public FormulaEntry(FormulaInfo info, AssetEntry pentry, int ptype) {
         super(info, pentry, ptype);
         this.field = info.getFormulaField();
         this.info = info;
      }

      @Override
      public Field getField() {
         return getFormulaField();
      }

      public FormulaField getFormulaField() {
         return this.field;
      }

      public FormulaInfo getFormulaInfo() {
         return info;
      }

      @Override
      public int score() {
         return 1;
      }

      @Override
      public Icon getIcon() {
         return new SVGIcon(Config.getFormulaIcon());
      }

      @Override
      public String getIconPath() {
         return Config.getFormulaIcon();
      }

      @Override
      public Entry getParent() {
         String source = info.getSource().getSource();
         int type = info.getSource().getType();

         if(XSourceInfo.MODEL == type) {
            return new LogicalModelEntry(source, info.getSource().getPrefix());
         }
         else if(XSourceInfo.REPORT == type) {
            return new ReportDataEntry(source, getCross());
         }

         return  super.getParent();
      }

      @Override
      public String toView() {
         StringBuilder buf = new StringBuilder();

         Principal user = ThreadContext.getContextPrincipal();
         Catalog catalog = Catalog.getCatalog(user, Catalog.REPORT);
         buf.append(catalog.getString(getName()));
         return buf.toString();
      }

      private FormulaField field;
      private FormulaInfo info;
   }

   public class AssetRoot extends Folder {
      @Override
      public Entry getParent() {
         return getRoot();
      }

      @Override
      public Entry[] getEntries() {
         List<Entry> list = new ArrayList<>();

         try {
            AssetEntry gentry = AssetEntry.createGlobalRoot();
            AssetRepository asset = context.getAssetRepository();

            if(asset.supportsScope(AssetRepository.GLOBAL_SCOPE)) {
               AssetFolder gfolder = new AssetFolder(gentry);
               list.add(gfolder);
            }

            if(context.isDisplayUserScope() &&
               asset.supportsScope(AssetRepository.USER_SCOPE))
            {
               AssetEntry uentry = AssetEntry.createUserRoot(user);
               AssetFolder ufolder = new AssetFolder(uentry);

               if(ufolder.getEntries().length > 0) {
                  list.add(ufolder);
               }
            }

            Entry[] entries = new Entry[list.size()];
            list.toArray(entries);
            return filter(entries);
         }
         catch(Exception ex) {
            LOG.error("Failed to get child entries of asset root: " + this, ex);
         }

         return new Entry[0];
      }

      @Override
      public String getName() {
         return Catalog.getCatalog().getString("Worksheet");
      }

      @Override
      public Icon getIcon() {
         return new SVGIcon(getIconPath());
      }

      @Override
      public String getIconPath() {
         return "shared-worksheet.svg";
      }

      @Override
      public String getIconClass() {
         return "asset";
      }

      @Override
      public int score() {
         return 3;
      }

      public boolean equals(Object obj) {
         return obj.getClass() == AssetRoot.class;
      }

      public int hashCode() {
         return AssetRoot.class.hashCode();
      }
   }

   public interface AssetEntryContainer {
      AssetEntry getAssetEntry();
   }

   public interface Query {
      String getDataSource();
      String getName();
      int getType();
   }

   public class AssetFolder extends Folder implements AssetEntryContainer {
      public AssetFolder(AssetEntry entry) {
         this.entry = entry;
      }

      @Override
      public AssetEntry getAssetEntry() {
         return entry;
      }

      @Override
      public Entry getParent() {
         AssetEntry parent = entry.getParent();
         boolean root = context.isDisplayAsset() && entry.isRoot() &&
            entry.getScope() == AssetRepository.GLOBAL_SCOPE;

         if(parent == null) {
            if(root) {
               return getRoot();
            }

            return new AssetRoot();
         }

         return new AssetFolder(parent);
      }

      @Override
      public Entry[] getEntries() {
         try {
            List<Entry> list = new ArrayList<>();
            boolean root = entry != null && context.isDisplayAsset() && entry.isRoot() &&
               entry.getScope() == AssetRepository.GLOBAL_SCOPE;

            if(root && context.isDisplayUserScope() &&
               context.getAssetRepository().supportsScope(AssetRepository.USER_SCOPE))
            {
               AssetEntry uentry = AssetEntry.createUserRoot(user);
               AssetFolder ufolder = new AssetFolder(uentry);

               if(ufolder.getEntries().length > 0) {
                  list.add(0, ufolder);
               }
            }

            Entry[] folders = filter((Entry[]) cache.get(this));

            for(int i = 0; i < folders.length; i++) {
               if(RecycleUtils.isInRecycleBin(folders[i].getName())) {
                  continue;
               }

               list.add(folders[i]);
            }

            Entry[] result = new Entry[list.size()];
            list.toArray(result);
            return result;
         }
         catch(Exception ex) {
            LOG.error("Failed to get child entries of asset folder: " + this, ex);
         }

         return new Entry[0];
      }

      @Override
      public String getName() {
         Catalog catalog = Catalog.getCatalog();

         if(entry != null && entry.isRoot()) {
            int scope = entry.getScope();

            if(scope == AssetRepository.GLOBAL_SCOPE) {
               return context.isDisplayAsset() ?
                  catalog.getString("Global Worksheet") :
                  catalog.getString("Global");
            }
            else if(scope == AssetRepository.USER_SCOPE) {
               return catalog.getString("User Worksheet");
            }
            else if(scope == AssetRepository.REPORT_SCOPE) {
               return catalog.getString("Report");
            }
            else {
               throw new RuntimeException("Unsupported scope found: " + scope);
            }
         }

         assert entry != null;
         return entry.getName();
      }

      public void setLabel(String label) {
         this.label = label;
      }

      @Override
      public String getLabel() {
         if(label != null) {
            return label;
         }

         return super.getLabel();
      }

      @Override
      public Icon getIcon(boolean expanded) {
         String imagePath = getIconPath();

         if(expanded && imagePath.endsWith("folder.svg")) {
            imagePath = imagePath.replaceAll("folder.svg", "folder-open.svg");
         }

         return new SVGIcon(imagePath);
      }

      @Override
      public Icon getIcon() {
         return new SVGIcon(getIconPath());
      }

      @Override
      public String getIconPath() {
         return getIconClass() + ".svg";
      }

      @Override
      public String getIconClass() {
         if(iconClass != null) {
            return iconClass;
         }

         int scope = entry.getScope();

         if(entry.isRoot()) {
            if(scope == AssetRepository.GLOBAL_SCOPE) {
               return "shared-worksheet";
            }
            else if(scope == AssetRepository.USER_SCOPE) {
               return "private-worksheet";
            }
            else if(scope == AssetRepository.REPORT_SCOPE) {
               return "report";
            }
         }

         return "folder";
      }

      @Override
      public int score() {
         // object tree want the global root node move to bottom
         return context.isDisplayAsset() && entry.isRoot() &&
            entry.getScope() == AssetRepository.GLOBAL_SCOPE ? 5 : 0;
      }

      public boolean equals(Object obj) {
         if(!(obj instanceof AssetFolder)) {
            return false;
         }

         AssetFolder folder2 = (AssetFolder) obj;
         return entry.equals(folder2.entry);
      }

      public int hashCode() {
         return entry.hashCode();
      }

      @Override
      public String getTooltip() {
         Catalog catalog = Catalog.getCatalog();

         if(entry.isWorksheetFolder()) {
            if(entry.isRoot()) {
               return getName();
            }

            return catalog.getString("Folder") + ": " +  getName();
         }

         return null;
      }

      private AssetEntry entry;
      private String iconClass;
      private String label;
   }

   public class AssetData extends Folder implements AssetEntryContainer {
      public AssetData(AssetEntry entry) {
         this.entry = entry;
         // remember the local report so the hashCode doesn't change after
         // it's added to the resource cache, which could cause the hashmap's
         // internal state to be completely messed up
         reportname = (context.getReport() != null)
            ? "" + context.getReport().addr() : null;
      }

      @Override
      public AssetEntry getAssetEntry() {
         return entry;
      }

      @Override
      public boolean isEditable() {
         return false;
      }

      @Override
      public Entry getParent() {
         return new AssetFolder(entry.getParent());
      }

      @Override
      public String getName() {
         return entry.toIdentifier();
      }

      @Override
      public String getLabel() {
         String alias = entry.getAlias();

         if(alias != null && alias.length() > 0) {
            return alias;
         }

         return entry.getName();
      }

      @Override
      public String toView() {
         String alias = entry.getAlias();

         if(alias != null && alias.length() > 0) {
            Principal user = ThreadContext.getContextPrincipal();
            Catalog catalog = Catalog.getCatalog(user, Catalog.REPORT);
            return catalog.getString(alias);
         }

         return super.toView();
      }

      @Override
      public int getType() {
         return XSourceInfo.ASSET;
      }

      @Override
      public String getDataSource() {
         return "";
      }

      private String getMainType() {
         return ObjectInfo.WORKSHEET;
      }

      @Override
      public AssetEntry createAssetEntry() {
         AssetEntry aentry = getAssetEntry();
         aentry.setProperty("mainType", getMainType());

         return aentry;
      }

      @Override
      public String getDescription() {
         String desc = entry.getProperty(AssetEntry.SHEET_DESCRIPTION);

         if(desc == null) {
            desc = entry.getProperty("description");
         }
         return desc != null ? desc : "";
      }

      @Override
      public Icon getIcon() {
         return new SVGIcon(getIconPath());
      }

      @Override
      public String getIconPath() {
         return getIconClass() + ".svg";
      }

      @Override
      public String getIconClass() {
         String val = entry.getProperty(AssetEntry.WORKSHEET_TYPE);
         val = val == null ? Worksheet.TABLE_ASSET + "" : val;
         int wstype = Integer.parseInt(val);

         switch(wstype) {
         case Worksheet.CONDITION_ASSET:
            return "condition";
         case Worksheet.NAMED_GROUP_ASSET:
            return "grouping";
         case Worksheet.VARIABLE_ASSET:
            return "variable";
         case Worksheet.TABLE_ASSET:
            return null == null ? "worksheet" : "formula";
         case Worksheet.DATE_RANGE_ASSET:
            return "date-range";
         default:
            return "worksheet";
         }
      }

      @Override
      public Icon getCommonIcon() {
         String val = entry.getProperty(AssetEntry.WORKSHEET_TYPE);
         val = val == null ? Worksheet.TABLE_ASSET + "" : val;
         int wstype = Integer.parseInt(val);

         if(wstype == Worksheet.TABLE_ASSET) {
            return new SVGIcon("worksheet.svg");
         }

         return getIcon();
      }

      @Override
      public int score() {
         return 1;
      }

      public boolean equals(Object obj) {
         if(!(obj instanceof AssetData)) {
            return false;
         }

         AssetData data2 = (AssetData) obj;
         // @by tomzhang bug1318530017384
         return this.getPseudoReportName().equals(data2.getPseudoReportName())
            && entry.equals(data2.entry);
      }

      public int hashCode() {
         // @by tomzhang bug1318530017384
         return getPseudoReportName().hashCode() ^ entry.hashCode();
      }

      @Override
      public boolean isLeaf() {
         String val = entry.getProperty(AssetEntry.WORKSHEET_TYPE);
         return !(context.isShowColumn() &&
            (val == null || val.equals(Worksheet.TABLE_ASSET + "")));
      }

      /**
       * Get pseudo report name to create new key for ResourceCache;
       */
      private String getPseudoReportName() {
    // @by tomzhang bug1318530017384 for properties view pane
         if(reportname != null) {
            return reportname;
         }

         AssetRepository rep = context.getAssetRepository();
         return rep == null ? entry.toIdentifier() :
                              rep.getEntryIdentifier(entry);
      }

      @Override
      public Entry[] getEntries() {
         try {
            Entry[] entries = (Entry[]) cache.get(this);

            // optimization. (58738)
            if(entries != lastEntries) {
               setColumnDescription(lastEntries = entries);
            }

            if(null != null) {
               return (Entry[]) Tool.mergeArray(null, entries);
            }

            return entries;
         }
         catch(Exception ex) {
            LOG.error("Failed to get child entries of asset data: " + this, ex);
         }

         return new Entry[0];
      }

      private void setColumnDescription(Entry[] entries) {
         AssetRepository repository = AssetUtil.getAssetRepository(false);

         try {
            Worksheet sheet = (Worksheet)
               repository.getSheet(entry, null, false, AssetContent.ALL);

            if(sheet != null) {
               WSAssembly primary = sheet.getPrimaryAssembly();

               if(primary instanceof TableAssembly) {
                  TableAssemblyInfo info = ((TableAssembly) primary).getTableInfo();
                  ColumnSelection all = info.getPrivateColumnSelection();

                  for(Entry colEntry : entries) {
                     ColumnEntry col = (ColumnEntry) colEntry;
                     ColumnRef ref = (ColumnRef) all.getAttribute(col.getName());

                     if(ref != null) {
                        col.setDescription(ref.getDescription());
                     }
                  }
               }
            }
         }
         catch(Exception ex) {
            LOG.error(
               "Failed to set column description: " + this, ex);
         }
      }

      @Override
      public String getTooltip() {
         return getDescription();
      }

      private String reportname;
      private AssetEntry entry;
      private Entry[] lastEntries = null;
   }

   /**
    * Binding tree model context.
    */
   public interface Context {
      XRepository getRepository();
      AssetRepository getAssetRepository();
      DesignSession getDesignSession();
      ReportSheet getReport();
      ReportElement getElement();
      boolean isDisplayAsset();
      boolean isDisplayEmbeddedData();
      boolean isBlocked();
      boolean isDisplayEmptyDataSource();
      boolean isDisplayXMLADataSource();
      boolean isDisplayUserScope();
      boolean isDisplayEmptyReportScope();
      boolean isAll();
      boolean isShowColumn();
      boolean isCheckFormulaIcon();
      Entry[] getFilterEntry();

      default boolean portal() {
       return false;
      }

      default boolean isSupportBindSource() {
         return false;
      }
   }

   /**
    * Filter nodes.
    */
   private Entry[] filter(Entry[] entries) {
      Entry[] fentries = context.getFilterEntry();

      if(fentries == null || fentries.length <= 0 || entries.length <= 0) {
         return entries;
      }

      List<Entry> nodes = new ArrayList<>();

      for(Entry entry : entries) {
         for(Entry fentry : fentries) {
            if(fentry != null && isAncestor(entry, fentry)) {
               nodes.add(entry);
               break;
            }
         }
      }

      return nodes.toArray(new Entry[nodes.size()]);
   }

   /**
    * Check if the checker entry is ancestor of child entry.
    */
   private boolean isAncestor(Entry checker, Entry child) {
      Entry parent = child;

      while(parent != null) {
         if(parent.equals(checker)) {
            return true;
         }

         parent = parent.getParent();
      }

      return false;
   }

   /**
    * Sort QueryEntries.
    */
   private static class QueryEntriesComparator implements Comparator<XEntity> {
      @Override
      public int compare(XEntity v1, XEntity v2) {
         String str1 = v1.toString().toUpperCase();
         String str2 = v2.toString().toUpperCase();

         if(str1.equals(str2)) {
            return v1.toString().compareTo(v2.toString());
         }
         else {
            return str1.compareTo(str2);
         }
      }
   }

   /**
    * Get report tooltip.
    */
   private String getReportTooltip(String name, String title, String author) {
      String tooltip = name;
      Catalog catalog = Catalog.getCatalog();

      if(title != null) {
         tooltip = tooltip + "\n" + catalog.getString("Title") + ": " +
            title;
      }

      if(author != null) {
         tooltip = tooltip + "\n" + catalog.getString("Author") + ": " +
            author;
      }

      return tooltip;
   }

   /**
    * Remove a entry from cache.
    */
   public void removeEntryFromCache(Entry entry) {
      if(cache == null) {
         return;
      }

      cache.remove(entry);
   }

   private static final String DATA_MODEL = "Data Model";
   private static final String LOCAL_QUERY = "Local Query";
   private transient Context context;
   private transient Principal user = null;
   private transient Set<TreeModelListener> listeners = new LinkedHashSet<>();
   private transient EntryCache cache = new EntryCache();
   private transient AssetCache dataCache = new AssetCache();
   private final transient DataCache<EntityKey, AssetEntry[]> entityCache = new DataCache<>(5, 50);
   private final transient DataCache<EntityKey, XDataModel> modelCache = new DataCache<>(5, 50);
   private transient Entry[] rentries = null;
   private transient Entry[] rentries_s = null;
   private transient RootFolder root;

   private static final Logger LOG =
      LoggerFactory.getLogger(BindingTreeModel.class);
}
