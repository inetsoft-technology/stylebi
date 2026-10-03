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
package inetsoft.web.viewsheet.service;

import inetsoft.analytic.composition.VSPortalHelper;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.io.csv.CSVConfig;
import inetsoft.report.io.viewsheet.*;
import inetsoft.report.io.viewsheet.excel.CSVUtil;
import inetsoft.report.io.viewsheet.excel.CSVVSExporter;
import inetsoft.report.io.viewsheet.snapshot.SnapshotVSExporter;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.Mailer;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.security.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.util.Identity;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.*;
import inetsoft.util.log.LogLevel;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.security.Principal;
import java.text.MessageFormat;
import java.util.*;

@Component
public class VSEmailService {
   public VSEmailService(FileSystemService fileSystemService) {
      this.fileSystemService = fileSystemService;
   }

   public void emailViewsheet(RuntimeViewsheet rvs, int formatType, String[] bookmarks,
                              boolean matchLayout, boolean expandSelections,
                              boolean includeCurrent, String toaddrs,
                              String ccaddrs, String from, String subject, String body,
                              boolean isSendLink, String linkUri, Principal principal)
      throws Exception
   {
      emailViewsheet(rvs, formatType, bookmarks, matchLayout, expandSelections, false,
      includeCurrent, toaddrs, ccaddrs,  from,  subject, body, isSendLink, linkUri, principal);
   }

   public void emailViewsheet(RuntimeViewsheet rvs, int formatType, String[] bookmarks,
                              boolean matchLayout, boolean expandSelections,
                              boolean onlyDataComponent,
                              boolean includeCurrent, String toaddrs,
                              String ccaddrs, String from, String subject, String body,
                              boolean isSendLink, String linkUri, Principal principal)
      throws Exception
   {
      emailViewsheet(rvs, formatType, bookmarks, matchLayout, expandSelections, onlyDataComponent,
         null, includeCurrent, toaddrs, ccaddrs, from, subject, body, isSendLink,
         linkUri, principal);
   }

   public void emailViewsheet(RuntimeViewsheet rvs, int formatType, String[] bookmarks,
                              boolean matchLayout, boolean expandSelections,
                              boolean onlyDataComponent, CSVConfig csvConfig,
                              boolean includeCurrent, String toaddrs,
                              String ccaddrs, String from, String subject, String body,
                              boolean isSendLink, String linkUri, Principal principal)
      throws Exception
   {
      emailViewsheet(rvs, formatType, bookmarks, matchLayout, expandSelections, onlyDataComponent,
         csvConfig, false, includeCurrent, toaddrs, ccaddrs, null, from, subject, body,
         isSendLink, linkUri, principal);
   }

