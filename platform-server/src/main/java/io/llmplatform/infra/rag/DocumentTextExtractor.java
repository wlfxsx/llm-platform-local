package io.llmplatform.infra.rag;

import io.llmplatform.common.error.PlatformException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

/** 按扩展名抽取可读正文；Office/PDF 走 Tika，扫描件无文字层时失败。 */
@Component
public class DocumentTextExtractor {

    private static final Set<String> MARKDOWN = Set.of("md", "markdown");
    private static final Set<String> HTML = Set.of("html", "htm");
    private static final Set<String> TEXT =
            Set.of("txt", "csv", "log", "json", "xml", "yml", "yaml");
    private static final Set<String> CODE =
            Set.of(
                    "java", "cs", "py", "js", "ts", "tsx", "jsx", "go", "rs", "c", "cc", "cpp", "h",
                    "hpp", "kt", "swift", "sql", "sh", "ps1", "rb", "php");
    private static final Set<String> TIKA =
            Set.of("pdf", "docx", "doc", "rtf", "odt", "pptx", "xlsx");
    private static final int MAX_CHARS = 2_000_000;

    private final Tika tika = new Tika();

    public ExtractedDocument extract(Path source) {
        if (source == null || !Files.isRegularFile(source)) {
            throw new PlatformException("INVALID_REQUEST", "error.invalidRequest");
        }
        String fileName = source.getFileName().toString();
        String ext = extension(fileName);
        DocumentKind kind = kindOf(ext);
        String text;
        try {
            if (HTML.contains(ext)) {
                text = stripHtml(readText(source));
            } else if (TIKA.contains(ext) || looksBinary(source, ext)) {
                text = parseWithTika(source);
            } else {
                text = readText(source);
            }
        } catch (PlatformException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new PlatformException("FILE_COPY_FAILED", "error.fileCopyFailed");
        }
        text = normalize(text);
        if (text.isBlank()) {
            throw new PlatformException("RAG_EMPTY_TEXT", "error.ragNoExtractableText");
        }
        if (text.length() > MAX_CHARS) {
            text = text.substring(0, MAX_CHARS);
        }
        return new ExtractedDocument(text, kind, fileName);
    }

    private String parseWithTika(Path source) throws IOException {
        try (InputStream input = Files.newInputStream(source)) {
            String parsed = tika.parseToString(input);
            return parsed == null ? "" : parsed;
        } catch (Exception ex) {
            if (ex instanceof IOException io) {
                throw io;
            }
            throw new PlatformException("RAG_EMPTY_TEXT", "error.ragNoExtractableText");
        }
    }

    private String readText(Path source) throws IOException {
        byte[] bytes = Files.readAllBytes(source);
        try {
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return new String(bytes, Charset.defaultCharset());
        }
    }

    private boolean looksBinary(Path source, String ext) throws IOException {
        if (!TEXT.contains(ext)
                && !MARKDOWN.contains(ext)
                && !CODE.contains(ext)
                && !HTML.contains(ext)) {
            byte[] probe = readProbe(source);
            return probe.length > 0 && !isMostlyText(probe);
        }
        return false;
    }

    private static byte[] readProbe(Path source) throws IOException {
        try (InputStream input = Files.newInputStream(source)) {
            return input.readNBytes(2048);
        }
    }

    private static boolean isMostlyText(byte[] bytes) {
        int n = Math.min(bytes.length, 2048);
        if (n == 0) {
            return true;
        }
        int control = 0;
        for (int i = 0; i < n; i++) {
            int b = bytes[i] & 0xff;
            if (b == 0) {
                return false;
            }
            if (b < 9 || (b > 13 && b < 32)) {
                control++;
            }
        }
        return control * 20 < n;
    }

    static String stripHtml(String html) {
        String withoutScripts =
                html.replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                        .replaceAll("(?is)<style[^>]*>.*?</style>", " ");
        String text = withoutScripts.replaceAll("(?is)<[^>]+>", " ");
        return text.replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"");
    }

    static String normalize(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    static DocumentKind kindOf(String ext) {
        if (MARKDOWN.contains(ext)) {
            return DocumentKind.MARKDOWN;
        }
        if (CODE.contains(ext)) {
            return DocumentKind.CODE;
        }
        return DocumentKind.PLAIN;
    }

    private static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
