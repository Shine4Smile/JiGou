package com.simple.jigou.core.saver;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.simple.jigou.core.AppStorageManager;
import com.simple.jigou.exception.BusinessException;
import com.simple.jigou.exception.ErrorCode;
import com.simple.jigou.model.enums.CodeGenTypeEnum;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * 抽象代码文件保存器 - 模板方法模式
 * <p>
 * 代码统一保存到应用的工作区目录 tmp/code_output/{codeGenType}_{appId}，
 * 且采用「先写临时目录、再整体替换工作区」的方式，保证生成失败时不会破坏已有代码
 *
 */
public abstract class CodeFileSaverTemplate<T> {

    /**
     * 模板方法：保存代码的标准流程
     *
     * @param result 代码结果对象
     * @param appId  应用 id
     * @return 保存的目录（应用工作区目录）
     */
    public final File saveCode(T result, Long appId) {
        // 1. 验证输入
        validateInput(result);
        if (appId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        }
        String codeType = getCodeType().getValue();
        // 2. 构建临时目录（先写临时目录，写入完成后整体替换工作区，避免生成失败破坏已有代码）
        File tempDir = AppStorageManager.getTempDir(codeType, appId);
        FileUtil.del(tempDir);
        FileUtil.mkdir(tempDir);
        try {
            // 3. 保存文件（具体实现由子类提供）
            saveFiles(result, tempDir.getAbsolutePath());
            // 4. 用临时目录整体替换工作区目录
            return AppStorageManager.replaceWorkDir(tempDir, codeType, appId);
        } finally {
            // 5. 兜底清理临时目录（替换成功后该目录已被移走，此处不会重复删除任何内容）
            FileUtil.del(tempDir);
        }
    }

    /**
     * 验证输入参数（可由子类覆盖）
     *
     * @param result 代码结果对象
     */
    protected void validateInput(T result) {
        if (result == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "代码结果对象不能为空");
        }
    }

    /**
     * 写入单个文件的工具方法
     *
     * @param dirPath  目录路径
     * @param filename 文件名
     * @param content  文件内容
     */
    protected final void writeToFile(String dirPath, String filename, String content) {
        if (StrUtil.isNotBlank(content)) {
            String filePath = dirPath + File.separator + filename;
            FileUtil.writeString(content, filePath, StandardCharsets.UTF_8);
        }
    }

    /**
     * 获取代码类型（由子类实现）
     *
     * @return 代码生成类型
     */
    protected abstract CodeGenTypeEnum getCodeType();

    /**
     * 保存文件的具体实现（由子类实现）
     *
     * @param result      代码结果对象
     * @param baseDirPath 基础目录路径
     */
    protected abstract void saveFiles(T result, String baseDirPath);
}
