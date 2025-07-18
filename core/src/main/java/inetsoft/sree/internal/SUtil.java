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
package inetsoft.sree.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.mv.fs.*;
import inetsoft.report.Hyperlink;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.license.*;
import inetsoft.report.io.viewsheet.snapshot.ViewsheetAsset2;
import inetsoft.sree.*;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.ignite.serializer.Object2ObjectOpenHashMapSerializer;
import inetsoft.sree.schedule.ScheduleClient;
import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.sree.security.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.*;
import inetsoft.uql.viewsheet.vslayout.DeviceInfo;
import inetsoft.uql.viewsheet.vslayout.DeviceRegistry;
import inetsoft.util.*;
import inetsoft.util.audit.*;
import inetsoft.util.dep.*;
import inetsoft.util.log.LogManager;
import inetsoft.web.RecycleUtils;
import inetsoft.web.admin.schedule.model.ServerLocation;
import inetsoft.web.admin.schedule.model.ServerPathInfoModel;
import inetsoft.web.viewsheet.service.LinkUriArgumentResolver;

import java.io.*;
import java.lang.ref.WeakReference;
import java.net.*;
import java.rmi.RemoteException;
import java.security.Principal;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import jakarta.servlet.http.*;
import org.apache.commons.io.IOUtils;
import org.apache.hc.core5.net.InetAddressUtils;
import org.apache.ignite.binary.BinaryTypeConfiguration;
import org.apache.ignite.configuration.BinaryConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.user.DestinationUserNameProvider;
import org.springframework.util.StringUtils;

