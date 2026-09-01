<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useRealtimeStore } from '../stores/realtime'
import { isMockMode } from '../config/runtime'
import { navFor, roleFocusZh } from '../auth/rbac'
import BrandIcon from '../components/BrandIcon.vue'
import AgriAgent from '../components/AgriAgent.vue'

const auth = useAuthStore()
const realtime = useRealtimeStore()
const route = useRoute()
const router = useRouter()

const nav = computed(() => navFor(auth.role))
const focus = computed(() => roleFocusZh(auth.role))

onMounted(() => realtime.connect())

async function onLogout() {
  realtime.disconnect()
  await auth.logout()
  router.push({ name: 'login' })
}
</script>

<template>
  <div class="shell" :class="{ 'scene-home': route.name === 'greenhouse' }">
    <aside class="rail">
      <div class="brand">
        <div class="brand-icon">
          <BrandIcon :size="22" />
        </div>
        <div>
          <p class="mark">智慧光棚</p>
          <p class="sub">农业补光与遮阳控制台</p>
        </div>
      </div>

      <nav class="nav">
        <RouterLink
          v-for="item in nav"
          :key="item.to"
          :to="item.to"
          class="nav-link"
        >
          <span class="nav-icon">{{ item.icon }}</span>
          {{ item.label }}
        </RouterLink>
      </nav>

      <div class="foot">
        <div class="user-card">
          <p class="who">{{ auth.session?.username }}</p>
          <p class="role">{{ auth.roleLabel }}</p>
          <p v-if="focus" class="focus">{{ focus }}</p>
        </div>
        <p class="mode mono">
          {{ isMockMode ? 'Mock' : 'HTTP' }} · {{ realtime.connected ? 'Live' : 'Off' }}
        </p>
        <button type="button" class="logout-btn" @click="onLogout">退出登录</button>
      </div>
    </aside>

    <div class="main">
      <header class="top" :class="{ compact: route.name === 'greenhouse' }">
        <div class="top-text">
          <h1>{{ route.meta.title }}</h1>
          <p v-if="route.name !== 'greenhouse'" class="subtitle">{{ route.meta.title }} · 智慧光棚</p>
          <p v-else class="subtitle">三维冠层为主 · 调控与曲线收入侧栏</p>
        </div>
        <div class="status-pill" :data-on="realtime.connected">
          <span class="dot" />
          实时链路
        </div>
      </header>

      <main class="content">
        <div class="content-body">
          <RouterView v-slot="{ Component }">
            <Transition name="fade" mode="out-in">
              <component :is="Component" />
            </Transition>
          </RouterView>
        </div>
      </main>
    </div>

    <Transition name="fade">
      <div v-if="realtime.latestAlarm" class="toast" role="alert">
        <div class="toast-icon">!</div>
        <div class="toast-body">
          <strong>新告警</strong>
          <p>{{ realtime.latestAlarm.deviceName }} · {{ realtime.latestAlarm.message }}</p>
        </div>
                <button type="button" class="toast-close" @click="realtime.clearAlarmToast()">关闭</button>
      </div>
    </Transition>

    <AgriAgent />
  </div>
</template>

<style scoped>
.shell {
  display: grid;
  grid-template-columns: var(--rail) 1fr;
  height: 100vh;
  overflow: hidden;
}

.rail {
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow-y: auto;
  overflow-x: hidden;
  padding: var(--space-6) var(--space-4);
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: saturate(180%) blur(20px);
  -webkit-backdrop-filter: saturate(180%) blur(20px);
  border-right: 1px solid var(--line);
}

.brand {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-8);
  padding: 0 var(--space-2);
}

.brand-icon {
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  border-radius: var(--radius-md);
  background: linear-gradient(145deg, var(--sodium) 0%, #ffcc00 100%);
  color: #fff;
  box-shadow: 0 4px 12px rgba(255, 149, 0, 0.35);
}

.mark {
  margin: 0;
  font-size: var(--text-xl);
  font-weight: 700;
  letter-spacing: var(--tracking-tight);
  color: var(--ink);
}

.sub {
  margin: 2px 0 0;
  font-size: var(--text-xs);
  color: var(--ink-muted);
}

.nav {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.nav-link {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: 10px var(--space-3);
  border-radius: var(--radius-sm);
  color: var(--ink-soft);
  font-size: var(--text-sm);
  font-weight: 500;
  text-decoration: none;
  transition:
    background var(--duration-fast) var(--ease-out),
    color var(--duration-fast);
}

.nav-icon {
  width: 20px;
  text-align: center;
  font-size: 14px;
  opacity: 0.7;
}

.nav-link:hover {
  background: var(--paper);
  color: var(--ink);
}

.nav-link.router-link-active {
  background: var(--accent-soft);
  color: var(--accent);
}

.nav-link.router-link-active .nav-icon {
  opacity: 1;
}

.foot {
  margin-top: auto;
  display: grid;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-2) 0;
}

.user-card {
  padding: var(--space-3);
  background: var(--paper);
  border-radius: var(--radius-sm);
}

.who {
  margin: 0;
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--ink);
}

