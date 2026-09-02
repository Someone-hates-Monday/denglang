package com.cqu.config;

import com.pgvector.PGvector;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * pgvector 类型转换器：float[] ↔ PGvector
 * <p>
 * 使用方式：
 * 1. 实体类 embedding 字段加 {@code @TableField(typeHandler = PgVectorTypeHandler.class)}
 * 2. 或在 application.yml 中全局注册
 */
@MappedTypes(float[].class)
public class PgVectorTypeHandler extends BaseTypeHandler<float[]> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, float[] parameter, JdbcType jdbcType) throws SQLException {
        ps.setObject(i, new PGvector(parameter));
    }

    @Override
    public float[] getNullableResult(ResultSet rs, String columnName) throws SQLException {
        PGvector vector = (PGvector) rs.getObject(columnName);
        return vector != null ? vector.toArray() : null;
    }

    @Override
    public float[] getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        PGvector vector = (PGvector) rs.getObject(columnIndex);
        return vector != null ? vector.toArray() : null;
    }

    @Override
    public float[] getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        PGvector vector = (PGvector) cs.getObject(columnIndex);
        return vector != null ? vector.toArray() : null;
    }
}
