# The original front end, for the check

Copied unchanged from [pymicro-features](https://github.com/rhasspy/pymicro-features) at commit
`96bd69cfad79aa67697e176570d3dd87052c3def`, so the check needs nothing from the internet:

- `tensorflow/lite/experimental/microfrontend/lib/`: TensorFlow Lite Micro's audio front end,
  Apache License 2.0 (`LICENSE-tensorflow.txt`), © The TensorFlow Authors.
- `kissfft/`: KISS FFT, BSD licence (in each file's header), © Mark Borgerding.

Only `run.sh` uses these; nothing here goes into the app.
