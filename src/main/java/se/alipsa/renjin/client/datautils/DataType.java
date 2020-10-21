package se.alipsa.renjin.client.datautils;

import org.renjin.sexp.DoubleVector;
import org.renjin.sexp.IntVector;
import org.renjin.sexp.LogicalVector;
import org.renjin.sexp.RawVector;
import org.renjin.sexp.StringVector;
import org.renjin.sexp.Vector;

import java.sql.Types;

public enum DataType {
  INTEGER("integer", IntVector.VECTOR_TYPE),
  BOOLEAN("logical", LogicalVector.VECTOR_TYPE),
  DOUBLE("double", DoubleVector.VECTOR_TYPE),
  STRING("character", StringVector.VECTOR_TYPE),
  BYTE_ARRAY("raw", RawVector.VECTOR_TYPE);

  private final String rtypeName;
  private final Vector.Type vectorType;

  DataType(String rtypeName, Vector.Type vectorType) {
    this.rtypeName = rtypeName;
    this.vectorType = vectorType;
  }

  public static DataType forVectorType(Vector.Type vectorType) {
    for (DataType dataType : values()) {
      if (dataType.vectorType.equals(vectorType)) {
        return dataType;
      }
    }
    return null;
  }

  public static DataType forSqlType(int columnType) {
    switch (columnType) {
      case Types.BIGINT:
      case Types.BINARY:
      case Types.INTEGER:
      case Types.SMALLINT:
      case Types.TINYINT:
        return DataType.INTEGER;
      case Types.BOOLEAN:
      case Types.BIT:
        return DataType.BOOLEAN;
      case Types.CHAR:
      case Types.CLOB:
      case Types.LONGNVARCHAR:
      case Types.LONGVARCHAR:
      case Types.NCHAR:
      case Types.NCLOB:
      case Types.NVARCHAR:
      case Types.VARCHAR:
        return DataType.STRING;
      case Types.DECIMAL:
      case Types.DOUBLE:
      case Types.FLOAT:
      case Types.REAL:
        return DataType.DOUBLE;
      case Types.JAVA_OBJECT:
      case Types.LONGVARBINARY:
      case Types.BLOB:
      case Types.VARBINARY:
        return DataType.BYTE_ARRAY;
      default:
        return(null);
    }
  }

  public String getRtypeName() {
    return rtypeName;
  }

  public Vector.Type getVectorType() {
    return vectorType;
  }
}
