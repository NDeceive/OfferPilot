-- OfferPilot 2.0 岗位图标补丁：按稳定岗位编码补齐图标键和主题色。
UPDATE job_position
SET icon_key = CASE code
    WHEN 'BE-JAVA' THEN 'openjdk' WHEN 'BE-PY' THEN 'python' WHEN 'BE-GO' THEN 'go'
    WHEN 'BE-NODE' THEN 'nodejs' WHEN 'BE-CPP' THEN 'chip-speed'
    WHEN 'FE-WEB' THEN 'web' WHEN 'FE-ANDROID' THEN 'android' WHEN 'FE-IOS' THEN 'apple'
    WHEN 'FE-CROSS' THEN 'flutter' WHEN 'FE-MINI' THEN 'wechat' WHEN 'FE-DESKTOP' THEN 'electron'
    WHEN 'FS-JAVA' THEN 'openjdk' WHEN 'FS-NODE' THEN 'nodejs' WHEN 'FS-PY' THEN 'python' WHEN 'FS-AI' THEN 'ai-app'
    WHEN 'ALG-ML' THEN 'machine-learning' WHEN 'ALG-NLP' THEN 'nlp' WHEN 'ALG-CV' THEN 'computer-vision'
    WHEN 'ALG-REC' THEN 'recommendation' WHEN 'ALG-SPEECH' THEN 'speech'
    WHEN 'ALG-MM' THEN 'multimodal' WHEN 'ALG-MLOPS' THEN 'mlops'
    WHEN 'PM-C' THEN 'product-consumer' WHEN 'PM-B' THEN 'product-enterprise' WHEN 'PM-AI' THEN 'product-ai'
    WHEN 'DA-BIZ' THEN 'data-business' WHEN 'DA-PROD' THEN 'data-product' WHEN 'DA-BI' THEN 'data-bi'
    WHEN 'QA-FUNC' THEN 'qa-functional' WHEN 'QA-AUTO' THEN 'qa-automation'
    WHEN 'QA-PERF' THEN 'qa-performance' WHEN 'QA-SDET' THEN 'qa-sdet'
    ELSE icon_key
END,
theme_key = CASE
    WHEN code LIKE 'ALG-%' THEN 'violet' WHEN code LIKE 'PM-%' THEN 'orange'
    WHEN code LIKE 'DA-%' THEN 'blue' WHEN code LIKE 'QA-%' THEN 'amber'
    WHEN code LIKE 'FE-%' THEN 'cyan' WHEN code LIKE 'FS-%' THEN 'green'
    ELSE 'jade'
END
WHERE code IS NOT NULL;