   public void emailViewsheet(RuntimeViewsheet rvs, int formatType, String[] bookmarks,
                              boolean matchLayout, boolean expandSelections,
                              boolean onlyDataComponent, CSVConfig csvConfig,
                              boolean exportAllTabbedCrosstab, boolean includeCurrent,
                              String toaddrs, String ccaddrs, String bccaddrs, String from,
                              String subject, String body, boolean isSendLink, String linkUri,
                              Principal principal)
      throws Exception
   {
      Catalog catalog = Catalog.getCatalog(principal);
      Viewsheet vs = rvs.getViewsheet();
      Optional<ViewsheetSandbox> box = rvs.getViewsheetSandbox();

      if(box.isEmpty()) {
         return;
      }

      if(formatType == FileFormatInfo.EXPORT_TYPE_SNAPSHOT && isCube(vs)) {
         throw new MessageException(catalog.getString("cube.not.supported"), LogLevel.WARN);
      }

      boolean excelToCSV = false;

      if(formatType == FileFormatInfo.EXPORT_TYPE_EXCEL && CSVUtil.hasLargeDataTable(rvs)) {
         formatType = FileFormatInfo.EXPORT_TYPE_CSV;
         excelToCSV = true;
      }

      String path = rvs.getEntry().getPath();
      String name = SUtil.localize(path, principal, true, rvs.getEntry());
      String ftype;
      File file = null;

      boolean multipleFiles = formatType == FileFormatInfo.EXPORT_TYPE_PNG && bookmarks.length > 1;
      List<File> fileList = new ArrayList<>();

      FileSystemService fileSystemService = this.fileSystemService;

      if(formatType != -1) {
         ftype = ExportUtil.getSuffix(formatType);

         String fname = Tool.toFileName(name);
         file = fileSystemService.getCacheFile(fname + "." + ftype);

         if(!multipleFiles) {
            for(int i = 0; file.exists(); i++) {
               file = fileSystemService.getCacheFile(fname + "_" + (i + 1) + "." + ftype);
            }
         }
         else {
            Set<String> takenFileNames = new HashSet<>();
            String fn = fname + "." + ftype;
            takenFileNames.add(fn);

            for(int i = 0; i < bookmarks.length && takenFileNames.contains(fn); i++) {
               for(int j = 0; file.exists() || takenFileNames.contains(fn); j++) {
                  fn = fname + "_" + (j + 1) + "." + ftype;
                  file = fileSystemService.getCacheFile(fn);
               }

               takenFileNames.add(fn);
               fileList.add(file);
               file = fileSystemService.getCacheFile(fname + "." + ftype);
            }

            if(includeCurrent) {
               fn = fname + "_0." + ftype;
               file = fileSystemService.getCacheFile(fn);
               fileList.add(file);
            }
         }

         if(multipleFiles) {
            // the images are written to fileList; the base name is only a naming seed and may
            // be another export's file, so it must not be opened, truncated or deleted here
            file = null;
         }

         File excelFile = null;
         // the excel intermediate's own unique directory (excelToCSV only); kept separate from
         // the attachment file's cache path so two concurrent emails of the same viewsheet entry
         // can never collide on the same ".xlsx" name
         File excelDir = null;
         // the files this export opened; names picked but not yet reached may since have been
         // picked and written by a concurrent email of the same viewsheet
         List<File> created = new ArrayList<>();
         boolean exported = false;

         try {
            if(FileFormatInfo.EXPORT_TYPE_SNAPSHOT == formatType) {
               try(OutputStream output = new FileOutputStream(file)) {
                  created.add(file);
                  SnapshotVSExporter exporter = new SnapshotVSExporter(rvs);
                  exporter.setLogExport(true);
                  exporter.write(output);
               }
            }
            else if(!multipleFiles) {
               try(OutputStream output = new FileOutputStream(file)) {
                  created.add(file);

                  if(excelToCSV) {
                     // @by stephenwebster, fix bug1395938669865 (same fix, applied here too)
                     // put the excel intermediate in its own unique directory, keeping the
                     // ".xlsx" base name, so two concurrent emails of the same viewsheet never
                     // collide on the same cache path
                     excelDir = createTmpDir();
                     excelFile = fileSystemService.getFile(excelDir.getPath(), fname + ".xlsx");

                     try(FileOutputStream out = new FileOutputStream(excelFile)) {
                        created.add(excelFile);
                        exportViewsheet(rvs, principal, FileFormatInfo.EXPORT_TYPE_EXCEL,
                           bookmarks, out, csvConfig, matchLayout, expandSelections,
                           onlyDataComponent, includeCurrent, null, exportAllTabbedCrosstab);
                     }

                     exportViewsheet(rvs, principal, formatType, bookmarks, output, csvConfig,
                        false, expandSelections, onlyDataComponent, includeCurrent,
                        excelFile, exportAllTabbedCrosstab);
                  }
                  else {
                     exportViewsheet(rvs, principal, formatType, bookmarks, output, csvConfig,
                        matchLayout, expandSelections, onlyDataComponent, includeCurrent, null,
                        exportAllTabbedCrosstab);
                  }
               }
            }
            else {
               for(int i = 0; i < fileList.size(); i++) {
                  File f = fileList.get(i);

                  try(OutputStream output0 = new FileOutputStream(f)) {
                     created.add(f);
                     VSExporter exporter = AbstractVSExporter.getVSExporter(
                        formatType, PortalThemesManager.getColorTheme(), output0, false,
                        csvConfig);
                     exporter.setLogExport(true);
                     exporter.setMatchLayout(matchLayout);
                     exporter.setExpandSelections(expandSelections);
                     exporter.setAssetEntry(rvs.getEntry());
                     exporter.setOnlyDataComponents(onlyDataComponent && !matchLayout);
                     VSPortalHelper helper = new VSPortalHelper();

                     if(includeCurrent && i >= bookmarks.length) {
                        exporter.export(box.get(), catalog.getString("Current View"), helper);
                     }
                     else {
                        int vmode = Viewsheet.SHEET_RUNTIME_MODE;

                        ViewsheetSandbox sandbox = createSandbox(
                           rvs.getOriginalBookmark(bookmarks[i]), vmode, principal,
                           rvs.getEntry(), box.get().getVariableTable());

                        try {
                           exporter.export(sandbox, bookmarks[i], (i + 1), helper);
                        }
                        finally {
                           sandbox.dispose();
                        }
                     }

                     exporter.write();
                  }
               }
            }

            exported = true;
         }
         finally {
            if(!exported) {
               // a failed export must not leave its partial attachments in the cache dir
               created.forEach(this::deleteCacheFile);
            }

            if(excelDir != null) {
               // on success, CSVVSExporter.removeCSVFiles() has already deleted excelFile
               // itself, leaving an empty directory; on failure, excelFile may still exist.
               // Tool.deleteFile recursively removes the directory (and anything left in it)
               // either way.
               Tool.deleteFile(excelDir);
            }
         }
      }

      try {
         Mailer mailer = createMailer();
         toaddrs = getEmailsString(getEmailsList(toaddrs, principal));
         boolean isEmptyCC = StringUtils.isEmpty(ccaddrs);
         boolean isEmptyBCC = StringUtils.isEmpty(bccaddrs);
         ccaddrs = getEmailsString(getEmailsList(ccaddrs, principal));
         bccaddrs = getEmailsString(getEmailsList(bccaddrs, principal));
         boolean isValidCC = isEmptyCC || !StringUtils.isEmpty(ccaddrs);
         boolean isValidBCC = isEmptyBCC || !StringUtils.isEmpty(bccaddrs);
         boolean htmlMime = false;
         AssetEntry entry = rvs.getEntry();

         if("".equals(toaddrs) || !isValidCC || !isValidBCC) {
            throw new MessageException(catalog.getString("Test Mail No Address"), LogLevel.ERROR);
         }

         if(isSendLink && linkUri != null && entry.getScope() != AssetRepository.TEMPORARY_SCOPE) {
            String encodeUri = getLink(linkUri, rvs, entry, principal);
            StringBuilder includeBookMark = new StringBuilder();
            String includeLink;

            if(body == null) {
               body = "";
            }

            body += "<br>" + catalog.getString("common.mail.link.description.dashboard") + "<br>";

            if(bookmarks != null) {
               for(String book : bookmarks) {
                  IdentityID bookmarkUser;
                  String bookmarkName;
                  String home = catalog.getString("(Home)");
                  IdentityID currUser = IdentityID.getIdentityIDFromKey(principal.getName());
                  VSBookmarkInfo binfo = null;

                  for(VSBookmarkInfo bminfo : rvs.getBookmarks()) {
                     if(bminfo == null) {
                        break;
                     }

                     IdentityID owner = bminfo.getOwner();

                     if(!currUser.equals(owner)) {
                        if(VSBookmarkInfo.PRIVATE == bminfo.getType()) {
                           continue;
                        }
                        else if(VSBookmarkInfo.GROUPSHARE == bminfo.getType() &&
                           !rvs.isSameGroup(owner, currUser)) {
                           continue;
                        }
                     }

                     if(home.equals(book)) {
                        binfo = bminfo;
                        break;
                     }

                     if(book.equals(bminfo.getName()) ||
                        book.equals(bminfo.getName() + "(" + VSUtil.getUserAlias(owner) + ")"))
                     {
                        binfo = bminfo;
                        break;
                     }
                  }

                  if(binfo == null) {
                     throw new IllegalArgumentException("Bookmark not found: " + book);
                  }

                  bookmarkUser = binfo.getOwner();
                  bookmarkName = binfo.getName();

                  String wrapUrl = SUtil.getURL(encodeUri)
                     + "bookmarkName=" + Tool.encodeWebURL(bookmarkName)
                     + "&bookmarkUser=" + Tool.encodeWebURL(bookmarkUser.convertToKey());
                  includeBookMark.append("<a href=\"").append(wrapUrl).append("\">")
                     .append(book).append("</a><br>");
               }
            }

            includeLink =
               "<a href=\"" + encodeUri + "\">" + name + "</a>";
            body += (bookmarks == null || bookmarks.length == 0) ? includeLink : includeBookMark;
         }

         String sfmt = "".equals(subject) ?
            SreeEnv.getProperty("mail.subject.format", "Viewsheet " + name) : subject;
         Object[] fmtparams = new Object[]{
            name, new Date(), (principal != null) ? IdentityID.getIdentityIDFromKey(principal.getName()).getName() : ""
         };

         try {
            subject = java.text.MessageFormat.format(sfmt, fmtparams);
         }
         catch(IllegalArgumentException e) {
            // escape '{', for format
            StringBuilder sb = new StringBuilder();

            for(int i = 0; i < sfmt.length(); i++) {
               if("{".equals(sfmt.charAt(i) + "")) {
                  sb.append("'").append(sfmt.charAt(i)).append("'");
               }
               else {
                  sb.append(sfmt.charAt(i));
               }
            }

            sfmt = sb.toString();
            subject = MessageFormat.format(sfmt, fmtparams);
         }

         ArrayList<String> images = null;


         if(FileFormatInfo.EXPORT_TYPE_PNG == formatType) {
            if(!multipleFiles) {
               final File pngFile = file;
               final String fname = Tool.toFileName(name);
               final File htmlFile = fileSystemService.getCacheFile(fname + "." + "html");

               try(final FileOutputStream output = new FileOutputStream(htmlFile);
                   final OutputStreamWriter outputWriter = new OutputStreamWriter(output);
                   final PrintWriter htmlWriter = new PrintWriter(outputWriter))
               {
                  if(isSendLink) {
                     htmlWriter.write("<a href=\"" + getLink(linkUri, rvs, entry, principal) + "\" >");
                     htmlWriter.write("<img src=\"cid:" + pngFile.getName() + "\" /></a>");
                  }
                  else {
                     htmlWriter.write("<img src=\"cid:" + pngFile.getName() + "\" />");
                  }
               }

               images = new ArrayList<>();
               images.add(pngFile.getName());
               // deleted with the other images in the finally, also when the send fails
               fileList.add(pngFile);
               htmlMime = true;
               file = htmlFile;
            }
            else {
               final String fname = Tool.toFileName(name);
               final File htmlFile = fileSystemService.getCacheFile(fname + "." + "html");
               final FileOutputStream output = new FileOutputStream(htmlFile);
               final OutputStreamWriter outputWriter = new OutputStreamWriter(output);
               images = new ArrayList<>();

               try(PrintWriter htmlWriter = new PrintWriter(outputWriter)) {
                  for(int i = 0; i < fileList.size(); i++) {
                     final File pngFile = fileList.get(i);

                     if(isSendLink) {
                        htmlWriter.write("<a href=\"" + getLink(linkUri, rvs, entry, principal) + "\" >");
                        htmlWriter.write("<img src=\"cid:" + pngFile.getName() + "\" /></a>");
                     }
                     else {
                        htmlWriter.write("<img src=\"cid:" + pngFile.getName() + "\" />");
                     }

                     images.add(pngFile.getName());
                  }
               }

               htmlMime = true;
               file = htmlFile;
            }
         }

         mailer.send(toaddrs, ccaddrs, bccaddrs, from, subject, body, file,
                     images, htmlMime, true);
      }
      finally {
         deleteCacheFile(file);
         // the png images, which are attached through the html file
         fileList.forEach(this::deleteCacheFile);
      }
   }

