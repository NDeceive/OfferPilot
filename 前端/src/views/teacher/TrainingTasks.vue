<!-- User-pinned T09–T12: white, #2AB783, compact task rows, five-step wizard,
     text-only statuses and date-grouped activity; no decorative status dots. -->
<template>
<TeachingPortal v-if="route.query.demo!=='1'"/><AppLayout v-else class="training-task-shell"><div class="training-module" :class="{'wizard-module':surface==='wizard'}">
  <aside v-if="surface!=='wizard'" class="task-nav" aria-label="训练任务导航"><strong>训练任务</strong><RouterLink :to="link('/teacher/tasks')" :class="{current:surface!=='templates'}">任务中心</RouterLink><RouterLink :to="link('/teacher/tasks/templates')" :class="{current:surface==='templates'}">任务模板</RouterLink></aside>
  <main class="task-main">
    <div class="task-notice" role="status"><span>{{ demo?'演示模式 · 操作仅保存在当前浏览器，不写入真实系统。示例时间：2026年10月3日。':'真实数据模式 · 任务与模板接口尚未开放，暂不能发布或修改真实任务。' }}</span><button class="text" @click="switchMode">{{ demo?'切换真实数据':'查看演示效果' }}</button></div>
    <RouterLink v-if="!q.returnStudent &amp;&amp; safeTeacherReturn(q.returnTo) &amp;&amp; surface!=='wizard'" :to="q.returnTo" class="source-return">{{q.returnTo.startsWith('/teacher/analytics')?'返回数据分析':'返回来源页面'}}</RouterLink><RouterLink v-else-if="q.returnStudent&&surface!=='wizard'" class="text" :to="studentReturnTarget">返回学生详情</RouterLink><RouterLink v-else-if="q.returnClass && surface!=='wizard'" class="source-return" :to="link('/teacher/classes/'+q.returnClass)">返回来源班级</RouterLink>
    <p v-if="error" class="error-message" role="alert">{{error}}<button class="text" @click="error=''">关闭</button></p><p v-if="message" class="success" role="status">{{message}}<button class="text" @click="message=''">关闭</button></p>
    <template v-if="surface==='list'">
      <header class="task-heading"><div><h1>训练任务</h1><p>布置训练、跟进执行，让每一次练习都有明确目标</p></div><RouterLink class="primary" :to="link('/teacher/tasks/create')">创建训练任务</RouterLink></header>
      <nav class="task-tabs" aria-label="任务关注视图"><button v-for="v in views" :key="v.key" :class="{active:(q.view||'all')===v.key}" @click="setQuery('view',v.key)">{{v.name}}</button></nav>
      <div class="task-filters"><label>班级<select :value="q.classId||''" @change="setQuery('classId',$event.target.value)"><option value="">全部班级</option><option v-for="c in classes" :key="c.id" :value="c.id">{{c.name}}</option></select></label><label class="search-field">搜索<input placeholder="搜索任务名称" :value="q.search||''" @input="setQuery('search',$event.target.value)" /></label><label>排序<select :value="q.sort||'attention'" @change="setQuery('sort',$event.target.value)"><option value="attention">需要处理优先</option><option value="deadline">截止最近</option><option value="published">最新发布</option><option value="rate">完成率最低</option></select></label><button :aria-expanded="showAdvanced" aria-controls="advanced-task-filters" @click="showAdvanced=!showAdvanced">{{showAdvanced?'收起筛选':'更多筛选'}}</button></div>
