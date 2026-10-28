// The Java side of tools/frontend_check: prints MicroFrontend.java's features for raw audio on stdin.
import io.github.soullink9.lena.MicroFrontend;
import java.io.*;
import java.nio.*;
public class Main {
    public static void main(String[] a) throws Exception {
        byte[] b = System.in.readAllBytes();
        short[] s = new short[b.length / 2];
        ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(s);
        MicroFrontend fe = new MicroFrontend();
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i + 160 <= s.length) {
            int read = fe.process(s, i, 160);
            i += read;
            if (fe.ready) {
                for (int k = 0; k < 40; k++) out.append(fe.features[k]).append(k < 39 ? ' ' : '\n');
            }
        }
        System.out.print(out);
    }
}
