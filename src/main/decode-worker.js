/**
 * Worker thread that runs a decoder off the main process.
 *
 * Decrypting a 100 MB FLAC is CPU-bound; doing it on the Electron main
 * thread stalls every IPC message (progress events, window controls) and
 * makes the UI feel frozen. Each conversion job spawns one of these workers,
 * so several files can be decrypted in parallel while the UI stays smooth.
 *
 * Message in : { inputPath, outputDir, opts }
 * Message out: { ok: true, result } | { ok: false, error }
 */
const { parentPort, workerData } = require('node:worker_threads');
const decoders = require('../decoders');

async function main() {
  const { inputPath, outputDir, opts } = workerData;
  const decoder = decoders.pickDecoder(inputPath);
  if (!decoder) throw new Error(`No decoder for file: ${inputPath}`);
  return decoder.decodeFile(inputPath, outputDir, opts || {});
}

main()
  .then((result) => parentPort.postMessage({ ok: true, result }))
  .catch((e) => parentPort.postMessage({ ok: false, error: e && e.message ? e.message : String(e) }));
