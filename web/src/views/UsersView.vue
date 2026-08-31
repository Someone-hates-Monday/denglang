<script setup lang="ts">
import { DEMO_ACCOUNTS, ROLE_LABEL } from '../auth/rbac'
</script>

<template>
  <div class="ui-page">
    <section class="ui-card block">
      <h2 class="ui-section-title">用户与角色</h2>
      <p class="lead">系统管理员可绑定角色、重置演示种子。以下为文档约定的六类演示账号（只读示意）。</p>
      <table class="tbl">
        <thead>
          <tr>
            <th>用户名</th>
            <th>角色</th>
            <th>职责</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="a in DEMO_ACCOUNTS" :key="a.username">
            <td class="mono">{{ a.username }}</td>
            <td>{{ ROLE_LABEL[a.role] }}</td>
            <td>{{ a.blurb }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>

<style scoped>
.block {
  padding: var(--space-6);
  max-width: 720px;
}
.lead {
  margin: 0 0 var(--space-4);
  color: var(--ink-soft);
  line-height: var(--leading-normal);
}
.tbl {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--text-sm);
}
.tbl th,
.tbl td {
  text-align: left;
  padding: 10px 8px;
  border-bottom: 1px solid var(--line);
}
.tbl th {
  color: var(--ink-muted);
  font-weight: 500;
}
</style>
