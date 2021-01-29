package se.alipsa.renjin.client.datautils;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.renjin.primitives.matrix.Matrix;
import org.renjin.sexp.*;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static se.alipsa.renjin.client.datautils.RDataTransformer.*;

/**
 * Data frames in R are "column based" (variable based) which is very convenient for analysis but Java is
 * Object / Observation based so a Table which essentially is just a List of rows (observations), makes is much easier
 * to work with the data in Java.
 */
public class Table {

  List<String> headerList = new ArrayList<>();
  List<List<Object>> rowList = new ArrayList<>();
  List<DataType> columnTypes = new ArrayList<>();

  public Table() {
    // Empty
  }

  public static Table createTable(SEXP sexp) {
    String type = sexp.getTypeName();
    if (sexp instanceof ListVector) {
      return new Table((ListVector) sexp);
    } else if (sexp instanceof Vector) {
      Vector vec = (Vector) sexp;
      if (vec.hasAttributes()) {
        AttributeMap attributes = vec.getAttributes();
        Vector dim = attributes.getDim();
        if (dim == null) {
          return new Table(vec);
        } else {
          if (dim.length() == 2) {
            Matrix mat = new Matrix(vec);
            return new Table(mat);
          } else {
            throw new IllegalArgumentException("Array of type, "
                + sexp.getTypeName() + " cannot be shown , Result is an array with "
                + dim.length() + " dimensions. Convert this object to a data.frame to use it!");
          }
        }
      } else {
        return new Table(vec);
      }
    } else {
      throw new DataTransformationRuntimeException("Unknown type, " + type + " ( class: " + sexp.getClass()
          + "), convert this object to a data.frame or vector to use it");
    }
  }

  public Table(ListVector df) {
    this(df, false);
  }

  public Table(ListVector df, boolean contentAsStrings) {
    headerList = toHeaderList(df);
    columnTypes = toTypeList(df, contentAsStrings);
    rowList = toRowlist(df, contentAsStrings);
  }

  @SafeVarargs
  public Table(List<String> columnList, List<List<Object>> rowList, List<DataType>... dataTypesOpt) {
    headerList = columnList;
    this.rowList = rowList;
    if (dataTypesOpt.length > 0) {
      columnTypes = dataTypesOpt[0];
    } else {
      for(int i = 0; i < columnList.size(); i++) {
        columnTypes.add(null);
      }
    }
  }

  public Table(Matrix mat) {
    DataType dataType;
    if("integer".equals(mat.getVector().getTypeName())) {
      dataType = DataType.INTEGER;
    } else { // No other types supported in Renjin at the moment
      dataType = DataType.DOUBLE;
    }
    for (int i = 0; i < mat.getNumCols(); i++) {
      columnTypes.add(dataType);
    }

    for (int i = 0; i < mat.getNumCols(); i++) {
      String colName = mat.getColName(i) == null ? i + "" : mat.getColName(i);
      headerList.add(colName);
    }

    List<Object> row;
    for (int i = 0; i < mat.getNumRows(); i++) {
      row = new ArrayList<>();
      for (int j = 0; j < mat.getNumCols(); j++) {
        if (DataType.INTEGER == dataType) {
          row.add(mat.getElementAsInt(i, j));
        } else {
          row.add(mat.getElementAsDouble(i, j));
        }
      }
      rowList.add(row);
    }
  }

  public Table(Vector vec) {
    headerList.add(vec.getTypeName()); // TODO: should be name of the list, not the type name
    columnTypes.add(DataType.forVectorType(vec.getVectorType()));
    List<Vector> values = new ArrayList<>();
    values.add(vec);

    rowList = transpose(values);
  }

  public Table(ResultSet rs) throws SQLException {
    ResultSetMetaData rsmd = rs.getMetaData();

    int ncols = rsmd.getColumnCount();

    for (int i = 1; i <= ncols; i++) {
      headerList.add(rsmd.getColumnName(i));
      columnTypes.add(DataType.forSqlType(rsmd.getColumnType(i)));
    }
    while (rs.next()) {
      List<Object> row = new ArrayList<>();
      for (int i = 1; i <= ncols; i++) {
        row.add(rs.getObject(i));
      }
      rowList.add(row);
    }
  }

  public Table asTransposed() {
    return new Table(headerList, getColumnList(), columnTypes);
  }

  public List<List<Object>> getColumnList() {
    List<List<Object>> columnList = new ArrayList<>();
    for (List<Object> row : rowList) {
      if (columnList.size() == 0) {
        for (int i = 0; i < row.size(); i++) {
          columnList.add(new ArrayList<>());
        }
      }
      for (int j = 0; j < row.size(); j++) {
        columnList.get(j).add(row.get(j));
      }
    }
    return columnList;
  }

