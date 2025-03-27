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
package inetsoft.util.dep;

import inetsoft.report.*;
import inetsoft.report.internal.binding.AssetNamedGroupInfo;
import inetsoft.sree.RepletRegistry;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.ChartDescriptor;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.uql.viewsheet.vslayout.*;
import inetsoft.util.*;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.io.EOFException;
import java.io.PrintWriter;
import java.security.Principal;
import java.util.*;

/**
 * ViewsheetAsset represents a viewsheet type asset.
 *
 * @author InetSoft Technology Corp
 * @version 9.1
 */
public class ViewsheetAsset extends AbstractSheetAsset implements FolderChangeableAsset {
   /**
    * Viewsheet type XAsset.
    */
   public static final String VIEWSHEET = "VIEWSHEET";

   /**
    * Constructor.
    */
   public ViewsheetAsset() {
      super();
   }

   /**
    * Constructor.
    *
    * @param viewsheet the viewsheet asset entry.
    */
   public ViewsheetAsset(AssetEntry viewsheet) {
      this();
      this.entry = viewsheet;
   }

   /**
    * Get all dependencies of this asset.
    *
    * @return an array of XAssetDependency.
    */
   @Override
   public XAssetDependency[] getDependencies() {
      List<XAssetDependency> dependencies = new ArrayList<>();
      Set<Hyperlink> hyperlinks = new HashSet<>();
      Set<String> assetKeys = null;
      RepletRegistry registry = null;
      RepletRegistry userRegistry = null;

      try {
         assetKeys = IndexedStorage.getIndexedStorage().getKeys(null);
         registry = RepletRegistry.getRegistry();
         AssetEntry assetEntry = getAssetEntry();

         if(assetEntry != null &&
            assetEntry.getUser() != null && !StringUtils.isEmpty(assetEntry.getUser().name))
         {
            userRegistry = RepletRegistry.getRegistry(assetEntry.getUser());
         }
         else {
            Principal user = ThreadContext.getPrincipal();
            IdentityID uId = IdentityID.getIdentityIDFromKey(user.getName());

            if(user != null) {
               userRegistry = RepletRegistry.getRegistry(uId);
            }
         }
      }
      catch(Exception ignored) {
         // ignore, logged already and this is only used to validate hyperlinks
      }

      AssetRepository engine = AssetUtil.getAssetRepository(false);
      AbstractSheet abstractSheet = getCurrentSheet(engine);
      Viewsheet sheet = abstractSheet instanceof Viewsheet ? (Viewsheet) abstractSheet : null;

      if(sheet != null) {
         // check if depends on a worksheet
         AssetEntry entry0 = sheet.getBaseEntry();

         if(entry0 != null) {
            if(entry0.isWorksheet()) {
               try {
                  entry0 = engine.getAssetEntry(entry0);
               }
               catch(Exception ignore) {
               }

               if(entry0 != null) {
                  getWSDependency(entry0, dependencies);
               }
            }
            else if(entry0.isQuery()) {
               getQueryDependency(entry0, dependencies);
            }
            else if(entry0.isLogicModel()) {
               getModelDependency(entry0, dependencies);
            }
            else if(entry0.isPhysicalTable()) {
               getPhyDependency(entry0, dependencies);
            }
         }

         String desc = catalog.getString("common.xasset.viewsheet", getPath());
         ViewsheetInfo vinfo = sheet.getViewsheetInfo();
         processRunQueryDependencies(vinfo, dependencies);
         processScript(vinfo.getOnInit(), dependencies, desc, sheet);
         processScript(vinfo.getOnLoad(), dependencies, desc, sheet);

         getDeviceDependency(sheet, dependencies);

         if(sheet.getCalcFieldSources() != null) {
            for(String calcField: sheet.getCalcFieldSources()) {
               CalculateRef[] calcFields = sheet.getCalcFields(calcField);

               if(calcFields != null) {
                  for(CalculateRef calcRef : calcFields) {
                     DataRef ref = calcRef.getDataRef();

                     if(ref instanceof ExpressionRef exprRef) {
                        processScript(exprRef.getExpression(), dependencies, desc, sheet);
                     }
                  }
               }
            }
         }

         // do not recursively get assemblies
         Assembly[] assemblies = sheet.getAssemblies();

         for(Assembly assembly : assemblies) {
            if(assembly instanceof TableDataVSAssembly) {
               getTableStyleDependency((TableDataVSAssembly) assembly, dependencies);
            }

            // check if any DataVSAssembly depends on data source
            if(assembly instanceof BindableVSAssembly) {
               getOLAPDataDependency((BindableVSAssembly) assembly, dependencies, sheet);
            }
            // check if depends on another viewsheet
            else if(assembly instanceof Viewsheet) {
               getVSDependency((Viewsheet) assembly, dependencies);
            }

            // check if depends on script
            if(assembly instanceof VSAssembly) {
               VSAssemblyInfo vinfo0 = ((VSAssembly) assembly).getVSAssemblyInfo();
               List<DynamicValue> dvalues = vinfo0.getViewDynamicValues(false);
               processDVScript(dvalues, dependencies, sheet);
               processScript(vinfo0.getScript(), dependencies, desc, sheet);
            }

            if(assembly instanceof ChartVSAssembly) {
               ChartDescriptor chartDesc = ((ChartVSAssembly) assembly).getChartDescriptor();
               List<DynamicValue> dvalues = chartDesc.getDynamicValues();
               processDVScript(dvalues, dependencies, sheet);

               VSChartInfo info = ((ChartVSAssembly) assembly).getVSChartInfo();
               dvalues = info.getDynamicValues();
               processDVScript(dvalues, dependencies, sheet);
            }
            else if(assembly instanceof CrosstabVSAssembly) {
               VSCrosstabInfo info = ((CrosstabVSAssembly) assembly).getVSCrosstabInfo();
               List<DynamicValue> dvalues = info == null ? null : info.getDynamicValues();
               processDVScript(dvalues, dependencies, sheet);
            }
            else if(assembly instanceof AbstractVSAssembly) {
               List<DynamicValue> dvalues = ((AbstractVSAssembly) assembly).getDynamicValues();
               processDVScript(dvalues, dependencies, sheet);
            }

            final AssemblyInfo info = assembly.getInfo();

            // add asset named group info dependency
            if(assembly instanceof CalcTableVSAssembly) {
               AssetNamedGroupInfo[] assetNamedGroupInfos = getAssetNamedGroupInfo((VSAssemblyInfo) info);
               Arrays.stream(assetNamedGroupInfos).map(AssetNamedGroupInfo::getEntry)
                  .forEach(assetEntry -> getWSDependency(assetEntry, dependencies));
            }

            final Hyperlink[] links = VSUtil.getAllLinks((VSAssemblyInfo) info, false);
            hyperlinks.addAll(Arrays.asList(links));
         }
      }

      if(assetKeys != null && registry != null) {
         final List<Hyperlink> linkDependencies = new ArrayList<>();

         for(Hyperlink hyperlink : hyperlinks) {
            final String link = hyperlink.getLink();
            final int linkType = hyperlink.getLinkType();
            boolean isViewsheetLink = linkType == Hyperlink.VIEWSHEET_LINK;

            // we only care about finding dependencies for reports and viewsheets
            if(isViewsheetLink && assetKeys.contains(link)) {
               linkDependencies.add(hyperlink);
            }
            else {
               if(linkType == Hyperlink.VIEWSHEET_LINK) {
                  LOG.warn("Failed to find asset for hyperlink {} in {}", link, getPath());
               }
            }
         }

         final Hyperlink[] links = linkDependencies.toArray(new Hyperlink[0]);
         SUtil.processAssetlinkDependencies(links, this, dependencies);
      }

      XAssetDependency[] deps = new XAssetDependency[dependencies.size()];
      return dependencies.toArray(deps);
   }

