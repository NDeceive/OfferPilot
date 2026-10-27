"""Curated Chinese pilot content; canonical tests are converted, never inferred."""
import json
import pathlib
import hashlib

ROOT = pathlib.Path(__file__).resolve().parents[1]
SOURCES = ROOT / '后端/src/main/resources/practice-bank/sources'
MANIFEST = json.loads((SOURCES / 'manifest.json').read_text(encoding='utf-8'))
STARTER = 'import java.io.*;\nimport java.util.*;\n\npublic class Main {\n    public static void main(String[] args) throws Exception {\n        // 根据题面读取标准输入，输出结果\n    }\n}\n'

def source(repo, path, license):
    commit = MANIFEST[repo]['commit']
    return {'repo': repo, 'commit': commit, 'path': path, 'url': f'https://github.com/{repo}/blob/{commit}/{path}', 'license': license,
            'author': repo.split('/')[0], 'adaptation': 'OfferPilot 中文改编；输入输出适配及自评要点由项目整理', 'reviewedAt': '2026-10-08'}

# title | topic | tags | answer. These are independently worded questions and review notes, not copied articles.
JAVA = '''
equals 与 hashCode 为什么需要一起重写？|Java核心|Java|相等对象必须拥有相同哈希值，反向不成立。哈希集合先定位桶再判断相等；只重写 equals 会破坏查找一致性。自评：是否解释契约、冲突及可变键风险。
String 不可变性带来哪些收益和限制？|Java核心|Java|不可变有利于共享、哈希缓存和线程安全，修改会创建新对象。大量循环拼接宜使用 StringBuilder；并不意味着包含 String 的对象自动线程安全。
重载与重写分别在什么时候确定？|Java核心|Java|重载依据参数列表在编译期选择，重写由运行期对象类型参与动态分派。自评：是否说明返回类型不能单独区分重载，以及静态方法不参与同样的动态分派。
泛型类型擦除有什么影响？|Java核心|Java|泛型主要提供编译期类型约束，运行期通常保留原始类型并插入转换。不能直接创建泛型数组或用类型参数实例化对象；需区分泛型元数据与实例类型检查。
try-with-resources 如何处理多个异常？|Java核心|Java|资源按声明逆序关闭。主体抛出的异常优先保留，关闭异常可作为 suppressed exception 附加。自评：是否说明 AutoCloseable 与异常信息保留。
ArrayList 与 LinkedList 的取舍是什么？|Java核心|Java、数据结构|ArrayList 随机访问快但中间插入需搬移元素；LinkedList 定位通常需遍历，且节点有额外内存成本。不能仅凭链表插入常数时间就断言其整体更快。
HashMap 为什么需要扩容？|Java核心|Java、HashMap|扩容降低桶的负载与冲突，但会带来重新分布和瞬时开销。自评：是否解释容量、负载因子、相等判断，以及普通 HashMap 不能用于无同步并发写入。
JVM 运行时内存区域如何区分？|JVM|JVM|堆主要存放对象，虚拟机栈维护线程调用帧，方法区承载类相关信息，程序计数器记录执行位置。需区分规范概念与具体实现，并说明线程共享和私有区域。
如何判断对象能否被垃圾回收？|JVM|JVM|可达性分析从 GC Roots 出发判断对象是否可达，而非简单按引用次数。自评：是否解释循环引用、强弱引用及不可达与立即回收的区别。
类加载的双亲委派有什么作用？|JVM|JVM|优先委派父加载器可减少重复加载并保护基础类型的一致性，但不是所有加载场景都严格遵循。自评：是否说明类型身份与类加载器有关，且加载不等于初始化。
volatile 能保证哪些并发性质？|并发编程|多线程、Java|volatile 提供可见性及相关有序性，不保证复合操作原子性。计数自增仍需锁或原子类。自评：是否使用读改写过程说明原因。
synchronized 与 ReentrantLock 如何选择？|并发编程|多线程、Java|两者均可实现互斥与可见性。显式锁提供可中断、限时尝试和多个条件队列，但必须可靠释放。选择应基于控制需求与可维护性，而非笼统的性能结论。
线程池队列满了该怎么办？|并发编程|多线程、Java|应依据任务性质设计容量、超时、背压与拒绝策略，并监控队列等待和失败。无限队列会掩盖过载；不能无条件丢弃重要任务或无限重试。
CAS 为什么会遇到 ABA 问题？|并发编程|多线程、Java|值从 A 变 B 再回 A，比较相等可能遗漏中间变化。版本戳可辅助识别；是否需要处理取决于算法对历史变化的敏感性，并需考虑自旋成本。
Spring 单例 Bean 是否线程安全？|Spring|Spring|单例描述实例数量，不提供线程安全保证。无状态 Bean 较易安全共享；可变共享状态需同步、隔离或重新设计。自评：是否区分容器作用域与并发访问。
如何理解事务 ACID？|MySQL|MySQL、数据库|原子性关注事务整体提交或回滚，一致性依赖约束和正确业务逻辑，隔离性限制并发干扰，持久性关注提交后的保存。不能把所有业务一致性都归因于数据库隔离。
事务隔离级别解决了哪些并发异常？|MySQL|MySQL、数据库|隔离级别控制脏读、不可重复读等现象；具体行为还受数据库实现和读写方式影响。自评：是否用并发例子说明异常，并区分快照读与锁定读。
B+ 树索引为什么适合数据库查询？|MySQL|MySQL、数据库|较高扇出减少树高，叶子有序且相连便于范围扫描。索引仍有存储和写入维护成本；其效果取决于选择性、查询条件及访问模式。
联合索引与最左前缀如何理解？|MySQL|MySQL、数据库|联合索引按字段顺序组织，前导字段通常决定可利用的扫描范围。需结合等值、范围条件与执行计划分析；不要把经验规则当成所有优化器行为的绝对结论。
如何诊断慢 SQL？|MySQL|MySQL、数据库|先确认耗时、频率与数据规模，再看执行计划、扫描行数、索引和锁等待。优化前后使用同条件验证；避免只看到慢就盲目加索引。
Redis 缓存穿透如何处理？|Redis|Redis|对不存在数据的重复查询可结合参数校验、短期空值缓存或布隆过滤器。方案需考虑误判、过期策略及合法新增数据，不应无限缓存不存在结果。
缓存击穿与雪崩有什么区别？|Redis|Redis|击穿通常是热点键失效，雪崩是大面积同时失效或缓存服务故障。可按情境采用过期时间抖动、互斥重建、限流和降级，并评估恢复过程。
为什么网络请求重试需要幂等设计？|分布式|分布式|超时不代表服务端未执行，重试可能造成重复业务操作。应通过业务唯一键、幂等记录与一致性约束控制重复，并设置有限重试和退避。
HTTP 与 HTTPS 的主要区别是什么？|网络|HTTP/HTTPS|HTTPS 在 HTTP 通信之外使用 TLS 提供传输保护与身份验证。保护传输并不自动解决应用鉴权、输入验证或终端被攻破问题。
Cookie 与 Session 如何协作？|网络|HTTP/HTTPS|Cookie 是浏览器保存并发送的数据机制，Session 是服务端会话状态方案，常通过 Cookie 携带会话标识。需说明失效、Secure、HttpOnly 和跨站请求风险。
'''
FRONT = '''
var、let 与 const 的作用域有什么区别？|JavaScript|JavaScript/Typescript|var 通常为函数作用域，let 与 const 为块作用域并存在暂时性死区。const 限制绑定重新赋值，不使对象内部属性不可变。
闭包为什么可能造成内存滞留？|JavaScript|JavaScript/Typescript|函数保留对词法环境的访问，仍被引用的闭包可能延长捕获对象的生命周期。自评：是否区分有用的状态封装与不必要的长期引用。
Promise.then 与同步日志的执行顺序是什么？|JavaScript|JavaScript/Typescript|当前同步任务先完成，随后执行相关微任务。需结合实际代码区分 Promise 执行器同步执行与 then 回调排队，不能简单说 Promise 全部异步。
async 函数抛出异常后如何捕获？|JavaScript|JavaScript/Typescript|async 函数返回 Promise，异常表现为拒绝。调用者可 await 后用 try/catch，或使用 catch；只用同步 try/catch 包住未 await 的调用通常捕获不到拒绝。
普通函数与箭头函数的 this 有何区别？|JavaScript|JavaScript/Typescript|普通函数的 this 与调用方式有关；箭头函数使用创建时词法环境中的 this。call、apply、bind 不会为箭头函数重新绑定 this。
浅拷贝与深拷贝各有哪些限制？|JavaScript|JavaScript/Typescript|浅拷贝复制第一层属性，嵌套对象仍共享。深拷贝需处理循环引用及特殊类型；JSON 序列化会丢失或改变部分值，不能普遍替代深拷贝。
原型链查找如何工作？|JavaScript|JavaScript/Typescript|属性先在对象自身查找，再沿原型链逐级查找。应区分自身属性与继承属性，并注意原型修改和不受控属性合并带来的风险。
TypeScript 类型检查能替代接口数据校验吗？|TypeScript|JavaScript/Typescript|静态类型通常不在运行期验证外部数据。接口响应、用户输入仍需运行期检查；类型断言只是告诉编译器如何理解数据，不会实际转换或校验。
事件委托有什么收益和边界？|浏览器原理|浏览器、JavaScript/Typescript|利用事件传播在祖先统一处理后代事件，减少监听器并支持动态元素。需正确识别目标，并考虑不冒泡事件、传播阻止与可访问性。
浏览器缓存协商如何工作？|浏览器原理|浏览器、HTTP/HTTPS|强缓存依据有效期复用，协商缓存通过验证器向服务端确认是否变化。304 表示可复用已有内容；缓存策略应区分静态资源与敏感数据。
跨域限制为什么不能只靠前端解决？|浏览器原理|浏览器、HTTP/HTTPS|同源策略由浏览器实施，CORS 授权通常由服务端响应头控制。代理改变请求路径但不替代鉴权；携带凭据时不能使用任意来源授权。
组件状态更新为什么要注意不可变数据？|Vue / React|Vue、React|明确状态变化有利于追踪更新和避免共享引用带来的意外修改。不同框架的响应式机制不同，不能套用单一规则；应结合具体组件说明更新如何触发。
如何减少首屏 JavaScript 开销？|工程化|Webpack、工程化|结合分析结果拆包、按需加载和去除未使用依赖，避免盲目增加碎片请求。验证压缩体积、下载、解析执行时间及实际用户指标。
防抖与节流分别适合哪些场景？|性能优化|性能、JavaScript/Typescript|防抖等待一段静默时间再执行，节流限制执行频率。搜索建议与高频滚动可能采用不同策略，并需处理取消、最后一次调用及组件销毁。
如何分析页面交互卡顿？|性能优化|性能、浏览器|先使用性能记录定位主线程长任务、布局与绘制，再针对瓶颈处理。减少同步工作、批量更新或移至 Worker 前，需验证数据传递成本与交互正确性。
'''

