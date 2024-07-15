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
package inetsoft.sree.web;

import com.nimbusds.jose.util.IOUtils;
import inetsoft.sree.ClientInfo;
import inetsoft.sree.RepletRepository;
import inetsoft.sree.internal.HTMLUtil;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.util.FileSystemService;
import inetsoft.util.Tool;
import inetsoft.util.script.JavaScriptEngine;
import inetsoft.util.swap.XSwapper;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.ref.SoftReference;
import java.net.URLDecoder;
import java.security.Principal;
import java.util.*;

/**
 * Service request that wraps an HttpServletRequest. This class is used for HTTP
 * clients, such as the repository servlet.
 * @author InetSoft Technology Corp.
 * @since  5.0
 */
public class HttpServiceRequest implements ServiceRequest {
   public HttpServiceRequest() {
      this.requestRef = new SoftReference<>(null);
   }

   /**
    * Create a new instance of HttpServiceRequest.
    * @param request the base HTTP request.
    */
   public HttpServiceRequest(HttpServletRequest request) {
      // @by larryl, since the request is held in VariableTable inside a
      // ReportSheet, it would not be freed until the report times out. For
      // tomcat, this would hold the input/output buffer associated with
      // this one request, which causes memory to quickly run out.
      this.requestRef = new SoftReference<>(request);

      // @by billh, if we call getParameter in Tomcat, the input stream of http
      // request will be cleared with nothing left, and for xml communication,
      // we can not tolerate that. I have to keep a copy of the input stream
      if("POST".equals(request.getMethod())) {
         try {
            postData = new PostData(request.getInputStream());
         }
         catch(IOException ioe) {
            LOG.error("Failed to copy HTTP POST data", ioe);
            postData = new PostData();
         }
      }
      else {
         postData = new PostData();
      }

      paramsMap = readGetParameters();
      paramsMap = "POST".equals(request.getMethod()) ?
         mergeParameters(paramsMap, readPostParameters(request)) : paramsMap;
      // validate the parameter values,
      // protect system against cross-site attack
      HTMLUtil.validatePairs(paramsMap);

      // @by larryl, we need to make a copy of the request since Websphere's
      // request object only works in the current thread
      session = request.getSession(true);
      remoteUser = IdentityID.getIdentityIDFromKey(request.getRemoteUser());
      remoteAddr = Tool.getRemoteAddr(request);
      remoteHost = Tool.getRemoteHost(request);
      serverName = request.getServerName();
      serverPort = request.getServerPort();
      userAgent = request.getHeader("User-Agent");
      requestedSessionId = session.getId();

      Enumeration<?> iter = request.getHeaderNames();

      while(iter.hasMoreElements()) {
         String name = (String) iter.nextElement();
         headerMap.put(name, request.getHeader(name));
      }

      linkURI = SUtil.getReportServletUrl(SUtil.getRequestURI(request));
      cookies = request.getCookies();
   }

   /**
    * Set the value of a request parameter.
    * @param name the name of the parameter.
    * @value the value of the parameter.
    */
   @Override
   public void setParameter(String name, String value) {
      paramsMap.put(name, new String[] {value});
   }

   /**
    * Get the value of a request parameter.
    * @param name the name of the parameter.
    * @return the value of the parameter, or <code>null</code> if the parameter
    * does not exist.
    */
   @Override
   public String getParameter(String name) {
      String[] values = getParameterValues(name);

      if(values != null) {
         return values[0];
      }
      else {
         return null;
      }
   }

   /**
    * Get an array of String objects containing all of the values the given
    * request parameter has, or <code>null</code> if the parameter does not
    * exist.
    * @param name the name of the parameter.
    * @return an array of String objects containing the parameter's values.
    */
   @Override
   public String[] getParameterValues(String name) {
      return paramsMap.get(name);
   }

   /**
    * Get the names of all request parameters.
    * @return a list of the names of all the parameters in this request.
    */
   @Override
   public Enumeration<String> getParameterNames() {
      return Collections.enumeration(paramsMap.keySet());
   }

   /**
    * Get a request header value.
    */
   public String getHeader(String name) {
      return headerMap.get(name);
   }

   /**
    * Get all the headers in the request.
    */
   public Enumeration<String> getHeaderNames() {
      return Collections.enumeration(headerMap.keySet());
   }

   /**
    * Get the value of a persistent attribute in the current session.
    * @param name the name of the attribute.
    * @return the value of the attribute.
    */
   @Override
   public Object getAttribute(String name) {
      try {
         return session.getAttribute(name);
      }
      catch(IllegalStateException ex) {
         HttpServletRequest req = getRequest();

         if(req != null) {
            session = req.getSession(true);
            return session.getAttribute(name);
         }
      }

      return null;
   }

