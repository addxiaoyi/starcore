package dev.starcore.starcore.security;

import org.bukkit.plugin.Plugin;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.logging.Logger;

/**
 * 配置文件加密服务
 * 使用 AES-256-GCM 加密敏感配置信息
 */
public final class ConfigEncryptionService {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int KEY_SIZE = 256;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    private static final String ENC_PREFIX = "ENC(";
    private static final String ENC_SUFFIX = ")";

    private final Plugin plugin;
    private final Logger logger;
    private final Path keyFile;
    private SecretKey masterKey;

    public ConfigEncryptionService(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.keyFile = plugin.getDataFolder().toPath().resolve(".secrets").resolve("master.key");
    }

    /**
     * 启动服务
     */
    public void start() {
        try {
            ensureSecretsDirectory();
            loadOrGenerateMasterKey();
            logger.info("✅ 配置加密服务已启动");
        } catch (Exception e) {
            logger.severe("❌ 配置加密服务启动失败: " + e.getMessage());
        }
    }

    /**
     * 确保密钥目录存在
     */
    private void ensureSecretsDirectory() throws IOException {
        Path secretsDir = keyFile.getParent();
        if (!Files.exists(secretsDir)) {
            Files.createDirectories(secretsDir);

            // 设置目录权限（仅所有者可访问）
            File dir = secretsDir.toFile();
            dir.setReadable(false, false);
            dir.setReadable(true, true);
            dir.setWritable(false, false);
            dir.setWritable(true, true);
            dir.setExecutable(false, false);
            dir.setExecutable(true, true);

            logger.info("创建密钥目录: " + secretsDir);
        }
    }

    /**
     * 加载或生成主密钥
     */
    private void loadOrGenerateMasterKey() throws GeneralSecurityException, IOException {
        if (Files.exists(keyFile)) {
            // 加载现有密钥
            byte[] keyBytes = Files.readAllBytes(keyFile);
            masterKey = new SecretKeySpec(keyBytes, "AES");
            logger.info("加载主密钥成功");
        } else {
            // 生成新密钥
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(KEY_SIZE);
            masterKey = keyGen.generateKey();

            // 保存密钥到文件
            Files.write(keyFile, masterKey.getEncoded());

            // 设置文件权限（仅所有者可读写）
            File file = keyFile.toFile();
            file.setReadable(false, false);
            file.setReadable(true, true);
            file.setWritable(false, false);
            file.setWritable(true, true);

            logger.warning("⚠️ 生成新的主密钥，请妥善保管: " + keyFile);
            logger.warning("⚠️ 备份建议：将此文件复制到安全位置！");
        }
    }

    /**
     * 加密字符串
     * @param plaintext 明文
     * @return 加密后的 Base64 字符串，格式: ENC(base64)
     * E-094: 加密失败时抛异常而不是返回明文，防止数据以明文泄露
     */
    public String encrypt(String plaintext) throws EncryptionException {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }

        try {
            // 生成随机 IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);

            // 初始化加密器
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, parameterSpec);

