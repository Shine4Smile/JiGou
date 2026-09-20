package com.simple.jigou.core;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.simple.jigou.constant.AppConstant;
import com.simple.jigou.exception.BusinessException;
import com.simple.jigou.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 应用代码文件存储管理器
 * <p>
 * 统一管理应用代码的两类目录，供版本管理、部署、数据清理等场景复用：
 * <ul>
 *     <li>工作区目录：tmp/code_output/{codeGenType}_{appId}，存放应用最新代码，AI 每次生成就地覆盖，预览与部署均基于该目录</li>
 *     <li>版本库目录：tmp/code_versions/{codeGenType}_{appId}/v{n}，存放用户主动「提交版本」时固化的代码快照，历史版本不可变</li>
 * </ul>
 * 目录结构示例（多文件模式、应用 id 为 1）：
 * <pre>
 * tmp/code_output/multi_file_1/index.html、style.css、script.js   —— 工作区（应用最新代码）
 * tmp/code_versions/multi_file_1/v1、v2                            —— 历史版本快照
 * </pre>
 * 设计说明：工作区目录即原有代码目录，因此历史数据无需任何迁移，老应用天然表现为「从未提交过版本」。
 *
 * @author simple
 */
@Slf4j
public class AppStorageManager {

    /**
     * 工作区根目录
     */
    private static final String WORK_ROOT_DIR = AppConstant.CODE_OUTPUT_ROOT_DIR;

    /**
     * 版本库根目录
     */
    private static final String VERSION_ROOT_DIR = AppConstant.CODE_VERSION_ROOT_DIR;

    /**
     * 版本目录名前缀，版本 n 的目录名为 v{n}
     */
    private static final String VERSION_DIR_PREFIX = "v";

    /**
     * 版本目录名格式：v1、v2 ...
     */
    private static final Pattern VERSION_DIR_PATTERN = Pattern.compile("^v(\\d+)$");

    /**
     * 写入代码时使用的临时目录前缀（先写临时目录，写入完成后整体替换工作区）
     */
    private static final String TEMP_DIR_PREFIX = ".tmp_";

    /**
     * 替换工作区时使用的备份目录前缀（替换失败时用于回滚）
     */
    private static final String BACKUP_DIR_PREFIX = ".bak_";

    private AppStorageManager() {
    }

    /**
     * 获取应用目录名：{codeGenType}_{appId}
     *
     * @param codeGenType 代码生成类型
     * @param appId       应用 id
     * @return 应用目录名
     */
    public static String getAppDirName(String codeGenType, Long appId) {
        throwIfIllegal(codeGenType, appId);
        return StrUtil.format("{}_{}", codeGenType, appId);
    }

    /**
     * 获取工作区目录（应用最新代码，仅返回路径对象，不存在时不会创建目录）
     *
     * @param codeGenType 代码生成类型
     * @param appId       应用 id
     * @return 工作区目录
     */
    public static File getWorkDir(String codeGenType, Long appId) {
        return FileUtil.file(WORK_ROOT_DIR, getAppDirName(codeGenType, appId));
    }

    /**
     * 获取写代码时使用的临时目录（与工作区同级，保证后续可以原子替换工作区）
     *
     * @param codeGenType 代码生成类型
     * @param appId       应用 id
     * @return 临时目录
     */
    public static File getTempDir(String codeGenType, Long appId) {
        return FileUtil.file(WORK_ROOT_DIR, TEMP_DIR_PREFIX + getAppDirName(codeGenType, appId));
    }

    /**
     * 获取版本库根目录（某个应用全部版本目录的父目录）
     *
     * @param codeGenType 代码生成类型
     * @param appId       应用 id
     * @return 版本库根目录
     */
    public static File getVersionRootDir(String codeGenType, Long appId) {
        return FileUtil.file(VERSION_ROOT_DIR, getAppDirName(codeGenType, appId));
    }

