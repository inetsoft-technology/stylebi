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
package inetsoft.uql.xmla;

import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import org.xml.sax.SAXException;

import java.util.List;

/**
 * DiscoverHandler is responsible for XMLA discover method.
 *
 * @version 10.3, 3/30/2010
 * @author InetSoft Technology Corp
 */
class DiscoverHandler extends SAXHandler {
   /**
    * Start element.
    * @throws SAXException
    */
   @Override
   public void startElement() throws SAXException {
      super.startElement();

      if("row".equals(lastTag)) {
         objList.add(createObject());
      }
   }

   /**
    * End element.
    */
   @Override
   public void endElement() {
      super.endElement();

      if("row".equals(lastTag)) {
         if(invalidFound) {
            objList.remove(objList.size() - 1);
            invalidFound = false;
         }
      }
   }

   /**
    * Parse contents.
    */
   @Override
   public void characters() {
      String txt = getText();

      if(txt == null || txt.trim().length() <= 0 || txt.equals("\n")) {
         return;
      }

      if(reqType == null || objList.size() == 0) {
         return;
      }
      else {
         Object obj = objList.get(objList.size() - 1);

         if(obj == null) {
            return;
         }

         if(reqType.equals(XMLAUtil.CATALOGS_REQUEST)) {
            if("CATALOG_NAME".equals(lastTag)) {
               objList.set(objList.size() - 1, txt);
            }
         }
         else if(reqType.equals(XMLAUtil.CUBES_REQUEST)) {
            Cube cube = (Cube) obj;

            if("CUBE_NAME".equals(lastTag)) {
               cube.setName(txt);
               cube.setType(getCubeType());
            }

            if("CUBE_CAPTION".equals(lastTag)) {
               cube.setCaption(txt);
            }

            if(getCubeType().equals(Cube.SAP) &&
               "DESCRIPTION".equals(lastTag))
            {
               cube.setCaption(txt);
            }
         }
         else if(reqType.equals(XMLAUtil.DIMENSIONS_REQUEST)) {
            Dimension dim = (Dimension) obj;

            if("DIMENSION_NAME".equals(lastTag)) {
               dim.setDimensionName(txt);
            }

            if("DIMENSION_UNIQUE_NAME".equals(lastTag)) {
               if("[Measures]".equals(txt)) {
                  invalidFound = true;
               }

               dim.setUniqueName(txt);
            }

            if("DIMENSION_CAPTION".equals(lastTag)) {
               dim.setCaption(txt);
            }

            if("DIMENSION_TYPE".equals(lastTag)) {
               dim.setType(getDimensionType(Integer.parseInt(txt)));
            }
         }
         else if(reqType.equals(XMLAUtil.MEASURES_REQUEST)) {
            if("MEASURE_IS_VISIBLE".equals(lastTag) && "false".equals(txt)) {
               invalidFound = true;
            }

            Measure measure = (Measure) obj;

            if("MEASURE_NAME".equals(lastTag)) {
               if("Measures".equals(txt)) {
                  invalidFound = true;
               }

               measure.setName(txt);
            }

            if("DATA_TYPE".equals(lastTag)) {
               // for sap data type
               if("FLTP".equals(txt)) {
                  txt = "130";
               }
               else if("CHAR".equals(txt) ||
                  "DATS".equals(txt) || "TIMS".equals(txt))
               {
                  txt = "1";
               }
               else if("INT4".equals(txt)) {
                  txt = "20";
               }

               measure.setType(getType(Integer.parseInt(txt)));
            }

            if("MEASUREGROUP_NAME".equals(lastTag)) {
               measure.setFolder(txt);
            }

            if("MEASURE_UNIQUE_NAME".equals(lastTag)) {
               measure.setUniqueName(txt);
            }

            if("MEASURE_CAPTION".equals(lastTag)) {
               measure.setCaption(txt);
            }
         }
         else if(reqType.equals(XMLAUtil.HIERARCHIES_REQUEST)) {
            String[] hierarchy = (String[]) obj;

            if("HIERARCHY_ORIGIN".equals(lastTag)) {
               hierarchy[0] = txt;
            }

            if("HIERARCHY_NAME".equals(lastTag)) {
               hierarchy[1] = txt;
            }

            if("HIERARCHY_UNIQUE_NAME".equals(lastTag)) {
               hierarchy[2] = txt;
            }

            if("HIERARCHY_CAPTION".equals(lastTag)) {
               hierarchy[3] = txt;
            }
         }
         else if(reqType.equals(XMLAUtil.LEVELS_REQUEST)) {
            DimMember dmember = (DimMember) obj;

            if("LEVEL_NAME".equals(lastTag)) {
               String name = txt;
               dmember.setName(txt);
            }

            if("LEVEL_CAPTION".equals(lastTag)) {
               dmember.setCaption(txt);
            }

            if("LEVEL_UNIQUE_NAME".equals(lastTag)) {
               String name = txt;
               dmember.setUniqueName(txt);
            }

            if("LEVEL_NUMBER".equals(lastTag)) {
               int level = Integer.parseInt(txt);
               dmember.setNumber(level);
            }
         }
         else if(reqType.equals(XMLAUtil.MDSCHEMA_MEMBERS)) {
            MemberObject mobj = (MemberObject) obj;

            if("MEMBER_UNIQUE_NAME".equals(lastTag)) {
               mobj.uName = txt;
            }

            if("MEMBER_CAPTION".equals(lastTag)) {
               mobj.caption = txt;
            }

            if("MEMBER_ALIAS".equals(lastTag)) {
               mobj.caption = txt;
            }

            if("PARENT_UNIQUE_NAME".equals(lastTag)) {
               mobj.parent = txt;
            }

            if("PARENT_LEVEL".equals(lastTag)) {
               mobj.plNum = Integer.parseInt(txt);
            }

            if("HIERARCHY_UNIQUE_NAME".equals(lastTag)) {
               mobj.hierarchy = txt;
            }

            if("LEVEL_UNIQUE_NAME".equals(lastTag)) {
               mobj.lName = txt;
            }

            if("LEVEL_NUMBER".equals(lastTag)) {
               mobj.lNum = Integer.parseInt(txt);
            }
         }
      }
   }