   private AssetNamedGroupInfo[] getAssetNamedGroupInfo(VSAssemblyInfo vsAssemblyInfo) {
      Set<AssetNamedGroupInfo> set = new HashSet<>();

      if(vsAssemblyInfo instanceof CalcTableVSAssemblyInfo) {
         CalcTableVSAssemblyInfo calcTableVSAssemblyInfo = (CalcTableVSAssemblyInfo) vsAssemblyInfo;
         TableLayout tableLayout = calcTableVSAssemblyInfo.getTableLayout();
         List<CellBindingInfo> cellInfos = tableLayout.getCellInfos(true);

         cellInfos.stream().filter(cellInfo -> cellInfo != null)
            .map(cellInfo -> ((TableLayout.TableCellBindingInfo) cellInfo).getCellBinding())
            .filter(cellBinding -> cellBinding != null && cellBinding.getOrderInfo(false) != null)
            .map(cellBinding -> cellBinding.getOrderInfo(false).getRealNamedGroupInfo())
            .filter(namedGroupInfo -> namedGroupInfo != null && namedGroupInfo instanceof AssetNamedGroupInfo)
            .forEach(namedGroupInfo -> set.add((AssetNamedGroupInfo) namedGroupInfo));
      }

      return set.toArray(new AssetNamedGroupInfo[0]);
   }

