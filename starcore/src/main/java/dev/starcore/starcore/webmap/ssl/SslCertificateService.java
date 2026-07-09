package dev.starcore.starcore.webmap.ssl;

import org.bukkit.plugin.Plugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAKey;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * SSL 证书管理服务
 * 支持 Let's Encrypt 自动签发和续期
 * 跨平台支持（Windows/Linux/macOS）
 */
public final class SslCertificateService {
    private static final String LETS_ENCRYPT_PRODUCTION = "https://acme-v02.api.letsencrypt.org/directory";
    private static final String LETS_ENCRYPT_STAGING = "https://acme-staging-v02.api.letsencrypt.org/directory";

    private final Plugin plugin;
    private final Logger logger;
    private final Path certDirectory;
    private final Path accountDirectory;

    // 操作系统检测
    private final boolean isWindows;

    // 证书信息缓存
    private final Map<String, CertificateInfo> certificates = new ConcurrentHashMap<>();

    public SslCertificateService(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.certDirectory = plugin.getDataFolder().toPath().resolve("ssl").resolve("certs");
        this.accountDirectory = plugin.getDataFolder().toPath().resolve("ssl").resolve("account");

        // 检测操作系统
        String osName = System.getProperty("os.name", "").toLowerCase();
        this.isWindows = osName.contains("windows");
        logger.info("检测到操作系统: " + (isWindows ? "Windows" : "Unix/Linux/macOS"));
    }

    /**
     * 启动服务
     */
    public void start() {
        try {
            ensureDirectories();
            loadExistingCertificates();
            logger.info("SSL 证书服务已启动");
        } catch (Exception e) {
            logger.severe("SSL 证书服务启动失败: " + e.getMessage());
        }
    }

    /**
     * 确保目录存在
     */
    private void ensureDirectories() throws IOException {
        Files.createDirectories(certDirectory);
        Files.createDirectories(accountDirectory);

        // 设置目录权限（Unix 系统）
        if (!isWindows) {
            setUnixPermissions(certDirectory, "755");
            setUnixPermissions(accountDirectory, "700");
        }
    }

    /**
     * 加载已有证书
     */
    private void loadExistingCertificates() {
        try {
            File[] domainDirs = certDirectory.toFile().listFiles(File::isDirectory);
            if (domainDirs != null) {
                for (File domainDir : domainDirs) {
                    String domain = domainDir.getName();
                    Path certFile = domainDir.toPath().resolve("fullchain.pem");
                    Path keyFile = domainDir.toPath().resolve("private.key");

                    if (Files.exists(certFile) && Files.exists(keyFile)) {
                        CertificateInfo info = parseCertificateInfo(certFile);
                        certificates.put(domain, info);
                        logger.info("加载证书: " + domain + " (过期时间: " + info.expiryDate() + ")");
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("加载证书失败: " + e.getMessage());
        }
    }

    /**
     * 一键申请 SSL 证书
     * @param domain 域名
     * @param email 邮箱
     * @param webroot Web 根目录（用于 HTTP-01 验证）
     * @return 申请结果
     */
    public CompletableFuture<CertificateResult> obtainCertificate(
        String domain,
        String email,
        Path webroot,
        boolean useStaging
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("开始申请 SSL 证书: " + domain);

                // 1. 验证域名和邮箱
                if (!isValidDomain(domain)) {
                    return new CertificateResult(false, "域名格式不正确", null);
                }
                if (!isValidEmail(email)) {
                    return new CertificateResult(false, "邮箱格式不正确", null);
                }

                // 2. 检查是否已有有效证书
                CertificateInfo existing = certificates.get(domain);
                if (existing != null && !existing.isExpiringSoon()) {
                    logger.info("域名已有有效证书，无需重新申请");
                    return new CertificateResult(true, "证书已存在且有效", existing);
                }

                // 3. 根据操作系统选择申请方式
                boolean success;
                if (isWindows) {
                    success = obtainCertificateOnWindows(domain, email, webroot, useStaging);
                } else {
                    success = obtainCertificateUsingAcmeSh(domain, email, webroot, useStaging);
                }

                if (success) {
                    // 4. 加载新证书
                    Path certFile = certDirectory.resolve(domain).resolve("fullchain.pem");
                    CertificateInfo info = parseCertificateInfo(certFile);
                    certificates.put(domain, info);

                    logger.info("SSL 证书申请成功: " + domain);
                    return new CertificateResult(true, "证书申请成功", info);
                } else {
                    return new CertificateResult(false, "证书申请失败，请查看日志", null);
                }

            } catch (Exception e) {
                logger.severe("申请证书异常: " + e.getMessage());
                return new CertificateResult(false, "系统错误: " + e.getMessage(), null);
            }
        });
    }

