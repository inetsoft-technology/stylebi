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

import java.util.HashSet;
import java.util.Set;

/**
 * Helper class of UniformSQL. This class generate SQL statement
 * from Informix Database.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class InformixSQLHelper extends DB2SQLHelper {
   /**
    * Get the sql helper type.
    * @return the sql helper type.
    */
   @Override
   public String getSQLHelperType() {
      return "informix";
   }

   /**
    * Get the function used for converting to lower case.
    */
   public String getDbLowerCaseFunction() {
      return "LOWER";
   }

   /**
    * Generate condition string for XBinaryCondition.
    * @param condition
    * @return condition string
    */
   @Override
   protected String buildConditionString(XBinaryCondition condition) {
      String op = condition.getOp();
      StringBuilder str = new StringBuilder();
      XExpression expression1 = condition.getExpression1();
      XExpression expression2 = condition.getExpression2();
      String v2 = expression2.getValue().toString();
      String v3 = v2.toLowerCase();

      // subquery pattern: select * from
      if(op.equalsIgnoreCase("like") &&
         !(v2.startsWith("'") && v2.endsWith("'")) &&
         !(v2.startsWith("\"") && v2.endsWith("\"")) &&
         !(v2.startsWith("(") && v2.endsWith(")")) &&
         !(v3.startsWith("select") && v3.indexOf("from") >=9))
      {
         if(condition.isIsNot()) {
            str.append("not (");
         }

         str.append(buildExpressionString(expression1));
         str.append(" ");
         str.append(op.toString());
         str.append(" ");
         str.append("\"");
         str.append(buildExpressionString(expression2));
         str.append("\"");

         if(condition.isIsNot()) {
            str.append(")");
         }
      }
      else {
         return super.buildConditionString(condition);
      }

      return str.toString();
   }

   /**
    * Transform date format
    */
   @Override
   protected String transformDate(String str) {
      StringBuilder str_informix = new StringBuilder();
      str = str.trim();

      if(str.startsWith("{d")) {
         String temp = str.substring(2, str.length()-1).trim();

         if(str.indexOf('-') > -1 && str.indexOf(':') > -1) {
            str_informix.append("To_Date(");
            str_informix.append(temp);
            str_informix.append(", '%Y-%m-%d %H:%M:%S')");
         }
         else if(str.indexOf('-') > -1) {
            str_informix.append("To_Date(");
            str_informix.append(temp);
            str_informix.append(", '%Y-%m-%d')");
         }
         else if(str.indexOf(':') > -1) {
            str_informix.append("To_Date(");
            str_informix.append(temp);
            str_informix.append(", '%H:%M:%S')");
         }
         else {
            str_informix.append(temp);
         }
      }
      else if(str.startsWith("{ts")) {
         str_informix.append("To_Date(");
         str_informix.append(str.substring(3, str.length()-1).trim());
         str_informix.append(", '%Y-%m-%d %H:%M:%S')");
      }
      else if(str.startsWith("{t")) {
         str_informix.append("To_Date(");
         str_informix.append(str.substring(2, str.length()-1).trim());
         str_informix.append(", '%H:%M:%S')");
      }
      else {
         return str;
      }

      return str_informix.toString();
   }

   /**
    * The alias has special character should be quoted as "alias".
    * @param same true if the alias is the same as the column string.
    * @param part true if the alias is the same as the column part.
    */
   @Override
   protected String quoteColumnAlias(String alias, boolean same, boolean part){
      // if quote date in informix, syntax error occurs
      if("date".equals(alias)) {
         return alias;
      }

      return super.quoteColumnAlias(alias, same, part);
   }

   /**
    * Check if a word is a keyword.
    * @param word the specified keyword.
    * @return <tt>true</tt> is a keyword, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isKeyword(String word) {
      return super.isKeyword(word) || keywords.contains(word);
   }

   /**
    * Get a sql string for replacing the table name so a row limit is added
    * to restrict the number of rows from the table.
    */
   @Override
   protected String getTableWithLimit(String tbl, int maxrows) {
      // this clause is not supported in subquery by informix
      // return "select first " + maxrows + " * from " + quoteTableName(tbl);
      return null;
   }

   /**
    * Create a query that limits the output to the specified number of rows.
    */
   @Override
   protected String generateMaxRowsClause(String sql, int maxrows) {
      // this clause is not supported in subquery by informix
      /*
      return "select first " + maxrows + " * from (" + sql + ") inner" +
         System.currentTimeMillis();
      */
      return sql;
   }

   @Override
   public boolean isValidAlias(String alias) {
      return alias == null || super.isValidAlias(alias) && !alias.contains(".") &&
         !alias.contains(":");
   }

   private static Set keywords = new HashSet(); // keywords

   static {
      keywords.add("access");
      keywords.add("access_method");
      keywords.add("active");
      keywords.add("administrator");
      keywords.add("after");
      keywords.add("aggregate");
      keywords.add("alignment");
      keywords.add("all_rows");
      keywords.add("ansi");
      keywords.add("append");
      keywords.add("attach");
      keywords.add("audit");
      keywords.add("auto");
      keywords.add("autofree");
      keywords.add("avoid_execute");
      keywords.add("avoid_subqf");
      keywords.add("before");
      keywords.add("binary");
      keywords.add("boolean");
      keywords.add("buffered");
      keywords.add("builtin");
      keywords.add("byte");
      keywords.add("cache");
      keywords.add("call");
      keywords.add("cannothash");
      keywords.add("cardinality");
      keywords.add("class");
      keywords.add("client");
      keywords.add("cluster");
      keywords.add("clustersize");
      keywords.add("coarse");
      keywords.add("cobol");
      keywords.add("codeset");
      keywords.add("collection");
      keywords.add("committed");
      keywords.add("commutator");
      keywords.add("concurrent");
      keywords.add("const");
      keywords.add("constructor");
      keywords.add("copy");
      keywords.add("costfunc");
      keywords.add("crcols");
      keywords.add("current_role");
      keywords.add("cycle");
      keywords.add("database");
      keywords.add("datafiles");
      keywords.add("dataskip");
      keywords.add("datetime");
      keywords.add("dba");
      keywords.add("dbdate");
      keywords.add("dbpassword");
      keywords.add("dbservername");
      keywords.add("debug");
      keywords.add("dec_t");
      keywords.add("decode");
      keywords.add("default_role");
      keywords.add("deferred_prepare");
      keywords.add("define");
      keywords.add("delay");
      keywords.add("delimiter");
      keywords.add("deluxe");
      keywords.add("deref");
      keywords.add("detach");
      keywords.add("directives");
      keywords.add("dirty");
      keywords.add("disabled");
      keywords.add("distributebinary");
      keywords.add("distributesreferences");
      keywords.add("distributions");
      keywords.add("document");
      keywords.add("donotdistribute");
      keywords.add("dormant");
      keywords.add("dtime_t");
      keywords.add("each");
      keywords.add("elif");
      keywords.add("enabled");
      keywords.add("encryption");
      keywords.add("enum");
      keywords.add("environment");
      keywords.add("error");
      keywords.add("exclusive");
      keywords.add("executeanywhere");
      keywords.add("exit");
      keywords.add("explain");
      keywords.add("explicit");
      keywords.add("express");
      keywords.add("expression");
      keywords.add("extend");
      keywords.add("extent");
      keywords.add("far");
      keywords.add("file");
      keywords.add("fillfactor");
      keywords.add("filtering");
      keywords.add("first_rows");
      keywords.add("fixchar");
      keywords.add("fixed");
      keywords.add("flush");
      keywords.add("foreach");
      keywords.add("format");
      keywords.add("fraction");
      keywords.add("fragment");
      keywords.add("free");
      keywords.add("function");
      keywords.add("general");
      keywords.add("gk");
      keywords.add("handlesnulls");
      keywords.add("hash");
      keywords.add("high");
      keywords.add("hint");
      keywords.add("hold");
      keywords.add("hybrid");
      keywords.add("if");
      keywords.add("ifx_int8_t");
      keywords.add("ifx_lo_create_spec_t");
      keywords.add("ifx_lo_stat_t");
      keywords.add("implicit");
      keywords.add("inactive");
      keywords.add("increment");
      keywords.add("indexes");
      keywords.add("informix");
      keywords.add("init");
      keywords.add("initcap");
      keywords.add("inline");
      keywords.add("inout");
      keywords.add("instead");
      keywords.add("int8");
      keywords.add("integ");
      keywords.add("internal");
      keywords.add("internallength");
      keywords.add("intrvl_t");
      keywords.add("iscanonical");
      keywords.add("item");
      keywords.add("iterator");
      keywords.add("keep");
      keywords.add("labeleq");
      keywords.add("labelge");
      keywords.add("labelglb");
      keywords.add("labelgt");
      keywords.add("labelle");
      keywords.add("labellt");
      keywords.add("labellub");
      keywords.add("labeltostring");
      keywords.add("let");
      keywords.add("limit");
      keywords.add("list");
      keywords.add("listing");
      keywords.add("load");
      keywords.add("loc_t");
      keywords.add("locator");
      keywords.add("lock");
      keywords.add("locks");
      keywords.add("log");
      keywords.add("long");
      keywords.add("low");
      keywords.add("lvarchar");
      keywords.add("matches");
      keywords.add("maxerrors");
      keywords.add("maxlen");
      keywords.add("maxvalue");
      keywords.add("mdy");
      keywords.add("median");
      keywords.add("medium");
      keywords.add("memory_resident");
      keywords.add("middle");
      keywords.add("minvalue");
      keywords.add("mode");
      keywords.add("moderate");
      keywords.add("modify");
      keywords.add("money");
      keywords.add("mounting");
      keywords.add("multiset");
      keywords.add("name");
      keywords.add("negator");
      keywords.add("new");
      keywords.add("nocache");
      keywords.add("nocycle");
      keywords.add("nomaxvalue");
      keywords.add("nomigrate");
      keywords.add("nominvalue");
      keywords.add("non_resident");
      keywords.add("noorder");
      keywords.add("normal");
      keywords.add("notemplatearg");
      keywords.add("nvarchar");
      keywords.add("nvl");
      keywords.add("off");
      keywords.add("old");
      keywords.add("online");
      keywords.add("opaque");
      keywords.add("opclass");
      keywords.add("operational");
      keywords.add("optcompind");
      keywords.add("optical");
      keywords.add("optimization");
      keywords.add("out");
      keywords.add("page");
      keywords.add("parallelizable");
      keywords.add("parameter");
      keywords.add("partition");
      keywords.add("passedbyvalue");
      keywords.add("password");
      keywords.add("pdqpriority");
      keywords.add("percall_cost");
      keywords.add("pli");
      keywords.add("pload");
      keywords.add("previous");
      keywords.add("private");
      keywords.add("put");
      keywords.add("raise");
      keywords.add("range");
      keywords.add("raw");
      keywords.add("recordend");
      keywords.add("ref");
      keywords.add("referencing");
      keywords.add("register");
      keywords.add("rejectfile");
      keywords.add("release");
      keywords.add("remainder");
      keywords.add("rename");
      keywords.add("reoptimization");
      keywords.add("repeatable");
      keywords.add("replication");
      keywords.add("reserve");
      keywords.add("resolution");
      keywords.add("resource");
      keywords.add("restart");
      keywords.add("resume");
      keywords.add("retain");
      keywords.add("return");
      keywords.add("returning");
      keywords.add("returns");
      keywords.add("reuse");
      keywords.add("robin");
      keywords.add("role");
      keywords.add("rollforward");
      keywords.add("round");
      keywords.add("routine");
      keywords.add("row");
      keywords.add("rowid");
      keywords.add("rowids");
      keywords.add("sameas");
      keywords.add("samples");
      keywords.add("save");
      keywords.add("schedule");
      keywords.add("scratch");
      keywords.add("secondary");
      keywords.add("selconst");
      keywords.add("selfunc");
      keywords.add("sequence");
      keywords.add("serial");
      keywords.add("serial8");
      keywords.add("serializable");
      keywords.add("serveruuid");
      keywords.add("share");
      keywords.add("short");
      keywords.add("signed");
      keywords.add("sitename");
      keywords.add("skall");
      keywords.add("skinhibit");
      keywords.add("skip");
      keywords.add("skshow");
      keywords.add("smallfloat");
      keywords.add("specific");
      keywords.add("sqlcontext");
      keywords.add("stability");
      keywords.add("stack");
      keywords.add("standard");
      keywords.add("start");
      keywords.add("static");
      keywords.add("statistics");
      keywords.add("stdev");
      keywords.add("step");
      keywords.add("stop");
      keywords.add("storage");
      keywords.add("strategies");
      keywords.add("string");
      keywords.add("stringtolabel");
      keywords.add("struct");
      keywords.add("style");
      keywords.add("substr");
      keywords.add("support");
      keywords.add("sync");
      keywords.add("synonym");
      keywords.add("system");
      keywords.add("temp");
      keywords.add("template");
      keywords.add("test");
      keywords.add("text");
      keywords.add("timeout");
      keywords.add("today");
      keywords.add("trace");
      keywords.add("trigger");
      keywords.add("triggers");
      keywords.add("truncate");
      keywords.add("typedef");
      keywords.add("typeid");
      keywords.add("typename");
      keywords.add("typeof");
      keywords.add("uncommitted");
      keywords.add("under");
      keywords.add("units");
      keywords.add("unload");
      keywords.add("unlock");
      keywords.add("unsigned");
      keywords.add("use_subqf");
      keywords.add("var");
      keywords.add("variable");
      keywords.add("variance");
      keywords.add("variant");
      keywords.add("violations");
      keywords.add("void");
      keywords.add("volatile");
      keywords.add("wait");
      keywords.add("warning");
      keywords.add("while");
      keywords.add("without");
      keywords.add("xadatasource");
      keywords.add("xid");
      keywords.add("xload");
      keywords.add("xunload");
   }
}
