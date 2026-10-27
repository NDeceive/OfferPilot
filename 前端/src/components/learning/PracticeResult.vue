<template>
  <div class="judge-result">
    <p v-if="deferred" role="status">{{ result.submitted ? '答案已记录，练习结束后统一查看结果。' : '样例运行已完成，判定与解析在练习结束后统一展示。' }}</p>
    <template v-else>
    <p v-if="!coding || result.demoMode || active !== '编译信息'" role="status">{{ result.demoMode && coding ? result.passed ? '样例粗判通过' : '样例粗判未通过' : judgeStatusLabel(result.status) }}<template v-if="result.compilerVersion && !infrastructureFailure"> · {{ result.compilerVersion }}</template></p>
    <p v-if="showMessage && result.message && result.status !== 'COMPILE_ERROR'">{{ result.message }}</p>
    <nav v-if="coding && !infrastructureFailure && !result.demoMode" aria-label="结果详情"><button v-for="tab in tabs" :key="tab" :aria-pressed="active === tab" @click="active=tab">{{ tab }}</button></nav>
    <p v-if="coding && infrastructureFailure">尚未获得编译或运行结果，本次不计入正式提交。</p>
    <template v-else-if="result.demoMode && coding"><p>编程题只进行了文本规则粗判，未调用真实编译器，也未执行测试用例。</p><pre v-if="result.compileOutput">{{ result.compileOutput }}</pre></template>
    <template v-else-if="!coding"><p v-if="result.correctAnswer && !result.referenceAnswer">正确答案：{{ result.correctAnswer === 'true' ? '正确' : result.correctAnswer === 'false' ? '错误' : result.correctAnswer }}</p><p v-if="result.referenceAnswer && result.status?.startsWith('OBJECTIVE_')" class="objective-explanation">{{ result.referenceAnswer }}</p><p v-if="result.score != null">历史关键词覆盖得分：{{ result.score }}（不代表客观正确率）</p><p v-if="result.matchedKeywords?.length">已覆盖：{{ result.matchedKeywords.join('、') }}</p></template>
    <template v-else-if="active === '编译信息'">
      <p>{{ result.status === 'COMPILE_ERROR' ? '编译未通过' : pendingJudgeStatuses.has(result.status) ? '尚无最终编译结果' : ['ACCEPTED','WRONG_ANSWER','RUNTIME_ERROR','TIME_LIMIT_EXCEEDED','MEMORY_LIMIT_EXCEEDED','OUTPUT_LIMIT_EXCEEDED'].includes(result.status) ? '编译通过' : '编译结果未确认' }}<template v-if="result.compilerVersion && !infrastructureFailure"> · {{ result.compilerVersion }}</template></p>
      <div v-if="result.compileOutput"><pre>{{ result.compileOutput }}</pre><button @click="copy(result.compileOutput)">复制编译日志</button></div><p v-else>暂无编译日志。</p>
      <button v-for="(location,n) in locations" :key="n" @click="$emit('reveal',location)">定位第 {{ location.line }} 行</button>
    </template>
    <template v-else-if="active === '运行输出'">
      <p>状态：{{ judgeStatusLabel(result.status) }}</p><p v-if="result.time != null">最后执行用例耗时：{{ result.time }} 秒</p><p v-if="result.memory != null">最后执行用例内存：{{ result.memory }} KB</p>
      <h3>标准输出</h3><pre>{{ result.stdout || '无公开输出' }}</pre><h3>标准错误</h3><pre>{{ result.stderr || '无公开错误输出' }}</pre><button v-if="result.stdout || result.stderr" @click="copy([result.stdout,result.stderr].filter(Boolean).join('\n'))">复制公开输出</button>
    </template>
    <template v-else>
      <p v-if="result.totalCount != null">已执行 {{ result.executedCount }} / {{ result.totalCount }} 个用例；未执行用例不计失败。</p>
      <article v-for="(item,n) in result.cases || []" :key="n"><strong>{{ item.name }} · {{ item.status }}</strong><p v-if="item.hidden">隐藏用例不公开输入输出。</p><template v-else><h3>输入</h3><pre>{{ item.input }}</pre><h3>预期输出</h3><pre>{{ item.expected }}</pre><h3>实际输出</h3><pre>{{ item.output }}</pre></template></article><p v-if="!result.cases?.length">暂无测试用例结果。</p>
    </template>
    <p v-if="copyStatus" role="status">{{ copyStatus }}</p>
    </template>
  </div>
</template>
<script setup>
import {computed,ref,watch} from 'vue'
import {judgeStatusLabel,compileLocations,pendingJudgeStatuses} from '../../utils/practiceJudging'
const props=defineProps({result:{type:Object,required:true},coding:Boolean,deferred:Boolean,showMessage:{type:Boolean,default:true}})
defineEmits(['reveal'])
const infrastructureFailure=computed(()=>['SYSTEM_ERROR','SERVICE_UNAVAILABLE'].includes(props.result.status))
const tabs=['编译信息','运行输出','测试用例'], active=ref('测试用例'), copyStatus=ref('')
const locations=computed(()=>compileLocations(props.result.compileOutput))
watch(()=>props.result,()=>{active.value=props.result.status==='COMPILE_ERROR'?'编译信息':'测试用例';copyStatus.value=''}, {immediate:true})
async function copy(value){try{await navigator.clipboard.writeText(value);copyStatus.value='已复制'}catch{copyStatus.value='复制失败，可选中日志手动复制。'}}
</script>
<style scoped>
.objective-explanation{white-space:pre-wrap;overflow-wrap:anywhere}
.judge-result nav{display:flex;gap:8px;flex-wrap:wrap}.judge-result button{min-height:44px;background:#fff;border:1px solid #dce5e0;border-radius:6px;padding:8px 10px;color:#20352c}.judge-result button[aria-pressed=true]{background:#e9f8ef;color:#087c55}.judge-result pre{max-width:100%;overflow:auto;white-space:pre;font:14px/1.65 Consolas,monospace;background:#f4f7f5;padding:12px;border-radius:6px}.judge-result h3{font-size:14px;margin:12px 0 6px}.judge-result article{padding:12px 0;border-bottom:1px solid #e2e9e5}.judge-result button:focus-visible{outline:2px solid #059669;outline-offset:2px}
</style>
