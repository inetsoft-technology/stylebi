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
package inetsoft.web;

import inetsoft.sree.SreeEnv;
import inetsoft.util.Tool;
import jakarta.servlet.*;
import jakarta.servlet.http.*;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

/**
 * Filter that compresses the HTTP response body.
 *
 * This class was created to have better control over which endpoints get compressed and which
 * don't to avoid the <a href="http://breachattack.com/">BREACH exploit</a>.
 */
public class GZIPFilter implements Filter {
   public GZIPFilter() {
      String str = SreeEnv.getProperty("http.compress.whiteList");

      if(str != null) {
         whiteList = Arrays.stream(Tool.split(str, ',')).collect(Collectors.toSet());
      }
   }

   @Override
   public void init(FilterConfig filterConfig) throws ServletException {
      final String minCompressionSize = filterConfig.getInitParameter("minCompressionSize");

      if(minCompressionSize != null) {
         final int minSize = Integer.parseInt(minCompressionSize);
         this.minCompressionSize = Math.max(minSize, DEFAULT_MIN_COMPRESSION_SIZE);
      }

      final String compressibleMimeTypes = filterConfig.getInitParameter("compressibleMimeTypes");

      if(compressibleMimeTypes != null) {
         final String[] tokens = compressibleMimeTypes.split(",");
         List<String> values = new ArrayList<>(tokens.length);

         Arrays.stream(tokens)
            .map(String::trim)
            .filter(s -> s.length() > 0)
            .forEach(values::add);

         if(values.size() > 0) {
            this.compressibleMimeTypes = values;
         }
      }
   }

