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
package inetsoft.uql.asset;

import inetsoft.uql.*;
import inetsoft.uql.asset.internal.TabularTableAssemblyInfo;
import inetsoft.uql.asset.internal.WSAssemblyInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.tabular.*;
import inetsoft.uql.util.QueryManager;
import inetsoft.uql.util.XUtil;
import inetsoft.util.ThreadContext;
import inetsoft.web.composer.model.ws.DependencyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.security.Principal;
import java.util.*;

/**
 * Tabular table assembly, bound to a data source.
 *
 * @version 12.2
 * @author InetSoft Technology Corp
 */
public class TabularTableAssembly extends BoundTableAssembly implements ScriptedTableAssembly {
   /**
    * Constructor.
    */
   public TabularTableAssembly() {
      super();
   }

   /**
    * Constructor.
    */
   public TabularTableAssembly(Worksheet ws, String name) {
      super(ws, name);
   }

   /**
    * Create assembly info.
    * @return the associated assembly info.
    */
   @Override
   protected WSAssemblyInfo createInfo() {
      return new TabularTableAssemblyInfo();
   }

   /**
    * Get the sql bound table assembly info.
    * @return the sql bound table assembly info of the sql bound table assembly.
    */
   protected TabularTableAssemblyInfo getTabularTableAssemblyInfo() {
      return (TabularTableAssemblyInfo) getTableInfo();
   }

   @Override
   public UserVariable[] getAllVariables() {
      UserVariable[] vars = super.getAllVariables();
      List<UserVariable> list = new ArrayList<>();
      mergeVariables(list, vars);

      TabularQuery query = getTabularTableAssemblyInfo().getQuery();

      if(query != null) {
         if(query.getDataSource() != null) {
            Principal principal = ThreadContext.getPrincipal();
            XDataSource ds = null;

            if(principal != null && query.getDataSource() != null) {
               ds = XUtil.getDatasource(principal, query.getDataSource());
            }

            if(ds == null) {
               ds = query.getDataSource();
            }

            list.addAll(TabularUtil.findVariables(ds));
         }

         list.addAll(TabularUtil.findVariables(query));
      }

      return list.toArray(new UserVariable[0]);
   }

   @Override
   public void replaceVariables(VariableTable vars) {
      super.replaceVariables(vars);

      TabularQuery query = getTabularTableAssemblyInfo().getQuery();
      TabularUtil.replaceVariables(query.getDataSource(), vars);
      TabularUtil.replaceVariables(query, vars);
   }

   @Override
   public void dependencyChanged(String depname) {
      super.dependencyChanged(depname);

      TabularQuery query = getTabularTableAssemblyInfo().getQuery();
      String[] deps = query.getDependedAssets(new String[] { depname });

      if(deps.length > 0) {
         try {
            loadColumnSelection(new VariableTable(), false, null);
         }
         catch(Exception ex) {
            // no-op
         }
      }
   }

   /**
    * Update the columns from source.
    * @param addnew true to start from scratch (add new columns).
    */
   public void loadColumnSelection(VariableTable vtable, boolean addnew, QueryManager manager)
         throws Exception
   {
      TabularQuery query = getTabularTableAssemblyInfo().getQuery();
      // column in the query output before the refresh
      Set<String> o_qcolumns = new HashSet<>();

      if(!addnew) {
         XTypeNode[] o_output_columns = query.getOutputColumns();

         if(o_output_columns != null) {
            for(XTypeNode node : o_output_columns) {
               o_qcolumns.add(node.getName());
            }
         }
      }

      try {
         if(manager != null) {
            query.setProperty("queryManager", manager);
         }

         query.loadOutputColumns(vtable);
      }
      catch(TabularQuery.ParametersRequiredException ex) {
         LOG.warn(
            "Failed to load the columns for the table", ex);
      }

      ColumnSelection ocolumns = getColumnSelection(false);

      if(query.getOutputColumns() != null) {
         ColumnSelection ncolumns = new ColumnSelection();

         for(XTypeNode node : query.getOutputColumns()) {
            if(node == null) {
               continue;
            }

            String cname = node.getName();

            // avoid np exception. (58598)
            if(cname == null) {
               cname = "";
            }

            // if a column was in old output columns, but not on ocolumns,
            // it has been explicitly deleted from the column list so
            // we don't add it unless explicitly instructed to do so
            if(!addnew && ocolumns.getAttribute(cname) == null &&
               o_qcolumns.contains(cname))
            {
               continue;
            }

            AttributeRef attributeRef = new AttributeRef(cname);
            attributeRef.setDataType(node.getType());
            ColumnRef ref = new ColumnRef(attributeRef);
            ref.setDataType(node.getType());
            DataRef oldRef = ocolumns.findAttribute(ref);

            if(oldRef instanceof ColumnRef) {
               ref.setAlias(((ColumnRef) oldRef).getAlias());
            }

            ncolumns.addAttribute(ref);
         }

         for(int i = 0; i < ocolumns.getAttributeCount(); i++) {
            DataRef ref = ocolumns.getAttribute(i);

            if(ref.isExpression()) {
               AggregateInfo aggInfo = getAggregateInfo();

               // check if the group is still applicable
               if(aggInfo.containsGroup(ref)) {
                  Enumeration<?> e = ref.getAttributes();
                  boolean exists = true;

                  while(exists && e.hasMoreElements()) {
                     Object ref1 = e.nextElement();

                     if(ref1 instanceof DataRef) {
                        exists = ncolumns.containsAttribute((DataRef) ref1);
                     }
                     else if(ref1 instanceof String) {
                        exists = false;

                        for(int j = 0; j < ncolumns.getAttributeCount(); j++) {
                           if(ncolumns.getAttribute(j).getAttribute().equals(ref1)) {
                              exists = true;
                              break;
                           }
                        }
                     }
                     else {
                        exists = false;
                     }
                  }

                  if(exists) {
                     ncolumns.addAttribute(ref);
                  }
               }
               // if not a group then just add the expression
               else {
                  ncolumns.addAttribute(ref);
               }
            }
         }

         setColumnSelection(ncolumns, false);
      }
   }

