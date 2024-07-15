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
package inetsoft.uql.jdbc;

import inetsoft.uql.path.XSelection;
import inetsoft.util.OrderedMap;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * SQLProcessor, it could replace one unparseable column in a UniformSQL object
 * with another parseable column. After the UniformSQL object is parsed, it will
 * restore the parseable column with the unparseable column. In this way, an
 * unparseable column will not cause the sql unparseable.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
class SQLProcessor {
   /**
    * Create an instance.
    */
   public SQLProcessor(UniformSQL sql) {
      super();
      this.sql = sql;
      this.map = new OrderedMap();
   }

   /**
    * Parse statement.
    */
   public void parse(String stmt) {
      this.stmt = stmt;
      replace();
      parse0();
      restore();
   }

   /**
    * Parse sql internally.
    */
   private void parse0() {
      if(sql == null || stmt == null) {
         return;
      }

      try {
         synchronized(sql) {
            sql.setParseResult(UniformSQL.PARSE_FAILED);
            sql.parse(stmt, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);

            if(sql.getParseResult() == UniformSQL.PARSE_FAILED) {
               sql.parse(stmt, UniformSQL.PARSE_ONLY_SELECT_FROM,
                         UniformSQL.PARSE_PERIOD / 2);

               if(sql.getParseResult() == UniformSQL.PARSE_FAILED) {
                  sql.parse(stmt, UniformSQL.PARSE_ONLY_SELECT,
                            UniformSQL.PARSE_PERIOD / 2);
               }
            }
         }
      }
      catch(Exception ex) {
         synchronized(sql) {
            LOG.debug("Can not parse:{}\n{}", sql, ex.getMessage());
            // @by stephenwebster, Fix bug1404417347570
            // The very act of logging sql (which invokes toString()) causes
            // the parse result to become successful.
            sql.setParseResult(UniformSQL.PARSE_FAILED);
         }
      }
      finally {
         synchronized(sql) {
            sql.sqlstring = stmt;
            sql.notifyAll();
         }
      }
   }

   /**
    * Replace unparsable column with parseble column.
    */
   private void replace() {
      if(sql == null || stmt == null) {
         return;
      }

      // sort unparseable expression from longer to shorter, so that we
      // could avoid unwanted replacement (at lease reduce the possibility)
      String[] exps = sql.getExpressions();
      Arrays.sort(exps, new Comparator() {
         @Override
         public int compare(Object a, Object b) {
            String sa = (String) a;
            int lena = sa.length();
            String sb = (String) b;
            int lenb = sb.length();
            return lena == lenb ? 0 : (lena > lenb ? -1 : 1);
         }
      });

      for(int i = 0; i < exps.length; i++) {
         String name = "PSEUEDO_EXP_" + i;
         stmt = replaceExp(stmt, exps[i], name);
      }
   }

   /**
    * Replace one expression.
    */
   private String replaceExp(String stmt, String from, String to) {
      map.put(to, from);
      return Tool.replaceAll(stmt, from, to);
   }

   /**
    * Restore parseable column with unparseable column.
    */
   private void restore() {
      Iterator<String> keys = map.keySet().iterator();

      while(keys.hasNext()) {
         String from = keys.next();
         String to = (String) map.get(from);
         restoreSelection(from, to);
         restoreFilter(sql.getWhere(), from, to);
         restoreFilter(sql.getHaving(), from, to);
         restoreGroupBy(from, to);
         restoreOrderBy(from, to);
      }
   }

   /**
    * Restore selection.
    */
   private void restoreSelection(String from, String to) {
      XSelection selection = sql.getSelection();

      if(selection == null) {
         return;
      }

      int cnt = selection.getColumnCount();

      for(int i = 0; i < cnt; i++) {
         String path = selection.getColumn(i);
         int idx = path.indexOf(from);

         if(idx >= 0) {
            String npath = Tool.replaceAll(path, from, to);
            String alias = selection.getAlias(i);
            selection.setColumn(i, npath);
            selection.setAlias(idx, alias);
         }
      }
   }

   /**
    * Restore filter.
    */
   private void restoreFilter(XFilterNode filter, String from, String to) {
      if(filter instanceof XSet) {
         XSet set = (XSet) filter;

         for(int i = 0; i < set.getChildCount(); i++) {
            XFilterNode node = (XFilterNode)set.getChild(i);
            restoreFilter(node, from, to);
         }
      }
      else if(filter instanceof XBinaryCondition) {
         XBinaryCondition bin = (XBinaryCondition) filter;
         restoreExp(bin.getExpression1(), from, to);
         restoreExp(bin.getExpression2(), from, to);
      }
      else if(filter instanceof XUnaryCondition) {
         XUnaryCondition una = (XUnaryCondition) filter;
         restoreExp(una.getExpression1(), from, to);
      }
      else if(filter instanceof XTrinaryCondition) {
         XTrinaryCondition tri = (XTrinaryCondition) filter;
         restoreExp(tri.getExpression1(), from, to);
         restoreExp(tri.getExpression2(), from, to);
         restoreExp(tri.getExpression3(), from, to);
      }
   }

   /**
    * Restore expression.
    */
   private void restoreExp(XExpression exp, String from, String to) {
      Object val = exp.getValue();

      if(!(val instanceof String)) {
         return;
      }

      String path = (String) val;
      int idx = path.indexOf(from);

      if(idx >= 0) {
         String npath = Tool.replaceAll(path, from, to);
         String type = exp.getType();
         exp.setValue(npath, type);
      }
   }

   /**
    * Restore group by.
    */
   private void restoreGroupBy(String from, String to) {
      Object[] groups = sql.getGroupBy();

      if(groups == null) {
         return;
      }

      for(int i = 0; i < groups.length; i++) {
         if(!(groups[i] instanceof String)) {
            continue;
         }

         String path = (String) groups[i];
         int idx = path.indexOf(from);

         if(idx >= 0) {
            String npath = Tool.replaceAll(path, from, to);
            groups[i] = npath;
         }
      }
   }

   /**
    * Restore order by.
    */
   private void restoreOrderBy(String from, String to) {
      Object[] orders = sql.getOrderByFields();

      if(orders == null) {
         return;
      }

      for(int i = 0; i < orders.length; i++) {
         if(!(orders[i] instanceof String)) {
             continue;
         }

         String path = (String) orders[i];
         int idx = path.indexOf(from);

         if(idx >= 0) {
            String npath = Tool.replaceAll(path, from, to);
            String order = sql.getOrderBy(path);
            sql.replaceOrderBy(path, order, npath, order);
         }
      }
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(SQLProcessor.class);
   private UniformSQL sql;
   private String stmt;
   private Map map;
}
