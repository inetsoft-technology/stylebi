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
package inetsoft.web.viewsheet.service;

import inetsoft.analytic.composition.VSPortalHelper;
import inetsoft.report.composition.RuntimeViewsheet;
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
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.util.Identity;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.*;
import inetsoft.util.log.LogLevel;
import inetsoft.web.viewsheet.controller.dialog.ExportDialogController;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;

import java.io.*;
import java.security.Principal;
import java.text.MessageFormat;
import java.util.*;

@Component
public class VSEmailService {
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
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(formatType == FileFormatInfo.EXPORT_TYPE_SNAPSHOT && ExportDialogController.isCube(vs)) {
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

      FileSystemService fileSystemService = FileSystemService.getInstance();

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

         OutputStream output = new FileOutputStream(file);

         if(FileFormatInfo.EXPORT_TYPE_SNAPSHOT == formatType) {
            SnapshotVSExporter exporter = new SnapshotVSExporter(rvs);
            exporter.setLogExport(true);
            exporter.write(output);
         }
         else {
            if(!multipleFiles) {
               if(excelToCSV) {
                  File excelFile = fileSystemService.getCacheFile(fname + ".xlsx");
                  FileOutputStream out = new FileOutputStream(excelFile);
                  exportViewsheet(rvs, principal, FileFormatInfo.EXPORT_TYPE_EXCEL, bookmarks, out,
                     csvConfig, matchLayout, expandSelections, onlyDataComponent, includeCurrent,
                     null, exportAllTabbedCrosstab);
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
            else {
               for(int i = 0; i < fileList.size(); i++) {
                  File f = fileList.get(i);
                  OutputStream output0 = new FileOutputStream(f);
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
                     exporter.export(box, catalog.getString("Current View"), helper);
                  }
                  else {
                     int vmode = Viewsheet.SHEET_RUNTIME_MODE;

                     ViewsheetSandbox sandbox = new ViewsheetSandbox(
                        rvs.getOriginalBookmark(bookmarks[i]), vmode, principal,
                        rvs.getEntry());
                     exporter.export(sandbox, bookmarks[i], (i + 1), helper);
                     sandbox.dispose();
                  }

                  exporter.write();
                  output0.close();
               }
            }
         }
      }

      try {
         Mailer mailer = new Mailer();
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
            String encodeUri = getLink(linkUri, rvs, entry);
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

                     if(!currUser.equals(bminfo.getOwner())) {
                        if(VSBookmarkInfo.PRIVATE == bminfo.getType()) {
                           continue;
                        }
                        else if(VSBookmarkInfo.GROUPSHARE == bminfo.getType() &&
                           !rvs.isSameGroup(bminfo.getOwner(), currUser)) {
                           continue;
                        }
                     }

                     if(home.equals(book)) {
                        binfo = bminfo;
                        break;
                     }

                     if(book.equals(bminfo.getName()) ||
                        book.equals(bminfo.getName() +
                                       "(" + bminfo.getOwner() + ")")) {
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
            name, new Date(), (principal != null) ? principal.getName() : ""
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
                     htmlWriter.write("<a href=\"" + getLink(linkUri, rvs, entry) + "\" >");
                     htmlWriter.write("<img src=\"cid:" + pngFile.getName() + "\" /></a>");
                  }
                  else {
                     htmlWriter.write("<img src=\"cid:" + pngFile.getName() + "\" />");
                  }
               }

               images = new ArrayList<>();
               images.add(pngFile.getName());
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
                        htmlWriter.write("<a href=\"" + getLink(linkUri, rvs, entry) + "\" >");
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

         if(images != null) {
            for(String image : images) {
               final File imageFile = fileSystemService.getCacheFile(image);

               if(imageFile != null) {
                  final boolean removed = imageFile.delete();

                  if(!removed) {
                     fileSystemService.remove(imageFile, 60000);
                  }
               }
            }
         }
      }
      finally {
         if(file != null) {
            boolean removed = file.delete();

            if(!removed) {
               fileSystemService.remove(file, 60000);
            }
         }
      }
   }

   private static void exportViewsheet(RuntimeViewsheet rvs, Principal principal, int formatType,
                                       String[] bookmarks, OutputStream output, CSVConfig csvConfig,
                                       boolean matchLayout, boolean expandSelections,
                                       boolean onlyDataComponent,  boolean includeCurrent,
                                       File excelFile, boolean exportAllTabbedTables)
      throws Exception
   {
      Catalog catalog = Catalog.getCatalog(principal);
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      VSExporter exporter = AbstractVSExporter.getVSExporter(
              formatType, PortalThemesManager.getColorTheme(), output, false, csvConfig);
      exporter.setLogExport(true);
      exporter.setMatchLayout(matchLayout);
      exporter.setExpandSelections(expandSelections);
      exporter.setAssetEntry(rvs.getEntry());
      exporter.setOnlyDataComponents(onlyDataComponent && !matchLayout);
      exporter.setSandbox(box);
      VSPortalHelper helper = new VSPortalHelper();

      if(exporter instanceof CSVVSExporter) {
         ((CSVVSExporter) exporter).setExcelFile(excelFile);
      }

      if(exporter instanceof ExcelVSExporter) {
         ((ExcelVSExporter) exporter).setExportAllTabbedTables(exportAllTabbedTables);
      }

      if(includeCurrent) {
         exporter.export(box, catalog.getString("Current View"), helper);
      }

      int vmode = Viewsheet.SHEET_RUNTIME_MODE;

      for(int i = 0; bookmarks != null && i < bookmarks.length; i++) {
         ViewsheetSandbox sandbox = new ViewsheetSandbox(
                 rvs.getOriginalBookmark(bookmarks[i]), vmode, principal,
                 rvs.getEntry());
         exporter.export(sandbox, bookmarks[i], (i + 1), helper); //!!! maybe the pictures aren't being written out become of overwriting?
         sandbox.dispose();
      }

      exporter.write();
      output.close();
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
                  IdentityID userID = new IdentityID(userName, OrganizationManager.getCurrentOrgName());
                  User user = security.getUser(userID);

                  if(user != null && !userEmails.contains(addr)) {
                     userEmails.add(addr);
                  }
               }
               else if(security != null && addr.endsWith(Identity.GROUP_SUFFIX)) {
                  String groupName = addr.substring(0, addr.lastIndexOf(Identity.GROUP_SUFFIX));
                  IdentityID groupID = new IdentityID(groupName, OrganizationManager.getCurrentOrgName());

                  addr = groupID.name + Identity.GROUP_SUFFIX;

                  Group group = security.getGroup(groupID);

                  if(group != null && !userEmails.contains(addr)) {
                     userEmails.add(addr);
                  }
               }
            }
            else {
               addr = fixGroupAddr(addr, principal);
               String[] uemails = SUtil.getEmails(new IdentityID(addr, OrganizationManager.getCurrentOrgName()));

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
   private String getLink(String linkUri, RuntimeViewsheet rvs, AssetEntry entry) {
      StringBuilder url = new StringBuilder().append(linkUri).append("app/viewer/view/");

      if(rvs.getEntry().getScope() == AssetRepository.USER_SCOPE) {
         url.append("user/").append(entry.getUser().name).append('/');
      }
      else {
         url.append("global/");
      }

      url.append(entry.getPath());
      return Tool.encodeUriPath(url.toString());
   }
}
