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
package inetsoft.mv.data;

import inetsoft.mv.MVColumn;
import inetsoft.mv.MVDef;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.PreAssetQuery;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.jdbc.util.ConditionListHandler;
import inetsoft.util.script.ScriptEnv;
import inetsoft.util.script.ScriptException;

import java.util.HashMap;
import java.util.Map;

/**
 * MVConditionListHandler translates a selection from a data model into
 * a XFilterNode format.
 *
 * @version 11.2
 * @author InetSoft Technology Corp
 * @see XFilterNode
 */
public class MVConditionListHandler extends ConditionListHandler {
   public MVConditionListHandler(TableAssembly table, MV mv, VariableTable vars,
      MVDef def)
   {
      super();

      this.mvdef = def;
      this.vars = vars;
      Worksheet ws = table.getWorksheet();
      this.box = new AssetQuerySandbox(ws == null ? new Worksheet() : ws);
      this.box.setWSName(def.getWsId());

      // in the case a table's formula is rewritten, the tables are like:
      //   Query2 (condition: col1 = ...)
      //   Query2_O (columns: Count_Query2_O_col1 ...)
      // In this case the MV contains the data for Query2_O, so if the
      // condition is applied on the MV, the column (col1) is not found
      // here we map col1 to Count_Query2_O_col1 to avoid the problem
      if(table instanceof MirrorTableAssembly &&
         !table.getName().equals(mvdef.getMVTable()))
      {
         MirrorTableAssembly mirror = (MirrorTableAssembly) table;
         ColumnSelection subcols =
            ((TableAssembly) mirror.getAssembly()).getColumnSelection();

         for(int i = 0 ; i < subcols.getAttributeCount(); i++) {
            DataRef ref = subcols.getAttribute(i);
            ref = ((ColumnRef) ref).getDataRef();

            if(ref instanceof AliasDataRef) {
               AliasDataRef aref = (AliasDataRef) ref;
               String oname = aref.getDataRef().getAttribute();

               // if the original column doesn't exist in the MV, map it
               // to the aliased name in the condition
               if(mv.indexOfHeader(oname, 0) < 0) {
                  // e.g. col1 -> Count_query1_col1
                  colmap.put(oname, aref.getAttribute());
               }
            }
         }
      }
   }

   /**
    * Create XFilterNode.
    */
   @Override
   protected XFilterNode createCondNode(Condition cond,
                                        XExpression field,
                                        VariableTable vars)
   {
      XFilterNode condnode = null;
      Object val = null;
      int vcnt = cond.getValueCount();
      String op = getOP(cond);

      // fix bug1329125428467, if the op is ">" or "<" or "=",
      // only get the first value as condition value
      if(vcnt > 1 && !(op.startsWith(">") || op.startsWith("<") || op.equals("="))) {
         val = new Object[vcnt];

         for(int i = 0; i < vcnt; i++) {
            Object obj = cond.getValue(i);

            if(obj instanceof ExpressionValue) {
               obj = executeScript(cond, (ExpressionValue) obj, i, mvcol);
            }

            ((Object[]) val)[i] = obj;
         }
      }
      else {
         val = cond.getValue(0);

         if(val instanceof ExpressionValue) {
            val = executeScript(cond, (ExpressionValue) val, 0, mvcol);
         }
      }

      XExpression exp = new XExpression(val, XExpression.VALUE);
      condnode = new XBinaryCondition(field, exp, op);
      condnode.setIsNot(cond.isNegated());
      return condnode;
   }

   /**
    * Execute the mv condition.
    */
   public Object executeScript(Condition cond, ExpressionValue eval, int idx,
                               MVColumn mvcol)
   {
      if(eval == null) {
         return null;
      }

      Object val = null;
      String exp = eval.getExpression();
      ScriptEnv senv = box.getScriptEnv();
      MVScriptable scriptable = new MVScriptable(mvdef, mvcol);

      try {
         senv.put("MV", scriptable);
         val = senv.exec(senv.compile(exp), box.getScope(), null, box.getWorksheet());
      }
      catch(Exception ex) {
         throw new ScriptException("MV Script error: " + ex.getMessage());
      }

      val = PreAssetQuery.getScriptValue(val, cond);
      cond.setValue(idx, val);

      return val;
   }

   /**
    * Get the transformed operation.
    */
   private String getOP(Condition cond) {
      String op = null;
      int operation = cond.getOperation();

      switch(operation) {
         case XCondition.ONE_OF:
            op = "IN";
            break;
         case XCondition.BETWEEN:
            op = "BETWEEN";
            break;
         case XCondition.LESS_THAN:
            op = cond.isEqual() ? "<=" : "<";
            break;
         case XCondition.GREATER_THAN:
            op = cond.isEqual() ? ">=" : ">";
            break;
         case XCondition.EQUAL_TO:
            op = "=";
            break;
         case XCondition.NULL:
            op = "null";
            break;
         // start with and contains will be converted to in
         case XCondition.STARTING_WITH:
            op = "STARTSWITH"; // like
            break;
         case XCondition.CONTAINS:
            op = "CONTAINS"; // like
            break;
         case XCondition.LIKE:
            op = "LIKE";
            break;
         default:
            throw new RuntimeException("Unsupported operation found: " +
                                       operation);
      }

      return op;
   }

   /**
    * Create XFilterNode Item.
    */
   @Override
   protected XFilterNodeItem createItem(ConditionItem item,
                                        VariableTable vars)
   {
      Condition cond = item.getCondition();
      DataRef dref = item.getAttribute();
      cond.replaceVariable(vars);
      XExpression field = createXExpression(dref);
      XFilterNode condnode = createCondNode(cond, field, vars);
      return new XFilterNodeItem(condnode, item.getLevel());
   }

   /**
    * Create XExpression.
    */
   private XExpression createXExpression(DataRef dref) {
      String attrName = MVDef.getMVHeader(dref);
      String ftype = XExpression.FIELD;
      String subattr = colmap.get(attrName);
      mvcol = getMVColumn(attrName);

      if(subattr != null) {
         attrName = subattr;
      }

      return new XExpression(attrName, ftype);
   }

   /**
    * Convert an attribute to an XExpression.
    */
   @Override
   protected XExpression attributeToExpression(DataRef dref) {
      return createXExpression(dref);
   }

   /**
    * Convert a DataRef to an XExpression.
    */
   @Override
   protected XExpression dataRefToExpression(DataRef dref) {
      return createXExpression(dref);
   }

   /**
    * Get the mv column.
    */
   public MVColumn getMVColumn(String name) {
      MVColumn mvcol = mvdef.getColumn(name, true);

      if(mvcol == null) {
         mvcol = mvdef.getColumn(name, false);
      }

      return mvcol;
   }

   // column name in mirror mapping to sub-table (with formula rewrite)
   private Map<String, String> colmap = new HashMap<>();
   private MVDef mvdef;
   private MVColumn mvcol;
   private VariableTable vars;
   private AssetQuerySandbox box;
}
