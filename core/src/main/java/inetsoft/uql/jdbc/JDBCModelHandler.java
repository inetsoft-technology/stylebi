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
package inetsoft.uql.jdbc;

import inetsoft.uql.erm.vpm.VpmProcessor;
import inetsoft.uql.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.erm.XPartition.PartitionTable;
import inetsoft.uql.jdbc.util.ConditionListHandler;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XVariable;
import inetsoft.uql.service.XHandler;
import inetsoft.uql.util.XUtil;
import inetsoft.util.IteratorEnumeration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.util.*;

/**
 * JDBCModelHandler translates a selection from a data model into a format
 * that can be executed by <code>JDBCHandler</code>.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 * @see JDBCHandler
 */
public class JDBCModelHandler extends XModelHandler {
   /**
    * Create a JDBCModelHandler that uses the specified handler.
    * @param handler the <code>JDBCHandler</code> used to actually execute the
    * generated query.
    */
   public JDBCModelHandler(XHandler handler) {
      super(handler);
   }

   /**
    * Executes a selection of attributes from the specified data model.
    * @param session session object.
    * @param selection a group of attributes and conditions defining a query.
    * @param model the data model from which the attributes were taken.
    * @param vars variable values for the query.
    * @return the result set.
    * @exception Exception if an error is encountered while executing the query.
    */
   @Override
   public XNode execute(Object session, XDataSelection selection,
                        XDataModel model, VariableTable vars, Principal user)
         throws Exception
   {
      if(!selection.getType().equals(XDataSelection.MODEL_TYPE)) {
         throw new Exception("The data selection is not based on a data model");
      }

      if(!selection.getSource().startsWith(model.getDataSource() + ".")) {
         throw new Exception(
            "The data selection is not based on this data model");
      }

      if(vars == null) {
         vars = new VariableTable();
      }

      XQuery query = prepareQuery(selection, model, vars, user);
      query.setProperty("queryManager", selection.getProperty("queryManager"));

      // @by mikec, if roles have no permission on that model, just return a
      // null node
      if(query == null) {
         return null;
      }

      XDataSource dx =
         XFactory.getRepository().getDataSource(model.getDataSource());
      getHandler().connect(dx, vars);

      return ((JDBCHandler) getHandler()).execute(query, vars, user, null);
   }

