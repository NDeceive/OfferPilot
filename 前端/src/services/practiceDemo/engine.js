// Temporary front-end demonstration. No backend writes and no executable user code.
import questions from './questions.json' with { type: 'json' }

export const demoStoragePrefix = 'offerpilot:practice-demo:v1:'
const clone = value => JSON.parse(JSON.stringify(value))
const stamp = () => new Date(Date.now()+8*60*60*1000).toISOString().replace('Z','+08:00')
const neutral = (submission, finished) => !submission || finished ? submission : {
  id:submission.id, sessionQuestionId:submission.sessionQuestionId, answerText:submission.answerText,
  sourceCode:submission.sourceCode, createdAt:submission.createdAt, status:'SUBMITTED',
  result:{submissionId:submission.id,status:'SUBMITTED',submitted:true,message:'答案已记录，练习结束后统一查看结果。'},
}

export function roughCodeResult(source, question) {
  const text=String(source).replace(/\/\*[\s\S]*?\*\/|\/\/[^\n]*/g,'').trim()
  if (!/class\s+Main\b/.test(text) || !/static\s+void\s+main\s*\(/.test(text) || !/System\.out\.(?:print|println|printf)\s*\(/.test(text) || /=\s*;/.test(text))
    return {status:'COMPILE_ERROR',passed:false,message:'样例检查未发现完整的 Main 入口与输出，或存在明显空赋值。',compileOutput:'样例检查：请提供 public class Main、main 方法和有效输出语句。'}
  const rules={leap:[/%\s*4\b/,/%\s*100\b/,/%\s*400\b/],hamming:[/(?:charAt|toCharArray)/,/(?:\+\+|\+=\s*1)/],grains:[/(?:BigInteger|shiftLeft)/], 'reverse-string':[/reverse\s*\(|codePoints|length\s*\(\)\s*-\s*1/], 'binary-search':[/binarySearch|(?:while|for)[\s\S]*(?:mid|middle|\/\s*2)/], 'collatz-conjecture':[/[\/%]\s*2/,/\*\s*3|3\s*\*/], 'roman-numerals':[/900/,/IV/], 'rna-transcription':[/case\s*'G'|replace/,/'U'|"U"/], pangram:[/26/], 'armstrong-numbers':[/pow\s*\(/], 'gigasecond':[/1000000000/,/plusSeconds/], 'sum-of-multiples':[/[%]/,/for|while/], allergies:[/&/],darts:[/sqrt|hypot|\*|pow/],isogram:[/Set|distinct/],acronym:[/toUpperCase/],raindrops:[/Pling/,/Plang/,/Plong/], 'two-fer':[/One for/,/one for me/], 'difference-of-squares':[/\*|pow/], 'resistor-color-duo':[/black/,/brown/]}
  const checks=rules[question.canonicalId.split(':').pop()] || [/readLine|Scanner|nextInt/,/for|while|\+|\*|%/]
  const passed=checks.every(pattern=>pattern.test(text))
  return {status:passed?'ACCEPTED':'WRONG_ANSWER',passed,message:passed?'样例规则识别到题目所需的主要结构。':'样例规则未识别到题目所需的主要处理逻辑。',compileOutput:'仅进行本地文本规则检查，未调用真实编译器。'}
}

export function createDemoEngine(storage, owner) {
  let roles=[]
  const key=()=>demoStoragePrefix+String(owner() || 'guest')
  const read=()=>JSON.parse(storage.getItem(key()) || '{"sessions":[],"tasks":[],"sequence":0}')
  const write=data=>storage.setItem(key(),JSON.stringify(data))
  const find=(data,id)=>{const s=data.sessions.find(s=>String(s.id)===String(id));if(!s)throw Error('这个样例练习不存在，请返回学习资源重新创建。');return s}
  const latest=(s,qid)=>s.submissions.find(item=>String(item.sessionQuestionId)===String(qid))
  const summary=s=>({id:s.id,role:s.role,company:s.company,topic:s.topic,questionType:s.questionType,questionCount:s.questionList.length,completed:s.questionList.filter(q=>latest(s,q.id)).length,status:s.status,startedAt:s.startedAt,updatedAt:s.updatedAt})
  const present=s=>{
    const finished=s.status==='FINISHED'
    return {...summary(s),deferFeedback:true,demoMode:true,requestedCount:s.questionList.length,currentIndex:Math.max(0,s.questionList.findIndex(q=>!latest(s,q.id))),submissions:s.submissions.map(item=>neutral(item,finished)),questionList:s.questionList.map(q=>{
      const {correctAnswer,referenceAnswer,referenceCode,tests,keywords,...publicQ}=q
      const item=latest(s,q.id)
      return {...publicQ,description:q.content,type:q.type.toLowerCase(),questionType:q.type,starter:q.starter || '',status:item?'ANSWERED':'PENDING',latestSubmission:neutral(item,finished),referenceAnswer:finished?referenceAnswer || (referenceCode ? '参考实现（供样例核对）：\n'+referenceCode : ''):undefined,reviewState:finished?q.reviewState || '':'',constraints:q.type==='CODING'?['Java 17 样例，标准输入输出','本次仅做本地粗略检查，结束后统一展示结果']:['作答期间不展示答案与判分，结束后统一结算']}
    }),matching:{notice:'样例演示：题目与记录保存在当前浏览器，结算结果仅用于功能展示。'}}
  }
  function catalog(){return {ready:true,demoMode:true,companies:[],codingCoverage:questions.filter(q=>q.type==='CODING').map(q=>({jobCodes:roles.map(r=>r.code),topics:q.topics})),objectiveCoverage:questions.filter(q=>q.options).map(q=>({type:q.type,difficulty:q.difficulty,jobCodes:q.jobCodes,topics:q.topics}))}}
  function overview(){const data=read(),sessions=data.sessions;return {libraryCounts:{recent:sessions.length,favorites:sessions.flatMap(s=>s.questionList).filter(q=>q.favorite).length,mistakes:sessions.filter(s=>s.status==='FINISHED').flatMap(s=>s.questionList.filter(q=>latest(s,q.id)?.isCorrect===false||q.reviewState==='PENDING')).length},recentSession:sessions.find(s=>s.status==='ONGOING')?summary(sessions.find(s=>s.status==='ONGOING')):null,recentSessions:sessions.slice(0,5).map(summary)}}
  function create(input, originalQuestions){
    const data=read(),role=roles.find(r=>String(r.id)===String(input.jobId)) || data.sessions.find(s=>String(s.role.id)===String(input.jobId))?.role || roles[0]
    if(!role) throw Error('请先从学习资源选择演示岗位。')
    const type=String(input.questionType || 'all').toUpperCase(),difficulty=({basic:1,medium:2,advanced:3,all:3})[input.difficulty] || 3
    const broad=['综合高频','项目场景'].includes(input.topic)
    const pool=questions.filter(q=>q.difficulty<=difficulty && (q.type==='CODING'||q.jobCodes.includes(role.code)) && (broad || input.topic==='编程与算法'&&q.type==='CODING'||q.topics.includes(input.topic)||q.demoCommon&&input.topic!=='编程与算法'))
    let selected=originalQuestions ? clone(originalQuestions) : []
    if(!originalQuestions){
      const count=Math.max(1,Math.min(50,Number(input.questionCount)||10))
      if(type==='ALL'){
        const coding=pool.filter(q=>q.type==='CODING').slice(0,Math.max(2,Math.round(count*.25)))
        const nonCoding=pool.filter(q=>q.type!=='CODING'),used=new Set()
        for(const t of ['CHOICE','MULTIPLE_CHOICE','TRUE_FALSE','KNOWLEDGE']) {const q=nonCoding.find(q=>q.type===t&&!used.has(q.conceptId||q.canonicalId));if(q){selected.push(q);used.add(q.conceptId||q.canonicalId)}}
        for(const q of nonCoding){if(selected.length>=count-coding.length)break;if(!used.has(q.conceptId||q.canonicalId)){selected.push(q);used.add(q.conceptId||q.canonicalId)}}
        for(const q of coding)selected.splice(Math.min(selected.length,Math.floor(selected.length/2)),0,q)
        selected=selected.slice(0,count)
      }else selected=pool.filter(q=>q.type===type || type==='KNOWLEDGE'&&q.type==='CULTURE').slice(0,count)
    }
    if(!selected.length) throw Error('该专项暂时没有对应样例，请选择综合高频或其他题型。')
    const id=`demo-${Date.now()}-${++data.sequence}`
    const s={id,role:clone(role),company:input.company||'不限公司',topic:input.topic,questionType:type,difficulty:input.difficulty,status:'ONGOING',startedAt:stamp(),updatedAt:stamp(),submissions:[],questionList:selected.map((q,n)=>({...clone(q),id:`${id}-q${n+1}`,sequenceNo:n+1,favorite:false,reviewState:'',selfAssessment:null,gradingMode:q.options?'OBJECTIVE':q.type==='CODING'?'CODE':'SELF_REVIEW'}))}
    data.sessions.unshift(s);write(data);return present(s)
  }
  function report(s){
    const base=present(s),stats={total:s.questionList.length,submitted:0,unsubmitted:0,codingPassed:0,codingFailed:0,codingEvaluated:0,selfReview:0,objectiveCorrect:0,objectiveIncorrect:0,legacyReview:0}
    if(s.status!=='FINISHED')return {...base,statistics:{...stats,submitted:base.completed,unsubmitted:stats.total-base.completed},settlementStatus:'DEFERRED',pendingCount:0,systemFailureCount:0}
    for(const q of s.questionList){const item=latest(s,q.id);if(!item)continue;stats.submitted++;if(q.type==='CODING'){stats.codingEvaluated++;stats[item.isCorrect?'codingPassed':'codingFailed']++}else if(q.options)stats[item.isCorrect?'objectiveCorrect':'objectiveIncorrect']++;else if(!q.selfAssessment)stats.selfReview++}
    stats.unsubmitted=stats.total-stats.submitted
    const evaluated=stats.codingEvaluated+stats.objectiveCorrect+stats.objectiveIncorrect
    return {...base,finishedAt:s.finishedAt,statistics:stats,settlementStatus:'READY',pendingCount:0,systemFailureCount:0,settlement:{evaluated,correct:stats.codingPassed+stats.objectiveCorrect,score:evaluated?Math.round((stats.codingPassed+stats.objectiveCorrect)/evaluated*100):null,message:'客观题按标准答案比对；编程题为文本规则粗判；简答题按参考分析自评。'}}
  }
  function submit(data,s,q,value,code=false){
    if(s.status!=='ONGOING')throw Error('练习已结束，请创建新练习。')
    if(!String(value).trim())throw Error('请先完成作答。')
    let result
    if(code)result=roughCodeResult(value,q)
    else if(q.options){const keys=String(value).split(',').map(v=>v.trim());if(keys.some(k=>!q.options.some(o=>o.key===k))||new Set(keys).size!==keys.length||q.type!=='MULTIPLE_CHOICE'&&keys.length!==1)throw Error('请选择有效选项。');const passed=[...keys].sort().join(',')===q.correctAnswer;result={status:passed?'OBJECTIVE_CORRECT':'OBJECTIVE_INCORRECT',passed,correctAnswer:q.correctAnswer}}
    else result={status:'SELF_REVIEW',message:'请结合参考分析自行检查关键概念和边界条件。'}
    const id=++data.sequence
    result={...result,submissionId:id,submitted:true,referenceAnswer:q.referenceAnswer,demoMode:true}
    const item={id,sessionQuestionId:q.id,status:result.status,isCorrect:result.passed??null,answerText:code?null:value,sourceCode:code?value:null,createdAt:stamp(),result}
    s.submissions.unshift(item);s.updatedAt=stamp();if(item.isCorrect===false)q.reviewState='PENDING';else if(item.isCorrect===true)q.reviewState='RESOLVED'
    write(data);return neutral(item,false).result
  }
  async function handle(url,method,input={},params={}){
    if(url==='/practice/catalog')return catalog()
    if(url==='/practice/overview')return overview()
    if(url==='/practice/judge/capability')return {ready:true,languages:{java:'Java · 样例检查'},notice:'本次仅进行本地粗略检查，未调用真实编译器。'}
    if(url==='/practice/sessions'&&method==='post')return create(input)
    const data=read(),parts=url.split('/').filter(Boolean)
    const presentTask=t=>{const session=data.sessions.find(s=>s.id===t.sessionId),item=session?.submissions.find(item=>item.id===t.result.submissionId);return session?.status==='FINISHED'&&item?{...t,status:item.status,result:item.result}:t}
    if(parts[1]==='tasks'){const t=data.tasks.find(t=>String(t.id)===parts[2]);if(!t)throw Error('样例任务不存在');return presentTask(t)}
    if(parts[1]==='questions'){
      const s=data.sessions.find(s=>s.questionList.some(q=>q.id===parts[2]));if(!s)throw Error('样例题目不存在');const q=s.questionList.find(q=>q.id===parts[2])
      if(parts[3]==='favorite'){q.favorite=method==='delete'?false:!q.favorite;write(data);return {favorite:q.favorite}}
      if(parts[3]==='review'){q.reviewState=input.state;write(data);return {state:q.reviewState}}
    }
    if(parts[1]==='library'||url==='/practice/sessions'){
      const page=Number(params.page)||1,size=Number(params.size)||20,kind=parts[2]
      const items=kind?data.sessions.flatMap(s=>s.questionList.filter(q=>kind==='favorites'?q.favorite:s.status==='FINISHED'&&(latest(s,q.id)?.isCorrect===false&&q.reviewState!=='RESOLVED'||q.reviewState==='PENDING')).map(q=>({id:q.id,sessionId:s.id,title:q.title,questionType:q.type,topic:s.topic,company:s.company,jobName:s.role.name,reviewState:s.status==='FINISHED'?q.reviewState:'',submissionStatus:neutral(latest(s,q.id),s.status==='FINISHED')?.status}))):data.sessions.filter(s=>!params.status||params.status==='ALL'||s.status===params.status).map(summary)
      return {items:items.slice((page-1)*size,page*size),total:items.length,page,size}
    }
    const s=find(data,parts[2]),action=parts[3]
    if(!action)return present(s)
    if(action==='report')return report(s)
    if(action==='finish'){s.status='FINISHED';s.finishedAt=stamp();s.updatedAt=stamp();write(data);return report(s)}
    if(action==='retry'){if(s.status!=='FINISHED')throw Error('请先结束当前练习。');const selected=params.failedOnly?s.questionList.filter(q=>latest(s,q.id)?.isCorrect===false&&q.reviewState!=='RESOLVED'||q.reviewState==='PENDING'):s.questionList; if(!selected.length)throw Error('没有可复练的题目。');return create({jobId:s.role.id,company:s.company,topic:s.topic,questionType:s.questionType,difficulty:s.difficulty},selected)}
    if(action==='tasks')return data.tasks.filter(t=>t.sessionId===s.id).map(presentTask)
    if(action==='questions'){
      const q=s.questionList.find(q=>q.id===parts[4]);if(!q)throw Error('样例题目不存在');const operation=parts[5]
      if(operation==='submissions'){const items=s.submissions.filter(item=>item.sessionQuestionId===q.id).map(item=>neutral(item,s.status==='FINISHED'));const page=Number(params.page)||1;return {items:items.slice((page-1)*20,page*20),total:items.length,page}}
      if(operation==='answers')return submit(data,s,q,input.answer)
      if(operation==='tasks'){
        const old=data.tasks.find(t=>t.requestKey===input.requestKey&&t.sessionId===s.id);if(old)return old
        const result=input.operation==='submit'?submit(data,s,q,input.sourceCode,true):{status:'DEMO_RUN',submitted:false,message:'样例运行已完成，判定与解析在练习结束后统一展示。'}
        const updated=read(),task={id:++updated.sequence,sessionId:s.id,questionId:q.id,requestKey:input.requestKey,operation:input.operation,status:result.status,result};updated.tasks.push(task);write(updated);return task
      }
    }
    throw Error('此样例操作暂不支持。')
  }
  function selfAssess(sessionId,questionId,state){
    if(!['MASTERED','NEEDS_REVIEW'].includes(state))throw Error('请选择有效的自评结果。')
    const data=read(),s=find(data,String(sessionId)),q=s.questionList.find(q=>q.id===questionId)
    if(s.status!=='FINISHED'||!q||latest(s,q.id)?.status!=='SELF_REVIEW')throw Error('只能对已结束练习中的普通自评题进行自评。')
    q.selfAssessment={state,submissionId:latest(s,q.id).id,updatedAt:stamp()}
    q.reviewState=state==='MASTERED'?'RESOLVED':'PENDING'
    write(data);return {selfAssessment:q.selfAssessment,reviewState:q.reviewState}
  }
  return {handle,catalog,overview,selfAssess,setRoles:value=>{roles=value},loadResources:(roleList,families,companies)=>{roles=roleList.map((r,n)=>({...r,id:Number(r.id)||10000+n}));return {roles,families:families.map(f=>({...f,count:roles.filter(r=>r.code.startsWith(f.code+'-')).length})),companies,catalog:catalog(),overviewAvailable:true,...overview(),currentRole:roles[0],currentFocus:'混合题型样例练习',preferredRole:roles[0],preferredCompany:''}}}
}

