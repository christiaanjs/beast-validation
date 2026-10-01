package beast.util;

import org.junit.Test;

import beastfx.app.tools.LogAnalyser;
import beastvalidation.util.BoolLogAnalyser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.*;

public class BoolLogAnalyserTest {

    private static LogAnalyser read(String content) throws IOException {
        File f = File.createTempFile("boollogtest-", ".log");
        f.deleteOnExit();
        Files.writeString(f.toPath(), content);
        return new BoolLogAnalyser(f.getAbsolutePath(), 0, true, false);
    }

    @Test
    public void testOneZeroUnchanged() throws IOException {
        LogAnalyser trace = read("Sample\tind\n0\t1\n1000\t0\n2000\t1\n3000\t0\n");
        assertArrayEquals(new Double[] {1.0, 0.0, 1.0, 0.0}, trace.getTrace("ind"));
    }

    @Test
    public void testTrueIsOneFalseIsZero() throws IOException {
        // starts with true, as BEAST 3 logs do
        LogAnalyser trace = read("Sample\tind\n0\ttrue\n1000\tfalse\n2000\ttrue\n3000\tfalse\n");
        assertArrayEquals(new Double[] {1.0, 0.0, 1.0, 0.0}, trace.getTrace("ind"));
    }
}