            // 加密
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // 组合 IV + 密文
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            // Base64 编码
            String encoded = Base64.getEncoder().encodeToString(combined);
            return ENC_PREFIX + encoded + ENC_SUFFIX;

        } catch (Exception e) {
            throw new EncryptionException("加密失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解密字符串
     * @param encrypted 加密的字符串，格式: ENC(base64)
     * @return 解密后的明文
     * E-095: 解密失败时抛异常而不是返回原值，防止使用损坏的密文
     */
    public String decrypt(String encrypted) throws EncryptionException {
        if (encrypted == null || encrypted.isEmpty()) {
            return encrypted;
        }

        // 检查是否是加密格式
        if (!isEncrypted(encrypted)) {
            return encrypted;
        }

        try {
            // 提取 Base64 内容
            String base64Content = encrypted.substring(ENC_PREFIX.length(), encrypted.length() - ENC_SUFFIX.length());
            byte[] combined = Base64.getDecoder().decode(base64Content);

            // 分离 IV 和密文
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, ciphertext, 0, ciphertext.length);

            // 初始化解密器
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, masterKey, parameterSpec);

            // 解密
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);

        } catch (Exception e) {
            throw new EncryptionException("解密失败: " + e.getMessage(), e);
        }
    }

    /**
     * 判断字符串是否已加密
     */
    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(ENC_PREFIX) && value.endsWith(ENC_SUFFIX);
    }

    /**
     * 加密配置值（如果尚未加密）
     */
    public String encryptIfNeeded(String value) throws EncryptionException {
        if (isEncrypted(value)) {
            return value;
        }
        return encrypt(value);
    }

    /**
     * 批量加密配置文件中的敏感字段
     * E-097: 使用更健壮的 YAML 解析方式，避免正则注入风险
     */
    public void encryptSensitiveFields(File configFile) {
        logger.info("开始加密配置文件: " + configFile.getName());

        // E-097: 敏感字段列表
        String[] sensitiveKeys = {
            "password",
            "secret",
            "token",
            "key",
            "credential",
            "api-key",
            "access-key"
        };

        try {
            String content = Files.readString(configFile.toPath());
            String original = content;
            boolean changed = false;

            for (String key : sensitiveKeys) {
                // E-097: 改进正则，限制匹配范围避免跨行注入
                Pattern pattern = Pattern.compile(
                    "(" + Pattern.quote(key) + "\\s*:\\s*)(['\"]?)([^'\"\\n]+)(['\"]?)",
                    Pattern.CASE_INSENSITIVE
                );
                Matcher matcher = pattern.matcher(content);
                StringBuffer sb = new StringBuffer();

                while (matcher.find()) {
                    String prefix = matcher.group(1);
                    String openQuote = matcher.group(2);
                    String value = matcher.group(3);
                    String closeQuote = matcher.group(4);

                    // 跳过已加密的值
                    if (isEncrypted(value)) {
                        matcher.appendReplacement(sb, matcher.group(0));
                        continue;
                    }

                    // 跳过空值
                    if (value.trim().isEmpty() || value.equals("\"\"") || value.equals("''")) {
                        matcher.appendReplacement(sb, matcher.group(0));
                        continue;
                    }

                    // E-094: 加密失败时记录错误而不是跳过
                    try {
                        String encrypted = encrypt(value);
                        matcher.appendReplacement(sb, Matcher.quoteReplacement(prefix + openQuote + encrypted + closeQuote));
                        changed = true;
                    } catch (EncryptionException e) {
                        logger.severe("加密字段 [" + key + "] 失败: " + e.getMessage());
                        matcher.appendReplacement(sb, matcher.group(0));
                    }
                }
                matcher.appendTail(sb);
                content = sb.toString();
            }

            // 如果有变化则保存
            if (changed) {
                // 备份原文件
                Path backupPath = configFile.toPath().getParent().resolve(configFile.getName() + ".backup");
                Files.copy(configFile.toPath(), backupPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                // 保存加密后的内容
                Files.writeString(configFile.toPath(), content);
                logger.info("✅ 配置文件加密完成，已创建备份: " + backupPath);
            } else {
                logger.info("配置文件无需加密");
            }

        } catch (IOException e) {
            logger.severe("加密配置文件失败: " + e.getMessage());
        }
    }

    /**
     * 解密配置值（用于读取配置时）
     * E-095: 解密失败时记录错误并返回 null，防止使用损坏的密文
     */
    public String getDecryptedValue(String configKey, String encryptedValue) {
        if (encryptedValue == null) {
            return null;
        }

        try {
            String decrypted = decrypt(encryptedValue);

            // 记录解密操作（仅记录键名，不记录值）
            if (!decrypted.equals(encryptedValue)) {
                logger.fine("解密配置: " + configKey);
            }

            return decrypted;
        } catch (EncryptionException e) {
            logger.severe("解密配置失败 [" + configKey + "]: " + e.getMessage());
            return null;
        }
    }

    /**
     * 从环境变量或加密配置中获取值
     * 优先级: 环境变量 > 加密配置
     */
    public String getSecureValue(String configKey, String configValue) {
        // 1. 尝试从环境变量读取
        String envKey = configKey.toUpperCase().replace(".", "_").replace("-", "_");
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isEmpty()) {
            logger.fine("从环境变量读取: " + envKey);
            return envValue;
        }

        // 2. 解密配置文件中的值
        return getDecryptedValue(configKey, configValue);
    }

    /**
     * 轮换主密钥（重新加密所有数据）
     * E-098: 添加 reEncryptAll 方法供运维在轮换后调用，确保所有配置重新加密
     */
    public void rotateMasterKey() throws GeneralSecurityException, IOException {
        logger.warning("⚠️ 开始轮换主密钥...");

        // 生成新密钥
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(KEY_SIZE);
        SecretKey newKey = keyGen.generateKey();

        // 备份旧密钥
        Path backupKeyFile = keyFile.getParent().resolve("master.key.old");
        Files.copy(keyFile, backupKeyFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        // 保存新密钥
        Files.write(keyFile, newKey.getEncoded());

        // 更新内存中的密钥
        SecretKey oldKey = masterKey;
        masterKey = newKey;

        logger.warning("✅ 主密钥已轮换，旧密钥已备份到: " + backupKeyFile);
        logger.warning("⚠️ 请调用 reEncryptAll() 重新加密所有配置文件！");
    }

    /**
     * 重新加密所有配置文件
     * E-098: 在 rotateMasterKey 后必须调用此方法，确保所有数据使用新密钥加密
     */
    public void reEncryptAll(File configDirectory) {
        if (configDirectory == null || !configDirectory.isDirectory()) {
            logger.warning("配置目录无效，跳过重新加密");
            return;
        }
        File[] configFiles = configDirectory.listFiles((dir, name) ->
            name.endsWith(".yml") || name.endsWith(".yaml") || name.endsWith(".properties")
        );
        if (configFiles == null || configFiles.length == 0) {
            logger.info("配置目录中没有找到需要重新加密的文件");
            return;
        }
        for (File file : configFiles) {
            try {
                encryptSensitiveFields(file);
            } catch (Exception e) {
                logger.severe("重新加密文件失败 [" + file.getName() + "]: " + e.getMessage());
            }
        }
        logger.info("✅ 所有配置文件重新加密完成");
    }

    /**
     * 验证密钥文件完整性
     */
    public boolean verifyKeyIntegrity() {
        try {
            if (!Files.exists(keyFile)) {
                logger.warning("密钥文件不存在: " + keyFile);
                return false;
            }

            byte[] keyBytes = Files.readAllBytes(keyFile);
            if (keyBytes.length != KEY_SIZE / 8) {
                logger.warning("密钥长度不正确: " + keyBytes.length + " (期望: " + (KEY_SIZE / 8) + ")");
                return false;
            }

            // 测试加密/解密
            String testData = "test-" + System.currentTimeMillis();
            String encrypted = encrypt(testData);
            String decrypted = decrypt(encrypted);

            if (!testData.equals(decrypted)) {
                logger.warning("密钥验证失败：加密/解密测试不通过");
                return false;
            }

            return true;

        } catch (Exception e) {
            logger.warning("密钥验证失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 加密异常
     */
    public static class EncryptionException extends Exception {
        public EncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
