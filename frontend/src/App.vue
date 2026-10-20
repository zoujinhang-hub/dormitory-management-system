<script setup>
import { computed, onMounted, reactive, ref } from 'vue'

const user = ref(null)
const page = ref('overview')
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const loginForm = reactive({ username: 'admin', password: 'Admin123!' })
const summary = ref({ students: 0, rooms: 0, beds: 0, occupied: 0, openRepairs: 0, notices: 0 })
const rooms = ref([])
const students = ref([])
const repairs = ref([])
const notices = ref([])
const modal = ref(null)
const form = reactive({})

const isAdmin = computed(() => user.value?.role === 'ADMIN')
const pageNames = { overview: '工作总览', rooms: '宿舍空间', students: '学生档案', repairs: '报修工单', notices: '公告通知' }
const navigation = computed(() => [
  { key: 'overview', label: '工作总览', code: '01' },
  { key: 'rooms', label: isAdmin.value ? '宿舍空间' : '我的宿舍', code: '02' },
  ...(isAdmin.value ? [{ key: 'students', label: '学生档案', code: '03' }] : []),
  { key: 'repairs', label: '报修工单', code: isAdmin.value ? '04' : '03' },
  { key: 'notices', label: '公告通知', code: isAdmin.value ? '05' : '04' },
])
const roomMap = computed(() => Object.fromEntries(rooms.value.map(room => [room.id, `${room.building} · ${room.number}`])))
const pendingRepairs = computed(() => repairs.value.filter(repair => repair.status !== 'COMPLETED'))
const statusText = { SUBMITTED: '待受理', PROCESSING: '处理中', COMPLETED: '已完成' }

async function api(path, options = {}) {
  const response = await fetch(`/api${path}`, {
    credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json', ...options.headers },
    ...options,
  })
  if (response.status === 204) return null
  const data = await response.json().catch(() => ({}))
  if (!response.ok) throw new Error(data.message || data.detail || `请求失败（${response.status}）`)
  return data
}

async function refresh() {
  const [roomData, repairData, noticeData] = await Promise.all([
    api('/rooms'), api('/repairs'), api('/notices'),
  ])
  rooms.value = roomData
  repairs.value = repairData
  notices.value = noticeData
  if (isAdmin.value) {
    ;[summary.value, students.value] = await Promise.all([api('/summary'), api('/students')])
  }
}

async function signIn() {
  error.value = ''
  saving.value = true
  try {
    user.value = await api('/login', { method: 'POST', body: JSON.stringify(loginForm) })
    page.value = 'overview'
    await refresh()
  } catch (e) {
    error.value = e.message
  } finally {
    saving.value = false
  }
}

async function signOut() {
  try { await api('/logout', { method: 'POST' }) } catch (_) { /* The local session may already have expired. */ }
  user.value = null
  error.value = ''
}

function openModal(kind, item = null) {
  error.value = ''
  Object.keys(form).forEach(key => delete form[key])
  if (kind === 'room') Object.assign(form, { building: item?.building || '', number: item?.number || '', capacity: item?.capacity || 4 })
  if (kind === 'student') Object.assign(form, { username: item?.username || '', displayName: item?.displayName || '', roomId: item?.roomId ?? '', password: '' })
  if (kind === 'notice') Object.assign(form, { title: item?.title || '', content: item?.content || '' })
  if (kind === 'repair') Object.assign(form, { description: '' })
  modal.value = { kind, id: item?.id || null }
}

async function saveModal() {
  if (!modal.value) return
  error.value = ''
  saving.value = true
  try {
    const { kind, id } = modal.value
    const paths = { room: 'rooms', student: 'students', notice: 'notices', repair: 'repairs' }
    const payload = { ...form }
    if (kind === 'student') payload.roomId = payload.roomId === '' ? null : Number(payload.roomId)
    await api(`/${paths[kind]}${id ? `/${id}` : ''}`, {
      method: id ? 'PUT' : 'POST', body: JSON.stringify(payload),
    })
    modal.value = null
    await refresh()
  } catch (e) {
    error.value = e.message
  } finally {
    saving.value = false
  }
}

async function remove(kind, item) {
  if (!window.confirm(`确定删除“${item.title || item.displayName || item.number}”吗？`)) return
  error.value = ''
  try {
    await api(`/${kind}/${item.id}`, { method: 'DELETE' })
    await refresh()
  } catch (e) {
    error.value = e.message
  }
}

