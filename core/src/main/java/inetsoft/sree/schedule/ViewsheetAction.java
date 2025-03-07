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
package inetsoft.sree.schedule;

import com.fasterxml.jackson.databind.JsonNode;
import inetsoft.analytic.composition.VSPortalHelper;
import inetsoft.graph.EGraph;
import inetsoft.graph.VGraph;
import inetsoft.graph.aesthetic.*;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.report.Hyperlink;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.HLColorFrame;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.table.TableHighlightAttr;
import inetsoft.report.io.csv.CSVConfig;
import inetsoft.report.io.viewsheet.*;
import inetsoft.report.io.viewsheet.excel.CSVUtil;
import inetsoft.report.io.viewsheet.excel.CSVVSExporter;
import inetsoft.report.io.viewsheet.html.HTMLVSExporter;
import inetsoft.report.script.viewsheet.ViewsheetScope;
import inetsoft.sree.RepletRequest;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.Mailer;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.security.IdentityID;
import inetsoft.storage.ExternalStorageService;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.*;
import inetsoft.web.viewsheet.service.ExcelVSExporter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.security.Principal;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A schedule action to run a viewsheet. The action could save the
 * viewsheet in a PDF file, deliver it through email.
 *
 * @version 10.3, 5/3/2010
 * @author InetSoft Technology Corp
 */
public class ViewsheetAction extends AbstractAction implements ViewsheetSupport {
   /**
    * Create an empty action.
    */
   public ViewsheetAction() {
      initFormatType();
   }

   /**
    * A viewsheet action for the specified viewsheet.
    * When this action is executed, the viewsheet is created and the result
    * is mailed.
    */
   public ViewsheetAction(String viewsheet, RepletRequest request) {
      this.viewsheet = viewsheet;

      setViewsheetRequest((request == null)
                       ? new RepletRequest() : request);
      initFormatType();
   }

   /**
    * Get the request used to generate the viewsheet.
    */
   public RepletRequest getViewsheetRequest() {
      return vrequest;
   }

   /**
    * Set the request used to generate the viewsheet.
    */
   public void setViewsheetRequest(RepletRequest req) {
      this.vrequest = req;
   }

   /**
    * Get a human readable string representing the viewsheet
    */
   private String toView() {
      AssetEntry viewEntry = getViewsheetEntry();
      return viewEntry.toView();
   }

   /**
    * Get the email file format.
    */
   public CSVConfig getEmailCSVConfig() {
      return this.emailInfo.getCSVConfig();
   }

   /**
    * Get the email file format.
    */
   public void setEmailCSVConfig(CSVConfig config) {
      this.emailInfo.setCSVConfig(config);
   }

   /**
    * Get the viewsheet name of the action.
    */
   @Override
   public String getViewsheetName() {
      return viewsheet;
   }

   /**
    * Set the viewsheet name of the action.
    */
   @Override
   public void setViewsheetName(String name) {
      this.viewsheet = name;
   }

   @Override
   public int getScope() {
      AssetEntry entry0 = AssetEntry.createAssetEntry(getViewsheetName());

      return entry0.getScope();
   }

   @Override
   public RepletRequest getRepletRequest() {
      return vrequest;
   }

   @Override
   public AssetEntry buildAssetEntry(Principal principal) {
      // fix bug1324521198560, keep the vs's scope and user information.
      AssetEntry entry0 = AssetEntry.createAssetEntry(getViewsheetName());
      return new AssetEntry(entry0.getScope(),
         AssetEntry.Type.VIEWSHEET, entry0.getPath(), entry0.getUser(), entry0.getOrgID());
   }

   @Override
   public AssetEntry getViewsheetEntry() {
      AssetEntry entry = buildAssetEntry(null);
      setViewsheetName(entry.toIdentifier());

      return entry;
   }

   public void clearBookmarks() {
      bookmarkTypes = null;
      bookmarkNames = null;
      bookmarkUsers = null;
      bookmarkReadOnly = true;
   }

   /**
    * Parse the replet action definition from xml.
    */
   @Override
   public void parseXML(Element action) throws Exception {
      viewsheet = byteDecode(action.getAttribute("viewsheet"));
      bookmarkReadOnly = "true".equals(action.getAttribute("bookmarkReadOnly"));
      String type = action.getAttribute("bookmarkType");

      NodeList list = Tool.getChildNodesByTagName(action, "Bookmark");

      if(list.getLength() > 0) {
         bookmarkNames = new String[list.getLength()];
         bookmarkTypes = new int[list.getLength()];
         bookmarkUsers = new IdentityID[list.getLength()];

         for(int i = 0; i < list.getLength(); i ++) {
            Element bookmark = (Element) list.item(i);
            String bType = bookmark.getAttribute("type");
            bookmarkNames[i] = bookmark.getAttribute("name");
            bookmarkUsers[i] = IdentityID.getIdentityIDFromKey(bookmark.getAttribute("user"));

            if(!org.apache.commons.lang3.StringUtils.isEmpty(bType)) {
               bookmarkTypes[i] = Integer.parseInt(bType);
            }
         }
      }
      else {
         String bookmarkName = action.getAttribute("bookmarkName");
         IdentityID bookmarkUser = IdentityID.getIdentityIDFromKey(action.getAttribute("bookmarkUser"));
         String bType = action.getAttribute("bookmarkType");
         bookmarkNames = new String[]{bookmarkName};
         bookmarkUsers = new IdentityID[]{bookmarkUser};

         if(!StringUtils.isEmpty(bookmarkName) && !StringUtils.isEmpty(bType)) {
            bookmarkTypes = new int[]{Integer.parseInt(bType)};
         }
      }

      Element node = Tool.getChildNodeByTagName(action, "LinkURI");

      if(node != null) {
         linkURI = Tool.getAttribute(node, "uri");
      }

      list = Tool.getChildNodesByTagName(action, "MailTo");

      if(list.getLength() > 0) {
         emailInfo.parseXML((Element) list.item(0));
      }

      node = Tool.getChildNodeByTagName(action, "Notify");

      if(node != null) {
         notifies = byteDecode(Tool.getAttribute(node, "email"));
         isNotifyError = "true".equals(Tool.getAttribute(node, "onError"));
         link = "true".equals(Tool.getAttribute(node, "link"));
      }

      node = Tool.getChildNodeByTagName(action, "Deliver");

      if(node != null) {
         deliverLink = "true".equals(Tool.getAttribute(node, "deliverLink"));
      }

      node = Tool.getChildNodeByTagName(action, "SaveToServer");

      if(node != null) {
         this.saveToServerMatch = "true".equals(Tool.getAttribute(node, "match"));
         this.saveToServerExpandSelections =
            "true".equals(Tool.getAttribute(node, "expandSelections"));
         this.saveToServerOnlyDataComponents =
            "true".equals(Tool.getAttribute(node, "onlyDataComponents"));
         this.saveExportAllTabbedTables =
            "true".equals(Tool.getAttribute(node, "saveExportAllTabbedTables"));
      }

      list = Tool.getChildNodesByTagName(action, "SaveFile");

      for(int i = 0; i < list.getLength(); i++) {
         Element save = (Element) list.item(i);
         int format = Integer.parseInt(Tool.getAttribute(save, "format"));
         String file = Tool.getAttribute(save, "file");

         if(file == null) {
            ServerPathInfo pathInfo = new ServerPathInfo();
            pathInfo.parseXML(Tool.getChildNodeByTagName(save, "ServerPath"));
            setFilePath(format, pathInfo);
         }
         else {
            file = byteDecode(file);
            setFilePath(format, file);
         }

         if(format == FileFormatInfo.EXPORT_TYPE_CSV) {
            if(saveCSVConfig == null) {
               saveCSVConfig = new CSVConfig();
            }

            Element config = Tool.getChildNodeByTagName(action, "CSVConfig");

            if(config != null) {
               boolean encoding = saveCSVConfig.isEncoding();
               saveCSVConfig.setEncoding(isEncoding());
               saveCSVConfig.parseXML(config);
               saveCSVConfig.setEncoding(encoding);
            }
         }
      }

      NodeList reqlist = Tool.getChildNodesByTagName(action, "Request");
      RepletRequest request = new RepletRequest();

      if(reqlist.getLength() > 0) {
         // as the RepletRequest instance is created dynamically, it should
         // set encoding before parsing xml
         request.setEncoding(encoding);
         request.parseXML((Element) reqlist.item(0));
      }

      setViewsheetRequest(request);

      list = Tool.getChildNodesByTagName(action, "Alert");

      if(list.getLength() == 0) {
         alerts = null;
      }
      else {
         alerts = new ScheduleAlert[list.getLength()];

         for(int i = 0; i < list.getLength(); i++) {
            alerts[i] = new ScheduleAlert();
            alerts[i].parseXML((Element) list.item(i));
         }
      }
   }

