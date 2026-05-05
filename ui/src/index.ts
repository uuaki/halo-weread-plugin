import { definePlugin } from '@halo-dev/ui-shared'
import { markRaw } from 'vue'
import BookList from './views/BookList.vue'
import Setting from './views/Setting.vue'

export default definePlugin({
  components: {},
  routes: [],
  extensionPoints: {
    'plugin:self:tabs:create'() {
      return [
        {
          id: 'setting',
          label: '同步设置',
          component: markRaw(Setting),
        },
        {
          id: 'books',
          label: '书籍管理',
          component: markRaw(BookList),
        }
      ]
    },
  },
} as any)
