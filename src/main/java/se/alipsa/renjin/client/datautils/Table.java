package se.alipsa.renjin.client.datautils;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.renjin.primitives.matrix.Matrix;
import org.renjin.sexp.*;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
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
      throw new IllegalArgumentException("Unknown type, " + type + " ( class: " + sexp.getClass()
          + "), convert this object to a data.frame or vector to use it");
    }
  }

  public Table(ListVector df) {
    headerList = toHeaderList(df);
    columnTypes = toTypeList(df);
    rowList = toRowlist(df);
  }

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

  public List<String> getHeaderList() {
    return headerList;
  }

  public List<List<Object>> getRowList() {
    return rowList;
  }

  public List<DataType> getColumnTypes() {
    return columnTypes;
  }

  /**
   * @param stringsOnlyOpt Optional param - if true, the resulting ListVector (data.frame) will consist of Strings (characters)
   * @return this table as a ListVector (data.frame) for easy handling in R
   */
  public ListVector asDataFrame(boolean... stringsOnlyOpt) {
    return toDataFrame(this, stringsOnlyOpt);
  }


  Object getValue(int row, int column) {
    return rowList.get(row).get(column);
  }

  public String getValueAsString(int row, int column) {
    return String.valueOf(getValue(row, column));
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
    if (val == null) {
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
    return getValueAsDouble(row, column).intValue();
  }

  public Long getValueAsLong(int row, int column) {
    return getValueAsDouble(row, column).longValue();
  }

  public Float getValueAsFloat(int row, int column) {
    return getValueAsDouble(row, column).floatValue();
  }

  public Byte getValueAsByte(int rowIdx, int colIdx) {
    Object val = getValue(rowIdx, colIdx);
    if (val instanceof Byte) {
      return (Byte)val;
    }
    return Byte.valueOf(String.valueOf(val));
  }
}
