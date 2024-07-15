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
package inetsoft.util.dep;

import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.jdbc.JDBCQuery;
import inetsoft.uql.path.XSelection;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.Tool;
import inetsoft.util.TransformerManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * WorksheetAsset represents a worksheet type asset.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public class WorksheetAsset extends AbstractSheetAsset implements
   FolderChangeableAsset, AutoDrillAsset
{
   /**
    * Worksheet type XAsset.
    */
   public static final String WORKSHEET = "WORKSHEET";

   /**
    * Constructor.
    */
   public WorksheetAsset() {
      super();
   }

   /**
    * Constructor.
    * @param worksheet the worksheet asset entry.
    */
   public WorksheetAsset(AssetEntry worksheet) {
      this();
      this.entry = worksheet;
   }

   /**
    * Constructor.
    * @param worksheet the worksheet asset entry.
    * @param engine the specified asset engine.
    */
   public WorksheetAsset(AssetEntry worksheet, AssetRepository engine) {
      this(worksheet);
      this.engine = engine;
   }

   /**
    * Get all dependencies of this asset.
    * @return an array of XAssetDependency.
    */
   @Override
   public XAssetDependency[] getDependencies() {
      List<XAssetDependency> dependencies = new ArrayList<>();
      engine = engine == null ?
         AssetUtil.getAssetRepository(false) : engine;
      Worksheet sheet = (Worksheet) getCurrentSheet(engine);

      if(sheet != null) {
         Assembly[] assemblies = sheet.getAssemblies(true);

         for(int i = 0; i < assemblies.length; i++) {
            // ignore the copied outer assembly
            if(assemblies[i].getName().startsWith(AssetUtil.OUTER_PREFIX)) {
               continue;
            }

            getDataDependency(assemblies[i], dependencies);
            getWSDependency(assemblies[i], dependencies);
            getQueryDependency(assemblies[i], dependencies);
            getAutoDrillDependency(assemblies[i], dependencies);

            // check if depends on a script
            if(assemblies[i] instanceof TableAssembly) {
               ColumnSelection columnsel =
                  ((TableAssembly) assemblies[i]).getColumnSelection();
               Enumeration columns = columnsel.getAttributes();

               while(columns.hasMoreElements()) {
                  ColumnRef col = (ColumnRef) columns.nextElement();
                  DataRef ref = col.getDataRef();

                  if(ref instanceof ExpressionRef) {
                     ExpressionRef exp = (ExpressionRef) col.getDataRef();
                     String script = exp.getScriptExpression();
                     String desc =
                        catalog.getString("common.xasset.worksheet", getPath());
                     processScript(script, dependencies, desc, sheet);
                  }
               }

               ConditionListWrapper preCons =
                  ((TableAssembly) assemblies[i]).getPreConditionList();
               processConditionScript(preCons, dependencies, sheet);

               ConditionListWrapper postCons =
                  ((TableAssembly) assemblies[i]).getPostConditionList();
               processConditionScript(postCons, dependencies, sheet);

               ConditionListWrapper rankCons =
                  ((TableAssembly) assemblies[i]).getRankingConditionList();
               processConditionScript(rankCons, dependencies, sheet);
            }
         }
      }

      return dependencies.toArray(new XAssetDependency[0]);
   }

   /**
    * Get the type of this asset.
    * @return the type of this asset.
    */
   @Override
   public String getType() {
      return WORKSHEET;
   }

   /**
    * Parse an identifier to a real asset.
    * @param identifier the specified identifier, usually with the format of
    * ClassName^identifier.
    */
   @Override
   public void parseIdentifier(String identifier) {
      int idx = identifier.indexOf("^");
      String className = identifier.substring(0, idx);

      if(!className.equals(getClass().getName())) {
         return;
      }

      identifier = identifier.substring(idx + 1);
      entry = AssetEntry.createAssetEntry(identifier);
   }

   /**
    * Create an asset by its path and owner if any.
    *
    * @param path         the specified asset path.
    * @param userIdentity the specified asset owner if any.
    */
   @Override
   public void parseIdentifier(String path, IdentityID userIdentity) {
      int scope = userIdentity != null ?
         AssetRepository.USER_SCOPE : AssetRepository.GLOBAL_SCOPE;
      entry = new AssetEntry(scope, AssetEntry.Type.WORKSHEET, path, userIdentity);
   }

   /**
    * Convert this asset to an identifier.
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
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      AssetEntry worksheet2 = (AssetEntry) entry.clone();
      return new WorksheetAsset(worksheet2, engine);
   }

   /**
    * Get corresponding sheet object.
    */
   @Override
   public AbstractSheet getSheet0() {
      return new Worksheet();
   }

   /**
    * Get the worksheet type.
    */
   public int getWorksheetType() {
      AssetRepository repository = engine == null ?
         AssetUtil.getAssetRepository(false) : engine;
      Worksheet sheet = null;
      int type = Worksheet.TABLE_ASSET;

      try {
         sheet = (Worksheet) repository.getSheet(entry, null, false,
                                                 AssetContent.ALL);
      }
      catch(Exception e) {
         LOG.warn("Failed to get worksheet: " + entry, e);
      }

      if(sheet != null) {
         WSAssembly primary = sheet.getPrimaryAssembly();
         type = primary != null ? primary.getAssemblyType() : type;
      }

      return type;
   }

   protected void getAutoDrillDependency(Assembly assembly, List<XAssetDependency> dependencies) {
      if(!(assembly instanceof SQLBoundTableAssembly) ||
         ((SQLBoundTableAssembly) assembly).isAdvancedEditing())
      {
         return;
      }

      SQLBoundTableAssemblyInfo info =
         (SQLBoundTableAssemblyInfo) ((SQLBoundTableAssembly) assembly).getTableInfo();
      JDBCQuery query = info.getQuery();

      if(query == null || query.getSelection() == null) {
         return;
      }

      XSelection selection = query.getSelection();
      String fromDesc = getPath();

      for(int i = 0; i < selection.getColumnCount(); i++) {
         getAutoDrillDependency(selection.getXMetaInfo(i), dependencies, fromDesc);
      }
   }

   /**
    * Get ws-data source dependency.
    */
   protected void getDataDependency(Assembly assembly, List<XAssetDependency> dependencies) {
      if(assembly instanceof AttachedAssembly ||
         assembly instanceof BoundTableAssembly)
      {
         SourceInfo info = null;

         if(assembly instanceof AttachedAssembly) {
            AttachedAssembly asmb = ((AttachedAssembly) assembly);

            if((asmb.getAttachedType() & AttachedAssembly.SOURCE_ATTACHED)
               == AttachedAssembly.SOURCE_ATTACHED)
            {
               info = asmb.getAttachedSource();
            }
         }
         else if(assembly instanceof BoundTableAssembly) {
            info = ((BoundTableAssembly) assembly).getSourceInfo();
         }

         if(info != null && !info.isEmpty() &&
            (info.getType() == SourceInfo.PHYSICAL_TABLE ||
             info.getType() == SourceInfo.MODEL ||
             info.getType() == SourceInfo.DATASOURCE ||
             AssetEventUtil.isCubeType(info.getType())))
         {
            String dataSource = info.getPrefix();
            String source = info.getSource();
            String desc = catalog.getString("common.xasset.assembly1",
               assembly.getName());

            boolean logmodel = false;

            try {
               logmodel = DataSourceRegistry.getRegistry().
                  getDataModel(dataSource).getLogicalModel(source) !=
                  null;
            }
            catch(Exception e) {
            }

            getSourceDependencies(dependencies, dataSource, source,
               logmodel, desc,
               XAssetDependency.WORKSHEET_DATASOURCE,
               XAssetDependency.WORKSHEET_XLOGICALMODEL);
         }
      }
   }

   /**
    * Get ws-ws dependency.
    */
   protected void getWSDependency(Assembly assembly, List<XAssetDependency> dependencies) {
      if(assembly instanceof MirrorAssembly) {
         AssetEntry entry = ((MirrorAssembly) assembly).getEntry();

         if(entry != null) {
            String desc = generateDescription(
               catalog.getString("common.xasset.assembly1", assembly.getName()),
               entry.getDescription());
            WorksheetAsset wsAsset =
               entry.getScope() == AssetRepository.REPORT_SCOPE ?
               new WorksheetAsset(entry, engine) :
               new WorksheetAsset(entry);
            dependencies.add(new XAssetDependency(wsAsset, this,
               XAssetDependency.WORKSHEET_WORKSHEET, desc));
         }
      }
   }

   /**
    * Get ws-query dependency.
    */
   protected void getQueryDependency(Assembly assembly, List<XAssetDependency> dependencies) {
      if(assembly instanceof BoundTableAssembly ||
         assembly instanceof NamedGroupAssembly ||
         assembly instanceof VariableAssembly ||
         assembly instanceof ConditionAssembly)
      {
         SourceInfo info = null;

         if(assembly instanceof BoundTableAssembly) {
            info = ((BoundTableAssembly) assembly).getSourceInfo();
         }
         else if(assembly instanceof AttachedAssembly) {
            AttachedAssembly asmb = (AttachedAssembly) assembly;

            if((asmb.getAttachedType() & AttachedAssembly.SOURCE_ATTACHED)
               == AttachedAssembly.SOURCE_ATTACHED)
            {
               info = asmb.getAttachedSource();
            }
         }

         if(info != null && !info.isEmpty()) {
            if(info.getType() == SourceInfo.PHYSICAL_TABLE) {
              String desc = generateDescription(getPath(),
                 catalog.getString("common.xasset.dataSource", info.getPrefix()));
               dependencies.add(new XAssetDependency(
                  new XDataSourceAsset(info.getPrefix()), this,
                  XAssetDependency.WORKSHEET_DATASOURCE, desc));
            }
         }
      }
   }

   /**
    * Get transformer type.
    */
   @Override
   protected String getTransformerType() {
      return TransformerManager.WORKSHEET;
   }

   /**
    * Get script dependency type.
    */
   @Override
   protected int getScriptDependencyType() {
      return XAssetDependency.WORKSHEET_SCRIPT;
   }

   /**
    * Process conditions's script.
    * @param cons the condition to process.
    * @param deps the dependencies to process.
    * @param sheet the sheet th process.
    */
   private void processConditionScript(ConditionListWrapper cons,
      List<XAssetDependency> deps, AbstractSheet sheet)
   {
      for(int j = 0; j < cons.getConditionSize(); j++) {
         ConditionItem item = cons.getConditionItem(j);

         if(item == null || !(item.getXCondition() instanceof AssetCondition)) {
            continue;
         }

         AssetCondition acon = (AssetCondition) item.getXCondition();

         for(int k = 0; k < acon.getValueCount(); k++) {
            Object val = acon.getValue(k);

            if(val instanceof ExpressionValue) {
               ExpressionValue eval = (ExpressionValue) val;

               if(ExpressionValue.JAVASCRIPT.equals(eval.getType())) {
                  String script = eval.getExpression();
                  String desc =
                     catalog.getString("common.xasset.worksheet", getPath());
                  processScript(script, deps, desc, sheet);
               }
            }
         }
      }
   }

   @Override
   public AbstractSheet getCurrentSheet(AssetRepository engine) {
      AbstractSheet sheet = super.getCurrentSheet(engine);

      if(sheet instanceof Worksheet) {
         return sheet;
      }

      /* export snapshot as snapshot instead of converting to embedded table
      if(!snapshot) {
         // @by jasonshobe, bug1411032367385. When exporting a worksheet that
         // was imported from a snapshot, we need to convert any snapshot tables
         // into standard embedded tables.
         AssetQuerySandbox box = null;

         if(sheet != null && sheet.getAssemblies() != null) {
            for(Assembly assembly : sheet.getAssemblies()) {
               if(assembly instanceof SnapshotEmbeddedTableAssembly) {
                  SnapshotEmbeddedTableAssembly snapshot =
                     (SnapshotEmbeddedTableAssembly) assembly;

                  if(box == null) {
                     sheet = (Worksheet) sheet.clone();
                     box = new AssetQuerySandbox(sheet);
                  }

                  try {
                     EmbeddedTableAssembly embedded =
                        AssetEventUtil.convertEmbeddedTable(box, snapshot, true);
                     sheet.addAssembly(embedded);
                  }
                  catch(Exception e) {
                     throw new RuntimeException(
                        "Failed to convert snapshot table to embedded table", e);
                  }
               }
            }
         }

         if(box != null) {
            box.setWorksheet(new Worksheet());
            box.dispose();
         }
      }
      */

      return null;
   }

   /**
    * Sets a flag indicating if this worksheet is being used in a snapshot
    * export.
    *
    * @param snapshot <tt>true</tt> if a snapshot; <tt>false</tt> otherwise.
    */
   public void setSnapshot(boolean snapshot) {
      this.snapshot = snapshot;
   }

   private boolean snapshot = false;
   private AssetRepository engine;
   private static final Logger LOG =
      LoggerFactory.getLogger(WorksheetAsset.class);
}
