/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web;

import inetsoft.util.CachedByteArrayOutputStream;
import inetsoft.util.Tool;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

/**
 * Filter that prevents calls to {@link ServletRequest#getParameter(String)} from breaking
 * the input stream.
 *
 * @since 12.3
 */
public class SafeInputStreamFilter implements Filter {
   @Override
   public void init(FilterConfig filterConfig) {
      // NO-OP
   }

   @Override
   public void doFilter(ServletRequest request, ServletResponse response,
                        FilterChain chain) throws IOException, ServletException
   {
      HttpServletRequest httpRequest = (HttpServletRequest) request;

      if("GET".equals(httpRequest.getMethod()) || "DELETE".equals(httpRequest.getMethod())) {
         // wrapping isn't necessary, no body
         chain.doFilter(request, response);
      }
      else {
         try(Wrapper wrapper = new Wrapper(httpRequest)) {
            chain.doFilter(wrapper, response);
         }
         catch(IOException | ServletException e) {
            throw e;
         }
         catch(Exception e) {
            Throwable cause = e.getCause();

            if(cause != null &&
               GlobalExceptionHandler.isClientAbortException(cause.getClass().getName()))
            {
               LOG.debug("Failed to clean up cached request input", e);
            }
            else {
               // caused by the wrapper's auto-close method, just log it
               LOG.warn("Failed to clean up cached request input", e);
            }
         }
      }
   }

   @Override
   public void destroy() {
      // NO-OP
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(SafeInputStreamFilter.class);

   private static final class Wrapper
      extends HttpServletRequestWrapper implements AutoCloseable
   {
      public Wrapper(HttpServletRequest request) {
         super(request);
      }

      @Override
      public ServletInputStream getInputStream() throws IOException {
         ServletInputStream result;
         String contentType = getContentType();

         if(buffer == null && "application/x-www-form-urlencoded".equals(contentType)) {
            // Bug #38113, always cache the input stream of form encoded requests, because after
            // the initial filter chain is processed, the request could be forwarded and
            // getParameter() could be called on the second pass through the filter chain or
            // getParameter() could be called by a filter after the call to FilterChain.doFilter()
            createBuffer();
         }

         if(buffer == null) {
            result = super.getInputStream();
         }
         else {
            result = new CachedServletInputStream(buffer);
         }

         return result;
      }

      @Override
      public BufferedReader getReader() throws IOException {
         String encoding = getCharacterEncoding();

         if(encoding == null) {
            encoding = "UTF-8";
         }

         return new BufferedReader(new InputStreamReader(getInputStream(), encoding));
      }

      @Override
      public String getParameter(String name) {
         if(createBuffer() && parameters != null) {
            String[] values = parameters.get(name);
            return values == null || values.length == 0 ? null : values[0];
         }

         return super.getParameter(name);
      }

      @Override
      public Map<String, String[]> getParameterMap() {
         if(createBuffer() && parameters != null) {
            return Collections.unmodifiableMap(parameters);
         }

         return super.getParameterMap();
      }

      @Override
      public Enumeration<String> getParameterNames() {
         if(createBuffer() && parameters != null) {
            return Collections.enumeration(parameters.keySet());
         }

         return super.getParameterNames();
      }

      @Override
      public String[] getParameterValues(String name) {
         if(createBuffer() && parameters != null) {
            return parameters.get(name);
         }

         return super.getParameterValues(name);
      }

      @Override
      public void close() throws Exception {
         if(buffer != null) {
            buffer.dispose();
            buffer = null;
         }
      }

      /**
       * Creates the input stream buffer, if necessary.
       */
      private boolean createBuffer() {
         if(buffer == null && !"GET".equals(getMethod())) {
            try {
               // Bug #34379. Do not parse an empty buffer after the stream has been
               // read to ensure that the parameter is retrieved from the origin request.
               if(super.getInputStream().isFinished()) {
                  return false;
               }

               buffer = new CachedByteArrayOutputStream();
               IOUtils.copy(super.getInputStream(), buffer);
               buffer.close();
               parseParameters();
            }
            catch(EOFException e) {
               LOG.debug("Failed to read input stream: " + e, e);
            }
            catch(IOException e) {
               throw new RuntimeException("Failed to create input stream buffer", e);
            }
         }

         return !"GET".equals(getMethod());
      }

      private void parseParameters() throws IOException {
         // it seems that newer versions of tomcat won't parse the parameters in the body after
         // get input stream has been called, so we need to handle parsing the parameters ourselves
         parameters = new HashMap<>();
         parseQueryString(getQueryString());

         String contentType = getContentType();

         if("application/x-www-form-urlencoded".equals(contentType)) {
            StringWriter buffer = new StringWriter();

            try(BufferedReader reader = getReader();
                PrintWriter writer = new PrintWriter(buffer))
            {
               String line;

               while((line = reader.readLine()) != null) {
                  writer.print(line);
               }
            }

            parseQueryString(buffer.toString());
         }
         else if("multipart/form-data".equals(contentType)) {
            // we'll cross this bridge when we come to it
            LOG.warn("Caching parameters for multipart/form-data not implemented");
         }
      }

      private void parseQueryString(String query) {
         parameters.putAll(Tool.parseQueryString(query));
      }

      private CachedByteArrayOutputStream buffer;
      private Map<String, String[]> parameters;
   }

   private static final class CachedServletInputStream extends ServletInputStream {
      CachedServletInputStream(CachedByteArrayOutputStream buffer) throws IOException {
         this.input = buffer.getInputStream();
      }

      @Override
      public int read() throws IOException {
         return input.read();
      }

      @Override
      public int read(byte[] b) throws IOException {
         return input.read(b);
      }

      @Override
      public int read(byte[] b, int off, int len) throws IOException {
         return input.read(b, off, len);
      }

      @Override
      public long skip(long n) throws IOException {
         return input.skip(n);
      }

      @Override
      public int available() throws IOException {
         return input.available();
      }

      @Override
      public void close() throws IOException {
         input.close();
      }

      @Override
      public synchronized void mark(int readlimit) {
         input.mark(readlimit);
      }

      @Override
      public synchronized void reset() throws IOException {
         input.reset();
      }

      @Override
      public boolean markSupported() {
         return input.markSupported();
      }

      @Override
      public boolean isFinished() {
         try {
            return input.available() == 0;
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
   }
}