   private void processRunQueryDependencies(ViewsheetInfo vsInfo, List<XAssetDependency> dependencies) {
      List<AssetEntry> list = new ArrayList<>();
      // runQuery command generally be placed in the onInit,
      // here check both onInit and onLoad to be more secure
      collectRunQueryDependencies(vsInfo.getOnInit(), list);
      collectRunQueryDependencies(vsInfo.getOnLoad(), list);

      list.stream().forEach(wsEntry -> getWSDependency(wsEntry, dependencies));
   }

   private void collectRunQueryDependencies(String script, List<AssetEntry> list) {
      if(StringUtils.isEmpty(script)) {
         return;
      }

      FunctionIterator iterator = new FunctionIterator(script);
      ScriptIterator.ScriptListener listener = new ScriptIterator.ScriptListener() {
         @Override
         public void nextElement(ScriptIterator.Token token, ScriptIterator.Token pref,
                                 ScriptIterator.Token cref)
         {
            if(token.isRef() && pref != null && "runQuery".equals(pref.val) )
            {
               list.add(createAssetEntry(token.val));
            }
         }
      };

      iterator.addScriptListener(listener);
      iterator.iterate();
      iterator.removeScriptListener(listener);
   }

   /**
    * @param wsPath used in runQuery script, like "ws:global:path" or "ws:global:path:tableName"
    * @return AssetEntry
    */
   private AssetEntry createAssetEntry(String wsPath) {
      if(StringUtils.isEmpty(wsPath) || !wsPath.startsWith("ws:")) {
         return null;
      }

      String[] arr = wsPath.split(":");

      if(arr.length < 3) {
         return null;
      }


      int scope = Tool.equals(arr[1], "global") ?
         AssetRepository.GLOBAL_SCOPE : AssetRepository.USER_SCOPE;
      IdentityID user = scope == AssetRepository.USER_SCOPE ? IdentityID.getIdentityIDFromKey(arr[1]) : null;
      String path = arr[2]; // {Folder1/Folder 2/.../}datasetName
      path = path.replace("{", "");
      path = path.replace("}", "");

      Principal principal = ThreadContext.getContextPrincipal();
      String orgId;
      if(principal instanceof XPrincipal) {
         orgId = ((XPrincipal) principal).getOrgId();
      }
      else {
         orgId = OrganizationManager.getInstance().getCurrentOrgID();
      }

      return new AssetEntry(scope, AssetEntry.Type.WORKSHEET, path, user, orgId);
   }

   /**
    * Get the type of this asset.
    *
    * @return the type of this asset.
    */
   @Override
   public String getType() {
      return VIEWSHEET;
   }

   /**
    * Parse an identifier to a real asset.
    *
    * @param identifier the specified identifier, usually with the format of
    *                   ClassName^identifier.
    */
   @Override
   public void parseIdentifier(String identifier) {
      int idx = identifier.indexOf('^');
      String className = identifier.substring(0, idx);

      if(!className.equals(getClass().getName())) {
         return;
      }

      identifier = identifier.substring(idx + 1);
      entry = AssetEntry.createAssetEntryForCurrentOrg(identifier);
   }

   /**
    * Create an asset by its path and owner if any.
    *
    * @param path         the specified asset path.
    * @param userIdentity the specified asset owner if any.
    */
   @Override
   public void parseIdentifier(String path, IdentityID userIdentity) {
      int scope = userIdentity != null ? AssetRepository.USER_SCOPE : AssetRepository.GLOBAL_SCOPE;
      entry = new AssetEntry(scope, AssetEntry.Type.VIEWSHEET, path, userIdentity);
   }

   /**
    * Convert this asset to an identifier.
    *
    * @return an identifier.
    */
   @Override
   public String toIdentifier() {
      return getClass().getName() + "^" + entry.toIdentifier();
   }

   @Override
   public String getChangeFolderIdentifier(String oldFolder, String newFolder) {
      return getChangeFolderIdentifier(oldFolder, newFolder, null);
   }

