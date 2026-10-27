"""Build reviewed objective items; preserve pinned GitHub attribution and original wording."""
import collections
import hashlib
import importlib.util
import json
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[1]
BANK = ROOT / '后端/src/main/resources/practice-bank'
ROLES = ['BE-JAVA','BE-PY','BE-GO','BE-NODE','BE-CPP','FE-WEB','FE-ANDROID','FE-IOS','FE-CROSS','FE-MINI','FE-DESKTOP','FS-JAVA','FS-NODE','FS-PY','FS-AI','ALG-ML','ALG-NLP','ALG-CV','ALG-REC','ALG-SPEECH','ALG-MM','ALG-MLOPS','PM-C','PM-B','PM-AI','DA-BIZ','DA-PROD','DA-BI','QA-FUNC','QA-AUTO','QA-PERF','QA-SDET']
GROUPS = {
    'java': (['BE-JAVA','FS-JAVA'], ['Java核心','Java'], 'https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/package-summary.html'),
    'web': (['FE-WEB','BE-NODE','FS-NODE','FS-JAVA','FE-CROSS','FE-MINI','FE-DESKTOP'], ['JavaScript','TypeScript','浏览器原理','网络'], 'https://developer.mozilla.org/en-US/docs/Web/JavaScript/Guide'),
    'sql': ([r for r in ROLES if r.startswith(('BE-','FS-','DA-','QA-'))], ['SQL','MySQL','数据库','PostgreSQL','数仓'], 'https://www.postgresql.org/docs/current/tutorial.html'),
    'ml': ([r for r in ROLES if r.startswith('ALG-')] + ['FS-AI','PM-AI','DA-BIZ','DA-PROD','BE-PY','FS-PY'], ['机器学习','模型评估','LLM','特征工程','数据处理','AI'], 'https://scikit-learn.org/stable/modules/model_evaluation.html'),
    'qa': ([r for r in ROLES if r.startswith('QA-')], ['用例设计','缺陷管理','回归测试','自动化框架','脚本','CI','CD','接口测试','性能','压测','监控','质量度量'], 'https://playwright.dev/docs/best-practices'),
    'pm': (['PM-C','PM-B','PM-AI','DA-PROD'], ['用户研究','需求分析','增长','项目推进','行业分析','客户需求','商业化','系统设计','数据策略','AI 产品'], 'https://scrumguides.org/scrum-guide.html'),
    'common': (ROLES, ['系统设计','项目场景','网络','HTTP','性能','数据治理'], 'https://developer.mozilla.org/en-US/docs/Web/HTTP/Overview'),
}

