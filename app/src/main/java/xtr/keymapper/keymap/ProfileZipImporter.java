package xtr.keymapper.keymap;

import android.content.SharedPreferences;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import xtr.keymapper.structured.ProfileCodec;

/** Parse and validate the whole bounded archive before entering the checked storage transaction. */
public final class ProfileZipImporter {
    public static final int MAX_ENTRIES = 64;
    public static final int MAX_LINES_PER_ENTRY = 4096;
    public static final int MAX_ENTRY_BYTES = 2 * ProfileCodec.MAX_BYTES;
    // A maximal 1 MiB structured document expands to base64 on a single container line.
    public static final int MAX_LINE_BYTES = (4 * ProfileCodec.MAX_BYTES / 3) + 1024;
    public static final int MAX_TOTAL_BYTES = 16 * ProfileCodec.MAX_BYTES;
    public static final int MAX_ARCHIVE_BYTES = 20 * ProfileCodec.MAX_BYTES;
    private ProfileZipImporter() {}

    public static void importInto(InputStream input, File cache, SharedPreferences prefs) throws IOException {
        Map<String, Set<String>> parsed = read(input, cache);
        ProfileStorageTransactions.importNew(prefs, parsed);
    }

    public static Map<String, Set<String>> read(InputStream input, File cache) throws IOException {
        if (input == null) throw new IOException("Cannot open archive");
        File temporary = File.createTempFile("profile-import-", ".zip", cache);
        try {
            // ZipFile checks the central directory: ZipInputStream alone accepts missing/truncated tails.
            try (OutputStream out = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[8192]; int total = 0, count;
                while ((count = input.read(buffer, 0, Math.min(buffer.length, MAX_ARCHIVE_BYTES - total + 1))) != -1) {
                    total += count;
                    if (total > MAX_ARCHIVE_BYTES) throw new IOException("Archive exceeds compressed byte limit");
                    out.write(buffer, 0, count);
                }
            }
            Map<String, Set<String>> result = new LinkedHashMap<>();
            try (ZipFile zip = new ZipFile(temporary)) {
                if (zip.size() == 0 || zip.size() > MAX_ENTRIES) throw new IOException("Invalid ZIP entry count");
                List<? extends ZipEntry> entries = Collections.list(zip.entries());
                Set<String> names = new HashSet<>();
                long declared = 0;
                // Validate every name before decompressing any content. Never extract entry paths.
                for (ZipEntry entry : entries) {
                    ProfileStorageTransactions.validName(entry.getName());
                    if (entry.isDirectory() || !names.add(entry.getName())) throw new IOException("Duplicate or directory ZIP entry");
                    if (entry.getSize() < 0 || entry.getSize() > MAX_ENTRY_BYTES) throw new IOException("ZIP entry exceeds byte limit");
                    declared += entry.getSize();
                    if (declared > MAX_TOTAL_BYTES) throw new IOException("ZIP exceeds total decompressed byte limit");
                }
                int total = 0;
                for (ZipEntry entry : entries) {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    CRC32 crc = new CRC32(); int line = 0, lines = 1;
                    boolean lineStart = false, previousCR = false;
                    try (InputStream content = zip.getInputStream(entry)) {
                        byte[] buffer = new byte[8192]; int count;
                        while ((count = content.read(buffer, 0, Math.min(buffer.length,
                                Math.min(MAX_ENTRY_BYTES - bytes.size(), MAX_TOTAL_BYTES - total) + 1))) != -1) {
                            total += count;
                            if (bytes.size() + count > MAX_ENTRY_BYTES || total > MAX_TOTAL_BYTES)
                                throw new IOException("ZIP exceeds decompressed byte limit");
                            for (int i = 0; i < count; i++) {
                                // Count blank and unterminated lines too; CRLF is one separator.
                                if (lineStart && !(buffer[i] == '\n' && previousCR)
                                        && ++lines > MAX_LINES_PER_ENTRY)
                                    throw new IOException("ZIP entry exceeds line count limit");
                                lineStart = buffer[i] == '\n' || buffer[i] == '\r';
                                previousCR = buffer[i] == '\r';
                                if (buffer[i] == '\n') line = 0;
                                else if (++line > MAX_LINE_BYTES) throw new IOException("ZIP line exceeds byte limit");
                            }
                            crc.update(buffer, 0, count); bytes.write(buffer, 0, count);
                        }
                    }
                    if (bytes.size() != entry.getSize() || crc.getValue() != entry.getCrc())
                        throw new IOException("ZIP size or CRC mismatch");
                    String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
                    result.put(entry.getName(), ProfileConfiguration.validate(text));
                }
            }
            return result;
        } finally {
            if (!temporary.delete() && temporary.exists()) throw new IOException("Cannot remove temporary import archive");
        }
    }
}
