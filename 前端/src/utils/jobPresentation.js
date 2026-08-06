const iconByCode = {
  'BE-JAVA': 'openjdk', 'BE-PY': 'python', 'BE-GO': 'go', 'BE-NODE': 'nodejs', 'BE-CPP': 'chip-speed',
  'FE-WEB': 'web', 'FE-ANDROID': 'android', 'FE-IOS': 'apple', 'FE-CROSS': 'flutter', 'FE-MINI': 'wechat', 'FE-DESKTOP': 'electron',
  'FS-JAVA': 'openjdk', 'FS-NODE': 'nodejs', 'FS-PY': 'python', 'FS-AI': 'ai-app',
  'ALG-ML': 'machine-learning', 'ALG-NLP': 'nlp', 'ALG-CV': 'computer-vision', 'ALG-REC': 'recommendation',
  'ALG-SPEECH': 'speech', 'ALG-MM': 'multimodal', 'ALG-MLOPS': 'mlops',
  'PM-C': 'product-consumer', 'PM-B': 'product-enterprise', 'PM-AI': 'product-ai',
  'DA-BIZ': 'data-business', 'DA-PROD': 'data-product', 'DA-BI': 'data-bi',
  'QA-FUNC': 'qa-functional', 'QA-AUTO': 'qa-automation', 'QA-PERF': 'qa-performance', 'QA-SDET': 'qa-sdet',
}

const toneByFamily = {
  BE: 'jade', FE: 'cyan', FS: 'green', ALG: 'violet', PM: 'orange', DA: 'blue', QA: 'amber',
}

export function getJobPresentation(job = {}) {
  const code = String(job.code || job.directionCode || '').toUpperCase()
  const family = code.split('-')[0]
  return {
    iconKey: iconByCode[code] || job.iconKey || 'qa-sdet',
    themeKey: job.themeKey || toneByFamily[family] || 'jade',
  }
}
