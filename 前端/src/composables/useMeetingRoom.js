import { computed, onUnmounted, reactive, ref } from 'vue'
import { MSG, CLOSE, meetingWsUrl, normalizeCode, isValidCode } from '../utils/meetingProtocol'
import { getIceServers } from '../utils/iceServers'
import { getApiBase } from '../utils/apiBase'
import { postMeetingAdvice } from '../api/meeting'
import { useUserStore } from '../store/user'
import { useMeetingMediaShared } from './useMeetingMedia'

/**
 * 会议室核心：WebSocket 信令 + 每对成员一个 RTCPeerConnection（mesh，≤6 人）。
 *
 * 协商方向是确定性的（无 glare）：同一对 peer 中 seq 大的一方发 offer。
 * 服务端分配的 seq 单调递增——后进者 seq 更大，即「后进者向每个老成员发起」。
 * 双方的 seq 都来自 joined / peer-joined，独立算出同一结论，不需要额外协商。
 *
 * 静音/关摄像头只翻本地 track.enabled（不重协商）；track.enabled=false 后
 * 所有已连 peer 立即收到黑帧/静音帧。
 *
 * 不做 perfect negotiation / ICE restart：本场景不换设备、不共享屏幕；
 * 将来要支持这些再升级（届时把 createOffer 改成 negotiationneeded 驱动）。
 */