<div v-if="showAdvanced" id="advanced-task-filters" class="task-filters advanced-filters"><label>任务类型<select :value="q.type||''" @change="setQuery('type',$event.target.value)"><option value="">全部类型</option><option v-for="(v,k) in taskTypes" :key="k" :value="k">{{v}}</option></select></label><label>训练形式<select :value="q.mode||''" @change="setQuery('mode',$event.target.value)"><option value="">全部形式</option><option v-for="(v,k) in modes" :key="k" :value="k">{{v}}</option></select></label><label>发布开始日期<input type="date" :value="q.from||''" @change="setQuery('from',$event.target.value)" /></label><label>发布结束日期<input type="date" :value="q.to||''" @change="setQuery('to',$event.target.value)" /></label></div>
<div v-if="advancedFilters.length" class="active-filters" aria-label="已启用筛选"><button v-for="filter in advancedFilters" :key="filter.key" @click="setQuery(filter.key,'')" :aria-label="'移除'+filter.label">{{filter.label}} ×</button></div>
      <p class="muted list-caption">共{{filteredTasks.length}}项任务 · 优先处理逾期未完成、48小时内截止及低完成率任务</p>
      <section v-if="!filteredTasks.length" class="task-empty"><h2>{{demo?'暂无符合条件的任务':'训练任务尚未接入真实数据'}}</h2><p>{{demo?'调整筛选条件，或创建一项训练任务。':'可在演示模式查看创建、发布和执行跟进流程。'}}</p><button @click="demo?clearFilters():switchMode()">{{demo?'清除筛选':'查看演示效果'}}</button></section>
      <article v-for="t in pagedTasks" :key="t.id" class="task-list-row"><div class="row-identity"><h2>{{t.title}}</h2><p>{{className(t.classId)}} · {{taskTypes[t.type]}} · {{modes[t.trainingMode]}}</p><small>{{t.scope==='all'?'全班':t.scope==='role'?t.participantRole:'指定学生'}} · {{counts(t).total}}人</small></div><div class="row-progress"><template v-if="!['DRAFT','SCHEDULED'].includes(lifecycle(t))"><div class="progress-heading"><span>完成 {{counts(t).completed}} / {{counts(t).total}}人</span><strong>{{counts(t).rate??'—'}}%</strong></div><div class="progress-track"><span :style="{width:(counts(t).rate||0)+'%'}"></span></div><div class="execution-links"><button v-for="status in ['COMPLETED','IN_PROGRESS','NOT_STARTED']" :key="status" class="text" @click="openStudents(t,status)">{{statusNames[status]}} {{countStatus(t,status)}}</button><button v-if="counts(t).overdue" class="text danger" @click="openStudents(t,'overdue')">逾期 {{counts(t).overdue}}</button></div></template><template v-else><strong>{{counts(t).total}}名训练对象</strong><p>{{lifecycle(t)==='DRAFT'?`草稿 · 编辑至第${t.lastEditedStep||1}步`:`开放时间 ${formatTime(t.startTime)}`}}</p></template></div><div class="row-state"><span class="lifecycle" :class="lifecycle(t)">{{lifecycleNames[lifecycle(t)]}}</span><p :class="{danger:counts(t).overdue}">{{counts(t).overdue?`${counts(t).overdue}人逾期未完成`:lifecycle(t)==='DRAFT'?'尚未发布':`截止 ${formatTime(t.deadline)}`}}</p></div><RouterLink class="secondary" :to="link(lifecycle(t)==='DRAFT'?`/teacher/tasks/${t.id}/edit`:`/teacher/tasks/${t.id}`,{tab:lifecycle(t)==='DRAFT'?undefined:'overview'})">{{lifecycle(t)==='DRAFT'?'继续编辑':'查看任务'}}</RouterLink></article>
      <div v-if="filteredTasks.length" class="pagination"><span>共{{filteredTasks.length}}项</span><button :disabled="page===1" @click="setQuery('page',page-1)">上一页</button><span>{{page}} / {{taskPages}}</span><button :disabled="page===taskPages" @click="setQuery('page',page+1)">下一页</button></div>
    </template>
    <template v-else-if="surface==='wizard'">
      <header class="task-heading"><div><RouterLink class="text" :to="q.returnStudent?studentReturnTarget:link('/teacher/tasks')">{{q.returnStudent?'返回学生详情':'退出创建'}}</RouterLink><h1>{{route.params.id?'编辑训练任务':'创建训练任务'}}</h1></div><span role="status" :class="{danger:saveState==='保存失败'}">{{demo?saveState:'真实发布暂不可用'}}<button v-if="saveState==='保存失败'" class="text" @click="persistDraft">重试保存</button></span></header>
      <nav class="wizard-steps" aria-label="创建任务步骤"><button v-for="(s,i) in steps" :key="s" :disabled="i+1>unlocked" :class="{active:step===i+1,finished:step>i+1}" :aria-current="step===i+1?'step':undefined" @click="changeStep(i+1)"><span>{{i+1}}</span>{{s}}</button></nav>
      <div class="task-columns" :class="{'review-layout':step===5}"><section class="wizard-work"><h2>Step {{step}} · {{steps[step-1]}}</h2><p v-if="draft.sourceContext.type!=='TASK_CENTER'" class="context-note">{{sourceDescription}}</p>
        <template v-if="step===1"><fieldset><legend>所属班级</legend><select v-model="draft.classId" aria-label="所属班级" @change="draft.studentIds=[];draft.participantRole=''"><option value="">请选择班级</option><option v-for="c in classes" :key="c.id" :value="c.id">{{c.name}}</option></select><p class="muted">一个任务对应一个班级；发布时固定训练对象。</p></fieldset><fieldset><legend>训练对象范围</legend><div class="choice-grid"><label v-for="s in scopes" :key="s.key" class="choice" :class="{chosen:draft.scope===s.key}"><input type="radio" v-model="draft.scope" :value="s.key" />{{s.name}}<small>{{s.note}}</small></label></div></fieldset><label v-if="draft.scope==='role'" class="field">岗位方向<select v-model="draft.participantRole"><option value="">请选择岗位方向</option><option v-for="p in availableRoles" :key="p">{{p}}</option></select></label><div class="selection-summary"><div><span>已选训练对象</span><strong>{{chosenMembers.filter(s=>s.eligible).length}}<small>人</small></strong></div><button v-if="draft.scope==='students'" :disabled="!demo||!draft.classId" @click="openSelector">选择学生</button></div><p v-if="invalidIds.length||chosenMembers.some(s=>!s.eligible)" class="error-message">部分所选学生已失效或无法训练，请重新选择。</p><p class="muted">待加入、未激活或停用成员不计入可训练人数。</p></template>
        <template v-else-if="step===2"><fieldset><legend>选择训练类型</legend><div class="choice-grid"><label v-for="(v,k) in taskTypes" :key="k" class="choice" :class="{chosen:draft.type===k}"><input v-model="draft.type" type="radio" :value="k" />{{v}}<small>{{k==='ABILITY'?'聚焦一个主要能力':k==='POSITION'?'围绕目标岗位练习':'综合模拟训练'}}</small></label></div></fieldset><label v-if="draft.type==='ABILITY'" class="field">主要训练能力<select v-model="draft.primaryAbility"><option value="">请选择主要能力</option><option v-for="a in commonAbilities" :key="a">{{a}}</option></select></label><label v-if="draft.type==='POSITION'" class="field">目标岗位<select v-model="draft.targetRole"><option value="">请选择目标岗位</option><option v-for="p in roles" :key="p">{{p}}</option></select></label><label class="field">任务名称<input v-model="draft.title" maxlength="80" placeholder="例如：逻辑表达专项训练" @input="titleEdited=true" /></label><label class="field">任务说明（可选）<textarea v-model="draft.description" rows="4" maxlength="1000" placeholder="说明练习重点与对学生的要求" /></label></template>
        <template v-else-if="step===3"><fieldset><legend>训练形式</legend><div class="choice-grid"><label v-for="(v,k) in modes" :key="k" class="choice" :class="{chosen:draft.trainingMode===k}"><input v-model="draft.trainingMode" type="radio" :value="k" />{{v}}</label></div></fieldset><div class="field-grid"><label class="field">预计训练时长<select v-model.number="draft.durationMinutes"><option v-for="n in [10,15,20,30]" :key="n" :value="n">{{n}}分钟</option></select></label><label class="field">难度<select v-model="draft.difficulty"><option value="BASIC">基础</option><option value="STANDARD">标准</option><option value="CHALLENGE">挑战</option></select></label><label class="field">主问题数量<input type="number" min="1" max="30" v-model.number="draft.mainQuestionCount" /></label><label class="field checkbox-field"><input type="checkbox" v-model="draft.dynamicFollowUp" />允许动态追问</label></div><div class="section-heading"><h3>训练结构预览</h3><button class="text" @click="openContent(false)">查看与调整训练内容</button></div><ol class="question-preview"><li v-for="(question,i) in draft.questions.slice(0,4)" :key="question.id"><span>{{i+1}}</span><div>{{question.text}}<small>{{question.focus}} · {{question.followUp?'允许追问':'不追问'}}</small></div></li></ol><p class="muted">共{{draft.questions.length}}个主问题 · 根据当前训练目标配置</p></template>
        <template v-else-if="step===4"><fieldset><legend>执行时间</legend><label class="checkbox-field"><input type="checkbox" v-model="draft.immediate" />立即开始</label><div class="field-grid"><label v-if="!draft.immediate" class="field">开始时间<input type="datetime-local" v-model="draft.startTime" /></label><label class="field">截止时间<input type="datetime-local" v-model="draft.deadline" /></label></div><p class="muted">执行窗口至少30分钟；日期和时间按北京时间。</p></fieldset><fieldset><legend>完成要求</legend><p>完成训练并生成有效结果，计为一次有效完成。</p><label class="checkbox-field"><input type="checkbox" v-model="draft.allowRepeat" />允许重复训练</label><template v-if="draft.allowRepeat"><label class="field">最多训练次数<input type="number" min="1" max="20" v-model.number="draft.maxAttempts" /></label><details class="completion-options" :open="draft.minValidAttempts>1"><summary>设置多次完成要求（可选）</summary><label class="field">最低有效完成次数<input type="number" min="1" max="10" v-model.number="draft.minValidAttempts" /></label></details></template><p class="muted">{{draft.minValidAttempts===1?'有效完成1次即可完成任务。':('需有效完成'+draft.minValidAttempts+'次；每次均需生成有效结果。')}}</p></fieldset><fieldset><legend>截止后处理</legend><label class="checkbox-field"><input type="checkbox" v-model="draft.allowLateCompletion" />允许截止后补练</label><p class="muted">不允许补练时，截止前已开始的当前训练仍可完成。</p></fieldset><p v-if="shortWindow" class="context-note">执行窗口不足24小时，请确认学生有足够时间完成训练。</p></template>
        <template v-else><div class="review-banner"><strong>{{validation.length?'发布前还有信息需要处理':'训练信息已就绪，请确认后发布'}}</strong><p>发布后，训练对象和内容将固定，请确认班级与人数。</p></div><div v-for="(s,i) in steps.slice(0,4)" :key="s" class="review-row"><div><h3>{{s}}</h3><p>{{reviewLines[i]}}</p><p v-for="v in validation.filter(v=>v.step===i+1)" :key="v.message" class="danger">{{v.message}}</p></div><button class="text" @click="changeStep(i+1)">{{validation.some(v=>v.step===i+1)?'去处理':'修改'}}</button></div><p v-if="shortWindow" class="context-note">执行窗口不足24小时，请在发布前确认。</p></template>
      </section><aside v-if="step<5" class="task-summary"><h3>训练任务摘要</h3><dl><dt>所属班级</dt><dd>{{className(draft.classId)}}</dd><dt>训练对象</dt><dd>{{chosenMembers.filter(s=>s.eligible).length}}人</dd><dt>任务类型</dt><dd>{{taskTypes[draft.type]}}</dd><dt>主要目标</dt><dd>{{goal(draft)||'待设置'}}</dd><dt>训练形式</dt><dd>{{modes[draft.trainingMode]}}</dd><dt>时长 / 难度</dt><dd>{{draft.durationMinutes}}分钟 · {{difficulty(draft)}}</dd><dt>主问题</dt><dd>{{draft.questions.length}}题</dd><dt>完成要求</dt><dd>有效完成{{draft.minValidAttempts}}次</dd><dt>截止时间</dt><dd>{{draft.deadline?formatTime(draft.deadline+':00+08:00'):'待设置'}}</dd></dl><p class="muted">草稿自动保存，发布前可返回各步调整。</p></aside></div>
      <footer class="wizard-footer"><div><button v-if="step>1" @click="changeStep(step-1)">上一步</button><button v-if="step<5" class="primary" @click="nextStep">下一步</button><button v-else class="primary" :disabled="!demo||validation.length>0||busy" @click="openModal('publish')">发布任务</button></div></footer>
    </template>
    <template v-else-if="surface==='detail'">
      <template v-if="task"><header class="task-heading"><div><RouterLink class="text" :to="link('/teacher/tasks')">返回任务中心</RouterLink><h1>{{task.title}} <span class="lifecycle" :class="lifecycle(task)">{{lifecycleNames[lifecycle(task)]}}</span></h1><p>{{className(task.classId)}} · {{goal(task)}} · {{modes[task.trainingMode]}} · 截止 {{formatTime(task.deadline)}} · {{deadlineRemaining(task)}}</p></div><div class="header-actions"><button :disabled="!incomplete.length||lifecycle(task)==='DRAFT'" @click="openModal('remind')">提醒未完成</button><button :disabled="!!task.manuallyEndedAt||lifecycle(task)==='DRAFT'" @click="openModal('extend')">延长截止</button><details><summary>更多</summary><div><button @click="openModal('saveTemplate')">保存为模板</button><button class="danger" :disabled="['ENDED','DRAFT'].includes(lifecycle(task))" @click="openModal('end')">结束任务</button></div></details></div></header><nav class="task-tabs" aria-label="任务工作台"><RouterLink v-for="t in detailTabs" :key="t.key" :class="{active:tab===t.key}" :to="link(`/teacher/tasks/${task.id}`,{tab:t.key})">{{t.name}}</RouterLink></nav>
      <div class="task-columns detail-columns"><section>
        <template v-if="tab==='overview'"><section class="overview-progress"><div><h2>整体完成进度</h2><strong>{{taskCounts.rate??0}}<small>%</small></strong><p>{{taskCounts.completed}} / {{taskCounts.total}}人已完成</p></div><div class="overview-progress-content"><div class="progress-track large"><span :style="{width:(taskCounts.rate||0)+'%'}"></span></div><div class="execution-links"><button v-for="status in ['COMPLETED','IN_PROGRESS','NOT_STARTED']" :key="status" class="text" @click="openStudents(task,status)">{{statusNames[status]}} {{countStatus(task,status)}}人</button></div></div></section><div class="text-metrics overview-metrics"><div><span>{{goal(task)}}平均表现</span><strong>{{averageScore}}</strong><small>{{scoreSampleNote}}</small></div><div><span>平均用时</span><strong>{{averageResult('durationMinutes')}}<small v-if="latestResults.length">分钟</small></strong><small>有效完成训练</small></div></div><section class="task-section"><h2>完成情况趋势</h2><TeacherChart v-if="completionTrend.length" :points="completionTrend" :series="[{key:'completed',name:'累计完成人数'}]" label="累计完成学生人数趋势" /><p v-else class="task-empty">暂无完成记录，学生完成后将展示趋势。</p></section><section class="task-section"><div class="section-heading"><h2>学生完成情况</h2><button class="text" @click="setQuery('tab','students')">查看全部学生</button></div><ExecutionTable :rows="executionRows.slice(0,5)" :task="task" :selectable="false" :selected="selected" @select="selected=$event" @student="showStudent" @result="showResult" /></section></template>
        <template v-else-if="tab==='students'"><TaskStatusFilters :task="task" :status="studentFilter.status" :timing="studentFilter.timing" @change="setStudentFilter" /><div class="task-filters"><label>搜索<input placeholder="搜索姓名或学号" :value="q.search||''" @input="setQuery('search',$event.target.value)" /></label><label>岗位<select :value="q.position||''" @change="setQuery('position',$event.target.value)"><option value="">全部岗位</option><option v-for="p in roles" :key="p">{{p}}</option></select></label></div><p class="muted">共{{studentRows.length}}名学生</p><div v-if="selected.length" class="selection-actions" role="region" aria-label="已选学生操作"><span>已选{{selected.length}}人</span><button :disabled="!executionRows.some(s=>selected.includes(s.studentId)&&s.completionStatus!=='COMPLETED')" @click="remindRows(task,selected)">提醒未完成</button><button class="primary" @click="intervene(task,selected)">再次布置训练</button><button class="text" @click="selected=[]">取消选择</button></div><ExecutionTable :rows="pagedStudents" :task="task" :selected="selected" @select="selected=$event" @student="showStudent" @result="showResult" /><p v-if="!studentRows.length" class="task-empty">暂无符合条件的学生</p><div class="pagination"><button :disabled="page===1" @click="setQuery('page',page-1)">上一页</button><span>{{page}} / {{studentPages}}</span><button :disabled="page===studentPages" @click="setQuery('page',page+1)">下一页</button></div></template>
        <template v-else>