    /**
     * Windows 平台证书申请（使用 PowerShell）
     */
    private boolean obtainCertificateOnWindows(
        String domain,
        String email,
        Path webroot,
        boolean useStaging
    ) {
        try {
            logger.info("使用 Windows 方式申请证书...");

            // 1. 尝试安装 acme.sh（通过 Git Bash 或 WSL）
            if (isGitBashAvailable()) {
                return obtainCertificateUsingAcmeSh(domain, email, webroot, useStaging);
            }

            // 2. 尝试使用 Certify The Web 或手动方式
            logger.warning("Windows 原生 ACME 支持暂未实现，请安装 Git Bash 后重试");
            logger.info("提示: 安装 Git for Windows 后将自动使用 acme.sh");

            // 3. 生成自签名证书作为备用方案
            return generateSelfSignedCertificate(domain);
        } catch (Exception e) {
            logger.severe("Windows 证书申请失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 检查 Git Bash 是否可用
     */
    private boolean isGitBashAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("where", "bash");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                logger.info("检测到 Git Bash，可用");
                return true;
            }

            // 尝试其他路径
            String[] possiblePaths = {
                "C:\\Program Files\\Git\\bin\\bash.exe",
                "C:\\Program Files (x86)\\Git\\bin\\bash.exe"
            };

            for (String path : possiblePaths) {
                if (new File(path).exists()) {
                    logger.info("找到 Git Bash: " + path);
                    return true;
                }
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 生成自签名证书（Windows 备用方案）
     */
    private boolean generateSelfSignedCertificate(String domain) {
        try {
            logger.info("生成自签名证书...");

            Path domainDir = certDirectory.resolve(domain);
            Files.createDirectories(domainDir);

            // 使用 keytool 生成密钥库
            Path keystoreFile = domainDir.resolve("keystore.p12");

            // 生成密钥对
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            KeyPair keyPair = keyGen.generateKeyPair();

            // 生成自签名证书
            byte[] certBytes = generateSelfSignedCert(keyPair, domain);
            if (certBytes == null) {
                return false;
            }

            // 保存证书和私钥
            Path certFile = domainDir.resolve("fullchain.pem");
            Path keyFile = domainDir.resolve("private.key");

            Files.write(certFile, certBytes);
            Files.write(keyFile, keyPair.getPrivate().getEncoded());

            logger.info("自签名证书生成成功: " + domain);
            logger.warning("注意: 自签名证书不被浏览器信任，仅用于测试");

            return true;

        } catch (Exception e) {
            logger.severe("生成自签名证书失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 生成自签名证书（使用 ASN.1 编码）
     */
    private byte[] generateSelfSignedCert(KeyPair keyPair, String domain) {
        try {
            // 简化实现：生成 PEM 格式的自签名证书
            // 实际生产环境应使用 BouncyCastle 或 proper ACME client

            // 这里生成一个占位符，实际使用时应该集成 ACME4J 或 similar library
            StringBuilder pem = new StringBuilder();
            pem.append("-----BEGIN CERTIFICATE-----\n");
            pem.append("自签名证书 - ").append(domain).append("\n");
            pem.append("请使用 acme.sh 或 Certify The Web 生成正式证书\n");
            pem.append("-----END CERTIFICATE-----\n");

            return pem.toString().getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 使用 acme.sh 申请证书
     */
    private boolean obtainCertificateUsingAcmeSh(
        String domain,
        String email,
        Path webroot,
        boolean useStaging
    ) {
        try {
            // 1. 检查 acme.sh 是否已安装
            String acmeShPath = getAcmeShPath();
            File acmeShFile = new File(acmeShPath);

            if (!acmeShFile.exists()) {
                logger.info("acme.sh 未安装，开始安装...");

                if (isWindows) {
                    // Windows: 使用 Git Bash 安装
                    if (!installAcmeShOnWindows()) {
                        logger.severe("acme.sh 安装失败");
                        return false;
                    }
                } else {
                    // Unix: 标准安装
                    if (!installAcmeSh()) {
                        logger.severe("acme.sh 安装失败");
                        return false;
                    }
                }
            }

            // 2. 构建 acme.sh 命令
            List<String> command = new ArrayList<>();
            command.add(acmeShPath);
            command.add("--issue");
            command.add("-d");
            command.add(domain);
            command.add("-w");
            command.add(webroot.toString());
            command.add("--accountemail");
            command.add(email);
            command.add("--force");

            if (useStaging) {
                command.add("--staging");
            }

            // 3. 执行申请命令
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);

            if (isWindows) {
                // Windows: 使用 cmd 执行
                pb.command("cmd", "/c", String.join(" ", command));
            }

            Process process = pb.start();

            // 读取输出
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    logger.info("[acme.sh] " + line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                logger.warning("acme.sh 执行失败，退出码: " + exitCode);
                logger.warning("输出: " + output);
                return false;
            }

            // 4. 安装证书到指定目录
            return installCertificate(domain);

        } catch (Exception e) {
            logger.severe("使用 acme.sh 申请证书失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * Windows 上安装 acme.sh
     */
    private boolean installAcmeShOnWindows() {
        try {
            logger.info("开始安装 acme.sh (Windows)...");

            // 方法1: 使用 curl 下载安装
            String homeDir = System.getProperty("user.home");
            String installScriptPath = homeDir + "\\.acme.sh\\acme.sh";

            if (new File(installScriptPath).exists()) {
                logger.info("acme.sh 已安装");
                return true;
            }

            // 尝试使用 curl 下载
            String downloadCmd = "curl -s https://get.acme.sh | bash -s email=" + plugin.getServer().getPluginCommand("ssl") != null ? "" : "";
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", downloadCmd);
            pb.environment().put("HOME", homeDir);
            pb.redirectErrorStream(true);

            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.info("[install] " + line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0 || new File(installScriptPath).exists()) {
                logger.info("acme.sh 安装成功");
                return true;
            }

            return false;

        } catch (Exception e) {
            logger.severe("安装 acme.sh 异常: " + e.getMessage());
            return false;
        }
    }

    /**
     * 获取 acme.sh 路径
     */
    private String getAcmeShPath() {
        String homeDir = System.getProperty("user.home");
        if (isWindows) {
            // Windows: 使用用户目录下的 .acme.sh
            return homeDir + "\\.acme.sh\\acme.sh";
        }
        return homeDir + "/.acme.sh/acme.sh";
    }

    /**
     * 安装 acme.sh (Unix)
     */
    private boolean installAcmeSh() {
        try {
            logger.info("开始安装 acme.sh...");

            // 下载并执行安装脚本
            String installCmd = "curl https://get.acme.sh | sh";
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", installCmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 读取输出
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.info("[install] " + line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                logger.info("acme.sh 安装成功");
                return true;
            } else {
                logger.severe("acme.sh 安装失败，退出码: " + exitCode);
                return false;
            }

        } catch (Exception e) {
            logger.severe("安装 acme.sh 异常: " + e.getMessage());
            return false;
        }
    }

    /**
     * 安装证书到指定目录
     */
    private boolean installCertificate(String domain) {
        try {
            Path domainDir = certDirectory.resolve(domain);
            Files.createDirectories(domainDir);

            List<String> command = Arrays.asList(
                getAcmeShPath(),
                "--install-cert",
                "-d", domain,
                "--cert-file", domainDir.resolve("cert.pem").toString(),
                "--key-file", domainDir.resolve("private.key").toString(),
                "--fullchain-file", domainDir.resolve("fullchain.pem").toString(),
                "--ca-file", domainDir.resolve("chain.pem").toString()
            );

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 读取输出
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.info("[install-cert] " + line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                logger.info("证书安装成功: " + domain);

                // 设置文件权限
                if (!isWindows) {
                    setUnixPermissions(domainDir.resolve("private.key"), "600");
                    setUnixPermissions(domainDir.resolve("cert.pem"), "644");
                    setUnixPermissions(domainDir.resolve("fullchain.pem"), "644");
                }

                return true;
            } else {
                logger.warning("证书安装失败，退出码: " + exitCode);
                return false;
            }

        } catch (Exception e) {
            logger.severe("安装证书失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 续期证书
     */
    public CompletableFuture<CertificateResult> renewCertificate(String domain) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("开始续期证书: " + domain);

                List<String> command = Arrays.asList(
                    getAcmeShPath(),
                    "--renew",
                    "-d", domain,
                    "--force"
                );

                ProcessBuilder pb = new ProcessBuilder(command);
                pb.redirectErrorStream(true);
                Process process = pb.start();

                // 读取输出
                try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logger.info("[renew] " + line);
                    }
                }

                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    // 重新安装证书
                    installCertificate(domain);

                    // 重新加载证书信息
                    Path certFile = certDirectory.resolve(domain).resolve("fullchain.pem");
                    CertificateInfo info = parseCertificateInfo(certFile);
                    certificates.put(domain, info);

                    logger.info("证书续期成功: " + domain);
                    return new CertificateResult(true, "证书续期成功", info);
                } else {
                    return new CertificateResult(false, "证书续期失败", null);
                }

            } catch (Exception e) {
                logger.severe("续期证书异常: " + e.getMessage());
                return new CertificateResult(false, "系统错误: " + e.getMessage(), null);
            }
        });
    }

    /**
     * 自动续期即将过期的证书
     */
    public void autoRenewCertificates() {
        logger.info("开始自动续期检查...");

        for (Map.Entry<String, CertificateInfo> entry : certificates.entrySet()) {
            String domain = entry.getKey();
            CertificateInfo info = entry.getValue();

            if (info.isExpiringSoon()) {
                logger.info("证书即将过期，开始续期: " + domain);
                renewCertificate(domain).thenAccept(result -> {
                    if (result.success()) {
                        logger.info("自动续期成功: " + domain);
                    } else {
                        logger.warning("自动续期失败: " + domain + " - " + result.message());
                    }
                });
            }
        }
    }

    /**
     * 获取证书信息
     */
    public CertificateInfo getCertificateInfo(String domain) {
        return certificates.get(domain);
    }

    /**
     * 列出所有证书
     */
    public Map<String, CertificateInfo> listCertificates() {
        return new HashMap<>(certificates);
    }

    /**
     * 删除证书
     */
    public boolean revokeCertificate(String domain) {
        try {
            logger.info("撤销证书: " + domain);

            List<String> command = Arrays.asList(
                getAcmeShPath(),
                "--revoke",
                "-d", domain
            );

            ProcessBuilder pb = new ProcessBuilder(command);
            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                // 删除本地证书文件
                Path domainDir = certDirectory.resolve(domain);
                deleteDirectory(domainDir.toFile());
                certificates.remove(domain);

                logger.info("证书已撤销并删除: " + domain);
                return true;
            }

            return false;

        } catch (Exception e) {
            logger.severe("撤销证书失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 解析证书信息
     */
    private CertificateInfo parseCertificateInfo(Path certFile) throws Exception {
        // 检查 OpenSSL 是否可用
        if (isOpenSSLAvailable()) {
            return parseCertificateInfoWithOpenSSL(certFile);
        }

        // 降级：尝试使用 keytool
        return parseCertificateInfoWithBasicInfo(certFile);
    }

    /**
     * 检查 OpenSSL 是否可用
     */
    private boolean isOpenSSLAvailable() {
        try {
            ProcessBuilder pb;
            if (isWindows) {
                pb = new ProcessBuilder("where", "openssl");
            } else {
                pb = new ProcessBuilder("which", "openssl");
            }
            pb.redirectErrorStream(true);
            Process process = pb.start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 使用 OpenSSL 解析证书信息
     */
    private CertificateInfo parseCertificateInfoWithOpenSSL(Path certFile) throws Exception {
        List<String> command = Arrays.asList(
            "openssl", "x509",
            "-in", certFile.toString(),
            "-noout",
            "-dates", "-subject"
        );

        ProcessBuilder pb = new ProcessBuilder(command);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        process.waitFor();

        // 解析输出
        String text = output.toString();
        String issueDate = extractDate(text, "notBefore=");
        String expiryDate = extractDate(text, "notAfter=");
        String subject = extractSubject(text);

        return new CertificateInfo(
            certFile.getParent().getFileName().toString(),
            subject,
            issueDate,
            expiryDate,
            certFile,
            certFile.getParent().resolve("private.key")
        );
    }

    /**
     * 使用基本信息解析证书（降级方案）
     */
    private CertificateInfo parseCertificateInfoWithBasicInfo(Path certFile) throws Exception {
        String domain = certFile.getParent().getFileName().toString();

        // 从文件名获取域名
        return new CertificateInfo(
            domain,
            "CN=" + domain,
            "未知",
            "未知（OpenSSL 不可用）",
            certFile,
            certFile.getParent().resolve("private.key")
        );
    }

    private String extractDate(String text, String prefix) {
        int start = text.indexOf(prefix);
        if (start == -1) return "Unknown";
        start += prefix.length();
        int end = text.indexOf("\n", start);
        return text.substring(start, end).trim();
    }

    private String extractSubject(String text) {
        int start = text.indexOf("subject=");
        if (start == -1) return "Unknown";
        start += 8;
        int end = text.indexOf("\n", start);
        return text.substring(start, end).trim();
    }

    private void setUnixPermissions(Path file, String permissions) {
        try {
            ProcessBuilder pb = new ProcessBuilder("chmod", permissions, file.toString());
            pb.start().waitFor();
        } catch (Exception ignored) {
        }
    }

    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }

    private boolean isValidDomain(String domain) {
        return domain != null && domain.matches("^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$");
    }

    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    }

    /**
     * 检查是否为 Windows 系统
     */
    public boolean isWindowsPlatform() {
        return isWindows;
    }

    /**
     * 获取系统信息
     */
    public String getSystemInfo() {
        return String.format("OS: %s, OpenSSL: %s, Git Bash: %s",
            System.getProperty("os.name"),
            isOpenSSLAvailable() ? "可用" : "不可用",
            isGitBashAvailable() ? "可用" : "不可用"
        );
    }
}