   @Override
   public String getChangeFolderIdentifier(String oldFolder, String newFolder, IdentityID newUser) {
      if(Tool.isEmptyString(newFolder)) {
         return toIdentifier();
      }
      else {
         String path = FolderChangeableAsset.changeFolder(entry.getPath(), oldFolder, newFolder);
         int scope = newUser != null && !Tool.isEmptyString(newUser.name) ?
            AssetRepository.USER_SCOPE : AssetRepository.GLOBAL_SCOPE;
         AssetEntry nEntry = new AssetEntry(scope, entry.getType(), path, newUser);

         return getClass().getName() + "^" + nEntry.toIdentifier();
      }
   }

   /**
    * Clone this object.
    *
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      AssetEntry viewsheet2 = (AssetEntry) entry.clone();
      return new ViewsheetAsset(viewsheet2);
   }

   /**
    * Get corresponding sheet object.
    */
   @Override
   protected AbstractSheet getSheet0() {
      return new Viewsheet();
   }

   /**
    * Get transformer type.
    */
   @Override
   protected String getTransformerType() {
      return TransformerManager.VIEWSHEET;
   }

   /**
    * Check if should validate repository path.
    */
   @Override
   protected boolean checkRepositoryPath() {
      return true;
   }

   /**
    * Get script dependency type.
    */
   @Override
   protected int getScriptDependencyType() {
      return XAssetDependency.VIEWSHEET_SCRIPT;
   }

   @Override
   protected synchronized void parseContent0(Element elem) throws Exception
   {
      NodeList usersList = null;
      String alias = null;

      Element belem = Tool.getChildNodeByTagName(elem, "AllBookmarks");

      if(belem != null) {
         usersList = Tool.getChildNodesByTagName(belem, "user");
      }

      alias = Tool.getChildValueByTagName(elem, "entryAlias");

      AssetEntry entry = getAssetEntry();
      entry.setAlias(alias);

      // clear bookmarks here for new viewsheet imported
      AssetRepository engine = AssetUtil.getAssetRepository(false);
      engine.clearVSBookmark(entry);

      if(usersList == null) {
         return;
      }

      for(int i = 0; i < usersList.getLength(); i++) {
         if(!(usersList.item(i) instanceof Element)) {
            continue;
         }

         VSBookmark vsBookmark = new VSBookmark();
         Node userBookmark = usersList.item(i);
         IdentityID name = IdentityID.getIdentityIDFromKey(Tool.getChildValueByTagName(userBookmark, "name"));
         Element bookmark = Tool.getChildNodeByTagName(userBookmark, "bookmarks");
         vsBookmark.parseXML(bookmark);
         engine.setVSBookmark(entry, vsBookmark, new XPrincipal(name));
      }
   }

   /**
    * Write content of the specified asset to an output stream.
    */
   @Override
   protected synchronized void writeContent0(AbstractSheet sheet0, PrintWriter writer)
      throws Exception
   {
      AssetRepository engine = AssetUtil.getAssetRepository(false);
      writer.println("<viewsheet>");
      sheet0.writeXML(writer);
      writer.println("<AllBookmarks>");

      for(IdentityID user : getAllUsers()) {
         try {
            VSBookmark vsBookmark = engine.getVSBookmark(getAssetEntry(), new XPrincipal(user));

            if(vsBookmark != null && vsBookmark.getBookmarks().length != 0) {
               writer.println("<user>");
               writer.println("<name>");
               writer.println(user.convertToKey());
               writer.println("</name>");
               vsBookmark.writeXML(writer);
               writer.println("</user>");
            }
         }
         catch(EOFException exception) {
            // @by stephenwebster, For Bug #4985
            // Most likely the bookmark is corrupt.  Do not allow this to
            // prevent an import of the asset.
            LOG.warn("Failed to get bookmark", exception);
         }
      }

      writer.println("</AllBookmarks>");

      if(entry.getAlias() != null) {
         writer.println("<entryAlias><![CDATA[" + entry.getAlias() + "]]></entryAlias>");
      }

      writer.println("</viewsheet>");
   }

