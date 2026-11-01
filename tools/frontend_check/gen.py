# Test audio for tools/frontend_check (raw 16 kHz 16-bit mono on stdout).
# Test audio: silence, quiet noise, speech-like tones, sweeps, loud bursts, clipping, and odd extremes.
import math, random, struct, sys
random.seed(int(sys.argv[2]) if len(sys.argv) > 2 else 1)
sr = 16000
out = []
def add(xs):
    """Appends samples as 16-bit audio, clipped the way a microphone would clip them."""
    out.extend(max(-32768, min(32767, int(round(x)))) for x in xs)
add([0.0] * 8000)
add([random.gauss(0, 30) for _ in range(16000)])
for f0, amp in [(110, 3000), (220, 12000), (180, 800), (300, 25000)]:
    xs = []
    for n in range(sr):
        t = n / sr
        env = 0.5 - 0.5 * math.cos(2 * math.pi * 3 * t)
        v = sum(math.sin(2 * math.pi * f0 * h * t + h) / h for h in range(1, 12))
        xs.append(amp * env * v / 3 + random.gauss(0, 50))
    add(xs)
add([20000 * math.sin(2 * math.pi * (100 + 3500 * n / sr) * n / sr) for n in range(sr)])
add([random.gauss(0, 9000) for _ in range(8000)])
add([40000 * math.sin(2 * math.pi * 440 * n / sr) for n in range(4000)])  # clipped
add([(-32768 if n % 7 == 0 else 32767) for n in range(1600)])
add([random.choice([0, 1, -1, 5]) for _ in range(4000)])
add([random.gauss(0, 2000) * (1 if (n // 400) % 2 else 0.01) for n in range(12000)])
sys.stdout.buffer.write(struct.pack("<%dh" % len(out), *out))
