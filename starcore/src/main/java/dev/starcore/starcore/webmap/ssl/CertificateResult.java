package dev.starcore.starcore.webmap.ssl;

/**
 * 证书操作结果
 */
public record CertificateResult(
    boolean success,
    String message,
    CertificateInfo certificateInfo
) {
    @Override
    public String toString() {
        return String.format("CertificateResult[success=%s, message=%s, domain=%s]",
            success, message, certificateInfo != null ? certificateInfo.domain() : "null");
    }
}
