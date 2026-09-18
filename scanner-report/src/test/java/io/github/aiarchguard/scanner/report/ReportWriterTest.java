package io.github.aiarchguard.scanner.report;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportWriterTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void atomicallyCreatesAndReplacesARegularReport() throws Exception {
        Path output = temporaryDirectory.resolve("report.json");
        ReportWriter writer = new ReportWriter();

        writer.write(output, "first".getBytes());
        writer.write(output, "second".getBytes());

        assertArrayEquals("second".getBytes(), Files.readAllBytes(output));
    }

    @Test
    void rejectsMissingParentAndNonRegularOutput() throws Exception {
        ReportWriter writer = new ReportWriter();

        assertThrows(
                IOException.class,
                () -> writer.write(temporaryDirectory.resolve("missing/report.json"), new byte[] {1}));
        assertThrows(IOException.class, () -> writer.write(temporaryDirectory, new byte[] {1}));
    }

    @Test
    void rejectsSymbolicLinkOutputWhenSupported() throws Exception {
        Path target = temporaryDirectory.resolve("target.json");
        Files.writeString(target, "unchanged");
        Path link = temporaryDirectory.resolve("report.json");
        try {
            Files.createSymbolicLink(link, target.getFileName());
        } catch (UnsupportedOperationException | IOException exception) {
            return;
        }

        assertThrows(IOException.class, () -> new ReportWriter().write(link, new byte[] {1}));
        assertArrayEquals("unchanged".getBytes(), Files.readAllBytes(target));
    }
}