   /**
    * Generates an <code>XQuery</code> object that can be executed by
    * <code>JDBCHandler</code>.
    * @param selection a group of attributes and condtions defining a query.
    * @param model the data model from which the attributes were taken.
    * @param vars variable values for the query.
    * @param user the user executing the query.
    * @return an SQL statement string.
    */
   public XQuery prepareQuery(XDataSelection selection, XDataModel model,
                              VariableTable vars, Principal user) {
      JDBCQuery query = new JDBCQuery();
      UniformSQL sql = new UniformSQL();
      Enumeration e = selection.getAttributes();
      JDBCSelection colist = new JDBCSelection();
      sql.setDistinct(selection.isDistinct());

      String dmName = selection.getSource().substring(
         selection.getSource().indexOf('.') + 1);
      XLogicalModel pmodel = model.getLogicalModel(dmName, user);
      XLogicalModel lmodel = pmodel;
      XPartition partition = model.getPartition(pmodel.getPartition(), user);
      partition = partition.applyAutoAliases();
      addVariables(pmodel, vars);

      try {
         XRepository rep = XFactory.getRepository();
         XDataSource ds = rep.getDataSource(model.getDataSource());
         query.setDataSource(ds);
      }
      catch(Exception ex) {
         LOG.error("Failed to get data source for model: " + model,
            ex);
      }

      query.setSQLDefinition(sql);
      query.setPartition(pmodel.getPartition());
      SQLHelper helper = SQLHelper.getSQLHelper(query.getDataSource());
      helper.setUniformSql(sql);

      Vector nonAggregateFields = new Vector();
      Vector aggregateFields = new Vector();

      for(int i = 0; lmodel != null && e.hasMoreElements(); i++) {
         DataRef dref = (DataRef) e.nextElement();
         Enumeration entities = dref.getEntities();
         XEntity entity = null;

         while(entities.hasMoreElements()) {
            Object ename = entities.nextElement();

            if(ename == null) {
               continue;
            }

            entity = lmodel.getEntity(ename.toString());

            // a formula?
            if(entity == null) {
               continue;
            }
         }

         if(!(dref instanceof ExpressionRef)) {
            XAttribute attr =
               entity.getAttribute(((AttributeRef) dref).getAttribute());

            if(attr instanceof ExpressionAttribute) {
               dref = new ExprAttributeRef((ExpressionAttribute) attr);

               if(((ExpressionAttribute) attr).isAggregateExpression()) {
                  aggregateFields.add(dref);
               }
            }
         }

         if(!aggregateFields.contains(dref)) {
            nonAggregateFields.add(dref);
         }

         XField field = null;

         if(dref instanceof ExpressionRef) {
            ExpressionRef eref = (ExpressionRef) dref;
            Enumeration tables = getRefTables(eref, lmodel);

            while(tables.hasMoreElements()) {
               AttributeRef attref = (AttributeRef) tables.nextElement();
               String ent = attref.getEntity();
               Object alias = partition.getRunTimeTable(ent, true);

               if(alias != null) {
                  sql.addTable(ent, alias);
               }
               else {
                  sql.addTable(ent);
               }
            }

            String colistname =
               convertExpression(eref.getExpression(), lmodel, helper);
            colist.addColumn(colistname);
            colist.setAlias(colist.getColumnCount() - 1, eref.getName());
            field = new XField(eref.getName(), colistname, null,
                               XSchema.DOUBLE);
         }
         else {
            // a formula?
            if(entity == null) {
               continue;
            }

            XAttribute attr =
               entity.getAttribute(((AttributeRef) dref).getAttribute());

            if(attr != null) {
               String colName = attr.getColumn();

               if(colName.indexOf(' ') >= 0) {
                  colName = XUtil.quoteName(colName, helper);
               }

               String tblName = attr.getTable();
               Object alias = partition.getRunTimeTable(tblName, true);
               field = new XField(attr.getName(), colName, tblName,
                                  attr.getDataType());
               if(alias != null) {
                  sql.addTable(tblName, alias);
               }
               else {
                  sql.addTable(tblName);
               }

               String colistname = tblName + "." + colName;
               colist.addColumn(colistname);

               if(attr.getName() != null && !attr.getName().equals(colName)) {
                  colist.setAlias(colist.getColumnCount() - 1, attr.getName());
               }

               colist.setTable(colistname,
                  alias instanceof String ? (String) alias : tblName);
            }

            sql.addField(field);
         }
      }

      sql.setSelection(colist);

      ConditionList conditions =
         (ConditionList) selection.getConditionList().clone();

      if(conditions.getSize() > 0) {
         conditions = filterConditionList(selection, conditions, partition,
                                          lmodel, helper);
         ConditionListHandler handler = new ConditionListHandler();
         XFilterNode selconds = handler.createXFilterNode(conditions, sql,
                                                          vars, partition,
                                                          lmodel);

         sql.combineWhereByAnd(selconds);
      }

      if(selection.getGroup() != null && selection.isSQLGroup() ||
         aggregateFields.size() > 0) {
         ArrayList groupList = new ArrayList();
         Enumeration groups = selection.getGroup().getGroupRefs();

         if(aggregateFields.size() > 0) {
            groups = nonAggregateFields.elements();
         }

         while(groups.hasMoreElements()) {
            DataRef dref = (DataRef) groups.nextElement();
            String fname = null;

            if(lmodel != null && !(dref instanceof ExpressionRef)) {
               XEntity entity = lmodel.getEntity(dref.getEntity());
               XAttribute attr =
                  entity.getAttribute(((AttributeRef) dref).getAttribute());

               if(attr instanceof ExpressionAttribute) {
                  dref = new ExprAttributeRef((ExpressionAttribute) attr);
               }
            }

            if(dref instanceof ExpressionRef) {
               fname = convertExpression(((ExpressionRef) dref).getExpression(),
                                         lmodel, helper);
               groupList.add(fname);
            }
            else if(lmodel != null){
               String[] field = mapToPhysicalField(dref.getEntity(),
                                                   dref.getAttribute(), lmodel);
               fname = field[0] + '.' + field[1];
               groupList.add(fname);
            }

            if(fname != null) {
               int order = selection.getGroup().getOrderType(dref);

               if(order == XConstants.SORT_ASC) {
                  sql.setOrderBy(fname, "asc");
               }
               else if(order == XConstants.SORT_DESC) {
                  sql.setOrderBy(fname, "desc");
               }
            }
         }

         sql.setGroupBy(groupList.toArray());
      }

      addJoins(sql, partition, true);
      // query.setSQLDefinition(sql);

      if(sql == null || "".equals(sql.toString())) {
         return null;
      }

      try {
         query.setVPMEnabled(true);
         query = (JDBCQuery) VpmProcessor.getInstance().applyConditions(query, vars, false, user);
         query = (JDBCQuery) VpmProcessor.getInstance().applyHiddenColumns(query, vars, user);
         query.setVPMEnabled(false);
      }
      catch(Exception ex) {
         LOG.error("Failed to apply VPM to query " + query +
            " for user " + user, ex);
      }

      LOG.debug("Prepare to execute: " + sql);
      return query;
   }

