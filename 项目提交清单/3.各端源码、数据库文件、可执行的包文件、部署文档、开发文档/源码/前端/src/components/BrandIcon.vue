<script setup lang="ts">
withDefaults(
  defineProps<{
    size?: number
  }>(),
  { size: 24 },
)
</script>

<template>
  <!-- 智慧光棚：冠层拱棚 + 补光示意 -->
  <svg
    class="canopy-icon"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    aria-hidden="true"
  >
    <path
      d="M3.5 14.5c0-4.2 3.8-7.5 8.5-7.5s8.5 3.3 8.5 7.5"
      stroke="currentColor"
      stroke-width="1.6"
      stroke-linecap="round"
    />
    <path d="M5 14.5h14" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" />
    <path
      d="M12 7v3.2M8.2 9.2l1.4 1.8M15.8 9.2l-1.4 1.8"
      stroke="currentColor"
      stroke-width="1.3"
      stroke-linecap="round"
      opacity="0.9"
    />
    <rect x="7.2" y="15.2" width="2.2" height="4.2" rx="0.5" fill="currentColor" opacity="0.85" />
    <rect x="14.6" y="15.2" width="2.2" height="4.2" rx="0.5" fill="currentColor" opacity="0.85" />
    <circle cx="12" cy="18.2" r="1.35" fill="currentColor" />
  </svg>
</template>

<style scoped>
.canopy-icon {
  display: block;
  flex-shrink: 0;
}
</style>