<div class="text-metrics overview-metrics result-counts"><div><span>有有效结果</span><strong>{{latestResults.length}}<small>人</small></strong><small>暂无有效结果 {{resultGroups.none}}人</small></div><div><span>可比较前后结果</span><strong>{{comparableRows.length}}<small>人</small></strong><small v-if="resultGroups.single||resultGroups.incomparable">仅一次结果 {{resultGroups.single}}人 · 评分口径不一致 {{resultGroups.incomparable}}人</small><small v-else>同一任务、同一目标、相同评分口径</small></div></div>
<section class="task-section"><h2>{{goal(task)}}：首次与最近结果</h2><p class="muted">变化值为最近分数减去首次分数，仅描述分差，不自动判断训练效果。</p><div v-for="r in comparableRows" :key="r.studentId" class="comparison-row"><div class="comparison-person"><strong>{{r.name}}</strong><small>变化 {{signedDelta(r.evidence.delta)}}分</small></div><div class="comparison-bars"><div><span>首次</span><i :style="{width:r.evidence.first.score+'%'}"></i><b>{{r.evidence.first.score.toFixed(1)}}</b></div><div><span>最近</span><i :style="{width:r.evidence.latest.score+'%'}"></i><b>{{r.evidence.latest.score.toFixed(1)}}</b></div></div></div><p v-if="!comparableRows.length" class="task-empty">当前没有可比较的两次有效结果。</p><details v-if="resultGroups.none||resultGroups.single||resultGroups.incomparable" class="result-availability"><summary>查看暂无结果或暂不能比较的学生（{{nonComparableRows.length}}人）</summary><div class="table-scroll"><table><thead><tr><th>学生</th><th>结果情况</th><th>原因</th></tr></thead><tbody><tr v-for="r in nonComparableRows" :key="r.studentId"><td>{{r.name}}</td><td>{{evidenceLabels[r.evidence.state]}}</td><td>{{evidenceReasons[r.evidence.state]}}</td></tr></tbody></table></div></details></section>
<section class="task-section"><div class="section-heading"><h2>需要跟进的学生</h2><span class="muted">{{attentionRows.length}}人 · 按执行或报告问题列出</span></div><div v-if="selected.length" class="selection-actions" role="region" aria-label="已选学生操作"><span>已选{{selected.length}}人</span><button :disabled="!executionRows.some(s=>selected.includes(s.studentId)&&s.completionStatus!=='COMPLETED')" @click="remindRows(task,selected)">提醒未完成</button><button class="primary" @click="intervene(task,selected)">再次布置训练</button><button class="text" @click="selected=[]">取消选择</button></div><ExecutionTable :rows="attentionRows" :task="task" :show-reasons="true" :selected="selected" @select="selected=$event" @student="showStudent" @result="showResult" /><p v-if="!attentionRows.length" class="task-empty">当前没有需要跟进的执行或报告问题。</p></section>
</template>
      </section><aside class="task-summary"><h3>任务信息</h3><dl><dt>班级</dt><dd>{{className(task.classId)}}</dd><dt>主要目标</dt><dd>{{goal(task)}}</dd><dt>训练对象</dt><dd>{{taskCounts.total}}人</dd><dt>截止时间</dt><dd>{{formatTime(task.deadline)}}</dd></dl><details class="task-config"><summary>查看任务配置</summary><dl><dt>训练形式</dt><dd>{{modes[task.trainingMode]}}</dd><dt>预计时长</dt><dd>{{task.durationMinutes}}分钟</dd><dt>难度</dt><dd>{{difficulty(task)}}</dd><dt>主问题</dt><dd>{{(task.contentSnapshot||task.questions).length}}题</dd><dt>完成要求</dt><dd>有效完成{{task.minValidAttempts}}次</dd><dt>最多训练</dt><dd>{{task.maxAttempts}}次</dd><dt>截止后补练</dt><dd>{{task.allowLateCompletion?'允许':'不允许'}}</dd></dl><button class="text" @click="openContent(false,task)">查看训练内容</button></details><h3 class="activity-heading">最近动态</h3><div v-for="group in activityGroups" :key="group.date" class="activity-group"><h4>{{group.date}}</h4><div v-for="(event,i) in group.events" :key="i" class="activity-row"><time>{{formatClock(event.at)}}</time><p>{{event.text}}</p><small>{{relativeTime(event.at)}}</small></div></div><p v-if="!activityGroups.length" class="muted">暂无最近动态</p></aside></div></template><section v-else class="task-empty"><h1>{{demo?'未找到任务':'任务详情尚未接入真实数据'}}</h1><p>请返回任务中心选择任务，或切换演示模式查看工作台。</p><RouterLink :to="link('/teacher/tasks')">返回任务中心</RouterLink></section>
    </template>
    <template v-else><header class="task-heading"><div><h1>任务模板中心</h1><p>复用训练方案，每次重新确认班级与学生</p></div></header><nav class="task-tabs"><button :class="{active:(q.owner||'SYSTEM')==='SYSTEM'}" @click="setQuery('owner','SYSTEM')">系统模板</button><button :class="{active:q.owner==='TEACHER'}" @click="setQuery('owner','TEACHER')">我的模板</button></nav><div class="task-filters"><label>任务类型<select :value="q.type||''" @change="setQuery('type',$event.target.value)"><option value="">全部类型</option><option v-for="(v,k) in taskTypes" :key="k" :value="k">{{v}}</option></select></label><label>训练形式<select :value="q.mode||''" @change="setQuery('mode',$event.target.value)"><option value="">全部形式</option><option v-for="(v,k) in modes" :key="k" :value="k">{{v}}</option></select></label><label>适用方向<select :value="q.goal||''" @change="setQuery('goal',$event.target.value)"><option value="">全部方向</option><option v-for="g in templateGoals" :key="g">{{g}}</option></select></label><label>搜索<input :value="q.search||''" @input="setQuery('search',$event.target.value)" placeholder="搜索模板名称" /></label><label>排序<select :value="q.sort||'name'" @change="setQuery('sort',$event.target.value)"><option value="name">模板名称</option><option value="updated">最近更新</option></select></label></div><div class="table-scroll"><table><thead><tr><th>模板名称</th><th>训练类型</th><th>主要目标</th><th>训练形式</th><th>时长 / 难度</th><th>主问题</th><th>操作</th></tr></thead><tbody><tr v-for="t in filteredTemplates" :key="t.id"><td><strong>{{t.name}}</strong><small>{{t.description}}</small></td><td>{{taskTypes[t.type]}}</td><td>{{goal(t)}}</td><td>{{modes[t.trainingMode]}}</td><td>{{t.durationMinutes}}分钟 / {{difficulty(t)}}</td><td>{{t.questions.length}}题</td><td class="table-actions"><button class="text" @click="drawer={type:'template',template:t}">查看模板</button><button @click="useTemplate(t)">使用模板</button><details v-if="t.ownerType==='TEACHER'"><summary>更多</summary><div><button @click="openModal('renameTemplate',t)">重命名</button><button class="danger" @click="openModal('deleteTemplate',t)">删除</button></div></details></td></tr></tbody></table></div><section v-if="!filteredTemplates.length" class="task-empty"><h2>{{demo?'暂无模板':'模板尚未接入真实数据'}}</h2><p>{{demo?'在任务工作台将训练方案保存为模板，之后可在这里复用。':'切换演示模式查看系统模板。'}}</p></section></template>
  </main>