   /**
    * Set the value of a persistent attribute in the current session.
    * @param name the name of the attribute.
    * @param value the value of the attribute.
    */
   @Override
   public void setAttribute(String name, Object value) {
      try {
         if(value == null) {
            session.removeAttribute(name);
         }
         else {
            session.setAttribute(name, JavaScriptEngine.unwrap(value));
         }
      }
      catch(IllegalStateException ex) {
         HttpServletRequest req = getRequest();

         if(req != null) {
            session = req.getSession(true);
            session.setAttribute(name, value);
         }
      }
   }

   /**
    * Get the names of all persistent attributes in the current session.
    * @return a list of the names of all the attributes.
    */
   @Override
   public Enumeration<?> getAttributeNames() {
      try {
         return session.getAttributeNames();
      }
      catch(IllegalStateException ex) {
         LOG.warn("Failed to list session attributes", ex);
         // session might be invalid, here renew session
         HttpServletRequest req = getRequest();

         if(req != null) {
            session = req.getSession(true);
            return session.getAttributeNames();
         }

         return new Vector().elements();
      }
   }

   /**
    * Get the user that is making the request.
    * @return a Principal object that identifies the user.
    */
   @Override
   public Principal getPrincipal() {
      boolean fireEvent = false;

      try {
         // @by jasons, if security is enabled, don't fire a login event for
         // the anonymous user -- this is an initial request before
         // authentication
         fireEvent = SecurityEngine.getSecurity().getSecurityProvider().isVirtual()
            || remoteUser != null && !ClientInfo.ANONYMOUS.equals(remoteUser);
      }
      catch(Exception exc) {
         LOG.debug("Failed to get security engine", exc);
      }

      Principal principal =
         SUtil.getPrincipal(remoteUser, getRemoteAddr(), session, fireEvent);
      setPrincipal(principal);
      return principal;
   }

   /**
    * Set the Principal object that identifies the user.
    *
    * @param principal the Principal object.
    */
   @Override
   public void setPrincipal(Principal principal) {
      setPrincipal(session, principal);
   }

   /**
    * Sets the principal that identifies the user.
    *
    * @param session   the current HTTP session.
    * @param principal the principal object.
    *
    * @since 11.4
    */
   public static void setPrincipal(HttpSession session, Principal principal) {
      if(principal == null) {
         synchronized(session) {
            SessionTimeoutListener listener = (SessionTimeoutListener)
               session.getAttribute("inetsoft.sree.web.SessionTimeoutListener");

            if(listener == null) {
               listener = new SessionTimeoutListener();
               session.setAttribute(
                  "inetsoft.sree.web.SessionTimeoutListener", listener);
            }

            listener.setPrincipal(null);
         }

         session.removeAttribute(RepletRepository.PRINCIPAL_COOKIE);
      }
      else {
         session.setAttribute(RepletRepository.PRINCIPAL_COOKIE, principal);

         if(principal instanceof SRPrincipal) {
            SRPrincipal suser = (SRPrincipal) principal;
            suser.setSession(session);
         }

         synchronized(session) {
            SessionTimeoutListener listener = (SessionTimeoutListener)
               session.getAttribute("inetsoft.sree.web.SessionTimeoutListener");

            if(listener == null) {
               listener = new SessionTimeoutListener();
               session.setAttribute(
                  "inetsoft.sree.web.SessionTimeoutListener", listener);
            }

            // @by stephenwebster, Fix bug1414698494642
            // setPrincipal must be called again on the listener so that the
            // current session principal is the same as the listener principal.
            // Otherwise, the listener calls logout on the wrong principal.
            // The reason that this happens is the first principal assigned
            // to the session is an 'anonymous' principal prior to login.
            // In the case of a session based license, it will orphan the principal
            // in the SessionLicenseService, and there will be a slow license leak
            // and eventually no users will be able to login.
            listener.setPrincipal(principal);
         }
      }
   }

   /**
    * Get the IP address of the remote client making the request.
    * @return an IP (Internet Protocol) address.
    */
   @Override
   public String getRemoteAddr() {
      return remoteAddr;
   }

   /**
    * Get the host name of the remote client making the request.
    * @return an host name.
    */
   @Override
   public String getRemoteHost() {
      return remoteHost;
   }

   /**
    * Get the host name of the server that received the request.
    * @return server name.
    */
   @Override
   public String getServerName() {
      return serverName;
   }

   /**
    * Get the port number on which this request was received.
    * @return the server port.
    */
   @Override
   public int getServerPort() {
      return serverPort;
   }

