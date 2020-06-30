package com.kakarote.crm9.common.config.druid;

import com.jfinal.plugin.activerecord.Table;
import com.jfinal.plugin.activerecord.dialect.MysqlDialect;
import com.kakarote.crm9.utils.BaseUtil;

import java.util.List;
import java.util.Map;

public class MysqlDialectTo extends MysqlDialect {
    @Override
    public void forModelSave(Table table, Map<String, Object> attrs, StringBuilder sql, List<Object> paras) {
        attrs.put("c_uid", BaseUtil.getCompanyId());
        super.forModelSave(table,attrs,sql,paras);
    }
}
