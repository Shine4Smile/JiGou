package com.simple.jigou.core;

import cn.hutool.core.io.FileUtil;
import com.simple.jigou.ai.model.HtmlCodeResult;
import com.simple.jigou.ai.model.MultiFileCodeResult;
import com.simple.jigou.constant.AppConstant;
import com.simple.jigou.core.saver.CodeFileSaverExecutor;
import com.simple.jigou.core.saver.CodeFileSaverTemplate;
import com.simple.jigou.exception.BusinessException;
import com.simple.jigou.exception.ErrorCode;
import com.simple.jigou.model.enums.CodeGenTypeEnum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 应用文件存储与版本管理测试
 * 使用独立的测试应用 id，避免影响真实应用的代码目录
 */
class AppStorageManagerTest {

    /**
     * 测试应用 id（与真实应用 id 区分开，测试结束后会清理目录）
     */
    private static final Long TEST_APP_ID = 999999999999999999L;

    private static final String CODE_GEN_TYPE = CodeGenTypeEnum.MULTI_FILE.getValue();

    @AfterEach
    void cleanUp() {
        // 清理测试产生的工作区、临时目录、备份目录与版本库目录
        FileUtil.del(AppStorageManager.getWorkDir(CODE_GEN_TYPE, TEST_APP_ID));
        FileUtil.del(AppStorageManager.getTempDir(CODE_GEN_TYPE, TEST_APP_ID));
        FileUtil.del(AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + ".bak_" + AppStorageManager.getAppDirName(CODE_GEN_TYPE, TEST_APP_ID));
        FileUtil.del(AppConstant.CODE_VERSION_ROOT_DIR + File.separator + AppStorageManager.getAppDirName(CODE_GEN_TYPE, TEST_APP_ID));
    }

    @Test
    void commitAndRestoreVersion() {
        // 1. 构造工作区代码（模拟 AI 生成结果）
        writeWorkFile("index.html", "<h1>v0</h1>");
        Assertions.assertTrue(AppStorageManager.hasCode(CODE_GEN_TYPE, TEST_APP_ID));
        // 从未提交版本时，工作区有代码即认为存在未提交内容
        Assertions.assertTrue(AppStorageManager.hasUncommittedChanges(CODE_GEN_TYPE, TEST_APP_ID, 0));

        // 2. 第一次提交：生成 v1
        int v1 = AppStorageManager.commitVersion(CODE_GEN_TYPE, TEST_APP_ID);
        Assertions.assertEquals(1, v1);
        Assertions.assertEquals("<h1>v0</h1>", readVersionFile(v1, "index.html"));
        // 提交后工作区与 v1 内容一致，不应再有未提交修改
        Assertions.assertFalse(AppStorageManager.hasUncommittedChanges(CODE_GEN_TYPE, TEST_APP_ID, v1));

        // 3. 修改工作区并再次提交：生成 v2（版本号只增不减）
        writeWorkFile("index.html", "<h1>v1</h1>");
        Assertions.assertTrue(AppStorageManager.hasUncommittedChanges(CODE_GEN_TYPE, TEST_APP_ID, v1));
        int v2 = AppStorageManager.commitVersion(CODE_GEN_TYPE, TEST_APP_ID);
        Assertions.assertEquals(2, v2);
        Assertions.assertEquals(Arrays.asList(2, 1), AppStorageManager.listVersions(CODE_GEN_TYPE, TEST_APP_ID));

        // 4. 回退到 v1：工作区内容变为 v1 的内容，历史版本不受影响
        AppStorageManager.restoreVersion(CODE_GEN_TYPE, TEST_APP_ID, v1);
        Assertions.assertEquals("<h1>v0</h1>", FileUtil.readString(
                new File(AppStorageManager.getWorkDir(CODE_GEN_TYPE, TEST_APP_ID), "index.html"), StandardCharsets.UTF_8));
        Assertions.assertEquals("<h1>v1</h1>", readVersionFile(v2, "index.html"));
        // 回退后工作区内容与 v1 完全一致
        Assertions.assertFalse(AppStorageManager.hasUncommittedChanges(CODE_GEN_TYPE, TEST_APP_ID, v1));

        // 5. 回退后再次提交：生成 v3（分叉的新版本，不覆盖 v2）
        Assertions.assertEquals(3, AppStorageManager.commitVersion(CODE_GEN_TYPE, TEST_APP_ID));
        Assertions.assertEquals(Arrays.asList(3, 2, 1), AppStorageManager.listVersions(CODE_GEN_TYPE, TEST_APP_ID));
        // 提交时间应晚于当前时间之前（即已被正确记录）
        Assertions.assertNotNull(AppStorageManager.getCommitTime(CODE_GEN_TYPE, TEST_APP_ID, v1));
    }