   /**
    * Get the URI that should be used for all hyperlinks back to the report
    * server.
    * @return the URI of the report server.
    */
   @Override
   public String getLinkURI() {
      return linkURI;
   }

   /**
    * Get the prefix that is used to identify the forms, parameters, etc. of
    * a report in a page that contains multiple reports. This is a no-op method
    * in this implementation; it will always return <code>null</code>.
    * @return the prefix for the report to which this request applies or
    * <code>null</code> if no prefix is applicable.
    */
   @Override
   public String getReportPrefix() {
      return null;
   }

   /**
    * Determine if the layout of the report should be overridden and the table
    * layout be used.
    * @return <code>true</code> if the report should be forced to use the table
    * layout; <code>false</code> otherwise.
    */
   @Override
   public boolean isForceTable() {
      return false;
   }

   /**
    * Determine if view type of the report supports frame-based features, such
    * as the new search panel.
    * @return <code>true</code>.
    */
   @Override
   public boolean isFrameSupported() {
      return true;
   }

   /**
    * Determines if the report toolbar should be made "floating" using CSS-P.
    *
    * @return <code>true</code> if a floating toolbar is supported.
    *
    * @since 8.0
    */
   @Override
   public boolean isFloatToolbarSupported() {
      return true;
   }

   /**
    * Determines if PDF exports should be forced to load in a new window.
    *
    * @return <code>true</code> if PDF exports should be forced to load in a
    *         new window.
    */
   @Override
   public boolean isForcePDFPopup() {
      return false;
   }

   /**
    * Get the user agent of the client. This is typically the name of the web
    * browser being used.
    * @return the user agent.
    */
   @Override
   public String getUserAgent() {
      return userAgent;
   }

   /**
    * Get the post content type.
    */
   public String getContentType() {
      return contentType;
   }

   /**
    * Gets the content type as originally reported by the HTTP request.
    *
    * @return the content type.
    */
   public String getOriginalContentType() {
      return originalContentType;
   }

   /**
    * Get request protocol.
    * @return protocol such as http, https.
    */
   @Override
   public String getProtocol() {
      String url = getRequest().getRequestURL().toString();
      int idx = url.indexOf(":");

      return idx < 0 ? "http:" : url.substring(0, idx + 1);
   }

   /**
    * Get the length of the post contents.
    */
   public int getContentLength() {
      return contentLength;
   }

   /**
    * Gets the character encoding of the request.
    *
    * @return the character encoding.
    */
   public String getCharacterEncoding() {
      return characterEncoding;
   }

   /**
    * Get the session ID requested by the client.
    * @return a String specifying the session ID, or <code>null</code> if the
    * request did not specify a session ID.
    */
   @Override
   public String getRequestedSessionId() {
      return requestedSessionId;
   }

   /**
    * Get an input stream to read the request raw data. This only works for
    * servlet deployment and should not be used in JSP.
    */
   @Override
   public InputStream getInputStream() throws IOException {
      return postData.getInputStream();
   }

   /**
    * Get the servlet request that this object wraps.
    * @return the base request.
    */
   public HttpServletRequest getRequest() {
      return requestRef.get();
   }

   /**
    * Get an array containing all of the Cookie objects the client sent with
    * this request. This method returns null if no cookies were sent.
    * @return an array of all the Cookies included with this request,
    * or <code>null</code> if the request has no cookies.
    */
   @Override
   public Cookie[] getCookies() {
      return cookies;
   }

