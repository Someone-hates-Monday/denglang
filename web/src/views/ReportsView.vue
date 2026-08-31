<script setup lang="ts">
import { computed } from 'vue'
import { useAuthStore } from '../stores/auth'
import { ROLE_LABEL, normalizeRole } from '../auth/rbac'

const auth = useAuthStore()
const roleZh = computed(() => (auth.role ? ROLE_LABEL[normalizeRole(auth.role)] : ''))
</script>

<template>
  <div class="ui-page">
    <section class="ui-card block">
      <h2 class="ui-section-title">报告</h2>
      <p class="lead">
        当前身份：<strong>{{ roleZh }}</strong>。可查看产量–能耗与日光合摘要；学员提交的实训报告在此批阅（场长/农艺）。
      </p>
      <ul class="list">
        <li v-if="auth.can('report.write')">可撰写 / 提交报告</li>
        <li v-else>仅可浏览已发布报告</li>
        <li>完整报告流与批阅状态将在后续迭代接入</li>
      </ul>
    </section>
  </div>
</template>

<style scoped>
.block {
  padding: var(--space-6);
  max-width: 640px;
}
.lead {
  margin: 0 0 var(--space-4);
  color: var(--ink-soft);
  line-height: var(--leading-normal);
}
.list {
  margin: 0;
  padding-left: 1.2em;
  color: var(--ink-muted);
  font-size: var(--text-sm);
  line-height: 1.7;
}
</style>