def knowledge():
    result=[]
    for family, text, repo, license in [('java',JAVA,'Snailclimb/JavaGuide','Apache-2.0'),('web',FRONT,'yangshun/front-end-interview-handbook','MIT')]:
        for index,line in enumerate(text.strip().splitlines(),1):
            title,topic,tags,answer=line.split('|')
            src=source(repo,'README.md',license)
            src['adaptation']='OfferPilot 原创措辞的问题与自评要点；参考该仓库所列知识主题，不是逐字摘录或企业真题'
            result.append({'canonicalId':f'offerpilot:{family}:2026-10:{index}', 'type':'KNOWLEDGE','title':title,
                'content':title+'\n请解释原因，并举例说明适用范围或边界条件。','referenceAnswer':answer,'keywords':'', 'difficulty':1,
                'jobCodes':['BE-JAVA','FS-JAVA'] if family=='java' else ['FE-WEB','FS-JAVA','FS-NODE'], 'topics':[topic], 'tags':tags.split('、'), 'source':src})
    return result

def flat(cases):
    for case in cases:
        if 'cases' in case: yield from flat(case['cases'])
        else: yield case

def output(value):
    if isinstance(value,bool): return str(value).lower()
    if isinstance(value,list): return ' '.join(map(str,value))
    return str(value)