</div>
<TeacherDrawer v-if="drawer" modal :title="drawerTitle" class="training-dialog training-detail-window" @close="drawer=null"><div class="training-dialog-body">
  <template v-if="drawer.type==='selector'"><p>{{className(draft.classId)}} · 已选择{{selectorIds.length}}名学生</p><div class="task-filters"><label>搜索<input v-model="drawerSearch" placeholder="搜索姓名或学号" /></label><label>岗位<select v-model="drawerRole"><option value="">全部岗位</option><option v-for="p in availableRoles" :key="p">{{p}}</option></select></label><label>成员状态<select v-model="drawerEligibility"><option value="all">全部成员</option><option value="valid">可训练</option><option value="invalid">异常成员</option></select></label></div><label class="checkbox-field"><input type="checkbox" :checked="selectorRows.filter(s=>s.eligible).length>0&&selectorRows.filter(s=>s.eligible).every(s=>selectorIds.includes(s.id))" @change="selectFiltered($event.target.checked)" />全选当前筛选的可训练学生</label><div class="table-scroll"><table><thead><tr><th>选择</th><th>姓名</th><th>学号</th><th>岗位</th><th>成员状态</th></tr></thead><tbody><tr v-for="s in selectorRows" :key="s.id"><td><input type="checkbox" v-model="selectorIds" :value="s.id" :disabled="!s.eligible" :aria-label="`选择${s.name}`" /></td><td>{{s.name}}</td><td>{{s.number}}</td><td>{{s.position}}</td><td>{{s.reason||'可训练'}}</td></tr></tbody></table></div><details v-if="selectorIds.length" class="selected-roster"><summary>查看已选学生（{{selectorIds.length}}人）：{{selectorMembers.slice(0,3).map(s=>s.name).join('、')}}{{selectorIds.length>3?'等':''}}</summary><ul><li v-for="s in selectorMembers" :key="s.id"><span>{{s.name}} <small>{{s.number}}</small></span><button class="text" :aria-label="'移除'+s.name" @click="selectorIds=selectorIds.filter(id=>id!==s.id)">移除</button></li></ul></details></template>
  <template v-else-if="drawer.type==='content'"><p>{{modes[drawer.config.trainingMode]}} · {{goal(drawer.config)}} · {{drawer.config.durationMinutes}}分钟 · {{difficulty(drawer.config)}} · {{contentQuestions.length}}题</p><div v-for="(question,i) in contentQuestions" :key="question.id" class="content-question"><div class="section-heading"><h3>{{i+1}}. {{question.focus}}</h3><div v-if="drawer.edit"><button class="text" :disabled="i===0" @click="moveQuestion(i,-1)">上移</button><button class="text" :disabled="i===contentQuestions.length-1" @click="moveQuestion(i,1)">下移</button><button class="text danger" @click="contentQuestions.splice(i,1)">删除</button></div></div><template v-if="drawer.edit"><label class="field">问题内容<textarea v-model="question.text" rows="3" /></label><label class="field">训练重点<input v-model="question.focus" /></label><label class="checkbox-field"><input type="checkbox" v-model="question.followUp" />允许动态追问</label><button class="text" @click="replaceQuestion(i)">替换为推荐问题</button></template><p v-else>{{question.text}}</p></div><div v-if="drawer.edit" class="header-actions"><button @click="addQuestion">添加自定义问题</button><button @click="contentQuestions=recommendedQuestions(draft)">恢复推荐方案</button></div><p v-if="drawer.edit" class="muted">题库查询接口尚未开放；可编辑推荐问题或添加教师自定义题。</p></template>
  <template v-else-if="drawer.type==='students'"><TaskStatusFilters :task="drawer.task" :status="drawer.status" :timing="drawer.timing" @change="(key,value)=>drawer[key]=value" /><label class="field">搜索学生<input v-model="drawerSearch" placeholder="搜索姓名或学号" /></label><ExecutionTable :rows="drawerStudentRows" :task="drawer.task" :selected="drawerSelected" @select="drawerSelected=$event" @student="showStudent" @result="showResult" /><p v-if="!drawerStudentRows.length" class="task-empty">暂无符合条件的学生</p></template>
  <template v-else-if="drawer.type==='template'"><h3>{{drawer.template.name}}</h3><p>{{drawer.template.ownerType==='SYSTEM'?'系统模板 · 只读':'我的模板'}}</p><dl><dt>类型 / 目标</dt><dd>{{taskTypes[drawer.template.type]}} / {{goal(drawer.template)}}</dd><dt>训练形式</dt><dd>{{modes[drawer.template.trainingMode]}}</dd><dt>时长 / 难度</dt><dd>{{drawer.template.durationMinutes}}分钟 / {{difficulty(drawer.template)}}</dd><dt>默认规则</dt><dd>最低完成{{drawer.template.minValidAttempts}}次，最多{{drawer.template.maxAttempts}}次</dd></dl><h3>训练结构与典型问题</h3><ol><li v-for="question in drawer.template.questions" :key="question.id">{{question.text}}</li></ol><div class="context-note"><strong>会带入</strong><p>训练目标、内容与默认规则（第2～4步）。</p><strong>不会带入</strong><p>班级、学生、原任务对象快照和历史执行记录。</p></div></template>
  
  <template v-else><h3>{{drawer.student.name}} · 最近有效结果</h3><template v-if="resultEvidence(drawer.task,drawer.student).latest"><p>报告维度：{{resultEvidence(drawer.task,drawer.student).latest.dimension||'未标注'}}</p><p>本次报告得分 {{resultEvidence(drawer.task,drawer.student).latest.score.toFixed(1)}} · 完成于 {{formatTime(resultEvidence(drawer.task,drawer.student).latest.completedAt)}}</p></template><p v-else>暂无有效训练结果。</p><RouterLink v-if="resultEvidence(drawer.task,drawer.student).latest" :to="link('/teacher/reports/'+(resultEvidence(drawer.task,drawer.student).latest.reportId||resultEvidence(drawer.task,drawer.student).latest.id),{returnTo:route.fullPath})">查看完整报告</RouterLink></template>
