package nnue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads and writes {@code .nnue} files (written by {@code train/export.py}).
 *
 * <pre>
 *   "NNUE" (4 bytes) | version int32 = 1 | hidden int32 = 256
 *   featureWeights int16[768 * 256] | featureBias int16[256] | outputWeights int16[512]
 *   outputBias int32                                          (all little-endian)
 * </pre>
 */
public final class NetFile {

  public static final int VERSION = 1;

  /** The network bundled in the jar (the best generation so far), if any. */
  public static final String DEFAULT_RESOURCE = "/nets/best.nnue";

  private static final Map<String, Network> CACHE = new ConcurrentHashMap<>();

  private NetFile() {}

  /** Loads (and caches) a network file. Engines share one copy of the weights. */
  public static Network load(Path path) throws IOException {
    String key = path.toAbsolutePath().normalize().toString();
    Network cached = CACHE.get(key);
    if (cached != null) {
      return cached;
    }
    Network net = parse(Files.readAllBytes(path));
    CACHE.put(key, net);
    return net;
  }

  /** The network bundled in the jar, or {@code null} if this build has none. */
  public static Network loadDefault() {
    return CACHE.computeIfAbsent(
        DEFAULT_RESOURCE,
        k -> {
          try (InputStream in = NetFile.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            return in == null ? null : parse(in.readAllBytes());
          } catch (IOException e) {
            throw new IllegalStateException("Cannot read bundled network", e);
          }
        });
  }

  public static Network parse(byte[] bytes) {
    ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    if (bytes.length < 12 || b.get() != 'N' || b.get() != 'N' || b.get() != 'U' || b.get() != 'E') {
      throw new IllegalArgumentException("Not a .nnue file (bad magic)");
    }
    int version = b.getInt();
    int hidden = b.getInt();
    if (version != VERSION || hidden != Network.HIDDEN) {
      throw new IllegalArgumentException(
          "Unsupported network: version " + version + ", hidden " + hidden);
    }
    short[] fw = readShorts(b, Features.COUNT * Network.HIDDEN);
    short[] fb = readShorts(b, Network.HIDDEN);
    short[] ow = readShorts(b, 2 * Network.HIDDEN);
    int ob = b.getInt();
    if (b.hasRemaining()) {
      throw new IllegalArgumentException("Network file has " + b.remaining() + " extra bytes");
    }
    return new Network(fw, fb, ow, ob);
  }

  public static byte[] write(Network net) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteBuffer header = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
    header.put((byte) 'N').put((byte) 'N').put((byte) 'U').put((byte) 'E');
    header.putInt(VERSION).putInt(Network.HIDDEN);
    out.writeBytes(header.array());
    out.writeBytes(
        shorts(java.util.Arrays.copyOf(net.featureWeights, Features.COUNT * Network.HIDDEN)));
    out.writeBytes(shorts(net.featureBias));
    out.writeBytes(shorts(net.outputWeights));
    out.writeBytes(
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(net.outputBias).array());
    return out.toByteArray();
  }

  private static short[] readShorts(ByteBuffer b, int n) {
    short[] a = new short[n];
    b.asShortBuffer().get(a);
    b.position(b.position() + 2 * n);
    return a;
  }

  private static byte[] shorts(short[] a) {
    ByteBuffer b = ByteBuffer.allocate(2 * a.length).order(ByteOrder.LITTLE_ENDIAN);
    b.asShortBuffer().put(a);
    return b.array();
  }
}
