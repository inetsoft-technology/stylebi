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
package inetsoft.util;

import de.idyl.winzipaes.impl.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Date;
import java.util.zip.*;

/**
 * Create a AES zip file encrypter.
 *
 * @author InetSoft Technology Corp
 */
public class ExtAesZipFileEncrypter {
   /**
    * Constructor for encrypter.
    * @param zipName the zip file name.
    * @param password the password for encrypt the zip file.
    */
   public ExtAesZipFileEncrypter(String zipName, String password)
      throws IOException
   {
      this.zipOS = new ExtZipOutputStream(FileSystemService.getInstance().getFile(zipName));
      this.encrypter = new AESEncrypterJCA();

      verifyPassword(password);
      this.password = password;
   }

   /**
    * Constructor for encrypter.
    * @param out the zip file output stream.
    * @param password the password for encrypt the zip file.
    */
   public ExtAesZipFileEncrypter(OutputStream out, String password)
      throws IOException
   {
      this.zipOS = new ExtZipOutputStream(out);
      this.encrypter = new AESEncrypterJCA();

      verifyPassword(password);
      this.password = password;
   }

   /**
    * Add un-encrypted and un-zipped file to encrypted zip file.
    * @param file to be added.
    * @param type use full path as zip entry if false.
    */
   public void add(String fileName, boolean type) {
      File file = FileSystemService.getInstance().getFile(Tool.convertUserFileName(fileName));

      if(!file.exists()) {
         return;
      }

      try {
         String name = type ? file.getName() : file.getPath();
         add(name, new FileInputStream(file));
      }
      catch(IOException ex) {
      }
   }

   /**
    * Add un-encrypted and un-zipped files to encrypted zip file.
    * @param files to be added.
    * @param type use full path as zip entry if false.
    */
   public void add(String[] files, boolean type) {
      if(files == null || files.length == 0) {
         return;
      }

      for(String file : files) {
         add(file, type);
      }
   }

    /**
    * Add un-encrypted and un-zipped zipEntry to encrypted zip file.
    * @param files to be added.
    * @param in the file input stream need to be added.
    */
   public void add(ZipEntry zipEntry, InputStream in) {
      final Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);

      try {
         initEncrypter();

         ByteArrayOutputStream baos = new ByteArrayOutputStream();
         DeflaterOutputStream dos =
            new DeflaterOutputStream(baos, deflater, 8 * 1024);

         int read = 0;
         long length = 0;
         byte[] buf = new byte[8 * 1024];

         while((read = in.read(buf)) > 0) {
            length += read;
            dos.write(buf, 0, read);
         }

         dos.close();
         byte[] data = baos.toByteArray();

         ExtZipEntry entry = new ExtZipEntry(zipEntry.getName());
         entry.setMethod(getEntryMethod(zipEntry));
         entry.setSize(length);
         entry.setCompressedSize(data.length + 28);
         entry.setTime(getEntryTime(zipEntry));
         entry.initEncryptedEntry();
         zipOS.putNextEntry(entry);
         zipOS.writeBytes(encrypter.getSalt());
         zipOS.writeBytes(encrypter.getPwVerification());
         encrypter.encrypt(data, data.length);

         zipOS.writeBytes(data, 0, data.length);
         zipOS.writeBytes(encrypter.getFinalAuthentication());
      }
      catch(IOException ex) {
         LOG.error(ex.getMessage(), ex);
      }
      finally {
         deflater.end();
      }
   }

   /**
    * Close zip output stream.
    */
   public void close() throws IOException {
      zipOS.finish();
   }

   /**
    * Set zip entry comment.
    */
   public void setComment(String comment) {
      zipOS.setComment(comment);
   }

   /**
    * Add un-encrypted + un-zipped InputStream contents as file "name" to
    * encrypted zip file.
    *
    * @param name of the new zipEntry within the zip file
    * @param is provides the data to be added. It is left open and should be
    * closed by the caller
    */
   private void add(String name, InputStream is) throws IOException {
      final Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);

      try {
         initEncrypter();

         ByteArrayOutputStream bos = new ByteArrayOutputStream();
         DeflaterOutputStream dos =
            new DeflaterOutputStream(bos, deflater, 8 * 1024);
         int read=0;
         long inputLen = 0;
         byte[] buf = new byte[8 * 1024];

         while((read = is.read(buf)) > 0) {
            inputLen += read;
            dos.write(buf, 0, read);
         }

         dos.close();
         byte[] data = bos.toByteArray();

         ExtZipEntry entry = new ExtZipEntry(name);
         entry.setMethod(ZipEntry.DEFLATED);
         entry.setSize(inputLen);
         entry.setCompressedSize(data.length + 28);
         entry.setTime((new Date()).getTime());
         entry.initEncryptedEntry();
         zipOS.putNextEntry(entry);
         zipOS.writeBytes(encrypter.getSalt());
         zipOS.writeBytes(encrypter.getPwVerification());
         encrypter.encrypt(data, data.length);
         zipOS.writeBytes(data, 0, data.length);

         byte[] finalAuthentication = encrypter.getFinalAuthentication();
         zipOS.writeBytes(finalAuthentication);
      }
      finally {
         deflater.end();
      }
   }

   /**
    * Init aes encrtypter.
    */
   private void initEncrypter() {
      try {
         encrypter.init(password, 256);
      }
      catch(Exception exception) {
         try {
            encrypter.init(password, 192);
         }
         catch(Exception ex) {
            try {
               encrypter.init(password, 128);
            }
            catch(Exception e) {
            }
         }
      }
   }

   /**
    * Verify password, it's not allowed password null or empty.
    */
   private void verifyPassword(String password) {
      if(password == null || password.length() == 0) {
         throw new RuntimeException("Password should not be empty");
      }
   }

   /**
    * Get zip entry method, if entry method is not defined, return DEFLATED by
    * default.
    */
   private int getEntryMethod(ZipEntry entry) {
      int method = entry.getMethod();

      return method == -1 ? ZipEntry.DEFLATED : method;
   }

   /**
    * Get zip entry time, if entry time is not defined, return current time by
    * default.
    */
   private long getEntryTime(ZipEntry entry) {
      long time = entry.getTime();

      return time == -1 ? new Date().getTime() : time;
   }

   private String password = null;
   private ExtZipOutputStream zipOS;
   private AESEncrypter encrypter;
   private static final Logger LOG = LoggerFactory.getLogger(ExtAesZipFileEncrypter.class);
}