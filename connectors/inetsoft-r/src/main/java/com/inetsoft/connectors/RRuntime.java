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
package com.inetsoft.connectors;

import com.inetsoft.connectors.script.REXPDecoder;
import com.inetsoft.connectors.script.RScope;
import inetsoft.report.ReportSheet;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.io.viewsheet.excel.CSVUtil;
import inetsoft.report.script.TableArray;
import inetsoft.report.script.formula.AssetQueryScope;
import inetsoft.uql.*;
import inetsoft.uql.asset.internal.WSExecution;
import inetsoft.uql.tabular.*;
import inetsoft.uql.util.XTableTableNode;
import inetsoft.uql.util.filereader.CSVLoader;
import inetsoft.util.FileSystemService;
import inetsoft.util.Tool;
import inetsoft.util.script.JavaScriptEnv;
import inetsoft.util.script.ScriptEnv;
import org.apache.commons.io.IOUtils;
import org.rosuda.REngine.REXP;
import org.rosuda.REngine.REXPMismatchException;
import org.rosuda.REngine.Rserve.RConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

/**
 * Runtime engine for the R connector.
 */
public class RRuntime extends TabularRuntime {
   @Override
   public XTableNode runQuery(TabularQuery query, VariableTable params) {
      XDataSource dataSource = query.getDataSource();
      RConnection connection = null;
      ScriptEnv scriptEnv = null;
      RScope scope = null;

      try {
         connection = createConnection((RDataSource) dataSource);
         RQuery rQuery = (RQuery) query;
         boolean hasPreScript = rQuery.getPreExecute() != null &&
            !rQuery.getPreExecute().trim().isEmpty();
         boolean hasPostScript = rQuery.getPostExecute() != null &&
            !rQuery.getPostExecute().trim().isEmpty();

         if(hasPreScript || hasPostScript) {
            scope = new RScope(connection);
            AssetQuerySandbox box = WSExecution.getAssetQuerySandbox();

            if(box != null) {
               scriptEnv = box.getScriptEnv();
               AssetQueryScope parentScope = box.getScope();
               scope.setPrototype(parentScope);
            }
            else if(query.getProperty("enclosingReport") != null) {
               ReportSheet report = (ReportSheet) query.getProperty("enclosingReport");
               scriptEnv = report.getScriptEnv();
            }
            else {
               scriptEnv = new JavaScriptEnv();
               scriptEnv.init();
            }
         }

         if(hasPreScript) {
            Object js = scriptEnv.compile(rQuery.getPreExecute());
            scriptEnv.exec(js, scope, null, null);
         }

         XTableNode result = evalScript(connection, rQuery);

         if(hasPostScript) {
            scope.setScriptExecuted(true);
            Object js = scriptEnv.compile(rQuery.getPostExecute());
            scriptEnv.exec(js, scope, null, null);
         }

         return result;
      }
      catch(REXPMismatchException me) {
         LOG.error("Operation not supported on: {}", me.getSender().toDebugString(), me);
         LOG.error("Make sure object being returned by R script is a data frame.");
      }
      catch(Exception e) {
         LOG.error("Error running query", e);
         handleError(params, e, () -> null);
      }
      finally {
         if(connection != null) {
            connection.close();
         }
      }

      return null;
   }

   public RConnection createConnection(RDataSource rDataSource) throws Exception {
      RConnection c = null;

      try {
         String url = rDataSource.getUrl();
         url = url == null || url.isEmpty() ? "127.0.0.1" : url;
         c = new RConnection(url, rDataSource.getPort());

         if(c.needLogin()) {
            c.login(rDataSource.getUser(), rDataSource.getPassword());
         }

         if(!c.isConnected()){
            throw new Exception("Could not create connection to R Server:\n" +
               "At url " + url + "\nWith port " + rDataSource.getPort());
         }

         return c;
      }
      catch(Exception e) {
         if(c != null) {
            c.close();
         }
         throw e;
      }
   }

   @Override
   public void testDataSource(TabularDataSource dataSource,
                              VariableTable params) throws Exception
   {
      RConnection c = null;

      try {
         c = createConnection((RDataSource) dataSource);
         REXP x = c.eval("R.version.string");
         x.asString();
         c.close();
      }
      finally {
         if(c != null) {
            c.close();
         }
      }
   }

   private XTableNode evalScript(RConnection connection, RQuery query) throws Exception {
      Object result = new REXPDecoder().decode(connection.eval(query.getScript()));

      if(result instanceof TableArray) {
         return new XTableTableNode(((TableArray) result).getElementTable());
      }
      else if(result instanceof String) {
         return new XTableTableNode(transferTable(connection, (String) result));
      }
      else {
         throw new IllegalStateException(
            "The result of the R script must be a data frame or the name of a variable " +
            "containing a data frame");
      }
   }

   public static boolean isList(RConnection connection, String name) {
      try {
         return "list".equals(new REXPDecoder().decode(connection.eval("typeof(" + name + ")")));
      }
      catch(Exception e) {
         LOG.debug("Failed to get type of variable '{}' from R server", name, e);
      }

      return false;
   }

   public static void transferTable(XTable table, RConnection connection, String name) {
      String fileName = "inetsoft-" + UUID.randomUUID().toString() + ".csv";

      try {
         try(OutputStream output = connection.createFile(fileName)) {
            CSVUtil.writeTableDataAssembly(table, output, ",");
         }

         connection.voidEval(name + " <- read.csv('" + fileName + "')");
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to transfer table '" + name + "' to R server", e);
      }
      finally {
         try {
            connection.removeFile(fileName);
         }
         catch(Exception e) {
            LOG.debug("Failed to remove temp file from R server", e);
         }
      }
   }

   public static XTable transferTable(RConnection connection, String name) {
      String fileName = "inetsoft-" + UUID.randomUUID().toString() + ".csv";
      File tempFile = FileSystemService.getInstance().getCacheTempFile("r", ".csv");

      try {
         connection.voidEval("write.csv(" + name + ", '" + fileName + "')");

         try(InputStream input = connection.openFile(fileName);
             OutputStream output = new FileOutputStream(tempFile))
         {
            IOUtils.copy(input, output);
         }

         return CSVLoader.readCSV(
            tempFile, "UTF-8", true, ",", true, false, new HashMap<>(), new ArrayList<>(), true,
            null, 50000, -1, -1, null);
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to transfer table '" + name + "' from R server", e);
      }
      finally {
         Tool.deleteFile(tempFile);

         try {
            connection.removeFile(fileName);
         }
         catch(Exception e) {
            LOG.debug("Failed to remove temp file from R server", e);
         }
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(RRuntime.class.getName());
}
