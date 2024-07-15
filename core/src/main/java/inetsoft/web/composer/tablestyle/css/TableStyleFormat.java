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
package inetsoft.web.composer.tablestyle.css;

public class TableStyleFormat {
    public TableStyleFormat() {
    }

    public TableStyleFormat(String hAlign, String vAlign, String background, String foreground,
                            String font, String bottomBorder, String rightBorder)
    {
        this.hAlign = hAlign;
        this.vAlign = vAlign;
        this.background = background;
        this.foreground = foreground;
        this.font = font;
        this.bottomBorder = bottomBorder;
        this.rightBorder = rightBorder;
    }

    public String gethAlign() {
        return hAlign;
    }

    public void sethAlign(String hAlign) {
        this.hAlign = hAlign;
    }

    public String getvAlign() {
        return vAlign;
    }

    public void setvAlign(String vAlign) {
        this.vAlign = vAlign;
    }

    public String getBackground() {
        return background;
    }

    public void setBackground(String background) {
        this.background = background;
    }

    public String getForeground() {
        return foreground;
    }

    public void setForeground(String foreground) {
        this.foreground = foreground;
    }

    public String getFont() {
        return font;
    }

    public void setFont(String font) {
        this.font = font;
    }

    public String getBottomBorder() {
        return bottomBorder;
    }

    public void setBottomBorder(String rowBorder) {
        this.bottomBorder = rowBorder;
    }

    public String getRightBorder() {
        return rightBorder;
    }

    public void setRightBorder(String rightBorder) {
        this.rightBorder = rightBorder;
    }

    public String getTopBorder() {
        return topBorder;
    }

    public void setTopBorder(String topBorder) {
        this.topBorder = topBorder;
    }

    public String getLeftBorder() {
        return leftBorder;
    }

    public void setLeftBorder(String leftBorder) {
        this.leftBorder = leftBorder;
    }

    public String getFontUnderline() {
        return fontUnderline;
    }

    public void setFontUnderline(String fontUnderline) {
        this.fontUnderline = fontUnderline;
    }

    public String getFontStrikethrough() {
        return fontStrikethrough;
    }

    public void setFontStrikethrough(String fontStrikethrough) {
        this.fontStrikethrough = fontStrikethrough;
    }

    private String hAlign;
    private String vAlign;
    private String background;
    private String foreground;
    private String font;
    private String bottomBorder;
    private String rightBorder;
    private String topBorder;
    private String leftBorder;
    private String fontUnderline;
    private String fontStrikethrough;
}