   /**
    * Write itself to a xml file
    */
   @Override
   public void writeXML(PrintWriter writer) {
      String bookMarkInfo;
      bookMarkInfo = "\" bookmarkReadOnly=\"" + bookmarkReadOnly;

      writer.println("<Action type=\"Viewsheet\" viewsheet=\"" +
         Tool.escape(byteEncode(viewsheet)) + bookMarkInfo + "\">");
      emailInfo.writeXML(writer);
      String notifications = getNotifications();

      if(bookmarkNames != null) {
         for(int i = 0; i < bookmarkNames.length; i ++) {
            if(!Tool.isEmptyString(bookmarkNames[i])) {
               writer.format("<Bookmark name=\"%s\" type=\"%d\"" + " user=\"%s\"/>%n",
                  Tool.encodeHTMLAttribute(bookmarkNames[i]),
                  bookmarkTypes[i],
                  Tool.encodeHTMLAttribute(bookmarkUsers[i].convertToKey()));
            }
         }
      }

      if(getLinkURI() != null) {
         writer.println("<LinkURI uri=\"" + getLinkURI() + "\"></LinkURI>");
      }

      if(notifications != null) {
         writer.println("<Notify email=\"" + Tool.escape(byteEncode(getNotifications())) +
           "\" onError=\"" + isNotifyError() + "\" link=\"" + isLink() + "\"/>");
      }

      if(getEmails() != null) {
         writer.println("<Deliver deliverLink=\"" + isDeliverLink() + "\"/>");
      }

      writer.println("<SaveToServer match=\"" + this.saveToServerMatch +
         "\" expandSelections=\"" + saveToServerExpandSelections +
         "\" saveExportAllTabbedTables=\"" + saveExportAllTabbedTables +
         "\" onlyDataComponents=\"" + saveToServerOnlyDataComponents + "\"/>");

      int[] formats = getSaveFormats();
      ServerPathInfo[] orders = new ServerPathInfo[formats.length];

      for(int i = 0; i < formats.length; i++) {
         orders[i] = getFilePathInfo(formats[i]);

         if(formats[i] == FileFormatInfo.EXPORT_TYPE_CSV && getSaveCSVConfig() != null) {
            getSaveCSVConfig().writeXML(writer);
         }
      }

      Tool.qsort(orders, true);

      for(ServerPathInfo order : orders) {
         int j;

         for(j = 0; j < formats.length; j++) {
            if(order.equals(filePaths.get(formats[j]))) {
               break;
            }
         }

         writer.println("<SaveFile format=\"" + formats[j] + "\">");
         getFilePathInfo(formats[j]).writeXML(writer);
         writer.println("</SaveFile>");
         formats[j] = -1;
      }

      if(getViewsheetRequest() != null) {
         // as the RepletRequest instance is created dynamically, it should
         // set encoding before writing xml
         getViewsheetRequest().setEncoding(encoding);
         getViewsheetRequest().writeXML(writer);
      }

      ScheduleAlert[] alerts = getAlerts();

      if(alerts != null) {
         for(ScheduleAlert alert : alerts) {
            alert.writeXML(writer);
         }
      }

      writer.println("</Action>");
   }

   /**
    * Compares whether two schedule actions are identical.
    */
   @Override
   public boolean equals(Object val) {
      if(!(val instanceof ViewsheetAction action)) {
         return false;
      }

      if(!(Tool.equals(action.viewsheet, viewsheet) &&
           Tool.equals(action.emailInfo, emailInfo) &&
           Tool.equals(action.getNotifications(), notifies) &&
           Tool.equals(action.isNotifyError(), isNotifyError) &&
           Tool.equals(action.vrequest, vrequest) &&
           Tool.equals(action.bookmarkNames, bookmarkNames) &&
           Tool.equals(action.bookmarkUsers, bookmarkUsers) &&
           Tool.equals(action.bookmarkTypes, bookmarkTypes) &&
           Tool.equals(action.alerts, alerts)))
      {
         return false;
      }

      if(filePaths.size() != action.getSaveFormats().length) {
         return false;
      }

      int[] formats = getSaveFormats();

      for(int format : formats) {
         String file = action.getFilePath(format);

         if(file == null || !Tool.equals(file, getFilePath(format))) {
            return false;
         }
      }

      return true;
   }