   private void deleteCacheFile(File file) {
      if(file != null && file.exists() && !file.delete()) {
         fileSystemService.remove(file, 60000);
      }
   }

   /**
    * Create a unique, per-call cache subdirectory, mirroring the pattern already used by
    * {@code ViewsheetAction} and {@code VSExportService.createTmpDir()} for the same purpose:
    * giving an exported intermediate file a unique path while keeping its own file name, so
    * concurrent exports of the same viewsheet never collide on a shared cache path.
    */
   private File createTmpDir() throws IOException {
      String uuid = UUID.randomUUID().toString();
      String dir = fileSystemService.getCacheDirectory() + File.separator + uuid;
      File tmpDir = fileSystemService.getFile(dir);

      if(!tmpDir.mkdir()) {
         LOG.warn("Failed to create temporary directory: {}", tmpDir);
      }

      return tmpDir;
   }

   private void exportViewsheet(RuntimeViewsheet rvs, Principal principal, int formatType,
                                       String[] bookmarks, OutputStream output, CSVConfig csvConfig,
                                       boolean matchLayout, boolean expandSelections,
                                       boolean onlyDataComponent,  boolean includeCurrent,
                                       File excelFile, boolean exportAllTabbedTables)
      throws Exception
   {
      Catalog catalog = Catalog.getCatalog(principal);
      Optional<ViewsheetSandbox> box = rvs.getViewsheetSandbox();

      if(box.isEmpty()) {
         return;
      }

      VSExporter exporter = AbstractVSExporter.getVSExporter(
              formatType, PortalThemesManager.getColorTheme(), output, false, csvConfig);
      exporter.setLogExport(true);
      exporter.setMatchLayout(matchLayout);
      exporter.setExpandSelections(expandSelections);
      exporter.setAssetEntry(rvs.getEntry());
      exporter.setOnlyDataComponents(onlyDataComponent && !matchLayout);
      exporter.setSandbox(box.get());
      VSPortalHelper helper = new VSPortalHelper();

      if(exporter instanceof CSVVSExporter) {
         ((CSVVSExporter) exporter).setExcelFile(excelFile);
      }

      if(exporter instanceof ExcelVSExporter) {
         ((ExcelVSExporter) exporter).setExportAllTabbedTables(exportAllTabbedTables);
      }

      if(includeCurrent) {
         exporter.export(box.get(), catalog.getString("Current View"), helper);
      }

      int vmode = Viewsheet.SHEET_RUNTIME_MODE;
      // Bug #77621: dispose the bookmark sandboxes only after write(). A print-layout PDF
      // paints its queued reports in write(), and their table highlights evaluate script
      // condition values against these sandboxes.
      List<ViewsheetSandbox> createdBoxes = new ArrayList<>();

      try {
         for(int i = 0; bookmarks != null && i < bookmarks.length; i++) {
            ViewsheetSandbox sandbox = createSandbox(
                    rvs.getOriginalBookmark(bookmarks[i]), vmode, principal,
                    rvs.getEntry(), box.get().getVariableTable());
            createdBoxes.add(sandbox);
            exporter.export(sandbox, bookmarks[i], (i + 1), helper); //!!! maybe the pictures aren't being written out become of overwriting?
         }

         exporter.write();
      }
      finally {
         for(ViewsheetSandbox sandbox : createdBoxes) {
            sandbox.dispose();
         }
      }

      output.close();
   }