# slug: title, explanation, tags, Java reference body. UTF-8 input/output; no runtime dependencies.
CODE = {
'two-fer': ('分享一句话','读取一行姓名；空行表示没有姓名。输出 One for NAME, one for me.；没有姓名时 NAME 使用 you。姓名长度不超过100。','字符串', 'String s=r.readLine();System.out.println("One for "+(s.isEmpty()?"you":s)+", one for me.");'),
'leap': ('判断闰年','读取公历年份，输出 true 或 false。能被4整除且不能被100整除，或能被400整除的年份为闰年。年份1–9999。','数学','int y=Integer.parseInt(r.readLine());System.out.println(y%4==0&&(y%100!=0||y%400==0));'),
'raindrops': ('雨滴转换','读取正整数。含因子3输出Pling，含因子5输出Plang，含因子7输出Plong，按此顺序拼接；都不含则输出原数。数值不超过1000000。','数学','int n=Integer.parseInt(r.readLine());String s=(n%3==0?"Pling":"")+(n%5==0?"Plang":"")+(n%7==0?"Plong":"");System.out.println(s.isEmpty()?n:s);'),
'hamming': ('汉明距离','读取两行DNA字符串，字符为A/C/G/T，计算同位置不同字符数。长度可为0且不超过1000；长度不同输出 error。','字符串','String a=r.readLine(),b=r.readLine();if(a.length()!=b.length()){System.out.println("error");return;}int n=0;for(int i=0;i<a.length();i++)if(a.charAt(i)!=b.charAt(i))n++;System.out.println(n);'),
'difference-of-squares': ('平方差','读取整数n（0–100）。输出前n个正整数之和的平方与前n个正整数平方之和的差。','数学','long n=Long.parseLong(r.readLine()),s=n*(n+1)/2;System.out.println(s*s-n*(n+1)*(2*n+1)/6);'),
'grains': ('棋盘上的麦粒','读取格子编号1–64，第一格1粒，每格翻倍，输出该格麦粒数；超出范围输出error。使用大整数避免溢出。','数学','int n=Integer.parseInt(r.readLine());System.out.println(n<1||n>64?"error":java.math.BigInteger.ONE.shiftLeft(n-1));'),
'armstrong-numbers': ('阿姆斯特朗数','读取非负整数n（最多40位）。若各位数字的位数次幂之和等于n，输出true，否则false。','数学','String s=r.readLine();java.math.BigInteger n=new java.math.BigInteger(s),sum=java.math.BigInteger.ZERO;for(char c:s.toCharArray())sum=sum.add(java.math.BigInteger.valueOf(c-48).pow(s.length()));System.out.println(sum.equals(n));'),
'isogram': ('判断等值线单词','读取一行英文短语，只检查英文字母是否重复，忽略大小写、空格与连字符。无重复输出true，否则false。长度不超过1000。','字符串','String s=r.readLine().toLowerCase(Locale.ROOT);Set<Character> a=new HashSet<>();boolean ok=true;for(char c:s.toCharArray())if(c>=97&&c<=122&&!a.add(c))ok=false;System.out.println(ok);'),
'pangram': ('判断全字母句','读取一行文本，判断是否包含全部26个英文字母，忽略大小写和非字母字符。输出true或false。长度不超过1000。','字符串','Set<Integer> s=new HashSet<>();r.readLine().toLowerCase(Locale.ROOT).chars().filter(c->c>=97&&c<=122).forEach(s::add);System.out.println(s.size()==26);'),
'reverse-string': ('按字符反转文本','读取一行UTF-8文本（可为空，不超过1000个Unicode码点），按Unicode码点逆序输出。本题不要求将组合字符或字素簇作为整体。','字符串','int[] a=r.readLine().codePoints().toArray();StringBuilder s=new StringBuilder();for(int i=a.length-1;i>=0;i--)s.appendCodePoint(a[i]);System.out.println(s);'),
'binary-search': ('二分查找','第一行读取目标整数，第二行读取空格分隔的严格递增整数数组（可为空，最多1000项）。输出0起始下标，未找到输出-1。','查找','int t=Integer.parseInt(r.readLine());String s=r.readLine().trim();int[] a=s.isEmpty()?new int[0]:Arrays.stream(s.split("\\\\s+")).mapToInt(Integer::parseInt).toArray();int i=Arrays.binarySearch(a,t);System.out.println(i<0?-1:i);'),
'gigasecond': ('十亿秒之后','读取ISO本地日期或日期时间，如2011-04-25或2015-01-24T22:00:00。日期按00:00:00处理，增加1000000000秒，输出yyyy-MM-ddTHH:mm:ss。不涉及时区。','日期','String s=r.readLine();var t=s.length()==10?java.time.LocalDate.parse(s).atStartOfDay():java.time.LocalDateTime.parse(s);System.out.println(t.plusSeconds(1000000000L).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd\'T\'HH:mm:ss")));'),
'roman-numerals': ('整数转罗马数字','读取整数1–3999，输出标准罗马数字。使用I/V/X/L/C/D/M和减法表示，如4为IV、9为IX。','字符串','int n=Integer.parseInt(r.readLine());int[] v={1000,900,500,400,100,90,50,40,10,9,5,4,1};String[] z={"M","CM","D","CD","C","XC","L","XL","X","IX","V","IV","I"};StringBuilder s=new StringBuilder();for(int i=0;i<v.length;i++)while(n>=v[i]){s.append(z[i]);n-=v[i];}System.out.println(s);'),
'rna-transcription': ('DNA 转录','读取一行DNA序列（可为空，长度不超过1000）。按G→C、C→G、T→A、A→U转换为RNA并输出；只接受A/C/G/T。','字符串','String s=r.readLine();StringBuilder b=new StringBuilder();for(char c:s.toCharArray())b.append(switch(c){case \'G\'->\'C\';case \'C\'->\'G\';case \'T\'->\'A\';case \'A\'->\'U\';default->throw new IllegalArgumentException();});System.out.println(b);'),
'acronym': ('生成英文缩略词','读取一行英文短语，以空格、连字符及下划线划分单词，忽略其他非字母字符，输出各单词首字母的大写拼接。长度不超过1000。','字符串','StringBuilder b=new StringBuilder();for(String w:r.readLine().split("[\\\\s_\\\\-]+")){String s=w.replaceAll("[^A-Za-z]","");if(!s.isEmpty())b.append(Character.toUpperCase(s.charAt(0)));}System.out.println(b);'),
'collatz-conjecture': ('考拉兹步数','读取整数n。正数时按偶数除2、奇数乘3加1，直到1，输出步骤数；n≤0输出error。正数不超过1000000，使用long计算中间值。','数学','long n=Long.parseLong(r.readLine());if(n<=0){System.out.println("error");return;}int k=0;while(n!=1){n=n%2==0?n/2:3*n+1;k++;}System.out.println(k);'),
'sum-of-multiples': ('倍数之和','第一行读取上界limit（0–100000），第二行读取非负因子列表（可为空，最多20项，每项不超过1000）。输出所有小于limit的正整数中、至少是一个非零因子倍数的数的总和，重复值只计一次。','数组','int n=Integer.parseInt(r.readLine());String s=r.readLine().trim();int[] a=s.isEmpty()?new int[0]:Arrays.stream(s.split("\\\\s+")).mapToInt(Integer::parseInt).toArray();long sum=0;for(int i=1;i<n;i++)for(int v:a)if(v!=0&&i%v==0){sum+=i;break;}System.out.println(sum);'),
'allergies': ('过敏位标记','读取整数score（0–1000）及待查询英文项目，共两行。eggs/peanuts/shellfish/strawberries/tomatoes/chocolate/pollen/cats依次对应1/2/4/8/16/32/64/128；对应位存在则输出true，否则false，高位忽略。','位运算','int n=Integer.parseInt(r.readLine());String t=r.readLine();List<String> a=List.of("eggs","peanuts","shellfish","strawberries","tomatoes","chocolate","pollen","cats");int i=a.indexOf(t);System.out.println(i>=0&&(n&(1<<i))!=0);'),
'darts': ('飞镖计分','一行读取坐标x y（绝对值不超过100）。到原点距离≤1得10分，≤5得5分，≤10得1分，否则0分，边界包含在内。','数学','String[] s=r.readLine().trim().split("\\\\s+");double x=Double.parseDouble(s[0]),y=Double.parseDouble(s[1]),d=x*x+y*y;System.out.println(d<=1?10:d<=25?5:d<=100?1:0);'),
'resistor-color-duo': ('电阻色环值','读取空格分隔的颜色，取前两个作为十位和个位，其余忽略。black/brown/red/orange/yellow/green/blue/violet/grey/white依次为0–9。至少两个有效颜色，输出两位组合的整数值。','数组','String[] s=r.readLine().split("\\\\s+");List<String> a=List.of("black","brown","red","orange","yellow","green","blue","violet","grey","white");System.out.println(a.indexOf(s[0])*10+a.indexOf(s[1]));')
}