   @Override
   public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                        FilterChain filterChain) throws IOException, ServletException
   {
      HttpServletRequest request = (HttpServletRequest) servletRequest;
      HttpServletResponse response = (HttpServletResponse) servletResponse;

      if(acceptsGZIPEncoding(request) && isCompressibleEndpoint(request)) {
         ServletContext servletContext = request.getServletContext();
         final GZIPWrapper wrapper = new GZIPWrapper(response, servletContext == null ? null :
            servletContext.getServerInfo());
         filterChain.doFilter(request, wrapper);
         wrapper.close();
      }
      else {
         filterChain.doFilter(request, response);
      }
   }

   /**
    * @return true if the request accepts gzip encoding, false otherwise.
    */
   private boolean acceptsGZIPEncoding(HttpServletRequest request) {
      final String acceptEncoding = request.getHeader("Accept-Encoding");
      return acceptEncoding != null && acceptEncoding.contains("gzip");
   }

   /**
    * @return true if the requested endpoint is compressible, false otherwise.
    */
   private boolean isCompressibleEndpoint(HttpServletRequest request) {
      String path = request.getServletPath();
      return !path.contains("/api/public") && !path.endsWith("/xhr_send") &&
         !path.endsWith("/xhr_streaming") || whiteList.contains(path);
   }

   @Override
   public void destroy() {
      // no-op
   }

   private class GZIPWrapper extends HttpServletResponseWrapper {
      public GZIPWrapper(HttpServletResponse response, String serverType) {
         super(response);
         this.serverType = serverType;
      }

      public void close() throws IOException {
         if(printWriter != null) {
            printWriter.close();
         }

         if(gzipOutputStream != null) {
            gzipOutputStream.close();
         }
      }

      @Override
      public void flushBuffer() throws IOException {
         if(this.printWriter != null) {
            this.printWriter.flush();
         }

         IOException exception1 = null;

         try {
            if(this.gzipOutputStream != null) {
               this.gzipOutputStream.flush();
            }
         }
         catch(IOException e) {
            exception1 = e;
         }

         IOException exception2 = null;

         try {
            super.flushBuffer();
         }
         catch(IOException e) {
            exception2 = e;
         }

         if(exception1 != null) {
            throw exception1;
         }

         if(exception2 != null) {
            throw exception2;
         }
      }

      @Override
      public ServletOutputStream getOutputStream() throws IOException {
         if(this.printWriter != null) {
            throw new IllegalStateException(
               "PrintWriter obtained already - cannot get OutputStream");
         }

         if(this.gzipOutputStream == null) {
            final HttpServletResponse response = (HttpServletResponse) getResponse();
            this.gzipOutputStream = new GZIPServletOutputStream(response.getOutputStream(),
                                                                response, serverType);
         }
         return this.gzipOutputStream;
      }

      @Override
      public PrintWriter getWriter() throws IOException {
         if(this.printWriter == null && this.gzipOutputStream != null) {
            throw new IllegalStateException(
               "OutputStream obtained already - cannot get PrintWriter");
         }

         if(this.printWriter == null) {
            final HttpServletResponse response = (HttpServletResponse) getResponse();
            this.gzipOutputStream = new GZIPServletOutputStream(response.getOutputStream(),
                                                                response, serverType);
            this.printWriter = new PrintWriter(new OutputStreamWriter(
               this.gzipOutputStream, response.getCharacterEncoding()));
         }

         return this.printWriter;
      }

      @Override
      public void setContentLength(int len) {
         // ignore, since content length of zipped content does not match content length of
         // unzipped content.
      }

      private GZIPServletOutputStream gzipOutputStream;
      private PrintWriter printWriter;
      private String serverType;
   }

   private class GZIPServletOutputStream extends ServletOutputStream {
      private GZIPServletOutputStream(ServletOutputStream servletOutputStream,
                                      HttpServletResponse response, String serverType)
      {
         this.servletOutputStream = servletOutputStream;
         this.response = response;
         this.serverType = serverType;
      }

      @Override
      public void close() throws IOException {
         if(closed) {
            return;
         }

         closed = true;

         if(isCompressible()) {
            if(!isCompressed()) {
               // Length less than minimum compression size, so dump preGZIPBuffer to the regular
               // output stream.
               preGZIPBuffer.writeTo(servletOutputStream);
               servletOutputStream.close();
            }
            else {
               gzipOutputStream.close();
            }
         }
         else {
            servletOutputStream.close();
         }
      }

      @Override
      public void flush() throws IOException {
         if(isCompressible()) {
            // If not compressed, then written count is less than min size so do nothing.
            if(isCompressed()) {
               gzipOutputStream.flush();
            }
         }
         else {
            servletOutputStream.flush();
         }
      }

      @Override
      public boolean isReady() {
         return true;
      }

      @Override
      public void setWriteListener(WriteListener writeListener) {
         servletOutputStream.setWriteListener(writeListener);
      }

      @Override
      public void write(int b) throws IOException {
         if(isCompressible()) {
            if(isCompressed()) {
               gzipOutputStream.write(b);
            }
            else if(preGZIPBuffer.size() < minCompressionSize - 1) {
               preGZIPBuffer.write(b);
            }
            else if(preGZIPBuffer.size() == minCompressionSize - 1) {
               tryWriteCompressionHeaders();

               if(compressionHeadersWritten) {
                  gzipOutputStream = new BufferedOutputStream(
                     new GZIPOutputStream(servletOutputStream));
                  preGZIPBuffer.writeTo(gzipOutputStream);
                  gzipOutputStream.write(b);
               }
               else {
                  preGZIPBuffer.writeTo(servletOutputStream);
                  servletOutputStream.write(b);
               }
            }
         }
         else {
            servletOutputStream.write(b);
         }
      }

      /**
       * @return true if the contentType matches one of the compressible mime types, false
       * otherwise.
       */
      private boolean canCompressContentType() {
         if(compressContentType == null) {
            final String contentType = response.getContentType();

            if(contentType != null) {
               compressContentType = compressibleMimeTypes.stream()
                  .anyMatch(contentType::contains);
            }
            else {
               compressContentType = false;
            }
         }

         return compressContentType;
      }

      private boolean isCompressed() {
         return compressionHeadersWritten != null && compressionHeadersWritten;
      }

      private boolean isCompressible() {
         return (compressionHeadersWritten == null || compressionHeadersWritten) &&
            canCompressContentType();
      }

      /**
       * Try to write the compression headers and correct the cache headers.
       * This will fail if the response is already committed.
       */
      private void tryWriteCompressionHeaders() {
         if(!response.isCommitted() && compressionHeadersWritten == null) {
            final String contentEncoding = response.getHeader("Content-Encoding");

            if(contentEncoding == null ||
               !(contentEncoding.contains("gzip") || contentEncoding.contains("br")))
            {
               response.addHeader("Content-Encoding", "gzip");

               if(serverType == null || !serverType.startsWith("IBM WebSphere")) {
                  response.setContentLength(-1);
               }

               response.addHeader("Vary", "Accept-Encoding");
               changeStrongETag();
               compressionHeadersWritten = true;
            }
         }

         if(compressionHeadersWritten == null) {
            compressionHeadersWritten = false;
         }
      }

      /**
       * Change strong ETag to weak ETag and add -gzip suffix to differentiate from uncompressed
       * entity.
       */
      private void changeStrongETag() {
         String etag = response.getHeader("ETag");

         if(etag != null && !etag.startsWith("W/")) {
            if(etag.startsWith("\"") && etag.endsWith("\"")) {
               etag = etag.substring(1, etag.length() - 1);
            }

            response.setHeader("ETag", "W/\"" + etag + "-gzip\"");
         }
      }

      private OutputStream gzipOutputStream;
      private Boolean compressContentType;
      private Boolean compressionHeadersWritten;
      private boolean closed;

      private final ServletOutputStream servletOutputStream;
      private final HttpServletResponse response;
      private final ByteArrayOutputStream preGZIPBuffer = new ByteArrayOutputStream();
      private final String serverType;
   }

   private int minCompressionSize = DEFAULT_MIN_COMPRESSION_SIZE;
   private List<String> compressibleMimeTypes = DEFAULT_COMPRESSIBLE_MIME_TYPES;

   private static final int DEFAULT_MIN_COMPRESSION_SIZE = 2048;
   private static final List<String> DEFAULT_COMPRESSIBLE_MIME_TYPES = Arrays.asList(
      "text/html",
      "text/xml",
      "text/plain",
      "text/css",
      "text/javascript",
      "application/javascript",
      "application/json");
   private Set<String> whiteList = Collections.emptySet();
}