   protected Mailer createMailer() {
      return new Mailer();
   }

   protected ViewsheetSandbox createSandbox(Viewsheet bookmark, int mode,
                                             Principal principal, AssetEntry entry)
      throws Exception
   {
      return createSandbox(bookmark, mode, principal, entry, null);
   }

   /**
    * Create the sandbox used to export a bookmark. Keep the step order in sync with the bookmark
    * loop in VSExportService (clearScale, construct, refreshVariableTable, clear input variables,
    * onInit, reset with onLoad) so an emailed bookmark matches an exported one. (77246)
    *
    * @param liveVars the variable table of the viewer's live sandbox, or null if none.
    */
   protected ViewsheetSandbox createSandbox(Viewsheet bookmark, int mode,
                                             Principal principal, AssetEntry entry,
                                             VariableTable liveVars)
      throws Exception
   {
      // The bookmark is a clone of the live (possibly scaled-to-screen) viewsheet, so clear the
      // scaled positions/sizes and runtime column widths like export does. (77246)
      if(bookmark != null) {
         VSEventUtil.clearScale(bookmark);
      }

      // Run onInit/onLoad in this thread like bookmark export does (VSExportService), instead
      // of the constructor's reset without onLoad. Otherwise a wrapper's onLoad never runs when
      // the bookmark is exported through a print layout, which skips prepareForExport(). (77180)
      ViewsheetSandbox sandbox = new ViewsheetSandbox(bookmark, mode, principal, false, entry);

      // Copy the viewer's variables (URL/prompted parameters) before onInit/onLoad and the
      // queries run. This must happen before the input variable clearing below, otherwise the
      // viewer's current input values would overwrite the bookmark's. (77246)
      AssetQuerySandbox abox = sandbox.getAssetQuerySandbox();

      if(abox != null && liveVars != null) {
         abox.refreshVariableTable(liveVars);
      }

      // Clear input assembly variables from the sandbox variable table before reset.
      // During reset, applyParameterToInput() reads from this table and would otherwise
      // overwrite bookmark-restored assembly selections (checkbox, radio button, etc.).
      // Also clear the bare variable-name key used by $(varname)-bound assemblies. (74212)
      VariableTable sandboxVars = sandbox.getVariableTable();

      if(sandboxVars != null) {
         for(Assembly assembly : bookmark.getAssemblies()) {
            if(assembly instanceof InputVSAssembly inputAssembly) {
               sandboxVars.remove(assembly.getName());
               String varKey = inputAssembly.getVariableTableKey();

               if(varKey != null) {
                  sandboxVars.remove(varKey);
               }
            }
         }
      }

      try {
         sandbox.processOnInit();
         sandbox.reset(null, bookmark.getAssemblies(),
                       new ChangedAssemblyList(), true, true, null);
      }
      catch(Exception ex) {
         LOG.error("Failed to execute onInit() and onLoad() scripts", ex);
      }

      return sandbox;
   }