def converted(slug,case):
    x,e,p=case['input'],case['expected'],case['property']
    if slug=='difference-of-squares' and p!='differenceOfSquares': return None
    if slug=='grains' and p!='square': return None
    if slug=='allergies' and p!='allergicTo': return None
    if slug=='gigasecond' and not isinstance(e,str) or slug=='gigasecond' and e=='moment === moment': return None
    if slug=='two-fer': inp=x.get('name') or ''
    elif slug=='leap': inp=str(x['year'])
    elif slug in ['raindrops','armstrong-numbers','roman-numerals','collatz-conjecture','difference-of-squares']: inp=str(x['number'])
    elif slug=='hamming': inp=x['strand1']+'\n'+x['strand2']
    elif slug=='grains': inp=str(x['square'])
    elif slug=='isogram': inp=x['phrase']
    elif slug=='pangram': inp=x['sentence']
    elif slug=='reverse-string':
        if 'grapheme cluster' in case.get('description',''): return None
        inp=x['value']
    elif slug=='binary-search': inp=str(x['value'])+'\n'+output(x['array'])
    elif slug=='gigasecond': inp=x['moment']
    elif slug=='rna-transcription': inp=x['dna']
    elif slug=='acronym': inp=x['phrase']
    elif slug=='sum-of-multiples': inp=str(x['limit'])+'\n'+output(x['factors'])
    elif slug=='allergies': inp=str(x['score'])+'\n'+x['item']
    elif slug=='darts': inp=str(x['x'])+' '+str(x['y'])
    elif slug=='resistor-color-duo': inp=output(x['colors'])
    else: raise ValueError(slug)
    if isinstance(e,dict): e='-1' if slug=='binary-search' else 'error'
    return {'input':inp+'\n','expected':output(e),'uuid':case['uuid']}

