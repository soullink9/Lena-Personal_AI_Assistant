// Reference: TFLM microfrontend with microWakeWord's settings, fed 160 samples at a time like pymicro-features.
#include <cstdio>
#include <cstdint>
#include <vector>
extern "C" {
#include "tensorflow/lite/experimental/microfrontend/lib/frontend.h"
#include "tensorflow/lite/experimental/microfrontend/lib/frontend_util.h"
}
int main() {
  FrontendConfig cfg;
  cfg.window.size_ms = 30; cfg.window.step_size_ms = 10;
  cfg.filterbank.num_channels = 40; cfg.filterbank.lower_band_limit = 125.0f; cfg.filterbank.upper_band_limit = 7500.0f;
  cfg.noise_reduction.smoothing_bits = 10; cfg.noise_reduction.even_smoothing = 0.025f; cfg.noise_reduction.odd_smoothing = 0.06f;
  cfg.noise_reduction.min_signal_remaining = 0.05f;
  cfg.pcan_gain_control.enable_pcan = 1; cfg.pcan_gain_control.strength = 0.95f; cfg.pcan_gain_control.offset = 80.0f;
  cfg.pcan_gain_control.gain_bits = 21;
  cfg.log_scale.enable_log = 1; cfg.log_scale.scale_shift = 6;
  FrontendState st;
  if (!FrontendPopulateState(&cfg, &st, 16000)) { fprintf(stderr, "populate failed\n"); return 1; }
  std::vector<int16_t> audio;
  int16_t s;
  while (fread(&s, 2, 1, stdin) == 1) audio.push_back(s);
  size_t i = 0;
  while (i + 160 <= audio.size()) {
    size_t read = 0;
    FrontendOutput o = FrontendProcessSamples(&st, &audio[i], 160, &read);
    i += read;
    if (o.size) { for (size_t k = 0; k < o.size; k++) printf("%u%c", o.values[k], k + 1 < o.size ? ' ' : '\n'); }
  }
  return 0;
}
