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
package inetsoft.report;

/**
 * A SectionLens represents a section element. A section can have a header
 * band, footer band, and a content band. The content band is required, and
 * the other two bands are optional. The content band can contain another
 * section, which is called a nested section.
 * <p>
 * A section can be used to display the contents of a TableLens, in which
 * case the section header corresponds to the table header, and is repeated
 * at the top of each page. The section content corresponds to rows in the
 * table, and is repeated for each row. The section footer corresponds to
 * the group summary if the table is a grouped table.
 * <p>
 * A section can also be used to present fixed position elements, where
 * the contents of the elements are statis or bound at runtime individually
 * to each element. The header and footer normally don't apply in this
 * situation.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public interface SectionLens extends java.io.Serializable, Cloneable {
   /**
    * Section header type.
    */
   public static final String HEADER = "Header";
   /**
    * Section content type.
    */
   public static final String CONTENT = "Content";
   /**
    * Section footer type.
    */
   public static final String FOOTER = "Footer";

   /**
    * Get the section header frame. If the section does not have a
    * header frame, return null. A section header can be a single
    * section band, or an array or section band.
    * @return array of header bands.
    */
   public SectionBand[] getSectionHeader();

   /**
    * Get the content of the section content. The content could be an
    * array of SectionBand or a SectionLens. If a SectionLens is returned,
    * the returned section is nested in this section.
    * @return SectionLens if there is nested section and this is not
    * the inner most section, or array of SectionBand if
    * this is the inner most section.
    */
   public SectionBand[] getSectionContent();

   /**
    * Get the section footer frame. If the section does not have a
    * footer frame, return null. A section footer can be a single
    * section band, or an array or section band.
    * @return array of footer bands.
    */
   public SectionBand[] getSectionFooter();

   /**
    * Clone this section.
    */
   public Object clone();

   /**
    * Visit all bands in the section.
    */
   public void visit(Visitor visitor);

   /**
    * Visitor to all bands in the section.
    */
   public static interface Visitor {
      /**
       * This method is called on every band in the section.
       * @param band visited band.
       * @param type band type.
       */
      public void visit(SectionBand band, Object type);
   }
}

