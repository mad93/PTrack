package cat.ptrack;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** ELM automatic CAN formatting (ATH0 / CAF1); no CAN broadcast or write services. */
public final class ElmProtocol {
    private final InputStream in;
    private final OutputStream out;
    private String target = "";
    public ElmProtocol(InputStream in, OutputStream out) { this.in = in; this.out = out; }
    public String exchange(String command) throws IOException {
        if (!command.matches("AT[A-Z0-9 ]+|22[0-9A-F]{4}")) throw new IOException("Read-only command rejected");
        out.write((command + "\r").getBytes(StandardCharsets.US_ASCII)); out.flush();
        long deadline = System.nanoTime() + 4_000_000_000L;
        StringBuilder text = new StringBuilder();
        while (System.nanoTime() < deadline) {
            if (Thread.currentThread().isInterrupted()) throw new IOException("Cancelled");
            if (in.available() == 0) {
                try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
                continue;
            }
            int b = in.read();
            if (b < 0) throw new IOException("Disconnected");
            if (b == '>') return text.toString();
            text.append((char)b);
            if (text.length() > 32768) throw new IOException("ELM reply too large");
        }
        // Abandon the socket after timeout: never send a new command into an unfinished one.
        throw new IOException("ELM prompt timeout");
    }
    private void expect(String command) throws IOException {
        if (!exchange(command).trim().matches("(?s).*\\bOK\\b.*")) throw new IOException("ELM rejected " + command);
    }
    public void initialize() throws IOException {
        exchange("ATZ"); expect("ATE0"); expect("ATL0"); expect("ATS0"); expect("ATH0");
        expect("ATCAF1"); expect("ATSP6"); expect("ATST64");
    }
    public String read(String request, String response, String did) throws IOException {
        if (!target.equals(request)) {
            expect("ATSH" + request); expect("ATCRA" + response);
            exchange("ATFCSH" + request); exchange("ATFCSD300000"); exchange("ATFCSM1");
            target = request;
        }
        String raw = exchange("22" + did);
        if (raw.toUpperCase(Locale.ROOT).contains("ELM327") || raw.toUpperCase(Locale.ROOT).contains("STN")) throw new IOException("ELM reset");
        for(String line:raw.split("[\\r\\n]+"))if(line.trim().equalsIgnoreCase("22"+did))throw new IOException("ELM echo returned: reset suspected");
        return raw;
    }
    public static byte[] payload(String raw, String did) {
        StringBuilder data = new StringBuilder();
        boolean numbered = false; int sequence = 0, declared = -1;
        for (String original : raw.toUpperCase(Locale.ROOT).split("[\\r\\n]+")) {
            String line = original.replace(" ", "").trim();
            if (line.matches("[0-9A-F]+:[0-9A-F]+")) {
                int colon = line.indexOf(':');
                int n = Integer.parseInt(line.substring(0, colon), 16);
                if (n != sequence++ % 16 || line.substring(colon+1).length()%2!=0) return null;
                numbered = true; data.append(line.substring(colon + 1));
            } else if (!numbered && line.matches("[0-9A-F]{1,3}")) declared=Integer.parseInt(line,16);
            else if (!numbered && line.startsWith("62" + did)) data.append(line);
        }
        String hex = data.toString();
        if(numbered) { if(declared<3||hex.length()<declared*2)return null; hex=hex.substring(0,declared*2); }
        if (!hex.startsWith("62" + did) || hex.length() % 2 != 0) return null;
        List<Byte> bytes = new ArrayList<>();
        try { for (int i = 6; i < hex.length(); i += 2) bytes.add((byte) Integer.parseInt(hex.substring(i, i + 2), 16)); }
        catch (NumberFormatException e) { return null; }
        byte[] result = new byte[bytes.size()];
        for (int i = 0; i < result.length; i++) result[i] = bytes.get(i);
        return result;
    }
    public static double unsigned(byte[] data, int offset, int length) {
        if (data == null || offset < 0 || length < 1 || length > 4 || offset + length > data.length) return Double.NaN;
        long value = 0; for (int i = offset; i < offset + length; i++) value = (value << 8) | (data[i] & 255);
        return value;
    }
}