   /**
    * Parse sheet.
    */
   @Override
   protected void parseSheet(AbstractSheet sheet, Element elem, XAssetConfig config, String orgId)
      throws Exception
   {
      if("viewsheet".equals(elem.getNodeName()) &&
         Tool.getChildNodeByTagName(elem, "viewsheet") != null)
      {
         elem = Tool.getChildNodeByTagName(elem, "viewsheet");
      }

      sheet.parseXML(elem);

      Viewsheet vs = (Viewsheet) sheet;

      if(config != null && vs.getLayoutInfo() != null &&
         vs.getLayoutInfo().getViewsheetLayouts() != null)
      {
         Map<String, String> mapping = config.getContextAttribute(DeviceAsset.DEVICE_ID_MAPPING);

         if(mapping != null) {
            // When overwriting existing devices, the existing ID is preserved
            // so that existing viewsheet are not broken. The old ID of the
            // imported device is replaced in the imported viewsheets.
            for(ViewsheetLayout layout : vs.getLayoutInfo().getViewsheetLayouts()) {
               String[] ids = layout.getDeviceIds();

               if(ids != null) {
                  for(int i = 0; i < ids.length; i++) {
                     String id = mapping.get(ids[i]);

                     if(id != null) {
                        ids[i] = id;
                     }
                  }
               }
            }
         }
      }

      AssetEntry baseEntry = vs.getBaseEntry();

      if(baseEntry != null) {
         vs.setBaseEntry(baseEntry.cloneAssetEntry(
            OrganizationManager.getInstance().getCurrentOrgID(), ""));
      }
   }

   protected void getDeviceDependency(Viewsheet sheet, List<XAssetDependency> dependencies) {
      if(sheet.getLayoutInfo() != null && sheet.getLayoutInfo().getViewsheetLayouts() != null) {
         Set<String> allDevices = new HashSet<>();

         for(ViewsheetLayout layout : sheet.getLayoutInfo().getViewsheetLayouts()) {
            String[] devices = layout.getDeviceIds();

            if(devices != null) {
               Collections.addAll(allDevices, devices);
            }
         }

         for(String id : allDevices) {
            DeviceInfo device = DeviceRegistry.getRegistry().getDevice(id);

            if(device != null) {
               String desc =
                  generateDescription(entry.getDescription(), "Device " + device.getName());
               dependencies.add(
                  new XAssetDependency(new DeviceAsset(id), this, XAssetDependency.VIEWSHEET_DEVICE,
                                       desc));
            }
         }
      }
   }

   /**
    * Get vs-ws dependency.
    */
   protected void getWSDependency(AssetEntry entry0, List<XAssetDependency> dependencies) {
      String desc = generateDescription(entry.getDescription(), entry0.getDescription());
      dependencies.add(new XAssetDependency(new WorksheetAsset(entry0), this,
                                            XAssetDependency.VIEWSHEET_WORKSHEET, desc));
   }

   /**
    * Get vs-query dependency.
    */
   protected void getQueryDependency(AssetEntry entry0, List<XAssetDependency> dependencies) {
      String desc = generateDescription(entry.getDescription(), entry0.getDescription());
      XQueryAsset asset = new XQueryAsset(entry0.getProperty("source"));
      dependencies.add(new XAssetDependency(asset, this, XAssetDependency.VIEWSHEET_QUERY, desc));
      addDependencies(asset, dependencies);
      List<Hyperlink> drillLinks = asset.getDrillLinks();
      Hyperlink[] links = new Hyperlink[drillLinks.size()];
      drillLinks.toArray(links);
      SUtil.processAssetlinkDependencies(links, this, dependencies);
   }

   /**
    * Get vs-logical model dependency.
    */
   protected void getModelDependency(AssetEntry entry0, List<XAssetDependency> dependencies) {
      String desc = generateDescription(entry.getDescription(), entry0.getDescription());
      String dataSource = entry0.getProperty("prefix");
      String source = entry0.getProperty("source");

      XLogicalModelAsset[] res = XLogicalModelAsset.getAssets(dataSource + "^" + source);

      for(XLogicalModelAsset lasset : res) {
         dependencies.add(
            new XAssetDependency(lasset, this, XAssetDependency.VIEWSHEET_XLOGICALMODEL, desc));
         addDependencies(lasset, dependencies);
         List<Hyperlink> drillLinks = lasset.getDrillLinks();
         Hyperlink[] links = new Hyperlink[drillLinks.size()];
         drillLinks.toArray(links);
         SUtil.processAssetlinkDependencies(links, this, dependencies);
      }
   }