   /**
    * Add necessary joins to query according to datamodel relationships.
    * @param useAlias true to use the table alias for searching for joins.
    */
   private static void addJoins(UniformSQL sql, XPartition partition,
				boolean useAlias) {
      SelectTable[] seltables = sql.getSelectTable();
      HashSet originating = new HashSet();
      HashSet others = new HashSet();
      HashSet tableset = new HashSet();

      // if an alias is defined in partition, the alias is added to the graph
      // instead of the original name. Need to use alias
      for(int i = 0; i < seltables.length; i++) {
         String name = seltables[i].getName().toString();
         String alias = seltables[i].getAlias();

         if(originating.size() == 0) {
            originating.add(alias != null ? alias : name);
         }
         else {
            others.add(alias != null ? alias : name);
         }
      }

      tableset.addAll(originating);
      tableset.addAll(others);
      XUtil.applyPartition(sql, partition, tableset, originating, others);

      if(others.size() > 0) {
         LOG.error("No join path found for tables: " + others);
      }
   }

   private static String getTableFromExpression(XExpression expr) {
      String value = expr.getValue().toString();

      if(value.indexOf('.') < 0) {
         return null;
      }

      return value.substring(0, value.lastIndexOf('.'));
   }

   /**
    * Update Variable table with parameters from XLogicalModel.
    */
   private static void addVariables(XLogicalModel lmodel, VariableTable vars) {
      // safety check for backwards compatibility
      if(vars == null || lmodel == null) {
         return;
      }

      // add variables with default values if they were not passed in
      Enumeration vnames = lmodel.getDefinedVariables();

      while(vnames.hasMoreElements()) {
         String varname = (String) vnames.nextElement();

         // if var not in variable table, add with xvariable value
         if(varname != null && !vars.contains(varname)) {
            XVariable xvar = lmodel.getVariable(varname);
            Object val = xvar.evaluate(vars);

            if(val != null) {
               vars.put(xvar.getName(), val);
            }
         }
      }
   }

   /**
    * Return hashcode of an object.
    */
   private int hashCode(Object val) {
      return val == null ? (hashCode() + cnt++) : val.hashCode();
   }

