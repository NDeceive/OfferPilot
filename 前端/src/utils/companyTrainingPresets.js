// Platform training presets inferred from public company culture, not official hiring rubrics.
// Codes use the existing score_module dictionary; array order sets training priority.
export const companyTrainingPresets = [
  {
    name: '字节跳动',
    culture: '求真务实 · 坦诚清晰 · 共同成长',
    focus: '重视事实分析、清晰沟通与持续复盘。',
    source: 'https://www.bytedance.com/zh/',
    codes: ['problem_analysis', 'technical_depth', 'expression_clarity', 'followup_adaptability', 'project_review'],
  },
  {
    name: '美团',
    culture: '以客户为中心 · 合作共赢 · 追求卓越',
    focus: '重视业务问题拆解、工程落地与协作表达。',
    source: 'https://peisong.meituan.com/about',
    codes: ['problem_analysis', 'engineering_practice', 'position_cognition', 'project_expression', 'project_review'],
  },
  {
    name: '华为',
    culture: '以客户为中心 · 长期奋斗 · 自我批判',
    focus: '重视技术积累、工程实践与自我改进。',
    source: 'https://www.huawei.com/cn/news/2019/7/opening-remarks-chairman-lianghua-2018-csr',
    codes: ['technical_depth', 'engineering_practice', 'technical_base', 'problem_analysis', 'project_review'],
  },
  {
    name: '阿里巴巴',
    culture: '客户第一 · 信任 · 拥抱变化',
    focus: '重视客户需求理解、方案表达与变化应对。',
    source: 'https://www.alibabagroup.com/zh-HK/about-alibaba',
    codes: ['position_cognition', 'problem_analysis', 'project_expression', 'followup_adaptability', 'project_review'],
  },
  {
    name: '腾讯',
    culture: '正直 · 进取 · 协作 · 创造',
    focus: '重视问题解决、清晰协作与方案创新。',
    source: 'https://www.tencent.com/index.php/zh-cn/integrity-policy.html',
    codes: ['problem_analysis', 'project_expression', 'expression_clarity', 'technical_depth', 'logical_structure'],
  },
  {
    name: '京东',
    culture: '客户为先 · 创新 · 拼搏 · 担当',
    focus: '重视工程交付、主动解决问题与结果复盘。',
    source: 'https://zhaopin.jd.com/web/static/develop/2',
    codes: ['engineering_practice', 'problem_analysis', 'project_review', 'project_expression', 'position_cognition'],
  },
  {
    name: '小米',
    culture: '真诚热爱 · 工程师思维 · 持续成长',
    focus: '重视技术原理、动手实践与持续优化。',
    source: 'https://www.mi.com/about/culture',
    codes: ['technical_depth', 'engineering_practice', 'problem_analysis', 'technical_base', 'project_review'],
  },
]