</div><footer class="training-dialog-footer"><button @click="drawer=null">{{drawer.type==='selector'||drawer.edit?'取消':'关闭'}}</button><button v-if="drawer.type==='selector'" class="primary" @click="confirmSelector">确认选择 {{selectorIds.length}}名学生</button><button v-if="drawer.type==='content'&&!drawer.edit&&!drawer.config.publishedAt" @click="drawer.edit=true">编辑方案</button><button v-if="drawer.type==='content'&&drawer.edit" class="primary" :disabled="!contentQuestions.length||contentQuestions.some(q=>!q.text.trim())" @click="confirmContent">保存方案</button><button v-if="drawer.type==='template'" class="primary" @click="useTemplate(drawer.template)">使用模板</button><button v-if="drawer.type==='students'&&drawerSelected.length" :disabled="!rowsFor(drawer.task).some(s=>drawerSelected.includes(s.studentId)&&s.completionStatus!=='COMPLETED')" @click="remindSelection">提醒所选未完成学生</button><button v-if="drawer.type==='students'&&drawerSelected.length" class="primary" @click="intervene(drawer.task,drawerSelected)">再次布置训练</button></footer></TeacherDrawer>
<TeacherDrawer v-if="modal" :title="modalTitle" :modal="true" class="training-dialog training-confirm-window" @close="closeModal"><div class="training-dialog-body">
  <p v-if="modal.type==='publish'">确认向{{className(draft.classId)}}的{{chosenMembers.filter(s=>s.eligible).length}}名学生发布？<br />发布后，训练对象与内容将固定。<br />本次为演示操作。<span v-if="shortWindow" class="danger">执行窗口不足24小时，请确认。</span></p><template v-else-if="modal.type==='extend'"><p>当前截止：{{formatTime(task.deadline)}}</p><label class="field">新截止时间<input v-model="modal.value" type="datetime-local" /></label><label class="field">延期说明（可选）<textarea v-model="modal.description" rows="3" /></label></template><template v-else-if="modal.type==='remind'"><p>提醒对象：{{modal.studentIds.length}}名未完成学生。</p><div class="context-note">请及时完成「{{modal.task.title}}」，截止时间为{{formatTime(modal.task.deadline)}}。</div><p class="muted">演示模式仅记录提醒操作，不发送真实通知。</p></template><template v-else-if="modal.type==='end'"><p class="danger">结束后，未开始的学生无法再进入任务；当前已开始的训练允许完成。</p><p>该操作不可撤销，历史执行记录将保留。</p></template><template v-else-if="['saveTemplate','renameTemplate'].includes(modal.type)"><label class="field">模板名称<input v-model="modal.value" maxlength="80" /></label><label class="field">说明（可选）<textarea v-model="modal.description" rows="3" maxlength="500" /></label><p class="muted">只保存目标、内容及默认规则；不保存班级和学生。</p></template><p v-else-if="modal.type==='deleteTemplate'">确定删除「{{modal.template.name}}」？不会影响历史任务。</p><p v-else>草稿未保存成功，离开将丢失本地未保存的修改。可取消后重试保存。</p><p v-if="modalError" class="error-message" role="alert">{{modalError}}</p>
</div><footer class="training-dialog-footer"><button :disabled="busy" @click="closeModal">取消</button><button class="primary" :class="{destructive:['end','deleteTemplate','leave'].includes(modal.type)}" :disabled="busy||!demo&&modal.type!=='leave'" @click="confirmModal">{{busy?'处理中…':modal.type==='leave'?'放弃修改并离开':modal.type==='publish'?'确认发布':modal.type==='remind'?'确认记录演示提醒':'确认'}}</button></footer></TeacherDrawer>
</AppLayout>
</template>

