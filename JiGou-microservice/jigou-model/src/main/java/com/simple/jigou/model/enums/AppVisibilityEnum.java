package com.simple.jigou.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

/**
 * 应用可见范围枚举类
 */
@Getter
public enum AppVisibilityEnum {

    PRIVATE("私有", "private"),
    PUBLIC("公开", "public");

    private final String text;

    private final String value;

    AppVisibilityEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 value 获取枚举
     *
     * @param value 枚举值的value
     * @return 枚举值
     */
    public static AppVisibilityEnum getEnumByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (AppVisibilityEnum anEnum : AppVisibilityEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }

    /**
     * 判断应用是否公开（历史数据 visibility 为空时按私有处理，保证可见范围默认为最小）
     *
     * @param visibility 可见范围取值
     * @return 是否公开
     */
    public static boolean isPublic(String visibility) {
        return PUBLIC.getValue().equals(visibility);
    }
}
