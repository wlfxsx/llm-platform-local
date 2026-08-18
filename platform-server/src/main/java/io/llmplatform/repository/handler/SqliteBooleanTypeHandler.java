package io.llmplatform.repository.handler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

/** SQLite 以 0/1 存储布尔值。 */
@MappedTypes(Boolean.class)
public class SqliteBooleanTypeHandler extends BaseTypeHandler<Boolean> {

    private static Boolean toBoolean(Object value) {
        return switch (value) {
            case null -> null;
            case Boolean bool -> bool;
            case Number number -> number.intValue() != 0;
            default -> "1".equals(value.toString()) || Boolean.parseBoolean(value.toString());
        };
    }

    @Override
    public void setNonNullParameter(
            PreparedStatement ps, int i, Boolean parameter, JdbcType jdbcType) throws SQLException {
        ps.setInt(i, Boolean.TRUE.equals(parameter) ? 1 : 0);
    }

    @Override
    public Boolean getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toBoolean(rs.getObject(columnName));
    }

    @Override
    public Boolean getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toBoolean(rs.getObject(columnIndex));
    }

    @Override
    public Boolean getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toBoolean(cs.getObject(columnIndex));
    }
}
