package se.alipsa.renjin.client.datautils;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.renjin.primitives.matrix.Matrix;
import org.renjin.sexp.*;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static se.alipsa.renjin.client.datautils.RDataTransformer.*;

/**
 * Data frames in R are "column based" (variable based) which is very convenient for analysis but Java is
 * Object / Observation based so a Table which essentially is just a List of rows (observations), makes it much easier
 * to work with the data in Java.
 * Once the Table it created, the data is immutable.
 * You can, however, set the decimal formatter which determines how conversion to decimal data (double and floats) are
 * performed when you retrieve the data though the convenience methods getValueAsDouble() and getValueAsFloat()
 * (The default DecimalFormatter is using the default (current) Locale of the jvm).
 */
public class Table {

  private DecimalFormat decimalFormat = new DecimalFormat();

  private List<String> headerList;
  private List<List<Object>> rowList;
  private List<DataType> columnTypes;

  // derived from rowlist, calculated on first use and cached (this is why rowList is immutable)
  private List<List<Object>> columnList = null;

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
    headerList = Collections.unmodifiableList(toHeaderList(df));
    columnTypes = Collections.unmodifiableList(toTypeList(df, contentAsStrings));
    rowList = Collections.unmodifiableList(toRowlist(df, contentAsStrings));
  }

  @SafeVarargs
  public Table(List<String> headerList, List<List<Object>> rowList, List<DataType>... dataTypesOpt) {
    setHeaderList(headerList);
    setRowList(rowList);
    if (dataTypesOpt.length > 0) {
      setColumnTypes(dataTypesOpt[0]);
    } else {
      List<DataType> columns = new ArrayList<>();
      for(int i = 0; i < headerList.size(); i++) {
        columns.add(DataType.UNKNOWN);
      }
      setColumnTypes(columns);
    }
  }

  public Table(Matrix mat) {
    DataType dataType;
    if("integer".equals(mat.getVector().getTypeName())) {
      dataType = DataType.INTEGER;
    } else { // No other types supported in Renjin at the moment
      dataType = DataType.DOUBLE;
    }
    List<DataType> columns = new ArrayList<>();
    for (int i = 0; i < mat.getNumCols(); i++) {
      columns.add(dataType);
    }
    setColumnTypes(columns);

    List<String> headers = new ArrayList<>();
    for (int i = 0; i < mat.getNumCols(); i++) {
      String colName = mat.getColName(i) == null ? i + "" : mat.getColName(i);
      headers.add(colName);
    }
    setHeaderList(headers);

    List<Object> row;
    List<List<Object>> rows = new ArrayList<>();
    for (int i = 0; i < mat.getNumRows(); i++) {
      row = new ArrayList<>();
      for (int j = 0; j < mat.getNumCols(); j++) {
        if (DataType.INTEGER == dataType) {
          row.add(mat.getElementAsInt(i, j));
        } else {
          row.add(mat.getElementAsDouble(i, j));
        }
      }
      rows.add(row);
    }
    setRowList(rows);
  }

  public Table(Vector vec) {
    setHeaderList(Collections.singletonList(vec.getTypeName())); // TODO: should be name of the list, not the type name
    setColumnTypes(Collections.singletonList(DataType.forVectorType(vec.getVectorType())));
    List<Vector> values = new ArrayList<>();
    values.add(vec);
    setRowList(transpose(values));
  }

  public Table(ResultSet rs) throws SQLException {
    ResultSetMetaData rsmd = rs.getMetaData();

    int ncols = rsmd.getColumnCount();

    List<String> headers = new ArrayList<>();
    List<DataType> types = new ArrayList<>();
    for (int i = 1; i <= ncols; i++) {
      headers.add(rsmd.getColumnName(i));
      types.add(DataType.forSqlType(rsmd.getColumnType(i)));
    }
    setHeaderList(headers);
    setColumnTypes(types);

    List<List<Object>> rows = new ArrayList<>();
    while (rs.next()) {
      List<Object> row = new ArrayList<>();
      for (int i = 1; i <= ncols; i++) {
        row.add(rs.getObject(i));
      }
      rows.add(row);
    }
    setRowList(rows);
  }

  public Table asTransposed() {
    return new Table(headerList, getColumnList(), columnTypes);
  }

  @SuppressFBWarnings("EI_EXPOSE_REP")
  public List<List<Object>> getColumnList() {
    if (columnList == null) {
      List<List<Object>> columns = new ArrayList<>();
      for (List<Object> row : rowList) {
        if (columns.size() == 0) {
          for (int i = 0; i < row.size(); i++) {
            columns.add(new ArrayList<>());
          }
        }
        for (int j = 0; j < row.size(); j++) {
          columns.get(j).add(row.get(j));
        }
      }
      columnList = Collections.unmodifiableList(columns);
    }
    return columnList;
  }

  @SuppressFBWarnings("EI_EXPOSE_REP")
  public List<String> getHeaderList() {
    return headerList;
  }

  public int getHeaderSize() {
    return headerList.size();
  }

  @SuppressFBWarnings("EI_EXPOSE_REP")
  public List<List<Object>> getRowList() {
    return rowList;
  }

  public int getRowSize() {
    return rowList.size();
  }

  public List<Object> getRow(int index) {
    return rowList.get(index);
  }

  private void setHeaderList(List<String> headers) {
    headerList = Collections.unmodifiableList(headers);
  }

  private void setRowList(List<List<Object>> rows) {
    rowList = Collections.unmodifiableList(rows);
  }

  private void setColumnTypes(List<DataType> dataTypes) {
    columnTypes = Collections.unmodifiableList(dataTypes);
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

  @SuppressFBWarnings("EI_EXPOSE_REP")
  public List<DataType> getColumnTypes() {
    return columnTypes;
  }

  public List<Object> getColumnForName(String name) {
    int index = -1;
    for (int i = 0; i < headerList.size(); i++) {
      if (name.equals(headerList.get(i))) {
        index = i;
        break;
      }
    }
    return getColumnList().get(index);
  }

  public int getColumnIndex(String colName) {
    for (int colIdx = 0; colIdx < headerList.size(); colIdx++) {
      if (headerList.get(colIdx).equals(colName)) return colIdx;
    }
    return -1;
  }

  public List<Object> getColumn(int index) {
    return getColumnList().get(index);
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

    try {
      return decimalFormat.parse(String.valueOf(val)).doubleValue();
    } catch (ParseException e) {
      // if we could not parse it, this will also likely fail
      return Double.parseDouble(String.valueOf(val));
    }
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

  public DataType getColumnType(int i) {
    return columnTypes.get(i);
  }

  /**
   * Set the decimal formatter to use in convenience methods getting data as double or float.
   * Once set, the decimal formatter is no longer mutable i.e. a change to the decimal formatter
   * after it has been set in the table does not affect the decimal formatter in the table.
   */
  public void setDecimalFormat(DecimalFormat decimalFormat) {
    this.decimalFormat = (DecimalFormat)decimalFormat.clone();
  }
}