def coding():
    result=[]
    for slug,(title,description,tags,body) in CODE.items():
        data=json.loads((SOURCES/'problem-specifications'/f'{slug}.json').read_text(encoding='utf-8'))
        tests=[v for c in flat(data['cases']) if (v:=converted(slug,c)) is not None]
        # Two public examples; the remaining upstream public cases are withheld by our API, not claimed as secret upstream tests.
        for i,t in enumerate(tests): t['public']='true' if i<2 else 'false'
        verification_tests=tests
        if len(tests)>12:
            indexes=sorted({0,1,*[2+round(i*(len(tests)-3)/9) for i in range(10)]})
            tests=[tests[i] for i in indexes]
        ref='import java.io.*;import java.util.*;public class Main {public static void main(String[] args)throws Exception{BufferedReader r=new BufferedReader(new InputStreamReader(System.in,java.nio.charset.StandardCharsets.UTF_8));'+body+'}}'
        src=source('exercism/problem-specifications',f'exercises/{slug}/canonical-data.json','MIT')
        src['canonicalSha256']=MANIFEST['exercism/problem-specifications']['files'][slug]
        src['testAdaptation']='按题目属性选择canonical用例，异常映射为题面指定的error或-1；运行仅公开示例，正式提交最多12个代表用例。'
        if slug=='reverse-string': src['testAdaptation']+='本题采用Unicode码点反转，排除上游两个可选字素簇用例。'
        result.append({'canonicalId':f'exercism:{slug}','type':'CODING','title':title,'content':description+'\n请使用 Java 17，标准输出仅包含答案。','difficulty':1,
            'jobCodes':['BE-JAVA','FE-WEB','FS-JAVA','FS-NODE'],'topics':['编程与算法','Java核心'],
            'tags':tags.split('、'),'source':src,'language':'java','starter':STARTER,'examples':tests[:2],'tests':tests,'verificationTests':verification_tests,'referenceCode':ref})
    return result

