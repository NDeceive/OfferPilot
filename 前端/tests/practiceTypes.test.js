import test from 'node:test'
import assert from 'node:assert/strict'
import { isObjectiveType, practiceTypeLabel, toggleObjectiveAnswer, objectiveQuestionCount } from '../src/utils/practiceTypes.js'
import { readPracticeDraft, writePracticeDraft } from '../src/utils/practiceDrafts.js'

test('selection preserves false and canonicalizes unordered multi-select; drafts survive refresh', () => {
  assert.equal(toggleObjectiveAnswer('true','false',false),'false')
  assert.equal(toggleObjectiveAnswer('','C',true),'C')
  assert.equal(toggleObjectiveAnswer('C','A',true),'A,C')
  assert.equal(toggleObjectiveAnswer('A,C','A',true),'C')
  const values=new Map(), storage={getItem:k=>values.get(k),setItem:(k,v)=>values.set(k,v)}
  writePracticeDraft(storage,1,10,{questions:{11:{answer:'false'},12:{answer:'A,C'}}})
  assert.equal(readPracticeDraft(storage,1,10).questions[11].answer,'false')
  assert.equal(readPracticeDraft(storage,1,10).questions[12].answer,'A,C')
  assert.ok(isObjectiveType('TRUE_FALSE'))
  assert.equal(practiceTypeLabel('MULTIPLE_CHOICE'),'多选题')
})

test('objective counts respect role, topic and difficulty and preserve legacy sizing', () => {
  const catalog={objectiveCoverage:[{type:'CHOICE',difficulty:1,jobCodes:['BE-JAVA'],topics:['Java核心']},{type:'CHOICE',difficulty:3,jobCodes:['BE-JAVA'],topics:['JVM']},{type:'TRUE_FALSE',difficulty:1,jobCodes:['FE-WEB'],topics:['JavaScript']}]}
  const role={code:'BE-JAVA'}
  assert.equal(objectiveQuestionCount('综合高频',role,catalog,'choice','basic'),1)
  assert.equal(objectiveQuestionCount('综合高频',role,catalog,'choice','all'),2)
  assert.equal(objectiveQuestionCount('JVM',role,catalog,'choice','basic'),0)
  assert.equal(objectiveQuestionCount('Java核心',role,catalog,'true_false'),0)
  assert.equal(objectiveQuestionCount('Java核心',role,catalog,'knowledge'),null)
})