   @Override
   public void getDependeds(Set<AssemblyRef> set) {
      super.getDependeds(set);

      Assembly[] arr = ws.getAssemblies();
      List<String> names = new ArrayList<>();

      for(final Assembly assembly : arr) {
         if(assembly instanceof TableAssembly) {
            names.add(assembly.getName());
         }
      }

      TabularQuery query = getTabularTableAssemblyInfo().getQuery();
      String[] used = query.getDependedAssets(names.toArray(new String[0]));

      for(String name : used) {
         set.add(new AssemblyRef(new AssemblyEntry(name, AbstractSheet.TABLE_ASSET)));
      }
   }

   @Override
   public void getAugmentedDependeds(Map<String, Set<DependencyType>> dependeds) {
      super.getAugmentedDependeds(dependeds);

      Assembly[] arr = ws.getAssemblies();
      List<String> names = new ArrayList<>();

      for(Assembly anArr : arr) {
         if(anArr instanceof TableAssembly) {
            names.add(anArr.getName());
         }
      }

      TabularQuery query = getTabularTableAssemblyInfo().getQuery();
      String[] used = query.getDependedAssets(names.toArray(new String[0]));
      Arrays.stream(used).forEach(
         (name) -> addToDependencyTypes(dependeds, name, DependencyType.TABULAR_SUBQUERY));
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
         return null;
      }
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);
      fixTheColumnSelections();
   }

   /**
    * fix the column selection. change the attribute of the column to "" when it is null.
    * see TabularTableAssembly.loadColumnSelection
    */
   private void fixTheColumnSelections() {
      fixTheColumnSelections0(getColumnSelection(true));
      fixTheColumnSelections0(getColumnSelection(false));
   }

   private void fixTheColumnSelections0(ColumnSelection columnSelection) {
      if(columnSelection == null) {
         return;
      }

      for(int i = 0; i < columnSelection.getAttributeCount(); i++) {
         DataRef column = columnSelection.getAttribute(i);

         // see TabularTableAssembly.loadColumnSelection
         if(column instanceof ColumnRef && column.getAttribute() == null &&
            ((ColumnRef) column).getDataRef() instanceof AttributeRef)
         {
            AttributeRef attributeRef = (AttributeRef) ((ColumnRef) column).getDataRef();
            AttributeRef newAttributeRef = new AttributeRef(attributeRef.getEntity(),
               "");
            newAttributeRef.setRefType(attributeRef.getRefType());
            newAttributeRef.setCaption(attributeRef.getCaption());
            newAttributeRef.setDefaultFormula(attributeRef.getDefaultFormula());
            newAttributeRef.setSqlType(attributeRef.getSqlType());
            newAttributeRef.setDataType(attributeRef.getDataType());
            newAttributeRef.setDrillVisible(attributeRef.isDrillVisible());
            ((ColumnRef) column).setDataRef(newAttributeRef);
         }
      }
   }

   /**
    * Print the key to identify this content object. If the keys of two content
    * objects are equal, the content objects are equal too.
    */
   @Override
   public boolean printKey(PrintWriter writer) throws Exception {
      boolean success = super.printKey(writer);

      if(!success) {
         return false;
      }

      TabularTableAssemblyInfo info = getTabularTableAssemblyInfo();
      TabularQuery query = info.getQuery();

      writer.print("ColType[");
      XTypeNode[] nodes = query.getOutputColumns();

      for(XTypeNode node : nodes) {
         String type = query.getColumnType(node.getName());
         type = type == null ? node.getType() : type;

         writer.print("," + type);
      }

      writer.print("]TabularT[");
      List<PropertyMeta> props = TabularUtil.findProperties(query.getClass());

      for(PropertyMeta prop : props) {
         writer.print("," + prop.getName() + "=" + prop.getValue(query));
      }

      if(isOuter() && query.dependsOnMVSession()) {
         for(String key : query.getPropertyKeys()) {
            if(key != null && key.startsWith(TabularQuery.OUTER_TABLE_NAME_PROPERTY_PREFIX)) {
               writer.print(key + "=" + query.getProperty(key));
            }
         }
      }

      return true;
   }

   @Override
   public String getInputScript() {
      TabularQuery query = getTabularTableAssemblyInfo().getQuery();
      return (query instanceof ScriptedQuery) ? ((ScriptedQuery) query).getInputScript() : null;
   }

   @Override
   public String getOutputScript() {
      TabularQuery query = getTabularTableAssemblyInfo().getQuery();
      return (query instanceof ScriptedQuery) ? ((ScriptedQuery) query).getOutputScript() : null;
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(TabularTableAssembly.class);
}
