class PcmCaptureProcessor extends AudioWorkletProcessor {
  process(inputs) {
    const samples = inputs[0]?.[0]
    if (samples?.length) {
      const chunk = samples.slice()
      this.port.postMessage(chunk.buffer, [chunk.buffer])
    }
    return true
  }
}

registerProcessor('pcm-capture-processor', PcmCaptureProcessor)
