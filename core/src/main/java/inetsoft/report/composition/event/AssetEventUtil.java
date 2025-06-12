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
package inetsoft.report.composition.event;

import inetsoft.analytic.composition.event.CubeTreeModelBuilder;
import inetsoft.report.*;
import inetsoft.report.composition.AssetTreeModel.Node;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.WorksheetService;
import inetsoft.report.composition.execution.AssetQuery;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.internal.Util;
import inetsoft.report.lens.FormulaTableLens;
import inetsoft.report.lens.xnode.XNodeTableLens;
import inetsoft.report.script.formula.AssetQueryScope;
import inetsoft.report.style.XTableStyle;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.*;
import inetsoft.uql.schema.*;
import inetsoft.uql.table.XSwappableTable;
import inetsoft.uql.util.*;
import inetsoft.uql.viewsheet.CalculateRef;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.uql.xmla.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.script.ScriptEnv;
import inetsoft.web.composer.BrowseDataController;
import inetsoft.web.composer.model.BrowseDataModel;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.security.Principal;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility methods for asset event.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class AssetEventUtil {
   /**
    * Base worksheet.
    */
   public static final String BASE_WORKSHEET = "baseWorksheet";

   /**
    * Gets the cube node of all XDataSource objects that have an associated OLAP
    * domain.
    */
   public static List<Node> getCubes(Principal user, Properties cubeProps, Viewsheet vs)
      throws Exception
   {
      return getCubes(user, cubeProps, vs, true, null);
   }

   /**
    * Gets the cube node of all XDataSource objects that have an associated OLAP
    * domain.
    */
   private static List<Node> getCubes(Principal user, Properties cubeProps,
       Viewsheet vs, Boolean isVSIncluded, AssetSelector filter)
       throws Exception
   {
      if(cubeProps == null) {
         cubeProps = new Properties();
      }

      String prefix = cubeProps.getProperty("prefix");
      String source = cubeProps.getProperty("source");
      boolean showMeasures = vs == null ||
         !"false".equals(cubeProps.getProperty("showMeasures"));
      boolean showDimensions = vs == null ||
         !"false".equals(cubeProps.getProperty("showDimensions"));

      List<Node> cubeList = new ArrayList<>();

      if(source != null) {
         if(source.startsWith(Assembly.CUBE_VS)) {
            source = source.substring(Assembly.CUBE_VS.length());
            int idx = source.lastIndexOf("/");

            if(idx >= 0) {
               prefix = source.substring(0, idx);
               source = source.substring(idx + 1);
            }
         }

         XCube cube = getXCube(prefix, source, user);
         Node cubeNode = getCubeNode(cube, prefix, showMeasures, showDimensions,
            vs, filter);

         if(cubeNode != null) {
            cubeNode.setRequested(true);
            cubeList.add(cubeNode);
         }

         return cubeList;
      }

      XRepository repository = XFactory.getRepository();
      SecurityEngine security = SecurityEngine.getSecurity();
      String[] dxNames = repository.getDataSourceFullNames();

      for(String dxName : dxNames) {
         if(isVSIncluded && !checkDataSourcePermission(dxName, user)) {
            continue;
         }

         XDomain domain = repository.getDomain(dxName);

         if(domain == null) {
            continue;
         }

         Enumeration cubes = domain.getCubes();

         while(cubes.hasMoreElements()) {
            XCube cube = (XCube) cubes.nextElement();

            // in case has name but not really created
            if(cube == null) {
               continue;
            }

            ResourceType type;
            String resource;

            if(cube instanceof Cube) {
               type = ResourceType.CUBE;
               resource = dxName + "::" + cube.getName();
            }
            else {
               type = ResourceType.QUERY;
               resource = cube.getName() + "::" + dxName;
            }

            if(isVSIncluded &&
               !security.checkPermission(user, type, resource, ResourceAction.READ))
            {
               continue;
            }

            if(vs == null && !(cube instanceof Cube)) {
               continue;
            }

            String searchString = (filter instanceof SearchAssetSelector) ?
               ((SearchAssetSelector) filter).getSearchString() : null;

            if(searchString != null &&
               cube.getName().toLowerCase().indexOf(searchString) < 0) {
               continue;
            }

            Node tempNode = getCubeNode(cube, dxName, showMeasures,
                                        showDimensions, vs, filter);

            if(tempNode != null) {
               // If cube table is expand, setRequested to true so to do not
               // expand it again. So the variable input dialog will not popup
               // again.
               if(filter != null && filter.traverse(tempNode.getEntry())) {
                  tempNode.setRequested(true);
               }

               cubeList.add(tempNode);
            }
         }
      }

      return cubeList;
   }

   /**
    * Get a cube by cube name.
    */
   public static XCube getXCube(String prefix, String source, Principal user)
      throws Exception
   {
      if(prefix == null || source == null) {
         return null;
      }

      if(!checkDataSourcePermission(prefix, user)) {
         return null;
      }

      XRepository repository = XFactory.getRepository();
      SecurityEngine security = SecurityEngine.getSecurity();
      XDomain domain = repository.getDomain(prefix);

      if(domain == null) {
         return null;
      }

      Resource resource;

      if(domain instanceof Domain) {
         resource = new Resource(ResourceType.CUBE, prefix + "::" + source);
      }
      else {
         resource = new Resource(ResourceType.QUERY, source + "::" + prefix);
      }

      if(!security.checkPermission(
         user, resource.getType(), resource.getPath(), ResourceAction.READ))
      {
         return null;
      }

      return domain.getCube(source);
   }

   /**
    * Convert an XCube to a tree model node.
    */
   private static Node getCubeNode(XCube cube, String datasource,
      boolean showMeasures, boolean showDimensions, Viewsheet vs,
      AssetSelector filter)
   {
      if(cube == null || isEmpty(cube)) {
         return null;
      }

      int type = vs == null ?
         AssetRepository.QUERY_SCOPE : AssetRepository.GLOBAL_SCOPE;

      AssetEntry tempEntry = new AssetEntry(type, AssetEntry.Type.TABLE,
                                            datasource + "/" + cube.getName(), null);

      String tableName = vs == null ? tempEntry.getPath() :
         Assembly.CUBE_VS + tempEntry.getPath();
      // if is cube table, not allowed dnd
      tempEntry.setProperty("CUBE_TABLE", "true");
      tempEntry.setProperty("source", BASE_WORKSHEET);
      tempEntry.setProperty("localStr", (cube instanceof Cube ?
         ((Cube) cube).getCaption() : cube.getName()) + "(" + datasource + ")");
      tempEntry.setProperty("table", tableName);
      String sqlServer = "SQLServer".equals(cube.getType()) ? "true" : "false";
      tempEntry.setProperty("sqlServer", sqlServer);
      Node tempNode = new Node(tempEntry);

      if(filter == null || filter.traverse(tempEntry)) {
         appendCubeMembers(cube, tempNode, showMeasures, showDimensions,
            tableName, vs, filter);
      }

      return tempNode;
   }

   /*
    * Append cube members nodes.
    * @param cube the cube.
    * @param parentNode the parent node.
    * @param showMeasures the showMeasures property.
    * @param showDimensions the showDimensions property.
    * @param tableName the cube's name.
    */
   private static void appendCubeMembers(XCube cube, Node parentNode,
      boolean showMeasures, boolean showDimensions, String tableName,
      Viewsheet vs, AssetSelector filter)
   {
      appendCubeDimensions(cube, parentNode, showDimensions, tableName, vs,
         filter);
      appendCubeMeasures(cube, parentNode, showMeasures, tableName, vs, filter);
   }

   /*
    * Append cube measure nodes.
    * @param cube the cube.
    * @param parentNode the parent node.
    * @param showDimensions the showDimensions property.
    * @param tableName the cube's name.
    */
   private static void appendCubeDimensions(XCube cube, Node parentNode,
      boolean showDimensions, String tableName, Viewsheet vs,
      AssetSelector filter)
   {
      if(!showDimensions) {
         return;
      }

      int type = vs == null ?
         AssetRepository.QUERY_SCOPE : AssetRepository.GLOBAL_SCOPE;

      AssetEntry dmsEntry = new AssetEntry(type, AssetEntry.Type.FOLDER,
         parentNode.getEntry().getPath() + "/"
            + Catalog.getCatalog().getString("Dimension"), null);
      dmsEntry.setProperty("localStr", Catalog.getCatalog().getString("Dimension"));
      Node dmsRootNode = new Node(dmsEntry);
      dmsRootNode.setRequested(true);
      parentNode.addNode(dmsRootNode);

      if(filter != null && !filter.traverse(dmsEntry)) {
         // Set requested to false to the nodes not expanded, so it can expand
         // its nodes when click. Then the refresh tree event can get its expand
         // children to expand tree proper.
         dmsRootNode.setRequested(false);
         return;
      }

      Enumeration dms = cube.getDimensions();
      Map dgroups = XMLAUtil.getDimensionGroups(cube);
      ArrayList<Node> nodes = new ArrayList<>();

      if(dgroups != null) {
         for(Object o : dgroups.keySet()) {
            String dimStr = (String) o;

            if(cube.getMeasure(dimStr) != null) {
               continue;
            }

            Vector vec = (Vector) dgroups.get(dimStr);
            AssetEntry dimEntry = new AssetEntry(type, AssetEntry.Type.FOLDER,
                                                 dmsRootNode.getEntry().getPath() + "/" +
                                                    Tool.replaceAll(dimStr, "/", "^_^"),
                                                 null);
            Node tempNode = new Node(dimEntry);
            nodes.add(tempNode);

            if(filter != null && !filter.traverse(dimEntry)) {
               tempNode.setRequested(false);
               continue;
            }

            tempNode.setRequested(true);
            ArrayList<Node> nodes0 = new ArrayList<>();

            for(Object item : vec) {
               HierDimension hdim = (HierDimension) item;

               if(hdim == null) {
                  continue;
               }

               Node node0 = getDimensionNode(
                  cube, tableName, hdim, dimEntry.getPath(), vs, filter);

               if(node0 == null) {
                  continue;
               }

               nodes0.add(node0);
            }

            nodes0.sort(new NodeComparator());

            for(Node node : nodes0) {
               tempNode.addNode(node);
            }
         }
      }
      else {
         while(dms.hasMoreElements()) {
            XDimension dm = (XDimension) dms.nextElement();

            if(cube.getMeasure(dm.getName()) != null) {
               continue;
            }

            Node tempNode = getDimensionNode(cube, tableName, dm,
               dmsRootNode.getEntry().getPath(), vs, filter);

            if(tempNode == null) {
               continue;
            }

            nodes.add(tempNode);
         }
      }

      // sort nodes
      nodes.sort(new NodeComparator());

      for(Node node : nodes) {
         dmsRootNode.addNode(node);
      }
   }

   /**
    * Get dimension node.
    * @param cube the cube.
    * @param tableName the cube's name.
    * @param dm Dimension.
    * @param ppath parent path.
    * @return dimension node.
    */
   private static Node getDimensionNode(XCube cube, String tableName,
      XDimension dm, String ppath, Viewsheet vs, AssetSelector filter) {
      if(cube.getMeasure(dm.getName()) != null) {
         return null;
      }

      if(dm.getLevelCount() == 0) {
         return null;
      }

      String label0 = AssetUtil.getCaption(dm);
      String source = null;
      String prefix = null;
      int type = vs == null ?
         AssetRepository.QUERY_SCOPE : AssetRepository.GLOBAL_SCOPE;

      AssetEntry tempEntry = new AssetEntry(type, AssetEntry.Type.FOLDER,
         ppath + "/" + Tool.replaceAll(label0, "/", "^_^"),
         null);

      // if is dimension folder, allowed dnd
      tempEntry.setProperty("DIMENSION_FOLDER", "true");
      tempEntry.setProperty("table", tableName);
      tempEntry.setProperty("DIMENSION_NAME", dm.getName());
      tempEntry.setProperty("caption", label0);

      if(tableName != null) {
         if(!tableName.startsWith(Assembly.CUBE_VS)) {
            source = tableName;
            int idx = source.lastIndexOf("/");

            if(idx >= 0) {
               prefix = source.substring(0, idx);
               tableName = source = source.substring(idx + 1);
            }
         }

      }

      if(dm instanceof HierDimension) {
         tempEntry.setProperty("user_defined_hier",
            "" + ((HierDimension) dm).isUserDefined());
      }

      Node tempNode = new Node(tempEntry);

      for(int i = 0; i < dm.getLevelCount(); i++) {
         XCubeMember ms = dm.getLevelAt(i);
         String label = ms instanceof DimMember ?
            ((DimMember) ms).getCaption() : ms.getName();
         AssetEntry tEntry = new AssetEntry(type, AssetEntry.Type.COLUMN,
            tempNode.getEntry().getPath() + "/" +
            Tool.replaceAll(label, "/", "^_^"), null);
         tEntry.setProperty("caption", VSUtil.getCaption(dm, ms));
         tEntry.setProperty("assembly", tableName);
         tEntry.setProperty("entity", dm.getName());
         tEntry.setProperty("attribute", ms.getName());
         tEntry.setProperty("dtype", ms.getType());
         tEntry.setProperty("table", tableName);
         tEntry.setProperty("type", dm.getType() + "");
         tEntry.setProperty("refType", dm.getType() + "");
         tEntry.setProperty("alias", dm.getName() + "." + ms.getName());
         tEntry.setProperty("source", source != null ? source : BASE_WORKSHEET);
         tEntry.setProperty(AssetEntry.CUBE_COL_TYPE,
                            AssetEntry.DIMENSIONS + "");
         tEntry.setProperty("level_number", i + "");

         if(prefix != null) {
            tEntry.setProperty("prefix", prefix);
         }

         Node tNode = new Node(tEntry);

         if(filter == null || filter.traverse(tempEntry)) {
            tempNode.setRequested(true);
            tNode.setRequested(true);
            tempNode.addNode(tNode);
         }
         else {
            tempNode.setRequested(false);
         }
      }

      return tempNode;
   }

   /*
    * Append cube measure nodes.
    * @param cube the cube.
    * @param parentNode the parent node.
    * @param showMeasures the showMeasures property.
    * @param tableName the cube's name.
    */
   private static void appendCubeMeasures(XCube cube, Node parentNode,
      boolean showMeasures, String tableName, Viewsheet vs,
      AssetSelector filter)
   {
      if(!showMeasures) {
         return;
      }

      String source = null;
      String prefix = null;
      int type = vs == null ?
         AssetRepository.QUERY_SCOPE : AssetRepository.GLOBAL_SCOPE;

      AssetEntry mssEntry = new AssetEntry(type, AssetEntry.Type.FOLDER,
         parentNode.getEntry().getPath() + "/"
            + Catalog.getCatalog().getString("Measure"), null);
      mssEntry.setProperty("localStr", Catalog.getCatalog().getString("Measure"));
      String sqlServer = "SQLServer".equals(cube.getType()) ? "true" : "false";
      mssEntry.setProperty("sqlServer", sqlServer);
      Node mssRootNode = new Node(mssEntry);
      mssRootNode.setRequested(true);
      parentNode.addNode(mssRootNode);

      if(filter != null && !filter.traverse(mssEntry)) {
         mssRootNode.setRequested(false);
         return;
      }

      Enumeration mss = cube.getMeasures();
      ArrayList<Node> nodes = new ArrayList<>();

      if(tableName != null) {
         if(!tableName.startsWith(Assembly.CUBE_VS)) {
            source = tableName;
            int idx = source.lastIndexOf("/");

            if(idx >= 0) {
               prefix = source.substring(0, idx);
               source = source.substring(idx + 1);
            }
         }

      }

      while(mss.hasMoreElements()) {
         XCubeMember ms = (XCubeMember) mss.nextElement();

         if(cube.getDimension(ms.getName()) != null && (ms.getDataRef() != null
            && (ms.getDataRef().getRefType() & DataRef.MEASURE) == 0))
         {
            continue;
         }

         String label = ms instanceof Measure ?
            ((Measure) ms).getCaption() : ms.getName();
         AssetEntry tempEntry = new AssetEntry(
            AssetRepository.GLOBAL_SCOPE,
            AssetEntry.Type.COLUMN,
            mssRootNode.getEntry().getPath() + "/" +
            Tool.replaceAll(label, "/", "^_^"), null);
         tempEntry.setProperty("caption", label);
         tempEntry.setProperty("assembly", tableName);
         tempEntry.setProperty("attribute", ms.getName());
         tempEntry.setProperty("dtype", ms.getType());
         tempEntry.setProperty("table", tableName);
         tempEntry.setProperty("type", DataRef.CUBE_MEASURE + "");
         tempEntry.setProperty("refType", DataRef.CUBE_MEASURE + "");
         tempEntry.setProperty("alias", ms.getName());
         tempEntry.setProperty("sqlServer", sqlServer);
         tempEntry.setProperty(
            AssetEntry.CUBE_COL_TYPE, AssetEntry.MEASURES+"");
         tempEntry.setProperty("source", source);

         if(prefix != null) {
            tempEntry.setProperty("prefix", prefix);
         }

         Node tempNode = new Node(tempEntry);
         nodes.add(tempNode);
      }

      if(vs != null) {
         CubeTreeModelBuilder.addExpressionMeasureNode(mssRootNode, vs);
         CalculateRef[] calcs = vs.getCalcFields(tableName);

         if(calcs != null) {
            for(CalculateRef cref : calcs) {
               String name = cref.getName();

               if(cube.getDimension(name) != null) {
                  continue;
               }

               ExpressionRef eref = (ExpressionRef) cref.getDataRef();
               String expression = eref.getExpression();

               AssetEntry tempEntry = new AssetEntry(
                  AssetRepository.GLOBAL_SCOPE,
                  AssetEntry.Type.COLUMN,
                  mssRootNode.getEntry().getPath() + "/" + name, null);
               tempEntry.setProperty("caption", name);
               tempEntry.setProperty("assembly", tableName);
               tempEntry.setProperty("attribute", name);
               tempEntry.setProperty("dtype", cref.getDataType());
               tempEntry.setProperty("table", tableName);
               tempEntry.setProperty("refType", DataRef.CUBE_MEASURE + "");
               tempEntry.setProperty("alias", name);
               tempEntry.setProperty("sqlServer", sqlServer);
               tempEntry.setProperty("expression", expression);
               tempEntry.setProperty(
                  AssetEntry.CUBE_COL_TYPE, AssetEntry.MEASURES + "");
               appendCalcProperty(tempEntry, cref);
               Node tempNode = new Node(tempEntry);
               nodes.add(tempNode);
            }
         }
      }

      nodes.sort(new NodeComparator());

      for(Node node : nodes) {
         node.setRequested(true);
         mssRootNode.addNode(node);
      }
   }

   /**
    * if the type is a cube type.
    */
   public static Boolean isCubeType(int type) {
      return type == SourceInfo.CUBE || (type & DataRef.CUBE_TIME_DIMENSION) ==
      DataRef.CUBE_TIME_DIMENSION || (type & DataRef.CUBE_MODEL_DIMENSION) ==
      DataRef.CUBE_MODEL_DIMENSION || (type & DataRef.CUBE_MODEL_TIME_DIMENSION)
      == DataRef.CUBE_MODEL_TIME_DIMENSION || (type & DataRef.CUBE_MEASURE)
      == DataRef.CUBE_MEASURE;
   }

   /**
    * Append calc property to a cube measure.
    */
   public static void appendCalcProperty(AssetEntry entry, ColumnRef ref) {
      boolean isCalc = ref instanceof CalculateRef;
      entry.setProperty("isCalc", isCalc + "");

      if(isCalc) {
         CalculateRef cref = (CalculateRef) ref;
         ExpressionRef eref = (ExpressionRef) cref.getDataRef();

         entry.setProperty("basedOnDetail", cref.isBaseOnDetail() + "");
         entry.setProperty("isSQL", cref.isSQL() + "");
         entry.setProperty("dtype", cref.getDataType());
         entry.setProperty("isPreparedCalc", VSUtil.isPreparedCalcField(ref) + "");
         entry.setProperty("script", eref.getExpression());

         if(!cref.isBaseOnDetail()) {
            int refType = entry.getProperty("refType") == null ?
               DataRef.NONE : Integer.parseInt(entry.getProperty("refType"));
            entry.setProperty("refType", (DataRef.AGG_CALC | refType) + "");
         }
      }
   }

   /**
    * Case insensitive comparison of node entry names.
    */
   public static class NodeComparator implements Comparator<Node> {
      @Override
      public int compare(Node a, Node b) {
         if(a == null && b == null) {
            return 0;
         }
         else if(a == null) {
            return -1;
         }
         else if(b == null) {
            return 1;
         }

         return compareEntry(a.getEntry(), b.getEntry(), false);
      }
   }

   /**
    * Case insensitive comparison of entry names.
    */
   private static int compareEntry(AssetEntry entry0, AssetEntry entry1,
                                   boolean isLogicModelEntityLevel) {
      if(isLogicModelEntityLevel) {
         if(entry0.getProperty("isCalc") != null &&
            entry1.getProperty("isCalc") == null)
         {
            return -1;
         }
         else if(entry0.getProperty("isCalc") == null &&
            entry1.getProperty("isCalc") != null)
         {
            return 1;
         }
      }

      String label0 = entry0.getProperty("localStr");
      String label1 = entry1.getProperty("localStr");
      label0 = label0 == null ? entry0.getName() : label0;
      label1 = label1 == null ? entry1.getName() : label1;
      return label0.compareTo(label1);
   }

   /**
    * Check the datasource permission.
    * @param dname the specified datasource.
    * @param user the specified user.
    * @return <tt>true</tt> if pass, <tt>false</tt> otherwise.
    */
   private static boolean checkDataSourcePermission(String dname,
                                                    Principal user) {
      return SUtil.checkDataSourcePermission(dname, user);
   }

   /**
    * Check if a cube is empty.
    */
   private static boolean isEmpty(XCube cube) {
      return cube == null || !cube.getDimensions().hasMoreElements()
         && !cube.getMeasures().hasMoreElements();
   }

   /**
    * Find the variable assembly.
    */
   private static VariableAssembly findVariable(Worksheet ws, String name) {
      return Arrays.stream(ws.getAssemblies())
         .filter(a -> a instanceof VariableAssembly)
         .map(a -> (VariableAssembly) a)
         .filter(a -> {
               UserVariable var = a.getVariable();
               return Tool.equals(a.getName(), name) ||
                  var != null && Tool.equals(var.getName(), name);
            })
         .findAny()
         .orElse(null);
   }

   /**
    * Get a list of all variables and replace with variable assembly. All
    * variable binding will be executed up on return.
    */
   public static UserVariable[] executeVariables(WorksheetService engine,
                                                 AssetQuerySandbox box,
                                                 VariableTable vart,
                                                 WSAssembly assembly) {
      return executeVariables(engine, box, vart, assembly, null);
   }

   /**
    * Get a list of all variables and replace with variable assembly. All
    * variable binding will be executed up on return. use userEnv to save
    * last input values to variable input dialog, if vsName is null, it is
    * from worksheet, else viewsheet.
    */
   public static UserVariable[] executeVariables(WorksheetService engine,
                                                 AssetQuerySandbox box,
                                                 VariableTable vart,
                                                 WSAssembly assembly,
                                                 String vsName) {
      return executeVariables(engine, box, vart, assembly, vsName, null, null);
   }

   /**
    * Get a list of all variables and replace with variable assembly. All
    * variable binding will be executed up on return. use userEnv to save
    * last input values to variable input dialog, if vsName is null, it is
    * from worksheet, else viewsheet.
    * @param initvars parameters passed in for opening a new vs. They shouldn't
    * be prompted again.
    */
   public static UserVariable[] executeVariables(WorksheetService engine,
      AssetQuerySandbox box, VariableTable vart, WSAssembly assembly,
      String vsName, String wsName, VariableTable initvars)
   {
      return executeVariables(engine, box, vart, assembly, vsName, wsName,
                              initvars, false);
   }

   /**
    * Get a list of all variables and replace with variable assembly. All
    * variable binding will be executed up on return. use userEnv to save
    * last input values to variable input dialog, if vsName is null, it is
    * from worksheet, else viewsheet.
    * @param initvars parameters passed in for opening a new vs. They shouldn't
    * be prompted again.
    * @param varNameOnly if just get variable name.
    */
   public static UserVariable[] executeVariables(WorksheetService engine,
      AssetQuerySandbox box, VariableTable vart, WSAssembly assembly,
      String vsName, String wsName, VariableTable initvars, boolean varNameOnly)
   {
      Worksheet ws = box.getWorksheet();
      String name = vsName == null ? ((wsName == null) ? box.getWSName() : wsName) : vsName;
      UserVariable[] variables;
      Principal user = box.getUser();

      if(assembly == null) {
         variables = (initvars != null) ? box.getAllVariables(initvars) : box.getAllVariables(vart);
      }
      else {
         variables = assembly.getAllVariables();
      }

      List<UserVariable> list = Arrays.stream(variables)
         .filter(v -> !VariableTable.isContextVariable(v.getName()))
         .collect(Collectors.toList());

      variables = new UserVariable[list.size()];
      list.toArray(variables);
      List<UserVariable> allVariableAssemblies = getWSAllVariables(ws);
      list.forEach(item -> {
         boolean exist = allVariableAssemblies.stream()
            .filter(var -> var != null)
            .anyMatch(var -> Tool.equals(var.getName(), item.getName()));

         if(!exist) {
            allVariableAssemblies.add(item.clone());
         }
      });

      Map<String, Object> executedVars = new HashMap<>();
      AssetQueryScope scope = box.getScope();
      VariableTable oldVars = scope.getVariableTable();

      for(int i = 0; i < variables.length; i++) {
         String vname = variables[i].getName();
         VariableAssembly vassembly = findVariable(ws, vname);
         boolean used = usedInCondition(ws, vname);
         Object varValue = user == null || engine == null ? null :
            engine.getCachedProperty(user, name + " variable : " + vname);

         if(vassembly != null) {
            variables[i] = vassembly.getVariable();
         }

         variables[i] = variables[i].clone();
         variables[i].setUsedInOneOf(used || variables[i].isUsedInOneOf());

         try {
            if(vart != null && varValue != null) {
               XValueNode lastVal = XValueNode.createValueNode(varValue, "default");
               variables[i].setValueNode(lastVal);
            }

            if(!varNameOnly && !variables[i].isExecuted()) {
               AssetEventUtil.executeVariable(box, variables[i]);

               if(variables[i].getValueNode() != null && variables[i].getValueNode().isExpression())
               {
                  XValueNode result = executeExpressionVariable(scope, box.getScriptEnv(),
                     variables[i], allVariableAssemblies, executedVars);
                  variables[i].setValueNode(result);
               }
            }
         }
         catch(Exception ex) {
            LOG.warn("Failed to set value of variable " + variables[i] + " to " + varValue, ex);
         }

         variables[i].setName(vname);
      }

      scope.setVariableTable(oldVars);

      return variables;
   }

   private static XValueNode getExecuteExpressionValue(UserVariable var, Object result) {
      if(var == null) {
         return null;
      }

      if(result == null && var.getValueNode() != null) {
         var.setValueNode(null);

         return var.getValueNode();
      }

      if(result instanceof Object[]){
         int displayStyle = var.getDisplayStyle();

         if(result instanceof Object[] && ((Object[]) result).length > 0 &&
            var.getTypeNode() != null &&
            displayStyle != AssetVariable.RADIO_BUTTONS ||
            displayStyle != AssetVariable.COMBOBOX)
         {
            Object[] results = (Object[]) result;
            XValueNode node = XValueNode.createValueNode(results[0], "default");

            if(node == null) {
               return var.getValueNode();
            }

            Object[] values = new Object[results.length];

            for(int i = 0; i < values.length; i++) {
               XValueNode item = XValueNode.createValueNode(results[i], "default");

               if(item == null) {
                  continue;
               }

               if((displayStyle == AssetVariable.CHECKBOXES || displayStyle == AssetVariable.LIST)
                  && !ArrayUtils.contains(var.getValues(), item.getValue()))
               {
                  continue;
               }

               values[i] = item.getValue();
            }

            values = Arrays.stream(values).
               filter(v -> v != null).toArray(Object[]::new);

            node.setValue(values);
            var.setValueNode(node);
         }
      }
      else {
         XValueNode lastVal = XValueNode.createValueNode(result,
            "default", var.getTypeNode().getType());
         var.setValueNode(lastVal);
      }

      return fixVariableValue(var);
   }

   /**
    * Make the variable value type correct,
    * try to fix the value to correct type or else lost the value.
    * @param var
    */
   private static XValueNode fixVariableValue(UserVariable var) {
      if(var == null || var.getValueNode() == null || var.getValueNode().getValue() == null ||
         var.getTypeNode() == null || var.getTypeNode().getType() == null)
      {
         return var == null ? null : var.getValueNode();
      }

      String varType = var.getTypeNode().getType();

      if(XSchema.isDateType(varType) && Tool.isDateClass(var.getValueNode().getValue().getClass()))
      {
         Date dateValue = (Date) var.getValueNode().getValue();

         if(XSchema.DATE.equals(varType)) {
            dateValue = new java.sql.Date(dateValue.getTime());
         }
         else if(XSchema.TIME.equals(varType)) {
            dateValue = new Time(dateValue.getTime());
         }
         else if(XSchema.TIME_INSTANT.equals(varType)) {
            dateValue = new Timestamp(dateValue.getTime());
         }

         var.setValueNode(XValueNode.createValueNode(dateValue, "default"));
      }
      else {
         if(!Tool.equals(var.getTypeNode().getType(), var.getValueNode().getType())) {
            var.getValueNode().setValue(null);
         }
      }

      return var.getValueNode();
   }

   /**
    * execute expression of variable. return null when
    * @param scope query scope.
    * @param senv script execute environment.
    * @param var execute variable
    * @param allVariableAssemblies all variables.
    * @param executedVars cache the executed variable to avoid duplicate execution
    * @return result if execute successfully, null if invalid parameter and loop dependency.
    */
   public static XValueNode executeExpressionVariable(AssetQueryScope scope,
                                                      ScriptEnv senv,
                                                      UserVariable var,
                                                      List<UserVariable> allVariableAssemblies,
                                                      Map<String, Object> executedVars)
      throws Exception
   {
      Object executeResult = executeExpressionVariable0(scope, senv, var, allVariableAssemblies,
                                                        executedVars, new HashMap<>());

      return getExecuteExpressionValue(var, executeResult);
   }

   private static Object executeExpressionVariable0(AssetQueryScope scope,
                                                   ScriptEnv senv,
                                                   UserVariable var,
                                                   List<UserVariable> allVariableAssemblies,
                                                   Map<String, Object> executedVars,
                                                   Map<String, Boolean> traversedFlag)
      throws Exception
   {
      if(var == null || var.getName() == null || var.getValueNode() == null) {
         return null;
      }

      if(!var.getValueNode().isExpression()) {
         VariableTable newVarTable = new VariableTable();
         newVarTable.put(var.getName(), var.getValueNode().getValue());
         scope.mergeVariableTable(newVarTable);

         return var.getValueNode().getValue();
      }

      Boolean traversed = traversedFlag.get(var.getName());

      // expression variable loop dependency.
      if(traversed != null && traversed) {
         return null;
      }

      if(executedVars.get(var.getName()) != null) {
         return executedVars.get(var.getName());
      }

      traversedFlag.put(var.getName(), true);
      String exp = ((ExpressionValue) var.getValueNode().getValue()).getExpression();

      try {
         List<String> depVarNames = getExpressionDepVarName(var);

         for(String depVarName : depVarNames) {
            if(StringUtils.isEmpty(depVarName)) {
               continue;
            }

            for(UserVariable variable: allVariableAssemblies) {
               if(variable == null || !depVarName.equals(variable.getName())) {
                  continue;
               }

               if(executedVars.get(variable.getName()) != null) {
                  break;
               }

               executeExpressionVariable0(scope, senv, variable, allVariableAssemblies,
                  executedVars, traversedFlag);
            }
         }

         Object result = senv.exec(senv.compile(exp), scope, null, null);
         executedVars.put(var.getName(), result);
         VariableTable newVarTable = new VariableTable();
         newVarTable.put(var.getName(), result);
         scope.mergeVariableTable(newVarTable);

         return result;
      }
      catch (Exception ex) {
         String suggestion = senv.getSuggestion(ex, null, scope);
         String msg = "Script error: " + ex.getMessage() +
            (suggestion != null ? "\nTo fix: " + suggestion : "") +
            "\nScript failed:\n" + XUtil.numbering(exp);

         if(LOG.isDebugEnabled()) {
            LOG.debug(msg, ex);
         }
         else {
            LOG.warn(msg);
         }

         Tool.addUserMessage(msg, ConfirmException.WARNING);
         throw ex;
      }
   }

   /**
    * Get a list of variable names for this worksheet.
    */
   public static List<UserVariable> getWSAllVariables(Worksheet ws) {
      Assembly[] arr = ws.getAssemblies();
      List<UserVariable> list = new ArrayList<>();
      Set added = new HashSet();

      for(int i = 0; i < arr.length; i++) {
         WSAssembly assembly = (WSAssembly) arr[i];

         if(!assembly.isVariable() || !assembly.isVisible()) {
            continue;
         }

         VariableAssembly vassembly = (VariableAssembly) assembly;
         UserVariable var = vassembly.getVariable();

         if(var != null && !added.contains(var.getName())) {
            added.add(var.getName());
            list.add(var.clone());
         }
      }

      return list;
   }

   private static List<String> getExpressionDepVarName(UserVariable var) {
      if(var.getValueNode() == null ||
         !(var.getValueNode().getValue() instanceof ExpressionValue))
      {
         return null;
      }

      List<String> result = new ArrayList<>();
      String exp = ((ExpressionValue) var.getValueNode().getValue()).getExpression();
      String depName;

      int idx1 = exp.indexOf("parameter.");

      while(idx1 >= 0) {
         depName = exp.substring(idx1 + 10);
         int j = 0;

         for(; j < depName.length(); j++) {
            char c = depName.charAt(j);

            if(!Character.isLetterOrDigit(c) && c != 95) {
               break;
            }
         }

         result.add(depName.substring(0, j));
         idx1 = exp.indexOf("parameter.", idx1 + 10 + j);
      }

      return result;
   }

   /**
    * Find the specified table assembly which has one of variable consition.
    */
   private static boolean usedInCondition(Worksheet ws, String name) {
      if(name == null) {
         return false;
      }

      Assembly[] assemblies = ws.getAssemblies();

      for(Assembly assembly : assemblies) {
         if(!(assembly instanceof TableAssembly)) {
            continue;
         }

         TableAssembly table = (TableAssembly) assembly;
         ConditionList cond = table.getPreConditionList().getConditionList();

         if(checkUsed(cond, name)) {
            return true;
         }

         cond = table.getPostConditionList().getConditionList();

         if(checkUsed(cond, name)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Find the specified table assembly which has one of variable consition.
    */
   public static boolean checkUsed(ConditionList cond, String name) {
      if(cond == null || cond.isEmpty()) {
         return false;
      }

      for(int j = 0; j < cond.getSize(); j += 2) {
         XCondition xcond = cond.getXCondition(j);

         if(xcond != null && xcond.getOperation() != XCondition.ONE_OF) {
            continue;
         }

         // Bug #59422, ignore the variables if the value is a subquery. While the subquery
         // is in fact used in a "one of" condition, the variables that this subquery depends
         // on might not be. Therefore, the variables shouldn't be flagged for being used
         // in a "one of" condition when that's the case. If the subquery does contain variables
         // in a "one of" condition then the variables will be marked as such when
         // checking the subquery's conditions.
         if(xcond instanceof AssetCondition && ((AssetCondition) xcond).getSubQueryValue() != null) {
            continue;
         }

         assert xcond != null;
         UserVariable[] vars = xcond.getAllVariables();

         for(UserVariable var : vars) {
            if(name.equals(var.getName())) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Get all available attributes by a given source info.
    * @param engine worksheet engine.
    * @param user the user.
    * @param sourceInfo the specified source info.
    * @return data ref array of available attributes.
    */
   public static ColumnSelection getAttributesBySource(WorksheetService engine,
      Principal user, SourceInfo sourceInfo) throws Exception
   {
      if(sourceInfo == null || sourceInfo.isEmpty() ||
         isCubeType(sourceInfo.getType()))
      {
         return new ColumnSelection();
      }

      DataRef[] attributes;
      ColumnSelection columns = new ColumnSelection();
      AssetEntry[] fields = null;
      int type = sourceInfo.getType();
      AssetEntry sourceEntry =
         new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.LOGIC_MODEL,
         sourceInfo.getPrefix() + "/" + sourceInfo.getSource(), null);
      sourceEntry.setProperty("type", type + "");
      sourceEntry.setProperty("prefix", sourceInfo.getPrefix());
      sourceEntry.setProperty("source", sourceInfo.getSource());

      AssetEntry[] tables = engine.getAssetRepository().
         getEntries(sourceEntry, user, ResourceAction.READ,
            new AssetEntry.Selector(AssetEntry.Type.COLUMN));

      if(type == SourceInfo.MODEL) {
         for(AssetEntry table : tables) {
            AssetEntry[] savedEntries = fields == null ?
               new AssetEntry[]{} : fields;
            AssetEntry[] tempEntries = engine.getAssetRepository().getEntries(
               table, user, ResourceAction.READ,
               new AssetEntry.Selector(AssetEntry.Type.COLUMN));
            fields = new AssetEntry[savedEntries.length + tempEntries.length];
            System.arraycopy(savedEntries, 0, fields, 0, savedEntries.length);
            System.arraycopy(tempEntries, 0, fields, savedEntries.length,
                             tempEntries.length);
         }
      }

      if(fields != null) {
         attributes = new DataRef[fields.length];

         for(int i = 0; i < fields.length; i++) {
            String entity = fields[i].getProperty("entity");
            String attribute = fields[i].getProperty("attribute");
            attribute = AssetUtil.trimEntity(attribute, entity);
            AttributeRef attributeRef = new AttributeRef(entity, attribute);
            attributes[i] = new ColumnRef(attributeRef);
            ((ColumnRef) attributes[i]).setDataType(
               fields[i].getProperty("dtype"));
            ((ColumnRef) attributes[i]).setDescription(
               fields[i].getProperty("description"));
            columns.addAttribute(attributes[i]);
            columns.setProperty(attributes[i].getName(),
                                Boolean.valueOf(fields[i].getProperty("aggformula")));
         }
      }

      return columns;
   }

   /**
    * Create outer mirror assembly.
    * @param rws the specified runtime worksheet.
    * @param assembly the specified assembly.
    * @param name the specified assembly name.
    * @param entry the specified mirror entry.
    * @return the created outer mirror assembly.
    */
   public static WSAssembly createMirrorAssembly(RuntimeWorksheet rws,
      WSAssembly assembly, String name, AssetEntry entry) throws Exception
   {
      WSAssembly tmp;
      Worksheet ws = rws.getWorksheet();

      if(assembly instanceof ConditionAssembly) {
         tmp = new MirrorConditionAssembly(ws, name, entry, true, assembly);
      }
      else if(assembly instanceof DateRangeAssembly) {
         tmp = new MirrorDateRangeAssembly(ws, name, entry, true, assembly);
      }
      else if(assembly instanceof NamedGroupAssembly) {
         tmp = new MirrorNamedGroupAssembly(ws, name, entry, true, assembly);
      }
      else if(assembly instanceof VariableAssembly) {
         tmp = new MirrorVariableAssembly(ws, name, entry, true, assembly);
      }
      else if(assembly instanceof TableAssembly) {
         tmp = new MirrorTableAssembly(ws, name, entry, true, assembly);
         initColumnSelection(rws, (TableAssembly) tmp);
         ((MirrorTableAssembly) tmp).setHierarchical(false);
      }
      else {
         throw new RuntimeException("Unsupported assembly found: " + assembly);
      }

      return tmp;
   }

   /**
    * Get the object type for action record from asset entry.
    * @param entry the specified asset entry.
    * @return the object type for action record.
    */
   public static String getObjectType(AssetEntry entry) {
      if(entry.isVSSnapshot()) {
         return ActionRecord.OBJECT_TYPE_SNAPSHOT;
      }
      else if(entry.isViewsheet()) {
         return ActionRecord.OBJECT_TYPE_DASHBOARD;
      }
      else if(entry.isWorksheet()) {
         return ActionRecord.OBJECT_TYPE_WORKSHEET;
      }
      else if(entry.isFolder()) {
         return ActionRecord.OBJECT_TYPE_FOLDER;
      }
      else if(entry.isTableStyle()) {
         return ActionRecord.OBJECT_TYPE_TABLE_STYLE;
      }
      else if(entry.isScript()) {
         return ActionRecord.OBJECT_TYPE_SCRIPT;
      }

      return ActionRecord.OBJECT_TYPE_ASSET;
   }

   /**
    * Determine if the rename Asset Tree has folders and tablestyles with the same name.
    */
   public static boolean isRenameDuplicate(AssetEntry nentry) {
      boolean duplicate = false;
      String folder = nentry.getProperty("folder");

      if(nentry.isTableStyle()) {
         duplicate = isDuplicateStyle(folder, nentry.getName()) ||
            isDuplicateFolder(folder, nentry.getName());
      }
      else if(nentry.isTableStyleFolder()) {
         String nFolder = folder.contains(LibManager.SEPARATOR) ? folder.substring(0,
            folder.lastIndexOf(LibManager.SEPARATOR)) : null;
         duplicate = isDuplicateStyle(nFolder, nentry.getName()) ||
            isDuplicateFolder(nFolder, nentry.getName());
      }

      return duplicate;
   }

   /**
    * Determine if the remove Asset Tree has folders and tablestyles with the same name.
    */
   public static boolean isChangeDuplicate(AssetEntry entry, AssetEntry nentry) {
      boolean duplicate = false;
      String folder = entry.getProperty("folder");
      String folder2 = nentry.getProperty("folder");

      if(nentry.isTableStyle()) {
         duplicate = !Tool.equals(folder, folder2) && (isDuplicateStyle(folder2, nentry.getName()) ||
            isDuplicateFolder(folder2, nentry.getName()));
      }
      else if(nentry.isTableStyleFolder()) {
         String nFolder = folder.contains(LibManager.SEPARATOR) ? folder.substring(0,
            folder.lastIndexOf(LibManager.SEPARATOR)) : null;
         duplicate = !Tool.equals(nFolder, folder2) && (isDuplicateStyle(folder2, nentry.getName()) ||
            isDuplicateFolder(folder2, nentry.getName()));
      }

      return duplicate;
   }

   public static boolean isDuplicateFolder(String folder1, String folder2) {
      LibManager manager = LibManager.getManager();

      if(folder1 != null) {
         return manager.containsFolder(folder1 + LibManager.SEPARATOR + folder2);
      }
      else {
         return manager.containsFolder(folder2);
      }
   }

   public static boolean isDuplicateStyle(String folder, String name) {
      LibManager manager = LibManager.getManager();
      XTableStyle[] tableStyles = manager.getTableStyles(folder);

      return Arrays.stream(tableStyles).anyMatch(xTableStyle -> Tool.equals(xTableStyle.getName(),
         folder == null ? name : folder + LibManager.SEPARATOR + name));
   }

   /**
    * Remove a table Style folder.
    */
   public static void removeStyleFolder(String folder, LibManager manager) {
      manager.removeTableStyleFolder(folder);

      // remove all table styles under this folder
      XTableStyle[] tstyles = manager.getTableStyles(folder);

      for(int i = 0; i < tstyles.length; i++) {
         manager.removeTableStyle(tstyles[i].getID());
      }

      // remove all folders under this folder
      String[] folders = manager.getTableStyleFolders(folder);

      for(int i = 0; i < folders.length; i++) {
         removeStyleFolder(folders[i], manager);
      }
   }

   public static void changeTableStyleFolder(String pfolder, String ofolder, LibManager manager) {
      int idx = ofolder.lastIndexOf(LibManager.SEPARATOR);
      String name = idx == -1 ? ofolder : ofolder.substring(idx + 1);
      String nfolder = pfolder == null ? name : pfolder + LibManager.SEPARATOR + name;
      manager.renameTableStyleFolder(ofolder, nfolder);

      XTableStyle[] tstyles = manager.getTableStyles(ofolder);

      for(XTableStyle tstyle : tstyles) {
         String tstyleName = tstyle.getName();
         String nname = nfolder + LibManager.SEPARATOR + tstyleName.substring(ofolder.length() + 1);
         manager.renameTableStyle(tstyleName, nname, tstyle.getID());
      }

      String[] folders = manager.getTableStyleFolders(ofolder);

      for(String folder : folders) {
         changeTableStyleFolder(nfolder, folder, manager);
      }
   }

   /**
    * Get the mode of a table asesmbly.
    * @param table the specified table assembly.
    * @return the mode of the table asesmbly.
    */
   public static int getMode(TableAssembly table) {
      if(table.isRuntime()) {
         return AssetQuerySandbox.RUNTIME_MODE;
      }
      else if(table.isLiveData()) {
         return AssetQuerySandbox.LIVE_MODE;
      }

      return AssetQuerySandbox.DESIGN_MODE;
   }

   /**
    * Check if the target table has maxrow setting.
    */
   public static boolean hasMaxRowSetting(TableAssembly table) {
      if(table == null) {
         return false;
      }

      boolean runtime = table.isRuntime();
      int dmax = 0;
      int tmax = runtime ? table.getMaxRows() : table.getMaxDisplayRows();

      if(tmax <= 0) {
         tmax = table.getMaxRows();
      }

      if(tmax > 0) {
         return true;
      }

      String mstr = null;

      try {
         mstr = getWorksheetQueryMaxRow(table.getWorksheet(), runtime);

         if(mstr != null) {
            dmax = Integer.parseInt(mstr);
            return dmax > 0;
         }
      }
      catch(Exception ignore) {
      }

      return false;
   }

   /**
    * Get exceeded max rows message if any.
    * @param table the specified table assembly.
    * @param count the specified table rows count.
    */
   public static String getExceededMsg(TableAssembly table, int count) {
      if(table == null) {
         return null;
      }

      boolean runtime = table.isRuntime();
      int dmax = 0;
      int tmax = runtime ? table.getMaxRows() : table.getMaxDisplayRows();

      if(tmax <= 0) {
         tmax = table.getMaxRows();
      }

      String mstr = null;

      try {
         mstr = getWorksheetQueryMaxRow(table.getWorksheet(), runtime);

         if(mstr != null) {
            dmax = Integer.parseInt(mstr);
         }
      }
      catch(Exception ex) {
         LOG.warn("Invalid value for the maximum row property: " + mstr, ex);
      }

      String exceeded = null;

      if(dmax > 0 && count >= dmax) {
         exceeded = runtime ?
            Catalog.getCatalog().getString("composer.ws.preview.query.limit", dmax) :
            Catalog.getCatalog().getString("composer.ws.preview.limit", dmax);
      }
      else if(tmax > 0 && count >= tmax) {
         exceeded = Catalog.getCatalog().getString("composer.ws.table.limit", tmax);
      }

      return exceeded;
   }

   /**
    * Return worksheet query max row.
    */
   private static String getWorksheetQueryMaxRow(Worksheet ws, boolean runtime) {
      int max = 0;

      if(runtime) {
         max = Util.getQueryRuntimeMaxrow();
      }
      else if(ws == null || ws.getWorksheetInfo() == null) {
         max = Util.getQueryPreviewMaxrow();
      }
      else {
         max = Util.getQueryLocalPreviewMaxrow(ws.getWorksheetInfo().getPreviewMaxRow());
      }

      // Feature #39140, always respect the global row limit
      return Integer.toString(Util.getQueryLocalRuntimeMaxrow(max));
   }

   /**
    * Initialize column selection.
    * @param rws the specified runtime worksheet.
    * @param table the specified table.
    */
   public static void initColumnSelection(RuntimeWorksheet rws,
                                          TableAssembly table) throws Exception {
      initColumnSelection(rws.getAssetQuerySandbox(), table);
   }

   /**
    * Initialize column selection.
    */
   public static void initColumnSelection(AssetQuerySandbox box,
                                          TableAssembly table) throws Exception {
      TableAssembly table2 = (TableAssembly) table.clone();
      box.resetDefaultColumnSelection(table.getName());
      AssetQuery query = AssetQuery.createAssetQuery(
         table2, AssetQuerySandbox.DESIGN_MODE, box, false, -1L, true, true);
      ColumnSelection columns = query.getDefaultColumnSelection();
      ColumnSelection ocolumns = table.getColumnSelection();

      boolean samecols = false;

      // Check to see if the columns selections are identical, disregarding the
      // column order. If they are, reuse the original selection.
      if(columns.getAttributeCount() == ocolumns.getAttributeCount()) {
         samecols = true;

         for(int i = 0; i < columns.getAttributeCount(); i++) {
            if(!ocolumns.containsAttribute(columns.getAttribute(i))) {
               samecols = false;
               break;
            }
         }
      }

      if(!samecols) {
         // The columns selection has changed. If any columns names match,
         // reuse the previous column definition (format, data type), but use
         // the order defined in the new selection
         ColumnSelection ocolumns2 = ocolumns.clone(true);
         ocolumns.clear();

         for(int i = 0; i < columns.getAttributeCount(); i++) {
            ColumnRef column = (ColumnRef) columns.getAttribute(i);
            ColumnRef ocolumn = (ColumnRef) ocolumns2.getAttribute(column.getName());

            ocolumns.addAttribute(ocolumn == null ? column : ocolumn);
         }

         // don't discard the formula columns added for the join table.(52304)
         for(int i = 0; i < ocolumns2.getAttributeCount(); i++) {
            ColumnRef column = (ColumnRef) ocolumns2.getAttribute(i);

            if(column.getDataRef() instanceof ExpressionRef &&
               !ocolumns.containsAttribute(column))
            {
               ocolumns.addAttribute(column);
            }
         }
      }

      table.setColumnSelection(ocolumns);
   }

   /**
    * Convert table to embedded table.
    */
   public static EmbeddedTableAssembly convertEmbeddedTable(AssetQuerySandbox box,
                                                            TableAssembly tab, boolean replace,
                                                            boolean snapshot, boolean direct)
      throws Exception
   {
      return convertEmbeddedTable(box, tab, replace, snapshot, direct, false);
   }

   /**
    * Convert table to embedded table.
    * @param forceLive if force to convert the live mode to embedded table.
    */
   public static EmbeddedTableAssembly convertEmbeddedTable(AssetQuerySandbox box,
                                                            TableAssembly tab, boolean replace,
                                                            boolean snapshot, boolean direct,
                                                            boolean forceLive)
      throws Exception
   {
      Worksheet ws = box.getWorksheet();
      String newname = replace ? tab.getName() :
                                 AssetUtil.getNextName(ws, AbstractSheet.TABLE_ASSET);

      if(snapshot && tab instanceof SnapshotEmbeddedTableAssembly) {
         return (SnapshotEmbeddedTableAssembly) tab;
      }

      EmbeddedTableAssembly assembly = snapshot ?
         new SnapshotEmbeddedTableAssembly(ws, newname) :
         new EmbeddedTableAssembly(ws, newname);
      Point pos = replace ? tab.getPixelOffset() :
         new Point(tab.getPixelOffset().x + 200, tab.getPixelOffset().y);
      assembly.setPixelOffset(pos);
      assembly.setLiveData(true);
      int mode = AssetEventUtil.getMode(tab);

      if(replace || forceLive) {
         mode = AssetQuerySandbox.RUNTIME_MODE;
      }

      TableLens table;

      if(snapshot && ((AbstractTableAssembly) tab).isCrosstab()) {
         assembly.setAggregateInfo(tab.getAggregateInfo());
         assembly.setColumnSelection(tab.getColumnSelection(true), true);
         assembly.setAggregate(tab.isAggregate());
         tab.setAggregateInfo(new AggregateInfo());
         AssetQuery query = AssetQuery.createAssetQuery(
            tab, mode, box, false, -1L, true, false);
         VariableTable vtable = (VariableTable) box.getVariableTable().clone();
         table = query.getTableLens(vtable);
      }
      else {
         table = box.getTableLens(tab.getName(), mode);
      }

      if(table == null) {
         return null;
      }

      table = AssetQuery.shuckOffFormat(table);

      if(snapshot) {
         SnapshotEmbeddedTableAssembly stab = (SnapshotEmbeddedTableAssembly) assembly;
         final ColumnSelection oldColumnSelection = tab.getColumnSelection();
         stab.setColumnSelection(convertColumnSelection(oldColumnSelection, tab));
         stab.setLiveData(true);
         boolean formula = false;
         XSwappableTable stable = null;
         TableLens table0 = table;
         XNodeTableLens ntable = null;

         while(table0 instanceof TableFilter) {
            if(table0 instanceof FormulaTableLens) {
               formula = true;
            }

            table0 = ((TableFilter) table0).getTable();
         }

         if(table0 instanceof XNodeTableLens) {
            ntable = ((XNodeTableLens) table0);
            stable = ntable.getSwappableTable();

            if(stable == null && ntable.getBaseDFWrapper() != null) {
               stable = createXSwappableTable(ntable);
            }
         }

         int ccnt = table.getColCount();

         if(table.moreRows(0) && stable != null) {
            for(int i = 0; i < ccnt; i++) {
               String id = table.getColumnIdentifier(i);
               int col = ntable.findColumnByIdentifier(id);

               if(col == -1) {
                  final Object header = table.getObject(0, i);

                  if(header instanceof String) {
                     final ColumnRef column =
                        (ColumnRef) oldColumnSelection.getAttribute(((String) header));

                     if(column != null) {
                        col = ntable.findColumnByIdentifier(column.getAttribute());
                     }
                  }
               }

               if(col >= 0) {
                  stable.setObject(0, col, table.getObject(0, i));
               }
            }
         }

         stab.setTable(stable);
         ColumnSelection cols = getDefaultColumnSelection(tab, table, formula, direct);
         stab.setDefaultColumnSelection(convertColumnSelection(cols, tab));
         stab.setColumnSelection(cols);
      }
      else {
         XEmbeddedTable etable = new XEmbeddedTable(getCreateTypes(table, tab), table);
         assembly.setEmbeddedData(etable);
      }

      AssetEventUtil.initColumnSelection(box, assembly);
      return assembly;
   }

   private static XSwappableTable createXSwappableTable(XTable lens) {
      XSwappableTable table = new XSwappableTable(lens.getColCount(), false);
      lens.moreRows(Integer.MAX_VALUE);

      for(int i = 0; i < lens.getRowCount(); i++) {
         Object[] rows = new Object[lens.getColCount()];

         for(int j = 0; j < lens.getColCount(); j++) {
            rows[j] = lens.getObject(i, j);
         }

         table.addRow(rows);
      }

      table.complete();

      return table;
   }

   private static String[] getCreateTypes(TableLens table, TableAssembly tab) {
      String[] types = XEmbeddedTable.createTypes(table);
      ColumnSelection columns = tab.getColumnSelection(true);

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         DataRef attribute = columns.getAttribute(i);

         if(!XSchema.BOOLEAN.equals(attribute.getDataType()) || i >= types.length ||
            !types[i].equals(XSchema.STRING))
         {
            continue;
         }

         types[i] = XSchema.BOOLEAN.equals(attribute.getDataType()) &&
            Tool.equals(types[i], XSchema.STRING) ? XSchema.BOOLEAN : types[i];
      }

      return types;
   }

   /**
    * Convert column selection.
    */
   private static ColumnSelection convertColumnSelection(ColumnSelection from,
                                                         TableAssembly table)
   {
      if(from == null || !(table instanceof ComposedTableAssembly)) {
         return from;
      }

      ColumnSelection cols = new ColumnSelection();

      for(int i = 0; i < from.getAttributeCount(); i++) {
         ColumnRef col = (ColumnRef) from.getAttribute(i).clone();
         DataRef dref = col.getDataRef();
         String alias = col.getAlias();

         if(dref instanceof AttributeRef &&
            (alias == null || alias.length() == 0))
         {
            col.setAlias(dref.getAttribute());
         }

         cols.addAttribute(col);
      }

      return cols;
   }

   /**
    * Get default column selection.
    */
   private static ColumnSelection getDefaultColumnSelection(TableAssembly table,
      TableLens lens, boolean formula, boolean direct)
   {
      ColumnSelection tcolumns = table.getColumnSelection();
      ColumnSelection columns = new ColumnSelection();

      for(int i = 0; i < lens.getColCount(); i++) {
         String header = AssetUtil.format(XUtil.getHeader(lens, i));
         ColumnRef col = (ColumnRef) tcolumns.getAttribute(header);
         ExpressionRef eref = formula ? getExpressionRef(col) : null;
         DataRef attr;

         if(eref != null) {
            ExpressionRef deref = new ExpressionRef(null, header);
            deref.setExpression(eref.getScriptExpression());
            attr = deref;
         }
         else {
            attr = new AttributeRef(col.getEntity(), col.getAttribute());
         }

         String type = direct ? Tool.getDataType(lens.getColType(i)) : col.getDataType();
         ColumnRef column = new ColumnRef(attr);
         column.setDataType(type);
         column.setAlias(col.getAlias());
         columns.addAttribute(column);
      }

      return columns;
   }

   /**
    * Get expression ref from wrapper.
    */
   private static ExpressionRef getExpressionRef(DataRef ref) {
      if(ref instanceof DataRefWrapper) {
         DataRef eref = ((DataRefWrapper) ref).getDataRef();

         if(eref instanceof ExpressionRef) {
            return (ExpressionRef) eref;
         }
      }

      return null;
   }

   /**
    * Execute the user variable.
    * @param box the query sandbox.
    * @param var the specified user variable.
    */
   public static void executeVariable(AssetQuerySandbox box, UserVariable var)
      throws Exception
   {
      if(var instanceof AssetVariable) {
         executeAssetVariable(box, (AssetVariable) var);
      }
      else {
         executeUserVariable(box, var);
      }
   }

   /**
    * Execute a user variable.
    * @param var the specified user variable.
    */
   private static void executeUserVariable(AssetQuerySandbox box, UserVariable var) {
      String choiceQuery = var.getChoiceQuery();

      if(choiceQuery != null && choiceQuery.contains("]:[")) {
         BrowseDataController browseDataCtrl = new BrowseDataController();
         String[] pair = Tool.split(var.getChoiceQuery(), "]:[", false);
         ColumnRef dataRef = new ColumnRef(new AttributeRef(pair[pair.length - 1]));

         browseDataCtrl.setColumn(dataRef);
         browseDataCtrl.setName(pair[0]);

         try {
            final BrowseDataModel data = browseDataCtrl.process(box);

            if(data != null) {
               var.setValues(data.values());
               var.setChoices(data.values());
               var.setDataTruncated(data.dataTruncated());
            }
         }
         catch(Exception ex) {
            throw new RuntimeException(ex);
         }
      }
      else {
         XSessionManager manager = XSessionManager.getSessionManager();
         manager.executeChoiceQuery(var);
      }
   }

   /**
    * Execute an asset variable.
    * @param box the query sandbox.
    * @param var the specified asset variable.
    */
   public static void executeAssetVariable(AssetQuerySandbox box, AssetVariable var)
      throws Exception
   {
      // no table? return
      if(var.getTableName() == null) {
         executeUserVariable(box, var);
         return;
      }

      Worksheet ws = box.getWorksheet();

      // update failed? return
      if(!var.update(ws)) {
         return;
      }

      TableAssembly tassembly = (TableAssembly) var.getTable().clone();
      ColumnSelection columns = tassembly.getColumnSelection(true);
      DataRef vattr = var.getValueAttribute();
      DataRef lattr = var.getLabelAttribute();
      DataRef vcol = (vattr == null) ? null :
         columns.getAttribute(vattr.getName());
      DataRef lcol = (lattr == null) ? null :
         columns.getAttribute(lattr.getName());

      if(vattr != null && vcol == null) {
         LOG.warn("Value attribute not found: " + vattr);
         return;
      }

      // get distinct query data
      tassembly.setDistinct(true);

      if(vcol != null) {
         ColumnSelection cols = tassembly.getColumnSelection();

         for(int i = 0; i < cols.getAttributeCount(); i++) {
            ColumnRef ref = (ColumnRef) cols.getAttribute(i);

            if(ref.equals(vcol) || ref.equals(lcol)) {
               continue;
            }

            if((ref.getRefType() & DataRef.CUBE) == DataRef.CUBE) {
               String id = ref.getAlias() != null ? ref.getAlias() :
                  ref.getAttribute();

               if(Tool.equals(id, vcol.getAttribute()) ||
                  (lcol != null && Tool.equals(id, lcol.getAttribute())))
               {
                  continue;
               }
            }

            ref.setVisible(false);
         }
      }

      AssetQuery query = AssetQuery.createAssetQuery(
         tassembly, AssetQuerySandbox.RUNTIME_MODE, box, false, -1L, true, false);
      VariableTable vtable = (VariableTable) box.getVariableTable().clone();
      vtable.put(AssetQuery.BROWSE_MAXROWS, BrowseDataController.MAX_ROW_COUNT);
      TableLens table = query.getTableLens(vtable);
      int index = vattr == null ? 0 : AssetUtil.findColumn(table, vcol);

      // table changed? return
      if(index < 0 || index >= table.getColCount()) {
         LOG.warn("Value attribute not found in table: " + vattr);
         return;
      }

      int lindex = lcol == null ? -1 : AssetUtil.findColumn(table, lcol);

      // table changed? return
      if(lindex < 0 && lattr != null) {
         LOG.warn("Label attribute not found in table: " + lattr);
      }

      Object[] values = AssetUtil.getXTableValues(table, index);
      Object[] labels = lindex < 0 ?
         values :
         AssetUtil.getXTableValues(table, lindex);
      var.setValues(values);
      var.setChoices(labels);
      var.setDataTruncated(table.moreRows(BrowseDataController.MAX_ROW_COUNT));
   }

   /**
    * Interface for selecting asset entry to be included in the asset tree.
    */
   private abstract static class AssetSelector {
      /**
       * Check if the node should be added to the tree.
       */
      public abstract boolean select(AssetEntry entry);

      /**
       * Check if the node should be traversed.
       */
      public abstract boolean traverse(AssetEntry entry);
   }

   /**
    * Select asset entries based on search string.
    */
   private static class SearchAssetSelector extends AssetSelector {
      public SearchAssetSelector(String searchString, AssetEntry[] entries) {
         this.searchString = searchString.toLowerCase();

         if(entries != null) {
            Collections.addAll(opens, entries);
         }
      }

      @Override
      public boolean select(AssetEntry entry) {
         boolean rc = true;
         boolean check = (entry.getAlias() != null &&
            entry.getAlias().toLowerCase().contains(searchString)) ||
            entry.getName().toLowerCase().contains(searchString);

         // physical table, viewsheet, worksheet, entity, data table,
         // logical model or query
         if(entry.isViewsheet() || entry.isPhysicalTable() ||
            entry.isWorksheet() || entry.isTable() || entry.isQuery())
         {
            String type = entry.getProperty("type");
            rc = check;

            // logical model or query
            if(entry.isQuery() && rc &&
               Integer.toString(XSourceInfo.MODEL).equals(type))
            {
               forced.add(entry.getPath());
            }

            //entity or data table
            if(entry.isTable() && !rc) {
               rc = forced.contains(entry.getParentPath());
            }
         }

         if(entry.isFolder() && check) {
            parents.add(entry);
         }

         if(!rc) {
            AssetEntry parent = entry.getParent();
            rc = parent != null && parents.contains(parent);
         }

         return rc;
      }

      @Override
      public boolean traverse(AssetEntry entry) {
         return opens != null && !opens.isEmpty() ?
            opens.contains(entry) : entry.isFolder();
      }

      public String getSearchString() {
         return searchString;
      }

      private String searchString;
      private Set<String> forced = new HashSet<>();
      private Set<AssetEntry> parents = new HashSet<>();
      private List<AssetEntry> opens = new ArrayList<>();
   }

   /**
    * Check if the assembly is depended by other non-deleted assembly.
    */
   public static boolean hasDependent(Assembly assembly, Worksheet ws,
                                      Set deleted) {
      AssemblyRef[] arr = ws.getDependings(assembly.getAssemblyEntry());

      for(AssemblyRef aref : arr) {
         if(!deleted.contains(aref.getEntry().getName())) {
            return true;
         }

         Assembly dep = ws.getAssembly(aref.getEntry().getName());

         if(dep == null) {
            continue;
         }

         if(hasDependent(dep, ws, deleted)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Layout the table assemblies for MergejoinTableEvent, MergeCrossEvent,
    * ConcatenateTableEvent, EditInnerJoinOverEvent, the function will layout
    * the new created resultant table to a position that will cause none other
    * assemblies original exist to be relayouted when call Worksheet.layout(),
    * its x is Math.min(tbl1.x, tbl2.x), and y will find a proper position.
    * @param tbl1 the first table for the event.
    * @param tbl2 the second table for the event, tbl1 and tbl2 order is not
    *  important.
    * @param rtbl the new table created in the event.
    */
   public static void layoutResultantTable(WSAssembly tbl1, WSAssembly tbl2,
                                           final WSAssembly rtbl)
   {
      if(tbl1 == null || tbl2 == null || rtbl == null ||
         tbl1.getName().equals(rtbl.getName()) ||
         tbl2.getName().equals(rtbl.getName()))
      {
         return;
      }

      // Worksheet.getGap()
      final int GAP = 40;
      final int WIDTH = 150;
      final int HEIGHT = 50;
      java.awt.Rectangle rect1 = tbl1.getBounds();
      java.awt.Rectangle rect2 = tbl2.getBounds();
      int y = Math.max(rect1.y + HEIGHT, rect2.y + HEIGHT) + GAP;
      int x = Math.min(rect1.x, rect2.x) + (int) (Math.abs(rect1.x - rect2.x) / 2);
      int w = WIDTH;
      int h = HEIGHT;
      // expect perfect position for layout
      rtbl.setPixelOffset(new Point(x, y));
      Worksheet ws = tbl1.getWorksheet();
      Assembly[] assemblies = ws.getAssemblies();
      // resultant table bounds
      java.awt.Rectangle rect;

      OUTER:
      while(true) {
         rect = new java.awt.Rectangle(Math.max(0, x - GAP),
                                       Math.max(0, y - GAP),
                                       // w/h + right/bottom gap + left/top gap
                                       w + GAP + (x - GAP < 0 ? 0 : GAP),
                                       h + GAP + (y - GAP < 0 ? 0 : GAP));

         for(Assembly assembly : assemblies) {
            if(assembly.getName().equals(rtbl.getName())) {
               continue;
            }

            Rectangle rectn = assembly.getBounds();
            rectn = new Rectangle(rectn.x, rectn.y, WIDTH, HEIGHT);

            if(rect.intersects(rectn)) {
               y = rectn.y + rectn.height + GAP;
               continue OUTER;
            }
         }

         break;
      }

      rtbl.setPixelOffset(new Point(x, y));
   }

   /**
    * Refresh table last modified.
    * This property is used worksheet preview table to auto update.
    */
   public static void refreshTableLastModified(Worksheet ws, String tname,
      boolean recursive) throws Exception
   {
      Assembly assembly = ws.getAssembly(tname);

      // assembly is condtion, group and variable, find depending tables to
      // refresh tables last modified.
      if((assembly instanceof AttachedAssembly) ||
         (assembly instanceof DateRangeAssembly))
      {
         AssemblyRef[] arr = ws.getDependings(assembly.getAssemblyEntry());

         for(AssemblyRef aref : arr) {
            refreshTableLastModified(ws, aref.getEntry().getName(), recursive);
         }

         return;
      }


      if(!(assembly instanceof TableAssembly)) {
         return;
      }

      TableAssembly tassembly = (TableAssembly) assembly;
      long now = new Date().getTime();
      tassembly.setLastModified(now);

      if(recursive) {
         ws.checkDependencies();
         AssemblyRef[] arr = ws.getDependings(tassembly.getAssemblyEntry());

         for(AssemblyRef aref : arr) {
            refreshTableLastModified(ws, aref.getEntry().getName(), recursive);
         }
      }
   }

   /**
    * Adjust assembly position, make it is not overlap with other assemblies.
    */
   public static void adjustAssemblyPosition(WSAssembly adjustAssembly,
      Worksheet ws)
   {
      // add ws gap
      Rectangle adjustBounds = adjustAssembly.getBounds();
      OptionalInt y = Arrays.stream(ws.getAssemblies())
         .mapToInt(a -> a.getPixelOffset().y + 80)
         .max();

      Point movePsition = new Point(Math.max(adjustBounds.x, 10), y.orElse(30));
      adjustAssembly.setPixelOffset(movePsition);
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(AssetEventUtil.class);
}
