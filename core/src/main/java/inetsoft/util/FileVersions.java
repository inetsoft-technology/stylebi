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
package inetsoft.util;

/**
 * This class managers the versions of all xml files.
 * The version of file may be different from the version of the product.
 * eg. for product 4.4, 4.5, 4.6, the template file version may all be 4.4,
 * so they use the same parser.
 *
 * The members HIS_* are the history versions of the file. They can be used to
 * map to different parsers.
 */
public class FileVersions {
   // Template and report file (*.srt) version
   public static final String REPORT = "13.7";
   // Version of asset
   public static final String ASSET = "14.0";
   // Version of datasource.xml
   public static final String DATASOURCE = "13.7";
   // Version of dashboard-registry.xml
   public static final String DASHBOARD_REGISTRY = "9.5";
   // Version of query.xml
   public static final String QUERY = "4.4";
   // Version of repository.xml
   public static final String REPOSITORY = "6.5";
   // Version of cycle.xml
   public static final String CYCLE = "9.0";
   // Version of portalthemes.xml
   public static final String PORTAL_THEMES = "13.1";
   // Version of customthemes.xml
   public static final String CUSTOM_THEMES = "14.0";
   // Version of portalskin.xml
   public static final String DOMAIN = "10.3";
   // materializedviews.xml
   public static final String MV = "11.1";
   //Version of JarFileInfo.xml in exported partial deployment jar file
   public static final String JAR_VERSION = "13.1";
   public static final String[] HIS_JAR_VERSIONS = {"12.0", "12.1", "12.2", "13.0"};
}
