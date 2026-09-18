package io.github.aiarchguard.scanner.report;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public final class ReportWriter {

    public void write(Path output, byte[] report) throws IOException {
        if (output == null || report == null) {
            throw new IllegalArgumentException("output and report must not be null");
        }
        Path target = output.toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent == null || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("report output parent must be an existing directory");
        }
        if (Files.isSymbolicLink(target)) {
            throw new IOException("report output must not be a symbolic link");
        }
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("report output must be a regular file");
        }

        Path temporary = Files.createTempFile(parent, ".archguard-report-", ".tmp");
        boolean moved = false;
        try {
            try (FileChannel channel = FileChannel.open(
                    temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(report);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
                moved = true;
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("atomic report replacement is not supported", exception);
            }
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }
}