def companies():
    definitions=[('bytedance','字节跳动',['ByteDance'],'https://jobs.bytedance.com/experienced/m/culture',[('candid','坦诚清晰','问题沟通应准确直接，结论应有事实依据。'),('practical','求真务实','通过一手信息验证问题，关注实际效果。')]),
        ('alibaba','阿里巴巴',['Alibaba'],'https://www.alibabagroup.com/en-US/about-alibaba',[('customer','客户第一','决策关注持续的客户价值。')]),
        ('tencent','腾讯',['Tencent'],'https://www.tencent.com/who-we-are/',[('user','用户价值','产品与服务以用户价值为指引。'),('collaboration','协作','倡导协作并关注共同贡献。')])]
    return [{'code':code,'name':name,'aliases':aliases,'evidence':{'checkedAt':'2026-10-08','scope':'企业文化公开资料；不证明招聘考点、技术栈或出题频率',
        'items':[{'id':id,'label':label,'summary':summary,'url':url,'type':'OFFICIAL_CULTURE'} for id,label,summary in items]}} for code,name,aliases,url,items in definitions]

def culture(company):
    # Distinct scenarios per enterprise, with independent wording and explicit evaluation points.
    stems={
    'bytedance': [('指标口径不一致','两支团队对同一功能给出相反结论，发现指标定义不一致。如何核实事实、沟通分歧并决定下一步？'),('发现错误假设','你发现上线方案的核心假设缺少证据，但评审已通过。如何直接说明问题并提出可验证的替代方案？'),('复盘责任与事实','一次故障复盘中讨论开始归责个人。如何用时间线和可验证信息推进复盘？')],
    'alibaba': [('客户需求与可靠性','重要客户要求立即上线功能，但方案存在稳定性风险。如何确认真实需求、制定替代交付并说明取舍？'),('短期指标与长期体验','一项改动能提高短期转化，却可能增加客户操作负担。如何收集反馈并决定是否推进？'),('承诺与交付范围','客户希望扩大已承诺的交付范围，现有时间无法满足。如何明确优先级、协商范围并维护信任？')],
    'tencent': [('跨团队协作受阻','用户问题横跨两个团队，双方都认为不属于自己的职责。如何协调行动并让用户问题得到解决？'),('用户体验与发布节奏','团队希望按时发布，但部分用户遇到明显体验退化。如何判断影响、协作修复并安排发布？'),('可访问性需求','少量用户反馈关键操作难以使用，团队担心修复影响既定计划。如何评估用户价值并推动协作？')]
    }
    out=[]
    for role,roleLabel in [('BE-JAVA','Java后端'),('FE-WEB','Web前端')]:
        for index,(title,stem) in enumerate(stems[company['code']],1):
            evidence=company['evidence']
            answer={'bytedance':'企业资料依据：坦诚清晰与求真务实。说明如何暴露待验证的问题，用事实区分假设和结论，并让沟通准确可执行。','alibaba':'企业资料依据：客户第一。说明真实客户需求与短期要求是否一致，比较短期交付和持续客户价值，避免无条件承诺。','tencent':'企业资料依据：用户价值与协作。说明用户影响、跨团队依赖与共同目标，明确责任交接和协作机制。'}[company['code']]+'\n'+('岗位核查：后端关注日志、依赖、容量、数据一致性和回滚验证。' if role=='BE-JAVA' else '岗位核查：前端关注真实交互、兼容性、性能记录、可访问性和发布回退。')+'\n自评要点：先识别受影响的用户、约束与待验证假设；收集可验证数据；提出至少两种可执行方案并比较风险、成本和收益；明确负责人、沟通对象与验证指标；说明回滚或后续复盘安排。\n情境允许多种合理答案，评价论证与行动，不以复述企业口号作为正确标准。'
            out.append({'canonicalId':f"offerpilot:culture:{company['code']}:{role}:{index}",'type':'CULTURE','companyCode':company['code'],'title':title+' · '+roleLabel,
                'content':stem+'\n请结合'+roleLabel+'岗位，说明信息收集、行动步骤、沟通方式与取舍依据。','referenceAnswer':answer,'keywords':'','difficulty':1,
                'jobCodes':[role],'topics':['企业情境','系统设计','项目场景'] if role=='BE-JAVA' else ['企业情境','项目场景','性能优化'], 'tags':['决策取舍','协作沟通'],
                'source':{'url':evidence['items'][0]['url'],'license':'OfferPilot-original','author':'OfferPilot','adaptation':'依据企业公开资料原创改编，不代表企业真实面试题',
                    'evidence':evidence,'evidenceIds':[i['id'] for i in evidence['items']],'reviewedAt':'2026-10-08'}})
    return out

def main():
    cs=companies(); qs=knowledge()+coding()+[q for c in cs for q in culture(c)]
    pack={'batchId':'pilot-2026-10-v1','reviewStatus':'APPROVED','reviewNote':'项目内容审阅及自动校验；编程参考解法需由 verify-practice-pack.py 验证。不是外部专家认证。','companies':cs,'questions':qs}
    target=ROOT/'后端/src/main/resources/practice-bank/pilot.json';target.parent.mkdir(parents=True,exist_ok=True)
    target.write_text(json.dumps(pack,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print('Built 40 knowledge + 20 coding + 18 company scenarios, 3 company profiles.')

if __name__=='__main__':main()
