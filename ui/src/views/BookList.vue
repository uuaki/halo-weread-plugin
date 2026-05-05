<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'

interface BookSpec {
  bookId: string
  title: string
  author: string
  cover: string
  intro: string
  publisher: string
  publishTime: string
  isbn: string
  category: string
  totalWords: number
  readInfo: number
  progress: number
  readingTime: number
  noteCount: number
  reviewCount: number
  lastReadTime: number
  pcUrl?: string
  finishTime?: number
  hidden?: boolean
}

interface BookItem {
  metadata: { name: string }
  spec: BookSpec
}

const books = ref<BookItem[]>([])
const loading = ref(false)
const message = ref('')

const toast = ref({
  show: false,
  message: '',
  type: 'success'
})

const showToast = (msg: string, type: 'success' | 'error' = 'success') => {
  toast.value.message = msg
  toast.value.type = type
  toast.value.show = true
  setTimeout(() => {
    toast.value.show = false
  }, 3000)
}

const totalBooks = computed(() => books.value.length)
const totalNotes = computed(() => books.value.reduce((s, b) => s + (b.spec.noteCount || 0), 0))
const totalReviews = computed(() => books.value.reduce((s, b) => s + (b.spec.reviewCount || 0), 0))

const fetchBooks = async () => {
  loading.value = true
  message.value = ''
  try {
    const res = await fetch('/api/admin/halo-weread-plugin/books')
    if (res.ok) {
      books.value = await res.json()
    } else {
      message.value = '获取书籍列表失败'
    }
  } catch (error) {
    message.value = '请求失败'
  } finally {
    loading.value = false
  }
}

const deleteBook = async (name: string, title: string) => {
  if (!confirm('确定删除「' + title + '」？')) return
  try {
    const res = await fetch('/api/admin/halo-weread-plugin/books/' + name, { method: 'DELETE' })
    if (res.ok) {
      books.value = books.value.filter((b) => b.metadata.name !== name)
      showToast('书籍已成功删除')
    } else {
      const err = await res.json()
      showToast(err.message || '删除失败', 'error')
    }
  } catch (error) {
    showToast('删除出现异常', 'error')
  }
}

const toggleVisibility = async (book: BookItem) => {
  const targetStatus = !book.spec.hidden
  try {
    const res = await fetch(`/api/admin/halo-weread-plugin/books/${book.metadata.name}/toggle-visibility`, {
      method: 'PATCH'
    })
    if (res.ok) {
      const data = await res.json()
      book.spec.hidden = data.hidden
      showToast(book.spec.hidden ? '书籍已设置为隐藏' : '书籍已设置为显示')
    } else {
      showToast('状态更新失败', 'error')
    }
  } catch (error) {
    showToast('操作异常', 'error')
  }
}