async function advance(repair) {
  const next = repair.status === 'SUBMITTED' ? 'PROCESSING' : 'COMPLETED'
  error.value = ''
  try {
    await api(`/repairs/${repair.id}/status`, { method: 'PATCH', body: JSON.stringify({ status: next }) })
    await refresh()
  } catch (e) {
    error.value = e.message
  }
}

function date(value) {
  return value ? new Date(value).toLocaleString('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }) : '—'
}

onMounted(async () => {
  try {
    user.value = await api('/me')
    await refresh()
  } catch (_) { user.value = null }
  finally { loading.value = false }
})
</script>

<template>
  <div v-if="loading" class="boot-screen">正在打开住宿服务平台…</div>

  <div v-else-if="!user" class="login-shell">
    <section class="login-story">
      <div class="wordmark">CAMPUS / LIVING</div>
      <div class="story-main">
        <span class="eyebrow light">校园空间 · 服务协作</span>
        <h1>让每一间宿舍，<br><em>都有回应。</em></h1>
        <p>入住信息、报修工单与校园通知，集中在一个清晰的工作台。</p>
      </div>
      <div class="story-footer">校园住宿服务平台 <span>课堂演示版 / 2026</span></div>
    </section>
    <section class="login-panel">
      <div class="login-card">
        <span class="eyebrow">WELCOME BACK</span>
        <h2>登录工作台</h2>
        <p class="muted">使用演示账号体验管理员或学生视角。</p>
        <form @submit.prevent="signIn" class="form-stack">
          <label>账号<input v-model.trim="loginForm.username" autocomplete="username" required placeholder="请输入账号"></label>
          <label>密码<input v-model="loginForm.password" type="password" autocomplete="current-password" required placeholder="请输入密码"></label>
          <p v-if="error" class="inline-error" role="alert">{{ error }}</p>
          <button class="primary wide" :disabled="saving">{{ saving ? '正在登录…' : '进入平台' }} <span aria-hidden="true">↗</span></button>
        </form>
        <div class="demo-accounts">
          <span>演示账号</span>
          <button @click="loginForm.username='admin';loginForm.password='Admin123!'">管理员 · admin</button>
          <button @click="loginForm.username='2026001';loginForm.password='Student123!'">学生 · 2026001</button>
        </div>
      </div>
    </section>
  </div>

  <div v-else class="app-shell">
    <aside class="sidebar">
      <div class="brand"><div class="brand-kicker">CAMPUS / LIVING</div><strong>校园住宿<br>服务平台</strong></div>
      <div class="sidebar-label">工作空间 <span>WORKSPACE</span></div>
      <nav aria-label="主导航">
        <button v-for="item in navigation" :key="item.key" :class="['nav-item', { active: page === item.key }]" @click="page = item.key; error = ''">
          <span class="nav-code">{{ item.code }}</span><span>{{ item.label }}</span><span class="nav-arrow">↗</span>
        </button>
      </nav>
      <div class="sidebar-bottom">
        <div class="side-note"><span class="side-note-dot"></span> 演示环境已就绪</div>
        <div class="profile"><div class="avatar">{{ user.displayName.slice(0, 1) }}</div><div><strong>{{ user.displayName }}</strong><small>{{ isAdmin ? '宿舍管理员' : '在校学生' }}</small></div></div>
        <button class="logout" @click="signOut">退出登录 <span>↗</span></button>
      </div>
    </aside>

    <main class="content">
      <header class="topbar"><span>校园住宿 / {{ pageNames[page] }}</span><span class="topbar-right">{{ isAdmin ? '管理端' : '学生端' }} <i></i> 2026 学年</span></header>
      <div class="content-inner">
        <div v-if="error" class="global-error" role="alert">{{ error }} <button @click="error=''" aria-label="关闭提示">×</button></div>

        <template v-if="page === 'overview'">
          <div class="page-heading"><div><span class="eyebrow">OVERVIEW / 01</span><h1>{{ isAdmin ? '今日工作，一目了然。' : '你好，' + user.displayName + '。' }}</h1><p>{{ isAdmin ? '查看住宿情况，及时跟进需要处理的报修。' : '你的宿舍、报修和校园通知都在这里。' }}</p></div><span class="heading-index">01 / 05</span></div>
          <template v-if="isAdmin">
            <div class="metric-grid">
              <article class="metric"><span>在住学生</span><strong>{{ summary.occupied }}<small> / {{ summary.students }}</small></strong><footer>已分配宿舍人数</footer></article>
              <article class="metric"><span>宿舍房间</span><strong>{{ summary.rooms }}</strong><footer>共 {{ summary.beds }} 个床位</footer></article>
              <article class="metric urgent"><span>待处理报修</span><strong>{{ summary.openRepairs }}</strong><footer>需要及时跟进</footer></article>
              <article class="metric"><span>公告通知</span><strong>{{ summary.notices }}</strong><footer>已发布通知</footer></article>
            </div>
          </template>
          <div class="overview-columns">
            <section class="panel"><div class="panel-head"><div><span class="eyebrow">SERVICE DESK</span><h2>{{ isAdmin ? '最近报修' : '我的报修' }}</h2></div><button class="text-action" @click="page='repairs'">查看全部 ↗</button></div>
              <div v-if="repairs.length" class="compact-list"><div v-for="repair in repairs.slice(0, 4)" :key="repair.id" class="compact-row"><span class="row-number">#{{ String(repair.id).padStart(3, '0') }}</span><div><strong>{{ repair.description }}</strong><small>{{ repair.roomName }} · {{ date(repair.createdAt) }}</small></div><span :class="['status', repair.status.toLowerCase()]">{{ statusText[repair.status] }}</span></div></div>
              <div v-else class="empty">还没有报修记录。</div>
            </section>
            <section class="panel notice-preview"><div class="panel-head"><div><span class="eyebrow">CAMPUS BOARD</span><h2>最新通知</h2></div><button class="text-action" @click="page='notices'">查看全部 ↗</button></div>
              <div v-if="notices.length" class="notice-highlight"><span>{{ date(notices[0].createdAt) }}</span><h3>{{ notices[0].title }}</h3><p>{{ notices[0].content }}</p></div><div v-else class="empty">还没有发布通知。</div>
            </section>
          </div>
          <section v-if="!isAdmin" class="my-room-card"><div><span class="eyebrow">MY ROOM</span><h2>{{ rooms[0] ? `${rooms[0].building} · ${rooms[0].number}` : '暂未分配宿舍' }}</h2><p>{{ rooms[0] ? `当前入住 ${rooms[0].occupied} / ${rooms[0].capacity} 人` : '请联系宿舍管理员完成分配。' }}</p></div><button class="primary" @click="openModal('repair')">提交报修 ↗</button></section>
        </template>

        <template v-else-if="page === 'rooms'">
          <div class="page-heading"><div><span class="eyebrow">SPACES / 02</span><h1>{{ isAdmin ? '宿舍空间' : '我的宿舍' }}</h1><p>房间与床位信息，保持清晰可查。</p></div><button v-if="isAdmin" class="primary" @click="openModal('room')">＋ 新增宿舍</button></div>
          <div v-if="rooms.length" class="room-grid"><article v-for="room in rooms" :key="room.id" class="room-card"><div class="room-card-top"><span>{{ room.building }}</span><span class="room-id">ROOM / {{ String(room.id).padStart(3, '0') }}</span></div><h2>{{ room.number }}</h2><div class="occupancy"><div><span :style="{ width: `${100 * room.occupied / room.capacity}%` }"></span></div><small>{{ room.occupied }} / {{ room.capacity }} 人入住</small></div><div v-if="isAdmin" class="card-actions"><button @click="openModal('room',room)">编辑</button><button @click="remove('rooms',room)">删除</button></div></article></div>
          <div v-else class="empty large">{{ isAdmin ? '还没有宿舍，点击“新增宿舍”开始。' : '你还没有分配宿舍。' }}</div>
        </template>

        <template v-else-if="page === 'students' && isAdmin">
          <div class="page-heading"><div><span class="eyebrow">RESIDENTS / 03</span><h1>学生档案</h1><p>管理学生账号、姓名与入住宿舍。</p></div><button class="primary" @click="openModal('student')">＋ 新增学生</button></div>
          <div class="table-panel"><table><thead><tr><th>学号 / 账号</th><th>学生姓名</th><th>入住宿舍</th><th>操作</th></tr></thead><tbody><tr v-for="student in students" :key="student.id"><td class="mono">{{ student.username }}</td><td><strong>{{ student.displayName }}</strong></td><td>{{ roomMap[student.roomId] || '未分配' }}</td><td><button class="table-action" @click="openModal('student',student)">编辑</button><button class="table-action danger" @click="remove('students',student)">删除</button></td></tr></tbody></table><div v-if="!students.length" class="empty">还没有学生档案。</div></div>
        </template>

        <template v-else-if="page === 'repairs'">
          <div class="page-heading"><div><span class="eyebrow">SERVICE DESK / {{ isAdmin ? '04' : '03' }}</span><h1>报修工单</h1><p>{{ isAdmin ? '按状态跟进每一项住宿问题。' : '提交问题后，可以在这里查看处理进度。' }}</p></div><button v-if="!isAdmin" class="primary" @click="openModal('repair')">＋ 提交报修</button></div>
          <div class="section-meta"><span>{{ isAdmin ? '全部工单' : '我的工单' }}</span><span>{{ repairs.length }} 条记录 · {{ pendingRepairs.length }} 条待完成</span></div>
          <div v-if="repairs.length" class="repair-list"><article v-for="repair in repairs" :key="repair.id" class="repair-card"><div class="repair-index">#{{ String(repair.id).padStart(3, '0') }}</div><div class="repair-main"><div class="repair-title"><h2>{{ repair.description }}</h2><span :class="['status', repair.status.toLowerCase()]">{{ statusText[repair.status] }}</span></div><p>{{ repair.roomName }} <template v-if="isAdmin">· {{ repair.studentName }}</template> · {{ date(repair.createdAt) }}</p></div><button v-if="isAdmin && repair.status !== 'COMPLETED'" class="outline" @click="advance(repair)">{{ repair.status === 'SUBMITTED' ? '受理工单' : '标记完成' }} ↗</button></article></div>
          <div v-else class="empty large">还没有报修工单。</div>
        </template>

        <template v-else-if="page === 'notices'">
          <div class="page-heading"><div><span class="eyebrow">BULLETIN / {{ isAdmin ? '05' : '04' }}</span><h1>公告通知</h1><p>重要消息，准确传达到每一位住宿学生。</p></div><button v-if="isAdmin" class="primary" @click="openModal('notice')">＋ 发布通知</button></div>
          <div v-if="notices.length" class="notice-list"><article v-for="notice in notices" :key="notice.id"><div class="notice-date">{{ date(notice.createdAt) }}</div><div><h2>{{ notice.title }}</h2><p>{{ notice.content }}</p></div><div v-if="isAdmin" class="notice-actions"><button @click="openModal('notice',notice)">编辑</button><button @click="remove('notices',notice)">删除</button></div></article></div><div v-else class="empty large">还没有公告通知。</div>
        </template>
      </div>
    </main>
  </div>

  <div v-if="modal" class="modal-backdrop" @click.self="modal=null"><div class="modal-card" role="dialog" aria-modal="true" :aria-label="modal.kind"><div class="modal-head"><div><span class="eyebrow">CAMPUS / LIVING</span><h2>{{ {room: modal.id ? '编辑宿舍' : '新增宿舍', student: modal.id ? '编辑学生' : '新增学生', notice: modal.id ? '编辑通知' : '发布通知', repair: '提交报修'}[modal.kind] }}</h2></div><button @click="modal=null" aria-label="关闭">×</button></div>
    <form @submit.prevent="saveModal" class="form-stack modal-form">
      <template v-if="modal.kind==='room'"><label>楼栋<input v-model.trim="form.building" required maxlength="80" placeholder="例如：东区 1 栋"></label><label>房间号<input v-model.trim="form.number" required maxlength="30" placeholder="例如：101"></label><label>床位数<input v-model.number="form.capacity" type="number" min="1" max="12" required></label></template>
      <template v-if="modal.kind==='student'"><label>学号 / 登录账号<input v-model.trim="form.username" required maxlength="50"></label><label>学生姓名<input v-model.trim="form.displayName" required maxlength="80"></label><label>{{ modal.id ? '重置密码（留空则不修改）' : '初始密码（至少 8 位）' }}<input v-model="form.password" type="password" :required="!modal.id" minlength="8" autocomplete="new-password"></label><label>入住宿舍<select v-model="form.roomId"><option value="">暂不分配</option><option v-for="room in rooms" :key="room.id" :value="room.id">{{ room.building }} · {{ room.number }}（{{ room.occupied }}/{{ room.capacity }}）</option></select></label></template>
      <template v-if="modal.kind==='notice'"><label>通知标题<input v-model.trim="form.title" required maxlength="200"></label><label>通知内容<textarea v-model.trim="form.content" required maxlength="2000" rows="5"></textarea></label></template>
      <template v-if="modal.kind==='repair'"><label>问题描述<textarea v-model.trim="form.description" required maxlength="500" rows="5" placeholder="请写明故障位置与具体情况"></textarea></label></template>
      <p v-if="error" class="inline-error" role="alert">{{ error }}</p>
      <div class="modal-actions"><button type="button" class="outline" @click="modal=null">取消</button><button class="primary" :disabled="saving">{{ saving ? '正在保存…' : '确认保存' }}</button></div>
    </form></div></div>
</template>