   /**
    * Find all the condition items dealing with formula columns.
    * If the formulas are ExpressionRefs, modify the entity information
    * else I DON'T KNOW WHAT TO DO!  -- FIX THIS
    */
   private ConditionList filterConditionList(XDataSelection selection,
                                             ConditionList conditions,
                                             XPartition partition,
                                             XLogicalModel model,
                                             SQLHelper helper) {
      UniformSQL sql = helper.getUniformSQL();

      for(int i = 0; i < conditions.getSize(); i++) {
         HierarchyItem item = conditions.getItem(i);

         if(item instanceof ConditionItem) {
            ConditionItem citem = (ConditionItem) item;
            DataRef dref0 = citem.getAttribute();
            Enumeration entities = dref0.getEntities();
            XEntity entity0 = null;

            while(entities.hasMoreElements()) {
               Object ename = entities.nextElement();

               if(ename == null) {
                  continue;
               }

               entity0 = model.getEntity(ename.toString());

               // a formula?
               if(entity0 == null) {
                  continue;
               }
            }

            if(!(dref0 instanceof ExpressionRef)) {
               XAttribute attr =
                  entity0.getAttribute(((AttributeRef) dref0).getAttribute());

               if(attr instanceof ExpressionAttribute) {
                  dref0 = new ExprAttributeRef((ExpressionAttribute) attr);
               }
            }

            if(dref0 instanceof ExpressionRef) {
               ExpressionRef eref = (ExpressionRef) dref0;
               Enumeration tables = getRefTables(eref, model);

               while(tables.hasMoreElements()) {
                  AttributeRef attref = (AttributeRef) tables.nextElement();
                  String ent = attref.getEntity();
                  Object alias = partition.getRunTimeTable(ent, true);

                  if(alias != null) {
                     sql.addTable(ent, alias);
                  }
                  else {
                     sql.addTable(ent);
                  }
               }
            }
            else {
               // a formula?
               if(entity0 == null) {
                  continue;
               }

               XAttribute attr =
                  entity0.getAttribute(((AttributeRef) dref0).getAttribute());
               String tblName = attr.getTable();
               Object alias = partition.getRunTimeTable(tblName, true);

               if(alias != null) {
                  sql.addTable(tblName, alias);
               }
               else {
                  sql.addTable(tblName);
               }
            }

            DataRef dref = citem.getAttribute();

            if(dref instanceof ExpressionRef) {
               ExpressionRef eref = (ExpressionRef) dref;
               String expr =
                  convertExpression(eref.getExpression(), model, helper);
               citem.setAttribute(new AttributeRef("", expr));
            }
            else {
               String entity = dref.getEntity();

               if(entity != null) {
                  XEntity xentity = model.getEntity(entity);
                  XAttribute xattr = xentity.getAttribute(dref.getAttribute());

                  if(xattr instanceof ExpressionAttribute) {
                     String expr = convertExpression(
                        ((ExpressionAttribute) xattr).getExpression(), model,
                        helper);
                     citem.setAttribute(new AttributeRef("", expr));
                  }

                  continue;
               }

               String attr = dref.getAttribute();
               Enumeration attributes = selection.getAttributes();
               boolean foundExpRef = false;

               while(attributes.hasMoreElements()) {
                  Object tempattr = attributes.nextElement();

                  if(tempattr instanceof ExpressionRef) {
                     ExpressionRef eref = (ExpressionRef) tempattr;

                     if(eref.getName().equals(attr)) {
                        String expr = convertExpression(eref.getExpression(),
                                                        model, helper);
                        citem.setAttribute(new AttributeRef("", expr));
                        foundExpRef = true;
                     }
                  }
               }

               if(!foundExpRef) {
                  if(i > 0) {
                     i--;
                  }

                  conditions.remove(i);
                  conditions.remove(i);
                  i--;
               }
            }
         }
      }

      return conditions;
   }

   /**
    * Gets the tables whose columns are used an expression.
    *
    * @param eref the expression reference.
    * @parma model the logical model referred to by the expression.
    *
    * @return an Enumeration with the names of the tables whose columns
    * are used in this expression.
    */
   private Enumeration getRefTables(ExpressionRef eref, XLogicalModel model) {
      Set set = new HashSet();
      Enumeration enumeration = eref.getAttributes();

      while(enumeration.hasMoreElements()) {
         AttributeRef ref = (AttributeRef) enumeration.nextElement();
         String entity = ref.getEntity();
         String attr = ref.getAttribute();
         XEntity xentity = model.getEntity(entity);
         XAttribute xattr = null;

         if(xentity == null && eref instanceof ExprAttributeRef) {
            set.add(new AttributeRef(entity, attr));
         }
         else if(xentity != null) {
            xattr = xentity.getAttribute(attr);
         }

         if(xattr == null) {
            continue;
         }

         if(xattr instanceof ExpressionAttribute) {
            eref = new ExpressionRef(null, xattr.getName());
            eref.setExpression(((ExpressionAttribute) xattr).getExpression());
            Enumeration enum2 = eref.getAttributes();

            while(enum2.hasMoreElements()) {
               AttributeRef ref2 = (AttributeRef) enum2.nextElement();
               set.add(ref2);
            }
         }
         else {
            String table = xattr.getTable();
            String column = xattr.getColumn();
            AttributeRef ref2 = new AttributeRef(table, column);
            set.add(ref2);
         }
      }

      return new IteratorEnumeration(set.iterator());
   }