    @Test
    void evictOldVersionsKeepsLatestAndProtected() {
        // 连续提交 5 个版本
        for (int i = 1; i <= 5; i++) {
            writeWorkFile("index.html", "<h1>v" + i + "</h1>");
            AppStorageManager.commitVersion(CODE_GEN_TYPE, TEST_APP_ID);
        }
        Assertions.assertEquals(Arrays.asList(5, 4, 3, 2, 1), AppStorageManager.listVersions(CODE_GEN_TYPE, TEST_APP_ID));
        // 保留最新 2 个版本，同时 v1 受保护（如当前版本 / 部署版本），其余淘汰
        List<Integer> evicted = AppStorageManager.evictOldVersions(CODE_GEN_TYPE, TEST_APP_ID, 2, Collections.singleton(1));
        Assertions.assertEquals(Arrays.asList(3, 2), evicted);
        Assertions.assertEquals(Arrays.asList(5, 4, 1), AppStorageManager.listVersions(CODE_GEN_TYPE, TEST_APP_ID));
    }

    @Test
    void commitWithoutCodeShouldFail() {
        Assertions.assertThrows(BusinessException.class,
                () -> AppStorageManager.commitVersion(CODE_GEN_TYPE, TEST_APP_ID));
    }

    @Test
    void saverReplacesWorkDirAtomically() {
        // 1. 第一次保存：生成 index.html、style.css、script.js
        MultiFileCodeResult firstResult = new MultiFileCodeResult();
        firstResult.setHtmlCode("<h1>first</h1>");
        firstResult.setCssCode("body{color:red}");
        firstResult.setJsCode("console.log(1)");
        File workDir = CodeFileSaverExecutor.executeSaver(firstResult, CodeGenTypeEnum.MULTI_FILE, TEST_APP_ID);
        Assertions.assertEquals("<h1>first</h1>", readFile(workDir, "index.html"));
        Assertions.assertEquals("body{color:red}", readFile(workDir, "style.css"));
        Assertions.assertEquals("console.log(1)", readFile(workDir, "script.js"));
        // 保存完成后不应残留临时目录与备份目录
        assertNoResidue();

        // 2. 第二次保存失败（模拟生成异常）：已有代码不能被破坏
        Assertions.assertThrows(BusinessException.class,
                () -> new BrokenCodeFileSaverTemplate().saveCode(new HtmlCodeResult(), TEST_APP_ID));
        Assertions.assertEquals("<h1>first</h1>", readFile(workDir, "index.html"));
        Assertions.assertEquals("body{color:red}", readFile(workDir, "style.css"));
        assertNoResidue();

        // 3. 再次保存成功：工作区是整体替换而不是覆盖写入，本次未生成的文件会被清理
        MultiFileCodeResult secondResult = new MultiFileCodeResult();
        secondResult.setHtmlCode("<h1>second</h1>");
        CodeFileSaverExecutor.executeSaver(secondResult, CodeGenTypeEnum.MULTI_FILE, TEST_APP_ID);
        Assertions.assertEquals("<h1>second</h1>", readFile(workDir, "index.html"));
        Assertions.assertFalse(new File(workDir, "style.css").exists());
        assertNoResidue();
    }

    /**
     * 写出工作区文件
     */
    private void writeWorkFile(String fileName, String content) {
        File workDir = AppStorageManager.getWorkDir(CODE_GEN_TYPE, TEST_APP_ID);
        FileUtil.mkdir(workDir);
        FileUtil.writeString(content, new File(workDir, fileName), StandardCharsets.UTF_8);
    }

    /**
     * 读取版本目录下的文件内容
     */
    private String readVersionFile(int version, String fileName) {
        return readFile(AppStorageManager.getVersionDir(CODE_GEN_TYPE, TEST_APP_ID, version), fileName);
    }

    /**
     * 读取目录下的文件内容
     */
    private String readFile(File dir, String fileName) {
        return FileUtil.readString(new File(dir, fileName), StandardCharsets.UTF_8);
    }

    /**
     * 校验没有残留的临时目录与备份目录
     */
    private void assertNoResidue() {
        Assertions.assertFalse(AppStorageManager.getTempDir(CODE_GEN_TYPE, TEST_APP_ID).exists());
        Assertions.assertFalse(backupDir().exists());
    }

    /**
     * 替换工作区时使用的备份目录
     */
    private File backupDir() {
        return new File(AppConstant.CODE_OUTPUT_ROOT_DIR,
                ".bak_" + AppStorageManager.getAppDirName(CODE_GEN_TYPE, TEST_APP_ID));
    }

    /**
     * 模拟保存失败的保存器：写入过程中抛异常，验证已有代码不会被破坏
     */
    private static class BrokenCodeFileSaverTemplate extends CodeFileSaverTemplate<HtmlCodeResult> {

        @Override
        protected CodeGenTypeEnum getCodeType() {
            return CodeGenTypeEnum.MULTI_FILE;
        }

        @Override
        protected void saveFiles(HtmlCodeResult result, String baseDirPath) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "模拟保存失败");
        }
    }
}
