package se.alipsa.renjin.client.datautils;

import org.renjin.primitives.Types;
import org.renjin.primitives.vector.RowNamesVector;
import org.renjin.sexp.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RDataTransformer {

  public static List<String> toHeaderList(ListVector df) {
    List<String> colList = new ArrayList<>();
    if (df.hasAttributes()) {
      AttributeMap attributes = df.getAttributes();
      Map<Symbol, SEXP> attrMap = attributes.toMap();
      Symbol s = attrMap.keySet().stream().filter(p -> "names".equals(p.getPrintName())).findAny().orElse(null);
      Vector colNames = (Vector) attrMap.get(s);
      for (int i = 0; i < colNames.length(); i++) {
        colList.add(colNames.getElementAsString(i));
      }
    }
    return colList;
  }

  public static List<List<Object>> toRowlist(ListVector df) {
    List<Vector> table = new ArrayList<>();
    for (SEXP col : df) {
      Vector column = (Vector) col;
      table.add(column);
    }
    return transpose(table);
  }

  // TODO: this needs some work, col.getElementAsObject(i) returns Integer.MIN_VALUE instead of null for numerics
  public static List<List<Object>> transpose(List<Vector> table) {
    List<List<Object>> ret = new ArrayList<>();
    final int N = table.get(0).length();
    //System.out.println("Transposing a table with " + N + " columns into " + N + " rows");
    for (int i = 0; i < N; i++) {
      List<Object> row = new ArrayList<>();
      for (Vector col : table) {
        if (Types.isFactor(col)) {
          int index = col.getElementAsInt(i) - 1;
          if (index < 0 || index > col.length() -1) {
            /*
            System.err.println("Failed to extract value from factor element, index " + index + " is bigger than vector size " + vec.length());
            System.err.println("Factor vector is " + vec.toString());
            System.err.println("Factor vector names are " + vec.getNames());
            System.err.println("col.getElementAsObject(i) = " + col.getElementAsObject(i));
            System.err.println("col " + (row.size() + 1) + " row " + (i + 1) + " interpreted as null");
             */
            row.add(null);
          } else {
            AttributeMap attributes = col.getAttributes();
            Map<Symbol, SEXP> attrMap = attributes.toMap();
            Symbol s = attrMap.keySet().stream().filter(p -> "levels".equals(p.getPrintName())).findAny().orElse(null);
            Vector vec = (Vector) attrMap.get(s);
            row.add(vec.getElementAsObject(index));
            //row.add(vec.getElementAsString(index)); // TODO: try this is instead, makes more sense as we know this is a string
          }
        } else {
          /*
          // TODO: Look at the type and do something for each type to avoid Integer.MIN_VALUE etc.
          //  Types are ComplexVector, DoubleVector, ExpressionVector, IntVector, ListVector, LogicalVector,
          //  org.renjin.sexp.Null, RawVector, StringVector.
          Vector.Type type = col.getVectorType();
          if (ComplexVector.VECTOR_TYPE.equals(type)) {
            row.add(col.getElementAsObject(i));
          } else if (DoubleVector.VECTOR_TYPE.equals(type)) {
            row.add(col.getElementAsDouble(i));
          } else if () {

          }
           */
          row.add(col.getElementAsObject(i));
        }
      }
      ret.add(row);
    }
    return ret;
  }

  public static ListVector toDataFrame(Table table) {
    List<StringVector.Builder> builders = stringBuilders(table.headerList.size());
    int numRows = 0;
    for (int rowIdx = 0; rowIdx <= table.rowList.size(); rowIdx++) {
      numRows++;
      List<Object> row = table.rowList.get(rowIdx);
      int i = 0;
      for (int colIdx = 0; colIdx <= row.size(); colIdx++) {
        //System.out.println("Adding ext.getString(" + rowIdx + ", " + colIdx+ ") = " + ext.getString(row, colIdx));
        builders.get(i++).add(String.valueOf(row.get(colIdx)));
      }
    }
    ListVector columnVector = columnInfo(table.headerList);
    /* call build() on each column and add them as named cols to df */
    ListVector.NamedBuilder dfBuilder = new ListVector.NamedBuilder();
    for (int i = 0; i < columnVector.length(); i++) {
      ListVector ci = (ListVector) columnVector.get(i);
      dfBuilder.add(ci.get("name").asString(), builders.get(i).build());
    }
    dfBuilder.setAttribute("row.names", new RowNamesVector(numRows));
    dfBuilder.setAttribute("class", StringVector.valueOf("data.frame"));
    return dfBuilder.build();
  }

  private static ListVector columnInfo(List<String> headerList) {
    ListVector.Builder tv = new ListVector.Builder();
    for (String name : headerList) {
      ListVector.NamedBuilder cv = new ListVector.NamedBuilder();
      cv.add("name", name);
      tv.add(cv.build());
    }
    return tv.build();
  }

  private static List<StringVector.Builder> stringBuilders(int numColNums) {
    List<StringVector.Builder> builder = new ArrayList<>();
    for (int i = 0; i <= numColNums; i++) {
      builder.add(new StringVector.Builder());
    }
    //System.out.println("created " + builder.size() + " stringBuilders");
    return builder;
  }
}
