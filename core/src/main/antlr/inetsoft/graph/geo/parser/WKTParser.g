header {
package inetsoft.graph.geo.parser;

import java.awt.geom.Point2D;
import java.io.StringReader;
import java.util.ArrayList;

import inetsoft.graph.geo.GeoShape;
}

class WKTParser extends Parser;

options {
  defaultErrorHandler=true;
  k=2;
}

{
  public static GeoShape parse(String wkt, boolean rawBounds) throws ANTLRException {
    WKTLexer lexer = new WKTLexer(new StringReader(wkt));
    WKTParser parser = new WKTParser(lexer);
    parser.rawBounds = rawBounds;
    return parser.geometry();
  }

  public boolean rawBounds = false;
}

geometry
  returns [GeoShape shape = null]
  : ( ( shape = point_tagged_text )
    | ( shape = linestring_tagged_text )
    | ( shape = polygon_tagged_text )
    | ( shape = multipoint_tagged_text )
    | ( shape = multilinestring_tagged_text )
    | ( shape = multipolygon_tagged_text )
    )
  ;

multipolygon_tagged_text
  returns [GeoMultiPolygon mp]
  {
    mp = new GeoMultiPolygon();
  }
  : MULTIPOLYGON multipolygon_text[mp]
  ;

multipolygon_text
  [GeoMultiPolygon mp]
  {
    GeoPolygon poly = null;
  }
  : ( EMPTY
    | ( LPAREN
        ( poly = polygon_text { mp.addPolygon(poly, rawBounds); } )
        ( COMMA ( poly = polygon_text { mp.addPolygon(poly, rawBounds); } ) )*
        RPAREN
      )
    )
  ;

polygon_tagged_text
  returns [GeoPolygon poly=null]
  : POLYGON ( poly = polygon_text )
  ;

polygon_text
  returns [GeoPolygon poly]
  {
    poly = new GeoPolygon();
    ArrayList<Point2D> ring = null;
  }
  : ( LPAREN
      ( ring = linestring_text { poly.setShell(ring); } )
      ( COMMA ( ring = linestring_text { poly.addHole(ring); } ) )*
      RPAREN
    )
  ;


multilinestring_tagged_text
  returns [GeoPolyline line]
  {
    line = new GeoPolyline();
  }
  : MULTILINESTRING multilinestring_text[line]
  ;

multilinestring_text
  [GeoPolyline line]
  {
    ArrayList<Point2D> segment = null;
  }
  : ( EMPTY
    | ( LPAREN
        ( segment = linestring_text { line.addSegment(segment); } )
        ( COMMA ( segment = linestring_text { line.addSegment(segment); } ) )*
        RPAREN
       )
    )
  ;

linestring_tagged_text
  returns [GeoPolyline line]
  {
    line = new GeoPolyline();
    ArrayList<Point2D> segment = null;
  }
  : LINESTRING ( segment = linestring_text )
  {
    line.addSegment(segment);
  }
  ;

linestring_text
  returns [ArrayList<Point2D> pts]
  {
    pts = new ArrayList<>();
    Point2D coord;
  }
  : ( EMPTY
    | ( LPAREN
        ( coord = point() { pts.add(coord); } )
        ( COMMA ( coord = point() { pts.add(coord); } ) )*
        RPAREN
      )
    )
  ;

multipoint_tagged_text
  returns [GeoMultiPoint mp]
  {
    mp = new GeoMultiPoint();
  }
  : MULTIPOINT multipoint_text[mp]
  ;

multipoint_text
  [GeoMultiPoint mp]
  {
    Point2D coord = null;
  }
  : ( EMPTY
    | ( LPAREN
        ( coord = point { mp.addPoint(coord); } )
        ( COMMA ( coord = point { mp.addPoint(coord); } ) )*
        RPAREN
      )
    )
  ;

point_tagged_text
  returns [GeoPoint pt]
  {
    pt = new GeoPoint();
    Point2D coord = null;
  }
  : POINT ( coord = point_text )
  {
    pt.setPoint(coord);
  }
  ;

point_text
  returns [Point2D pt=null]
  : ( EMPTY
    | ( LPAREN (pt = point) RPAREN )
    )
  ;

point
  returns [Point2D pt=null]
  : a:NUMBER b:NUMBER
  {
    pt = new Point2D.Double(Double.valueOf(a.getText()), Double.valueOf(b.getText()));
  }
  ;

class WKTLexer extends Lexer;

options {
  charVocabulary='\0'..'\377';
  testLiterals=false;
  k=2;
}

tokens {
  POINT              = "POINT"              ;
  LINESTRING         = "LINESTRING"         ;
  POLYGON            = "POLYGON"            ;
  MULTIPOINT         = "MULTIPOINT"         ;
  MULTILINESTRING    = "MULTILINESTRING"    ;
  MULTIPOLYGON       = "MULTIPOLYGON"       ;
  GEOMETRYCOLLECTION = "GEOMETRYCOLLECTION" ;
  EMPTY              = "EMPTY"              ;
}

WS
  : ( ' '
    | '\t'
    | '\f'
    | ( "\r\n"
      | '\r'
      | '\n'
      )
      { newline(); }
    )
    { $setType(Token.SKIP); }
  ;

LPAREN : '(' ;
RPAREN : ')' ;
COMMA  : ',' ;

IDENT
  options { testLiterals=true; }
  : ('A'..'Z')+
  ;

protected PLUS   : '+'      ;
protected MINUS  : '-'      ;
protected DIGIT  : '0'..'9' ;
protected DOT    : '.'      ;

protected SIGN
  : ( (PLUS)
    | (MINUS)
    )
  ;

protected UNSIGNEDINT : (DIGIT)+            ;
protected SIGNEDINT   : (SIGN)? UNSIGNEDINT ;

protected UNSIGNEDNUM
  : ( ( (UNSIGNEDINT (DOT (UNSIGNEDINT)?)?)
      | (DOT UNSIGNEDINT)
      )
      (( 'E' SIGNEDINT )?)
    )
  ;

NUMBER : (SIGN)? UNSIGNEDNUM ;
