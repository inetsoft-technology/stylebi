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
import inetsoft.util.script.ScriptEnv;
import inetsoft.util.script.ScriptSpan;
import inetsoft.util.script.graal.GraalJavaScriptEnv;
import org.apache.commons.io.IOUtils;
import org.rosuda.REngine.REXP;
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
      ScriptSpan span = ScriptSpan.NONE;

      try {
         RQuery rQuery = (RQuery) query;

         if(rQuery.getScript() == null || rQuery.getScript().trim().isEmpty()) {
            throw new IllegalArgumentException(NO_SCRIPT_MESSAGE);
         }

         connection = createConnection((RDataSource) dataSource);
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
               scope.setParentScope(parentScope);
            }
            else if(query.getProperty("enclosingReport") != null) {
               ReportSheet report = (ReportSheet) query.getProperty("enclosingReport");
               scriptEnv = report.getScriptEnv();
            }
            else {
               scriptEnv = new GraalJavaScriptEnv();
               scriptEnv.init();
            }

            // one claimed span from the pre-script compile through the post-script exec, so
            // the post-script sees the pre-script's state on a pooled context (bug #76960)
            span = scriptEnv == null ? ScriptSpan.NONE : scriptEnv.openSpan();
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
      catch(Exception e) {
         LOG.error("Error running query", e);
         // the connection is still open here, so R's own error text can be asked for
         Tool.addUserMessage(describeFailure(e, connection, dataSource));
         handleError(params, e, () -> null);
      }
      finally {
         // the connection is closed even if closing the span throws
         try {
            span.close();
         }
         finally {
            if(connection != null) {
               connection.close();
            }
         }
      }

      return null;
   }

   /**
    * Builds the single-line message that tells the user why the query failed. It never
    * contains the script or the data source password.
    */
   static String describeFailure(Exception e, RConnection connection, XDataSource dataSource) {
      String message = e.getMessage();

      if(e instanceof IllegalArgumentException && NO_SCRIPT_MESSAGE.equals(message)) {
         return message;
      }

      String server = describeServer(dataSource);

      if(connection == null) {
         if(message != null && message.startsWith("login failed")) {
            return limit("Could not log in to the R server at " + server + " (" + message +
                         "); check the data source's user and password");
         }

         return limit("Could not connect to the R server at " + server + " (" + message + ")");
      }

      for(Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
         if(t.getMessage() != null && t.getMessage().contains("error code")) {
            String rError = readRError(connection);

            if(rError != null) {
               return limit("The R script failed: " + rError);
            }

            break;
         }
      }

      return limit("The R query failed: " + message);
   }

   private static String describeServer(XDataSource dataSource) {
      if(dataSource instanceof RDataSource) {
         RDataSource rds = (RDataSource) dataSource;
         String url = rds.getUrl();
         return (url == null || url.isEmpty() ? "127.0.0.1" : url) + ":" + rds.getPort();
      }

      return "the configured address";
   }

   // R keeps the text of the last error; ask for it only right after a failed eval, as it is
   // stale otherwise. The session may be gone (e.g. quit()), so any failure means no text.
   private static String readRError(RConnection connection) {
      try {
         REXP x = connection.eval("geterrmessage()");

         if(x != null && x.isString()) {
            String text = x.asString();
            return text == null || text.trim().isEmpty() ? null : text;
         }
      }
      catch(Exception ex) {
         LOG.debug("Failed to read the R error message", ex);
      }

      return null;
   }

   private static String limit(String message) {
      String text = String.valueOf(message).replaceAll("\\s+", " ").trim();
      return text.length() > MAX_MESSAGE_LENGTH ?
         text.substring(0, MAX_MESSAGE_LENGTH) + "..." : text;
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

   static final String NO_SCRIPT_MESSAGE =
      "The R query has no script; enter the R script to run (it must return a data frame)";
   private static final int MAX_MESSAGE_LENGTH = 300;
   private static final Logger LOG = LoggerFactory.getLogger(RRuntime.class.getName());
}
