package se.alipsa.renjin.client.datautils;

import org.renjin.primitives.Types;
import org.renjin.primitives.sequence.IntSequence;
import org.renjin.sexp.*;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

/**
 * Simple transformations of Renjin R data into OOTB java collections and similar
 */
public class RDataTransformer {

  private RDataTransformer() {
    // Utiltity class
  }

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

  public static List<DataType> toTypeList(ListVector df, boolean... stringsOnlyOpt) {
    List<DataType> typeList = new ArrayList<>(df.length());
    if (stringsOnlyOpt.length > 0 && stringsOnlyOpt[0]) {
      for (int i = 0; i < df.length(); i++) {
        typeList.add(DataType.STRING);
      }
    } else {
      for (SEXP col : df) {
        Vector column = (Vector) col;
        typeList.add(DataType.forVectorType(column.getVectorType()));
      }
    }
    return typeList;
  }

  public static List<List<Object>> toRowlist(ListVector df) {
    return toRowlist(df, false);
  }

  public static List<List<Object>> toRowlist(ListVector df, boolean contentAsStrings) {
    List<Vector> table = new ArrayList<>();
    for (SEXP col : df) {
      Vector column = (Vector) col;
      table.add(column);
    }
    return transpose(table, contentAsStrings);
  }


  public static List<List<Object>> transpose(List<Vector> table) {
    return transpose(table, false);
  }

  public static List<List<Object>> transpose(List<Vector> table, boolean contentAsStrings) {
    List<List<Object>> ret = new ArrayList<>();
    if (table.size() == 0) {
      return ret;
    }
    final int N = table.get(0).length();
    for (int i = 0; i < N; i++) {
      List<Object> row = new ArrayList<>();
      for (Vector col : table) {
        addValue(col, row, i, contentAsStrings);
      }
      ret.add(row);
    }
    return ret;
  }

  private static void addValue(Vector col, List<Object> column, int i, boolean contentAsStrings) {
    if (Types.isFactor(col)) {
      AttributeMap attributes = col.getAttributes();
      Map<Symbol, SEXP> attrMap = attributes.toMap();
      Symbol s = attrMap.keySet().stream().filter(p -> "levels".equals(p.getPrintName())).findAny().orElse(null);
      Vector vec = (Vector) attrMap.get(s);
      int factorIndex = col.getElementAsInt(i) - 1;
      if (Integer.MAX_VALUE == factorIndex) {
        //Element is NA, setting value to StringArrayVector.NA (it's a factor, so we know the type is character)
        column.add(StringArrayVector.NA);
      } else {
        if (contentAsStrings) {
          column.add(vec.getElementAsString(factorIndex));
        } else {
          column.add(vec.getElementAsObject(factorIndex));
        }
      }
    } else {
      if (contentAsStrings) {
        column.add(col.getElementAsString(i));
      } else {
        column.add(col.getElementAsObject(i));
      }
    }
  }

  /**
   * @param table the Table to convert
   * @return a ListVector (data.frame) corresponding to the Table
   */
  public static ListVector toDataframe(Table table) {
    return toDataframe(table, false);
  }

  /**
   * @param table       the Table to convert
   * @param stringsOnly if true, the resulting ListVector (data.frame) will consist of Strings (characters)
   * @return a ListVector (data.frame) corresponding to the Table
   */
  public static ListVector toDataframe(Table table, boolean stringsOnly, NumberFormat... numberFormat) {
    List<Vector.Builder<?>> builders;
    if (stringsOnly) {
      builders = stringBuilders(table.getHeaderSize());
    } else {
      builders = builders(table);
    }
    int numRows = 0;

    for (int rowIdx = 0; rowIdx < table.getRowSize(); rowIdx++) {
      numRows++;
      List<Object> row = table.getRow(rowIdx);
      int i = 0;
      for (Object val : row) {
        // Unfortunately Vector.Builder does not have an add(Object) method so we need to downcast
        Vector.Builder<?> builder = builders.get(i++);
        if (val == null) {
          builder.addNA();
        } else if (builder instanceof IntArrayVector.Builder) {
          ((IntArrayVector.Builder) builder).add(asInt(val));
        } else if (builder instanceof LogicalArrayVector.Builder) {
          ((LogicalArrayVector.Builder) builder).add(ValueConverter.asBoolean(val));
        } else if (builder instanceof DoubleArrayVector.Builder) {
          ((DoubleArrayVector.Builder) builder).add(asDouble(val, numberFormat));
        } else if (builder instanceof StringVector.Builder) {
          ((StringVector.Builder) builder).add(asString(val));
        } else if (builder instanceof RawVector.Builder) {
          ((RawVector.Builder) builder).add(asByte(val));
        } else {
          throw new DataTransformationRuntimeException(RDataTransformer.class + ".toDataFrame(): Unknown Builder type: "
              + builder.getClass());
        }
      }
    }
    ListVector columnVector = columnInfo(table.getHeaderList());
    /* call build() on each column and add them as named cols to df */
    ListVector.NamedBuilder dfBuilder = new ListVector.NamedBuilder();
    for (int i = 0; i < columnVector.length(); i++) {
      ListVector ci = (ListVector) columnVector.get(i);
      dfBuilder.add(ci.get("name").asString(), builders.get(i).build());
    }
    // used to be able to do
    //dfBuilder.setAttribute("row.names", new org.renjin.primitives.vector.RowNamesVector(numRows));
    // Try it with an IntSequence for now...
    dfBuilder.setAttribute("row.names", new IntSequence(1, 1, numRows));
    dfBuilder.setAttribute("class", StringVector.valueOf("data.frame"));
    return dfBuilder.build();
  }

  private static byte asByte(@Nonnull Object value) {
    if (value instanceof Byte) {
      return (Byte) value;
    }
    return Byte.parseByte(String.valueOf(value));
  }

  private static String asString(@Nonnull Object value) {
    return String.valueOf(value);
  }

  private static double asDouble(@Nonnull Object value, NumberFormat... numberFormatOpt) {
    NumberFormat numberFormat = numberFormatOpt.length > 0 ? numberFormatOpt[0] : NumberFormat.getNumberInstance();
    return ValueConverter.asDouble(value, numberFormat);
  }

  private static int asInt(@Nonnull Object value, NumberFormat... numberFormatOpt) {
    if (value instanceof Integer) {
      return (int) value;
    }
    return Integer.parseInt(String.valueOf(value));
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

  private static List<Vector.Builder<?>> builders(Table table) {
    int numColNums = table.getColumnTypes().size();
    int numRows = table.getRowSize();
    List<Vector.Builder<?>> builderList = new ArrayList<>(numColNums);
    for (int i = 0; i < numColNums; i++) {
      DataType dataType = table.getColumnType(i);
      builderList.add(dataType.getVectorType().newBuilderWithInitialCapacity(numRows));
    }
    return builderList;
  }


  private static List<Vector.Builder<?>> stringBuilders(int numColNums) {
    List<Vector.Builder<?>> builder = new ArrayList<>();
    for (int i = 0; i <= numColNums; i++) {
      builder.add(new StringVector.Builder());
    }
    //System.out.println("created " + builder.size() + " stringBuilders");
    return builder;
  }
}