# title, single-choice prompt, four alternatives (first correct), multi-choice truths,
# judgment statement, truth value, explanation. Siblings share a conceptId and are not co-selected.
ITEMS = {
'java': [
('HashMap 并发','多线程同时修改同一个 HashMap 时，哪种做法合适？',['按访问模式使用外部同步或并发容器','依赖 fail-fast 保证线程安全','只增大初始容量','只降低负载因子'],['HashMap 本身未同步','fail-fast 不提供并发正确性保证'],'HashMap 的 fail-fast 迭代器能保证并发写入安全。',False,'HashMap 未同步；并发结构修改需同步策略。fail-fast 是尽力检测错误，不是线程安全机制。'),
('HashMap 空值','Java 17 的 HashMap 对 null 的支持是？',['允许一个 null 键和多个 null 值','键和值都不允许 null','只允许 null 值','只允许 null 键'],['允许 null 键','允许 null 值'],'Java 17 的 HashMap 不允许 null 键。',False,'HashMap 允许 null 键和值；同一个键只能对应一个当前映射。'),
('HashMap 顺序','HashMap 的迭代顺序有什么保证？',['不保证固定顺序','保证按插入顺序','保证按键自然顺序','保证按值大小'],['不能依赖固定迭代顺序','需要插入顺序时可考虑 LinkedHashMap'],'HashMap 保证按照键的插入顺序遍历。',False,'HashMap 没有顺序保证，LinkedHashMap 可维护插入顺序。'),
('ArrayList 访问','按索引读取 ArrayList 元素的通常时间复杂度是？',['O(1)','O(n)','O(log n)','O(n log n)'],['按索引读取通常是 O(1)','中间插入可能需要移动元素'],'ArrayList 中间插入总是 O(1)。',False,'ArrayList 基于数组，随机访问常数时间，插入中间需要移动后续元素。'),
('字符串不可变','对 String 调用 concat 后，应如何获得拼接结果？',['使用返回的新字符串','原对象一定被修改','修改原对象数组','强制转换为 StringBuilder'],['String 的内容不可变','拼接结果应通过返回值取得'],'String 的 concat 会原地修改原字符串。',False,'String 不可变，concat 返回结果字符串，原引用指向的内容不变。'),
('equals 与引用','两个内容相同但分别 new 的 String，如何比较内容？',['使用 equals','使用 ==','比较 identityHashCode','只比较引用地址'],['equals 可比较字符串内容','== 比较对象引用'],'两个分别 new 的同内容 String 用 == 一定返回 true。',False,'String.equals 比较内容；对象上的 == 比较引用身份。'),
('HashSet 去重','HashSet 的核心用途是？',['保存不重复元素','按插入顺序保存所有重复元素','按值排序所有元素','用索引随机访问'],['不保存重复元素','不能依赖固定遍历顺序'],'HashSet 允许保存多个相等的元素。',False,'Set 不含重复元素；HashSet 基于哈希表，不保证固定迭代顺序。'),
('ConcurrentHashMap 空值','ConcurrentHashMap 是否允许 null 键或值？',['二者均不允许','都允许','仅允许 null 键','仅允许 null 值'],['提供线程安全的映射操作','不允许 null 键和值'],'ConcurrentHashMap 允许 null 值。',False,'ConcurrentHashMap 不允许 null 键和值；复合操作仍应选用原子 API。'),
('List.of','对 List.of 创建的列表调用 add 会怎样？',['抛出 UnsupportedOperationException','追加成功','返回新列表','静默忽略'],['列表不支持结构修改','不接受 null 元素'],'List.of 创建的列表可以通过 add 追加元素。',False,'List.of 创建不可修改列表，add 抛出 UnsupportedOperationException。'),
('TreeMap 排序','TreeMap 默认依据什么排列键？',['键的自然顺序','插入时间','值的大小','对象内存地址'],['可按键自然顺序排列','也可指定 Comparator'],'TreeMap 可以通过 Comparator 指定键的排序规则。',True,'TreeMap 按键自然顺序或构造时提供的 Comparator 排序。'),
],
'web': [
('严格相等','JavaScript 中 1 === "1" 的结果是？',['false','true','1','抛出 TypeError'],['=== 不进行类型转换','不同类型的操作数严格相等结果为 false'],'JavaScript 的 === 会先把字符串转换成数字。',False,'严格相等不进行隐式类型转换，数字 1 与字符串 "1" 不相等。'),
('NaN 比较','NaN === NaN 的结果是？',['false','true','undefined','抛出异常'],['NaN 不严格等于自身','Number.isNaN 可用于检测 NaN'],'NaN === NaN 返回 true。',False,'NaN 与任何值（包括自身）严格比较均为 false。'),
('对象引用','两个分别创建的对象 {a:1} 用 === 比较结果是？',['false','true','取决于属性顺序','无法比较'],['对象严格比较依据引用身份','相同属性内容不代表同一个引用'],'两个独立创建且属性相同的对象严格相等。',False,'对象的严格相等比较引用身份，而不是深度比较属性。'),
('const 对象','const obj = {x:1} 后哪个操作可行？',['obj.x = 2','obj = {}','再次声明同作用域 const obj','删除变量绑定'],['const 禁止重新绑定变量','const 不自动冻结对象属性'],'const 声明的对象，其所有属性都不能修改。',False,'const 限制绑定的重新赋值，不自动让对象内容不可变。'),
('数组 map','Array.prototype.map 的主要返回值是？',['由回调结果组成的新数组','原数组长度','始终为 undefined','排序后的原数组'],['返回新数组','回调返回值成为对应位置的结果'],'map 返回一个由回调结果组成的新数组。',True,'map 创建新数组；回调仍可能产生副作用，不能据此断言原对象绝不被修改。'),
('JSON 解析','把 JSON 字符串转换为 JavaScript 值应使用？',['JSON.parse','JSON.stringify','String.parse','Object.toJSON'],['JSON.parse 用于解析 JSON 文本','JSON.stringify 用于序列化值'],'JSON.stringify 用于把 JSON 文本解析为对象。',False,'parse 是解析，stringify 是序列化；解析不合法 JSON 会抛异常。'),
('Promise 微任务','同一轮同步代码结束后，已就绪 Promise 回调和新定时器谁先执行？',['Promise 回调','定时器回调','一定同时执行','按代码行数随机'],['同步代码先完成','已就绪 Promise 回调通过微任务处理'],'Promise.then 回调会在当前同步调用栈执行完之前执行。',False,'Promise 回调异步执行；通常先清空微任务再处理下一项任务。'),
('let 作用域','let 的主要作用域规则是？',['块级作用域','只有全局作用域','始终函数作用域','没有作用域'],['let 具有块级作用域','初始化前访问可触发暂时性死区错误'],'let 变量在初始化前访问可能抛出 ReferenceError。',True,'let 为块级作用域，在声明初始化之前处于暂时性死区。'),
('事件目标','点击按钮后，event.target 通常表示？',['事件最初触发的目标元素','当前运行监听器的元素','最外层祖先元素','document 永远是目标'],['target 表示原始事件目标','currentTarget 表示当前监听器所在元素'],'event.target 与 event.currentTarget 在冒泡过程中总是相同。',False,'原始目标与当前监听器元素可能不同；涉及 Shadow DOM 时还存在事件重定向。'),
('HTTP 404','HTTP 404 最直接表示什么？',['请求的资源未找到','服务器成功创建资源','请求需要永久重定向','一定是浏览器断网'],['404 属于客户端错误状态码','404 表示资源未找到'],'HTTP 404 属于 5xx 服务器错误状态码。',False,'404 属于 4xx，通常表示所请求资源未找到；不能由状态码判断一定断网。'),
],
'sql': [
('WHERE 与 HAVING','过滤聚合后的分组结果应使用？',['HAVING','WHERE','ORDER BY','LIMIT'],['WHERE 过滤聚合前的行','HAVING 过滤分组后的结果'],'HAVING 可以用于过滤聚合后的分组结果。',True,'WHERE 在分组前过滤行，HAVING 在分组聚合后过滤结果。'),
('COUNT 与空值','COUNT(column) 会怎样处理 NULL？',['不计入 NULL','每个 NULL 计为 1','整组结果为 NULL','抛出异常'],['COUNT(*) 统计行数','COUNT(column) 只计非 NULL 值'],'COUNT(column) 会统计该列的 NULL 值。',False,'COUNT(*) 统计行，COUNT(表达式) 统计非 NULL 表达式值。'),
('NULL 判断','判断某列为空值，应使用？',['IS NULL','= NULL','== NULL','LIKE NULL'],['NULL 表示未知或缺失值','空值判断应使用 IS NULL'],'WHERE col = NULL 可以可靠地选出空值行。',False,'普通比较遇到 NULL 得到未知值；应使用 IS NULL 或 IS NOT NULL。'),
('LEFT JOIN','LEFT JOIN 最基本的保留规则是？',['保留左表行，未匹配右表列补 NULL','只保留右表行','只保留成功匹配行','自动去重左表'],['保留左表未匹配行','未匹配的右表列填充 NULL'],'LEFT JOIN 会保留左表未匹配的行。',True,'左连接保留左表行；右表匹配多行时可产生多行结果。'),
('排序保证','查询结果需要稳定排序时应怎么做？',['显式 ORDER BY 并补充唯一排序键','依赖插入顺序','依赖物理存储位置','只添加 LIMIT'],['使用 ORDER BY 明确排序','并列值可用唯一键作为次级排序依据'],'没有 ORDER BY 的查询保证按照插入顺序返回。',False,'SQL 查询未指定 ORDER BY 时没有结果顺序保证。'),
('UNION 去重','UNION 与 UNION ALL 的常见区别是？',['UNION 去重，UNION ALL 保留重复','UNION ALL 总会排序','UNION 只能连接两行','二者完全相同'],['UNION 去除重复结果行','UNION ALL 保留重复结果行'],'UNION ALL 会自动去除重复结果行。',False,'UNION 有去重语义，UNION ALL 直接合并并保留重复。'),
('事务原子性','事务原子性意味着？',['事务内修改作为整体提交或回滚','每条语句单独永久提交','并发事务永不冲突','查询永远只读'],['成功事务可整体提交','失败事务可通过回滚撤销修改'],'事务原子性表示事务内操作可以作为整体提交或回滚。',True,'原子性强调整体性；不等同于无并发冲突或任意外部副作用可回滚。'),
('主键约束','主键的核心约束是什么？',['唯一且不允许 NULL','可重复且可为空','只保证排序','必须是字符串'],['主键唯一标识记录','主键字段不允许 NULL'],'同一张表可以有两条主键值相同的记录。',False,'主键要求唯一且非 NULL；可以由多个列共同组成。'),
('参数化查询','避免把用户输入直接拼入 SQL，应优先采用？',['参数化查询','只删除空格','只限制字符串长度','只在页面隐藏输入框'],['值通过绑定参数传入','SQL 结构与输入值应分离'],'参数化查询把用户输入值与 SQL 结构分离。',True,'参数化查询绑定值，避免值被当作 SQL 结构；动态表名等仍需白名单。'),
('索引代价','增加数据库索引可能带来什么？',['增加写入维护和存储成本','所有查询都必然更快','完全没有存储开销','无需维护'],['索引需要额外存储','写操作需要维护相关索引'],'给每一列建立索引一定能提高所有查询性能。',False,'索引可加速适用查询，也有存储和写入成本，需按访问模式评估。'),
],
'ml': [
('训练测试分离','测试集的主要用途是？',['评估模型对未见数据的表现','反复调参直到最高分','训练所有参数','替代训练集'],['测试集用于最终评估','调参应使用验证集或交叉验证'],'可以反复用测试集选超参数，再把该成绩当作无偏泛化评估。',False,'反复以测试成绩调参会泄漏评估信息；应保留独立最终测试集。'),
('数据泄漏','标准化器应在哪里拟合？',['仅训练数据','训练与测试拼接后','仅测试数据','先读取所有测试标签'],['训练数据用于拟合预处理器','测试数据仅用训练好的变换器转换'],'在划分数据前用全量数据拟合标准化器可能造成泄漏。',True,'预处理器需要仅在训练数据拟合；交叉验证时还应在各训练折内拟合。'),
('精确率','分类精确率 Precision 的定义是？',['TP / (TP + FP)','TP / (TP + FN)','TN / (TN + FN)','FP / (TP + FP)'],['关注预测正类中有多少真实正类','假阳性增加会影响精确率'],'Precision 的分母是 TP + FN。',False,'Precision=TP/(TP+FP)，Recall=TP/(TP+FN)，注意分母为零的处理约定。'),
('召回率','分类召回率 Recall 的定义是？',['TP / (TP + FN)','TP / (TP + FP)','TN / (TN + FP)','FP / (FP + TN)'],['关注真实正类中有多少被发现','漏检对应假阴性'],'Recall 关注真实正例被识别出来的比例。',True,'Recall=TP/(TP+FN)，衡量覆盖真实正类的程度。'),
('类别不均衡','极不均衡分类任务只报告 accuracy 的风险是？',['可能掩盖少数类识别失败','必然使模型过拟合','不能计算 accuracy','必然出现数据泄漏'],['应关注少数类的召回等指标','混淆矩阵有助于分析错误类型'],'类别极不均衡时，高准确率就足以证明模型效果良好。',False,'多数类预测器也可能有高准确率，应结合业务成本与各类指标。'),
('交叉验证','K 折交叉验证的通常做法是？',['轮流保留一折验证，其余折训练','所有折都作为同一测试集反复调参','每折必须相同标签','只训练一次且不验证'],['轮流使用不同验证折','评估结果可以在各折间汇总'],'K 折交叉验证通常轮流用一折验证、其余折训练。',True,'K 折用于评估或选择模型；时序和分组数据需要合适的专用拆分策略。'),
('过拟合','训练误差低但验证误差高，通常提示？',['可能过拟合','一定欠拟合','一定无数据噪声','一定没有任何模型参数'],['训练表现好不等于泛化表现好','需结合验证结果检查过拟合'],'训练集得分高就能保证对未见数据表现好。',False,'模型可能记忆训练数据，需要独立验证检查泛化与泄漏。'),
('随机种子','设置随机种子的主要作用是？',['提高含随机步骤实验的可复现性','保证模型达到最优','消除数据偏差','使样本自动独立'],['有助于复现实验中的随机拆分','不能自动提高模型效果'],'设置随机种子能保证训练得到全局最优模型。',False,'随机种子控制可控的随机过程，不保证最优或跨硬件完全一致。'),
('时序划分','时间序列预测的验证划分应优先考虑？',['保持时间先后关系','任意打乱过去和未来','把未来标签并入训练','只选最高分的一天'],['应避免未来信息进入训练','验证应符合实际预测时间关系'],'预测未来的时序任务应避免用未来数据训练再评估过去。',True,'时间序列拆分需要按时间顺序，避免未来信息泄漏。'),
('Pipeline','机器学习 Pipeline 的一个主要价值是？',['串联预处理和模型并帮助正确交叉验证','消除所有测试需求','自动证明无偏差','保证模型不出错'],['可以串联数据变换和模型','有助于让预处理在训练折内拟合'],'使用 Pipeline 可以替代所有独立测试评估。',False,'Pipeline 组织处理流程并减少泄漏风险，不能替代独立评估。'),
],
'qa': [
('可重试断言','动态页面中检测元素最终出现，应优先使用？',['可自动等待重试的断言','固定等待 30 秒后忽略结果','只判断一次且不等待','跳过断言'],['断言应验证用户可见结果','动态状态应使用有超时的等待策略'],'固定 sleep 是处理所有页面异步变化的最佳方案。',False,'基于条件的等待更能适应异步变化，固定等待易造成慢测和偶发失败。'),
('测试隔离','自动化测试隔离的主要目标是？',['避免测试互相依赖状态','共享所有可变数据','要求固定执行顺序','取消清理步骤'],['每个测试应能独立运行','可变状态污染会引发偶发失败'],'测试依赖前一个测试留下的状态会降低独立性。',True,'测试应独立准备状态，避免执行顺序和共享数据造成干扰。'),
('定位器','定位交互按钮时通常优先考虑？',['角色和可访问名称','页面第 17 个 div','随机 class 名','只用绝对坐标'],['语义定位更贴近用户交互','可访问名称有助于稳定定位'],'基于角色和可访问名称的定位器通常更贴近用户交互。',True,'语义定位降低对实现结构的耦合，也能暴露可访问性问题。'),
('回归测试','修复缺陷后回归测试的主要目的是什么？',['检查修复及相关已有行为','只证明代码能编译','只删除失败用例','只验证新页面颜色'],['验证缺陷不再发生','检查相关已有功能未受影响'],'缺陷修复后只编译成功就能替代回归验证。',False,'回归测试验证修复效果及可能受影响的已有行为。'),
('边界值','输入范围 1–100 的整数，哪个测试组更能覆盖边界？',['0、1、100、101','只有 50','只有 25、75','只有随机一个值'],['测试合法边界值','测试刚越界的非法值'],'只测一个范围中间值就能覆盖边界缺陷。',False,'边界和紧邻边界外的值容易暴露比较、范围验证等问题。'),
('缺陷复现','提交可复现缺陷时最应包含什么？',['环境、步骤、实际与预期结果','只有一句不好用','只有开发者名字','只有情绪评价'],['明确最小复现步骤','记录实际结果与预期结果'],'清楚的环境和复现步骤有助于定位缺陷。',True,'缺陷报告应使他人能独立复现并理解偏差。'),
('接口负例','对需认证的接口，哪个是有价值的负例？',['不带有效凭证尝试访问','仅访问首页','只测试成功请求','仅检查按钮颜色'],['验证缺失凭证时拒绝访问','验证无权限用户不能访问他人资源'],'只测接口成功响应就足以覆盖权限校验。',False,'认证与授权需要独立负例，包括未登录和跨用户访问。'),
('持续集成','CI 中自动化测试的主要用途是？',['尽早发现变更引入的问题','证明线上永不故障','取消代码审查','保证零缺陷'],['尽早检测变更问题','失败应能提供可诊断信息'],'CI 测试全部通过可以保证线上永无缺陷。',False,'测试提供一定范围内的验证，不能证明覆盖了所有环境和输入。'),
('测试证据','用 mock API 做页面测试能直接证明什么？',['该模拟响应下的界面行为','真实数据库已正确写入','真实判题器已安装','生产网络可用性'],['可验证给定响应下的 UI 行为','真实服务还需要集成验证'],'通过 mock 页面测试就能证明真实后端数据库正确写入。',False,'模拟测试不能代替真实接口、数据库或执行器验证。'),
('断言价值','哪个断言更直接检查用户目标？',['提交后显示正确结果与状态','函数被调用一次但不检查结果','代码行数小于 20','变量名字够长'],['验证实际结果比只验证调用更有价值','失败断言应能帮助定位问题'],'只断言函数调用次数就必然验证了业务结果。',False,'调用存在不代表业务输出正确，应验证用户可见结果与关键状态。'),
],
'pm': [
('产品目标','Scrum 中 Product Goal 主要描述什么？',['产品希望实现的未来状态','本周每个人的工时','所有任务的详细实现','固定不变的技术栈'],['为产品工作提供长期目标','产品待办列表用于达成产品目标'],'Product Goal 可以为产品待办列表提供共同方向。',True,'Product Goal 描述产品未来状态，并作为团队规划的长期目标。'),
('待办列表','Product Backlog 应如何理解？',['有序且持续演化的产品改进工作列表','永远冻结的合同清单','开发个人随意备忘录','只有已完成工作'],['列表持续演化','排序需要体现产品价值与目标'],'Product Backlog 一经建立就不应再调整。',False,'产品待办列表是持续演化的有序列表，会随需求与学习更新。'),
('完成标准','Definition of Done 的主要作用是？',['明确增量达到的质量状态','只记录谁写代码','只统计会议时间','只说明发布日期'],['提供共享的完成质量标准','未满足完成标准的工作不能视为已完成增量'],'代码写完但未满足 Definition of Done 也可直接算完成增量。',False,'完成标准描述增量符合所需质量标准的状态，写完代码不等于完成。'),
('Sprint 目标','Sprint Goal 的主要作用是？',['让一次 Sprint 的工作有共同目标','要求每个任务独立无关联','禁止任何协商','代替产品目标'],['解释 Sprint 为什么有价值','支持在目标内灵活协商具体工作'],'Sprint Goal 与每个任务的详细实现完全等同。',False,'Sprint Goal 是单一共同目标，并不等同于所有任务的具体实现。'),
('每日 Scrum','Daily Scrum 的主要目的是什么？',['检查 Sprint Goal 进展并调整计划','向经理逐条汇报工时','安排产品发布广告','代替所有测试'],['检查朝向 Sprint Goal 的进展','调整接下来工作计划'],'Daily Scrum 的主要目的仅仅是向经理汇报。',False,'Daily Scrum 是开发者检查 Sprint Goal 进展、调整 Sprint Backlog 的事件。'),
('Sprint Review','Sprint Review 更关注什么？',['检查成果并与利益相关者讨论后续调整','只做团队内部情绪总结','只讲演示动画','只统计迟到次数'],['与利益相关者检查成果','讨论环境变化和下一步调整'],'Sprint Review 只是一次不能讨论调整的成果演示。',False,'Review 是检查成果、适应后续工作的工作会议，不应局限于演示。'),
('回顾会议','Sprint Retrospective 主要规划什么？',['提高质量和有效性的改进','只新增广告渠道','只更换产品名字','只汇报客户账单'],['回顾协作与过程','规划有价值的改进行动'],'Sprint Retrospective 旨在寻找提升质量与有效性的办法。',True,'回顾会议检查人员、交互、流程和工具，并规划改进。'),
('经验主义','Scrum 的经验主义依赖哪些基础？',['透明、检视、适应','猜测、隐瞒、固定','延期、加班、服从','排序、删除、归档'],['透明支持有效检视','检视结果应促成适应'],'缺乏透明会影响有效检视与适应。',True,'Scrum 的经验主义强调透明、检视和适应，三者相互支持。'),
('产品负责人','Product Owner 的主要责任是？',['使团队工作产生的产品价值最大化','替代所有开发者编码','只记录会议','独立决定所有技术实现'],['负责有效的 Product Backlog 管理','关注产品价值最大化'],'Product Owner 的职责只是安排会议时间。',False,'Product Owner 对产品价值最大化和有效管理产品待办列表负责。'),
('自管理团队','Scrum 团队的自管理意味着？',['团队内部决定谁做什么、何时做以及如何做','无需任何共同目标','无需质量标准','不允许协作'],['在团队内部安排工作','仍需要目标与质量标准'],'自管理意味着团队可以忽略完成标准。',False,'自管理是团队内部组织工作，不表示不需要共同目标、责任或质量要求。'),
],
'common': [
('HTTP 请求响应','HTTP 的基本交互模型是？',['客户端请求、服务端响应','服务器永远无请求主动返回全部数据','只允许文件复制','只在同一进程通信'],['客户端发送请求','服务端返回状态与响应内容'],'HTTP 通常采用客户端请求与服务端响应模型。',True,'HTTP 是客户端与服务端交换消息的应用层协议。'),
('HTTP 无状态','HTTP 无状态最直接意味着？',['协议本身不自动保留跨请求会话状态','服务器不能保存任何数据','无法实现登录','每个请求都要重启服务器'],['应用可借助 Cookie 等维护会话','无状态不表示应用不能保存数据'],'HTTP 无状态意味着网站不能实现用户登录。',False,'HTTP 协议无状态，应用可以通过 Cookie、会话等机制关联请求。'),
('GET 语义','GET 方法的标准语义主要是？',['读取资源表示','创建任意资源且必然收费','删除所有资源','强制刷新数据库'],['GET 属于安全方法','GET 属于幂等方法'],'符合标准语义的 GET 不应被设计为删除账户的操作。',True,'GET 用于读取资源，属于安全和幂等方法；日志等附带效果不等同于请求修改语义。'),
('幂等性','HTTP 幂等方法强调什么？',['重复相同请求的预期服务端效果与一次相同','每次返回内容必须逐字相同','网络必然不超时','任何请求无需认证'],['关注预期服务端效果','重复响应状态码不要求完全相同'],'幂等要求每次响应的状态码和内容完全一致。',False,'幂等关注相同请求执行多次的预期效果；重复删除可能返回不同状态码。'),
('HTTP 401','HTTP 401 最直接说明什么？',['请求缺少有效认证凭证','资源一定不存在','服务器一定断电','请求创建成功'],['需提供有效认证凭证','不等同于资源不存在'],'HTTP 401 与 HTTP 404 的标准含义完全相同。',False,'401 表示缺少有效认证凭证，404 表示资源未找到。'),
('HTTP 403','HTTP 403 最直接说明什么？',['服务器理解请求但拒绝处理','服务器已创建资源','服务器要求永久重定向','连接必然成功且业务完成'],['请求被服务端理解','服务端拒绝执行请求'],'HTTP 403 表示服务端理解请求但拒绝处理。',True,'403 表示拒绝请求；需结合权限策略分析，不能直接当作业务成功。'),
('HTTP 500','HTTP 500 属于哪一类状态？',['服务器错误','成功','重定向','信息响应'],['500 属于 5xx','表示服务端遇到意外条件'],'HTTP 500 表示请求成功。',False,'500 表示服务端遇到无法完成请求的意外情况。'),
('缓存验证','带 ETag 的资源通常可用哪个头验证缓存？',['If-None-Match','Content-Disposition','Authorization-Order','Accept-Index'],['ETag 是资源版本的验证器','If-None-Match 可用于条件请求'],'ETag 可配合 If-None-Match 进行条件请求。',True,'客户端带上验证器，资源未变化时服务端可返回 304，减少内容传输。'),
('内容类型','HTTP Content-Type 主要描述什么？',['消息体的媒体类型','客户端屏幕尺寸','数据库主键类型','网络线路长度'],['描述消息体类型','帮助接收方解释消息体'],'Content-Type: application/json 表示消息体声明为 JSON 类型。',True,'Content-Type 声明媒体类型，不保证消息体一定合法，接收方仍需验证。'),
('HTTPS','HTTPS 相比明文 HTTP 主要增加什么？',['通过 TLS 保护传输','保证程序没有漏洞','保证所有内容真实','无需认证授权'],['保护传输的机密性和完整性','不能替代应用层权限校验'],'用了 HTTPS 就可以省略应用层权限检查。',False,'TLS 保护传输并支持身份验证，但不替代应用安全和权限校验。'),
],
}