  public List<String> getHeaderList() {
    return headerList;
  }

  public int getHeaderSize() {
    return headerList.size();
  }

  public List<List<Object>> getRowList() {
    return rowList;
  }

  public int getRowSize() {
    return rowList.size();
  }

  public List<Object> getRow(int index) {
    return rowList.get(index);
  }

  /**
   * Find the first row based on a cell value specified
   * Note: Overloading methods does not work in Birt (Rhino), must use a unique name for each method so cannot name it getRow
   * @param value the cell value to search for
   * @param column the column index to look in, defaults to 0 (first column)
   * @return the row as a List of Objects or null if no row found
   */
  public List<Object> getRowForName(String value, int... column) {
    int col = column.length == 0 ? 0 : column[0];
    for (List<Object> row : rowList) {
      if (value.equals(row.get(col))) {
        return row;
      }
    }
    return null;
  }

  public List<DataType> getColumnTypes() {
    return columnTypes;
  }

  /**
   * @param stringsOnlyOpt Optional param - if true, the resulting ListVector (data.frame) will consist of Strings (characters)
   * @return this table as a ListVector (data.frame) for easy handling in R
   */
  public ListVector asDataframe(boolean... stringsOnlyOpt) {
    return toDataframe(this, stringsOnlyOpt.length > 0 && stringsOnlyOpt[0]);
  }


  public Object getValue(int row, int column) {
    return rowList.get(row).get(column);
  }

  public String getValueAsString(int row, int column) {
    Object val = getValue(row, column);
    if (val == null) {
      return null;
    }
    return String.valueOf(val);
  }

  public Double getValueAsDouble(int row, int column) {
    Object val = getValue(row, column);
    if (val == null) {
      return null;
    }
    if (val instanceof Double) {
      return (Double)val;
    }
    return Double.parseDouble(String.valueOf(val));
  }

  @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
  public Boolean getValueAsBoolean(int row, int column) {
    Object val = getValue(row, column);
    if (val == null || val instanceof Double && Double.isNaN((Double)val)) {
      return null;
    }
    if (val instanceof Boolean) {
      return (Boolean) val;
    }
    switch (String.valueOf(val).toLowerCase()) {
      case "true":
      case "1":
      case "on":
      case "yes":
        return Boolean.TRUE;
      default:
        return Boolean.FALSE;
    }
  }

  public Integer getValueAsInteger(int row, int column) {
    Double val = getValueAsDouble(row, column);
    if (val == null || Double.isNaN(val)) {
      return null;
    }
    return val.intValue();
  }

  public Long getValueAsLong(int row, int column) {
    Double val = getValueAsDouble(row, column);
    return val == null ? null : val.longValue();
  }

  public Float getValueAsFloat(int row, int column) {
    Double val = getValueAsDouble(row, column);
    return val == null ? null : val.floatValue();
  }

  public Byte getValueAsByte(int rowIdx, int colIdx) {
    Object val = getValue(rowIdx, colIdx);
    if (val instanceof Byte) {
      return (Byte)val;
    }
    return val == null ? null : Byte.valueOf(String.valueOf(val));
  }

  public LocalDate getValueAsLocalDate(int rowIdx, int colIdx) {
    return getValueAsLocalDate(rowIdx, colIdx, "yyyy-MM-dd");
  }

  public LocalDate getValueAsLocalDate(int rowIdx, int colIdx, String format) {
    Object val = getValue(rowIdx, colIdx);
    if (val == null) {
      return null;
    }
    if (val instanceof Double) {
      return LocalDate.ofEpochDay(((Double)val).longValue());
    } else {
      return LocalDate.parse(String.valueOf(val), DateTimeFormatter.ofPattern(format));
    }
  }

  public LocalDateTime getValueAsLocalDateTime(int rowIdx, int colIdx) {
    return getValueAsLocalDateTime(rowIdx, colIdx, ZoneOffset.UTC);
  }

  public LocalDateTime getValueAsLocalDateTime(int rowIdx, int colIdx, ZoneOffset offset) {
    Object val = getValue(rowIdx, colIdx);
    if (val == null) {
      return null;
    }
    if (val instanceof Double) {
      long longVal = ((Double)val).longValue();
      //System.out.println("LocalDateTime from " + longVal + ", offset = " + offset);
      return LocalDateTime.ofEpochSecond(longVal, 0, offset);
    } else {
      String strVal = String.valueOf(val);
      if (strVal.length() == 10 ) {
        return LocalDateTime.parse(strVal, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
      } else if (strVal.length() == 19) {
        return LocalDateTime.parse(strVal, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
      }
      throw new IllegalArgumentException("Unknown date time format for " + strVal);
    }
  }

  @Override
  public String toString() {
    return "Table with " + getHeaderSize() + " columns and " + getRowSize() + " rows";
  }
}