   /*
    * Get emails of users.
    */
   public static String getEmailsString(List<String> mails) {
      if(mails == null) {
         return "";
      }

      return Tool.arrayToString(mails.toArray(new String[0]));
   }

   private List<String> getEmailsList(String mails, Principal principal) throws Exception {
      return getEmailsList(mails, false, principal);
   }

   public static List<String> getEmailsList(String mails, boolean keepUser,
                                            Principal principal)
      throws Exception
   {
      if(mails == null || "".equals(mails)) {
         return null;
      }

      mails = mails.replace(";", ",");
      mails = mails.replaceAll(",\\s+", ",");
      mails = mails.replace(' ', ',');
      mails = mails.replace(" - ", "^_^");
      String[] addrs = Tool.split(mails, ',');
      List<String> userEmails = new ArrayList<>();

      for(String addr : addrs) {
         if(!Tool.matchEmail(addr) && (addr.endsWith(Identity.GROUP_SUFFIX) ||
            addr.endsWith(Identity.USER_SUFFIX)))
         {
            if(keepUser) {
               SecurityProvider security = SecurityEngine.getSecurity().getSecurityProvider();

               if(security != null && addr.endsWith(Identity.USER_SUFFIX)) {
                  String userName = addr.substring(0, addr.lastIndexOf(Identity.USER_SUFFIX));
                  IdentityID userID = new IdentityID(userName, OrganizationManager.getInstance().getCurrentOrgID());
                  User user = security.getUser(userID);

                  if(user != null && !userEmails.contains(addr)) {
                     userEmails.add(addr);
                  }
               }
               else if(security != null && addr.endsWith(Identity.GROUP_SUFFIX)) {
                  String groupName = addr.substring(0, addr.lastIndexOf(Identity.GROUP_SUFFIX));
                  IdentityID groupID = new IdentityID(groupName, OrganizationManager.getInstance().getCurrentOrgID());

                  addr = groupID.name + Identity.GROUP_SUFFIX;

                  Group group = security.getGroup(groupID);

                  if(group != null && !userEmails.contains(addr)) {
                     userEmails.add(addr);
                  }
               }
            }
            else {
               addr = fixGroupAddr(addr, principal);
               String[] uemails = SUtil.getEmails(new IdentityID(addr, OrganizationManager.getInstance().getCurrentOrgID()));

               for(int j = 0; uemails != null && j < uemails.length; j++) {
                  if(!userEmails.contains(uemails[j])) {
                     userEmails.add(uemails[j]);
                  }
               }
            }
         }
         else {
            if(!userEmails.contains(addr)) {
               userEmails.add(addr);
            }
         }
      }

      return userEmails;
   }

