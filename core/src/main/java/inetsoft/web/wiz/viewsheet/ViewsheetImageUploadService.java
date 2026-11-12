/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.wiz.viewsheet;

import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.ImageVSAssemblyInfo;
import inetsoft.web.viewsheet.service.VSObjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The Composer's Image assembly &gt; Static Image &gt; Upload for the wiz agent: adds an image to
 * the paired dashboard's uploaded images, which every Image assembly of the dashboard can then
 * select as {@code ^UPLOADED^<name>}.
 *
 * <p>The Composer dialog ({@code ImagePreviewPaneService.uploadImage}) rides an upload endpoint
 * keyed by the Composer's runtime id, so an agent holding a session token cannot reach it. This
 * class does the same two steps -- the image check and {@link Viewsheet#addUploadedImage} -- inside
 * {@link ViewsheetSessionService#mutate}, so the upload is one undo step and the human's Composer
 * shows the new image in the Image dialog's tree.
 *
 * <p>Every refusal is decided before the mutation: {@code mutate} spends an undo step and tells
 * the browser even when its mutation fails, and a refused upload must cost neither. The image
 * name is stored verbatim in the saved dashboard and in the preview URL, so names that would break
 * either are refused rather than rewritten.
 */
@Service
public class ViewsheetImageUploadService {
   /**
    * Largest image accepted. The dashboard stores uploaded images as hex text, so this is twice
    * as much in the saved file and in memory while saving.
    */
   public static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;

   /** Longest image name accepted. */
   public static final int MAX_NAME_LENGTH = 128;

   private static final String ACCEPTED = "PNG, JPEG, GIF, TIFF or SVG";

   @Autowired
   public ViewsheetImageUploadService(ViewsheetSessionService sessions,
                                      VSObjectService vsObjectService)
   {
      this.sessions = sessions;
      this.vsObjectService = vsObjectService;
   }

   /**
    * @param content  the image's bytes
    * @param fileName the name to store the image under; replaces an earlier upload of the same
    *                 name on this dashboard
    *
    * @return {@code ok}, {@code name}, {@code selectedImage} (the value to give an Image
    *         assembly), {@code replaced} and {@code size}
    */
   public Map<String, Object> upload(String sessionToken, Principal user, byte[] content,
                                     String fileName)
      throws Exception
   {
      checkName(fileName);
      String label = "'" + fileName + "'";

      if(content == null || content.length == 0) {
         throw new IllegalArgumentException("The image file " + label + " is empty.");
      }

      if(content.length > MAX_IMAGE_BYTES) {
         throw new IllegalArgumentException(
            "The image file " + label + " is " + content.length + " bytes; the limit is " +
            MAX_IMAGE_BYTES + " bytes (" + (MAX_IMAGE_BYTES / (1024 * 1024)) + " MB). " +
            "Nothing was uploaded.");
      }

      if(!isImage(content)) {
         throw new IllegalArgumentException(
            "The file " + label + " is not an image the dashboard can show (" + ACCEPTED +
            "). Nothing was uploaded.");
      }

      // resolve first so an unknown or foreign session token is refused with no undo step spent
      sessions.resolve(sessionToken, user);
      boolean[] replaced = new boolean[1];

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         Viewsheet vs = rvs.getViewsheet();
         replaced[0] = vs.getUploadedImageBytes(fileName) != null;
         vs.addUploadedImage(fileName, content);
      });

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("ok", true);
      result.put("name", fileName);
      result.put("selectedImage", ImageVSAssemblyInfo.UPLOADED_IMAGE + fileName);
      result.put("replaced", replaced[0]);
      result.put("size", content.length);
      return result;
   }

   /**
    * Whether the bytes are an image. A file the SVG reader chokes on is not an image either; it
    * must not surface as a server error.
    */
   private boolean isImage(byte[] content) {
      try {
         return vsObjectService.isImage(content);
      }
      catch(Exception e) {
         return false;
      }
   }

   private static void checkName(String name) {
      if(name == null || name.isBlank()) {
         throw new IllegalArgumentException("The image needs a file name, e.g. 'logo.png'.");
      }

      if(name.length() > MAX_NAME_LENGTH) {
         throw new IllegalArgumentException(
            "The image name is " + name.length() + " characters; the limit is " +
            MAX_NAME_LENGTH + ".");
      }

      for(int i = 0; i < name.length(); i++) {
         if(Character.isISOControl(name.charAt(i))) {
            throw new IllegalArgumentException(
               "The image name must not contain control characters.");
         }
      }

      if(name.startsWith("^")) {
         throw new IllegalArgumentException(
            "The image name '" + name + "' must not start with '^'; that marks the built-in " +
            "and uploaded image values.");
      }

      if(name.contains("]]>")) {
         throw new IllegalArgumentException(
            "The image name '" + name + "' must not contain ']]>'.");
      }

      for(char c : FORBIDDEN) {
         if(name.indexOf(c) >= 0) {
            throw new IllegalArgumentException(
               "The image name '" + name + "' must not contain '" + c + "'. Use a plain file " +
               "name such as 'logo.png'.");
         }
      }
   }

   private static final char[] FORBIDDEN = {'/', '\\', '?', '#', '%'};

   private final ViewsheetSessionService sessions;
   private final VSObjectService vsObjectService;
}