<script setup>
import TeachingPortal from '../TeachingPortal.vue'
import {normalizeTeacherQuery,teacherContext,safeTeacherReturn} from '../../services/teacherContext.js'
import { ref, reactive, computed, watch, nextTick, onBeforeUnmount } from 'vue'
import { useRoute, useRouter, onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router'
import AppLayout from '../../components/layout/AppLayout.vue'
import TeacherDrawer from '../../components/teacher/TeacherDrawer.vue'
import TeacherChart from '../../components/teacher/TeacherChart.vue'
import TaskStatusFilters from '../../components/teacher/TaskStatusFilters.vue'
import ExecutionTable from '../../components/teacher/TaskExecutionTable.vue'
import { classDefinitions } from '../../services/classInsights.js'
import { positions as roles } from '../../services/teacherModel.js'
import { taskTypes,modes,lifecycleNames,statusNames,commonAbilities,demoNow,membersFor,participantsFor,lifecycle,counts,attentionRank,matchesStatus,executionFilter,matchesExecution,resultEvidence,attentionReason,recommendedQuestions,newDraft,validateDraft,loadTasks,saveDraft,publishDraft,extendDeadline,loadTemplates,saveTemplate,deleteTemplate,applyTemplate } from '../../services/trainingTasks.js'
const route=useRoute(),router=useRouter(),q=computed(()=>normalizeTeacherQuery(route.query)),demo=computed(()=>q.value.demo==='1')
const surface=computed(()=>route.path.endsWith('/templates')?'templates':route.path.endsWith('/create')||route.path.endsWith('/edit')?'wizard':route.params.id?'detail':'list')
const classes=computed(()=>demo.value?classDefinitions:[]),tasks=ref([]),templates=ref([]),task=computed(()=>tasks.value.find(t=>t.id===route.params.id))
const error=ref(''),message=ref(''),busy=ref(false),draft=reactive(newDraft()),step=ref(1),unlocked=ref(1),saveState=ref('已保存'),dirty=ref(false),titleEdited=ref(false)
const drawer=ref(null),modal=ref(null),modalError=ref(''),selected=ref([]),drawerSelected=ref([]),selectorIds=ref([]),drawerSearch=ref(''),drawerRole=ref(''),drawerEligibility=ref('all'),contentQuestions=ref([])
const showAdvanced=ref(false)
let saveTimer,initializing=false,routeBypass=false,pendingNavigation
const steps=['训练对象','训练目标','训练内容','任务规则','预览并发布']
const views=[{key:'all',name:'全部'},{key:'active',name:'进行中'},{key:'dueSoon',name:'即将截止'},{key:'overdue',name:'已逾期'},{key:'scheduled',name:'未开始'},{key:'ended',name:'已结束'},{key:'draft',name:'草稿'}]
const detailTabs=[{key:'overview',name:'执行概览'},{key:'students',name:'学生执行'},{key:'results',name:'训练结果'}]
const scopes=[{key:'all',name:'全班',note:'当前班级可训练学生'},{key:'role',name:'岗位方向',note:'同一岗位方向的学生'},{key:'students',name:'指定学生',note:'手动选择训练对象'}]
const tab=computed(()=>['overview','students','results'].includes(q.value.tab)?q.value.tab:'overview')
function link(path,extra={}){return{path,query:{...teacherContext(q.value),sourceStudentId:q.value.sourceStudentId,...extra}}}
function setQuery(key,value){router.replace({query:{...q.value,[key]:value||undefined,...(key!=='page'?{page:undefined}:{})}})}
function clearFilters(){router.replace(link(route.path))}
function switchMode(){router.push({path:'/teacher/tasks',query:{demo:demo.value?undefined:'1'}})}
function className(id){return classDefinitions.find(c=>c.id===id)?.name||'待选择'}
function goal(t){return t.type==='ABILITY'?t.primaryAbility:t.type==='POSITION'?t.targetRole:'综合能力'}
function difficulty(t){return{BASIC:'基础',STANDARD:'标准',CHALLENGE:'挑战'}[t.difficulty]||'标准'}
function formatTime(value){if(!value)return'—';const d=new Date(value);return Number.isFinite(+d)?new Intl.DateTimeFormat('zh-CN',{timeZone:'Asia/Shanghai',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hour12:false}).format(d):'—'}
function formatClock(value){return new Intl.DateTimeFormat('zh-CN',{timeZone:'Asia/Shanghai',hour:'2-digit',minute:'2-digit',hour12:false}).format(new Date(value))}
function relativeTime(value){const n=Math.max(0,Math.floor((demoNow()-Date.parse(value))/60000));return n<60?`${n}分钟前`:n<1440?`${Math.floor(n/60)}小时前`:`${Math.floor(n/1440)}天前`}
function deadlineRemaining(t) {
  if (t.manuallyEndedAt) return '已手动结束'
  const hours = Math.ceil((Date.parse(t.deadline) - demoNow()) / 3600000)
  if (hours <= 0) return '已截止'
  return hours >= 24 ? `距截止还有${Math.floor(hours / 24)}天${hours % 24}小时` : `距截止还有${hours}小时`
}
const filteredTasks=computed(()=>tasks.value.filter(t=>{
  const state=lifecycle(t),c=counts(t),view=q.value.view||'all'
  const matches=view==='all'||(view==='overdue'?c.overdue>0:view==='dueSoon'?state==='ACTIVE'&&Date.parse(t.deadline)-demoNow()<=48*3600000&&c.completed<c.total:state==={active:'ACTIVE',scheduled:'SCHEDULED',ended:'ENDED',draft:'DRAFT'}[view])
  return matches&&(!q.value.classIds||String(q.value.classIds).split(',').includes(t.classId))&&(!q.value.ability||t.primaryAbility===q.value.ability)&&(!q.value.classId||t.classId===q.value.classId)&&(!q.value.type||t.type===q.value.type)&&(!q.value.mode||t.trainingMode===q.value.mode)&&(!q.value.search||t.title.includes(q.value.search))&&(!q.value.from||(q.value.analyticsDue==='1'?t.deadline:t.publishedAt)?.slice(0,10)>=q.value.from)&&(!q.value.to||(q.value.analyticsDue==='1'?t.deadline:t.publishedAt)?.slice(0,10)<=q.value.to)
}).sort((a,b)=>q.value.sort==='deadline'?(Date.parse(a.deadline)||Infinity)-(Date.parse(b.deadline)||Infinity):q.value.sort==='published'?(Date.parse(b.publishedAt)||0)-(Date.parse(a.publishedAt)||0):q.value.sort==='rate'?(counts(a).rate??101)-(counts(b).rate??101):attentionRank(a)-attentionRank(b)||(lifecycle(a)==='DRAFT'?Date.parse(b.updatedAt)-Date.parse(a.updatedAt):Date.parse(a.deadline)-Date.parse(b.deadline))))
const taskPages=computed(()=>Math.max(1,Math.ceil(filteredTasks.value.length/8)))
const page=computed(()=>Math.max(1,Math.min(Number(q.value.page)||1,surface.value==='list'?taskPages.value:studentPages.value)))
const pagedTasks=computed(()=>filteredTasks.value.slice((page.value-1)*8,page.value*8))
const chosenMembers=computed(()=>demo.value?participantsFor(draft):[]),invalidIds=computed(()=>draft.scope==='students'?draft.studentIds.filter(id=>!membersFor(draft.classId).some(s=>s.id===id)):[])
const availableRoles=computed(()=>[...new Set(demo.value?membersFor(draft.classId).map(s=>s.position):[])])
const validation=computed(()=>validateDraft(draft)),shortWindow=computed(()=>Date.parse(draft.deadline+':00+08:00')-(draft.immediate?demoNow():Date.parse(draft.startTime+':00+08:00'))<86400000)
const studentReturnTarget=computed(()=>typeof q.value.returnTo==='string'&&(q.value.returnTo===`/teacher/students/${q.value.returnStudent}`||q.value.returnTo.startsWith(`/teacher/students/${q.value.returnStudent}?`))?q.value.returnTo:link(`/teacher/students/${q.value.returnStudent}`))
const sourceDescription=computed(()=>({STUDENT_ABILITY:'来自学生能力页面：已预填当前学生与专项能力，请确认训练内容。',STUDENT_OVERVIEW:'来自学生概览：已预填当前学生，请选择训练目标。',TEMPLATE:'从模板创建：已带入目标、内容及默认规则，请重新选择班级和学生。',TASK_INTERVENTION:'再次干预：已带入原任务目标与选中学生，请确认方案。',CLASS_ABILITY_ANALYSIS:'来自班级能力分析：已预填班级、学生与主要能力。',CLASS_STUDENT_ANALYSIS:'来自学生表现分析：请自行选择训练目标。',CLASS_DETAIL:'来自班级详情：已预填班级，可继续调整对象。'}[draft.sourceContext.type]))
const reviewLines=computed(()=>[`${className(draft.classId)} · ${chosenMembers.value.filter(s=>s.eligible).length}人`,`${draft.title||'待命名'} · ${taskTypes[draft.type]} · ${goal(draft)||'待选择'}`,`${modes[draft.trainingMode]} · ${draft.durationMinutes}分钟 · ${draft.questions.length}个主问题 · ${difficulty(draft)}`,`最低有效完成${draft.minValidAttempts}次，最多${draft.maxAttempts}次 · 截止${formatTime(draft.deadline+':00+08:00')}`])
function rowsFor(t){return(t?.executions||[]).map(e=>({...t.participantSnapshot?.find(s=>s.id===e.studentId),...e,evidence:resultEvidence(t,e)}))}
const executionRows=computed(()=>rowsFor(task.value)),taskCounts=computed(()=>task.value?counts(task.value):{}),incomplete=computed(()=>executionRows.value.filter(s=>s.completionStatus!=='COMPLETED'))
function countStatus(t,status){return rowsFor(t).filter(r=>matchesStatus(t,r,status)).length}
const studentFilter=computed(()=>executionFilter(q.value.status,q.value.timing))
function setStudentFilter(key,value){router.replace({query:{...q.value,status:studentFilter.value.status,timing:studentFilter.value.timing||undefined,[key]:value||undefined,page:undefined}})}
const studentRows=computed(()=>executionRows.value.filter(s=>matchesExecution(task.value,s,studentFilter.value.status,studentFilter.value.timing)&&(!q.value.search||(s.name+s.number).includes(q.value.search))&&(!q.value.position||s.position===q.value.position)))
const studentPages=computed(()=>Math.max(1,Math.ceil(studentRows.value.length/10))),pagedStudents=computed(()=>studentRows.value.slice((page.value-1)*10,page.value*10))
const latestResults=computed(()=>executionRows.value.filter(s=>s.evidence.latest).map(s=>s.evidence.latest))
function averageResult(key){const rows=latestResults.value.filter(r=>Number.isFinite(r[key])&&r[key]>=0);return rows.length?(rows.reduce((n,r)=>n+r[key],0)/rows.length).toFixed(1):'—'}
const comparableRows=computed(()=>executionRows.value.filter(s=>s.evidence.comparable))
const nonComparableRows=computed(()=>executionRows.value.filter(s=>!s.evidence.comparable))
const resultGroups=computed(()=>Object.fromEntries(['none','single','incomparable'].map(state=>[state,executionRows.value.filter(s=>s.evidence.state===state).length])))
const evidenceLabels={none:'暂无有效结果',single:'仅一次有效结果',incomparable:'暂不能比较'}
const evidenceReasons={none:'尚未训练完成或尚未生成可用报告',single:'需要两次同口径结果才能比较变化',incomparable:'目标维度或评分口径不一致、或缺少口径说明'}
const averageScore=computed(()=>{const rows=executionRows.value.filter(s=>s.evidence.latest);return rows.length&&rows.every(s=>s.evidence.signature&&s.evidence.signature===rows[0].evidence.signature)?(rows.reduce((n,s)=>n+s.evidence.latest.score,0)/rows.length).toFixed(1):'—'})
const scoreSampleNote=computed(()=>averageScore.value==='—'?(latestResults.value.length?'结果口径不一致或未说明，暂不计算平均值':'暂无有效结果'):latestResults.value.length+'人 · 最近一次同口径有效结果')
function signedDelta(value){return(value>0?'+':'')+value.toFixed(1)}
const attentionRows=computed(()=>executionRows.value.filter(s=>attentionReason(task.value,s)))
const advancedFilters=computed(()=>[['type',taskTypes[q.value.type]],['mode',modes[q.value.mode]],['from',q.value.from&&'发布自 '+q.value.from],['to',q.value.to&&'发布至 '+q.value.to]].filter(([key,label])=>q.value[key]&&label).map(([key,label])=>({key,label})))
const selectorMembers=computed(()=>selectorIds.value.map(id=>membersFor(draft.classId).find(s=>s.id===id)||{id,name:'已失效学生',number:id}))
const completionTrend=computed(()=>[...new Set(executionRows.value.filter(s=>s.completedAt).map(s=>s.completedAt.slice(0,10)))].sort().map(date=>({date,completed:executionRows.value.filter(s=>s.completedAt&&s.completedAt.slice(0,10)<=date).length})))
const activityGroups=computed(()=>{const events=[...(task.value?.events||[]),...executionRows.value.filter(s=>s.completedAt).map(s=>({at:s.completedAt,text:`${s.name}完成了训练`}))].sort((a,b)=>Date.parse(b.at)-Date.parse(a.at)).slice(0,10),groups=[];for(const e of events){const date=new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Shanghai'}).format(new Date(e.at)),name=date==='2026-10-03'?'今天':date==='2026-10-02'?'昨天':date;let group=groups.find(g=>g.date===name);if(!group){group={date:name,events:[]};groups.push(group)}group.events.push(e)}return groups})
const templateGoals=computed(()=>[...new Set(templates.value.map(goal).filter(Boolean))]),filteredTemplates=computed(()=>templates.value.filter(t=>t.ownerType===(q.value.owner||'SYSTEM')&&(!q.value.type||t.type===q.value.type)&&(!q.value.mode||t.trainingMode===q.value.mode)&&(!q.value.goal||goal(t)===q.value.goal)&&(!q.value.search||t.name.includes(q.value.search))).sort((a,b)=>q.value.sort==='updated'?(Date.parse(b.updatedAt)||0)-(Date.parse(a.updatedAt)||0):a.name.localeCompare(b.name,'zh-CN')))
const selectorRows=computed(()=>membersFor(draft.classId).filter(s=>(!drawerSearch.value||(s.name+s.number).includes(drawerSearch.value))&&(!drawerRole.value||s.position===drawerRole.value)&&(drawerEligibility.value==='all'||s.eligible===(drawerEligibility.value==='valid'))))
const drawerStudentRows=computed(()=>rowsFor(drawer.value?.task).filter(s=>matchesExecution(drawer.value.task,s,drawer.value.status,drawer.value.timing)&&(!drawerSearch.value||(s.name+s.number).includes(drawerSearch.value))))
const drawerTitle=computed(()=>({selector:'选择学生',content:drawer.value?.edit?'调整训练方案':'完整训练内容',students:'任务学生名单',template:'模板详情',student:'学生执行详情',result:'最近训练结果'}[drawer.value?.type]))
const modalTitle=computed(()=>({publish:'发布训练任务',extend:'延长截止时间',remind:'提醒未完成学生',end:'结束任务',saveTemplate:'保存为模板',renameTemplate:'重命名模板',deleteTemplate:'删除模板',leave:'未保存的修改'}[modal.value?.type]))
function initialize() {
  initializing = true
  clearTimeout(saveTimer)
  error.value = ''
  selected.value = []
  drawer.value = null
  modal.value = null
  try {
    tasks.value = demo.value ? loadTasks() : []
    templates.value = demo.value ? loadTemplates() : []
    if (surface.value === 'wizard') {
      let next = route.params.id ? tasks.value.find(t => t.id === route.params.id) : newDraft(q.value)
      if (route.params.id && (!next || lifecycle(next) !== 'DRAFT')) {
        error.value = '该任务不是可编辑草稿，请返回任务中心。'
        next = newDraft(q.value)
      }
      if (q.value.templateId) {
        const template = templates.value.find(t => t.id === q.value.templateId)
        if (template) next = applyTemplate(template)
        else error.value = '模板未找到，请返回模板中心。'
      }
      Object.keys(draft).forEach(k => delete draft[k])
      Object.assign(draft, JSON.parse(JSON.stringify(next)))
      // Native datetime-local inputs accept local minute precision, not ISO offsets.
      for (const field of ['startTime', 'deadline']) {
        if (draft[field] && /Z$|[+]08:00$/.test(draft[field])) {
          draft[field] = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(draft[field])).replace(' ', 'T')
        }
      }
      if (!draft.questions.length) draft.questions = recommendedQuestions(draft)
      step.value = draft.lastEditedStep || 1
      unlocked.value = draft.unlockedStep || step.value
      titleEdited.value = !!draft.title
      dirty.value = false
      saveState.value = '已保存'
    }
  } catch (e) {
    error.value = e.message
  } finally {
    initializing = false
  }
}
watch(()=>[surface.value,route.params.id,demo.value,q.value.templateId],initialize,{immediate:true,flush:'sync'})
watch(draft,()=>{if(initializing||surface.value!=='wizard'||!demo.value)return;dirty.value=true;saveState.value='保存中…';clearTimeout(saveTimer);saveTimer=setTimeout(persistDraft,500)},{deep:true,flush:'sync'})
watch(()=>[draft.type,draft.primaryAbility,draft.targetRole],()=>{if(initializing)return;if(!titleEdited.value)draft.title=(goal(draft)||taskTypes[draft.type])+'专项训练';draft.questions=recommendedQuestions(draft)},{flush:'sync'})
watch(()=>draft.mainQuestionCount,()=>{if(initializing)return;if(Number.isInteger(draft.mainQuestionCount)&&draft.mainQuestionCount>=1&&draft.mainQuestionCount<=30){const recommended=recommendedQuestions(draft);draft.questions=Array.from({length:draft.mainQuestionCount},(_,i)=>draft.questions[i]||recommended[i])}},{flush:'sync'})
watch(()=>draft.dynamicFollowUp,()=>{if(!initializing)draft.questions.forEach(q=>q.followUp=draft.dynamicFollowUp)},{flush:'sync'})
watch(()=>draft.allowRepeat,value=>{if(!value){draft.maxAttempts=1;draft.minValidAttempts=1}})
function persistDraft(){clearTimeout(saveTimer);if(!demo.value||!dirty.value)return true;try{const saved=saveDraft(draft);initializing=true;draft.version=saved.version;draft.updatedAt=saved.updatedAt;initializing=false;dirty.value=false;saveState.value='已保存';tasks.value=loadTasks();return true}catch(e){saveState.value='保存失败';error.value=e.message;return false}}
function changeStep(value){step.value=value;draft.lastEditedStep=value;draft.unlockedStep=unlocked.value}
function nextStep(){const failures=validation.value.filter(e=>e.step===step.value);if(failures.length){error.value=failures[0].message;return}error.value='';unlocked.value=Math.max(unlocked.value,step.value+1);changeStep(step.value+1)}
function openSelector(){selectorIds.value=[...draft.studentIds];drawerSearch.value='';drawerRole.value='';drawerEligibility.value='all';drawer.value={type:'selector'}}
function selectFiltered(checked){const ids=selectorRows.value.filter(s=>s.eligible).map(s=>s.id);selectorIds.value=checked?[...new Set([...selectorIds.value,...ids])]:selectorIds.value.filter(id=>!ids.includes(id))}
function confirmSelector(){const valid=new Set(membersFor(draft.classId).filter(s=>s.eligible).map(s=>s.id));if(selectorIds.value.some(id=>!valid.has(id))){error.value='所选学生已失效，请重新选择。';return}draft.studentIds=[...selectorIds.value];drawer.value=null}
function openContent(edit,config=draft){contentQuestions.value=JSON.parse(JSON.stringify(config.publishedAt?config.contentSnapshot||config.questions:config.questions));drawer.value={type:'content',edit,config}}
function moveQuestion(index,delta){const question=contentQuestions.value.splice(index,1)[0];contentQuestions.value.splice(index+delta,0,question)}
function addQuestion(){contentQuestions.value.push({id:crypto.randomUUID(),text:'',focus:goal(draft),followUp:draft.dynamicFollowUp})}
function replaceQuestion(i){contentQuestions.value[i]={...recommendedQuestions(draft)[i%draft.mainQuestionCount],id:crypto.randomUUID()}}
function confirmContent(){draft.questions=JSON.parse(JSON.stringify(contentQuestions.value));draft.mainQuestionCount=draft.questions.length;drawer.value=null}
function openStudents(t,status){drawerSearch.value='';drawerSelected.value=[];drawer.value={type:'students',task:t,...executionFilter(status)}}
function showStudent(row,t=task.value){drawer.value=null;router.push(link(`/teacher/students/${row.studentId}`,{classId:t.classId,insights:'1',sourceTaskId:t.id,returnTo:route.fullPath}))}
function showResult(row,t=task.value){drawer.value={type:'result',student:row,task:t}}
async function intervene(t,ids){try{const next=applyTemplate({...t,name:t.title});next.classId=t.classId;next.scope='students';next.studentIds=[...ids];next.sourceContext={type:'TASK_INTERVENTION',sourceTaskId:t.id,returnClass:t.sourceContext?.returnClass||q.value.returnClass};const saved=saveDraft(next);drawer.value=null;await router.push(link(`/teacher/tasks/${saved.id}/edit`))}catch(e){error.value=e.message}}
function useTemplate(t){drawer.value=null;router.push(link('/teacher/tasks/create',{templateId:t.id}))}
function openModal(type,template,context={}) {
  modalError.value = ''
  modal.value = {
    type, template,
    task: context.task || task.value,
    studentIds: context.studentIds || incomplete.value.map(s => s.studentId),
    value: type === 'renameTemplate' ? template.name : type === 'saveTemplate' ? task.value.title : '',
    description: template?.description || '',
  }
}
function remindSelection(){return remindRows(drawer.value.task,drawerSelected.value)}
async function remindRows(task,selectedIds) {
  const context = { task, studentIds: rowsFor(task).filter(s => selectedIds.includes(s.studentId) && s.completionStatus !== 'COMPLETED').map(s => s.studentId) }
  drawer.value = null
  await nextTick()
  openModal('remind', undefined, context)
}
function closeModal(){if(busy.value)return;modal.value=null;modalError.value='';pendingNavigation=null}
async function confirmModal() {
  busy.value = true
  modalError.value = ''
  try {
    const type = modal.value.type
    if (type === 'leave') {
      const target = pendingNavigation
      dirty.value = false
      routeBypass = true
      clearTimeout(saveTimer)
      modal.value = null
      if (target) await router.push(target)
      return
    }
    if (!demo.value) throw new Error('真实任务接口尚未开放。')
    if (type === 'publish') {
      if (!persistDraft()) throw new Error('草稿未保存成功，请重试。')
      const saved = publishDraft(draft)
      dirty.value = false
      modal.value = null
      await router.push(link(`/teacher/tasks/${saved.id}`, { tab: 'overview' }))
      message.value = '演示任务已发布，训练对象、内容和规则已固定。'
    } else if (type === 'extend') {
      extendDeadline(task.value, modal.value.value, modal.value.description)
      message.value = '已延长演示截止时间，当前逾期状态已重新计算。'
    } else if (type === 'end') {
      saveDraft({ ...task.value, manuallyEndedAt: new Date(demoNow()).toISOString() })
      message.value = '演示任务已结束。'
    } else if (type === 'remind') {
      const target = modal.value.task
      saveDraft({ ...target, events: [...(target.events || []), { at: new Date(demoNow()).toISOString(), text: `记录演示提醒：${modal.value.studentIds.length}名未完成学生`, studentIds: [...modal.value.studentIds] }] })
      message.value = '已记录演示提醒，未发送真实通知。'
    } else if (type === 'saveTemplate') {
      saveTemplate(task.value, modal.value.value, modal.value.description)
      message.value = '已保存到我的模板。'
    } else if (type === 'renameTemplate') {
      saveTemplate(modal.value.template, modal.value.value, modal.value.description, modal.value.template.id)
      message.value = '模板名称已更新。'
    } else if (type === 'deleteTemplate') {
      deleteTemplate(modal.value.template)
      message.value = '模板已删除，历史任务未受影响。'
    }
    tasks.value = loadTasks()
    templates.value = loadTemplates()
    modal.value = null
  } catch (e) {
    modalError.value = e.message
  } finally {
    busy.value = false
  }
}
function guard(to){if(routeBypass){routeBypass=false;return true}if(surface.value!=='wizard'||!dirty.value)return true;if(persistDraft())return true;pendingNavigation=to.fullPath;openModal('leave');return false}
onBeforeRouteLeave(guard)
onBeforeRouteUpdate((to,from)=>to.path!==from.path||to.query.demo!==from.query.demo||to.query.templateId!==from.query.templateId?guard(to):true)
function beforeUnload(event){if(dirty.value){persistDraft();if(dirty.value){event.preventDefault();event.returnValue=''}}}
window.addEventListener('beforeunload',beforeUnload)
onBeforeUnmount(()=>{clearTimeout(saveTimer);window.removeEventListener('beforeunload',beforeUnload)})
</script>
<style src="../../assets/styles/training-tasks.css"></style>