   /**
    * Read "get" parameters.
    *
    * @return the parameter key-values pairs stored in hashtable
    */
   private Map<String, String[]> readGetParameters() {
      HttpServletRequest request = requestRef.get();

      if(request == null) {
         throw new RuntimeException(
            "HttpServletRequest has timed out and is not accessible");
      }

      String query = request.getQueryString();
      Map<String, String[]> parameters = new HashMap<>();

      if(query == null) {
         return parameters;
      }

      // @by jasons check for userURL (integration servlet parameter) if it
      // exists, everything following it is the value of the parameter
      int pos = query.indexOf("userURL=");

      if(pos >= 0) {
         parameters.put("userURL", new String[] { query.substring(pos + 8) });

         if(pos == 0) {
            query = "";
         }
         else {
            query = query.substring(0, pos);
         }
      }

      StringTokenizer tok = new StringTokenizer(query, "&", false);

      while(tok.hasMoreTokens()) {
         String pair = tok.nextToken();
         pos = pair.indexOf('=');

         if(pos != -1) {
            String key = pair.substring(0, pos);
            String value = pair.substring(pos + 1, pair.length());

            try {
               key = URLDecoder.decode(key, "UTF-8");
               value = URLDecoder.decode(value, "UTF-8");
            }
            catch(Exception ignore) {
            }

            // @by alam, fix bug1282547525951, if parameter name is para or
            // start with para_, IE encount problem. So we encode the parameter
            // name in this situation, and decode it here.
            if(key.indexOf("^_^") == 0) {
               key = key.substring(key.indexOf("^_^") + 3);
            }

            // @by charvi
            // @implemented feature1123184148715
            // The escape characters in the parameter values are escaped
            // when the request is sent over. We therefore need to
            // unescape those before saving the parameter values.

            // @by tonyy, fix bug1130378511418, it causes unnecessary unescape
            // such as in file full path "C:\\opt\\tomcat5.0\\webapps"
            // value = unescapeEscapeCharacters(value);

            String[] values;

            if(parameters.containsKey(key)) {
               String[] oldValues = parameters.get(key);
               values = new String[oldValues.length + 1];
               System.arraycopy(oldValues, 0, values, 0, oldValues.length);
               values[oldValues.length] = value;
            }
            else {
               values = new String[1];
               values[0] = value;
            }

            parameters.put(key, values);
         }
      }

      return parameters;
   }

   /**
    * Read "post" parameters.
    *
    * @return the parameter key-values pairs stored in hashtable
    */
   @SuppressWarnings({ "deprecation", "unchecked" })
   private Map<String, String[]> readPostParameters(HttpServletRequest req) {
      HttpServletRequest request = requestRef.get();

      if(request == null) {
         throw new RuntimeException(
            "HttpServletRequest has timed out and is not accessible");
      }

      contentType = request.getContentType();

      if(contentType != null) {
         originalContentType = contentType;

         if(contentType.indexOf(";") > 0) {
            contentType = contentType.substring(0, contentType.indexOf(";"));
         }

         contentType = contentType.toLowerCase().trim();
      }

      contentLength = request.getContentLength();
      characterEncoding = request.getCharacterEncoding();

      try {
         if(contentType != null &&
            contentType.startsWith("application/x-www-form-urlencoded"))
         {
            Map<String, String[]> parameters;

            if(contentLength > postData.getLength()) {
               // @by jasons looks like tomcat has already read in the
               // input stream, try to get the parameters from the request
               parameters = new HashMap<>();
               Enumeration<?> names = req.getParameterNames();

               while(names.hasMoreElements()) {
                  String name = (String) names.nextElement();
                  parameters.put(name, req.getParameterValues(name));
               }
            }
            else {
               parameters = parsePostData(postData.getInputStream());
            }

            return parameters;
         }
      }
      catch(Exception ex) {
        // @by billh, if it's xml communication, the parse will always fail.
        // A better solution is to avoid the parse later in this case
      }

      return new Hashtable();
   }

   private Hashtable<String, String[]> parsePostData(InputStream input) throws IOException {
      Map<String, List<String>> map = new HashMap<>();
      String form = IOUtils.readInputStreamToString(input);
      String[] pairs = StringUtils.split(form, '&');

      for(String pair : pairs) {
         int index = pair.indexOf('=');
         String name = index < 0 ? pair : pair.substring(0, index);
         String value = index < 0 ? "" : pair.substring(index + 1);
         map.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
      }

      Hashtable<String, String[]> output = new Hashtable<>();

      for(Map.Entry<String, List<String>> e : map.entrySet()) {
         output.put(e.getKey(), e.getValue().toArray(new String[0]));
      }

      return output;
   }

   /**
    * Merge parameters read from "get" and "post" methods.
    */
   private Map<String, String[]> mergeParameters(Map<String, String[]> one,
                                                 Map<String, String[]> two)
   {
      if(one.size() == 0) {
         return two;
      }

      if(two.size() == 0) {
         return one;
      }

      Map<String, String[]> combined = new HashMap<>(one);

      for(String name : two.keySet()) {
         String[] oneValue = one.get(name);
         String[] twoValue = two.get(name);
         String[] combinedValue;

         if(oneValue == null) {
            combinedValue = twoValue;
         }
         else {
            combinedValue = new String[oneValue.length + twoValue.length];
            System.arraycopy(oneValue, 0, combinedValue, 0, oneValue.length);
            System.arraycopy(twoValue, 0, combinedValue, oneValue.length,
                             twoValue.length);
         }

         combined.put(name, combinedValue);
      }

      return combined;
   }

