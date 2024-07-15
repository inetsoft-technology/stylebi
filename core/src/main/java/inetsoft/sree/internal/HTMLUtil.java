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
package inetsoft.sree.internal;

import inetsoft.sree.*;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.web.*;
import inetsoft.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTML related utility methods.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class HTMLUtil {
   public HTMLUtil() {

   }

   private static final Map<String, URL> resourceUrls = new ConcurrentHashMap<>();

   // Optimization, class.getResource is expensive when the classpath is long
   private static URL getResource(String res) {
      URL url = resourceUrls.get(res);

      if(url == null) {
         url = SreeEnv.class.getResource(res);

         if(url != null) {
            resourceUrls.put(res, url);
         }

      }

      return url;
   }

   /**
    * Copy a template and replate the ${} variables.
    */
   public static void copyTemplate(String res, PrintWriter writer,
                                   Hashtable<Object, Object> dict,
                                   Principal principal)
      throws IOException
   {
      URL resource = getResource(res);
      InputStream inp = resource == null ? null : resource.openStream();

      if(inp == null) {
         throw new FileNotFoundException("Resource not found: " + res);
      }

      Catalog catalog = Catalog.getCatalog(principal);
      copyTemplate(inp, writer, dict, catalog);
      inp.close();
   }

   /**
    * Copy a template and replace the variables in the template.
    */
   @SuppressWarnings("unchecked")
   public static void copyTemplate(InputStream inp, PrintWriter writer,
                                   Hashtable<Object, Object> dict,
                                   Catalog catalog)
      throws IOException
   {
      inp = new BufferedInputStream(inp);
      BufferedReader reader = new BufferedReader(
         new InputStreamReader(inp, "utf-8"));
      String line;

      if(dict != null && dict.get("html.character.encode") == null) {
         String encode = SreeEnv.getProperty("html.character.encode");
         dict.put("html.character.encode", encode);
      }

      if(dict != null) {
         String wmode = SreeEnv.getProperty("flash.wmode");
         wmode = wmode == null ? "" : wmode;
         dict.put("WMODE", wmode);
      }

      while((line = reader.readLine()) != null) {
         // check #ifdef
         if(line.startsWith("#ifdef")) {
            String name = line.substring(7).trim();

            // skep #ifdef
            if(dict.get(name) == null) {
               int ifdefNumber = 0;

               while((line = reader.readLine()) != null) {
                  if(line.startsWith("#ifdef")) {
                     ifdefNumber++;
                  }

                  if(line.startsWith("#endif")) {
                     if(ifdefNumber == 0) {
                        break;
                     }
                     else {
                        ifdefNumber--;
                     }
                  }
               }
            }

            continue;
         }
         else if(line.startsWith("#endif")) {
            continue;
         }

         int idx = 0;

         while((idx = line.indexOf("$(", idx)) >= 0) {
            if(idx > 0 && line.charAt(idx - 1) == '\\') {
               line = line.substring(0, idx - 1) + line.substring(idx);
               idx += 1;
               continue;
            }

            int eidx = line.indexOf(')', idx);

            // @by billh, for a string to be localized, we should ignore
            // the contained close parentheses in it and find proper end
            if(eidx > 0) {
               int eidx2 = line.indexOf(")#(inter)", eidx);

               if(eidx2 > eidx) {
                  int idx2 = line.indexOf("$(", eidx + 1);

                  while(idx2 > 0 && line.charAt(idx2 - 1) == '\\') {
                     idx2 = line.indexOf("$(", idx2 + 1);
                  }

                  if(idx2 == -1 || eidx2 < idx2) {
                     eidx = eidx2;
                  }
              }
           }

            if(eidx > idx) {
               String name = line.substring(idx + 2, eidx).trim();
               Object val = dict.get(name);
               char op = ((eidx < line.length() - 2) &&
                  (line.charAt(eidx + 2) == '(')) ?
                  line.charAt(eidx + 1) :
                  ' ';
               String opstr = null;

               if(op != ' ') {
                  int ci = eidx + 3;
                  int ce = line.indexOf(')', ci);

                  if(ci < 0 || ce < 0) {
                     LOG.error("Invalid conditional text: {}", name);
                     break;
                  }

                  opstr = line.substring(ci, ce);
                  eidx = ce + 1;
               }

               // check if a conditional value
               if(op == '?') {
                  if(val != null &&
                     (!(val instanceof Boolean) ||
                     ((Boolean) val).booleanValue())) {
                     line = line.substring(0, idx) + opstr +
                        line.substring(eidx);
                     idx += opstr.length();
                  }
                  else {
                     line = line.substring(0, idx) + line.substring(eidx);
                  }
               }
               // check for operator
               else if(op == '#') {
                  opstr = opstr.toLowerCase();
                  StringBuilder str = new StringBuilder();
                  Properties localeProperty = null;

                  if(opstr.equals("options") || opstr.equals("optionsn") ||
                     opstr.equals("options_encode") ||
                     opstr.equals("options_locale") ||
                     opstr.equals("options_no_localizations"))
                  {
                     if(val != null) {
                        if(opstr.equals("options_locale")) {
                           localeProperty = SUtil.loadLocaleProperties();
                        }

                        Object[] selarr = null;
                        Object[] selvalarr = null;
                        Object[] seltextarr = null;
                        Vector<Object> def = null;
                        Object obj = dict.get(name + ".def");

                        if(obj instanceof Vector) {
                           def = (Vector) obj;
                        }
                        else if(obj instanceof Object) {
                           def = new Vector<>();
                           def.addElement(obj);
                        }

                        if(val instanceof Object[]) {
                           selarr = (Object[]) val;
                        }
                        else if(val instanceof Vector) {
                           Vector sel = (Vector) val;

                           selarr = new Object[sel.size()];
                           sel.copyInto(selarr);
                        }
                        else if(val instanceof List) {
                           List sel = (List) val;

                           selarr = new Object[sel.size()];
                           sel.toArray(selarr);
                        }
                        else {
                           throw new RuntimeException("Wrong options: " + val);
                        }

                        // values.
                        obj = dict.get(name + ".values");

                        if(obj != null) {
                           if(obj instanceof Object[]) {
                              selvalarr = (Object[]) obj;
                           }
                           else if(obj instanceof Vector) {
                              Vector sel = (Vector) obj;

                              selvalarr = new Object[sel.size()];
                              sel.copyInto(selvalarr);
                           }
                           else if(obj instanceof List) {
                              List sel = (List) obj;

                              selvalarr = new Object[sel.size()];
                              sel.toArray(selvalarr);
                           }

                           if(selarr.length != selvalarr.length) {
                              throw new RuntimeException("Unequal length " +
                                 "between values in options: " + val);
                           }
                        }

                        // text
                        obj = dict.get(name + ".text");
                        if(obj != null) {
                           if(obj instanceof Object[]) {
                              seltextarr = (Object[]) obj;
                           }
                           else if(obj instanceof Vector) {
                              Vector sel = (Vector) obj;

                              seltextarr = new Object[sel.size()];
                              sel.copyInto(seltextarr);
                           }
                           else if(obj instanceof List) {
                              List sel = (List) obj;

                              seltextarr = new Object[sel.size()];
                              sel.toArray(seltextarr);
                           }

                           if(selarr.length != seltextarr.length) {
                              throw new RuntimeException("Unequal length " +
                                 "between text in options: " + val);
                           }
                        }

                        for(int i = 0; i < selarr.length; i++) {
                           Object item = selarr[i];

                           //bug1370232602210, the empty label will make select
                           //ui wrong. Do not append the empty option.
                           if("".equals(item) && selarr.length == 1) {
                              continue;
                           }

                           str.append("<option ");

                           if(selvalarr != null) {
                              if(def != null &&
                                 (def.contains(Integer.valueOf(i)) ||
                                  def.contains(selvalarr[i]))) {
                                 str.append("selected ");
                              }

                              str.append("value=\"" + selvalarr[i].toString() +
                                 "\"");
                           }
                           else if(opstr.endsWith("n")) {
                              if(def != null && def.contains(Integer.valueOf(i))) {
                                 str.append("selected ");
                              }

                              str.append("value=\"" + i + "\"");
                           }
                           else {
                              if(def != null && def.contains(item)) {
                                 str.append("selected ");
                              }

                              str.append("value=\"" + item + "\"");
                           }

                           String label = "";

                           if(opstr.equals("options_locale")) {
                              if(selvalarr != null) {
                                 if("My Locale".equals(selvalarr[i] + "")) {
                                    label = catalog.getString(
                                       selvalarr[i] + "");
                                 }
                                 else {
                                    label = localeProperty.getProperty(
                                       selvalarr[i] + "");
                                 }
                              }
                           }
                           else if(seltextarr != null) {
                              label = opstr.equals("options_no_localizations") ?
                                 seltextarr[i] + "":
                                 catalog.getString(seltextarr[i] + "");
                           }
                           else {
                              label = item == null ? "" :
                                 opstr.equals("options_no_localizations") ?
                                 item.toString() :
                                 catalog.getString(item.toString());
                           }

                           //add tooltip to html options
                           str.append(" title=\"" + label + "\"");

                           str.append(">");
                           str.append(label == null ? "" :
                              label.replace(" ", "&nbsp;"));

                           str.append("</option>");
                        }
                     }
                  }
                  // It is a table
                  else if(opstr.startsWith("table")) {
                     // check if the checkbox &/| edit is needed
                     // If edit cell is added to this table, need to get
                     // the link
                     Object[] links = null;
                     int checkbox = 0;
                     boolean edit = false;
                     boolean title = false;
                     boolean encodeValue = false;
                     boolean escapeName = false;
                     boolean crosscolor = true;
                     String uri = "";
                     String[] targets = null;
                     String[] cbchecked = null;
                     String[] nocheckbox = null;
                     String[] cbclicked = null;
                     String[] freeicons = null;
                     String[] freecolors = null;
                     int[] freelinkpositions = null;

                     // checkbox name, defaults to 'checkbox'
                     String cbname = (String) dict.get(name + ".name");
                     // flag to exclude checkbox column as table column
                     boolean excluded =
                        "true".equals(dict.get(name + ".excluded"));

                     if(cbname == null) {
                        cbname = "checkbox";
                     }

                     if(opstr.length() >= 7) {
                        checkbox = opstr.charAt(5) - '0';

                        if(checkbox > 0) {
                           cbchecked = (String[]) dict.get(name + ".def");
                           nocheckbox = (String[]) dict.get(name +
                              ".nocheckbox");
                           cbclicked = (String[]) dict.get(name + ".clicked");
                        }

                        char c = opstr.charAt(6);

                        if(c == '1') {
                           edit = true;
                           links = (Object[]) dict.get(name + ".url");
                           freeicons = (String[]) dict.get(name + ".icon");
                           freecolors = (String[]) dict.get(name + ".color");
                           freelinkpositions =
                              (int[]) dict.get(name + ".position");
                           uri = (String) dict.get("SERVLET");
                        }

                        if(opstr.length() >= 8 && opstr.charAt(7) == '1') {
                           title = true;
                        }

                        if(opstr.length() >= 9 && opstr.charAt(8) == '1') {
                           encodeValue = true;
                        }

                        if(opstr.length() >= 10 && opstr.charAt(9) == '1') {
                           escapeName = true;
                        }

                        if(opstr.length() >= 11 && opstr.charAt(10) == '0') {
                           crosscolor = false;
                        }
                     }

                     if(val != null && (val instanceof Object[][])) {
                        Object[][] table = (Object[][]) val;

                        if(edit) {
                           if(table.length > links.length) {
                              throw new RuntimeException("The number of " +
                                 "links doesn't match" +
                                 " the number of table rows");
                           }

                           targets = new String[table.length];
                           Object obj = dict.get(name + ".target");

                           if(obj != null) {
                              if(obj instanceof String[]) {
                                 String[] s = (String[]) obj;

                                 for(int i = 0; i <
                                    Math.min(targets.length, s.length); i++) {
                                    targets[i] = s[i];
                                 }
                              }
                              else if(obj instanceof String) {
                                 for(int i = 0; i < targets.length; i++) {
                                    targets[i] = (String) obj;
                                 }
                              }
                           }
                        }

                        for(int i = 0; i < table.length; i++) {
                           String tclass = ((i % 2 == 1) || !crosscolor) ?
                              "whiterow" :
                              "greenrow";

                           if(freecolors != null && freecolors[i] != null) {
                              tclass = freecolors[i];
                           }

                           if(title && i == 0) {
                              str.append("<tr class=\"titlerow\">\n");
                           }
                           else {
                              str.append("<tr class=\"" + tclass + "\">\n");
                           }

                           // check if checkbox is needed
                           if(checkbox > 0) {
                              if((title && i == 0) ||
                                 (nocheckbox != null && nocheckbox[i] != null &&
                                 nocheckbox[i].equals("no"))) {
                                 str.append("<td>&nbsp;</td>");
                              }
                              else {
                                 String content = "<input type=\"checkbox\"" +
                                    " name=\"" + cbname + i + "\" value=\"" +
                                    (encodeValue ?
                                    Tool.byteEncode(table[i][checkbox - 1].toString()) :
                                    table[i][checkbox - 1]
                                    ) + "\"";

                                 if(cbclicked != null && cbclicked[i] != null) {
                                    content += " onclick=\"" + cbclicked[i] +
                                       "\"";
                                 }

                                 if(Tool.contains(cbchecked,
                                    (String) table[i][checkbox - 1]))
                                 {
                                    content += " checked";
                                 }

                                 str.append(addTD("25", "center",
                                    content + ">"));
                              }
                           }

                           String linkContent = "";

                           if(links == null || links[i] == null ||
                              (links[i] != null && "disabled".equals(links[i])))
                           {
                              if(freeicons != null && freeicons[i] != null) {
                                 linkContent = "<img src=\"" + uri +
                                    "?op=Resource&name=" +
                                    Tool.encodeURL(freeicons[i]) +
                                    "\" width=\"15\" height=\"15\"" +
                                 " border=\"0\">";
                              }
                           }
                           else {
                              linkContent = "<a href=";

                              if(targets == null || targets[i] == null ||
                                 "".equals(targets[i]))
                              {
                                 linkContent += "\"" + links[i] + "\"";
                              }
                              else {
                                 String features = "resizable,status," +
                                    "width=625,height=400,scrollbars," +
                                    "screenX=100,screenY=50," +
                                    "left=100,top=50dependent";
                                 linkContent += "'javascript: void(0)'" +
                                    " onClick='window.open(\"" + links[i] +
                                    "&target=" + Tool.encodeURL(targets[i]) + "\",\"" +
                                    Tool.encodeURL(targets[i]) + "\",\"" + features +
                                    "\")'";
                              }

                              // edit icon
                              String editIcon = (freeicons == null ||
                                 freeicons[i] == null)
                                 ? "/inetsoft/sree/adm/markup/edit.gif" :
                                 (String) freeicons[i];

                              linkContent += "><img src=\"" + uri +
                                 "?op=Resource&name=" +
                                 Tool.encodeURL(editIcon) +
                                 "\" width=\"15\" height=\"15\"" +
                              " border=\"0\"></a>";
                           }

                           if(edit) {
                              if(title && i == 0) {
                                 str.append("<td>&nbsp;</td>\n");
                              }
                              else {
                                 if(!linkContent.isEmpty() &&
                                    (freelinkpositions == null ||
                                    freelinkpositions[i] == 0))
                                 {
                                    str.append(addTD("30", "center",
                                       linkContent));
                                 }
                                 else {
                                    str.append("<td>&nbsp;</td>\n");
                                 }
                              }
                           }

                           for(int j = 0; j < table[i].length; j++) {
                              String tval = (String) table[i][j];

                              if(excluded && j == checkbox - 1) {
                                 continue;
                              }

                              if(tval == null || tval.length() <= 0) {
                                 tval = " ";
                              }

                              if(title && i == 0) {
                                 str.append("<td class=\"titlerow\">" +
                                    Tool.encodeHTML(tval, !escapeName) +
                                    "</td>\n");
                              }
                              else {
                                 str.append("<td class=\"" + tclass + "\">");

                                 if(freelinkpositions != null &&
                                    freelinkpositions[i] == (j + 1))
                                 {
                                    str.append(linkContent);
                                 }

                                 str.append(Tool.encodeHTML(tval, !escapeName) +
                                    "</td>\n");
                              }
                           }

                           str.append("</tr>\n");
                        }
                     }
                  }
                  // It is a tree
                  else if(opstr.startsWith("tree")) {
                     String uri = (String) dict.get("SERVLET");
                     String selected = (String) dict.get("tree.selected");

                     if(selected == null || selected.equals("")) {
                        str.append("<a name='treeLocation'></a>");
                     }

                     if(val != null && val instanceof String[]) {
                        String[] tree = (String[]) val;

                        str.append("<table>");
                        for(int i = 0; i < tree.length; i++) {
                           boolean folder = tree[i].startsWith("__FOLDER");

                           if(folder) {
                              // folder

                              if(i > 0) {
                                 str.append("</table></td></tr>");
                              }

                              int underline = tree[i].lastIndexOf("_");
                              String nm = tree[i].substring(underline + 1);
                              String cellnm = "FOLDER_" + nm.replace(' ', '_') +
                                 i;
                              String iconname = cellnm + "AUX";

                              str.append("<tr><td width=\"14\" class=link " +
                                 "onclick=\"turnit(" + cellnm + "," + iconname +
                                 ")\">");
                              str.append("<p align=\"right\">" + "<img src=\"" +
                                 uri + "?op=Resource" + "&name=" +
                                 Tool.encodeURL("/inetsoft/sree/web/images/table.gif") +
                                 "\" width=\"13\" height=\"14\">");

                              str.append("</td><td class=link onclick=\"" +
                                 "turnit(" + cellnm + "," + iconname + ")\">");

                              str.append(nm);
                              str.append("</td></tr><tr><td width=\"14\" " +
                                 "ID=\"" + iconname + "\"></td>");
                              str.append("<td ID=\"" + cellnm +
                                 "\"  bgcolor=\"#eeeeee\">");
                              str.append("<table border=\"0\" width=\"100%\">");
                           }
                           else {
                              // leaf
                              int underline = tree[i].lastIndexOf("_");
                              String nm = tree[i].substring(underline + 1);
                              String cls = "";
                              String clsEnd = "";

                              if(selected != null && selected.equals(nm)) {
                                 cls = "<p class=cc><a name='treeLocation'></a>";
                              }
                              else {
                                 cls = "<a href=\"" + uri +
                                    "?op=table_openStyle&action=get&name=" +
                                    Tool.encodeURL(nm) + "#treeLocation\" class=bb>";
                                 clsEnd = "</a>";
                              }

                              str.append("<tr><td width=\"100%\">");
                              str.append(cls);
                              str.append(nm);
                              str.append(clsEnd);
                              str.append("</td></tr>");
                           }
                        }

                        if(tree.length > 0) {
                           str.append("</table></td></tr>");
                        }

                        str.append("</table>");
                     }
                  }
                  // for international string, which looks like "$(OK)#(inter)"
                  // or "$(viewer.duperror, rptid, elemid)#(inter)", in
                  // which rptid and elemid should be taken as parameters and
                  // prepared by calling dict.put method like,
                  //    ...
                  //    dict.put("rptid", reportID);
                  //    dict.put("elemid", elementID);
                  //    ...
                  //    HTMLUtil.copyTemplate("/inetsoft/sree/web/1.html",
                  //                          writer, dict, user);
                  //    ...
                  else if(opstr.startsWith("inter")) {
                     String[] name_params = Tool.split(name, ',');
                     Object[] params = new Object[name_params.length - 1];
                     name= name_params[0];

                     for(int pi = 0; pi < name_params.length - 1; pi++) {
                        String param = name_params[pi + 1].trim();
                        params[pi] = dict.get(param);
                     }

                     str.append(catalog.getString(name, params));
                  }
                  // @by jasons, this allows pages to be composed of other
                  // templated pages. The resource path to the included page
                  // is used in the name part of the tag, e.g.
                  // $(/inetsoft/sree/adm/markup/sub.html)#(include). The
                  // included page is run through copyTemplate using the
                  // same variable table as the parent page.
                  else if(opstr.startsWith("include")) {
                     InputStream inp2 = SreeEnv.class.getResourceAsStream(name);

                     if(inp2 == null) {
                        throw new FileNotFoundException(
                           "Resource not found: " + name);
                     }

                     StringWriter sw = new StringWriter();
                     PrintWriter pw = new PrintWriter(sw);
                     copyTemplate(inp2, pw, dict, catalog);
                     inp2.close();
                     pw.flush();
                     pw.close();
                     str.append(sw.toString());
                  }

                  line = line.substring(0, idx) + str + line.substring(eidx);
                  idx += str.length();
               }
               // regular substitution
               else {
                  String str = (val == null) ? "" : val.toString();

                  line = line.substring(0, idx) + str +
                     line.substring(eidx + 1);
                  idx += str.length();
               }
            }
            else {
               break;
            }
         }

         writer.println(line);
      }

      writer.flush();
   }

   /**
    * Get an input stream to a resource or downloadable file.
    */
   public static InputStream getResourceOrFileInputStream(String res)
      throws IOException
   {
      InputStream inp = SreeEnv.class.getResourceAsStream(res);

      if(inp == null && res != null && !res.equals("")) {
         try {
            DataSpace space = DataSpace.getDataSpace();

            if(isValidResource(res)) {
               inp = space.getInputStream(null, res);
            }
            else {
               LOG.warn("Invalid resource: " + res);
            }
         }
         catch(Exception ex) {
            LOG.warn("Failed to open input stream for resource: " + res, ex);
         }
      }

      return inp;
   }

   /**
    * Copy a resource stream to output.
    */
   public static void copyResource(String res, OutputStream out,
                                   ServiceResponse resp) throws IOException {
      InputStream inp = getResourceOrFileInputStream(res);

      if(inp == null) {
         throw new FileNotFoundException("Resource not found: " + res);
      }

      if(resp != null && res.endsWith(".swf") &&
         (res.contains("ExploratoryAnalyzer") || res.contains("ExploreView")))
      {
         long size = inp.available();

         if(size > 0) {
            resp.setHeader("Content-Length", size + "");
         }
      }

      try {
         copyResource(inp, out);
      }
      finally {
         inp.close();
      }
   }

   /**
    * Copy a resource stream to output.
    */
   private static void copyResource(InputStream inp, OutputStream out) {
      try {
         byte[] buf = new byte[40960];
         int cnt;

         while((cnt = inp.read(buf)) > 0) {
            out.write(Tool.convertUserByte(buf), 0, cnt);
         }

         out.flush();
      }
      // @by donio, Tomcat throws a harmless socket exception on flush.
      // Here we swallow the exception so that the user won't be alarmed.
      catch(Exception e) {
      }
   }

   public static PrintWriter getWriter(ServiceRequest request,
                                       ServiceResponse resp)
      throws IOException
   {
      if(resp == null) {
         return null;
      }

      OutputStream out = resp.getOutputStream();

      if(out == null) {
         return null;
      }

      if(request != null) {
         String encoding = request instanceof HttpServiceRequest ?
            ((HttpServiceRequest) request).getHeader("accept-encoding") : null;

         // support gzip?
         if(encoding != null && encoding.indexOf("gzip") >= 0) {
            resp.setHeader("Content-Encoding" , "gzip");
            out = new java.util.zip.GZIPOutputStream(out);
         }
      }

      String encoding = SreeEnv.getProperty("html.character.encode");
      return new PrintWriter(new OutputStreamWriter(out, encoding));
   }

   /**
    * Add a cell to a table
    */
   private static String addTD(String width, String align, String content) {
      return "<td " + "width=\"" + width + "\" align=\"" + align + "\">" +
         content + "</td>\n";
   }

   /**
    * Check if the user agent is IE.
    */
   public static boolean isIE(HttpServletRequest req) {
      String agent = req.getHeader("User-Agent");

      return (agent != null) && (agent.indexOf("MSIE") >= 0 ||
         agent.indexOf("Trident") >= 0);
   }

   /**
    * Get a portal resource (css, js, or image) with the specified name, looking
    * in the appropriate theme and/or style directory.
    *
    * @param name the name of the resource
    * @param applyStyle <tt>true</tt> to look in the tab style directory
    * @param applyTheme <tt>true</tt> to look in the theme directory
    * @return  the resource stream
    * @throws IOException
    */
   public static InputStream getPortalResource(String name, boolean applyStyle,
                                               boolean applyTheme)
      throws IOException
   {
      boolean copySuccessful;

      // 1. Attempt to load resource with Theme/Style
      String fromResource = getResourceClassPath(name, applyStyle, applyTheme);
      String toPath = getResourcePortalPath(name, applyStyle, applyTheme);

      copySuccessful = copyResourceToPath(fromResource, toPath);

      // 2. Load resource without Theme/Style
      if(!copySuccessful) {
         fromResource = getResourceClassPath(name, false, false);
         toPath = getResourcePortalPath(name, false, false);

         copySuccessful = copyResourceToPath(fromResource, toPath);
      }

      if(!copySuccessful) {
         throw new FileNotFoundException(
            "Source or target resource missing: " + fromResource +
            ", " + toPath);
      }

      byte[] cssbuf = getPortalResourceAsByteArray(toPath);

      // if portal-theme.css exists in sree.home, append it to theme.css
      // so it can customize theme colors
      String customcss = SreeEnv.getPath("$(sree.home)/portal-theme.css");
      DataSpace space = DataSpace.getDataSpace();

      if(space.exists(null, customcss)) {
         byte[] custom = getPortalResourceAsByteArray(customcss);

         if(custom != null) {
            cssbuf = Tool.combine(cssbuf, custom);
         }
      }

      return new ByteArrayInputStream(cssbuf);
   }

   /**
    * Read file into a byte array.
    */
   private static byte[] getPortalResourceAsByteArray(String path) throws IOException {
      // @by jasons read resource into memory buffer to avoid file lock contention
      DataSpace space = DataSpace.getDataSpace();
      ByteArrayOutputStream output = new ByteArrayOutputStream();

      try(InputStream input = space.getInputStream(null, path)) {
         if(input == null) {
            return new byte[0];
         }

         IOUtils.copy(input, output);
      }

      return output.toByteArray();
   }

   /**
    * Gets the classpath for a resource.
    * @param name The name of the resource.
    * @param applyStyle <tt>true</tt> to look in the tab style directory
    * @param applyTheme <tt>true</tt> to look in the theme directory
    * @return The full resource path
    */
   private static String getResourceClassPath(String name, boolean applyStyle,
                                              boolean applyTheme) {
      String basePath = "/inetsoft/sree/portal/";
      String themeStyleDir = getThemeStylePath(applyStyle, applyTheme);
      boolean isCSS = name.contains(".css");
      boolean isJS = name.contains(".js");
      String typeDir = isCSS ? "css/" : (isJS ? "" : "images/");

      if(themeStyleDir == null) {
         return basePath + typeDir + name;
      }

      return basePath + typeDir + themeStyleDir + name;
   }

   /**
    * Gets the portal path for a resource.
    * @param name The name of the resource.
    * @param applyStyle <tt>true</tt> to look in the tab style directory
    * @param applyTheme <tt>true</tt> to look in the theme directory
    * @return The full portal path
    */
   private static String getResourcePortalPath(String name, boolean applyStyle,
                                               boolean applyTheme)
   {
      boolean isCSS = name.contains(".css");
      boolean isJS = name.contains(".js");
      String typeDir = isCSS ? "css/" : (isJS ? "" : "images/");
      String basePath = isJS ?
         SreeEnv.getPath("$(sree.home)/" + typeDir) :
         SreeEnv.getPath("$(sree.home)/portal/" + typeDir);

      String themeStyleDir = getThemeStylePath(applyStyle, applyTheme);

      if(themeStyleDir == null) {
         return basePath + name;
      }

      return basePath + themeStyleDir + name;
   }

   /**
    * Gets the theme and style subpath for a resource. E.g: aqua/blue/
    * @param applyStyle <tt>true</tt> to look in the tab style directory
    * @param applyTheme <tt>true</tt> to look in the theme directory
    * @return The theme & style path
    */
   private static String getThemeStylePath(boolean applyStyle, boolean applyTheme)
   {
      String theme = PortalThemesManager.getColorTheme();
      String themeStyleDir = null;

      // Construct "style/theme/" directory path, or leave null
      if(applyStyle) {
         themeStyleDir = "modern/";

         if(applyTheme && theme != null) {
            themeStyleDir = themeStyleDir + theme + "/";
         }
      }

      return themeStyleDir;
   }

   /**
    * Copies a classpath resource to the data space.
    *
    * @param fromResource  the resource to copy from
    * @param toDataSpacePath  the path to copy to
    * @return  <tt>true</tt> if successful, <tt>false</tt> otherwise
    * @throws IOException
    */
   private static boolean copyResourceToPath(String fromResource,
                                            String toDataSpacePath)
      throws IOException
   {
      DataSpace space = DataSpace.getDataSpace();

      synchronized(HTMLUtil.class) {
         if(!space.exists(null, toDataSpacePath)) {
            try(InputStream res = SreeEnv.class.getResourceAsStream(fromResource)) {
               if(res == null) {
                  return false;
               }

               space.withOutputStream(null, toDataSpacePath, out -> IOUtils.copy(res, out));
            }
            catch(Throwable exc) {
               throw new IOException(
                  "Failed to copy portal resource " + fromResource + " to " +
                  toDataSpacePath, exc);
            }
         }
      }

      return true;
   }

   /**
    * Check if the location at which the resource exists.
    * is in the permissible list of directories.
    */
   public static boolean isValidResource(String location) {
      DataSpace space = DataSpace.getDataSpace();

      if(space.exists(null, location)) {
         return true;
      }

      String imageProp = SreeEnv.getProperty("html.image.directory");
      String htmlProp = SreeEnv.getProperty("html.directory");
      String sreeProp = SreeEnv.getProperty("sree.home");

      FileSystemService fileSystemService = FileSystemService.getInstance();

      // @by amitm 2004-08-04
      // Allow all files from this predefined
      // image directory specified through the EM
      String imageDir = Tool.isEmptyString(imageProp) ? "null"
         : fileSystemService.getFile(imageProp).getAbsolutePath();
      // @by jasons make sure that res paths don't get discarded because
      // different file path separtors are used.
      String htmlDir = (htmlProp == null) ? "null"
         : fileSystemService.getFile(htmlProp).getAbsolutePath();
      String sreeDir = (sreeProp == null) ? "null"
         : fileSystemService.getFile(sreeProp).getAbsolutePath();

      String resFile =
         fileSystemService.getFile(Tool.convertUserFileName(location)).getAbsolutePath();

      // @by larryl, ignore the difference between slash and back slash
      resFile = resFile.replace('\\', '/');
      sreeDir = sreeDir.replace('\\', '/');
      htmlDir = htmlDir.replace('\\', '/');
      imageDir = imageDir.replace('\\', '/');

      // @by larryl, ignore case if on windows
      if(File.separatorChar == '\\') {
         resFile = resFile.toLowerCase();
         sreeDir = sreeDir.toLowerCase();
         htmlDir = htmlDir.toLowerCase();
         imageDir = imageDir.toLowerCase();
      }

      // @by larryl, security,
      // don't allow traversing up directory chain
      // @by amitm 2004-08-04
      // Allow image files from this predefined image directory
      return (resFile.startsWith(htmlDir) || resFile.startsWith(sreeDir) ||
	      resFile.startsWith(imageDir)) && location.indexOf("..") < 0;
   }

   /**
    * The parameter value should not contain special characters for script.
    * Preventing cross-site script.
    * @param pairs the map contains request pairs.
    */
   public static void validatePairs(Map<String, String[]> pairs) {
      if(pairs.get("op") == null) {
         return;
      }

      pairMap.put("flashmovie", new String[]{"windowId", "browserType"});
      pairMap.put("organizetree",
            new String[]{"entrytype", "snapshot", "scope", "entryversioned",
                         "entryversion", "version", "versioned"});

      String op = pairs.get("op")[0].toLowerCase();
      String[] checkNames = null;

      if(op.startsWith("portal_organize") &&
         !op.equals("portal_organize_content") &&
         !op.equals("portal_organize_tree"))
      {
         checkNames = pairMap.get("portal_organize");
      }
      else {
         checkNames = pairMap.get(op);
      }

      if(checkNames == null) {
         return;
      }

      for(int i = 0; i < checkNames.length; i++) {
         String[] values = pairs.get(checkNames[i]);

         if(values == null) {
            continue;
         }

         for(int j = 0; j < values.length; j++) {
            String val = values[j];

            if(val == null) {
               continue;
            }

            Matcher matcher = pattern.matcher(val.toLowerCase());

            if(matcher.find()) {
               String errorValue = matcher.group();
               throw new RuntimeException("Parameter [" + checkNames[i] +
                  "] contains an invalid value of [" +
                  Tool.encodeHTML(errorValue) + "].");
            }
         }
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(HTMLUtil.class);
   private static final String INVALID_REGEX =
      ".*(\"|'|\\)).*(;|\n|\\+|-|\\*|/|\\&|\\||\\^|%|=|,|<|>|\\?).*";
   private static final Pattern pattern = Pattern.compile(INVALID_REGEX);
   // the map<op, paramNames> keeps the parameter names which should be
   // checked within an operation.
   private static final Map<String, String[]> pairMap = new HashMap<>();
   private static final Set<String> resset = new HashSet<>(Arrays.asList(
      "png", "svg", "jpg", "jpeg", "bmp", "gif", "swf", "css", "js", "html",
      "htm", "apk", "eot", "ttf", "woff", "woff2"));

   static {
      pairMap.put("adhocwizard",
         new String[]{"mode", "rmode", "index", "status",});
      pairMap.put("archive", new String[]{"isVersioned", "isComposite"});
      pairMap.put("change", new String[]{"action", "elementType"});
      pairMap.put("dashboard_configuration", new String[]{"inplace"});
      pairMap.put("editor",
         new String[]{"initialDate", "name", "color", "font", "ruletype",
                      "ruledata", "acct", "previous", "resource", "sel_users",
                      "sel_roles", "type", "paramIndex", "paramStr",
                      "nodeType"});
      pairMap.put("embeddedtree", new String[]{"ReportListType"});
      pairMap.put("explorer_browse", new String[]{"uPID"});
      pairMap.put("explorer_contentframe", new String[]{"uPID", "view"});
      pairMap.put("explorer_filter", new String[]{"uPID", "view"});
      pairMap.put("explorer_frame", new String[]{"uPID", "view"});
      pairMap.put("explorer_load", new String[]{"uPID", "view"});
      pairMap.put("explorer_optiontb", new String[]{"uPID", "view"});
      pairMap.put("explorer_searchgoto", new String[]{"uPID"});
      pairMap.put("explorer_searchresult", new String[]{"uPID"});
      pairMap.put("explorer_select", new String[]{"uPID", "view"});
      pairMap.put("explorer_showsearch", new String[]{"uPID", "view"});
      pairMap.put("explorer_sort", new String[]{"uPID", "view"});
      pairMap.put("explorer_toolbar", new String[]{"uPID", "view"});
      pairMap.put("explorer_top", new String[]{"uPID", "view"});
      pairMap.put("export", new String[]{"loadingNo", "isURLExport", "name"});
      pairMap.put("export_template", new String[]{"page", "pageCnt"});

      pairMap.put("flashmovie", new String[]{"name", "browserType"});
      pairMap.put("frameeditor", new String[]{"operation", "type", "pn"});
      pairMap.put("framereplet",
         new String[]{"action", "pn", "mode", "loc", "create"});
      pairMap.put("framereport",
         new String[]{"action", "pn", "mode", "loc", "create", "versioned"});
      pairMap.put("generate",
         new String[]{"isdashboard", "req", "action", "uPID",
                      "__action__", "__applyclicked__", "__eventSource__"});
      pairMap.put("logschedule", new String[]{"lines", "showAll"});
      pairMap.put("logsree", new String[]{"lines", "showAll"});
      pairMap.put("mail_list", new String[]{"name"});
      pairMap.put("organizetree",
         new String[]{"entrytype", "snapshot", "scope", "entryversioned",
                      "entryversion", "version", "versioned"});
      pairMap.put("portal_dashboard", new String[]{"selectedtab", "type"});
      pairMap.put("portal_lookandfeel",
         new String[]{"colorScheme", "sort", "fontFamily",
                      "copyright", "logoStyle", "faviconStyle"});
      pairMap.put("portal_organize",
         new String[]{"filter", "newReport", "ReportListType", "pn",
                      "entrytype", "snapshot", "scope", "entryversioned",
                      "entryversion", "version", "versioned"});
      pairMap.put("portal_organize_content",
         new String[]{"filter", "newReport", "ReportListType", "pn"});
      pairMap.put("portal_organize_tree",
         new String[]{"filter", "newReport", "ReportListType", "pn"});
      pairMap.put("portal_portallist", new String[]{"newReport"});
      pairMap.put("portal_portaltree",
         new String[]{"filter", "newReport", "ReportListType", "pn"});
      pairMap.put("portal_report",
         new String[]{"filter", "newReport", "ReportListType", "pn"});
      pairMap.put("portal_portalwelcome", new String[]{"selectedtab", "type"});
      pairMap.put("previewprint", new String[]{"pn"});
      pairMap.put("prototype", new String[]{"desc", "tempText", "screText"});
      pairMap.put("refresh",
         new String[]{"ID", "mode", "rmode", "adhoc", "elemid", "clicked",
                      "target", "action", "__action__", "__applyclicked__",
                      "uPID", "create", "pn"});
      pairMap.put("replet",
         new String[]{"url", "target", "uPID", "userid", "newName", "oldName",
                      "user", "desc", "alias", "newFolder", "textCls",
                      "oldFolder", "previousURL", "ID"});
      pairMap.put("repletpdf", new String[]{"ID", "url", "target", "uPID"});
      pairMap.put("report",
         new String[]{"action", "__action__", "__applyclicked__",
                      "__eventSource__", "uPID"});
      pairMap.put("request",
         new String[]{"target", "create", "pn", "action", "__action__",
                      "__applyclicked__", "__eventSource__", "uPID", "ID"});
      pairMap.put("resetcluster",
         new String[]{"login", "user", "pw", "database", "url", "ilevel",
                     "driver", "class", "type"});
      pairMap.put("resource", new String[]{"name"});
      pairMap.put("resourcefile", new String[]{"name"});
      pairMap.put("saveas", new String[]{"mode", "rmode"});
      pairMap.put("saveas_browse", new String[]{"type"});
      pairMap.put("scheduler_log", new String[]{"lines"});
      pairMap.put("scheduler_tasks",
         new String[]{"idx", "action", "archive_format", "commentFetched",
                      "ruletype", "ruledata", "path", "type"});
      pairMap.put("toc", new String[]{"uPID"});
      pairMap.put("viewsheetweb",
         new String[]{"width", "height", "mime", "suffix"});
      pairMap.put("vs", new String[]{"theme", "path"});
   }
}