/**
 * Common utility methods used in SREE.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class SUtil {
   /**
    * Analytic report prefix.
    */
   public static final String ANALYTIC_REPORT = "__ANALYTIC_REPORT__";
   /**
    * Preview report prefix.
    */
   public static final String PREVIEW_REPORT = "__PREVIEW_REPORT__";
   /**
    * My report prefix.
    */
   public static final String MY_REPORT = Tool.MY_DASHBOARD;
   /**
    * My report prefix.
    */
   public static final String MY_DASHBOARD = "My Portal Dashboards";
   /**
    * Report file folder.
    */
   public static final String REPORT_FILE = "Report Files";
   /**
    * Administrator ticket.
    */
   public static final String TICKET = "admin.security.ticket";
   public static final String LONGON_TIME = "user.logon.time";

   /**
    * Check if is my report.
    */
   public static boolean isMyReport(String name) {
      return name != null && (name.equals(MY_REPORT) || name.startsWith(MY_REPORT + "/"));
   }

   /**
    * Check if is my dashboard.
    */
   public static boolean isMyDashboard(String name) {
      return name != null && (name.equals(MY_DASHBOARD) || name.startsWith(MY_DASHBOARD + "/") ||
         name.equals(MY_REPORT) || name.startsWith(MY_REPORT + "/"));
   }

   /**
    * Check if is analytic report.
    */
   public static boolean isAnalyticReport(String name) {
      return name != null && name.startsWith(SUtil.ANALYTIC_REPORT);
   }

   /**
    * Check if is preview report.
    */
   public static boolean isPreviewReport(String name) {
      return name != null && name.startsWith(SUtil.PREVIEW_REPORT);
   }

    /**
    * Get the non-prefixed portion of the scoped path.
    * For a repository entry it would return the path of its asset entry
    * <p>
    * For example,
    * Repository/Examples/Census -> Examples/Census
    */
   public static String getUnscopedPath(String path) {
      if(!(path.startsWith(RepositoryEntry.REPOSITORY_FOLDER) || path.startsWith(Tool.MY_DASHBOARD) ||
         path.startsWith(SUtil.MY_DASHBOARD)))
      {
         return path;
      }

      String parent =
         path.startsWith(RepositoryEntry.REPOSITORY_FOLDER) ? RepositoryEntry.REPOSITORY_FOLDER :
            path.startsWith(Tool.MY_DASHBOARD) ? Tool.MY_DASHBOARD : SUtil.MY_DASHBOARD;

      if(path.startsWith(parent + "/")) {
         path = path.substring(parent.length() + 1);

         if(path.startsWith(RepositoryEntry.WORKSHEETS_FOLDER + "/")) {
            path = path.substring(RepositoryEntry.WORKSHEETS_FOLDER.length() + 1);
         }

         return path;
      }
      else {
         return path;
      }
   }

   /**
    * Try getting replet engine.
    */
   public static RepletEngine getRepletEngine(AnalyticRepository rep) {
      return rep instanceof RepletEngine ? (RepletEngine) rep : null;
   }

   /**
    * check if the String is null.
    */
   public static boolean isEmptyString(String str) {
      return str == null || str.trim().length() == 0;
   }

   /**
    * Gets the analytic repository.
    *
    * @return the repository.
    */
   public static AnalyticRepository getRepletRepository() {
      return SingletonManager.getInstance(AnalyticRepository.class);
   }

   /**
    * Start distributed server.
    */
   public static synchronized void startServerNode() {
      ConfigurationContext configuration =
         ConfigurationContext.getContext();

      if(Boolean.TRUE.equals(configuration.get(FS_STARTED_KEY))) {
         return;
      }

      configuration.put(FS_STARTED_KEY, true);

      new GroupedThread() {
         @Override
         protected void doRun() {
            XServerNode server = FSService.getServer();

            if(server == null) {
               return;
            }

            FSService.getDataNode();
         }
      }.start();
   }

   public static String computeServerClusterNode(String server) {
      if(StringUtils.isEmpty(server)) {
         return server;
      }

      int index = server.indexOf(':');
      String hostName;

      if(index < 0) {
         hostName = server;
      }
      else {
         hostName = server.substring(0, index);
      }

      Cluster cluster = Cluster.getInstance();
      String addr;

      if(hostName.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")) {
         addr = hostName;
      }
      else {
         // host name, look up
         try {
            InetAddress address = InetAddress.getByName(hostName);
            addr = address.getHostAddress();
         }
         catch(UnknownHostException e) {
            addr = cluster.getByName(hostName);

            if(addr != null) {
               return addr;
            }

            LOG.warn("Failed to get server address: " + hostName, e);
         }
      }

      for(String node : cluster.getClusterNodes()) {
         if(cluster.getClusterNodeHost(node).equals(addr) &&
            !Boolean.TRUE.equals(cluster.getClusterNodeProperty(node, "scheduler")))
         {
            return node;
         }
      }

      return null;
   }

   /**
    * Get a input stream from a file or resource.
    */
   public static InputStream getInputStream(String prop) throws Exception {
      InputStream input = null;
      File file = FileSystemService.getInstance().getFile(prop);

      if(file.exists()) {
         input = new FileInputStream(prop);
      }

      // try to load it from dataspace
      if(input == null) {
         DataSpace space = DataSpace.getDataSpace();
         input = space.getInputStream(null, prop);

         if(input != null) {
            LOG.debug("Loading from dataspace: {}", prop);
         }
      }

      // try to load it as a resource
      if(input == null) {
         input = DefaultContext.class.getResourceAsStream(prop);

         if(input != null) {
            LOG.debug("Loading as resource: {}", prop);
         }
      }
      else {
         LOG.debug("Loading from file: {}", file.getAbsolutePath());
      }

      // try the file name as a top level resource
      if(input == null) {
         input = DefaultContext.class.getResourceAsStream("/" + file.getName());

         if(input != null) {
            LOG.debug("Loading as resource: /{}", file.getName());
         }
      }

      return input;
   }

   /**
    * Verify a log file. If the file does not exist and can not be created,
    * create a directory in the user home directory instead and return the
    * new path.
    * @param path directory setting from the configuration.
    * @param name default directory name.
    */
   public static String verifyLog(String path, String name) {
      // check if no directory, add sree.home to front
      if(!path.contains("/") && !path.contains(File.separator)) {
         path = SreeEnv.getProperty("sree.home") + File.separator + path;
      }

      FileSystemService fileSystemService = FileSystemService.getInstance();
      File file = fileSystemService.getFile(path);
      String dir = file.getParent();
      if(dir == null) {
         dir = ".";
      }

      if((fileSystemService.getFile(dir)).exists()) {
         return path;
      }

      dir = Util.verifyDirectory(dir, "log");
      LOG.debug("Create log: {}", dir);
      return dir + File.separator + name;
   }

   /**
    * Return the folder portion of a path in a report archive.
    */
   public static String getFolder(String path) {
      if(path == null) {
         return "/";
      }

      int idx = path.lastIndexOf('/');

      return (idx > 0) ? path.substring(0, idx) : "/";
   }

   /**
    * Sets a user's password. The password is hashed using the bcrypt algorithm.
    *
    * @param user     the user.
    * @param password the clear text password.
    */
   public static void setPassword(FSUser user, String password) {
      HashedPassword hash = Tool.hash(password, "bcrypt");
      user.setPassword(hash.getHash());
      user.setPasswordAlgorithm(hash.getAlgorithm());
      user.setPasswordSalt(null);
      user.setAppendPasswordSalt(false);
   }

   /**
    * Check if the user agent is IE.
    */
   public static boolean isIE(String agent) {
      return (agent != null) && (agent.indexOf("MSIE") >= 0 ||
         // @by yanie: for IE11+
         agent.indexOf("Trident") >= 0);
   }

   /**
    * Check if the user agent is Nescape6.
    */
   public static boolean isMozilla(String agent) {
      return agent != null && agent.indexOf("Mozilla") >= 0 &&
         agent.indexOf("Gecko") >= 0;
   }

   /**
    * Get the URL with servlet and operator.
    */
   public static String getURL(String servletName) {
      return servletName + (servletName.indexOf('?') >= 0 ? "&" : "?");
   }

   /**
    * Get the viewsheet toolbar elements.
    * @return the toolbar elements stored in a <code>List</code> instance.
    */
   public static List<ToolBarElement> getVSToolBarElements(boolean forceGlobal) {
      List<ToolBarElement> elems = new ArrayList<>(ToolBarElement.VSTOOLBAR_ELEMENTS.length);

      for(int i = 0; i < ToolBarElement.VSTOOLBAR_ELEMENTS.length; i++) {
         String name = ToolBarElement.VSTOOLBAR_ELEMENTS[i];

         if("vs.import.button".equals(name) && !LicenseManager.isComponentAvailable(LicenseManager.LicenseComponent.FORM)) {
            continue;
         }

         String visible = SreeEnv.getProperty(name, forceGlobal, "true");
         ToolBarElement elem = new ToolBarElement();

         elem.name = name;
         elem.visible = visible;
         elem.index = i;
         elem.id = ToolBarElement.VSTOOLBAR_ELEMENT_ID[i];

         elems.add(elem);
      }

      return elems;
   }

   /**
    * Represents an element in toolbar.
    */
   public static class ToolBarElement implements Comparable<ToolBarElement> {
      @Override
      public int compareTo(ToolBarElement obj) {
         if(obj == null) {
            return Integer.MAX_VALUE;
         }

         // always return close button at the end of list
         return index - obj.index;
      }

      @Override
      public String toString() {
         return name.replace('.', '_') + '~' + visible + '~' + id;
      }

      public String name = null;
      public String visible = null;
      public int index = 0;
      public String id = null;
      static final String[] VSTOOLBAR_ELEMENTS = {
         "vs.previous.button", "vs.next.button", "vs.edit.button",
         "vs.refresh.button", "vs.email.button", "vs.share.button", "vs.schedule.button",
         "vs.export.button", "vs.import.button", "vs.print.button",
         "vs.zoom", "vs.fullScreen.button", "vs.close.button",
         "vs.bookmark.button"
      };
      static final String[] VSTOOLBAR_ELEMENT_ID = {
         "Previous", "Next", "Edit", "Refresh", "Email", "Social Sharing", "Schedule",
         "Export", "Import", "Print", "Zoom", "Full Screen", "Close", "Bookmark"};
   }

   /**
    * Get the classpath of the web application, if possible.
    * @return the web application's classpath or "." if it could not be
    * obtained.
    */
   public static String getApplicationClasspath() {
      String cp = null;
      ClassLoader cl = Thread.currentThread().getContextClassLoader();

      if(cl instanceof URLClassLoader) {
         StringBuilder sb = new StringBuilder();
         getApplicationClasspath((URLClassLoader) cl, sb);

         if(sb.length() == 0) {
            cp = ".";
         }
         else {
            cp = sb.toString();
         }
      }
      else {
         cp = ".";
      }

      if(cp.isEmpty() || ".".equals(cp)) {
         return System.getProperty("java.class.path", ".");
      }

      return cp;
   }

   /**
    * Recursive function to find the web application's classpath.
    * @param cl the ClassLoader to get path elements from.
    * @param sb the StringBuilder to append the classpath to.
    */
   private static void getApplicationClasspath(URLClassLoader cl,
                                               StringBuilder sb) {
      URL[] urls = (cl).getURLs();

      for(int i = 0; i < urls.length; i++) {
         String url = urls[i].toExternalForm();

         if(url.startsWith("file:/") || url.startsWith("jar:file:///")) {
            if(sb.length() > 0) {
               sb.append(File.pathSeparatorChar);
            }

            int length = url.startsWith("file:/") ? "file:/".length() : "jar:file:///".length();
            String path = null;

            try {
               path = java.net.URLDecoder.decode(
                  url.substring(length),
                  SreeEnv.getProperty("html.character.encode"));
            }
            catch(UnsupportedEncodingException exc) {
               // shouldn't happen with configured encoding or default UTF8
               LOG.error("Unsupported character set, Please check" +
                  " the property \"html.character.encode\" in configuration" +
                  " file", exc);
               path = url.substring(length);
            }

            if(path.endsWith("!/")) {
               path = path.substring(0, path.length() - 2);
            }

            // @by larryl, on unix, the leading slash is not added to the
            // string so we must add it since the file: url is always absolute
            // path
            if(File.separatorChar == '/' && !path.startsWith("/")) {
               sb.append("/");
            }

            sb.append(path);
         }
      }

      if(cl.getParent() != null && cl.getParent() instanceof URLClassLoader) {
         getApplicationClasspath((URLClassLoader) cl.getParent(), sb);
      }
   }

   /**
    * Get the home directory name from a URL path.
    * @param url the URL path.
    */
   @SuppressWarnings("unused")
   private static String normalizeURL(String url) {
      String dir = null;

      if(url.startsWith("jar:")) {
         dir = url.substring(10);
      }
      else if(url.startsWith("zip:")) {
         dir = url.substring(4);
      }
      else {
         throw new IllegalArgumentException("Unsupported protocol");
      }

      char[] buffer = new char[dir.length()];

      // replace invalid characters
      for(int i = 0; i < buffer.length; i++) {
         char c = dir.charAt(i);

         buffer[i] = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
            (c >= '0' && c <= '9') ?
            c :
            '_';
      }

      return new String(buffer);
   }

   /**
    * Get the groups.
    */
   public static String[] getGroups(IdentityID userId) {
      try {
         SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();

         if(provider == null) {
            return new String[0];
         }

         return provider.getUserGroups(userId);
      }
      catch(Exception ex) {
         LOG.error("Failed to get groups for user: " + userId, ex);
         return new String[0];
      }
   }

   /**
    * Check if target variable should be prompt.
    * @param principal the current principal.
    * @param variable the target variable.
    */
   public static boolean isNeedPrompt(Principal principal, UserVariable variable) {
      if(principal instanceof SRPrincipal) {
         SRPrincipal sp = (SRPrincipal) principal;

         if(sp.getParameter(variable.getName()) != null) {
            return false;
         }
      }

      return true;
   }

   /**
    * Check user session timeout.
    */
   public static boolean checkUserSessionTimeout() {
      if(userSessions.size() <= 0) {
         return false;
      }

      long tout = getUserSessionTimeout();

      if(tout <= 0) {
         return false;
      }

      boolean removed = false;

      synchronized(userSessions) {
         for(int i = 0; i < userSessions.size(); i++) {
            HttpSession session = userSessions.get(i).get();

            if(session == null) {
               userSessions.remove(i);
               i--;
               continue;
            }

            Long ts = null;

            try {
               ts = (Long) session.getAttribute("__touch_session_time__");
            }
            catch(Throwable ex) {
               // exception thrown by expired session (IllegalStateException)
               LOG.debug("Session expired", ex);
            }

            if(ts == null) {
               userSessions.remove(i);
               i--;
               continue;
            }

            if(System.currentTimeMillis() - ts > tout) {
               Principal principal = (Principal) session.getAttribute(
                  RepletRepository.PRINCIPAL_COOKIE);

               // do not invalidate session, this will cause EM logout
               if(principal != null) {
                  try {
                     logout(principal);
                     // @by stephenwebster, For Bug #715
                     // Remove certain session attributes required to put
                     // a user back into the userSessions list.
                     // Otherwise the user can get orphaned in the session
                     // license manager.
                     // Arguably, all the attributes should be removed, but for
                     // now this will resolve the bug.
                     session.removeAttribute("__touch_session_time__");
                     session.removeAttribute("__check_session_timout__");
                  }
                  catch(Exception ex) {
                     // ignore it
                  }
               }

               userSessions.remove(i);
               i--;
               removed = true;
            }
         }
      }

      return removed;
   }

   public static int getUserSessionTimeout() {
      int sessionTimeout = 0;

      try{
         sessionTimeout = Integer.parseInt(
         SreeEnv.getProperty("repository.user.timeout"));
      }
      catch(Exception e) {
         sessionTimeout = 0;
      }

      return sessionTimeout;
   }

   public static String getUserDestination(Principal principal) {
      String destination = null;

      if(principal instanceof DestinationUserNameProvider) {
         destination = ((DestinationUserNameProvider) principal).getDestinationUserName();
      }
      else if(principal != null) {
         destination = principal.getName();
      }

      return destination;
   }

   public static Principal createEMPrincipal(Principal defaultPrincipal) {
      return (Principal) ((SRPrincipal)defaultPrincipal).clone();
   }

   /**
    * Get principal.
    */
   public static Principal getPrincipal(IdentityID remoteUser, String remoteAddr,
                                        HttpSession session, boolean fireEvent) {
      Principal principal = (Principal) session.getAttribute(
         RepletRepository.PRINCIPAL_COOKIE);

      if(principal == null) {
         principal = getPrincipal(remoteUser, remoteAddr, session.getId(), fireEvent, null);
         session.setAttribute(RepletRepository.PRINCIPAL_COOKIE, principal);
      }

      return principal;
   }

   /**
    * Get principal.
    */
   public static SRPrincipal getPrincipal(IdentityID remoteUser, String remoteAddr, boolean fireEvent) {
      return getPrincipal(remoteUser, remoteAddr, (String) null, fireEvent, null);
   }

   /**
    * Get principal.
    */
   public static SRPrincipal getPrincipal(IdentityID remoteUser, String remoteAddr, String sessionId,
                                          boolean fireEvent, Locale locale)
   {
      return getPrincipal(remoteUser, remoteAddr, sessionId, fireEvent, locale, true);
   }

   /**
    * Get principal.
    */
   public static SRPrincipal getPrincipal(IdentityID remoteUserID, String remoteAddr, String sessionId,
                                          boolean fireEvent, Locale locale,
                                          boolean includeAdditionalDatasources)
   {
      SRPrincipal res = null;
      SecurityProvider provider = null;

      if(remoteUserID != null) {
         try {
            SecurityEngine engine = SecurityEngine.getSecurity();
            provider = engine.getSecurityProvider();

            User user = provider.getUser(remoteUserID);

            if(user != null) {
               String orgID = remoteUserID.name.equals("INETSOFT_SYSTEM") && remoteUserID.orgID == null ?
                  Organization.getDefaultOrganizationID() : remoteUserID.orgID;
               res = new SRPrincipal(new ClientInfo(remoteUserID, remoteAddr, sessionId, locale),
                  user.getRoles(), user.getGroups(), orgID,
                  getRandom().nextLong(), user.getAlias());

               if(includeAdditionalDatasources) {
                  SUtil.setAdditionalDatasource(res);
               }

               UserProvider userProvider = UserProvider.getInstance();

               if(userProvider != null) {
                  res = userProvider.configurePrincipal(user, res);
               }

               res.setProperty("__internal__", "true");
            }
            else {
               // for sso user.
               String orgID = remoteUserID.name.equals("INETSOFT_SYSTEM")  && remoteUserID.orgID == null ?
                  Organization.getDefaultOrganizationID() :
                  provider.getOrganization(remoteUserID.orgID).getId();
               res = new SRPrincipal(new ClientInfo(remoteUserID, remoteAddr, sessionId, locale),
                                     new IdentityID[0], new String[0], orgID,
                                     getRandom().nextLong(), null);

               if(includeAdditionalDatasources) {
                  SUtil.setAdditionalDatasource(res);
               }
            }
         }
         catch(Exception ex) {
            LOG.error("Failed to get principal for user " +
                         remoteUserID + " at " + remoteAddr, ex);
         }
      }

      if(res == null) {
         ClientInfo clientInfo;

         if(Identity.UNKNOWN_USER.equals(remoteUserID.name)) {
            clientInfo = new ClientInfo(new IdentityID(Identity.UNKNOWN_USER, remoteUserID.orgID), remoteAddr, sessionId, locale);
         }
         else if(XPrincipal.SYSTEM.equals(remoteUserID.name)) {
            clientInfo = new ClientInfo(new IdentityID(XPrincipal.SYSTEM, remoteUserID.orgID), remoteAddr, sessionId, locale);
         }
         else {
            clientInfo = new ClientInfo(new IdentityID(ClientInfo.ANONYMOUS, remoteUserID.orgID), remoteAddr, sessionId, locale);
         }

         res = new SRPrincipal(
            clientInfo, new IdentityID[0], new String[0], null, getRandom().nextLong());
         res.setProperty("__internal__", "true");

         if(!provider.getAuthenticationProvider().isVirtual()) {
            res.setProperty("__ignore__", "true");
         }
      }

      try {
         SecurityEngine engine = SecurityEngine.getSecurity();

         if(engine != null && fireEvent) {
            engine.fireLoginEvent(res);
         }
      }
      catch(Exception ex) {
         LOG.warn("Failed to fire login event", ex);
      }

      return res;
   }

   /**
    * Get the principal using an identity;
    * @param iden the identity object.
    * @return the specified principal.
    */
   public static SRPrincipal getPrincipal(Identity iden, String remoteAddr,
                                          boolean fireEvent) {
      if(iden == null) {
         return null;
      }

      SRPrincipal principal = null;

      if(iden.getType() == Identity.USER) {
         principal = getPrincipal(iden.getIdentityID(), remoteAddr, fireEvent);

         if(XPrincipal.SYSTEM.equals(iden.getName()) && iden.getOrganizationID() != null &&
            !iden.getOrganizationID().equals(principal.getOrgId()))
         {
            // MV analysis/generation uses INETSOFT_SYSTEM user with the organization set
            principal.setOrgId(iden.getOrganizationID());
         }

         principal.setProperty("identity_type", "user");
      }
      else if(iden.getType() == Identity.GROUP) {
         IdentityID group = iden.getIdentityID();
         SecurityEngine engine = SecurityEngine.getSecurity();
         String orgID = group.orgID;

         if(orgID == null) {
            orgID = Organization.getDefaultOrganizationID();
         }

         principal = new SRPrincipal(new ClientInfo(group, remoteAddr),
            iden.getRoles(), new String[] {group.name}, orgID, getRandom().nextLong());
         principal.setProperty("identity_type", "group");
         principal.setProperty("virtual", "true");
         principal.setProperty("__internal__", "true");
         setAdditionalDatasource(principal);
      }
      else if(iden.getType() == Identity.ROLE) {
         IdentityID role = iden.getIdentityID();
         SecurityEngine engine = SecurityEngine.getSecurity();
         String orgID = iden.getOrganizationID();

         if(orgID == null) {
            orgID = Organization.getDefaultOrganizationID();
         }
         else {
            Organization org = engine.getSecurityProvider().getOrganization(orgID);

            if(org == null) {
               orgID = Organization.getDefaultOrganizationID();
            }
            else {
               orgID = org.getId();
            }
         }

         principal = new SRPrincipal(new ClientInfo(role, remoteAddr),
            new IdentityID[] {role}, iden.getGroups(), orgID, getRandom().nextLong());
         principal.setProperty("identity_type", "role");
         principal.setProperty("virtual", "true");
         principal.setProperty("__internal__", "true");
         setAdditionalDatasource(principal);
      }

      principal.setIgnoreLogin(true);

      return principal;
   }

   /**
    * Check whether the principal is internal user of provider.
    *
    * @param principal user.
    */
   public static boolean isInternalUser(Principal principal) {
      return principal instanceof XPrincipal &&
         "true".equals(((XPrincipal) principal).getProperty("__internal__"));
   }

   /**
    * Set additional data sources.
    */
   public static void setAdditionalDatasource(XPrincipal principal) {
      if(principal == null || !isSecurityOn()) {
         return;
      }

      boolean ignoreLogin = false;

      if(principal instanceof SRPrincipal) {
         ignoreLogin = principal.isIgnoreLogin();
         principal.setIgnoreLogin(true);
      }

      DataSourceRegistry registry = DataSourceRegistry.getRegistry();
      Principal oPrincipal = ThreadContext.getContextPrincipal();

      try {
         ThreadContext.setContextPrincipal(principal);

         SecurityEngine engine = SecurityEngine.getSecurity();
         String[] names = registry.getDataSourceFullNames();
         String orgID = principal.getCurrentOrgId();

         for(String name : names) {
            XDataSource ds = registry.getDataSource(name, orgID);

            if(ds instanceof AdditionalConnectionDataSource) {
               AdditionalConnectionDataSource<?> jds = (AdditionalConnectionDataSource<?>) ds;
               String[] children = jds.getDataSourceNames();

               for(String child : children) {
                  String resource = name + "::" + child;

                  if(engine.checkPermission(
                     principal, ResourceType.DATA_SOURCE, resource, ResourceAction.READ))
                  {
                     principal.setProperty(resource, "r");
                  }
               }
            }
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to get additional data sources for user: " + principal, ex);
      }
      finally {
         ThreadContext.setContextPrincipal(oPrincipal);
      }

      if(principal instanceof SRPrincipal) {
         principal.setIgnoreLogin(ignoreLogin);
      }
   }

   /**
    * convert user name to user email.
    */
   public static String convertEmailsString(String emailStr) {
      if(emailStr == null || emailStr.length() == 0) {
         return "";
      }

      Set<String> userEmails = new LinkedHashSet<>();
      emailStr = Tool.replaceAll(emailStr, ";", ",");
      String[] notifications = Tool.split(emailStr, ',');

      if(notifications.length == 0) {
         return "";
      }

      for(String notification : notifications) {
         if(!Tool.matchEmail(notification)) {
            try {
               String[] emails = SUtil.getEmails(new IdentityID(notification, OrganizationManager.getInstance().getCurrentOrgID()));

               if(emails.length == 0) {
                  LOG.warn("No email address specified for: {}", notification);
               }

               userEmails.addAll(Arrays.asList(emails));
            }
            catch(Exception e) {
               LOG.error("Failed to add emails from notification: " +
                  notification, e);
            }
         }
         else {
            userEmails.add(notification);
         }
      }

      return Tool.arrayToString(userEmails.toArray());
   }

   /**
    * Get emails of a specified user.
    */
   public static String[] getEmails(IdentityID name) throws Exception {
      return getEmails(name, null);
   }

   /**
    * Get emails of a specified user.
    */
   public static String[] getEmails(IdentityID identityID, SRPrincipal user)
      throws Exception
   {
      user = user == null ? SUtil.getPrincipal(identityID, null, null, false, null, false) : user;
      String email = (String) UserEnv.getProperty(user, "email");

      if(email != null && email.length() > 0) {
         return new String[] {email};
      }
      else {
         SecurityProvider security =
            SecurityEngine.getSecurity().getSecurityProvider();

         if(security == null) {
            return new String[0];
         }

         if(identityID.name.endsWith(Identity.GROUP_SUFFIX)) {
            List<String> emails = new ArrayList<>();
            IdentityID id = new IdentityID(identityID.name.substring(0, identityID.name.lastIndexOf(Identity.GROUP_SUFFIX)), identityID.orgID);

            addGroupEmails(security, id, emails);

            return emails.toArray(new String[0]);
         }
         else if(identityID.name.endsWith(Identity.USER_SUFFIX)) {
            IdentityID userID = new IdentityID(identityID.name.substring(0, identityID.name.lastIndexOf(Identity.USER_SUFFIX)), identityID.orgID);
            User user0 = security.getUser(userID);

            return (user0 == null) ? new String[0] : user0.getEmails();
         }

         User user0 = security.getUser(identityID);

         return (user0 == null) ? new String[0] : user0.getEmails();
      }
   }

   private static void addGroupEmails(SecurityProvider security, IdentityID groupID, List<String> emails) {
      Identity[] groupMembers = security.getGroupMembers(groupID);

      for(Identity groupMember : groupMembers) {
         if(groupMember instanceof User) {
            String[] userEmails = ((User) groupMember).getEmails();

            if(userEmails != null && userEmails.length > 0) {
               Arrays.stream(userEmails)
                  .filter(email -> !emails.contains(email))
                  .forEach(email -> emails.add(email));
            }
         }
         else if(groupMember instanceof Group) {
            addGroupEmails(security, groupMember.getIdentityID(), emails);
         }
      }
   }

   /**
    * Check emails.
    */
   public static boolean checkMail(String mails, boolean userEmail)
      throws Exception
   {
      String emails = mails == null ? "" : mails;

      if(userEmail) {
         List<String> userEmails = new ArrayList<>();
         String[] toAddrs = Tool.split(mails, ',');

         for(int i = 0; i < toAddrs.length; i++) {
            if(toAddrs[i].indexOf('@') < 0) {
               String[] uemails = getEmails(new IdentityID(toAddrs[i], OrganizationManager.getInstance().getCurrentOrgID()));

               for(int j = 0; uemails != null && j < uemails.length; j++) {
                  if(!userEmails.contains(uemails[j])) {
                     userEmails.add(uemails[j]);
                  }
               }
            }
            else {
               if(!userEmails.contains(toAddrs[i])) {
                  userEmails.add(toAddrs[i]);
               }
            }
         }

         emails = Tool.arrayToString(userEmails.toArray());
      }

      Catalog catalog = Catalog.getCatalog();

      if(emails.trim().length() == 0) {
         throw new Exception(catalog.getString("Test Mail No Address"));
      }

      String subject = catalog.getString("Test Mail Subject");
      String body = catalog.getString("Test Mail Body",
         SimpleDateFormat.getDateInstance(
         SimpleDateFormat.FULL).format(new Date()));
      Mailer mailer = new Mailer();
      mailer.send(emails, null, subject, body, null);

      return true;
   }

   /**
    * Check if security is turned on.
    * @return <tt>true</tt> if it's turned on, false otherwise.
    */
   public static boolean isSecurityOn() {
      // @by billh, if security is null, or if is designer and
      // should not use security, here we do not apply principal
      try {
         SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();
         return provider != null && !provider.isVirtual();
      }
      catch(Exception ex) {
         LOG.error("Failed to determine if security is enabled", ex);
      }

      return false;
   }

   /**
    * Get user id for session record.
    */
   public static IdentityID getUserID(IdentityID clientUser, IdentityID user) {
      if(user == null) {
         return clientUser;
      }

      String userName = user == null || user.name.equals("") || clientUser.equals(user) ?
         clientUser.name : clientUser.name + "(" + user.name + ")";
      return new IdentityID(userName, user != null ? user.orgID : OrganizationManager.getInstance().getCurrentOrgID());
   }

   // ChrisS bug1382576675872 2014-6-4
   // Split getSessionRecord() functionality out from runTask(),
   // so getActionRecord() can also use it.
   /**
    * Return a SessionRecord for the supplied principal and host address.
    */
   private static SessionRecord getSessionRecord(Principal principal, String addr, String locale) {
      if(principal == null) {
         return null;
      }

      SessionRecord sessionRecord = null;
      IdentityID userID = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());

      if(principal instanceof SRPrincipal) {
         SRPrincipal srp = (SRPrincipal) principal;
         locale = LocaleService.getInstance().getLocale(locale, principal);
         srp.setProperty(SRPrincipal.LOCALE, locale);
         srp.setIgnoreLogin(true);
         userID = SUtil.getUserID(srp.getClientUserID(), userID);
      }

      if(principal instanceof XPrincipal) {
         XPrincipal xprin = (XPrincipal) principal;
         sessionRecord = new SessionRecord();
         sessionRecord.setUserID(userID.convertToKey());
         sessionRecord.setUserHost(addr);
         sessionRecord.setUserGroup(Arrays.asList(xprin.getGroups()));
         sessionRecord.setUserRole(Arrays.stream(xprin.getRoles())
                                      .map(IdentityID::getName)
                                      .collect(Collectors.toList()));
         sessionRecord.setUserSessionID(xprin.getSessionID());
         sessionRecord.setOpType(SessionRecord.OP_TYPE_TASKLOGON);
         sessionRecord.setOpTimestamp(new Timestamp(System.currentTimeMillis()));
         sessionRecord.setOpStatus(SessionRecord.OP_STATUS_FAILURE);
      }

      return sessionRecord;
   }

   /**
    * Record user creation when run a task.
    */
   public static void runTask(Principal principal, ScheduleTask task,
                              String addr) throws Throwable
   {
      String olocale = null;

      if(principal instanceof SRPrincipal) {
         olocale = ((SRPrincipal) principal).getProperty(SRPrincipal.LOCALE);
      }

      try {
         SessionRecord sessionRecord =
            getSessionRecord(principal, addr, task.getLocale());

         try {
            task.run(principal);

            if(sessionRecord != null) {
               sessionRecord.setOpStatus(SessionRecord.OP_STATUS_SUCCESS);
            }
         }
         catch(Throwable e) {
            if(sessionRecord != null) {
               sessionRecord.setOpStatus(SessionRecord.OP_STATUS_FAILURE);
               sessionRecord.setOpError(e.getMessage());
            }

            throw e;
         }

         if(sessionRecord != null) {
            Audit.getInstance().auditSession(sessionRecord, principal);
         }
      }
      finally {
         if(principal instanceof SRPrincipal) {
            ((SRPrincipal) principal).setProperty(SRPrincipal.LOCALE, olocale);
         }
      }
   }

   /**
    * Check if the specified entry is duplicated.
    * @param engine the specified engine.
    * @param entry the specified entry.
    * @return <tt>true</tt> if duplicated, <tt>false</tt> otherwise.
    */
   public static boolean isDuplicatedEntry(AssetRepository engine,
                                           AssetEntry entry)
      throws Exception
   {
      return AssetUtil.isDuplicatedEntry(engine, entry);
   }

   private static String getMyReportPath(String path) {
      path = path.substring(MY_REPORT.length());

      if(path.startsWith("/")) {
         path = path.substring(1);
      }

      return path;
   }

   public static String removeControlChars(String value) {
      return value.replaceAll("\\p{Cntrl}", "");
   }

   /**
    * Check if the specified entry is duplicated.
    * @param engine the specified engine.
    * @param nname the specified path.
    * @return <tt>true</tt> if duplicated, <tt>false</tt> otherwise.
    */
   public static boolean isDuplicatedViewsheet(AssetRepository engine,
      String nname, IdentityID user)
      throws Exception
   {
      boolean isMyReport = isMyReport(nname);
      int scope = isMyReport ?
         AssetRepository.USER_SCOPE : AssetRepository.GLOBAL_SCOPE;
      AssetEntry.Type type = AssetEntry.Type.VIEWSHEET;
      String path = isMyReport ? getMyReportPath(nname) : nname;
      IdentityID userID = isMyReport ? user : null;
      AssetEntry entry = new  AssetEntry(scope, type, path, userID);

      return AssetUtil.isDuplicatedEntry(engine, entry);
   }

   /**
    * Get the random number generator for principals.
    */
   public static Random getRandom() {
      return Tool.getSecureRandom();
   }

   /**
    * Find idle file name in data space.
    */
   public static String findIdleFileNameInSpace(String root, String name, String suffix) {
      DataSpace space = DataSpace.getDataSpace();
      int index = name.lastIndexOf('/');
      String fname = (index < 0) ? name : name.substring(index + 1);
      String ext = suffix == null ? "" : suffix;

      for(;;) {
         String temp = fname + ext;

         if(!space.exists(root, temp)) {
            break;
         }

         ext = (System.currentTimeMillis() % 1000) + ext;
      }

      return fname + ext;
   }

   /**
    * Gets the editable authentication module for the specified security
    * provider.
    *
    * @param security the security provider.
    *
    * @return the editable authentication module or <code>null</code> if the
    *         provider's authentication module is not editable.
    */
   public static EditableAuthenticationProvider
      getEditableAuthenticationProvider(SecurityProvider security)
   {
      EditableAuthenticationProvider result = null;

      if(security instanceof AbstractSecurityProvider) {
         AuthenticationProvider auth = security.getAuthenticationProvider();
         if(auth instanceof EditableAuthenticationProvider) {
            result = (EditableAuthenticationProvider) auth;
         }

         if(auth instanceof AuthenticationChain) {
            for(Object provider : ((AuthenticationChain) auth).getProviders()) {
               if(provider instanceof EditableAuthenticationProvider) {
                  result = (EditableAuthenticationProvider) provider;
                  break;
               }
            }
         }
      }

      return result;
   }

   /**
    * Gets the user editable authentication module for the specified security
    * provider.
    *
    * @param security the security provider.
    * @param user the user identityID.
    *
    * @return the editable authentication module or <code>null</code> if the
    *         provider's authentication module is not editable.
    */
   public static EditableAuthenticationProvider
      getEditableAuthenticationProvider(SecurityProvider security, IdentityID user)
   {
      return getEditableAuthenticationProvider(security, user, Identity.USER);
   }

   /**
    * Gets the user editable authentication module for the specified security
    * provider.
    *
    * @param security the security provider.
    * @param identityID the user identityID.
    *
    * @return the editable authentication module or <code>null</code> if the
    *         provider's authentication module is not editable.
    */
   public static EditableAuthenticationProvider
      getEditableAuthenticationProvider(SecurityProvider security, IdentityID identityID,
                                        int identityType)
   {
      if(security instanceof AbstractSecurityProvider) {
         AuthenticationProvider auth = security.getAuthenticationProvider();

         if(auth instanceof EditableAuthenticationProvider &&
            getIdentity(auth, identityID, identityType) != null)
         {
            return (EditableAuthenticationProvider) auth;
         }

         if(!(auth instanceof AuthenticationChain)) {
            return null;
         }

         for(Object provider : ((AuthenticationChain) auth).getProviders()) {
            if(provider instanceof EditableAuthenticationProvider eprovider &&
               getIdentity(eprovider, identityID, identityType) != null)
            {
               return eprovider;
            }
         }
      }

      return null;
   }

   private static Identity getIdentity(AuthenticationProvider provider, IdentityID identityID,
                                       int type)
   {
      if(provider == null) {
         return null;
      }

      if(type == Identity.USER) {
         return provider.getUser(identityID);
      }
      else if(type == Identity.GROUP) {
         return provider.getGroup(identityID);
      }
      else if(type == Identity.ROLE) {
         return provider.getRole(identityID);
      }
      else if(type == Identity.ORGANIZATION) {
         return provider.getOrganization(identityID.orgID);
      }

      return null;
   }

   /**
    * Check whether the use is in the EditableAuthenticationProvider.
    *
    * @param security the security provider.
    * @param user the user identityID.
    *
    * @return the editable authentication module or <code>null</code> if the
    *         provider's authentication module is not editable.
    */
   public static boolean isEditableUser(SecurityProvider security, IdentityID user) {
      return getEditableAuthenticationProvider(security, user) != null;
   }

   /**
    * Init the schedule listener to listen to the schedule server.
    */
   public static synchronized void initScheduleListener() {
      if(scheduleListener != null) {
         return;
      }

      scheduleListener = new GroupedThread() {
         @Override
         protected void doRun() {
            ScheduleClient client = ScheduleClient.getScheduleClient();

            while(!isCancelled()) {
               if(!isCancelled() && client.isReady() &&
                  (scheduleThread == null || scheduleThread.isStopped()))
               {
                  startScheduleThread();
                  LOG.info("Schedule server started");
               }

               try {
                  // optimization, no need to check too frequently,
                  // use the same sleep time as ScheduleThread
                  Thread.sleep(600000);
               }
               catch(InterruptedException e) {
               }

            }
         }
      };

      scheduleListener.start();
   }

   /**
    * Start the scheduler process.
    */
   public static void startScheduler() {
      scheduleLock.lock();
      final ScheduleClient client = ScheduleClient.getScheduleClient();

      try {
         if(client.isReady()) {
            return;
         }

         client.startServer();
         startScheduleThread();
         Cluster.getInstance().refreshConfig(true);
      }
      catch(Exception ex) {
         LOG.warn("Failed to start schedule server", ex);

         throw new RuntimeException(ex);
      }
      finally {
         scheduleLock.unlock();
      }
   }

   /**
    * Stop the scheduler process.
    */
   public static void stopScheduler() throws RemoteException {
      stopScheduler(true);
   }

   /**
    * Stop the scheduler process.
    */
   public static void stopScheduler(boolean stopServer) throws RemoteException
   {
      stopScheduler(stopServer, false);
   }

   /**
    * Stop the scheduler process.
    */
   public static void stopScheduler(boolean stopServer, boolean sendEmail)
      throws RemoteException
   {
      stopScheduleThread();

      if(stopServer) {
         ScheduleClient client = ScheduleClient.getScheduleClient();
         client.stopServer();
         Cluster.getInstance().refreshConfig(false);

         if(sendEmail) {
            ScheduleThread.sendScheduleOffEmail();
         }
      }
   }

   /**
    * Stop scheduler thread.
    */
   private static void stopScheduleThread() {
      if(gScheduleThread != null) {
         if(scheduleThread != null) {
            scheduleThread.setStopped(true);
         }

         try {
            gScheduleThread.interrupt();
            gScheduleThread.join();
         }
         catch(InterruptedException ie) {
         }
      }
   }

   /**
    * Start scheduler thread.
    */
   private static void startScheduleThread() {
      scheduleThread = new ScheduleThread(false);
      gScheduleThread = new GroupedThread(scheduleThread);
      gScheduleThread.setName("Scheduler Starter");
      gScheduleThread.start();
   }

   /**
    * Check if the specified entry path conflicts with another entry path.
    * @param path the specified repository path.
    * @param user the specified principal.
    * @return <tt>true</tt> if duplicated, <tt>false</tt> otherwise.
    */
   public static boolean isDuplicatedRepositoryPath(String path, IdentityID user) {
      return isDuplicatedRepositoryPath(path, user, -1);
   }

   /**
    * Check the query permission.
    *
    * @param query the specified query.
    * @param user the specified user.
    *
    * @return <tt>true</tt> if pass, <tt>false</tt> otherwise.
    */
   public static boolean checkQueryPermission(String query, Principal user) {
      try {
         SecurityEngine security = SecurityEngine.getSecurity();
         return security.checkPermission(user, ResourceType.QUERY, query, ResourceAction.READ);
      }
      catch(Exception ex) {
         LOG.error("Failed to check read permission on query " +
            query + " for user " + user, ex);
      }

      return false;
   }

   /**
    * Check the datasource data model folder permission.
    *
    * @param folder the specified data model folder.
    * @param datasource the specified data model folder's parent datasource.
    * @param user the specified user.
    *
    * @return <tt>true</tt> if pass, <tt>false</tt> otherwise.
    */
   public static boolean checkDataModelFolderPermission(String folder,
                                                        String datasource, Principal user)
   {
      try {
         SecurityEngine security = SecurityEngine.getSecurity();
         String res = datasource + "/" + folder;
         return security.checkPermission(user, ResourceType.DATA_MODEL_FOLDER, res, ResourceAction.READ);
      }
      catch(Exception ex) {
         LOG.error("Failed to check read permission on data model folder " + folder +
            " in data source " + datasource + " for user " + user, ex);
      }

      return false;
   }

   /**
    * Check the datasource query folder permission.
    *
    * @param folder the specified query folder.
    * @param datasource the specified query folder's parent datasource.
    * @param user the specified user.
    *
    * @return <tt>true</tt> if pass, <tt>false</tt> otherwise.
    */
   public static boolean checkQueryFolderPermission(String folder,
      String datasource, Principal user)
   {
      try {
         SecurityEngine security = SecurityEngine.getSecurity();
         String res = folder + "::" + datasource;
         return security.checkPermission(user, ResourceType.QUERY_FOLDER, res, ResourceAction.READ);
      }
      catch(Exception ex) {
         LOG.error("Failed to check read permission on query folder " + folder +
            " in data source " + datasource + " for user " + user, ex);
      }

      return false;
   }

   /**
    * Check the datasource permission.
    *
    * @param dname the specified datasource.
    * @param user the specified user.
    *
    * @return <tt>true</tt> if pass, <tt>false</tt> otherwise.
    */
   public static boolean checkDataSourcePermission(String dname,
                                                   Principal user)
   {
      try {
         SecurityEngine security = SecurityEngine.getSecurity();
         return security.checkPermission(
            user, ResourceType.DATA_SOURCE, dname, ResourceAction.READ);
      }
      catch(Exception ex) {
         LOG.error("Failed to check read permission on data source " + dname +
            " for user " + user, ex);
      }

      return false;
   }

   /**
    * Check the datasource folder permission.
    *
    * @param folder the specified folder.
    * @param user the specified user.
    *
    * @return <tt>true</tt> if pass, <tt>false</tt> otherwise.
    */
   public static boolean checkDataSourceFolderPermission(String folder,
      Principal user)
   {
      try {
         SecurityEngine security = SecurityEngine.getSecurity();
         return security.checkPermission(
            user, ResourceType.DATA_SOURCE_FOLDER ,folder, ResourceAction.READ);
      }
      catch(Exception ex) {
         LOG.error("Failed to check read permission on data source folder " + folder +
            " for user " + user, ex);
      }

      return false;
   }

   /**
    * Check permission for Repository Entry or Asset Entry.
    * @param principal the principal
    * @param entry the replet entry
    * @param repository the replet repository
    * @param action the permission
    */
    public static boolean checkPermission(Principal principal, Object entry,
                                          RepletRepository repository, ResourceAction action)
    {
       if(entry instanceof AssetEntry) {
          AssetRepository assetRepository = AssetUtil.getAssetRepository(false);

          try {
             assetRepository.checkAssetPermission(principal, (AssetEntry) entry, action);
          }
          catch(Exception e) {
             return false;
          }

          return true;
       }
       else if(entry instanceof RepositoryEntry) {
          try {
             return repository.checkPermission(
                principal, ResourceType.REPORT, ((RepositoryEntry) entry).getPath(), action);
          }
          catch(RemoteException re) {
             return false;
          }
       }

       return false;
   }

   /**
    * Check if the specified entry path conflicts with another entry path.
    * @param path the specified repository path.
    * @param user the specified principal.
    * @type the specified repository entry type.
    * @return <tt>true</tt> if duplicated, <tt>false</tt> otherwise.
    */
   public static boolean isDuplicatedRepositoryPath(String path, IdentityID user, int type) {
      try {
         boolean isMyReport = isMyReport(path);
         user = isMyReport ? user : null;
         RepletRegistry registry = RepletRegistry.getRegistry(user);
         boolean result = registry != null && registry.isFolder(path);

         if(type == RepositoryEntry.VIEWSHEET) {
         }
         else if(type == RepositoryEntry.FILE) {
            result = result ||
               SUtil.isDuplicatedViewsheet(AssetUtil.getAssetRepository(false), path, user);
         }
         // should support more types in future?
         else {
            result = result ||
               SUtil.isDuplicatedViewsheet(AssetUtil.getAssetRepository(false), path, user);
         }

         return result;
      }
      catch(Exception e) {
         LOG.error(e.getMessage(), e);
      }

      return false;
   }

   /**
    * Build up the properties for locallist.
    */
   public static Properties loadLocaleProperties() {
      DataSpace space = DataSpace.getDataSpace();
      String name = SreeEnv.getPath("locale.file", "locale.properties");
      InputStream repository = null;
      boolean firstTime = false;
      Properties prop = new Properties();

      try {
         repository = space.getInputStream(null, name);

         if(repository == null) {
            firstTime = true;
            repository = SUtil.getInputStream("/inetsoft/sree/internal/locale.properties");
         }

         prop.load(repository);
      }
      catch(Exception ex) {
         LOG.error("Failed to load locale properties", ex);
      }
      finally {
         IOUtils.closeQuietly(repository);

         if(firstTime) {
            saveLocaleProperties(prop);
         }
      }

      return prop;
   }

   /**
    * Save localelist properties file to sree home.
    */
   public static void saveLocaleProperties(Properties prop) {
      String name = SreeEnv.getPath("locale.file", "locale.properties");
      DataSpace space = DataSpace.getDataSpace();

      try {
         space.withOutputStream(null, name, out -> prop.store(out, null));
      }
      catch(Throwable ex) {
         LOG.error("Failed to save locale properties", ex);
      }
   }

   /**
    * Shared handling of exceptions thrown while serving a resource.
    *
    * @param e    the thrown exception.
    * @param name the name of the resource.
    */
   public static void handleResourceException(Throwable e, String name) {
      if(e instanceof FileNotFoundException) {
         // resource not found
         LOG.error("Resource not found: {}", name, e);
      }
      else if(e instanceof IOException) {
         // demote the severity of this exception, it comes from a communication
         // error over the client connection, usually a broken pipe
         LOG.debug("I/O error while serving resource: {}", name, e);
      }
      else if(Tool.isBrokenPipeException(e, true)) {
         // known broken pipe, can safely demote the severity of this exception
         LOG.error("Broken pipe while serving resource: {}", name, e);
      }
      else {
         // unexpected error
         LOG.error("Error serving resource: {}", name, e);
      }
   }

   /**
    * Localize an asset entry path.
    * @param path the path to be localized.
    * @param principal the user principal.
    * @param isReplet true if the path is point to a replet.
    * @param entry the asset entry to be localized.
    * @param isUserScope true if the asset entry is user scope entry.
    * @throws Exception
    */
   public static String localizeAssetEntry(String path, Principal principal,
                                           boolean isReplet, AssetEntry entry,
                                           boolean isUserScope) {
      if(isUserScope) {
         path = MY_REPORT + "/" + path;
      }

      String newPath = localize(path, principal, isReplet, entry);
      return isUserScope ? newPath.substring(newPath.indexOf('/') + 1) : newPath;
   }

   /**
    * Localize a path.
    * @throws Exception
    */
   public static String localize(String path, Principal principal) {
      return localize(path, principal, true);
   }

   /**
    * Localize a path.
    * @throws Exception
    */
   public static String localize(String path, Principal principal, boolean isReplet) {
      return localize(path, principal, isReplet, null);
   }

   /**
    * Localize a path.
    * @param path the path to be localized.
    * @param principal the user principal.
    * @param isReplet true if the path is point to a replet.
    * @param entry the asset entry to be localized.
    * @throws Exception
    */
   public static String localize(String path, Principal principal,
                                 boolean isReplet, AssetEntry entry) {
      return localize(path, principal, isReplet, entry, true);
   }

   /**
    * Localize a path.
    * @param path the path to be localized.
    * @param principal the user principal.
    * @param isReplet true if the path is point to a replet.
    * @param entry the asset entry to be localized.
    * @throws Exception
    */
   public static String localize(String path, Principal principal,
                                 boolean isReplet, AssetEntry entry,
                                 boolean applyAlias) {
      Catalog catalog = Catalog.getCatalog(principal);

      if("/".equals(path) || "".equals(path)) {
         return catalog.getString(path);
      }

      Catalog userCatalog = Catalog.getCatalog(principal, Catalog.REPORT);
      RepletRegistry registry = null;
      RepletRegistry dregistry = null;

      try {
         if(principal != null && path.startsWith(MY_REPORT)) {
            registry = RepletRegistry.getRegistry(IdentityID.getIdentityIDFromKey(principal.getName()));
         }

         dregistry = RepletRegistry.getRegistry();
      }
      catch(Exception ex) {
         LOG.error("Failed to get replet registry for localization", ex);
      }

      String[] values = Tool.split(path, '/');
      StringBuilder result = new StringBuilder();
      String curPath = null;

      for(int i = 0; i < values.length; i++) {
         String folder = values[i];
         curPath = curPath == null ? folder : curPath + "/" + folder;
         String alias = null;
         boolean sysFolder = MY_REPORT.equals(curPath);

         if(!sysFolder) {
            if(applyAlias) {
               if(registry != null) {
                  alias = registry.getFolderAlias(curPath);
               }
               else {
                  alias = dregistry.getFolderAlias(curPath);
               }
            }

            alias = "".equals(alias) ? null : alias;
            folder = alias != null ? alias : folder;
         }

         if(entry != null && i == values.length - 1) {
            if(applyAlias) {
               alias = entry.getAlias();
            }

            folder = alias != null && alias.length() > 0 ? alias : folder;
         }

         if(sysFolder) {
            result.append(catalog.getString(folder));
         }
         else {
            // 'My Reports/Worksheets' needs to translate the 'Worksheets' using system catalog
            // (68848)
            if(curPath.startsWith(MY_REPORT + "/")) {
               folder = catalog.getString(folder);
            }

            result.append(userCatalog.getString(folder));
         }

         if(i < values.length - 1) {
            result.append("/");
         }
      }

      return result.toString();
   }

   /**
    * Get ajax request url in dashboard or jsp.
    */
   public static String getRepositoryUrl(String servlet) {
      String url = getReportServletUrl();

      if(url == null || url.isEmpty()) {
         url = servlet;
      }

      return url;
   }

   public static String getReportServletUrl(String defaultUrl) {
      String url = getReportServletUrl();

      if(url == null || url.isEmpty()) {
         url = defaultUrl;
      }

      return url;
   }

   public static String getReportServletUrl() {
      String property = SreeEnv.getProperty("replet.repository.servlet");

      if(property != null) {
         if(property.endsWith("/")) {
            property += "reports";
         }
         else {
            property += "/reports";
         }
      }

      return property;
   }

   /**
    * Converts an AssetEntry into the type-appropriate XAsset
    * @param entry   the asset entry
    * @return  the XAsset type that matches the asset entry
    */
   public static XAsset getXAsset(AssetEntry entry) {
      String type = null;
      String path = null;

      if(entry.isQuery()) {
         type = XQueryAsset.XQUERY;
         path = convertPath_getLast(entry.getPath());
      }
      else if(entry.isDataSource()) {
         type = XDataSourceAsset.XDATASOURCE;
         path = entry.getPath();
      }
      else if(entry.isLogicModel() || entry.isExtendedLogicModel()) {
         type = XLogicalModelAsset.XLOGICALMODEL;
         path = convertPath_dataModel(entry.getProperty(AssetEntry.PATH_ARRAY));
      }
      else if(entry.isPartition() || entry.isExtendedPartition()) {
         type = XPartitionAsset.XPARTITION;
         path = convertPath_dataModel(entry.getProperty(AssetEntry.PATH_ARRAY));
      }
      else if(entry.isVPM()) {
         type = VirtualPrivateModelAsset.VPM;
         String vpath = entry.getPath();
         int dot = vpath.lastIndexOf('/');

         if(dot >= 0) {
            path = vpath.substring(0, dot) + "^" + vpath.substring(dot + 1);
         }
      }
      else if(entry.isWorksheet()) {
         type = WorksheetAsset.WORKSHEET;
         path = entry.getPath();
      }
      else if(entry.isViewsheet()) {
         type = ViewsheetAsset.VIEWSHEET;
         path = entry.getPath();
      }
      else if(entry.isTableStyle()) {
         type = TableStyleAsset.TABLESTYLE;
         path = convertPath_dropFirst(entry.getPath());
      }
      else if(entry.isScript()) {
         type = ScriptAsset.SCRIPT;
         path = convertPath_getLast(entry.getPath());
      }

      if(path == null) {
         throw new RuntimeException("Unknown Asset Entry [" + entry + "]");
      }

      return getXAsset(type, path, entry.getUser());
   }

   private static String convertPath_dataModel(String path) {
      String result = null;
      String[] parts = path.split("\\^_\\^");

      if(parts.length == 3) {
         result = parts[0] + "^" + parts[2];
      }
      else if(parts.length == 4) {
         result = parts[0] + "^" + parts[2] + "^" + parts[3];
      }

      return result;
   }

   private static String convertPath_dropFirst(String path) {
      String result = "";
      String[] parts = path.split("/");

      if(parts.length < 2) {
         return null;
      }

      for(int i = 1; i < parts.length; i++) {
         result += (i > 1 ? "/" : "") + parts[i];
      }

      return result;
   }

   private static String convertPath_getLast(String path) {
      String[] parts = path.split("/");
      return parts.length > 0 ? parts[parts.length - 1] : null;
   }

   /**
    * Get XAsset.
    * @return xasset.
    */
   public static XAsset getXAsset(String type, String path, IdentityID user) {
      XAsset asset = null;

      if(XQueryAsset.XQUERY.equals(type)) {
         asset = new XQueryAsset();
      }
      else if(XDataSourceAsset.XDATASOURCE.equals(type)) {
         asset = new XDataSourceAsset();
      }
      else if(XLogicalModelAsset.XLOGICALMODEL.equals(type)) {
         asset = new XLogicalModelAsset();
      }
      else if(XPartitionAsset.XPARTITION.equals(type)) {
         asset = new XPartitionAsset();
      }
      else if(VirtualPrivateModelAsset.VPM.equals(type)) {
         asset = new VirtualPrivateModelAsset();
      }
      else if(WorksheetAsset.WORKSHEET.equals(type)) {
         asset = new WorksheetAsset();
      }
      else if(ViewsheetAsset.VIEWSHEET.equals(type)) {
         asset = new ViewsheetAsset();
      }
      else if(VSAutoSaveAsset.AUTOSAVEVS.equals(type)) {
         path = trimAutoSaveOrganization(path);
         asset = new VSAutoSaveAsset();
      }
      else if(WSAutoSaveAsset.AUTOSAVEWS.equals(type)) {
         path = trimAutoSaveOrganization(path);
         asset = new WSAutoSaveAsset();
      }
      else if(VSSnapshotAsset.VSSNAPSHOT.equals(type)) {
         asset = new VSSnapshotAsset();
      }
      else if(DashboardAsset.DASHBOARD.equals(type)) {
         asset = new DashboardAsset();
      }
      else if(TableStyleAsset.TABLESTYLE.equals(type)) {
         asset = new TableStyleAsset();
      }
      else if(ScriptAsset.SCRIPT.equals(type)) {
         asset = new ScriptAsset();
      }
      else if(ScheduleTaskAsset.SCHEDULETASK.equals(type)) {
         asset = new ScheduleTaskAsset();
      }
      else if(DataCycleAsset.DATACYCLE.equals(type)) {
         asset = new DataCycleAsset();
      }
      else if(DeviceAsset.DEVICE.equals(type)) {
         asset = new DeviceAsset();
      }
      else if(EmbeddedDataAsset.EMBEDDEDDATA.equals(type)) {
         asset = new EmbeddedDataAsset();
      }

      if(asset == null) {
         LOG.warn("Unsupported asset type: " + type);
      }
      else {
         try {
            asset.parseIdentifier(path, user);
         }
         catch(ResourceNotFoundException ex) {
            LOG.warn(String.format("Ignore the not exist resource %s with path %s", type, path), ex);
            asset = null;
         }
      }

      return asset;
   }

   public static String addAutoSaveOrganization(String path) {
      if(path != null && path.contains("^")) {
         String[] paths = Tool.split(path, '^');

         if(paths.length > 3) {
            String user = paths[2];

            if(!user.contains(IdentityID.KEY_DELIMITER) && !user.equals("_NULL_")) {
               paths[2] =
                  user + IdentityID.KEY_DELIMITER + OrganizationManager.getInstance().getCurrentOrgID();
               path = String.join("^", paths);
            }
         }
      }

      return path;
   }

   public static String trimAutoSaveOrganization(String path) {
      if(path != null && path.indexOf("^") != -1) {
         String[] paths = Tool.split(path, '^');

         if(paths.length > 3) {
            String user = paths[2];

            if(user.indexOf(IdentityID.KEY_DELIMITER) != -1) {
               IdentityID id = IdentityID.getIdentityIDFromKey(user);
               paths[2] = id.getName();
               path = String.join("^", paths);
            }
         }
      }

      return path;
   }

   /**
    * Get the specified entry's assets depencencies.
    * @param asset the specified esset.
    * @return all depencencies.
    */
   public static XAssetDependency[] getXAssetDependencies(XAsset asset) {
      if(asset != null) {
         List<XAssetDependency> list = new ArrayList<>();
         getXAssetDependencies(asset, list);
         return list.toArray(new XAssetDependency[0]);
      }

      return new XAssetDependency[0];
   }

   /**
    * Get the asset's dependencies recursively.
    * @param asset the specified esset.
    * @param list the specified container.
    */
   public static void getXAssetDependencies(XAsset asset,
                                             List<XAssetDependency> list)
   {
      XAssetDependency[] dependencies = asset.getDependencies(list);

      for(int i = 0; i < dependencies.length; i++) {
         XAssetDependency dependency = dependencies[i];
         XAsset dependedAsset = dependency.getDependedXAsset();

         if(!list.contains(dependency) &&
            !dependedAsset.getPath().startsWith(RecycleUtils.RECYCLE_BIN_FOLDER))
         {
            list.add(dependency);
            getXAssetDependencies(dependedAsset, list);
         }
      }
   }

   /**
    * Get the identity due to name and type;
    *
    * @param identityID the identity name.
    * @param type       the identity type.
    *
    * @return the specified identity.
    */
   public static Identity getIdentity(IdentityID identityID, int type) {
      if(identityID == null) {
         return null;
      }

      SecurityProvider provider = null;
      Identity iden = null;

      try {
         provider = SecurityEngine.getSecurity().getSecurityProvider();
      }
      catch(Exception ex) {
         LOG.error("Failed to get security provider to get identity for: " + identityID, ex);
      }

      if(type == Identity.USER) {
         iden = provider != null ? provider.getUser(identityID) : new User(identityID);
      }
      else if(type == Identity.GROUP) {
         iden = provider != null ? provider.getGroup(identityID) : new Group(identityID);
      }
      else if(type == Identity.ROLE) {
         iden = provider != null ? provider.getRole(identityID) : new Role(identityID);
      }

      return iden;
   }

   /**
    * Get the scheduler "execute as" identity;
    * @param prin the executing principal .
    * @return the specified identity.
    */
   public static Identity getExecuteIdentity(Principal prin) {
      if(prin == null) {
         return null;
      }

      IdentityID idFromPrin = IdentityID.getIdentityIDFromKey(prin.getName());

      if(prin instanceof SRPrincipal &&
         "true".equals(((SRPrincipal) prin).getProperty("virtual")))
      {
         String type = ((SRPrincipal) prin).getProperty("identity_type");
         return "group".equals(type) ? new Group(idFromPrin) :
            new Role(idFromPrin);
      }

      if(prin instanceof DestinationUserNameProviderPrincipal) {
         String[] groups = ((DestinationUserNameProviderPrincipal) prin).getGroups();
         IdentityID[] roles = ((DestinationUserNameProviderPrincipal) prin).getRoles();
         return new User(idFromPrin, new String[0], groups, roles,
            "", "", true);
      }

      return new User(idFromPrin);
   }

   /**
    * Check the http headers is vaild.
    * For veracode security, see http://cwe.mitre.org/data/definitions/113.html
    */
   public static boolean isHttpHeadersValid(String header) {
      if(header == null || "".equals(header)) {
         return false;
      }

      if(header.indexOf('\r') >= 0 || header.indexOf('\n') >= 0 ||
         header.indexOf("%0d") >= 0 || header.indexOf("%0a") >= 0)
      {
         return false;
      }

      return true;
   }

   /**
    * Get the remote uri of the request.
    */
   public static String getRequestURI(HttpServletRequest req) {
      return LinkUriArgumentResolver.transformUri(req);
   }

   /**
    * Get Unregistered users.
    */
   public static IdentityID[] getUnregisteredUsers() {
      IdentityID[] registered = new IdentityID[0];

      try {
         SecurityEngine security = SecurityEngine.getSecurity();
         SecurityProvider provider = security.getSecurityProvider();

         if(provider != null) {
            registered = security.getUsers();
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to list all users", ex);
      }

      String workdir = SreeEnv.getProperty("sree.home") + File.separator
            + "sreeUserData";
      File root = FileSystemService.getInstance().getFile(workdir);

      if(!root.exists()) {
         return new IdentityID[0];
      }

      File[] files = root.listFiles();
      Set<String> unregistered = new HashSet<>();
      String suffix = ".xml";

      OUTER:
      for(int i = 0; i < files.length; i++) {
         if(!files[i].isFile()) {
            continue;
         }

         String name = files[i].getName();

         if(!name.endsWith(suffix)) {
            continue;
         }

         name = name.substring(0, name.length() - 4);

         for(int j = 0; j < registered.length; j++) {
            if(name.equals(registered[j].convertToKey())) {
               continue OUTER;
            }
         }

         if(!XPrincipal.ANONYMOUS.equals(IdentityID.getIdentityIDFromKey(name).name)) {
            unregistered.add(name);
         }
      }

      return unregistered.toArray(new IdentityID[0]);
   }

   /**
    * Get all users with an asset in their scope from indexed storage, even if they don't
    * exist in security.
    *
    * @return the list of users.
    *
    * @throws Exception if the indexed storage could not be initialized.
    */
   public static IdentityID[] getAllAssetUsers() throws Exception {
      return IndexedStorage.getIndexedStorage()
         .getKeys(SUtil::filterUserAsset)
         .stream()
         .map(SUtil::getAssetUser)
         .distinct()
         .toArray(IdentityID[]::new);
   }

   private static boolean filterUserAsset(String key) {
      AssetEntry entry = AssetEntry.createAssetEntry(key);

      return entry != null && entry.getUser() != null &&
         entry.getScope() == AssetRepository.USER_SCOPE;
   }

   private static IdentityID getAssetUser(String key) {
      return Objects.requireNonNull(AssetEntry.createAssetEntry(key)).getUser();
   }

   public static boolean isEMPrincipal(HttpServletRequest req) {
      if(req == null) {
         return false;
      }

      return Tool.equals("true", req.getHeader(RepletRepository.EM_CLIENT));
   }

   public static Principal getPrincipal(HttpServletRequest req) {
      return getPrincipal(req, false);
   }

   /**
    * Get principal.
    */
   public static Principal getPrincipal(HttpServletRequest req, boolean create) {
      if(req == null) {
         return null;
      }

      try {
         Principal principal = null;
         HttpSession session = req.getSession(create);
         boolean isEmRequest = Tool.equals("true", req.getHeader(RepletRepository.EM_CLIENT)) ||
            "websocket".equals(req.getHeader("Upgrade")) &&
               Tool.equals("true", req.getParameter(RepletRepository.EM_CLIENT));

         if(isEmRequest) {
            principal = (Principal) session.getAttribute(RepletRepository.EM_PRINCIPAL_COOKIE);

            if(principal == null) {
               principal = (Principal) session.getAttribute(RepletRepository.PRINCIPAL_COOKIE);

               if(principal != null && OrganizationManager.getInstance().isSiteAdmin(principal)) {
                  principal = createEMPrincipal(principal);
                  session.setAttribute(RepletRepository.EM_PRINCIPAL_COOKIE, principal);
               }
            }
         }

         if(principal == null) {
            principal = (Principal) session.getAttribute(RepletRepository.PRINCIPAL_COOKIE);
         }

         return principal;
      }
      catch(Exception ex) {
         LOG.error("Failed to get principal for HTTP request", ex);
      }

      return null;
   }

   public static boolean isSiteAdminSession(HttpSession session) {
      XPrincipal principal = (XPrincipal) session.getAttribute(RepletRepository.PRINCIPAL_COOKIE);

      return principal != null && OrganizationManager.getInstance().isSiteAdmin(principal.getIdentityID());
   }

   /**
    * Get action record to write info to audit log.
    */
   public static ActionRecord getActionRecord(HttpServletRequest req, String actionName, String objectName, String objectType) {
      return getActionRecord(SUtil.getPrincipal(req), actionName, objectName, objectType);
   }

   public static String getUserName(Principal principal) {
      return principal == null ? "" : principal.getName();
   }

   public static ActionRecord getActionRecord(Principal principal, String actionName,
                                              String objectName, String objectType) {
      Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
      return getActionRecord(SUtil.getUserName(principal), actionName, objectName, objectType, actionTimestamp, principal, true);
   }

   public static ActionRecord getActionRecord(String sessionId, String actionName,
                                              String objectName, String objectType,
                                              Timestamp actionTimestamp,
                                              Principal principal, boolean isAuto)
   {
      return getActionRecord(sessionId, actionName, objectName, objectType,
                             actionTimestamp, null, principal, isAuto);
   }

   public static ActionRecord getActionRecord(String sessionId, String actionName,
                                              String objectName, String objectType,
                                              Timestamp actionTimestamp, String actionError,
                                              Principal principal, boolean isAuto)
   {
      ActionRecord actionRecord = new ActionRecord(sessionId,
         actionName, objectName, objectType, actionTimestamp,
         ActionRecord.ACTION_STATUS_SUCCESS, actionError);
      String olocale = null;

      if(principal instanceof SRPrincipal) {
         olocale = ((SRPrincipal) principal).getProperty(SRPrincipal.LOCALE);
      }

      if(principal instanceof SRPrincipal) {
         ((SRPrincipal) principal).setProperty(SRPrincipal.LOCALE, olocale);
      }

      actionRecord.setResourceOrganization(OrganizationManager.getInstance().getCurrentOrgID());
      actionRecord.setResourceOrganizationName(OrganizationManager.getCurrentOrgName());

      return actionRecord;
   }

   public static IdentityInfoRecord getIdentityInfoRecord(IdentityID identityID,
                                                          int identityType, String actionType, String actionDesc, String state)
   {
      Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
      String type;

      switch(identityType) {
         case Identity.USER:
            type = IdentityInfoRecord.USER_TYPE;
            break;
         case Identity.GROUP:
            type = IdentityInfoRecord.GROUP_TYPE;
            break;
         case Identity.ROLE:
            type = IdentityInfoRecord.ROLE_TYPE;
            break;
         case Identity.ORGANIZATION:
            type = IdentityInfoRecord.ORGANIZATION_TYPE;
            break;
         default:
            type = IdentityInfoRecord.USER_TYPE;
            break;
      }

      return new IdentityInfoRecord(
         identityID, type, actionType, actionDesc, actionTimestamp, state);
   }

   /**
    * Check if the server is running as a cluster.
    * @return true if the server is running as a cluster.
    */
   public static boolean isCluster() {
      return "server_cluster".equals(SreeEnv.getProperty("server.type"));
   }

   /**
    * Logs out a user from the system.
    * @param srPrincipal The principal to log out.
    */
   public static void logout(Principal srPrincipal) {
      logout(srPrincipal, false);
   }

   /**
    * Logs out a user from the system.
    *
    * @param principal         a principal identifying the user to log out.
    * @param invalidateSession a flag indicating if the HTTP session should be invalidated.
    */
   public static void logout(Principal principal, boolean invalidateSession) {
      try {
         AuthenticationService.getInstance().logout(principal, invalidateSession);
      }
      catch(Exception e) {
         LOG.error("Failed to log out user: {}", principal, e);
      }
   }

   /**
    * Get hyperlink report dependencies.
    */
   public static void processAssetlinkDependencies(Hyperlink[] links,
                                                   XAsset asset,
                                                   List<XAssetDependency> dependencies)
   {
      for(Hyperlink link : links) {
         if(link == null) {
            continue;
         }

         int linkType = link.getLinkType();

         if(linkType != Hyperlink.VIEWSHEET_LINK) {
            continue;
         }

         processViewsheetAsset(link.getLink(), asset, dependencies);
      }
   }

   /**
    * Process the viewsheet asset.
    */
   private static void processViewsheetAsset(String name, XAsset asset,
                                             List<XAssetDependency> dependencies)
   {
      if(asset == null || name == null || dependencies == null) {
         return;
      }

      AssetEntry ventry = AssetEntry.createAssetEntry(name);

      if(ventry == null) {
         return;
      }

      Catalog catalog = Catalog.getCatalog();
      String desc = catalog.getString("common.xasset.depends",
         catalog.getString("common.xasset.viewsheet", asset.getPath()),
         ventry.getDescription());
      boolean snapshot = asset instanceof ViewsheetAsset2;
      ViewsheetAsset dependedXAsset = snapshot ?
         new ViewsheetAsset2(ventry) : new ViewsheetAsset(ventry);

      if(snapshot) {
         try {
            AbstractSheet sheet = AssetUtil.getAssetRepository(false).getSheet(
               ventry, ThreadContext.getContextPrincipal(), true, AssetContent.ALL);
            dependedXAsset.setSheet(sheet);
         }
         catch(Exception ex) {
            LOG.error("Cannot find sheet: " + ventry.getPath(), ex);
         }
      }

      XAssetDependency dependency = new XAssetDependency(dependedXAsset, asset,
         XAssetDependency.VIEWSHEET_LINK, desc);

      if(!dependencies.contains(dependency)) {
         dependencies.add(dependency);
      }
   }

   /**
    * Record the user login behavior, regardless of success or failure.
    */
   public static void loginRecord(HttpServletRequest req, IdentityID user,
                                  boolean uri, IdentityID loginAsUser)
   {
      if(req != null) {
         String remoteAddr = SUtil.getIpAddress(req);
         String serverName = req.getServerName();
         String path = null;

         if(uri) {
            StringBuilder buffer = new StringBuilder().append(req.getRequestURI());

            if(req.getQueryString() != null && req.getQueryString().length() != 0) {
               buffer.append('?').append(req.getQueryString());
            }

            path = buffer.toString();
         }

         loginRecord(user, loginAsUser, remoteAddr, serverName, path);
      }
   }

   /**
    * Record the user login behavior, regardless of success or failure.
    */
   public static void loginRecord(IdentityID user, IdentityID loginAsUser, String remoteAddr,
                                  String serverName, String uri)
   {
      if(!SreeEnv.isInitialized()) {
         // Bug #42223, prevent NPE during test drive startup
         return;
      }

      LogManager manager = LogManager.getInstance();

      if(!manager.isErrorEnabled(MAC_LOG.getName())) {
         return;
      }

      String mac = Catalog.getCatalog().getString("Unknown");

      try {
         if("0:0:0:0:0:0:0:1".equals(remoteAddr)) {
            remoteAddr = "127.0.0.1";
         }

         mac = SUtil.getMacAddress(remoteAddr);
      }
      catch(Exception ex) {
         if(manager.isDebugEnabled(MAC_LOG.getName())) {
            MAC_LOG.warn("Failed to obtain client MAC address", ex);
         }
         else {
            MAC_LOG.warn("Failed to obtain client MAC address");
         }
      }

      String info = String.format(
         "Login: User(%s) IP(%s) MAC(%s) Domain(%s)",
         user, remoteAddr, mac, serverName);
      info += loginAsUser == null || loginAsUser.name.length() == 0 ? "" :
         " Login As(" + loginAsUser + ")";

      if(uri != null) {
         info += " URI(" + uri + ")";
      }

      switch(manager.getLevel(MAC_LOG.getName())) {
      case ERROR:
         MAC_LOG.error(info);
         break;
      case WARN:
         MAC_LOG.warn(info);
         break;
      default:
         MAC_LOG.info(info);
      }
   }

   /**
    * Get ip address.
    */
   private static String getIpAddress(HttpServletRequest req) {
      String ip = req.getHeader("remote_ip");

      if(ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
         ip = req.getHeader("X-Forwarded-For");
      }

      if(ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
         ip = req.getHeader("Proxy-Client-IP");
      }

      if(ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
         ip = req.getHeader("WL-Proxy-Client-IP");
      }

      if(ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
         ip = req.getRemoteAddr();
      }

      return ip;
   }

   /**
    * Get MAC address by ip.
    * @param ipAddr ip address.
    * @return MAC address of this user.
    */
   private static String getMacAddress(String ipAddr) throws Exception {
      if("127.0.0.1".equals(ipAddr) || Tool.getIP().equals(ipAddr)) {
         InetAddress address = InetAddress.getByName(Tool.getIP());
         NetworkInterface network = NetworkInterface.getByInetAddress(address);
         byte[] mac = network.getHardwareAddress();
         StringBuilder sb = new StringBuilder();

         for (int i = 0; i < mac.length; i++) {
            sb.append(String.format(
               "%02X%s", mac[i], (i < mac.length - 1) ? "-" : ""));
         }

         return sb.toString();
      }

      return getRemoteMacAddr(ipAddr);
   }

   /**
    * Get remote client MAC address with UDP agreement.
    */
   private static String getRemoteMacAddr(String clientIP) throws Exception {
      String macAddr = null;
      byte[] cmd = getQueryCmd();
      DatagramPacket sendPacket = new DatagramPacket(cmd, cmd.length,
         InetAddress.getByName(clientIP), 137);
      DatagramSocket dataSocket = new DatagramSocket();

      try {
         dataSocket.setSoTimeout(500);
         dataSocket.send(sendPacket);
         DatagramPacket receivePacket = new DatagramPacket(new byte[1024], 1024);
         dataSocket.receive(receivePacket);
         macAddr = formatMacAddr(receivePacket.getData());
      }
      finally {
         try {
            dataSocket.close();
         }
         catch(Exception exc) {
            LOG.warn("Failed to close datagram socket", exc);
         }
      }

      return macAddr;
   }

   /**
    * Get query command.
    */
   private static byte[] getQueryCmd() {
      byte[] t_ns = new byte[50];
      t_ns[0] = 0x00;
      t_ns[1] = 0x00;
      t_ns[2] = 0x00;
      t_ns[3] = 0x10;
      t_ns[4] = 0x00;
      t_ns[5] = 0x01;
      t_ns[6] = 0x00;
      t_ns[7] = 0x00;
      t_ns[8] = 0x00;
      t_ns[9] = 0x00;
      t_ns[10] = 0x00;
      t_ns[11] = 0x00;
      t_ns[12] = 0x20;
      t_ns[13] = 0x43;
      t_ns[14] = 0x4B;

      for(int i = 15; i < 45; i++) {
         t_ns[i] = 0x41;
      }

      t_ns[45] = 0x00;
      t_ns[46] = 0x00;
      t_ns[47] = 0x21;
      t_ns[48] = 0x00;
      t_ns[49] = 0x01;

      return t_ns;
   }

   /**
    * Format MAC address to String.
    */
   private static String formatMacAddr(byte[] data) {
      int index = data[56] * 18 + 56;
      String addr = "";
      StringBuilder sb = new StringBuilder(17);

      for(int j = 1; j < 7; j++) {
         addr = Integer.toHexString(0xFF & data[index + j]);

         if(addr.length() < 2) {
            sb.append(0);
         }

         sb.append(addr.toUpperCase());

         if(j < 6) {
            sb.append("-");
         }
      }

      return sb.toString();
   }

   /**
    * Sends an HTTP error response without triggering the container's error pages. This is useful
    * for JSON endpoints where an HTML error page should not be returned.
    *
    * @param response the HTTP response object.
    * @param code     the HTTP status code.
    */
   public static void sendError(HttpServletResponse response, int code) throws IOException {
      response.setStatus(code);
      response.setContentType("application/json");

      try(PrintWriter writer = response.getWriter()) {
         writer.print("");
      }
   }

   public static void setMultiTenant(boolean multiTenant) {
      String oldValue = SreeEnv.getProperty("security.users.multiTenant");
      SreeEnv.setProperty("security.users.multiTenant", multiTenant + "");

      try {
         SreeEnv.save();
      }
      catch(IOException e) {
         LOG.error("Failed to save properties", e);
      }

      if(!Tool.equals(oldValue, multiTenant + "")) {
         LicenseManager.getInstance().updateNamedUserKeys();
         LogManager.getInstance().clearNonDefaultOrgLogLevels();
      }
   }

   public static boolean isMultiTenant() {
      return SecurityEngine.getSecurity().isSecurityEnabled() &&
         LicenseManager.getInstance().isEnterprise() &&
         Boolean.parseBoolean(SreeEnv.getProperty("security.users.multiTenant", "false"));
   }

   public static boolean isDefaultVSGloballyVisible() {
      String orgScopedProperty = "security." + OrganizationManager.getInstance().getCurrentOrgID() + ".exposeDefaultOrgToAll";

      return SUtil.isMultiTenant() &&
         (Boolean.parseBoolean(SreeEnv.getProperty("security.exposeDefaultOrgToAll", "false")) ||
          Boolean.parseBoolean(SreeEnv.getProperty(orgScopedProperty, "false")));
   }

   public static boolean isDefaultVSGloballyVisible(Principal principal) {
      String orgId = Organization.getDefaultOrganizationID();

      if(principal == null) {
         principal = ThreadContext.getContextPrincipal();
      }

      if(principal != null) {
         orgId = ((XPrincipal) principal).getOrgId();
      }

      String orgScopedProperty = "security." + orgId + ".exposeDefaultOrgToAll";
      return SUtil.isMultiTenant() && !OrganizationManager.getInstance().isSiteAdmin(principal) &&
         (Boolean.parseBoolean(SreeEnv.getProperty("security.exposeDefaultOrgToAll", "false")) ||
          Boolean.parseBoolean(SreeEnv.getProperty(orgScopedProperty, "false")));
   }

   public static boolean isSharedDefaultOrgDashboard(AssetEntry entry) {
      if(entry == null) {
         return false;
      }

      String orgID = entry.getOrgID();
      XPrincipal principal = (XPrincipal) ThreadContext.getContextPrincipal();
      String currOrgID = principal != null ?
         principal.getOrgId() : OrganizationManager.getInstance().getCurrentOrgID();
      return !Tool.equals(orgID, currOrgID) && SUtil.isDefaultVSGloballyVisible() &&
         Organization.getDefaultOrganizationID().equals(orgID);
   }

   public static String getOrgIDFromMVPath(String path) {
      String[] paths = path.split("\\_");
      String org = paths[paths.length-1];

      return SecurityEngine.getSecurity().getSecurityProvider().getOrganization(org) != null ? org : null;
   }

   public static String appendPath(String base, String suffix) {
      if(StringUtils.isEmpty(base)) {
         return suffix;
      }

      if(StringUtils.isEmpty(suffix)) {
         return base;
      }

      if(base.endsWith("/") && suffix.startsWith("/")) {
         return SUtil.appendPath(base, suffix.substring(1));
      }

      return base.endsWith("/") || suffix.startsWith("/")
         ? base + suffix
         : base + "/" + suffix;
   }

   /**
    * get user alias
    * @param userID the user name
    */
   public static String getUserAlias(IdentityID userID) {
      String alias = userID == null ? null : userID.getName();

      if(userID != null && !Tool.isEmptyString(userID.name)) {
         User user = SecurityEngine.getSecurity().getSecurityProvider().getUser(userID);

         if(user != null) {
            alias = user.getAlias() != null? user.getAlias() : user.getName();
         }
      }

      return alias;
   }

   /**
    * Gets the task name that does not contain organization info.
    */
   public static String getTaskNameWithoutOrg(String taskId) {
      if(Tool.isEmptyString(taskId) || !taskId.contains(":")) {
         return taskId;
      }

      String name = taskId;

      if(taskId.contains(IdentityID.KEY_DELIMITER)) {
         String taskName = taskId.substring(taskId.indexOf(":") + 1);
         String userName =
            IdentityID.getIdentityIDFromKey(taskId.substring(0, taskId.indexOf(":"))).getName();
         name = userName + ":" + taskName;
      }

      return name;
   }

   /**
    * Gets the task name for logging.
    */
   public static String getTaskNameForLogging(String taskId) {
      if(Tool.isEmptyString(taskId)) {
         return taskId;
      }
      else if(!taskId.contains(":")) {
         return Tool.buildString(taskId, "^", OrganizationManager.getInstance().getCurrentOrgID());
      }

      int index = taskId.indexOf(':');
      String userPart = taskId.substring(0, index);
      String taskName = taskId.substring(index + 1);
      String userName = userPart;
      String orgId = Organization.getDefaultOrganizationID();

      if(userPart.contains(IdentityID.KEY_DELIMITER)) {
         IdentityID identityID = IdentityID.getIdentityIDFromKey(userPart);
         userName = identityID.getName();
         orgId = identityID.getOrgID();
      }

      String finalTaskName = Tool.buildString(userName, ":", taskName);
      return LicenseManager.getInstance().isEnterprise() ?
         Tool.buildString(finalTaskName, "^", orgId) : finalTaskName;
   }

   /**
    * Gets the task name that does not contain organization info.
    */
   public static String getTaskOrgName(String taskFullName, Principal principal) {

      if(taskFullName.contains(IdentityID.KEY_DELIMITER)) {
         return IdentityID.getIdentityIDFromKey(taskFullName.substring(0, taskFullName.indexOf(":"))).getOrgID();
      }

      return OrganizationManager.getInstance().getInstance().getCurrentOrgID(principal);
   }

   public static String getTaskName(String name) {
      if(name.indexOf(":") < 0) {
         return name;
      }

      String[] names = name.split(":");

      if(names[0].indexOf(IdentityID.KEY_DELIMITER) > 0) {
         String[] userNames = names[0].split(IdentityID.KEY_DELIMITER);
         return userNames[0] + ":" + names[1];
      }

      return name;
   }

   public static String getTaskNameWithoutUser(String name) {
      if(name.indexOf(":") < 0) {
         return name;
      }

      String[] names = name.split(":");
      return names[1];
   }

   public static IdentityID getOwnerForNewTask(IdentityID user) {
      OrganizationManager organizationManager = OrganizationManager.getInstance();
      String currOrgId = organizationManager.getCurrentOrgID();

      if(user != null && !Tool.equals(user.getOrgID(), currOrgId)) {
         IdentityID[] orgUsers = SecurityEngine.getSecurity().getOrgUsers(currOrgId);

         if(orgUsers != null) {
            IdentityID orgAdmin = Arrays.stream(orgUsers)
               .filter(organizationManager::isOrgAdmin)
               .findFirst().orElse(null);
            return orgAdmin == null ? new IdentityID(user.name, currOrgId) : orgAdmin;
         }
      }

      return user;
   }

   public static String getTaskUser(String name) {
      if(name == null || !name.contains(":")) {
         return "";
      }

      String[] names = name.split(":");

      if(names[0].indexOf(IdentityID.KEY_DELIMITER) > 0) {
         String[] userNames = names[0].split(IdentityID.KEY_DELIMITER);
         return userNames[0];
      }

      return names[0];
   }

   public static IdentityID getTaskOwner(String taskId) {
      if(Tool.isEmptyString(taskId) || !taskId.contains(":")) {
         return null;
      }

      if(taskId.contains(IdentityID.KEY_DELIMITER)) {
         return IdentityID.getIdentityIDFromKey(taskId.substring(0, taskId.indexOf(":")));
      }

      return null;
   }

   public static String getDeviceName(String id) {
      DeviceRegistry registry = DeviceRegistry.getRegistry();
      DeviceInfo[] devices = registry.getDevices();

      for(DeviceInfo device : devices) {
         if(id.equals(device.getId())) {
            return device.getName();
         }
      }

      return "";
   }

   /**
    * Add user space path prefix to the original folder.
    *
    * @param principal current user.
    * @param path original folder.
    *
    * @return orgName/userName/originalFolder
    */
   public static String addUserSpacePathPrefix(Principal principal, String path) {
      if(path == null) {
         return null;
      }

      if(!SUtil.isSecurityOn() || principal == null) {
         return path;
      }

      String dir = null;
      String file = path;
      int idx = path.lastIndexOf("/");

      if(idx != -1) {
         dir = path.substring(0, idx);
         file = path.substring(idx + 1);
      }

      StringBuilder stringBuilder = new  StringBuilder();

      if(SUtil.isMultiTenant()) {
         stringBuilder.append(IdentityID.getIdentityIDFromKey(principal.getName()).getOrgID());
         stringBuilder.append("/");
      }

      boolean internalUser = SUtil.isInternalUser(principal);

      if(internalUser || principal.getName().contains(IdentityID.KEY_DELIMITER)) {
         stringBuilder.append(IdentityID.getIdentityIDFromKey(principal.getName()).getName());
      }
      else {
         stringBuilder.append(principal.getName());
      }

      String prefix = stringBuilder.toString();

      if(!StringUtils.isEmpty(SreeEnv.getProperty("server.save.locations"))) {
         List<ServerLocation> serverLocations = SUtil.getServerLocations();
         String serverPath = "";

         for(ServerLocation serverLocation : serverLocations) {
            if(dir != null && dir.startsWith(serverLocation.path())) {
               serverPath = serverLocation.path();
               dir = dir.substring(serverLocation.path().length());
               break;
            }
         }
         if(dir == null) {
            dir = prefix;
         }
         else {
            dir = serverPath + "/" + prefix + dir;
         }

         return dir + "/" + file;
      }

      if(path.startsWith(prefix)) {
         return path;
      }
      else {
         if(path.startsWith("/")) {
            return prefix + path;
         }
         else {
            return prefix + "/" + path;
         }
      }
   }

   public static List<ServerLocation> getServerLocations() {
      List<ServerLocation> locations = new ArrayList<>();
      String property = SreeEnv.getProperty("server.save.locations");

      if(property != null && !property.trim().isEmpty()) {
         for(String str : property.trim().split(";")) {
            String[] pathProperties = str.split("\\|");
            String path = pathProperties[0];
            String label = pathProperties[1];
            String username = null;
            String password = null;
            String secretId = null;
            boolean useSecretId = false;

            if(path.contains("?")) {
               String param = path.substring(path.lastIndexOf("?") + 1);
               String[] kv = param.split("=");

               if(kv.length == 2 && kv[0].equals("useSecretId")) {
                  useSecretId = Boolean.parseBoolean(kv[1]);
               }

               path = path.substring(0, path.lastIndexOf("?"));
            }

            if(pathProperties.length > 2) {
               if(useSecretId) {
                  secretId = pathProperties[2];
               }
               else {
                  username = pathProperties[2];
                  password = pathProperties.length > 3 ? pathProperties[3] : null;
               }
            }

            path = path.replaceAll("[/\\\\]+$", "");
            ServerPathInfoModel pathInfoModel = ServerPathInfoModel.builder()
               .path(path)
               .username(username)
               .password(password == null ? null : Util.PLACEHOLDER_PASSWORD)
               .secretId(secretId)
               .useCredential(useSecretId)
               .ftp(!Tool.isEmptyString(username) || !Tool.isEmptyString(secretId))
               .oldPasswordKey(Tool.buildString(path, label, username))
               .build();
            locations.add(ServerLocation.builder().path(path).label(label).pathInfoModel(pathInfoModel).build());
         }
      }

      locations.sort(Comparator.comparing(ServerLocation::label));
      return locations;
   }

   public static void configBinaryTypes(IgniteConfiguration config) {
      BinaryConfiguration binaryCfg = new BinaryConfiguration();
      binaryCfg.setTypeConfigurations(getBinaryTypeConfigurations());
      config.setBinaryConfiguration(binaryCfg);
   }

   private static List<BinaryTypeConfiguration> getBinaryTypeConfigurations() {
      List<BinaryTypeConfiguration> binaryTypeConfigurations = new ArrayList<>();
      BinaryTypeConfiguration typeCfg = new BinaryTypeConfiguration();
      typeCfg.setTypeName(Object2ObjectOpenHashMap.class.getName());
      typeCfg.setSerializer(new Object2ObjectOpenHashMapSerializer());
      binaryTypeConfigurations.add(typeCfg);

      return binaryTypeConfigurations;
   }

   public static String writeCookiesString(Cookie[] cookies) {
      if(cookies != null) {
         ObjectMapper objectMapper = new ObjectMapper();
         Map<String, String> cookieData = new HashMap<>();

         for(Cookie cookie : cookies) {
            cookieData.put(cookie.getName(), cookie.getValue());
         }

         try {
            return objectMapper.writeValueAsString(cookieData);
         }
         catch (Exception e) {
            throw new RuntimeException("Error parsing cookie data to JSON", e);
         }
      }

      return null;
   }

   public static String[] parseSignUpUserNames(IdentityID userID, SRPrincipal principal) {
      String firstName = null;
      String lastName = null;

      if(principal != null) {
         firstName= principal.getProperty("SignUpFirstName");
         lastName = principal.getProperty("SignUpLastName");
      }

      firstName = firstName != null ? firstName : userID.getName().split(" ")[0];
      lastName = lastName != null ? lastName :
         userID.getName().split(" ").length > 1 ? userID.name.split(" ")[1] : "";

      return new String[]{firstName, lastName};
   }

   public static String getLoginOrganization(HttpServletRequest request) {
      String orgID = null;

      if(SUtil.isMultiTenant()) {
         String type = SreeEnv.getProperty("security.login.orgLocation", "domain");

         if("path".equals(type)) {
            URI uri = URI.create(LinkUriArgumentResolver.getLinkUri(request));
            String requestedPath = request.getPathInfo();

            if(requestedPath == null) {
               requestedPath = uri.getRawPath();
            }

            if(requestedPath != null) {
               if(requestedPath.startsWith("/")) {
                  requestedPath = requestedPath.substring(1);
               }

               int index = requestedPath.indexOf('/');

               if(index < 0) {
                  orgID = requestedPath;
               }
               else {
                  orgID = requestedPath.substring(0, index);
               }
            }
         }
         else {
            // get the lowest level subdomain, of the form "http://orgID.somehost.com/"
            String host = LinkUriArgumentResolver.getRequestHost(request);

            if(host != null && !isIpHost(host)) {
               int index = host.indexOf('.');

               if(index >= 0) {
                  orgID = host.substring(0, index);
               }
            }
         }

         if(orgID != null) {
            boolean matched = false;

            for(String org : SecurityEngine.getSecurity().getOrganizations()) {
               if(orgID.equalsIgnoreCase(org)) {
                  matched = true;
                  orgID = org;
               }
            }

            if(!matched) {
               orgID = null;
            }
         }
      }

      return orgID;
   }

   private static boolean isIpHost(String host) {
      if(host == null) {
         return false;
      }

      int index = host.lastIndexOf(":");
      String hostName = host;

      if(index > 0) {
         String port = host.substring(index + 1);

         if(!org.apache.commons.lang.StringUtils.isNumeric(port)) {
            return false;
         }

         hostName = host.substring(0, index - 1);
      }


      return InetAddressUtils.isIPv4Address(hostName);
   }

   public static String getOrganizationId(String indexedStorageId) {
      if(indexedStorageId == null || !indexedStorageId.endsWith("__indexedStorage")) {
         return null;
      }

      String lowcase_orgID = indexedStorageId.substring(0, indexedStorageId.length() - 16);
      SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();
      String[] ids = provider.getOrganizationIDs();

      return Arrays.stream(ids)
         .filter(id -> Tool.equals(id, lowcase_orgID, false))
         .findFirst()
         .orElse(lowcase_orgID);
   }

   private static final List<WeakReference<HttpSession>> userSessions = new ArrayList<>();
   private static BitSet dontNeedEncoding;
   private static ScheduleThread scheduleThread = null;
   private static GroupedThread gScheduleThread = null;
   private static Thread scheduleListener = null;
   public static final String MY_LOCALE = "My Locale";

   static {
      dontNeedEncoding = new BitSet(256);
      int i;

      for(i = 'a'; i <= 'z'; i++) {
         dontNeedEncoding.set(i);
      }

      for(i = 'A'; i <= 'Z'; i++) {
         dontNeedEncoding.set(i);
      }

      for(i = '0'; i <= '9'; i++) {
         dontNeedEncoding.set(i);
      }

      dontNeedEncoding.set(' ');
      dontNeedEncoding.set('-');
      dontNeedEncoding.set('_');
      dontNeedEncoding.set('.');
      dontNeedEncoding.set('*');
      dontNeedEncoding.set('$');
      dontNeedEncoding.set('&');
      dontNeedEncoding.set(',');
      dontNeedEncoding.set('@');
      dontNeedEncoding.set('(');
      dontNeedEncoding.set(')');
      dontNeedEncoding.set(':');
      dontNeedEncoding.set('^');
      dontNeedEncoding.set('!');
      dontNeedEncoding.set('#');
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(SUtil.class);

   /**
    * Name of the logger used for auditing log ins by MAC address.
    */
   public static final String MAC_LOG_NAME = "inetsoft.sree.MACAudit";
   public static final String VPM_USER = "composer_vpm_user";
   private static final Logger MAC_LOG = LoggerFactory.getLogger(MAC_LOG_NAME);
   private static final ReentrantLock scheduleLock = new ReentrantLock();

   private static final String FS_STARTED_KEY = SUtil.class.getName() + ".fsServiceStarted";
}