   private static String fixGroupAddr(String addr, Principal principal) {
      SecurityProvider security = SecurityEngine.getSecurity().getSecurityProvider();

      if(security != null && addr.endsWith(Identity.GROUP_SUFFIX)) {
         if(addr.indexOf(" - ") != -1 ) {
            return addr;
         }

         String groupName = addr.substring(0, addr.lastIndexOf(Identity.GROUP_SUFFIX));

         if(groupName != null && principal instanceof SRPrincipal) {
            addr = groupName + Identity.GROUP_SUFFIX;
         }
      }

      return addr;
   }

   /**
    * Create a link path for the given viewsheet.
    * @param linkUri linkURI
    * @param rvs runtime viewsheet
    * @param entry viewsheet entry
    * @return
    */
   private String getLink(String linkUri, RuntimeViewsheet rvs, AssetEntry entry, Principal principal) {
      StringBuilder url = new StringBuilder().append(linkUri).append("app/viewer/view/");

      if(rvs.getEntry().getScope() == AssetRepository.USER_SCOPE) {
         url.append("user/").append(entry.getUser().name).append('/');
      }
      else {
         boolean globalShare = SUtil.isDefaultVSGloballyVisible(principal) &&
            !Tool.equals(((XPrincipal)principal).getOrgId(), entry.getOrgID()) &&
            Tool.equals(entry.getOrgID(), Organization.getDefaultOrganizationID());
         url.append(globalShare ? "shared_global/" : "global/");
      }

      url.append(entry.getPath());
      return Tool.encodeUriPath(url.toString());
   }

   /**
    * Copy of isCube() from ExportVSEvent.java
    * Check if base cube data source.
    */
   public static boolean isCube(Viewsheet viewsheet) {
      Assembly[] assemblies = viewsheet.getAssemblies(true);

      for(Assembly assembly : assemblies) {
         VSAssemblyInfo info = ((VSAssembly) assembly).getVSAssemblyInfo();

         if(info instanceof SelectionVSAssemblyInfo) {
            for(String tableName : ((SelectionVSAssemblyInfo) info).getTableNames()) {
               if(AssetUtil.getCubeType(null, tableName) != null) {
                  return true;
               }
            }
         }
         else if(info instanceof DataVSAssemblyInfo) {
            DataVSAssemblyInfo dinfo = (DataVSAssemblyInfo) info;
            SourceInfo sinfo = dinfo.getSourceInfo();
            String prefix = sinfo == null ? null : sinfo.getPrefix();
            String source = sinfo == null ? null : sinfo.getSource();

            if(source != null && source.length() > 0) {
               String cubeType = AssetUtil.getCubeType(prefix, source);

               if(cubeType != null) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private final FileSystemService fileSystemService;
   private static final Logger LOG = LoggerFactory.getLogger(VSEmailService.class);
}