const formatTime = (ts: number) => {
  if (!ts) return '--'
  const d = new Date(ts)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

const formatDuration = (minutes: number) => {
  if (!minutes) return '0分'
  if (minutes < 60) return minutes + '分'
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return h > 0 ? `${h}时${m}分` : `${m}分`
}

const readStatusText = (info: number) => {
  if (info === 3) return '已读完'
  if (info === 2) return '在读'
  return '未读'
}

onMounted(() => {
  fetchBooks()
})
</script>

<template>
  <div class="bl-container">
    <!-- Toast 提示 -->
    <Transition name="toast">
      <div v-if="toast.show" class="h-toast" :class="toast.type">
        <span class="toast-icon">{{ toast.type === 'success' ? '✅' : '❌' }}</span>
        <span class="toast-text">{{ toast.message }}</span>
      </div>
    </Transition>
    <!-- 统计面板 -->
    <div class="stats-grid">
      <div class="stat-box">
        <div class="stat-icon book-icon">
          <span class="icon-text">书籍</span>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ totalBooks }}</div>
          <div class="stat-title">已同步书籍</div>
        </div>
      </div>
      <div class="stat-box">
        <div class="stat-icon note-icon">
          <span class="icon-text">划线</span>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ totalNotes }}</div>
          <div class="stat-title">划线记录</div>
        </div>
      </div>
      <div class="stat-box">
        <div class="stat-icon review-icon">
          <span class="icon-text">心得</span>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ totalReviews }}</div>
          <div class="stat-title">想法心得</div>
        </div>
      </div>
    </div>

    <!-- 列表区域 -->
    <div class="list-wrapper">
      <!-- 桌面端表格 -->
      <table class="h-table desktop-only">
        <thead>
          <tr>
            <th width="60">封面</th>
            <th>书名与作者</th>
            <th width="100">阅读进度</th>
            <th width="100">阅读时长</th>
            <th width="80" class="text-center">划线</th>
            <th width="80" class="text-center">想法</th>
            <th width="150">最后阅读</th>
            <th width="120" class="text-center">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="book in books" :key="book.metadata.name" :class="{ 'row-hidden': book.spec.hidden }">
            <td>
              <div class="book-cover-wrapper">
                <img v-if="book.spec.cover" :src="book.spec.cover" class="book-cover" />
                <div v-else class="book-cover-placeholder"></div>
              </div>
            </td>
            <td>
              <div class="book-info">
                <a v-if="book.spec.pcUrl" :href="book.spec.pcUrl" target="_blank" class="book-title" :title="book.spec.title">
                  {{ book.spec.title }}
                </a>
                <div v-else class="book-title" :title="book.spec.title">{{ book.spec.title }}</div>
                <div class="book-author">{{ book.spec.author }}</div>
              </div>
            </td>
            <td>
              <div class="progress-col">
                <div class="progress-bar-bg">
                  <div class="progress-bar-fill" :style="{ width: Math.min(book.spec.progress || 0, 100) + '%' }"></div>
                </div>
                <span class="progress-text">{{ (book.spec.progress || 0).toFixed(1) }}%</span>
              </div>
            </td>
            <td class="text-muted fs-12">{{ formatDuration(book.spec.readingTime) }}</td>
            <td class="text-center fw-500">{{ book.spec.noteCount || 0 }}</td>
            <td class="text-center fw-500">{{ book.spec.reviewCount || 0 }}</td>
            <td class="text-muted fs-11">{{ formatTime(book.spec.lastReadTime) }}</td>
            <td class="text-center">
              <div class="row-actions">
                <div class="switch-wrapper" :title="book.spec.hidden ? '当前已隐藏' : '当前已显示'">
                  <label class="h-switch">
                    <input type="checkbox" :checked="book.spec.hidden" @change="toggleVisibility(book)">
                    <span class="slider round"></span>
                  </label>
                  <span class="switch-label">{{ book.spec.hidden ? '隐藏' : '显示' }}</span>
                </div>
                <button class="h-btn-text danger" @click="deleteBook(book.metadata.name, book.spec.title)" title="删除书籍">
                  删除
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>

      <!-- 移动端卡片列表 -->
      <div class="mobile-only book-cards">
        <div v-for="book in books" :key="book.metadata.name" class="book-card" :class="{ 'card-hidden': book.spec.hidden }">
          <div class="card-main">
            <div class="book-cover-wrapper">
              <img v-if="book.spec.cover" :src="book.spec.cover" class="book-cover" />
              <div v-else class="book-cover-placeholder"></div>
            </div>
            <div class="card-info">
              <div class="book-title">{{ book.spec.title }}</div>
              <div class="book-author">{{ book.spec.author }}</div>
              <div class="card-stats">
                <span>划线 {{ book.spec.noteCount || 0 }}</span>
                <span class="dot">·</span>
                <span class="status-tag" :class="book.spec.hidden ? 'tag-hidden' : 'tag-visible'">
                  {{ book.spec.hidden ? '隐藏' : '显示' }}
                </span>
              </div>
            </div>
            <div class="card-action-btns">
               <label class="h-switch mini">
                 <input type="checkbox" :checked="book.spec.hidden" @change="toggleVisibility(book)">
                 <span class="slider round"></span>
               </label>
               <button class="card-del-btn" @click="deleteBook(book.metadata.name, book.spec.title)">删除</button>
            </div>
          </div>
          <div class="card-footer">
            <div class="card-progress">
              <div class="progress-bar-bg">
                <div class="progress-bar-fill" :style="{ width: Math.min(book.spec.progress || 0, 100) + '%' }"></div>
              </div>
              <span class="progress-text">{{ (book.spec.progress || 0).toFixed(1) }}%</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.bl-container {
  padding: 24px;
  animation: fadeIn 0.3s ease-out;
  max-width: 1200px;
  margin: 0 auto;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* Toast 样式 */
.h-toast {
  position: fixed;
  top: 20px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 9999;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1);
  min-width: 200px;
  border: 1px solid #eef0f2;
}

