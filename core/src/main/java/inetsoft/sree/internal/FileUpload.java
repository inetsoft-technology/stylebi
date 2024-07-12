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
package inetsoft.sree.internal;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * A class to handle <tt>multipart/form-data</tt> requests,
 * the kind of requests that support file uploads.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class FileUpload {
   /**
    * Contructor.
    */
   public FileUpload(HttpServletRequest request) throws IOException {
      this.req = request;

      if(req == null) {
         throw new IllegalArgumentException("request cannot be null");
      }

      parseRequest();
   }

   /**
    * Get value of parameter(except file form component name).
    * @param name  parameter name.
    */
   public String getParameter(String name) {
      List<String> values = parameters.get(name);

      if(values == null || values.size() == 0) {
         return null;
      }

      return values.get(values.size() - 1);
   }

   /**
    * Get actual uploaded file content type.
    */
   public String getContentType(String name) {
      String contentType = contentTypes.get(name);

      if(contentType.length() <= 0) {
         return null;
      }

      return contentType;
   }

   /**
    * Get the post content length.
    */
   protected int getContentLength() {
      return req.getContentLength();
   }

   /**
    * Get a request header value.
    */
   protected String getHeader(String name) {
      return req.getHeader(name);
   }

   /**
    * Get the post content type.
    */
   protected String getContentType() {
      return req.getContentType();
   }

   /**
    * Get the post content type.
    */
   private String getContentType0() {
      // access header two ways to work around WebSphere oddities
      String type = null;
      String type1 = getHeader("Content-Type");
      String type2 = req.getContentType();

      // If one value is null, choose the other value
      if(type1 == null && type2 != null) {
         type = type2;
      }
      else if(type2 == null && type1 != null) {
         type = type1;
      }
      else if(type1 != null) {
         type = (type1.length() > type2.length() ? type1 : type2);
      }

      return type;
   }

   /**
    * Get the post content stream.
    */
   protected ServletInputStream getInputStream() throws IOException {
      return req.getInputStream();
   }
   
   /**
    * Get max size.
    */
   protected int getMaxSize() {
      return DEFAULT_MAX_POST_SIZE;
   }

   /**
    * The workhorse method that actually parses the request. A subclass
    * can override this method for a better optimized or differently
    * behaved implementation.
    *
    * @exception IOException if the uploaded content is larger than
    * max size or there's a problem parsing the request.
    */
   protected void parseRequest() throws IOException {
      // Check the content length to prevent denial of service attacks
      int length = getContentLength();

      if(length > getMaxSize()) {
         throw new IOException("Posted content length of " + length +
                               " exceeds limit of " + getMaxSize());
      }

      // check the content type to make sure it's "multipart/form-data"
      String type = getContentType0();

      if(type == null || !type.toLowerCase().startsWith("multipart/form-data"))
      {
         throw new IOException("Error, posted content type " +
                               "isn't multipart/form-data: " + type);
      }

      // get the boundary string; it's included in the content type
      // look like "------------------------12012133613061"
      String boundary = parseBoundary(type);

      if(boundary == null) {
         throw new IOException("Separation boundary was not specified: " +
                               type);
      }

      InputStreamHandler in = new InputStreamHandler(getInputStream(), length);
      // read the first line, should be the first boundary
      String line = in.readLine();

      if(line == null) {
         throw new IOException("Form data: premature ending.");
      }

      // verify that the line is the boundary
      if(!line.startsWith(boundary)) {
         throw new IOException("Form data: no leading boundary.");
      }

      // now that we're just beyond the first boundary, loop over each part
      boolean done = false;

      while(!done) {
         done = parseNextPart(in, boundary);
      }
   }

   /**
    * A utility method that reads an individual part.
    *
    * @param in the stream from which to read the part.
    * @param boundary the boundary separating parts.
    * @return a flag indicating whether this is the last part.
    * @exception IOException if there's a problem reading or parsing the
    * request.
    */
   protected boolean parseNextPart(InputStreamHandler in,
                                   String boundary) throws IOException {
      // read the first line, should look like this:
      // content-disposition: form-data; name="field"; filename="file.txt"
      String line = in.readLine();

      if(line == null) {
         // no parts left, we're done
         return true;
      }
      else if(line.length() == 0) {
         // IE4 on Mac sends an empty line at the end; treat that as the end
         // thanks to Daniel Lemire and Henri Tourigny for this fix
         return true;
      }

      // parse the content-disposition line
      String[] dispInfo = parseDispositionInfo(line);
      String name = dispInfo[1];
      // now onto the next line, this will either be empty
      String filename = dispInfo[2];

      // or contain a Content-Type and then an empty line
      line = in.readLine();

      if(line == null) {
         // no parts left, we're done
         return true;
      }

      // get the content type, or null if none specified
      String contentType = parseContentType(line);

      if(contentType != null) {
         // eat the empty line
         line = in.readLine();

         if(line == null || line.length() > 0) { // line should be empty
            throw new IOException("Malformed line after content type: " + line);
         }
      }
      else { // assume a default content type
         contentType = "application/octet-stream";
      }

      // now, finally, we read the content (end after reading the boundary)
      if(filename == null) {
         // this is a parameter, add it to the vector of values
         String value = parseParameter(in, boundary);

         if(value.equals("")) {
            value = null; // treat empty strings like nulls
         }

         List<String> existingValues = parameters.computeIfAbsent(name, k -> new ArrayList<>());
         existingValues.add(value);
      }
      else { // this is a file
         cacheFileData(name, in, boundary, name, contentType);

         if(filename.equals(NO_FILE)) {
            fileSystemNames.put(name, "");
            contentTypes.put(name, "");
         }
         else {
            fileSystemNames.put(name, filename);
            contentTypes.put(name, contentType);
         }
      }

      return false; // there's more to read
   }

   /**
    * A utility method that reads a single part of the multipart request
    * that represents a file, and writes it to a byte array.
    *
    * @param in the stream from which to read the file.
    * @param boundary the boundary signifying the end of this part.
    * @param filename the file parameter name.
    * @exception IOException if there's a problem reading or parsing the
    * request.
    */
   protected void cacheFileData(String name, InputStreamHandler in, 
      String boundary, String filename, String contentType) throws IOException
   {
      ByteArrayOutputStream out = new ByteArrayOutputStream(); // write to a byte array
      parseFile(in, boundary, out);
      fileDatas.put(name, out.toByteArray());
   }

   /**
    * A utility method that reads a single part of the multipart request
    * that represents a file, and writes it to an OutputStream.
    */
   protected void parseFile(InputStreamHandler in, String boundary, 
      OutputStream out) throws IOException 
   {
      byte[] bbuf = new byte[100 * 1024]; // 100K
      int result;
      String line;
      // ServletInputStream.readLine() has the annoying habit of adding a
      // \r\n to the end of the last line
      // since we want a byte-for-byte transfer, we have to cut those chars
      boolean rnflag = false;

      while((result = in.readLine(bbuf, 0, bbuf.length)) != -1) {
         // check for boundary
         if(result > 2 && bbuf[0] == '-' && bbuf[1] == '-') {
            line = new String(bbuf, 0, result, StandardCharsets.ISO_8859_1);

            if(line.startsWith(boundary)) {
               break;
            }
         }

         // are we supposed to write \r\n for the last iteration?
         if(rnflag) {
            out.write('\r');
            out.write('\n');
            rnflag = false;
         }

         // write the buffer, postpone any ending \r\n
         if(result >= 2 && bbuf[result - 2] == '\r' && bbuf[result - 1] == '\n')
         {
            out.write(bbuf, 0, result - 2); // skip the last 2 chars
            rnflag = true; // make a note to write them on the next iteration
         }
         else {
            out.write(bbuf, 0, result);
         }
      }
   }

   /**
    * A utility method that reads a single part of the multipart request
    * that represents a parameter.
    *
    * @param in the stream from which to read the parameter information.
    * @param boundary the boundary signifying the end of this part.
    * @return the parameter value.
    * @exception IOException if there's a problem reading or parsing the
    * request.
    */
   protected String parseParameter(InputStreamHandler in,
                                   String boundary) throws IOException {
      StringBuilder sbuf = new StringBuilder();
      String line;

      while((line = in.readLine()) != null) {
         if(line.startsWith(boundary)) {
            break;
         }

         // add the \r\n in case there are many lines
         sbuf.append(line).append("\r\n");
      }

      if(sbuf.length() == 0) {
         return null; // nothing read
      }

      sbuf.setLength(sbuf.length() - 2); // cut off the last line's \r\n
      return sbuf.toString(); // no URL decoding needed
   }

   /**
    * Extracts and returns disposition info from a line, as a String array
    * with elements: disposition, name, filename. Throws an IOException
    * if the line is malformatted.
    */
   private String[] parseDispositionInfo(String line) throws IOException {
      // return the line's data as an array: disposition, name, filename
      String[] retval = new String[3];
      // convert the line to a lowercase string without the ending \r\n
      // keep the original line for error messages and for variable names
      String origline = line;

      line = origline.toLowerCase();

      // get the content disposition, should be "form-data"
      int start = line.indexOf("content-disposition: ");
      int end = line.indexOf(";");

      if(start == -1 || end == -1) {
         throw new IOException("Content disposition corrupt: " + origline);
      }

      String disposition = line.substring(start + 21, end);

      if(!disposition.equals("form-data")) {
         throw new IOException("Invalid content disposition: " + disposition);
      }

      start = line.indexOf("name=\"", end); // start at last semicolon
      end = line.indexOf("\"", start + 7); // skip name=\"

      if(start == -1 || end == -1) {
         throw new IOException("Content disposition corrupt: " + origline);
      }

      String name = origline.substring(start + 6, end);
      // get the filename, if given
      String filename = null;

      start = line.indexOf("filename=\"", end + 2); // start after name
      end = line.indexOf("\"", start + 10); // skip filename=\"

      if(start != -1 && end != -1) { // note the !=
         filename = origline.substring(start + 10, end);
         // the filename may contain a full path, cut to just the filename
         int slash = Math.max(filename.lastIndexOf('/'),
                              filename.lastIndexOf('\\'));

         if(slash > -1) {
            filename = filename.substring(slash + 1); // past last slash
         }

         if(filename.equals("")) {
            filename = NO_FILE;
         }
      }

      retval[0] = disposition;
      retval[1] = name;
      retval[2] = filename;

      return retval;
   }

   // Extracts and returns the content type from a line, or null if the
   // line was empty. Throws an IOException if the line is malformatted.
   private String parseContentType(String line) throws IOException {
      String contentType = null;
      // convert the line to a lowercase string
      String origline = line;

      line = origline.toLowerCase();

      // get the content type, if any
      if(line.startsWith("content-type")) {
         int start = line.indexOf(" ");

         if(start == -1) {
            throw new IOException("Content type corrupt: " + origline);
         }

         contentType = line.substring(start + 1);
      }

      return contentType;
   }

   /**
    * Extracts and returns the boundary token from a line.
    */
   private String parseBoundary(String line) {
      // because IE 4.01 on Win98 has been known to send the
      // "boundary=" string for several times.
      int index = line.lastIndexOf("boundary=");

      if(index == -1) {
         return null;
      }

      String boundary = line.substring(index + 9); // 9 for "boundary="

      // the real boundary is always preceeded by an extra "--"
      boundary = "--" + boundary;

      return boundary;
   }

   /**
    * A class to aid in reading multipart/form-data from a ServletInputStream.
    * It keeps track of how many bytes have been read and detects when the
    * Content-Length limit has been reached. This is necessary since some
    * servlet engines are slow to notice the end of stream.
    */
   class InputStreamHandler {
      ServletInputStream in;
      int totalLength;
      int totalRead = 0;
      byte[] buf = new byte[8 * 1024];

      InputStreamHandler(ServletInputStream in, int totalLength) {
         this.in = in;
         this.totalLength = totalLength;
      }

      /**
       * Reads the next line of input.
       * Returns null to indicate the end of stream.
       */
      public String readLine() throws IOException {
         StringBuilder sbuf = new StringBuilder();
         int result;

         do { // this.readLine() does +=
            result = this.readLine(buf, 0, buf.length);

            if(result != -1) {
               sbuf.append(new String(buf, 0, result, StandardCharsets.UTF_8));
            }
         }
         while(result == buf.length); // loop until the buffer was filled

         if(sbuf.length() == 0) {
            return null; // nothing read, must be at the end of stream
         }

         sbuf.setLength(sbuf.length() - 2); // cut off the trailing \r\n
         return sbuf.toString();
      }

      /**
       * A pass-through to ServletInputStream.readLine() that keeps track
       * of how many bytes have been read and stops reading when the
       * Content-Length limit has been reached.
       */
      public int readLine(byte[] b, int off, int len) throws IOException {
         if(totalRead >= totalLength) {
            return -1;
         }
         else {
            if(len > (totalLength - totalRead)) {
               len = totalLength - totalRead; // keep from reading off end
            }

            int result = in.readLine(b, off, len);

            if(result > 0) {
               totalRead += result;
            }

            return result;
         }
      }
   }

   private static final int DEFAULT_MAX_POST_SIZE = 1024 * 1024 * 100; // 100M
   private static final String NO_FILE = "unknown";
   private HttpServletRequest req;
   // name - vector of values
   private Map<String, List<String>> parameters = new HashMap<>();
   // file name - file system names
   private Map<String, String> fileSystemNames = new HashMap<>();
   // file name - content types
   private Map<String, String> contentTypes = new HashMap<>();
   // file name - file data byte arrays
   private Map<String, byte[]> fileDatas = new HashMap<>();
}