export function useMeetingRoom() {
  const userStore = useUserStore()
  // 共享采集实例：与「加入会议」页的设备预览复用同一条流，
  // 避免进房瞬间先停后采（摄像头灯闪 + Windows 上偶发 NotReadableError）
  const media = useMeetingMediaShared()

  const status = ref('idle') // idle | connecting | joining | joined | ended | kicked | error
  const errorMessage = ref('')
  const meeting = ref(null)
  const selfId = ref(null)
  const selfSeq = ref(0)
  const adviceList = ref([])

  /** 成员表（id → {id,name,role,seq,stream,micOn,camOn,connectionState}），模板遍历它 */
  const members = reactive(new Map())
  const peers = computed(() => [...members.values()])
  const remotePeers = computed(() => peers.value.filter((p) => p.id !== selfId.value))

  // 非响应式资源：RTCPeerConnection 不进 Vue 响应式（避免 Proxy 干扰 WebRTC 内部）
  const pcs = new Map()
  const pendingIce = new Map()
  let ws = null
  let intentionalClose = false
  let currentCode = ''

  const isHost = computed(
    () => !!meeting.value && !!selfId.value && Number(meeting.value.hostId) === Number(selfId.value),
  )

  /* ---------------- 公开动作 ---------------- */

  /**
   * 加入会议（幂等：同一会议号已在连接中/已进入则直接返回）。
   * 顺序：采流 → 建 WS → onopen 发 join；joined 到达前不建任何 RTCPeerConnection。
   */
  async function join(code) {
    const normalized = normalizeCode(code)
    // 直接敲 /meeting/xxx/room 进来的兜底；格式错就不必白白采一次摄像头
    if (!isValidCode(normalized)) {
      status.value = 'error'
      errorMessage.value = '会议号格式不正确（应为 8 位字母数字，如 3F7K-2Q9A）'
      return
    }
    if ((status.value === 'joining' || status.value === 'joined') && currentCode === normalized) return
    if (status.value === 'joining' || status.value === 'joined') leave()

    currentCode = normalized
    status.value = 'connecting'
    errorMessage.value = ''
    adviceList.value = []

    // 1) 音视频（共享实例可能已由「加入会议」页预览采好）
    if (!media.isActive.value) await media.start()
    if (media.status.value !== 'active') {
      status.value = 'error'
      errorMessage.value = media.errorMessage.value || '摄像头/麦克风开启失败'
      return
    }

    // 2) 信令连接；onopen 发 join，后续全部由消息驱动
    status.value = 'joining'
    const url = meetingWsUrl(userStore.token, { base: getApiBase() })
    ws = new WebSocket(url)
    ws.onopen = () => send({ type: MSG.JOIN, code: normalized })
    ws.onmessage = (event) => handleMessage(event)
    ws.onclose = (event) => handleClose(event)
    // 关闭细节交给 onclose 统一处理，onerror 不重复提示
  }

  /** 离开会议：通知服务端 → 关所有连接 → 停音视频。幂等，组件卸载时兜底调用。 */
  function leave() {
    if (status.value === 'idle') return
    send({ type: MSG.LEAVE })
    intentionalClose = true
    teardown()
    media.stop()
    status.value = 'idle'
    currentCode = ''
  }

  function toggleMic() {
    if (!media.micAvailable.value) return
    const on = media.toggleMic()
    send({ type: MSG.MIC, on })
  }

  function toggleCam() {
    const on = media.toggleCam()
    send({ type: MSG.CAM, on })
  }

  /** 写建议：REST 落库，回显由服务端 pushAdvice 广播回来（含作者自己的单播回执） */
  async function postAdvice(content, targetUserId = null) {
    if (!meeting.value?.id) throw new Error('尚未进入会议')
    return postMeetingAdvice(meeting.value.id, { content, targetUserId })
  }

  /** 进入房间时灌入历史建议（GET /{id}/advice 的结果），后续靠 advice-new 增量 */
  function setAdviceSeed(list) {
    ;(list || []).forEach(addAdvice)
  }

  /* ---------------- 信令处理 ---------------- */

  function handleMessage(event) {
    let msg
    try {
      msg = JSON.parse(event.data)
    } catch {
      return
    }
    switch (msg.type) {
      case MSG.JOINED: onJoined(msg); break
      case MSG.PEER_JOINED: onPeerJoined(msg.peer); break
      case MSG.PEER_LEFT: removePeer(msg.id); break
      case MSG.PEER_STATE: onPeerState(msg); break
      case MSG.OFFER: onOffer(msg); break
      case MSG.ANSWER: onAnswer(msg); break
      case MSG.ICE: onIce(msg); break
      case MSG.ADVICE_NEW: addAdvice(msg.advice); break
      case MSG.MEETING_ENDED: fail('ended', '会议已被发起人结束'); break
      case MSG.ERROR: onServerError(msg); break
      default: break
    }
  }

  function onJoined(msg) {
    selfId.value = msg.selfId
    selfSeq.value = msg.you?.seq ?? 0
    meeting.value = msg.meeting || null
    status.value = 'joined'

    if (msg.you) upsertMember(msg.you)
    ;(msg.peers || []).forEach((peer) => {
      upsertMember(peer)
      // 后进者 seq 更大 → 由我向每个老成员发起 offer；否则等对方来 offer
      if (selfSeq.value > (peer.seq || 0)) startOffer(peer.id)
    })
  }

  function onPeerJoined(peer) {
    if (!peer || peer.id == null || peer.id === selfId.value) return
    upsertMember(peer)
    if (selfSeq.value > (peer.seq || 0)) startOffer(peer.id)
  }

  function onPeerState(msg) {
    const member = members.get(msg.id)
    if (!member) return
    if (typeof msg.micOn === 'boolean') member.micOn = msg.micOn
    if (typeof msg.camOn === 'boolean') member.camOn = msg.camOn
  }

  async function onOffer(msg) {
    if (msg.from == null) return
    if (!members.has(msg.from)) upsertMember({ id: msg.from })
    const pc = ensurePeer(msg.from)
    // 简单兜底（不做 perfect negotiation）：非 stable 时忽略新 offer，避免本地 answer 撞车
    if (pc.signalingState !== 'stable') return
    try {
      await pc.setRemoteDescription(msg.sdp)
      await flushPendingIce(msg.from)
      const answer = await pc.createAnswer()
      await pc.setLocalDescription(answer)
      send({ type: MSG.ANSWER, to: msg.from, sdp: pc.localDescription })
    } catch (e) {
      console.warn('[会议] 处理 offer 失败', e)
    }
  }

  async function onAnswer(msg) {
    const pc = pcs.get(msg.from)
    if (!pc || pc.signalingState !== 'have-local-offer') return
    try {
      await pc.setRemoteDescription(msg.sdp)
      await flushPendingIce(msg.from)
    } catch (e) {
      console.warn('[会议] 处理 answer 失败', e)
    }
  }

  async function onIce(msg) {
    const pc = pcs.get(msg.from)
    if (!pc || !msg.candidate) return
    // ICE 早到（candidate 先于 remoteDescription 到达）：先排队，
    // setRemoteDescription 之后补灌。同机几乎测不出、双机偶发，属最难查的一类问题。
    if (!pc.remoteDescription) {
      if (!pendingIce.has(msg.from)) pendingIce.set(msg.from, [])
      pendingIce.get(msg.from).push(msg.candidate)
      return
    }
    await pc.addIceCandidate(msg.candidate).catch(() => {})
  }

  function onServerError(msg) {
    switch (msg.code) {
      case 'NOT_FOUND':
        fail('error', msg.message || '会议号不存在，请核对后重试')
        break
      case 'MEETING_ENDED':
        fail('ended', '会议已结束')
        break
      case 'ROOM_FULL':
        fail('error', msg.message || '会议人数已满')
        break
      case 'PEER_GONE':
        // 对方刚好离开的竞态：本地会由 peer-left 收拾，不重复报错
        break
      default:
        console.warn('[会议] 服务端错误', msg.code, msg.message)
        break
    }
  }

  function handleClose(event) {
    if (intentionalClose) {
      intentionalClose = false
      return
    }
    switch (event.code) {
      case CLOSE.KICKED:
        fail('kicked', '你的账号已在另一个窗口进入这个会议，当前连接被移出。')
        break
      case CLOSE.MEETING_ENDED:
        fail('ended', '会议已被发起人结束')
        break
      case CLOSE.ROOM_FULL:
        fail('error', '房间已满，无法进入')
        break
      case CLOSE.NORMAL:
        // 服务端正常关闭（一般是我们自己离开）；还在 joined 态说明是意外，按断线处理
        if (status.value === 'joined' || status.value === 'joining') {
          fail('error', '已与服务器断开连接，请返回后重新进入会议')
        }
        break
      default:
        // 1006 等异常断开
        if (status.value !== 'idle') {
          fail('error', '与服务器的连接已断开，请检查网络后重新进入会议')
        }
        break
    }
  }

  /* ---------------- PeerConnection 管理 ---------------- */

  function ensurePeer(peerId) {
    const existing = pcs.get(peerId)
    if (existing) return existing
    const pc = new RTCPeerConnection({ iceServers: getIceServers() })
    pcs.set(peerId, pc)

    const stream = media.stream.value
    if (stream) stream.getTracks().forEach((track) => pc.addTrack(track, stream))

    pc.onicecandidate = (e) => {
      if (e.candidate) send({ type: MSG.ICE, to: peerId, candidate: e.candidate.toJSON() })
    }
    pc.ontrack = (e) => {
      const member = members.get(peerId)
      if (!member) return
      if (e.streams?.[0]) {
        member.stream = e.streams[0]
      } else {
        // 理论上不会走到：本地 addTrack 时绑定了 stream，对端才能拿到 streams[0]
        if (!member.stream) member.stream = new MediaStream()
        if (!member.stream.getTracks().includes(e.track)) member.stream.addTrack(e.track)
      }
    }
    pc.onconnectionstatechange = () => {
      const member = members.get(peerId)
      if (member) member.connectionState = pc.connectionState
    }
    return pc
  }

  async function startOffer(peerId) {
    const pc = ensurePeer(peerId)
    try {
      const offer = await pc.createOffer()
      await pc.setLocalDescription(offer)
      send({ type: MSG.OFFER, to: peerId, sdp: pc.localDescription })
    } catch (e) {
      console.warn('[会议] createOffer 失败', e)
    }
  }

  async function flushPendingIce(peerId) {
    const queued = pendingIce.get(peerId)
    if (!queued?.length) return
    pendingIce.delete(peerId)
    const pc = pcs.get(peerId)
    if (!pc) return
    for (const candidate of queued) {
      await pc.addIceCandidate(candidate).catch(() => {})
    }
  }

  function removePeer(peerId) {
    const pc = pcs.get(peerId)
    if (pc) {
      pc.onicecandidate = null
      pc.ontrack = null
      pc.onconnectionstatechange = null
      try { pc.close() } catch { /* 已关闭 */ }
      pcs.delete(peerId)
    }
    pendingIce.delete(peerId)
    members.delete(peerId)
  }

  /** 收尾：关全部 pc、清成员、**停音视频**、断 WS（异常路径统一走这里） */
  function teardown() {
    pcs.forEach((pc) => {
      try { pc.close() } catch { /* 已关闭 */ }
    })
    pcs.clear()
    pendingIce.clear()
    members.clear()
    if (ws) {
      try { ws.close() } catch { /* 已关闭 */ }
      ws = null
    }
  }

  /** 异常终止（被踢 / 会议结束 / 断线 / 服务端拒绝）：置状态 + 全套清理 */
  function fail(nextStatus, message) {
    if (status.value === 'idle') return
    status.value = nextStatus
    errorMessage.value = message
    intentionalClose = true
    teardown()
    media.stop()
  }

  /* ---------------- 小工具 ---------------- */

  function upsertMember(peer) {
    const existing = members.get(peer.id)
    if (existing) {
      if (peer.name) existing.name = peer.name
      if (peer.role) existing.role = peer.role
      if (peer.seq != null) existing.seq = peer.seq
      return existing
    }
    const member = {
      id: peer.id,
      name: peer.name || '参会者',
      role: peer.role || '',
      seq: peer.seq || 0,
      stream: null,
      micOn: true,
      camOn: true,
      connectionState: '',
    }
    members.set(peer.id, member)
    return member
  }

  function addAdvice(item) {
    if (!item || item.id == null) return
    // 幂等去重：自己的单播回执、历史种子与后续增量都可能重叠
    if (adviceList.value.some((a) => a.id === item.id)) return
    adviceList.value.unshift(item)
  }

  function send(obj) {
    if (ws && ws.readyState === WebSocket.OPEN) {
      try { ws.send(JSON.stringify(obj)) } catch { /* 发送失败按断开处理 */ }
    }
  }

  onUnmounted(leave)

  return {
    status,
    errorMessage,
    meeting,
    selfId,
    peers,
    remotePeers,
    localStream: media.stream,
    micEnabled: media.micEnabled,
    camEnabled: media.camEnabled,
    micAvailable: media.micAvailable,
    mediaStatus: media.status,
    mediaError: media.errorMessage,
    adviceList,
    isHost,
    join,
    leave,
    toggleMic,
    toggleCam,
    postAdvice,
    setAdviceSeed,
  }
}