.h-toast.success {
  border-left: 4px solid #10b981;
}

.h-toast.error {
  border-left: 4px solid #ef4444;
}

.toast-icon {
  font-size: 16px;
}

.toast-text {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}

/* Toast 过渡动画 */
.toast-enter-active,
.toast-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translate(-50%, -20px);
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  margin-bottom: 24px;
}

.stat-box {
  background: #fff;
  border: 1px solid #eef0f2;
  border-radius: 16px;
  padding: 20px;
  display: flex;
  align-items: center;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05);
  transition: transform 0.2s, box-shadow 0.2s;
}

.stat-box:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.05);
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 16px;
  flex-shrink: 0;
}

.icon-text {
  font-size: 0.7rem;
  font-weight: 700;
  text-transform: uppercase;
}

.book-icon {
  background: #eff6ff;
  color: #3b82f6;
}

.note-icon {
  background: #f0fdf4;
  color: #22c55e;
}

.review-icon {
  background: #fff7ed;
  color: #f97316;
}

.stat-value {
  font-size: 1.5rem;
  font-weight: 800;
  color: #0f172a;
  line-height: 1.2;
}

.stat-title {
  font-size: 0.8rem;
  color: #64748b;
  margin-top: 2px;
}

.list-wrapper {
  background: #fff;
  border: 1px solid #eef0f2;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05);
}

.h-table {
  width: 100%;
  border-collapse: collapse;
  text-align: left;
}

.h-table th {
  padding: 14px 20px;
  background: #f8fafc;
  font-size: 0.75rem;
  font-weight: 700;
  color: #475569;
  border-bottom: 1px solid #f1f5f9;
  text-transform: uppercase;
  letter-spacing: 0.025em;
}

.h-table td {
  padding: 16px 20px;
  border-bottom: 1px solid #f1f5f9;
  vertical-align: middle;
  font-size: 0.875rem;
  color: #334155;
}

.h-table tr:hover {
  background: #fdfdfd;
}

.book-cover-wrapper {
  width: 44px;
  height: 60px;
  flex-shrink: 0;
}

.book-cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 6px;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
}

.book-cover-placeholder {
  width: 100%;
  height: 100%;
  background: #f1f5f9;
  border-radius: 6px;
}

.book-info {
  display: flex;
  flex-direction: column;
}

.book-title {
  font-weight: 700;
  color: #1e293b;
  max-width: 240px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  text-decoration: none;
  transition: color 0.2s;
  line-height: 1.4;
}

.book-title:hover {
  color: #3b82f6;
}

.book-author {
  font-size: 0.75rem;
  color: #64748b;
  margin-top: 4px;
}

.progress-col {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100px;
}

.progress-bar-bg {
  height: 6px;
  background: #f1f5f9;
  border-radius: 3px;
  overflow: hidden;
}

