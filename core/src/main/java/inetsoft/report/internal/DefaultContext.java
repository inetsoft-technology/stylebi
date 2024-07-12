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
package inetsoft.report.internal;

import inetsoft.report.ReportElement;

import java.io.*;

/**
 * Report element context (all attributes affecting the elements).
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class DefaultContext extends BaseElement implements Cacheable {
   public static void write(ObjectOutputStream out, ReportElement cn)
      throws IOException 
   {
      // write custome objects first 
      out.writeObject(cn.getUserObject()); 
      // write standard objects second(Color)
      // BasePaintable.writeColor(out, cn.getForeground());  
      // BasePaintable.writeColor(out, cn.getBackground());
      /*
       * Write out string object third because string is possibly grouped 
       * into ObjectOutputStream 
       * blocks(font is written out as string).
       */
      // this.writeFont(out);  
      out.writeObject(cn.getID());
      out.writeObject(cn.getType());

      /*
       * write primitive types last because ObjectOutputStream group them 
       * into 1024 blocks.
       * The block is last so that any other primitive write happens after 
       * can be grouped also.
       */
      out.writeInt(cn.getAlignment());
      out.writeDouble(cn.getIndent());
      out.writeInt(cn.getSpacing());
      out.writeBoolean(cn.isVisible());
      out.writeBoolean(cn.isKeepWithNext());
   }

   public void read(ObjectInputStream inp)
      throws IOException, ClassNotFoundException 
   {
      setUserObject(inp.readObject());
      // setForeground(BasePaintable.readColor(inp));
      // setBackground(BasePaintable.readColor(inp));
      // setFont(this.readFont(inp));
      setID((String) inp.readObject());
      setType((String) inp.readObject());
      setAlignment(inp.readInt());
      setIndent(inp.readDouble());
      setSpacing(inp.readInt());
      setVisible(inp.readBoolean());
      setKeepWithNext(inp.readBoolean());
   }

   /**
    * Clone a copy of this object.
    */
   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception ex) {
      }

      BaseElement elem = new DefaultContext();

      elem.setContext(this);

      return elem;
   }
   
   /* use BaseElement hashCode and equals that are derived from full name 
    public boolean equals(Object obj) {
    if(obj instanceof DefaultContext) {
    DefaultContext ctx = (DefaultContext) obj;
    
    return (getID() == ctx.getID() || getID() != null && 
    ctx.getID() != null && getID().equals(ctx.getID())) &&
    (getFullName() == ctx.getFullName() || getFullName() != null && 
    ctx.getFullName() != null && 
    getFullName().equals(ctx.getFullName())) &&
    (getType() == ctx.getType() || getType() != null && 
    ctx.getType() != null && getType().equals(ctx.getType())) &&
    getAlignment() == ctx.getAlignment() && 
    getIndent() == ctx.getIndent() && 
    getSpacing() == ctx.getSpacing() &&
    isVisible() == ctx.isVisible() && 
    isKeepWithNext() == ctx.isKeepWithNext() &&
    (getFont() == ctx.getFont() || getFont() != null && 
    ctx.getFont() != null && getFont().equals(ctx.getFont())) &&
    (getForeground() == ctx.getForeground() || getForeground() != null
    && ctx.getForeground() != null &&
    getForeground().equals(ctx.getForeground())) &&
    (getBackground() == ctx.getBackground() || getBackground() != null
    && ctx.getBackground() != null &&
    getBackground().equals(ctx.getBackground())) &&
    (getScript() == ctx.getScript() || getScript() != null && 
    ctx.getScript() != null && getScript().equals(ctx.getScript())) &&
    (getOnClick() == ctx.getOnClick() || 
    getOnClick() != null && ctx.getOnClick() != null &&
    getOnClick().equals(ctx.getOnClick())) &&
    (getUserObject() == ctx.getUserObject() || getUserObject() != null
    && ctx.getUserObject() != null && 
    getUserObject().equals(ctx.getUserObject()));
    }
    
    return false;
    }
    */
}