   private static final class CachedServletInputStream
      extends ServletInputStream
   {
      public CachedServletInputStream(PostData data) throws IOException {
         if(data.file == null) {
            this.input = new ByteArrayInputStream(data.data);
            this.data = null;
         }
         else {
            this.input = new FileInputStream(data.file);
            this.data = data;
         }
      }

      @Override
      public int available() throws IOException {
         return input.available();
      }

      @Override
      public void close() throws IOException {
         closed = true;
         data = null;
         input.close();
      }

      @Override
      public void mark(int readlimit) {
         input.mark(readlimit);
      }

      @Override
      public boolean markSupported() {
         return input.markSupported();
      }

      @Override
      public int read() throws IOException {
         return input.read();
      }

      @Override
      public int read(byte[] b, int off, int len) throws IOException {
         return input.read(b, off, len);
      }

      @Override
      public int read(byte[] b) throws IOException {
         return input.read(b);
      }

      @Override
      public void reset() throws IOException {
         input.reset();
      }

      @Override
      public long skip(long n) throws IOException {
         return input.skip(n);
      }

      @Override
      protected void finalize() throws Throwable {
         if(!closed) {
            try {
               close();
            }
            catch(Throwable ignore) {
            }
         }

         super.finalize();
      }

      @Override
      public boolean isFinished() {
         try {
            return available() == 0;
         }
         catch(IOException e) {
            throw new RuntimeException(e);
         }
      }

      @Override
      public boolean isReady() {
         return true;
      }

      @Override
      public void setReadListener(ReadListener readListener) {
         throw new UnsupportedOperationException();
      }

      private final InputStream input;
      // hold reference to post data so that the cache file is not deleted
      // until the last input stream is closed
      @SuppressWarnings("unused")
      private PostData data;
      private boolean closed = false;
   }

   private static final class PostData {
      public PostData() {
         file = null;
         data = new byte[0];
      }

      public PostData(InputStream input) throws IOException {
         int read;
         byte[] bytes = new byte[1024];

         OutputStream output = new ByteArrayOutputStream();

         while((read = input.read(bytes)) >= 0) {
            if(file == null && length + read > 1024 * 1024) {
               // cache posts longer than 1M to file
               file = FileSystemService.getInstance().getCacheFile(XSwapper.getPrefix() + ".tdat");
               file.deleteOnExit();

               OutputStream foutput = new FileOutputStream(file);
               foutput.write(((ByteArrayOutputStream) output).toByteArray());
               output = foutput;
            }

            length += read;
            output.write(bytes, 0, read);
         }

         if(file == null) {
            data = ((ByteArrayOutputStream) output).toByteArray();
         }
      }

      public ServletInputStream getInputStream() throws IOException {
         return new CachedServletInputStream(this);
      }

      public int getLength() {
         return length;
      }

      @Override
      protected void finalize() throws Throwable {
         if(file != null) {
            if(!file.delete()) {
               FileSystemService.getInstance().remove(file, 30000);
            }
         }

         super.finalize();
      }

      private int length = 0;
      private File file = null;
      private byte[] data = null;
   }

   // private HttpServletRequest request = null;
   private SoftReference<HttpServletRequest> requestRef;
   private HttpSession session;
   private PostData postData = null;
   private Map<String, String[]> paramsMap = new HashMap<>();
   private Map<String, String> headerMap = new HashMap<>();
   private IdentityID remoteUser;
   private String remoteAddr;
   private String remoteHost;
   private String serverName;
   private int serverPort;
   private String linkURI;
   private String userAgent;
   private String requestedSessionId;
   private String contentType;
   private String originalContentType;
   private int contentLength;
   private String characterEncoding;
   private Cookie[] cookies;

   private static final Logger LOG =
      LoggerFactory.getLogger(HttpServiceRequest.class);

   /**
    * Listener that logs out the authenticated user when the session is
    * invalidated or times out.
    *
    * @author InetSoft Technology
    * @since 11.4
    */
   private static final class SessionTimeoutListener
      implements HttpSessionBindingListener, Cloneable
   {
      /**
       * Creates a new instance of <tt>SessionTimeoutListener</tt>.
       */
      public SessionTimeoutListener() {
      }

      /**
       * Sets the principal for this request.
       *
       * @param principal the principal that identifies the remote user.
       */
      public void setPrincipal(Principal principal) {
         this.principal = principal;
      }

      @Override
      public void valueBound(HttpSessionBindingEvent event) {
         // NO-OP
      }

      @Override
      public void valueUnbound(HttpSessionBindingEvent event) {
         if(principal != null) {
            try {
               SUtil.logout(principal);
            }
            catch(Throwable exc) {
               LOG.warn("Failed to log out: " + principal, exc);
            }
         }
      }

      private transient Principal principal = null;
   }
}