.role {
  margin: 2px 0 0;
  font-size: var(--text-xs);
  color: var(--accent);
  font-weight: 500;
}

.focus {
  margin: 4px 0 0;
  font-size: 10px;
  line-height: 1.35;
  color: var(--ink-muted);
}

.mode {
  margin: 0;
  font-size: var(--text-xs);
  color: var(--ink-muted);
  padding: 0 var(--space-2);
}

.logout-btn {
  font: inherit;
  font-size: var(--text-sm);
  font-weight: 500;
  padding: 9px var(--space-3);
  border: none;
  border-radius: var(--radius-sm);
  background: var(--paper);
  color: var(--ink-soft);
  cursor: pointer;
  transition:
    background var(--duration-fast),
    color var(--duration-fast);
}

.logout-btn:hover {
  background: var(--danger-soft);
  color: var(--danger);
}

.main {
  min-width: 0;
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--space-8) var(--page-pad) var(--space-4);
  flex-shrink: 0;
}

.top.compact {
  padding-top: var(--space-3);
  padding-bottom: var(--space-2);
}

.top.compact h1 {
  font-size: var(--text-xl);
}

.shell.scene-home .content {
  padding-bottom: var(--space-2);
}

.top-text h1 {
  font-size: var(--text-3xl);
  font-weight: 700;
}

.subtitle {
  margin: var(--space-1) 0 0;
  font-size: var(--text-sm);
  color: var(--ink-muted);
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: 8px 14px;
  background: var(--panel);
  border-radius: var(--radius-full);
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--ink-soft);
  box-shadow: var(--shadow-sm), var(--shadow-inset);
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--offline);
  transition: background var(--duration-normal), box-shadow var(--duration-normal);
}

.status-pill[data-on='true'] .dot {
  background: var(--online);
  box-shadow: 0 0 8px rgba(52, 199, 89, 0.5);
}

.content {
  padding: 0 var(--page-pad) calc(var(--space-5) + var(--shadow-bleed));
  flex: 1 1 0;
  min-height: 0;
  width: 100%;
  box-sizing: border-box;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.content-body {
  flex: 1 1 0;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  display: flex;
  flex-direction: column;
}

.content-body:has(.ui-page-fill) {
  overflow: hidden;
}

.content-body :deep(.ui-page-fill) {
  flex: 1 1 auto;
  min-height: 0;
  height: 100%;
}

.toast {
  position: fixed;
  top: calc(var(--space-4) + 56px);
  right: var(--space-6);
  bottom: auto;
  left: auto;
  z-index: 95;
  display: flex;
  gap: var(--space-3);
  align-items: center;
  max-width: min(380px, calc(100vw - 2 * var(--space-6)));
  padding: var(--space-4);
  background: var(--panel);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  border: 1px solid color-mix(in srgb, var(--danger) 35%, var(--line));
  animation: toast-in var(--duration-slow) var(--ease-spring) both;
}

@keyframes toast-in {
  from {
    opacity: 0;
    transform: translateY(-10px) scale(0.96);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

.toast-icon {
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  border-radius: var(--radius-sm);
  background: var(--danger-soft);
  color: var(--danger);
  font-weight: 700;
  font-size: var(--text-lg);
}

.toast-body strong {
  display: block;
  font-size: var(--text-sm);
  font-weight: 600;
}

.toast-body p {
  margin: 4px 0 0;
  font-size: var(--text-sm);
  color: var(--ink-soft);
  line-height: var(--leading-normal);
}

.toast-close {
  flex-shrink: 0;
  border: none;
  background: var(--paper);
  color: var(--ink);
  padding: 8px 12px;
  border-radius: var(--radius-sm);
  font: inherit;
  font-size: var(--text-xs);
  font-weight: 500;
  cursor: pointer;
  transition: background var(--duration-fast);
}

.toast-close:hover {
  background: var(--line-strong);
}

@media (max-width: 840px) {
  .shell {
    grid-template-columns: 1fr;
    height: auto;
    min-height: 100vh;
    overflow: visible;
  }

  .rail {
    height: auto;
    max-height: none;
    flex-direction: row;
    flex-wrap: wrap;
    gap: var(--space-2);
    padding: var(--space-4);
  }

  .main {
    height: auto;
    min-height: 0;
    overflow: visible;
  }

  .content {
    overflow: visible;
  }

  .content-body {
    overflow: visible;
  }

  .brand {
    width: 100%;
    margin-bottom: var(--space-3);
  }

  .nav {
    flex-direction: row;
    flex-wrap: wrap;
    width: 100%;
  }

  .foot {
    width: 100%;
    grid-template-columns: 1fr auto;
    align-items: center;
  }

  .top {
    padding: var(--space-5) var(--space-4) var(--space-3);
    flex-direction: column;
    align-items: flex-start;
    gap: var(--space-3);
  }

  .content {
    padding: 0 var(--page-pad) var(--space-5);
  }

  .toast {
    top: var(--space-3);
    right: var(--space-3);
    left: var(--space-3);
    max-width: none;
  }
}
</style>