JAVA_URLS = {
    0:'HashMap',1:'HashMap',2:'HashMap',3:'ArrayList',4:'../lang/String',5:'../lang/String',6:'HashSet',
    7:'concurrent/ConcurrentHashMap',8:'List',9:'TreeMap'
}

# Plausible distractors, independent of option order.
DISTRACTORS = {
'java': [
('fail-fast 能代替同步','增大初始容量可保证并发写安全'),('只能保存 null 值，不能保存 null 键','可以同时保存多个 null 键映射'),('按键自然顺序遍历','按插入顺序遍历'),('中间插入始终为 O(1)','底层以双向链表存储'),('concat 会原地修改原字符串','String 与 StringBuilder 都不可变'),('== 对字符串对象进行内容比较','相同内容的独立对象必然有相同引用'),('可以保存多个相等元素','可通过数字索引直接访问'),('允许 null 值但不允许 null 键','任意组合多个线程安全操作一定仍然原子'),('支持 add 追加元素','允许保存 null 元素'),('仅按插入先后排列','默认按值的大小排列')],
'web': [
('=== 会将数字字符串自动转成数字','1 === "1" 为 true'),('NaN === NaN 为 true','Number.isNaN("abc") 为 true'),('严格相等会递归比较属性','新建对象属性相同就严格相等'),('const 自动冻结所有嵌套属性','const 对象不能修改现有属性'),('map 总是返回 undefined','map 的回调不可能产生副作用'),('JSON.parse 用于把对象变成 JSON 文本','不合法的 JSON 文本会被 JSON.parse 静默忽略'),('then 回调在当前同步调用栈内执行','setTimeout 总是在就绪微任务之前执行'),('let 只有函数作用域','let 在声明前访问一定返回 undefined'),('target 永远是当前监听器所在元素','currentTarget 永远是最外层祖先'),('404 表示服务端成功创建资源','404 能直接证明客户端断网')],
'sql': [
('WHERE 总是在聚合后过滤结果','HAVING 仅用于对原始行排序'),('COUNT(*) 忽略含 NULL 的行','COUNT(column) 把 NULL 也算作非空值'),('NULL = NULL 的结果为 true','= NULL 与 IS NULL 含义相同'),('LEFT JOIN 只保留匹配成功的行','LEFT JOIN 会自动去重左表'),('LIMIT 能保证结果按插入顺序','没有 ORDER BY 时结果顺序一定固定'),('UNION ALL 自动去重','UNION 与 UNION ALL 总是完全相同'),('原子性保证所有外部副作用也可回滚','原子性意味着不会发生并发冲突'),('主键允许重复值','主键允许 NULL'),('只删除空格就能阻止 SQL 注入','绑定参数可直接代替所有动态表名'),('索引不占用额外存储','索引越多所有查询必然越快')],
'ml': [
('应反复用最终测试集选超参数','测试集应参与所有模型参数训练'),('标准化器应先在全量数据拟合','测试标签可参与特征筛选后再评估'),('Precision 等于 TP/(TP+FN)','Precision 只关注真实正类中的漏检'),('Recall 等于 TP/(TP+FP)','Recall 只统计预测正类中的误报'),('只预测多数类的高 accuracy 足以证明模型好','类别不均衡时无需分析少数类错误'),('所有验证折应同时参与本折训练','时序数据可以无条件随机打乱验证'),('训练得分高一定保证泛化好','验证误差高必然表示欠拟合'),('固定随机种子可消除样本偏差','固定随机种子保证全局最优'),('训练时可直接使用未来标签作为特征','任意打乱时序数据一定不会泄漏'),('Pipeline 能代替独立测试集评估','使用 Pipeline 就不必检查数据泄漏')],
'qa': [
('固定等待越长越能保证页面正确','动态页面只应检查一次瞬时状态'),('测试必须依赖固定执行顺序','共享所有可变状态更能保证稳定'),('绝对坐标永远比语义定位稳定','优先依赖易变的随机 class'),('编译成功就能替代回归测试','修复后无需检查相关已有功能'),('仅测试 50 即覆盖 1–100 的所有边界','越界输入无需验证'),('只有一句不好用就足够复现','环境差异不应记录'),('只测成功请求就覆盖权限校验','客户端隐藏按钮即可替代后端授权'),('CI 通过就保证线上零缺陷','自动化测试可替代所有代码审查'),('mock 测试通过证明生产数据库已写入','mock 页面测试能证明真实执行器已安装'),('只断言调用次数必然证明业务输出正确','只检查代码行数即可验证用户目标')],
'pm': [
('Product Goal 是每个人的详细工时清单','Product Goal 必须与产品未来状态无关'),('待办列表建立后永远不能调整','待办列表只存放已经完成的工作'),('只要代码写完就满足完成标准','完成标准只用于记录开发人员名字'),('Sprint Goal 等于所有任务详细实现','Sprint Goal 不应解释本次工作的价值'),('Daily Scrum 只是向经理报工时','Daily Scrum 应替代所有其他沟通'),('Sprint Review 只能演示不能讨论调整','Sprint Review 只统计团队迟到次数'),('回顾会议只能讨论客户账单','回顾会议不能改进流程或工具'),('隐藏信息可提高检视可靠性','检视发现偏差后应禁止调整'),('Product Owner 只负责安排会议时间','Product Owner 必须独自编写所有代码'),('自管理意味着无需共同目标','自管理允许忽略完成标准')],
'common': [
('HTTP 只允许同一进程内通信','服务端无需请求就必须返回全部资源'),('HTTP 无状态表示应用不能保存会话','HTTP 无状态表示网站无法登录'),('GET 的标准用途是删除账户','GET 是非幂等方法'),('幂等要求每次响应内容逐字相同','幂等可保证请求不会超时'),('401 的标准含义是资源未找到','401 表示服务器成功创建资源'),('403 表示服务器成功处理业务','403 表示永久重定向'),('500 属于 2xx 成功状态','500 表示请求无需进一步处理即可成功'),('ETag 表示客户端的屏幕尺寸','If-None-Match 用于声明请求体长度'),('Content-Type 用于声明数据库主键类型','Content-Type 能保证消息体一定合法'),('HTTPS 保证应用不存在漏洞','启用 HTTPS 后可以取消应用权限校验')],
}

