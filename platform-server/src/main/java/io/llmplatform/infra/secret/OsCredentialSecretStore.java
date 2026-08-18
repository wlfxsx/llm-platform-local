package io.llmplatform.infra.secret;

import com.sun.jna.*;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import io.llmplatform.common.error.PlatformException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 把密钥写入操作系统凭据库。
 *
 * <p>Windows 用 Credential Manager；macOS 用 Keychain（security）；Linux 用 Secret Service（secret-tool）。
 * 任一平台不可用时抛出明确错误，绝不回退到明文落库。
 */
@Component
@Primary
public class OsCredentialSecretStore implements SecretStore {

    private static final String SERVICE = "llm-platform";

    private static String target(String ref) {
        return SERVICE + "/" + ref;
    }

    private static void requireRef(String ref) {
        if (ref == null || ref.isBlank()) {
            throw new PlatformException("INVALID_REQUEST", "error.invalidRequest");
        }
    }

    private static int run(List<String> command, String stdin) {
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            process = builder.start();
            if (stdin != null) {
                process.getOutputStream().write(stdin.getBytes(StandardCharsets.UTF_8));
                process.getOutputStream().flush();
            }
            process.getOutputStream().close();
            process.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new PlatformException(
                        "SECRET_STORE_UNAVAILABLE", "error.secretStoreUnavailable");
            }
            return process.exitValue();
        } catch (PlatformException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PlatformException("SECRET_STORE_UNAVAILABLE", "error.secretStoreUnavailable");
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private static ProcessResult runCapture(List<String> command) {
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            process = builder.start();
            StringBuilder stdout = new StringBuilder();
            try (BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!stdout.isEmpty()) {
                        stdout.append('\n');
                    }
                    stdout.append(line);
                }
            }
            process.getErrorStream().transferTo(java.io.OutputStream.nullOutputStream());
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new ProcessResult(1, "");
            }
            return new ProcessResult(process.exitValue(), stdout.toString());
        } catch (Exception ex) {
            return new ProcessResult(1, "");
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    @Override
    public void put(String ref, String secret) {
        requireRef(ref);
        if (secret == null || secret.isBlank()) {
            throw new PlatformException("INVALID_REQUEST", "error.remoteApiKeyRequired");
        }
        if (OperatingSystem.isWindows()) {
            WindowsCred.put(target(ref), secret);
            return;
        }
        if (OperatingSystem.isMacOs()) {
            MacKeychain.put(target(ref), secret);
            return;
        }
        if (OperatingSystem.isLinux()) {
            LinuxSecretTool.put(target(ref), secret);
            return;
        }
        throw new PlatformException("SECRET_STORE_UNAVAILABLE", "error.secretStoreUnavailable");
    }

    @Override
    public String get(String ref) {
        requireRef(ref);
        if (OperatingSystem.isWindows()) {
            return WindowsCred.get(target(ref));
        }
        if (OperatingSystem.isMacOs()) {
            return MacKeychain.get(target(ref));
        }
        if (OperatingSystem.isLinux()) {
            return LinuxSecretTool.get(target(ref));
        }
        throw new PlatformException("SECRET_STORE_UNAVAILABLE", "error.secretStoreUnavailable");
    }

    @Override
    public void delete(String ref) {
        requireRef(ref);
        if (OperatingSystem.isWindows()) {
            WindowsCred.delete(target(ref));
            return;
        }
        if (OperatingSystem.isMacOs()) {
            MacKeychain.delete(target(ref));
            return;
        }
        if (OperatingSystem.isLinux()) {
            LinuxSecretTool.delete(target(ref));
            return;
        }
        throw new PlatformException("SECRET_STORE_UNAVAILABLE", "error.secretStoreUnavailable");
    }

    @Override
    public boolean exists(String ref) {
        try {
            String value = get(ref);
            return value != null && !value.isBlank();
        } catch (PlatformException ex) {
            return false;
        }
    }

    static final class OperatingSystem {
        private OperatingSystem() {}

        static boolean isWindows() {
            return System.getProperty("os.name", "").toLowerCase().contains("win");
        }

        static boolean isMacOs() {
            String name = System.getProperty("os.name", "").toLowerCase();
            return name.contains("mac") || name.contains("darwin");
        }

        static boolean isLinux() {
            return System.getProperty("os.name", "").toLowerCase().contains("linux");
        }
    }

    private static final class WindowsCred {
        private static final int CRED_TYPE_GENERIC = 1;
        private static final int CRED_PERSIST_LOCAL_MACHINE = 2;

        private WindowsCred() {}

        static void put(String target, String secret) {
            byte[] bytes = secret.getBytes(StandardCharsets.UTF_16LE);
            Memory blob = new Memory(bytes.length);
            blob.write(0, bytes, 0, bytes.length);
            CREDENTIAL cred = new CREDENTIAL();
            cred.Type = CRED_TYPE_GENERIC;
            cred.TargetName = new WString(target);
            cred.CredentialBlobSize = bytes.length;
            cred.CredentialBlob = blob;
            cred.Persist = CRED_PERSIST_LOCAL_MACHINE;
            cred.UserName = new WString(SERVICE);
            if (!Advapi32Cred.INSTANCE.CredWrite(cred, 0)) {
                throw new PlatformException(
                        "SECRET_STORE_UNAVAILABLE", "error.secretStoreUnavailable");
            }
        }

        static String get(String target) {
            PointerByReference pointer = new PointerByReference();
            if (!Advapi32Cred.INSTANCE.CredRead(target, CRED_TYPE_GENERIC, 0, pointer)) {
                return null;
            }
            try {
                CREDENTIAL cred = new CREDENTIAL(pointer.getValue());
                if (cred.CredentialBlob == null || cred.CredentialBlobSize <= 0) {
                    return null;
                }
                byte[] bytes = cred.CredentialBlob.getByteArray(0, cred.CredentialBlobSize);
                return new String(bytes, StandardCharsets.UTF_16LE);
            } finally {
                Advapi32Cred.INSTANCE.CredFree(pointer.getValue());
            }
        }

        static void delete(String target) {
            Advapi32Cred.INSTANCE.CredDelete(target, CRED_TYPE_GENERIC, 0);
        }

        public interface Advapi32Cred extends StdCallLibrary {
            Advapi32Cred INSTANCE =
                    Native.load("Advapi32", Advapi32Cred.class, W32APIOptions.UNICODE_OPTIONS);

            boolean CredWrite(CREDENTIAL credential, int flags);

            boolean CredRead(String targetName, int type, int flags, PointerByReference credential);

            boolean CredDelete(String targetName, int type, int flags);

            void CredFree(Pointer credential);
        }

        @Structure.FieldOrder({
            "Flags",
            "Type",
            "TargetName",
            "Comment",
            "LastWritten",
            "CredentialBlobSize",
            "CredentialBlob",
            "Persist",
            "AttributeCount",
            "Attributes",
            "TargetAlias",
            "UserName"
        })
        // CredWriteW/CredReadW 按宽字符解析字符串字段，必须用 WString；
        // 结构体里的 String 会被 JNA 按 ANSI 写入，导致凭据存到乱码目标名后再也读不回来。
        public static class CREDENTIAL extends Structure {
            public int Flags;
            public int Type;
            public WString TargetName;
            public WString Comment;
            public FILETIME LastWritten;
            public int CredentialBlobSize;
            public Pointer CredentialBlob;
            public int Persist;
            public int AttributeCount;
            public Pointer Attributes;
            public WString TargetAlias;
            public WString UserName;

            public CREDENTIAL() {}

            public CREDENTIAL(Pointer pointer) {
                super(pointer);
                read();
            }
        }

        @Structure.FieldOrder({"dwLowDateTime", "dwHighDateTime"})
        public static class FILETIME extends Structure {
            public int dwLowDateTime;
            public int dwHighDateTime;
        }
    }

    private static final class MacKeychain {
        private MacKeychain() {}

        static void put(String account, String secret) {
            delete(account);
            int code =
                    run(
                            List.of(
                                    "security",
                                    "add-generic-password",
                                    "-a",
                                    SERVICE,
                                    "-s",
                                    account,
                                    "-w",
                                    secret,
                                    "-U"),
                            null);
            if (code != 0) {
                throw new PlatformException(
                        "SECRET_STORE_UNAVAILABLE", "error.secretStoreUnavailable");
            }
        }

        static String get(String account) {
            ProcessResult result =
                    runCapture(
                            List.of(
                                    "security",
                                    "find-generic-password",
                                    "-a",
                                    SERVICE,
                                    "-s",
                                    account,
                                    "-w"));
            if (result.exitCode() != 0) {
                return null;
            }
            String value = result.stdout().trim();
            return value.isEmpty() ? null : value;
        }

        static void delete(String account) {
            run(List.of("security", "delete-generic-password", "-a", SERVICE, "-s", account), null);
        }
    }

    private static final class LinuxSecretTool {
        private LinuxSecretTool() {}

        static void put(String account, String secret) {
            int code =
                    run(
                            List.of(
                                    "secret-tool",
                                    "store",
                                    "--label",
                                    "LLM Platform",
                                    "service",
                                    SERVICE,
                                    "account",
                                    account),
                            secret);
            if (code != 0) {
                throw new PlatformException(
                        "SECRET_STORE_UNAVAILABLE", "error.secretStoreUnavailable");
            }
        }

        static String get(String account) {
            ProcessResult result =
                    runCapture(
                            List.of(
                                    "secret-tool",
                                    "lookup",
                                    "service",
                                    SERVICE,
                                    "account",
                                    account));
            if (result.exitCode() != 0) {
                return null;
            }
            String value = result.stdout();
            return value == null || value.isEmpty() ? null : value;
        }

        static void delete(String account) {
            run(List.of("secret-tool", "clear", "service", SERVICE, "account", account), null);
        }
    }

    private record ProcessResult(int exitCode, String stdout) {}
}