   /**
    * Maps attribute to physical table field.
    *
    * @param entity Name of the entity to which this attribute belongs.
    * @param name Name of the attribute that needs to be mapped to
    * the physical table field.
    * @param model the logical model containing the entity.
    * @return String[] storing tablename and fieldname.
    */
   private String[] mapToPhysicalField(String entity, String name,
                                       XLogicalModel model) {
      String[] field = new String[2];

      field[0] = null;
      field[1] = null;

      try {
         if(entity != null) {
            XEntity ent = model.getEntity(entity);
            XAttribute attr = ent.getAttribute(name);

            if(attr != null) {
               field[0] = attr.getTable();
               field[1] = attr.getColumn();
            }
         }
         else {
            Enumeration entities = model.getEntities();

            while(entities.hasMoreElements()) {
               XEntity ent = (XEntity) entities.nextElement();
               XAttribute attr = ent.getAttribute(name);

               if(attr != null) {
                  field[0] = attr.getTable();
                  field[1] = attr.getColumn();
                  break;
               }
            }
         }
      }
      catch(Exception exc) {
         LOG.warn("Failed to map model attribute to database " +
            "column: model=" + model + ", entity=" + entity + ", attribute=" +
            name, exc);
      }

      return field;
   }

   /**
    * Replace "field['xxx']" with "xxx" in expression.
    *
    * @param exp the specified expression.
    * @param model the specified logical model.
    */
   protected String convertExpression(String exp, XLogicalModel model,
                                      SQLHelper helper) {
      StringBuilder temp = new StringBuilder();
      int start, end;

      while((start = exp.indexOf("field['")) != -1) {
         end = exp.indexOf("']", start + 7);

         if(end == -1) {
            break;
         }

         temp.append(exp.substring(0, start));
         String name = exp.substring(start + 7, end);
         int index = name.lastIndexOf(".");

         if(index == -1) {
            temp.append(XUtil.quoteName(name, helper));
         }
         else {
            String entity = name.substring(0, index);
            String attr = name.substring(index + 1);
            XEntity xentity = model == null ? null : model.getEntity(entity);
            XAttribute xattr = xentity == null ?
               null : xentity.getAttribute(attr);

            if(xattr instanceof ExpressionAttribute) {
               String exp2 = ((ExpressionAttribute) xattr).getExpression();
               name = convertExpression(exp2, null, helper);
            }
            else if(xattr != null) {
               String table = xattr.getTable();
               String column = xattr.getColumn();
               name = helper.quotePath(table + "." + column);
            }
            else {
               name = helper.quotePath(entity + "." + attr);
            }

            temp.append(name);
         }

         exp = exp.substring(end + 2);
      }

      temp.append(exp);

      return temp.toString();
   }

   /**
    * Recursively find related tables.
    */
   public static void findRelatedTables(String table, Map tbls,
                                        XPartition model) {
      if(tbls.keySet().contains(table)) {
         return;
      }

      String alias = table;

      if(model.isAlias(table)) {
         table = model.getAliasTable(table, true);
      }

      PartitionTable ptable = model.getPartitionTable(table);

      if(ptable == null || ptable.getType() == inetsoft.uql.erm.PartitionTable.VIEW) {
         return;
      }

      tbls.put(alias, table);

      // check relationships
      Enumeration rels = model.getRelationships();

      while(rels.hasMoreElements()) {
         XRelationship rel = (XRelationship) rels.nextElement();

         if(rel.getDependentTable().equals(alias)) {
            findRelatedTables(rel.getIndependentTable(), tbls, model);
         }
         else if(rel.getIndependentTable().equals(alias)) {
            findRelatedTables(rel.getDependentTable(), tbls, model);
         }
      }
   }

   private int cnt = 0; // hashcode increment for null

   private static final Logger LOG =
      LoggerFactory.getLogger(JDBCModelHandler.class);
}