   @Override
   public String toString() {
      String str = viewsheet;

      if(str != null) {
         AssetEntry entry = AssetEntry.createAssetEntry(str);
         str = entry != null ? entry.getPath() : str;
      }

      return "ViewsheetAction: " + str;
   }

   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception e) {
         LOG.error("Failed to clone object", e);
      }

      return new ViewsheetAction();
   }

   /**
    * Execute this action.
    * @param principal represents an entity
    */
   @Override
   public void run(Principal principal) throws Throwable {
      if(isCanceled()) {
         return;
      }

      String id = null;
      Principal oldPrincipal = ThreadContext.getContextPrincipal();

      try {
         // @by stephenwebster, For Bug #9554
         // Tool.localizeHeaders uses the context principal to localize header
         // text.
         ThreadContext.setContextPrincipal(principal);
         this.principal = principal;
         id = runViewsheetAction(principal);
      }
      finally {
         closeViewsheet(id, principal);
         ThreadContext.setContextPrincipal(oldPrincipal);
      }
   }

   private List<String> checkAlerts(Principal principal) throws Throwable {
      RuntimeViewsheet rvs = null;

      try {
         if(alerts != null && alerts.length > 0 && bookmarkNames != null &&
            bookmarkNames.length > 0)
         {
            rvs = getRuntimeViewsheet(principal);
            ViewsheetSandbox box;
            Viewsheet vs = rvs.getViewsheet().clone();
            Assembly[] assemblies = vs.getAssemblies();
            List<String> alertTriggeredBookmarks = new ArrayList<>();
            ViewsheetSandbox obox = rvs.getViewsheetSandbox();

            for(int i = 0; i < bookmarkNames.length; i++) {
               int vmode = Viewsheet.SHEET_RUNTIME_MODE;
               box = new ViewsheetSandbox(
                  rvs.getOriginalBookmark(bookmarkNames[i]), vmode, principal, false,
                  rvs.getEntry());

               box.getAssetQuerySandbox().refreshVariableTable(obox.getVariableTable());
               setScheduleParameters(rvs.getVariableTable());
               box.resetAll(new ChangedAssemblyList());

               if(executeViewsheet(box, assemblies)) {
                  continue;
               }

               AssetEntry entry = getViewsheetEntry();
               Map<Assembly, List<ScheduleAlert>> alertAssemblies = new HashMap<>();
               AtomicBoolean alertTriggered = new AtomicBoolean(false);

               for(ScheduleAlert alert : alerts) {
                  boolean found = false;

                  for(Assembly assembly : rvs.getViewsheet().getAssemblies(true)) {
                     if(alert.getElementId().equals(assembly.getAbsoluteName())) {
                        int aType = assembly.getAssemblyType();

                        if((assembly instanceof TextVSAssembly) ||
                           (assembly instanceof ImageVSAssembly))
                        {
                           OutputVSAssemblyInfo info =
                              (OutputVSAssemblyInfo) assembly.getInfo();
                           HighlightGroup group = info.getHighlightGroup();

                           if(containsHighlight(alert, group, alertTriggered)) {
                              found = true;
                              List<ScheduleAlert> alerts =
                                 alertAssemblies.computeIfAbsent(assembly, k -> new ArrayList<>());
                              alerts.add(alert);
                           }
                        }
                        else if(aType == Viewsheet.TABLE_VIEW_ASSET) {
                           TableVSAssemblyInfo info =
                              (TableVSAssemblyInfo) assembly.getInfo();
                           TableHighlightAttr attr = info.getHighlightAttr();

                           if(attr != null) {
                              Enumeration<?> e = attr.getAllHighlights();

                              while(e.hasMoreElements()) {
                                 HighlightGroup group =
                                    (HighlightGroup) e.nextElement();

                                 if(containsHighlight(alert, group, alertTriggered)) {
                                    found = true;
                                    List<ScheduleAlert> alerts =
                                       alertAssemblies.computeIfAbsent(assembly, k -> new ArrayList<>());
                                    alerts.add(alert);
                                 }
                              }
                           }
                        }
                        else if(aType == Viewsheet.CROSSTAB_ASSET) {
                           CrosstabVSAssemblyInfo info =
                              (CrosstabVSAssemblyInfo) assembly.getInfo();
                           TableHighlightAttr attr = info.getHighlightAttr();

                           if(attr != null) {
                              Enumeration<?> e = attr.getAllHighlights();

                              while(e.hasMoreElements()) {
                                 HighlightGroup group =
                                    (HighlightGroup) e.nextElement();

                                 if(containsHighlight(alert, group, alertTriggered)) {
                                    found = true;
                                    List<ScheduleAlert> alerts =
                                       alertAssemblies.computeIfAbsent(assembly, k -> new ArrayList<>());
                                    alerts.add(alert);
                                 }
                              }
                           }
                        }
                        else if(aType == Viewsheet.FORMULA_TABLE_ASSET) {
                           CalcTableVSAssemblyInfo info =
                              (CalcTableVSAssemblyInfo) assembly.getInfo();
                           TableHighlightAttr attr = info.getHighlightAttr();

                           if(attr != null) {
                              Enumeration<?> e = attr.getAllHighlights();

                              while(e.hasMoreElements()) {
                                 HighlightGroup group =
                                    (HighlightGroup) e.nextElement();

                                 if(containsHighlight(alert, group, alertTriggered)) {
                                    found = true;
                                    List<ScheduleAlert> alerts =
                                       alertAssemblies.computeIfAbsent(assembly, k -> new ArrayList<>());
                                    alerts.add(alert);
                                 }
                              }
                           }
                        }
                        else if(aType == Viewsheet.CHART_ASSET) {
                           ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) assembly.getInfo();

                           for(ChartRef ref : info.getVSChartInfo().getBindingRefs(true)) {
                              found = findAlert(alertAssemblies, alert, found, assembly, ref,
                                                alertTriggered);
                           }

                           for(AestheticRef aref : info.getVSChartInfo().getAestheticRefs(true)) {
                              found = findAlert(alertAssemblies, alert, found, assembly,
                                                (ChartRef) aref.getDataRef(), alertTriggered);
                           }
                        }
                        else {
                           VSAssemblyInfo info =
                              (VSAssemblyInfo) assembly.getInfo();

                           if(info instanceof RangeOutputVSAssemblyInfo range) {
                              if(alert.getHighlightName()
                                 .matches("^RangeOutput_Range_\\d+$")) {
                                 int index = Integer.parseInt(
                                    alert.getHighlightName().substring(18)) - 1;

                                 if(index < range.getRangeValues().length) {
                                    found = true;
                                    List<ScheduleAlert> alerts =
                                       alertAssemblies.computeIfAbsent(assembly, k -> new ArrayList<>());
                                    alerts.add(alert);
                                 }
                              }
                           }
                        }

                        break;
                     }
                  }

                  if(!found) {
                     throw new Exception(
                        "Did not find alert highlight named \"" +
                           alert.getHighlightName() + "\" in assembly \"" +
                           alert.getElementId() + "\""
                     );
                  }
               }

               alertTriggered = new AtomicBoolean(false);
               AlertExporter exporter = new AlertExporter(alertAssemblies, alertTriggered, box);
               exporter.setAssetEntry(rvs.getEntry());
               AssetQuerySandbox abox = box.getAssetQuerySandbox();

               if(abox != null) {
                  abox.refreshVariableTable(box.getVariableTable());
               }

               exporter.setLogExecution(true);
               exporter.setLogExport(false);
               exporter.export(box, bookmarkNames[i], new VSPortalHelper());
               exporter.write();
               box.dispose();

               if(alertTriggered.get()) {
                  alertTriggeredBookmarks.add(bookmarkNames[i]);
               }
            }

            return alertTriggeredBookmarks;
         }
      }
      finally {
         if(rvs != null) {
            closeViewsheet(rvs.getID(), principal);
         }
      }

      return null;
   }

   private boolean findAlert(Map<Assembly, List<ScheduleAlert>> alertAssemblies,
                             ScheduleAlert alert, boolean found, Assembly assembly,
                             ChartRef ref, AtomicBoolean alertTriggered)
   {
      if(!(ref instanceof HighlightRef)) {
         return false;
      }

      boolean containsHL = ((HighlightRef) ref).highlights()
         .anyMatch(group -> containsHighlight(alert, group, alertTriggered));

      if(containsHL) {
         found = true;
         List<ScheduleAlert> alerts =
            alertAssemblies.computeIfAbsent(assembly, k -> new ArrayList<>());
         alerts.add(alert);
      }

      return found;
   }

   private String runViewsheetAction(Principal principal) throws Throwable {
      List<Throwable> vec = new ArrayList<>(1);
      Catalog catalog = Catalog.getCatalog(principal);
      Mailer mailer = new Mailer();
      String vname = toView();
      OutputStream out = null;
      File tmpFile = null;

      try {
         RepletRequest repletRequest = getRepletRequest();

         if(repletRequest != null) {
            repletRequest.executeParameter();
         }

         List<String> alertTriggeredBookmarks = checkAlerts(principal);

         // alertTriggeredBookmarks will be null when there are no alerts set
         // if it's empty then that means there are no bookmarks that pass the highlight conditions
         if(alertTriggeredBookmarks != null && alertTriggeredBookmarks.isEmpty()) {
            return null;
         }

         RuntimeViewsheet rvs = getRuntimeViewsheet(principal);

         if(rvs == null) {
            return null;
         }

         // set id here so that we can cancel during the execution
         id = rvs.getID();

         if(isCanceled()) {
            return id;
         }

         StringBuilder status = new StringBuilder();
         Viewsheet vs = rvs.getViewsheet();
         Assembly[] assemblies = vs.getAssemblies();
         ViewsheetSandbox box = rvs.getViewsheetSandbox();
         AssetEntry entry = getViewsheetEntry();
         Object[] msgParams = {entry.getName(), new Date(), (principal != null) ?
            IdentityID.getIdentityIDFromKey(principal.getName()).getName() : ""};
         FileSystemService fileSystemService = FileSystemService.getInstance();

         if(executeViewsheet(box, assemblies)) {
            return id;
         }

         if(!getScheduleEmails(box).isEmpty()) {
            if(isCanceled()) {
               return id;
            }

            File tmpDir = null;

            try {
               String subject = getSubject();

               if(subject == null || subject.isEmpty()) {
                  subject = SreeEnv.getProperty("mail.subject.format",
                     catalog.getString("Scheduled Viewsheet") +
                     " " + vname);
               }

               try {
                  subject = MessageFormat.format(subject, msgParams);
               }
               catch(IllegalArgumentException iae) {
                  // ignore it
                  LOG.error("Failed to format email subject " +
                     subject + " using parameters: " +
                     Arrays.toString(msgParams), iae);
               }

               String attName = getAttachmentName();
               Principal user = ThreadContext.getContextPrincipal();
               Catalog catalog0 = Catalog.getCatalog(user, Catalog.REPORT);
               attName = attName == null ? "default" :
                  catalog0.getString(attName, msgParams);
               attName = getPath(attName, msgParams);
               int type = getFileType();
               // @by stephenwebster, fix bug1395938669865
               // putting the export files in a unique folder allows us to
               // use the same name of the attachment, and safeguards against
               // sending the user an incorrect export file.
               String uuid =  UUID.randomUUID().toString();
               String dir = fileSystemService.getCacheDirectory() + File.separator + uuid;
               tmpDir = fileSystemService.getFile(dir);

               if(!tmpDir.mkdir()) {
                  LOG.warn("Failed to create temporary directory: {}", tmpDir);
               }

               try {
                  attName = MessageFormat.format(attName, msgParams);
               }
               catch(IllegalArgumentException iae) {
                  // ignore it
                  LOG.error("Failed to format attachment name " +
                     attName + " using parameters " +
                     Arrays.toString(msgParams), iae);
               }

               // @by ChrisSpagnoli bug1428646365402 2015-4-10
               // Filter disallowed characters out from Excel file name
               final Set<String> replaceValues = new LinkedHashSet<>(
                  Arrays.asList(":", "*", "[", "]", "/", "?", "__", "|"));
               StringBuilder sb = new StringBuilder(attName);

               for(String rv:replaceValues) {
                  int start = sb.indexOf(rv);

                  while(start != -1) {
                     sb.replace(start, start + rv.length(), "_");
                     start = sb.indexOf(rv);
                  }
               }

               String fname = sb.toString();
               String suffix = getFileExtend(type);
               File file = fileSystemService.getFile(dir, fname + "." + suffix);

               String zipFileName = dir + File.separator + fname + ".zip";
               out = new FileOutputStream(file);
               VSExporter exporter = getVSExporter(type, out,
                  type == FileFormatInfo.EXPORT_TYPE_CSV ? getEmailCSVConfig() : null);
               exporter.setMatchLayout(isMatchLayout());
               exporter.setExpandSelections(isExpandSelections());
               exporter.setOnlyDataComponents(isOnlyDataComponents() && !isMatchLayout());
               exporter.setAssetEntry(rvs.getEntry());

               if(exporter instanceof ExcelVSExporter) {
                  ((ExcelVSExporter) exporter)
                     .setExportAllTabbedTables(isExportAllTabbedTables());
               }

               AssetQuerySandbox abox = box.getAssetQuerySandbox();

               if(abox != null) {
                  abox.refreshVariableTable(box.getVariableTable());
               }

               String password = isUseCredential() ?
                  getDecryptedPassword(getSecretId()) : getPassword();

               if(exporter instanceof EncryptedCompressExporter) {
                  ((EncryptedCompressExporter) exporter).setPassword(password);
               }

               boolean excelToCSV = false;

               if(type == FileFormatInfo.EXPORT_TYPE_EXCEL) {
                  excelToCSV = CSVUtil.hasLargeDataTable(rvs);
               }

               getEmailCSVConfig().getExportAssemblies();
               exporter.setLogExecution(true);
               exporter.setLogExport(true);
               exporter.setSandbox(box);

               exportBookmarks(exporter, rvs, box.getVariableTable(), alertTriggeredBookmarks);

               exporter.write();
               out.flush();
               out.close();
               password = PasswordEncryption.isFipsCompliant() ? null : password;

               if(excelToCSV) {
                  out = new FileOutputStream(zipFileName);
                  VSExporter csv = getVSExporter(FileFormatInfo.EXPORT_TYPE_CSV, out,
                     getEmailCSVConfig());
                  csv.setMatchLayout(false);
                  csv.setExpandSelections(false);
                  csv.setOnlyDataComponents(isOnlyDataComponents());
                  csv.setAssetEntry(rvs.getEntry());

                  if(csv instanceof CSVVSExporter) {
                     ((CSVVSExporter) csv).setExcelFile(file);
                  }

                  exportBookmarks(csv, rvs, box.getVariableTable(), alertTriggeredBookmarks);

                  csv.write();
                  file = fileSystemService.getFile(Tool.convertUserFileName(zipFileName));
               }
               else if((isCompressFile() && type != FileFormatInfo.EXPORT_TYPE_CSV) &&
                  Tool.zipFiles(new String[] { file.getPath() }, zipFileName, true, password))
               {
                  boolean removed = file.delete();

                  if(!removed) {
                     fileSystemService.remove(file, 6000);
                  }

                  file = fileSystemService.getFile(Tool.convertUserFileName(zipFileName));
               }

               ScheduleMailService mailService;

               if(SreeEnv.getProperty("schedule.mail.service", "").isEmpty()) {
                  mailService = new DefaultScheduleMailService();
               }
               else {
                  try {
                     mailService = (ScheduleMailService)
                        Class.forName(SreeEnv.getProperty("schedule.mail.service"))
                           .getConstructor().newInstance();
                  }
                  catch(Exception ex) {
                     LOG.error("Failed to instantiate a custom schedule mail service: " + SreeEnv
                           .getProperty("schedule.mail.service") +
                           ". Please specify a valid class name in 'schedule.mail.service'");
                     mailService = new DefaultScheduleMailService();
                  }
               }

               ArrayList<String> images = null;
               String body = getMessage();

               if(FileFormatInfo.EXPORT_TYPE_PNG == type) {
                  final File pngFile = file;
                  final File htmlFile = fileSystemService.getFile(dir, fname + "." + "html");
                  final FileOutputStream output = new FileOutputStream(htmlFile);
                  final OutputStreamWriter outputStreamWriter = new OutputStreamWriter(output);

                  try(PrintWriter htmlWriter = new PrintWriter(outputStreamWriter)) {
                     if(isDeliverLink()) {
                        htmlWriter.write("<a href=\"" + getLinkURL(principal) + "\" >");
                        htmlWriter.write("<img src=\"cid:" + pngFile.getName() + "\" /></a>");
                     }
                     else {
                        htmlWriter.write("<img src=\"cid:" + pngFile.getName() + "\" />");
                     }
                  }

                  images = new ArrayList<>();
                  images.add(pngFile.getName());
                  file = htmlFile;
               }

               String ccAddress = getEmails(getCCAddresses());
               ccAddress = StringUtils.isEmpty(ccAddress) ? null : ccAddress;
               String bccAddress = getEmails(getBCCAddresses());
               bccAddress = StringUtils.isEmpty(bccAddress) ? null
                  : bccAddress;
               body = isDeliverLink() ? addIncludeLink(body, principal) : body;

               if(isCanceled()) {
                  return id;
               }

               IdentityID pId = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());
               mailService.emailViewsheet(principal, rvs, getFileFormat(),
                  isCompressFile(), getEmails(getScheduleEmails(box)), getFrom(pId), ccAddress,
                  bccAddress, subject, file, images, body,
                  isMessageHtml() || images != null || isDeliverLink());
               status.append(catalog.getString("common.viewsheetMailedParam",
                  vname, getEmails(getScheduleEmails(box))));
            }
            catch(Exception ex) {
               if(isCanceled()) {
                  return id;
               }

               String failureMessage = "Failed to email viewsheet {} to {}";
               String viewsheetID = rvs.getID();
               String emails = getEmails(getScheduleEmails(box));

               if(LOG.isDebugEnabled()) {
                  LOG.error(failureMessage, viewsheetID, emails, ex);
               }
               else {
                  LOG.error(failureMessage, viewsheetID, emails);
               }

               vec.add(ex);
            }
            finally {
               Tool.deleteFile(tmpDir);
            }
         }

         int[] formats = getSaveFormats();

         for(int format : formats) {
            if(isCanceled()) {
               return id;
            }

            if(!vec.isEmpty()) {
            //by nickgovus, 2023.11.7, Bug #62873, skip save to server if exception exists
               continue;
            }

            File tmpDir = null;
            ServerPathInfo pathInfo = getFilePathInfo(format);
            String path = getPath(pathInfo.getPath(), msgParams);
            boolean excelToCSV = false;

            if(format == FileFormatInfo.EXPORT_TYPE_EXCEL) {
               excelToCSV = CSVUtil.hasLargeDataTable(rvs);
            }

            try {
               String suffix = SreeEnv.getProperty("schedule.save.autoSuffix");

               try {
                  path = MessageFormat.format(path, msgParams);
               }
               catch(IllegalArgumentException iae) {
                  LOG.error("Failed to format path " + path +
                     " using parameters " + Arrays.toString(msgParams), iae);
               }

               if(suffix != null) {
                  try {
                     suffix = MessageFormat.format(suffix, msgParams);
                  }
                  catch(IllegalArgumentException iae) {
                     LOG.error("Failed to format path " + suffix +
                               " using parameters " + Arrays.toString(msgParams), iae);
                  }
               }

               int idx = path.indexOf("?");
               boolean append = false;

               if(pathInfo.isSFTP() && idx != -1 && path.length() > idx + 1) {
                  String queryParam = path.substring(idx + 1);
                  HashMap<String, String> map = Util.getQueryParamMap(queryParam);
                  append = "true".equals(map.get("append"));
                  path = path.substring(0, idx);
               }

               StringBuilder pathBuilder = new StringBuilder(path);

               if(suffix != null && !suffix.trim().isEmpty()) {
                  pathBuilder.append(Tool.normalizeFileName(suffix.trim()));
               }

               pathBuilder.append('.').append(getFileExtend(format));
               String str = pathBuilder.toString();

               try {
                  str = MessageFormat.format(str, msgParams);
               }
               catch(IllegalArgumentException iae) {
                  // ignore it
                  LOG.error("Failed to format path " + str +
                     " using parameters " + Arrays.toString(msgParams), iae);
               }

               str = Util.getNonexistentFilePath(str, 1);

               File file;
               File zipFile = null;

               String uuid =  UUID.randomUUID().toString();
               String dir = fileSystemService.getCacheDirectory() + File.separator + uuid;
               tmpDir = fileSystemService.getFile(dir);
               int index = Tool.replaceAll(str, "\\", "/").lastIndexOf("/");
               String fileName = str.substring(index + 1);

               if(!tmpDir.mkdir()) {
                  LOG.warn(
                     "Failed to create temporary directory: " + tmpDir);
               }

               file = fileSystemService.getFile(dir, fileName);

               if(excelToCSV) {
                  zipFile = fileSystemService.getFile(dir,
                     fileName.replace(".xlsx", ".zip"));
               }

               if(excelToCSV) {
                  zipFile = fileSystemService.getFile(
                     str.replace(".xlsx", ".zip"));
               }

               tmpFile = FileSystemService.getInstance().getCacheTempFile("taskExport", "zip");
               out = new FileOutputStream(tmpFile);
               VSExporter exporter = getVSExporter(format, out,
                  getSaveCSVConfig() == null ? null : getSaveCSVConfig());
               exporter.setMatchLayout(this.saveToServerMatch &&
                  format != FileFormatInfo.EXPORT_TYPE_CSV);
               exporter.setExpandSelections(this.saveToServerExpandSelections);
               exporter.setOnlyDataComponents(this.saveToServerOnlyDataComponents &&
                  !this.saveToServerMatch);

               if(exporter instanceof ExcelVSExporter) {
                  ((ExcelVSExporter) exporter).setExportAllTabbedTables(
                     isSaveExportAllTabbedTables());
               }

               exporter.setAssetEntry(rvs.getEntry());
               AssetQuerySandbox abox = box.getAssetQuerySandbox();

               if(abox != null) {
                  abox.refreshVariableTable(box.getVariableTable());
               }

               exporter.setLogExecution(true);
               exporter.setLogExport(true);

               exportBookmarks(exporter, rvs, abox.getVariableTable(), alertTriggeredBookmarks);

               exporter.write();
               out.flush();
               out.close();

               if(!tmpFile.renameTo(file)) {
                  throw new RuntimeException("Failed to save file: \"" + file.getPath() + "\"");
               }

               if(excelToCSV) {
                  OutputStream zout = new FileOutputStream(zipFile);
                  VSExporter csv = getVSExporter(FileFormatInfo.EXPORT_TYPE_CSV, zout, getSaveCSVConfig());
                  csv.setMatchLayout(false);
                  csv.setExpandSelections(false);
                  csv.setOnlyDataComponents(isOnlyDataComponents());
                  csv.setAssetEntry(rvs.getEntry());

                  if(csv instanceof CSVVSExporter) {
                     ((CSVVSExporter) csv).setExcelFile(file);
                  }

                  exportBookmarks(csv, rvs, box.getVariableTable(), alertTriggeredBookmarks);

                  csv.write();
                  file = fileSystemService.getFile(Tool.convertUserFileName(zipFile.getName()));
                  zout.close();
               }

               if(isCanceled()) {
                  return id;
               }

               if(pathInfo.isFTP()) {
                  FTPUtil.uploadToFTP(str, file, pathInfo, append);
               }
               else {
                  ExternalStorageService.getInstance().write(str, file.toPath(), principal);
               }
            }
            catch(Throwable ex) {
               if(isCanceled()) {
                  return id;
               }

               LOG.error("Failed to save viewsheet " + rvs.getID(), ex);
               vec.add(ex);

               if(out != null){
                  out.close();

                  if(tmpFile != null){
                     tmpFile.delete();
                  }
               }
            }
            finally {
               Tool.deleteFile(tmpDir);
            }
         }

         //by nickgovus, 2023.11.7, Bug #62873, skip send notification email if exception exists
         if(vec.isEmpty() && !StringUtils.isEmpty(notifies)) {
            if(isCanceled()) {
               return id;
            }

            String subject =
               SreeEnv.getProperty("mail.notification.subject.format",
               catalog.getString("em.scheduler.notification.note4", vname));

            try {
               subject = MessageFormat.format(subject, msgParams);
            }
            catch(IllegalArgumentException iae) {
               // ignore it
               LOG.error("Failed to format notification email subject " + subject +
                  " using parameters " + Arrays.toString(msgParams), iae);
            }

            if(status.toString().isEmpty()) {
               status.append(
                  catalog.getString("em.scheduler.notification.note5"));
            }

            if(!isNotifyError()) {
               String body = isLink() ? addIncludeLink(status.toString(), principal) :
                  status.toString();
               mailer.send(getEmails(notifies), null, null,
                  null, subject, body, null, isLink());
            }
         }

         if(!vec.isEmpty()) {
            throw vec.getFirst();
         }
      }
      catch(Exception ex) {
         if(isCanceled()) {
            return id;
         }

         LOG.error("Failed to send notification emails for viewsheet " + vname +
            " to " + notifies, ex);

         String sub = catalog.getString("em.scheduler.notification.failedSub1",
            vname);
         String body = catalog.getString(
            "em.scheduler.notification.failedBody3", ex) + "\n\n" +
            catalog.getString("em.scheduler.notification.failedBody2");

         if(notifies != null && !notifies.isEmpty()) {
            body = isLink() ? addIncludeLink(body, principal) : body;
            mailer.send(getEmails(notifies), null, null, getFrom(),
                    sub, body, null, isLink());
         }

         throw ex;
      }

      return id;
   }

   private void exportBookmarks(VSExporter exporter, RuntimeViewsheet rvs,
                                VariableTable variableTable, List<String> alertTriggeredBookmarks)
      throws Exception
   {
      int vmode = Viewsheet.SHEET_RUNTIME_MODE;

      for(int i = 0; i < bookmarkNames.length; i ++) {
         String bookmarkName = bookmarkNames[i];

         // ignore bookmarks that didn't pass the highlight condition
         if(alertTriggeredBookmarks != null && !alertTriggeredBookmarks.contains(bookmarkName)) {
            continue;
         }

         if(!Tool.equals(bookmarkUsers[i].getName(), XUtil.getUserName(principal))) {
            bookmarkName += "(" + bookmarkUsers[i].getName() + ")";
         }

         setScheduleParameters(variableTable);
         ViewsheetSandbox box = new ViewsheetSandbox(
            rvs.getOriginalBookmark(bookmarkName), vmode, principal, false,
            rvs.getEntry());
         AssetQuerySandbox assetQuerySandbox = box.getAssetQuerySandbox();

         if(assetQuerySandbox != null) {
            assetQuerySandbox.refreshVariableTable(variableTable);
         }

         box.resetAll(new ChangedAssemblyList());

         if(exporter instanceof AbstractVSExporter) {
            ((AbstractVSExporter) exporter).setRuntimeViewsheet(rvs);
         }

         exporter.export(box, bookmarkName, new VSPortalHelper());
         box.dispose();

         // Bug #61272
         if(exporter instanceof HTMLVSExporter) {
            break;
         }
      }
   }

   /**
    * Add the link to the viewsheet to the given string.
    * @param body the body string to add link to
    * @param principal the user
    * @return completed string with link
    */
   private String addIncludeLink(String body, Principal principal) throws Throwable {
      Catalog catalog = Catalog.getCatalog(principal);
      RuntimeViewsheet rvs = getRuntimeViewsheet(principal);
      AssetEntry entry = getViewsheetEntry();

      String linkUri = getLinkURI();
      boolean bookmark = (getBookmarks() != null && getBookmarks().length > 0);

      if(linkUri != null && entry.getScope() != AssetRepository.TEMPORARY_SCOPE) {
         String url = getLinkURL(principal);
         StringBuilder includeBookMark = new StringBuilder();
         String includeLink;

         if(body == null) {
            body = "";
         }

         body += "<br>" + catalog.getString("common.mail.link.description.dashboard") + "<br>";

         IdentityID currUser = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());

         if(bookmark) {
            String bookmarkName = getBookmarks()[0];
            int bookmarkType = getBookmarkTypes()[0];
            IdentityID bookmarkUser = getBookmarkUsers()[0];

            if(VSBookmarkInfo.PRIVATE != bookmarkType &&
               VSBookmarkInfo.GROUPSHARE == bookmarkType &&
               !rvs.isSameGroup(bookmarkUser, currUser))
            {
               String wrapUrl = url + "&bookmarkName=" +
                  Tool.encodeWebURL(bookmarkName) + "&bookmarkUser=" +
                  Tool.encodeWebURL(bookmarkUser.convertToKey());
               includeBookMark.append("<a href=\"").append(wrapUrl).append("\">")
                  .append(bookmarkName).append("</a><br>");
            }
         }

         includeLink = "<a href=\"" + url + "\">" + entry.getPath() + "</a>";

         body += bookmark && !includeBookMark.toString().isEmpty() ? includeBookMark : includeLink;
      }

      return body;
   }

   /**
    * Get the full link URL for linking.
    * @param principal the user
    * @return the link url as a string
    */
   private String getLinkURL(Principal principal) throws Throwable {
      AssetEntry entry = getViewsheetEntry();
      RuntimeViewsheet rvs = getRuntimeViewsheet(principal);
      StringBuilder url = new StringBuilder().append(getLinkURI()).append("app/viewer/view/");

      if(rvs.getEntry().getScope() == AssetRepository.USER_SCOPE) {
         url.append("user/").append(entry.getUser().getName()).append('/');
      }
      else {
         url.append("global/");
      }

      url.append(entry.getPath());

      return url.toString();
   }

   /**
    * Determines if the specified highlight group contains the highlight that
    * matches an alert.
    *
    * @param alert     the alert to check.
    * @param highlight the highlight condition to check.
    *
    * @return <tt>true</tt> if the highlight group matches; <tt>false</tt>
    *         otherwise.
    */
   private boolean containsHighlight(final ScheduleAlert alert, HighlightGroup highlight,
                                     AtomicBoolean alertTriggered)
   {
      boolean found = false;

      if(highlight != null) {
         for(String level : highlight.getLevels()) {
            for(String name : highlight.getNames(level)) {
               if(alert.getHighlightName().equals(name)) {
                  highlight.addHighlightAppliedListener(
                     event -> {
                        if(alert.getHighlightName().equals(event.getName()) &&
                           alertTriggered != null) {
                           alertTriggered.set(true);
                        }
                     });

                  found = true;
                  break;
               }
            }
         }
      }

      return found;
   }

   protected int getFileType() {
      return Integer.parseInt(formatMap.get(getFileFormat()).toString());
   }

   protected VSExporter getVSExporter(int type, OutputStream out) {
      return AbstractVSExporter.getVSExporter(type,
         PortalThemesManager.getColorTheme(), out);
   }

   protected VSExporter getVSExporter(int type, OutputStream out, Object extraConfig) {
      return AbstractVSExporter.getVSExporter(type,
         PortalThemesManager.getColorTheme(), out, false, extraConfig);
   }

   /**
    * Execute the viewsheet script.
    * @param box the viewsheet sandbox.
    * @param assemblies the viewsheet assemblies.
    * @return true if the task is canceled.
    */
   private boolean executeViewsheet(ViewsheetSandbox box, Assembly[] assemblies) throws Exception {
      if(box == null) {
         return false;
      }

      ViewsheetScope scope = box.getScope();

      for(Assembly assembly : assemblies) {
         scope.getVSAScriptable(assembly.getName());

         if(assembly.getInfo() instanceof VSAssemblyInfo vinfo) {
            String script = vinfo.getScript();

            if(vinfo.isScriptEnabled() && script != null && !script.isEmpty()) {
               // fix bug1293441936687, make sure the egraph and dataset defined
               // in script are executed when run task
               if(assembly instanceof ChartVSAssembly) {
                  box.getVGraphPair(assembly.getName());
               }

               scope.execute(script, assembly.getName());

               if(!box.isScheduleAction()) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   /**
    * Init the format type.
    */
   private void initFormatType() {
      if(formatMap == null) {
         formatMap = new HashMap<>();

         formatMap.put(FileFormatInfo.EXPORT_NAME_EXCEL,
            FileFormatInfo.EXPORT_TYPE_EXCEL);
         formatMap.put(FileFormatInfo.EXPORT_NAME_POWERPOINT,
            FileFormatInfo.EXPORT_TYPE_POWERPOINT);
         formatMap.put(FileFormatInfo.EXPORT_NAME_PDF,
            FileFormatInfo.EXPORT_TYPE_PDF);
         formatMap.put(FileFormatInfo.EXPORT_NAME_PNG,
               FileFormatInfo.EXPORT_TYPE_PNG);
         formatMap.put(FileFormatInfo.EXPORT_NAME_HTML,
               FileFormatInfo.EXPORT_TYPE_HTML);
         formatMap.put(FileFormatInfo.EXPORT_NAME_CSV,
            FileFormatInfo.EXPORT_TYPE_CSV);
      }
   }

   /**
    * Get the file extend.
    */
   protected String getFileExtend(int format) throws RuntimeException {
      return ExportUtil.getSuffix(format);
   }

   /**
    * If match the layout.
    * @return selection of match layout.
    */
   public boolean isMatchLayout() {
      if(!Tool.isEmptyString(getFileFormat()) && getFileType() == FileFormatInfo.EXPORT_TYPE_CSV) {
         return false;
      }

      return emailInfo.isMatchLayout();
   }

   /**
    * Set the match lay out.
    */
   public void setMatchLayout(boolean matched) {
      emailInfo.setMatchLayout(matched);
   }

   public boolean isExpandSelections() {
      return emailInfo.isExpandSelections();
   }

   public boolean isOnlyDataComponents() {
      return emailInfo.isOnlyDataComponents();
   }

   public void setExpandSelections(boolean expandSelections) {
      emailInfo.setExpandSelections(expandSelections);
   }

   public void setOnlyDataComponents(boolean onlyDataComponents) {
      emailInfo.setOnlyDataComponents(onlyDataComponents);
   }


   public boolean isExportAllTabbedTables() {
      return emailInfo.isExportAllTabbedTables();
   }

   public void setExportAllTabbedTables(boolean exportAllTabbedTables) {
      emailInfo.setExportAllTabbedTables(exportAllTabbedTables);
   }

   public boolean isSaveToServerMatch() {
      return saveToServerMatch;
   }

   public void setSaveToServerMatch(boolean matched) {
      this.saveToServerMatch = matched;
   }

   public boolean isSaveToServerExpandSelections() {
      return saveToServerExpandSelections;
   }

   public void setSaveToServerExpandSelections(boolean saveToServerExpandSelections) {
      this.saveToServerExpandSelections = saveToServerExpandSelections;
   }

   public boolean isSaveToServerOnlyDataComponents() {
      return saveToServerOnlyDataComponents;
   }

   public void setSaveToServerOnlyDataComponents(boolean saveOnlyData) {
      this.saveToServerOnlyDataComponents = saveOnlyData;
   }

   public boolean isSaveExportAllTabbedTables() {
      return saveExportAllTabbedTables;
   }

   public void setSaveExportAllTabbedTables(boolean saveExportAllTabbedTables) {
      this.saveExportAllTabbedTables = saveExportAllTabbedTables;
   }

   public String getViewsheet() {
      return viewsheet;
   }

   public void setViewsheet(String viewsheet) {
      this.viewsheet = viewsheet;
   }

   public String[] getBookmarks() {
      return bookmarkNames;
   }

   public void setBookmarks(String[] bookmarks) {
      this.bookmarkNames = bookmarks;
   }

   public IdentityID[] getBookmarkUsers() {
      return bookmarkUsers;
   }

   public void setBookmarkUsers(IdentityID[] bookmarkUsers) {
      this.bookmarkUsers = bookmarkUsers;
   }

   public void setIsBookmarkReadOnly(boolean isReadOnly) {
      this.bookmarkReadOnly = isReadOnly;
   }

   public Boolean isBookmarkReadOnly() {
      return this.bookmarkReadOnly;
   }

   public int[] getBookmarkTypes() {
      return bookmarkTypes;
   }

   public void setBookmarkTypes(int[] types) {
      this.bookmarkTypes = types;
   }

   public String getScheduleEmails(ViewsheetSandbox box) {
      if(box == null) {
         return getEmails();
      }

      ScheduleInfo sinfo = box.getScheduleInfo();

      if(sinfo == null || sinfo.getEmails() == null) {
         return getEmails();
      }

      return sinfo.getEmails();
   }

   public CSVConfig getSaveCSVConfig() {
      return saveCSVConfig;
   }

   public void setSaveCSVConfig(CSVConfig saveCSVConfig) {
      this.saveCSVConfig = saveCSVConfig;
   }

   /**
    * Get path.
    * @param path the path.
    */
   private String getPath(String path, Object[] msgParams) throws RuntimeException {
      if(!path.contains("{") && !path.contains("}")) {
         return path;
      }

      if(!Tool.isBracketPaired(path)) {
         throw new RuntimeException(Catalog.getCatalog().getString(
            "Brackets do not match"));
      }

      int i = 0;
      StringBuilder newPath = new StringBuilder();

      while(i < path.length()) {
         int startIndex = path.indexOf("{", i);

         if(startIndex == -1) {
            newPath.append(path.substring(i));
            break;
         }

         newPath.append(path, i, startIndex);
         int endIndex = path.indexOf("}", startIndex++);
         i = endIndex + 1;
         String key = path.substring(startIndex, endIndex);

         if(key.equals("0") || key.startsWith("1,")) {
            newPath.append("{").append(key).append("}");
            continue;
         }
         else if(Tool.equals("1", key)) {
            String suffix = "{" + key + "}";

            try {
               suffix = MessageFormat.format("{" + key + "}", msgParams);
            }
            catch(IllegalArgumentException iae) {
               LOG.error("Failed to format path " + suffix +
                  " using parameters " + Arrays.toString(msgParams), iae);
            }

            newPath.append(Tool.normalizeFileName(suffix));
            continue;
         }

         Object value = getViewsheetRequest().getParameter(key);

         if(value == null) {
            throw new RuntimeException(Catalog.getCatalog().getString(
               "Parameter not found", "'" + key + "'"));
         }

         String valueStr = (value instanceof Date) ?
            (new SimpleDateFormat("yyyy-MM-dd hh-mm-ss")).format(value) :
            value.toString();

         newPath.append(Tool.normalizeFileName(valueStr));
      }

      return newPath.toString();
   }

   /**
    * Sets the file path to which to save the file in a particular format.
    *
    * @param format the format in which to save. Must be a format
    * from inetsoft.uql.viewsheet.FileFormatInfo
    *
    * @param path the file path.
    */
   public void setFilePath(int format, String path) {
      if(path == null) {
         filePaths.remove(format);
      }
      else {
         ServerPathInfo info = new ServerPathInfo(path);
         filePaths.put(format, info);
      }
   }

   /**
    * Sets the file path to which to save the file in a particular format.
    *
    * @param format the format in which to save. Must be a format
    * from inetsoft.uql.viewsheet.FileFormatInfo
    *
    * @param info the file path info.
    */
   public void setFilePath(int format, ServerPathInfo info) {
      if(info == null || info.getPath() == null) {
         filePaths.remove(format);
      }
      else {
         filePaths.put(format, info);
      }
   }

   /**
    * Gets the file path at which the file will be saved for a particular
    * format.
    *
    * @param format the format for which to get the file path. Must be a format
    * from inetsoft.uql.viewsheet.FileFormatInfo
    *
    * @return the file path or <code>null</code> if the file will not be saved
    *         in the specified format.
    */
   public String getFilePath(int format) {
      return filePaths.get(format).getPath();
   }

   /**
    * Gets the file path at which the file will be saved for a particular
    * format.
    *
    * @param format the format for which to get the file path. Must be a format
    * from inetsoft.uql.viewsheet.FileFormatInfo
    *
    * @return the file path info or <code>null</code> if the file will not be saved
    *         in the specified format.
    */
   public ServerPathInfo getFilePathInfo(int format) {
      return filePaths.get(format);
   }

   /**
    * Gets a list of all the formats in which the viewsheet will be saved.
    *
    * @return an array of format types.
    */
   public int[] getSaveFormats() {
      int[] formats = new int[filePaths.size()];
      Iterator<Integer> iter = filePaths.keySet().iterator();

      for(int i = 0; iter.hasNext(); i++) {
         formats[i] = iter.next();
      }

      return formats;
   }

   /**
    * Gets the alert conditions for this action.
    *
    * @return the alert conditions.
    */
   public ScheduleAlert[] getAlerts() {
      return alerts;
   }

   /**
    * Sets the alert conditions for this action.
    *
    * @param alerts the alert conditions.
    */
   public void setAlerts(ScheduleAlert[] alerts) {
      this.alerts = alerts;
   }

   @Override
   public void cancel() {
      super.cancel();

      if(id != null) {
         closeViewsheet(id, principal);
      }
   }

   private void setScheduleParameters(VariableTable vars) {
      if(vars == null) {
         return;
      }

      RepletRequest repletRequest = getRepletRequest();

      if(repletRequest != null) {
         Enumeration<String> paramNames = repletRequest.getParameterNames();

         while(paramNames.hasMoreElements()) {
            String name = paramNames.nextElement();
            vars.put(name, repletRequest.getParameter(name));
         }
      }
   }

   private String getDecryptedPassword(String secretId) {
      return EmailInfo.getDecryptedPassword(secretId);
   }

   /**
    * Viewsheet exporter that executes a viewsheet to determine if the alert
    * conditions have been met.
    *
    * @author InetSoft Technology
    * @since  12.0
    */
   private static final class AlertExporter extends AbstractVSExporter {
      /**
       * Creates a new instance of <tt>AlertExporter</tt>.
       *
       * @param alertAssemblies the alert conditions.
       * @param alertTriggered  the flag that is set if a highlight is applied.
       */
      public AlertExporter(Map<Assembly, List<ScheduleAlert>> alertAssemblies,
                           AtomicBoolean alertTriggered, ViewsheetSandbox box)
      {
         this.alertAssemblies = alertAssemblies;
         this.alertTriggered = alertTriggered;
         this.box = box;
         setMatchLayout(false);
      }

      @Override
      public void export(ViewsheetSandbox box, String sheet, int index,
                         XPortalHelper helper)
            throws Exception
      {
         this.box = box;
         super.export(box, sheet, index, helper);
      }

      @Override
      protected boolean needExport(VSAssembly assembly) {
         return true;
      }

      private boolean checkHighlight(String highlight, ColorFrame frame,
                                     DataSet data)
      {
         boolean result = false;

         if(frame instanceof CompositeColorFrame composite) {

            for(int i = 0; i < composite.getFrameCount(); i++) {
               VisualFrame visual = composite.getFrame(i);

               if(visual instanceof ColorFrame) {
                  if(checkHighlight(highlight, (ColorFrame) visual, data)) {
                     result = true;
                     break;
                  }
               }
            }
         }
         else if(frame instanceof HLColorFrame) {
            if(((HLColorFrame) frame).isHighlighted(highlight, data)) {
               result = true;
            }
         }

         return result;
      }

      private void checkHighlight(ChartVSAssembly assembly, VGraph graph,
                                  DataSet data)
      {
         List<ScheduleAlert> alerts = alertAssemblies.get(assembly);

         if(alerts != null) {
            for(ScheduleAlert alert : alerts) {
               String highlight = alert.getHighlightName();
               EGraph eGraph = graph.getEGraph();

               for(int i = 0; i < eGraph.getElementCount(); i++) {
                  GraphElement element = eGraph.getElement(i);

                  if(checkHighlight(highlight, element.getColorFrame(), data)) {
                     alertTriggered.set(true);
                     break;
                  }
               }

               if(alertTriggered.get()) {
                  break;
               }
            }
         }
      }

      private void checkHighlight(VSAssembly assembly) {
         List<ScheduleAlert> alerts = alertAssemblies.get(assembly);

         if(alerts != null) {
            RangeOutputVSAssemblyInfo info =
               (RangeOutputVSAssemblyInfo) assembly.getInfo();
            double value = info.getDoubleValue();

            for(ScheduleAlert alert : alerts) {
               int index = Integer.parseInt(
                  alert.getHighlightName().substring(18)) - 1;

               double min = index == 0 ? 0D : info.getRanges()[index - 1];
               double max = info.getRanges()[index];

               if(value >= min && value < max) {
                  alertTriggered.set(true);
                  break;
               }
            }
         }
      }

      private void checkHighlight(VSAssembly assembly, VSTableLens lens) {
         List<ScheduleAlert> alerts = alertAssemblies.get(assembly);

         if(alerts != null && !alerts.isEmpty()) {
            if(assembly instanceof CrosstabVSAssembly) {
               AlertCrosstabHelper helper = new AlertCrosstabHelper();
               helper.setExporter(this);
               helper.write((CrosstabVSAssembly) assembly, lens);
            }
            else if(assembly instanceof CalcTableVSAssembly) {
               AlertCrosstabHelper helper = new AlertCrosstabHelper();
               helper.setExporter(this);
               helper.write((CalcTableVSAssembly) assembly, lens);
            }
            else if(assembly instanceof TableVSAssembly) {
               AlertTableHelper helper = new AlertTableHelper();
               helper.setExporter(this);
               helper.write((TableVSAssembly) assembly, lens);
            }

            TableHighlightAttr.HighlightTableLens highlights =
               (TableHighlightAttr.HighlightTableLens) Util.getNestedTable(
                  lens, TableHighlightAttr.HighlightTableLens.class);

            if(highlights != null) {
               for(ScheduleAlert alert : alerts) {
                  if(highlights.isHighlighted(alert.getHighlightName())) {
                     alertTriggered.set(true);
                     break;
                  }
               }
            }
         }
      }

      @Override
      protected void writeChart(ChartVSAssembly originalAsm,
                                ChartVSAssembly asm, VGraph graph, DataSet data,
                                BufferedImage img, boolean firstTime,
                                boolean imgOnly)
      {
         checkHighlight(originalAsm, graph, data);
      }

      @Override
      protected void writeWarningText(Assembly[] assemblies, String warning,
                                      VSCompositeFormat format)
      {
      }

      @Override
      protected void writeImageAssembly(ImageVSAssembly assembly,
                                        XPortalHelper helper)
      {
         if(alertAssemblies.containsKey(assembly) && alertTriggered != null) {
            if(assembly.isHighlighted(
               box.getAllVariables(),
               box.getConditionAssetQuerySandbox(assembly.getViewsheet())))
            {
               alertTriggered.set(true);
            }
         }
      }

      @Override
      protected void writeTextInput(TextInputVSAssembly assembly) {
      }

      @Override
      protected void writeText(VSAssembly assembly, String txt) {
         if(assembly instanceof OutputVSAssembly) {
            if(alertAssemblies.containsKey(assembly) && alertTriggered != null) {
               if(((OutputVSAssembly) assembly).isHighlighted(
                  box.getAllVariables(),
                  box.getConditionAssetQuerySandbox(assembly.getViewsheet())))
               {
                  alertTriggered.set(true);
               }
            }
         }
      }

      @Override
      protected void writeText(String text, Point pos, Dimension size,
                               VSCompositeFormat format)
      {
      }

      @Override
      protected void writeTimeSlider(TimeSliderVSAssembly assm) {
      }

      @Override
      protected void writeCalendar(CalendarVSAssembly assm) {
      }

      @Override
      protected void writeGauge(GaugeVSAssembly assembly) {
         checkHighlight(assembly);
      }

      @Override
      protected void writeThermometer(ThermometerVSAssembly assembly) {
         checkHighlight(assembly);
      }

      @Override
      protected void writeCylinder(CylinderVSAssembly assembly) {
         checkHighlight(assembly);
      }

      @Override
      protected void writeSlidingScale(SlidingScaleVSAssembly assembly) {
         checkHighlight(assembly);
      }

      @Override
      protected void writeRadioButton(RadioButtonVSAssembly assembly) {
      }

      @Override
      protected void writeCheckBox(CheckBoxVSAssembly assembly) {
      }

      @Override
      protected void writeSlider(SliderVSAssembly assembly) {
      }

      @Override
      protected void writeSpinner(SpinnerVSAssembly assembly) {
      }

      @Override
      protected void writeComboBox(ComboBoxVSAssembly assembly) {
      }

      @Override
      protected void writeSelectionList(SelectionListVSAssembly assembly) {
      }

      @Override
      protected void writeSelectionTree(SelectionTreeVSAssembly assembly) {
      }

      @Override
      protected void writeTable(TableVSAssembly assembly, VSTableLens lens) {
         checkHighlight(assembly, lens);
      }

      @Override
      protected void writeCrosstab(CrosstabVSAssembly assembly,
                                   VSTableLens lens)
      {
         checkHighlight(assembly, lens);
      }

      @Override
      protected void writeCalcTable(CalcTableVSAssembly assembly,
                                    VSTableLens lens)
      {
         checkHighlight(assembly, lens);
      }

      @Override
      protected void writeChart(ChartVSAssembly chartAsm, VGraph vgraph,
                                DataSet data, boolean imgOnly)
      {
         checkHighlight(chartAsm, vgraph, data);
      }

      @Override
      protected void writeVSTab(TabVSAssembly assembly) {
      }

      @Override
      protected void writeGroupContainer(GroupContainerVSAssembly assembly,
                                         XPortalHelper helper)
      {
      }

      @Override
      protected void writeShape(ShapeVSAssembly assembly) {
      }

      @Override
      protected void writeCurrentSelection(CurrentSelectionVSAssembly assembly)
      {
      }

      @Override
      protected void writeSubmit(SubmitVSAssembly assembly) {
      }

      @Override
      public void write() throws IOException {
      }

      private final Map<Assembly, List<ScheduleAlert>> alertAssemblies;
      private final AtomicBoolean alertTriggered;
      private ViewsheetSandbox box;
   }

   /**
    * Dummy table helper implementation that allows highlight conditions to be
    * evaluated.
    */
   private static final class AlertTableHelper extends VSTableHelper {
      @Override
      protected void writeTableCell(int startX, int startY, Dimension span,
                                    Rectangle2D pixelbounds, int row, int col,
                                    VSCompositeFormat format, String dispText,
                                    Object dispObj, Hyperlink.Ref hyperlink,
                                    VSCompositeFormat parentformat,
                                    Rectangle rec, Insets padding)
      {
      }

      @Override
      protected void drawObjectFormat(TableDataVSAssemblyInfo info,
                                      VSTableLens lens, boolean borderOnly)
      {
      }
   }

   /**
    * Dummy crosstab helper implementation that allows highlight conditions to
    * be evaluated.
    */
   private static final class AlertCrosstabHelper extends VSCrosstabHelper {
      @Override
      protected void writeTableCell(int startX, int startY, Dimension span,
                                    Rectangle2D pixelbounds, int row, int col,
                                    VSCompositeFormat format, String dispText,
                                    Object dispObj, Hyperlink.Ref hyperlink,
                                    VSCompositeFormat parentformat,
                                    Rectangle rec, Insets padding)
      {
      }

      @Override
      protected void drawObjectFormat(TableDataVSAssemblyInfo info,
                                      VSTableLens lens, boolean borderOnly)
      {
      }
   }

   private boolean saveToServerMatch = true;
   private boolean saveToServerExpandSelections = false;
   private boolean saveToServerOnlyDataComponents = false;
   private boolean saveExportAllTabbedTables = false;
   private Map<String, Integer> formatMap;
   private RepletRequest vrequest = new RepletRequest();
   private final Map<Integer, ServerPathInfo> filePaths = new HashMap<>();
   private String viewsheet;
   private int[] bookmarkTypes;
   private String[] bookmarkNames;
   private IdentityID[] bookmarkUsers;
   private Boolean bookmarkReadOnly;
   private ScheduleAlert[] alerts;
   private CSVConfig saveCSVConfig = null;
   private String id;
   private Principal principal;

   private static final Logger LOG =
      LoggerFactory.getLogger(ViewsheetAction.class);
}