    /**
     * 获取指定版本的目录
     *
     * @param codeGenType 代码生成类型
     * @param appId       应用 id
     * @param version     版本号
     * @return 版本目录
     */
    public static File getVersionDir(String codeGenType, Long appId, Integer version) {
        if (version == null || version <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "版本号不合法");
        }
        return FileUtil.file(getVersionRootDir(codeGenType, appId), VERSION_DIR_PREFIX + version);
    }

    /**
     * 判断工作区是否存在代码文件
     *
     * @param codeGenType 代码生成类型
     * @param appId       应用 id
     * @return 是否存在代码
     */
    public static boolean hasCode(String codeGenType, Long appId) {
        File workDir = getWorkDir(codeGenType, appId);
        return workDir.exists() && !FileUtil.loopFiles(workDir).isEmpty();
    }

    /**
     * 查询应用的版本号列表（按版本号降序，忽略非版本目录）
     *
     * @param codeGenType 代码生成类型
     * @param appId       应用 id
     * @return 版本号列表
     */
    public static List<Integer> listVersions(String codeGenType, Long appId) {
        File versionRootDir = getVersionRootDir(codeGenType, appId);
        if (!versionRootDir.exists()) {
            return new ArrayList<>();
        }
        File[] versionDirs = versionRootDir.listFiles(File::isDirectory);
        if (versionDirs == null) {
            return new ArrayList<>();
        }
        return Arrays.stream(versionDirs)
                .map(dir -> {
                    Matcher matcher = VERSION_DIR_PATTERN.matcher(dir.getName());
                    return matcher.matches() ? Integer.valueOf(matcher.group(1)) : null;
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());
    }

    /**
     * 获取应用当前最大的版本号（无版本时返回 0）
     *
     * @param codeGenType 代码生成类型
     * @param appId       应用 id
     * @return 最大版本号
     */
    public static int getMaxVersion(String codeGenType, Long appId) {
        List<Integer> versionList = listVersions(codeGenType, appId);
        return versionList.isEmpty() ? 0 : versionList.get(0);
    }

    /**
     * 获取版本的提交时间（取版本目录与其内部文件中最晚的修改时间）
     *
     * @param codeGenType 代码生成类型
     * @param appId       应用 id
     * @param version     版本号
     * @return 提交时间，版本不存在时返回 null
     */
    public static LocalDateTime getCommitTime(String codeGenType, Long appId, Integer version) {
        File versionDir = getVersionDir(codeGenType, appId, version);
        if (!versionDir.exists()) {
            return null;
        }
        long lastModified = versionDir.lastModified();
        for (File file : FileUtil.loopFiles(versionDir)) {
            lastModified = Math.max(lastModified, file.lastModified());
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(lastModified), ZoneId.systemDefault());
    }

    /**
     * 提交版本：把工作区的代码复制为一个新版本
     * <p>
     * 版本号 = 当前最大版本号 + 1，版本号只增不减，历史版本永远不会被覆盖，
     * 因此「回退到旧版本后再次提交」会生成分叉的新版本，而不是覆盖旧版本
     *
     * @param codeGenType 代码生成类型
     * @param appId       应用 id
     * @return 新版本号
     */
    public static synchronized int commitVersion(String codeGenType, Long appId) {
        if (!hasCode(codeGenType, appId)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "应用还没有代码，请先生成代码再提交版本");
        }
        int newVersion = getMaxVersion(codeGenType, appId) + 1;
        File versionDir = getVersionDir(codeGenType, appId, newVersion);
        if (versionDir.exists()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "版本目录已存在，请重试");
        }
        try {
            FileUtil.copyContent(getWorkDir(codeGenType, appId), versionDir, true);
        } catch (Exception e) {
            // 复制失败时清理残缺的版本目录，避免产生「空版本」
            FileUtil.del(versionDir);
            log.error("提交版本失败，appId = {}，version = {}", appId, newVersion, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "提交版本失败：" + e.getMessage());
        }
        return newVersion;
    }

    /**
     * 回退版本：把指定版本的代码恢复到工作区
     * <p>
     * 只修改工作区内容，历史版本目录保持不可变；工作区原有内容会被该版本内容整体替换
     *
     * @param codeGenType 代码生成类型
     * @param appId       应用 id
     * @param version     版本号
     */
    public static void restoreVersion(String codeGenType, Long appId, Integer version) {
        File versionDir = getVersionDir(codeGenType, appId, version);
        if (!versionDir.exists()) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "该版本不存在或已被清理");
        }
        // 先复制到临时目录，再整体替换工作区，避免复制失败导致工作区内容损坏
        File tempDir = getTempDir(codeGenType, appId);
        FileUtil.del(tempDir);
        try {
            FileUtil.copyContent(versionDir, tempDir, true);
        } catch (Exception e) {
            FileUtil.del(tempDir);
            log.error("回退版本失败，appId = {}，version = {}", appId, version, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "回退版本失败：" + e.getMessage());
        }
        replaceWorkDir(tempDir, codeGenType, appId);
    }

    /**
     * 删除指定版本（仅用于版本淘汰）
     *
     * @param codeGenType 代码生成类型
     * @param appId       应用 id
     * @param version     版本号
     * @return 是否删除成功
     */
    /**
     * 删除应用的工作区目录及生成过程中的残留目录（临时目录 / 备份目录）
     * <p>
     * 应用被删除时调用：工作区存放的是 AI 最新生成的代码文件，删除后应用不再占用磁盘空间
     *
     * @param codeGenType 代码生成类型
     * @param appId       应用 id
     * @return 是否删除成功
     */
    public static boolean deleteWorkDir(String codeGenType, Long appId) {
        File workDir = getWorkDir(codeGenType, appId);
        File parentDir = workDir.getParentFile();
        // 临时目录与备份目录由「写入代码」「替换工作区」流程产生，正常流程结束时会自动清理，
        // 这里兜底清理，避免流程异常中断时残留的目录随应用一起变成垃圾数据
        return FileUtil.del(workDir)
                & FileUtil.del(FileUtil.file(parentDir, TEMP_DIR_PREFIX + workDir.getName()))
                & FileUtil.del(FileUtil.file(parentDir, BACKUP_DIR_PREFIX + workDir.getName()));
    }

    /**
     * 删除应用的版本库目录（该应用的全部历史版本快照一并清理）
     * <p>
     * 应用被删除时调用：历史版本只对仍然存在的应用有意义，保留会变成无人可查的垃圾数据
     *
     * @param codeGenType 代码生成类型
     * @param appId       应用 id
     * @return 是否删除成功
     */
    public static boolean deleteVersionRootDir(String codeGenType, Long appId) {
        return FileUtil.del(getVersionRootDir(codeGenType, appId));
    }

    public static boolean deleteVersion(String codeGenType, Long appId, Integer version) {
        return FileUtil.del(getVersionDir(codeGenType, appId, version));
    }

    /**
     * 淘汰历史版本：保留最新的 keepCount 个版本，其余旧版本全部删除
     * <p>
     * 保护名单中的版本（如当前版本、线上部署版本）永远不会被淘汰，即使它已不在最新的 keepCount 个版本内
     *
     * @param codeGenType       代码生成类型
     * @param appId             应用 id
     * @param keepCount         保留的最新版本数量
     * @param protectedVersions 额外受保护的版本号（可为空）
     * @return 被淘汰的版本号列表
     */
    public static List<Integer> evictOldVersions(String codeGenType, Long appId, int keepCount,
                                                 Collection<Integer> protectedVersions) {
        List<Integer> versionList = listVersions(codeGenType, appId);
        if (CollUtil.isEmpty(versionList)) {
            return new ArrayList<>();
        }
        // 保护名单 = 额外指定的版本 + 最新的 keepCount 个版本
        Set<Integer> protectedSet = new HashSet<>();
        if (CollUtil.isNotEmpty(protectedVersions)) {
            protectedSet.addAll(protectedVersions);
        }
        int retainCount = Math.min(Math.max(keepCount, 0), versionList.size());
        protectedSet.addAll(versionList.subList(0, retainCount));
        List<Integer> evictedList = new ArrayList<>();
        for (Integer version : versionList) {
            if (protectedSet.contains(version)) {
                continue;
            }
            if (deleteVersion(codeGenType, appId, version)) {
                evictedList.add(version);
            }
        }
        if (CollUtil.isNotEmpty(evictedList)) {
            log.info("应用历史版本已超出上限，被淘汰的版本：appId = {}，versions = {}", appId, evictedList);
        }
        return evictedList;
    }

    /**
     * 判断工作区是否存在未提交的修改（基于内容摘要精确比对，不受文件修改时间影响）
     *
     * @param codeGenType 代码生成类型
     * @param appId       应用 id
     * @param baseVersion 当前版本号（0 表示从未提交版本）
     * @return 是否存在未提交的修改
     */
    public static boolean hasUncommittedChanges(String codeGenType, Long appId, Integer baseVersion) {
        File workDir = getWorkDir(codeGenType, appId);
        if (!workDir.exists()) {
            return false;
        }
        if (baseVersion == null || baseVersion <= 0) {
            // 从未提交过版本，工作区有代码即视为存在未提交内容
            return !FileUtil.loopFiles(workDir).isEmpty();
        }
        File versionDir = getVersionDir(codeGenType, appId, baseVersion);
        if (!versionDir.exists()) {
            // 基线版本已被清理，无法比对，保守认为存在未提交内容
            return true;
        }
        return !contentSignature(workDir).equals(contentSignature(versionDir));
    }

    /**
     * 用新目录整体替换工作区目录
     * <p>
     * 生成代码时先写临时目录，写入完成后调用本方法整体替换工作区，
     * 保证生成过程中出现异常（AI 输出被截断、解析失败等）时不会破坏已有代码；替换失败会自动回滚
     *
     * @param newDir      新目录（一般为临时目录，替换成功后会「移动」为工作区，原目录不再存在）
     * @param codeGenType 代码生成类型
     * @param appId       应用 id
     * @return 工作区目录
     */
    public static File replaceWorkDir(File newDir, String codeGenType, Long appId) {
        if (newDir == null || !newDir.exists()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "待替换的代码目录不存在");
        }
        File workDir = getWorkDir(codeGenType, appId);
        FileUtil.mkdir(workDir.getParentFile());
        File backupDir = FileUtil.file(workDir.getParentFile(), BACKUP_DIR_PREFIX + workDir.getName());
        boolean hasBackup = false;
        // 1. 旧工作区先改名备份（零拷贝），保证替换失败时可以回滚
        if (workDir.exists()) {
            FileUtil.del(backupDir);
            moveDir(workDir, backupDir);
            hasBackup = true;
        }
        try {
            // 2. 新目录整体移动为工作区
            moveDir(newDir, workDir);
        } catch (Exception e) {
            // 3. 替换失败：清理半成品并恢复备份
            FileUtil.del(workDir);
            if (hasBackup) {
                try {
                    moveDir(backupDir, workDir);
                } catch (Exception restoreException) {
                    log.error("代码目录回滚失败，appId = {}，备份目录 = {}", appId, backupDir.getAbsolutePath(), restoreException);
                }
            }
            log.error("替换应用代码目录失败，appId = {}", appId, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "保存代码失败：" + e.getMessage());
        }
        // 4. 替换成功，删除备份
        if (hasBackup) {
            FileUtil.del(backupDir);
        }
        return workDir;
    }

    /**
     * 计算目录内容摘要（文件名 + 文件内容的 MD5），用于判断工作区与某个版本的内容是否一致
     *
     * @param dir 目录
     * @return 内容摘要
     */
    private static String contentSignature(File dir) {
        List<File> fileList = FileUtil.loopFiles(dir);
        fileList.sort(Comparator.comparing(File::getAbsolutePath));
        Path rootPath = dir.toPath();
        StringBuilder builder = new StringBuilder();
        for (File file : fileList) {
            // 使用相对路径，避免不同机器的目录前缀影响比对结果
            String relativePath = rootPath.relativize(file.toPath()).toString().replace('\\', '/');
            builder.append(relativePath).append(':').append(md5Hex(FileUtil.readBytes(file))).append('\n');
        }
        return md5Hex(builder.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算 MD5 值
     *
     * @param bytes 字节数组
     * @return MD5 十六进制字符串
     */
    private static String md5Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "计算文件摘要失败：" + e.getMessage());
        }
    }

    /**
     * 移动目录（同一个磁盘分区下为原子改名操作）
     *
     * @param src  源目录
     * @param dest 目标目录（必须不存在）
     */
    private static void moveDir(File src, File dest) {
        try {
            Files.move(src.toPath(), dest.toPath());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "移动代码目录失败：" + e.getMessage());
        }
    }

    /**
     * 校验代码生成类型与应用 id
     *
     * @param codeGenType 代码生成类型
     * @param appId       应用 id
     */
    private static void throwIfIllegal(String codeGenType, Long appId) {
        if (StrUtil.isBlank(codeGenType)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "代码生成类型不能为空");
        }
        if (appId == null || appId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        }
    }
}