def plain(value):
    value=re.sub(r'<img\b[^>]*>','',value)
    value=re.sub(r'</?(?:p|i|b|em|strong|details|summary|a)\b[^>]*>','',value)
    value=re.sub(r'```\w*\n|```','',value)
    return value.replace('`','').strip()

def build():
    questions=[]
    for group,items in ITEMS.items():
        roles,topics,url=GROUPS[group]
        for n,(title,prompt,options,truths,statement,truth,explanation) in enumerate(items):
            evidence=url
            if group=='java': evidence='https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/'+JAVA_URLS[n]+'.html'
            if group=='web': evidence='https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Operators/Strict_equality' if n<3 else url
            if group=='common': evidence='https://developer.mozilla.org/en-US/docs/Web/HTTP/'+(['Overview']*4+['Status/401','Status/403','Status/500','Headers/ETag','Headers/Content-Type','Overview'])[n]
            source={'url':evidence,'license':'OfferPilot-original','author':'OfferPilot','checkedAt':'2026-10-09','adaptation':'基于公开技术事实独立编写题干、选项和解析；未复制文档正文。'}
            concept=f'objective-{group}-{n+1:02}'
            base={'conceptId':concept,'title':title,'difficulty':1,'jobCodes':roles,'topics':topics,'tags':[title], 'source':source,'keywords':''}
            # Rotate correct answer to avoid answer-position bias.
            offset=n%4; rotated=options[-offset:]+options[:-offset] if offset else options
            answer='ABCD'[offset]
            questions.append({**base,'canonicalId':concept+'-choice','type':'CHOICE','content':prompt,'options':[{'key':k,'text':v} for k,v in zip('ABCD',rotated)],'correctAnswer':answer,'referenceAnswer':f'正确答案：{answer}。{explanation}'})
            questions.append({**base,'canonicalId':concept+'-judgment','type':'TRUE_FALSE','content':statement,'options':[{'key':'true','text':'正确'},{'key':'false','text':'错误'}],'correctAnswer':str(truth).lower(),'referenceAnswer':f'正确答案：{"正确" if truth else "错误"}。{explanation}'})
            # Each alternative can be assessed independently; no position-dependent references.
            multi=[truths[0], DISTRACTORS[group][n][0], truths[1], DISTRACTORS[group][n][1]]
            shift=n%4; multi=multi[-shift:]+multi[:-shift] if shift else multi
            keys=','.join(sorted('ABCD'[(i+shift)%4] for i in [0,2]))
            questions.append({**base,'canonicalId':concept+'-multiple','type':'MULTIPLE_CHOICE','content':f'关于{title}，下列哪些表述正确？（多选；少选、多选均判错）','options':[{'key':k,'text':v} for k,v in zip('ABCD',multi)],'correctAnswer':keys,'referenceAnswer':f'正确答案：{keys}。{explanation}'})
    manifest=json.loads((BANK/'sources/manifest.json').read_text(encoding='utf-8'))
    repo='lydiahallie/javascript-questions';commit=manifest[repo]['commit']
    data=(BANK/'sources/javascript-questions/README_zh_CN.md').read_text(encoding='utf-8')
    # Reviewed, environment-independent questions; omit ambiguous/outdated browser-global items.
    chosen={1,2,4,5,6,7,8,10,11,13,15,16,17,18,19,20,21,23,24,25,27,28,29,30,31,32,34,35,36,38,40}
    for part in re.split(r'(?m)^###### ',data)[1:]:
        number_match=re.match(r'\d+',part)
        if not number_match: continue
        number=int(number_match[0])
        if number not in chosen: continue
        head,rest=part.split('\n',1)
        body,details=rest.split('<details>',1)
        options=[{'key':k,'text':plain(v)} for k,v in re.findall(r'(?m)^- ([A-F]):\s*(.+)$',body)]
        match=re.search(r'####\s*答案\s*[:：]\s*([A-F])',details)
        assert match and len(options)>=2, f'Unrecognized source item {number}'
        answer=match[1]
        explanation=plain(details[match.end():].split('</details>')[0])
        content=plain(re.sub(r'(?m)^- [A-F]:.*$','',body))
        questions.append({'canonicalId':f'lydia-js-{number:03}-choice','conceptId':f'lydia-js-{number:03}','title':f'JavaScript：{head.split(". ",1)[1]}（{number}）','content':content or head.split('. ',1)[1],'type':'CHOICE','difficulty':2,'jobCodes':GROUPS['web'][0],'topics':['JavaScript','TypeScript'],'tags':['JavaScript'],'keywords':'','options':options,'correctAnswer':answer,'referenceAnswer':f'正确答案：{answer}。\n{explanation}','source':{'repo':repo,'commit':commit,'url':f'https://github.com/{repo}/blob/{commit}/zh-CN/README-zh_CN.md','license':'MIT','author':'Lydia Hallie 与中文翻译贡献者','checkedAt':'2026-10-09','upstreamQuestion':number,'adaptation':'选用经检查的中文选择题，清除展示标记；保留原题与解析、MIT 许可及固定提交归属。'}})
    assert len([q for q in questions if q['source'].get('repo')])==len(chosen)
    pack={'batchId':'objective-v1-20261009','reviewStatus':'APPROVED','companies':[],'questions':questions}
    spec=importlib.util.spec_from_file_location('importer',ROOT/'scripts/import-practice-pack.py')
    importer=importlib.util.module_from_spec(spec);spec.loader.exec_module(importer);importer.validate(pack)
    (BANK/'objective.json').write_text(json.dumps(pack,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    (BANK/'objective-sources.json').write_text(json.dumps({'checkedAt':'2026-10-09','github':{'repo':repo,'commit':commit,'license':'MIT','translationSha256':hashlib.sha256(data.encode()).hexdigest(),'selectedQuestions':sorted(chosen)},'originalReferences':{g:GROUPS[g][2] for g in GROUPS},'counts':dict(collections.Counter(q['type'] for q in questions))},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print('Built:',dict(collections.Counter(q['type'] for q in questions)))

if __name__=='__main__': build()