.progress-bar-fill {
  height: 100%;
  background: linear-gradient(90deg, #3b82f6, #60a5fa);
  border-radius: 3px;
  transition: width 0.6s cubic-bezier(0.4, 0, 0.2, 1);
}

.progress-text {
  font-size: 0.75rem;
  color: #64748b;
  font-weight: 600;
}

.h-btn {
  padding: 8px 16px;
  border-radius: 10px;
  font-size: 0.875rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.h-btn-outline {
  border: 1.5px solid #e2e8f0;
  background: #fff;
  color: #475569;
}

.h-btn-outline:hover {
  background: #f8fafc;
  border-color: #cbd5e1;
  color: #1e293b;
}

.h-btn-text {
  background: transparent;
  border: 1px solid #e2e8f0;
  padding: 4px 12px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  color: #64748b;
  transition: all 0.2s;
  line-height: 1;
  white-space: nowrap;
  flex-shrink: 0;
}

.h-btn-text.danger {
  color: #ef4444;
  border-color: #fee2e2;
}

.h-btn-text.danger:hover {
  background: #fef2f2;
  border-color: #fecaca;
}

.h-btn-icon {
  background: transparent;
  border: 1px solid #e2e8f0;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;
  font-size: 14px;
}

.h-btn-icon.danger:hover {
  background: #fef2f2;
  border-color: #fecaca;
}

.row-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
}

.switch-wrapper {
  display: flex;
  align-items: center;
  gap: 8px;
}

.switch-label {
  font-size: 12px;
  color: #64748b;
  min-width: 28px;
}

/* Switch 核心样式 */
.h-switch {
  position: relative;
  display: inline-block;
  width: 36px;
  height: 20px;
}

.h-switch input {
  opacity: 0;
  width: 0;
  height: 0;
}

.slider {
  position: absolute;
  cursor: pointer;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: #e2e8f0;
  transition: .3s;
}

.slider:before {
  position: absolute;
  content: "";
  height: 14px;
  width: 14px;
  left: 3px;
  bottom: 3px;
  background-color: white;
  transition: .3s;
}

input:checked + .slider {
  background-color: #10b981;
}

input:focus + .slider {
  box-shadow: 0 0 1px #10b981;
}

input:checked + .slider:before {
  transform: translateX(16px);
}

.slider.round {
  border-radius: 20px;
}

.slider.round:before {
  border-radius: 50%;
}

.h-switch.mini {
  width: 30px;
  height: 16px;
}

.h-switch.mini .slider:before {
  height: 10px;
  width: 10px;
  left: 3px;
  bottom: 3px;
}

.h-switch.mini input:checked + .slider:before {
  transform: translateX(14px);
}

.row-hidden {
  opacity: 0.6;
}

.status-tag {
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 4px;
  font-weight: 700;
}

.tag-visible {
  background: #f0fdf4;
  color: #16a34a;
}

.tag-hidden {
  background: #f1f5f9;
  color: #64748b;
}

.text-center {
  text-align: center;
}

.text-muted {
  color: #94a3b8;
}

.fs-12 {
  font-size: 0.75rem;
}

.fs-11 {
  font-size: 0.7rem;
}

.fw-500 {
  font-weight: 500;
}

/* 响应式样式 */
.mobile-only {
  display: none;
}

@media (max-width: 768px) {
  .bl-container {
    padding: 16px;
  }

  .stats-grid {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .stat-box {
    padding: 16px;
  }

  .desktop-only {
    display: none;
  }

  .mobile-only {
    display: block;
  }

  .book-card {
    padding: 16px;
    border-bottom: 1px solid #f1f5f9;
    background: #fff;
    transition: all 0.2s;
  }

  .card-hidden {
    opacity: 0.8;
  }

  .book-card:last-child {
    border-bottom: none;
  }

  .card-main {
    display: flex;
    gap: 12px;
    position: relative;
    margin-bottom: 12px;
  }

  .card-info {
    flex: 1;
    min-width: 0;
  }

  .card-info .book-title {
    max-width: 100%;
    font-size: 0.95rem;
  }

  .card-stats {
    font-size: 0.75rem;
    color: #64748b;
    margin-top: 6px;
    display: flex;
    align-items: center;
    gap: 6px;
  }

  .dot {
    color: #e2e8f0;
  }

  .card-action-btns {
    display: flex;
    flex-direction: column;
    gap: 12px;
    align-items: flex-end;
  }

  .card-del-btn {
    padding: 4px 12px;
    border-radius: 6px;
    background: #fff;
    border: 1px solid #fee2e2;
    color: #ef4444;
    font-size: 12px;
    font-weight: 600;
    cursor: pointer;
  }

  .card-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .card-progress {
    display: flex;
    align-items: center;
    gap: 8px;
    flex: 1;
  }

  .card-progress .progress-bar-bg {
    flex: 1;
    max-width: 120px;
  }

  .last-read {
    font-size: 0.7rem;
    color: #94a3b8;
    margin-left: 12px;
  }
}
</style>
