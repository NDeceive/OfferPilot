import test from 'node:test'
import assert from 'node:assert/strict'
import { classifyExpression, createExpressionQueue, dominantExpression, displayExpression, expressionSeries, leadingExpressions } from '../src/utils/expressions.js'
const sample = id => ({ sampleId:id,questionId:0,roundNo:1,capturedAt:1700000000000,faceDetected:false,probabilities:null })

test('failed upload retains samples across reload; retries retain IDs and drain concurrent additions', async () => {
  const values = new Map()
  const storage = { getItem: k => values.get(k), setItem: (k, v) => values.set(k, v), removeItem: k => values.delete(k) }
  const failed = createExpressionQueue(8, async () => { throw new Error('offline') }, storage)
  failed.add(sample('same-id'))
  await assert.rejects(failed.flush())
  assert.equal(failed.size, 1)
  const batches = []
  let queue
  queue = createExpressionQueue(8, async (id, { samples }) => {
    assert.equal(id, 8)
    batches.push(samples)
    if (batches.length === 1) queue.add(sample('during-upload'))
  }, storage)
  const first = queue.flush()
  assert.equal(first, queue.flush())
  await first
  assert.deepEqual(batches.map(b => b.map(s => s.sampleId)), [['same-id'], ['during-upload']])
  assert.equal(queue.size, 0)
  assert.equal(values.size, 0)
})

test('uploads in batches of at most 100 and finds the highest probability expression', async () => {
  const sizes = []
  const queue = createExpressionQueue(9, async (_, { samples }) => sizes.push(samples.length), null)
  for (let i = 0; i < 205; i++) queue.add(sample(String(i)))
  await queue.flush()
  assert.deepEqual(sizes, [100, 100, 5])
  assert.equal(dominantExpression({ neutral: 0.1, happy: 0.8, sad: 0.1, angry: 0, fearful: 0, disgusted: 0, surprised: 0 }), 'happy')
})

test('isolates permanent errors and retains network or authentication failures', async () => {
  const queue=createExpressionQueue(9,async(_, {samples})=> {
    if(samples.some(s=>s.sampleId==='bad')) throw Object.assign(new Error('invalid'),{code:400})
    return {acceptedIds:samples.map(s=>s.sampleId),rejected:[]}
  },null)
  queue.add(sample('bad'));queue.add(sample('good'))
  await queue.flush()
  assert.equal(queue.size,0);assert.equal(queue.state.rejected,1)
  assert.equal(queue.state.rejectedSamples[0].sample.sampleId,'bad')
  let calls=0
  const expired=createExpressionQueue(9,async()=>{calls++;throw Object.assign(new Error('expired'),{code:401})},null)
  expired.add(sample('pending'))
  await assert.rejects(expired.flush());await assert.rejects(expired.flush())
  assert.equal(calls,1);assert.equal(expired.size,1);assert.equal(expired.state.authRequired,true)
})

test('server per-sample confirmation isolates a bad entry without deleting unacknowledged data',async()=>{
  const queue=createExpressionQueue(1,async()=>({acceptedIds:['good'],rejected:[{sampleId:'bad',reason:'wrong round'}]}),null)
  queue.add(sample('good'));queue.add(sample('bad'))
  await queue.flush();assert.equal(queue.size,0);assert.equal(queue.state.rejectedSamples[0].reason,'wrong round')
  const unconfirmed=createExpressionQueue(2,async()=>({acceptedIds:[],rejected:[]}),null)
  unconfirmed.add(sample('hold'));await assert.rejects(unconfirmed.flush());assert.equal(unconfirmed.size,1)
})

test('uncertain probabilities stay undecided, realtime smoothing preserves raw input, gaps are disconnected, ties are explicit',()=>{
  const p={neutral:.7,happy:.05,sad:.05,angry:.05,fearful:.05,disgusted:.05,surprised:.05}
  assert.equal(displayExpression(p).key,'neutral')
  const ambiguous={...p,neutral:.4,happy:.35}
  assert.equal(displayExpression(ambiguous).key,null)
  const before={...p};displayExpression(ambiguous,p);assert.deepEqual(p,before)
  const series=expressionSeries([{...sample('a'),capturedAt:1000,faceDetected:true,probabilities:p},{...sample('b'),capturedAt:11000,faceDetected:true,probabilities:p}],'neutral',1000)
  assert.equal(series.length,3);assert.equal(series[1][1],null)
  assert.deepEqual(leadingExpressions({dominantCounts:{neutral:3,happy:3}}),['neutral','happy'])
})

test('report classification separates ambiguous samples without changing raw probabilities',()=>{
 const p={neutral:.4,happy:.35,sad:.05,angry:.05,fearful:.05,disgusted:.05,surprised:.05};const raw={...p}
 assert.equal(classifyExpression(p).key,'uncertain');assert.equal(classifyExpression(p).first,'neutral');assert.deepEqual(p,raw)
 assert.equal(classifyExpression({...p,neutral:.7,happy:.05}).key,'neutral')
})
 test('uncertain samples participate in the most frequent result and ties',()=>{assert.deepEqual(leadingExpressions({dominantCounts:{happy:1},uncertainCount:99}),['uncertain']);assert.deepEqual(leadingExpressions({dominantCounts:{happy:2},uncertainCount:2}),['happy','uncertain'])})