   /**
    * Get vs-physical table dependency.
    */
   protected void getPhyDependency(AssetEntry entry0, List<XAssetDependency> dependencies) {
      String desc = generateDescription(entry.getDescription(), entry0.getDescription());
      String dataSource = entry0.getProperty("prefix");
      XDataSourceAsset[] res = XDataSourceAsset.getAssets(dataSource);

      for(XDataSourceAsset tasset : res) {
         dependencies.add(
            new XAssetDependency(tasset, this, XAssetDependency.VIEWSHEET_DATASOURCE, desc));
         addDependencies(tasset, dependencies);
      }
   }

   /**
    * Get vs-olap data source dependency.
    */
   protected void getOLAPDataDependency(BindableVSAssembly dassembly, List<XAssetDependency> dependencies,
                                        Viewsheet sheet0)
   {
      SourceInfo info;
      String dataSource;
      String cube = dassembly.getTableName();

      if(dassembly instanceof DataVSAssembly) {
         info = ((DataVSAssembly) dassembly).getSourceInfo();

         if(info == null || info.isEmpty()) {
            return;
         }

         cube = info.getSource();
      }

      Worksheet ws = sheet0.getBaseWorksheet();
      Assembly assembly = ws == null ? null : ws.getAssembly(cube);

      if(assembly instanceof CubeTableAssembly) {
         info = ((CubeTableAssembly) assembly).getSourceInfo();
         dataSource = info.getPrefix();
         cube = info.getSource();
         boolean logmodel = false;

         try {
            logmodel = DataSourceRegistry.getRegistry().
               getDataModel(dataSource).getLogicalModel(cube) != null;
         }
         catch(Exception ignored) {
            // ignored
         }

         String desc = catalog.getString("common.xasset.assembly", dassembly.getName());
         getSourceDependencies(dependencies, dataSource, cube, logmodel, desc,
                               XAssetDependency.VIEWSHEET_DATASOURCE,
                               XAssetDependency.VIEWSHEET_XLOGICALMODEL);
      }
   }

   /**
    * Get vs-vs dependency.
    */
   protected void getVSDependency(Viewsheet vs, List<XAssetDependency> dependencies) {
      AssetEntry viewEntry = vs.getEntry();
      String desc = generateDescription(entry.getDescription(), viewEntry.getDescription());
      dependencies.add(new XAssetDependency(new ViewsheetAsset(viewEntry), this,
                                            XAssetDependency.VIEWSHEET_VIEWSHEET, desc));
   }

   /**
    * Get vs-table style dependency.
    */
   private void getTableStyleDependency(TableDataVSAssembly assembly, List<XAssetDependency> dependencies)
   {
      String tstyle = assembly.getTableStyle();

      if(tstyle == null || tstyle.length() == 0) {
         return;
      }

      String desc =
         generateDescription(catalog.getString("common.xasset.assembly", assembly.getName()),
                             catalog.getString("common.xasset.style", tstyle));
      dependencies.add(new XAssetDependency(new TableStyleAsset(tstyle), this,
                                            XAssetDependency.VIEWSHEET_TABLESTYLE, desc));
   }

   /**
    * Add the dependencies of the asset to the list.
    */
   private void addDependencies(XAsset asset, List dependencies) {
      XAssetDependency[] deps = SUtil.getXAssetDependencies(asset);

      for(int i = 0; i < deps.length; i++) {
         if(!dependencies.contains(deps[i])) {
            dependencies.add(deps[i]);
         }
      }
   }

   /**
    * Get all users.
    */
   private IdentityID[] getAllUsers() {
      SecurityProvider provider = null;

      try {
         provider = SecurityEngine.getSecurity().getSecurityProvider();
      }
      catch(Exception e) {
         LOG.warn("Failed to get security provider", e);
      }

      return provider != null ? provider.getUsers() : new IdentityID[]{ new IdentityID("anonymous", OrganizationManager.getInstance().getCurrentOrgID()) };
   }

   /**
    * Process script of DynamicValues.
    *
    * @param dvalues the DynamicValues.
    * @param dep     the dependencies to process.
    * @param sheet   the viewsheet th process.
    */
   private void processDVScript(List<DynamicValue> dvalues, List<XAssetDependency> dep, Viewsheet sheet) {
      if(dvalues != null && !dvalues.isEmpty()) {
         for(DynamicValue dvalue : dvalues) {
            String dval = dvalue.getDValue();

            if(VSUtil.isScriptValue(dval)) {
               String script = dval.substring(1);
               String desc = catalog.getString("common.xasset.viewsheet", getPath());
               processScript(script, dep, desc, sheet);
            }
         }
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(ViewsheetAsset.class);
}
