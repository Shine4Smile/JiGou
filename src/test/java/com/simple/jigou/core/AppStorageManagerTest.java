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
import java.util.Map;

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
        // 清理测试产生的工作区、临时目录、构建目录、备份目录与版本库目录
        FileUtil.del(AppStorageManager.getWorkDir(CODE_GEN_TYPE, TEST_APP_ID));
        FileUtil.del(AppStorageManager.getTempDir(CODE_GEN_TYPE, TEST_APP_ID));
        FileUtil.del(AppStorageManager.getBuildDir(CODE_GEN_TYPE, TEST_APP_ID));
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

    @Test
    void deleteAppCodeShouldRemoveWorkDirAndVersionLib() {
        // 1. 构造工作区代码并提交两个版本，模拟真实应用的数据目录
        writeWorkFile("index.html", "<h1>v1</h1>");
        AppStorageManager.commitVersion(CODE_GEN_TYPE, TEST_APP_ID);
        writeWorkFile("index.html", "<h1>v2</h1>");
        AppStorageManager.commitVersion(CODE_GEN_TYPE, TEST_APP_ID);
        Assertions.assertEquals(Arrays.asList(2, 1), AppStorageManager.listVersions(CODE_GEN_TYPE, TEST_APP_ID));
        // 2. 造出生成流程异常中断时可能残留的临时目录、备份目录与部署历史版本时的临时构建目录
        FileUtil.mkdir(AppStorageManager.getTempDir(CODE_GEN_TYPE, TEST_APP_ID));
        FileUtil.mkdir(backupDir());
        FileUtil.mkdir(AppStorageManager.getBuildDir(CODE_GEN_TYPE, TEST_APP_ID));

        // 3. 删除应用代码：工作区与残留目录一并清理
        Assertions.assertTrue(AppStorageManager.deleteWorkDir(CODE_GEN_TYPE, TEST_APP_ID));
        Assertions.assertFalse(AppStorageManager.getWorkDir(CODE_GEN_TYPE, TEST_APP_ID).exists());
        Assertions.assertFalse(AppStorageManager.getTempDir(CODE_GEN_TYPE, TEST_APP_ID).exists());
        Assertions.assertFalse(backupDir().exists());
        Assertions.assertFalse(AppStorageManager.getBuildDir(CODE_GEN_TYPE, TEST_APP_ID).exists());

        // 4. 删除版本库目录：该应用的全部历史版本快照一并清理
        Assertions.assertTrue(AppStorageManager.deleteVersionRootDir(CODE_GEN_TYPE, TEST_APP_ID));
        Assertions.assertFalse(AppStorageManager.getVersionRootDir(CODE_GEN_TYPE, TEST_APP_ID).exists());

        // 5. 目录不存在时再次删除仍返回成功（应用可能从未生成过代码）
        Assertions.assertTrue(AppStorageManager.deleteWorkDir(CODE_GEN_TYPE, TEST_APP_ID));
        Assertions.assertTrue(AppStorageManager.deleteVersionRootDir(CODE_GEN_TYPE, TEST_APP_ID));
    }

    @Test
    void readCodeFilesReturnsRelativePathMap() {
        // 1. 工作区中的多级目录文件以「相对路径 -> 内容」形式返回
        File workDir = AppStorageManager.getWorkDir(CODE_GEN_TYPE, TEST_APP_ID);
        writeWorkFile("index.html", "<h1>work</h1>");
        FileUtil.writeString("body{color:red}", new File(workDir, "css/style.css"), StandardCharsets.UTF_8);
        Map<String, String> workFileMap = AppStorageManager.readCodeFiles(workDir);
        Assertions.assertEquals(2, workFileMap.size());
        Assertions.assertEquals("<h1>work</h1>", workFileMap.get("index.html"));
        // 路径分隔符统一为 '/'，保证前端对比两个版本时文件名能对齐
        Assertions.assertEquals("body{color:red}", workFileMap.get("css/style.css"));

        // 2. 提交版本后，版本目录可按同样方式读取（版本对比的数据来源）
        int v1 = AppStorageManager.commitVersion(CODE_GEN_TYPE, TEST_APP_ID);
        Assertions.assertEquals(workFileMap,
                AppStorageManager.readCodeFiles(AppStorageManager.getVersionDir(CODE_GEN_TYPE, TEST_APP_ID, v1)));

        // 3. 目录不存在（应用从未生成过代码）时返回空集合而不是抛异常
        Assertions.assertTrue(AppStorageManager.readCodeFiles(
                AppStorageManager.getWorkDir(CODE_GEN_TYPE, TEST_APP_ID + 1)).isEmpty());
    }

    @Test
    void commitVersionIgnoresBuildArtifacts() {
        // 1. 构造「源码 + 依赖 + 构建产物」的工作区，模拟 Vue 工程的真实目录
        writeWorkFile("index.html", "<h1>v1</h1>");
        writeWorkFile("src/main.js", "console.log(1)");
        writeWorkFile("node_modules/pkg/index.js", "module.exports = {}");
        writeWorkFile("node_modules/.bin/vite", "bin");
        writeWorkFile("dist/index.html", "<h1>built</h1>");
        writeWorkFile("dist/assets/index.js", "console.log('built')");

        // 2. 提交版本：版本快照只保留源码，依赖与构建产物不会进入版本库
        int v1 = AppStorageManager.commitVersion(CODE_GEN_TYPE, TEST_APP_ID);
        File versionDir = AppStorageManager.getVersionDir(CODE_GEN_TYPE, TEST_APP_ID, v1);
        Assertions.assertTrue(versionDir.exists());
        Assertions.assertFalse(new File(versionDir, "node_modules").exists());
        Assertions.assertFalse(new File(versionDir, "dist").exists());
        Assertions.assertEquals("<h1>v1</h1>", readVersionFile(v1, "index.html"));
        Assertions.assertEquals("console.log(1)", readVersionFile(v1, "src/main.js"));

        // 3. 版本对比的数据来源（readCodeFiles）同样过滤，只返回源码文件
        Map<String, String> versionFileMap = AppStorageManager.readCodeFiles(versionDir);
        Assertions.assertEquals(2, versionFileMap.size());
        Assertions.assertEquals("<h1>v1</h1>", versionFileMap.get("index.html"));
        Assertions.assertEquals("console.log(1)", versionFileMap.get("src/main.js"));

        // 4. 工作区只多出依赖与构建产物（如刚跑过一次构建）时，不算「有未提交的修改」
        writeWorkFile("node_modules/pkg/extra.js", "console.log(2)");
        writeWorkFile("dist/assets/extra.js", "console.log(3)");
        Assertions.assertFalse(AppStorageManager.hasUncommittedChanges(CODE_GEN_TYPE, TEST_APP_ID, v1));
    }

    @Test
    void restoreVersionSkipsLegacyBuildArtifacts() {
        // 1. 构造历史遗留快照：本次优化之前提交的版本目录里含有 node_modules 与 dist
        writeWorkFile("index.html", "<h1>v1</h1>");
        int v1 = AppStorageManager.commitVersion(CODE_GEN_TYPE, TEST_APP_ID);
        File versionDir = AppStorageManager.getVersionDir(CODE_GEN_TYPE, TEST_APP_ID, v1);
        FileUtil.writeString("module.exports = {}",
                new File(versionDir, "node_modules/pkg/index.js"), StandardCharsets.UTF_8);
        FileUtil.writeString("<h1>built</h1>",
                new File(versionDir, "dist/index.html"), StandardCharsets.UTF_8);

        // 2. 改动工作区后回退：工作区被 v1 整体替换，且不会把遗留的依赖与构建产物带回来
        writeWorkFile("index.html", "<h1>changed</h1>");
        AppStorageManager.restoreVersion(CODE_GEN_TYPE, TEST_APP_ID, v1);
        File workDir = AppStorageManager.getWorkDir(CODE_GEN_TYPE, TEST_APP_ID);
        Assertions.assertEquals("<h1>v1</h1>", readFile(workDir, "index.html"));
        Assertions.assertFalse(new File(workDir, "node_modules").exists());
        Assertions.assertFalse(new File(workDir, "dist").exists());
        // 回退后的工作区在内容上与 v1 的源码完全一致
        Assertions.assertFalse(AppStorageManager.hasUncommittedChanges(CODE_GEN_TYPE, TEST_APP_ID, v1));
        assertNoResidue();
    }

    @Test
    void readCodeFilesSkipsNestedIgnoredDirs() {
        // 任意层级的 node_modules / dist 都应被忽略，其余源码文件正常返回
        writeWorkFile("index.html", "<h1>work</h1>");
        writeWorkFile("src/app.js", "console.log(1)");
        writeWorkFile("src/node_modules/pkg/index.js", "console.log(2)");
        writeWorkFile("src/dist/app.js", "console.log(3)");
        Map<String, String> fileMap = AppStorageManager.readCodeFiles(
                AppStorageManager.getWorkDir(CODE_GEN_TYPE, TEST_APP_ID));
        Assertions.assertEquals(2, fileMap.size());
        Assertions.assertEquals("<h1>work</h1>", fileMap.get("index.html"));
        Assertions.assertEquals("console.log(1)", fileMap.get("src/app.js"));
    }

    @Test
    void hasCodeIgnoresBuildArtifactsOnly() {
        // 1. 工作区里只有依赖与构建产物时，视为「还没有可提交的代码」
        writeWorkFile("node_modules/pkg/index.js", "console.log(1)");
        writeWorkFile("dist/index.html", "<h1>built</h1>");
        Assertions.assertFalse(AppStorageManager.hasCode(CODE_GEN_TYPE, TEST_APP_ID));
        Assertions.assertThrows(BusinessException.class,
                () -> AppStorageManager.commitVersion(CODE_GEN_TYPE, TEST_APP_ID));

        // 2. 补上一份源码后即可正常提交
        writeWorkFile("index.html", "<h1>v1</h1>");
        Assertions.assertTrue(AppStorageManager.hasCode(CODE_GEN_TYPE, TEST_APP_ID));
        Assertions.assertEquals(1, AppStorageManager.commitVersion(CODE_GEN_TYPE, TEST_APP_ID));
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
     * 校验没有残留的临时目录、构建目录与备份目录
     */
    private void assertNoResidue() {
        Assertions.assertFalse(AppStorageManager.getTempDir(CODE_GEN_TYPE, TEST_APP_ID).exists());
        Assertions.assertFalse(AppStorageManager.getBuildDir(CODE_GEN_TYPE, TEST_APP_ID).exists());
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
