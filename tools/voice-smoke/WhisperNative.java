package app.lisan.keyboard.voice;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/** Host-only smoke test of the same JNI bridge used by the Android keyboard. */
public final class WhisperNative {
    native byte[] decode(String model, short[] pcm, String language, AtomicBoolean cancelled, int threads);

    public static void main(String[] args) throws Exception {
        if (args.length != 5) throw new IllegalArgumentException("LIB MODEL WAV LANGUAGE EXPECTED_FRAGMENT");
        System.load(Path.of(args[0]).toAbsolutePath().toString());
        try (var stream = AudioSystem.getAudioInputStream(Path.of(args[2]).toFile())) {
            var format = stream.getFormat();
            if (format.getSampleRate() != 16000 || format.getChannels() != 1 || format.getSampleSizeInBits() != 16 ||
                format.isBigEndian() || format.getEncoding() != AudioFormat.Encoding.PCM_SIGNED) {
                throw new IllegalArgumentException("Expected 16 kHz mono signed PCM16 WAV");
            }
            var raw = stream.readNBytes(960002);
            if (raw.length > 960000 || raw.length < 6400 || raw.length % 2 != 0)
                throw new IllegalArgumentException("Expected 0.2 to 30 seconds of audio");
            var pcm = new short[raw.length / 2];
            ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm);
            var engine = new WhisperNative();
            if (engine.decode(args[1], pcm, args[3], new AtomicBoolean(true), 2) != null)
                throw new AssertionError("A cancelled request must not return language data");
            var result = engine.decode(args[1], pcm, args[3], new AtomicBoolean(false), 2);
            if (result == null) throw new AssertionError("No transcription");
            var text = new String(result, StandardCharsets.UTF_8).strip();
            System.out.println(text);
            if (!text.toLowerCase(java.util.Locale.ROOT).contains(args[4].toLowerCase(java.util.Locale.ROOT)))
                throw new AssertionError("Expected phrase fragment was not found");
        }
    }
}
