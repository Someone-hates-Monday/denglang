<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { apiMode, isHttpMode, isMockMode } from '../config/runtime'
import {
  DEMO_ACCOUNTS,
  REGISTERABLE_ROLES,
  ROLE_LABEL,
  type Role,
} from '../auth/rbac'
import BrandIcon from '../components/BrandIcon.vue'

const apiBase = (import.meta.env.VITE_API_BASE as string) || ''

const auth = useAuthStore()
const router = useRouter()

const mode = ref<'login' | 'register'>('login')
const username = ref('')
const password = ref('')
const role = ref<Role>('GROWER')
const loading = ref(false)
const error = ref('')
const tip = ref('')
const backendOk = ref<boolean | null>(null)

async function checkBackend() {
  if (!isHttpMode) {
    backendOk.value = null
    return
  }
  try {
    const res = await fetch(`${apiBase}/users/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: '__ping__', password: '__ping__' }),
    })
    const body = (await res.json()) as { code?: number }
    backendOk.value = typeof body.code === 'number'
  } catch {
    backendOk.value = false
  }
}

onMounted(checkBackend)

function fillDemo(u: string, p: string) {
  mode.value = 'login'
  username.value = u
  password.value = p
  error.value = ''
}

async function submit() {
  loading.value = true
  error.value = ''
  tip.value = ''
  try {
    if (mode.value === 'login') {
      await auth.login(username.value, password.value)
      router.replace(auth.homePath)
    } else {
      await auth.register(username.value, password.value, role.value)
      tip.value = '注册成功，请登录'
      mode.value = 'login'
      password.value = ''
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : '失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login">
    <section class="hero slide-up-enter-active">
      <div class="hero-badge">
        <BrandIcon :size="28" />
      </div>
      <h1>冠层光环境，一屏尽览</h1>
      <p class="lead">
        六类人员各看各的界面：场长总览、农艺审批、种植执行、运维设备、学员只读、系统配置 —
        <strong>智慧光棚</strong>。
      </p>
      <p class="mode-badge mono" :data-mode="apiMode">
        {{ isMockMode ? 'Mock 内存演示' : 'HTTP 真后端' }} · {{ apiMode }}
      </p>
      <p v-if="isHttpMode && backendOk === false" class="hint warn">
        后端未响应。请先启动 Java 服务（:8080），或切 Mock 用下方六账号演示分权。
      </p>
      <p v-else-if="isHttpMode && backendOk" class="hint">
        后端已连通。可用种子账号，或注册时选择角色；演示用户名与 Mock 一致时会映射到文档角色。
      </p>
      <p v-else class="hint">点击下方角色卡片可一键填入演示账号。</p>

      <ul class="demo-grid">
        <li v-for="a in DEMO_ACCOUNTS" :key="a.username">
          <button type="button" class="demo-card" @click="fillDemo(a.username, a.password)">
            <span class="demo-role">{{ ROLE_LABEL[a.role] }}</span>
            <span class="demo-user mono">{{ a.username }}</span>
            <span class="demo-blurb">{{ a.blurb }}</span>
          </button>
        </li>
      </ul>
    </section>

    <form class="panel slide-up-enter-active slide-up-delay-1" @submit.prevent="submit">
      <div class="ui-tabs">
        <button type="button" :class="{ on: mode === 'login' }" @click="mode = 'login'">登录</button>
        <button type="button" :class="{ on: mode === 'register' }" @click="mode = 'register'">
          注册
        </button>
      </div>

      <label class="ui-label">
        用户名
        <input v-model="username" class="ui-input" required autocomplete="username" />
      </label>
      <label class="ui-label">
        密码
        <input
          v-model="password"
          class="ui-input"
          type="password"
          required
          autocomplete="current-password"
        />
      </label>
      <label v-if="mode === 'register'" class="ui-label">
        角色
        <select v-model="role" class="ui-select">
          <option v-for="r in REGISTERABLE_ROLES" :key="r" :value="r">{{ ROLE_LABEL[r] }}</option>
        </select>
      </label>

      <p v-if="error" class="ui-msg ui-msg-error">{{ error }}</p>
      <p v-if="tip" class="ui-msg">{{ tip }}</p>

      <button type="submit" class="ui-btn ui-btn-warm submit" :disabled="loading">
        {{ loading ? '提交中…' : mode === 'login' ? '进入智慧光棚' : '创建账号' }}
      </button>
    </form>
  </div>
</template>

<style scoped>
.login {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 1.1fr 0.9fr;
  gap: var(--space-10);
  padding: var(--space-10);
  align-items: center;
  max-width: 1100px;
  margin: 0 auto;
}

.hero {
  max-width: 520px;
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  margin-bottom: var(--space-5);
  border-radius: var(--radius-md);
  background: linear-gradient(145deg, var(--sodium) 0%, #ffcc00 100%);
  color: #fff;
  box-shadow: 0 8px 24px rgba(255, 149, 0, 0.3);
}

.hero h1 {
  font-size: clamp(32px, 4vw, 44px);
  font-weight: 700;
  line-height: var(--leading-tight);
  letter-spacing: var(--tracking-tight);
}

.lead {
  margin-top: var(--space-4);
  color: var(--ink-soft);
  line-height: var(--leading-normal);
  font-size: var(--text-lg);
}

.mode-badge {
  margin: var(--space-4) 0 0;
  display: inline-block;
  padding: 6px 12px;
  border-radius: var(--radius-full);
  font-size: var(--text-xs);
  font-weight: 500;
  background: var(--panel);
  box-shadow: var(--shadow-sm), var(--shadow-inset);
  color: var(--ink-soft);
}

.mode-badge[data-mode='http'] {
  color: var(--online);
  background: var(--online-soft);
}

.mode-badge[data-mode='mock'] {
  color: var(--sodium);
  background: var(--sodium-soft);
}

.hint {
  margin: var(--space-3) 0 0;
  color: var(--ink-muted);
  font-size: var(--text-sm);
  line-height: var(--leading-normal);
}

.hint.warn {
  color: var(--danger);
}

.demo-grid {
  list-style: none;
  margin: var(--space-5) 0 0;
  padding: 0;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-2);
}

.demo-card {
  width: 100%;
  text-align: left;
  font: inherit;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--panel);
  cursor: pointer;
  display: grid;
  gap: 2px;
  transition:
    border-color var(--duration-fast),
    background var(--duration-fast);
}

.demo-card:hover {
  border-color: var(--accent);
  background: var(--accent-soft);
}

.demo-role {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--ink);
}

.demo-user {
  font-size: var(--text-xs);
  color: var(--accent);
}

.demo-blurb {
  font-size: var(--text-xs);
  color: var(--ink-muted);
}

.panel {
  justify-self: end;
  width: min(100%, 400px);
  display: grid;
  gap: var(--space-4);
  padding: var(--space-6);
  background: var(--panel);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-lg);
}

.submit {
  margin-top: var(--space-1);
  width: 100%;
  padding: 12px;
  font-size: var(--text-base);
}

@media (max-width: 840px) {
  .login {
    grid-template-columns: 1fr;
    padding: var(--space-6) var(--space-4);
    gap: var(--space-6);
  }

  .panel {
    justify-self: stretch;
  }

  .demo-grid {
    grid-template-columns: 1fr;
  }
}
</style>
