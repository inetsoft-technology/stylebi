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
package inetsoft.uql.erm.transform;

import inetsoft.util.FileSystemService;
import inetsoft.util.XMLTool;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.File;
import java.util.Properties;

/**
 * A transform descriptor is used in data model transform.
 *
 * @version 10.1
 * @author InetSoft Technology Corp.
 */
public class TransformDescriptor {
   /**
    * DB2 data source type.
    */
   public static final int DB2 = 0;

   /**
    * Oracle data source type.
    */
   public static final int ORACLE = 1;

   /**
    * SQL data source type.
    */
   public static final int SQLSERVER = 2;

   /**
    * Table and column name uppercase.
    */
   public static final int UPPERCASE = 0;

   /**
    * Table and column name lowercase.
    */
   public static final int LOWERCASE = 1;

   /**
    * Creates a new instance of TransformDescriptor.
    */
   public TransformDescriptor(Properties props) {
      super();

      this.props = props;
      String val = props.getProperty("db");
      int dtype = getDBType(val);

      if(dtype < 0) {
         valid = false;
         return;
      }

      val = props.getProperty("from");

      if(val == null) {
         valid = false;
         return;
      }

      File file = FileSystemService.getInstance().getFile(val);

      if(!file.isFile()) {
         System.err.println("Source file not found: " + val + "!");
         valid = false;
         return;
      }

      val = props.getProperty("to");

      if(val == null) {
         valid = false;
         return;
      }
   }

   /**
    * Check if inputed properties is valid.
    */
   public boolean isValid() {
      return valid;
   }

   /**
    * Get the db type.
    */
   public int getDBType() {
      String val = props.getProperty("db");
      return getDBType(val);
   }

   /**
    * Get the db type from a string, <tt>-1</tt> if not supported.
    */
   private int getDBType(String val) {
      for(int i = 0; i < stypes.length; i++) {
         if(stypes[i].equals(val)) {
            return itypes[i];
         }
      }

      return -1;
   }

   /**
    * Get the url value.
    */
   public String getURL() {
      String val = props.getProperty("url");
      return val == null ? "" : val;
   }

   /**
    * Get the source prefix.
    */
   public String getSourcePrefix() {
      String val = props.getProperty("from.prefix");
      return val == null ? "" : val;
   }

   /**
    * Get the target prefix.
    */
   public String getTargetPrefix() {
      String val = props.getProperty("to.prefix");
      return val == null ? "" : val;
   }

   /**
    * Get the input file.
    */
   public File getInputFile() {
      String val = props.getProperty("from");
      return FileSystemService.getInstance().getFile(val);
   }

   /**
    * Get the output file.
    */
   public File getOutputFile() {
      String val = props.getProperty("to");
      return FileSystemService.getInstance().getFile(val);
   }

   /**
    * Get the data source.
    */
   public String getDataSource() {
      String val = props.getProperty("datasource");
      return val == null ? "" : val;
   }

   /**
    * Transform table name.
    */
   public String transformTableName(String oldName) {
      if(oldName == null) {
         return oldName;
      }

      if(!"".equals(getSourcePrefix())) {
         if(oldName.indexOf(getSourcePrefix()) != -1) {
            String subPart = oldName.substring(getSourcePrefix().length());

            return getCaseStyle() == UPPERCASE ?
               getTargetPrefix().toUpperCase() + subPart.toUpperCase() :
               getTargetPrefix().toLowerCase() + subPart.toLowerCase();
         }
         else {
            return getCaseStyle() == UPPERCASE ? oldName.toUpperCase() :
               oldName.toLowerCase();
         }
      }

      return getCaseStyle() == UPPERCASE ?
         getTargetPrefix().toUpperCase() + oldName.toUpperCase() :
         getTargetPrefix().toLowerCase() + oldName.toLowerCase();
   }

   /**
    * Transform column name.
    */
   public String transformColName(String oldName) {
      if(!"".equals(getSourcePrefix()) &&
         oldName.indexOf(getSourcePrefix()) != -1)
      {
         String subPart = oldName.substring(getSourcePrefix().length());

         return getCaseStyle() == UPPERCASE ?
            getTargetPrefix().toUpperCase() + subPart.toUpperCase() :
            getTargetPrefix().toLowerCase() + subPart.toLowerCase();
      }

      return getCaseStyle() == UPPERCASE ? oldName.toUpperCase() :
         oldName.toLowerCase();
   }

   /**
    * Replace the column node.
    */
   public void replaceColumnNode(Element cnode, Element pnode, String name) {
      Document doc = cnode.getOwnerDocument();
      Element nnode = doc.createElement("column");
      XMLTool.addCDATAValue(nnode, name);
      pnode.replaceChild(nnode, cnode);
   }

   /**
    * Replace the table node.
    */
   public void replaceTableNode(Element tnode, Element pnode, String name) {
      Document doc = tnode.getOwnerDocument();
      Element nnode = doc.createElement("table");
      XMLTool.addCDATAValue(nnode, name);
      pnode.replaceChild(nnode, tnode);
   }

   /**
    * Get diver.
    */
   public String getDriver() {
      String val = props.getProperty("db");

      for(int i = 0; i < stypes.length; i++) {
         if(stypes[i].equals(val)) {
            return drivers[i];
         }
      }

      return null;
   }

   /**
    * Get case style, uppercase or lowercase.
    */
   public int getCaseStyle() {
      String val = props.getProperty("case");
      int dbtype = getDBType();

      if(val == null) {
         if(dbtype == ORACLE || dbtype == DB2) {
            return UPPERCASE;
         }
         else if(dbtype == SQLSERVER) {
            return LOWERCASE;
         }
      }
      else {
         for(int i = 0; i < sctypes.length; i++) {
            if(sctypes[i].equals(val)) {
               return ictypes[i];
            }
         }
      }

      return -1;
   }

   private static String[] stypes = {"oracle", "db2", "sqlserver"};
   private static int[] itypes = {ORACLE, DB2, SQLSERVER};
   private static String[] drivers =
      {"oracle.jdbc.driver.OracleDriver", "com.ibm.db2.jcc.DB2Driver",
       "com.microsoft.jdbc.sqlserver.SQLServerDriver"};
   private static String[] sctypes = {"uppercase", "lowercase"};
   private static int[] ictypes = {UPPERCASE, LOWERCASE};

   private Properties props;
   private boolean valid = true;
}