   /**
    * Create empty object by request type.
    */
   private Object createObject() {
      if(reqType == null) {
         return null;
      }
      else if(reqType.equals(XMLAUtil.CATALOGS_REQUEST)) {
         return "";
      }
      else if(reqType.equals(XMLAUtil.CUBES_REQUEST)) {
         return new Cube();
      }
      else if(reqType.equals(XMLAUtil.DIMENSIONS_REQUEST)) {
         return new Dimension();
      }
      else if(reqType.equals(XMLAUtil.MEASURES_REQUEST)) {
         return new Measure();
      }
      else if(reqType.equals(XMLAUtil.HIERARCHIES_REQUEST)) {
         return new String[4];
      }
      else if(reqType.equals(XMLAUtil.LEVELS_REQUEST)) {
         return new DimMember();
      }
      else if(reqType.equals(XMLAUtil.MDSCHEMA_MEMBERS)) {
         return new MemberObject();
      }

      return null;
   }

   /**
    * Get the data type for dimension.
    *
    * @param type the data type code retrieve from XMLA.
    * @return the data type defined in XSchema.
    */
   private int getDimensionType(int type) {
      // the dimension type will find in official document
      switch(type) {
      case 1:
         return DataRef.CUBE_TIME_DIMENSION;
      case 2:
         return DataRef.CUBE_MEASURE;
      default:
         return DataRef.CUBE_DIMENSION;
      }
   }

   /**
    * Retrieve the appropiate data type from the index code
    * passed back via XMLA.
    *
    * @param type data type index.
    * @return string constant representing type from XSchema.
    */
   private String getType(int type) {
      // the measure type will find in old classes, as SQLServerOlapConnector
      switch(type) {
      case 3:
      case 20:
         return XSchema.INTEGER;
      case 5:
      case 6:
      case 12:
      // for calculated measures of mondrian
      case 130:
         return XSchema.DOUBLE;
      default:
         return XSchema.STRING;
      }
   }

   String reqType;
   List objList;
   boolean invalidFound;
}